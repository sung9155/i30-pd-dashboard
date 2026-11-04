package com.i30.installer;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

/** One screen: channel, 설치 / 삭제 / 취소 / 진단 기록 / 주행 기록 / 공유, a wait line with seconds, and the running log. The steps live in Flow. */
public class MainActivity extends Activity implements Flow.Ui, Flow.Car {
    private TextView log;
    private ScrollView scroll;
    private Button installBtn, removeBtn, diagBtn, recordBtn, cancelBtn;
    private TextView waitLine;
    private String waitLabel;   // UI thread only
    private long waitStart;
    private final AtomicBoolean cancel = new AtomicBoolean();
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (waitLabel == null) { waitLine.setVisibility(android.view.View.GONE); return; }
            waitLine.setText("⏳ " + waitLabel + "  " + (android.os.SystemClock.elapsedRealtime() - waitStart) / 1000 + "초");
            waitLine.postDelayed(this, 1000);
        }
    };
    private RadioButton test;
    private volatile boolean recording;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("i30 PD 대시보드 설치 (휴대폰, " + Flow.VERSION + ")");
        title.setTextSize(20);
        root.addView(title);
        TextView warn = new TextView(this);
        warn.setText("현대자동차 공식 앱이 아닙니다. 보증/AS 불이익이 있을 수 있고 책임은 본인에게 있습니다. 정차 · 시동 켠 상태에서 하세요.\n"
                + "준비물: OTG 어댑터(USB-C → USB-A 암) + A-A 케이블. 폰 설정에 OTG 켜기가 있으면 켜 두세요.");
        root.addView(warn);

        RadioGroup channel = new RadioGroup(this);
        RadioButton stable = new RadioButton(this);
        stable.setText("안정화 (권장) — 실차에서 확인된 버전");
        stable.setId(1);
        test = new RadioButton(this);
        test.setText("테스트 — 새 기능, 실차 검증 전");
        test.setId(2);
        channel.addView(stable);
        channel.addView(test);
        channel.check(1);
        root.addView(channel);
        // what each channel installs right now (GitHub release: version, date, the notes in a few lines)
        notes = new TextView(this);
        notes.setTextSize(13);
        notes.setText("버전 정보를 받는 중...");
        root.addView(notes);
        channel.setOnCheckedChangeListener((g, id) -> showNotes());
        new Thread(() -> {
            String[] s = Flow.release(false, 4), t = Flow.release(true, 4);
            runOnUiThread(() -> {
                rel[0] = s; rel[1] = t;
                if (s != null) stable.setText("안정화 (권장) " + s[0] + " — 실차에서 확인된 버전");
                if (t != null) test.setText("테스트 " + t[0] + " — 실차 검증 전");
                showNotes();
            });
        }).start();

        LinearLayout buttons = new LinearLayout(this);
        installBtn = button(buttons, "설치", v -> start(Flow.Mode.INSTALL));
        removeBtn = button(buttons, "삭제", v -> start(Flow.Mode.UNINSTALL));
        cancelBtn = button(buttons, "취소", v -> { cancel.set(true); cancelBtn.setEnabled(false); say(recording ? "\n기록을 마무리하는 중..." : "\n취소하는 중..."); });
        cancelBtn.setEnabled(false);
        root.addView(buttons);
        LinearLayout report = new LinearLayout(this);
        diagBtn = button(report, "진단 기록", v -> start(Flow.Mode.DIAG));
        recordBtn = button(report, "주행 기록", v -> start(Flow.Mode.RECORD));
        button(report, "공유", v -> new Thread(() -> {
            try { share(save("i30pd_log_", "")); } catch (IOException e) { say("기록 저장 실패: " + e.getMessage()); }
        }).start());
        root.addView(report);
        waitLine = new TextView(this);
        waitLine.setTypeface(Typeface.DEFAULT_BOLD);
        waitLine.setVisibility(android.view.View.GONE);
        root.addView(waitLine);

        log = new TextView(this);
        log.setTypeface(Typeface.MONOSPACE);
        log.setTextIsSelectable(true);
        scroll = new ScrollView(this);
        scroll.addView(log);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        say("[진단 기록]은 차를 연결해 상태와 앱 기록만 읽어 공유할 파일을 만듭니다. 차에는 아무것도 바꾸지 않습니다.");
        say("[주행 기록]은 운전하는 동안 차 신호를 읽기만 해서 기록합니다(최대 60분). 연결해 두고 운전한 뒤 [기록 끝]을 누르면 공유 화면이 뜹니다.");
        new Thread(() -> { String n = Flow.newer(); if (n != null) say("\n" + n); }).start();
    }

    private TextView notes;
    private final String[][] rel = new String[2][];
    /** The selected channel's notes summary, and where the full notes are. */
    private void showNotes() {
        boolean t = test.isChecked();String[] r = rel[t ? 1 : 0];
        String url = Flow.RELEASES + (t ? "/tag/test" : "/latest");
        notes.setText(r == null ? "버전 정보를 받지 못했습니다(인터넷 연결 확인). 설치는 그대로 할 수 있습니다.\n전체 설명: " + url
                : "이번 버전에 들어 있는 것\n" + r[1] + "\n전체 설명: " + url);
        android.text.util.Linkify.addLinks(notes, android.text.util.Linkify.WEB_URLS);
    }

    private Button button(LinearLayout row, String text, android.view.View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setOnClickListener(l);
        row.addView(b, new LinearLayout.LayoutParams(0, -2, 1));
        return b;
    }

    private void start(Flow.Mode mode) {
        enable(false);
        cancel.set(false);
        recording = mode == Flow.Mode.RECORD;
        cancelBtn.setText(recording ? "기록 끝" : "취소");
        log.setText("");
        String base = Flow.base(test.isChecked());
        if (mode == Flow.Mode.INSTALL) { String[] r = rel[test.isChecked() ? 1 : 0];
            say((test.isChecked() ? "버전: 테스트 (실차 검증 전)" : "버전: 안정화") + (r == null ? "" : " " + r[0])); }
        new Thread(() -> {
            try {
                if (recording) extractDump();
                File diag = Flow.run(base, mode, getFilesDir(), this, this);
                if (diag != null) share(diag);
            } catch (Exception e) {
                if (cancel.get()) say("\n취소했습니다. 설치 중이었다면 차에서는 그 단계가 끝날 수 있습니다. 다시 실행하면 이어서 맞춥니다.");
                else if (e instanceof Flow.Stop) say("\n중단: " + e.getMessage() + "\n[공유]로 이 화면의 기록을 보낼 수 있습니다.");
                else say("\n오류: " + e + "\n[공유]로 이 화면의 기록을 보내 알려 주세요.");
            }
            waiting(null);
            runOnUiThread(() -> enable(true));
        }).start();
    }

    private void enable(boolean on) {
        installBtn.setEnabled(on); removeBtn.setEnabled(on); diagBtn.setEnabled(on); recordBtn.setEnabled(on); cancelBtn.setEnabled(!on);
        if (on) cancelBtn.setText("취소");
    }

    /** build.sh puts hscandump.jar into the APK at assets/; Flow pushes it to the car from files/. */
    private void extractDump() throws IOException {
        try (java.util.zip.ZipFile z = new java.util.zip.ZipFile(getApplicationInfo().sourceDir)) {
            java.util.zip.ZipEntry e = z.getEntry("assets/hscandump.jar");
            if (e == null) return;   // Flow says what is missing
            try (java.io.InputStream in = z.getInputStream(e)) {
                Files.copy(in, new File(getFilesDir(), "hscandump.jar").toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    @Override public boolean cancelled() { return cancel.get(); }

    @Override public void waiting(String label) {
        runOnUiThread(() -> {
            waitLine.removeCallbacks(tick);
            waitLabel = label;
            waitStart = android.os.SystemClock.elapsedRealtime();
            if (label != null) waitLine.setVisibility(android.view.View.VISIBLE);
            tick.run();
        });
    }

    /** The on-screen log, masked, after the given head (a diagnostics file's text, or nothing). */
    private File save(String prefix, String head) throws IOException {
        CountDownLatch read = new CountDownLatch(1);
        String[] screen = new String[1];
        runOnUiThread(() -> { screen[0] = log.getText().toString(); read.countDown(); });
        try { read.await(); } catch (InterruptedException e) { throw new IOException(e); }
        File dir = new File(getFilesDir(), "diag");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("저장 폴더를 만들지 못했습니다");
        File f = new File(dir, prefix + System.currentTimeMillis() / 1000 + ".txt");
        Files.write(f.toPath(), (head + "\n[이 폰 화면 기록]\n" + Flow.mask(screen[0])).getBytes(StandardCharsets.UTF_8));
        return f;
    }

    /** The system share sheet with the file attached; the user picks the app and the person. */
    private void share(File f) throws IOException {
        boolean zip = f.getName().endsWith(".zip");   // a drive recording goes as it is
        if (!zip && !f.getName().startsWith("i30pd_log_")) {   // a diagnostics file: add this run's screen to it
            File merged = save("i30pd_diag_", new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
            if (!f.delete()) f.deleteOnExit();
            f = merged;
        }
        android.net.Uri uri = DiagProvider.uri(f);
        Intent send = new Intent(Intent.ACTION_SEND).setType(zip ? "application/zip" : "text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, "i30 PD 대시보드 기록")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        send.setClipData(ClipData.newRawUri("", uri));   // the chooser passes the read grant on only through ClipData
        say(zip ? "\n공유 화면에서 보낼 앱을 고르세요. 오르막에서 멈춘 곳 등 기억나는 것을 같이 적어 주면 찾기 쉽습니다."
                : "\n공유 화면에서 보낼 앱을 고르세요. 증상(언제, 어떤 화면에서, 무엇이)도 같이 알려 주세요.");
        runOnUiThread(() -> startActivity(Intent.createChooser(send, "공유").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)));
    }

    @Override public void say(String m) {
        runOnUiThread(() -> {
            log.append(m + "\n");
            scroll.post(() -> scroll.fullScroll(ScrollView.FOCUS_DOWN));
        });
    }

    @Override public boolean ask(String q) {
        CountDownLatch done = new CountDownLatch(1);
        AtomicBoolean yes = new AtomicBoolean();
        runOnUiThread(() -> new AlertDialog.Builder(this).setMessage(q).setCancelable(false)
                .setPositiveButton("예", (d, w) -> { yes.set(true); done.countDown(); })
                .setNegativeButton("아니오", (d, w) -> done.countDown()).show());
        try { done.await(); } catch (InterruptedException e) { return false; }
        say(q + " → " + (yes.get() ? "예" : "아니오"));
        return yes.get();
    }

    /** Waits up to 3 minutes for an adb USB interface (class ff/42/01), asks for USB permission, and opens it. */
    @Override public Adb.Transport waitCar(Flow.Ui ui) throws Exception {
        ui.say("\n■ 차 연결");
        ui.waiting("차 연결을 기다리는 중 (최대 3분)");
        try { return findCar(ui); } finally { ui.waiting(null); }
    }

    private Adb.Transport findCar(Flow.Ui ui) throws Exception {
        UsbManager usb = (UsbManager) getSystemService(Context.USB_SERVICE);
        boolean asked = false;
        for (int s = 0; s < 180; s++) {
            if (cancel.get()) throw new Flow.Stop("취소했습니다.");
            for (UsbDevice d : usb.getDeviceList().values()) {
                UsbInterface intf = adbInterface(d);
                if (intf == null) continue;
                if (!usb.hasPermission(d)) {
                    if (!asked) {
                        ui.say("→ 폰 화면에 USB 접근 허용 창이 뜨면 [확인]을 누르세요.");
                        Intent i = new Intent("com.i30.installer.USB").setPackage(getPackageName());
                        usb.requestPermission(d, PendingIntent.getBroadcast(this, 0, i, Build.VERSION.SDK_INT >= 31 ? 0x02000000 : 0));   // FLAG_MUTABLE: the system adds the result
                        asked = true;
                    }
                    continue;
                }
                UsbDeviceConnection c = usb.openDevice(d);
                if (c == null || !c.claimInterface(intf, true)) throw new Flow.Stop("USB 장치를 열지 못했습니다. 케이블을 다시 꽂고 다시 실행하세요.");
                ui.say(String.format("USB 장치 찾음: %04x:%04x", d.getVendorId(), d.getProductId()));
                return new UsbLink(c, intf, cancel);
            }
            if (s == 20) ui.say("→ 아직 안 보입니다. Factory Test(0A 01/65 01/0A 02), OTG 어댑터 방향, 케이블을 확인하세요.");
            Thread.sleep(1000);
        }
        throw new Flow.Stop("3분 안에 차를 찾지 못했습니다.\n"
                + "· Factory Test 코드를 띄어쓰기까지 정확히 다시 보내고 케이블을 다시 꽂으세요.\n"
                + "· 폰 쪽은 OTG 어댑터(USB-C 수 → USB-A 암)여야 합니다. 일반 충전 케이블로는 폰이 호스트가 되지 않습니다.\n"
                + "· 폰 설정에 OTG 켜기가 있으면 켜세요.");
    }

    private static UsbInterface adbInterface(UsbDevice d) {
        for (int i = 0; i < d.getInterfaceCount(); i++) {
            UsbInterface f = d.getInterface(i);
            if (f.getInterfaceClass() == 0xff && f.getInterfaceSubclass() == 0x42 && f.getInterfaceProtocol() == 1) return f;
        }
        return null;
    }

    /** Bulk endpoints of the adb interface. bulkTransfer with an offset is API 18, so this copies through a buffer. */
    private static final class UsbLink implements Adb.Transport {
        private final UsbDeviceConnection c;
        private final UsbInterface intf;
        private UsbEndpoint in, out;
        private final byte[] buf = new byte[16 * 1024];
        private final AtomicBoolean cancel;   // [취소]: the next read or write fails, which ends any adb wait

        UsbLink(UsbDeviceConnection c, UsbInterface intf, AtomicBoolean cancel) {
            this.c = c;
            this.intf = intf;
            this.cancel = cancel;
            for (int i = 0; i < intf.getEndpointCount(); i++) {
                UsbEndpoint e = intf.getEndpoint(i);
                if (e.getType() != UsbConstants.USB_ENDPOINT_XFER_BULK) continue;
                if (e.getDirection() == UsbConstants.USB_DIR_IN) in = e; else out = e;
            }
        }

        @Override public void write(byte[] b, int off, int len) throws IOException {
            while (len > 0) {
                if (cancel.get()) throw new IOException("취소했습니다");
                int n = Math.min(len, buf.length);
                System.arraycopy(b, off, buf, 0, n);
                int w = c.bulkTransfer(out, buf, n, 5000);
                if (w <= 0) throw new IOException("USB 쓰기 실패 (연결이 끊겼을 수 있습니다)");
                off += w;
                len -= w;
            }
        }

        @Override public int read(byte[] b, int off, int len, int timeoutMs) throws IOException {
            if (cancel.get()) throw new IOException("취소했습니다");
            int n = c.bulkTransfer(in, buf, Math.min(len, buf.length), Math.min(timeoutMs, 1000));
            if (n <= 0) return 0;
            System.arraycopy(buf, 0, b, off, n);
            return n;
        }

        @Override public void close() { c.releaseInterface(intf); c.close(); }
    }
}
