import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import com.android.systemui.statusbar.ConsentFast;

/**
 * Runs on the head unit / emulator Dalvik with app_process. A stand-in for ConsentScreen (a Handler.Callback
 * with a private mHandler, as in the firmware) is hooked, then the 10 s auto-close is armed the stock way.
 * Expect: message 1 (confirm) after about 1 s, message 5 (button enable) at its own delay, prints PASS/FAIL.
 */
public class ConsentFastTest {
    static long armed, confirmAt = -1, otherAt = -1;

    /** Mirrors the firmware: implements Handler.Callback, private mHandler = new Handler(this). */
    static class FakeConsent implements Handler.Callback {
        private Handler mHandler = new Handler(this);
        public boolean handleMessage(Message m) {
            long t = SystemClock.uptimeMillis() - armed;
            if (m.what == 1) confirmAt = t; else if (m.what == 5) otherAt = t;
            if (confirmAt >= 0 && otherAt >= 0) Looper.myLooper().quit();
            return true;
        }
        void armStock() {                       // what setConsentTimeoutInternal() does
            if (!mHandler.hasMessages(1)) mHandler.sendEmptyMessageDelayed(1, 10000);
            mHandler.sendEmptyMessageDelayed(5, 1500);
        }
    }

    public static void main(String[] a) {
        Looper.prepare();
        FakeConsent c = new FakeConsent();
        ConsentFast.hook(c);
        ConsentFast.hook(c);                    // twice must not wrap twice
        armed = SystemClock.uptimeMillis();
        c.armStock();
        new Handler().postDelayed(new Runnable() { public void run() { Looper.myLooper().quit(); } }, 4000);
        Looper.loop();
        boolean ok = confirmAt >= 900 && confirmAt <= 1300 && otherAt >= 1400 && otherAt <= 1800;
        System.out.println("confirm after " + confirmAt + " ms (want ~1000), msg5 after " + otherAt + " ms (want ~1500)");
        System.out.println(ok ? "PASS" : "FAIL");
    }
}
