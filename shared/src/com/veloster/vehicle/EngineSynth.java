package com.veloster.vehicle;

/**
 * Virtual engine sound for the 1.6 T-GDI inline four from received rpm, pedal and boost only (no recordings, no
 * Android). The same algorithm runs in the listening page (engine-sound.html), sample for sample.
 *
 * Built the way the active-sound-design literature does it:
 * - Engine-order synthesis: half orders 0.5..8 of the crank, phase-locked to rpm, with one amplitude map at no load and
 *   one at full load blended by the pedal (Siemens Simcenter ASD order maps per load; Cao et al., SAE 2020).
 * - Firing pulses: four per 720 degrees, each cylinder a little different and every firing jittered, the
 *   cylinder-to-cylinder micro-structure plain order synthesis misses (Jagla et al.; the order-synthesis limitation
 *   noted in the ASD literature).
 * - Harmonic plus noise: intake/combustion noise grows with load (Doerfler and Wyse, EUSIPCO 2026).
 * - Formant filters: three fixed resonances shape the sum like an exhaust and cabin, so the timbre moves as the orders
 *   sweep through them (formant-filter driving sound, KSNVE 2015).
 * - Turbo whistle with boost, blow-off on a lift with boost, and an occasional overrun pop on a lift at high rpm.
 * Inputs are smoothed per sample because hscan delivers rpm only every 40..80 ms. Mono, -1..1 floats.
 */
public final class EngineSynth {
    public static final int RATE=22050;
    static final int ORDERS=16;
    /** Amplitude of half order m+1 (order (m+1)/2) at no load and at full load. Order 2 (firing) and its multiples lead;
     *  the half orders are the uneven-firing burble that load brings out. */
    static final double[] COAST={.05,.10,.03,1.0,.03,.10,.02,.32,.02,.06,.01,.14,.01,.03,.01,.05};
    static final double[] FULL ={.20,.24,.16,1.0,.14,.30,.11,.55,.09,.22,.07,.36,.06,.15,.05,.20};
    /** Per-cylinder firing strength (a real four is never perfectly even). */
    static final double[] CYLINDER={1.0,.86,.95,.80};
    /** Formant centre Hz, Q and mix. */
    static final double[] FORMANT_HZ={105,420,1150},FORMANT_Q={1.1,2.2,3.0},FORMANT_MIX={1.0,.55,.18};
    static final double DRY=.35,SMOOTH_S=.08,ENVELOPE_S=.15,PULSE_S=.0025,BLOWOFF_S=.4,POP_S=.012;

    private final double rate;
    private int seed=0x2545F491;
    private double rpm,pedal,boost,targetRpm,targetPedal,targetBoost,master;
    private double phase,whistlePhase,wobblePhase;   // phase = half-order phase: 2*pi per 720 degrees of crank
    private int firing=-1;
    private double pulse,blowoff,pop,liftTimer,lastPedal=0,peakBoost;
    private double hp,hpIn,popLp,blowLp;
    private final double[][] bq=new double[3][5],st=new double[3][4];
    private final double[] amp=new double[ORDERS];
    private boolean running;

    public EngineSynth(){this(RATE);}
    public EngineSynth(double rate){
        this.rate=rate;
        for(int f=0;f<3;f++){   // RBJ band-pass, 0 dB peak
            double w=2*Math.PI*FORMANT_HZ[f]/rate,alpha=Math.sin(w)/(2*FORMANT_Q[f]),a0=1+alpha;
            bq[f][0]=alpha/a0;bq[f][1]=0;bq[f][2]=-alpha/a0;bq[f][3]=-2*Math.cos(w)/a0;bq[f][4]=(1-alpha)/a0;
        }
    }

    /** xorshift32, the same in the page: -1..1. */
    private double noise(){seed^=seed<<13;seed^=seed>>>17;seed^=seed<<5;return (seed&0xffffff)/8388608.0-1;}

    /** NaN rpm (engine off, stale data, reverse) fades to silence; pedal 0..1; boost in bar (negative = vacuum). */
    public void set(double rpm,double pedal,double boost){
        running=!Double.isNaN(rpm)&&rpm>300;
        if(running)targetRpm=rpm;
        double p=Double.isNaN(pedal)?0:Math.max(0,Math.min(1,pedal)),b=Double.isNaN(boost)?0:boost;
        if(lastPedal-p>.25&&Math.max(b,peakBoost)>.3)blowoff=1;   // a sharp lift while boosted
        if(lastPedal>.2&&p<.05)liftTimer=2.5;                       // overrun window after a lift
        peakBoost=Math.max(b,peakBoost*.9);
        lastPedal=p;targetPedal=p;targetBoost=b;
    }

