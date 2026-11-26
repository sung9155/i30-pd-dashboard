package com.veloster.carplayhub;

import com.veloster.i18n.UiText;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * ☆ (user button) short press → dashboard. The OEM ModeManager keeps the button's action (persist.sys.key.custom) and,
 * for an action outside its own list, sends it as an ordered broadcast with no permission; this receiver catches ours.
 * Opening the stock button settings (long press) resets an unlisted action to "none", so status reads the live value
 * and nothing turns it back on behind the user's back. Only the visible switch in My Apps writes it.
 *
 * Toggle (계기판에서는 ☆ 원래 기능, a switch in My Apps, default off): while the dashboard is on screen ☆ is the stock
 * function it had before us, and ours again when the dashboard leaves, so one button opens the dashboard and then does
 * its own job there. The dashboard already tells My Apps when it is on screen (QuickEntryService.HIDE_ACTION with
 * from=dashboard: on, again every 20 s, and off when it leaves); this receiver takes that too, so it works without the
 * edge button's service. What we changed is marked ("lent") and only that is ever changed back.
 */
public final class StarKey extends BroadcastReceiver {
    public void onReceive(Context context,Intent intent){
        String action=intent==null?null:intent.getAction();
        if(QuickEntryPolicy.STAR_ACTION.equals(action)){
            try{context.startActivity(QuickEntryService.dashboardIntent());}catch(RuntimeException e){Log.w("VNCarPlayHub","star key: dashboard not opened",e);}
        }else if(QuickEntryService.HIDE_ACTION.equals(action)&&"dashboard".equals(intent.getStringExtra("from")))
            dashboard(context,intent.getBooleanExtra("hide",false));
    }
    private static SharedPreferences prefs(Context context){return context.getSharedPreferences("star_key",Context.MODE_PRIVATE);}
    static boolean toggle(Context context){return prefs(context).getBoolean("toggle",false);}
    static void toggle(Context context,boolean on){prefs(context).edit().putBoolean("toggle",on).commit();}
    /** ☆ had a stock function before us: without one the toggle has nothing to hand back. */
    static boolean lendable(Context context){return QuickEntryPolicy.starLendable(prefs(context).getString("previous",null));}
    /** The dashboard came on screen (shown) or left it. Also called with false when My Apps itself is in front and after
     *  boot: the dashboard is not on screen then, so a ☆ left lent (the dashboard was killed while showing) comes back. */
    static void dashboard(Context context,boolean shown){
        try{
            SharedPreferences prefs=prefs(context);
            boolean on=prefs.getBoolean("toggle",false),lent=prefs.getBoolean("lent",false);
            if(!lent&&!(shown&&on))return;   // nothing of ours to move: the mode service is not asked every 20 s
            String next=QuickEntryPolicy.starToggle(on,shown,get(context),prefs.getString("previous",null),lent);
            boolean moved=next!=null&&put(context,next);
            boolean after=shown?lent||moved:next!=null&&!moved;   // gone: the mark is cleared, except after a hand-back that failed (tried again next time)
            if(after!=lent)prefs.edit().putBoolean("lent",after).commit();
        }catch(Exception e){Log.w("VNCarPlayHub","star key: toggle not applied",e);}
    }
    static String status(Context context){
        try{return QuickEntryPolicy.STAR_ACTION.equals(get(context))?UiText.t("켜짐 (짧게 누르면 계기판)"):UiText.t("꺼짐");}
        catch(Exception e){return UiText.t("확인 불가");}
    }
    /** On: remember the current action, then point ☆ at us. Off: put the remembered action back, only if ☆ is still ours. */
    static boolean set(Context context,boolean on)throws Exception{
        SharedPreferences prefs=prefs(context);
        String now=get(context);
        prefs.edit().putBoolean("lent",false).commit();   // the switch decides from here: nothing is changed back later on an old mark
        if(on){
            if(!QuickEntryPolicy.STAR_ACTION.equals(now))prefs.edit().putString("previous",now).commit();
            return put(context,QuickEntryPolicy.STAR_ACTION);
        }
        return !QuickEntryPolicy.STAR_ACTION.equals(now)||put(context,QuickEntryPolicy.starRestore(prefs.getString("previous",null)));
    }
    private static Object manager(Context context)throws Exception{   // the OEM keeps the first Context in a singleton
        return Class.forName("com.hkmc.mode.ModeManager").getConstructor(Context.class).newInstance(context.getApplicationContext());
    }
    private static String get(Context context)throws Exception{
        Object manager=manager(context);return (String)manager.getClass().getMethod("getCustomKey").invoke(manager);
    }
    private static boolean put(Context context,String action)throws Exception{
        Object manager=manager(context);
        return Boolean.TRUE.equals(manager.getClass().getMethod("setCustomKey",String.class).invoke(manager,action))&&action.equals(get(context));
    }
}
