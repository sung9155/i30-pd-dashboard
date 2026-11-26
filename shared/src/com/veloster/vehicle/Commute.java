package com.veloster.vehicle;

import java.util.ArrayList;
import java.util.Locale;

/**
 * 출퇴근 비교: drives that start and end at the same places are grouped by themselves (no setup), and while driving
 * one of them the screen shows how far ahead or behind the fastest earlier drive of that route this point was reached
 * (the circuit lap delta, along a road).
 *
 * A drive is one run of the app with GPS (the head unit starts and stops with the ignition): it starts where the car
 * first moves (a warm-up does not count), ends where it last moved, and is kept when it is at least MIN_DRIVE_MS and MIN_DRIVE_M long. The drive in
 * progress is saved every SAVE_MS, so the next start finishes it into the history even though the head unit gives no
 * warning when it powers off. Each drive keeps a point every STEP_M along the way (position and seconds since the start).
 * Two drives are the same route when both their starts and their ends are within SAME_M of each other.
 * No Android: the dashboard hands in the store (VehicleDash) and the fixes (Gps); without a store nothing is kept.
 */
public final class Commute {
    public interface Store{String get(String key);void put(String key,String value);}
    static final long MIN_DRIVE_MS=180000,SAVE_MS=30000,DELTA_STALE_MS=8000;
    static final float MIN_DRIVE_M=1000,STEP_M=400,SAME_M=400,ON_ROUTE_M=250,MAX_ACC_M=60,MOVING_KMH=3;
    static final int KEEP=40;
    static final String HISTORY="commute_history",CURRENT="commute_current";

    /** One drive: start and end positions, start wall time, duration and length, and the track (lat, lon, seconds). */
    static final class Drive{
        double sLat,sLon,eLat,eLon;long startWall;long ms;float metres;final ArrayList<double[]> track=new ArrayList<double[]>();
        String encode(){
            StringBuilder b=new StringBuilder(String.format(Locale.US,"%.5f;%.5f;%.5f;%.5f;%d;%d;%.0f;",sLat,sLon,eLat,eLon,startWall,ms,metres));
            for(int i=0;i<track.size();i++){double[] p=track.get(i);b.append(i==0?"":"/").append(String.format(Locale.US,"%.5f,%.5f,%.0f",p[0],p[1],p[2]));}
            return b.toString();
        }
        static Drive decode(String s){
            try{String[] f=s.split(";",-1);Drive d=new Drive();
                d.sLat=Double.parseDouble(f[0]);d.sLon=Double.parseDouble(f[1]);d.eLat=Double.parseDouble(f[2]);d.eLon=Double.parseDouble(f[3]);
                d.startWall=Long.parseLong(f[4]);d.ms=Long.parseLong(f[5]);d.metres=Float.parseFloat(f[6]);
                if(f.length>7&&f[7].length()>0)for(String p:f[7].split("/")){String[] q=p.split(",");d.track.add(new double[]{Double.parseDouble(q[0]),Double.parseDouble(q[1]),Double.parseDouble(q[2])});}
                return d;}
            catch(RuntimeException e){return null;}
        }
    }

    private static Store store;
    private static final ArrayList<Drive> history=new ArrayList<Drive>();
    private static Drive cur;
    private static long startMono,lastSave=Long.MIN_VALUE/2,lastMoveMono;
    private static double lastLat=Double.NaN,lastLon=Double.NaN;
    /** The earlier drive the one in progress is compared with (null = none yet). */
    private static Drive ref;
    /** Seconds ahead (negative) or behind (positive) the fastest earlier drive at this point; NaN = not on a known route. */
    private static float delta=Float.NaN;private static long deltaAt;
    private Commute(){}

    /** The app's store, once per process: a dashboard built again in the same drive must not end the drive. */
    public static synchronized void attach(Store s){if(store==null)store(s);}
    /** Loads the history and finishes the drive the last run left unsaved. UI thread. */
    public static synchronized void store(Store s){
        store=s;history.clear();cur=null;ref=null;seg.clear();delta=Float.NaN;lastLat=lastLon=Double.NaN;
        if(s==null)return;
        String h=s.get(HISTORY);if(h!=null&&h.length()>0)for(String x:h.split("\\|")){Drive d=Drive.decode(x);if(d!=null)history.add(d);}
        Drive left=Drive.decode(s.get(CURRENT)==null?"":s.get(CURRENT));
        if(left!=null&&left.ms>=MIN_DRIVE_MS&&left.metres>=MIN_DRIVE_M){history.add(left);while(history.size()>KEEP)history.remove(0);saveHistory();}
        s.put(CURRENT,"");
    }
    private static void saveHistory(){
        StringBuilder b=new StringBuilder();for(Drive d:history)b.append(b.length()==0?"":"|").append(d.encode());
        store.put(HISTORY,b.toString());
    }

