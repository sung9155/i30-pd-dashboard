package com.veloster.vehicle;

/**
 * A rally pace-note call from the wheel and the car: severity 1 (tightest, a hairpin) to 6 (barely a bend), signed by
 * direction (+ right, - left), 0 = straight. The steering wheel angle sets the severity; without it the bend radius
 * from lateral G and speed (r = v^2 / a) does. No Android: host-tested.
 * ponytail: the bands are a guess for the PD's ~13:1 rack, tune them from a drive log.
 */
public final class PaceNote {
    private PaceNote(){}
    /** Wheel angle bands (deg, upper edges) for 6..2; past the last is 1. */
    static final float[] STEER={30,55,90,140,220};
    /** Bend radius bands (m, lower edges) for 6..2; under the last is 1. */
    static final float[] RADIUS={200,120,70,40,20};
    static final float STRAIGHT_DEG=12,STRAIGHT_G=0.08f,MIN_KMH=5;
    /** Lateral G from which the call gets a caution mark. */
    public static final float CAUTION_G=0.6f;

    /** steer: displayed wheel angle (deg, + right); lat: lateral G (+ in a left bend); kmh: speed. NaN = not received. */
    public static int of(float steer,float lat,float kmh){
        if(!Float.isNaN(kmh)&&kmh<MIN_KMH)return 0;   // parking, not a stage
        if(!Float.isNaN(steer)){
            float a=Math.abs(steer);if(a<STRAIGHT_DEG)return 0;
            int sev=1;for(int i=0;i<STEER.length;i++)if(a<STEER[i]){sev=6-i;break;}
            return steer>0?sev:-sev;
        }
        if(Float.isNaN(lat)||Float.isNaN(kmh)||Math.abs(lat)<STRAIGHT_G)return 0;
        float v=kmh/3.6f,r=v*v/(Math.abs(lat)*9.81f);
        int sev=1;for(int i=0;i<RADIUS.length;i++)if(r>=RADIUS[i]){sev=6-i;break;}
        return lat>0?-sev:sev;   // a left bend throws the car right: +lat
    }
    /** "3 L", "5 R", or "" for straight. */
    public static String text(int note){return note==0?"":Math.abs(note)+(note<0?" L":" R");}
}
