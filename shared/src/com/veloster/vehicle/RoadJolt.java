package com.veloster.vehicle;

/**
 * Road jolt for the 8-bit chase view. The car has no vertical accelerometer: ESP12 sends lateral and longitudinal G at
 * ~12 Hz, and a bump shows as a one-sample spike (second difference over three samples). Gear shifts and pedal, brake
 * or steering changes make the same spikes, so a spike within 600 ms of one of those is the driver, not the road.
 * ponytail: 0.15 G from the 10/29 city log (240 s moving: 21 jolts, about one every 11 s; 0.08 G gave 67), not yet
 * checked against a known speed bump; tune SPIKE from such a log. No Android: host-tested.
 */
public final class RoadJolt {
    static final float SPIKE=0.15f,PEDAL=8,BRAKE=3,STEER=20;
    static final long QUIET_MS=600;
    private float la0,la1,lo0,lo1;
    private int n;
    private long inputAt=Long.MIN_VALUE/2,refAt=Long.MIN_VALUE/2;
    private float pedRef=Float.NaN,brakeRef=Float.NaN,steerRef=Float.NaN,gearRef=Float.NaN;

    /** One frame (G in g, pedal %, brake bar, steering deg, gear). Returns the spike in g when a new G sample is a road
     * jolt, else 0. Frames that repeat the last G sample count nothing. */
    public float update(long now,float lat,float lon,float pedal,float brake,float steer,float gear){
        if(!Float.isNaN(gear)&&!Float.isNaN(gearRef)&&gear!=gearRef)inputAt=now;
        gearRef=gear;
        if(moved(pedal,pedRef,PEDAL)||moved(brake,brakeRef,BRAKE)||moved(steer,steerRef,STEER)){inputAt=now;refAt=Long.MIN_VALUE/2;}
        if(now-refAt>QUIET_MS){pedRef=pedal;brakeRef=brake;steerRef=steer;refAt=now;}
        if(Float.isNaN(lat)||Float.isNaN(lon)){n=0;return 0;}
        if(n>0&&lat==la1&&lon==lo1)return 0;
        float d=n>=2?Math.max(Math.abs(lat-2*la1+la0),Math.abs(lon-2*lo1+lo0)):0;
        la0=la1;la1=lat;lo0=lo1;lo1=lon;n++;
        return d>SPIKE&&now-inputAt>QUIET_MS?d:0;
    }
    private static boolean moved(float v,float ref,float by){return !Float.isNaN(v)&&!Float.isNaN(ref)&&Math.abs(v-ref)>by;}
}
