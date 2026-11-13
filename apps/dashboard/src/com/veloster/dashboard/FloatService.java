package com.veloster.dashboard;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.IBinder;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import com.veloster.vehicle.FloatPanel;
import com.veloster.vehicle.VehicleDash;

/**
 * 플로팅 창: while the dashboard is not on screen (any other app: navi, home, media), keeps reading the car (its own
 * VehicleDataClient, like the activity's) and shows FloatPanel over everything. Started by DashboardActivity.onPause and
 * at boot when 설정 → 플로팅 → 플로팅 창 is on; stopped when the dashboard comes back. Redraws 4 times a second. A tap opens
 * the dashboard, a long press 내 앱, a drag moves it (remembered per shape). Steps aside while the rear camera shows. With
 * 플로팅 버튼 숨기기 on it asks 내 앱 to hide its edge button, again every 30 s while up (내 앱 brings the button back on
 * its own when that stops); on the way out it asks for the button back.
 */
public final class FloatService extends Service implements Runnable {
    static final String HUB="com.veloster.carplayhub",HIDE_ACTION="com.veloster.carplayhub.QUICK_ENTRY_HIDE";
    private static final long FRAME_MS=250,HIDE_REPEAT_MS=30000;
    private final Handler handler=new Handler();
    private VehicleDataClient client;
    private WindowManager windows;
    private View panel;
    private WindowManager.LayoutParams layout;
    private int shown=FloatPanel.OFF;
    private long hideSentAt;private boolean hideSent;

    /** Starts the window if it is switched on (the dashboard just left the screen, or the head unit booted). */
    static void startIfOn(Context c){
        VehicleDash.loadFloat(c.getSharedPreferences("pit_vehicle_ui",0));
        if(FloatPanel.style!=FloatPanel.OFF)try{c.startService(new Intent(c,FloatService.class));}catch(RuntimeException e){android.util.Log.w("VehicleDash","Floating window not started",e);}
    }
    static void stop(Context c){try{c.stopService(new Intent(c,FloatService.class));}catch(RuntimeException ignored){/* not running */}}

    public IBinder onBind(Intent intent){return null;}
    public void onCreate(){
        super.onCreate();
        windows=(WindowManager)getSystemService(Context.WINDOW_SERVICE);
        client=new VehicleDataClient(this);client.start();
        handler.post(this);
    }
    public int onStartCommand(Intent intent,int flags,int startId){
        VehicleDash.loadFloat(prefs());
        if(FloatPanel.style==FloatPanel.OFF){stopSelf();return START_NOT_STICKY;}
        return START_STICKY;
    }
    private SharedPreferences prefs(){return getSharedPreferences("pit_vehicle_ui",0);}

    public void run(){
        int want=VehicleDash.rearCamera()?FloatPanel.OFF:FloatPanel.style;
        if(want!=shown){remove();if(want!=FloatPanel.OFF)add(want);}
        if(panel!=null){panel.invalidate();
            long now=android.os.SystemClock.elapsedRealtime();
            if(FloatPanel.hideButton&&(!hideSent||now-hideSentAt>=HIDE_REPEAT_MS)){hide(true);hideSentAt=now;}
            else if(!FloatPanel.hideButton&&hideSent)hide(false);}
        handler.postDelayed(this,FRAME_MS);
    }
    private void add(final int style){
        final int w=FloatPanel.width(style),h=FloatPanel.height(style);
        layout=new WindowManager.LayoutParams(w,h,WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,PixelFormat.TRANSLUCENT);
        layout.gravity=Gravity.TOP|Gravity.LEFT;
        layout.x=prefs().getInt("float_x_"+style,FloatPanel.defaultX(style));layout.y=prefs().getInt("float_y_"+style,FloatPanel.defaultY(style));
        final View v=new View(this){protected void onDraw(Canvas c){FloatPanel.draw(c,style,VehicleDash.snapshotNow(),VehicleDash.diesel());}};
        v.setOnTouchListener(new View.OnTouchListener(){
            float downX,downY;int startX,startY;boolean moved,held;
            final Runnable longPress=new Runnable(){public void run(){held=true;open(HUB,"com.veloster.carplayhub.HubActivity");}};
            public boolean onTouch(View view,MotionEvent e){
                switch(e.getAction()){
                    case MotionEvent.ACTION_DOWN:downX=e.getRawX();downY=e.getRawY();startX=layout.x;startY=layout.y;moved=false;held=false;handler.postDelayed(longPress,700);return true;
                    case MotionEvent.ACTION_MOVE:{float dx=e.getRawX()-downX,dy=e.getRawY()-downY;
                        if(!moved&&Math.abs(dx)+Math.abs(dy)<12)return true;
                        moved=true;handler.removeCallbacks(longPress);
                        layout.x=Math.max(0,Math.min(800-w,startX+(int)dx));layout.y=Math.max(0,Math.min(480-h,startY+(int)dy));
                        try{windows.updateViewLayout(view,layout);}catch(RuntimeException ignored){/* removed meanwhile */}return true;}
                    case MotionEvent.ACTION_UP:handler.removeCallbacks(longPress);
                        if(moved)prefs().edit().putInt("float_x_"+style,layout.x).putInt("float_y_"+style,layout.y).apply();
                        else if(!held)open(getPackageName(),"com.veloster.dashboard.DashboardActivity");
                        return true;
                    case MotionEvent.ACTION_CANCEL:handler.removeCallbacks(longPress);return true;
                }
                return false;
            }
        });
        try{windows.addView(v,layout);panel=v;shown=style;}
        catch(RuntimeException e){android.util.Log.w("VehicleDash","Floating window refused",e);panel=null;shown=FloatPanel.OFF;stopSelf();}
    }
    private void remove(){
        if(panel!=null){try{windows.removeView(panel);}catch(RuntimeException ignored){/* already gone */}panel=null;}
        shown=FloatPanel.OFF;
        if(hideSent)hide(false);
    }
    private void open(String pkg,String cls){
        try{startActivity(new Intent(Intent.ACTION_MAIN).setComponent(new ComponentName(pkg,cls)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP));}
        catch(RuntimeException e){android.util.Log.w("VehicleDash","Could not open "+cls,e);}
    }
    /** Asks 내 앱 to hide (or bring back) its edge button. Only 내 앱 receives it. */
    private void hide(boolean on){
        hideSent=on;
        try{sendBroadcast(new Intent(HIDE_ACTION).setPackage(HUB).putExtra("hide",on));}catch(RuntimeException ignored){/* 내 앱 not installed */}
    }
    public void onDestroy(){
        handler.removeCallbacks(this);remove();
        if(client!=null){client.stop();client=null;}
        super.onDestroy();
    }
}
