import com.veloster.neondrive.DriveModel;
import com.veloster.neondrive.DriveSignals;
import com.veloster.neondrive.CarSprite;
import com.veloster.neondrive.PdSignals;
import com.veloster.vehicle.Telemetry;

/** Synthetic decoding/blinker/model checks for Neon Drive (upstream), its PD signals and i30 sprite; no vehicle capture required. */
public final class NeonDriveTest {
    static int checks;
    static void check(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }
    static byte[] hex(String s) {
        byte[] b = new byte[s.length() / 2];
        for (int i = 0; i < b.length; i++) b[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        return b;
    }
    static boolean near(float a, float b) { return Math.abs(a - b) < 0.01f; }

    public static void main(String[] args) {
        long t = 10_000;
        DriveSignals s = new DriveSignals();
        DriveSignals.Snapshot n = s.snapshot(t);
        check(!n.live && Float.isNaN(n.speedKmh) && n.brake == -1 && !n.leftActive, "empty snapshot is unknown");
        {   // DCT guess (unconfirmed): zero TCU frames (the owner's 6MT) say nothing; a non-zero one or a 7th gear does
            DriveSignals d = new DriveSignals();
            d.record(DriveSignals.TCU11, new byte[8], 1000); d.record(DriveSignals.TCU12, new byte[8], 1000);
            byte[] g = new byte[40]; g[4] = 6; d.gearInfo(g, 1000);
            check(!d.dctSeen(), "6MT: zero TCU frames and 6th gear are no DCT sign");
            byte[] tcu = new byte[8]; tcu[2] = 0x30; d.record(DriveSignals.TCU12, tcu, 1100);
            check(d.dctSeen(), "a non-zero TCU frame is a DCT sign");
            DriveSignals e = new DriveSignals(); g[4] = 7; e.gearInfo(g, 1000);
            check(e.dctSeen(), "7th gear is a DCT sign");
        }

        // CLU11: 9-bit speed x0.5, unit bit
        s.record(DriveSignals.CLU11, hex("80286830"), t);
        n = s.snapshot(t);
        check(n.live && near(n.speedKmh, 20f) && !n.mph, "CLU11 20 km/h");
        s.record(DriveSignals.CLU11, new byte[] {0, (byte) 0xff, 1, 0}, t);
        check(Float.isNaN(s.snapshot(t).speedKmh), "CLU11 raw 511 is invalid");
        s.record(DriveSignals.CLU11, new byte[] {0, 100, 2, 0}, t);
        n = s.snapshot(t);
        check(n.mph && near(n.speedKmh, 50f * 1.609344f), "CLU11 mph converted to km/h");
        s.record(DriveSignals.CLU11, new byte[] {1, 2, 3}, t);
        check(near(s.snapshot(t).speedKmh, 50f * 1.609344f), "wrong length ignored");

        // EMS12: brake switch 1 released / 2 pressed, pedal x0.3906
        byte[] ems = new byte[8];
        ems[4] = 2; ems[6] = 16;
        s.record(DriveSignals.EMS12, ems, t);
        n = s.snapshot(t);
        check(n.brake == 1 && near(n.pedal, 6.2496f), "EMS12 brake pressed, pedal");
        ems[4] = 1; s.record(DriveSignals.EMS12, ems, t);
        check(s.snapshot(t).brake == 0, "EMS12 released");
        ems[4] = 3; s.record(DriveSignals.EMS12, ems, t);
        check(s.snapshot(t).brake == -1, "EMS12 undefined value is unknown");

        // ESP12: 12-bit master cylinder pressure x0.1 bar
        byte[] esp = new byte[8];
        int raw = 283; esp[3] = (byte) ((raw & 0x3f) << 2); esp[4] = (byte) ((raw >> 6) & 0x3f);
        s.record(DriveSignals.ESP12, esp, t);
        check(near(s.snapshot(t).brakeBar, 28.3f), "ESP12 cylinder pressure");

        // SAS11: signed little-endian x0.1 deg, raw positive = left; snapshot reports +right
        s.record(DriveSignals.SAS11, new byte[] {(byte) 0xe6, 0x06, 0x14, 0, 0}, t); // raw +176.6
        check(near(s.snapshot(t).steerDeg, -176.6f), "SAS11 left turn reported as negative (left)");
        s.record(DriveSignals.SAS11, new byte[] {(byte) 0xe8, (byte) 0xff, 0, 0, 0}, t); // raw -2.4
        check(near(s.snapshot(t).steerDeg, 2.4f), "SAS11 small right");
        s.record(DriveSignals.SAS11, new byte[] {(byte) 0xff, 0x7f, 0, 0, 0}, t);
        check(Float.isNaN(s.snapshot(t).steerDeg), "SAS11 0x7fff invalid");
        s.record(DriveSignals.SAS11, new byte[] {0, 0, 0, 0}, t);
        check(Float.isNaN(s.snapshot(t).steerDeg), "SAS11 wrong length ignored");
        s.record(DriveSignals.SAS11, new byte[] {0, 0, 0, 0, 0}, t);
        check(s.snapshot(t).steerDeg == 0f && !(1f / s.snapshot(t).steerDeg < 0), "SAS11 zero has no negative sign");

        // CGW1 samples with lamps off; low beam bit in byte3
        s.record(DriveSignals.CGW1, hex("03448180c0000000"), t);
        n = s.snapshot(t);
        check(n.lowBeam && !n.leftActive && !n.rightActive && !n.hazardSwitch, "CGW1 low beam, lamps off");
        s.record(DriveSignals.CGW1, hex("0340810000000010"), t);
        check(!s.snapshot(t).lowBeam, "CGW1 low beam off");

        // Left lamp blinking: lit flag mirrors the lamp, active holds across the off phase.
        DriveSignals b = new DriveSignals();
        byte[] on = hex("0344898000000000"), off = hex("0344818000000000");
        b.record(DriveSignals.CGW1, on, t);
        n = b.snapshot(t);
        check(n.leftActive && n.leftLit && n.lampPhaseLive && !n.rightActive, "left lamp lit");
        b.record(DriveSignals.CGW1, off, t + 380);
        n = b.snapshot(t + 400);
        check(n.leftActive && !n.leftLit && n.lampPhaseLive, "left lamp off phase keeps active");
        b.record(DriveSignals.CGW1, off, t + 1050);
        check(b.snapshot(t + 1050).leftActive, "still active within hold");
        b.record(DriveSignals.CGW1, off, t + 1200);
        check(!b.snapshot(t + 1200).leftActive, "inactive after hold");

        // Right lamp lives in byte7 bits 6-7
        b.record(DriveSignals.CGW1, hex("0344818000000040"), t + 2000);
        n = b.snapshot(t + 2000);
        check(n.rightActive && n.rightLit && !n.leftActive, "right lamp lit");

        // Hazard switch alone: both active, view self-times the blink
        DriveSignals h = new DriveSignals();
        h.record(DriveSignals.CGW1, hex("03448180c2000000"), t);
        n = h.snapshot(t);
        check(n.hazardSwitch && n.leftActive && n.rightActive && !n.lampPhaseLive, "hazard switch without lamp phase");

        // Stalk switch alone (byte5 bits 1/2)
        DriveSignals st = new DriveSignals();
        st.record(DriveSignals.CGW1, hex("0344818000040000"), t);
        n = st.snapshot(t);
        check(n.rightActive && !n.leftActive && !n.lampPhaseLive, "right stalk switch");

        // Freshness
        n = s.snapshot(t + DriveSignals.FRESH_MS);
        check(!n.live && Float.isNaN(n.speedKmh) && n.brake == -1 && !n.lowBeam, "stale after FRESH_MS");
        s.clear();
        check(!s.snapshot(t).live, "clear forgets readings");

        // Model: live braking ramps the brake light, waiting/demo modes, lift-off pops.
        DriveModel m = new DriveModel();
        DriveSignals live = new DriveSignals();
        DriveModel.State state = null;
        for (int i = 0; i < 15; i++) {
            long now = t + i * 33;
            live.record(DriveSignals.CLU11, new byte[] {0, 80, 0, 0}, now);
            byte[] e = new byte[8]; e[4] = 2; live.record(DriveSignals.EMS12, e, now);
            state = m.update(live, now, 0.033f);
        }
        check(state.mode == DriveModel.LIVE && state.brake == 1f && state.speedKmh > 30f, "live braking at full brightness");
        // Switch pressed with tiny pressure still lights fully on the very first frame; released turns off.
        DriveModel lamps = new DriveModel();
        DriveSignals lampSig = new DriveSignals();
        byte[] pressed = new byte[8]; pressed[4] = 2;
        lampSig.record(DriveSignals.EMS12, pressed, t);
        byte[] esp0 = new byte[8]; esp0[3] = (byte) (3 << 2); lampSig.record(DriveSignals.ESP12, esp0, t); // 0.3 bar
        check(lamps.update(lampSig, t, 0.033f).brake == 1f, "brake switch lights full immediately");
        byte[] released = new byte[8]; released[4] = 1;
        lampSig.record(DriveSignals.EMS12, released, t + 33);
        DriveModel.State lampOff = null;
        for (int i = 1; i <= 10; i++) lampOff = lamps.update(lampSig, t + i * 33, 0.033f);
        check(lampOff.brake < 0.01f, "released switch turns the lamps off");
        // Unknown switch: resting pressure does not light, real pressure does.
        DriveSignals pressureOnly = new DriveSignals();
        pressureOnly.record(DriveSignals.ESP12, esp0, t);
        check(new DriveModel().update(pressureOnly, t, 0.033f).brake == 0f, "0.3 bar alone stays off");
        byte[] esp2 = new byte[8]; esp2[3] = (byte) ((20 & 0x3f) << 2); pressureOnly.record(DriveSignals.ESP12, esp2, t + 1);
        check(new DriveModel().update(pressureOnly, t + 1, 0.033f).brake == 1f, "2.0 bar without switch lights full");
        state = m.update(live, t + 5000, 0.033f);
        check(state.mode == DriveModel.WAITING, "stale stream falls back to waiting cruise");
        // Steering: road bends only while moving; the car still shows the wheel when parked.
        DriveModel sm = new DriveModel();
        DriveSignals ss = new DriveSignals();
        for (int i = 0; i < 40; i++) {
            long at = t + i * 33;
            ss.record(DriveSignals.CLU11, new byte[] {0, 0, 0, 0}, at);
            ss.record(DriveSignals.SAS11, new byte[] {(byte) 0xa4, (byte) 0xfa, 0, 0, 0}, at); // raw -137.2 = right
            state = sm.update(ss, at, 0.033f);
        }
        check(state.steer > 0.8f && state.bend == 0f && state.yaw > 0.25f && near(state.shownSteer, 137.2f), "parked right steer");
        for (int i = 40; i < 80; i++) {
            long at = t + i * 33;
            ss.record(DriveSignals.CLU11, new byte[] {0, 120, 0, 0}, at);
            ss.record(DriveSignals.SAS11, new byte[] {(byte) 0x5c, 0x05, 0, 0, 0}, at); // raw +137.2 = left
            state = sm.update(ss, at, 0.033f);
        }
        check(state.steer < -0.8f && state.bend < -0.7f && state.yaw < -0.7f, "moving left steer bends the road left");
        DriveModel d = new DriveModel();
        d.setFeedAvailable(false);
        check(d.update(null, t, 0.033f).mode == DriveModel.DEMO, "demo when the stream is not available");
        DriveModel forced = new DriveModel();
        forced.setDemoForced(true);
        check(forced.update(live, t, 0.033f).mode == DriveModel.DEMO, "demo can be forced");
        DriveModel pops = new DriveModel();
        DriveSignals ps = new DriveSignals();
        long now = t;
        for (int i = 0; i < 40; i++, now += 33) {
            byte[] e = new byte[8]; e[4] = 1; e[6] = (byte) 240;
            ps.record(DriveSignals.EMS12, e, now); ps.record(DriveSignals.CLU11, new byte[] {0, (byte) 200, 0, 0}, now);
            pops.update(ps, now, 0.033f);
        }
        byte[] e = new byte[8]; e[4] = 1; e[6] = 0;
        ps.record(DriveSignals.EMS12, e, now); ps.record(DriveSignals.CLU11, new byte[] {0, (byte) 200, 0, 0}, now);
        check(pops.update(ps, now, 0.033f).pop > 0.9f, "lift-off pop");
        // Light switch: off/tail/head/auto, tail lamps on with tail, head, or a lit low beam.
        DriveSignals lsig = new DriveSignals();
        lsig.record(DriveSignals.CGW1, hex("0340810000000010"), t);      // parked capture: switch 0, low beam 0
        n = lsig.snapshot(t);
        check(n.lightSwitch == 0 && n.lampsKnown && !n.lamps, "switch off, lamps off");
        lsig.record(DriveSignals.CGW1, hex("03448180c0000000"), t);      // driving capture: switch 3 (auto), low beam 1
        n = lsig.snapshot(t);
        check(n.lightSwitch == 3 && n.lamps, "auto with low beam lit counts as lamps on");
        lsig.record(DriveSignals.CGW1, hex("0344810040000000"), t);      // switch 1 (tail), low beam 0
        check(lsig.snapshot(t).lamps, "tail position lights the tail lamps");
        lsig.record(DriveSignals.CGW1, hex("03448100c0000000"), t);      // auto, low beam 0 (daylight)
        check(!lsig.snapshot(t).lamps, "auto in daylight: lamps off");
        check(!lsig.snapshot(t + DriveSignals.FRESH_MS).lampsKnown, "stale CGW1: lamps unknown");
        DriveModel lm = new DriveModel();
        check(!lm.update(lsig, t, 0.033f).lamps, "model follows lamps off");
        check(lm.update(lsig, t + DriveSignals.FRESH_MS + 10, 0.033f).lamps, "unknown lamps count as on (night)");
        // HUD brake gauge follows the cylinder pressure, not the on/off lamp switch.
        check(Math.abs(com.veloster.neondrive.DriveModel.brakeGauge(25f) - 0.25f) < 1e-6f, "25 bar: 25 % like the N dashboard");
        check(com.veloster.neondrive.DriveModel.brakeGauge(-1f) == 0f && com.veloster.neondrive.DriveModel.brakeGauge(150f) == 1f, "gauge clamps 0..100 bar");
        // Gear from the OEM N-mode packet (byte 4, byte 5 = 1 no gear) and reverse from the rear view.
        DriveSignals gs = new DriveSignals();
        check(gs.snapshot(5000).gear == DriveSignals.GEAR_UNKNOWN, "no gear packet: unknown");
        byte[] nadd = new byte[40]; nadd[4] = 3;
        gs.gearInfo(nadd, 5000);
        check(gs.snapshot(5100).gear == 3, "gear 3");
        nadd[5] = 1; gs.gearInfo(nadd, 5200);
        check(gs.snapshot(5300).gear == DriveSignals.GEAR_NEUTRAL, "byte5=1: no forward gear");
        nadd[5] = 0; nadd[4] = 0; gs.gearInfo(nadd, 5400);
        check(gs.snapshot(5500).gear == DriveSignals.GEAR_NEUTRAL, "gear 0: neutral");
        gs.rearView(1);
        check(gs.snapshot(5500).gear == DriveSignals.GEAR_R, "rear view 1: reverse");
        gs.rearView(0);
        check(gs.snapshot(5400 + 600_000).gear == DriveSignals.GEAR_NEUTRAL, "no new packets: last gear held");
        gs.gearInfo(new byte[8], 9000);
        check(gs.snapshot(9001).gear == DriveSignals.GEAR_NEUTRAL, "short packet ignored, last gear kept");
        nadd = new byte[40]; nadd[4] = 8; gs.gearInfo(nadd, 9100);
        check(gs.snapshot(9101).gear == 8, "DCT gear 8 accepted");
        nadd[4] = 9; gs.gearInfo(nadd, 9200);
        check(gs.snapshot(9201).gear == DriveSignals.GEAR_NEUTRAL, "gear 9: no forward gear");
        nadd[4] = 7; gs.gearInfo(nadd, 9300);
        check(gs.snapshot(9301).gear == 7 && gs.snapshot(9301).gear != DriveSignals.GEAR_R, "gear 7 is not reverse");
        check(DriveModel.demoGear8(0) == 0 && DriveModel.demoGear8(20) == 2 && DriveModel.demoGear8(150) == 7, "demo DCT gears (7-speed)");
        check(DriveModel.demoGear(0) == 0 && DriveModel.demoGear(50) == 3 && DriveModel.demoGear(150) == 6, "demo gears");
        // Standstill: park brake bit, stop timer and trip statistics.
        DriveSignals pks = new DriveSignals(); DriveModel pm = new DriveModel();
        byte[] cgwPark = {0x03, 0x44, (byte) 0x81, 0x00, (byte) 0xc0, 0, 0, 0x10};
        long pt0 = 20000;
        DriveModel.State pst = null;
        for (long tt = pt0; tt < pt0 + 10000; tt += 33) {
            int praw = tt < pt0 + 3600 ? 72 * 2 : 0;
            pks.record(DriveSignals.CLU11, new byte[] {0, (byte) praw, (byte) ((praw >> 8) & 1), 0}, tt);
            pks.record(DriveSignals.CGW1, tt < pt0 + 3600 ? new byte[] {0x03, 0x44, (byte) 0x81, 0x00, (byte) 0xc0, 0, 0, 0} : cgwPark, tt);
            pst = pm.update(pks, tt, 0.033f);
        }
        check(pks.snapshot(pt0 + 10050).parkBrake, "CGW1 b7 bit4: park brake");
        check(pst.stoppedFor > 5.5f && pst.stoppedFor < 7f, "stopped for ~6.4 s: " + pst.stoppedFor);
        check(pst.parked && pst.park > 0.9f, "parked, at the shoulder");
        check(Math.abs(pst.tripKm - 0.072) < 0.003 && Math.abs(pst.maxKmh - 72f) < 0.1f, "trip 72 km/h for 3.6 s = 0.072 km: " + pst.tripKm);
        check(Math.abs(pst.avgKmh() - 72f) < 1.5f, "average uses moving time: " + pst.avgKmh());
        // HUD pixel font: glyph shapes and widths.
        check(com.veloster.neondrive.PixelFont.lit('1', 2, 0) && !com.veloster.neondrive.PixelFont.lit('1', 0, 0), "pixel font '1' top");
        check(com.veloster.neondrive.PixelFont.lit('k', 0, 3) == com.veloster.neondrive.PixelFont.lit('K', 0, 3), "lower case maps to upper case");
        check(com.veloster.neondrive.PixelFont.widthCells("KM/H") == 23 && com.veloster.neondrive.PixelFont.supports("22:51 R12°"), "pixel font width and coverage");
        check(!com.veloster.neondrive.PixelFont.supports("11월"), "Korean falls back to the regular face");
        // Time of day: fixed modes, and auto from the clock against sunrise/sunset (11/24: 07:17 / 17:16).
        double rise = 7 * 60 + 17, set = 17 * 60 + 16;
        check(com.veloster.neondrive.Theme.resolve(4, 12 * 60, rise, set) == com.veloster.neondrive.Theme.NIGHT, "mode 4 forces night");
        check(com.veloster.neondrive.Theme.resolve(1, 0, rise, set) == com.veloster.neondrive.Theme.MORNING, "mode 1 forces morning");
        check(com.veloster.neondrive.Theme.resolve(0, 6 * 60 + 40, rise, set) == com.veloster.neondrive.Theme.NIGHT, "06:40 before dawn window: night");
        check(com.veloster.neondrive.Theme.resolve(0, 6 * 60 + 50, rise, set) == com.veloster.neondrive.Theme.MORNING, "06:50: morning");
        check(com.veloster.neondrive.Theme.resolve(0, 7 * 60 + 17, rise, set) == com.veloster.neondrive.Theme.MORNING, "sunrise: morning");
        check(com.veloster.neondrive.Theme.resolve(0, 8 * 60 + 50, rise, set) == com.veloster.neondrive.Theme.DAY, "08:50: day");
        check(com.veloster.neondrive.Theme.resolve(0, 12 * 60, rise, set) == com.veloster.neondrive.Theme.DAY, "noon: day");
        check(com.veloster.neondrive.Theme.resolve(0, 16 * 60 + 20, rise, set) == com.veloster.neondrive.Theme.SUNSET, "16:20: sunset");
        check(com.veloster.neondrive.Theme.resolve(0, 17 * 60 + 40, rise, set) == com.veloster.neondrive.Theme.SUNSET, "17:40: sunset");
        check(com.veloster.neondrive.Theme.resolve(0, 17 * 60 + 50, rise, set) == com.veloster.neondrive.Theme.NIGHT, "17:50: night");
        check(com.veloster.neondrive.Theme.resolve(0, 23 * 60, rise, set) == com.veloster.neondrive.Theme.NIGHT, "23:00: night");
        check(com.veloster.neondrive.Theme.resolve(0, 12 * 60, Double.NaN, Double.NaN) == com.veloster.neondrive.Theme.DAY, "unknown sunrise/sunset: fixed fallback");
        // Sun position (Seoul, KST): published sunrise/sunset times, +-4 minutes.
        java.util.TimeZone kst = java.util.TimeZone.getTimeZone("Asia/Seoul");
        java.util.Calendar cal = java.util.Calendar.getInstance(kst);
        double S_LAT = com.veloster.neondrive.SolarClock.SEOUL_LAT, S_LON = com.veloster.neondrive.SolarClock.SEOUL_LON;
        int[][] known = {{2024, 6, 21, 5 * 60 + 11, 19 * 60 + 57}, {2024, 12, 21, 7 * 60 + 43, 17 * 60 + 17}, {2024, 3, 20, 6 * 60 + 35, 18 * 60 + 44}};
        for (int[] k : known) {
            cal.clear(); cal.set(k[0], k[1] - 1, k[2], 10, 0);
            double[] rs = com.veloster.neondrive.SolarClock.sunriseSunset(cal.getTimeInMillis(), kst, S_LAT, S_LON);
            check(Math.abs(rs[0] - k[3]) <= 4 && Math.abs(rs[1] - k[4]) <= 4,
                "Seoul " + k[0] + "-" + k[1] + "-" + k[2] + " sunrise " + com.veloster.neondrive.SolarClock.hhmm(rs[0]) + " sunset " + com.veloster.neondrive.SolarClock.hhmm(rs[1]));
        }
        cal.clear(); cal.set(2024, 5, 21, 12, 30);   // around solar noon at the June solstice: about 76 deg
        double[] noon = com.veloster.neondrive.SolarClock.position(cal.getTimeInMillis(), S_LAT, S_LON);
        check(noon[0] > 74 && noon[0] < 77, "solstice noon altitude " + noon[0]);
        cal.clear(); cal.set(2024, 11, 21, 7, 0);      // before sunrise in December
        double[] dawn = com.veloster.neondrive.SolarClock.position(cal.getTimeInMillis(), S_LAT, S_LON);
        check(dawn[0] < 0 && dawn[1] < 0, "december 07:00 sun below horizon, before noon");
        // i30 PD: the dashboard's Telemetry (hscanPd frames) through PdSignals.
        Telemetry tel = new Telemetry();
        PdSignals pd = new PdSignals(tel);
        long p0 = 50_000;
        check(!pd.snapshot(p0).live && pd.snapshot(p0).gear == DriveSignals.GEAR_UNKNOWN, "PD: nothing received is unknown");
        tel.hscanPd(0xc7, 0x02, new byte[] {0x00, (byte) 0x84, 0, 0x40}, p0);                // CLU11 66 km/h
        tel.hscanPd(0xc7, 0x09, new byte[] {0, 100, 0, 0, 2, 0, (byte) 128, 0}, p0);         // EMS12 brake pressed, pedal 50 %
        tel.hscanPd(0xc7, 0x19, new byte[] {(byte) 0x2c, (byte) 0x01, 0, 0, 0}, p0);         // SAS11 +30.0 raw = left
        byte[] cgw = new byte[8];
        cgw[2] = 1 << 3; cgw[3] = (byte) 0x80;                                                // left lamp lit, low beam
        tel.hscanPd(0xc7, 0x01, cgw, p0);
        n = pd.snapshot(p0 + 10);
        check(n.live && near(n.speedKmh, 66f) && n.brake == 1 && near(n.pedal, 50f), "PD: speed, brake switch, pedal");
        check(near(n.steerDeg, -30f), "PD: SAS left shows negative (left), like upstream");
        check(n.leftActive && n.leftLit && n.lampPhaseLive && !n.rightActive && !n.hazardSwitch, "PD: left lamp lit is the live phase");
        check(n.lowBeam && n.lamps && n.lampsKnown && n.lightSwitch == 2, "PD: low beam lights the lamps");
        cgw[2] = 0; tel.hscanPd(0xc7, 0x01, cgw, p0 + 400);
        n = pd.snapshot(p0 + 410);
        check(n.leftActive && !n.leftLit, "PD: off phase keeps the signal active");
        check(!pd.snapshot(p0 + 1600).leftActive, "PD: no lit phase for longer than the hold ends it");
        check(!pd.snapshot(p0 + 400 + DriveSignals.FRESH_MS).live, "PD: stale after FRESH_MS");
        check(new DriveModel().update(pd, p0 + 10, 0.033f).mode == DriveModel.LIVE, "PD: the model runs live from Telemetry");

        // The i30 sprite from the Arcade frames: 13 angles, sheet laid out as the renderer reads it.
        int[] body = {0xff111111, 0xff222222, 0xff333333, 0xff444444};
        CarSprite car = CarSprite.i30(body);
        check(car.frames == 13 && car.angle[0] == -24 && car.angle[12] == 24 && car.pixels.length == car.sheetW() * car.sheetH(), "i30 sheet size");
        check(car.frameFor(0) == 6 && car.frameFor(-35) == 0 && car.frameFor(35) == 12, "frame for an angle, clamped");
        int bodyPx = 0, brakePx = 0, leftPx = 0, rightPx = 0, wheelPx = 0;
        for (int y = 0; y < car.cellH; y++) for (int x = 0; x < car.cellW; x++) {
            int at = 6 * car.cellW + x, w = car.sheetW();
            if (car.pixels[at + y * w] == body[0]) bodyPx++;
            if (car.pixels[at + (y + car.cellH) * w] != 0) brakePx++;
            if (car.pixels[at + (y + 2 * car.cellH) * w] == 0xffffb020) leftPx++;
            if (car.pixels[at + (y + 3 * car.cellH) * w] == 0xffffb020) rightPx++;
            if (car.pixels[at + (y + CarSprite.WHEEL_ROW * car.cellH) * w] != 0) wheelPx++;
        }
        check(bodyPx > 300 && brakePx > 20 && leftPx > 3 && rightPx > 3 && wheelPx == 0, "straight frame: paint, brake layer, both turn layers, no wheels "
            + bodyPx + "/" + brakePx + "/" + leftPx + "/" + rightPx);
        int[] pt = car.points[6];
        check(pt[0] < car.cellW / 2 && pt[2] > car.cellW / 2 && pt[4] < pt[6] && pt[8] < pt[9] && pt[9] < car.cellH && pt[10] > 0, "lamp, pipe, top/bottom and stop lamp points");
        System.out.println("NeonDriveTest PASS " + checks);
    }
}
