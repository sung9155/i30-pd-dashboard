package com.i30.installer;

import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;

/** Runs Flow on the desktop against fake_adbd.py over TCP. args: PORT BASE_URL KEY_DIR install|uninstall|diag|record. Exit 3 = Flow.Stop. */
public class FlowTest {
    public static void main(String[] a) throws Exception {
        int port = Integer.parseInt(a[0]);
        Flow.Ui ui = new Flow.Ui() {
            public void say(String m) { System.out.println(m); }
            public boolean ask(String q) { System.out.println("ASK " + q); return true; }
        };
        try {
            File diag = Flow.run(a[1], Flow.Mode.valueOf(a[3].toUpperCase()), new File(a[2]), u -> tcp(port), ui);
            if (diag != null) System.out.println("DIAG " + diag);
        } catch (Flow.Stop e) {
            System.out.println("STOP " + e.getMessage());
            System.exit(3);
        }
    }

    /** A new connection each time Flow looks for the car (a drive recording reconnects after a drop). */
    static Adb.Transport tcp(int port) throws IOException {
        Socket s = new Socket("127.0.0.1", port);
        return new Adb.Transport() {
            public void write(byte[] b, int off, int len) throws IOException { s.getOutputStream().write(b, off, len); }
            public int read(byte[] b, int off, int len, int timeoutMs) throws IOException {
                s.setSoTimeout(timeoutMs);
                try { int n = s.getInputStream().read(b, off, len); if (n < 0) throw new IOException("closed"); return n; }
                catch (SocketTimeoutException e) { return 0; }
            }
            public void close() throws IOException { s.close(); }
        };
    }
}
