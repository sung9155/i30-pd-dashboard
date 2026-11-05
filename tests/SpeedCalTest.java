import com.veloster.vehicle.SpeedCal;

/** GPS speed calibration: steady straight samples only, the median, the 0.90-1.10 guard, restore / reset. */
public final class SpeedCalTest {
    private static int checks;
    private static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
    /** secs of driving at carKmh (20 ms frames) with a GPS fix a second reading gpsKmh(i). */
    private static long drive(SpeedCal c,long t,int secs,float carKmh,float yaw,boolean brake,float[] gps,int seq0,float acc){
        for(int s=0;s<secs;s++){for(int f=0;f<50;f++){t+=20;c.update(t,carKmh,yaw,brake,f==49?seq0+s:seq0+s-1,gps[s%gps.length],acc);}}   // a new fix once a second, as GpsFix.seq moves
        return t;
    }
    public static void main(String[] a){
        // the speedometer reads 3 % high: 103 shown at a true 100
        SpeedCal c=new SpeedCal();long t=drive(c,0,200,103,0.5f,false,new float[]{100},0,4);
        check(Float.isNaN(new SpeedCal().ratio()),"nothing learned at first");
        check(c.samples()>=MIN()&&Math.abs(c.ratio()-100f/103)<0.001f,"steady straight: 100 / 103");
        // a bad fix in among them does not move the median
        t=drive(c,t,30,103,0.5f,false,new float[]{100,100,100,100,100,100,100,100,100,92},200,4);
        check(Math.abs(c.ratio()-100f/103)<0.002f,"one-off bad fixes leave the median");
        // braking, turning, a poor fix, too slow: no samples
        int n=c.samples();
        t=drive(c,t,20,103,0.5f,true,new float[]{100},300,4);
        t=drive(c,t,20,103,8f,false,new float[]{100},400,4);
        t=drive(c,t,20,103,0.5f,false,new float[]{100},500,25);
        t=drive(c,t,20,35,0.5f,false,new float[]{34},600,4);
        check(c.samples()==n,"braking, a bend, a 25 m fix and 35 km/h add nothing");
        // a ratio outside 0.90-1.10 is not a speedometer error
        SpeedCal w=new SpeedCal();drive(w,0,200,100,0.5f,false,new float[]{80},0,4);
        check(w.samples()==0,"GPS 20 % off: dropped");
        // a saved ratio stands until this drive has its own, reset forgets it
        SpeedCal r=new SpeedCal();r.restore(0.97f,500);
        check(Math.abs(r.ratio()-0.97f)<1e-6&&r.samples()==500,"restored 0.97 from 500 samples");
        r.reset();check(Float.isNaN(r.ratio())&&r.samples()==0,"reset forgets");
        System.out.println("PASS "+checks+" GPS speed calibration checks");
    }
    private static int MIN(){return SpeedCal.MIN_SAMPLES;}
}
