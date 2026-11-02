package com.veloster.vehicle;

/**
 * Powertrain variant of the same i30 PD head unit, used only to pick DISPLAY constants (redline, rpm scale, top gear,
 * shift-light range, whether an ignition-timing reading exists). The byte decode is identical for every variant; a
 * message a variant does not send simply never arrives and reads as a dash, because the head unit is receive-only.
 *
 * Verified only on the gasoline 7DCT car (eng.vmadmin.20260114.084130). The 6MT and 1.6 CRDi diesel values here are
 * from published specs, NOT from a capture: on a real diesel the engine's CAN ids differ, so which hscan function
 * slots carry rpm/coolant/boost — and whether a 6MT reports a gear at all — must be confirmed with a capture before
 * these are trusted. {@link Telemetry#profile()} auto-detects the variant from what actually arrives.
 */
public final class VehicleProfile {
    public enum Trans { DCT, MT }
    public enum Fuel { PETROL, DIESEL }

    public final Fuel fuel;
    public final Trans trans;
    public final float redline;      // rpm where the red zone / shift urgency begins
    public final float maxRpm;       // full-scale rpm for bars and gauges
    public final int topGear;        // highest gear; the shift cue never asks to upshift out of it
    public final int shiftMin, shiftMax, shiftDefault;  // adjustable shift-light target range
    public final boolean hasSpark;   // diesels have no ignition timing (EMS16 SPK_TIME)
    public final String label;

    private VehicleProfile(Fuel fuel, Trans trans) {
        this.fuel = fuel; this.trans = trans;
        this.topGear = trans == Trans.MT ? 6 : 7;
        if (fuel == Fuel.DIESEL) {
            this.redline = 4600; this.maxRpm = 5500;
            // The petrol shift range scaled by the redline ratio (4600/6500), rounded to 100 rpm.
            this.shiftMin = 2100; this.shiftMax = 4700; this.shiftDefault = 4200;
            this.hasSpark = false;
        } else {
            this.redline = 6500; this.maxRpm = 8000;
            // 3000-6700: low enough for everyday shifting on the 1.6 T-GDi (torque plateau 1500-4500), up to the limiter.
            this.shiftMin = 3000; this.shiftMax = 6700; this.shiftDefault = 6000;
            this.hasSpark = true;
        }
        this.label = (fuel == Fuel.DIESEL ? "DIESEL" : "PETROL") + " " + (trans == Trans.MT ? "6MT" : "7DCT");
    }

    public static final VehicleProfile PETROL_DCT = new VehicleProfile(Fuel.PETROL, Trans.DCT);
    public static final VehicleProfile PETROL_MT  = new VehicleProfile(Fuel.PETROL, Trans.MT);
    public static final VehicleProfile DIESEL_DCT = new VehicleProfile(Fuel.DIESEL, Trans.DCT);
    public static final VehicleProfile DIESEL_MT  = new VehicleProfile(Fuel.DIESEL, Trans.MT);

    public static VehicleProfile of(Fuel fuel, Trans trans) {
        return fuel == Fuel.PETROL ? (trans == Trans.DCT ? PETROL_DCT : PETROL_MT)
                                   : (trans == Trans.DCT ? DIESEL_DCT : DIESEL_MT);
    }
}