    /** Fills out[off..off+n) with -1..1 samples at the given master volume (0..1). */
    public void render(float[] out,int off,int n,double volume){
        double k=1-Math.exp(-1/(SMOOTH_S*rate)),ke=1-Math.exp(-1/(ENVELOPE_S*rate)),pulseDecay=Math.exp(-1/(PULSE_S*rate)),
            blowDecay=1/(BLOWOFF_S*rate),popDecay=Math.exp(-1/(POP_S*rate)),dt=n/rate;
        if(!running&&master<1e-5){master=0;for(int i=0;i<n;i++)out[off+i]=0;return;}   // fully faded: silence
        // Order map blended by load, once per block (pedal moves slowly next to the sample rate).
        double load=pedal;
        for(int m=0;m<ORDERS;m++)amp[m]=COAST[m]+(FULL[m]-COAST[m])*load;
        liftTimer=Math.max(0,liftTimer-dt);
        for(int i=0;i<n;i++){
            rpm+=(targetRpm-rpm)*k;pedal+=(targetPedal-pedal)*k;boost+=(targetBoost-boost)*k;
            master+=((running?1:0)-master)*ke;
            double f=rpm/120;   // half-order frequency: rpm/60 rev/s, one cycle per two revolutions
            phase+=2*Math.PI*f/rate;
            if(phase>=2*Math.PI){phase-=2*Math.PI;}
            // Firing pulse: four per cycle, the cylinder's strength and a jitter on every one.
            int fire=(int)(phase*4/(2*Math.PI));
            if(fire!=firing){
                firing=fire;
                pulse=CYLINDER[fire&3]*(1+.12*noise());
                if(liftTimer>0&&pedal<.05&&rpm>2200&&noise()>.97)pop=1;   // about one firing in 33: a few pops a second
            }
            // Orders: sin(m*phase) for m = 1..16 by the Chebyshev recurrence, one sin and one cos per sample.
            double c2=2*Math.cos(phase),prev=0,cur=Math.sin(phase),v=0;
            for(int m=0;m<ORDERS;m++){v+=amp[m]*cur;double next=c2*cur-prev;prev=cur;cur=next;}
            v*=.30;
            double nz=noise();
            v+=pulse*(.35+.45*nz)*(.30+.50*pedal);          // combustion pulse with noise in it
            v+=nz*.04*(.2+pedal)*Math.min(1,rpm/4000);      // intake / flow noise with load
            pulse*=pulseDecay;
            // Formants (exhaust and cabin), then a DC blocker.
            double y=v*DRY;
            for(int b=0;b<3;b++){
                double[] c=bq[b],s=st[b];
                double o=c[0]*v+c[1]*s[0]+c[2]*s[1]-c[3]*s[2]-c[4]*s[3];
                s[1]=s[0];s[0]=v;s[3]=s[2];s[2]=o;
                y+=o*FORMANT_MIX[b]*3;
            }
            double dc=y-hpIn+.995*hp;hpIn=y;hp=dc;
            double level=master*Math.min(1,rpm/800)*(.45+.55*pedal)*(.60+.40*Math.min(1,rpm/6000));
            double s=dc*level;
            // Turbo whistle rises with boost, with a slow wobble.
            double spool=Math.max(0,Math.min(1,boost/1.2));
            if(spool>0){
                wobblePhase+=2*Math.PI*3.1/rate;
                whistlePhase+=2*Math.PI*(2200+3800*spool+40*Math.sin(wobblePhase))/rate;
                if(whistlePhase>2*Math.PI)whistlePhase-=2*Math.PI;
                s+=Math.sin(whistlePhase)*.035*spool*(.4+.6*pedal)*master;
            }
            // Blow-off: high-passed noise decaying over BLOWOFF_S.
            if(blowoff>0){blowLp+=(nz-blowLp)*.25;s+=(nz-blowLp)*.30*blowoff*master;blowoff=Math.max(0,blowoff-blowDecay);}
            // Overrun pop: a short low-passed crack.
            if(pop>.001){popLp+=(nz-popLp)*.35;s+=popLp*1.4*pop*master;pop*=popDecay;}
            out[off+i]=(float)(Math.tanh(s*.8)*volume);
        }
    }
    public boolean running(){return running;}
}
