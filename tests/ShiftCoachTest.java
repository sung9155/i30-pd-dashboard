import com.veloster.vehicle.ShiftCoach;

/** 6MT shift measurements: rev match at the clutch release, clutch time, the lurch after it, heel-and-toe. */
public final class ShiftCoachTest {
    private static int checks;
    private static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
    /** Drive t0..t1 ms at 20 ms steps with fixed inputs. */
    private static long run(ShiftCoach c,long t,long ms,float rpm,float kmh,float gear,boolean clutch,float brake,float pedal,float g){
        for(long e=t+ms;t<e;t+=20)c.update(t,rpm,kmh,gear,clutch,brake,pedal,g);return t;}
    public static void main(String[] a){
        // 4th at 80 km/h, heel-and-toe down to 3rd, released at 3740 (3rd = 3750): S with H&T
        ShiftCoach c=new ShiftCoach();long t=0;
        t=run(c,t,500,2830,80,4,false,0,0,-0.6f);
        t=run(c,t,300,1500,80,4,true,25,0,-0.6f);t=run(c,t,400,3740,80,4,true,25,40,-0.6f);
        t=run(c,t,700,3740,80,3,false,25,0,-0.6f);
        check(c.count==1&&c.last.from==4&&c.last.to==3&&c.last.down&&c.last.heelToe&&Math.abs(c.last.revErr+10)<1&&Math.abs(c.last.timeS-0.7f)<0.03f,"heel-toe 4>3: H&T, -10 rpm, clutch 0.7 s");
        // upshift 3>4 at 100 km/h released 400 rpm high, the car lurches 0.4 G and back
        t=run(c,t,300,4700,100,3,false,0,100,0.3f);
        t=run(c,t,300,4000,100,3,true,0,0,0f);
        t=run(c,t,100,3930,100,4,false,0,20,0.7f);t=run(c,t,600,3530,100,4,false,0,50,0.3f);
        check(c.count==2&&!c.last.down&&!c.last.heelToe&&Math.abs(c.last.revErr-380)<1&&c.last.jolt>0.3f,"lurching upshift: +380 rpm, jolt over 0.3 G");
        // smooth take-up: G rises from 0 to 0.3 and stays: no jolt
        t=run(c,t,300,3530,100,4,true,0,0,0f);
        for(int i=0;i<35;i++){c.update(t,2820,100,5,false,0,60,0.3f*Math.min(1,i/20f));t+=20;}
        check(c.count==3&&c.last.to==5&&c.last.jolt<0.01f,"smooth upshift: no jolt");
        // clutch pressed again before it settled, or below 10 km/h: not a shift
        t=run(c,t,200,2000,8,2,true,0,0,0);t=run(c,t,700,2000,8,1,false,0,0,0);
        check(c.count==3,"no shift recorded below 10 km/h");
        System.out.println("PASS "+checks+" shift measurement checks");
    }
}
