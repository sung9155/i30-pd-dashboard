package com.i30.installer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

/**
 * The phone installer's steps, the same as install.ps1: get the APKs, find the car (pd_kr only),
 * check the build, push + pm install (a signature clash alone is fixed by uninstalling), open '내 앱'.
 * No Android imports: MainActivity supplies the USB link and the screen, the desktop test a TCP link.
 */
final class Flow {
    interface Ui { void say(String m); boolean ask(String q); }
    interface Car { Adb.Transport waitCar(Ui ui) throws Exception; }

    /** A failure the user should read as is (no stack trace). */
    static final class Stop extends Exception { Stop(String m) { super(m); } }

    static final String RELEASES = "https://github.com/sung9155/i30-pd-dashboard/releases";
    static final String VERIFIED_BUILD = "eng.vmadmin.20260114.084130", LOW_BUILD = "eng.vmadmin.20260114.084725";
    static final String[][] APPS = {
            {"dashboard-i30pd-platform.apk", "com.veloster.dashboard", "대시보드"},
            {"myapps-i30pd-platform.apk", "com.veloster.carplayhub", "'내 앱'"}};

    /** '내 앱' points the ☆ button here (persist.sys.key.custom); with the app gone ☆ would do nothing. */
    static final String STAR = "com.veloster.carplayhub.STAR_KEY", STAR_NONE = "com.mobis.intent.action.CUSTOM_SHORTCUT";

    static String base(boolean test) { return test ? RELEASES + "/download/test" : RELEASES + "/latest/download"; }

    static void run(String base, boolean uninstall, File dir, Car car, Ui ui) throws Exception {
        File[] apk = new File[APPS.length];
        if (!uninstall) {
            ui.say("■ 앱 파일 받기");
            for (int i = 0; i < APPS.length; i++) apk[i] = download(base + "/" + APPS[i][0], new File(dir, APPS[i][0]), ui);
        }

        ui.say("\n■ 차 화면에서 먼저 (직접)");
        ui.say("  (1) 설정 → 시스템 정보 → 저장 공간 화면");
        ui.say("  (2) 내용 영역 좌하단 모서리 5회 탭 → 우하단 모서리 1회 탭 (엔지니어 모드)");
        ui.say("  (3) 암호 입력 → Diagnosis → Factory Test 에서  0A 01 → 65 01 → 0A 02  전송");
        ui.say("  (4) 폰 — OTG 어댑터(USB-C→A 암) — A-A 케이블 — 차 USB 포트 연결");

        Adb adb = new Adb(car.waitCar(ui));
        try {
            ui.say("\n■ 차와 adb 연결");
            adb.connect(key(dir), 180_000, () -> ui.say("→ 차 화면에 \"USB 디버깅 허용\" 창이 뜨면 [항상 허용]을 누르세요. (3분 기다립니다)"));
            String product = prop(adb, "ro.product.name");
            if (!product.equals("pd_kr")) throw new Stop("i30 PD 내비(pd_kr)가 아닙니다: " + product + "\n이 기기에는 아무것도 하지 않았습니다.");
            String build = prop(adb, "ro.build.version.incremental"), device = prop(adb, "ro.product.device");
            String board = device.contains("daudiopluslow") ? "D-Audio Plus LOW (블루링크 없음)"
                    : device.contains("daudioplus") ? "D-Audio Plus (블루링크)" : "확인 안 됨 (" + device + ")";
            ui.say("차 연결됨: " + board + " / 빌드 " + build);
            if (!build.isEmpty() && !build.equals(VERIFIED_BUILD) && !build.equals(LOW_BUILD)
                    && !ui.ask("검증된 빌드(" + VERIFIED_BUILD + ")와 다릅니다. 값이 틀릴 수 있습니다. 그래도 계속할까요?"))
                throw new Stop("사용자 취소.");

            if (uninstall) {
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
    }

    private static void install(Adb adb, String[] app, File apk, Ui ui) throws Exception {
        String remote = "/data/local/tmp/" + app[0];
        ui.say(app[2] + " (" + apk.length() / 1024 + " KB)");
        ui.say("  ① 전송 (USB)...");
        try { adb.push(apk, remote, 60_000); }
        catch (IOException e) { throw new Stop(app[2] + " 전송 실패: " + e.getMessage() + "\n케이블·OTG 어댑터를 확인하고 Factory Test부터 다시 하세요."); }
        ui.say("  ② 설치 (내비)...");
        String o = adb.shell("pm install -r " + remote, 120_000);
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

    private static String prop(Adb adb, String k) throws IOException { return adb.shell("getprop " + k, 15_000).trim(); }

    private static File download(String url, File dst, Ui ui) throws Exception {
        try {
            URLConnection c = new URL(url).openConnection();
            c.setConnectTimeout(15_000);
            c.setReadTimeout(30_000);
            try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(dst)) {
                byte[] b = new byte[16 * 1024];
                for (int n; (n = in.read(b)) > 0; ) out.write(b, 0, n);
            }
        } catch (IOException e) {
            throw new Stop("APK 다운로드 실패: " + dst.getName() + "\n인터넷 연결을 확인하세요.\n" + url + "\n" + e);
        }
        // An APK is a ZIP ('PK'); this rejects a saved 404 page.
        byte[] magic = new byte[2];
        try (InputStream in = new FileInputStream(dst)) { if (in.read(magic) != 2) magic[0] = 0; }
        if (magic[0] != 'P' || magic[1] != 'K') throw new Stop("받은 파일이 APK가 아닙니다: " + dst.getName() + "\n" + url);
        ui.say(dst.getName() + ": 받음 (" + dst.length() / 1024 + " KB)");
        return dst;
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
