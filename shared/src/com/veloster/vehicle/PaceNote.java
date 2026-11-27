package com.veloster.vehicle;

import com.veloster.i18n.UiText;

/**
 * Rally pace notes from the car's own data, for the WRC screen. A Detector watches the turn rate (the ESC's yaw rate,
 * else lateral G over speed, else the wheel angle) and hands back each corner when it ends: where it started (metres
 * along the drive, Commute's), its direction and severity 1 (tightest) to 6 (slight) from the tightest radius
 * (speed over turn rate), and the modifiers 길게 (turned LONG_DEG or more), 조임 / 풀림 (the second half turned
 * TIGHTEN times the first, or the other way) and 주의 (CAUTION_G or more of lateral G). Commute keeps the corners with
 * the drive, so the next time the same road is driven the calls come ahead of the car, like a co-driver's.
 *
 * A corner starts at MIN_KMH and a curvature of 1/ENTER_R, ends under 1/EXIT_R, under OUT_KMH or when the bend turns the
 * other way, and counts when it lasted MIN_S and turned MIN_DEG: lane changes, parking and walking-pace junctions are
 * not corners. No Android: host-tested.
 * ponytail: the thresholds and radius bands are a first guess from physics; tune them from drives on known roads.
 */
public final class PaceNote {
    private PaceNote(){}
    /** Bend radius bands (m, lower edges) for 6..2; under the last is 1. */
    static final float[] RADIUS={200,120,70,40,20};
    static final float MIN_KMH=25,OUT_KMH=20,ENTER_R=300,EXIT_R=450,MIN_S=1,MIN_DEG=15,LONG_DEG=110,TIGHTEN=1.6f,TAU_S=0.4f;
    /** Wheel-angle fallback: the PD's rack (~13:1) and wheelbase (m). */
    static final float RACK=13,WHEELBASE=2.65f;
    /** Lateral G from which a corner gets 주의. */
    public static final float CAUTION_G=0.6f;

    /** Severity 6 (slight) .. 1 (tightest) for a bend radius in metres. */
    public static int severity(float radius){for(int i=0;i<RADIUS.length;i++)if(radius>=RADIUS[i])return 6-i;return 1;}

    /** One corner: start (m along its drive, NaN without GPS), note (severity, + right, - left) and modifiers. */
    public static final class Corner{
        public final float at;public final int note;public final boolean longer,tightens,opens,caution;
        public Corner(float at,int note,boolean longer,boolean tightens,boolean opens,boolean caution){
            this.at=at;this.note=note;this.longer=longer;this.tightens=tightens;this.opens=opens;this.caution=caution;}
        /** "1234R3lc": metres, L/R, severity, then l long, t tightens, o opens, c caution. */
        public String encode(){return Math.round(at)+(note<0?"L":"R")+Math.abs(note)+(longer?"l":"")+(tightens?"t":"")+(opens?"o":"")+(caution?"c":"");}
        public static Corner decode(String s){
            try{int i=0;while(i<s.length()&&Character.isDigit(s.charAt(i)))i++;
                char d=s.charAt(i);int sev=s.charAt(i+1)-'0';if(i==0||sev<1||sev>6||(d!='L'&&d!='R'))return null;
                String f=s.substring(i+2);
                return new Corner(Float.parseFloat(s.substring(0,i)),d=='L'?-sev:sev,f.indexOf('l')>=0,f.indexOf('t')>=0,f.indexOf('o')>=0,f.indexOf('c')>=0);}
            catch(RuntimeException e){return null;}
        }
    }

    /** "우 3" / "R3", "" for 0. */
    public static String call(int note){
        if(note==0)return "";
        return "ko".equals(UiText.language())?(note<0?"좌 ":"우 ")+Math.abs(note):(note<0?"L":"R")+Math.abs(note);
    }
    /** The modifiers: "길게 · 조임", with 주의 when caution is asked for and set; "" when none. */
    public static String extras(Corner c,boolean caution){
        StringBuilder b=new StringBuilder();
        if(c.longer)add(b,"길게");if(c.tightens)add(b,"조임");if(c.opens)add(b,"풀림");if(caution&&c.caution)add(b,"주의");
        return b.toString();
    }
    private static void add(StringBuilder b,String ko){if(b.length()>0)b.append(" · ");b.append(UiText.t(ko));}

