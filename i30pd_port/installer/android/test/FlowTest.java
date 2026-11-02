package com.i30.installer;

import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;

/** Runs Flow on the desktop against fake_adbd.py over TCP. args: PORT BASE_URL KEY_DIR install|uninstall. Exit 3 = Flow.Stop. */
public class FlowTest {
    public static void main(String[] a) throws Exception {
        Socket s = new Socket("127.0.0.1", Integer.parseInt(a[0]));
        Adb.Transport tcp = new Adb.Transport() {
            public void write(byte[] b, int off, int len) throws IOException { s.getOutputStream().write(b, off, len); }
            public int read(byte[] b, int off, int len, int timeoutMs) throws IOException {
                s.setSoTimeout(timeoutMs);
                try { int n = s.getInputStream().read(b, off, len); if (n < 0) throw new IOException("closed"); return n; }
                catch (SocketTimeoutException e) { return 0; }
            }
            public void close() throws IOException { s.close(); }
        };
        Flow.Ui ui = new Flow.Ui() {
            public void say(String m) { System.out.println(m); }
            public boolean ask(String q) { System.out.println("ASK " + q); return true; }
        };
        try {
            Flow.run(a[1], a[3].equals("uninstall"), new File(a[2]), u -> tcp, ui);
        } catch (Flow.Stop e) {
            System.out.println("STOP " + e.getMessage());
            System.exit(3);
        }
    }
}
