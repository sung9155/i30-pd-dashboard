import com.android.apksig.ApkSigner;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Builds the start-up notice 1 s SystemUI on the owner's own computer, from the owner's own car files, so no Hyundai
 * code is ever distributed: only this builder and the two hook classes (our code) are.
 *
 *   java -jar consent-fast-builder.jar <capture dir> <tools dir> <out apk>
 *
 * capture: HKMC_SystemUI.apk + HKMC_SystemUI.odex (/system/app) and framework/<BOOT>.odex (/system/framework).
 * tools:   baksmali.jar, smali.jar (2.5.2), platform.pk8 + platform.x509.pem (the public AOSP 4.2.2 test key the car's
 *          SystemUI is signed with); apksig.jar sits next to this jar (Class-Path).
 * One change: StatusBarService.onIntentReloadSettings() calls ConsentFast.hook(notice) right after
 * new ConsentScreen(context) (see ConsentFast.java); versionCode 17 -> 18 so the update survives the boot scan.
 * Exit: 0 built · 2 usage / missing file · 3 SystemUI build not known (stop, nothing made) · 4 code not where expected ·
 * 5 a tool failed.
 */
public final class ConsentFastBuilder {
    /** SystemUI builds this patch was checked on: name, sha256 of HKMC_SystemUI.apk, of HKMC_SystemUI.odex. */
    static final String[][] KNOWN = {
        {"V141", "9a5f71ba436c368ff98a14b6a1c978a593644e30ae5b51df2cb03e7537eb0f82", "2b4a2b62d9e24993f8f8dafd4dc876520b92c25a83b51309699db97b4fab7662"}};
    static final String[] BOOT = {"core", "core-junit", "bouncycastle", "ext", "framework", "telephony-common", "mms-common",
        "android.policy", "services", "apache-xml", "automotive"};   // BOOTCLASSPATH order of the car's init.rc
    static final String PLATFORM_CERT = "c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8";
    static final String TARGET = "com/android/systemui/statusbar/StatusBarService.smali";
    static final String ANCHOR = "    iput-object v0, p0, Lcom/android/systemui/statusbar/StatusBarService;->mConsentScreen:"
        + "Lcom/hkmc/internal/consentscreen/ConsentScreen;\n";
    static final String HOOK = "    invoke-static {v0}, Lcom/android/systemui/statusbar/ConsentFast;->hook(Ljava/lang/Object;)V\n";
    static final int VERSION_CODE = 18;   // stock 17: an update with the same number is dropped at the next boot scan
    static final String[] HOOK_FILES = {"ConsentFast.smali", "ConsentFast$Fast.smali"};

    public static void main(String[] a) {
        if (a.length != 3) { System.out.println("usage: <capture dir> <tools dir> <out apk>"); System.exit(2); }
        try { System.exit(build(new File(a[0]), new File(a[1]), new File(a[2]))); }
        catch (Exception e) { System.out.println("FAILED: " + e); System.exit(5); }
    }

    static int build(File cap, File tools, File out) throws Exception {
        File apk = new File(cap, "HKMC_SystemUI.apk"), odex = new File(cap, "HKMC_SystemUI.odex");
        for (File f : new File[]{apk, odex, new File(tools, "baksmali.jar"), new File(tools, "smali.jar"), new File(tools, "platform.pk8"), new File(tools, "platform.x509.pem")})
            if (!f.isFile()) { System.out.println("missing: " + f); return 2; }
        for (String b : BOOT) if (!new File(cap, "framework/" + b + ".odex").isFile()) { System.out.println("missing: framework/" + b + ".odex"); return 2; }
        String ha = sha(Files.readAllBytes(apk.toPath())), ho = sha(Files.readAllBytes(odex.toPath()));
        String build = null;
        for (String[] k : KNOWN) if (k[1].equals(ha) && k[2].equals(ho)) build = k[0];
        System.out.println("SystemUI apk " + ha.substring(0, 16) + " odex " + ho.substring(0, 16) + " -> " + (build == null ? "unknown" : build));
        if (build == null) { System.out.println("STOP: this SystemUI build was never checked. Nothing was made or changed."); return 3; }

        Path work = Files.createTempDirectory("consentfast");
        try { return make(cap, tools, out, odex, apk, build, work); } finally { delete(work.toFile()); }   // the work folder holds Hyundai code: never left behind
    }

