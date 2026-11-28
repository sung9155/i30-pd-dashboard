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
    /** One track for the process: the floating window reads it while the dashboard is away (floatMusic). */
    private static final MediaState mediaState=new MediaState();
    private static final MediaReceiver mediaReceiver=new MediaReceiver(mediaState);
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
    /** GPS speed calibration (설정 → 진단): learned all the time, applied to the shown speed and the runs only when on. */
    private final SpeedChime chime=new SpeedChime();private boolean speedCalOn;private final SpeedCal speedCal=new SpeedCal();private long speedCalSavedAt;private int speedCalSavedN;
    private int lastMode=-1,modeBack=-1;
    /** Settings category open on the left of the gear's settings (SettingsLayout). */
    private int settingsCat=SettingsLayout.SCREENS;
    // ---- 진단 → USB 디버깅: '내 앱''s switch (UsbDebugControl) in the dashboard, and the start-up option (BootReceiver) ----
    private long usbReadAt=Long.MIN_VALUE/2;private boolean usbBusy;
    private void usbRefresh(){usbReadAt=SystemClock.elapsedRealtime();renderer.usbStatus=UsbDebugControl.status();
        renderer.usbAtBoot=activity.getSharedPreferences("pit_vehicle_ui",0).getBoolean(com.veloster.dashboard.BootReceiver.USB_AT_BOOT,false);}
    /** Asks first both ways: ON takes the USB port from the CarPlay dongle, OFF can cut a PC or Pi off. */
    private void usbAsk(){
        if(usbBusy)return;usbRefresh();
        final boolean on=!renderer.usbStatus.equals(UiText.t("설정: ON"));
        new android.app.AlertDialog.Builder(activity).setTitle(UiText.t("USB 디버깅")+" "+(on?"ON":"OFF"))
            .setMessage(UiText.t(on?"PC·파이를 adb로 연결하는 모드로 바꿉니다. 켜 있는 동안 USB 카플레이 동글은 안 됩니다. 시동을 껐다 켜면 꺼집니다.":"일반 USB 모드로 돌아갑니다. 연결된 PC·파이는 끊깁니다."))
            .setPositiveButton(on?"ON":"OFF",new android.content.DialogInterface.OnClickListener(){public void onClick(android.content.DialogInterface d,int w){usbSend(on);}})
            .setNegativeButton(UiText.t("취소"),null).show();
    }
    private void usbSend(final boolean on){
        usbBusy=true;
        new Thread(new Runnable(){public void run(){String r;
            try{r=UsbDebugControl.request(activity,on)?UiText.t("USB 디버깅 ")+(on?"ON":"OFF")+UiText.t(" 전환 요청 완료. 실제 PC 연결은 별도로 확인하세요."):UiText.t("USB 모드 전환 요청이 거부되었습니다.");}
            catch(Exception ex){r=UiText.t("USB 모드 전환 실패: ")+ex.getClass().getSimpleName();}
            catch(LinkageError ex){r=UiText.t("USB 모드 전환 실패: ")+ex.getClass().getSimpleName();}
            final String answer=r;
            activity.runOnUiThread(new Runnable(){public void run(){usbBusy=false;usbRefresh();invalidate();
                if(!activity.isFinishing())Toast.makeText(activity,answer,Toast.LENGTH_LONG).show();}});
        }},"usb-debug").start();
    }
    private int[] order=ClusterPicker.defaultOrder();   // display order of the screens (header tabs, swipe, settings list)
    private float settingsScroll,lastMoveY;             // vertical scroll of the SCREENS list, and the last touch y for dragging it
    private static String orderCsv(int[] o){StringBuilder s=new StringBuilder();for(int i=0;i<o.length;i++){if(i>0)s.append(',');s.append(o[i]);}return s.toString();}
    private static int[] parseOrder(String s){if(s==null||s.length()==0)return null;String[] p=s.split(",");int[] o=new int[p.length];try{for(int i=0;i<p.length;i++)o[i]=Integer.parseInt(p[i].trim());}catch(NumberFormatException e){return null;}return o;}
    /** Screen per ClusterPicker.MODES slot (-1 = 원래 화면), and the slot being chosen in settings (-1 none). */
    private final int[] modeTargets=ClusterPicker.MODE_DEFAULT.clone();
    private int pickSlot=-1;
    /** 화면 list drag: gripRow = the row pressed on its grip (a tap until the finger moves 16 px), dragRow = the row being dragged. */
    private int gripRow=-1,dragRow=-1;private float dragY;
    /** 플로팅 크기 / 투명도 bar under the finger (SettingsLayout.F_SIZE or F_ALPHA), -1 none. */
    private int sliderDrag=-1;
    /** While a row is dragged: scroll the list when the finger rests near the top or bottom edge. */
    private final Runnable dragTick=new Runnable(){public void run(){
        if(dragRow<0)return;float d=SettingsLayout.autoScroll(dragY);
        float to=Math.max(0,Math.min(SettingsLayout.maxScreenScroll(),settingsScroll+d));if(to!=settingsScroll){settingsScroll=to;invalidate();}   // at either end: nothing to redraw
        postDelayed(this,30);}};
    /** Closes settings (완료, 돌아가기, the back key). Every choice is already saved. */
    private void closeSettingsNow(){picker=false;editing=false;pickSlot=-1;floatEdit=-1;gripRow=-1;dragRow=-1;sliderDrag=-1;removeCallbacks(dragTick);fontDrop();invalidate();}
    // ---- 글꼴 (FontManager, from reikop/veloster-n-dashboard): read when the pane shows, applied as root off the UI thread ----
    private FontManager fontManager;private FontManager.Snapshot fontSnap;private int fontPick=-1,fontTop;
    /** fontBusy: a scan or an apply is running; fontTried: this opening of the pane scanned once (a failed scan waits for 새로고침). */
    private boolean fontBusy,fontLoading,fontTried;
    private FontManager fonts(){if(fontManager==null)fontManager=new FontManager(activity);return fontManager;}
    /** The settings closed: the list and the preview typeface go (a bundled font is a few MB), the next opening reads again. */
    private void fontDrop(){if(!fontBusy){fontSnap=null;fontTried=false;}}
    private PitRenderer.FontView fontView(){
        PitRenderer.FontView v=new PitRenderer.FontView();FontManager.Snapshot s=fontSnap;
        v.busy=fontBusy;v.top=fontTop;v.pick=fontPick;
        if(s==null)return v;
        int n=s.entries.size();v.scanned=true;v.names=new String[n];v.details=new String[n];v.usable=new boolean[n];v.faces=new android.graphics.Typeface[n];
        for(int i=0;i<n;i++){FontManager.Entry e=s.entries.get(i);v.names[i]=e.name;v.details[i]=e.detail;v.usable[i]=e.usable;v.faces[i]=e.regular;}
        v.current=s.current;v.currentName=s.currentName;v.pending=s.pending;v.root=s.root;v.free=s.free;v.refusal=s.refusal;
        if(fontPick>=0&&fontPick<n){FontManager.Entry e=s.entries.get(fontPick);v.regular=e.regular;v.bold=e.bold;v.loading=e.needsLoad();}
        return v;
    }
    private void fontScan(){
        if(fontBusy)return;fontBusy=true;fontTried=true;invalidate();
        FontManager.Entry was=fontSnap!=null&&fontPick>=0&&fontPick<fontSnap.entries.size()?fontSnap.entries.get(fontPick):null;
        final String wasKey=was==null?null:was.key();
        new Thread(new Runnable(){public void run(){FontManager.Snapshot r=null;
            try{r=fonts().scan();}catch(RuntimeException e){android.util.Log.w("VehicleDash","font scan",e);}
            final FontManager.Snapshot result=r;
            activity.runOnUiThread(new Runnable(){public void run(){fontBusy=false;
                if(!(picker&&editing)){fontDrop();return;}   // closed meanwhile: keep nothing
                if(result!=null){
                    int pick=result.current>=0?result.current:0;
                    for(int i=0;i<result.entries.size();i++)if(result.entries.get(i).key().equals(wasKey))pick=i;
                    fontSnap=result;fontPick=result.entries.isEmpty()?-1:pick;fontReveal();fontLoadPicked();
                }else if(!activity.isFinishing())Toast.makeText(activity,UiText.t("글꼴 목록을 읽지 못했습니다"),Toast.LENGTH_SHORT).show();
                invalidate();}});
        }},"vn-font-scan").start();
    }
    /** A font inside the app loads (from the APK) when its row is picked; one at a time. */
    private void fontLoadPicked(){
        final FontManager.Snapshot snap=fontSnap;
        if(fontLoading||snap==null||fontPick<0||fontPick>=snap.entries.size())return;
        final FontManager.Entry e=snap.entries.get(fontPick);if(!e.needsLoad())return;
        fontLoading=true;
        new Thread(new Runnable(){public void run(){
            try{fonts().load(snap,e);}catch(RuntimeException x){android.util.Log.w("VehicleDash","font load",x);}
            activity.runOnUiThread(new Runnable(){public void run(){fontLoading=false;invalidate();fontLoadPicked();}});
        }},"vn-font-load").start();
    }
    private void fontReveal(){int n=fontSnap==null?0:fontSnap.entries.size();
        if(fontPick>=0&&fontPick<fontTop)fontTop=fontPick;
        if(fontPick>=fontTop+SettingsLayout.FONT_ROWS)fontTop=fontPick-SettingsLayout.FONT_ROWS+1;
        fontTop=Math.max(0,Math.min(fontTop,Math.max(0,n-SettingsLayout.FONT_ROWS)));}
    private void fontAction(int a){
        int n=fontSnap==null?0:fontSnap.entries.size();
        if(a==SettingsLayout.FONT_SCAN){fontScan();return;}
        if(a==SettingsLayout.FONT_UP){fontTop=Math.max(0,fontTop-SettingsLayout.FONT_ROWS);return;}
        if(a==SettingsLayout.FONT_DOWN){if(fontTop+SettingsLayout.FONT_ROWS<n)fontTop+=SettingsLayout.FONT_ROWS;return;}
        if(a>=SettingsLayout.FONT_PICK){int k=fontTop+a-SettingsLayout.FONT_PICK;if(k<n){fontPick=k;fontLoadPicked();}return;}
        if(fontBusy){Toast.makeText(activity,UiText.t("작업 중…"),Toast.LENGTH_SHORT).show();return;}
        if(a==SettingsLayout.FONT_REBOOT){fontRebootAsk(UiText.t("내비를 재부팅할까요? 화면·음악·PC 연결이 잠시 끊깁니다."));return;}
        if(a!=SettingsLayout.FONT_APPLY)return;
        if(fontPick<0||fontPick>=n){Toast.makeText(activity,UiText.t("왼쪽에서 글꼴을 고르세요"),Toast.LENGTH_SHORT).show();return;}
        if(fontSnap.refusal!=null){new android.app.AlertDialog.Builder(activity).setMessage(UiText.t("이 내비에서는 글꼴을 바꾸지 않습니다")+"\n\n"+fontSnap.refusal).setPositiveButton(UiText.t("확인"),null).show();return;}
        final FontManager.Entry e=fontSnap.entries.get(fontPick);
        if(!e.usable){Toast.makeText(activity,UiText.t("이 글꼴은 적용할 수 없습니다"),Toast.LENGTH_SHORT).show();return;}
        if(fontPick==fontSnap.current){Toast.makeText(activity,UiText.t("이미 설정된 글꼴입니다"),Toast.LENGTH_SHORT).show();return;}
        StringBuilder m=new StringBuilder(e.name).append(UiText.t(" 글꼴을 내비 기본 글꼴로 바꿉니다."))
            .append("\n\n").append(UiText.t("루트 권한으로 시스템 글꼴 설정(/system/etc/system_fonts.xml)을 바꾸며, 재부팅해야 반영됩니다. 처음 바꾸기 전 설정은 /data/local/tmp/vn-fonts에 보관됩니다."));
        if(!e.stock()&&!e.hangul())m.append("\n\n").append(UiText.t("이 글꼴은 한글이 모자라 빠진 글자는 기존 대체 글꼴로 보입니다."));
        if(e.variable())m.append("\n\n").append(UiText.t("가변 글꼴은 기본 굵기 하나만 쓰입니다."));
        new android.app.AlertDialog.Builder(activity).setTitle(UiText.t("글꼴 적용")).setMessage(m.toString())
            .setPositiveButton(UiText.t("적용"),new android.content.DialogInterface.OnClickListener(){public void onClick(android.content.DialogInterface d,int w){fontApply(e);}})
            .setNegativeButton(UiText.t("취소"),null).show();
    }
    private void fontApply(final FontManager.Entry e){
        fontBusy=true;invalidate();
        new Thread(new Runnable(){public void run(){String r;boolean ok=false;
            try{r=fonts().apply(e);ok=true;}
            catch(Exception x){android.util.Log.w("VehicleDash","font apply",x);r=UiText.t("글꼴을 바꾸지 못했습니다: ")+(x.getMessage()!=null?x.getMessage():x.getClass().getSimpleName());}
            final String answer=r;final boolean done=ok;
            activity.runOnUiThread(new Runnable(){public void run(){fontBusy=false;fontScan();
                if(activity.isFinishing())return;
                if(done)fontRebootAsk(answer);
                else new android.app.AlertDialog.Builder(activity).setMessage(answer).setPositiveButton(UiText.t("확인"),null).show();}});
        }},"vn-font-apply").start();
    }
    private void fontRebootAsk(String message){
        new android.app.AlertDialog.Builder(activity).setMessage(message)
            .setPositiveButton(UiText.t("지금 재부팅"),new android.content.DialogInterface.OnClickListener(){public void onClick(android.content.DialogInterface d,int w){
                new Thread(new Runnable(){public void run(){
                    try{fonts().reboot();}
                    catch(final RuntimeException x){android.util.Log.w("VehicleDash","reboot",x);
                        activity.runOnUiThread(new Runnable(){public void run(){if(!activity.isFinishing())new android.app.AlertDialog.Builder(activity)
                            .setMessage(UiText.t("재부팅하지 못했습니다: ")+x.getClass().getSimpleName()+"\n"+UiText.t("'내 앱' → 강제 재시작으로 재부팅하세요."))
                            .setPositiveButton(UiText.t("확인"),null).show();}});}
                }},"vn-reboot").start();}})
            .setNegativeButton(UiText.t("나중에"),null).show();
    }
    /** The back key: true when it left 내 차 / 네온 드라이브 or closed the open settings (the activity stays), false to let it go back as usual. */
    public static boolean closeSettings(Activity a){VehicleDash d=current.get();if(d==null||d.activity!=a)return false;
        if(d.overlayUp()){d.exitOverlay.run();return true;}
        if(!(d.picker&&d.editing))return false;d.closeSettingsNow();return true;}
    // ---- 내 차 (com.veloster.car3d) and 네온 드라이브 (com.veloster.neondrive): their own views over the whole dashboard, like
    // upstream's; fed from DATA (PdSignals). Their back button or the back key returns to the screen before. ----
    private com.veloster.car3d.CarPage carPage;private com.veloster.neondrive.NeonDrivePage neonPage;
    private com.veloster.neondrive.PdSignals pdSignals;
    private int overlayBack=ClusterPicker.OVERVIEW,shownScreen=-1;
    private static boolean ownView(int s){return s==ClusterPicker.CAR3D||s==ClusterPicker.NEON_DRIVE;}
    private boolean overlayUp(){return carPage!=null&&carPage.view().getParent()!=null||neonPage!=null&&neonPage.view().getParent()!=null;}
    private final Runnable exitOverlay=new Runnable(){public void run(){screen=overlayBack==screen||ownView(overlayBack)?ClusterPicker.OVERVIEW:overlayBack;showCluster();invalidate();}};
    /** Shows the current screen's own view while the dashboard is up and in front, and takes the other one away. */
    private void syncOverlay(){
        boolean up=resumed&&getVisibility()==VISIBLE;
        try{
            if(pdSignals==null)pdSignals=new com.veloster.neondrive.PdSignals(DATA);
            boolean car=up&&screen==ClusterPicker.CAR3D,neon=up&&screen==ClusterPicker.NEON_DRIVE;
            if(car&&carPage==null)carPage=new com.veloster.car3d.CarPage(activity,pdSignals,exitOverlay);
            if(neon&&neonPage==null)neonPage=new com.veloster.neondrive.NeonDrivePage(activity,pdSignals,exitOverlay,mediaState);   // the dashboard's now-playing state: the playing track shows at once
            if(carPage!=null){if(!car)carPage.stop();overlay(carPage.view(),car);if(car)carPage.start();}
            if(neonPage!=null){if(!neon)neonPage.stop();overlay(neonPage.view(),neon);if(neon)neonPage.start();}
        }catch(RuntimeException e){com.veloster.diagnostics.ErrorLog.record("own_view_page",e);android.util.Log.e("VehicleDash","screen "+screen+" unavailable",e);}
    }
    private final Runnable syncLater=new Runnable(){public void run(){syncOverlay();}};
    private void overlay(View v,boolean on){
        if(!on){if(v.getParent()!=null)((ViewGroup)v.getParent()).removeView(v);return;}
        if(v.getParent()==null)((FrameLayout)activity.findViewById(android.R.id.content)).addView(v,new FrameLayout.LayoutParams(-1,-1));
        v.bringToFront();
    }
    /** The G sensor for 내 차: {longitudinal (+ accelerating), lateral} in g, NaN when not received. */
    public static float[] gSensor(){long now=SystemClock.elapsedRealtime();Telemetry.Sample x=DATA.sample(11,now),y=DATA.sample(12,now);
        return new float[]{y.valid?y.value:Float.NaN,x.valid?x.value:Float.NaN};}
    private void setSlider(int which,float x){
        if(which==SettingsLayout.F_SIZE)FloatPanel.size=SettingsLayout.sliderStop(SettingsLayout.F_SIZE_BAR,SettingsLayout.F_SIZE_KNOB,SettingsLayout.F_SIZES.length,x);
        else FloatPanel.alpha=SettingsLayout.sliderStop(SettingsLayout.F_ALPHA_BAR,SettingsLayout.F_ALPHA_KNOB,SettingsLayout.F_ALPHAS.length,x);}
    /** 플로팅 pane: the cell whose chooser is open (-1 none), the cell shown on the right, and the chooser's pick. */
    private int floatEdit=-1,floatSel,floatEditSig=-1,floatEditGraph,floatEditTab;
    private final HeaderTabs header=renderer.header();
    private final LapTimer lap=new LapTimer();
    private boolean lapHeld;   // the hold already acted; the finger's release is not a tap
    private long introStart;
    private float downX,downY;
    private long lastDiagnostic,lastFullDiagnostic;
    /** Frame cost since the last diagnostics line (logcat VehicleDash every 5 s), to judge the heavier layouts on the car. */
    private long drawNs,drawMaxNs;
    /** A frame's drawing cost in ms, smoothed over about ten frames; 25 (the heaviest pages on the car) until measured. */
    private float drawCost=25;
    /** How long to wait for the next frame of a live page: up to FRAME_SHARE of the time goes into drawing, so a light
     *  page gets up to 30 frames a second and a heavy one keeps the pace it had (`most`: 50 or 100 ms). The pages were
     *  drawn 10 or 20 times a second whatever they cost, which shows as stutter on a needle. */
    static final float FRAME_SHARE=.25f;
    private long pace(long most){return Math.max(33,Math.min(most,(long)(drawCost/FRAME_SHARE)));}
    private int drawFrames;
    private String drawStats(){
        String s=drawFrames==0?"":String.format(java.util.Locale.US," draw avg %.1f max %.1f ms over %d frames (page %d%s)",drawNs/1e6/drawFrames,drawMaxNs/1e6,drawFrames,page,symmetric?"":", tab");
        drawNs=drawMaxNs=0;drawFrames=0;return s;
    }
    private long lastPolicyCheck;
    private boolean resumed;
    private long entryRequestUntil;
    private int shiftTarget,redline;
    /** Virtual engine sound (settings 사운드): on/off, EngineSound route, engine and exhaust, and the − / + values
     *  (SettingsLayout.SOUND_VOLUME on: volume, pop rate, turbo whistle, blow-off in %, latency compensation in ms). */
    private boolean soundOn;private int soundRoute,soundEngine,soundExhaust;
    private final int[] soundVals=SettingsLayout.SOUND_DEFAULT.clone();
    private static final String[] SOUND_KEYS={"engine_sound_volume","engine_sound_mix","engine_sound_pop","engine_sound_turbo","engine_sound_bov","engine_sound_lead_ms"};
    /** The − / + value of a 사운드 row (SettingsLayout.SOUND_VOLUME..SOUND_LEAD). */
    private int soundVal(int row){return soundVals[row-SettingsLayout.SOUND_VOLUME];}
    private EngineSound sound;
    /** Optional shift beep (설정 → RPM → 변속 알림음, off by default). */
    private final int[] downSounds=SettingsLayout.DOWN_DEFAULT.clone();private int downScope;private final RevMatch.Cue downCue=new RevMatch.Cue();
    private ShiftBeep beep;private boolean beepOn;private int beepSound;private int beepVol=70;private final ShiftGuide.Beep beepEdge=new ShiftGuide.Beep();
    private float regenEnd=Float.NaN;   // odometer at the last DPF regeneration's end, saved when Telemetry moves it   // as saved, 0 = the profile's default
    private final java.util.concurrent.atomic.AtomicBoolean mediaPending=new java.util.concurrent.atomic.AtomicBoolean();
    private Button entry;
    private boolean blocked;
    private boolean standalone;
    private final Runnable editHold=new Runnable(){public void run(){
        if((gauges||graphs)&&touching&&!blocked&&!picker&&getVisibility()==VISIBLE){
            int slot=graphs?graphLayout.hit(downX,downY):gaugeEditor.hit(downX,downY);
            if(slot>=0){touching=false;if(graphs)graphEditor.edit(slot,DATA.profile());else gaugeEditor.edit(slot,DATA.profile());}
        }
    }};
    /** Which hold-to-edit tile set (Tiles) the current screen has, or -1. */
    private int tileLayout(){
        if(cluster)return Tiles.F1;
        if(!symmetric)return -1;
        return page==PitRenderer.PAGE_RACE?Tiles.RACE:page==PitRenderer.PAGE_CLASSIC?Tiles.CLASSIC:page==PitRenderer.PAGE_RETRO?Tiles.RETRO:-1;
    }
    private TilePicker tilePicker;
    /** Hold a tile: pick what it shows from the grouped list, or put its default back; kept per layout and tile. */
    private final Runnable tileHold=new Runnable(){public void run(){
        final int l=tileLayout(),i=l<0?-1:Tiles.hit(l,downX,downY);
        if(i<0||!touching||blocked||picker||getVisibility()!=VISIBLE)return;
        touching=false;int cur=Tiles.signal(l,i,DATA.profile());
        tilePicker.open(UiText.t("표시 항목")+" · "+UiText.t(Tiles.label(cur)),DATA.profile(),true,true,cur,new TilePicker.Pick(){public void pick(int sig){
            Tiles.picked[l][i]=sig;activity.getSharedPreferences("pit_vehicle_ui",0).edit().putInt(Tiles.key(l,i),sig).apply();invalidate();}});
    }};
    /** Lap card on the performance page (PerformanceRenderer.LAP_*). */
    private boolean onLap(float x,float y){return symmetric&&page==PitRenderer.PAGE_PERFORMANCE&&x>=PerformanceRenderer.LAP_L&&x<=PerformanceRenderer.LAP_R&&y>=PerformanceRenderer.LAP_T&&y<=PerformanceRenderer.LAP_B;}
    private final Runnable lapHold=new Runnable(){public void run(){
        if(touching&&!blocked&&getVisibility()==VISIBLE&&onLap(downX,downY)&&lap.hold(SystemClock.elapsedRealtime())){lapHeld=true;invalidate();}
    }};
    /** Hold the 에코 fare 1 s: a new trip. */
    private final Runnable ecoHold=new Runnable(){public void run(){
        EcoCluster.holding=false;
        if(touching&&!blocked&&getVisibility()==VISIBLE&&renderer.ecoShown()&&EcoCluster.onFare(downX,downY)){touching=false;EcoCluster.reset(SystemClock.elapsedRealtime());invalidate();}
    }};
    private void saveEco(){activity.getSharedPreferences("pit_vehicle_ui",0).edit().putBoolean("eco_coins",EcoCluster.coins).putBoolean("eco_l100",EcoCluster.l100)
        .putInt("eco_grade",EcoCluster.grade).putInt("eco_price_0",EcoCluster.prices[0]).putInt("eco_price_1",EcoCluster.prices[1]).putInt("eco_price_2",EcoCluster.prices[2]).putBoolean("eco_hint_done",EcoCluster.hintDone).putBoolean("eco_coin_sound",EcoCluster.coinSound).apply();}
    /** A 에코 picture from res/raw (PNG), or null. */
    private static android.graphics.Bitmap rawBitmap(android.content.Context c,String name){
        try{int id=c.getResources().getIdentifier(name,"raw",c.getPackageName());if(id==0)return null;
            android.graphics.BitmapFactory.Options o=new android.graphics.BitmapFactory.Options();o.inScaled=false;o.inPreferredConfig=android.graphics.Bitmap.Config.ARGB_8888;
            java.io.InputStream in=c.getResources().openRawResource(id);try{return android.graphics.BitmapFactory.decodeStream(in,null,o);}finally{in.close();}
        }catch(Throwable e){android.util.Log.w("VehicleDash","bitmap "+name+": "+e);return null;}
    }
    /** Hold the header clock: 24-hour ("16:52") or 12-hour ("4:52 PM"); kept. Works with the settings open too. */
    private final Runnable clockHold=new Runnable(){public void run(){
        if(touching&&!blocked&&getVisibility()==VISIBLE&&HeaderClock.hit(downX,downY)){
            touching=false;renderer.clock12=!renderer.clock12;
            activity.getSharedPreferences("pit_vehicle_ui",0).edit().putBoolean("clock_12h",renderer.clock12).apply();invalidate();
        }
    }};
    private final Runnable clusterHold=new Runnable(){public void run(){
        if(touching&&!blocked&&getVisibility()==VISIBLE&&header.onStrip(downX,downY)){
            touching=false;picker=true;editing=false;showCluster();invalidate();
        }
    }};
    /** Set the view flags for the current screen (the next shown one if it is hidden). */
    private void applyScreen(){
        screen=ClusterPicker.shown(order,screen,hidden);
        gauges=screen==ClusterPicker.GAUGES;graphs=screen==ClusterPicker.GRAPHS;cluster=screen==ClusterPicker.F1;
        symmetric=screen>ClusterPicker.F1;page=symmetric?ClusterPicker.page(screen):0;
    }
    private void showCluster(){
        applyScreen();FloatPanel.screen=screen;
        if(ownView(screen)&&shownScreen!=screen&&shownScreen>=0&&!ownView(shownScreen))overlayBack=shownScreen;
        shownScreen=screen;removeCallbacks(syncLater);post(syncLater);   // not inside onDraw (a drive-mode jump changes the screen there)
        activity.getSharedPreferences("pit_vehicle_ui",0).edit().putInt("cluster_screen",screen).putInt("cluster_hidden",hidden).putString("cluster_order",orderCsv(order)).apply();
    }
    private VehicleDash(Activity a) { super(a); activity=a; artwork=new ArtworkLoader(a,this);
        gaugeEditor=new GaugeEditor(a,gaugeLayout,new Runnable(){public void run(){invalidate();}});
        tilePicker=new TilePicker(a);
        {final SharedPreferences rp=a.getApplicationContext().getSharedPreferences("pit_vehicle_records",0);   // 기록실
         Records.store(new Records.Store(){public String get(String k){return rp.getString(k,"");}
             public void put(String k,String l){rp.edit().putString(k,l).apply();}public void clear(){rp.edit().clear().apply();}});}
        try{renderer.appVersion=a.getPackageManager().getPackageInfo(a.getPackageName(),0).versionName;}catch(Exception e){/* the status row shows no name */}
        {SharedPreferences tp=a.getSharedPreferences("pit_vehicle_ui",0);   // the tiles' picks (Tiles), -1 = default
         for(int l=0;l<Tiles.KEYS.length;l++)for(int i=0;i<Tiles.picked[l].length;i++)
             try{Tiles.picked[l][i]=tp.getInt(Tiles.key(l,i),-1);}catch(ClassCastException e){Tiles.picked[l][i]=-1;}}
        graphEditor=new GraphEditor(a,graphLayout,new Runnable(){public void run(){invalidate();}});
        for(int i=0;i<Telemetry.LABELS.length;i++)try{
            overviewLayout.style(i,a.getSharedPreferences("pit_vehicle_ui",0).getInt("overview_style_"+i,2));
        }catch(IllegalArgumentException e){/* Retain default for an invalid saved style. */}
        catch(ClassCastException e){/* Retain default for incompatible preferences. */}
        // Kept as saved (0 = never set); the renderer and the -/+ buttons resolve it with the detected powertrain.
        shiftTarget=a.getSharedPreferences("pit_vehicle_ui",0).getInt("shift_target",0);
        redline=a.getSharedPreferences("pit_vehicle_ui",0).getInt("redline",0);VehicleProfile.setRedline(redline);
        try{renderer.clock12=a.getSharedPreferences("pit_vehicle_ui",0).getBoolean("clock_12h",false);}catch(ClassCastException e){/* 24-hour, as before */}
        sound=new EngineSound(a.getApplicationContext(),DATA);beep=new ShiftBeep(a);
        try{SharedPreferences p=a.getSharedPreferences("pit_vehicle_ui",0);beepOn=p.getBoolean("shift_beep",false);SettingsLayout.floatBeep=beepOn&&p.getBoolean("float_beep",false);SettingsLayout.floatSound=p.getBoolean("engine_sound",false)&&p.getBoolean("float_sound",false);
            beepSound=Math.max(0,Math.min(SettingsLayout.BEEP_SOUNDS.length-1,p.getInt("shift_beep_sound",0)));
            beepVol=Math.max(0,Math.min(200,p.getInt("shift_beep_vol",70)));SettingsLayout.fxVol=Math.max(0,Math.min(200,p.getInt("fx_vol",70)));
            downScope=Math.max(0,Math.min(SettingsLayout.DOWN_SCOPES.length-1,p.getInt("downshift_scope",0)));
            for(int k=0;k<3;k++)downSounds[k]=Math.max(-1,Math.min(SettingsLayout.BEEP_SOUNDS.length-1,p.getInt("downshift_sound_"+(k+1),SettingsLayout.DOWN_DEFAULT[k])));}catch(ClassCastException e){/* off, first sound */}
        try{SharedPreferences p=a.getSharedPreferences("pit_vehicle_ui",0);soundOn=SettingsLayout.ENGINE_SOUND&&p.getBoolean("engine_sound",false);
            soundRoute=Math.max(0,Math.min(SettingsLayout.SOUND_ROUTES.length-1,p.getInt("engine_sound_route",0)));
            soundEngine=Math.max(0,Math.min(EngineSynth.engines()-1,p.getInt("engine_sound_engine",0)%EngineSynth.engines()));
            soundExhaust=Math.max(0,Math.min(EngineSynth.exhausts()-1,p.getInt("engine_sound_exhaust",0)));EngineSound.fast=p.getBoolean("engine_sound_fast",false);
            EngineSound.yieldMode=Math.max(0,Math.min(SettingsLayout.SOUND_YIELDS.length-1,p.getInt("engine_sound_yield",0)));
            EngineSound.boostLevel=Math.max(0,Math.min(SettingsLayout.SOUND_BOOSTS.length-1,p.getInt("engine_sound_boost",0)));
            SpeedChime.kmh=p.getInt("speed_chime_kmh",0);loadFloat(p);SpeedChime.sound=Math.max(0,Math.min(SpeedChime.NAMES.length-1,p.getInt("speed_chime_sound",0)));CassetteCluster.clickSound=p.getBoolean("cassette_click",true);
            for(int k=0;k<soundVals.length;k++)soundVals[k]=Math.max(SettingsLayout.SOUND_MIN[k],Math.min(SettingsLayout.SOUND_MAX[k],p.getInt(SOUND_KEYS[k],SettingsLayout.SOUND_DEFAULT[k])));}
        catch(ClassCastException e){/* Sound off for an incompatible saved value. */}
        applySound();
        fonts(a);
        if(EcoCluster.CAR==null){EcoCluster.CAR=rawBitmap(a,"eco_car_rear");EcoCluster.PAINT=rawBitmap(a,"eco_car_paint");}ShiftBeep.loadCassette(a);
        try{SharedPreferences p=a.getSharedPreferences("pit_vehicle_ui",0);EcoCluster.coins=p.getBoolean("eco_coins",false);AnalogCluster.style=p.getInt("analog_look",0);LambdaCluster.downAlways=p.getBoolean("lambda_down_always",false);EcoCluster.l100=p.getBoolean("eco_l100",false);
            EcoCluster.grade=p.getInt("eco_grade",0)==1?1:0;
            for(int g=0;g<3;g++)EcoCluster.prices[g]=Math.max(0,Math.min(EcoCluster.PRICE_MAX,p.getInt("eco_price_"+g,0)));EcoCluster.hintDone=p.getBoolean("eco_hint_done",false);EcoCluster.coinSound=p.getBoolean("eco_coin_sound",false);}
        catch(ClassCastException e){/* defaults */}
        try{regenEnd=a.getSharedPreferences("pit_vehicle_ui",0).getFloat("dpf_regen_end_odo",Float.NaN);DATA.dpfRegenEnd(regenEnd);}
        catch(ClassCastException e){/* Incompatible saved value: count from the next regeneration. */}
        try{Theme.select(a.getSharedPreferences("pit_vehicle_ui",0).getString("theme","n"));}
        catch(ClassCastException e){Theme.select("n");/* Incompatible saved value: open in N. */}
        try{CarPaint.select(a.getSharedPreferences("pit_vehicle_ui",0).getString("car_paint","blue"));}
        catch(ClassCastException e){CarPaint.select("blue");}
        try{SharedPreferences p=a.getSharedPreferences("pit_vehicle_ui",0);PlatePixels.wide=p.getString(PlatePixels.WIDE_KEY,"");   // 넓은 번호판 (3D), 좁은 번호판 (pixel cars)
            if(!p.contains(PlatePixels.NARROW_KEY))p.edit().putString(PlatePixels.NARROW_KEY,PlatePixels.migrate(PlatePixels.wide)).apply();   // once: from the one plate saved before the split
            PlatePixels.narrow=p.getString(PlatePixels.NARROW_KEY,"");}
        catch(ClassCastException e){PlatePixels.wide="";PlatePixels.narrow="";}
        try{Weather.selectSky(a.getSharedPreferences("pit_vehicle_ui",0).getString("sky_8bit","auto"));}
        catch(ClassCastException e){Weather.selectSky("auto");}
        try{SharedPreferences p=a.getSharedPreferences("pit_vehicle_ui",0);modeColor=p.getBoolean("mode_color",false);speedCalOn=p.getBoolean("speed_cal",false);speedCal.restore(p.getFloat("speed_cal_ratio",Float.NaN),p.getInt("speed_cal_samples",0));modeScreen=p.getBoolean("mode_screen",false);
            for(int k=0;k<modeTargets.length;k++)modeTargets[k]=ClusterPicker.validTarget(p.getInt("mode_screen_"+ClusterPicker.MODE_NAMES[k].toLowerCase(java.util.Locale.US),ClusterPicker.MODE_DEFAULT[k]));}
        catch(ClassCastException e){/* Both options off for an incompatible saved value. */}
        try{
            SharedPreferences p=a.getSharedPreferences("pit_vehicle_ui",0);
            int v=p.getInt("cluster_screen",ClusterPicker.OVERVIEW);
            screen=v>=0&&v<ClusterPicker.COUNT?v:ClusterPicker.OVERVIEW;
            hidden=ClusterPicker.valid(p.getInt("cluster_hidden",0));
            order=ClusterPicker.validOrder(parseOrder(p.getString("cluster_order",null)));
            // The order is saved on every screen change, so every install has one: a new default replaces it once.
            if(p.getInt("cluster_order_v",0)<ClusterPicker.ORDER_VERSION){
                order=ClusterPicker.defaultOrder();p.edit().putString("cluster_order",orderCsv(order)).putInt("cluster_order_v",ClusterPicker.ORDER_VERSION).putInt("cluster_screen",screen).putInt("cluster_hidden",hidden).apply();}
        }catch(ClassCastException e){/* Open on Overview, everything shown, for an incompatible saved value. */}
        warmEco();
        applyScreen();
        setVisibility(GONE); }
    /** Hands the saved sound choices to the audio thread. */
    private void applySound(){
        sound.volume(soundVal(SettingsLayout.SOUND_VOLUME));sound.engineMix(soundVal(SettingsLayout.SOUND_MIX));sound.voice(soundEngine,soundExhaust);
        sound.trims(soundVal(SettingsLayout.SOUND_POP),soundVal(SettingsLayout.SOUND_TURBO),soundVal(SettingsLayout.SOUND_BOV),soundVal(SettingsLayout.SOUND_LEAD));
    }
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
        dash.backupOffer();
    }
    // ---- 설정 백업 (SettingsBackup): a file on the head unit's storage, which 삭제 does not remove ----
    private static volatile String backupWritten;
    private static java.io.File backupFile(){return new java.io.File(new java.io.File(android.os.Environment.getExternalStorageDirectory(),SettingsBackup.DIR),SettingsBackup.FILE);}
    /** Writes every preference file when something changed, off the main thread. Not before the restore question is
     *  settled (ASKED): a fresh install would overwrite the backup with its defaults. */
    static void backupSave(android.content.Context context){
        final android.content.Context c=context.getApplicationContext();
        if(!c.getSharedPreferences(SettingsBackup.PREFS,0).getBoolean(SettingsBackup.ASKED,false))return;
        java.util.Map<String,java.util.Map<String,?>> all=new java.util.TreeMap<String,java.util.Map<String,?>>();
        String[] files=new java.io.File(c.getApplicationInfo().dataDir,"shared_prefs").list();
        if(files!=null)for(String n:files)if(n.endsWith(".xml")){String name=n.substring(0,n.length()-4);all.put(name,c.getSharedPreferences(name,0).getAll());}
        final String text=SettingsBackup.text(all);if(text.equals(backupWritten))return;
        new Thread(new Runnable(){public void run(){
            try{SettingsBackup.write(backupFile(),text);backupWritten=text;}catch(Exception e){android.util.Log.w("VehicleDash","settings backup not written: "+e);}
        }},"SettingsBackup").start();
    }
    private static boolean backupRestore(android.content.Context c){
        try{java.util.Map<String,java.util.Map<String,Object>> all=SettingsBackup.values(SettingsBackup.read(backupFile()));if(all.isEmpty())return false;
            for(java.util.Map.Entry<String,java.util.Map<String,Object>> f:all.entrySet()){
                SharedPreferences.Editor ed=c.getSharedPreferences(f.getKey(),0).edit().clear();
                for(java.util.Map.Entry<String,Object> v:f.getValue().entrySet()){Object o=v.getValue();
                    if(o instanceof Integer)ed.putInt(v.getKey(),(Integer)o);else if(o instanceof Boolean)ed.putBoolean(v.getKey(),(Boolean)o);
                    else if(o instanceof Float)ed.putFloat(v.getKey(),(Float)o);else if(o instanceof Long)ed.putLong(v.getKey(),(Long)o);else ed.putString(v.getKey(),(String)o);}
                ed.commit();}
            c.getSharedPreferences(SettingsBackup.PREFS,0).edit().putBoolean(SettingsBackup.ASKED,true).commit();return true;
        }catch(Exception e){android.util.Log.w("VehicleDash","settings backup not restored: "+e);return false;}
    }
    /** 넓은 번호판 (wide: the 3D 내 차 plates, any text up to 10 characters) or 좁은 번호판 (the pixel cars: only what their
     *  font draws, as wide as their plate): type it, saved in pit_vehicle_ui (so the settings backup keeps it) and shown at
     *  once; blank = "i30 PD". The 내 차 screen's 번호판 button opens the wide one. after runs on the UI thread once saved. */
    public static void plateDialog(final android.content.Context c,final boolean wide,final Runnable after){
        final android.widget.EditText in=new android.widget.EditText(c);
        in.setSingleLine(true);
        if(wide)in.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(PlatePixels.WIDE_MAX)});
        else in.setFilters(new android.text.InputFilter[]{new android.text.InputFilter(){   // drawable characters only, no wider than the plate
            public CharSequence filter(CharSequence src,int st,int en,android.text.Spanned dst,int ds,int de){
                String head=dst.subSequence(0,ds).toString(),tail=dst.subSequence(de,dst.length()).toString();StringBuilder b=new StringBuilder();
                for(int i=st;i<en;i++){char ch=src.charAt(i);if(ch!=' '&&!PlatePixels.drawable(ch))continue;
                    if(PlatePixels.width(head+b+ch+tail)>PlatePixels.COLS)break;b.append(ch);}
                return b.length()==en-st?null:b.toString();}}});
        String cur=wide?PlatePixels.wide:PlatePixels.narrow;
        in.setText(cur);in.setSelection(in.getText().length());
        in.setHint(UiText.t(wide?"예: 12가 3456 · 비우면 i30 PD":"예: I30N · 비우면 i30 PD"));
        android.app.AlertDialog.Builder d=new android.app.AlertDialog.Builder(c).setTitle(UiText.t(wide?"넓은 번호판 (3D)":"좁은 번호판 (도트 차)")).setView(in);
        if(!wide)d.setMessage(UiText.t("영문·숫자·공백·- 만 · 도트 번호판 폭까지 (숫자 5개 정도)"));
        d.setPositiveButton(UiText.t("확인"),new android.content.DialogInterface.OnClickListener(){public void onClick(android.content.DialogInterface di,int w){
                String t=in.getText().toString().trim().replaceAll("\\s+"," ");if(wide&&t.length()>PlatePixels.WIDE_MAX)t=t.substring(0,PlatePixels.WIDE_MAX).trim();else if(!wide)t=PlatePixels.filter(t);
                c.getSharedPreferences("pit_vehicle_ui",0).edit().putString(wide?PlatePixels.WIDE_KEY:PlatePixels.NARROW_KEY,t).apply();
                if(wide)PlatePixels.wide=t;else PlatePixels.narrow=t;
                if(after!=null)after.run();}})
            .setNegativeButton(UiText.t("취소"),null).show();
    }
    /** A fresh install (ASKED not set) that finds a backup asks once whether to put it back; with none there is nothing
     *  to ask. Closing the question without an answer leaves it for the next start. */
    private void backupOffer(){
        final SharedPreferences p=activity.getSharedPreferences(SettingsBackup.PREFS,0);
        if(p.getBoolean(SettingsBackup.ASKED,false))return;
        long at=0;try{java.io.File f=backupFile();if(f.isFile()&&f.length()>0)at=f.lastModified();}catch(RuntimeException e){/* no storage: nothing to ask */}
        if(at==0){p.edit().putBoolean(SettingsBackup.ASKED,true).apply();return;}
        String when=new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm",java.util.Locale.US).format(new java.util.Date(at));
        new android.app.AlertDialog.Builder(activity).setTitle(UiText.t("저장해 둔 설정이 있습니다"))
            .setMessage(String.format(UiText.t("전에 쓰던 화면 배치 · 테마 · 플로팅 창 설정입니다. %s 저장"),when))
            .setPositiveButton(UiText.t("설정 되돌리기"),new android.content.DialogInterface.OnClickListener(){public void onClick(android.content.DialogInterface d,int w){
                if(!backupRestore(activity)){Toast.makeText(activity,UiText.t("저장해 둔 설정을 읽지 못했습니다."),Toast.LENGTH_LONG).show();return;}
                // everything read its settings at start: a new process reads the restored ones
                android.content.Intent again=activity.getPackageManager().getLaunchIntentForPackage(activity.getPackageName());
                ((android.app.AlarmManager)activity.getSystemService(android.content.Context.ALARM_SERVICE)).set(android.app.AlarmManager.ELAPSED_REALTIME,SystemClock.elapsedRealtime()+500,
                    android.app.PendingIntent.getActivity(activity,0,again,android.app.PendingIntent.FLAG_CANCEL_CURRENT));
                activity.finish();System.exit(0);
            }})
            .setNegativeButton(UiText.t("새로 시작"),new android.content.DialogInterface.OnClickListener(){public void onClick(android.content.DialogInterface d,int w){
                p.edit().putBoolean(SettingsBackup.ASKED,true).apply();
            }}).show();
    }
    /** Eco's big bitmaps on a background thread, so the first Eco frame does not build them. */
    private boolean ecoHidden(){return (hidden&(1<<ClusterPicker.ECO))!=0;}
    /** Eco's big bitmaps, then one off-screen frame of 아케이드 and 신스웨이브 (shown ones only), on a background thread. */
    private void warmEco(){
        final boolean eco=!ecoHidden(),arcade=(hidden&(1<<ClusterPicker.ARCADE))==0,neon=(hidden&(1<<ClusterPicker.NEON))==0;final VehicleProfile prof=DATA.profile();
        new Thread(new Runnable(){public void run(){android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
            if(eco)try{EcoCluster.warm();}catch(RuntimeException e){/* the draw builds them */}
            if(arcade)renderer.warm(ClusterPicker.ARCADE,prof,SystemClock.elapsedRealtime());
            if(neon)renderer.warm(ClusterPicker.NEON,prof,SystemClock.elapsedRealtime());}},"screen-warm").start();}
    public static void resumeStandalone(Activity a){
        VehicleDash d=current.get();if(d==null||d.activity!=a)return;
        d.resumed=true;d.artwork.open();d.mediaReceiver.start(a);
        if(EcoCluster.CAR==null){EcoCluster.CAR=rawBitmap(a,"eco_car_rear");EcoCluster.PAINT=rawBitmap(a,"eco_car_paint");}d.warmEco();   // let go while out of sight (trim)
        d.policy(false,"standalone-resume");d.setVisibility(VISIBLE);d.shownScreen=d.screen;d.syncOverlay();
    }
    /** The dashboard is out of sight (only the floating window is up, or the system asks for memory): let go of the screens'
     *  kept bitmaps. Switching screens keeps them, so that stays smooth. */
    public static void trimStandalone(Activity a){VehicleDash d=current.get();boolean go=d!=null&&d.activity==a&&!d.resumed;
        android.util.Log.i("VehicleDash","trim: "+(go?"screens let go":d==null?"no dashboard":d.activity!=a?"other activity":"still resumed"));
        if(go){d.renderer.trim();d.invalidate();TaxiZones.release();d.carPage=null;d.neonPage=null;giveBack();}}   // invalidate: no display list may keep a recycled bitmap; 내 차 / 네온 드라이브 are already down (pause)
    /** After a trim: collect now, twice (bitmap finalizers run late), so the let-go pixels leave the heap at once; Dalvik hands
     *  free heap pages back to the system by itself after a collection (Android 4.2 has no VMRuntime.trimHeap to force it).
     *  On the V141 emulator: live heap 9.7 MB -> 5.3 MB, the process 13 MB -> about 9 MB once the pages went back. Off the UI thread. */
    private static void giveBack(){new Thread("vn-trim"){public void run(){
        try{for(long wait:new long[]{500,5000}){Thread.sleep(wait);System.gc();System.runFinalization();System.gc();}
            android.util.Log.i("VehicleDash","trim: collected, "+(Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory())/1024+" KB in use");}
        catch(InterruptedException e){/* the app is going away */}
    }}.start();}
    public static void pauseStandalone(Activity a){
        VehicleDash d=current.get();if(d==null||d.activity!=a)return;
        d.resumed=false;d.touching=false;d.removeCallbacks(d.editHold);backupSave(a);
        d.floatEdit=-1;d.gaugeEditor.dismiss();d.graphEditor.dismiss();if(d.tilePicker!=null)d.tilePicker.dismiss();d.dismissList();d.mediaReceiver.stop();d.artwork.close();
        d.syncOverlay();
    }
    /** Close the screen list and anything it opened (language chooser). */
    private void dismissList(){picker=false;editing=false;gripRow=-1;dragRow=-1;sliderDrag=-1;removeCallbacks(dragTick);fontDrop();}   // every close path: a drag cut off by the lock-out must not leave dragTick running
    private void showDashboard(){setVisibility(VISIBLE);if(entry!=null)entry.setVisibility(GONE);bringToFront();}
    private void close() { entryRequestUntil=0;removeCallbacks(editHold);removeCallbacks(tileHold);touching=false;gaugeEditor.dismiss();graphEditor.dismiss();tilePicker.dismiss();dismissList();setVisibility(GONE); if(entry!=null) entry.setVisibility(blocked?GONE:VISIBLE); }
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
    public static void obd(int pid,byte[] d){
        long now=SystemClock.elapsedRealtime();DATA.obd(pid,d,now);
        // Diesel boost scale check (EMS19 byte1 x 20 hPa is fitted, not measured): MAP beside it every 4 s on a diesel.
        if(pid==0x0b&&now-mapLogAt>=4000&&DATA.profile().fuel==VehicleProfile.Fuel.DIESEL){mapLogAt=now;android.util.Log.i("VehicleDash","OBD "+DATA.boostCheck(now));}
    }
    private static long mapLogAt;
    /** tm_hscan_service poller (telematics, read only): one decoded signal value. */
    public static void tm(int sig,float v,boolean ok){DATA.tm(sig,v,ok,SystemClock.elapsedRealtime());}
    /** The floating window's choices from the dashboard's preferences (pit_vehicle_ui); FloatService reads the same. */
    /** A tap released on the 플로팅 pane (x, y; downX, downY where it went down). A button applies when the finger comes up
     *  on the same button it went down on; with the chooser open only the chooser answers, and 완료 applies its pick. */
    private void floatTap(float x,float y){
        VehicleProfile prof=DATA.profile();
        if(floatEdit==SettingsLayout.FORM_SHEET){int i=SettingsLayout.hitForm(x,y,SettingsLayout.FORM_SLOTS);if(i!=SettingsLayout.hitForm(downX,downY,SettingsLayout.FORM_SLOTS))return;
            if(i==-1)return;if(i>=0){int f=FloatPanel.FORMS[i];if(FloatPanel.shape==FloatPanel.OFF)FloatPanel.lastShape=f;else FloatPanel.shape=f;FloatPanel.count=FloatPanel.fitCount(f,FloatPanel.count);   // never a count the shape leaves a hole in
                floatSel=Math.min(floatSel,FloatPanel.used(f,FloatPanel.count)-1);}floatEdit=-1;   // a tile picks it, outside the sheet closes it
            SharedPreferences.Editor e=activity.getSharedPreferences("pit_vehicle_ui",0).edit();saveFloat(e);e.apply();return;}
        if(floatEdit==SettingsLayout.SKIN_SHEET||floatEdit==SettingsLayout.SKIN_SHEET_DAY){int i=SettingsLayout.hit4(x,y,FloatPanel.skinCount());if(i!=SettingsLayout.hit4(downX,downY,FloatPanel.skinCount()))return;
            if(i==-1)return;if(i>=0){if(floatEdit==SettingsLayout.SKIN_SHEET_DAY)FloatPanel.skinDay=i;else FloatPanel.skinNight=i;}floatEdit=-1;   // a tile picks that side's look, outside the sheet closes it
            SharedPreferences.Editor e=activity.getSharedPreferences("pit_vehicle_ui",0).edit();saveFloat(e);e.apply();return;}
        if(floatEdit>=0){int[] gs=FloatPanel.groups(prof);int tab=Math.max(0,Math.min(floatEditTab,gs.length-1));int[] ch=gs.length==0?new int[0]:FloatPanel.group(gs[tab],prof);int g=FloatPanel.GRAPH_NAMES.length;
            int a=SettingsLayout.sheetHit(x,y,gs.length+1,ch.length,g);if(a!=SettingsLayout.sheetHit(downX,downY,gs.length+1,ch.length,g))return;
            if(a>=SettingsLayout.S_TAB){if(a==SettingsLayout.S_TAB)floatEditSig=-1;else floatEditTab=a-SettingsLayout.S_TAB-1;return;}   // 자동, or open a group
            if(a>=SettingsLayout.S_GRAPH&&a<SettingsLayout.S_CANCEL&&((FloatPanel.shape==FloatPanel.OFF?FloatPanel.lastShape:FloatPanel.shape)==FloatPanel.ROUND||!FloatPanel.graphsUsed()))a=SettingsLayout.S_IN;   // no graph on a round gauge
            if(a>=SettingsLayout.S_GRAPH)floatEditGraph=a<SettingsLayout.S_CANCEL?a-SettingsLayout.S_GRAPH:floatEditGraph;
            if(a>=SettingsLayout.S_VAL&&a<SettingsLayout.S_GRAPH)floatEditSig=ch[a-SettingsLayout.S_VAL];
            if(a==SettingsLayout.S_CANCEL)floatEdit=-1;
            if(a==SettingsLayout.S_DONE){FloatPanel.cells[floatEdit]=floatEditSig;FloatPanel.graphs[floatEdit]=floatEditGraph;floatEdit=-1;
                SharedPreferences.Editor e=activity.getSharedPreferences("pit_vehicle_ui",0).edit();saveFloat(e);e.apply();}
            return;}
        boolean off=FloatPanel.shape==FloatPanel.OFF;int shape=off?FloatPanel.lastShape:FloatPanel.shape;
        float fw=FloatPanel.width(shape,FloatPanel.count,FloatPanel.size),fh=FloatPanel.height(shape,FloatPanel.count,FloatPanel.size);float[][] cr=FloatPanel.cellRects(shape,FloatPanel.count,FloatPanel.size);
        int t=SettingsLayout.floatHit(x,y,fw,fh,cr);
        if(t<0||t!=SettingsLayout.floatHit(downX,downY,fw,fh,cr))return;
        if(t==SettingsLayout.F_ON){if(off)FloatPanel.shape=FloatPanel.lastShape;else{FloatPanel.lastShape=FloatPanel.shape;FloatPanel.shape=FloatPanel.OFF;}}
        else if(t==SettingsLayout.F_HIDE)FloatPanel.hideButton=!FloatPanel.hideButton;
        else if(t==SettingsLayout.F_MUSIC){if(shape==FloatPanel.MUSIC)return;FloatPanel.musicStrip=!FloatPanel.musicStrip;}
        else if(t==SettingsLayout.F_SKIN||t==SettingsLayout.F_SKIN_DAY){if(FloatPanel.skin>=0&&!FloatPanel.round(shape))floatEdit=t==SettingsLayout.F_SKIN?SettingsLayout.SKIN_SHEET:SettingsLayout.SKIN_SHEET_DAY;return;}   // dim while 계기판 따라 is on, and for round gauges
        else if(t>=SettingsLayout.F_DN&&t<SettingsLayout.F_DN+SettingsLayout.F_DN_NAMES.length){if(FloatPanel.skin<0||FloatPanel.round(shape))return;FloatPanel.dn=t-SettingsLayout.F_DN;}   // 자동 / 낮 / 밤, dim like the looks
        else if(t==SettingsLayout.F_AUTO){if(FloatPanel.round(shape))return;if(FloatPanel.skin<0)FloatPanel.setActiveSkin(FloatPanel.skinNow());else FloatPanel.skin=FloatPanel.AUTO;}   // off keeps the look it was following, in the side in use now
        else if(t==SettingsLayout.F_FORM){floatEdit=SettingsLayout.FORM_SHEET;return;}
        else if(t==SettingsLayout.F_EDIT||t>=SettingsLayout.F_CELL&&t<SettingsLayout.F_CELL+FloatPanel.MAX_CELLS){   // open the chooser for that cell (shapes with up to 7 cells)
            int u=FloatPanel.used(shape,FloatPanel.count),k=t==SettingsLayout.F_EDIT?Math.min(floatSel,u-1):t-SettingsLayout.F_CELL;if(k>=u)return;
            floatSel=k;floatEdit=k;int cur=FloatPanel.cells[k];floatEditSig=cur>=0&&FloatPanel.available(cur,prof)?cur:-1;floatEditGraph=FloatPanel.graphs[k];
            int[] gs=FloatPanel.groups(prof);int want=FloatPanel.groupOf(FloatPanel.resolve(cur,k,prof));floatEditTab=0;for(int gt=0;gt<gs.length;gt++)if(gs[gt]==want)floatEditTab=gt;   // open on the reading's group
            return;}
        else{int g=t/10*10,i=t-g;
            if(g==SettingsLayout.F_SHAPE){if(off)FloatPanel.lastShape=i+1;else FloatPanel.shape=i+1;}
            else if(g==SettingsLayout.F_COUNT){if(!FloatPanel.fits(shape,FloatPanel.COUNTS[i]))return;FloatPanel.count=FloatPanel.COUNTS[i];floatSel=Math.min(floatSel,FloatPanel.count-1);}
            else if(g==SettingsLayout.F_CORNER){if(FloatPanel.skinNow()==0)FloatPanel.corner=i;}
            else if(g==SettingsLayout.F_COLOR){if(FloatPanel.skinNow()==0||FloatPanel.round())FloatPanel.color=i;}
            }
        SharedPreferences.Editor e=activity.getSharedPreferences("pit_vehicle_ui",0).edit();saveFloat(e);e.apply();
    }
    public static void loadFloat(android.content.SharedPreferences p){
        try{int shape=p.getInt("float_shape",-1),size=p.getInt("float_size",1);
            if(shape<0){int old=p.getInt("float_style",0);shape=old==1||old==2?FloatPanel.CARD:old==3?FloatPanel.STRIP:old==4?FloatPanel.SIDE:FloatPanel.OFF;if(old==2)size=3;}   // the first test build's single choice
            FloatPanel.shape=shape>=FloatPanel.OFF&&shape<=FloatPanel.LAST_SHAPE?shape:FloatPanel.OFF;FloatPanel.size=Math.max(0,Math.min(FloatPanel.SIZES.length-1,size));
            int n=p.getInt("float_count",4);FloatPanel.count=n>=1&&n<=FloatPanel.MAX_CELLS?n:4;FloatPanel.hideButton=p.getBoolean("float_hide_button",true);FloatPanel.musicStrip=p.getBoolean("float_music_strip",false);
            int last=p.getInt("float_last_shape",FloatPanel.CARD);FloatPanel.lastShape=last>=FloatPanel.CARD&&last<=FloatPanel.LAST_SHAPE?last:FloatPanel.CARD;
            {int ef=FloatPanel.shape==FloatPanel.OFF?FloatPanel.lastShape:FloatPanel.shape;if(!FloatPanel.fits(ef,FloatPanel.count))FloatPanel.count=FloatPanel.fitCount(ef,FloatPanel.count);}   // a count the shape takes (세로 띠 + RPM: 7)
            int cn=p.getInt("float_corner",0),co=p.getInt("float_color",0),al=p.getInt("float_alpha",1),skn=p.getInt("float_skin",FloatPanel.AUTO);
            int[] dn=p.contains("float_dn")?new int[]{p.getInt("float_dn",DayNight.NIGHT),p.getInt("float_skin_day",FloatSkin.WHITE),p.getInt("float_skin_night",0),0}
                :DayNight.firstRead(skn);   // first read after the update: 밤, the night look = the look in use (an update changes nothing)
            if(dn[3]==1){   // only then: the round gauge was skin 9 for a while; on a grid shape it is the 원형 게이지 shape now (9 is 카드 since)
                if(FloatPanel.shape>=FloatPanel.CARD&&FloatPanel.shape<=FloatPanel.SIDE)FloatPanel.shape=FloatPanel.ROUND;if(FloatPanel.lastShape<=FloatPanel.SIDE)FloatPanel.lastShape=FloatPanel.ROUND;}
            FloatPanel.dn=dn[0]>=DayNight.AUTO&&dn[0]<=DayNight.NIGHT?dn[0]:DayNight.NIGHT;
            FloatPanel.skinDay=dn[1]>=0&&dn[1]<FloatPanel.skinCount()?dn[1]:FloatSkin.WHITE;FloatPanel.skinNight=dn[2]>=0&&dn[2]<FloatPanel.skinCount()?dn[2]:0;
            FloatPanel.skin=skn<0||skn>=FloatPanel.skinCount()?FloatPanel.AUTO:0;   // -1 = 계기판 따라, 0 = the day / night looks
            FloatPanel.count=FloatPanel.fitCount(FloatPanel.shape==FloatPanel.OFF?FloatPanel.lastShape:FloatPanel.shape,FloatPanel.count);
            int scr=p.getInt("cluster_screen",ClusterPicker.OVERVIEW);FloatPanel.screen=scr>=0&&scr<ClusterPicker.COUNT?scr:ClusterPicker.OVERVIEW;FloatPanel.alpha=al>=0&&al<FloatPanel.ALPHA_NAMES.length?al:1;
            FloatPanel.shiftTarget=p.getInt("shift_target",0);   // the edge flashes at 설정 → RPM's shift point
            FloatPanel.corner=FloatPanel.CORNER_THEME;FloatPanel.color=FloatPanel.COLOR_THEME;   // the 모서리 / 색 rows are gone: the theme's (an earlier pick is ignored)
            if(current.get()==null)Theme.select(p.getString("theme","n"));   // the window started at boot, before the dashboard: its colours follow the theme too
            for(int k=0;k<FloatPanel.MAX_CELLS;k++){int v=p.getInt("float_cell_"+k,-1);FloatPanel.cells[k]=FloatPanel.pseudo(v)||v>=0&&v<Telemetry.LABELS.length?v:-1;int g=p.getInt("float_graph_"+k,0);FloatPanel.graphs[k]=g>=0&&g<FloatPanel.GRAPH_NAMES.length?g:0;}}
        catch(ClassCastException e){FloatPanel.shape=FloatPanel.OFF;}
    }
    private static void saveFloat(android.content.SharedPreferences.Editor e){
        e.putInt("float_shape",FloatPanel.shape).putInt("float_count",FloatPanel.count).putInt("float_size",FloatPanel.size).putBoolean("float_hide_button",FloatPanel.hideButton).putBoolean("float_music_strip",FloatPanel.musicStrip).remove("float_style")
         .putInt("float_last_shape",FloatPanel.lastShape).putInt("float_corner",FloatPanel.corner).putInt("float_color",FloatPanel.color).putInt("float_alpha",FloatPanel.alpha).putInt("float_skin",FloatPanel.skin)
         .putInt("float_dn",FloatPanel.dn).putInt("float_skin_day",FloatPanel.skinDay).putInt("float_skin_night",FloatPanel.skinNight);
        for(int k=0;k<FloatPanel.MAX_CELLS;k++)e.putInt("float_cell_"+k,FloatPanel.cells[k]).putInt("float_graph_"+k,FloatPanel.graphs[k]);}
    /** Every signal now, for the floating window (FloatService) while the dashboard is not on screen. */
    public static Telemetry.Sample[] snapshotNow(){return DATA.snapshot(SystemClock.elapsedRealtime());}
    private static final Telemetry.Sample[] FLOAT_Q=new Telemetry.Sample[Telemetry.LABELS.length];
    /** Only the signals the floating window's n cells show, in an array indexed by signal that is reused (main thread only). */
    public static Telemetry.Sample[] floatSamples(int n){long now=SystemClock.elapsedRealtime();VehicleProfile prof=DATA.profile();
        for(int k=0;k<n;k++){int sig=FloatPanel.signal(k,prof);if(FloatPanel.quad(sig))for(int w=0;w<4;w++){int s=FloatPanel.quadSig(sig,w);FLOAT_Q[s]=DATA.sample(s,now);}else if(sig==FloatPanel.DCT2){FLOAT_Q[Telemetry.CLUTCH_ODD]=DATA.sample(Telemetry.CLUTCH_ODD,now);FLOAT_Q[Telemetry.CLUTCH_EVEN]=DATA.sample(Telemetry.CLUTCH_EVEN,now);}   // both clutches in one cell
            else if(sig>=0&&sig<FLOAT_Q.length)FLOAT_Q[sig]=DATA.sample(sig,now);}
        FLOAT_Q[0]=DATA.sample(0,now);FLOAT_Q[8]=DATA.sample(8,now);FLOAT_Q[Telemetry.GEAR_SELECTOR]=DATA.sample(Telemetry.GEAR_SELECTOR,now);FLOAT_Q[Telemetry.HEAD_LAMP]=DATA.sample(Telemetry.HEAD_LAMP,now);return FLOAT_Q;}   // rpm and gear: the pill, 단수 중심 and 변속등 머리 show them; the headlamp: the day/night fallback
    /** 과속 차임 while the floating window is up (the dashboard's own frame does not run then). Needs the dashboard to have
     *  been opened once since boot: the sound path belongs to its view. */
    /** '내 앱' 계기판에서 숨김: while the dashboard is on screen it asks 내 앱 to hide its edge button (again every 20 s; 내 앱 brings
     *  it back by itself 75 s after the last ask), and to bring it back when it leaves. 내 앱 decides (its option, default off). */
    private boolean dashHideSent;private long dashHideAt;
    private void dashHide(long now){
        boolean want=resumed&&getVisibility()==VISIBLE;
        if(want&&(!dashHideSent||now-dashHideAt>=20000)){dashHideSent=true;dashHideAt=now;dashHideSend(true);}
        else if(!want&&dashHideSent){dashHideSent=false;dashHideSend(false);}
    }
    private void dashHideSend(boolean on){
        try{activity.sendBroadcast(new android.content.Intent("com.veloster.carplayhub.QUICK_ENTRY_HIDE").setPackage("com.veloster.carplayhub").putExtra("hide",on).putExtra("from","dashboard"));}
        catch(RuntimeException ignored){/* 내 앱 not installed */}
    }
    /** The floating window is up (FloatService): with 엔진 사운드 = 항상 the engine sound goes on while the dashboard is away. */
    public static volatile boolean floatUp;
    private boolean soundWanted(){return soundOn&&(resumed&&getVisibility()==VISIBLE||floatUp&&SettingsLayout.floatSound&&!resumed);}
    /** The floating window's tick: starts or stops the engine sound as soundWanted says (the dashboard's own loop may not run while
     *  it is stopped). Needs the dashboard to have been opened once since boot: the sound belongs to its view. */
    public static void soundTick(){VehicleDash d=current.get();if(d==null||d.sound==null)return;d.sound.play(d.soundWanted()?d.soundRoute:-1);}
    public static void chimeTick(){
        VehicleDash d=current.get();if(d==null||d.beep==null||SpeedChime.kmh<=0)return;
        long now=SystemClock.elapsedRealtime();Telemetry.Sample sp=DATA.sample(7,now);
        if(d.chime.due(sp.valid?Telemetry.displayValue(7,sp.value):Float.NaN,now)&&d.beep.playChime(d.soundOn&&d.soundRoute==0?d.sound:null,SpeedChime.sound,SettingsLayout.fxVol/100f))d.chime.rang(now);
    }
    /** The floating window's tick (FloatService): while the dashboard is away its onDraw does not run, so the shift beep and
     *  the downshift sounds are worked out here from the samples, with the same edges and settings (the 맞춤 소리 화면 scope
     *  against the screen last shown). Only with 플로팅 중 변속 알림 on. */
    public static void shiftTick(){
        VehicleDash d=current.get();if(d==null||d.beep==null||!SettingsLayout.floatBeep||d.resumed)return;
        long now=SystemClock.elapsedRealtime();Telemetry.Sample[] s=DATA.snapshot(now);VehicleProfile p=DATA.profile();
        boolean scoped=SettingsLayout.downScreen(d.downScope,d.screen),clutch=p.trans==VehicleProfile.Trans.MT&&s[Telemetry.CLUTCH].valid&&s[Telemetry.CLUTCH].value>0;
        EngineSound mix=d.soundOn&&d.soundRoute==0?d.sound:null;
        if(d.beepEdge.fire(ShiftGuide.evaluate(s,d.shiftTarget,p).shift&&!(scoped&&clutch),now)&&d.beepOn)d.beep.play(mix,d.beepSound,d.beepVol/100f);
        int dn=d.downCue.fire(scoped?RevMatch.downs(s,p):0,now);
        if(dn>0&&d.downSounds[dn-1]>=0)d.beep.play(mix,d.downSounds[dn-1],d.beepVol/100f);
    }
    public static boolean diesel(){return DATA.profile().fuel==VehicleProfile.Fuel.DIESEL;}
    public static VehicleProfile profileNow(){return DATA.profile();}
    private static ArtworkLoader floatArt;
    /** The floating window shows the track (FloatPanel.musicShown): on keeps the media receiver up on the application context
     *  while the dashboard is away (its pause stopped it), with an album art loader of its own; off lets both go unless the
     *  dashboard is on screen (it uses the receiver then). The track itself (mediaState) lives on between the two. */
    public static void floatMusic(android.content.Context c,boolean on){
        if(on){mediaReceiver.start(c);if(floatArt==null)floatArt=new ArtworkLoader(c.getApplicationContext(),null);return;}
        if(floatArt!=null){floatArt.close();floatArt=null;}
        VehicleDash d=current.get();if(d==null||!d.resumed)mediaReceiver.stop();
    }
    /** The track now for the floating window; asks its loader for the album art. */
    public static MediaState.Snapshot floatTrack(){MediaState.Snapshot t=mediaState.snapshot(SystemClock.elapsedRealtime());if(floatArt!=null)floatArt.request(t);return t;}
    public static android.graphics.Bitmap floatArtwork(){return floatArt==null?null:floatArt.bitmap();}
    /** The rear camera is showing (PGS): the floating window steps aside. */
    public static boolean rearCamera(){return DATA.rearCamera();}
    /** The latest valid value of a signal, NaN when there is none (the OBD poller's sports-drive burst reads rpm and pedal). */
    public static float now(int i){Telemetry.Sample x=DATA.sample(i,SystemClock.elapsedRealtime());return x.valid?x.value:Float.NaN;}
    /** A TTF from res/raw (API 17 has no font resources): copied once into the cache, then Typeface.createFromFile; null on any failure. */
    /** The Chakra Petch faces (LambdaCluster, the floating window's skins), loaded once: by the dashboard, or by FloatService at boot. */
    public static void fonts(android.content.Context a){if(LambdaCluster.FACE==null){LambdaCluster.FACE=rawFont(a,"chakra_petch_medium");LambdaCluster.FACE_BOLD=rawFont(a,"chakra_petch_bold");}}
    private static android.graphics.Typeface rawFont(android.content.Context c,String name){
        try{int id=c.getResources().getIdentifier(name,"raw",c.getPackageName());if(id==0)return null;
            java.io.File f=new java.io.File(c.getCacheDir(),name+".ttf");
            if(!f.isFile()||f.length()==0){java.io.InputStream in=c.getResources().openRawResource(id);java.io.FileOutputStream out=new java.io.FileOutputStream(f);
                try{byte[] b=new byte[8192];for(int n;(n=in.read(b))>0;)out.write(b,0,n);}finally{in.close();out.close();}}
            return android.graphics.Typeface.createFromFile(f);
        }catch(Throwable e){android.util.Log.w("VehicleDash","font "+name+": "+e);return null;}
    }
    protected void onAttachedToWindow() { super.onAttachedToWindow(); introStart=0; artwork.open();mediaReceiver.start(activity);Gps.start(activity); post(this); }
    protected void onDetachedFromWindow() { sound.play(-1);removeCallbacks(this);removeCallbacks(editHold);removeCallbacks(tileHold);removeCallbacks(clusterHold);removeCallbacks(clockHold);removeCallbacks(lapHold);removeCallbacks(ecoHold);EcoCluster.holding=false;dismissList();touching=false;gaugeEditor.dismiss();graphEditor.dismiss();tilePicker.dismiss();mediaReceiver.stop();artwork.close();Gps.stop();super.onDetachedFromWindow(); }
    public void run() {
        long delay=100;
        long tick=SystemClock.elapsedRealtime();
        if(resumed&&!standalone&&tick-lastPolicyCheck>=1000){lastPolicyCheck=tick;int status=RegulationState.read(activity);if(status>=0)policy(status==1,"service");}
        tryRequestedEntry();
        sound.play(soundWanted()?soundRoute:-1);
        dashHide(tick);
        {int ck=EcoCluster.clink;if(ck!=0){EcoCluster.clink=0;   // a coin reaches the road on the 에코 screen (negative: its bounce)
            if(EcoCluster.coinSound&&EcoCluster.coins&&resumed&&getVisibility()==VISIBLE&&renderer.ecoShown()&&!picker)beep.playCoin(soundOn&&soundRoute==0?sound:null,Math.abs(ck),SettingsLayout.fxVol/100f*EcoCluster.clinkGain);}}
        {int ck=CassetteCluster.cue;if(ck!=0){CassetteCluster.cue=0;   // a key of the 카세트 screen: latched, released or tapped
            if(CassetteCluster.clickSound&&resumed&&getVisibility()==VISIBLE&&!picker)beep.playCassette(soundOn&&soundRoute==0?sound:null,ck,SettingsLayout.fxVol/100f);}}
        if(getVisibility()==VISIBLE) {
            invalidate();long now=SystemClock.elapsedRealtime();
            if(graphs)delay=250;
            else if(symmetric&&page==PitRenderer.PAGE_PERFORMANCE&&lap.state()==LapTimer.RUNNING)delay=33;
            else if(symmetric&&page==PitRenderer.PAGE_ECO)delay=EcoCluster.animating||EcoCluster.holding?40:250;   // 25 frames a second while anything moves; still: 4
            else if(symmetric&&page==PitRenderer.PAGE_RECORDS)delay=100;   // the live run timer shows tenths; nothing else moves
            else if(symmetric&&page>=PitRenderer.PAGE_RACE)delay=pace(50);  // shift blink, upshift pop, live run timers
            else if(!(cluster&&media&&!gauges) && mediaState.snapshot(now).notice)delay=16;
            else if(!picker)delay=pace(100);   // the tab pages: needles and bars (the screen list and the settings stand still)
            if(introStart>0&&now-introStart<1500)delay=Math.min(delay,33);   // smooth the welcome sweep
            if(header.flinging())delay=16;   // a flicked tab strip coasts
            if(picker&&editing&&settingsCat==SettingsLayout.FLOAT)delay=1000;   // 플로팅 settings: example values, nothing moves (a tap redraws at once)
            float re=DATA.dpfRegenEnd();
            if(Float.compare(re,regenEnd)!=0){regenEnd=re;activity.getSharedPreferences("pit_vehicle_ui",0).edit().putFloat("dpf_regen_end_odo",re).apply();}
            // every 5 s the short line; the per-signal dump (about 3 KB) once a minute: at 5 s it filled the head unit's small
            // log buffer in seconds and pushed the OBD probe rounds out of a diagnostic record (a diesel PD, 2026-11-06)
            if(now-lastDiagnostic>=5000) { lastDiagnostic=now;boolean full=now-lastFullDiagnostic>=60000;if(full)lastFullDiagnostic=now;
                android.util.Log.i("VehicleDash",(full?DATA.diagnostics(now):"status")+drawStats()+DATA.boostFit()+DATA.egt()+ObdLive.summary()); }
        }
        postDelayed(this,delay);
    }
    protected void onDraw(Canvas c) {
        long now=SystemClock.elapsedRealtime();
        if(FloatPanel.shape!=FloatPanel.OFF)FloatPanel.track(FloatPanel.count,floatSamples(FloatPanel.count),DATA.profile(),now);   // peaks and line graphs go on while the window is down
        header.step(SystemClock.uptimeMillis());   // MotionEvent times are uptime
        MediaState.Snapshot track=mediaState.snapshot(now);artwork.request(track);FloatPanel.music(track,artwork.bitmap());   // the settings' preview shows the track too
        renderer.setArtwork(artwork.bitmap());
        renderer.setGaugeLayout(gauges?gaugeLayout:null);
        renderer.setGraphLayout(graphs?graphLayout:null);
        renderer.setSymmetric(symmetric);
        renderer.setPage(page);
        {Telemetry.Sample sp=DATA.sample(7,now),yw=DATA.sample(Telemetry.YAW,now),bs=DATA.sample(Telemetry.BRAKE_SWITCH,now),bp=DATA.sample(10,now);
         boolean braking=Telemetry.braking(bs.valid?bs.value:Float.NaN,bp.valid?bp.value:Float.NaN);
         speedCal.update(now,sp.valid&&sp.ageMs<=500?sp.value:Float.NaN,yw.valid?yw.value:Float.NaN,braking,GpsFix.seq,GpsFix.fresh()?GpsFix.speedKmh:Float.NaN,GpsFix.accuracy);
         float r=speedCal.ratio();Telemetry.speedScale=speedCalOn&&!Float.isNaN(r)?r:1;
         if(!Float.isNaN(r)&&speedCal.samples()!=speedCalSavedN&&now-speedCalSavedAt>60000){speedCalSavedAt=now;speedCalSavedN=speedCal.samples();
             activity.getSharedPreferences("pit_vehicle_ui",0).edit().putFloat("speed_cal_ratio",r).putInt("speed_cal_samples",speedCalSavedN).apply();}}
        renderer.setSpeedCal(speedCalOn,speedCal.ratio(),speedCal.samples());
        Telemetry.Sample ms=DATA.sample(Telemetry.DRIVE_MODE,now);int mode=ms.valid?(int)ms.value:-1;
        Theme.mode(modeColor?mode:-1);
        if(mode!=lastMode){
            if(modeScreen&&!picker){int[] st=ClusterPicker.onMode(lastMode,mode,screen,modeBack,hidden,modeTargets);modeBack=st[1];if(st[0]!=screen){screen=st[0];showCluster();}}
            lastMode=mode;
        }
        renderer.setPicker(picker,editing,hidden);renderer.setOrder(order,settingsScroll);renderer.setScreenDrag(dragRow,dragY);
        renderer.setModeOptions(modeColor,modeScreen);
        if(picker&&editing){renderer.setFloatEdit(floatEdit,floatSel,floatEditSig,floatEditGraph,floatEditTab);renderer.setSettings(settingsCat,LanguageSettings.selection(activity),pickSlot,modeTargets);renderer.setSound(soundOn,soundRoute,soundEngine,soundExhaust,soundVals);renderer.setBeep(beepOn,beepSound,beepVol,downSounds,downScope);
            if(settingsCat==SettingsLayout.FONT&&pickSlot<0){if(fontSnap==null&&!fontBusy&&!fontTried)fontScan();renderer.setFonts(fontView());}
            if(settingsCat==SettingsLayout.DIAG&&SystemClock.elapsedRealtime()-usbReadAt>2000)usbRefresh();}   // the port can change under us (Factory Test, '내 앱')
        renderer.setLap(lap);
        renderer.setOverviewLayout(overviewLayout);
        long drawStart=System.nanoTime();
        renderer.draw(c,getWidth(),getHeight(),cluster,DATA,now,shiftTarget,media,track);
        // the edge is tracked every frame; the beep only plays when switched on and the dashboard is up
        // the downshift-sound screens keep the shift beep for the clutch out: a downshift blip often crosses the upshift target
        boolean scoped=SettingsLayout.downScreen(downScope,screen);
        boolean up=renderer.shiftCue()&&!(scoped&&renderer.clutchIn()),shown=resumed&&getVisibility()==VISIBLE;
        if(beepEdge.fire(up,now)&&beepOn&&shown)beep.play(soundOn&&soundRoute==0?sound:null,beepSound,beepVol/100f);
        int dn=downCue.fire(scoped?renderer.revMatchDowns():0,now);   // the blip landed on a gear's target: that gear's sound
        if(dn>0&&downSounds[dn-1]>=0&&shown)beep.play(soundOn&&soundRoute==0?sound:null,downSounds[dn-1],beepVol/100f);
        {Telemetry.Sample sp=DATA.sample(7,now);if(chime.due(sp.valid?Telemetry.displayValue(7,sp.value):Float.NaN,now)&&shown&&beep.playChime(soundOn&&soundRoute==0?sound:null,SpeedChime.sound,SettingsLayout.fxVol/100f))chime.rang(now);}   // 과속 차임
        long took=System.nanoTime()-drawStart;drawNs+=took;drawMaxNs=Math.max(drawMaxNs,took);drawFrames++;drawCost+=(took/1e6f-drawCost)*.1f;
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
            try{boolean ok=command==1||command==-1?MediaControl.step(activity,command>0,displayedMode)   // next / previous
                    :command==2?MediaControl.key(activity,displayedMode,126)   // KEYCODE_MEDIA_PLAY
                    :command==3?MediaControl.key(activity,displayedMode,127)   // KEYCODE_MEDIA_PAUSE
                    :command==4?MediaControl.key(activity,displayedMode,86)    // KEYCODE_MEDIA_STOP
                    :command==5?MediaControl.key(activity,displayedMode,91)    // KEYCODE_MUTE (the car's mute: BT has no pause)
                    :MediaControl.toggle(activity,displayedMode,displayed.playState);   // 0: play/pause toggle (race panel)
                if(!ok)error=UiText.t("현재 재생 소스에서 음악을 제어할 수 없습니다");}
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
            removeCallbacks(editHold);removeCallbacks(tileHold);removeCallbacks(clusterHold);removeCallbacks(clockHold);removeCallbacks(lapHold);removeCallbacks(ecoHold);EcoCluster.holding=false;touching=true;downX=x;downY=y;lastMoveY=y;header.down(x,y);
            if(!blocked&&!picker&&header.onStrip(x,y))postDelayed(clusterHold,600);
            if(!blocked&&HeaderClock.hit(x,y))postDelayed(clockHold,600);
            gripRow=-1;dragRow=-1;sliderDrag=-1;removeCallbacks(dragTick);
            if(picker&&editing&&pickSlot<0&&floatEdit<0&&settingsCat==SettingsLayout.SCREENS&&SettingsLayout.onGrip(x))gripRow=SettingsLayout.screenRow(y,settingsScroll);
            if(picker&&editing&&pickSlot<0&&floatEdit<0&&settingsCat==SettingsLayout.FLOAT){float[] sb=SettingsLayout.F_SIZE_BAR,ab=SettingsLayout.F_ALPHA_BAR;
                if(x>=sb[0]&&x<=sb[2]&&y>=sb[1]&&y<=sb[3])sliderDrag=SettingsLayout.F_SIZE;else if(x>=ab[0]&&x<=ab[2]&&y>=ab[1]&&y<=ab[3])sliderDrag=SettingsLayout.F_ALPHA;}
            removeCallbacks(lapHold);removeCallbacks(ecoHold);EcoCluster.holding=false;lapHeld=false;if(!blocked&&!picker&&onLap(x,y))postDelayed(lapHold,700);
            overviewDown=!cluster&&!gauges&&!graphs&&!symmetric&&!blocked&&!overviewCovered(y)?overviewLayout.hit(x,y):-1;
            if(!blocked&&!picker&&((gauges&&gaugeEditor.hit(x,y)>=0)||(graphs&&graphLayout.hit(x,y)>=0)))postDelayed(editHold,600);   // not under the screen list
            if(!blocked&&!picker&&tileLayout()>=0&&Tiles.hit(tileLayout(),x,y)>=0)postDelayed(tileHold,600);
            if(!blocked&&!picker&&renderer.ecoShown()&&EcoCluster.onFare(x,y)){EcoCluster.holding=true;postDelayed(ecoHold,1000);}
            return true;
        }
        if(event.getAction()==MotionEvent.ACTION_MOVE&&gripRow>=0){   // a row held by its grip: drag it once the finger has moved
            if(dragRow<0&&Math.abs(y-downY)>=16){dragRow=gripRow;touching=false;postDelayed(dragTick,30);}
            if(dragRow>=0){dragY=y;invalidate();}
            return true;
        }
        if(event.getAction()==MotionEvent.ACTION_MOVE&&sliderDrag>=0){int was=sliderDrag==SettingsLayout.F_SIZE?FloatPanel.size:FloatPanel.alpha;setSlider(sliderDrag,x);
            touching=false;if((sliderDrag==SettingsLayout.F_SIZE?FloatPanel.size:FloatPanel.alpha)!=was)invalidate();return true;}
        if(event.getAction()==MotionEvent.ACTION_MOVE&&picker&&editing&&pickSlot<0&&SettingsLayout.scrolls(settingsCat)&&downX>=SettingsLayout.PANE_L-6&&downY>=SettingsLayout.TOP&&downY<=SettingsLayout.BOTTOM){
            settingsScroll=Math.max(0,Math.min(SettingsLayout.maxPaneScroll(settingsCat),settingsScroll-(y-lastMoveY)));lastMoveY=y;
            if(Math.abs(y-downY)>=10)touching=false;   // a drag that scrolls is not a tap
            invalidate();return true;
        }
        if(event.getAction()==MotionEvent.ACTION_MOVE&&header.move(x,y,event.getEventTime())){touching=false;removeCallbacks(editHold);removeCallbacks(tileHold);removeCallbacks(clusterHold);removeCallbacks(clockHold);removeCallbacks(lapHold);removeCallbacks(ecoHold);EcoCluster.holding=false;invalidate();return true;}
        if(event.getAction()==MotionEvent.ACTION_MOVE){if(Math.abs(x-downX)>=16||Math.abs(y-downY)>=16){touching=false;removeCallbacks(editHold);removeCallbacks(tileHold);removeCallbacks(clusterHold);removeCallbacks(clockHold);removeCallbacks(lapHold);removeCallbacks(ecoHold);EcoCluster.holding=false;}return true;}
        if(event.getAction()==MotionEvent.ACTION_CANCEL) { touching=false;gripRow=-1;dragRow=-1;sliderDrag=-1;removeCallbacks(dragTick);removeCallbacks(editHold);removeCallbacks(tileHold);removeCallbacks(clusterHold);removeCallbacks(clockHold);removeCallbacks(lapHold);removeCallbacks(ecoHold);EcoCluster.holding=false;header.up();return true; }
        if(event.getAction()!=MotionEvent.ACTION_UP)return true;
        removeCallbacks(editHold);removeCallbacks(tileHold);removeCallbacks(clusterHold);removeCallbacks(clockHold);removeCallbacks(lapHold);removeCallbacks(ecoHold);EcoCluster.holding=false;
        if(header.up(event.getEventTime())){touching=false;return true;}   // a strip scroll is never a tap
        boolean tap=touching && Math.abs(x-downX)<16 && Math.abs(y-downY)<16;
        touching=false;
        if(tap&&!blocked&&HeaderTabs.gear(x,y)&&HeaderTabs.gear(downX,downY)){   // gear: edit the screen list, again to close it
            boolean open=!(picker&&editing);picker=open;editing=open;if(open)showCluster();invalidate();return true;
        }
        if(picker&&editing){   // settings: a category on the left opens its choices (완료 closes); a choice applies at once
            if(dragRow>=0){   // a dragged 화면 row lands where the finger is
                int to=SettingsLayout.dropRow(y,settingsScroll);
                if(to!=dragRow){order=ClusterPicker.move(order,dragRow,to);activity.getSharedPreferences("pit_vehicle_ui",0).edit().putString("cluster_order",orderCsv(order)).apply();showCluster();}
                gripRow=-1;dragRow=-1;removeCallbacks(dragTick);invalidate();return true;}
            gripRow=-1;
            if(sliderDrag>=0){setSlider(sliderDrag,x);sliderDrag=-1;SharedPreferences.Editor e=activity.getSharedPreferences("pit_vehicle_ui",0).edit();saveFloat(e);e.apply();invalidate();return true;}
            if(!tap)return true;
            if(x>=698&&x<=782&&downX>=698&&downX<=782&&y>=8&&y<=46&&downY>=8&&downY<=46){closeSettingsNow();return true;}   // 돌아가기 = 완료 here
            int k=SettingsLayout.cat(x,y);
            if(floatEdit<0&&k>=0&&k==SettingsLayout.cat(downX,downY)){pickSlot=-1;if(k==SettingsLayout.DONE)closeSettingsNow();else{settingsCat=k;settingsScroll=0;}invalidate();return true;}
            if(settingsCat==SettingsLayout.SCREENS&&pickSlot<0){   // the 화면 list: ▲▼ reorder, tap elsewhere toggles 사용/미사용
                int p=SettingsLayout.screenRow(y,settingsScroll);
                if(p>=0&&p==SettingsLayout.screenRow(downY,settingsScroll)){
                    int[] o=ClusterPicker.validOrder(order);int sc=o[p],z=SettingsLayout.screenZone(x);
                    if(z==1)order=ClusterPicker.reorder(order,p,-1);
                    else if(z==2)order=ClusterPicker.reorder(order,p,1);
                    else hidden=ClusterPicker.toggle(hidden,sc);
                    activity.getSharedPreferences("pit_vehicle_ui",0).edit().putString("cluster_order",orderCsv(order)).putInt("cluster_hidden",hidden).apply();
                    showCluster();invalidate();
                }
                return true;
            }
            if(settingsCat==SettingsLayout.FLOAT&&pickSlot<0){floatTap(x,y);invalidate();return true;}
            if(settingsCat==SettingsLayout.FONT&&pickSlot<0){int t=SettingsLayout.fontHit(x,y);if(t>=0&&t==SettingsLayout.fontHit(downX,downY))fontAction(t);invalidate();return true;}
            int pane=pickSlot>=0?SettingsLayout.PICK:settingsCat;float paneSc=SettingsLayout.rowScroll(pane)?settingsScroll:0;
            int i=SettingsLayout.item(pane,x,y,paneSc);
            if(i<0||i!=SettingsLayout.item(pane,downX,downY,paneSc))return true;
            SharedPreferences.Editor e=activity.getSharedPreferences("pit_vehicle_ui",0).edit();
            if(pickSlot>=0){   // chooser: a screen (or 원래 화면, the last tile) for that mode, then back to the mode rows
                modeTargets[pickSlot]=i<ClusterPicker.COUNT?i:-1;modeBack=-1;
                e.putInt("mode_screen_"+ClusterPicker.MODE_NAMES[pickSlot].toLowerCase(java.util.Locale.US),modeTargets[pickSlot]);pickSlot=-1;
            }
            else switch(settingsCat){
                case SettingsLayout.SCREENS:hidden=ClusterPicker.toggle(hidden,i);showCluster();break;
                case SettingsLayout.THEME:Theme.select(Theme.IDS[i]);e.putString("theme",Theme.current().id);break;
                case SettingsLayout.PAINT:
                    if(i==CarPaint.IDS.length){Weather.nextSky();e.putString("sky_8bit",Weather.skyId());break;}   // the sky tile cycles 자동 -> 맑음 -> 흐림
                    if(i>CarPaint.IDS.length){plateDialog(activity,i==SettingsLayout.PAINT_WIDE_PLATE,new Runnable(){public void run(){invalidate();}});break;}   // the two plate rows
                    CarPaint.select(SettingsLayout.PAINT_ORDER[i]);e.putString("car_paint",CarPaint.id());break;
                case SettingsLayout.MODE:
                    if(i==SettingsLayout.MODE_COLOR)modeColor=!modeColor;
                    else if(i==SettingsLayout.MODE_SCREEN){modeScreen=!modeScreen;modeBack=-1;}
                    else pickSlot=i-SettingsLayout.MODE_FIRST;
                    e.putBoolean("mode_color",modeColor).putBoolean("mode_screen",modeScreen);break;
                case SettingsLayout.LANG:
                    if(LanguageSettings.save(activity,SettingsLayout.LANGS[i])&&entry!=null)entry.setText(UiText.t("N 계기판"));   // the OEM screen's entry button follows the language too
                    break;
                case SettingsLayout.RPM:{   // − / + step 100 rpm; the rest of the row puts the default back
                    if(i==SettingsLayout.RPM_BEEP){boolean was=beepOn;SettingsLayout.floatBeep=beepOn&&!SettingsLayout.floatBeep;beepOn=!was||SettingsLayout.floatBeep;   // 끔 -> 계기판 -> 항상 -> 끔
                        e.putBoolean("shift_beep",beepOn);e.putBoolean("float_beep",SettingsLayout.floatBeep);if(was)break;if(beepOn)beep.play(soundOn&&soundRoute==0?sound:null,beepSound,beepVol/100f);break;}   // a sample beep when switched on
                    if(i==SettingsLayout.RPM_BEEP_SOUND){if(!beepOn)break;beepSound=(beepSound+1)%SettingsLayout.BEEP_SOUNDS.length;e.putInt("shift_beep_sound",beepSound);beep.play(soundOn&&soundRoute==0?sound:null,beepSound,beepVol/100f);break;}
                    if(i==SettingsLayout.RPM_DOWN_SCOPE){downScope=(downScope+1)%SettingsLayout.DOWN_SCOPES.length;e.putInt("downshift_scope",downScope);break;}
                    if(i>=SettingsLayout.RPM_DOWN1){int dk=i-SettingsLayout.RPM_DOWN1;   // 끔 -> each sound in turn -> 끔
                        downSounds[dk]=downSounds[dk]+1>=SettingsLayout.BEEP_SOUNDS.length?-1:downSounds[dk]+1;e.putInt("downshift_sound_"+(dk+1),downSounds[dk]);
                        if(downSounds[dk]>=0)beep.play(soundOn&&soundRoute==0?sound:null,downSounds[dk],beepVol/100f);break;}
                    if(i==SettingsLayout.RPM_BEEP_VOL){if(!beepOn&&downSounds[0]<0&&downSounds[1]<0&&downSounds[2]<0)break;int zv=SettingsLayout.stepZone(x);if(zv<0||zv!=SettingsLayout.stepZone(downX))break;   // − / + 10%; the label puts 70% back
                        beepVol=zv==1?Math.max(0,beepVol-10):zv==2?Math.min(200,beepVol+10):70;e.putInt("shift_beep_vol",beepVol);beep.play(soundOn&&soundRoute==0?sound:null,beepSound,beepVol/100f);break;}
                    int z=SettingsLayout.stepZone(x);if(z<0||z!=SettingsLayout.stepZone(downX))break;
                    VehicleProfile prof=DATA.profile();int d=z==1?-100:z==2?100:0;
                    if(i==SettingsLayout.RPM_SHIFT){shiftTarget=d==0?0:ShiftGuide.clamp(ShiftGuide.target(shiftTarget,prof)+d,prof.shiftMin,prof.shiftTop());e.putInt("shift_target",shiftTarget);}
                    else{redline=d==0?0:ShiftGuide.clamp((int)prof.redline+d,prof.redlineMin,prof.redlineMax);VehicleProfile.setRedline(redline);e.putInt("redline",redline);}
                    break;}
                case SettingsLayout.SOUND:
                    if(!SettingsLayout.ENGINE_SOUND)break;
                    if(i==SettingsLayout.SOUND_FAST){EngineSound.fast=!EngineSound.fast;e.putBoolean("engine_sound_fast",EngineSound.fast);sound.play(-1);}   // the block size is read when the thread starts: stop it here, the play() below starts it again
                    else if(i==SettingsLayout.SOUND_ON){boolean was=soundOn;SettingsLayout.floatSound=soundOn&&!SettingsLayout.floatSound;soundOn=!was||SettingsLayout.floatSound;   // 끔 -> 계기판 -> 항상 -> 끔, like 변속 알림음
                        e.putBoolean("float_sound",SettingsLayout.floatSound);}
                    else if(i==SettingsLayout.SOUND_ROUTE)soundRoute=(soundRoute+1)%SettingsLayout.SOUND_ROUTES.length;
                    else if(i==SettingsLayout.SOUND_YIELD){EngineSound.yieldMode=(EngineSound.yieldMode+1)%SettingsLayout.SOUND_YIELDS.length;e.putInt("engine_sound_yield",EngineSound.yieldMode);sound.play(-1);}   // read when the thread starts: restarted below
                    else if(i==SettingsLayout.SOUND_BOOST){EngineSound.boostLevel=(EngineSound.boostLevel+1)%SettingsLayout.SOUND_BOOSTS.length;e.putInt("engine_sound_boost",EngineSound.boostLevel);}
                    else if(i==SettingsLayout.SOUND_ENGINE)soundEngine=(soundEngine+1)%EngineSynth.engines();
                    else if(i==SettingsLayout.SOUND_EXHAUST)soundExhaust=(soundExhaust+1)%EngineSynth.exhausts();
                    else{int z=SettingsLayout.stepZone(x);if(z<0||z!=SettingsLayout.stepZone(downX))break;   // − / +; the label is the default
                        int sv=i-SettingsLayout.SOUND_VOLUME;soundVals[sv]=SettingsLayout.soundStep(i,soundVals[sv],z);e.putInt(SOUND_KEYS[sv],soundVals[sv]);}
                    applySound();
                    e.putBoolean("engine_sound",soundOn).putInt("engine_sound_route",soundRoute).putInt("engine_sound_engine",soundEngine).putInt("engine_sound_exhaust",soundExhaust);
                    sound.play(soundWanted()?soundRoute:-1);break;
                case SettingsLayout.FX:   // sounds that are not the engine: each its own switch, a sample when it goes on
                    if(i==SettingsLayout.FX_VOL){int zv=SettingsLayout.stepZone(x);if(zv<0||zv!=SettingsLayout.stepZone(downX))break;   // − / + 10%; the label puts 70% back
                        SettingsLayout.fxVol=zv==1?Math.max(0,SettingsLayout.fxVol-10):zv==2?Math.min(200,SettingsLayout.fxVol+10):70;e.putInt("fx_vol",SettingsLayout.fxVol);
                        beep.playCoin(soundOn&&soundRoute==0?sound:null,500,SettingsLayout.fxVol/100f);break;}   // a sample clink
                    if(i==SettingsLayout.FX_COIN){EcoCluster.coinSound=!EcoCluster.coinSound;saveEco();   // eco coin sound: plays through the beep channel
                        if(EcoCluster.coinSound&&EcoCluster.coins)beep.playCoin(soundOn&&soundRoute==0?sound:null,500,SettingsLayout.fxVol/100f);break;}   // a sample clink
                    if(i==SettingsLayout.FX_CHIME){SpeedChime.kmh=SpeedChime.next(SpeedChime.kmh);e.putInt("speed_chime_kmh",SpeedChime.kmh);
                        if(SpeedChime.kmh>0)beep.playChime(soundOn&&soundRoute==0?sound:null,SpeedChime.sound,SettingsLayout.fxVol/100f);break;}   // a sample ring
                    if(i==SettingsLayout.FX_CHIME_SOUND){if(SpeedChime.kmh<=0)break;SpeedChime.sound=(SpeedChime.sound+1)%SpeedChime.NAMES.length;e.putInt("speed_chime_sound",SpeedChime.sound);
                        beep.playChime(soundOn&&soundRoute==0?sound:null,SpeedChime.sound,SettingsLayout.fxVol/100f);break;}
                    CassetteCluster.clickSound=!CassetteCluster.clickSound;e.putBoolean("cassette_click",CassetteCluster.clickSound);   // FX_CLICK
                    if(CassetteCluster.clickSound)beep.playCassette(soundOn&&soundRoute==0?sound:null,ShiftBeep.LATCH,SettingsLayout.fxVol/100f);break;   // a sample click
                case SettingsLayout.DIAG:
                    if(i==1){speedCalOn=!speedCalOn;e.putBoolean("speed_cal",speedCalOn);break;}
                    if(i==2){speedCal.reset();e.remove("speed_cal_ratio").remove("speed_cal_samples");break;}
                    if(i==4)break;   // the status row: nothing to tap
                    if(i==5){usbAsk();break;}
                    if(i==6){renderer.usbAtBoot=!renderer.usbAtBoot;e.putBoolean(com.veloster.dashboard.BootReceiver.USB_AT_BOOT,renderer.usbAtBoot);break;}
                    if(i==3){   // the hub app ('내 앱'); a plain launch, nothing is changed
                        android.content.Intent hub=activity.getPackageManager().getLaunchIntentForPackage("com.veloster.carplayhub");
                        if(hub==null)Toast.makeText(activity,UiText.t("'내 앱'이 설치돼 있지 않습니다."),Toast.LENGTH_SHORT).show();
                        else try{activity.startActivity(hub);}catch(RuntimeException ex){Toast.makeText(activity,UiText.t("'내 앱'을 열지 못했습니다."),Toast.LENGTH_SHORT).show();}
                        break;}
                    DtcDialog.show(activity);break;   // read-only trouble codes
            }
            e.apply();invalidate();return true;
        }
        if(picker){   // a tile picks that screen, any other tap closes the list
            if(!tap)return true;
            int i=ClusterPicker.hit(x,y),slot=i==ClusterPicker.hit(downX,downY)?i:-1;
            int[] list=ClusterPicker.slots(order,hidden,false);
            if(slot>=0&&slot<list.length){screen=list[slot];showCluster();picker=false;}
            else{picker=false;editing=false;}
            invalidate();return true;
        }
        // Horizontal swipe on the body switches screens (left = next shown screen, right = previous).
        if(!blocked&&downY>52&&Math.abs(x-downX)>=60&&Math.abs(x-downX)>Math.abs(y-downY)*1.5f){
            screen=x<downX?ClusterPicker.next(order,screen,hidden):ClusterPicker.prev(order,screen,hidden);showCluster();invalidate();return true;
        }
        // 돌아가기 puts the dashboard behind, as the home key does: finish() threw away the run times, the taxi fare and the
        // laps, and with the floating window up left the old screen's engine sound playing beside the new one's
        if(standalone&&tap&&x>=698&&x<=782&&downX>=698&&downX<=782&&y>=8&&y<=46&&downY>=8&&downY<=46){activity.moveTaskToBack(true);return true;}
        if(tap&&!blocked&&!cluster&&!gauges&&!graphs&&!symmetric&&overviewDown>=0&&overviewLayout.hit(x,y)==overviewDown&&!overviewCovered(y)){
            overviewLayout.cycle(overviewDown);
            activity.getSharedPreferences("pit_vehicle_ui",0).edit().putInt("overview_style_"+overviewDown,overviewLayout.style(overviewDown)).apply();
            invalidate();return true;
        }
        if(tap&&!blocked&&symmetric&&page==PitRenderer.PAGE_RECORDS&&y>=60&&y<=250&&downY>=60&&downY<=250){RecordsDialog.show(activity);return true;}   // a run box: 기록실
        if(tap&&!blocked&&onLap(x,y)&&onLap(downX,downY)){   // tap: start / next lap / resume; a hold (lapHold) stops, then clears
            if(!lapHeld)lap.tap(SystemClock.elapsedRealtime());
            invalidate();return true;
        }
        if(tap&&!blocked&&symmetric&&(PitRenderer.isCluster(page)||page==PitRenderer.PAGE_ECO||page==PitRenderer.PAGE_MANUAL||page>PitRenderer.PAGE_TACHO)&&y>52&&downY>52&&renderer.clusterTap(x,y)){int kc=CassetteCluster.consumeKeyCmd();if(kc!=0)requestMedia(kc);if(EcoCluster.consumeChanged())saveEco();
            if(page==PitRenderer.PAGE_MANUAL)activity.getSharedPreferences("pit_vehicle_ui",0).edit().putBoolean("lambda_down_always",LambdaCluster.downAlways).apply();
            if(page>PitRenderer.PAGE_TACHO)activity.getSharedPreferences("pit_vehicle_ui",0).edit().putInt("analog_look",AnalogCluster.style).apply();invalidate();return true;}   // 아날로그: the dial look
        if(tap&&!blocked&&gauges&&x>=650&&x<=788&&downX>=650&&downX<=788&&y>=54&&y<=78&&downY>=54&&downY<=78){gaugeEditor.toggleGrid();return true;}
        if(tap&&!blocked&&graphs&&y>=54&&y<=79&&downY>=54&&downY<=79){
            if(x>=628&&x<=702&&downX>=628&&downX<=702)graphEditor.window(60000);
            else if(x>=708&&x<=782&&downX>=708&&downX<=782)graphEditor.window(300000);
            return true;
        }
        if(tap&&!blocked&&cluster&&!gauges){
            boolean small=renderer.mediaToggleShown()&&x>=18&&x<=70&&downX>=18&&downX<=70&&y>=436&&y<=480&&downY>=436&&downY<=480;
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
            shiftTarget=ShiftGuide.clamp(next,prof.shiftMin,prof.shiftTop());
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
