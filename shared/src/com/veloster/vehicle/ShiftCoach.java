package com.veloster.vehicle;

/**
 * 6MT shift grading for the 수동 screen. A shift is the clutch pressed and released while rolling (10 km/h up). At the
 * release it keeps the rpm and speed; GRADE_MS later the gear has settled (Telemetry holds a new 6MT gear 300 ms), and
 * the shift is graded on how far the release rpm was from what the new gear turns at that speed (the rev match, the
 * same for an upshift as for a downshift) and on the jolt: how far the longitudinal G swung in that window beyond a
 * plain change of level (max - min - |end - start|), so taking up drive smoothly scores 0 and a lurch does not. A
 * downshift with the brake and the throttle both pressed while the clutch was in is a heel-and-toe. Grades S/A/B/C
 * count up for the session, with the run of S or A shifts in a row. No Android: host-tested.
 */
public final class ShiftCoach {
    public static final long GRADE_MS=600;
    public static final float MIN_KMH=10,HEEL_BRAKE_BAR=5,HEEL_PEDAL=10;

    /** One graded shift. revErr = release rpm minus the new gear's rpm at that speed. */
    public static final class Shift {
        public final int from,to;public final boolean down,heelToe;public final float revErr,timeS,jolt;public final char grade;
        Shift(int from,int to,boolean heelToe,float revErr,float timeS,float jolt){
            this.from=from;this.to=to;down=to<from;this.heelToe=heelToe&&down;this.revErr=revErr;this.timeS=timeS;this.jolt=jolt;grade=grade(revErr,jolt);}
    }
    /** S: within 100 rpm and 0.15 G; A: 250 rpm, 0.25 G; B: 500 rpm; C otherwise. */
    public static char grade(float revErr,float jolt){
        float e=Math.abs(revErr),j=Float.isNaN(jolt)?0:jolt;
        return e<=100&&j<=0.15f?'S':e<=250&&j<=0.25f?'A':e<=500?'B':'C';
    }

    public Shift last;
    public int count,streak,bestStreak;
    public final int[] grades=new int[4];   // S A B C
    private boolean in,pending,heel;private long inAt,outAt;private int from;private float outRpm,outKmh,baseG,lastG,minG,maxG,timeS,inG=Float.NaN;
    private boolean clutchSeen;private boolean lastClutch;

    /** Every frame. gear: the shown 6MT gear (held while the clutch is in); lonG in G; NaN where not received. */
    public void update(long now,float rpm,float kmh,float gear,boolean clutch,float brakeBar,float pedal,float lonG){
        boolean rise=clutchSeen&&clutch&&!lastClutch,fall=clutchSeen&&!clutch&&lastClutch;clutchSeen=true;lastClutch=clutch;
        if(rise&&kmh>=MIN_KMH&&gear>=1&&gear<=6){in=true;pending=false;inAt=now;from=(int)gear;heel=false;inG=Float.NaN;}
        if(in&&clutch&&brakeBar>HEEL_BRAKE_BAR&&pedal>HEEL_PEDAL)heel=true;
        if(in&&clutch&&!Float.isNaN(lonG))inG=lonG;   // the G just before the release is the baseline
        if(fall&&in){in=false;if(kmh>=MIN_KMH&&!Float.isNaN(rpm)){pending=true;outAt=now;outRpm=rpm;outKmh=kmh;timeS=(now-inAt)/1000f;baseG=Float.isNaN(inG)?lonG:inG;lastG=minG=maxG=baseG;}}
        if(pending){
            if(!Float.isNaN(lonG)){if(Float.isNaN(baseG))baseG=minG=maxG=lonG;lastG=lonG;minG=Math.min(minG,lonG);maxG=Math.max(maxG,lonG);}
            if(clutch){pending=false;return;}   // pressed again before it settled: not a shift
            if(now-outAt>=GRADE_MS){pending=false;
                int to=Float.isNaN(gear)?0:(int)gear;
                if(to>=1&&to<=6&&to!=from){
                    float target=Telemetry.mtRpmIn(outKmh,to);
                    float jolt=Float.isNaN(baseG)?Float.NaN:Math.max(0,maxG-minG-Math.abs(lastG-baseG));
                    last=new Shift(from,to,heel,outRpm-target,timeS,jolt);count++;
                    int gi="SABC".indexOf(last.grade);grades[gi]++;
                    streak=gi<=1?streak+1:0;bestStreak=Math.max(bestStreak,streak);}
            }
        }
    }
}
