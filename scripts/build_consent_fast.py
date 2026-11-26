#!/usr/bin/env python3
"""Builds i30pd-consent-fast.jar, the start-up notice 1 s builder the PC installers run (option 4).

  python scripts/build_consent_fast.py [--java <JDK 11+ bin/java>] [--out build/i30pd-consent-fast.jar]

The jar holds only our code: ConsentFastBuilder and the two hook classes as smali (i30pd_port/consent_fast/hook,
made from src/com/android/systemui/statusbar/ConsentFast.java with r8 8.3.37 + baksmali 2.5.2 -d -a 17). It is
compiled against apksig 8.3.0 (fetched here, hash-pinned); at run time apksig.jar sits next to it (manifest Class-Path).
"""
import argparse
import hashlib
import shutil
import subprocess
import tempfile
import urllib.request
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CF = ROOT / "i30pd_port" / "consent_fast"
APKSIG = ("https://dl.google.com/dl/android/maven2/com/android/tools/build/apksig/8.3.0/apksig-8.3.0.jar",
          "468ee1852fa12461c6629608fe47be70e50f60be7c4744bb7f62ca48e85c0a6c")
DEFAULT_JAVA = Path("C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot/bin/java.exe")


def main():
    a = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    a.add_argument("--java", type=Path, default=DEFAULT_JAVA)
    a.add_argument("--out", type=Path, default=ROOT / "build" / "i30pd-consent-fast.jar")
    x = a.parse_args()
    javac = x.java.with_name("javac" + x.java.suffix)
    with tempfile.TemporaryDirectory() as t:
        t = Path(t)
        with urllib.request.urlopen(APKSIG[0], timeout=60) as r:
            data = r.read()
        if hashlib.sha256(data).hexdigest() != APKSIG[1]:
            raise SystemExit("apksig.jar hash mismatch")
        (t / "apksig.jar").write_bytes(data)
        subprocess.run([str(javac), "--release", "11", "-encoding", "UTF-8", "-cp", str(t / "apksig.jar"),
                        "-d", str(t / "cls"), str(CF / "src" / "ConsentFastBuilder.java")], check=True)
        x.out.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(x.out, "w", zipfile.ZIP_DEFLATED) as z:
            z.writestr("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\r\nMain-Class: ConsentFastBuilder\r\nClass-Path: apksig.jar\r\n\r\n")
            for c in sorted((t / "cls").rglob("*.class")):
                z.write(c, c.relative_to(t / "cls").as_posix())
            for h in sorted((CF / "hook").glob("*.smali")):
                z.writestr("hook/" + h.name, h.read_bytes())
    print(f"{x.out}: {x.out.stat().st_size:,} B  sha256 {hashlib.sha256(x.out.read_bytes()).hexdigest()}")


if __name__ == "__main__":
    main()
