"""USB 업데이트 end to end on the host: scripts/usb_update.py makes a bundle from two jarsigner-signed stand-in APKs and
a throwaway update key, and My Apps' own checks (UsbBundle) accept it and refuse a tampered copy. No Android."""
import importlib.util
import shutil
import subprocess
import tempfile
import unittest
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
EXE = ".exe" if __import__("os").name == "nt" else ""
HARNESS = r'''import com.veloster.carplayhub.usb.UsbBundle;
import java.io.File;
public class UsbHarness {
 public static void main(String[] a) throws Exception {
  File dir = new File(a[0]);
  UsbBundle b = UsbBundle.verify(UsbBundle.read(new File(dir, "manifest.txt"), 2048), UsbBundle.read(new File(dir, "manifest.sig"), 256), UsbBundle.key(a[1]));
  b.checkDevice("pd_kr", 17);
  for (UsbBundle.App app : b.apps) b.checkApk(new File(dir, app.file), app);
  System.out.println("OK " + b.release + " " + b.apps.get(0).packageName + " " + b.apps.get(1).packageName);
 }
}'''


def run(*cmd, **kw):
    return subprocess.run([str(c) for c in cmd], capture_output=True, text=True, errors="replace", **kw)


class UsbUpdateTests(unittest.TestCase):
    def test_bundle_round_trip(self):
        javac = shutil.which("javac")
        if not javac or not shutil.which("openssl"):
            self.skipTest("JDK (javac on PATH) or openssl unavailable")
        bin_dir = Path(javac).parent; java = bin_dir / ("java" + EXE)
        spec = importlib.util.spec_from_file_location("usb_update", ROOT / "scripts/usb_update.py")
        usb = importlib.util.module_from_spec(spec); spec.loader.exec_module(usb)
        with tempfile.TemporaryDirectory(prefix="i30-usb-test-") as tmp:
            t = Path(tmp)
            # a throwaway update key and APK signer
            self.assertEqual(run("openssl", "genrsa", "-out", t / "update.pem", "2048").returncode, 0)
            der = run("openssl", "rsa", "-in", t / "update.pem", "-pubout", "-outform", "DER", "-out", t / "pub.der")
            self.assertEqual(der.returncode, 0, der.stderr)
            pub_hex = (t / "pub.der").read_bytes().hex()
            ks = t / "signer.p12"
            r = run(bin_dir / "keytool", "-genkeypair", "-keystore", ks, "-storetype", "PKCS12", "-storepass", "pass123", "-keypass", "pass123",
                    "-alias", "k", "-keyalg", "RSA", "-keysize", "2048", "-dname", "CN=test", "-validity", "2")
            self.assertEqual(r.returncode, 0, r.stderr)
            r = run(bin_dir / "keytool", "-exportcert", "-rfc", "-keystore", ks, "-storepass", "pass123", "-alias", "k", "-file", t / "cert.pem")
            self.assertEqual(r.returncode, 0, r.stderr)
            apks = []
            for name in ("dash", "hub"):
                apk = t / (name + ".apk")
                with zipfile.ZipFile(apk, "w") as z:
                    z.writestr("AndroidManifest.xml", b"\x03\x00\x08\x00" + name.encode())
                    z.writestr("classes.dex", b"dex\n035\x00" + bytes(64))
                    z.writestr("res/raw/x.bin", bytes(1000))
                r = run(bin_dir / "jarsigner", "-keystore", ks, "-storepass", "pass123", "-digestalg", "SHA-256", "-sigalg", "SHA256withRSA", apk, "k")
                self.assertEqual(r.returncode, 0, r.stderr)
                apks.append(apk)
            out = t / "i30-usb-update.zip"
            apps = [("com.veloster.dashboard", 6, "v1.20.3", apks[0]), ("com.veloster.carplayhub", 20, "1.19", apks[1])]
            release, text = usb.build(apps, t / "update.pem", t / "cert.pem", out)
            self.assertEqual(release, "v1.20.3")
            with zipfile.ZipFile(out) as z:
                self.assertEqual(sorted(z.namelist()), ["i30-update/dashboard.apk", "i30-update/hub.apk", "i30-update/manifest.sig", "i30-update/manifest.txt"])
                z.extractall(t / "stick")
            self.assertIn(b"product=pd_kr\nsdk=17\n", text)
            self.assertIn(b",dashboard.apk,v1.20.3\n", text)
            # My Apps' checks, compiled from the app source
            classes = t / "classes"
            (t / "UsbHarness.java").write_text(HARNESS)
            r = run(javac, "-encoding", "UTF-8", "-d", classes, ROOT / "apps/hub/src/com/veloster/carplayhub/usb/UsbBundle.java", t / "UsbHarness.java")
            self.assertEqual(r.returncode, 0, r.stderr)
            stick = t / "stick/i30-update"
            ok = run(java, "-cp", classes, "UsbHarness", stick, pub_hex)
            self.assertEqual(ok.returncode, 0, ok.stdout + ok.stderr)
            self.assertIn("OK " + release + " com.veloster.dashboard com.veloster.carplayhub", ok.stdout)
            # a different APK under the same name is refused (size / SHA-256)
            data = bytearray((stick / "hub.apk").read_bytes()); data[-30] ^= 1; (stick / "hub.apk").write_bytes(bytes(data))
            bad = run(java, "-cp", classes, "UsbHarness", stick, pub_hex)
            self.assertNotEqual(bad.returncode, 0)
            self.assertIn("APK changed", bad.stdout + bad.stderr)
            # a manifest edited after signing is refused
            (stick / "hub.apk").write_bytes(bytes(data[:-30]) + bytes([data[-30] ^ 1]) + bytes(data[-29:]))
            (stick / "manifest.txt").write_bytes((stick / "manifest.txt").read_bytes().replace(b"sdk=17", b"sdk=18"))
            bad = run(java, "-cp", classes, "UsbHarness", stick, pub_hex)
            self.assertIn("signature", bad.stdout + bad.stderr)

    def test_key_in_the_app_is_the_update_key(self):
        source = (ROOT / "apps/hub/src/com/veloster/carplayhub/usb/UsbUpdateKey.java").read_text(encoding="utf-8")
        import hashlib, re
        der = bytes.fromhex(re.search(r'DER = "([0-9a-f]+)"', source).group(1))
        self.assertEqual(hashlib.sha256(der).hexdigest(), "1bb3cb032be721b98726190ab29c28e430e0f817ae77712345a965692e2c3b29")   # this build's key, not upstream's
        self.assertIn(hashlib.sha256(der).hexdigest(), source)


if __name__ == "__main__":
    unittest.main()
