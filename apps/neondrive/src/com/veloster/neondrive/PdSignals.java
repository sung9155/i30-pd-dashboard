package com.veloster.neondrive;

import com.veloster.vehicle.Telemetry;

/**
 * Neon Drive's and 내 차's signals on the i30 PD: read from the dashboard's own Telemetry (its hscan decode of the PD
 * frames), not from a second decoder. Upstream decoded the Veloster N's hscand records in DriveSignals.record(); those
 * frames do not exist on the PD, so only the Snapshot is filled here. No Android: host-tested.
 *
 * Mapping: speed (7, km/h, the GPS calibration applied like every screen), pedal (9), master cylinder (10, bar), brake
 * switch (EMS12), steering (SAS11, shown + right), turn lamps (CGW1 bits that blink with the lamp: the real lamp phase),
 * low beam (HEAD_LAMP), tail lamps (TAIL_LAMP), parking brake (EPB11), gear (8: 1..7, 0 N, 14 R; the rear camera or the
 * lever at R reads as reverse). No hazard switch is decoded on the PD: both lamps blinking counts as hazards.
 */
public final class PdSignals extends DriveSignals {
    private final Telemetry t;
    private long leftOnAt = Long.MIN_VALUE, rightOnAt = Long.MIN_VALUE;

    public PdSignals(Telemetry t) { this.t = t; }

    /** A sample received within FRESH_MS, else null (Telemetry's own staleness is a minute: too slow for lamps). */
    private Telemetry.Sample fresh(int i, long now) {
        Telemetry.Sample x = t.sample(i, now);
        return x.valid && x.ageMs < FRESH_MS ? x : null;
    }

    private static boolean on(Telemetry.Sample x) { return x != null && x.value >= 0.5f; }

    /** The LVR12 gear lever only comes from a DCT car (Telemetry.profile's rule). */
    @Override public boolean dctSeen() { return t.sample(Telemetry.GEAR_SELECTOR, 0).seen; }
    /** The dashboard's car profile (6MT or 7DCT): the gear display follows it. */
    @Override public int transmission() { return t.profile().trans == com.veloster.vehicle.VehicleProfile.Trans.DCT ? 1 : 0; }

    @Override public synchronized Snapshot snapshot(long now, Snapshot s) {
        Telemetry.Sample speed = fresh(7, now), pedal = fresh(9, now), bar = fresh(10, now), brake = fresh(Telemetry.BRAKE_SWITCH, now),
            steer = fresh(Telemetry.STEERING, now), left = fresh(Telemetry.TURN_LEFT, now), right = fresh(Telemetry.TURN_RIGHT, now),
            head = fresh(Telemetry.HEAD_LAMP, now), tail = fresh(Telemetry.TAIL_LAMP, now), park = fresh(Telemetry.PARK_BRAKE, now),
            gear = t.sample(8, now), lever = t.sample(Telemetry.GEAR_SELECTOR, now);   // the gear holds like the dashboard's (a minute)
        if (!gear.valid) gear = null;
        if (!lever.valid) lever = null;
        boolean cgw = left != null || right != null;
        s.live = speed != null || pedal != null || cgw || steer != null;
        s.speedKmh = speed == null ? Float.NaN : speed.value * Telemetry.speedScale;
        s.mph = false;
        s.pedal = pedal == null ? Float.NaN : pedal.value;
        s.brake = brake == null ? -1 : on(brake) ? 1 : 0;
        s.brakeBar = bar == null ? Float.NaN : bar.value;
        s.steerDeg = steer == null ? Float.NaN : Telemetry.displayValue(Telemetry.STEERING, steer.value);
        s.leftLit = on(left); s.rightLit = on(right);
        if (s.leftLit) leftOnAt = now;
        if (s.rightLit) rightOnAt = now;
        boolean lb = cgw && leftOnAt != Long.MIN_VALUE && now - leftOnAt < BLINK_HOLD_MS;
        boolean rb = cgw && rightOnAt != Long.MIN_VALUE && now - rightOnAt < BLINK_HOLD_MS;
        s.leftActive = lb; s.rightActive = rb;
        s.lampPhaseLive = lb || rb;
        s.hazardSwitch = lb && rb;
        s.lowBeam = on(head);
        s.lampsKnown = head != null || tail != null;
        s.lamps = s.lowBeam || on(tail);
        s.lightSwitch = !s.lampsKnown ? -1 : s.lowBeam ? 2 : on(tail) ? 1 : 0;
        s.parkBrake = on(park);
        int g = gear == null ? -1 : Math.round(gear.value);
        if (t.rearCamera() || lever != null && Math.round(lever.value) == 1 || g == 14) s.gear = GEAR_R;
        else if (g >= 1 && g <= 8) s.gear = g;
        else if (g == 0 || lever != null) s.gear = GEAR_NEUTRAL;   // P / N, or D before a gear number arrives
        else s.gear = GEAR_UNKNOWN;
        s.records = s.live ? 1 : 0;
        return s;
    }
}
