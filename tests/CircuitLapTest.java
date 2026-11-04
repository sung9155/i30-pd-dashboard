import com.veloster.vehicle.CircuitLap;

/** GPS laps on a synthetic 2 km circle at 1 Hz: the interpolated lap time, direction, the minimum lap and the map. */
public final class CircuitLapTest {
    private static int checks;
    private static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
    static final double LAT=38.08,LON=128.36,R=318.31;   // circumference about 2,000 m
    /** Position on the circle after s metres, as a fix. */
    private static void at(CircuitLap c,double s,long t,boolean ccw){
        double a=(ccw?1:-1)*s/R;
        c.fix(LAT+R*Math.sin(a)/110540,LON+R*(1-Math.cos(a))/(111320*Math.cos(Math.toRadians(LAT))),t);
    }
    public static void main(String[] args){
        CircuitLap c=new CircuitLap();
        check(!c.setLine(),"no line before a fix");
        double v=2000/80.0;   // 80 s laps
        long t=1000;double s=0;
        at(c,s,t,true);check(c.setLine()&&c.state()==CircuitLap.ARMED,"line set at the first fix, armed");
        for(int i=0;i<=400;i++){t+=1000;s+=v;at(c,s,t,true);}   // five laps and some, one fix a second
        check(c.state()==CircuitLap.TIMING,"timing once the car moved off the line");
        check(c.laps==5,"five laps in 401 s at 80 s a lap: "+c.laps);
        check(Math.abs(c.last-80000)<=50&&Math.abs(c.best-80000)<=50,"lap time interpolated between 1 Hz fixes: "+c.last);
        check(c.lastPath!=null&&c.lastPath.length/2>=79,"the finished lap's path is kept for the map");
        double minX=1e9,maxX=-1e9;for(int i=0;i<c.lastPath.length;i+=2){minX=Math.min(minX,c.lastPath[i]);maxX=Math.max(maxX,c.lastPath[i]);}
        check(Math.abs(maxX-minX-2*R)<20,"the map spans the circle: "+(maxX-minX));

        CircuitLap back=new CircuitLap();t=0;s=0;at(back,s,t,true);back.setLine();
        for(int i=0;i<10;i++){t+=1000;s+=v;at(back,s,t,true);}
        for(int i=0;i<20;i++){t+=1000;s-=v;at(back,s,t,true);}   // turn round and drive back over the line
        check(back.laps==0,"crossing the wrong way is no lap");

        CircuitLap jitter=new CircuitLap();t=0;s=0;at(jitter,s,t,true);jitter.setLine();
        for(int i=0;i<3;i++){t+=1000;s+=v;at(jitter,s,t,true);}
        for(int i=0;i<3;i++){t+=1000;s-=v;at(jitter,s,t,true);}
        for(int i=0;i<3;i++){t+=1000;s+=v;at(jitter,s,t,true);}   // back over the line and forward again within 20 s
        check(jitter.laps==0,"no lap shorter than the minimum");

        CircuitLap off=new CircuitLap();t=0;off.fix(LAT,LON,t);off.setLine();
        off.fix(LAT,LON+50/(111320*Math.cos(Math.toRadians(LAT))),t+=1000);   // heading east
        off.fix(LAT+0.002,LON-200/(111320*Math.cos(Math.toRadians(LAT))),t+=30000);   // come back far to the side
        off.fix(LAT+0.002,LON+200/(111320*Math.cos(Math.toRadians(LAT))),t+=5000);   // cross the line's extension 220 m away
        check(off.laps==0,"crossing far from the line (pit lane, another straight) is no lap");
        // A known line (a circuit's start / finish) driven clockwise: the direction comes from the first crossing.
        CircuitLap known=new CircuitLap();known.origin(LAT,LON);known.knownLine(0,0,0,1);   // line at the circle's start, tangent north
        t=5000;s=-10*v;at(known,s,t,false);   // ten seconds before the line, going the other way round (clockwise)
        for(int i=0;i<200;i++){t+=1000;s+=v;at(known,s,t,false);}
        check(known.state()==CircuitLap.TIMING&&known.laps==2,"a known line times laps in whichever direction the car first crosses it: "+known.laps);
        check(Math.abs(known.last-80000)<=50,"and the lap time is right: "+known.last);
        check(Math.abs(known.startAt-(5000+10000+160000))<=50,"lap 1 started at the first crossing, not at the first fix");
        System.out.println("PASS "+checks+" circuit lap checks");
    }
}
