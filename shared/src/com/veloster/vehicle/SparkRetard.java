package com.veloster.vehicle;

/**
 * Estimated spark retard for the knock monitor. No knock-sensor value reaches the head unit, so at full load
 * (pedal > 70 %, boost > 0.3 bar) the spark is compared with the full-load line seen on our 10/30 drive,
 * about 13 - 9 x boost degrees BTDC (heavy load read 2.3 at 1.2 bar). Later than the line counts as retard.
 * ponytail: one straight line from one car; a tuned car may sit off it. No Android: host-tested.
 */
public final class SparkRetard {
    private SparkRetard(){}
    public static float reference(float boost){return 13-9*Math.max(0,boost);}
    /** Degrees later than the full-load line, 0 off full load or when a value is missing. */
    public static float estimate(float spark,float boost,float pedal){
        if(Float.isNaN(spark)||Float.isNaN(boost)||Float.isNaN(pedal)||pedal<=70||boost<=0.3f)return 0;
        return Math.max(0,reference(boost)-spark);
    }
}
