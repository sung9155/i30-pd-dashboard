import com.veloster.vehicle.Commute;
import java.util.HashMap;

/** 출퇴근 비교: drives are kept across restarts, grouped by start and end, and compared along the road. */
public final class CommuteTest {
    private static int checks;
    private static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
    static final HashMap<String,String> prefs=new HashMap<String,String>();
    static final Commute.Store STORE=new Commute.Store(){public String get(String k){return prefs.get(k);}public void put(String k,String v){prefs.put(k,v);}};
    static long mono=1000,wall=1_700_000_000_000L;
    /** Drives north from (lat0, 127.0) for metres at kmh, a fix a second; returns the delta seen at checkAt metres. */
    static float drive(double lat0,double metres,double kmh,double checkAt){return drive(lat0,metres,kmh,checkAt,5);}
    static float drive(double lat0,double metres,double kmh,double checkAt,int wait){
        Commute.store(STORE);   // a new run of the app: finishes the last drive into the history
        double v=kmh/3.6,d=0;float seen=Float.NaN;
        for(int i=0;i<wait;i++){Commute.fix(lat0,127.0,5,0,wall,mono);mono+=1000;wall+=1000;}   // waiting at the start (warm-up)
        while(d<metres){d=Math.min(metres,d+v);Commute.fix(lat0+d/110540,127.0,5,(float)kmh,wall,mono);
            if(Float.isNaN(seen)&&d>=checkAt)seen=Commute.delta(mono);mono+=1000;wall+=1000;}
        for(int i=0;i<30;i++){Commute.fix(lat0+metres/110540,127.0,5,0,wall,mono);mono+=1000;wall+=1000;}   // parked; the save covers it
        return seen;
    }
    public static void main(String[] a){
        float first=drive(37.0,10000,60,5000);
        check(Float.isNaN(first),"nothing to compare on the first drive");
        float second=drive(37.0,10000,50,5000,90);   // 5 km at 50 vs 60 km/h: 360 s vs 300 s (a 90 s warm-up does not count)
        check(Math.abs(second-60)<4,"second drive 60 s behind at 5 km: "+second);
        float third=drive(37.0,10000,75,5000);    // 240 s vs the fastest so far (300 s)
        check(Math.abs(third+60)<4,"third drive 60 s ahead at 5 km: "+third);
        float other=drive(37.2,10000,60,5000);    // starts 22 km away: another route
        check(Float.isNaN(other),"a drive from elsewhere is not compared");
        float shortHop=drive(37.0,500,30,400);    // 60 s, 500 m: too short to keep
        Commute.store(STORE);
        java.util.ArrayList<long[]> r=Commute.routes();
        check(r.size()==1&&r.get(0)[0]==3,"one route driven three times: "+(r.isEmpty()?0:r.get(0)[0]));
        check(Math.abs(r.get(0)[1]-480000)<6000,"fastest 8:00 at 75 km/h: "+r.get(0)[1]);
        check(Commute.format(-65.4f).equals("−1:05")&&Commute.format(12f).equals("+0:12"),"delta text");
        // A bad fix (accuracy 200 m) is ignored.
        Commute.store(STORE);Commute.fix(37.0,127.0,200,0,wall,mono);
        System.out.println("PASS "+checks+" commute checks");
    }
}
