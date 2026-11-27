package com.veloster.car3d;

import android.content.Context;
import android.view.View;
import com.veloster.neondrive.DriveSignals;

/**
 * 내 차 as a page of the dashboard: its own full-screen view over the dashboard (like Neon Drive), fed by the
 * dashboard's signals (PdSignals on the i30 PD). The back button returns to the dashboard.
 */
public final class CarPage {
    private final CarView view;
    private boolean running;

    public CarPage(Context context, DriveSignals signals, Runnable onExit) {
        view = new CarView(context, signals, true);
        view.setExit(onExit);
    }

    public View view() { return view; }

    public void start() { if (running) return; running = true; view.resume(); }

    public void stop() { if (!running) return; running = false; view.pause(); view.clearTests(); }
}
