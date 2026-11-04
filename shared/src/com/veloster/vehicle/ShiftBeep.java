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
    private final Context context;
    private volatile boolean busy,pathHeld;private volatile int sound;
    ShiftBeep(Context context){this.context=context.getApplicationContext();}

    /** Starts a beep unless one is still playing. pathHeld: the engine sound has the micom alarm path open. */
    void play(boolean pathHeld,int sound){
        if(busy)return;
        busy=true;this.pathHeld=pathHeld;this.sound=Math.max(0,Math.min(SOUNDS.length-1,sound));new Thread(this,"shift-beep").start();
    }
    /** 60 ms of silence (the amplifier path opens), then the sound's notes, each with 5 ms fades; built once per sound. */
    static synchronized short[] pcm(int sound){
        if(PCM[sound]!=null)return PCM[sound];
        double[][] notes=SOUNDS[sound];int lead=Math.round(RATE*60/1000f),n=lead;double fade=RATE*5/1000.0;
        for(double[] t:notes)n+=Math.round(RATE*t[1]/1000)+Math.round(RATE*t[2]/1000);
        short[] s=new short[n];int o=lead;
        for(double[] t:notes){int len=(int)Math.round(RATE*t[1]/1000);double ph=0;
            for(int i=0;i<len;i++){ph+=2*Math.PI*t[0]/RATE;
                double env=Math.min(1,Math.min(i,len-1-i)/fade);if(t[4]>0)env*=Math.exp(-(i*1000.0/RATE)/t[4]);
                double w=t[3]>0?square(ph):Math.sin(ph);
                s[o+i]=(short)Math.round(Math.max(-1,Math.min(1,w*env*t[5]))*32767);}
            o+=len+(int)Math.round(RATE*t[2]/1000);}
        return PCM[sound]=s;
    }
    /** Band-limited square: odd harmonics up to the 9th. */
    private static double square(double ph){double s=0;for(int k=1;k<=9;k+=2)s+=Math.sin(k*ph)/k;return s*4/Math.PI*.9;}
    public void run(){
        AudioTrack track=null;Object router=null;short[] pcm=pcm(sound);
        try{
            track=new AudioTrack(STREAM_NAVI,RATE,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT,pcm.length*2,AudioTrack.MODE_STATIC);
            if(track.write(pcm,0,pcm.length)!=pcm.length||track.getState()!=AudioTrack.STATE_INITIALIZED){
                android.util.Log.w("VehicleDash","Shift beep: AudioTrack not ready");return;}
            // ponytail: if the engine sound starts on this path while a beep holds it, the beep's endAudio may close it; rare, rechecked by the next toggle.
            if(!pathHeld)router=EngineSound.startMicom(context,MICOM_ALARM);
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
