package com.i30.installer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The phone installer's steps, the same as install.ps1: get the APKs, find the car (pd_kr only),
 * check the build, push + pm install (a signature clash alone is fixed by uninstalling), open '내 앱'.
 * Also removes both apps, or only reads the car for a diagnostics file to mail.
 * No Android imports: MainActivity supplies the USB link and the screen, the desktop test a TCP link.
 */
final class Flow {
    interface Ui {
        void say(String m);
        boolean ask(String q);
        /** One line that shows how long this wait has lasted; null ends it. */
        default void waiting(String label) {}
        default boolean cancelled() { return false; }
    }
    interface Car { Adb.Transport waitCar(Ui ui) throws Exception; }
    enum Mode { INSTALL, UNINSTALL, DIAG }

    /** A failure the user should read as is (no stack trace). */
    static final class Stop extends Exception { Stop(String m) { super(m); } }

    static final String VERSION = "2026-11-03";   // shown on screen and compared with the published source; raise on every change
    static final String RELEASES = "https://github.com/sung9155/i30-pd-dashboard/releases";
    static final String RAW = "https://raw.githubusercontent.com/sung9155/i30-pd-dashboard/";
    static final String VERIFIED_BUILD = "eng.vmadmin.20260114.084130", LOW_BUILD = "eng.vmadmin.20260114.084725",
        OLD_BUILD = "eng.vmadmin.20240205.105401";   // Blue Link D-Audio Plus, 2024-02 build: installed and values checked in the same model 2026-11-03
    static final String[][] APPS = {
            {"dashboard-i30pd-platform.apk", "com.veloster.dashboard", "대시보드"},
            {"myapps-i30pd-platform.apk", "com.veloster.carplayhub", "'내 앱'"}};
    /** '내 앱' points the ☆ button here (persist.sys.key.custom); with the app gone ☆ would do nothing. */
    static final String STAR = "com.veloster.carplayhub.STAR_KEY", STAR_NONE = "com.mobis.intent.action.CUSTOM_SHORTCUT";
    /** Only these properties: a full getprop has the serial and Bluetooth addresses. */
    static final String[] PROPS = {"ro.product.name", "ro.product.device", "ro.build.product", "ro.build.version.incremental",
            "ro.build.fingerprint", "sys.usb.config", "sys.usb.state", "persist.sys.usb.config", "persist.sys.dipo.state", "persist.sys.key.custom"};
    /** Our apps' tags, crashes and installs only: the whole log has phone names, numbers and contacts from Bluetooth. */
    static final String LOG_TAGS = "VehicleDash:V VNCarPlayHub:V VNQuickEntry:V NDashboard:V DtcDialog:V AndroidRuntime:E PackageManager:I";

    static String base(boolean test) { return test ? RELEASES + "/download/test" : RELEASES + "/latest/download"; }

