package com.i30.installer;

import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;

/** Runs Flow on the desktop against fake_adbd.py over TCP. args: PORT BASE_URL KEY_DIR install|uninstall|diag|record. Exit 3 = Flow.Stop. */
public class FlowTest {
    public static void main(String[] a) throws Exception {
        if (a[0].equals("notes")) { notes(); return; }
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

    /** Release info for the channel buttons: the JSON reader and the notes summary, on GitHub-shaped samples. */
    static void notes() {
        String stable = "{\"url\":\"x\",\"author\":{\"login\":\"sung9155\"},\"tag_name\":\"v1.8.2\",\"name\":\"v1.8.2\",\"published_at\":\"2026-11-04T05:10:00Z\","
            + "\"assets\":[{\"name\":\"dashboard-i30pd-platform.apk\",\"updated_at\":\"2026-11-04T05:09:00Z\"}],"
            + "\"body\":\"## v1.8.2\\r\\n\\r\\n디젤 차의 DPF\\n\\n### 디젤 DPF\\n- PD 디젤 엔진은 표준 요청에 답하지 않아 \\\"—\\\"였습니다. 이제 [현대 전용](https://x) 요청을 `22 ED03`으로 묻습니다.\\n- 둘째\\n\\n### 설치\\n- 무시 caf\\u00e9\"}";
        String[] s = Flow.describe(stable, false, 6);
        check(s[0].equals("v1.8.2 · 2026-11-04"), "stable title: " + s[0]);
        check(s[1].equals("■ 디젤 DPF\n   PD 디젤 엔진은 표준 요청에 답하지 않아 \"—\"였습니다."), "stable summary: " + s[1]);
        String test = "{\"tag_name\":\"test\",\"name\":\"테스트 — v1.8.2 + 칸 바꾸기\",\"published_at\":\"2026-11-02T01:00:00Z\","
            + "\"assets\":[{\"name\":\"myapps-i30pd-platform.apk\",\"updated_at\":\"2026-11-03T01:00:00Z\"},{\"name\":\"dashboard-i30pd-platform.apk\",\"updated_at\":\"2026-11-05T01:00:00Z\"}],"
            + "\"body\":\"> 이 레포는 포크입니다.\\n\\n**테스트 버전 (실차 검증 전)** — 고른 사람만.\\n\\n**대시보드: A (새로)**\\n- 첫 문장입니다. 둘째 문장.\\n\\n**대시보드: B**\\n- [링크](https://y) b caf\\u00e9\\n\\n> **v1.8.2에 정식으로 들어간 것**\\n\\n**대시보드: 옛것**\\n- 옛\"}";
        String[] t = Flow.describe(test, true, 6);
        check(t[0].equals("v1.8.2 + 칸 바꾸기 · 갱신 2026-11-05"), "test title: " + t[0]);
        check(t[1].equals("■ 대시보드: A (새로)\n   첫 문장입니다.\n■ 대시보드: B\n   링크 b café"), "test summary: " + t[1]);
        check(Flow.describe(test, true, 1)[1].equals("■ 대시보드: A (새로)\n   첫 문장입니다."), "heads limit");
        System.out.println("PASS release notes");
    }
    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
