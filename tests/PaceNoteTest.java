import com.veloster.i18n.UiText;
import com.veloster.vehicle.Commute;
import com.veloster.vehicle.PaceNote;
import java.util.HashMap;

/** WRC screen's pace notes: corners found from the turn rate, kept with the drive, and called ahead on the same road. */
public final class PaceNoteTest {
    private static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
    static long t;static float along;
    /** Drives at kmh for sec seconds turning at deg/s (+ right) through yaw (+ left on the CAN), 20 samples a second;
     *  returns the corner that ended, if any. Lateral G follows the turn (v * rate). */
    static PaceNote.Corner run(PaceNote.Detector d,float kmh,float sec,float rate,boolean useYaw){
        PaceNote.Corner out=null;
        for(int i=0;i<sec*20;i++){t+=50;along+=kmh/3.6f*0.05f;
            float latG=(float)(-Math.toRadians(rate)*kmh/3.6f/9.81f);
            PaceNote.Corner c=d.feed(t,kmh,useYaw?-rate:Float.NaN,latG,Float.NaN,along);if(c!=null)out=c;}
        return out;
    }
    /** A corner: ramp in over a second, hold, ramp out, then a straight; the corner found (or null). */
    static PaceNote.Corner corner(float kmh,float rate,float holdS,boolean useYaw){
        PaceNote.Detector d=new PaceNote.Detector();PaceNote.Corner out=null,c;
        run(d,kmh,2,0,useYaw);
        for(int i=1;i<=5;i++)if((c=run(d,kmh,0.2f,rate*i/5,useYaw))!=null)out=c;
        if((c=run(d,kmh,holdS,rate,useYaw))!=null)out=c;
        for(int i=4;i>=0;i--)if((c=run(d,kmh,0.2f,rate*i/5,useYaw))!=null)out=c;
        if((c=run(d,kmh,3,0,useYaw))!=null)out=c;
        return out;
    }
    public static void main(String[] args){
        UiText.configure("ko","ko");
        check(PaceNote.severity(300)==6&&PaceNote.severity(45)==3&&PaceNote.severity(10)==1,"radius bands");

        // a 90 degree right at 40 km/h (15 deg/s: a 42 m radius): 우 3
        PaceNote.Corner c=corner(40,15,5,true);
        check(c!=null&&c.note==3&&!c.longer&&!c.tightens&&!c.opens,"90 degrees right at 40 km/h is a 3 R: "+(c==null?"none":c.encode()));
        check(PaceNote.call(c.note).equals("우 3"),"Korean call");
        // the same from lateral G when the yaw rate is not received
        c=corner(40,15,5,false);check(c!=null&&c.note==3,"lateral G alone gives the same call");

        // a long sweeping left at 90 km/h on a 160 m radius, 130 degrees: 좌 5 길게
        float sweep=(float)Math.toDegrees(25/160.0);
        c=corner(90,-sweep,130/sweep-1,true);
        check(c!=null&&c.note==-5&&c.longer,"long sweeping left is 5 L long: "+(c==null?"none":c.encode()));
        check(PaceNote.extras(c,false).equals("길게"),"long modifier");

        // parking and junction speeds, a lane change, a blip: nothing
        check(corner(10,30,5,true)==null,"parking-speed turn is no corner");
        check(corner(20,25,4,true)==null,"a 20 km/h junction is no corner");
        check(corner(80,6,0.3f,true)==null,"a lane change (a few degrees) is no corner");

        // a tightening right at 60 km/h: 120 m for 5 s, then 45 m for 3 s: 우 3 조임
        {PaceNote.Detector d=new PaceNote.Detector();float v=60/3.6f;float r1=(float)Math.toDegrees(v/120),r2=(float)Math.toDegrees(v/45);
         run(d,60,2,0,true);PaceNote.Corner o=null,k;
         if((k=run(d,60,5,r1,true))!=null)o=k;
         check(d.live()==5,"in the corner the live call shows once it counts: "+d.live());
         if((k=run(d,60,3,r2,true))!=null)o=k;if((k=run(d,60,3,0,true))!=null)o=k;
         check(o!=null&&o.note==3&&o.tightens&&!o.opens,"tightening corner: "+(o==null?"none":o.encode()));
         check(d.live()==0&&d.recent(0)==o,"the finished corner is the latest recent one");
         check(PaceNote.extras(o,false).equals("조임")&&o.caution&&PaceNote.extras(o,true).equals("조임 · 주의"),"tightens, and 0.63 G is 주의");}

        // the call text in English
        UiText.configure("en","en");
        check(PaceNote.call(-5).equals("L5")&&PaceNote.call(3).equals("R3"),"English call");
        check(PaceNote.extras(new PaceNote.Corner(0,3,true,false,false,true),true).equals("long · caution"),"English modifiers");
        UiText.configure("ko","ko");

        // storage: a corner round-trips through its text
        PaceNote.Corner s=new PaceNote.Corner(1234,-2,true,false,true,true);
        PaceNote.Corner b=PaceNote.Corner.decode(s.encode());
        check(s.encode().equals("1234L2loc")&&b!=null&&b.at==1234&&b.note==-2&&b.longer&&!b.tightens&&b.opens&&b.caution,"corner round trip");
        check(PaceNote.Corner.decode("x")==null&&PaceNote.Corner.decode("12R9")==null&&PaceNote.Corner.decode("L3")==null,"bad text is no corner");

        // a known road: the first drive records three corners, the next one gets them called ahead
        final HashMap<String,String> prefs=new HashMap<String,String>();
        Commute.Store st=new Commute.Store(){public String get(String k){return prefs.get(k);}public void put(String k,String v){prefs.put(k,v);}};
        long mono=1000,wall=1000;double lat0=37.0;
        Commute.store(st);
        for(int i=0;i<=240;i++){double m=i*12.5;Commute.fix(lat0+m/110540,127.0,5,45,wall,mono);   // 3 km north at 45 km/h
            if(i==64)Commute.corner(new PaceNote.Corner(Commute.along(mono),3,false,false,false,false));   // 800 m
            if(i==120)Commute.corner(new PaceNote.Corner(Commute.along(mono),-4,true,false,false,true));  // 1500 m
            if(i==176)Commute.corner(new PaceNote.Corner(Commute.along(mono),2,false,true,false,false));  // 2200 m
            mono+=1000;wall+=1000;}
        check(Commute.recorded()==3,"three corners recorded");
        check(Commute.ahead(mono,new PaceNote.Corner[3],new float[3])<0,"no notes on the first drive");
        Commute.store(st);   // next start: the drive goes into the history with its notes
        PaceNote.Corner[] next=new PaceNote.Corner[3];float[] dist=new float[3];int n=-1;
        for(int i=0;i<=48;i++){Commute.fix(lat0+i*12.5/110540,127.0,5,45,wall,mono);n=Commute.ahead(mono,next,dist);mono+=1000;wall+=1000;}   // 600 m in
        check(n==3&&next[0].note==3&&next[1].note==-4&&next[2].note==2,"the three corners ahead, nearest first: "+n);
        check(Math.abs(dist[0]-200)<20&&Math.abs(dist[1]-900)<20,"distances along the road: "+dist[0]+" "+dist[1]);
        n=Commute.ahead(mono+500,next,dist);check(Math.abs(dist[0]-(200-6.25f))<20,"carried forward between fixes");
        for(int i=49;i<=130;i++){Commute.fix(lat0+i*12.5/110540,127.0,5,45,wall,mono);n=Commute.ahead(mono,next,dist);mono+=1000;wall+=1000;}   // 1625 m
        check(n==1&&next[0].note==2,"passed corners drop off");
        check(Commute.ahead(mono+20000,next,dist)<0,"notes go stale without fixes");
        // a road with a right-angle bend at 1 km (north, then east): the track gains points in the bend, so a car 100 m
        // past the bend is placed 1.1 km along, not on the chord between the 400 m points
        {double la=37.5,kx=111320*Math.cos(Math.toRadians(la));Commute.store(st);
         for(int i=0;i<=200;i++){double m=i*12.5;double y=Math.min(m,1000),x=Math.max(0,m-1000);
             Commute.fix(la+y/110540,127.0+x/kx,5,45,wall,mono);
             if(i==120)Commute.corner(new PaceNote.Corner(Commute.along(mono),-3,false,false,false,false));   // 1500 m
             mono+=1000;wall+=1000;}
         Commute.store(st);
         for(int i=0;i<=88;i++){double m=i*12.5;double y=Math.min(m,1000),x=Math.max(0,m-1000);Commute.fix(la+y/110540,127.0+x/kx,5,45,wall,mono);n=Commute.ahead(mono,next,dist);mono+=1000;wall+=1000;}
         check(n==1&&Math.abs(dist[0]-400)<25,"placed along the bend: "+n+" "+dist[0]);}
        System.out.println("PaceNoteTest OK");
    }
}
