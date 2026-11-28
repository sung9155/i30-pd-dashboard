package com.veloster.carplayhub;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.veloster.carplayhub.usb.UsbBundle;
import com.veloster.carplayhub.usb.UsbInstaller;
import com.veloster.i18n.UiText;

/**
 * USB 업데이트: shows what a stick's i30-update/ folder would install (from 내 앱 → USB 업데이트, or by itself when a stick
 * with a newer bundle is mounted), installs after [설치] and [지금 설치], and shows each app's progress. Nothing is
 * installed without those two taps.
 *
 * One screen in My Apps' look (Look): a card with a headline, a row per app (now → new, its step), a line of what to
 * do, and big buttons along the bottom (76 px: a gloved finger in the car). The states: looking, the offer, the
 * confirmation, installing, done, failed, nothing to install (no folder / already current / a bundle refused).
 * Replacing My Apps ends this process; UsbUpdateDone opens the screen again on "done" (EXTRA_DONE) from the rows saved
 * before the install.
 */
public final class UsbUpdateActivity extends Activity {
    public static final String EXTRA_ROOT = "root", EXTRA_DONE = "done";
    private static final int SCAN = 0, OFFER = 1, CONFIRM = 2, RUN = 3, DONE = 4, FAIL = 5, NONE = 6, HANDOFF = 7;
    private static final String ROWS = "pending_rows";
    private UsbInstaller installer;
    private UsbInstaller.Offer offer;
    private int state = SCAN;
    /** NONE / FAIL / HANDOFF: the headline, what to do about it, and the installer's own reason in small print. */
    private String head = "", note = "", detail = "";
    /** A row per app: its package, the installed and the new build name, and its step (UsbInstaller.WAIT .. OK). */
    private String[] pkg = {}, from = {}, to = {};
    private int[] step = {};
    /** The whole install in percent (-1: not started); the bar's two halves, so a new number moves it without a new layout. */
    private int pct = -1;
    private View barOn, barOff; private TextView barText;
    /** Fallback (silent install refused): the apps still to hand to the system installer, one per return here. */
    private int fallbackNext = -1;
    /** The mount point the screen was opened for (null: the usual ones), kept for 다시 확인. */
    private String root;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        installer = new UsbInstaller(this);
        open(getIntent());
    }

    @Override protected void onNewIntent(Intent i) { super.onNewIntent(i); if (state != RUN) open(i); }

    private void open(Intent i) {
        if (i != null && i.getBooleanExtra(EXTRA_DONE, false) && loadRows()) { state = DONE; render(); return; }
        scan(i == null ? null : i.getStringExtra(EXTRA_ROOT));
    }

    @Override protected void onResume() {
        super.onResume();
        if (fallbackNext >= 0 && state == HANDOFF) nextFallback();   // back from the system installer: the next app, or the result
    }

    @Override public void onBackPressed() { if (state != RUN) super.onBackPressed(); }   // not in the middle of an install

    private static String t(String s) { return UiText.t(s); }

    private void scan(final String root) {
        this.root = root; state = SCAN; offer = null; render();
        new Thread(new Runnable() { public void run() {
            final UsbInstaller.Offer o = installer.find(root);
            runOnUiThread(new Runnable() { public void run() { show(o); } });
        }}, "usb-update-scan").start();
    }

    private void show(UsbInstaller.Offer o) {
        offer = o; detail = "";
        if (o != null) {
            int n = o.apps.size(); pkg = new String[n]; from = new String[n]; to = new String[n]; step = new int[n];
            for (int i = 0; i < n; i++) { pkg[i] = o.apps.get(i).packageName; from[i] = versionName(pkg[i]); to[i] = o.apps.get(i).name; }
            state = OFFER;
        } else {
            state = NONE; pkg = new String[0];
            if (installer.lastError != null) {
                head = t("이 USB 업데이트는 쓸 수 없습니다"); note = advice(installer.lastError); detail = reason(installer.lastError);
            } else if (installer.current != null) {
                head = t("이미 최신 버전입니다"); note = t("USB에 든 버전") + ": " + installer.current;
            } else {
                head = t("USB에서 i30-update 폴더를 찾지 못했습니다"); note = t("USB 메모리 맨 위에 i30-update 폴더를 넣고 다시 꽂으세요.");
            }
        }
        render();
    }

    /** What to do about a bundle or an install that was refused, from the installer's reason (UsbBundle.require). */
    private static String advice(String error) {
        if (error == null) return "";
        if (error.contains("made for")) return t("이 헤드유닛용 업데이트가 아닙니다. i30 PD용 파일을 받으세요.");
        if (error.contains("signed differently") || error.contains("installed signer")) return t("설치된 앱과 서명이 다릅니다. 설치기로 다시 설치하세요.");
        if (error.contains("did not finish")) return t("설치가 끝나지 않았습니다. 다시 확인을 눌러 보세요.");
        if (error.contains("signature") || error.contains("signer") || error.contains("format") || error.contains("manifest") || error.contains("ASCII") || error.contains("field"))
            return t("서명이 맞지 않습니다. 배포 파일을 다시 받아 USB에 넣으세요.");
        return t("업데이트 파일이 손상됐습니다. 다시 받아 USB에 넣으세요.");
    }

    private static String reason(String error) { return error == null ? "" : error.replace("USB update rejected: ", ""); }

    private String label(String p) { return UsbBundle.DASHBOARD.equals(p) ? t("계기판") : t("내 앱"); }

    private String versionName(String p) {
        try { return getPackageManager().getPackageInfo(p, 0).versionName; } catch (PackageManager.NameNotFoundException e) { return "?"; }
    }

    // ---- the screen ----

    private void render() {
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(16, 8, 16, 12); root.setBackgroundColor(Look.BG);
        TextView title = Look.label(this, t("USB 업데이트"), 24, Look.TXT, true); title.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(title, new LinearLayout.LayoutParams(-1, 48));
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(26, 20, 26, 16);
        card.setBackgroundDrawable(Look.shape(Look.CARD, 0, 14));
        String h, n = "";
        int ink = Look.TXT;
        switch (state) {
            case SCAN: h = t("USB 메모리를 확인하는 중…"); break;
            case OFFER: h = t("새 업데이트가 있습니다"); n = t("설치하는 동안 계기판과 내 앱이 잠시 닫힙니다."); break;
            case CONFIRM: h = t("업데이트를 설치할까요?"); n = t("설치하는 동안 계기판과 내 앱이 잠시 닫힙니다."); break;
            case RUN: h = t("설치하는 중…"); n = t("끝날 때까지 시동을 끄거나 USB를 빼지 마세요."); break;
            case DONE: h = t("업데이트 완료"); ink = Look.ON; break;
            case FAIL: h = t("업데이트하지 못했습니다"); n = note; ink = Look.RED; break;
            default: h = head; n = note;
        }
        card.addView(Look.label(this, h, 28, ink, true));
        for (int i = 0; i < pkg.length; i++) row(card, i);
        barOn = null;
        if (state == RUN || state == DONE) bar(card, state == DONE ? 100 : Math.max(pct, 0));
        if (n.length() > 0) { TextView v = Look.label(this, n, 19, Look.MUT, false); v.setPadding(0, 14, 0, 0); v.setLineSpacing(5, 1); card.addView(v); }
        if (detail.length() > 0 && (state == FAIL || state == NONE)) { TextView v = Look.label(this, detail, 14, 0xff6a7078, false); v.setPadding(0, 8, 0, 0); card.addView(v); }
        root.addView(card, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout buttons = new LinearLayout(this);
        Runnable close = new Runnable() { public void run() { finish(); } }, again = new Runnable() { public void run() { scan(UsbUpdateActivity.this.root); } };
        switch (state) {
            case OFFER: big(buttons, t("닫기"), false, 1, close); big(buttons, t("설치"), true, 2, new Runnable() { public void run() { state = CONFIRM; render(); } }); break;
            case CONFIRM: big(buttons, t("취소"), false, 1, new Runnable() { public void run() { state = OFFER; render(); } }); big(buttons, t("지금 설치"), true, 2, new Runnable() { public void run() { install(); } }); break;
            case RUN: break;
            case DONE: big(buttons, t("닫기"), false, 1, close); big(buttons, t("계기판 열기"), true, 2, new Runnable() { public void run() { openDashboard(); } }); break;
            case FAIL: case NONE: big(buttons, t("닫기"), false, 1, close); big(buttons, t("다시 확인"), true, 2, again); break;
            default: big(buttons, t("닫기"), false, 1, close);
        }
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, 76); bp.topMargin = 12;
        root.addView(buttons, bp);
        setContentView(root);
    }

    /** One app: its name, the installed build → the new one, and where its install stands. */
    private void row(LinearLayout card, int i) {
        LinearLayout r = new LinearLayout(this); r.setGravity(Gravity.CENTER_VERTICAL);
        r.addView(Look.label(this, label(pkg[i]), 24, Look.TXT, true), new LinearLayout.LayoutParams(150, -2));
        boolean done = step[i] == UsbInstaller.OK;
        TextView was = Look.label(this, from[i], 22, Look.MUT, false), arrow = Look.label(this, "  →  ", 22, Look.MUT, false), now = Look.label(this, to[i], 24, done ? Look.ON : Look.TXT, true);
        was.setSingleLine(true); now.setSingleLine(true);
        r.addView(was); r.addView(arrow); r.addView(now, new LinearLayout.LayoutParams(0, -2, 1));
        String s = step[i] == UsbInstaller.COPY ? t("확인 중…") : step[i] == UsbInstaller.INSTALL ? t("설치 중…") : done ? t("완료") : state == RUN ? t("대기") : "";
        TextView mark = Look.label(this, s, 20, done ? Look.ON : step[i] == UsbInstaller.WAIT ? Look.MUT : Look.TXT, done);
        mark.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        r.addView(mark, new LinearLayout.LayoutParams(140, -2));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, 58); if (i == 0) p.topMargin = 10;
        card.addView(r, p);
        View rule = new View(this); rule.setBackgroundColor(Look.LINE); card.addView(rule, new LinearLayout.LayoutParams(-1, 1));
    }

    /** The progress bar under the rows: a track, its filled part, and the percent. */
    private void bar(LinearLayout card, int p) {
        LinearLayout line = new LinearLayout(this); line.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout track = new LinearLayout(this); track.setBackgroundDrawable(Look.shape(Look.LINE, 0, 9));
        barOn = new View(this); barOn.setBackgroundDrawable(Look.shape(p >= 100 ? Look.ON : Look.ACC, 0, 9)); barOff = new View(this);
        track.addView(barOn, new LinearLayout.LayoutParams(0, -1, p)); track.addView(barOff, new LinearLayout.LayoutParams(0, -1, 100 - p));
        line.addView(track, new LinearLayout.LayoutParams(0, 18, 1));
        barText = Look.label(this, p + " %", 22, Look.TXT, true); barText.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        line.addView(barText, new LinearLayout.LayoutParams(100, -2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.topMargin = 18;
        card.addView(line, lp);
    }

    private void setPct(int p) {
        pct = p;
        if (barOn == null) return;
        barOn.setLayoutParams(new LinearLayout.LayoutParams(0, -1, p)); barOff.setLayoutParams(new LinearLayout.LayoutParams(0, -1, 100 - p));
        barText.setText(p + " %");
    }

    /** A bottom button, 76 px tall: the main one filled with the accent, the other an outline. */
    private void big(LinearLayout parent, String text, boolean main, float weight, final Runnable action) {
        TextView v = Look.label(this, text, 26, Look.TXT, true); v.setGravity(Gravity.CENTER);
        v.setBackgroundDrawable(Look.look(main ? Look.ACC : 0, main ? 0 : Look.LINE, 14)); v.setClickable(true);
        v.setOnClickListener(new View.OnClickListener() { public void onClick(View view) { action.run(); } });
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -1, weight); if (parent.getChildCount() > 0) p.leftMargin = 12;
        parent.addView(v, p);
    }

    // ---- installing ----

    private void install() {
        if (offer == null || state != CONFIRM) return;
        state = RUN; pct = 0; saveRows(); render();
        final UsbInstaller.Offer o = offer;
        new Thread(new Runnable() { public void run() {
            Exception failed = null;
            try {
                installer.install(o, new UsbInstaller.Progress() { public void step(final int app, final int phase) {
                    runOnUiThread(new Runnable() { public void run() { if (app < step.length) { step[app] = phase; render(); } } });
                }
                public void percent(final int p) { runOnUiThread(new Runnable() { public void run() { setPct(p); } }); }});
            } catch (Exception e) { failed = e; }
            final Exception error = failed;
            runOnUiThread(new Runnable() { public void run() { finished(error); } });
        }}, "usb-update-install").start();
    }

    private void finished(Exception e) {
        UsbInstaller.takePendingSelf(this); prefs().edit().remove(ROWS).apply();   // we are still here: no report after a restart
        if (e == null) { state = DONE; render(); return; }
        if (UsbInstaller.refused(e)) { state = HANDOFF; fallbackNext = 0; nextFallback(); return; }   // no silent install on this unit: one system installer screen per app
        com.veloster.diagnostics.ErrorLog.record("usb_update", e);
        state = FAIL; note = advice(String.valueOf(e.getMessage())); detail = reason(e.getMessage()); render();
    }

    /** Hands the next app (dashboard first) to the system installer; after the last, rescans to show what is left. */
    private void nextFallback() {
        if (offer == null || fallbackNext >= offer.apps.size()) { fallbackNext = -1; scan(null); return; }
        UsbBundle.App a = offer.apps.get(fallbackNext++);
        try {
            head = t("설치 화면에서 [설치]를 누르세요."); note = label(a.packageName); pkg = new String[0]; render();
            startActivity(installer.installerIntent(offer, a));
        } catch (Exception e) {
            com.veloster.diagnostics.ErrorLog.record("usb_update_installer", e);
            fallbackNext = -1; state = FAIL; note = advice(String.valueOf(e.getMessage())); detail = reason(e.getMessage()); render();
        }
    }

    private void openDashboard() {
        try {
            startActivity(new Intent(Intent.ACTION_MAIN).setClassName(UsbBundle.DASHBOARD, UsbBundle.DASHBOARD + ".DashboardActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
        } catch (RuntimeException e) { com.veloster.diagnostics.ErrorLog.record("usb_update_open", e); }
        finish();
    }

    // ---- the rows, kept across My Apps replacing itself ----

    private SharedPreferences prefs() { return getSharedPreferences(UsbInstaller.PREFS, 0); }

    private void saveRows() {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < pkg.length; i++) s.append(pkg[i]).append('\t').append(from[i]).append('\t').append(to[i]).append('\n');
        prefs().edit().putString(ROWS, s.toString()).commit();
    }

    /** The rows of the install that replaced My Apps, each marked done when its build is the one installed now. */
    private boolean loadRows() {
        String saved = prefs().getString(ROWS, null);
        prefs().edit().remove(ROWS).apply();
        if (saved == null || saved.length() == 0) return false;
        String[] lines = saved.split("\n");
        pkg = new String[lines.length]; from = new String[lines.length]; to = new String[lines.length]; step = new int[lines.length];
        for (int i = 0; i < lines.length; i++) {
            String[] f = lines[i].split("\t", -1);
            if (f.length != 3) return false;
            pkg[i] = f[0]; from[i] = f[1]; to[i] = f[2]; step[i] = f[2].equals(versionName(f[0])) ? UsbInstaller.OK : UsbInstaller.WAIT;
        }
        return true;
    }
}
