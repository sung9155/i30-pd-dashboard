package com.veloster.vehicle;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioTrack;

/**
 * Two short chime beeps when the shift cue lights (rpm at the target), on the one app audio path that plays in the
 * car (2026-11-03): STREAM_NAVI with the micom SYSTEM_ALARM type 0x41, EngineSound's route 0 (front speakers, no
 * bass, which suits a chime). When the engine sound already holds that micom path the beep only mixes in; otherwise it
 * opens the path for the beep and closes it after. ShiftGuide.Beep decides when.
 */
final class ShiftBeep implements Runnable {
    static final int RATE=22050;
    private static final int STREAM_NAVI=12,MICOM_ALARM=0x41;
    private static final short[] PCM=pcm();
    private final Context context;
    private volatile boolean busy,pathHeld;
    ShiftBeep(Context context){this.context=context.getApplicationContext();}

    /** Starts a beep unless one is still playing. pathHeld: the engine sound has the micom alarm path open. */
    void play(boolean pathHeld){
        if(busy)return;
        busy=true;this.pathHeld=pathHeld;new Thread(this,"shift-beep").start();
    }
    /** 60 ms of silence (the amplifier path opens), then 1760 Hz for 70 ms, 50 ms off, 70 ms again; 5 ms fades. */
    private static short[] pcm(){
        int lead=RATE*60/1000,tone=RATE*70/1000,gap=RATE*50/1000,fade=RATE*5/1000;
        short[] s=new short[lead+tone+gap+tone];
        for(int k=0;k<2;k++){int o=lead+k*(tone+gap);
            for(int i=0;i<tone;i++){double env=Math.min(1,Math.min(i,tone-1-i)/(double)fade);
                s[o+i]=(short)Math.round(Math.sin(2*Math.PI*1760*i/RATE)*env*0.6*32767);}}
        return s;
    }
    public void run(){
        AudioTrack track=null;Object router=null;
        try{
            track=new AudioTrack(STREAM_NAVI,RATE,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT,PCM.length*2,AudioTrack.MODE_STATIC);
            if(track.write(PCM,0,PCM.length)!=PCM.length||track.getState()!=AudioTrack.STATE_INITIALIZED){
                android.util.Log.w("VehicleDash","Shift beep: AudioTrack not ready");return;}
            // ponytail: if the engine sound starts on this path while a beep holds it, the beep's endAudio may close it; rare, rechecked by the next toggle.
            if(!pathHeld)router=EngineSound.startMicom(context,MICOM_ALARM);
            track.play();
            Thread.sleep(PCM.length*1000L/RATE+80);
        }catch(InterruptedException e){Thread.currentThread().interrupt();}
        catch(RuntimeException e){android.util.Log.w("VehicleDash","Shift beep failed",e);}
        finally{
            if(track!=null){try{track.stop();}catch(IllegalStateException ignored){/* never started */}track.release();}
            if(router!=null)EngineSound.endMicom(router,MICOM_ALARM);
            busy=false;
        }
    }
}
