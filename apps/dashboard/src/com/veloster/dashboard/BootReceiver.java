package com.veloster.dashboard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** After boot: the floating window (if switched on) comes back without opening the dashboard first. */
public final class BootReceiver extends BroadcastReceiver {
    public void onReceive(Context context,Intent intent){
        if(intent!=null&&Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()))FloatService.startIfOn(context);
    }
}
