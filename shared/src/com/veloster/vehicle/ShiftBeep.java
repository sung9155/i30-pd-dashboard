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
        {{3000,25,45,0,10,.6},{3000,25,0,0,10,.6}}};                         // 틱틱
    private static final short[][] PCM=new short[SOUNDS.length][];
    private static final short[][] PCM_MIX=new short[SOUNDS.length][];
    private final Context context;
    private volatile boolean busy;private volatile int sound;private volatile float vol=1;
    ShiftBeep(Context context){this.context=context.getApplicationContext();}

    /** Plays a beep at volume vol (0..1). onPath = the engine sound that holds the alarm path: the beep is then mixed
     *  into its stream, the one way it is heard over the engine sound (the single alarm path carries only one track).
     *  Otherwise the beep opens the alarm path itself on its own track. Does nothing if its own-track beep is still playing. */
    void play(EngineSound onPath,int sound,float vol){
        int s=Math.max(0,Math.min(SOUNDS.length-1,sound));
        if(onPath!=null){onPath.mixBeep(pcmMix(s),vol);return;}
        if(busy)return;
        busy=true;this.sound=s;this.vol=Math.max(0,Math.min(1,vol));new Thread(this,"shift-beep").start();
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
        AudioTrack track=null;Object router=null;short[] pcm=pcm(sound);float v=vol;
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
