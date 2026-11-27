#!/usr/bin/env python3
"""Build an unsigned API17 APK from public sources. Never connects to a vehicle."""
import argparse
import os
from pathlib import Path
import shutil
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def executable(value):
    found = shutil.which(str(value))
    if not found:
        raise SystemExit("Executable not found: " + str(value))
    return Path(found).resolve()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("app", choices=("dashboard", "hub"))
    parser.add_argument("--android-jar", required=True, type=Path)
    parser.add_argument("--r8-jar", required=True, type=Path)
    parser.add_argument("--aapt2", required=True)
    parser.add_argument("--java", default="java")
    parser.add_argument("--out", type=Path)
    parser.add_argument("--version-name")
    args = parser.parse_args()
    subprocess.run([sys.executable, str(ROOT / "scripts/generate_translations.py"), "--check"], check=True)
    java = executable(args.java)
    javac = executable(java.with_name("javac.exe" if os.name == "nt" else "javac"))
    aapt2 = executable(args.aapt2)
    sdk, r8 = args.android_jar.resolve(), args.r8_jar.resolve()
    for path in (sdk, r8):
        if not path.is_file():
            raise SystemExit("Required external tool file not found: " + str(path))
    out = (args.out or ROOT / "build" / args.app).resolve()
    out.mkdir(parents=True, exist_ok=False)

    def run(label, command):
        result = subprocess.run([str(part) for part in command], capture_output=True)
        log = out / (label + ".log")
        log.write_bytes(result.stdout + result.stderr)
        if result.returncode:
            raise SystemExit(label + " failed; see " + str(log))

    classes = out / "classes"
    classes.mkdir()
    app = ROOT / "apps" / args.app
    shared = ROOT / "shared" / "src"
    sources = sorted((app / "src").rglob("*.java"))
    classpath = str(sdk)
    if args.app == "dashboard":
        abi = out / "compile-only-abi"
        abi.mkdir()
        stubs = sorted((ROOT / "compile-stubs" / "src").rglob("*.java"))
        run("compile-abi", [javac, "-encoding", "UTF-8", "-source", "7", "-target", "7",
                            "-classpath", sdk, "-d", abi, *stubs])
        classpath = os.pathsep.join((str(sdk), str(abi)))
        sources += sorted(shared.rglob("*.java"))   # with UsbDebugControl: 설정 → 진단 → USB 디버깅 (upstream 665642b did the same)
        # Neon Drive runs as a dashboard page (NeonDrivePage, fed from the dashboard's data); no standalone app here.
        sources += sorted((ROOT / "apps/neondrive/src").rglob("*.java"))
    else:
        sources.append(shared / "com/veloster/vehicle/UsbDebugControl.java")
        sources += sorted((shared / "com/veloster/i18n").rglob("*.java"))
        sources += sorted((shared / "com/veloster/diagnostics").rglob("*.java"))
    # The file lists go through an argument file and a jar: on Windows the command line has a 32 KB limit, which the
    # dashboard's sources and classes passed once the shared tree grew.
    listing = out / "sources.txt"
    listing.write_text("".join('"' + p.as_posix() + '"\n' for p in sources), encoding="utf-8")
    run("javac", [javac, "-encoding", "UTF-8", "-source", "7", "-target", "7",
                  "-classpath", classpath, "-d", classes, "@" + str(listing)])
    # Only app classes enter D8; compile-only OEM declarations never enter the APK.
    jar = out / "classes.jar"
    with zipfile.ZipFile(jar, "w") as packed:
        for cls in sorted(classes.rglob("*.class")):
            packed.write(cls, cls.relative_to(classes).as_posix())
    run("d8", [java, "-cp", r8, "com.android.tools.r8.D8", "--min-api", "17",
               "--lib", sdk, "--output", out, jar])
    dex = out / "classes.dex"
    if dex.read_bytes()[:8] != b"dex\n035\0":
        raise SystemExit("Unexpected DEX version; API17 requires DEX035")
    if list(out.glob("classes[2-9]*.dex")):
        raise SystemExit("Multidex output is not supported by this build")
    resources = []
    if (app / "res").is_dir():
        run("resources", [aapt2, "compile", "--dir", app / "res", "-o", out / "resources.zip"])
        resources = ["--auto-add-overlay", "-R", out / "resources.zip"]
    if (app / "assets").is_dir():   # dashboard: the fonts offered in 설정 → 글꼴 (BundledFonts), 내 차's 3D car (car3d)
        resources += ["-A", app / "assets"]
    # --version-name: the release's own name (v1.16.1, test-1120-abc1234) in place of the manifest's, so the installers and
    # the diagnostics can tell which build is on the car. versionCode stays: the head unit refuses a lower one on reinstall.
    named = ["--version-name", args.version_name, "--replace-version"] if args.version_name else []
    run("manifest", [aapt2, "link", "-I", sdk, "--manifest", app / "AndroidManifest.xml", *resources, *named,
                     "--min-sdk-version", "17", "--target-sdk-version", "17", "-o", out / "base.apk"])
    target = out / (args.app + "-UNSIGNED.apk")
    with zipfile.ZipFile(out / "base.apk") as source, zipfile.ZipFile(target, "w") as archive:
        for entry in source.infolist():
            archive.writestr(entry, source.read(entry))
        archive.write(dex, "classes.dex", compress_type=zipfile.ZIP_DEFLATED)
    print("Created unsigned APK: " + str(target))
    print("Not installable as-is. See docs/BUILD.md for API17 signing and vehicle compatibility limits.")


if __name__ == "__main__":
    main()
