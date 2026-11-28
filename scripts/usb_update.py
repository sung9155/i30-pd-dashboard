#!/usr/bin/env python3
"""Make the USB update bundle (i30-usb-update.zip) from the two signed release APKs and the update key.

    python scripts/usb_update.py --dashboard dashboard-i30pd-platform.apk --hub myapps-i30pd-platform.apk --out i30-usb-update.zip

The zip holds one folder, i30-update/, to unzip onto the top of a USB stick: manifest.txt (the build's name, the head
unit it is for, the APK signer and each app's versionCode / size / SHA-256 / file name / versionName), manifest.sig
(RSA SHA-256 over manifest.txt with the update key, via openssl) and the APKs. My Apps checks all of it before
installing (apps/hub/.../usb/UsbBundle.java has the format). The versions are read from the APKs themselves (aapt2):
this build keeps versionCode fixed and names each build (v1.20.3, test-1128-abc1234), so the name is what My Apps compares.

The update key's private half is not in the repository: --key, or usb-update-key.pem in KEY_DIR (where release.py
finds the platform key). Its public half is UsbUpdateKey.java. From upstream bcc6141. Never connects to a vehicle.
"""
import argparse
import base64
import hashlib
import os
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

FILES = {"com.veloster.dashboard": "dashboard.apk", "com.veloster.carplayhub": "hub.apk"}   # in install order
FOLDER = "i30-update"
PRODUCT, SDK = "pd_kr", 17
NAME = r"[A-Za-z0-9][A-Za-z0-9._-]{0,39}"
EXE = ".exe" if os.name == "nt" else ""
OPENSSL = shutil.which("openssl") or "C:/Program Files/Git/mingw64/bin/openssl.exe"   # Git for Windows ships one


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for block in iter(lambda: f.read(1 << 20), b""):
            h.update(block)
    return h.hexdigest()


def apk_version(aapt2, apk):
    """(package, versionCode, versionName) of an APK."""
    out = subprocess.run([str(aapt2), "dump", "badging", str(apk)], capture_output=True, text=True, encoding="utf-8", errors="replace").stdout
    m = re.search(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']*)'", out, re.M)
    if not m:
        raise SystemExit("no package / version in " + str(apk))
    return m.group(1), int(m.group(2)), m.group(3)


def cert_sha256(pem_path):
    text = Path(pem_path).read_text(encoding="ascii")
    body = re.search(r"-----BEGIN CERTIFICATE-----(.*?)-----END CERTIFICATE-----", text, re.S)
    if not body:
        raise SystemExit("not a PEM certificate: " + str(pem_path))
    return hashlib.sha256(base64.b64decode("".join(body.group(1).split()))).hexdigest()


def manifest_text(release, signer, apps, product=PRODUCT, sdk=SDK):
    lines = ["I30USB1", "release=" + release, "product=" + product, "sdk=%d" % sdk, "signer=" + signer]
    for package, version, size, digest, file, name in apps:
        lines.append("app=%s,%d,%d,%s,%s,%s" % (package, version, size, digest, file, name))
    return ("\n".join(lines) + "\n").encode("ascii")


def build(apps, key, cert, out, product=PRODUCT, sdk=SDK):
    """apps: (package, versionCode, versionName, apk path) for the dashboard, then My Apps. Returns (release, manifest bytes)."""
    if [a[0] for a in apps] != list(FILES):
        raise SystemExit("the dashboard APK first, then My Apps: got %s" % [a[0] for a in apps])
    rows = []
    for package, code, name, apk in apps:
        if not re.fullmatch(NAME, name) or code < 1:
            raise SystemExit("%s: version %r / %r cannot go in a bundle" % (package, code, name))
        rows.append((package, code, Path(apk).stat().st_size, sha256(apk), FILES[package], name))
    release = apps[0][2]
    text = manifest_text(release, cert_sha256(cert), rows, product, sdk)
    with tempfile.TemporaryDirectory(prefix="i30-usb-") as tmp:
        m, s = Path(tmp) / "manifest.txt", Path(tmp) / "manifest.sig"
        m.write_bytes(text)
        subprocess.run([OPENSSL, "dgst", "-sha256", "-sign", str(key), "-out", str(s), str(m)], check=True)
        signature = s.read_bytes()
    if len(signature) != 256:
        raise SystemExit("the update key must be RSA 2048 (signature of %d bytes)" % len(signature))
    out = Path(out)
    out.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(out, "w", zipfile.ZIP_STORED) as z:   # APKs are already compressed
        z.writestr(FOLDER + "/manifest.txt", text)
        z.writestr(FOLDER + "/manifest.sig", signature)
        for (package, _, _, apk), row in zip(apps, rows):
            z.write(apk, FOLDER + "/" + row[4])
    return release, text


def main():
    keys = Path(os.environ.get("KEY_DIR", "D:/Utility/veloster-n-dashboard/build/keys"))
    sdk = Path(os.environ.get("ANDROID_SDK", "D:/Android/sdk"))
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--dashboard", type=Path, required=True, help="the signed dashboard APK of the release")
    p.add_argument("--hub", type=Path, required=True, help="the signed My Apps APK of the release")
    p.add_argument("--key", type=Path, default=keys / "usb-update-key.pem", help="the update key's private half (PEM)")
    p.add_argument("--cert", type=Path, default=keys / "platform.x509.pem", help="the APK signing certificate")
    p.add_argument("--aapt2", type=Path, default=sdk / ("build-tools/34.0.0/aapt2" + EXE))
    p.add_argument("--out", type=Path, required=True)
    p.add_argument("--product", default=PRODUCT, help=argparse.SUPPRESS)   # emulator tests only: the head unit is pd_kr / API 17
    p.add_argument("--sdk", type=int, default=SDK, help=argparse.SUPPRESS)
    a = p.parse_args()
    apps = [apk_version(a.aapt2, apk) + (apk,) for apk in (a.dashboard, a.hub)]
    release, text = build(apps, a.key, a.cert, a.out, a.product, a.sdk)
    sys.stdout.write("i30-usb-update %s -> %s\n%s" % (release, a.out, text.decode("ascii")))


if __name__ == "__main__":
    main()
