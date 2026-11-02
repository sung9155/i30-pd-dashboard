package com.veloster.vehicle;

/**
 * Seoul medium-taxi meter (news.seoul.go.kr/traffic 1659, in force since 2023-02-01), distance and time combined
 * the way a real meter does it: above 15 km/h the distance counts, at 15 km/h or slower 30 s count as 131 m, never
 * both. The base 4,800 won covers the first 1.6 km of that combined distance, then each 131 m adds 100 won. The night
 * surcharge (+20 % 22-23 and 02-04 h, +40 % 23-02 h) applies to the base at the start and to each step at the time it
 * is charged, so a finished trip's total never changes afterwards. The total is rounded to 100 won. No Android:
 * host-tested.
 */
public final class TaxiFare {
    static final int BASE=4800,STEP=100;
    static final float BASE_M=1600,STEP_M=131,STEP_S=30,SLOW_KMH=15;
    /** Combined distance so far (m). */
    private float meters;
    /** Steps charged past the base, by distance and by time. */
    public int distUnits,timeUnits;
    /** Won from the base and the steps, each with the surcharge in force when it was charged. */
    private float won;

    /** A new trip; sur is the surcharge in force now (0, 0.2 or 0.4). */
    public void start(float sur){meters=0;distUnits=0;timeUnits=0;won=BASE*(1+sur);}
    /** dtSec at kmh (NaN: no reading, nothing counts). */
    public void add(float kmh,float dtSec,float sur){
        if(Float.isNaN(kmh)||dtSec<=0)return;
        boolean slow=kmh<=SLOW_KMH;
        meters+=slow?dtSec/STEP_S*STEP_M:kmh/3.6f*dtSec;
        while(meters>=BASE_M+STEP_M*(distUnits+timeUnits+1)){
            if(slow)timeUnits++;else distUnits++;
            won+=STEP*(1+sur);
        }
    }
    public int total(){return Math.round(won/100f)*100;}
    /** Surcharge part of the total (won). */
    public int surcharge(){return total()-BASE-STEP*(distUnits+timeUnits);}
    /** Combined metres to the next 100 won. */
    public float toNext(){return BASE_M+STEP_M*(distUnits+timeUnits+1)-meters;}
    /** 0..1 progress toward the next step (across the base distance while still inside it). */
    public float progress(){
        int n=distUnits+timeUnits;float from=n==0?0:BASE_M+STEP_M*n,to=BASE_M+STEP_M*(n+1);
        return Math.max(0,Math.min(1,(meters-from)/(to-from)));
    }
}
