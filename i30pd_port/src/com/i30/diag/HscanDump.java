package com.i30.diag;

import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * hscan_mux 수신 전용 덤퍼 — i30 PD에서 실제로 오는 function/payload를 확인한다.
 * veloster-n-dashboard의 HscanFrameDecoder와 동일한 프레이밍 규칙을 그대로 쓴다.
 * app_process로 실행 (Activity/권한 불필요, abstract 소켓은 파일 권한 없음).
 *
 * 빌드:  javac -bootclasspath android.jar -d out HscanDump.java
 *        d8 --output hscandump.jar out/com/i30/diag/HscanDump.class
 * 실행:  adb push hscandump.jar /data/local/tmp/
 *        adb shell CLASSPATH=/data/local/tmp/hscandump.jar app_process /data/local/tmp com.i30.diag.HscanDump
 *        (기본 20초 관찰. 인자로 초 지정 가능: ... HscanDump 60)
 */
public final class HscanDump {
    private static final int[] HEADER = {255, 11, 142, 1, 139, 199, 1, 0};

    public static void main(String[] args) throws Exception {
        int seconds = args.length > 0 ? Integer.parseInt(args[0]) : 20;
        boolean dumpRaw = args.length > 1 && args[1].equals("raw");
        System.out.println("[hscandump] connecting @hscan_mux, observe " + seconds + "s");

        LocalSocket s = new LocalSocket();
        try {
            s.connect(new LocalSocketAddress("hscan_mux", LocalSocketAddress.Namespace.ABSTRACT));
        } catch (Exception e) {
            System.out.println("[hscandump] CONNECT FAILED: " + e
                + "  (소켓명이 다르거나 시동 OFF일 수 있음)");
            return;
        }
        System.out.println("[hscandump] connected. IGN ON + 주행 중이어야 값이 흐른다.");

        InputStream in = s.getInputStream();
        byte[] buf = new byte[4096];
        byte[] frame = new byte[264];
        int used = 0;
        long end = System.currentTimeMillis() + seconds * 1000L;
        long frames = 0;
        // function -> [count, lastSize, lastPayloadHex]
        Map<Integer, long[]> counts = new LinkedHashMap<Integer, long[]>();
        Map<Integer, String> sample = new LinkedHashMap<Integer, String>();

        while (System.currentTimeMillis() < end) {
            int n = in.read(buf);
            if (n < 0) break;
            for (int i = 0; i < n; i++) {
                frame[used++] = buf[i];
                while (used > 0 && !prefix(frame, used)) shift(frame, used--);
                if (used != frame.length) continue;
                int xor = 0;
                for (byte b : frame) xor ^= b & 255;
                if (xor == 0) {
                    frames++;
                    if (dumpRaw) System.out.println("RAW " + hex(frame, 0, 264));
                    batch(frame, counts, sample);
                    used = 0;
                } else {
                    shift(frame, used--);
                    while (used > 0 && !prefix(frame, used)) shift(frame, used--);
                }
            }
        }
        in.close(); s.close();

        System.out.println("\n[hscandump] frames=" + frames + "  functions seen:");
        System.out.println(String.format("  %-6s %-6s %-5s %s", "func", "count", "size", "last payload (hex)"));
        for (Map.Entry<Integer, long[]> e : counts.entrySet()) {
            int f = e.getKey(); long[] c = e.getValue();
            System.out.println(String.format("  0x%02x   %-6d %-5d %s",
                f, c[0], c[1], sample.get(f)));
        }
        System.out.println("\n[hscandump] 참고 — i30 PD 실측 매핑 (벨N과 function 번호 다름, HSCAN_DECODE.md):");
        System.out.println("  0x02=CLU11 차속((b1|(b2&3)<<8)*0.5km/h)  0x08=EMS11 RPM((b2|b3<<8)*0.25), b6=차속km/h");
        System.out.println("  0x19=SAS11 조향각(s16(b0|b1<<8)*0.1, b2=조향속도)  0x13=ESP12(b3~b4 브레이크압)");
        System.out.println("  0x1d b1&7=기어 셀렉터(P0 D5 N6 R7)  0x1f b2>>4=현재 단수  0x0a b3*0.1=전압  0x0b b2=수온");
        System.out.println("  0x01=램프(b2.3 좌시그널 b7.6 우시그널 b3.7/b4 전조등)  0x09 b1=도어락  WHL_SPD11 없음");
        System.out.println("  PD는 function 0x00~0x22 연속 35슬롯. 벨N 상수(0x07/0x23/0x53/0x5a/0x6e)는 그대로 안 맞음.");
    }

    private static boolean prefix(byte[] f, int used) {
        int m = Math.min(used, HEADER.length);
        for (int i = 0; i < m; i++) if ((f[i] & 255) != HEADER[i]) return false;
        return true;
    }
    private static void shift(byte[] f, int used) { System.arraycopy(f, 1, f, 0, used - 1); }

    private static void batch(byte[] f, Map<Integer, long[]> counts, Map<Integer, String> sample) {
        if ((f[8] & 255) != 0xc7) return;
        int count = (f[9] & 255) | ((f[10] & 255) << 8);
        if (count > 84) return;
        int off = 11;
        for (int i = 0; i < count; i++) {           // validate first
            if (off + 2 > 263) return;
            int size = f[off + 1] & 255;
            if (size < 1 || size > 8 || off + 2 + size > 263) return;
            off += 2 + size;
        }
        off = 11;
        for (int i = 0; i < count; i++) {
            int func = f[off++] & 255, size = f[off++] & 255;
            long[] c = counts.get(func);
            if (c == null) { c = new long[]{0, size}; counts.put(func, c); }
            c[0]++; c[1] = size;
            sample.put(func, hex(f, off, size));
            off += size;
        }
    }

    private static String hex(byte[] b, int off, int len) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) sb.append(String.format("%02x ", b[off + i] & 255));
        return sb.toString().trim();
    }
}
