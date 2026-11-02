package com.i30.installer;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ClipboardManager;
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
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

/** One screen: channel, 설치 / 삭제, and the running log. The steps live in Flow. */
public class MainActivity extends Activity implements Flow.Ui, Flow.Car {
    private static final String VERSION = "2026-11-02";
    private TextView log;
    private ScrollView scroll;
    private Button installBtn, removeBtn;
    private RadioButton test;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("i30 PD 대시보드 설치 (휴대폰, " + VERSION + ")");
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

        LinearLayout buttons = new LinearLayout(this);
        installBtn = button(buttons, "설치", v -> start(false));
        removeBtn = button(buttons, "삭제", v -> start(true));
        button(buttons, "기록 복사", v -> {
            ((ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("i30pd", log.getText()));
            say("(기록을 복사했습니다)");
        });
        root.addView(buttons);

        log = new TextView(this);
        log.setTypeface(Typeface.MONOSPACE);
        log.setTextIsSelectable(true);
        scroll = new ScrollView(this);
        scroll.addView(log);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    private Button button(LinearLayout row, String text, android.view.View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setOnClickListener(l);
        row.addView(b, new LinearLayout.LayoutParams(0, -2, 1));
        return b;
    }

    private void start(boolean uninstall) {
        installBtn.setEnabled(false);
        removeBtn.setEnabled(false);
        log.setText("");
        String base = Flow.base(test.isChecked());
        if (!uninstall) say(test.isChecked() ? "버전: 테스트 (실차 검증 전)" : "버전: 안정화");
        new Thread(() -> {
            try {
                Flow.run(base, uninstall, getFilesDir(), this, this);
            } catch (Flow.Stop e) {
                say("\n중단: " + e.getMessage());
            } catch (Exception e) {
                say("\n오류: " + e + "\n[기록 복사]로 이 내용을 Issues(github.com/sung9155/i30-pd-dashboard/issues)에 알려 주세요.");
            }
            runOnUiThread(() -> { installBtn.setEnabled(true); removeBtn.setEnabled(true); });
        }).start();
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
        ui.say("\n■ 차 연결 기다리는 중 (최대 3분)");
        UsbManager usb = (UsbManager) getSystemService(Context.USB_SERVICE);
        boolean asked = false;
        for (int s = 0; s < 180; s++) {
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
                return new UsbLink(c, intf);
            }
            if (s % 15 == 14) ui.say("→ 차를 기다리는 중... Factory Test(0A 01/65 01/0A 02), OTG 어댑터 방향, 케이블을 확인하세요.");
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

        UsbLink(UsbDeviceConnection c, UsbInterface intf) {
            this.c = c;
            this.intf = intf;
            for (int i = 0; i < intf.getEndpointCount(); i++) {
                UsbEndpoint e = intf.getEndpoint(i);
                if (e.getType() != UsbConstants.USB_ENDPOINT_XFER_BULK) continue;
                if (e.getDirection() == UsbConstants.USB_DIR_IN) in = e; else out = e;
            }
        }

        @Override public void write(byte[] b, int off, int len) throws IOException {
            while (len > 0) {
                int n = Math.min(len, buf.length);
                System.arraycopy(b, off, buf, 0, n);
                int w = c.bulkTransfer(out, buf, n, 5000);
                if (w <= 0) throw new IOException("USB 쓰기 실패 (연결이 끊겼을 수 있습니다)");
                off += w;
                len -= w;
            }
        }

        @Override public int read(byte[] b, int off, int len, int timeoutMs) {
            int n = c.bulkTransfer(in, buf, Math.min(len, buf.length), timeoutMs);
            if (n <= 0) return 0;
            System.arraycopy(buf, 0, b, off, n);
            return n;
        }

        @Override public void close() { c.releaseInterface(intf); c.close(); }
    }
}
