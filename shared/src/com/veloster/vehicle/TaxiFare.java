package com.veloster.vehicle;

/**
 * Medium-taxi meter on one area's table (TaxiTariff, Seoul until told otherwise), distance and time combined the way
 * a real meter does it: above 15 km/h the distance counts, at 15 km/h or slower each stepS seconds count as stepM,
 * never both. The base fare covers the first baseM of that combined distance, then each stepM adds a step (100 won,
 * 제주 120 past 20 km). The surcharge (night, out of area) applies to the base at the start and to each step at the
 * time it is charged, so a finished trip's total never changes afterwards. The total is rounded to 100 won. No
 * Android: host-tested.
 */
public final class TaxiFare {
    static final float SLOW_KMH=15;
    private TaxiTariff tariff=TaxiTariff.of("서울");
    /** Combined distance so far (m). */
    private float meters;
    /** Steps charged past the base, by distance and by time, and their won before surcharges. */
    public int distUnits,timeUnits,distWon,timeWon;
    /** Won from the base and the steps, each with the surcharge in force when it was charged. */
    private float won;

    /** A new trip on table t; sur is the surcharge in force now (TaxiTariff.surcharge). */
    public void start(TaxiTariff t,float sur){tariff=t;meters=0;distUnits=0;timeUnits=0;distWon=0;timeWon=0;won=t.base*(1+sur);}
    /** A new trip on the same table. */
    public void start(float sur){start(tariff,sur);}
    public TaxiTariff tariff(){return tariff;}
    /** dtSec at kmh (NaN: no reading, nothing counts). */
    public void add(float kmh,float dtSec,float sur){
        if(Float.isNaN(kmh)||dtSec<=0)return;
        boolean slow=kmh<=SLOW_KMH;
        meters+=slow?dtSec/tariff.stepS*tariff.stepM:kmh/3.6f*dtSec;
        while(meters>=tariff.baseM+tariff.stepM*(distUnits+timeUnits+1)){
            int w=tariff.stepWon(tariff.baseM+tariff.stepM*(distUnits+timeUnits));
            if(slow){timeUnits++;timeWon+=w;}else{distUnits++;distWon+=w;}
            won+=w*(1+sur);
        }
    }
    /** Base and steps, plus the surcharges rounded to 100 won (Seoul at night: 4,800 + 960 = 5,800). */
    public int total(){return tariff.base+distWon+timeWon+surcharge();}
    /** Surcharge part of the total (won). */
    public int surcharge(){return Math.round((won-tariff.base-distWon-timeWon)/100f)*100;}
    /** Combined metres to the next step. */
    public float toNext(){return tariff.baseM+tariff.stepM*(distUnits+timeUnits+1)-meters;}
    /** 0..1 progress toward the next step (across the base distance while still inside it). */
    public float progress(){
        int n=distUnits+timeUnits;float from=n==0?0:tariff.baseM+tariff.stepM*n,to=tariff.baseM+tariff.stepM*(n+1);
        return Math.max(0,Math.min(1,(meters-from)/(to-from)));
    }
}