    /** Turn rate, deg/s + right, from what the car sends (each NaN when not received); NaN with nothing to go on. */
    static float rate(float kmh,float yaw,float lat,float steer){
        if(Float.isNaN(kmh))return Float.NaN;
        if(!Float.isNaN(yaw))return -yaw;   // ESC yaw, lateral G and the raw wheel angle are all + left on the CAN (2026-11-05 drive log)
        float v=kmh/3.6f;
        if(!Float.isNaN(lat)&&v>3)return (float)Math.toDegrees(-lat*9.81f/v);
        if(!Float.isNaN(steer))return (float)Math.toDegrees(v/WHEELBASE*Math.tan(Math.toRadians(steer/RACK)));   // displayed angle: + right
        return Float.NaN;
    }

    /** Finds corners in a stream of samples (WrcCluster.tick, every frame). Not thread-safe. */
    public static final class Detector{
        private long last=Long.MIN_VALUE;private boolean warm;private float w;
        private int dir;private float at,dur,deg,dist,peakK,peakLat;
        private float[] prof=new float[128];private int n;   // (metres into the corner, degrees turned) per sample
        private final Corner[] recent=new Corner[3];

        /** One sample: frame time (ms), speed (km/h), yaw (deg/s, + left), lateral G (+ left), wheel angle (deg, + right),
         *  metres along the drive (NaN without GPS). Returns the corner that just ended, else null. */
        public Corner feed(long ms,float kmh,float yaw,float lat,float steer,float along){
            if(ms==last)return null;
            float dt=(ms-last)/1000f;
            if(last==Long.MIN_VALUE||dt<0||dt>0.5f){last=ms;warm=false;return end();}   // a gap in the samples: start afresh
            last=ms;
            float rate=rate(kmh,yaw,lat,steer);
            if(Float.isNaN(rate)){warm=false;return end();}
            w=warm?w+(rate-w)*Math.min(1,dt/TAU_S):rate;warm=true;
            float v=kmh/3.6f,k=v>1?(float)Math.toRadians(w)/v:0;
            Corner done=null;
            if(dir!=0){
                if(kmh<OUT_KMH||Math.abs(k)<1/EXIT_R||(k>0?1:-1)!=dir)done=end();
                else{dur+=dt;deg+=Math.abs(w)*dt;dist+=v*dt;peakK=Math.max(peakK,Math.abs(k));if(!Float.isNaN(lat))peakLat=Math.max(peakLat,Math.abs(lat));
                    if(n+2>prof.length)prof=java.util.Arrays.copyOf(prof,prof.length*2);prof[n++]=dist;prof[n++]=deg;}
            }
            if(dir==0&&kmh>=MIN_KMH&&Math.abs(k)>=1/ENTER_R){dir=k>0?1:-1;at=along;dur=deg=dist=peakLat=0;peakK=Math.abs(k);n=0;}
            return done;
        }
        private Corner end(){
            if(dir==0)return null;int d=dir;dir=0;
            if(dur<MIN_S||deg<MIN_DEG)return null;
            float a1=0;for(int i=0;i<n;i+=2){if(prof[i]>dist/2)break;a1=prof[i+1];}
            float a2=deg-a1;
            Corner c=new Corner(at,d*severity(1/peakK),deg>=LONG_DEG,a2>=TIGHTEN*a1,a1>=TIGHTEN*a2,peakLat>=CAUTION_G);
            recent[2]=recent[1];recent[1]=recent[0];recent[0]=c;
            return c;
        }
        /** The corner being driven now (signed severity so far) once it counts as one, else 0. */
        public int live(){return dir!=0&&dur>=MIN_S&&deg>=MIN_DEG?dir*severity(1/peakK):0;}
        public boolean liveCaution(){return live()!=0&&peakLat>=CAUTION_G;}
        /** The i-th last finished corner (0 = latest), or null. */
        public Corner recent(int i){return recent[i];}
    }
}
