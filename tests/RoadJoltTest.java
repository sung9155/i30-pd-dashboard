import com.veloster.vehicle.RoadJolt;

/** Road jolt for the 8-bit chase: a sharp G spike counts only when no gear, pedal, brake or steering change explains it. */
public final class RoadJoltTest {
    private static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
    /** 20 fps frames; a new G sample every 80 ms (ESP12 at ~12 Hz), the spike on sample 10. */
    private static float run(float spikeLat,float spikeLon,float pedalStep,float brakeStep,float steerStep,boolean shift){
        RoadJolt j=new RoadJolt();float max=0;int sample=0;
        for(int f=0;f<80;f++){long now=1000+f*50L;if(f%2==0&&f>0)sample++;
            float lat=0.02f+sample*0.0005f,lon=-0.01f;
            if(sample==10){lat+=spikeLat;lon+=spikeLon;}
            boolean near=sample>=9&&sample<=10;
            max=Math.max(max,j.update(now,lat,lon,18+(near?pedalStep:0),1+(near?brakeStep:0),3+(near?steerStep:0),shift&&near?4:3));}
        return max;
    }
    public static void main(String[] args){
        check(run(0,0,0,0,0,false)==0,"a steady drive never jolts");
        check(run(0.15f,0,0,0,0,false)>0,"a sharp lateral spike with no driver input is a road jolt");
        check(run(0,0.15f,0,0,0,false)>0,"a sharp longitudinal spike with no driver input is a road jolt");
        check(run(0.06f,0,0,0,0,false)==0,"a spike under the threshold is road noise (second difference 2 x 0.06 = 0.12 G)");
        check(run(0.15f,0,0,0,0,true)==0,"a gear shift explains the spike");
        check(run(0.15f,0,12,0,0,false)==0,"a pedal change explains the spike");
        check(run(0.15f,0,0,5,0,false)==0,"a brake change explains the spike");
        check(run(0.15f,0,0,0,30,false)==0,"a steering change explains the spike");
        // Braking from 0 to -0.4 G over 1 s is smooth: no second-difference spike.
        RoadJolt j=new RoadJolt();float max=0;
        for(int s=0;s<30;s++)max=Math.max(max,j.update(1000+s*80L,0,-Math.min(0.4f,s*0.032f),0,20,0,3));
        check(max==0,"a smooth G ramp is no jolt");
        // Repeated frames of one G sample count once, and missing G resets the history.
        j=new RoadJolt();j.update(1000,0,0,0,0,0,3);j.update(1080,0,0,0,0,0,3);
        check(j.update(1160,Float.NaN,Float.NaN,0,0,0,3)==0,"no G, no jolt");
        System.out.println("PASS road jolt checks");
    }
}
