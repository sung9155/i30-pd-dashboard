import com.veloster.vehicle.SpeedChime;

public final class SpeedChimeTest {
    private static int checks;
    private static void check(boolean ok,String what){checks++;if(!ok)throw new AssertionError(what);}
    public static void main(String[] a){
        SpeedChime c=new SpeedChime();
        SpeedChime.kmh=0;check(!c.due(150,0),"off: never");
        SpeedChime.kmh=105;
        check(!c.due(104.9f,0),"under the speed: quiet");
        check(c.due(105,10),"at the speed: rings");
        check(!c.due(110,500),"within a period: once");
        check(c.due(110,1010),"next period: again");
        check(c.due(103,2010),"just under (hysteresis 3): keeps ringing");
        check(!c.due(101.9f,3010),"3 km/h under: stops");
        check(!c.due(104,4010),"back up but under the speed: still quiet");
        check(c.due(105,5010),"at the speed again: rings");
        check(!c.due(Float.NaN,6010),"unknown speed: quiet");
        check(SpeedChime.next(0)==100&&SpeedChime.next(105)==110&&SpeedChime.next(120)==0&&SpeedChime.next(7)==0,"settings cycle: off, 100, 105, 110, 120, off");
        System.out.println("PASS "+checks+" speed chime checks");
    }
}
