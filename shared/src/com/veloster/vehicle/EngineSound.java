package com.veloster.vehicle;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.SystemClock;

/**
 * Plays EngineSynth from the shared Telemetry. On this head unit an app's PCM reaches the amplifier only after the
 * micom opens a path for it (MicomAudioService.startAudio; AudioService never asks), which is why the first version,
 * a plain media-stream AudioTrack, was silent in the car. ROUTES (firmware analysis; tried in the car 2026-11-03): the
 * navi mix channel (Android STREAM_NAVI 12, plays over radio and music) as a system alarm, the only one that sounds
 * (front speakers, no bass), or as a navi sound effect; the main channel as a music source; and the plain media
 * stream without the micom. The last three were silent. Silent in reverse and without fresh rpm.
 */
final class EngineSound implements Runnable {
    /** Android stream and micom stream type per route (SettingsLayout.SOUND_ROUTES names them). */
    private static final int STREAM_NAVI=12;   // android.media.AudioSystem.STREAM_NAVI on this firmware
    private static final int[] ROUTE_STREAM={STREAM_NAVI,STREAM_NAVI,AudioManager.STREAM_MUSIC,AudioManager.STREAM_MUSIC};
    private static final int[] ROUTE_MICOM={0x41,0x40,0x31,-1};   // SYSTEM_ALARM, NAVI_SOUNDEFFECT, MY_MUSIC, none
    private static final int BLOCK=512;   // about 46 ms at 11.025 kHz on top of the 40..80 ms hscan rate
    private final Context context;
    private final Telemetry data;
    private volatile Thread thread;
    private volatile float volume=.7f;
    private int route=-1;
    EngineSound(Context context,Telemetry data){this.context=context;this.data=data;}
    void volume(int percent){volume=Math.max(0,Math.min(100,percent))/100f;}
    /** Engine and exhaust (EngineSynth tables), pop rate / turbo whistle / blow-off in percent of the voice's own, and
     *  the latency compensation in ms. Picked up by the audio thread on its next block. */
    private volatile EngineSynth.Voice voice=EngineSynth.voice(0,0);
    private volatile double popRate=1,turboVol=1,bovVol=1,lead=.08;
    void voice(int engine,int exhaust){voice=EngineSynth.voice(engine,exhaust);}
    void trims(int popPercent,int turboPercent,int bovPercent,int leadMs){popRate=popPercent/100.0;turboVol=turboPercent/100.0;bovVol=bovPercent/100.0;lead=leadMs/1000.0;}

    /** Idempotent. route -1 stops. A new route stops the old thread first, so its endAudio never closes the new path. */
    synchronized void play(int r){
        if(r==route)return;
        Thread old=thread;thread=null;
        if(old!=null){old.interrupt();try{old.join(500);}catch(InterruptedException e){Thread.currentThread().interrupt();}}
        route=r;
        if(r>=0&&r<ROUTE_STREAM.length){thread=new Thread(this,"engine-sound");thread.start();}
    }

    public void run(){
        // Default, not THREAD_PRIORITY_AUDIO: on this head unit the audio band (-16) preempted the cluster's own
        // drawing and the whole UI stuttered. The stream buffer below is large enough to ride out the lower priority.
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_DEFAULT);
        int r=route,micom=ROUTE_MICOM[r];
        Object router=null;AudioTrack track=null;
        try{
            int min=AudioTrack.getMinBufferSize(EngineSynth.RATE,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT);
            int bytes=Math.max(min,BLOCK*2*4);long aheadMs=(bytes/2+BLOCK)*1000L/EngineSynth.RATE;   // write blocks on a full buffer: this far ahead
            track=new AudioTrack(ROUTE_STREAM[r],EngineSynth.RATE,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT,
                bytes,AudioTrack.MODE_STREAM);
            if(track.getState()!=AudioTrack.STATE_INITIALIZED){android.util.Log.w("VehicleDash","Engine sound: AudioTrack not initialised, stream "+ROUTE_STREAM[r]);return;}
            if(micom>=0)router=startMicom(context,micom);
            track.play();
            android.util.Log.i("VehicleDash","Engine sound on: route "+r+" stream "+ROUTE_STREAM[r]+" micom 0x"+Integer.toHexString(micom)+" min buffer "+min);
            EngineSynth.Voice playing=voice;EngineSynth synth=new EngineSynth(EngineSynth.RATE,playing);float[] f=new float[BLOCK];short[] pcm=new short[BLOCK];int flames=0;
            while(thread==Thread.currentThread()){
                long now=SystemClock.elapsedRealtime();EngineSynth.voiced=volume>0;
                Telemetry.Sample rpm=data.sample(0,now),pedal=data.sample(9,now),boost=data.sample(Telemetry.BOOST,now),
                    selector=data.sample(Telemetry.GEAR_SELECTOR,now);
                Telemetry.Sample gear=data.sample(8,now);
                boolean reverse=selector.valid&&(int)selector.value==1||gear.valid&&(int)gear.value==14;   // DCT lever R, or 6MT reverse
                synth.set(rpm.valid&&rpm.ageMs<=1000&&!reverse?rpm.value:Double.NaN,pedal.valid?pedal.value/100.0:0,boost.valid?boost.value:0);
                if(voice!=playing){playing=voice;synth.use(playing);}
                synth.popRate=popRate;synth.turboVol=turboVol;synth.bovVol=bovVol;synth.lead=lead;
                synth.render(f,0,BLOCK,volume);
                if(synth.flames!=flames){flames=synth.flames;if(volume>0)EngineSynth.flameAt=now+aheadMs;}
                for(int i=0;i<BLOCK;i++)pcm[i]=(short)Math.round(f[i]*32000);
                // MODE_STREAM write blocks on a full buffer and paces the loop; a negative return is an error, not
                // back-pressure, so stop rather than spin the CPU rewriting a dead track.
                int w=track.write(pcm,0,BLOCK);
                if(w<0){android.util.Log.w("VehicleDash","Engine sound: AudioTrack.write "+w+", stopping");break;}
            }
        }catch(RuntimeException e){android.util.Log.e("VehicleDash","Engine sound stopped",e);}
        finally{
            EngineSynth.voiced=false;
            if(track!=null){try{track.stop();}catch(IllegalStateException ignored){/* never started */}track.release();}
            if(router!=null)endMicom(router,micom);
        }
    }

    /** MicomAudioManager lives in the head unit's automotive library; anywhere else this logs and plays unrouted. */
    static Object startMicom(Context context,int type){
        try{
            com.hkmc.media.MicomAudioManager m=new com.hkmc.media.MicomAudioManager(context);
            boolean ok=m.startAudio(type);
            android.util.Log.i("VehicleDash","Engine sound: micom startAudio(0x"+Integer.toHexString(type)+") = "+ok);
            return m;
        }catch(LinkageError|RuntimeException e){android.util.Log.w("VehicleDash","Engine sound: no micom audio router",e);return null;}
    }
    static void endMicom(Object router,int type){
        try{boolean ok=((com.hkmc.media.MicomAudioManager)router).endAudio(type);android.util.Log.i("VehicleDash","Engine sound: micom endAudio(0x"+Integer.toHexString(type)+") = "+ok);}
        catch(LinkageError|RuntimeException e){android.util.Log.w("VehicleDash","Engine sound: endAudio failed",e);}
    }
}
