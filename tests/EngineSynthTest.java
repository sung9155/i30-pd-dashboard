import com.veloster.vehicle.EngineSynth;
import com.veloster.vehicle.EngineSynthClassic;
import com.veloster.vehicle.SettingsLayout;

/** Virtual engine sound: silent when off, follows the firing order, louder and brighter with load, harmonics well
 *  above 800 Hz (the first, sine-order version had almost none: an organ), every engine and exhaust bounded, the
 *  listener's tuning, overrun burble that the pop rate scales, and latency compensation that cuts the lag without
 *  overshooting a gear change. */
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
    /** Power in lo..hi Hz (Goertzel every 2 Hz). */
    private static double band(float[] x,double lo,double hi){double p=0;for(double hz=lo;hz<=hi;hz+=2)p+=power(x,hz);return p;}
    /** Kurtosis of what is left above about 700 Hz (two one-pole high-passes): 3 for steady noise, far above for a train of clicks. */
    private static double clatter(float[] x){
        double k=1-Math.exp(-2*Math.PI*700/EngineSynth.RATE),a=0,b=0,m2=0,m4=0;
        for(float v:x){a+=(v-a)*k;double h=v-a;b+=(h-b)*k;h-=b;m2+=h*h;m4+=h*h*h*h;}
        return m4/x.length/(m2/x.length*m2/x.length);
    }
    private static float[] tuned(double tone,double rough,double body,double intake,double uneven,double low,double rpm,double pedal){
        EngineSynth s=new EngineSynth();s.tune(tone,rough,body,intake,uneven,low);return run(s,rpm,pedal,0,2);
    }
    /** Energy added by overrun pops over three lifts from 4000 rpm (one lift is too few big pops to count on): the voice
     *  minus the same voice with pops off. */
    static int lastFlames;
    private static double burble(double popRate){
        EngineSynth a=new EngineSynth(),b=new EngineSynth();a.popRate=popRate;b.popRate=0;
        float[] x=new float[512],y=new float[512];double e=0;
        for(int lift=0;lift<3;lift++){
        for(int i=0;i<60;i++){a.set(4500,1,1);b.set(4500,1,1);a.render(x,0,512,1);b.render(y,0,512,1);}
        for(int i=0;i<108;i++){a.set(4000-i*15,0,-.5);b.set(4000-i*15,0,-.5);a.render(x,0,512,1);b.render(y,0,512,1);for(int k=0;k<512;k++){double d=x[k]-y[k];e+=d*d;}}
        }
        lastFlames=a.flames;return e;
    }
    /** Mean rpm lag (rpm) while accelerating 2000 rpm/s with CAN frames every 50 ms arriving delayMs late, and the
     *  furthest the sound drops below the engine after an upshift. */
    private static double[] lag(double lead,int delayMs){
        EngineSynth s=new EngineSynth();s.lead=lead;float[] x=new float[512];double sum=0,low=0;int n=0;
        for(int i=0;i<(int)(3.5*EngineSynth.RATE/512);i++){
            double t=i*512.0/EngineSynth.RATE,ct=Math.floor((t-delayMs/1000.0)/.05)*.05;
            s.set(ct<0?1500:truth(ct),.8,.5);s.render(x,0,512,1);
            if(t>1&&t<1.9){sum+=truth(t)-s.rpmForTest();n++;}
            if(t>2.3&&t<3)low=Math.min(low,s.rpmForTest()-truth(t));
        }
        return new double[]{sum/n,low};
    }
    /** How far the sound is past the engine (rpm) after a 2000 rpm/s pull ends at t = 1 s and the rpm stays put, CAN frames
     *  every 80 ms, 80 ms of lead, with the pedal let go at that moment or held: {worst from 50 to 200 ms after, at 1.8 s}.
     *  (What the lead had added by t = 1 s is there either way; held, it goes on until the slope is seen to be stale.) */
    private static double[] overshoot(boolean lift){
        EngineSynth s=new EngineSynth();s.lead=.08;float[] x=new float[512];double over=0;
        for(int i=0;i<(int)(1.8*EngineSynth.RATE/512);i++){
            double t=i*512.0/EngineSynth.RATE,ct=Math.floor(t/.08)*.08;
            s.set(2000+Math.min(1,ct)*2000,lift&&ct>=1?0:.8,.5);s.render(x,0,512,1);
            if(t>1.05&&t<1.2)over=Math.max(over,s.rpmForTest()-4000);
        }
        return new double[]{over,s.rpmForTest()-4000};
    }
    private static double truth(double t){return t<.5?1500:t<2?1500+(t-.5)*2000:3000+Math.max(0,t-2.15)*1500;}

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
        // Rich like an engine, not an organ: under load at 3000 rpm the 800-2500 Hz band is within 10 dB of 80-250 Hz
        // (measured -6 dB; the sine-order version was near -20 dB), and a lift dulls it.
        double hiFull=band(full,800,2500),loFull=band(full,80,250),hiCoast=band(coast,800,2500),loCoast=band(coast,80,250);
        check(hiFull>loFull*.1,"harmonics above 800 Hz under load: "+hiFull/loFull);
        check(hiFull/loFull>hiCoast/loCoast*3,"brighter under load than off it: "+hiFull/loFull+" vs "+hiCoast/loCoast);
        // The listener's tuning: each knob moves what it names, and the design values are the untuned sound.
        float[] plainTune=tuned(1,1,1,1,1,SettingsLayout.SOUND_DEFAULT[SettingsLayout.SOUND_LOW-SettingsLayout.SOUND_VOLUME]/100.0,3000,1);   // the settings default is the design value
        check(java.util.Arrays.equals(plainTune,full),"tuning at the design values changes nothing");
        check(band(tuned(2.5,1,1,1,1,.3,3000,1),2500,4500)>band(tuned(.5,1,1,1,1,.3,3000,1),2500,4500)*4,"밝기 lifts the top");
        check(power(tuned(1,3,1,1,0,.3,3000,1),987)>power(tuned(1,0,1,1,0,.3,3000,1),987)*4,"거칠기 fills between the orders");
        check(rms(tuned(1,1,1,1,1,.6,3000,0))>rms(tuned(1,1,1,1,1,.1,3000,0))*3,"저부하 음량 sets the off-load level");
        check(rms(tuned(1,1,1,3,1,.3,3000,1))>rms(tuned(1,1,1,0,1,.3,3000,1))*1.05,"흡기음 adds on the pedal");
        check(power(tuned(1,0,1,1,3,.3,3000,1),2987)>power(tuned(1,0,1,1,0,.3,3000,1),2987)*4,"불규칙함 smears the high orders into noise");
        float[] dry=tuned(1,1,0,1,1,.3,3000,1),wet=tuned(1,1,2,1,1,.3,3000,1);double differ=0;for(int i=0;i<dry.length;i++)differ+=Math.abs(dry[i]-wet[i]);
        check(differ/dry.length>.01&&peak(wet)<=1,"울림 changes the wavelets, bounded");
        // A firing's mids and highs are spread over the firing interval: one click per firing, heard apart at idle, is a diesel (14 before).
        check(clatter(tuned(1,1,1,1,1,.3,750,0))<8&&clatter(tuned(1,1,0,1,1,.3,750,0))>11,"idle does not clatter; 울림 0 is the dry click");
        float[] boosted=run(new EngineSynth(),4000,1,1.2,2);
        EngineSynth quiet=new EngineSynth();quiet.turboVol=0;EngineSynth loud=new EngineSynth();loud.turboVol=2;
        double w0=band(run(quiet,4000,1,1.2,2),4450,4750),w1=band(boosted,4450,4750),w2=band(run(loud,4000,1,1.2,2),4450,4750);
        check(w1>w0*2&&w2>w1*2,"turbo whistle stands out of the engine with boost and follows its trim: "+w1/w0+" / "+w2/w0);
        for(float[] x:new float[][]{idle,coast,full,boosted})check(peak(x)<=1,"bounded");
        // Every engine through every exhaust plays, bounded.
        check(SettingsLayout.SOUND_CLASSIC==EngineSynth.engines()&&SettingsLayout.SOUND_ENGINES.length==EngineSynth.engines()+EngineSynthClassic.engines()
            &&SettingsLayout.SOUND_EXHAUSTS.length==EngineSynth.exhausts()&&EngineSynthClassic.exhausts()==EngineSynth.exhausts(),"a name per engine (new, then 예전) and exhaust");
        // The 예전 engines: the sound before 2026-11-20, every voice plays, bounded, turbo rows as named.
        for(int e=0;e<EngineSynthClassic.engines();e++){
            check(SettingsLayout.SOUND_HAS_TURBO[SettingsLayout.SOUND_CLASSIC+e]==EngineSynthClassic.voice(e,0).turbo(),"turbo rows follow 예전 engine "+e);
            for(int x=0;x<EngineSynthClassic.exhausts();x++){
                EngineSynthClassic c=new EngineSynthClassic(EngineSynth.RATE,EngineSynthClassic.voice(e,x));int n=2*EngineSynth.RATE;float[] all=new float[n];
                for(int i=0;i<n;i+=512){c.set(5000,1,1);c.render(all,i,Math.min(512,n-i),1);}
                float[] last=new float[EngineSynth.RATE];System.arraycopy(all,n-last.length,last,0,last.length);
                check(rms(last)>.05&&peak(last)<=1,"예전 engine "+e+" exhaust "+x+": "+rms(last));
            }
        }
        for(int e=0;e<EngineSynth.engines();e++){
            check(SettingsLayout.SOUND_HAS_TURBO[e]==EngineSynth.voice(e,0).turbo(),"turbo rows follow engine "+e);
            for(int x=0;x<EngineSynth.exhausts();x++){
                float[] v=run(new EngineSynth(EngineSynth.RATE,EngineSynth.voice(e,x)),5000,1,1,2);
                check(rms(v)>.1&&peak(v)<=1,"engine "+e+" exhaust "+x+": "+rms(v));
            }
        }
        // V8 fires four times a revolution: order 4 at 3000 rpm is 200 Hz.
        float[] v8=run(new EngineSynth(EngineSynth.RATE,EngineSynth.voice(3,0)),3000,1,0,2);
        check(power(v8,200)>power(v8,180)*4,"V8 firing order");
        // Overrun burble follows the pop rate.
        double b0=burble(0);int f0=lastFlames;double b1=burble(1);int f1=lastFlames;double b2=burble(2);int f2=lastFlames;
        check(f0==0&&f1>=1&&f1<=9&&f2>=1&&f2<=9,"flames: a few big pops per lift (three lifts), none with pops off: "+f0+" / "+f1+" / "+f2);
        check(b0==0&&b1>0&&b2>b1*1.3,"burble scales with the pop rate: "+b1+" -> "+b2);
        // Latency compensation: with 50 ms of CAN delay, 80 ms of lead takes most of the lag away and the upshift
        // is held rather than extrapolated.
        double[] plain=lag(0,50),comp=lag(.08,50);
        check(plain[0]>200&&Math.abs(comp[0])<80,"lag "+plain[0]+" rpm -> "+comp[0]+" rpm");
        check(comp[1]>-150,"no dive after the upshift: "+comp[1]);
        double[] held=overshoot(false),let=overshoot(true);
        check(held[0]>100&&let[0]<50,"letting the pedal go holds the lead: the sound is "+held[0]+" rpm past the engine with the pedal held, "+let[0]+" with it let go");
        check(Math.abs(held[1])<5&&Math.abs(let[1])<5,"a slope gone stale is dropped: "+held[1]+" / "+let[1]+" rpm off once the rpm has stopped");
        EngineSynth b=new EngineSynth();run(b,4000,1,1,1);
        float[] stop=run(b,Double.NaN,0,0,2);
        check(peak(stop)<.001,"fades out when the engine stops: "+peak(stop));
        System.out.println("PASS "+checks+" engine synth checks; lag "+Math.round(plain[0])+" -> "+Math.round(comp[0])+" rpm");
    }
}
