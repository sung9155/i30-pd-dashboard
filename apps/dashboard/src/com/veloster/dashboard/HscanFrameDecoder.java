package com.veloster.dashboard;

/** Exact captured250902 stream framing; never interprets arbitrary bytes as CAN IDs. */
final class HscanFrameDecoder {
    interface Sink { void record(int function, byte[] data, long receivedAt); }
    private static final int[] HEADER = {255, 11, 142, 1, 139, 199, 1, 0};
    private final byte[] frame = new byte[264];
    private final Sink sink;
    private int used;
    HscanFrameDecoder(Sink sink) { this.sink = sink; }
    void reset() { used = 0; }

    void accept(byte[] data, int offset, int length, long receivedAt) {
        if (offset < 0 || length < 0 || offset > data.length - length) throw new IndexOutOfBoundsException();
        for (int i = offset; i < offset + length; i++) {
            frame[used++] = data[i];
            while (used > 0 && !prefix()) discardFirst();
            if (used != frame.length) continue;
            int checksum = 0;
            for (byte b : frame) checksum ^= b & 255;
            if (checksum == 0) {
                batch(receivedAt);
                used = 0;
            } else {
                discardFirst();
                while (used > 0 && !prefix()) discardFirst();
            }
        }
    }

    /** i30 PD (pd_kr) hscan_mux slots verified in-car 2026-10-29 (i30pd_port/HSCAN_DECODE.md).
     * Function numbers differ from the JSN stream (0x07/0x23/0x53/0x5a/0x6e); unlisted slots are never published. */
    private static int expectedSize(int function) {
        switch (function) {
            case 0x01: return 8; // CGW1: driver door (byte1 bit0), wiper speed (byte3)
            case 0x02: return 4; // CLU11: vehicle speed
            case 0x03: return 4; // CLU12: odometer
            case 0x04: return 8; // CLU13: drive mode, range, average fuel economy
            case 0x08: return 8; // EMS11: engine rpm, torque
            case 0x09: return 8; // EMS12: coolant, accelerator pedal
            case 0x0a: return 8; // battery voltage
            case 0x0b: return 8; // byte2 intake air temperature
            case 0x0c: return 8; // EMS16: spark timing
            case 0x0e: return 8; // EMS19: boost pressure
            case 0x10: return 8; // EMS_H12: manual-gearbox gear sent to the cluster (CF_Ems_DesCurGr)
            case 0x12: return 7; // EPB11: electronic parking brake
            case 0x13: return 8; // ESP12: brake pressure, lateral/longitudinal acceleration
            case 0x17: return 8; // MDPS11: driver steering torque
            case 0x19: return 5; // SAS11: steering angle and speed
            case 0x1b: return 8; // TCS13: driver braking
            case 0x1d: return 8; // LVR12: gear selector P/R/N/D/M
            case 0x1f: return 8; // current gear while driving
            case 0x20: return 6; // TPMS: byte1 valid flag, bytes 2-5 tire pressure psi
            case 0x21: return 6; // EMS20: fuel consumption
            default: return -1;
        }
    }

    private boolean prefix() {
        for (int i = 0; i < Math.min(used, HEADER.length); i++)
            if ((frame[i] & 255) != HEADER[i]) return false;
        return true;
    }
    private void discardFirst() { used--; System.arraycopy(frame, 1, frame, 0, used); }

    private void batch(long receivedAt) {
        if ((frame[8] & 255) != 0xc7) return;
        int count = (frame[9] & 255) | ((frame[10] & 255) << 8);
        if (count > 84) return;
        int offset = 11;
        // Validate the complete batch before publishing any values.
        for (int i = 0; i < count; i++) {
            if (offset + 2 > 263) return;
            int size = frame[offset + 1] & 255;
            if (size < 1 || size > 8 || offset + 2 + size > 263) return;
            offset += 2 + size;
        }
        for (int i = offset; i < 263; i++) if (frame[i] != 0) return;
        offset = 11;
        for (int i = 0; i < count; i++) {
            int function = frame[offset++] & 255, size = frame[offset++] & 255;
            if (size == expectedSize(function)) {
                byte[] value = new byte[size]; System.arraycopy(frame, offset, value, 0, size);
                sink.record(function, value, receivedAt);
            }
            offset += size;
        }
    }
}