    /** Returns the diagnostics file in DIAG mode, otherwise null. */
    static File run(String base, Mode mode, File dir, Car car, Ui ui) throws Exception {
        File[] apk = new File[APPS.length];
        if (mode == Mode.INSTALL) {
            ui.say("■ 앱 파일 받기");
            for (int i = 0; i < APPS.length; i++) apk[i] = fetch(base, APPS[i][0], dir, ui);
        }

        ui.say("\n■ 차 화면에서 먼저 (직접)");
        ui.say("  (1) 설정 → 시스템 정보 → 저장 공간 화면");
        ui.say("  (2) 내용 영역 좌하단 모서리 5회 탭 → 우하단 모서리 1회 탭 (엔지니어 모드)");
        ui.say("  (3) 암호 입력 → Diagnosis → Factory Test 에서  0A 01 → 65 01 → 0A 02  전송");
        ui.say("      (이미 설치돼 있으면 '내 앱' → USB 디버깅 → ON 으로 대신할 수 있습니다)");
        ui.say("  (4) 폰 — OTG 어댑터(USB-C→A 암) — A-A 케이블 — 차 USB 포트 연결");

        Adb adb = new Adb(car.waitCar(ui));
        try {
            ui.say("\n■ 차와 adb 연결");
            ui.waiting("차와 연결 중");
            adb.connect(key(dir), 180_000, () -> {
                ui.say("→ 차 화면에 \"USB 디버깅 허용\" 창이 뜨면 [항상 허용]을 누르세요.");
                ui.waiting("차 화면의 허용을 기다리는 중 (최대 3분)");
            });
            ui.waiting(null);
            String product = prop(adb, "ro.product.name");
            if (!product.equals("pd_kr")) throw new Stop("i30 PD 내비(pd_kr)가 아닙니다: " + product + "\n이 기기에는 아무것도 하지 않았습니다.");
            String build = prop(adb, "ro.build.version.incremental"), device = prop(adb, "ro.product.device");
            String board = device.contains("daudiopluslow") ? "D-Audio Plus LOW (블루링크 없음)"
                    : device.contains("daudioplus") ? "D-Audio Plus (블루링크)" : "확인 안 됨 (" + device + ")";
            ui.say("차 연결됨: " + board + " / 빌드 " + build);
            if (mode == Mode.DIAG) return diag(adb, dir, ui);
            if (!build.isEmpty() && !build.equals(VERIFIED_BUILD) && !build.equals(LOW_BUILD) && !build.equals(OLD_BUILD)
                    && !ui.ask("검증된 빌드(" + VERIFIED_BUILD + ")와 다릅니다. 값이 틀릴 수 있습니다. 그래도 계속할까요?"))
                throw new Stop("사용자 취소.");

            if (mode == Mode.UNINSTALL) {
                ui.say("\n■ 앱 삭제");
                if (prop(adb, "persist.sys.key.custom").equals(STAR)) {
                    // IModeManager.setCustomKey (transaction 34): the same call '내 앱' makes, no permission on the service side.
                    adb.shell("service call com.hkmc.micom.mode 34 s16 " + STAR_NONE, 15_000);
                    ui.say(prop(adb, "persist.sys.key.custom").equals(STAR)
                            ? "☆ 버튼: 되돌리지 못했습니다. 삭제 뒤 ☆를 길게 눌러 설정 화면에서 기능을 다시 고르세요."
                            : "☆ 버튼: 원래대로(없음) 되돌림");
                }
                for (String[] app : APPS) {
                    String o = adb.shell("pm uninstall " + app[1], 60_000);
                    if (o.contains("Success")) { ui.say(app[2] + ": 삭제됨"); continue; }
                    if (adb.shell("pm path " + app[1], 30_000).contains("package:"))
                        throw new Stop(app[2] + " 삭제 실패 (앱이 아직 차에 있습니다):\n" + o.trim());
                    ui.say(app[2] + ": 설치돼 있지 않음");
                }
                ui.say("\n삭제 완료. 순정 상태로 돌아갔습니다.");
            } else {
                ui.say("\n■ 앱 설치");
                for (int i = 0; i < APPS.length; i++) install(adb, APPS[i], apk[i], ui);
                adb.shell("am start -n com.veloster.carplayhub/.HubActivity", 30_000);
                ui.say("\n완료! 차 화면 '내 앱'에서  실행 바로가기 → 가장자리 버튼 + 알림 → 저장  을 고르면");
                ui.say("가장자리 '계기판' 버튼으로 대시보드가 열립니다.");
            }
            ui.say("USB 디버깅은 시동을 껐다 켜면 저절로 꺼집니다. 바로 끄려면 Factory Test에서  0A 01 → 65 00 → 0A 02");
        } finally {
            adb.close();
        }
        return null;
    }

