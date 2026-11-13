package com.veloster.carplayhub;

import com.veloster.i18n.UiText;
import com.veloster.i18n.LanguageSettings;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import java.lang.ref.WeakReference;

/** Opt-in standard Android shortcut. No OEM APIs, HOME replacement or full-screen boot launch. */
public final class QuickEntryService extends Service {
    private static final String PREFS="quick_entry",CHANNEL="vn_quick_entry",STOP="com.veloster.carplayhub.QUICK_ENTRY_OFF";
    private static final int NOTICE=9117;
    private static final float IDLE_ALPHA=0.06f;   // barely there at rest (paired with DashBadge's low-contrast idle colours); opaque with the real colours while touched and for FADE_DELAY after
    private static final long FADE_DELAY=3000;     // fade back to IDLE_ALPHA this long after the last operation (appear, tap, drag)
    private static WeakReference<Runnable> statusObserver=new WeakReference<Runnable>(null);
    private SharedPreferences prefs;
    private WindowManager windows;
    private View button;
    private WindowManager.LayoutParams layout;
    private int width,height,size;
    private boolean registered,overlayFailed;
    private String renderedLanguage;
    private final BroadcastReceiver screen=new BroadcastReceiver(){public void onReceive(Context context,Intent intent){
        if(Intent.ACTION_SCREEN_OFF.equals(intent.getAction()))removeOverlay();else refreshOverlay();
    }};
    /** The dashboard's floating window is up and asked for the edge button to step aside (it repeats every 30 s); the
     *  button comes back when it says so, or by itself when HIDE_FOR passes without a repeat (the dashboard was killed). */
    static final String HIDE_ACTION="com.veloster.carplayhub.QUICK_ENTRY_HIDE";
    private static final long HIDE_FOR=75000;
    private long hiddenUntil;
    private final android.os.Handler later=new android.os.Handler();
    private final Runnable unhide=new Runnable(){public void run(){if(SystemClockNow()>=hiddenUntil)refreshOverlay();}};
    private static long SystemClockNow(){return android.os.SystemClock.elapsedRealtime();}
    private final BroadcastReceiver floatWindow=new BroadcastReceiver(){public void onReceive(Context context,Intent intent){
        if(intent.getBooleanExtra("hide",false)){hiddenUntil=SystemClockNow()+HIDE_FOR;removeOverlay();later.removeCallbacks(unhide);later.postDelayed(unhide,HIDE_FOR+500);}
        else{hiddenUntil=0;later.removeCallbacks(unhide);refreshOverlay();}
    }};
    private boolean floatRegistered;
    private static SharedPreferences prefs(Context context){return context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);}
    public static int mode(Context context){
        try{return QuickEntryPolicy.mode(prefs(context).getInt("mode",QuickEntryPolicy.OFF));}
        catch(ClassCastException e){return QuickEntryPolicy.OFF;}
    }
    public static void observe(Runnable observer){statusObserver=new WeakReference<Runnable>(observer);}
    public static void unobserve(Runnable observer){if(statusObserver.get()==observer)statusObserver.clear();}
    public static String status(Context context){
        LanguageSettings.init(context);
        int mode=mode(context);
        String base=mode==QuickEntryPolicy.FLOATING?UiText.t("버튼 + 알림"):mode==QuickEntryPolicy.NOTIFICATION?UiText.t("알림만"):UiText.t("꺼짐");
        String error;
        try{error=prefs(context).getString("error","");}catch(ClassCastException ignored){error="";}
        return error.length()==0?base:base+"\n"+UiText.t(error);
    }
    private static void error(Context context,String message){
        prefs(context).edit().putString("error",message).commit();
        Runnable observer=statusObserver.get();if(observer!=null)observer.run();
    }
    public static boolean apply(Context context,int wanted){
        int selected=QuickEntryPolicy.mode(wanted);
        if(!prefs(context).edit().putInt("mode",selected).putString("error","").commit())return false;
        if(selected==QuickEntryPolicy.OFF){
            try{context.stopService(new Intent(context,QuickEntryService.class));return true;}
            catch(RuntimeException e){error(context,"바로가기 서비스 종료 요청에 실패했습니다.");return false;}
        }
        return restore(context);
    }
    public static boolean restore(Context context){
        if(!QuickEntryPolicy.restore(mode(context)))return true;
        Intent intent=new Intent(context,QuickEntryService.class);
        try{
            if(Build.VERSION.SDK_INT>=26)Context.class.getMethod("startForegroundService",Intent.class).invoke(context,intent);
            else if(context.startService(intent)==null)throw new IllegalStateException("Service unavailable");
            return true;
        }catch(Exception e){
            Log.e("VNQuickEntry","Shortcut service start failed",e);
            error(context,"바로가기 시작 실패. 내 앱을 다시 열어 설정을 확인하세요.");return false;
        }catch(LinkageError e){error(context,"이 시스템에서 바로가기 서비스를 시작할 수 없습니다.");return false;}
    }
    public void onCreate(){
        super.onCreate();LanguageSettings.init(this);prefs=prefs(this);windows=(WindowManager)getSystemService(WINDOW_SERVICE);
    }
    public int onStartCommand(Intent intent,int flags,int startId){
        LanguageSettings.init(this);
        if(!UiText.language().equals(renderedLanguage)){removeOverlay();renderedLanguage=UiText.language();}
        if(intent!=null&&STOP.equals(intent.getAction())){
            prefs.edit().putInt("mode",QuickEntryPolicy.OFF).commit();error(this,"");
        }
        if(!QuickEntryPolicy.restore(mode(this))){stopForeground(true);stopSelf();return START_NOT_STICKY;}
        overlayFailed=false;error(this,"");
        try{startForeground(NOTICE,notification());}
        catch(Exception e){Log.e("VNQuickEntry","Notification unavailable",e);error(this,"알림 바로가기를 만들지 못했습니다. 서비스가 중지되었습니다.");stopSelf();return START_NOT_STICKY;}
        if(!registered){
            IntentFilter filter=new IntentFilter();filter.addAction(Intent.ACTION_SCREEN_OFF);filter.addAction(Intent.ACTION_SCREEN_ON);filter.addAction(Intent.ACTION_USER_PRESENT);
            try{registerReceiver(screen,filter);registered=true;}
            catch(RuntimeException e){error(this,"화면 잠금 상태를 추적할 수 없어 알림만 표시합니다.");overlayFailed=true;}
        }
        if(!floatRegistered){try{registerReceiver(floatWindow,new IntentFilter(HIDE_ACTION));floatRegistered=true;}catch(RuntimeException e){Log.w("VNQuickEntry","Floating window signal unavailable",e);}}
        refreshOverlay();return START_STICKY;
    }
    private Intent hubIntent(){return new Intent(this,HubActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);}
    static Intent dashboardIntent(){return new Intent(Intent.ACTION_MAIN).setClassName("com.veloster.dashboard","com.veloster.dashboard.DashboardActivity")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);}
    private Notification notification()throws Exception{
        NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if(manager==null)throw new IllegalStateException("Notification service unavailable");
        if(Build.VERSION.SDK_INT>=26){
            Class<?> type=Class.forName("android.app.NotificationChannel");
            Object channel=type.getConstructor(String.class,CharSequence.class,Integer.TYPE).newInstance(CHANNEL,"My Apps shortcut",Integer.valueOf(2));
            NotificationManager.class.getMethod("createNotificationChannel",type).invoke(manager,channel);
        }
        int flags=PendingIntent.FLAG_UPDATE_CURRENT|(Build.VERSION.SDK_INT>=23?0x04000000:0);
        PendingIntent open=PendingIntent.getActivity(this,0,hubIntent(),flags);
        PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,QuickEntryService.class).setAction(STOP),flags);
        Notification.Builder builder=new Notification.Builder(this).setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle(UiText.t("내 앱 / My Apps")).setContentText(UiText.t("눌러서 계기판과 앱 목록 열기"))
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel,UiText.t("바로가기 끄기"),stop);
        if(Build.VERSION.SDK_INT>=26)Notification.Builder.class.getMethod("setChannelId",String.class).invoke(builder,CHANNEL);
        return builder.build();
    }
    private boolean unlockedScreen(){
        try{
            PowerManager power=(PowerManager)getSystemService(POWER_SERVICE);
            KeyguardManager keyguard=(KeyguardManager)getSystemService(KEYGUARD_SERVICE);
            return power!=null&&keyguard!=null&&QuickEntryPolicy.visible(mode(this),power.isScreenOn(),keyguard.isKeyguardLocked());
        }catch(RuntimeException e){return false;}
    }
    private float storedY(){try{return QuickEntryPolicy.fraction(prefs.getFloat("relative_y",0.5f));}catch(ClassCastException e){return 0.5f;}}
    private boolean storedRight(){try{return prefs.getBoolean("right",true);}catch(ClassCastException e){return true;}}
    private void dimensions(){
        DisplayMetrics metrics=new DisplayMetrics();windows.getDefaultDisplay().getMetrics(metrics);
        width=Math.max(1,metrics.widthPixels);height=Math.max(1,metrics.heightPixels);
        size=Math.max(1,Math.min(Math.min(width,height),Math.round(48*metrics.density)));
    }
    private void refreshOverlay(){
        if(overlayFailed||!unlockedScreen()||SystemClockNow()<hiddenUntil){removeOverlay();return;}
        if(button!=null)return;
        try{
            if(windows==null)throw new IllegalStateException("Window manager unavailable");
            dimensions();
            layout=new WindowManager.LayoutParams(size,size,Build.VERSION.SDK_INT>=26?2038:WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT);
            layout.gravity=Gravity.TOP|Gravity.LEFT;layout.x=QuickEntryPolicy.edge(storedRight(),width,size);layout.y=QuickEntryPolicy.y(storedY(),height,size);
            final View created=new View(this){   // a small speedometer face (DashBadge), not a text button
                protected void onDraw(Canvas canvas){DashBadge.draw(canvas,Math.min(getWidth(),getHeight()),isPressed());}
            };
            created.setAlpha(1f);   // appears opaque, then fades to IDLE_ALPHA a few seconds after the last touch (below)
            final Runnable fade=new Runnable(){public void run(){created.setAlpha(IDLE_ALPHA);}};
            created.setContentDescription(UiText.t("계기판 열기 · 길게 눌러 내 앱 · 끌어서 위치 변경 / Dashboard"));
            created.setOnClickListener(new View.OnClickListener(){public void onClick(View view){
                if(!unlockedScreen())return;
                // i30 PD: the edge button opens the dashboard itself; My Apps stays in the notification (and is the fallback).
                try{startActivity(dashboardIntent());}catch(RuntimeException e){
                    try{startActivity(hubIntent());}catch(RuntimeException again){error(QuickEntryService.this,"앱 화면을 열지 못했습니다. 알림에서 다시 시도하세요.");}
                }
            }});
            created.setOnTouchListener(new View.OnTouchListener(){
                float downX,downY;int initialX,initialY;boolean dragged,longPressed;
                final int slop=ViewConfiguration.get(QuickEntryService.this).getScaledTouchSlop();
                // Long press opens My Apps (the hub); a short tap opens the dashboard; a drag repositions.
                final Runnable openHub=new Runnable(){public void run(){
                    longPressed=true;
                    if(!unlockedScreen())return;
                    try{startActivity(hubIntent());}catch(RuntimeException e){error(QuickEntryService.this,"앱 화면을 열지 못했습니다. 알림에서 다시 시도하세요.");}
                }};
                public boolean onTouch(View view,MotionEvent event){
                    if(layout==null||button!=created)return false;
                    int action=event.getActionMasked();
                    if(action==MotionEvent.ACTION_DOWN){
                        downX=event.getRawX();downY=event.getRawY();initialX=layout.x;initialY=layout.y;dragged=false;longPressed=false;
                        created.removeCallbacks(fade);created.setAlpha(1f);created.setPressed(true);created.invalidate();created.postDelayed(openHub,500);return true;
                    }
                    if(action==MotionEvent.ACTION_MOVE){
                        float dx=event.getRawX()-downX,dy=event.getRawY()-downY;
                        if(dx*dx+dy*dy>slop*slop){dragged=true;created.removeCallbacks(openHub);}
                        if(dragged){layout.x=QuickEntryPolicy.bound(initialX+Math.round(dx),width,size);layout.y=QuickEntryPolicy.bound(initialY+Math.round(dy),height,size);updateOverlay();}
                        return true;
                    }
                    if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){
                        created.removeCallbacks(openHub);created.setPressed(false);created.invalidate();
                        if(dragged){
                            boolean right=QuickEntryPolicy.right(layout.x,width,size);
                            layout.x=QuickEntryPolicy.edge(right,width,size);
                            prefs.edit().putBoolean("right",right).putFloat("relative_y",QuickEntryPolicy.relativeY(layout.y,height,size)).commit();updateOverlay();
                        }else if(action==MotionEvent.ACTION_UP&&!longPressed)created.performClick();
                        created.postDelayed(fade,FADE_DELAY);   // stay opaque, then fade a few seconds after the last touch
                        return true;
                    }
                    return true;
                }
            });
            windows.addView(created,layout);button=created;
            created.postDelayed(fade,FADE_DELAY);   // opaque on appear, then fade so it stops distracting
        }catch(RuntimeException e){
            Log.w("VNQuickEntry","Overlay unavailable; retaining notification",e);removeOverlay();overlayFailed=true;
            error(this,"가장자리 버튼을 표시할 수 없어 알림 바로가기를 유지합니다. 시스템의 다른 앱 위 표시 권한을 확인하세요.");
        }
    }
    private void updateOverlay(){
        try{if(button!=null)windows.updateViewLayout(button,layout);}
        catch(RuntimeException e){removeOverlay();overlayFailed=true;error(this,"버튼 위치를 갱신하지 못해 알림만 유지합니다.");}
    }
    private void removeOverlay(){
        View old=button;button=null;
        if(old!=null&&windows!=null)try{windows.removeView(old);}catch(RuntimeException ignored){}
    }
    public void onConfigurationChanged(Configuration configuration){super.onConfigurationChanged(configuration);removeOverlay();onStartCommand(null,0,0);}
    public void onDestroy(){
        removeOverlay();if(registered){try{unregisterReceiver(screen);}catch(RuntimeException ignored){}registered=false;}
        later.removeCallbacks(unhide);if(floatRegistered){try{unregisterReceiver(floatWindow);}catch(RuntimeException ignored){}floatRegistered=false;}
        stopForeground(true);super.onDestroy();
    }
    public IBinder onBind(Intent intent){return null;}
}