    /** A GPS fix (Gps, UI thread): position, accuracy (m), speed (km/h, NaN when none), the wall clock and a monotonic ms. */
    public static synchronized void fix(double lat,double lon,float acc,float kmh,long wall,long mono){
        if(!Float.isNaN(acc)&&acc>MAX_ACC_M)return;
        double step=Double.isNaN(lastLat)?0:metres(lastLat,lastLon,lat,lon);
        boolean moving=Float.isNaN(kmh)?step>5:kmh>=MOVING_KMH;
        if(cur==null){   // the clock starts when the car first moves: a warm-up at the start is not part of the drive
            lastLat=lat;lastLon=lon;
            if(!moving)return;
            cur=new Drive();cur.sLat=cur.eLat=lat;cur.sLon=cur.eLon=lon;cur.startWall=wall;startMono=lastMoveMono=mono;cur.track.add(new double[]{lat,lon,0});
            return;}
        if(moving&&step<500){   // a jump over 500 m in a second is a bad fix, not driving
            cur.metres+=step;cur.eLat=lat;cur.eLon=lon;cur.ms=mono-startMono;lastMoveMono=mono;
            double[] lp=cur.track.get(cur.track.size()-1);
            if(metres(lp[0],lp[1],lat,lon)>=STEP_M)cur.track.add(new double[]{lat,lon,cur.ms/1000.0});
        }
        lastLat=lat;lastLon=lon;
        follow(lat,lon,(mono-startMono)/1000.0,mono);
        if(store!=null&&mono-lastSave>=SAVE_MS){lastSave=mono;store.put(CURRENT,cur.encode());}
    }

    /** Each earlier drive's segment last matched in this drive: searching from there keeps an out-and-back road on the
     *  right leg. */
    private static final java.util.IdentityHashMap<Drive,Integer> seg=new java.util.IdentityHashMap<Drive,Integer>();
    /** Compares the position with every earlier drive from the same start that passed here, against the one that got
     *  here soonest, and sets the delta. */
    private static void follow(double lat,double lon,double sec,long mono){
        Drive best=null;double bestT=Double.MAX_VALUE;
        for(Drive d:history){
            if(metres(d.sLat,d.sLon,cur.sLat,cur.sLon)>SAME_M||d.track.size()<2)continue;
            double t=timeAt(d,lat,lon);
            if(!Double.isNaN(t)&&t<bestT){bestT=t;best=d;}
        }
        if(best==null)return;   // off every known route: the last delta goes stale
        ref=best;delta=(float)(sec-bestT);deltaAt=mono;
    }
    /** Seconds into drive d at the point of its track nearest (lat, lon), from its last matched segment on; NaN when
     *  the track passes no closer than ON_ROUTE_M. Positions are projected onto the segment between two points. */
    static double timeAt(Drive d,double lat,double lon){
        Integer from=seg.get(d);int i0=from==null?0:Math.max(0,from-1);double bestGap=ON_ROUTE_M,t=Double.NaN;int at=-1;
        double kx=111320*Math.cos(Math.toRadians(lat)),ky=110540;
        for(int i=i0;i+1<d.track.size();i++){
            double[] a=d.track.get(i),b=d.track.get(i+1);
            double ax=(a[1]-lon)*kx,ay=(a[0]-lat)*ky,bx=(b[1]-lon)*kx,by=(b[0]-lat)*ky,dx=bx-ax,dy=by-ay,l2=dx*dx+dy*dy;
            double f=l2==0?0:Math.max(0,Math.min(1,-(ax*dx+ay*dy)/l2)),px=ax+f*dx,py=ay+f*dy,g=Math.sqrt(px*px+py*py);
            if(g<bestGap){bestGap=g;t=a[2]+f*(b[2]-a[2]);at=i;}
        }
        if(at>=0)seg.put(d,at);
        return t;
    }

    /** Seconds behind (+) or ahead (−) the fastest earlier drive at this point, NaN when not on a known route. */
    public static synchronized float delta(long mono){return mono-deltaAt>DELTA_STALE_MS?Float.NaN:delta;}
    /** Earlier drives of the route this one follows, and their fastest and mean duration (ms); empty when none. */
    public static synchronized long[] routeStats(){
        if(ref==null)return new long[0];
        int n=0;long best=Long.MAX_VALUE,sum=0;
        for(Drive d:history)if(same(d,ref)){n++;best=Math.min(best,d.ms);sum+=d.ms;}
        return new long[]{n,best,sum/Math.max(1,n)};
    }
    /** Routes driven at least twice, most driven first: {drives, fastest ms, mean ms, last ms} each. For 기록실. */
    public static synchronized ArrayList<long[]> routes(){
        ArrayList<long[]> out=new ArrayList<long[]>();boolean[] used=new boolean[history.size()];
        for(int i=history.size()-1;i>=0;i--){if(used[i])continue;Drive a=history.get(i);int n=0;long best=Long.MAX_VALUE,sum=0,last=a.ms;
            for(int j=0;j<history.size();j++)if(!used[j]&&same(history.get(j),a)){used[j]=true;n++;best=Math.min(best,history.get(j).ms);sum+=history.get(j).ms;}
            if(n>=2)out.add(new long[]{n,best,sum/n,last});}
        java.util.Collections.sort(out,new java.util.Comparator<long[]>(){public int compare(long[] x,long[] y){return Long.compare(y[0],x[0]);}});
        return out;
    }
    static boolean same(Drive a,Drive b){return metres(a.sLat,a.sLon,b.sLat,b.sLon)<=SAME_M&&metres(a.eLat,a.eLon,b.eLat,b.eLon)<=SAME_M;}
    /** Metres between two positions (equirectangular: fine at road distances). */
    static double metres(double la1,double lo1,double la2,double lo2){
        double y=Math.toRadians(la2-la1),x=Math.toRadians(lo2-lo1)*Math.cos(Math.toRadians((la1+la2)/2));return 6371000*Math.sqrt(x*x+y*y);
    }
    /** "−1:05" / "+0:12": the delta for the screen. */
    public static String format(float sec){int s=Math.round(Math.abs(sec));return (sec<0?"−":"+")+s/60+":"+String.format(Locale.US,"%02d",s%60);}
    public static String clock(long ms){long s=Math.round(ms/1000.0);return s/60+":"+String.format(Locale.US,"%02d",s%60);}
}
