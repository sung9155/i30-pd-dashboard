package com.veloster.neondrive;

import android.content.Context;
import android.view.View;

/**
 * Neon Drive inside the dashboard: its own view over the dashboard, fed by the dashboard's signals (PdSignals on the
 * i30 PD), with a back button that returns to the dashboard. Settings stay in the "neon_drive" preferences.
 */
public final class NeonDrivePage {
    private final NeonView view;
    private boolean running;

    public NeonDrivePage(Context context, DriveSignals signals, Runnable onExit) {
        view = new NeonView(context, signals, true);
        view.setExit(onExit);
    }
    public View view() { return view; }
    public void start() { if (running) return; running = true; view.resume(); }
    public void stop() { if (!running) return; running = false; view.pause(); }
}
