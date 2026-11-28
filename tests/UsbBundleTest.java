import com.veloster.carplayhub.usb.UsbBundle;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;

/** USB 업데이트: only a manifest signed with the update key, in the exact format, for this head unit, is accepted. */
public class UsbBundleTest {
    static void check(boolean v, String message) { if (!v) throw new AssertionError(message); }
    static final String SIGNER = "ab".repeat(32), D = "cd".repeat(32), H = "ef".repeat(32);

    static byte[] sign(PrivateKey k, byte[] data) throws Exception { Signature s = Signature.getInstance("SHA256withRSA"); s.initSign(k); s.update(data); return s.sign(); }
    static byte[] text(String... lines) { return (String.join("\n", lines) + "\n").getBytes(StandardCharsets.US_ASCII); }
    static byte[] good(String... apps) {
        String[] head = {"I30USB1", "release=v1.20.3", "product=pd_kr", "sdk=17", "signer=" + SIGNER};
        String[] all = new String[head.length + apps.length]; System.arraycopy(head, 0, all, 0, head.length); System.arraycopy(apps, 0, all, head.length, apps.length);
        return text(all);
    }
    static String rejected(KeyPair kp, byte[] data) {
        try { UsbBundle.verify(data, sign(kp.getPrivate(), data), kp.getPublic()); return null; } catch (Exception e) { return e.getMessage(); }
    }

    public static void main(String[] args) throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA"); g.initialize(2048);
        KeyPair kp = g.generateKeyPair(), other = g.generateKeyPair();
        String dash = "app=com.veloster.dashboard,6,20000000," + D + ",dashboard.apk,v1.20.3", hub = "app=com.veloster.carplayhub,20,90000," + H + ",hub.apk,1.19";
        byte[] m = good(hub, dash);
        UsbBundle b = UsbBundle.verify(m, sign(kp.getPrivate(), m), kp.getPublic());
        check(b.release.equals("v1.20.3") && b.signer.equals(SIGNER) && b.apps.size() == 2, "a good manifest reads");
        check(b.apps.get(0).packageName.equals(UsbBundle.DASHBOARD) && b.apps.get(1).packageName.equals(UsbBundle.HUB), "the dashboard installs first, My Apps (the installer itself) last");
        check(b.apps.get(0).version == 6 && b.apps.get(0).bytes == 20000000 && b.apps.get(0).file.equals("dashboard.apk") && b.apps.get(0).name.equals("v1.20.3") && b.apps.get(1).name.equals("1.19"), "app fields");
        b.checkDevice("pd_kr", 17);
        try { b.checkDevice("jsn_kr", 17); check(false, "another head unit (the Veloster N) refused"); } catch (java.io.IOException expected) { }
        try { b.checkDevice("pd_kr", 19); check(false, "another API refused"); } catch (java.io.IOException expected) { }
        // the i30 PD build keeps versionCode between releases: the build name decides
        UsbBundle.App d = b.apps.get(0), h = b.apps.get(1);
        check(UsbBundle.newer(d, 6, "v1.20.2") && UsbBundle.newer(d, 6, "v1.9.9") && !UsbBundle.newer(d, 6, "v1.20.3") && !UsbBundle.newer(d, 6, "v1.20.4") && !UsbBundle.newer(d, 6, "v1.21.0"), "the same versionCode: only a later release");
        check(UsbBundle.newer(d, 6, "test-1128-abc1234") && UsbBundle.newer(d, 6, "1.5-i18n") && UsbBundle.newer(d, 6, null), "over a test build: offered");
        check(UsbBundle.newer(d, 5, "v1.30.0") && !UsbBundle.newer(d, 7, "v1.0.0") && !UsbBundle.newer(d, 0, null), "a higher versionCode wins, a lower one and not installed are refused");
        check(UsbBundle.newer(h, 19, "1.18") && !UsbBundle.newer(h, 20, "1.19"), "My Apps by its versionCode");
        // the signature
        try { UsbBundle.verify(m, sign(other.getPrivate(), m), kp.getPublic()); check(false, "another key refused"); } catch (java.io.IOException e) { check(e.getMessage().contains("signature"), e.getMessage()); }
        byte[] changed = m.clone(); changed[20] ^= 1;
        try { UsbBundle.verify(changed, sign(kp.getPrivate(), m), kp.getPublic()); check(false, "a changed manifest refused"); } catch (java.io.IOException expected) { }
        try { UsbBundle.verify(m, new byte[255], kp.getPublic()); check(false, "short signature refused"); } catch (java.io.IOException expected) { }
        // the format (signed, but wrong)
        check(rejected(kp, good(dash)) == null, "one app alone is fine");
        check(rejected(kp, good()) != null, "no app refused");
        check(rejected(kp, good(dash, dash)) != null, "the same app twice refused");
        check(rejected(kp, good("app=com.evil.app,1,10," + D + ",x.apk,v1.0.0")) != null, "another package refused");
        check(rejected(kp, good("app=com.veloster.dashboard,6,10," + D + ",../x.apk,v1.20.3")) != null, "a path in the file name refused");
        check(rejected(kp, good("app=com.veloster.dashboard,6,99999999," + D + ",d.apk,v1.20.3")) != null, "an oversized APK refused");
        check(rejected(kp, good("app=com.veloster.dashboard,06,10," + D + ",d.apk,v1.20.3")) != null, "a padded number refused");
        check(rejected(kp, good("app=com.veloster.dashboard,6,10," + D + ",d.apk")) != null, "an app line without its version name (upstream's format) refused");
        check(rejected(kp, good("app=com.veloster.dashboard,6,10," + D + ",d.apk,v1 20")) != null, "a version name with a space refused");
        check(rejected(kp, text("VNUSB1", "release=v1.20.3", "product=pd_kr", "sdk=17", "signer=" + SIGNER, dash)) != null, "upstream's format name refused");
        check(rejected(kp, text("I30USB1", "product=pd_kr", "release=v1.20.3", "sdk=17", "signer=" + SIGNER, dash)) != null, "fields out of order refused");
        byte[] crlf = new String(good(dash), StandardCharsets.US_ASCII).replace("\n", "\r\n").getBytes(StandardCharsets.US_ASCII);
        check(rejected(kp, crlf) != null, "CR LF refused");
        System.out.println("PASS: USB update manifest - signature, format, order, device and version checks");
    }
}
