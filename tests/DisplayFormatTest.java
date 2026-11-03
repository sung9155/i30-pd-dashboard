import com.veloster.vehicle.Telemetry;

/** Display numbers: no "-0.00" for a value that rounds to zero; G points past a plot's rim are pinned onto it. */
public final class DisplayFormatTest {
    private static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
    private static void same(String got,String want){check(got.equals(want),"got "+got+", want "+want);}
    public static void main(String[] args){
        same(Telemetry.format("%+.2f",-0.004f),"+0.00");
        same(Telemetry.format("%.2f",-0.004f),"0.00");
        same(Telemetry.format("%.0f",-0.4f),"0");
        same(Telemetry.format("%+.1f",-0.04f)+"°","+0.0°");
        same(Telemetry.format("%.2f bar",-0.001f),"0.00 bar");
        same(Telemetry.format("%+.2f",-0.05f),"-0.05");     // a real negative keeps its sign
        same(Telemetry.format("%.0f",-12f),"-12");
        same(Telemetry.format("%+.2f",-1.004f),"-1.00");
        same(Telemetry.format("%+.2f",0.004f),"+0.00");
        check(Telemetry.pinG(0.6f,0.6f,1)==1,"inside the rim stays put");
        float k=Telemetry.pinG(-1.48f,-1.48f,1);
        check(Math.abs(Math.hypot(-1.48f*k,-1.48f*k)-1)<1e-4,"a 2.09 g spike lands on the 1 g rim");
        k=Telemetry.pinG(1.5f,0,1.2f);check(Math.abs(1.5f*k-1.2f)<1e-5,"pinned to the given edge");
        System.out.println("PASS display format checks");
    }
}
