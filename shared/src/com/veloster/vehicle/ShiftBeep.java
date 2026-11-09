package com.veloster.vehicle;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioTrack;

/**
 * A short chime (삐빅 by default, SOUNDS) when the shift cue lights (rpm at the target), on the one app audio path that plays in the
 * car (2026-11-03): STREAM_NAVI with the micom SYSTEM_ALARM type 0x41, EngineSound's route 0 (front speakers, no
 * bass, which suits a chime). When the engine sound already holds that micom path the beep only mixes in; otherwise it
 * opens the path for the beep and closes it after. ShiftGuide.Beep decides when.
 */
final class ShiftBeep implements Runnable {
    static final int RATE=22050;
    private static final int STREAM_NAVI=12,MICOM_ALARM=0x41;
    /** Notes per sound, in SettingsLayout.BEEP_SOUNDS order: Hz, ms, gap after (ms), square (1) or sine (0), decay ms
     *  (0 = flat), amplitude. The same tables and math as the sound page's beepPcm(). */
    static final double[][][] SOUNDS={
        {{1760,70,50,0,0,.6},{1760,70,0,0,0,.6}},                           // 삐빅
        {{2093,110,0,0,0,.6}},                                               // 삑
        {{1319,55,30,0,0,.55},{1760,55,30,0,0,.55},{2349,70,0,0,0,.55}},     // 상승 3음
        {{880,160,0,1,0,.35}},                                               // 레이스 버저
        {{3000,25,45,0,10,.6},{3000,25,0,0,10,.6}},                          // 틱틱
        // 2026-11-05, auditioned on a sample page first (the same synthesis): repeats, pitch steps, chimes, digital
        {{1760,45,35,0,0,.6},{1760,45,35,0,0,.6},{1760,45,0,0,0,.6}},   // 삐빅 · 3번
        {{2093,30,25,0,0,.55},{2093,30,25,0,0,.55},{2093,30,25,0,0,.55},{2093,30,0,0,0,.55}},   // 삐빅 · 4번 빠르게
        {{1568,22,22,0,15,.6},{1568,22,22,0,15,.6},{1568,22,22,0,15,.6},{1568,22,22,0,15,.6},{1568,22,0,0,15,.6}},   // 연타 · 5번
        {{1047,70,50,0,0,.65},{1047,70,0,0,0,.65}},   // 삐빅 · 낮게
        {{2637,60,45,0,0,.5},{2637,60,0,0,0,.5}},   // 삐빅 · 높게
        {{1760,110,45,0,0,.55},{1760,40,0,0,0,.55}},   // 두 번 · 길고 짧게
        {{1319,60,30,0,0,.55},{1760,80,0,0,0,.55}},   // 상승 2음
        {{1760,60,30,0,0,.55},{1319,80,0,0,0,.55}},   // 하강 2음
        {{2349,55,30,0,0,.5},{1760,55,30,0,0,.55},{1319,70,0,0,0,.55}},   // 하강 3음
        {{988,35,18,0,0,.55},{1319,35,18,0,0,.55},{1760,35,18,0,0,.55},{2349,50,0,0,0,.5}},   // 빠른 상승 4음
        {{1175,70,25,0,0,.55},{1760,90,0,0,0,.55}},   // 5도 위로
        {{1047,50,20,0,0,.6},{2093,80,0,0,0,.5}},   // 옥타브 위로
        {{1568,300,0,0,90,.6}},   // 맑은 차임
        {{1319,160,0,0,70,.55},{1976,300,0,0,90,.5}},   // 두 음 차임
        {{880,350,0,0,110,.65}},   // 낮은 차임
        {{2637,160,0,0,35,.55}},   // 유리 톡
        {{4000,15,35,0,6,.6},{4000,15,35,0,6,.6},{4000,15,0,0,6,.6}},   // 디지털 3틱
        {{1175,40,40,1,0,.3},{1175,40,0,1,0,.3}},   // 짧은 펄스 · 낮게
        {{1760,250,0,0,0,.5}},   // 긴 비프
        {{740,140,0,0,0,.6}}};   // 낮은 톤
    private static final short[][] PCM=new short[SOUNDS.length][];
    private static final short[][] PCM_MIX=new short[SOUNDS.length][];
    private final Context context;
    private volatile boolean busy;private volatile int sound;private volatile float vol=1;private volatile short[] own;
    ShiftBeep(Context context){this.context=context.getApplicationContext();}

