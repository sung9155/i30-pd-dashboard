package com.veloster.vehicle;

/**
 * GPS speed calibration (설정 → 진단 → GPS 속도 보정, off by default): learns GPS speed ÷ car speed and the dashboard
 * multiplies the shown speed and the measured runs by it. A sample counts only when the GPS gives its own speed with a
 * fix within MAX_ACC_M, and for the STEADY_MS before it the car held MIN_KMH-MAX_KMH within STEADY_KMH, went straight
 * (yaw under MAX_YAW) and was not braking, and the last two GPS speeds agree within STEADY_KMH: steady, straight,
 * open-sky driving, where a 1 s GPS fix and the CAN speed describe the same thing. The GPS speed is compared with the
 * car's average over the second before the fix (the fix arrives late). The ratio is the median of the last RING
 * samples (a tunnel exit or a bad fix cannot move it), used once MIN_SAMPLES are in; a ratio outside 0.90-1.10 is not a
 * speedometer error and is dropped. The internal sums (6MT gear, learned ratios, shift numbers) keep the car's speed.
 * No Android: host-tested.
 */
public final class SpeedCal {
    public static final int MIN_SAMPLES=120,RING=600;
    public static final float MIN_KMH=40,MAX_KMH=130,STEADY_KMH=2,MAX_YAW=3,MAX_ACC_M=10,LO=0.90f,HI=1.10f;
    public static final long STEADY_MS=3000;
    private final float[] ring=new float[RING];private int n,head;
    private float saved=Float.NaN;private int total;
    // the car's last few seconds, frame by frame
    private static final int HIST=256;
    private final long[] ht=new long[HIST];private final float[] hv=new float[HIST];private final boolean[] hok=new boolean[HIST];private int hn,hi;
    private int lastSeq=-1;private float lastGps=Float.NaN;private long lastGpsAt;

    /** A learned ratio from an earlier drive (saved preferences) and how many samples made it. */
    public void restore(float ratio,int samples){if(ratio>=LO&&ratio<=HI){saved=ratio;total=samples;}}
    public void reset(){n=0;head=0;saved=Float.NaN;total=0;}
    /** The ratio to apply, or NaN while fewer than MIN_SAMPLES have been seen. */
    public float ratio(){
        if(n>=MIN_SAMPLES){float[] a=new float[n];System.arraycopy(ring,0,a,0,n);java.util.Arrays.sort(a);return n%2==1?a[n/2]:(a[n/2-1]+a[n/2])/2;}
        return total>=MIN_SAMPLES?saved:Float.NaN;
    }
    /** Samples behind the ratio, this drive's added to the saved ones. */
    public int samples(){return total;}

    /** Every frame: the car's speed and whether this frame is fit (straight, not braking); then, when gpsSeq moved on,
     *  the GPS speed (km/h, NaN without one) and accuracy (m) of that fix. Returns true when a sample was taken. */
    public boolean update(long now,float carKmh,float yaw,boolean braking,int gpsSeq,float gpsKmh,float gpsAcc){
        boolean ok=!Float.isNaN(carKmh)&&carKmh>=MIN_KMH&&carKmh<=MAX_KMH&&!braking&&!Float.isNaN(yaw)&&Math.abs(yaw)<=MAX_YAW;
        ht[hi]=now;hv[hi]=carKmh;hok[hi]=ok;hi=(hi+1)%HIST;if(hn<HIST)hn++;
        if(gpsSeq==lastSeq)return false;
        lastSeq=gpsSeq;
        float prev=lastGps;long prevAt=lastGpsAt;lastGps=gpsKmh;lastGpsAt=now;
        if(Float.isNaN(gpsKmh)||!(gpsAcc<=MAX_ACC_M)||Float.isNaN(prev)||now-prevAt>1500||Math.abs(gpsKmh-prev)>STEADY_KMH)return false;
        // the car over the last STEADY_MS: every frame fit, within STEADY_KMH, and covering the whole span
        float lo=Float.MAX_VALUE,hiV=-Float.MAX_VALUE,sum=0;int cnt=0;long oldest=now;
        for(int k=1;k<=hn;k++){int j=(hi-k+HIST)%HIST;if(now-ht[j]>STEADY_MS)break;
            if(!hok[j])return false;lo=Math.min(lo,hv[j]);hiV=Math.max(hiV,hv[j]);oldest=ht[j];
            if(now-ht[j]<=1000){sum+=hv[j];cnt++;}}
        if(cnt==0||now-oldest<STEADY_MS-200||hiV-lo>STEADY_KMH)return false;
        float r=gpsKmh/(sum/cnt);
        if(r<LO||r>HI)return false;
        ring[head]=r;head=(head+1)%RING;if(n<RING)n++;total++;
        return true;
    }
}
