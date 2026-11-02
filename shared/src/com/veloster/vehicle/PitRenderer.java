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
    private final Path shape=new Path();
    private Shader background;private Theme backgroundTheme;
    /** The page gradient is a Shader built once per theme (a cached Shader would keep the old theme's colours). */
    private Shader background(){
        if(backgroundTheme!=Theme.current()){backgroundTheme=Theme.current();background=new LinearGradient(0,0,800,480,T(0xff111315),T(0xff030405),Shader.TileMode.CLAMP);}
        return background;
    }
    private final android.graphics.RectF cellRect=new android.graphics.RectF();
    private static final int BG=0xff040506,PANEL=0xff101315,LINE=0xff303638,BLUE=0xffa4d9ef,WHITE=0xffe1edf2,MUTED=0xff91a5ae,RED=0xffff4059;
    private Telemetry.Sample[] samples;
    private int shiftTarget;
    private long frameTime;
    private ShiftGuide.Cue cue;
    private VehicleProfile profile=VehicleProfile.PETROL_DCT;
    private boolean mediaPage;
    private MediaState.Snapshot music;
    private Bitmap artwork;
    private final GaugeRenderer gaugeRenderer=new GaugeRenderer();
    private final GraphRenderer graphRenderer=new GraphRenderer();
    private final PerformanceRenderer performanceRenderer=new PerformanceRenderer();
    private final RaceRenderer raceRenderer=new RaceRenderer();
    private final ShowRenderer showRenderer=new ShowRenderer();
    private final ClusterRenderer clusterRenderer=new ClusterRenderer();
    public static final int PAGE_PERFORMANCE=1,PAGE_RACE=2,PAGE_RECORDS=3,PAGE_SHOW=4,PAGE_CLASSIC=5,PAGE_RETRO=6,PAGE_RALLY=7;
    /** Pages ClusterRenderer draws: PAGE_CLASSIC and up (layout = page - PAGE_CLASSIC, screen = ClusterPicker.CLASSIC + layout). */
    public static boolean isCluster(int page){return page>=PAGE_CLASSIC&&page<ClusterPicker.page(ClusterPicker.COUNT);}
    private boolean picker,editing;
    private int hidden;
    /** Screen list over the body while open, its edit mode and the hidden screens (VehicleDash owns the gesture). */
    public void setPicker(boolean open,boolean edit,int hiddenScreens){picker=open;editing=edit;hidden=hiddenScreens;}
    /** Body tap on a Cluster layout (taxi meter button and the like); true when it was used. */
    public boolean clusterTap(float x,float y){return symmetric&&isCluster(page)&&clusterRenderer.tap(page-PAGE_CLASSIC,x,y);}
    private final HeaderTabs header=new HeaderTabs();
    /** The tab strip VehicleDash hit-tests and scrolls; the same instance draws it. */
    public HeaderTabs header(){return header;}
    private final DriveStats stats=new DriveStats();
    /** Session records, fed every frame whatever tab is shown. */
    public DriveStats stats(){return stats;}
    private boolean symmetric,lapRunning;
    private int page;
    private long lapMs;
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
    public void setArtwork(Bitmap value){artwork=value;}
    public void setGaugeLayout(GaugeLayout value){gaugeLayout=value;}
    public void setGraphLayout(GraphLayout value){graphLayout=value;}
    public void setSymmetric(boolean value){symmetric=value;}
    /** Performance and race are variants of the symmetric page (symmetric must also be set), so every other tab check stays as is. */
    public void setPage(int value){page=value;}
    public void setLap(long ms,boolean running){lapMs=ms;lapRunning=running;}
    public void setOverviewLayout(OverviewLayout value){if(value!=null)overviewLayout=value;}
    private static final String[] PREFIX={"엔진 RPM","오일 온도","냉각수 온도","앞 왼쪽 휠","앞 오른쪽 휠","뒤 왼쪽 휠","뒤 오른쪽 휠","속도 km/h","현재 기어","스로틀 %","브레이크 압력 bar","횡가속","종가속","조향각","외기 온도","출력 (계산)","토크 (계산)","부스트 (계산)","앞 왼쪽 공기압","앞 오른쪽 공기압","뒤 왼쪽 공기압","뒤 오른쪽 공기압","TCS","ESC","ABS","배터리 SOC","배터리 전압","조향 속도","e-LSD 수치","흡기 온도"};
    private int index(String prefix){for(int i=0;i<PREFIX.length;i++)if(PREFIX[i].equals(prefix))return i;return -1;}
    private String value(String prefix){int i=index(prefix);if(i<0||i>=samples.length||!samples[i].valid)return "—";
        if(i==8)return Telemetry.gearLabel((int)samples[i].value);
        return String.format(Locale.US,i==0||i==7||(i>=Telemetry.TPMS_FL&&i<=Telemetry.BAT_SOC)||i==Telemetry.STEERING_SPEED?"%.0f":i==11||i==12?"%+.2f":i==13?"%+.1f":i==17||i==Telemetry.BAT_VOLTAGE?"%.2f":"%.1f",Telemetry.displayValue(i,samples[i].value));}
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
        mediaPage=cluster && media;music=track;
        frameTime=now;samples=model.snapshot(now);profile=model.profile();
        clusterRenderer.tick(samples,now);   // trip totals of the layouts count on every page
        // The profile's range, so the "목표" label shows the target the cue really uses (a diesel's is 2100-4700).
        shiftTarget=ShiftGuide.target(target,profile);
        cue=ShiftGuide.evaluate(samples,shiftTarget,profile);stats.update(samples,now);
        c.save();c.scale(width/800f,height/480f);c.drawColor(T(BG));
        paint.setShader(background());c.drawRect(0,0,800,480,paint);paint.setShader(null);
        for(int y=56;y<480;y+=8)stroke(c,0,y,800,y,0x063f4948,1);
        box(c,0,0,800,52,0xff090b0c);box(c,18,51,782,52,LINE);
        if(Theme.current().mStripe){box(c,0,0,267,3,0xff5bc2e7);box(c,267,0,533,3,0xff1c69d4);box(c,533,0,800,3,0xffe22718);}  // BMW M stripe (not in the remap table)
        stroke(c,19,16,13,35,RED,4);stroke(c,27,16,21,35,BLUE,4);
        text(c,"i30 PD",36,33,23,WHITE,true);
        // Tab strip: every shown screen is a button; PitRenderer measures the labels and feeds HeaderTabs, which
        // scrolls sideways when they overflow and keeps the current tab in view.
        int[] shownTabs=ClusterPicker.slots(hidden,false);int cur=screen(cluster),curTab=-1;
        float[] tabW=new float[shownTabs.length];
        for(int i=0;i<shownTabs.length;i++){tabW[i]=tabWidth(UiText.t(ClusterPicker.NAMES[shownTabs[i]]));if(shownTabs[i]==cur)curTab=i;}
        header.setTabs(shownTabs,tabW,curTab);
        c.save();c.clipRect(HeaderTabs.LEFT,0,HeaderTabs.RIGHT,52);
        for(int i=0;i<header.count();i++)button(c,UiText.t(ClusterPicker.NAMES[header.screenAt(i)]),header.x(i),HeaderTabs.TOP,header.width(i),HeaderTabs.H,i==curTab,BLUE);
        c.restore();
        if(header.moreLeft())arrow(c,HeaderTabs.LEFT+5,-1);
        if(header.moreRight())arrow(c,HeaderTabs.RIGHT-5,1);
        gearButton(c,picker&&editing);
        centered(c,clock(),667,32,20,WHITE,true);
        button(c,UiText.t("돌아가기"),698,8,84,38,false,BLUE);
        if(graphLayout!=null)graphRenderer.draw(c,model,now,graphLayout);
        else if(gaugeLayout!=null)gaugeRenderer.draw(c,model,now,gaugeLayout);
        else if(symmetric&&page==PAGE_PERFORMANCE)performanceRenderer.draw(c,samples,lapMs,lapRunning,profile);
        else if(symmetric&&page==PAGE_SHOW)showRenderer.draw(c,samples,frameTime,profile);
        else if(symmetric&&page==PAGE_RACE)raceRenderer.draw(c,samples,cue,frameTime,stats,profile,media,music,artwork);
        else if(symmetric&&page==PAGE_RECORDS)raceRenderer.drawRecords(c,stats,frameTime);
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
        else if(graphLayout==null&&!mediaPage && music!=null && music.notice)drawMedia(c,true);
        if(cluster&&!picker&&gaugeLayout==null&&graphLayout==null)drawArtworkToggle(c);
        c.restore();
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
    /**
     * Screen list: 6 x 5 tiles over a dimmed body, the current screen outlined. Normally only the shown screens; in edit
     * mode (the gear) all of them, each marked 사용 or 미사용, then the language and theme tiles and 완료.
     */
    private void drawPicker(Canvas c,int cur){
        box(c,0,52,800,480,0xe6040506);
        int[] list=ClusterPicker.slots(hidden,editing);
        for(int i=0;i<list.length;i++){
            int s=list[i];boolean off=(hidden&(1<<s))!=0;
            float l=ClusterPicker.left(i),t=ClusterPicker.top(i),w=ClusterPicker.TILE_W,h=ClusterPicker.TILE_H;
            box(c,l,t,l+w,t+h,s==cur?0xff1c303b:off?0xff08090a:0xff101315);
            if(s==cur){box(c,l,t,l+w,t+3,BLUE);box(c,l,t+h-3,l+w,t+h,BLUE);box(c,l,t,l+3,t+h,BLUE);box(c,l+w-3,t,l+w,t+h,BLUE);}
            centered(c,UiText.t(ClusterPicker.NAMES[s]),l+w/2,t+35,20,off?MUTED:s==cur?BLUE:WHITE,true);
            if(editing)centered(c,UiText.t(off?"미사용":"사용"),l+w/2,t+60,14,off?MUTED:BLUE,true);
            else centered(c,UiText.t(ClusterPicker.DESC[s]),l+w/2,t+58,11,MUTED,false);
        }
        if(!editing)return;
        setting(c,ClusterPicker.DIAG,"진단",UiText.t("고장코드"));
        setting(c,ClusterPicker.CAR,"차량 색",UiText.t(CarPaint.label()));
        setting(c,ClusterPicker.LANG,"언어",UiText.language().toUpperCase(Locale.US));
        setting(c,ClusterPicker.THEME,"테마",Theme.current().label);
        float l=ClusterPicker.left(ClusterPicker.EDIT),t=ClusterPicker.top(ClusterPicker.EDIT),w=ClusterPicker.TILE_W,h=ClusterPicker.TILE_H;
        box(c,l,t,l+w,t+h,0xff16232b);
        box(c,l,t,l+w,t+3,BLUE);box(c,l,t+h-3,l+w,t+h,BLUE);box(c,l,t,l+3,t+h,BLUE);box(c,l+w-3,t,l+w,t+h,BLUE);
        centered(c,UiText.t("완료"),l+w/2,t+35,20,BLUE,true);
        centered(c,UiText.t("눌러서 사용·미사용"),l+w/2,t+58,11,MUTED,false);
    }
    /** Setting tile in edit mode: name, current value in the accent. */
    private void setting(Canvas c,int slot,String name,String value){
        float l=ClusterPicker.left(slot),t=ClusterPicker.top(slot),w=ClusterPicker.TILE_W,h=ClusterPicker.TILE_H;
        box(c,l,t,l+w,t+h,0xff16232b);
        centered(c,UiText.t(name),l+w/2,t+35,20,WHITE,true);
        centered(c,value,l+w/2,t+60,14,BLUE,true);
    }
    /** Pixel width of a header tab: its label plus side padding, with a sensible minimum. */
    private float tabWidth(String label){paint.setTypeface(Typeface.DEFAULT_BOLD);paint.setTextSize(14);return Math.max(56f,paint.measureText(label)+24f);}
    /** Faint chevron at a strip edge that has more tabs past it; dir -1 points left. */
    private void arrow(Canvas c,float x,int dir){
        box(c,x-7,9,x+7,45,0xff090b0c);
        shape.reset();shape.moveTo(x+dir*4,27);shape.lineTo(x-dir*3,20);shape.lineTo(x-dir*3,34);shape.close();
        paint.setColor(T(0xcc91a5ae));c.drawPath(shape,paint);
    }
    private void box(Canvas c,float l,float t,float r,float b,int color){paint.setColor(T(color));c.drawRect(l,t,r,b,paint);}
    private void panel(Canvas c,float x,float y,float w,float h,int fill,int edge){
        if(ThemeShapes.cell(c,paint,shape,cellRect,x,y,x+w,y+h,T(fill),T(edge)))return;
        shape.reset();shape.moveTo(x,y);shape.lineTo(x+w-12,y);shape.lineTo(x+w,y+12);
        shape.lineTo(x+w,y+h);shape.lineTo(x+12,y+h);shape.lineTo(x,y+h-12);shape.close();
        paint.setColor(T(fill));c.drawPath(shape,paint);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1);paint.setColor(T(edge));c.drawPath(shape,paint);paint.setStyle(Paint.Style.FILL);
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
        paint.setTypeface(Typeface.DEFAULT);paint.setTextSize(size);
        float dw=0;for(char d='0';d<='9';d++)dw=Math.max(dw,paint.measureText(String.valueOf(d)));
        float total=0;for(int k=0;k<s.length();k++){char ch=s.charAt(k);total+=ch>='0'&&ch<='9'?dw:paint.measureText(String.valueOf(ch));}
        if(total>width&&total>0){paint.setTextSize(size*width/total);dw*=width/total;}
        paint.setColor(T(color));float cx=x;
        for(int k=0;k<s.length();k++){char ch=s.charAt(k);String g=String.valueOf(ch);float gw=paint.measureText(g);
            if(ch>='0'&&ch<='9'){c.drawText(g,cx+(dw-gw)/2,y,paint);cx+=dw;}else{c.drawText(g,cx,y,paint);cx+=gw;}}
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
        centered(c,value("현재 기어"),248,290,130,BG,true);
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
        centered(c,gear,400,321,184,BG,true);
        centered(c,"i 3 0  P D",400,350,11,BG,true);
        text(c,"SPEED",22,148,14,MUTED,true);
        text(c,"km/h",229,148,12,MUTED,false);
        centered(c,value("속도 km/h"),150,253,88,WHITE,true);
        box(c,18,276,276,278,LINE);
        // No oil sensor on this engine and the oil slot only repeated coolant: intake air (EMS15) instead.
        text(c,"INTAKE TEMP",22,307,11,MUTED,true);
        text(c,value("흡기 온도"),175,310,22,WHITE,true);
        text(c,"°C",250,310,11,MUTED,false);
        text(c,"ENGINE",526,148,14,MUTED,true);
        text(c,"rpm",749,148,12,MUTED,false);
        centered(c,value("엔진 RPM"),653,247,69,WHITE,true);
        box(c,524,276,782,278,LINE);
        text(c,"COOLANT",528,307,11,MUTED,true);
        text(c,value("냉각수 온도"),685,310,22,WHITE,true);
        text(c,"°C",760,310,11,MUTED,false);
        accelPedal(c,18);
        pedal(c,"BRAKE PRESSURE", "브레이크 압력 bar", "bar",406,RED,100);
        button(c,"−",548,452,42,27,false,BLUE);
        text(c,UiText.t("목표 ")+shiftTarget+" rpm",604,471,12,BLUE,true);
        button(c,"+",740,452,42,27,false,BLUE);
    }
    private void drawDashboard(Canvas c){
        String[] names={"SPEED / km/h","ENGINE / rpm","EMS GEAR","ACCEL / %","BRAKE / bar"};
        for(int slot=0;slot<overviewLayout.count();slot++){
            int i=overviewLayout.signalAt(slot);boolean head=overviewLayout.headline(i);
            if(overviewLayout.grouped(i))groupCard(c,i);else overviewCard(c,i,head?names[slot]:UiText.t(Telemetry.LABELS[i]));
        }
    }
    private void groupCard(Canvas c,int i){
        float x=overviewLayout.left(i),y=overviewLayout.top(i),w=overviewLayout.width(i);
        panel(c,x,y,w,overviewLayout.height(i),PANEL,LINE);box(c,x+10,y,x+53,y+2,BLUE);
        if(i==Telemetry.TPMS_FL){
            fitted(c,UiText.t("타이어 공기압"),x+10,y+20,13,w-20,MUTED);
            String[] corners={"FL","FR","RL","RR"};
            for(int n=0;n<4;n++){
                float l=x+10+(n%2)*69,t=y+44+(n/2)*25;
                text(c,corners[n],l,t,10,MUTED,false);
                text(c,value(PREFIX[i+n]),l+20,t,20,BLUE,true);
            }
            text(c,"psi",x+10,y+88,11,MUTED,false);
        }else{
            fitted(c,"TCS · ESC · ABS",x+10,y+20,13,w-20,MUTED);
            for(int n=0;n<3;n++){
                float t=y+43+n*22;Telemetry.Sample s=samples[i+n];
                text(c,UiText.t(Telemetry.LABELS[i+n]),x+10,t,12,MUTED,false);
                text(c,s.valid?(s.value==1?UiText.t("작동"):UiText.t("비작동")):"—",x+66,t,16,s.valid&&s.value==1?D():Theme.current().calm(BLUE),true);
            }
        }
    }
    private void overviewCard(Canvas c,int i,String title){
        float x=overviewLayout.left(i),y=overviewLayout.top(i),w=overviewLayout.width(i),h=overviewLayout.height(i);
        boolean head=overviewLayout.headline(i),ok=samples[i].valid&&!Float.isNaN(samples[i].value)&&!Float.isInfinite(samples[i].value);
        int style=overviewLayout.style(i),color=samples[i].changed?0xffe9f7ff:BLUE;
        panel(c,x,y,w,h,PANEL,LINE);box(c,x+10,y,x+53,y+2,head&&i==0?RED:BLUE);
        fitted(c,title,x+10,y+(head?18:22),head?11:13,w-20,MUTED);
        String number=ok?value(PREFIX[i]):"—";
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
            if(position){paint.setColor(T(BLUE));c.drawArc(arc,150,240*f,false,paint);}
            paint.setStyle(Paint.Style.FILL);
            if(position){double angle=Math.toRadians(150+240*f);stroke(c,cx,cy,cx+(float)Math.cos(angle)*(radius-6),cy+(float)Math.sin(angle)*(radius-6),WHITE,1.5f);paint.setColor(T(BLUE));c.drawCircle(cx,cy,2,paint);}
            fittedNumber(c,number,x+58,y+(head?54:66),head?26:28,w-68,color);
        }
        if(!head)text(c,Telemetry.UNITS[i],x+10,y+85,11,MUTED,false);
    }
}
