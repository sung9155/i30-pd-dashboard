package com.veloster.vehicle;

import com.veloster.i18n.UiText;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.Path;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.Bitmap;
import android.graphics.RectF;
import java.util.Locale;

/** Original N/PIT drawing geometry, with only received vehicle samples as values. */
public final class PitRenderer {
    /** Every colour goes through the selected theme (identity for N). */
    private static int T(int c){return Theme.current().map(c);}
    private static int D(){return Theme.current().danger;}
    private final Paint paint=new Paint(3);
    private final KeyPath shape=new KeyPath();   // draw shape.kept(): see KeyPath
    private Shader background;private Theme backgroundTheme;
    /** The page gradient is a Shader built once per theme (a cached Shader would keep the old theme's colours). */
    private Shader background(){
        if(backgroundTheme!=Theme.current()){backgroundTheme=Theme.current();background=new LinearGradient(0,0,800,480,T(0xff111315),T(0xff030405),Shader.TileMode.CLAMP);}
        return background;
    }
    private final android.graphics.RectF cellRect=new android.graphics.RectF();
    private static final int BG=0xff040506,PANEL=0xff101315,LINE=0xff303638,BLUE=0xffa4d9ef,WHITE=0xffe1edf2,MUTED=0xff91a5ae,RED=0xffff4059,AMBER=0xffffb84d;
    private Telemetry.Sample[] samples;
    private int shiftTarget;
    private long frameTime,floatPaneAt=-100000;
    private ShiftGuide.Cue cue;
    private VehicleProfile profile=VehicleProfile.PETROL_DCT;
    private boolean mediaPage,raceMedia;
    private MediaState.Snapshot music;
    private Bitmap artwork;
    private final GaugeRenderer gaugeRenderer=new GaugeRenderer();
    private final GraphRenderer graphRenderer=new GraphRenderer();
    private final PerformanceRenderer performanceRenderer=new PerformanceRenderer();
    private final RaceRenderer raceRenderer=new RaceRenderer();
    private final RaceRenderer.Digits digitW=new RaceRenderer.Digits();   // fittedNumber: digit widths per size
    private final LambdaCluster mtSport=new LambdaCluster();
    private final EcoCluster eco=new EcoCluster();
    private final TachoCluster tacho=new TachoCluster();
    private final ShowRenderer showRenderer=new ShowRenderer();
    private final ClusterRenderer clusterRenderer=new ClusterRenderer();
    public static final int PAGE_PERFORMANCE=1,PAGE_RACE=2,PAGE_RECORDS=3,PAGE_SHOW=4,PAGE_CLASSIC=5,PAGE_RETRO=6,PAGE_RALLY=7;
    /** Pages ClusterRenderer draws: PAGE_CLASSIC and up (layout = page - PAGE_CLASSIC, screen = ClusterPicker.CLASSIC + layout). */
    /** The 수동 screen (LambdaCluster): drawn here, not through the cluster layout list. */
    public static final int PAGE_MANUAL=ClusterPicker.MANUAL-ClusterPicker.F1;
    public static boolean isCluster(int page){return page>=PAGE_CLASSIC&&page<PAGE_MANUAL;}
    /** The 에코 screen (EcoCluster): drawn here like 람다; its taps and the fare hold come through VehicleDash. */
    public static final int PAGE_ECO=ClusterPicker.ECO-ClusterPicker.F1;
    /** The 타코 screen (TachoCluster): drawn here too. */
    public static final int PAGE_TACHO=ClusterPicker.TACHO-ClusterPicker.F1;
    private boolean picker,editing;
    private int hidden;
    /** Screen list over the body while open, its edit mode and the hidden screens (VehicleDash owns the gesture). */
    public void setPicker(boolean open,boolean edit,int hiddenScreens){picker=open;editing=edit;hidden=hiddenScreens;}
    private int[] order=ClusterPicker.naturalOrder();
    private float settingsScroll;
    public void setOrder(int[] o,float scroll){order=o;settingsScroll=scroll;}
    /** Drive-mode options shown on their edit tiles: accent colour by mode, screen by mode. */
    private boolean modeColor,modeScreen;
    public void setModeOptions(boolean color,boolean screen){modeColor=color;modeScreen=screen;}
    private boolean speedCalOn;private float speedCalRatio=Float.NaN;private int speedCalSamples;
    public void setSpeedCal(boolean on,float ratio,int samples){speedCalOn=on;speedCalRatio=ratio;speedCalSamples=samples;}
    /** The installed build's name (v1.16.3, test-1121-abc1234) for the 진단 status row. */
    public String appVersion="";
    /** Settings category open on the left (SettingsLayout) and the saved language choice, for the gear's settings. */
    private int settingsCat,pickSlot=-1;
    private String langSel="auto";
    private int[] modeTargets=ClusterPicker.MODE_DEFAULT.clone();
    /** pick = the ClusterPicker.MODES slot whose screen is being chosen, or -1; targets = each mode's screen (-1 원래 화면). */
    public void setSettings(int cat,String language,int pick,int[] targets){settingsCat=cat;langSel=language;pickSlot=pick;modeTargets=targets;}
    /** Virtual engine sound settings: switch, route (SettingsLayout.SOUND_ROUTES), engine and exhaust, and the − / + values. */
    private boolean soundOn;private int soundRoute,soundEngine,soundExhaust;private int[] soundVals=SettingsLayout.SOUND_DEFAULT;
    private boolean beepOn;private int beepSound,beepVol=70,downScope;private int[] downSounds={-1,-1,-1};
    public void setBeep(boolean on,int sound,int vol,int[] downs,int scope){beepOn=on;beepSound=sound;beepVol=vol;downSounds=downs;downScope=scope;}
    private int downs;private boolean clutchIn;
    /** How many gears down the blip is matched to (RevMatch.downs), or 0; VehicleDash applies the screen scope. */
    public int revMatchDowns(){return downs;}
    /** A 6MT's clutch is in: on the downshift-sound screens the shift beep waits (a downshift blip crosses the target). */
    public boolean clutchIn(){return clutchIn;}
    /** The shift cue of the last frame (rpm at the target, moving, not top gear): the optional beep follows it. */
    public boolean shiftCue(){return cue!=null&&cue.shift;}
    public void setSound(boolean on,int route,int engine,int exhaust,int[] vals){soundOn=on;soundRoute=route;soundEngine=engine;soundExhaust=exhaust;soundVals=vals;}
    /** − / + sound rows from SettingsLayout.SOUND_VOLUME on (the first line is the name, the tap on it the default). */
    private static final String[] SOUND_ROW_NAMES={"음량","엔진음","팝 빈도","터보 휘슬","블로오프","지연 보정"},
        SOUND_ROW_SUBS={null,"0 %면 터보 휘슬 · 블로오프만",null,null,null,null};
    private String soundValue(int row){
        int v=soundVals[row-SettingsLayout.SOUND_VOLUME];
        return row==SettingsLayout.SOUND_LEAD?(v==0?UiText.t("끔"):v+" ms"):v+" %";
    }
    private static String screenName(int s){return s>=0&&s<ClusterPicker.COUNT?ClusterPicker.NAMES[s]:"원래 화면";}
    /** False when the shown layout draws its own track controls (the corner album-art button is hidden). */
    public boolean mediaToggleShown(){return !(symmetric&&isCluster(page)&&clusterRenderer.ownsMediaToggle(page-PAGE_CLASSIC));}
    /** Body tap on a Cluster layout (taxi meter button and the like); true when it was used. */
    public boolean clusterTap(float x,float y){return symmetric&&(isCluster(page)&&clusterRenderer.tap(page-PAGE_CLASSIC,x,y)||page==PAGE_ECO&&eco.tap(x,y)||page==PAGE_MANUAL&&mtSport.tap(x,y));}
    /** The 에코 screen shows (VehicleDash: the fare hold, the coin sound, the frame rate). */
    public boolean ecoShown(){return symmetric&&page==PAGE_ECO;}
    private final HeaderTabs header=new HeaderTabs();
    /** The tab strip VehicleDash hit-tests and scrolls; the same instance draws it. */
    public HeaderTabs header(){return header;}
    private final DriveStats stats=new DriveStats();
    /** Session records, fed every frame whatever tab is shown. */
    public DriveStats stats(){return stats;}
    private boolean symmetric;
    private int page;
    private LapTimer lap=new LapTimer();
    private GraphLayout graphLayout;
    private GaugeLayout gaugeLayout;
    private OverviewLayout overviewLayout=new OverviewLayout();
    private final java.text.SimpleDateFormat clockFormat=new java.text.SimpleDateFormat("HH:mm",Locale.US);
    private long clockMinute=Long.MIN_VALUE;
    private String clockText="";
    private String clock(){
        long now=System.currentTimeMillis(),minute=now/60000;
        if(minute!=clockMinute){clockMinute=minute;clockFormat.setTimeZone(java.util.TimeZone.getDefault());clockText=clockFormat.format(new java.util.Date(now));}
        return clockText;
    }
    public void setArtwork(Bitmap value){artwork=value;ClusterKit.artwork=value;}
    public void setGaugeLayout(GaugeLayout value){gaugeLayout=value;}
    public void setGraphLayout(GraphLayout value){graphLayout=value;}
    public void setSymmetric(boolean value){symmetric=value;}
    /** Performance and race are variants of the symmetric page (symmetric must also be set), so every other tab check stays as is. */
    public void setPage(int value){page=value;}
    public void setLap(LapTimer timer){lap=timer;}
    public void setOverviewLayout(OverviewLayout value){if(value!=null)overviewLayout=value;}
    private static final String[] PREFIX={"엔진 RPM","오일 온도","냉각수 온도","앞 왼쪽 휠","앞 오른쪽 휠","뒤 왼쪽 휠","뒤 오른쪽 휠","속도 km/h","현재 기어","스로틀 %","브레이크 압력 bar","횡가속","종가속","조향각","외기 온도","출력 (계산)","토크 (계산)","부스트 (계산)","앞 왼쪽 공기압","앞 오른쪽 공기압","뒤 왼쪽 공기압","뒤 오른쪽 공기압","TCS","ESC","ABS","배터리 SOC","배터리 전압","조향 속도","e-LSD 수치","흡기 온도"};
    private int index(String prefix){for(int i=0;i<PREFIX.length;i++)if(PREFIX[i].equals(prefix))return i;return -1;}
    private String value(String prefix){int i=index(prefix);
        if(i==8){Telemetry.Sample sel=samples[Telemetry.GEAR_SELECTOR];return Telemetry.gearText(samples[8].valid?samples[8].value:Float.NaN,sel.valid?sel.value:Float.NaN,profile);}   // P / R / N like the other pages: a DCT's gear is not valid there
        if(i<0||i>=samples.length||!samples[i].valid)return "—";
        return Telemetry.format(i==0||i==7||(i>=Telemetry.TPMS_FL&&i<=Telemetry.BAT_SOC)||i==Telemetry.STEERING_SPEED?"%.0f":i==11||i==12?"%+.2f":i==13?"%+.1f":i==17||i==Telemetry.BAT_VOLTAGE?"%.2f":"%.1f",Telemetry.displayValue(i,samples[i].value));}
    private float numeric(String prefix){int i=index(prefix);return i>=0&&i<samples.length&&samples[i].valid?Telemetry.displayValue(i,samples[i].value):0;}
    private boolean changed(String prefix){int i=index(prefix);return i>=0&&i<samples.length&&samples[i].changed;}
    // Signals past the PREFIX list (e.g. THROTTLE_POS) are read by index.
    private String valueAt(int i){if(i<0||i>=samples.length||!samples[i].valid)return "—";return String.format(Locale.US,"%.1f",Telemetry.displayValue(i,samples[i].value));}
    private float numericAt(int i){return i>=0&&i<samples.length&&samples[i].valid?Telemetry.displayValue(i,samples[i].value):0;}
    // Accelerator pedal (signal 9, PV_AV_CAN) is how deep the pedal is pressed; the throttle plate
    // (THROTTLE_POS, TPS) is the actual valve opening. Both are shown; the bar tracks the pedal.
    private void accelThrottle(Canvas c,int x,int width){
        text(c,UiText.t("가속 · 스로틀"),x,420,11,MUTED,true);
        fitted(c,valueAt(9)+" / "+valueAt(Telemetry.THROTTLE_POS)+" %",x+66,422,18,width-66,WHITE);
        box(c,x,432,x+width,438,LINE);
        float fraction=Math.max(0,Math.min(1,numericAt(9)/100));
        if(fraction>0)box(c,x,432,x+width*fraction,438,BLUE);
    }
    public void draw(Canvas c,int width,int height,boolean cluster,Telemetry model,long now){
        draw(c,width,height,cluster,model,now,0);
    }
    public void draw(Canvas c,int width,int height,boolean cluster,Telemetry model,long now,int target){
        draw(c,width,height,cluster,model,now,target,false,null);
    }
    public void draw(Canvas c,int width,int height,boolean cluster,Telemetry model,long now,int target,boolean media,MediaState.Snapshot track){
        mediaPage=cluster && media;raceMedia=media;music=track;
        frameTime=now;samples=model.snapshot(now);profile=model.profile();thModel=model;
        clusterRenderer.tick(samples,now);   // trip totals of the layouts count on every page
        eco.prof=profile;eco.background(samples,now);   // the 에코 trip too
        Glide.rpm(samples,now);   // what is drawn from here on: the trip counts above take the samples as they came
        // The profile's range, so the "목표" label shows the target the cue really uses (a diesel's is 2100-4700).
        shiftTarget=ShiftGuide.target(target,profile);
        cue=ShiftGuide.evaluate(samples,shiftTarget,profile);stats.dct=profile.trans==VehicleProfile.Trans.DCT&&profile.fuel==VehicleProfile.Fuel.PETROL;stats.update(samples,now);
        downs=RevMatch.downs(samples,profile);
        clutchIn=profile.trans==VehicleProfile.Trans.MT&&samples[Telemetry.CLUTCH].valid&&samples[Telemetry.CLUTCH].value>0;
        c.save();c.scale(width/800f,height/480f);
        chrome(c,width,height,cluster);
        if(graphLayout!=null)graphRenderer.draw(c,model,now,graphLayout);
        else if(gaugeLayout!=null)gaugeRenderer.draw(c,model,now,gaugeLayout);
        else if(symmetric&&page==PAGE_PERFORMANCE)performanceRenderer.draw(c,samples,lap,now,profile);
        else if(symmetric&&page==PAGE_SHOW)showRenderer.draw(c,samples,frameTime,profile);
        else if(symmetric&&page==PAGE_RACE)raceRenderer.draw(c,samples,cue,frameTime,stats,profile,media,music,artwork);
        else if(symmetric&&page==PAGE_RECORDS)raceRenderer.drawRecords(c,stats,frameTime);
        else if(symmetric&&page==PAGE_MANUAL){mtSport.shiftTarget=shiftTarget;mtSport.frame(c,samples,model,stats,profile,frameTime,null);}
        else if(symmetric&&page==PAGE_ECO)eco.frame(c,samples,model,stats,profile,frameTime,null);
        else if(symmetric&&page==PAGE_TACHO){tacho.shiftTarget=shiftTarget;tacho.frame(c,samples,model,stats,profile,frameTime,null);}
        else if(symmetric&&isCluster(page))clusterRenderer.draw(c,page-PAGE_CLASSIC,samples,frameTime,stats,profile,model,music);
        else if(mediaPage)drawCombined(c);else if(cluster)drawCluster(c);else drawDashboard(c);
        // Pages without their own shift light (performance, show, every cluster layout) get the race tab's cue from the same
        // shift target: the page edge blinks red (100 ms, as on the race tab).
        if(graphLayout==null&&gaugeLayout==null&&symmetric&&cue.shift&&(page==PAGE_PERFORMANCE||page==PAGE_SHOW||isCluster(page))
            &&(frameTime/100)%2==0){
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(4);paint.setColor(T(RED));c.drawRect(3,55,797,478,paint);
            paint.setStyle(Paint.Style.FILL);
        }
        if(picker)drawPicker(c,screen(cluster));
        else if(graphLayout==null&&!mediaPage && music!=null && music.notice && !showsMusic())drawMedia(c,true);
        if(cluster&&!picker&&gaugeLayout==null&&graphLayout==null&&mediaToggleShown())drawArtworkToggle(c);
        c.restore();
    }
    // Page background and header strip, painted into two bitmaps and repainted only when they change. On Android 4.2's
    // GPU renderer each drawPath (every chamfered tab and its outline: ~50 a frame) is rasterised on the UI thread every
    // frame, and the gradient, 53 scan lines and header fill were redrawn too; a cached bitmap is one texture, uploaded
    // again only after a repaint. eraseColor first marks the pixels changed so the renderer re-uploads them.
    // ponytail: the keys are int hashes; a collision would keep the old strip until the next change.
    private Bitmap body,head;private int bodyKey,headKey;
    /** The dashboard went out of sight: the page and header bitmaps, the screen list's previews and every screen's kept
     *  bitmaps go (about 9 MB after a visit to 에코, RPM and 서킷), and come back as each is drawn again. UI thread. */
    public void trim(){body=ClusterKit.recycle(body);head=ClusterKit.recycle(head);ClusterKit.recycle(thumbs);eco.trim();tacho.trim();clusterRenderer.trim();}
    private static Bitmap canvasBitmap(Bitmap old,int w,int h){
        if(old!=null&&old.getWidth()==w&&old.getHeight()==h){old.eraseColor(0);return old;}
        return Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
    }
    private void chrome(Canvas c,int width,int height,boolean cluster){
        int k=System.identityHashCode(Theme.current());k=k*31+T(BG);k=k*31+T(0xff111315);k=k*31+width;k=k*31+height;
        if(body==null||k!=bodyKey){
            body=canvasBitmap(body,width,height);Canvas b=new Canvas(body);b.scale(width/800f,height/480f);
            b.drawColor(T(BG));paint.setShader(background());b.drawRect(0,0,800,480,paint);paint.setShader(null);
            for(int y=56;y<480;y+=8)stroke(b,0,y,800,y,0x063f4948,1);
            bodyKey=k;
        }
        cellRect.set(0,0,800,480);c.drawBitmap(body,null,cellRect,null);
        // Tab strip: every shown screen is a button; PitRenderer measures the labels and feeds HeaderTabs, which
        // scrolls sideways when they overflow and keeps the current tab in view.
        int[] shownTabs=ClusterPicker.slots(order,hidden,false);int cur=screen(cluster),curTab=-1;
        float[] tabW=new float[shownTabs.length];
        for(int i=0;i<shownTabs.length;i++){tabW[i]=tabWidth(UiText.t(ClusterPicker.NAMES[shownTabs[i]]));if(shownTabs[i]==cur)curTab=i;}
        header.setTabs(shownTabs,tabW,curTab);
        boolean gearOn=picker&&editing;String time=clock(),back=UiText.t("돌아가기");
        int hh=Math.max(1,Math.round(height*52/480f));
        k=System.identityHashCode(Theme.current());k=k*31+T(BLUE);k=k*31+T(0xff233b46);k=k*31+T(MUTED);k=k*31+T(WHITE);k=k*31+T(RED);k=k*31+T(LINE);
        k=k*31+width;k=k*31+hh;k=k*31+(gearOn?1:0);k=k*31+time.hashCode();k=k*31+back.hashCode();k=k*31+curTab;
        k=k*31+(header.moreLeft()?1:0)+(header.moreRight()?2:0);
        for(int i=0;i<header.count();i++){k=k*31+UiText.t(ClusterPicker.NAMES[header.screenAt(i)]).hashCode();
            k=k*31+Float.floatToIntBits(header.x(i));k=k*31+Float.floatToIntBits(header.width(i));}
        if(head==null||k!=headKey){
            head=canvasBitmap(head,width,hh);Canvas h=new Canvas(head);h.scale(width/800f,hh/52f);
            box(h,0,0,800,52,0xff090b0c);box(h,18,51,782,52,LINE);
            if(Theme.current().mStripe){box(h,0,0,267,3,0xff5bc2e7);box(h,267,0,533,3,0xff1c69d4);box(h,533,0,800,3,0xffe22718);}  // BMW M stripe (not in the remap table)
            stroke(h,19,16,13,35,RED,4);stroke(h,27,16,21,35,BLUE,4);
            text(h,"i30 PD",36,33,23,WHITE,true);
            h.save();h.clipRect(HeaderTabs.LEFT,0,HeaderTabs.RIGHT,52);
            for(int i=0;i<header.count();i++)button(h,UiText.t(ClusterPicker.NAMES[header.screenAt(i)]),header.x(i),HeaderTabs.TOP,header.width(i),HeaderTabs.H,i==curTab,BLUE);
            h.restore();
            if(header.moreLeft())arrow(h,HeaderTabs.LEFT+5,-1);
            if(header.moreRight())arrow(h,HeaderTabs.RIGHT-5,1);
            gearButton(h,gearOn);
            centered(h,time,667,32,20,WHITE,true);
            button(h,back,698,8,84,38,false,BLUE);
            headKey=k;
        }
        cellRect.set(0,0,800,52);c.drawBitmap(head,null,cellRect,null);
    }
    /** Screen now showing (ClusterPicker index). */
    private int screen(boolean cluster){
        return graphLayout!=null?ClusterPicker.GRAPHS:gaugeLayout!=null?ClusterPicker.GAUGES:cluster?ClusterPicker.F1:symmetric?ClusterPicker.F1+page:ClusterPicker.OVERVIEW;
    }
    /** Gear left of the clock (HeaderTabs.GEAR_L): opens the screen list in edit mode; lit while editing. */
    private void gearButton(Canvas c,boolean on){
        float x=HeaderTabs.GEAR_L,cx=x+HeaderTabs.GEAR_W/2,cy=HeaderTabs.TOP+HeaderTabs.H/2;int ink=on?BLUE:MUTED;
        button(c,"",x,HeaderTabs.TOP,HeaderTabs.GEAR_W,HeaderTabs.H,on,BLUE);
        for(int i=0;i<8;i++){double a=i*Math.PI/4;stroke(c,cx+(float)Math.cos(a)*6,cy+(float)Math.sin(a)*6,cx+(float)Math.cos(a)*11,cy+(float)Math.sin(a)*11,ink,4);}
        paint.setColor(T(ink));c.drawCircle(cx,cy,8,paint);
        paint.setColor(T(on?0xff233b46:0xff111415));c.drawCircle(cx,cy,3.5f,paint);
    }
    /** Screen list: 6 x 5 tiles of the shown screens over a dimmed body, the current one outlined. The gear opens settings instead. */
    private void drawPicker(Canvas c,int cur){
        if(editing){drawSettings(c);return;}
        box(c,0,52,800,480,0xe6040506);
        int[] list=ClusterPicker.slots(order,hidden,editing);
        for(int i=0;i<list.length;i++){
            int s=list[i];boolean off=(hidden&(1<<s))!=0;
            float l=ClusterPicker.left(i),t=ClusterPicker.top(i),w=ClusterPicker.TILE_W,h=ClusterPicker.TILE_H;
            box(c,l,t,l+w,t+h,s==cur?0xff1c303b:off?0xff08090a:0xff101315);
            if(s==cur){box(c,l,t,l+w,t+3,BLUE);box(c,l,t+h-3,l+w,t+h,BLUE);box(c,l,t,l+3,t+h,BLUE);box(c,l+w-3,t,l+w,t+h,BLUE);}
            centered(c,UiText.t(ClusterPicker.NAMES[s]),l+w/2,t+35,20,off?MUTED:s==cur?BLUE:WHITE,true);
            centered(c,UiText.t(ClusterPicker.DESC[s]),l+w/2,t+58,11,MUTED,false);
        }
    }
    /** Settings (the gear): categories on the left, the open category's choices on the right, the chosen ones outlined. */
    private void drawSettings(Canvas c){
        box(c,0,52,800,480,0xf2040506);
        for(int i=0;i<SettingsLayout.CATS.length;i++){
            float l=SettingsLayout.CAT_L,t=SettingsLayout.catTop(i);boolean on=i==settingsCat,done=i==SettingsLayout.DONE;
            if(done){box(c,l,t,l+SettingsLayout.CAT_W,t+SettingsLayout.CAT_H,BLUE);   // 완료: filled, not a menu item
                centered(c,UiText.t(SettingsLayout.CATS[i]),l+SettingsLayout.CAT_W/2,t+SettingsLayout.CAT_H/2+7,18,DARK_INK,true);continue;}
            box(c,l,t,l+SettingsLayout.CAT_W,t+SettingsLayout.CAT_H,on?0xff1c303b:0xff101315);
            if(on)box(c,l,t,l+4,t+SettingsLayout.CAT_H,BLUE);
            text(c,UiText.t(SettingsLayout.CATS[i]),l+16,t+SettingsLayout.CAT_H/2+7,18,on?BLUE:WHITE,true);
        }
        box(c,SettingsLayout.PANE_L-8,SettingsLayout.TOP,SettingsLayout.PANE_L-7,SettingsLayout.BOTTOM,LINE);
        if(settingsCat==SettingsLayout.SCREENS&&pickSlot<0){drawScreenList(c);return;}
        if(settingsCat==SettingsLayout.FLOAT&&pickSlot<0){drawFloatPane(c);return;}
        int cat=pickSlot>=0?SettingsLayout.PICK:settingsCat;float w=SettingsLayout.itemW(cat),h=SettingsLayout.itemH(cat);
        if(pickSlot>=0)text(c,ClusterPicker.MODE_NAMES[pickSlot]+" "+UiText.t("모드 화면"),SettingsLayout.PANE_L,SettingsLayout.TOP+20,16,BLUE,true);
        boolean paneScroll=SettingsLayout.rowScroll(cat);float paneSc=paneScroll?settingsScroll:0;   // the 사운드 pane scrolls: its rows overflow the pane
        if(paneScroll){c.save();c.clipRect(SettingsLayout.PANE_L-6,SettingsLayout.TOP,800,SettingsLayout.BOTTOM);}
        for(int i=0;i<SettingsLayout.count(cat);i++){
            float l=SettingsLayout.left(cat,i),t=SettingsLayout.top(cat,i)-paneSc;
            if(paneScroll&&(t+h<SettingsLayout.TOP||t>SettingsLayout.BOTTOM))continue;   // scrolled out of view
            String name,sub=null,value=null;boolean sel=false,dim=false;int swatch=0,swatchBg=0xff000000;
            switch(cat){
                case SettingsLayout.SCREENS:dim=(hidden&(1<<i))!=0;name=ClusterPicker.NAMES[i];sub=dim?"미사용":"사용";break;
                case SettingsLayout.THEME:{Theme th=Theme.of(Theme.IDS[i]);sel=th==Theme.current();name=SettingsLayout.THEME_NAMES[i];sub=th.label;swatch=th.accent;swatchBg=th.map(0xff101315);break;}
                case SettingsLayout.PAINT:
                    if(i==CarPaint.IDS.length){name="8비트 하늘";sub="자동: 와이퍼·외기온·낮 전조등";value=Weather.skyLabel();break;}
                    {int pi=SettingsLayout.PAINT_ORDER[i];sel=pi==CarPaint.index();name=CarPaint.LABELS[pi];swatch=CarPaint.color(pi);}break;
                case SettingsLayout.MODE:
                    if(i<SettingsLayout.MODE_FIRST){sel=i==0?modeColor:modeScreen;name=i==0?"모드 색":"모드 화면";
                        sub=i==0?"ECO 초록 · SPORT 주황":"모드를 바꾸면 아래 화면으로";value=sel?"켬":"끔";}
                    else{int k=i-SettingsLayout.MODE_FIRST;name=ClusterPicker.MODE_NAMES[k];dim=!modeScreen;sub="눌러서 화면 고르기";value=screenName(modeTargets[k]);}
                    break;
                case SettingsLayout.PICK:{int s=i<ClusterPicker.COUNT?i:-1;sel=modeTargets[pickSlot]==s;name=screenName(s);
                    if(s>=0&&(hidden&(1<<s))!=0){dim=true;sub="미사용";}break;}
                case SettingsLayout.RPM:
                    if(i==SettingsLayout.RPM_BEEP){sel=beepOn;name="변속 알림음";sub="변속 목표에서 울림 · 항상 = 플로팅 창에서도 · 맞춤 소리 화면에선 클러치 뗐을 때만";value=!beepOn?"끔":SettingsLayout.floatBeep?"항상":"계기판";break;}
                    if(i==SettingsLayout.RPM_BEEP_SOUND){name="알림음 종류";dim=!beepOn;sub="누를 때마다 바뀌고 한 번 들려줌";value=SettingsLayout.BEEP_SOUNDS[beepSound];break;}
                    if(i==SettingsLayout.RPM_BEEP_VOL){name="알림음 음량";dim=!beepOn&&downSounds[0]<0&&downSounds[1]<0&&downSounds[2]<0;sub="− / + 로 조절 · 한 번 들려줌";break;}
                    if(i==SettingsLayout.RPM_DOWN_SCOPE){name="맞춤 소리 화면";sub="다운시프트 맞춤 소리가 나는 화면 · 누르면 바뀜";value=SettingsLayout.DOWN_SCOPES[downScope];break;}
                    if(i>=SettingsLayout.RPM_DOWN1){int k=i-SettingsLayout.RPM_DOWN1,d=downSounds[k];
                        name=k==0?"1단 아래 맞춤 소리":k==1?"2단 아래 맞춤 소리":"3단 아래 맞춤 소리";sub="클러치 밟고 블립이 그 단의 목표 RPM에 맞을 때";
                        dim=d<0;value=d<0?"끔":SettingsLayout.BEEP_SOUNDS[d];break;}
                    name=i==SettingsLayout.RPM_SHIFT?"변속 목표":"레드라인";
                    sub=i==SettingsLayout.RPM_SHIFT?"레이스 SHIFT ▲ · F1 LED · 이름을 누르면 기본값":"SHIFT 경고 · 빨간 구간 · 이름을 누르면 기본값";break;
                case SettingsLayout.SOUND:{
                    if(!SettingsLayout.ENGINE_SOUND){name="엔진 사운드";dim=true;value="사용 안 함";}
                    else if(i==SettingsLayout.SOUND_ON){sel=soundOn;name="엔진 사운드";sub="항상 = 플로팅 창에서도";value=!soundOn?"끔":SettingsLayout.floatSound?"항상":"계기판";}
                    else if(i==SettingsLayout.SOUND_ROUTE){name="출력 경로";dim=!soundOn;value=SettingsLayout.SOUND_ROUTES[soundRoute];}
                    else if(i==SettingsLayout.SOUND_ENGINE){name="엔진";dim=!soundOn;value=SettingsLayout.SOUND_ENGINES[soundEngine];}
                    else if(i==SettingsLayout.SOUND_EXHAUST){name="배기";dim=!soundOn;value=SettingsLayout.SOUND_EXHAUSTS[soundExhaust];}
                    else if(i==SettingsLayout.SOUND_FAST){sel=EngineSound.fast;name="소리 반응 속도";dim=!soundOn;sub="빠름: 소리가 덜 늦음 · 화면이 느려질 때 소리가 끊기면 보통으로";value=EngineSound.fast?"빠름":"보통";}
                    else if(i==SettingsLayout.SOUND_YIELD){int m=EngineSound.yieldMode;sel=m>0;name="안내·통화 양보";dim=!soundOn;
                        sub="내비 안내·전화·음성인식 때 엔진 소리를 줄이거나 멈춤 · 통화는 늘 멈춤";value=SettingsLayout.SOUND_YIELDS[m];}
                    else if(i==SettingsLayout.SOUND_BOOST){int b=EngineSound.boostLevel;sel=b>0;name="속도 따라 키우기";dim=!soundOn;
                        sub="40 km/h부터 110 km/h까지 바람·노면 소음만큼 크게";value=SettingsLayout.SOUND_BOOSTS[b];}
                    else{name=SOUND_ROW_NAMES[i-SettingsLayout.SOUND_VOLUME];sub=SOUND_ROW_SUBS[i-SettingsLayout.SOUND_VOLUME];
                        boolean noTurbo=(i==SettingsLayout.SOUND_TURBO||i==SettingsLayout.SOUND_BOV)&&!SettingsLayout.SOUND_HAS_TURBO[soundEngine];
                        dim=!soundOn||noTurbo;}
                    break;}
                case SettingsLayout.FX:   // sounds that are not the engine: each its own switch (strings kept as they were, for the translations)
                    if(i==SettingsLayout.FX_CLICK){sel=CassetteCluster.clickSound;name="카세트 찰칵 소리";sub="카세트 화면의 건반: 재생·일시정지는 철컥, 곡 넘김은 딸깍 · 엔진 사운드와 별개";value=CassetteCluster.clickSound?"켬":"끔";}
                    else if(i==SettingsLayout.FX_COIN){sel=EcoCluster.coinSound;name="에코 동전 소리";sub="에코 화면에서 동전이 떨어질 때 · 엔진 사운드와 별개";value=EcoCluster.coinSound?"켬":"끔";}
                    else if(i==SettingsLayout.FX_CHIME){int k=SpeedChime.kmh;sel=k>0;name="과속 차임";sub="정한 속도를 넘는 동안 반복해서 울림 (AE86 같은 옛 일본차) · 엔진 사운드와 별개";value=k>0?k+" km/h":"끔";}
                    else if(i==SettingsLayout.FX_VOL){name="효과음 음량";sub="− / + 로 조절 · 동전·카세트·차임 · 변속 알림음은 RPM 화면에서";}
                    else{name="차임 소리";dim=SpeedChime.kmh<=0;sub="누를 때마다 다음 소리를 들려줌";value=SpeedChime.NAMES[SpeedChime.sound];}
                    break;
                case SettingsLayout.LANG:sel=SettingsLayout.LANGS[i].equals(langSel);name=SettingsLayout.LANG_NAMES[i];break;
                default:if(i==1){sel=speedCalOn;name="실제 속도로 표시";value=speedCalOn?"켬":"끔";
                        // plain words: how far the speedometer is off, not the ratio (speedometer = GPS / ratio)
                        if(Float.isNaN(speedCalRatio))sub=String.format(Locale.US,UiText.t("GPS로 속도계 오차 재는 중 · %d %%"),Math.min(99,speedCalSamples*100/SpeedCal.MIN_SAMPLES));
                        else{float off=(1/speedCalRatio-1)*100;
                            sub=String.format(Locale.US,UiText.t(Math.abs(off)<0.05f?"속도계가 실제와 같음":off>0?"속도계가 실제보다 %.1f %% 높게 나옴":"속도계가 실제보다 %.1f %% 낮게 나옴"),Math.abs(off))
                                +UiText.t(speedCalOn?" · 실제 속도로 표시 중":" · 켜면 실제 속도로 표시");}}
                    else if(i==2){name="속도 GPS 보정 값 초기화";sub="잰 값을 지우고 처음부터";dim=speedCalSamples==0;}
                    else if(i==3){name="'내 앱' 열기";sub="버튼 숨김: 내 앱 → 계기판에서 숨김";}
                    else if(i==4){   // what a "no values" report needs, on one row (not a button): app, detected car, CAN, OBD, the head unit's build
                        boolean can=samples[0].valid||samples[7].valid,obd=samples[Telemetry.FUEL_LEVEL].valid||samples[Telemetry.ABS_LOAD].valid||samples[Telemetry.CAT_TEMP].valid;
                        name=appVersion+" · "+UiText.t(profile.fuel==VehicleProfile.Fuel.DIESEL?"디젤":"가솔린")+" · "+UiText.t(profile.trans==VehicleProfile.Trans.MT?"수동 6단":"DCT");
                        sub=UiText.t(can?"CAN 수신 중":"CAN 수신 없음")+" · "+UiText.t(obd?"OBD 응답 있음":"OBD 응답 없음")+" · "+UiText.t("내비 빌드")+" "+android.os.Build.VERSION.INCREMENTAL;dim=!can;}
                    else{name="고장코드 읽기";sub="엔진·변속기·제동·조향·에어백·바디 (읽기만)";}
            }
            box(c,l,t,l+w,t+h,sel?0xff1c303b:dim?0xff08090a:0xff101315);
            if(sel){box(c,l,t,l+w,t+3,BLUE);box(c,l,t+h-3,l+w,t+h,BLUE);box(c,l,t,l+3,t+h,BLUE);box(c,l+w-3,t,l+w,t+h,BLUE);}
            float tx=l+16;
            if(swatch!=0){float s=30,sy=t+(h-s)/2;   // the real colours, not remapped by the current theme
                paint.setColor(0xff303638);c.drawRect(l+14,sy,l+14+s,sy+s,paint);paint.setColor(swatchBg);c.drawRect(l+15,sy+1,l+13+s,sy+s-1,paint);
                paint.setColor(swatch);c.drawRect(l+19,sy+5,l+9+s,sy+s-5,paint);tx=l+56;}
            int ink=dim?MUTED:sel?BLUE:WHITE;
            if(sub==null)text(c,UiText.t(name),tx,t+h/2+7,18,ink,true);
            else{text(c,UiText.t(name),tx,t+h/2-3,18,ink,true);text(c,UiText.t(sub),tx,t+h/2+17,12,cat==SettingsLayout.SCREENS&&!dim?BLUE:MUTED,cat==SettingsLayout.SCREENS);}
            if(cat==SettingsLayout.RPM&&(i<SettingsLayout.RPM_BEEP||i==SettingsLayout.RPM_BEEP_VOL)||cat==SettingsLayout.SOUND&&i>=SettingsLayout.SOUND_VOLUME&&i<=SettingsLayout.SOUND_LEAD||cat==SettingsLayout.FX&&i==SettingsLayout.FX_VOL){   // value, then − and +
                float by=t+(h-SettingsLayout.STEP_H)/2;String v=cat==SettingsLayout.FX?SettingsLayout.fxVol+"%":cat==SettingsLayout.SOUND?soundValue(i):i==SettingsLayout.RPM_BEEP_VOL?beepVol+"%":(i==SettingsLayout.RPM_SHIFT?shiftTarget:(int)profile.redline)+" rpm";
                paint.setTypeface(Typeface.DEFAULT_BOLD);paint.setTextSize(18);text(c,v,SettingsLayout.stepMinusL()-14-paint.measureText(v),t+h/2+7,18,dim?MUTED:BLUE,true);   // a dimmed row (sound off, no turbo) greys its value and buttons too
                stepButton(c,"−",SettingsLayout.stepMinusL(),by,dim);stepButton(c,"+",SettingsLayout.stepPlusL(),by,dim);
            }
            if(value!=null){paint.setTypeface(Typeface.DEFAULT_BOLD);paint.setTextSize(18);String v=UiText.t(value);text(c,v,l+w-20-paint.measureText(v),t+h/2+7,18,sel||(cat==SettingsLayout.MODE&&i>=SettingsLayout.MODE_FIRST||cat==SettingsLayout.SOUND||cat==SettingsLayout.FX||cat==SettingsLayout.RPM)&&!dim?BLUE:MUTED,true);}
        }
        if(paneScroll){c.restore();drawScrollHint(c,settingsScroll,SettingsLayout.maxPaneScroll(cat));}
        if(cat==SettingsLayout.DIAG){   // who made it, under the rows: an APK passed on alone carries no README or LICENSE
            float y=SettingsLayout.top(cat,SettingsLayout.count(cat)-1)+SettingsLayout.itemH(cat)+20;
            fitted(c,UiText.t("i30 PD 포팅")+" · sung9155 · github.com/sung9155/i30-pd-dashboard   "+UiText.t("원작")+" · reikop/veloster-n-dashboard",SettingsLayout.PANE_L+6,y,11,SettingsLayout.PANE_R-SettingsLayout.PANE_L-12,MUTED);}
    }
    // --- 플로팅 pane: preview, switches, button rows, and the cell chooser sheet ---
    private int floatEdit=-1,floatSel,floatEditSig=-1,floatEditGraph,floatEditTab;
    /** edit: the cell whose chooser is open (-1 none); sel: the cell shown in the right box; sig / graph: the chooser's pick. */
    public void setFloatEdit(int edit,int sel,int sig,int graph,int tab){floatEdit=edit;floatSel=sel;floatEditSig=sig;floatEditGraph=graph;floatEditTab=tab;}
    /** A button with its label centred (and a small second line); on = the chosen one. */
    private void chip(Canvas c,float[] b,String label,String sub,boolean on,boolean dim){
        panel(c,b[0],b[1],b[2]-b[0],b[3]-b[1],on?0xff233b46:dim?0xff0a0c0d:0xff111415,on?BLUE:LINE);   // dim: not available now
        if(on){box(c,b[0]+9,b[3]-3,b[2]-9,b[3]-1,BLUE);}
        float cx=(b[0]+b[2])/2,cy=(b[1]+b[3])/2;int ink=dim?0xff566068:on?WHITE:0xffc8d3d8;
        float room=b[2]-b[0]-12;paint.setTypeface(Typeface.DEFAULT_BOLD);paint.setTextSize(14);float ls=Math.min(14,14*room/Math.max(1,paint.measureText(label)));
        if(sub==null)centered(c,label,cx,cy+5,ls,ink,true);
        else{centered(c,label,cx,cy-1,ls,ink,true);paint.setTypeface(Typeface.DEFAULT);paint.setTextSize(11);centered(c,sub,cx,cy+14,Math.min(11,11*room/Math.max(1,paint.measureText(sub))),on?BLUE:MUTED,false);}
    }
    private String floatValue(int sig){
        if(FloatPanel.quad(sig))return samples==null?"—":FloatPanel.inline(samples,sig);
        if(samples==null||sig<0||sig>=samples.length||samples[sig]==null||!samples[sig].valid)return "—";
        String u=Telemetry.UNITS[sig];return Tiles.text(sig,samples[sig].value)+(u==null||u.isEmpty()?"":" "+u);
    }
    private void drawFloatPane(Canvas c){
        if(frameTime-floatPaneAt>1500||frameTime<floatPaneAt)FloatPanel.dayNightReset();floatPaneAt=frameTime;FloatPanel.dayNight(samples,frameTime);   // FloatService is stopped here: judge the side in use ourselves (frameTime = elapsedRealtime); the pane redraws at 1 Hz, a longer gap = it just opened
        boolean off=FloatPanel.shape==FloatPanel.OFF;int shape=off?FloatPanel.lastShape:FloatPanel.shape,n=FloatPanel.count,size=FloatPanel.size;
        float w=FloatPanel.width(shape,n,size),h=FloatPanel.height(shape,n,size);float[][] cells=FloatPanel.cellRects(shape,n,size);
        box(c,SettingsLayout.PANE_L-6,SettingsLayout.TOP,800,480,0xff040506);   // opaque: the dashboard behind must not show between the buttons
        // the preview well: the window as it will look (real size when it fits), the chosen cell outlined
        box(c,SettingsLayout.FW_L,SettingsLayout.FW_T,SettingsLayout.FW_R,SettingsLayout.FW_B,0xff0b0f12);
        stroke(c,SettingsLayout.FW_L,SettingsLayout.FW_T,SettingsLayout.FW_R,SettingsLayout.FW_T,LINE,1);stroke(c,SettingsLayout.FW_L,SettingsLayout.FW_B,SettingsLayout.FW_R,SettingsLayout.FW_B,LINE,1);
        stroke(c,SettingsLayout.FW_L,SettingsLayout.FW_T,SettingsLayout.FW_L,SettingsLayout.FW_B,LINE,1);stroke(c,SettingsLayout.FW_R,SettingsLayout.FW_T,SettingsLayout.FW_R,SettingsLayout.FW_B,LINE,1);
        float k=SettingsLayout.floatPreviewScale(w,h),px=SettingsLayout.floatPreviewX(w,h),py=SettingsLayout.floatPreviewY(w,h);
        if(samples!=null){c.save();c.translate(px,py);c.scale(k,k);FloatPanel.drawStill(c,shape,n,size,FloatPanel.example(),profile,frameTime);c.restore();}
        int sel=Math.min(floatSel,cells.length-1);float[] cr=cells[sel];float sx=px+cr[0]*k,sy=py+cr[1]*k,cw=cr[2]-cr[0],ch=cr[3]-cr[1];
        if(off){box(c,px,py,px+w*k,py+h*k,0xb0040506);centered(c,UiText.t("꺼져 있음"),px+w*k/2,py+h*k/2+6,16,MUTED,true);}
        else{paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2);paint.setColor(T(BLUE));
            if(FloatPanel.round())c.drawCircle(sx+cw*k/2,sy+ch*k/2,cw*k/2,paint);else c.drawRect(sx+2,sy+2,sx+cw*k-2,sy+ch*k-2,paint);paint.setStyle(Paint.Style.FILL);}
        text(c,UiText.t("칸을 누르면 값 바꾸기")+(k<.999f?" · "+UiText.t("축소"):k>1.001f?" · "+UiText.t("확대"):""),SettingsLayout.FW_L+10,SettingsLayout.FW_B-5,11,MUTED,false);
        // right column
        {float[] b=SettingsLayout.F_ON_BOX;String on=UiText.t("플로팅")+"  "+UiText.t(off?"끔":"켬");   // the main switch: filled while on
            if(off)chip(c,b,on,null,false,false);else{box(c,b[0],b[1],b[2],b[3],BLUE);centered(c,on,(b[0]+b[2])/2,(b[1]+b[3])/2+6,15,DARK_INK,true);}}
        boolean ms=FloatPanel.musicStrip&&shape!=FloatPanel.MUSIC;   // the 음악 shape shows the track itself: the strip is dim there
        chip(c,SettingsLayout.F_MUSIC_BOX,UiText.t("음악 띠")+"  "+UiText.t(ms?"켬":"끔"),null,ms&&!off,shape==FloatPanel.MUSIC||off);
        chip(c,SettingsLayout.F_HIDE_BOX,UiText.t("내 앱 버튼 숨김")+"  "+UiText.t(FloatPanel.hideButton?"켬":"끔"),null,FloatPanel.hideButton&&!off,off);
        float[] cb=SettingsLayout.F_CELL_BOX;panel(c,cb[0],cb[1],cb[2]-cb[0],cb[3]-cb[1],0xff101315,LINE);
        int sig=FloatPanel.signal(sel,profile);
        fitted(c,String.format(Locale.US,UiText.t("%d번 칸"),sel+1)+" · "+UiText.t(FloatPanel.name(sig))+(!FloatPanel.graphsUsed()?"":" · "+UiText.t(FloatPanel.GRAPH_NAMES[FloatPanel.graphs[sel]])),cb[0]+12,cb[1]+22,14,cb[2]-cb[0]-24,off?MUTED:WHITE);
        chip(c,SettingsLayout.F_EDIT_BOX,UiText.t("칸 값 바꾸기")+"  ›",null,false,false);
        // button rows
        for(int r=0;r<SettingsLayout.F_ROW_LABELS.length;r++)text(c,UiText.t(SettingsLayout.F_ROW_LABELS[r]),SettingsLayout.PANE_L+6,SettingsLayout.F_ROW_Y[r]+SettingsLayout.F_ROW_H/2+5,14,MUTED,true);
        text(c,UiText.t("칸 수"),SettingsLayout.F_COUNT_X-50,SettingsLayout.F_ROW_Y[0]+SettingsLayout.F_ROW_H/2+5,14,MUTED,true);
        text(c,UiText.t("투명도"),SettingsLayout.F_ALPHA_LABEL_X,SettingsLayout.F_ROW_Y[2]+SettingsLayout.F_ROW_H/2+5,14,MUTED,true);
        slider(c,SettingsLayout.F_SIZE_BAR,SettingsLayout.F_SIZE_KNOB,SettingsLayout.F_SIZES,size,off);
        slider(c,SettingsLayout.F_ALPHA_BAR,SettingsLayout.F_ALPHA_KNOB,SettingsLayout.F_ALPHAS,FloatPanel.alpha,off);
        for(int t:SettingsLayout.F_TARGETS){if(t<SettingsLayout.F_SHAPE||t==SettingsLayout.F_SIZE||t==SettingsLayout.F_ALPHA)continue;int g=t/10*10,i=t-g;String label;boolean on;
            switch(g){case SettingsLayout.F_SHAPE:label=SettingsLayout.F_SHAPES[i];on=shape==i+1;break;
                case SettingsLayout.F_COUNT:label=SettingsLayout.F_COUNTS[i];on=n==FloatPanel.COUNTS[i];break;
                case SettingsLayout.F_SIZE:label=SettingsLayout.F_SIZES[i];on=size==i;break;
                case SettingsLayout.F_CORNER:label=SettingsLayout.F_CORNERS[i];on=FloatPanel.corner==i&&FloatPanel.skinNow()==0;break;
                case SettingsLayout.F_COLOR:label=SettingsLayout.F_COLORS[i];on=FloatPanel.color==i&&(FloatPanel.skinNow()==0||FloatPanel.round());break;
                default:label=SettingsLayout.F_ALPHAS[i];on=FloatPanel.alpha==i;}
            chip(c,SettingsLayout.floatBox(t),UiText.t(label),null,on,off||g==SettingsLayout.F_COUNT&&!FloatPanel.fits(shape,FloatPanel.COUNTS[i]));}   // a count the shape would leave a hole in is dim
        boolean rnd=FloatPanel.round(shape),auto=FloatPanel.skin<0;   // round gauges take the theme's colours; following the screen, the look is not picked here
        chip(c,SettingsLayout.F_AUTO_BOX,UiText.t("계기판 따라")+"  "+UiText.t(auto&&!rnd?"켬":"끔"),null,auto&&!rnd,rnd||off);
        boolean dimL=rnd||auto||off,pick=!rnd&&!auto,day=FloatPanel.dayNow();   // 자동 / 낮 / 밤 and the two looks: the side in use now is lit
        for(int i=0;i<SettingsLayout.F_DN_NAMES.length;i++)chip(c,SettingsLayout.floatBox(SettingsLayout.F_DN+i),UiText.t(SettingsLayout.F_DN_NAMES[i]),null,pick&&FloatPanel.dn==i,dimL);
        if(rnd)chip(c,new float[]{SettingsLayout.F_SKIN_DAY_BOX[0],SettingsLayout.F_SKIN_BOX[1],SettingsLayout.F_SKIN_BOX[2],SettingsLayout.F_SKIN_BOX[3]},UiText.t("원형 게이지는 테마 색"),null,false,true);
        else{chip(c,SettingsLayout.F_SKIN_DAY_BOX,UiText.t("낮")+" "+UiText.t(FloatPanel.SKIN_NAMES[FloatPanel.skinDay])+" ›",null,pick&&day,dimL);
            chip(c,SettingsLayout.F_SKIN_BOX,UiText.t("밤")+" "+UiText.t(FloatPanel.SKIN_NAMES[FloatPanel.skinNight])+" ›",null,pick&&!day,dimL);}
        text(c,UiText.t("손을 떼면 적용 · 끌면 취소"),SettingsLayout.PANE_L+6,SettingsLayout.BOTTOM-4,11,MUTED,false);
        chip(c,SettingsLayout.F_FORM_BOX,UiText.t(FloatPanel.SHAPE_NAMES[shape])+"  ›",null,false,off);
        if(floatEdit==SettingsLayout.SKIN_SHEET||floatEdit==SettingsLayout.SKIN_SHEET_DAY||floatEdit==SettingsLayout.FORM_SHEET)cachedSheet(c,floatEdit);
        else{sheetBmp=null;if(floatEdit>=0)drawFloatSheet(c);}
    }
    /** A bar that snaps to names.length stops: a track with a dot per stop, the knob a chip with the current name. */
    private void slider(Canvas c,float[] bar,float knob,String[] names,int value,boolean dim){
        int n=names.length;float cy=(bar[1]+bar[3])/2;
        box(c,SettingsLayout.sliderX(bar,knob,n,0),cy-2,SettingsLayout.sliderX(bar,knob,n,n-1),cy+2,LINE);
        for(int i=0;i<n;i++){float x=SettingsLayout.sliderX(bar,knob,n,i);box(c,x-3,cy-3,x+3,cy+3,dim?LINE:0xff2a3b44);}
        int v=Math.max(0,Math.min(n-1,value));float kx=SettingsLayout.sliderX(bar,knob,n,v);
        chip(c,new float[]{kx-knob/2,bar[1],kx+knob/2,bar[3]},UiText.t(names[v]),null,!dim,dim);
    }
    /** The 모양 / 색감 sheets draw 17-20 small windows: drawn once into a bitmap and shown from it until something they show changes
     *  (a tap on the sheet redrew every window and lagged). */
    private Bitmap sheetBmp;private int sheetKey;
    private void cachedSheet(Canvas c,int which){
        int key=which;key=key*31+FloatPanel.shape;key=key*31+FloatPanel.lastShape;key=key*31+FloatPanel.count;key=key*31+FloatPanel.size;
        key=key*31+FloatPanel.skin;key=key*31+FloatPanel.skinNow();key=key*31+FloatPanel.corner;key=key*31+FloatPanel.color;key=key*31+FloatPanel.alpha;
        key=key*31+FloatPanel.dn;key=key*31+FloatPanel.skinDay;key=key*31+FloatPanel.skinNight;
        key=key*31+(FloatPanel.musicStrip?1:0);key=key*31+java.util.Arrays.hashCode(FloatPanel.cells);key=key*31+java.util.Arrays.hashCode(FloatPanel.graphs);
        key=key*31+Theme.current().id.hashCode();key=key*31+UiText.language().hashCode();key=key*31+(profile==null?0:profile.hashCode());key=key*31+(samples==null?0:1);
        key=key*31+FloatMusic.sheetKey();   // not the position: the progress bars in the sheet stay where they were drawn
        if(sheetBmp==null||sheetKey!=key){
            if(sheetBmp==null)sheetBmp=Bitmap.createBitmap(800,480,Bitmap.Config.ARGB_8888);else sheetBmp.eraseColor(0);
            Canvas sc=new Canvas(sheetBmp);if(which==SettingsLayout.FORM_SHEET)drawFormSheet(sc);else drawSkinSheet(sc,which==SettingsLayout.SKIN_SHEET_DAY);sheetKey=key;}
        c.drawBitmap(sheetBmp,0,0,null);
    }
    /** 모양 고르기: every shape drawn small with the cells' readings and the skin now; a tap picks it. */
    private void drawFormSheet(Canvas c){
        box(c,0,0,800,480,0xc0000000);
        panel(c,SettingsLayout.F_SH_L,SettingsLayout.SH_T,SettingsLayout.F_SH_R-SettingsLayout.F_SH_L,SettingsLayout.SH_B-SettingsLayout.SH_T,0xff101315,LINE);
        text(c,UiText.t("모양 고르기"),SettingsLayout.F5_X,SettingsLayout.SH_T+30,18,WHITE,true);
        text(c,UiText.t("누르면 바로 적용 · 칸 수는 모양에 맞춰집니다"),SettingsLayout.F5_X+120,SettingsLayout.SH_T+30,11,MUTED,false);
        int cur=FloatPanel.shape==FloatPanel.OFF?FloatPanel.lastShape:FloatPanel.shape,n=FloatPanel.count;
        for(int i=0;i<FloatPanel.FORMS.length;i++){int f=FloatPanel.FORMS[i],fn=FloatPanel.fitCount(f,n);float[] b=SettingsLayout.tile5(SettingsLayout.FORM_SLOTS[i]);boolean on=f==cur;
            panel(c,b[0],b[1],b[2]-b[0],b[3]-b[1],on?0xff1c303b:0xff0b0f12,on?BLUE:LINE);
            text(c,UiText.t(FloatPanel.SHAPE_NAMES[f]),b[0]+10,b[1]+20,13,on?WHITE:MUTED,true);
            if(samples!=null){float fw=FloatPanel.width(f,fn,1),fh=FloatPanel.height(f,fn,1),s=Math.min(1,Math.min((b[2]-b[0]-16)/fw,(b[3]-b[1]-34)/fh));
                c.save();c.translate((b[0]+b[2])/2-fw*s/2,b[1]+28+(b[3]-b[1]-34-fh*s)/2);c.scale(s,s);FloatPanel.drawStill(c,f,fn,1,FloatPanel.example(),profile,frameTime);c.restore();}}
    }
    /** 낮 / 밤 색감 고르기: every skin as a tile with the chosen cell drawn in it; a tap picks it for that side. */
    private void drawSkinSheet(Canvas c,boolean day){
        box(c,0,0,800,480,0xc0000000);
        panel(c,SettingsLayout.SH_L,SettingsLayout.SH_T,SettingsLayout.SH_R-SettingsLayout.SH_L,SettingsLayout.SH_B-SettingsLayout.SH_T,0xff101315,LINE);
        String title=UiText.t(day?"낮 색감 고르기":"밤 색감 고르기");text(c,title,SettingsLayout.K_X,SettingsLayout.SH_T+30,18,WHITE,true);
        float tx=SettingsLayout.K_X+Math.max(120,paint.measureText(title)+12);fitted(c,UiText.t("지금 모양에 입힌 모습 · 누르면 바로 적용"),tx,SettingsLayout.SH_T+30,11,SettingsLayout.SH_R-16-tx,MUTED);   // the hint after the title, however long
        int keep=FloatPanel.skin,keepDn=FloatPanel.dn,cur=day?FloatPanel.skinDay:FloatPanel.skinNight,f=FloatPanel.shape==FloatPanel.OFF?FloatPanel.lastShape:FloatPanel.shape,n=FloatPanel.count;
        try{
        for(int i=0;i<FloatPanel.skinCount();i++){float[] b=SettingsLayout.tile4(i);boolean on=i==cur;
            panel(c,b[0],b[1],b[2]-b[0],b[3]-b[1],on?0xff1c303b:0xff0b0f12,on?BLUE:LINE);
            text(c,UiText.t(FloatPanel.SKIN_NAMES[i]),b[0]+10,b[1]+20,13,on?WHITE:MUTED,true);
            if(samples!=null){FloatPanel.skin=0;FloatPanel.dn=day?DayNight.DAY:DayNight.NIGHT;if(day)FloatPanel.skinDay=i;else FloatPanel.skinNight=i;float fw=FloatPanel.width(f,n,1),fh=FloatPanel.height(f,n,1),sc=Math.min(1,Math.min((b[2]-b[0]-16)/fw,(b[3]-b[1]-34)/fh));
                c.save();c.translate((b[0]+b[2])/2-fw*sc/2,b[1]+28+(b[3]-b[1]-34-fh*sc)/2);c.scale(sc,sc);FloatPanel.drawStill(c,f,n,1,FloatPanel.example(),profile,frameTime);c.restore();}}
        }finally{FloatPanel.skin=keep;FloatPanel.dn=keepDn;if(day)FloatPanel.skinDay=cur;else FloatPanel.skinNight=cur;}
    }
    /** The chooser for one cell: this car's values with their live readings, the graph buttons, 취소 / 완료. */
    private void drawFloatSheet(Canvas c){
        box(c,0,0,800,480,0xc0000000);
        panel(c,SettingsLayout.SH_L,SettingsLayout.SH_T,SettingsLayout.SH_R-SettingsLayout.SH_L,SettingsLayout.SH_B-SettingsLayout.SH_T,0xff101315,LINE);
        text(c,String.format(Locale.US,UiText.t("%d번 칸 값"),floatEdit+1),SettingsLayout.S_TAB_X,SettingsLayout.SH_T+32,18,WHITE,true);
        text(c,UiText.t("작은 글씨: 지금 값 · ▲: 최고값 표시 · 이 차에 없는 값은 숨김"),SettingsLayout.S_TAB_X,SettingsLayout.SH_T+52,11,MUTED,false);
        boolean round=FloatPanel.round();
        if(samples!=null){c.save();c.translate(SettingsLayout.S_CELL_X+(round?62-34:0),SettingsLayout.S_CELL_Y-(round?6:0));if(round)c.scale(.63f,.63f);
            FloatPanel.drawCell(c,FloatPanel.CARD,1,FloatPanel.example(),FloatPanel.resolve(floatEditSig,floatEdit,profile),floatEditGraph,floatEdit,frameTime);c.restore();}
        int[] gs=FloatPanel.groups(profile);int tab=Math.max(0,Math.min(floatEditTab,gs.length-1));
        chip(c,SettingsLayout.sheetTab(0),UiText.t("자동"),UiText.t(FloatPanel.name(FloatPanel.resolve(-1,floatEdit,profile))),floatEditSig<0,false);
        for(int t=0;t<gs.length;t++){float[] b=SettingsLayout.sheetTab(t+1);boolean open=t==tab;   // a group tab: open = its readings are shown
            box(c,b[0],b[1],b[2],b[3],open?0xff1c303b:0xff101315);if(open)box(c,b[0],b[1],b[0]+3,b[3],BLUE);
            boolean mine=floatEditSig>=0&&FloatPanel.groupOf(floatEditSig)==gs[t];
            fitted(c,UiText.t(Tiles.GROUPS[gs[t]])+(mine?" ●":""),b[0]+10,b[1]+20,13,b[2]-b[0]-14,open?WHITE:MUTED);}
        int[] vs=gs.length==0?new int[0]:FloatPanel.group(gs[tab],profile);
        for(int i=0;i<Math.min(vs.length,SettingsLayout.S_MAX_VALUES);i++)chip(c,SettingsLayout.sheetValue(i),UiText.t(FloatPanel.name(vs[i])),floatValue(vs[i])+(FloatPanel.peaks(vs[i])?"  ▲":""),vs[i]==floatEditSig,false);
        if(!round&&!FloatPanel.graphsUsed()){text(c,UiText.t("그래프는 기본 스킨의 카드·띠에서만 고릅니다"),SettingsLayout.S_GRAPH_X,SettingsLayout.S_GRAPH_Y+SettingsLayout.S_GRAPH_H/2+5,13,MUTED,false);}
        else if(round){text(c,UiText.t("그래프는 기본 스킨의 카드·띠에서만 고릅니다"),SettingsLayout.S_GRAPH_X,SettingsLayout.S_GRAPH_Y+SettingsLayout.S_GRAPH_H/2+5,13,MUTED,false);}
        else for(int g=0;g<FloatPanel.GRAPH_NAMES.length;g++){float[] b=SettingsLayout.sheetGraph(g);boolean on=g==floatEditGraph;
            panel(c,b[0],b[1],b[2]-b[0],b[3]-b[1],on?0xff233b46:0xff111415,on?BLUE:LINE);
            if(on)box(c,b[0]+9,b[3]-3,b[2]-9,b[3]-1,BLUE);
            float il=b[0]+12,ir=b[0]+44,cy=(b[1]+b[3])/2;String nm=UiText.t(FloatPanel.GRAPH_NAMES[g]);int ink=on?WHITE:0xffc8d3d8;
            if(g==FloatPanel.BAR){box(c,il,cy-3,ir,cy+3,0xff2a343b);box(c,il,cy-3,il+(ir-il)*.6f,cy+3,BLUE);}
            else if(g==FloatPanel.LINE){shape.reset();float[] ys={.7f,.5f,.6f,.3f,.45f,.2f,.35f};
                for(int j=0;j<ys.length;j++){float xx=il+j*(ir-il)/(ys.length-1),yy=cy-10+ys[j]*20;if(j==0)shape.moveTo(xx,yy);else shape.lineTo(xx,yy);}
                paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2);paint.setColor(T(BLUE));c.drawPath(shape.kept(),paint);paint.setStyle(Paint.Style.FILL);}
            else if(g==FloatPanel.LED){float sw=(ir-il)/8;for(int j=0;j<8;j++)box(c,il+j*sw+1,cy-4,il+(j+1)*sw-1,cy+4,j<5?BLUE:0xff2a343b);}
            if(g==FloatPanel.NUM)centered(c,nm,(b[0]+b[2])/2,cy+5,14,ink,true);else fitted(c,nm,ir+8,cy+5,14,b[2]-ir-14,ink);}
        chip(c,SettingsLayout.S_CANCEL_BOX,UiText.t("취소"),null,false,false);
        chip(c,SettingsLayout.S_DONE_BOX,UiText.t("완료"),null,true,false);
    }
    private static final int DARK_INK=0xff04121a;
    /** A pane that overflows: a scroll bar right of the pane, a fade and "▼ 아래에 더 있음" while rows are below,
     *  a fade at the top once scrolled. */
    private int fadeBg;private LinearGradient fadeDown,fadeUp;
    private void drawScrollHint(Canvas c,float scroll,float max){
        if(max<=0)return;
        float L=SettingsLayout.PANE_L,R=SettingsLayout.PANE_R,top=SettingsLayout.TOP,bot=SettingsLayout.BOTTOM,h=bot-top,vis=Math.max(32,h*h/(max+h)),y=top+(h-vis)*(scroll/max);
        box(c,791,top,797,bot,0xff0f1518);box(c,791,y,797,y+vis,BLUE);
        int bg=T(0xff040506),clear=bg&0x00ffffff;
        if(fadeBg!=bg){fadeBg=bg;fadeDown=new LinearGradient(0,bot-56,0,bot,clear,bg,Shader.TileMode.CLAMP);fadeUp=new LinearGradient(0,top,0,top+20,bg,clear,Shader.TileMode.CLAMP);}   // made once per theme, not per frame
        if(scroll<max-1){paint.setShader(fadeDown);c.drawRect(L,bot-56,R,bot,paint);paint.setShader(null);
            centered(c,UiText.t("▼ 아래에 더 있음"),(L+R)/2,bot-8,13,BLUE,true);}
        if(scroll>1){paint.setShader(fadeUp);c.drawRect(L,top,R,top+20,paint);paint.setShader(null);}
    }
    /** A 화면 row being dragged by its grip: its display position (-1 none) and the finger's y. */
    private int dragRow=-1;private float dragY;
    public void setScreenDrag(int row,float y){dragRow=row;dragY=y;}
    /** The 화면 category as a reorderable, scrollable list: a mini preview, the name, 사용/미사용, and ▲▼ to move it. */
    private void drawScreenList(Canvas c){
        int[] ord=ClusterPicker.validOrder(order);
        float L=SettingsLayout.PANE_L,R=SettingsLayout.PANE_R,rh=SettingsLayout.SCREEN_ROW_H,top=SettingsLayout.TOP,bot=SettingsLayout.BOTTOM;
        boolean drag=dragRow>=0&&dragRow<ord.length;
        c.save();c.clipRect(L-6,top,800,bot);
        for(int p=0;p<ord.length;p++){
            float t=top+p*rh-settingsScroll;
            if(t+rh<top||t>bot)continue;
            if(drag&&p==dragRow){box(c,L,t+4,R,t+rh-4,0xff08090a);continue;}   // the lifted row's empty place
            screenRow(c,p,ord.length,ord[p],t,0,false);
        }
        if(drag){int to=SettingsLayout.dropRow(dragY,settingsScroll);float ly=top+(to>dragRow?to+1:to)*rh-settingsScroll;
            box(c,L,ly-2,R,ly+1,BLUE);   // where it lands
            screenRow(c,dragRow,ord.length,ord[dragRow],dragY-rh/2,8,true);}
        c.restore();
        drawScrollHint(c,settingsScroll,SettingsLayout.maxScreenScroll());
    }
    /** One 화면 row at y t (shifted dx): grip, mini preview, name and description, 사용/미사용, ▲▼. lifted = being dragged. */
    private void screenRow(Canvas c,int p,int n,int s,float t,float dx,boolean lifted){
        float L=SettingsLayout.PANE_L+dx,R=SettingsLayout.PANE_R+dx,rh=SettingsLayout.SCREEN_ROW_H;
        boolean off=(hidden&(1<<s))!=0;
        box(c,L,t+4,R,t+rh-4,lifted?0xff1c303b:off?0xff08090a:0xff101315);
        if(lifted){box(c,L,t+4,R,t+6,BLUE);box(c,L,t+rh-6,R,t+rh-4,BLUE);box(c,L,t+4,L+2,t+rh-4,BLUE);box(c,R-2,t+4,R,t+rh-4,BLUE);}
        for(int k=-1;k<=1;k++)box(c,L+5,t+rh/2+k*6-1,L+19,t+rh/2+k*6+1,BLUE);   // the grip
        float tx0=L+24,tw=SettingsLayout.THUMB_W-28,th=rh-18;
        Bitmap tb=thumb(s);
        if(tb!=null){cellRect.set(tx0,t+9,tx0+tw,t+9+th);c.drawBitmap(tb,null,cellRect,paint);}
        else box(c,tx0,t+9,tx0+tw,t+9+th,0xff05070a);
        float tx=L+SettingsLayout.THUMB_W+8;int ink=off?MUTED:WHITE;
        text(c,UiText.t(ClusterPicker.NAMES[s]),tx,t+rh/2-3,17,ink,true);
        text(c,UiText.t(ClusterPicker.DESC[s]),tx,t+rh/2+16,11,MUTED,false);
        String use=UiText.t(off?"미사용":"사용");paint.setTypeface(Typeface.DEFAULT_BOLD);paint.setTextSize(13);
        text(c,use,SettingsLayout.upBtnL()+dx-12-paint.measureText(use),t+rh/2+5,13,off?MUTED:BLUE,true);
        float by=t+(rh-SettingsLayout.SBTN_W)/2;
        moveBtn(c,SettingsLayout.upBtnL()+dx,by,true,p>0);
        moveBtn(c,SettingsLayout.downBtnL()+dx,by,false,p<n-1);
    }
    private void moveBtn(Canvas c,float x,float y,boolean up,boolean on){
        float w=SettingsLayout.SBTN_W;box(c,x,y,x+w,y+w,on?0xff16232b:0xff0c0e10);
        float cx=x+w/2,cy=y+w/2;paint.setColor(T(on?BLUE:0xff2a3036));
        shape.reset();
        if(up){shape.moveTo(cx,cy-7);shape.lineTo(cx-8,cy+5);shape.lineTo(cx+8,cy+5);}
        else{shape.moveTo(cx,cy+7);shape.lineTo(cx-8,cy-5);shape.lineTo(cx+8,cy-5);}
        shape.close();c.drawPath(shape.kept(),paint);
    }
    // Mini previews for the screen list: each layout rendered once at thumbnail size and cached, rebuilt when the
    // car paint or theme changes (so the 8-bit and themed previews stay right).
    private static final int TW=96,TH=54;
    private final Bitmap[] thumbs=new Bitmap[ClusterPicker.COUNT];
    private Telemetry thModel;
    private final GaugeLayout thGauge=new GaugeLayout();
    private final GraphLayout thGraph=new GraphLayout();
    private int thumbPaint=-1;private Theme thumbTheme;
    /** Mini preview of a screen: the real layout rendered small, cached. Null if it could not be drawn. */
    private Bitmap thumb(int s){
        if(s<0||s>=thumbs.length||thModel==null)return null;
        if(thumbPaint!=CarPaint.index()||thumbTheme!=Theme.current()){for(int i=0;i<thumbs.length;i++)thumbs[i]=null;thumbPaint=CarPaint.index();thumbTheme=Theme.current();}
        if(thumbs[s]==null){
            try{
                Bitmap b=Bitmap.createBitmap(TW,TH,Bitmap.Config.ARGB_8888);
                Canvas tc=new Canvas(b);tc.drawColor(T(BG));
                tc.scale(TW/800f,TH/428f);tc.translate(0,-52);   // show the body (y 52..480) only
                drawBodyFor(tc,s);
                thumbs[s]=b;
            }catch(Throwable e){thumbs[s]=null;}
        }
        return thumbs[s];
    }
    /** Draw a given screen's body (no header) for its thumbnail, using the current frame's data. */
    private void drawBodyFor(Canvas c,int s){
        if(s==ClusterPicker.GRAPHS)graphRenderer.draw(c,thModel,frameTime,thGraph);
        else if(s==ClusterPicker.GAUGES)gaugeRenderer.draw(c,thModel,frameTime,thGauge);
        else if(s==ClusterPicker.F1)drawCluster(c);
        else if(s==ClusterPicker.OVERVIEW)drawDashboard(c);
        else{int pg=s-ClusterPicker.F1;
            if(pg==PAGE_PERFORMANCE)performanceRenderer.draw(c,samples,null,frameTime,profile);
            else if(pg==PAGE_SHOW)showRenderer.draw(c,samples,frameTime,profile);
            else if(pg==PAGE_RACE)raceRenderer.draw(c,samples,cue,frameTime,stats,profile,false,null,null);
            else if(pg==PAGE_RECORDS)raceRenderer.drawRecords(c,stats,frameTime);
            else if(pg==PAGE_MANUAL)mtSport.frame(c,samples,thModel,stats,profile,frameTime,null);
            else if(pg==PAGE_ECO)eco.frame(c,samples,thModel,stats,profile,frameTime,null);
            else if(pg==PAGE_TACHO){tacho.shiftTarget=shiftTarget;tacho.frame(c,samples,thModel,stats,profile,frameTime,null);}
            else if(isCluster(pg))clusterRenderer.draw(c,pg-PAGE_CLASSIC,samples,frameTime,stats,profile,thModel,null);
        }
    }
    /** Pixel width of a header tab: its label plus side padding, with a sensible minimum. */
    /** Tab widths by label: every page measured all 26 tab labels every frame. ponytail: grows by a language's labels only. */
    private final java.util.HashMap<String,Float> tabW0=new java.util.HashMap<String,Float>();
    private float tabWidth(String label){Float w=tabW0.get(label);
        if(w==null){paint.setTypeface(Typeface.DEFAULT_BOLD);paint.setTextSize(14);w=Math.max(56f,paint.measureText(label)+24f);tabW0.put(label,w);}
        return w;}
    /** Faint chevron at a strip edge that has more tabs past it; dir -1 points left. */
    private void arrow(Canvas c,float x,int dir){
        box(c,x-7,9,x+7,45,0xff090b0c);
        shape.reset();shape.moveTo(x+dir*4,27);shape.lineTo(x-dir*3,20);shape.lineTo(x-dir*3,34);shape.close();
        paint.setColor(T(0xcc91a5ae));c.drawPath(shape.kept(),paint);
    }
    private void box(Canvas c,float l,float t,float r,float b,int color){paint.setColor(T(color));c.drawRect(l,t,r,b,paint);}
    private void panel(Canvas c,float x,float y,float w,float h,int fill,int edge){
        if(ThemeShapes.cell(c,paint,shape,cellRect,x,y,x+w,y+h,T(fill),T(edge)))return;
        shape.reset();shape.moveTo(x,y);shape.lineTo(x+w-12,y);shape.lineTo(x+w,y+12);
        shape.lineTo(x+w,y+h);shape.lineTo(x+12,y+h);shape.lineTo(x,y+h-12);shape.close();
        paint.setColor(T(fill));c.drawPath(shape.kept(),paint);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1);paint.setColor(T(edge));c.drawPath(shape.kept(),paint);paint.setStyle(Paint.Style.FILL);
    }
    private void stroke(Canvas c,float x,float y,float x2,float y2,int color,float width){
        paint.setColor(T(color));paint.setStrokeWidth(width);c.drawLine(x,y,x2,y2,paint);
    }
    private void text(Canvas c,String s,float x,float y,float size,int color,boolean bold){
        paint.setColor(T(color));paint.setTextSize(size);paint.setTypeface(bold?Typeface.DEFAULT_BOLD:Typeface.DEFAULT);c.drawText(s,x,y,paint);
    }
    private void fitted(Canvas c,String s,float x,float y,float size,float width,int color){
        paint.setTypeface(Typeface.DEFAULT);paint.setTextSize(size);
        float measured=paint.measureText(s);
        text(c,s,x,y,measured>width?size*width/measured:size,color,false);
    }
    /** Left-aligned numeric readout with a fixed digit cell, so a narrow 1 in the middle does not shift the other digits (no jitter). */
    private void fittedNumber(Canvas c,String s,float x,float y,float size,float width,int color){
        float[] d=digitW.of(paint,size,false);float dw=d[10];
        float total=0;for(int k=0;k<s.length();k++){char ch=s.charAt(k);total+=ch>='0'&&ch<='9'?dw:paint.measureText(String.valueOf(ch));}
        if(total>width&&total>0){d=digitW.of(paint,size*width/total,false);dw=d[10];}
        paint.setColor(T(color));float cx=x;
        for(int k=0;k<s.length();k++){char ch=s.charAt(k);String g=String.valueOf(ch);
            if(ch>='0'&&ch<='9'){c.drawText(g,cx+(dw-d[ch-'0'])/2,y,paint);cx+=dw;}else{float gw=paint.measureText(g);c.drawText(g,cx,y,paint);cx+=gw;}}
    }
    private void ellipsis(Canvas c,String s,float x,float y,float size,float width,int color){
        paint.setTypeface(Typeface.DEFAULT);paint.setTextSize(size);
        if(paint.measureText(s)>width){
            int lo=0,hi=s.length();while(lo<hi){int m=(lo+hi+1)/2;if(paint.measureText(s.substring(0,m)+"…")<=width)lo=m;else hi=m-1;}int end=lo;   // binary search
            if(end>0 && Character.isHighSurrogate(s.charAt(end-1)))end--;
            s=s.substring(0,end)+"…";
        }
        text(c,s,x,y,size,color,false);
    }
    /** The page already shows the track (race tab music panel, taxi radio, cassette): no track-change bar over it. */
    private boolean showsMusic(){
        return gaugeLayout==null&&symmetric&&(page==PAGE_RACE&&raceMedia||isCluster(page)&&clusterRenderer.showsMusic(page-PAGE_CLASSIC));
    }
    private void drawMedia(Canvas c,boolean notice){
        long age=music==null?0:music.noticeAgeMs;
        float phase=Math.min(1f,Math.min(age/250f,(5000-age)/250f));
        phase=Math.max(0,phase);phase=1-(1-phase)*(1-phase);
        float top=347+133*(1-phase);
        panel(c,18,top,764,101,0xff12212a,BLUE);
        cover(c,30,top+12,76);
        String source=music==null||music.source.length()==0?"MEDIA":music.source;
        text(c,source,120,top+18,11,BLUE,true);
        String title=music==null||music.title.length()==0?"—":music.title;
        ellipsis(c,title,120,top+43,21,644,WHITE);
        String artist=music==null||music.artist.length()==0?"—":music.artist;
        ellipsis(c,artist,120,top+64,13,480,MUTED);
        if(music!=null && music.hasProgress()){
            String times=MediaState.time(music.positionMs)+" / "+MediaState.time(music.durationMs);
            text(c,times,622,top+64,12,WHITE,false);
            box(c,120,top+79,764,top+83,LINE);
            box(c,120,top+79,120+644*music.progress(),top+83,BLUE);
        }
    }
    private void cover(Canvas c,float x,float y,float size){
        box(c,x,y,x+size,y+size,0xff1c303b);
        if(artwork!=null){
            float scale=Math.min(size/artwork.getWidth(),size/artwork.getHeight());
            float w=artwork.getWidth()*scale,h=artwork.getHeight()*scale;
            c.drawBitmap(artwork,null,new RectF(x+(size-w)/2,y+(size-h)/2,x+(size+w)/2,y+(size+h)/2),paint);
        }else{
            centered(c,"♪",x+size/2,y+size*.61f,size*.35f,BLUE,true);
            if(size>100)centered(c,"ALBUM ART",x+size/2,y+size*.84f,11,MUTED,false);
        }
    }
    private void drawArtworkToggle(Canvas c){
        panel(c,18,436,52,44,mediaPage?0xff233b46:PANEL,mediaPage?BLUE:LINE);
        cover(c,24,438,40);
        text(c,music==null||music.source.length()==0?UiText.t("음악"):music.source,80,451,11,BLUE,true);
        ellipsis(c,music==null||music.title.length()==0?"♪":music.title,80,472,13,120,MUTED);
    }
    private void drawCombined(Canvas c){
        panel(c,18,60,464,388,PANEL,LINE);
        text(c,cue.shift?"SHIFT ↑":"SHIFT / LED",30,83,11,cue.shift?RED:MUTED,true);
        for(int i=0;i<15;i++){
            boolean lit=cue.shift?(frameTime/250)%2==0:i<cue.leds;
            paint.setColor(T(lit?(i<5?BLUE:i<10?WHITE:RED):0xff252a2c));
            c.drawCircle(36+i*30.5f,105,7,paint);
        }
        float rpm=Math.max(0,Math.min(profile.maxRpm,numeric("엔진 RPM")));
        box(c,30,124,470,127,LINE);if(rpm>0)box(c,30,124,30+440*rpm/profile.maxRpm,127,BLUE);
        box(c,180,143,316,340,BLUE);
        centered(c,"EMS GEAR",248,164,11,BG,true);
        {String g=value("현재 기어");paint.setTypeface(Typeface.DEFAULT_BOLD);paint.setTextSize(130);float gw=paint.measureText(g);
            float sz=gw>116?130*116/gw:130;   // "M7" in the manual gate ran past the 136-wide box: two characters shrink to fit, one stays 130
            centered(c,g,248,290-(130-sz)*0.36f,sz,BG,true);}
        centered(c,"HYUNDAI i30",248,324,10,BG,true);
        centered(c,"SPEED",98,177,12,MUTED,true);
        centeredNumber(c,value("속도 km/h"),98,253,57,WHITE,true);
        centered(c,"km/h",98,280,12,MUTED,false);
        centered(c,"ENGINE",398,177,12,MUTED,true);
        centeredNumber(c,value("엔진 RPM"),398,253,46,WHITE,true);
        centered(c,"rpm",398,280,12,MUTED,false);
        text(c,"INTAKE",32,372,11,MUTED,true);text(c,value("흡기 온도")+" °C",116,375,22,WHITE,true);
        text(c,"COOLANT",258,372,11,MUTED,true);text(c,value("냉각수 온도")+" °C",368,375,22,WHITE,true);
        accelThrottle(c,32,208);
        compactPedal(c,UiText.t("브레이크"),"브레이크 압력 bar","bar",258,212,RED,100);
        panel(c,494,60,288,388,0xff12212a,LINE);
        cover(c,518,72,240);
        text(c,music==null||music.source.length()==0?"MEDIA":music.source,510,333,12,BLUE,true);
        ellipsis(c,music==null||music.title.length()==0?"—":music.title,510,362,21,256,WHITE);
        ellipsis(c,music==null||music.artist.length()==0?"—":music.artist,510,386,14,256,MUTED);
        if(music!=null && music.hasProgress()){
            text(c,MediaState.time(music.positionMs)+" / "+MediaState.time(music.durationMs),510,413,13,WHITE,false);
            box(c,510,428,766,432,LINE);box(c,510,428,510+256*music.progress(),432,BLUE);
        }
        button(c,"−",210,452,42,27,false,BLUE);text(c,UiText.t("목표 ")+shiftTarget+" rpm",267,471,12,BLUE,true);
        button(c,"+",440,452,42,27,false,BLUE);
        button(c,UiText.t("이전"),494,452,86,27,false,BLUE);
        button(c,music!=null&&music.playState==1?"Ⅱ":music!=null&&music.playState==0?"▶":"▶ / Ⅱ",588,452,98,27,false,BLUE);
        button(c,UiText.t("다음"),694,452,88,27,false,BLUE);
    }
    private void compactPedal(Canvas c,String label,String prefix,String unit,int x,int width,int color,float maximum){
        text(c,label,x,420,11,MUTED,true);
        fitted(c,value(prefix)+" "+unit,x+66,422,18,width-66,WHITE);
        box(c,x,432,x+width,438,LINE);
        float fraction=Math.max(0,Math.min(1,numeric(prefix)/maximum));
        if(fraction>0)box(c,x,432,x+width*fraction,438,color);
    }
    /** A settings − / + button: wide, the sign large and centred in the accent (the small grey sign was easy to miss). */
    private void stepButton(Canvas c,String s,float x,float y,boolean dim){
        float w=SettingsLayout.STEP_W,h=SettingsLayout.STEP_H;panel(c,x,y,w,h,0xff141a1d,dim?LINE:BLUE);
        centered(c,s,x+w/2,y+h/2+9,26,dim?MUTED:BLUE,true);
    }
    private void button(Canvas c,String s,float x,float y,float w,float h,boolean on,int accent){
        panel(c,x,y,w,h,on?0xff233b46:0xff111415,on?accent:LINE);
        if(on)box(c,x+9,y+h-3,x+w-9,y+h-1,accent);
        paint.setTypeface(Typeface.DEFAULT_BOLD);paint.setTextSize(14);float measured=paint.measureText(s);
        text(c,s,x+12,y+h/2+5,measured>w-24?14*(w-24)/measured:14,on?accent:MUTED,true);
    }
    private void centered(Canvas c,String s,float x,float y,float size,int color,boolean bold){
        paint.setTextSize(size);paint.setTypeface(bold?Typeface.DEFAULT_BOLD:Typeface.DEFAULT);
        text(c,s,x-paint.measureText(s)/2,y,size,color,bold);
    }
    /** Start-up welcome sweep, drawn on top of the live dashboard for t in 0..1 then gone: a dark cover that fades
     * out, an accent wipe left to right and the i30 PD badge easing in. Pure overlay; it never blocks the data. */
    public void drawIntro(Canvas c,float t){
        float cover=t<0.72f?1f:Math.max(0f,1f-(t-0.72f)/0.28f);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(T((((int)(cover*255))&0xff)<<24|0x060a0e));c.drawRect(0,0,800,480,paint);
        float sp=Math.min(1f,t/0.55f),se=1f-(1f-sp)*(1f-sp),x=800*se;
        if(sp<1f){
            paint.setColor(T((((int)(cover*36))&0xff)<<24|0x5bc8f5));c.drawRect(0,0,x,480,paint);
            paint.setColor(T((((int)(cover*210))&0xff)<<24|0x5bc8f5));c.drawRect(x-3,0,x+1,480,paint);
        }
        float lp=Math.max(0f,Math.min(1f,(t-0.2f)/0.5f)),le=1f-(1f-lp)*(1f-lp);
        int la=((int)(le*cover*255)&0xff)<<24;
        float scale=0.72f+0.28f*le,size=60*scale,cy=244+(1f-le)*12;
        paint.setTypeface(Typeface.DEFAULT_BOLD);paint.setTextSize(size);
        float tw=paint.measureText("i30 PD"),sx=400-(tw+34*scale)/2;
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeWidth(6*scale);
        paint.setColor(T(la|0xff3b30));c.drawLine(sx,cy+14*scale,sx+13*scale,cy-16*scale,paint);
        paint.setColor(T(la|0x4aa6ff));c.drawLine(sx+13*scale,cy+14*scale,sx+26*scale,cy-16*scale,paint);
        paint.setStyle(Paint.Style.FILL);paint.setStrokeCap(Paint.Cap.BUTT);
        text(c,"i30 PD",sx+34*scale,cy+size*0.34f,size,la|0xffffff,true);
        if(le>0.55f){paint.setTextSize(13);float sw=paint.measureText("D A S H B O A R D");
            int sa=((int)((le-0.55f)/0.45f*cover*150)&0xff)<<24;
            text(c,"D A S H B O A R D",400-sw/2,cy+size*0.34f+28,13,sa|0xbcd0d8,false);}
    }
    /** Centered numeric readout with a fixed digit cell, so a narrow 1 does not make the whole value jitter (F1 RPM/speed). */
    private void centeredNumber(Canvas c,String s,float cx,float y,float size,int color,boolean bold){
        paint.setTypeface(bold?Typeface.DEFAULT_BOLD:Typeface.DEFAULT);paint.setTextSize(size);paint.setColor(T(color));paint.setTextAlign(Paint.Align.LEFT);
        float dw=0;for(char d='0';d<='9';d++)dw=Math.max(dw,paint.measureText(String.valueOf(d)));
        float total=0;for(int k=0;k<s.length();k++){char ch=s.charAt(k);total+=ch>='0'&&ch<='9'?dw:paint.measureText(String.valueOf(ch));}
        float x=cx-total/2;
        for(int k=0;k<s.length();k++){char ch=s.charAt(k);String g=String.valueOf(ch);float gw=paint.measureText(g);
            if(ch>='0'&&ch<='9'){c.drawText(g,x+(dw-gw)/2,y,paint);x+=dw;}else{c.drawText(g,x,y,paint);x+=gw;}}
    }
    // F1 accelerator card: big pedal number (signal 9), with the actual throttle plate (TPS) beside it.
    private void accelPedal(Canvas c,int x){
        panel(c,x,377,376,57,0xff111516,LINE);
        text(c,"ACCEL",x+14,396,10,MUTED,true);
        text(c,UiText.t("스로틀")+" "+valueAt(Telemetry.THROTTLE_POS)+"%",x+232,396,10,MUTED,true);
        fitted(c,valueAt(9),x+14,423,25,85,WHITE);
        text(c,"%",x+105,422,11,MUTED,false);
        float fraction=Math.max(0,Math.min(1,numericAt(9)/100));
        for(int i=0;i<20;i++)box(c,x+148+i*10.6f,403,x+155+i*10.6f,421,fraction>i/20f?BLUE:LINE);
    }
    /** One of F1's two lines under speed and rpm: caption, value, unit; several-value entries in a smaller line. */
    private void f1Tile(Canvas c,int i,float capX,float valX,float unitX){
        int sig=Tiles.signal(Tiles.F1,i,profile);String txt;int lv=0;float size=22;
        if(sig==Tiles.TIRES){StringBuilder b=new StringBuilder();for(int k=0;k<4;k++){float x=clutchT(Telemetry.TPMS_FL+k);lv=Math.max(lv,Tiles.level(Telemetry.TPMS_FL+k,x));b.append(k>0?" ":"").append(Tiles.text(Telemetry.TPMS_FL+k,x));}
            boolean anyT=false;StringBuilder tb=new StringBuilder(" | ");for(int k=0;k<4;k++){float t=clutchT(Telemetry.TIRE_TEMP_FL+k);if(!Float.isNaN(t))anyT=true;tb.append(k>0?" ":"").append(Tiles.tyreTemp(t));}
            txt=b.toString()+(anyT?tb.append("C"):"");size=anyT?13:16;}   // the tyre temperatures ride after the pressures, a size smaller
        else if(sig==Tiles.CLUTCH){float o=clutchT(Telemetry.CLUTCH_ODD),e=clutchT(Telemetry.CLUTCH_EVEN);lv=Math.max(Telemetry.clutchLevel(o),Telemetry.clutchLevel(e));txt=Tiles.text(Telemetry.CLUTCH_ODD,o)+" / "+Tiles.text(Telemetry.CLUTCH_EVEN,e);size=18;}
        else if(sig==Tiles.EXHAUST){txt=Tiles.text(Telemetry.DPF_TEMP,clutchT(Telemetry.DPF_TEMP))+" / "+Tiles.text(Telemetry.EGT_CAT,clutchT(Telemetry.EGT_CAT));size=18;}
        else if(sig==Tiles.DPF){int k=Tiles.dpfLead(clutchT(Telemetry.DPF_SOOT),clutchT(Telemetry.DPF_TRIGGER),clutchT(Telemetry.DPF_DP));
            txt=Tiles.text(k,clutchT(k));lv=clutchT(Telemetry.DPF_REGEN)>0?1:0;
            text(c,Telemetry.UNITS[k],unitX,310,11,MUTED,false);}
        else{float v=Tiles.value(sig,samples,stats);lv=Tiles.level(sig,v);txt=Tiles.text(sig,v);}
        text(c,Tiles.caption(sig),capX,307,11,MUTED,true);
        boolean several=sig==Tiles.TIRES||sig==Tiles.CLUTCH||sig==Tiles.EXHAUST;   // a longer line starts right after the caption, clear of the unit
        text(c,txt,several?capX+85:valX,310,txt.length()>6&&size==22?18:size,lv==2?D():lv==1?AMBER:WHITE,true);
        if(sig!=Tiles.DPF)text(c,Tiles.unit(sig),unitX,310,11,MUTED,false);
    }
    /** 7DCT clutch temperatures (odd / even gears), Telemetry.clutchLevel colours; the card fills at red. */
    private void clutchCard(Canvas c,float x,float y,float w,float h){
        float[] t={clutchT(Telemetry.CLUTCH_ODD),clutchT(Telemetry.CLUTCH_EVEN)};
        boolean hot=Telemetry.clutchLevel(t[0])==2||Telemetry.clutchLevel(t[1])==2;
        panel(c,x,y,w,h,hot?D():PANEL,hot?D():LINE);box(c,x+10,y,x+53,y+2,hot?BG:BLUE);
        fitted(c,UiText.t("DCT 클러치"),x+10,y+20,13,w-20,hot?BG:MUTED);
        String[] n={UiText.t("홀수단"),UiText.t("짝수단")};
        for(int k=0;k<2;k++){float ty=y+50+k*30;int lv=Telemetry.clutchLevel(t[k]);
            text(c,n[k],x+10,ty,12,hot?BG:MUTED,false);
            text(c,Float.isNaN(t[k])?"—":Telemetry.format("%.0f",t[k]),x+70,ty,22,hot?BG:lv==1?AMBER:BLUE,true);}
        text(c,"°C",x+w-28,y+88,11,hot?BG:MUTED,false);
    }
    /** The TCU 22 01A5 pair for owners to check against a scanner or their mileage: odd / even, 1 decimal, not yet called the clutch life. */
    private void lifeCard(Canvas c,float x,float y,float w,float h){
        panel(c,x,y,w,h,PANEL,LINE);box(c,x+10,y,x+53,y+2,BLUE);
        fitted(c,UiText.t("DCT 값 확인용 (01A5)"),x+10,y+20,13,w-20,MUTED);
        String[] n={UiText.t("홀수단"),UiText.t("짝수단")};
        for(int k=0;k<2;k++){float v=clutchT(Telemetry.DCT_LIFE_A_ODD+k),ty=y+48+k*26;
            text(c,n[k],x+10,ty,12,MUTED,false);
            String s=Float.isNaN(v)?"—":String.format(Locale.US,"%.1f",v);paint.setTypeface(Typeface.DEFAULT_BOLD);paint.setTextSize(17);
            text(c,s,x+w-12-paint.measureText(s),ty,17,BLUE,true);}
        text(c,UiText.t("잔량인지 확인 중 · 주행거리와 함께 알려 주세요"),x+10,y+88,9,MUTED,false);
    }
    private float clutchT(int i){Telemetry.Sample k=samples[i];return k.valid?k.value:Float.NaN;}
    /** F1 brake card: the clutch temperatures where a 6MT shows its clutch switch; filled at red, nothing before the TCU answers. */
    private void clutchTempChip(Canvas c,float x,float y){
        float o=clutchT(Telemetry.CLUTCH_ODD),e=clutchT(Telemetry.CLUTCH_EVEN);if(Float.isNaN(o)&&Float.isNaN(e))return;
        int lv=Math.max(Telemetry.clutchLevel(o),Telemetry.clutchLevel(e));
        panel(c,x,y,132,18,lv==2?D():0xff111516,lv==2?D():lv==1?AMBER:LINE);
        centered(c,"ODD "+(Float.isNaN(o)?"—":Telemetry.format("%.0f",o)+"°")+"  EVEN "+(Float.isNaN(e)?"—":Telemetry.format("%.0f",e)+"°"),x+66,y+13,10,lv==2?BG:lv==1?AMBER:MUTED,true);
    }
    /** 6MT clutch switch as a chip: filled with the accent and IN while pressed, OUT outlined, a dash with no signal. */
    private void clutchChip(Canvas c,float x,float y){
        Telemetry.Sample k=samples[Telemetry.CLUTCH];boolean seen=k.valid,in=seen&&k.value>0;
        panel(c,x,y,132,18,in?BLUE:0xff111516,in?BLUE:LINE);
        centered(c,"CLUTCH "+(!seen?"—":in?"IN":"OUT"),x+66,y+13,10,in?BG:MUTED,true);
    }
    private void pedal(Canvas c,String label,String prefix,String unit,int x,int color,float maximum){
        panel(c,x,377,376,57,0xff111516,LINE);
        text(c,label,x+14,396,10,MUTED,true);
        String v=value(prefix);
        fitted(c,v,x+14,423,25,85,WHITE);
        text(c,unit,x+105,422,11,MUTED,false);
        float fraction=Math.max(0,Math.min(1,numeric(prefix)/maximum));
        for(int i=0;i<20;i++)box(c,x+148+i*10.6f,403,x+155+i*10.6f,421,fraction>i/20f?color:LINE);
    }
    private void drawCluster(Canvas c){
        float rpm=Math.max(0,Math.min(profile.maxRpm,numeric("엔진 RPM")));
        text(c,cue.shift?"SHIFT ↑":"SHIFT / LED",20,83,11,cue.shift?RED:MUTED,true);
        text(c,shiftTarget+" rpm",690,83,11,MUTED,true);
        for(int i=0;i<15;i++){
            int color=i<5?BLUE:i<10?0xffd0ebf8:RED;
            float x=155+i*35;
            boolean lit=cue.shift?(frameTime/250)%2==0:i<cue.leds;
            paint.setColor(T(lit?color:0xff252a2c));c.drawCircle(x,79,10,paint);
            if(lit){paint.setColor(T(0x70ffffff));c.drawCircle(x-2,76,3,paint);}
        }
        box(c,18,105,782,108,LINE);
        if(rpm>0)box(c,18,105,18+764*rpm/profile.maxRpm,108,BLUE);
        // Central gear is the primary cue, like a single-seat race display.
        box(c,294,120,506,367,BLUE);
        centered(c,"E M S  G E A R",400,148,15,BG,true);
        String gear=value("현재 기어");
        {paint.setTypeface(Typeface.DEFAULT_BOLD);paint.setTextSize(184);float gw=paint.measureText(gear),sz=gw>190?184*190/gw:184;   // "M7" ran past the 212-wide box
            centered(c,gear,400,321-(184-sz)*0.36f,sz,BG,true);}
        centered(c,"i 3 0  P D",400,350,11,BG,true);
        text(c,"SPEED",22,148,14,MUTED,true);
        text(c,"km/h",229,148,12,MUTED,false);
        centered(c,value("속도 km/h"),150,253,88,WHITE,true);
        box(c,18,276,276,278,LINE);
        f1Tile(c,0,22,175,250);   // hold to pick (Tiles): intake air by default, coolant on the right
        text(c,"ENGINE",526,148,14,MUTED,true);
        text(c,"rpm",749,148,12,MUTED,false);
        centered(c,value("엔진 RPM"),653,247,69,RevMatch.matched(samples,profile)>0?0xff3ddc84:WHITE,true);   // green: a 6MT blip on a lower gear's rpm
        box(c,524,276,782,278,LINE);
        f1Tile(c,1,528,685,760);
        accelPedal(c,18);
        pedal(c,"BRAKE PRESSURE", "브레이크 압력 bar", "bar",406,RED,100);
        if(profile.trans==VehicleProfile.Trans.MT)clutchChip(c,406+230,382);   // 6MT: the clutch beside the brake (EMS12 CLU_ACK)
        else clutchTempChip(c,406+230,382);   // 7DCT: the clutch temperatures in the same place
        button(c,"−",548,452,42,27,false,BLUE);
        text(c,UiText.t("목표 ")+shiftTarget+" rpm",604,471,12,BLUE,true);
        button(c,"+",740,452,42,27,false,BLUE);
    }
    private void drawDashboard(Canvas c){
        String[] names={"SPEED / km/h","ENGINE / rpm","EMS GEAR","ACCEL / %","BRAKE / bar"};
        for(int slot=0;slot<overviewLayout.count();slot++){
            int i=overviewLayout.signalAt(slot);boolean head=overviewLayout.headline(i);
            if((i==Telemetry.CLUTCH_ODD||i==Telemetry.DCT_LIFE_A_ODD)&&profile.trans==VehicleProfile.Trans.MT)continue;   // a 6MT has no clutch temperatures: the slot stays empty
            if(overviewLayout.grouped(i))groupCard(c,i);else overviewCard(c,i,head?names[slot]:UiText.t(Telemetry.LABELS[i]));
        }
    }
    private void groupCard(Canvas c,int i){
        float x=overviewLayout.left(i),y=overviewLayout.top(i),w=overviewLayout.width(i);
        if(i==Telemetry.CLUTCH_ODD){clutchCard(c,x,y,w,overviewLayout.height(i));return;}
        if(i==Telemetry.DCT_LIFE_A_ODD){lifeCard(c,x,y,w,overviewLayout.height(i));return;}
        panel(c,x,y,w,overviewLayout.height(i),PANEL,LINE);box(c,x+10,y,x+53,y+2,BLUE);
        if(i==Telemetry.TPMS_FL){
            fitted(c,UiText.t("타이어 공기압"),x+10,y+20,13,w-20,MUTED);
            String[] corners={"FL","FR","RL","RR"};
            for(int n=0;n<4;n++){
                float l=x+10+(n%2)*69,t=y+44+(n/2)*25;
                text(c,corners[n],l,t,10,MUTED,false);
                text(c,value(PREFIX[i+n]),l+20,t,20,BLUE,true);
                float tt=samples[Telemetry.TIRE_TEMP_FL+n].value;
                if(samples[Telemetry.TIRE_TEMP_FL+n].valid&&!Float.isNaN(tt))text(c,String.format(Locale.US,"%.0f°",tt),l+47,t,10,MUTED,false);
            }
            text(c,"psi",x+10,y+88,11,MUTED,false);
        }else{
            fitted(c,"TCS · ESC · ABS",x+10,y+20,13,w-20,MUTED);
            for(int n=0;n<3;n++){
                float t=y+43+n*22;Telemetry.Sample s=samples[i+n];
                text(c,UiText.t(Telemetry.LABELS[i+n]),x+10,t,12,MUTED,false);
                text(c,s.valid?(s.value==1?UiText.t(n==2?"경고":"꺼짐"):UiText.t("정상")):"—",x+66,t,16,s.valid&&s.value==1?D():Theme.current().calm(BLUE),true);   // TCS / ESC 1 = switched off, ABS 1 = warning lamp
            }
        }
    }
    private void overviewCard(Canvas c,int i,String title){
        float x=overviewLayout.left(i),y=overviewLayout.top(i),w=overviewLayout.width(i),h=overviewLayout.height(i);
        boolean head=overviewLayout.headline(i),ok=samples[i].valid&&!Float.isNaN(samples[i].value)&&!Float.isInfinite(samples[i].value);
        int style=overviewLayout.style(i),color=samples[i].changed?0xffe9f7ff:BLUE;
        panel(c,x,y,w,h,PANEL,LINE);box(c,x+10,y,x+53,y+2,head&&i==0?RED:BLUE);
        fitted(c,title,x+10,y+(head?18:22),head?11:13,w-20,MUTED);
        String number=ok||i==8?value(PREFIX[i]):"—";   // the gear card: P / R / N come from the selector while the gear itself is not valid
        float lo=GaugeRenderer.min(i,Telemetry.UNITS[i]),hi=GaugeRenderer.max(i,Telemetry.UNITS[i]);
        // Unsupported gears keep the existing dash and have no gauge position.
        boolean position=ok&&(i!=8||(samples[i].value>=1&&samples[i].value<=7));
        float f=position?Math.max(0,Math.min(1,(Telemetry.displayValue(i,samples[i].value)-lo)/(hi-lo))):0;
        if(style==2){
            fittedNumber(c,number,x+10,y+(head?61:63),head?(i==0?35:38):32,w-20,color);
        }else if(style==1){
            fittedNumber(c,number,x+10,y+(head?48:54),head?28:29,w-20,color);
            float t=y+(head?59:64),b=t+7;
            box(c,x+10,t,x+w-10,b,LINE);if(position)box(c,x+10,t,x+10+(w-20)*f,b,BLUE);
            if(lo<0){float zero=x+10+(w-20)*(-lo)/(hi-lo);stroke(c,zero,t-2,zero,b+2,MUTED,1);}
        }else{
            float cx=x+32,cy=y+(head?46:56),radius=head?17:23;
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(head?3:4);paint.setColor(T(LINE));
            RectF arc=new RectF(cx-radius,cy-radius,cx+radius,cy+radius);c.drawArc(arc,150,240,false,paint);
            if(position){paint.setColor(T(BLUE));c.drawArc(arc,150,KeyPath.arc(240*f),false,paint);}
            paint.setStyle(Paint.Style.FILL);
            if(position){double angle=Math.toRadians(150+240*f);stroke(c,cx,cy,cx+(float)Math.cos(angle)*(radius-6),cy+(float)Math.sin(angle)*(radius-6),WHITE,1.5f);paint.setColor(T(BLUE));c.drawCircle(cx,cy,2,paint);}
            fittedNumber(c,number,x+58,y+(head?54:66),head?26:28,w-68,color);
        }
        if(!head)text(c,Telemetry.UNITS[i],x+10,y+85,11,MUTED,false);
    }
}
