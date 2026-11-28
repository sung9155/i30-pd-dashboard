package com.veloster.carplayhub;

import com.veloster.i18n.UiText;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import android.util.Log;

/**
 * MODE 버튼 → 계기판 (a switch in My Apps, default off): the steering wheel's MODE button goes between the dashboard and
 * one app the owner picks (phone projection), as ☆ does with its toggle. Away from the dashboard MODE opens the
 * dashboard; on the dashboard it opens that app, the way its tile in My Apps does.
 *
 * How (car, 2026-11-28, the unit's own log): with the stock MODE list empty a MODE press, short or held, makes the mode
 * service send com.hkmc.intent.action.MODE_SHORTCUT_LONG as an ordered broadcast, and Settings opens the MODE button
 * setting on it. We receive it first (highest priority) and end it there, so Settings stays shut, then open the
 * dashboard or the app. Nothing on the unit is changed. With sources in the stock list a short press never leaves the
 * mode service (it steps the source) and there is nothing for us to receive: the list has to be empty, which the switch
 * checks when it is turned on and shows in its status after.
 *
 * MODE 길게 → 내 앱 (a second switch, default off): a held MODE opens My Apps. A held press sends the same broadcast
 * (about 0.75 s into the press, before the release) with another EXTRA_RUNNING_STATE: QuickEntryPolicy.modeHeld.
 *
 * Two receivers for the one broadcast: this class from the manifest (My Apps put away) and one registered while My
 * Apps runs (the one seen in the car; whether the manifest one is served was not seen). The first to get it ends the
 * broadcast; the press's time stamp keeps a second delivery from acting again. The dashboard's on screen / left signal
 * (QuickEntryService.HIDE_ACTION, from=dashboard, again every 20 s) comes through StarKey.
 */
public final class ModeKey extends BroadcastReceiver {
    static final String ACTION="com.hkmc.intent.action.MODE_SHORTCUT_LONG";
    private static final String TAG="VNCarPlayHub";
    private static BroadcastReceiver registered;
    private static long shownUntil,lastStamp=-1,lastAt=-10000;   // main thread only
    public void onReceive(Context context,Intent intent){
        if(intent==null||!ACTION.equals(intent.getAction()))return;
        handle(this,context,intent,"long(m)");
        start(context);
    }
    private static SharedPreferences prefs(Context context){return context.getSharedPreferences("mode_key",Context.MODE_PRIVATE);}
    static boolean on(Context context){return prefs(context).getBoolean("on",false);}
    /** MODE 길게 → 내 앱 (a second switch, default off, only with the first on): a held MODE opens My Apps instead. */
    static boolean hold(Context context){return prefs(context).getBoolean("hold",false);}
    static void hold(Context context,boolean on){prefs(context).edit().putBoolean("hold",on).commit();}
    /** The app MODE opens from the dashboard (a package name). */
    static String app(Context context){return prefs(context).getString("app","");}
    static void set(Context context,boolean on,String app){
        prefs(context).edit().putBoolean("on",on).putString("app",app==null?"":app).commit();
        start(context);
    }
    /** The stock MODE list (ModeManager.getModeKeySetting), or null when it cannot be read. Read only. */
    static int[] list(Context context){
        try{
            Object manager=Class.forName("com.hkmc.mode.ModeManager").getConstructor(Context.class).newInstance(context.getApplicationContext());
            return (int[])manager.getClass().getMethod("getModeKeySetting").invoke(manager);
        }catch(Throwable e){Log.w(TAG,"mode key: stock list not read: "+e);return null;}
    }
    static String label(Context context,String name){
        try{PackageManager pm=context.getPackageManager();return pm.getApplicationLabel(pm.getApplicationInfo(name,0)).toString();}
        catch(Exception e){return name;}
    }
    /** For the switch's row: off, what MODE goes between, or why it does not work now. list: list(context) read by the caller. */
    static String status(Context context,boolean on,int[] list){
        if(!on)return UiText.t("꺼짐");
        if(QuickEntryPolicy.modeListBlocks(list))return UiText.t("순정 MODE 목록에 소스가 있어 동작하지 않습니다");
        return UiText.t("계기판")+" ↔ "+label(context,app(context))+(list==null?" ("+UiText.t("확인 불가")+")":"");
    }
    /** Registers the running receiver once per process while the switch is on: from My Apps, the 바로가기 버튼 service
     *  (which keeps the process alive), boot and the dashboard's signal. */
    static void start(Context context){
        if(registered!=null||!on(context))return;
        try{
            BroadcastReceiver r=new BroadcastReceiver(){public void onReceive(Context c,Intent intent){handle(this,c,intent,"long(d)");}};
            IntentFilter filter=new IntentFilter(ACTION);filter.setPriority(Integer.MAX_VALUE);
            context.getApplicationContext().registerReceiver(r,filter);registered=r;
        }catch(RuntimeException e){Log.w(TAG,"mode key: shortcut broadcast not watched",e);}
    }
    /** The dashboard came on screen (shown, said again every 20 s) or left it; false too when My Apps itself is in front. */
    static void dashboard(boolean shown){shownUntil=shown?SystemClock.elapsedRealtime()+QuickEntryPolicy.MODE_SHOWN_FOR:0;}
    /** One delivery of the stock broadcast. With the switch off it is left alone and goes on to Settings. With it on it is
     *  ended here (only an ordered broadcast can be) and the press is acted on once; what was done goes to logcat (tag
     *  VNCarPlayHub, in the installers' [진단 기록]): path "long(m)" the manifest receiver, "long(d)" the running one. */
    private static void handle(BroadcastReceiver receiver,Context context,Intent intent,String path){
        if(!on(context))return;
        boolean ordered=receiver.isOrderedBroadcast();
        if(ordered)receiver.abortBroadcast();
        long now=SystemClock.elapsedRealtime(),stamp=number(intent,"com.hkmc.mode.TIMESTAMP");
        int state=(int)number(intent,"com.hkmc.mode.EXTRA_RUNNING_STATE");
        boolean held=QuickEntryPolicy.modeHeld(state);
        String did;
        if(QuickEntryPolicy.modeSamePress(stamp,lastStamp,now,lastAt))did="same press";
        else{lastStamp=stamp;lastAt=now;did=open(context,QuickEntryPolicy.modeOpens(held,hold(context),QuickEntryPolicy.modeOnDashboard(now,shownUntil)));}
        Log.i(TAG,"mode key: "+path+" ordered="+ordered+" "+(held?"held":"short")+" (state "+state+") -> "+did);
    }
    /** A number among the broadcast's extras whatever type the service put it in; -1 when it is not there. */
    private static long number(Intent intent,String name){
        Object value=intent.getExtras()==null?null:intent.getExtras().get(name);
        return value instanceof Number?((Number)value).longValue():-1;
    }
    /** The picked app, as its tile in My Apps opens it; the dashboard, as ☆ opens it; My Apps, as the edge button's hold does. */
    private static String open(Context context,int what){
        try{
            if(what==QuickEntryPolicy.MODE_HUB){
                context.startActivity(new Intent(context,HubActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));
                return "my apps";
            }
            if(what==QuickEntryPolicy.MODE_DASHBOARD){context.startActivity(QuickEntryService.dashboardIntent());return "dashboard";}
            String name=app(context);
            Intent launch=name.length()==0?null:context.getPackageManager().getLaunchIntentForPackage(name);
            if(launch==null)return "no app to open: "+name;
            context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return "app "+name;
        }catch(RuntimeException e){Log.w(TAG,"mode key: not opened",e);return "not opened: "+e.getClass().getSimpleName();}
    }
}
