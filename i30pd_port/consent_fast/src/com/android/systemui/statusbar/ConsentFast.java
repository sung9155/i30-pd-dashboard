package com.android.systemui.statusbar;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;
import java.lang.reflect.Field;

/**
 * Shortens the stock start-up notice's auto-close from 10 s to 1 s.
 *
 * The notice (automotive.jar com.hkmc.internal.consentscreen.ConsentScreen) arms its auto-close with
 * mHandler.sendEmptyMessageDelayed(1, DELAY_AUTO_CLOSE = 10000); message 1 runs its own confirm(), the same
 * code the 10 s timer and the OK button run. The constant is compiled into the framework, so instead the
 * patched SystemUI swaps that private handler right after creating the notice for one that brings any
 * delayed message 1 forward to 1 s. Everything else stays stock: the notice still appears and closes the
 * boot animation itself, records the agreement, and handles quick boot. Any failure leaves the stock handler.
 */
public final class ConsentFast {
    static final String TAG = "ConsentFast";
    static final int MSG_CONFIRM = 1;
    static final long CLOSE_MS = 1000;

    private ConsentFast() {}

    /** Called by StatusBarService.onIntentReloadSettings() right after new ConsentScreen(context). */
    public static void hook(Object consent) {
        try {
            Field f = consent.getClass().getDeclaredField("mHandler");
            f.setAccessible(true);
            Handler stock = (Handler) f.get(consent);
            if (stock instanceof Fast) return;
            f.set(consent, new Fast(stock.getLooper(), (Handler.Callback) consent));
            Log.i(TAG, "start-up notice auto-close set to " + CLOSE_MS + " ms");
        } catch (Throwable t) {
            Log.w(TAG, "start-up notice left as stock: " + t);
        }
    }

    /** Same looper and callback as the stock handler; only a delayed confirm is brought forward. */
    static final class Fast extends Handler {
        Fast(Looper looper, Handler.Callback callback) { super(looper, callback); }

        @Override
        public boolean sendMessageAtTime(Message msg, long uptimeMillis) {
            if (msg.what == MSG_CONFIRM) {
                long soonest = SystemClock.uptimeMillis() + CLOSE_MS;
                if (uptimeMillis > soonest) uptimeMillis = soonest;
            }
            return super.sendMessageAtTime(msg, uptimeMillis);
        }
    }
}