    /** Plays a beep at volume vol (0..1). onPath = the engine sound that holds the alarm path: the beep is then mixed
     *  into its stream, the one way it is heard over the engine sound (the single alarm path carries only one track).
     *  Otherwise the beep opens the alarm path itself on its own track. Does nothing if its own-track beep is still playing. */
    void play(EngineSound onPath,int sound,float vol){
        int s=Math.max(0,Math.min(SOUNDS.length-1,sound));
        if(onPath!=null){onPath.mixBeep(pcmMix(s),vol);return;}
        if(busy)return;
        busy=true;this.sound=s;this.own=pcm(s);this.vol=Math.max(0,Math.min(1,vol));new Thread(this,"shift-beep").start();
    }
    /** A coin hitting the road on the 에코 screen: 10, 100 or 500 won, light to heavy. Same paths as play(); a clink
     *  while the last own-track one still sounds is dropped (at most about three a second). */
    void playCoin(EngineSound onPath,int den,float vol){
        int k=den>=500?2:den>=100?1:0;
        if(onPath!=null){onPath.mixBeep(coinPcm(k,true),vol);return;}
        if(busy)return;
        busy=true;this.own=coinPcm(k,false);this.vol=Math.max(0,Math.min(1,vol));new Thread(this,"coin-clink").start();
    }
    /** Inharmonic partials (Hz, amplitude, decay ms) per coin, then a softer second clink where it bounces: the small
     *  10 won rings high and short, the 500 won lower, longer and with a thud. */
    private static final double[][][] COIN={
        {{5200,.35,28},{7400,.25,18},{3600,.22,40}},
        {{3900,.38,45},{6100,.25,30},{2700,.27,60}},
        {{2600,.4,70},{4300,.3,50},{1500,.3,90},{6800,.15,30},{180,.3,25}}};
    private static final int[] COIN_MS={130,200,280};
    private static final short[][] COIN_PCM=new short[6][];
    static synchronized short[] coinPcm(int k,boolean mix){
        int slot=k*2+(mix?1:0);if(COIN_PCM[slot]!=null)return COIN_PCM[slot];
        int rate=mix?EngineSynth.RATE:RATE,lead=mix?0:Math.round(rate*0.06f),n=lead+rate*COIN_MS[k]/1000;double[] w=new double[n];
        double[][] parts=COIN[k];java.util.Random r=new java.util.Random(k);
        for(int hit=0;hit<2;hit++){int start=lead+(hit==0?0:rate*75/1000);double g=hit==0?1:.45;
            for(int i=start;i<n;i++){double tm=(i-start)/(double)rate,ms=tm*1000,v=0;
                for(double[] p:parts)if(p[0]<rate*.45)v+=p[1]*Math.exp(-ms/p[2])*Math.sin(2*Math.PI*p[0]*tm);
                if(ms<2)v+=(r.nextDouble()*2-1)*.3*(1-ms/2);   // the strike
                w[i]+=g*v;}}
        double peak=1e-9;for(double x:w)peak=Math.max(peak,Math.abs(x));
        short[] out=new short[n];for(int i=0;i<n;i++)out[i]=(short)Math.round(w[i]/peak*.8*32767);
        return COIN_PCM[slot]=out;
    }
    /** 60 ms of silence (the amplifier path opens), then the sound's notes, each with 5 ms fades; built once per sound. */
    static synchronized short[] pcm(int sound){return PCM[sound]!=null?PCM[sound]:(PCM[sound]=build(sound,RATE,60));}
    /** The same notes at the engine stream's rate with no lead, for mixing into an already-open EngineSound stream. */
    static synchronized short[] pcmMix(int sound){return PCM_MIX[sound]!=null?PCM_MIX[sound]:(PCM_MIX[sound]=build(sound,EngineSynth.RATE,0));}
    private static short[] build(int sound,int rate,int leadMs){
        double[][] notes=SOUNDS[sound];int lead=Math.round(rate*leadMs/1000f),n=lead;double fade=rate*5/1000.0;
        for(double[] t:notes)n+=Math.round(rate*t[1]/1000)+Math.round(rate*t[2]/1000);
        short[] s=new short[n];int o=lead;
        for(double[] t:notes){int len=(int)Math.round(rate*t[1]/1000);double ph=0;
            for(int i=0;i<len;i++){ph+=2*Math.PI*t[0]/rate;
                double env=Math.min(1,Math.min(i,len-1-i)/fade);if(t[4]>0)env*=Math.exp(-(i*1000.0/rate)/t[4]);
                double w=t[3]>0?square(ph):Math.sin(ph);
                s[o+i]=(short)Math.round(Math.max(-1,Math.min(1,w*env*t[5]))*32767);}
            o+=len+(int)Math.round(rate*t[2]/1000);}
        return s;
    }
    /** Band-limited square: odd harmonics up to the 9th. */
    private static double square(double ph){double s=0;for(int k=1;k<=9;k+=2)s+=Math.sin(k*ph)/k;return s*4/Math.PI*.9;}
    /** Own-track path (the engine sound is not on the alarm channel): open the alarm path, play the static PCM at vol, close it. */
    public void run(){
        AudioTrack track=null;Object router=null;short[] pcm=own;float v=vol;
        try{
            track=new AudioTrack(STREAM_NAVI,RATE,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT,pcm.length*2,AudioTrack.MODE_STATIC);
            if(track.write(pcm,0,pcm.length)!=pcm.length||track.getState()!=AudioTrack.STATE_INITIALIZED){
                android.util.Log.w("VehicleDash","Shift beep: AudioTrack not ready");return;}
            track.setStereoVolume(v,v);
            router=EngineSound.startMicom(context,MICOM_ALARM);
            track.play();
            Thread.sleep(pcm.length*1000L/RATE+80);
        }catch(InterruptedException e){Thread.currentThread().interrupt();}
        catch(RuntimeException e){android.util.Log.w("VehicleDash","Shift beep failed",e);}
        finally{
            if(track!=null){try{track.stop();}catch(IllegalStateException ignored){/* never started */}track.release();}
            if(router!=null)EngineSound.endMicom(router,MICOM_ALARM);
            busy=false;
        }
    }
}
