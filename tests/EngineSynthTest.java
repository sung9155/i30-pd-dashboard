import com.veloster.vehicle.EngineSynth;

/** Virtual engine sound: silent when off, follows the firing order, louder with load, bounded, blow-off on a lift. */
public final class EngineSynthTest {
    private static int checks;
    private static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
    /** Renders seconds of steady input in 512-sample blocks like the audio thread; returns the last second. */
    private static float[] run(EngineSynth s,double rpm,double pedal,double boost,double seconds){
        int n=(int)(seconds*EngineSynth.RATE),tail=EngineSynth.RATE;float[] all=new float[n];
        for(int i=0;i<n;i+=512){s.set(rpm,pedal,boost);s.render(all,i,Math.min(512,n-i),1);}
        float[] last=new float[tail];System.arraycopy(all,n-tail,last,0,tail);return last;
    }
    private static double rms(float[] x){double a=0;for(float v:x)a+=v*v;return Math.sqrt(a/x.length);}
    private static double peak(float[] x){double a=0;for(float v:x)a=Math.max(a,Math.abs(v));return a;}
    /** Goertzel power at hz. */
    private static double power(float[] x,double hz){
        double w=2*Math.PI*hz/EngineSynth.RATE,c=2*Math.cos(w),s1=0,s2=0;
        for(float v:x){double s0=v+c*s1-s2;s2=s1;s1=s0;}
        return s1*s1+s2*s2-c*s1*s2;
    }
    public static void main(String[] args){
        EngineSynth s=new EngineSynth();
        float[] off=run(s,Double.NaN,0,0,1);
        check(peak(off)==0,"silent before the engine runs");
        float[] idle=run(s,750,0,0,2);
        check(rms(idle)>.01,"audible at idle: "+rms(idle));
        float[] coast=run(new EngineSynth(),3000,0,0,2),full=run(new EngineSynth(),3000,1,0,2);
        check(rms(full)>rms(coast)*1.3,"louder under load: "+rms(coast)+" -> "+rms(full));
        double firing=power(full,100);   // order 2 at 3000 rpm
        check(firing>power(full,87)*4&&firing>power(full,113)*4,"firing order stands out");
        check(power(full,75)>power(full,81)*2,"half order 1.5 present under load");
        float[] boosted=run(new EngineSynth(),4000,1,1.2,2);
        check(power(boosted,6000)>power(full,6000)*10,"turbo whistle with boost");
        for(float[] x:new float[][]{idle,coast,full,boosted})check(peak(x)<=1,"bounded");
        EngineSynth b=new EngineSynth();run(b,4000,1,1,1);
        float[] lift=new float[EngineSynth.RATE/4];
        b.set(4000,0,1);b.render(lift,0,lift.length,1);
        check(rms(lift)>0,"lift renders");
        float[] stop=run(b,Double.NaN,0,0,2);
        check(peak(stop)<.001,"fades out when the engine stops: "+peak(stop));
        System.out.println("PASS "+checks+" engine synth checks; idle "+String.format("%.3f",rms(idle))+" coast "+String.format("%.3f",rms(coast))+" full "+String.format("%.3f",rms(full)));
    }
}
