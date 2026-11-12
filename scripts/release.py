#!/usr/bin/env python3
"""Release helper: the steps done by hand for every release, in one place.

  python scripts/release.py stable --notes notes.md            # build, sign, check; publishes nothing
  python scripts/release.py stable --notes notes.md --publish  # ...then push main, tag, GitHub release, verify downloads
  python scripts/release.py test                               # build beta, check; publishes nothing
  python scripts/release.py test --publish                     # ...then replace the test APK and refresh the test notes

stable: run it on a checkout whose HEAD is origin/main plus the commits to release (cherry-pick or fast-forward
first). It runs the host tests, builds and signs the dashboard APK, takes the other assets from the latest release
(or from --asset NAME=PATH), and with --publish pushes HEAD to main, tags the next version (or --version vX.Y.Z),
creates the release and downloads the files from /releases/latest to compare their hashes.
test: HEAD must be origin/beta. Replaces the dashboard APK of the `test` release, checks the stored digest, and edits
the notes from the CURRENT GitHub text (other sessions edit them too): title and the stable version in the header.
Nothing outward-facing happens without --publish. Needs git, gh (logged in), JDK 17, Android build-tools.
Overrides: JAVA_HOME, ANDROID_SDK, KEY_DIR (platform.pk8 + platform.x509.pem), RELEASE_ROOT (the checkout to release).
"""
import argparse, hashlib, json, os, re, shutil, subprocess, sys, tempfile, urllib.request
from pathlib import Path

ROOT = Path(os.environ.get("RELEASE_ROOT") or Path(__file__).resolve().parents[1])   # RELEASE_ROOT: run it against another checkout
REPO = "sung9155/i30-pd-dashboard"
DASH = "dashboard-i30pd-platform.apk"
CERT8 = "c8a2e9bc"   # the public AOSP platform test certificate
IS_WIN = os.name == "nt"
EXE, BAT = (".exe", ".bat") if IS_WIN else ("", "")


def sh(*a, cwd=ROOT, check=True, capture=False):
    r = subprocess.run([str(x) for x in a], cwd=cwd, text=True, encoding="utf-8",
                       stdout=subprocess.PIPE if capture else None, stderr=subprocess.STDOUT if capture else None)
    if check and r.returncode:
        sys.exit("FAILED: " + " ".join(str(x) for x in a) + ("\n" + r.stdout if capture else ""))
    return r.stdout.strip() if capture else r.returncode


def sha(p):
    return hashlib.sha256(Path(p).read_bytes()).hexdigest()


def tools():
    java_home = next((Path(h) for h in (os.environ.get("JAVA_HOME"), "C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot")
                      if h and (Path(h) / ("bin/javac" + EXE)).exists()), Path("C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot"))   # a JAVA_HOME without javac (stale or a JRE) is skipped
    sdk = Path(os.environ.get("ANDROID_SDK", "D:/Android/sdk"))
    bt = sdk / "build-tools/34.0.0"
    keys = Path(os.environ.get("KEY_DIR", "D:/Utility/veloster-n-dashboard/build/keys"))
    if not (keys / "platform.pk8").is_file():
        keys = ROOT / "build/keys"
    t = dict(java=java_home / ("bin/java" + EXE), android=sdk / "platforms/android-17/android.jar", aapt2=bt / ("aapt2" + EXE),
             d8=bt / "lib/d8.jar", zipalign=bt / ("zipalign" + EXE), apksigner=bt / ("apksigner" + BAT),
             pk8=keys / "platform.pk8", cert=keys / "platform.x509.pem")
    missing = [k for k, v in t.items() if not v.exists()]
    if missing:
        sys.exit("Missing tool or key: " + ", ".join(f"{k}={t[k]}" for k in missing) + "  (set JAVA_HOME / ANDROID_SDK / KEY_DIR)")
    return t


