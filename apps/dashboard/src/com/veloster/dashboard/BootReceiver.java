package com.veloster.dashboard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** After boot: the floating window (if switched on) comes back without opening the dashboard first. */
public final class BootReceiver extends BroadcastReceiver {
    /** Brings the floating window back (if it is switched on) without opening the dashboard: after an update over adb while driving,
     *  `adb shell am broadcast -a com.veloster.dashboard.FLOAT_START`. Starts nothing that the setting has not already switched on. */
    static final String FLOAT_START="com.veloster.dashboard.FLOAT_START";
    /** 설정 → 진단 → 시동 때 USB 디버깅 켜기 (default off: debugging takes the port from the CarPlay dongle). */
    public static final String USB_AT_BOOT="usb_debug_at_boot";
    private static final String QUICKBOOT="android.intent.action.QUICKBOOT_POWERON";
    public void onReceive(Context context,Intent intent){
        if(intent!=null&&FLOAT_START.equals(intent.getAction())&&intent.getBooleanExtra("reset_cells",false)){   // --ez reset_cells true: every cell back to 자동
            android.content.SharedPreferences.Editor e=context.getSharedPreferences("pit_vehicle_ui",0).edit();
            for(int k=0;k<com.veloster.vehicle.FloatPanel.MAX_CELLS;k++)e.putInt("float_cell_"+k,-1);e.commit();}
        String a=intent==null?null:intent.getAction();
        if(Intent.ACTION_BOOT_COMPLETED.equals(a)||FLOAT_START.equals(a))FloatService.startIfOn(context);
        if((Intent.ACTION_BOOT_COMPLETED.equals(a)||QUICKBOOT.equals(a))&&context.getSharedPreferences("pit_vehicle_ui",0).getBoolean(USB_AT_BOOT,false)){
            final Context app=context.getApplicationContext();final PendingResult done=goAsync();   // the Factory Test lines take ~2.4 s
            new Thread(new Runnable(){public void run(){
                try{com.veloster.vehicle.UsbDebugControl.request(app,true);}   // already on: the same lines again change nothing
                catch(Exception e){android.util.Log.w("VehicleDash","USB debugging at start-up failed: "+e);}
                catch(LinkageError e){android.util.Log.w("VehicleDash","USB debugging at start-up failed: "+e);}
                finally{done.finish();}}},"usb-at-boot").start();}
    }
}
