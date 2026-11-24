package com.veloster.dashboard;

import android.content.Context;
import android.os.Handler;
import com.hkmc.bcm.BCMManager;
import com.hkmc.bcm.BCMPowerListener;
import com.hkmc.micom.NModeListener;
import com.hkmc.micom.NModeManager;
import com.hkmc.micom.VehicleInfoListener;
import com.hkmc.micom.VehicleInfoManager;
import com.hkmc.pgs.PgsManager;
import com.hkmc.pgs.PgsStatusListener;
import com.hkmc.setup.VideoSetupListener;
import com.hkmc.setup.VideoSetupManager;
import com.veloster.vehicle.VehicleDash;

/** Passive framework subscriptions only. No drive-mode setters, stream commands or OEM app IPC. */
final class VehicleDataClient implements Runnable {
    private final Context context;
    private final Handler handler=new Handler();
    private volatile boolean active,ignition;
    // i30 PD: the BCM ignition service may be absent. The head unit only runs with the key on and the
    // hscan subscriber is read-only, so an unknown ignition state is treated as on.
    private volatile boolean ignitionUnknown;
    private volatile int generation;
    private volatile HscanDataClient hscan;
    private volatile com.veloster.vehicle.ObdLive obd;
    private volatile com.veloster.vehicle.TmHscan tm;
    private NModeManager nmode;
    private VehicleInfoManager vehicle;
    private BCMManager power;
    private PgsManager rear;
    private VideoSetupManager video;
    // volatile: a callback checks it is still the registered listener (a replaced one's late calls are ignored)
    private volatile NModeListener nlistener;
    private volatile VehicleInfoListener vlistener;
    private volatile BCMPowerListener plistener;
    private volatile PgsStatusListener rlistener;
    private volatile VideoSetupListener dlistener;
    private VehicleDataClient(Context context){this.context=context;}
    private static VehicleDataClient shared;
    /** The app's one client: the dashboard and the floating window hand it to each other without a stop in between. */
    static synchronized VehicleDataClient shared(Context c){if(shared==null)shared=new VehicleDataClient(c.getApplicationContext());return shared;}
    /** Retry delay for services that are missing; doubles up to 30 s (the BCM service may not exist on the PD). */
    private long retryMs=2000;
    synchronized void start(){if(active)return;active=true;generation++;ignition=false;VehicleDash.ignition(false);retryMs=2000;run();}
    private boolean accepts(int token){return active&&token==generation&&(ignition||ignitionUnknown);}
    /** i30 PD has no BCM service: after the first miss, stop asking (the lookup runs on the UI thread). */
    private boolean bcmAbsent(){return ignitionUnknown&&"pd_kr".equals(android.os.Build.PRODUCT);}
    public synchronized void run(){
        if(!active)return;
        handler.removeCallbacks(this);   // one pending retry at most
        final int token=generation;
        if(power==null&&!bcmAbsent())try{
            power=(BCMManager)com.hkmc.VehicleInfoManager.getInstance(context).getService(com.hkmc.VehicleInfoManager.BCM_SERVICE);
            if(power==null)throw new IllegalStateException("BCM service unavailable");
            ignition=power.getIGNStatus();ignitionUnknown=false;
            plistener=new BCMPowerListener(){public void onIgnitionChanged(boolean on){
                synchronized(VehicleDataClient.this){
                if(!active||token!=generation||plistener!=this)return;
                if(ignition!=on){
                    ignition=on;
                    HscanDataClient current=hscan;
                    if(current!=null)current.stop();
                    VehicleDash.ignition(on);
                    if(on&&current!=null)current.start();
                    if(on)pollers(token);   // OBD and tm_hscan too: made with the ignition off (a floating window up from boot at ACC), they never started
                }
                }
            }};
            power.addPowerListener(plistener);
        }catch(RuntimeException e){releasePower();ignitionUnknown=true;log("power",e);}catch(LinkageError e){releasePower();ignitionUnknown=true;log("power",e);}
        if(nmode==null)try{
            nmode=new NModeManager(context);
            nlistener=new NModeListener(){
                public void onPowerTorqueTurboBoostReceived(byte[] b){if(nlistener==this&&accepts(token)){VehicleDash.rpm(b);VehicleDash.calculateObserved();}}
                public void onGForceDataReceived(byte[] b){if(nlistener==this&&accepts(token)){VehicleDash.gforce(b);VehicleDash.calculateObserved();}}
                public void onOilCoolantTemperature(byte[] b){if(nlistener==this&&accepts(token))VehicleDash.temperatures(b);}
                public void onSpeedTempUnitTempReceived(byte[] b){if(nlistener==this&&accepts(token))VehicleDash.units(b);}
                public void onNAddInfoReceived(byte[] b){if(nlistener==this&&accepts(token))VehicleDash.additional(b);}
                // Some OEM app subclasses declare this; captured250902 base/proxy lacks dispatch.
                public void onNModeZeroSpeedChanged(byte[] b){if(nlistener==this&&accepts(token))VehicleDash.wheels(b);}
            };
            nmode.registerNModeListener(nlistener);
        }catch(RuntimeException e){releaseNmode();log("NMode",e);}catch(LinkageError e){releaseNmode();log("NMode",e);}
        if(vehicle==null)try{
            vehicle=new VehicleInfoManager(context);
            vlistener=new VehicleInfoListener(){public void onVehicleSpeedChanged(int speed){if(vlistener==this&&accepts(token))VehicleDash.speed(speed);}};
            vehicle.addListener(vlistener);
        }catch(RuntimeException e){releaseVehicle();log("speed",e);}catch(LinkageError e){releaseVehicle();log("speed",e);}
        if(rear==null)try{
            rear=new PgsManager(context);
            rlistener=new PgsStatusListener(){public void onRVMViewStatusChanged(int view){if(rlistener==this&&accepts(token))VehicleDash.rearView(view);}};
            rear.registerPgsStatusListener(rlistener);
        }catch(RuntimeException e){releaseRear();log("rear view",e);}catch(LinkageError e){releaseRear();log("rear view",e);}
        if(video==null)try{   // the stock screen mode and illumination: FloatPanel reads the two ints (events only, no polling)
            video=new VideoSetupManager(context);
            dlistener=new VideoSetupListener(){
                public void onDimChanged(int dim){if(dlistener==this&&active&&token==generation)com.veloster.vehicle.DayNight.oemDim=dim;}
                public void onIlluminateStatusChanged(boolean on){if(dlistener==this&&active&&token==generation)com.veloster.vehicle.DayNight.oemIllum=on?1:0;}
            };
            video.addListener(dlistener);
            com.veloster.vehicle.DayNight.oemDim=video.getDim();com.veloster.vehicle.DayNight.oemIllum=video.getIlluminateStatus()?1:0;
        }catch(RuntimeException e){releaseVideo();log("video setup",e);}catch(LinkageError e){releaseVideo();log("video setup",e);}
        if(hscan==null)try{
            hscan=new HscanDataClient(new HscanDataClient.Gate(){public boolean accepts(){return VehicleDataClient.this.accepts(token);}});
        }catch(RuntimeException e){hscan=null;log("HSCAN",e);}catch(LinkageError e){hscan=null;log("HSCAN",e);}
        if(hscan!=null&&(ignition||ignitionUnknown))hscan.start();
        if(obd==null)try{obd=new com.veloster.vehicle.ObdLive();}catch(RuntimeException e){obd=null;log("OBD",e);}catch(LinkageError e){obd=null;log("OBD",e);}
        if(tm==null)try{tm=new com.veloster.vehicle.TmHscan();}catch(RuntimeException e){tm=null;log("tm_hscan",e);}catch(LinkageError e){tm=null;log("tm_hscan",e);}
        pollers(token);
        // every missing subscriber is tried again (rear view, hscan and OBD too, which used to stay off until the app restarted);
        // the ones in place stay registered, and an absent service is asked less and less often (2 s doubling to 30 s)
        if((power==null&&!bcmAbsent())||nmode==null||vehicle==null||rear==null||video==null||hscan==null||obd==null||tm==null){handler.postDelayed(this,retryMs);retryMs=Math.min(30000,retryMs*2);}
        else retryMs=2000;
    }
    /** Starts the OBD and tm_hscan pollers while the ignition is on (each start does nothing while it already runs): from run(),
     *  and when the ignition comes on, since run() is not called again once every subscriber is in place. */
    private void pollers(final int token){
        if(!(ignition||ignitionUnknown))return;
        if(obd!=null)obd.start(
            new com.veloster.vehicle.ObdLive.Sink(){public void value(int pid,byte[] d){if(accepts(token))VehicleDash.obd(pid,d);}},
            new com.veloster.vehicle.ObdLive.Gate(){public boolean open(){return accepts(token);}});
        if(tm!=null)tm.start(
            new com.veloster.vehicle.TmHscan.Sink(){public void value(int sig,float v,boolean ok){if(accepts(token))VehicleDash.tm(sig,v,ok);}},
            new com.veloster.vehicle.TmHscan.Gate(){public boolean open(){return accepts(token);}});
    }
    synchronized void stop(){
        if(!active)return;active=false;generation++;handler.removeCallbacks(this);
        if(hscan!=null){hscan.stop();hscan=null;}
        if(obd!=null){obd.stop();obd=null;}
        if(tm!=null){tm.stop();tm=null;}
        releaseNmode();releaseVehicle();releasePower();releaseRear();releaseVideo();retryMs=2000;
    }
    // A registration that failed half way (manager made, listener not added) is undone here too, so a retry starts clean.
    private void releaseNmode(){NModeManager m=nmode;NModeListener l=nlistener;nmode=null;nlistener=null;
        try{if(m!=null&&l!=null)m.unregisterNModeListener(l);}catch(RuntimeException e){log("NMode stop",e);}catch(LinkageError e){log("NMode stop",e);}}
    private void releaseVehicle(){VehicleInfoManager m=vehicle;VehicleInfoListener l=vlistener;vehicle=null;vlistener=null;
        try{if(m!=null&&l!=null)m.removeListener(l);}catch(RuntimeException e){log("speed stop",e);}catch(LinkageError e){log("speed stop",e);}}
    private void releasePower(){BCMManager m=power;BCMPowerListener l=plistener;power=null;plistener=null;
        try{if(m!=null&&l!=null)m.removePowerListener(l);}catch(RuntimeException e){log("power stop",e);}catch(LinkageError e){log("power stop",e);}}
    private void releaseRear(){PgsManager m=rear;PgsStatusListener l=rlistener;rear=null;rlistener=null;
        try{if(m!=null&&l!=null)m.unregisterPgsStatusListener(l);}catch(RuntimeException e){log("rear view stop",e);}catch(LinkageError e){log("rear view stop",e);}}
    private void releaseVideo(){VideoSetupManager m=video;VideoSetupListener l=dlistener;video=null;dlistener=null;
        com.veloster.vehicle.DayNight.oemDim=com.veloster.vehicle.DayNight.UNKNOWN;com.veloster.vehicle.DayNight.oemIllum=com.veloster.vehicle.DayNight.UNKNOWN;
        try{if(m!=null&&l!=null)m.removeListener(l);}catch(RuntimeException e){log("video setup stop",e);}catch(LinkageError e){log("video setup stop",e);}}
    private static final java.util.Set<String> LOGGED=new java.util.HashSet<String>();
    /** Full trace once per source; repeats of a missing service log one line. */
    private static void log(String source,Throwable error){
        if(LOGGED.add(source)){android.util.Log.e("NDashboard","Subscription unavailable: "+source,error);com.veloster.diagnostics.ErrorLog.record(source,error);}   // the record once per source too
        else android.util.Log.w("NDashboard","Subscription unavailable: "+source+" ("+error+")");
    }
}
