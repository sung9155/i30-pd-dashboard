package com.veloster.vehicle;

/** Adjustable display advisory, NOT a measured optimal-shift map or ECU control. */
public final class ShiftGuide {
    public static int clamp(int rpm,int min,int max) { return Math.max(min,Math.min(max,rpm/100*100)); }
    /** The saved target inside the profile's range; 0 (never set) is the profile's default (diesel 4200, not 6000). */
    public static int target(int saved,VehicleProfile p) { return saved<=0?p.shiftDefault:clamp(saved,p.shiftMin,p.shiftTop()); }
    public static final class Cue {
        public final int leds;
        public final boolean shift;
        Cue(int n,boolean s) { leds=n;shift=s; }
    }
    /** When the optional shift beep sounds: on the cue's rising edge only, and not again within MIN_GAP ms, so rpm
     *  hovering at the target does not chirp on every flicker. Pure, host-tested; ShiftBeep plays it. */
    public static final class Beep {
        public static final long MIN_GAP=1500;
        private boolean was;private long lastAt=Long.MIN_VALUE/2;
        public boolean fire(boolean shift,long now){
            boolean edge=shift&&!was;was=shift;
            if(!edge||now-lastAt<MIN_GAP)return false;
            lastAt=now;return true;
        }
    }
    public static Cue evaluate(Telemetry.Sample[] s,int target) { return evaluate(s,target,VehicleProfile.PETROL_DCT); }
    public static Cue evaluate(Telemetry.Sample[] s,int target,VehicleProfile p) {
        target=clamp(target,p.shiftMin,p.shiftTop());
        boolean fresh=s[0].valid && s[0].ageMs<=500;
        // 15 LEDs over the last quarter below the target: 4500-6000 at 6000 (100 rpm each, as before), 2250-3000 at
        // 3000, so a low everyday target does not light the bar from idle.
        float window=target/4f;
        int leds=fresh?Math.max(0,Math.min(15,(int)Math.floor((s[0].value-(target-window))/(window/15))+1)):0;
        boolean moving=s[7].valid && s[7].ageMs<=500 && s[7].value>0;
        // No upshift cue out of the top gear: 7th on the DCT, 6th on the 6MT (profile.topGear).
        boolean gear=s[8].valid && s[8].ageMs<=500 && s[8].value>=1 && s[8].value<p.topGear;
        return new Cue(leds,fresh && moving && gear && s[0].value>=target);
    }
}