    static int make(File cap, File tools, File out, File odex, File apk, String build, Path work) throws Exception {
        String java = new File(System.getProperty("java.home"), "bin/java").getPath();
        StringBuilder bcp = new StringBuilder();
        for (String b : BOOT) bcp.append(bcp.length() == 0 ? "" : ":").append(b).append(".odex");
        run(java, "-jar", new File(tools, "baksmali.jar").getPath(), "x", "-a", "17", "-d", new File(cap, "framework").getPath(),
            "-b", bcp.toString(), odex.getPath(), "-o", work.resolve("smali").toString());
        Path target = work.resolve("smali").resolve(TARGET);
        String s = new String(Files.readAllBytes(target), StandardCharsets.UTF_8).replace("\r\n", "\n");   // baksmali writes CRLF on Windows
        if (count(s, ANCHOR) != 1 || s.contains("ConsentFast")) { System.out.println("STOP: StatusBarService is not as expected."); return 4; }
        Files.write(target, s.replace(ANCHOR, ANCHOR + HOOK).getBytes(StandardCharsets.UTF_8));
        for (String h : HOOK_FILES) try (InputStream in = ConsentFastBuilder.class.getResourceAsStream("/hook/" + h)) {
            Files.copy(in, target.getParent().resolve(h)); }
        File dex = work.resolve("classes.dex").toFile();
        run(java, "-jar", new File(tools, "smali.jar").getPath(), "a", "-a", "17", "-o", dex.getPath(), work.resolve("smali").toString());

        File unsigned = work.resolve("unsigned.apk").toFile();
        try (ZipFile src = new ZipFile(apk); ZipOutputStream dst = new ZipOutputStream(new FileOutputStream(unsigned))) {
            if (src.getEntry("classes.dex") != null) { System.out.println("STOP: the stock apk already has classes.dex"); return 4; }
            List<? extends ZipEntry> entries = Collections.list((Enumeration<? extends ZipEntry>) src.entries());
            for (ZipEntry e : entries) {
                if (e.getName().toUpperCase().startsWith("META-INF/")) continue;
                byte[] data = readAll(src.getInputStream(e));
                if (e.getName().equals("AndroidManifest.xml")) {
                    int old = versionCode(data, VERSION_CODE);
                    System.out.println("versionCode " + old + " -> " + VERSION_CODE);
                    if (old >= VERSION_CODE) { System.out.println("STOP: stock versionCode is already " + old); return 4; }
                }
                put(dst, e.getName(), data, e.getMethod());
            }
            put(dst, "classes.dex", Files.readAllBytes(dex.toPath()), ZipEntry.DEFLATED);
        }
        KeyFactory kf = KeyFactory.getInstance("RSA");
        PrivateKey key = kf.generatePrivate(new PKCS8EncodedKeySpec(Files.readAllBytes(new File(tools, "platform.pk8").toPath())));
        X509Certificate cert;
        try (InputStream in = Files.newInputStream(new File(tools, "platform.x509.pem").toPath())) {
            cert = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(in); }
        if (!sha(cert.getEncoded()).equals(PLATFORM_CERT)) { System.out.println("STOP: platform.x509.pem is not the car's platform certificate"); return 2; }
        List<ApkSigner.SignerConfig> signers = new ArrayList<>();
        signers.add(new ApkSigner.SignerConfig.Builder("CERT", key, Collections.singletonList(cert)).build());
        out.getAbsoluteFile().getParentFile().mkdirs();
        File signed = work.resolve("signed.apk").toFile();
        new ApkSigner.Builder(signers).setInputApk(unsigned).setOutputApk(signed).setMinSdkVersion(17)
            .setV1SigningEnabled(true).setV2SigningEnabled(false).setV3SigningEnabled(false).setV4SigningEnabled(false).build().sign();
        align(signed, out);   // after signing, like zipalign after jarsigner: v1 signs entry contents, not their offsets
        System.out.println(out.getName() + ": " + out.length() + " B  sha256 " + sha(Files.readAllBytes(out.toPath())) + "  (" + build + ")");
        return 0;
    }