    private static void install(Adb adb, String[] app, File apk, Ui ui) throws Exception {
        // The car keeps the installed APK byte for byte, so the same bytes mean the same version: nothing to do.
        String path = adb.shell("pm path " + app[1], 30_000).trim();
        if (path.startsWith("package:") && path.endsWith(".apk")) {
            try {
                if (Arrays.equals(adb.pull(path.substring(8), 60_000), Files.readAllBytes(apk.toPath()))) {
                    ui.say(app[2] + ": 이미 같은 버전이 설치돼 있습니다 — 건너뜀");
                    return;
                }
            } catch (IOException e) { ui.say("  (설치된 파일과 비교하지 못해 그대로 설치합니다: " + e.getMessage() + ")"); }
        }
        String remote = "/data/local/tmp/" + app[0];
        ui.say(app[2] + " (" + apk.length() / 1024 + " KB)");
        ui.say("  ① 전송 (USB)");
        ui.waiting("① 전송 중 (최대 60초)");
        try { adb.push(apk, remote, 60_000); }
        catch (IOException e) { if (ui.cancelled()) throw e; throw new Stop(app[2] + " 전송 실패: " + e.getMessage() + "\n케이블·OTG 어댑터를 확인하고 Factory Test부터 다시 하세요."); }
        ui.say("  ② 설치 (내비)");
        ui.waiting("② 설치 중 (최대 2분)");
        String o = adb.shell("pm install -r " + remote, 120_000);
        ui.waiting(null);
        // Only a signature clash is fixed by uninstalling; any other failure must not remove the working app.
        if (o.contains("INCONSISTENT_CERTIFICATES") || o.contains("UPDATE_INCOMPATIBLE")) {
            ui.say(app[2] + ": 기존에 다른 서명으로 설치돼 있습니다.");
            if (ui.ask(app[2] + "이(가) 다른 서명으로 설치돼 있습니다. 지우고 다시 설치할까요? (앱 설정도 지워집니다)")) {
                String u = adb.shell("pm uninstall " + app[1], 60_000);
                if (!u.contains("Success")) throw new Stop(app[2] + " 기존 앱 삭제 실패:\n" + u.trim());
                o = adb.shell("pm install -r " + remote, 120_000);
            }
        }
        adb.shell("rm -f " + remote, 15_000);
        if (!o.contains("Success")) throw new Stop(app[2] + " 설치 실패:\n" + o.trim());
        ui.say(app[2] + " 설치 완료");
    }

    /** Reads only: fixed properties, our two packages' versions, our apps' log. Masked, then saved under dir/diag. */
    private static File diag(Adb adb, File dir, Ui ui) throws Exception {
        ui.say("\n■ 진단 기록 모으기 (차에는 아무것도 바꾸지 않습니다)");
        StringBuilder b = new StringBuilder("i30 PD 진단 기록 — " + stamp("yyyy-MM-dd HH:mm") + " / 휴대폰 설치 앱 " + VERSION + "\n\n[내비]\n");
        for (String k : PROPS) b.append(k).append(": ").append(prop(adb, k)).append('\n');
        b.append("시각: ").append(adb.shell("date", 15_000).trim()).append("\n\n[앱]\n");
        for (String[] app : APPS) {
            String v = versionLines(adb.shell("dumpsys package " + app[1], 30_000));
            b.append(app[2]).append(" (").append(app[1]).append(")\n").append(v.isEmpty() ? "  설치돼 있지 않음\n" : v);
        }
        ui.waiting("기록 읽는 중");
        b.append("\n[기록: ").append(LOG_TAGS).append("]\n").append(tail(adb.shell("logcat -d -v time -s " + LOG_TAGS, 60_000), 3000));
        ui.waiting(null);
        File out = new File(new File(dir, "diag"), "i30pd_diag_" + stamp("yyyyMMdd_HHmmss") + ".txt");
        if (!out.getParentFile().isDirectory() && !out.getParentFile().mkdirs()) throw new IOException("저장 폴더를 만들지 못했습니다");
        Files.write(out.toPath(), mask(b.toString()).getBytes(StandardCharsets.UTF_8));
        ui.say("진단 기록을 만들었습니다 (" + Math.max(1, out.length() / 1024) + " KB).");
        return out;
    }

    static String versionLines(String dumpsys) {
        StringBuilder b = new StringBuilder();
        for (String l : dumpsys.split("\n"))
            if (l.matches(".*(versionCode|versionName|firstInstallTime|lastUpdateTime|codePath)=.*") && b.indexOf(l.trim()) < 0)
                b.append("  ").append(l.trim()).append('\n');
        return b.toString();
    }

    static String tail(String text, int lines) {
        String[] l = text.split("\n");
        return String.join("\n", Arrays.copyOfRange(l, Math.max(0, l.length - lines), l.length)) + "\n";
    }

    /** What could still identify a person in our own log lines: MAC addresses, Korean mobile numbers, mail addresses. */
    static String mask(String t) {
        return t.replaceAll("\\b(?:[0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}\\b", "<MAC>")
                .replaceAll("\\b01[016789][-. ]?\\d{3,4}[-. ]?\\d{4}\\b", "<전화번호>")
                .replaceAll("[\\w.+-]+@[\\w-]+(?:\\.[\\w-]+)+", "<메일>");
    }

    private static String stamp(String f) { return new SimpleDateFormat(f, Locale.US).format(new Date()); }

    private static String prop(Adb adb, String k) throws IOException { return adb.shell("getprop " + k, 15_000).trim(); }

