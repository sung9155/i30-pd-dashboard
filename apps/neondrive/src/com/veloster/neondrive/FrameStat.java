package com.veloster.neondrive;

import java.util.Locale;

/**
 * Draw time of a screen that draws itself, outside the dashboard's own pages (네온 드라이브's canvas, 내 차's GL frame):
 * one line every 5 s in the dashboard's status format, so the car Pi's heartbeat and the slow-frame capture read it too:
 * "status draw avg X max Y ms over N frames (page neon)". N over 5 s is the frame rate the screen really got.
 */
public final class FrameStat {
    private final String page;
    private long since = -1, n;
    private double sum, max;

    public FrameStat(String page) { this.page = page; }

    /** Call at the end of a frame with System.nanoTime() taken at its start. */
    public void end(long startNs) {
        long now = System.nanoTime();
        double ms = (now - startNs) / 1e6;
        n++; sum += ms; if (ms > max) max = ms;
        if (since < 0) since = startNs;
        if (now - since >= 5_000_000_000L) {
            android.util.Log.i("VehicleDash", String.format(Locale.US, "status draw avg %.1f max %.1f ms over %d frames (page %s)", sum / n, max, n, page));
            since = now; n = 0; sum = 0; max = 0;
        }
    }
}
