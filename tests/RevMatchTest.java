import com.veloster.vehicle.RevMatch;
import com.veloster.vehicle.Telemetry;

/** 6MT downshift targets: speed x the 1.6 T-GDi 6MT ratios, only gears the engine can take, the rev-match window. */
public final class RevMatchTest {
    private static int checks;
    private static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
    public static void main(String[] args){
        int[] g=new int[3];float[] r=new float[3];
        // 4th at 80 km/h: 3rd 3750, 2nd 5700; 1st (9600) would over-rev and is left out
        int n=RevMatch.targets(80,4,6500,g,r);
        check(n==2&&g[0]==3&&r[0]==3750&&g[1]==2&&r[1]==5700,"4th 80 km/h: 3rd 3750, 2nd 5700, no 1st");
        // 6th at 96 km/h: 5th 2700, 4th 3400, 3rd 4500 (2nd 6800 is past the redline, a 4th gear down is not offered)
        n=RevMatch.targets(96,6,6500,g,r);
        check(n==3&&r[0]==2700&&r[1]==3400&&r[2]==4500,"6th 96 km/h: three gears down");
        // a raised redline lets 2nd in from 5th at 100 km/h (7100)
        check(RevMatch.targets(100,5,6500,g,r)==2&&RevMatch.targets(100,5,7200,g,r)==3,"the redline decides which gears show");
        // too slow, unknown gear, 1st gear, neutral: nothing
        check(RevMatch.targets(8,4,6500,g,r)==0&&RevMatch.targets(80,Float.NaN,6500,g,r)==0&&RevMatch.targets(30,1,6500,g,r)==0,"nothing below 10 km/h, without a gear, or in 1st");
        // below 1500 rpm is left out: 6th to 5th at 50 km/h is 1400
        n=RevMatch.targets(50,6,6500,g,r);check(n>0&&g[0]==4,"a gear that would lug (5th 1400) is left out");
        // the match window: within 100 rpm of the 3rd-gear target
        check(RevMatch.matched(3720,80,4,6500)==3&&RevMatch.matched(3860,80,4,6500)==0&&RevMatch.matched(5650,80,4,6500)==2,"rev match within 100 rpm");
        check(Float.isNaN(Telemetry.mtRpmIn(80,7))&&Telemetry.mtRpmIn(80,3)==3750,"mtRpmIn: 3rd at 80 km/h, no 7th");
        // learned ratios (7DCT): a gear counts after 1.5 s steady above 20 km/h with the clutch closed
        com.veloster.vehicle.GearRatios lr=new com.veloster.vehicle.GearRatios();
        lr.update(0,3,1800,60,false);lr.update(1000,3,1800,60,false);check(Float.isNaN(lr.rpmIn(80,3)),"not before 1.5 s");
        lr.update(1600,3,1800,60,false);check(lr.rpmIn(80,3)==2400,"3rd learned: 30 rpm per km/h, 2400 at 80");
        lr.update(1700,4,1500,60,false);lr.update(2000,4,9999,10,false);check(Float.isNaN(lr.rpmIn(80,4)),"a new gear starts its hold; under 20 km/h never counts");
                System.out.println("PASS "+checks+" rev-match checks");
    }
}