    /** Downloads into a per-channel folder and keeps the file, so a weak signal in the car can fall back to it. */
    private static File fetch(String base, String name, File dir, Ui ui) throws Exception {
        File cache = new File(dir, base.endsWith("/test") ? "test" : "stable");
        if (!cache.isDirectory() && !cache.mkdirs()) throw new IOException("저장 폴더를 만들지 못했습니다");
        File dst = new File(cache, name), part = new File(cache, name + ".part");
        try {
            download(base + "/" + name, part, ui);
        } catch (Stop e) {
            if (!dst.isFile() || ui.cancelled()) throw e;
            ui.say(e.getMessage());
            if (!ui.ask(name + "을(를) 받지 못했습니다. 전에 받아 둔 파일(" + new SimpleDateFormat("M월 d일 HH:mm", Locale.KOREA).format(new Date(dst.lastModified())) + ")로 계속할까요?"))
                throw e;
            ui.say(name + ": 전에 받아 둔 파일을 씁니다");
            return dst;
        }
        Files.move(part.toPath(), dst.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return dst;
    }

    private static void download(String url, File dst, Ui ui) throws Exception {
        try {
            URLConnection c = new URL(url).openConnection();
            c.setConnectTimeout(15_000);
            c.setReadTimeout(30_000);
            try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(dst)) {
                byte[] b = new byte[16 * 1024];
                for (int n; (n = in.read(b)) > 0; ) { if (ui.cancelled()) throw new Stop("취소했습니다."); out.write(b, 0, n); }
            }
        } catch (IOException e) {
            throw new Stop("APK 다운로드 실패: " + dst.getName().replace(".part", "") + "\n인터넷 연결을 확인하세요.\n" + url + "\n" + e);
        }
        // An APK is a ZIP ('PK'); this rejects a saved 404 page.
        byte[] magic = new byte[2];
        try (InputStream in = new FileInputStream(dst)) { if (in.read(magic) != 2) magic[0] = 0; }
        if (magic[0] != 'P' || magic[1] != 'K') throw new Stop("받은 파일이 APK가 아닙니다: " + url);
        ui.say(url.substring(url.lastIndexOf('/') + 1) + ": 받음 (" + dst.length() / 1024 + " KB)");
    }

    /** A line about a newer phone installer on main (stable) or beta (test); null when none or offline. */
    static String newer() {
        StringBuilder b = new StringBuilder();
        for (String branch : new String[]{"main", "beta"}) {
            try {
                URLConnection c = new URL(RAW + branch + "/i30pd_port/installer/android/src/com/i30/installer/Flow.java").openConnection();
                c.setConnectTimeout(5_000);
                c.setReadTimeout(5_000);
                ByteArrayOutputStream o = new ByteArrayOutputStream();
                try (InputStream in = c.getInputStream()) { byte[] buf = new byte[8192]; for (int n; (n = in.read(buf)) > 0; ) o.write(buf, 0, n); }
                Matcher m = Pattern.compile("VERSION = \"([^\"]+)\"").matcher(o.toString("UTF-8"));
                if (m.find() && m.group(1).compareTo(VERSION) > 0)
                    b.append("새 휴대폰 설치 앱이 있습니다 (").append(branch.equals("main") ? "안정화 " : "테스트 ").append(m.group(1)).append("): ")
                            .append(RELEASES).append(branch.equals("main") ? "/latest" : "/tag/test").append(" 에서 i30pd-installer-android.apk를 다시 받으세요.\n");
            } catch (IOException ignored) { /* offline or not published on that branch yet */ }
        }
        return b.length() == 0 ? null : b.toString().trim();
    }

    /** This phone's adb key, kept so the car's "항상 허용" holds next time. */
    static KeyPair key(File dir) throws Exception {
        File priv = new File(dir, "adbkey"), pub = new File(dir, "adbkey.pub");
        KeyFactory f = KeyFactory.getInstance("RSA");
        if (priv.isFile() && pub.isFile())
            return new KeyPair(f.generatePublic(new X509EncodedKeySpec(Files.readAllBytes(pub.toPath()))),
                    f.generatePrivate(new PKCS8EncodedKeySpec(Files.readAllBytes(priv.toPath()))));
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        KeyPair k = g.generateKeyPair();
        Files.write(priv.toPath(), k.getPrivate().getEncoded());
        Files.write(pub.toPath(), k.getPublic().getEncoded());
        return k;
    }
}
