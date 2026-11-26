package com.veloster.carplayhub;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Manifest receiver restores the opt-in shortcut after boot, QuickBoot or package replacement; never opens an Activity. */
public final class QuickEntryReceiver extends BroadcastReceiver {
    public void onReceive(Context context,Intent intent){
        String action=intent==null?null:intent.getAction();
        if(Intent.ACTION_BOOT_COMPLETED.equals(action)||"android.intent.action.MY_PACKAGE_REPLACED".equals(action)
                ||"android.intent.action.QUICKBOOT_POWERON".equals(action)){
            QuickEntryService.restore(context);
            StarKey.dashboard(context,false);   // ☆ toggle: a ☆ left on its stock function when the head unit went off is ours again
        }
        // consent skip hidden: ConsentSkip.onWake not sent (did not work in the car)
    }
}
