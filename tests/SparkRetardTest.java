import com.veloster.vehicle.SparkRetard;

/** Knock monitor estimate: spark later than the full-load line, only at full load. */
public final class SparkRetardTest {
    private static int checks;
    private static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
    public static void main(String[] args){
        check(Math.abs(SparkRetard.reference(1.2f)-2.2f)<0.01f,"full-load line at 1.2 bar is about 2.2 deg (log read 2.3)");
        check(SparkRetard.estimate(2.3f,1.2f,100)==0,"on the line: no retard");
        check(Math.abs(SparkRetard.estimate(-2f,1.2f,100)-4.2f)<0.01f,"4.2 deg later than the line");
        check(SparkRetard.estimate(-13.5f,-0.6f,0)==0,"fuel cut is not retard");
        check(SparkRetard.estimate(-3.8f,0.5f,40)==0,"part throttle is not judged");
        check(SparkRetard.estimate(Float.NaN,1.2f,100)==0,"missing spark reads 0");
        System.out.println("PASS "+checks+" spark retard checks");
    }
}
