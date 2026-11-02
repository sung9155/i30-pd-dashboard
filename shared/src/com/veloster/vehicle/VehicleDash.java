package com.veloster.vehicle;

import com.veloster.i18n.UiText;
import com.veloster.i18n.LanguageSettings;
import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;
import java.lang.ref.WeakReference;

/** Opt-in in-activity panel; keeps the original content and all OEM controls alive. */
public final class VehicleDash extends View implements Runnable {
    private static final Telemetry DATA=new Telemetry();
    private static WeakReference<VehicleDash> current=new WeakReference<VehicleDash>(null);
    private final Activity activity;
    private final PitRenderer renderer=new PitRenderer();
    private final MediaState mediaState=new MediaState();
    private final MediaReceiver mediaReceiver=new MediaReceiver(mediaState);
    private final ArtworkLoader artwork;
    private final GaugeLayout gaugeLayout=new GaugeLayout();
    private final GaugeEditor gaugeEditor;
    private final GraphLayout graphLayout=new GraphLayout();
    private final GraphEditor graphEditor;
    private final OverviewLayout overviewLayout=new OverviewLayout();
    private int overviewDown=-1;
    private boolean cluster, media, gauges, graphs, symmetric, touching;
    /** Symmetric-tab variant (PitRenderer.PAGE_*). Lap timer on the performance page: tap starts, tap stops, tap again resets. */
    private int page;
    /** Screen showing (ClusterPicker index); saved, so the dashboard reopens on it. */
    private int screen;
    /** Screens the driver hid (ClusterPicker bit mask); saved, so it survives a reboot. */
    private int hidden;
    /** Screen list open (long press on the Cluster tab), and its edit mode where a tile tap shows or hides it. */
    private boolean picker,editing;
    /** Options: accent colour follows the drive mode; SPORT/ECO open their screen (ClusterPicker.onMode). */
    private boolean modeColor,modeScreen;
    private int lastMode=-1,modeBack=-1;
    /** Settings category open on the left of the gear's settings (SettingsLayout). */
    private int settingsCat=SettingsLayout.SCREENS;
    /** Screen per ClusterPicker.MODES slot (-1 = 원래 화면), and the slot being chosen in settings (-1 none). */
    private final int[] modeTargets=ClusterPicker.MODE_DEFAULT.clone();
    private int pickSlot=-1;
    private final HeaderTabs header=renderer.header();
    private boolean lapRunning;
    private long lapStart, lapMs;
    private long introStart;
    private float downX,downY;
    private long lastDiagnostic;
    /** Frame cost since the last diagnostics line (logcat VehicleDash every 5 s), to judge the heavier layouts on the car. */
    private long drawNs,drawMaxNs;
    private int drawFrames;
    private String drawStats(){
        String s=drawFrames==0?"":String.format(java.util.Locale.US," draw avg %.1f max %.1f ms over %d frames (page %d%s)",drawNs/1e6/drawFrames,drawMaxNs/1e6,drawFrames,page,symmetric?"":", tab");
        drawNs=drawMaxNs=0;drawFrames=0;return s;
    }
    private long lastPolicyCheck;
    private boolean resumed;
    private long entryRequestUntil;
    private int shiftTarget;
    private final java.util.concurrent.atomic.AtomicBoolean mediaPending=new java.util.concurrent.atomic.AtomicBoolean();
    private Button entry;
    private boolean blocked;
    private boolean standalone;
    private final Runnable editHold=new Runnable(){public void run(){
        if((gauges||graphs)&&touching&&!blocked&&!picker&&getVisibility()==VISIBLE){
            int slot=graphs?graphLayout.hit(downX,downY):gaugeEditor.hit(downX,downY);
            if(slot>=0){touching=false;if(graphs)graphEditor.edit(slot);else gaugeEditor.edit(slot);}
        }
    }};
    private final Runnable clusterHold=new Runnable(){public void run(){
        if(touching&&!blocked&&getVisibility()==VISIBLE&&header.onStrip(downX,downY)){
            touching=false;picker=true;editing=false;showCluster();invalidate();
        }
    }};
    /** Set the view flags for the current screen (the next shown one if it is hidden). */
    private void applyScreen(){
        screen=ClusterPicker.shown(screen,hidden);
        gauges=screen==ClusterPicker.GAUGES;graphs=screen==ClusterPicker.GRAPHS;cluster=screen==ClusterPicker.F1;
        symmetric=screen>ClusterPicker.F1;page=symmetric?ClusterPicker.page(screen):0;
    }
    private void showCluster(){
        applyScreen();
        activity.getSharedPreferences("pit_vehicle_ui",0).edit().putInt("cluster_screen",screen).putInt("cluster_hidden",hidden).apply();
    }
    private VehicleDash(Activity a) { super(a); activity=a; artwork=new ArtworkLoader(a,this);
        gaugeEditor=new GaugeEditor(a,gaugeLayout,new Runnable(){public void run(){invalidate();}});
        graphEditor=new GraphEditor(a,graphLayout,new Runnable(){public void run(){invalidate();}});
        for(int i=0;i<Telemetry.LABELS.length;i++)try{
            overviewLayout.style(i,a.getSharedPreferences("pit_vehicle_ui",0).getInt("overview_style_"+i,2));
        }catch(IllegalArgumentException e){/* Retain default for an invalid saved style. */}
        catch(ClassCastException e){/* Retain default for incompatible preferences. */}
        // Kept as saved (0 = never set); the renderer and the -/+ buttons resolve it with the detected powertrain.
        shiftTarget=a.getSharedPreferences("pit_vehicle_ui",0).getInt("shift_target",0);
        try{Theme.select(a.getSharedPreferences("pit_vehicle_ui",0).getString("theme","n"));}
        catch(ClassCastException e){Theme.select("n");/* Incompatible saved value: open in N. */}
        try{CarPaint.select(a.getSharedPreferences("pit_vehicle_ui",0).getString("car_paint","blue"));}
        catch(ClassCastException e){CarPaint.select("blue");}
        try{SharedPreferences p=a.getSharedPreferences("pit_vehicle_ui",0);modeColor=p.getBoolean("mode_color",false);modeScreen=p.getBoolean("mode_screen",false);
            for(int k=0;k<modeTargets.length;k++)modeTargets[k]=ClusterPicker.validTarget(p.getInt("mode_screen_"+ClusterPicker.MODE_NAMES[k].toLowerCase(java.util.Locale.US),ClusterPicker.MODE_DEFAULT[k]));}
        catch(ClassCastException e){/* Both options off for an incompatible saved value. */}
        try{
            SharedPreferences p=a.getSharedPreferences("pit_vehicle_ui",0);
            int v=p.getInt("cluster_screen",ClusterPicker.OVERVIEW);
            screen=v>=0&&v<ClusterPicker.COUNT?v:ClusterPicker.OVERVIEW;
            hidden=ClusterPicker.valid(p.getInt("cluster_hidden",0));
        }catch(ClassCastException e){/* Open on Overview, everything shown, for an incompatible saved value. */}
        applyScreen();
        setVisibility(GONE); }
    public static void attach(Activity a) {
        try {
            FrameLayout parent=(FrameLayout)a.findViewById(android.R.id.content);
            VehicleDash old=current.get();
            if(old!=null && old.activity==a) return;
            final VehicleDash dash=new VehicleDash(a);
            parent.addView(dash,new FrameLayout.LayoutParams(-1,-1));
            Button button=new Button(a); dash.entry=button; button.setText(UiText.t("N 계기판"));
            FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(-2,48,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);
            parent.addView(button,lp);
            button.setOnClickListener(new OnClickListener() { public void onClick(View v) {
                if(!dash.blocked) dash.showDashboard();
            }});
            current=new WeakReference<VehicleDash>(dash);
            android.util.Log.i("VehicleDash","attached N/PIT vehicle panel");
        } catch(RuntimeException e) { android.util.Log.e("VehicleDash","attach failed",e); }
    }
    /** Independent APK entry: no OEM view or disappearing entry button is involved. */
    public static void attachStandalone(Activity a){
        FrameLayout parent=(FrameLayout)a.findViewById(android.R.id.content);
        VehicleDash dash=new VehicleDash(a);dash.standalone=true;dash.blocked=false;
        current=new WeakReference<VehicleDash>(dash);
        parent.addView(dash,new FrameLayout.LayoutParams(-1,-1));dash.setVisibility(VISIBLE);
    }
    public static void resumeStandalone(Activity a){
        VehicleDash d=current.get();if(d==null||d.activity!=a)return;
        d.resumed=true;d.artwork.open();d.mediaReceiver.start(a);
        d.policy(false,"standalone-resume");d.setVisibility(VISIBLE);
    }
    public static void pauseStandalone(Activity a){
        VehicleDash d=current.get();if(d==null||d.activity!=a)return;
        d.resumed=false;d.touching=false;d.removeCallbacks(d.editHold);
        d.gaugeEditor.dismiss();d.graphEditor.dismiss();d.dismissList();d.mediaReceiver.stop();d.artwork.close();
    }
    /** Close the screen list and anything it opened (language chooser). */
    private void dismissList(){picker=false;editing=false;}
    private void showDashboard(){setVisibility(VISIBLE);if(entry!=null)entry.setVisibility(GONE);bringToFront();}
    private void close() { entryRequestUntil=0;removeCallbacks(editHold);touching=false;gaugeEditor.dismiss();graphEditor.dismiss();dismissList();setVisibility(GONE); if(entry!=null) entry.setVisibility(blocked?GONE:VISIBLE); }
    public static void consumeEntryIntent(Activity a){entryIntent(a,a.getIntent());}
    /** Explicit opt-in only; consume once and require a live unrestricted OEM response. */
    public static void entryIntent(Activity a,android.content.Intent intent){
        if(intent==null)return;
        final String key="com.veloster.vehicle.OPEN_DASHBOARD";
        try{
            if(!intent.getBooleanExtra(key,false))return;
            intent.removeExtra(key);
            VehicleDash d=current.get();
            if(d==null||d.activity!=a)return;
            d.entryRequestUntil=SystemClock.elapsedRealtime()+3000;
            d.tryRequestedEntry();
        }catch(RuntimeException e){android.util.Log.e("VehicleDash","invalid dashboard entry request",e);}
    }
    private void tryRequestedEntry(){
        if(entryRequestUntil==0||!resumed)return;
        int state=RegulationState.read(activity);
        if(state==1){entryRequestUntil=0;policy(true,"entry-service");Toast.makeText(activity,UiText.t("현재 차량 표시 제한으로 N 계기판을 열 수 없습니다."),Toast.LENGTH_SHORT).show();return;}
        if(SystemClock.elapsedRealtime()>=entryRequestUntil){entryRequestUntil=0;Toast.makeText(activity,UiText.t("차량 표시 상태를 확인하지 못했습니다. N 계기판 버튼으로 다시 열어주세요."),Toast.LENGTH_SHORT).show();return;}
        if(state==0){entryRequestUntil=0;policy(false,"entry-service");showDashboard();}
    }
    public static void hide(Activity a) {
        VehicleDash d=current.get(); if(d!=null && d.activity==a) d.close();
    }
    public static void resume(Activity a,boolean cachedRestriction){
        attach(a);VehicleDash d=current.get();
        if(d!=null&&d.activity==a){d.resumed=true;d.lastPolicyCheck=SystemClock.elapsedRealtime();regulation(a,cachedRestriction);d.tryRequestedEntry();android.util.Log.i("VehicleDash","entry resume cachedRestriction="+cachedRestriction);}
    }
    public static void pause(Activity a){VehicleDash d=current.get();if(d!=null&&d.activity==a){d.resumed=false;d.close();}}
    private void policy(boolean restricted,String source){
        // The independent instrument display is not gated by the OEM parking-brake UI policy.
        if(standalone){blocked=false;setVisibility(VISIBLE);invalidate();return;}
        if(blocked!=restricted)android.util.Log.i("VehicleDash","OEM regulation="+restricted+" source="+source);
        blocked=restricted;
        if(blocked)close();
        else if(entry!=null){entry.setVisibility(getVisibility()==VISIBLE?GONE:VISIBLE);if(getVisibility()!=VISIBLE)entry.bringToFront();}
    }
    public static void regulation(final Activity a,final boolean blocked) {
        a.runOnUiThread(new Runnable(){public void run(){VehicleDash d=current.get();if(d==null||d.activity!=a||d.standalone)return;
            int status=RegulationState.read(a);d.policy(status<0?blocked:status==1,status<0?"cached":"service");
        }});
    }
    public static void regulation(boolean blocked){
        VehicleDash d=current.get();if(d!=null)regulation(d.activity,blocked);
    }
    public static void ignition(boolean on) { DATA.clear(); }
    public static void rpm(byte[] data) { DATA.rpm(data,SystemClock.elapsedRealtime()); }
    public static void beginComputed(float rpm,float tqi,float acor,float standard,float boost,float ambient) { DATA.beginComputed(rpm,tqi,acor,standard,boost,ambient); }
    public static void computed(float power,float torque,float boostKpa) { DATA.computed(power,torque,boostKpa); }
    public static void calculateObserved(){DATA.calculateObserved(SystemClock.elapsedRealtime());}
    public static void temperatures(byte[] data) { DATA.temperatures(data,SystemClock.elapsedRealtime()); }
    public static void gforce(byte[] data) { DATA.gforce(data,SystemClock.elapsedRealtime()); }
    public static void wheels(byte[] data) { DATA.wheels(data,SystemClock.elapsedRealtime()); }
    public static void rearView(int data) { DATA.rearView(data); }
    public static void units(byte[] data) { DATA.units(data,SystemClock.elapsedRealtime()); }
    public static void speed(int data) { DATA.speed(data,SystemClock.elapsedRealtime()); }
    public static void additional(byte[] data) { DATA.additional(data,SystemClock.elapsedRealtime()); }
    public static void hscanJsn(int cid,int funcLow,byte[] data){DATA.hscanJsn(cid,funcLow,data,SystemClock.elapsedRealtime());}
    public static void hscanJsn(int cid,int funcLow,byte[] data,long receivedAt){DATA.hscanJsn(cid,funcLow,data,receivedAt);}
    public static void hscanPd(int cid,int funcLow,byte[] data,long receivedAt){DATA.hscanPd(cid,funcLow,data,receivedAt);}
    protected void onAttachedToWindow() { super.onAttachedToWindow(); introStart=0; artwork.open();mediaReceiver.start(activity); post(this); }
    protected void onDetachedFromWindow() { removeCallbacks(this);removeCallbacks(editHold);removeCallbacks(clusterHold);dismissList();touching=false;gaugeEditor.dismiss();graphEditor.dismiss();mediaReceiver.stop();artwork.close();super.onDetachedFromWindow(); }
    public void run() {
        long delay=100;
        long tick=SystemClock.elapsedRealtime();
        if(resumed&&!standalone&&tick-lastPolicyCheck>=1000){lastPolicyCheck=tick;int status=RegulationState.read(activity);if(status>=0)policy(status==1,"service");}
        tryRequestedEntry();
        if(getVisibility()==VISIBLE) {
            invalidate();long now=SystemClock.elapsedRealtime();
            if(graphs)delay=250;
            else if(symmetric&&page==PitRenderer.PAGE_PERFORMANCE&&lapRunning)delay=33;
            else if(symmetric&&page>=PitRenderer.PAGE_RACE)delay=50;  // shift blink, upshift pop, live run timers
            else if(!(cluster&&media&&!gauges) && mediaState.snapshot(now).notice)delay=16;
            if(introStart>0&&now-introStart<1500)delay=Math.min(delay,33);   // smooth the welcome sweep
            if(now-lastDiagnostic>=5000) { lastDiagnostic=now;android.util.Log.i("VehicleDash",DATA.diagnostics(now)+drawStats()); }
        }
        postDelayed(this,delay);
    }
    protected void onDraw(Canvas c) {
        long now=SystemClock.elapsedRealtime();
        MediaState.Snapshot track=mediaState.snapshot(now);artwork.request(track);
        renderer.setArtwork(artwork.bitmap());
        renderer.setGaugeLayout(gauges?gaugeLayout:null);
        renderer.setGraphLayout(graphs?graphLayout:null);
        renderer.setSymmetric(symmetric);
        renderer.setPage(page);
        Telemetry.Sample ms=DATA.sample(Telemetry.DRIVE_MODE,now);int mode=ms.valid?(int)ms.value:-1;
        Theme.mode(modeColor?mode:-1);
        if(mode!=lastMode){
            if(modeScreen&&!picker){int[] st=ClusterPicker.onMode(lastMode,mode,screen,modeBack,hidden,modeTargets);modeBack=st[1];if(st[0]!=screen){screen=st[0];showCluster();}}
            lastMode=mode;
        }
        renderer.setPicker(picker,editing,hidden);
        renderer.setModeOptions(modeColor,modeScreen);
        if(picker&&editing)renderer.setSettings(settingsCat,LanguageSettings.selection(activity),pickSlot,modeTargets);
        renderer.setLap(lapRunning?now-lapStart:lapMs,lapRunning);
        renderer.setOverviewLayout(overviewLayout);
        long drawStart=System.nanoTime();
        renderer.draw(c,getWidth(),getHeight(),cluster,DATA,now,shiftTarget,media,track);
        long took=System.nanoTime()-drawStart;drawNs+=took;drawMaxNs=Math.max(drawMaxNs,took);drawFrames++;
        if(introStart==0)introStart=now;
        long introEl=now-introStart;
        if(introEl<1500)renderer.drawIntro(c,introEl/1500f);
    }
    private void requestMedia(final int command){
        if(blocked||!mediaPending.compareAndSet(false,true))return;
        final MediaState.Snapshot displayed=mediaState.snapshot(SystemClock.elapsedRealtime());
        final int displayedMode=MediaState.modeForSource(displayed.source);
        new Thread(new Runnable(){public void run(){
            String error=null;
            try{if(!(command==0?MediaControl.toggle(activity,displayedMode,displayed.playState):MediaControl.step(activity,command>0,displayedMode)))error=UiText.t("현재 재생 소스에서 음악을 제어할 수 없습니다");}
            catch(Exception e){android.util.Log.e("VehicleDash","Media control unavailable",e);error=UiText.t("음악 제어 요청을 보낼 수 없습니다");}
            final String message=error;
            activity.runOnUiThread(new Runnable(){public void run(){
                mediaPending.set(false);
                if(message!=null&&!activity.isFinishing())Toast.makeText(activity,message,Toast.LENGTH_SHORT).show();
            }});
        }},"NMode-track-control").start();
    }
    private void selectTab(int id){
        int s=header.screenAt(id);   // a tap on a tab shows that screen; a long press on the strip opens the list
        if(s<0)return;
        screen=s;showCluster();invalidate();
    }
    private boolean overviewCovered(float y){return y>=347&&mediaState.snapshot(SystemClock.elapsedRealtime()).notice;}
    public boolean onTouchEvent(MotionEvent event) {
        float x=event.getX()*800/getWidth(), y=event.getY()*480/getHeight();
        if(event.getAction()==MotionEvent.ACTION_DOWN) {
            removeCallbacks(editHold);removeCallbacks(clusterHold);touching=true;downX=x;downY=y;header.down(x,y);
            if(!blocked&&!picker&&header.onStrip(x,y))postDelayed(clusterHold,600);
            overviewDown=!cluster&&!gauges&&!graphs&&!symmetric&&!blocked&&!overviewCovered(y)?overviewLayout.hit(x,y):-1;
            if(!blocked&&!picker&&((gauges&&gaugeEditor.hit(x,y)>=0)||(graphs&&graphLayout.hit(x,y)>=0)))postDelayed(editHold,600);   // not under the screen list
            return true;
        }
        if(event.getAction()==MotionEvent.ACTION_MOVE&&header.move(x,y)){touching=false;removeCallbacks(editHold);removeCallbacks(clusterHold);invalidate();return true;}
        if(event.getAction()==MotionEvent.ACTION_MOVE){if(Math.abs(x-downX)>=16||Math.abs(y-downY)>=16){touching=false;removeCallbacks(editHold);removeCallbacks(clusterHold);}return true;}
        if(event.getAction()==MotionEvent.ACTION_CANCEL) { touching=false;removeCallbacks(editHold);removeCallbacks(clusterHold);header.up();return true; }
        if(event.getAction()!=MotionEvent.ACTION_UP)return true;
        removeCallbacks(editHold);removeCallbacks(clusterHold);
        if(header.up()){touching=false;return true;}   // a strip scroll is never a tap
        boolean tap=touching && Math.abs(x-downX)<16 && Math.abs(y-downY)<16;
        touching=false;
        if(tap&&!blocked&&HeaderTabs.gear(x,y)&&HeaderTabs.gear(downX,downY)){   // gear: edit the screen list, again to close it
            boolean open=!(picker&&editing);picker=open;editing=open;if(open)showCluster();invalidate();return true;
        }
        if(picker&&editing){   // settings: a category on the left opens its choices (완료 closes); a choice applies at once
            if(!tap)return true;
            int k=SettingsLayout.cat(x,y);
            if(k>=0&&k==SettingsLayout.cat(downX,downY)){pickSlot=-1;if(k==SettingsLayout.DONE){picker=false;editing=false;}else settingsCat=k;invalidate();return true;}
            int pane=pickSlot>=0?SettingsLayout.PICK:settingsCat,i=SettingsLayout.item(pane,x,y);
            if(i<0||i!=SettingsLayout.item(pane,downX,downY))return true;
            SharedPreferences.Editor e=activity.getSharedPreferences("pit_vehicle_ui",0).edit();
            if(pickSlot>=0){   // chooser: a screen (or 원래 화면, the last tile) for that mode, then back to the mode rows
                modeTargets[pickSlot]=i<ClusterPicker.COUNT?i:-1;modeBack=-1;
                e.putInt("mode_screen_"+ClusterPicker.MODE_NAMES[pickSlot].toLowerCase(java.util.Locale.US),modeTargets[pickSlot]);pickSlot=-1;
            }
            else switch(settingsCat){
                case SettingsLayout.SCREENS:hidden=ClusterPicker.toggle(hidden,i);showCluster();break;
                case SettingsLayout.THEME:Theme.select(Theme.IDS[i]);e.putString("theme",Theme.current().id);break;
                case SettingsLayout.PAINT:CarPaint.select(i);e.putString("car_paint",CarPaint.id());break;
                case SettingsLayout.MODE:
                    if(i==SettingsLayout.MODE_COLOR)modeColor=!modeColor;
                    else if(i==SettingsLayout.MODE_SCREEN){modeScreen=!modeScreen;modeBack=-1;}
                    else pickSlot=i-SettingsLayout.MODE_FIRST;
                    e.putBoolean("mode_color",modeColor).putBoolean("mode_screen",modeScreen);break;
                case SettingsLayout.LANG:
                    if(LanguageSettings.save(activity,SettingsLayout.LANGS[i])&&entry!=null)entry.setText(UiText.t("N 계기판"));   // the OEM screen's entry button follows the language too
                    break;
                case SettingsLayout.DIAG:DtcDialog.show(activity);break;   // read-only trouble codes
            }
            e.apply();invalidate();return true;
        }
        if(picker){   // a tile picks that screen, any other tap closes the list
            if(!tap)return true;
            int i=ClusterPicker.hit(x,y),slot=i==ClusterPicker.hit(downX,downY)?i:-1;
            int[] list=ClusterPicker.slots(hidden,false);
            if(slot>=0&&slot<list.length){screen=list[slot];showCluster();picker=false;}
            else{picker=false;editing=false;}
            invalidate();return true;
        }
        // Horizontal swipe on the body switches screens (left = next shown screen, right = previous).
        if(!blocked&&downY>52&&Math.abs(x-downX)>=60&&Math.abs(x-downX)>Math.abs(y-downY)*1.5f){
            screen=x<downX?ClusterPicker.next(screen,hidden):ClusterPicker.prev(screen,hidden);showCluster();invalidate();return true;
        }
        if(standalone&&tap&&x>=698&&x<=782&&downX>=698&&downX<=782&&y>=8&&y<=46&&downY>=8&&downY<=46){activity.finish();return true;}
        if(tap&&!blocked&&!cluster&&!gauges&&!graphs&&!symmetric&&overviewDown>=0&&overviewLayout.hit(x,y)==overviewDown&&!overviewCovered(y)){
            overviewLayout.cycle(overviewDown);
            activity.getSharedPreferences("pit_vehicle_ui",0).edit().putInt("overview_style_"+overviewDown,overviewLayout.style(overviewDown)).apply();
            invalidate();return true;
        }
        if(tap&&!blocked&&symmetric&&page==PitRenderer.PAGE_PERFORMANCE&&x>=600&&x<=788&&downX>=600&&downX<=788&&y>=339&&y<=468&&downY>=339&&downY<=468){
            long now=SystemClock.elapsedRealtime();
            if(lapRunning){lapMs=now-lapStart;lapRunning=false;}else if(lapMs>0)lapMs=0;else{lapStart=now;lapRunning=true;}
            invalidate();return true;
        }
        if(tap&&!blocked&&symmetric&&PitRenderer.isCluster(page)&&y>52&&downY>52&&renderer.clusterTap(x,y)){invalidate();return true;}
        if(tap&&!blocked&&gauges&&x>=650&&x<=788&&downX>=650&&downX<=788&&y>=54&&y<=78&&downY>=54&&downY<=78){gaugeEditor.toggleGrid();return true;}
        if(tap&&!blocked&&graphs&&y>=54&&y<=79&&downY>=54&&downY<=79){
            if(x>=628&&x<=702&&downX>=628&&downX<=702)graphEditor.window(60000);
            else if(x>=708&&x<=782&&downX>=708&&downX<=782)graphEditor.window(300000);
            return true;
        }
        if(tap&&!blocked&&cluster&&!gauges){
            boolean small=x>=18&&x<=70&&downX>=18&&downX<=70&&y>=436&&y<=480&&downY>=436&&downY<=480;
            boolean large=media&&x>=518&&x<=758&&downX>=518&&downX<=758&&y>=72&&y<=312&&downY>=72&&downY<=312;
            if(small||large){media=!media;mediaState.dismissNotice();invalidate();return true;}
        }
        if(tap && !blocked && cluster && y>=452 && y<=480 && downY>=452 && downY<=480) {
            if(media && x>=494 && x<=580 && downX>=494 && downX<=580){requestMedia(-1);return true;}
            if(media && x>=588 && x<=686 && downX>=588 && downX<=686){requestMedia(0);return true;}
            if(media && x>=694 && x<=782 && downX>=694 && downX<=782){requestMedia(1);return true;}
            // Step from the target on screen, inside the detected powertrain's range (petrol 3000-6700, diesel 2100-4700).
            VehicleProfile prof=DATA.profile();
            int next=ShiftGuide.target(shiftTarget,prof);
            float minus=media?210:548,plus=media?440:740;
            if(x>=minus && x<=minus+42 && downX>=minus && downX<=minus+42)next-=100;
            if(x>=plus && x<=plus+42 && downX>=plus && downX<=plus+42)next+=100;
            shiftTarget=ShiftGuide.clamp(next,prof.shiftMin,prof.shiftMax);
            activity.getSharedPreferences("pit_vehicle_ui",0).edit().putInt("shift_target",shiftTarget).apply();
            invalidate();return true;
        }
        if(tap && !blocked && symmetric && page==PitRenderer.PAGE_RACE){
            if(x>=500 && x<=788 && downX>=500 && downX<=788 && y>=104 && y<=184 && downY>=104 && downY<=184){media=!media;mediaState.dismissNotice();invalidate();return true;}
            if(media && y>=410 && y<=440 && downY>=410 && downY<=440){
                if(x>=508 && x<=594 && downX>=508 && downX<=594){requestMedia(-1);return true;}
                if(x>=598 && x<=690 && downX>=598 && downX<=690){requestMedia(0);return true;}
                if(x>=694 && x<=782 && downX>=694 && downX<=782){requestMedia(1);return true;}
            }
        }
        if(!tap || blocked || y<8 || y>46 || downY<8 || downY>46)return true;
        gaugeEditor.dismiss();graphEditor.dismiss();
        int id=header.hit(x,y);
        if(id>=0&&id==header.hit(downX,downY))selectTab(id);
        else if(x>=698 && x<=782 && downX>=698 && downX<=782)close();
        return true;
    }
}
