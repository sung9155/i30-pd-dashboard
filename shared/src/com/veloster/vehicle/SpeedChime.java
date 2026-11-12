package com.veloster.vehicle;

/**
 * 과속 차임: like the speed chime of Japanese-market cars of the 1980s (the AE86's "킨콘"), a bell that keeps ringing while
 * the car is over a set speed. Off by default; the speed is picked in 설정 → 사운드. It starts at the speed and stops
 * 3 km/h under it, so it does not stutter on the edge, and rings once every PERIOD_MS. No Android: host-tested.
 */
public final class SpeedChime {
    /** The choices, km/h; 0 = off. 105 is the old Japanese limit the original chime was set to. */
    public static final int[] KMH={0,100,105,110,120};
    static final long PERIOD_MS=1000;
    static final float HYSTERESIS=3;
    public static volatile int kmh=0;
    private boolean over;private long last=Long.MIN_VALUE/2;

    /** True when a chime is due now: speed (km/h, NaN = unknown) at or over the set speed, a period since the last. */
    public boolean due(float speed,long now){
        int at=kmh;
        if(at<=0||Float.isNaN(speed)){over=false;return false;}
        over=over?speed>=at-HYSTERESIS:speed>=at;
        if(!over||now-last<PERIOD_MS)return false;
        last=now;return true;
    }
    /** The next choice after the current one (a tap on the settings row). */
    public static int next(int current){for(int i=0;i<KMH.length;i++)if(KMH[i]==current)return KMH[(i+1)%KMH.length];return KMH[0];}
}
