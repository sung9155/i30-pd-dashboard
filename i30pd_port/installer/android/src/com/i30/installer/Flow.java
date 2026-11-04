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
 * Also removes both apps, or only reads the car: a diagnostics file, or a drive's raw hscan recording to share.
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
    enum Mode { INSTALL, UNINSTALL, DIAG, RECORD }

    /** A failure the user should read as is (no stack trace). */
    static final class Stop extends Exception { Stop(String m) { super(m); } }

    static final String VERSION = "2026-11-04c";   // shown on screen and compared with the published source; raise on every change
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

    /** Drive recording: the receive-only hscan dumper (i30pd_port/src/com/i30/diag/HscanDump.java), shipped inside this APK. */
    static final String DUMP = "/data/local/tmp/hscandump.jar";
    static final int RECORD_SECS = 60 * 60;   // [기록 끝] ends it sooner; about 1.2 MB of text a minute, ~13x smaller zipped
    static final String[] MISSIONS = {
            "폰은 거치대에 두고 화면을 켜 둔 채 평소처럼 운전하세요. 아래는 정차 중에만, 순서는 자유:",
            "  1. 정차 중 AUTO HOLD 버튼 3번 껐다 켜기",
            "  2. 오토홀드 켠 채 평지 정차 → 브레이크 떼고 5초 → 출발",
            "  3. 오토홀드 끈 채 오르막 정차 → 브레이크 떼고 출발",
            "  4. 전자식 주차 브레이크 채운 채 오르막에서 출발",
            "  5. 신호 대기 중 기어 중립 + 클러치 뗌",
            "  6. 주차하며 후진",
            "다 했으면 [기록 끝] → 공유로 보내 주세요."};

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

        Adb adb = connect(car, dir, ui);
        try {
            String build = prop(adb, "ro.build.version.incremental");
            if (mode == Mode.DIAG) return diag(adb, dir, ui);
            if (mode == Mode.RECORD) return record(adb, dir, car, ui);
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

    /** USB, adb auth, and pd_kr only. The link is closed again when any of it fails. */
    private static Adb connect(Car car, File dir, Ui ui) throws Exception {
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
            String device = prop(adb, "ro.product.device");
            String board = device.contains("daudiopluslow") ? "D-Audio Plus LOW (블루링크 없음)"
                    : device.contains("daudioplus") ? "D-Audio Plus (블루링크)" : "확인 안 됨 (" + device + ")";
            ui.say("차 연결됨: " + board + " / 빌드 " + prop(adb, "ro.build.version.incremental"));
            return adb;
        } catch (Exception e) {
            adb.close();
            throw e;
        }
    }

    /**
     * The raw hscan stream (receive only, nothing is sent to the car) into one text file for up to RECORD_SECS,
     * reconnecting when the link drops, then zipped for sharing. The driver taps nothing on the way: the scenes
     * are found later from the signals already decoded (standstill, brake, slope, reverse camera).
     */
    private static File record(Adb adb, File dir, Car car, Ui ui) throws Exception {
        File jar = new File(dir, "hscandump.jar");
        if (!jar.isFile()) throw new Stop("기록 도구(hscandump.jar)가 앱 안에 없습니다. 휴대폰 설치 앱을 다시 받아 설치하세요.");
        File txt = new File(new File(dir, "diag"), "i30pd_drive_" + stamp("yyyyMMdd_HHmmss") + ".txt");
        if (!txt.getParentFile().isDirectory() && !txt.getParentFile().mkdirs()) throw new IOException("저장 폴더를 만들지 못했습니다");
        ui.say("\n■ 주행 기록 (차에는 아무것도 보내지 않고 읽기만 합니다, 최대 " + RECORD_SECS / 60 + "분)");
        for (String m : MISSIONS) ui.say(m);
        long end = System.currentTimeMillis() + RECORD_SECS * 1000L;
        try (OutputStream o = new java.io.BufferedOutputStream(new FileOutputStream(txt))) {
            o.write(("# DRIVE 휴대폰 설치 앱 " + VERSION + " / 빌드 " + prop(adb, "ro.build.version.incremental")
                    + " / 시작 " + stamp("yyyy-MM-dd HH:mm:ss") + "\n").getBytes(StandardCharsets.UTF_8));
            for (int drops = 0; ; ) {
                long left = (end - System.currentTimeMillis()) / 1000;
                if (left < 5 || ui.cancelled()) break;
                try {
                    adb.push(jar, DUMP, 60_000);
                    ui.waiting("주행 기록 중 — 다 했으면 [기록 끝]");
                    adb.shell("mkdir -p /data/local/tmp/dalvik-cache; cd /data/local/tmp; ANDROID_DATA=/data/local/tmp CLASSPATH=" + DUMP
                            + " app_process /data/local/tmp com.i30.diag.HscanDump " + left + " raw", o, 90_000);
                    break;   // the dump ran its time out (or found no hscan: ignition off)
                } catch (IOException e) {
                    if (ui.cancelled()) break;
                    o.write(("\n# DROP " + stamp("HH:mm:ss") + " " + e.getMessage() + "\n").getBytes(StandardCharsets.UTF_8));
                    o.flush();
                    adb.close();
                    if (++drops > 5) { ui.say("\n연결이 계속 끊겨 기록을 마칩니다."); break; }
                    ui.say("\n연결이 끊겼습니다 (" + e.getMessage() + "). 다시 연결합니다 — 시동과 케이블을 그대로 두세요.");
                    try { adb = connect(car, dir, ui); }
                    catch (Exception x) { ui.say("다시 연결하지 못했습니다: " + x.getMessage()); break; }
                }
            }
        } finally {
            ui.waiting(null);
            adb.close();   // run() closes the first link again: harmless
        }
        File zip = new File(txt.getParentFile(), txt.getName().replace(".txt", ".zip"));
        try (java.util.zip.ZipOutputStream z = new java.util.zip.ZipOutputStream(new FileOutputStream(zip))) {
            z.putNextEntry(new java.util.zip.ZipEntry(txt.getName()));
            Files.copy(txt.toPath(), z);
        }
        ui.say("\n주행 기록을 만들었습니다 (원본 " + txt.length() / 1024 + " KB → zip " + Math.max(1, zip.length() / 1024) + " KB).");
        if (!txt.delete()) txt.deleteOnExit();
        return zip;
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

    static final String API = "https://api.github.com/repos/sung9155/i30-pd-dashboard/releases/";
    /** What a channel installs, for the screen: {"v1.8.2 · 2026-11-04", summary} for stable, {test title " · 갱신 " upload
     *  date, summary} for test; null when offline. Same rules as the PC installers (install.ps1, 설치.command). */
    static String[] release(boolean test, int heads) {
        try {
            URLConnection c = new URL(API + (test ? "tags/test" : "latest")).openConnection();
            c.setConnectTimeout(8_000);
            c.setReadTimeout(8_000);
            c.setRequestProperty("User-Agent", "i30pd-installer");
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            try (InputStream in = c.getInputStream()) { byte[] buf = new byte[8192]; for (int n; (n = in.read(buf)) > 0; ) o.write(buf, 0, n); }
            return describe(o.toString("UTF-8"), test, heads);
        } catch (IOException e) { return null; }
    }
    static String[] describe(String json, boolean test, int heads) {
        String tag = field(json, "tag_name", 0), name = field(json, "name", json.indexOf("\"tag_name\"")), body = field(json, "body", 0);
        String date = field(json, "published_at", 0);
        int dash = json.indexOf("\"dashboard-i30pd-platform.apk\"");
        if (test && dash >= 0) { String up = field(json, "updated_at", dash); if (up != null) date = up; }   // test: when its APK was last replaced
        date = date == null ? "" : date.substring(0, Math.min(10, date.length()));
        String title = test ? (name == null ? "test" : name.replaceFirst("^테스트\\s*[—-]\\s*", "")) + " · 갱신 " + date : tag + " · " + date;
        return new String[]{title, summary(body == null ? "" : body, heads)};
    }
    /** The string value of "key" at or after from (JSON escapes decoded), or null. */
    static String field(String json, String key, int from) {
        int k = json.indexOf("\"" + key + "\"", Math.max(0, from));
        if (k < 0) return null;
        int i = k + key.length() + 2;
        while (i < json.length() && (json.charAt(i) == ' ' || json.charAt(i) == ':')) i++;
        if (i >= json.length() || json.charAt(i) != '"') return null;
        StringBuilder b = new StringBuilder();
        for (i++; i < json.length(); i++) {
            char ch = json.charAt(i);
            if (ch == '"') return b.toString();
            if (ch != '\\') { b.append(ch); continue; }
            char e = json.charAt(++i);
            switch (e) {
                case 'n': b.append('\n'); break;
                case 'r': break;
                case 't': b.append('\t'); break;
                case 'u': b.append((char) Integer.parseInt(json.substring(i + 1, i + 5), 16)); i += 4; break;
                default: b.append(e);
            }
        }
        return null;
    }
    /** The release notes in a few lines: each section (### or a **bold** line) with its first bullet's first sentence,
     *  up to heads sections, stopping at the install / signature sections or the test notes' history ("> ..."). */
    static String summary(String body, int heads) {
        StringBuilder b = new StringBuilder();int n = 0;boolean want = false;
        for (String raw : body.split("\r?\n")) {
            String l = raw.trim();
            if (n > 0 && (l.startsWith("> ") || l.startsWith("### 설치") || l.startsWith("### 서명"))) break;
            String h = l.startsWith("### ") ? l.substring(4) : l.length() > 4 && l.startsWith("**") && l.endsWith("**") ? l.substring(2, l.length() - 2) : null;
            if (h != null) { if (n == heads) break; n++; b.append("■ ").append(clean(h)).append('\n'); want = true; continue; }
            if (want && l.startsWith("- ")) { b.append("   ").append(shorten(clean(l.substring(2)))).append('\n'); want = false; }
        }
        return b.toString().trim();
    }
    private static String clean(String t) { return t.replaceAll("\\[([^\\]]+)\\]\\([^)]+\\)", "$1").replace("**", "").replace("`", ""); }
    private static String shorten(String t) {
        int i = t.indexOf("다. ");
        if (i < 0) i = t.indexOf(". ");
        if (i >= 0) t = t.substring(0, i + 2).trim();
        return t.length() > 90 ? t.substring(0, 89) + "…" : t;
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
