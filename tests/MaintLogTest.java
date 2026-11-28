import com.veloster.vehicle.MaintItems;
import com.veloster.vehicle.MaintLog;
import com.veloster.vehicle.Telemetry;
import java.util.HashMap;

/** 정비: usage since the last oil change and in total, from the samples both the dashboard and the floating window see. */
public final class MaintLogTest {
    static int n;
    static void check(boolean ok,String what){n++;if(!ok)throw new AssertionError(what);}
    static final HashMap<String,String> kept=new HashMap<String,String>();
    static final MaintItems.Store STORE=new MaintItems.Store(){public String get(String k){String v=kept.get(k);return v==null?"":v;}public void put(String k,String v){kept.put(k,v);}};
    static long now,wall;
    static void reset(){kept.clear();now=100000;wall=1700000000000L;MaintLog.attach(STORE);}
    /** A sample array with the given signals valid (rpm 0, speed 7, coolant 2 ...); the rest not received. */
    static Telemetry.Sample[] s(float rpm,float kmh,float... more){
        Telemetry.Sample[] q=new Telemetry.Sample[100];
        for(int i=0;i<q.length;i++)q[i]=new Telemetry.Sample(Float.NaN,false,false,false,false,Long.MAX_VALUE);
        q[0]=v(rpm);q[7]=v(kmh);
        for(int i=0;i+1<more.length;i+=2)q[(int)more[i]]=v(more[i+1]);
        return q;}
    static Telemetry.Sample v(float x){return Float.isNaN(x)?new Telemetry.Sample(x,false,false,false,false,Long.MAX_VALUE):new Telemetry.Sample(x,true,false,true,false,0);}
    /** Runs the same samples for seconds, a tick every 100 ms (twice when dup). */
    static void run(Telemetry.Sample[] q,double seconds,boolean dup){
        for(int i=0;i<seconds*10;i++){now+=100;wall+=100;MaintLog.tick(q,now,wall);if(dup){MaintLog.tick(q,now,wall);MaintLog.tick(q,now+50,wall+50);}}}
    static void close(float a,float b,float tol,String what){check(Math.abs(a-b)<=tol,what+": "+a+" vs "+b);}

    public static void main(String[] a){
        reset();run(s(2000,60),600,false);
        MaintLog.Usage u=MaintLog.sinceOil();
        close((float)u.movingKm,10,0.2f,"10 min at 60 km/h");close((float)u.engineS,600,2,"engine seconds");
        check(u.starts==1,"one start");check(Math.abs(u.seenKm-u.movingKm)<1e-6,"seen = moving while ticking");

        reset();run(s(2000,60),600,true);u=MaintLog.sinceOil();
        close((float)u.movingKm,10,0.2f,"doubleTickCountsOnce: km");close((float)u.engineS,600,2,"doubleTickCountsOnce: engine");

        reset();run(s(2000,60),300,false);MaintLog.flush();MaintLog.attach(STORE);   // the app restarted mid-drive
        run(s(2000,60),300,false);u=MaintLog.sinceOil();
        check(u.starts==1,"restartMidDriveSameStart: still one start ("+u.starts+")");close((float)u.movingKm,10,0.3f,"restart keeps the km");
        run(s(0,0),120,false);run(s(2000,60),60,false);u=MaintLog.sinceOil();
        check(u.starts==2&&u.shortStarts==0,"the 10 km drive across the restart is not short");

        reset();
        for(int k=0;k<3;k++){run(s(2000,60),180,false);run(s(0,0),120,false);}   // three 3 km drives
        u=MaintLog.sinceOil();check(u.starts==3&&u.shortStarts==2,"two finished 3 km drives are short ("+u.starts+"/"+u.shortStarts+")");
        reset();run(s(2000,60,Telemetry.AMBIENT,-5),720,false);run(s(0,0),120,false);run(s(2000,60),10,false);
        u=MaintLog.sinceOil();check(u.shortStarts==1,"12 km at -5 °C is short (16 km rule)");

        reset();run(s(900,0,2,25),300,false);u=MaintLog.sinceOil();
        check(u.coldStarts==1,"coolant 25 °C at the start: a cold start");close((float)u.idleS,300,2,"5 min idle");

        reset();float[] t={180,210,190,205};
        for(float c:t)run(s(2000,40,Telemetry.CLUTCH_ODD,c),2,false);
        u=MaintLog.sinceOil();check(u.clutchHot==2&&u.clutchMax==210,"clutch over 200 °C twice, max 210 ("+u.clutchHot+", "+u.clutchMax+")");

        reset();run(s(2000,60),600,false);MaintLog.oilChanged(97000);
        check(MaintLog.sinceOil().engineS==0&&MaintLog.sinceOil().odoAtOil==97000&&MaintLog.total().engineS>590,"oil change resets since-oil, keeps totals");
        run(s(2000,60),60,false);MaintLog.flush();double eng=MaintLog.sinceOil().engineS;
        MaintLog.attach(STORE);check(Math.abs(MaintLog.sinceOil().engineS-eng)<1e-3&&MaintLog.sinceOil().odoAtOil==97000,"flush + attach keeps the values");

        reset();run(s(0,0,Telemetry.BAT_VOLTAGE,12.6f),40,false);u=MaintLog.sinceOil();
        close(u.restV,12.6f,0.01f,"rest voltage after 30 s engine off");
        run(s(0,0),5,false);run(s(800,0,Telemetry.BAT_VOLTAGE,14.2f),5,false);u=MaintLog.sinceOil();
        close(u.startV,14.2f,0.01f,"first voltage after a start");check(u.restHistory.length==1&&Math.abs(u.restHistory[0]-12.6f)<0.01f,"rest voltage kept in the history");

        reset();run(s(2000,50),20,false);run(s(2000,1),5,false);run(s(2000,50),20,false);run(s(2000,0),5,false);
        check(MaintLog.sinceOil().stops==2,"two stops after moving");
        System.out.println("PASS "+n+" maintenance log checks");
    }
}
