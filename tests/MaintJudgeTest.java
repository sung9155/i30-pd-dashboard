import com.veloster.vehicle.MaintItems;
import com.veloster.vehicle.MaintItems.Id;
import com.veloster.vehicle.MaintJudge;
import com.veloster.vehicle.MaintLog;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;

/** 정비: 자동 / 일반 / 가혹 from the driving seen since the oil change, and the once-per-band reminder. */
public final class MaintJudgeTest {
    static int n;
    static void check(boolean ok,String what){n++;if(!ok)throw new AssertionError(what);}
    static MaintLog.Usage plain(){MaintLog.Usage u=new MaintLog.Usage();u.odoAtOil=90000;u.seenKm=u.movingKm=3000;u.movingS=3000/50.0*3600;
        u.engineS=u.movingS*1.1;u.idleS=u.engineS*0.05;u.starts=21;u.judgedStarts=20;u.shortStarts=2;u.stops=600;u.highSpeedKm=100;u.highLoadS=u.engineS*0.02;return u;}
    static MaintItems.Left left(float km,int days){MaintItems.Left l=new MaintItems.Left();l.recorded=true;l.km=km;l.days=days;l.overdue=km<0||days!=Integer.MIN_VALUE&&days<0;return l;}

    public static void main(String[] a){
        float odo=95000;
        MaintJudge.Verdict v=MaintJudge.judge(MaintJudge.Mode.AUTO,plain(),odo);
        check(!v.severe&&!v.insufficient,"ordinary driving: normal ("+v.reason+")");
        MaintLog.Usage u=plain();u.starts=9;u.judgedStarts=8;u.shortStarts=5;v=MaintJudge.judge(MaintJudge.Mode.AUTO,u,odo);
        check(v.severe&&v.reason.contains("짧은 주행")&&v.reason.contains("62"),"5 of 8 finished starts short: severe ("+v.reason+")");
        u=plain();u.idleS=u.engineS*0.31;v=MaintJudge.judge(MaintJudge.Mode.AUTO,u,odo);check(v.severe&&v.reason.contains("공회전"),"idle 31 %: severe");
        u=plain();u.movingS=u.movingKm/18*3600;v=MaintJudge.judge(MaintJudge.Mode.AUTO,u,odo);check(v.severe&&v.reason.contains("km/h"),"average 18 km/h: severe ("+v.reason+")");
        u=plain();u.stops=(int)(u.movingKm*2.2);v=MaintJudge.judge(MaintJudge.Mode.AUTO,u,odo);check(v.severe,"2.2 stops a km: severe");
        u=plain();u.highSpeedKm=u.movingKm*0.25;v=MaintJudge.judge(MaintJudge.Mode.AUTO,u,odo);check(v.severe&&v.reason.contains("고속"),"25 % at 140+: severe");
        u=plain();u.highLoadS=u.engineS*0.16;v=MaintJudge.judge(MaintJudge.Mode.AUTO,u,odo);check(v.severe&&v.reason.contains("고부하"),"16 % high load: severe");
        u=plain();u.seenKm=u.movingKm=200;v=MaintJudge.judge(MaintJudge.Mode.AUTO,u,91000);
        check(!v.severe&&v.insufficient&&v.reason.contains("근거 부족"),"200 of 1,000 km seen: too little, normal");
        check(MaintJudge.judge(MaintJudge.Mode.AUTO,plain(),Float.NaN).insufficient,"no odometer: too little");
        u=plain();u.starts=9;u.judgedStarts=8;u.shortStarts=8;
        check(!MaintJudge.judge(MaintJudge.Mode.NORMAL,u,odo).severe&&MaintJudge.judge(MaintJudge.Mode.SEVERE,plain(),odo).severe,"일반 / 가혹 chosen by hand ignore the driving");

        HashMap<String,String> m=new HashMap<String,String>();final HashMap<String,String> mm=m;
        MaintItems.Store s=new MaintItems.Store(){public String get(String k){String x=mm.get(k);return x==null?"":x;}public void put(String k,String x){mm.put(k,x);}};
        check(MaintJudge.mode(s)==MaintJudge.Mode.AUTO,"default 자동");MaintJudge.setMode(s,MaintJudge.Mode.SEVERE);check(MaintJudge.mode(s)==MaintJudge.Mode.SEVERE,"mode kept");

        check(MaintJudge.band(Id.OIL,left(900,200))==MaintJudge.Band.SOON&&MaintJudge.band(Id.OIL,left(5000,10))==MaintJudge.Band.SOON,"within 1,000 km or 14 days: soon");
        check(MaintJudge.band(Id.OIL,left(5000,200))==MaintJudge.Band.NONE&&MaintJudge.band(Id.OIL,left(-10,200))==MaintJudge.Band.OVERDUE,"far: none; past: overdue");
        check(MaintJudge.band(Id.INSPECTION,left(Float.NaN,25))==MaintJudge.Band.SOON&&MaintJudge.band(Id.OIL,new MaintItems.Left())==MaintJudge.Band.NONE,"inspection 30 days; no record never");

        EnumMap<Id,MaintItems.Left> l=new EnumMap<Id,MaintItems.Left>(Id.class);l.put(Id.OIL,left(800,100));l.put(Id.AIR,left(30000,Integer.MIN_VALUE));
        List<Id> d=MaintJudge.due(l,s,20260601);check(d.size()==1&&d.get(0)==Id.OIL,"soon: told once");
        check(MaintJudge.due(l,s,20260601).isEmpty()&&MaintJudge.due(l,s,20260602).isEmpty(),"not again the same day or the next while still soon");
        l.put(Id.OIL,left(-20,90));check(MaintJudge.due(l,s,20260610).contains(Id.OIL),"turning overdue: told");
        check(MaintJudge.due(l,s,20260616).isEmpty()&&MaintJudge.due(l,s,20260617).contains(Id.OIL),"overdue: again after 7 days, not 6");
        MaintJudge.recorded(s,Id.OIL);l.put(Id.OIL,left(900,300));check(MaintJudge.due(l,s,20260618).contains(Id.OIL),"a new record starts the bands over");
        // The head unit's date gone back (clock not set yet): nothing is told and nothing forgotten until it is right again.
        l.put(Id.OIL,left(900,10));check(MaintJudge.due(l,s,20260619).isEmpty(),"still soon: quiet");
        l.put(Id.OIL,left(900,4000));l.put(Id.AIR,left(30000,-5));check(MaintJudge.due(l,s,20150101).isEmpty(),"date gone back: nothing told");
        l.put(Id.OIL,left(900,10));l.put(Id.AIR,left(30000,Integer.MIN_VALUE));check(MaintJudge.due(l,s,20260620).isEmpty(),"date right again: the band was not forgotten");
        System.out.println("PASS "+n+" maintenance judge checks");
    }
}