    /** Rewrites <manifest android:versionCode> in a binary AndroidManifest.xml in place; returns the old value. */
    static int versionCode(byte[] b, int code) {
        int o = 8; int[] ids = new int[0];
        while (o + 8 <= b.length) {
            int typ = u16(b, o), hsz = u16(b, o + 2), size = u32(b, o + 4);
            if (typ == 0x0180) { ids = new int[(size - hsz) / 4]; for (int i = 0; i < ids.length; i++) ids[i] = u32(b, o + hsz + 4 * i); }
            else if (typ == 0x0102) {   // first start tag = <manifest>
                int start = u16(b, o + 24), asz = u16(b, o + 26), cnt = u16(b, o + 28);
                for (int i = 0; i < cnt; i++) { int at = o + 16 + start + i * asz, name = u32(b, at + 4);
                    if (name >= 0 && name < ids.length && ids[name] == 0x0101021b) { int old = u32(b, at + 16);
                        for (int k = 0; k < 4; k++) b[at + 16 + k] = (byte) (code >>> (8 * k)); return old; } }
                break;
            }
            if (size <= 0) break; o += size;
        }
        throw new IllegalStateException("android:versionCode not found in the manifest");
    }
    static void delete(File f) { File[] k = f.listFiles(); if (k != null) for (File c : k) delete(c); f.delete(); }
    static int u16(byte[] b, int o) { return (b[o] & 255) | (b[o + 1] & 255) << 8; }
    static int u32(byte[] b, int o) { return u16(b, o) | u16(b, o + 2) << 16; }

    static void put(ZipOutputStream z, String name, byte[] data, int method) throws Exception {
        ZipEntry e = new ZipEntry(name);e.setMethod(method);e.setTime(1199145600000L);   // fixed DOS-range time: the same car files give the same apk
        if (method == ZipEntry.STORED) { CRC32 c = new CRC32(); c.update(data); e.setSize(data.length); e.setCompressedSize(data.length); e.setCrc(c.getValue()); }
        z.putNextEntry(e); z.write(data); z.closeEntry();
    }
    /** Copies a zip, padding each uncompressed entry's local header (extra field) so its data starts on 4 bytes, as
     *  zipalign -p 4 does: the stock SystemUI is aligned and 4.2 maps resources straight from the apk. */
    static void align(File in, File out) throws Exception {
        final long[] pos = {0};
        try (ZipFile src = new ZipFile(in); java.io.OutputStream raw = new FileOutputStream(out);
             ZipOutputStream dst = new ZipOutputStream(new java.io.FilterOutputStream(raw) {
                 @Override public void write(int b) throws java.io.IOException { raw.write(b); pos[0]++; }
                 @Override public void write(byte[] b, int o, int n) throws java.io.IOException { raw.write(b, o, n); pos[0] += n; } })) {
            for (ZipEntry e : Collections.list((Enumeration<? extends ZipEntry>) src.entries())) {
                byte[] data = readAll(src.getInputStream(e));
                ZipEntry n = new ZipEntry(e.getName());n.setMethod(e.getMethod());n.setTime(e.getTime());
                if (e.getMethod() == ZipEntry.STORED) { CRC32 c = new CRC32(); c.update(data); n.setSize(data.length); n.setCompressedSize(data.length); n.setCrc(c.getValue());
                    dst.flush();
                    int name = e.getName().getBytes(StandardCharsets.UTF_8).length, pad = (int) ((4 - (pos[0] + 30 + name + 6) % 4) % 4);
                    byte[] x = new byte[6 + pad];x[0] = 0x35;x[1] = (byte) 0xd9;x[2] = (byte) (2 + pad);x[4] = 4;   // 0xD935: Android alignment field
                    n.setExtra(x); }
                dst.putNextEntry(n); dst.write(data); dst.closeEntry();
            }
        }
    }
    static byte[] readAll(InputStream in) throws Exception {
        try (InputStream i = in) { ByteArrayOutputStream o = new ByteArrayOutputStream(); byte[] buf = new byte[65536]; int n;
            while ((n = i.read(buf)) > 0) o.write(buf, 0, n); return o.toByteArray(); }
    }
    static int count(String s, String sub) { int n = 0; for (int i = s.indexOf(sub); i >= 0; i = s.indexOf(sub, i + 1)) n++; return n; }
    static String sha(byte[] d) throws Exception {
        StringBuilder sb = new StringBuilder(); for (byte x : MessageDigest.getInstance("SHA-256").digest(d)) sb.append(String.format("%02x", x & 255)); return sb.toString(); }
    static void run(String... cmd) throws Exception {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String log = new String(readAll(p.getInputStream()), StandardCharsets.UTF_8);
        if (p.waitFor() != 0) throw new IllegalStateException(new File(cmd[2]).getName() + " failed: " + log.substring(Math.max(0, log.length() - 1500)));
    }
}
