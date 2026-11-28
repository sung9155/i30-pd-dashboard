package com.veloster.carplayhub;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.veloster.carplayhub.usb.UsbInstaller;

/** USB 업데이트 replaced My Apps itself (the process ended mid-install): remove the staged copies and open the screen
 *  again on what was installed, once. */
public final class UsbUpdateDone extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        UsbInstaller.clean(context);
        if (UsbInstaller.takePendingSelf(context) != 0)
            context.startActivity(new Intent(context, UsbUpdateActivity.class).putExtra(UsbUpdateActivity.EXTRA_DONE, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
    }
}
