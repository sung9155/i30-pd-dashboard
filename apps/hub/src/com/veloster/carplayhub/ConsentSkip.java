package com.veloster.carplayhub;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * Optional: confirm the head unit's start-up notice ("안내 및 동의", com.hkmc.internal.consentscreen.ConsentScreen)
 * for the user. The OEM FactoryManager.hideConsentScreen() feeds the factory hard key 1 to MicomFactoryService
 * (no permission check), and the consent screen's factory listener treats key 1 as its confirm button, the same as a
 * tap. Sent a few times after boot and after the screen comes on (ignition with quick boot), because the screen may
 * draw a moment later. With no consent screen up only factory-test listeners get the key. Off by default.
 */
final class ConsentSkip {
    private static final String PREFS="consent_skip";
    private static final long[] DELAYS_MS={1000,3000,6000};   // ponytail: blind retries; tune after the car's logcat shows when the screen draws
    private ConsentSkip(){}

    static boolean enabled(Context context){return context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getBoolean("on",false);}
    static boolean set(Context context,boolean on){return context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putBoolean("on",on).commit();}

    /** After boot or screen on: confirm the notice in the background, only when the user switched this on. */
    static void onWake(final Context context){
        if(!enabled(context))return;
        new Thread(new Runnable(){public void run(){
            long waited=0;
            for(long at:DELAYS_MS){
                try{Thread.sleep(at-waited);}catch(InterruptedException e){return;}
                waited=at;
                try{
                    Object manager=Class.forName("com.hkmc.factory.FactoryManager").getConstructor(Context.class).newInstance(context);
                    manager.getClass().getMethod("hideConsentScreen").invoke(manager);
                    Log.i("VNCarPlayHub","consent skip: hideConsentScreen sent at "+at+" ms");
                }catch(Throwable e){Log.w("VNCarPlayHub","consent skip unavailable: "+e);return;}
            }
        }},"vn-consent-skip").start();
    }
}