def preflight(branch_ref):
    if sh("git", "status", "--porcelain", capture=True):
        sys.exit("The working tree has uncommitted changes; commit or stash them first.")
    sh("git", "fetch", "-q", "origin", "--tags")
    if branch_ref == "origin/beta" and sh("git", "rev-parse", "HEAD", capture=True) != sh("git", "rev-parse", "origin/beta", capture=True):
        sys.exit("test: HEAD must be exactly origin/beta (run `git merge --ff-only origin/beta`).")
    if branch_ref == "origin/main" and sh("git", "merge-base", "--is-ancestor", "origin/main", "HEAD", check=False):
        sys.exit("stable: HEAD must contain origin/main (cherry-pick onto it, or fast-forward).")
    print("HEAD", sh("git", "log", "--oneline", "-1", capture=True))


def build(t, out):
    env = dict(os.environ, JAVA_HOME=str(t["java"].parents[1]))
    os.environ.update(env)
    print("== host tests")
    sh(sys.executable, "-X", "utf8", "scripts/test.py", "--java", t["java"])
    print("== build + sign")
    build_dir = ROOT / "build/dashboard"
    shutil.rmtree(build_dir, ignore_errors=True)
    sh(sys.executable, "-X", "utf8", "scripts/build.py", "dashboard", "--java", t["java"], "--android-jar", t["android"],
       "--aapt2", t["aapt2"], "--r8-jar", t["d8"], "--out", build_dir)
    aligned = build_dir / "dashboard-aligned.apk"
    sh(t["zipalign"], "-f", "-p", "4", build_dir / "dashboard-UNSIGNED.apk", aligned)
    apk = out / DASH
    sh(t["apksigner"], "sign", "--key", t["pk8"], "--cert", t["cert"], "--v1-signing-enabled", "true", "--v2-signing-enabled", "false",
       "--v3-signing-enabled", "false", "--min-sdk-version", "17", "--out", apk, aligned)
    sh(t["apksigner"], "verify", apk)
    certs = sh(t["apksigner"], "verify", "--print-certs", apk, capture=True)
    if CERT8 not in certs.replace(":", "").lower():
        sys.exit("The APK is not signed with the expected platform certificate (" + CERT8 + "…).")
    print(f"{DASH}: {apk.stat().st_size:,} B  sha256 {sha(apk)}")
    return apk


def gh_json(*a):
    return json.loads(sh("gh", *a, "-R", REPO, capture=True))


def latest_tag():
    return gh_json("release", "view", "--json", "tagName")["tagName"]


def next_version(tag):
    m = re.fullmatch(r"v(\d+)\.(\d+)\.(\d+)", tag)
    return f"v{m[1]}.{m[2]}.{int(m[3]) + 1}"


def stable(a):
    preflight("origin/main")
    t = tools()
    prev = latest_tag()
    ver = a.version or next_version(prev)
    notes = Path(a.notes).read_text(encoding="utf-8")
    out = Path(tempfile.mkdtemp(prefix="release-"))
    apk = build(t, out)
    print(f"== other assets from {prev}")
    sh("gh", "release", "download", prev, "-R", REPO, "-D", out, "--skip-existing", "--pattern", "*")
    for item in a.asset or []:
        name, path = item.split("=", 1)
        shutil.copy(path, out / name)
        print("replaced", name)
    (out / DASH).write_bytes(apk.read_bytes())
    files = sorted(p for p in out.iterdir() if p.is_file())
    sign = [f"AOSP platform 테스트 키 V1, cert SHA-256 `{CERT8}…`."]
    for p in files:
        if p.suffix == ".apk":
            sign.append(f"`{p.name}` {p.stat().st_size:,} B, sha256 `{sha(p)[:8]}…`.")
    block = " ".join(sign)
    if "{{SIGN}}" in notes:
        notes = notes.replace("{{SIGN}}", block)
    elif CERT8 not in notes:
        sys.exit("The notes have no signing line: add {{SIGN}} where it belongs.")
    notes_path = out / "notes.md"
    notes_path.write_text(notes, encoding="utf-8")
    title = a.title or f"{ver}"
    print(f"\nready: {ver} ({len(files)} assets in {out})\n  title: {title}\n  notes: {notes_path}")
    if not a.publish:
        print("Dry run. Add --publish to push main, tag, release and verify.")
        return
    sh("git", "push", "origin", "HEAD:refs/heads/main")
    sh("git", "tag", ver)
    sh("git", "push", "origin", ver)
    sh("gh", "release", "create", ver, "-R", REPO, "--verify-tag", "--latest", "--title", title, "--notes-file", notes_path, *files)
    print("== verify /releases/latest/download")
    bad = 0
    for p in files:
        data = urllib.request.urlopen(f"https://github.com/{REPO}/releases/latest/download/{p.name}").read()
        ok = hashlib.sha256(data).hexdigest() == sha(p)
        bad += not ok
        print(("OK   " if ok else "DIFF ") + p.name)
    if bad:
        sys.exit("Some downloads differ from the files uploaded (a CDN cache can lag: check again in a minute).")
    print("Released", ver, "\nNext: run `release.py test --publish` so the test notes follow this version.")


def test(a):
    preflight("origin/beta")
    t = tools()
    out = Path(tempfile.mkdtemp(prefix="release-test-"))
    apk = build(t, out)
    if not a.publish:
        print("Dry run. Add --publish to replace the test APK and refresh the notes.")
        return
    sh("gh", "release", "upload", "test", "-R", REPO, apk, "--clobber")
    stored = {x["name"]: x for x in gh_json("release", "view", "test", "--json", "assets")["assets"]}[DASH]
    if stored["digest"] != "sha256:" + sha(apk):
        sys.exit("The stored test APK digest does not match the file uploaded.")
    print("test APK replaced, digest matches")
    cur = gh_json("release", "view", "test", "--json", "name,body")
    stable_tag = latest_tag()
    m = re.match(r"테스트 — (v\d+\.\d+\.\d+)(.*)", cur["name"])
    if m and m[1] != stable_tag:
        title = f"테스트 — {stable_tag}{m[2]}"
        head, rest = cur["body"].split("\n", 8)[:8], cur["body"].split("\n", 8)[8:]
        body = "\n".join(l.replace(m[1], stable_tag) for l in head) + "\n" + "\n".join(rest)   # header lines only; the history below stays
        notes = out / "test-notes.md"
        # Bytes, not write_text: on Windows write_text turned the body's \r\n into \r\r\n, so the blank lines doubled on
        # every run until GitHub refused the notes (125,000 characters). Normalise to \n and at most one blank line.
        body = re.sub(r"\n{3,}", "\n\n", re.sub(r"\r+\n", "\n", body))
        notes.write_bytes(body.encode("utf-8"))
        sh("gh", "release", "edit", "test", "-R", REPO, "--title", title, "--notes-file", notes)
        print("test title:", title)
    else:
        print("test title already follows", stable_tag)
    tag_at = sh("git", "ls-remote", "origin", "refs/tags/test", capture=True).split()[0]
    head = sh("git", "rev-parse", "HEAD", capture=True)
    if tag_at != head:
        if a.move_tag:
            sh("git", "tag", "-f", "test", head)
            sh("git", "push", "-f", "origin", "refs/tags/test")
        else:
            print("The `test` tag is behind beta; to move it:  git tag -f test HEAD && git push -f origin refs/tags/test   (or pass --move-tag)")


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    s = p.add_subparsers(dest="cmd", required=True)
    st = s.add_parser("stable")
    st.add_argument("--notes", required=True, help="release notes (markdown); {{SIGN}} becomes the signing line")
    st.add_argument("--title")
    st.add_argument("--version", help="default: the latest tag with the patch number + 1")
    st.add_argument("--asset", action="append", metavar="NAME=PATH", help="replace one of the previous release's files")
    st.add_argument("--publish", action="store_true")
    te = s.add_parser("test")
    te.add_argument("--publish", action="store_true")
    te.add_argument("--move-tag", action="store_true")
    a = p.parse_args()
    {"stable": stable, "test": test}[a.cmd](a)


if __name__ == "__main__":
    main()
