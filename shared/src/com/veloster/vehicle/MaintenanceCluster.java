package com.veloster.vehicle;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 정비 (screen 30): three tabs — 교환 (service items with km / time left), 타이어 (rotation, replacement with the DOT age,
 * air, alignment, the four pressures), 상태 (trouble codes and lamp, battery, inspection, first registration, clutch, DPF,
 * and what 자동 judged from). 2026-11-28 look (MaintLayout): a big card on the left — the item to see to first with a
 * "교환했어요" button, the tyre pressures around a car, the trouble codes — and on the right cards two to a row (icon,
 * what is left, a bar) over "+ item" chips for what has no record yet. A tap asks VehicleDash for a dialog (pendingEdit /
 * pendingAction): input is Android dialogs, parked only. Worked out once a second, drawn four times a second.
 * Colours: the theme's accent while fine, amber from 10 % left or once the reminder would speak (MaintJudge.band), the
 * danger colour when past; tints are those colours see-through, so each theme keeps its own.
 */
final class MaintenanceCluster extends ClusterKit {
    { themed=true; }
    /** pit_vehicle_ui behind maint_* keys (VehicleDash); null draws every item as not recorded. */
    static volatile MaintItems.Store store;
    static volatile int tab;
    /** Set after a dialog saved: rebuild on the next frame rather than at the next second. */
    static volatile boolean stale;
    static volatile MaintItems.Id pendingEdit;
    static volatile int pendingAction;
    /** ACT_DONE comes with pendingEdit: the big card's 교환했어요 (a confirm, then the same as the dialog's 오늘 교환함). */
    static final int ACT_MODE=1,ACT_FIRST_REG=2,ACT_DTC=3,ACT_DONE=4;
    /** Last trouble-code read (DtcDialog): count and date, from the store. */
    static int dtcCount(MaintItems.Store s){String v=s==null?"":s.get("maint_dtc_count");try{return v.length()==0?-1:Integer.parseInt(v);}catch(NumberFormatException e){return -1;}}

    private static final int BG=0xff040506,PANEL=0xff101315,TRACK=0xff1d2326,WHITE=0xffe1edf2,SOFT=0xffc9d2d6,MUTED=0xff91a5ae,BLUE=0xffa4d9ef,AMBER=0xffffb84d,EDGE=0xff2c363b;
    private static final String[] TABS={"교환","타이어","상태"};
    static final float LOW=0.10f;   // of the interval left: amber
    private static final int OK=0,SOON=1,PAST=2,INFO=3;
    private static final int I_FLUID=1,I_FILTER=2,I_SPARK=3,I_DISC=4,I_WIPER=5,I_TIRE=6,I_BATTERY=7,I_CHECK=8,I_LAMP=9;

    /** A card (or a chip when nothing is recorded): name, the big value, a small note beside the name, a line under. */
    static final class Row { String name,value="",aside="",sub="";float fraction=Float.NaN;int state=INFO,icon;MaintItems.Id id;int action;boolean chip; }
    private static final class Page {
        Row hero;String heroHead="",button,caption="",cardsHead="",tail[];boolean buttonIcon=true;
        final List<Row> cards=new ArrayList<Row>(),chips=new ArrayList<Row>();MaintLayout.Grid grid;int past;
    }
    private final Page[] pages={new Page(),new Page(),new Page()};
    private String mode="",odoText="";private final float[] psi=new float[4];private int tpmsLow;private float chipL=800;
    private long builtAt=Long.MIN_VALUE;private MaintItems.Store builtStore;

    @Override boolean tap(float x,float y){
        int p=MaintLayout.pill(x,y);if(p>=0){tab=p;return true;}
        if(y>=MaintLayout.PILL_T&&y<=MaintLayout.PILL_T+MaintLayout.PILL_H&&x>=chipL){pendingAction=ACT_MODE;return true;}
        Page pg=pages[tab];
        if(MaintLayout.hero(x,y)){Row h=pg.hero;if(h==null)return false;
            if(pg.button!=null&&MaintLayout.button(x,y)&&h.id!=null){pendingEdit=h.id;pendingAction=ACT_DONE;return true;}
            return open(h);}
        if(pg.grid==null)return false;
        int i=MaintLayout.hit(pg.grid.cards,x,y);if(i>=0)return open(pg.cards.get(i));
        i=MaintLayout.hit(pg.grid.chips,x,y);return i>=0&&open(pg.chips.get(i));
    }
    private static boolean open(Row r){if(r.id!=null){pendingEdit=r.id;return true;}if(r.action!=0){pendingAction=r.action;return true;}return false;}

    @Override protected void draw(Canvas c){
        if(now/1000!=builtAt/1000||stale||store!=builtStore)build();
        fill(c,0,52,800,480,BG);
        float pt=MaintLayout.PILL_T,ph=MaintLayout.PILL_H;
        for(int i=0;i<TABS.length;i++){float l=MaintLayout.PILL_L+i*(MaintLayout.PILL_W+8);boolean on=i==tab;int n=pages[i].past;
            round(c,l,pt,l+MaintLayout.PILL_W,pt+ph,ph/2,on?WHITE:PANEL);
            String s=t(TABS[i]);float cx=l+MaintLayout.PILL_W/2-(n>0?11:0);bold(c,s,cx,pt+24,16,on?BG:SOFT,Paint.Align.CENTER);
            if(n>0){float bx=cx+width(s,16,Typeface.DEFAULT_BOLD)/2+13;dot(c,bx,pt+ph/2,9,danger());bold(c,String.valueOf(n),bx,pt+ph/2+4.5f,12,0xffffffff,Paint.Align.CENTER);}}
        float cw=width(mode,13,Typeface.DEFAULT)+28;chipL=784-cw;
        outline(c,chipL,pt+3,784,pt+ph-3,15,1,EDGE);text(c,mode,chipL+14,pt+23,13,SOFT,Paint.Align.LEFT,Typeface.DEFAULT);
        fit(c,odoText,chipL-12,pt+24,chipL-12-360,16,WHITE,Paint.Align.RIGHT,true);
        Page pg=pages[tab];
        if(tab==1)tireHero(c);else hero(c,pg);
        MaintLayout.Grid g=pg.grid;
        if(pg.cards.size()>0)text(c,pg.cardsHead,MaintLayout.RIGHT_L+2,g.cardsLabel+15,13,MUTED,Paint.Align.LEFT,Typeface.DEFAULT);
        for(int i=0;i<pg.cards.size();i++)card(c,pg.cards.get(i),g.cards[i],g.tall);
        if(pg.chips.size()>0)text(c,t("아직 기록이 없어요 · 눌러서 입력"),MaintLayout.RIGHT_L+2,g.chipsLabel+15,13,MUTED,Paint.Align.LEFT,Typeface.DEFAULT);
        for(int i=0;i<pg.chips.size();i++){float[] r=g.chips[i];outline(c,r[0],r[1],r[2],r[3],10,1,0xff3a464c);
            fit(c,"+ "+pg.chips.get(i).name,(r[0]+r[2])/2,(r[1]+r[3])/2+5,r[2]-r[0]-16,14,SOFT,Paint.Align.CENTER,false);}
        if(pg.tail!=null){float t=g.tailT,w=MaintLayout.RIGHT_R-MaintLayout.RIGHT_L-28;round(c,MaintLayout.RIGHT_L,t,MaintLayout.RIGHT_R,t+MaintLayout.TAIL_H,10,PANEL);
            for(int i=0;i<pg.tail.length;i++)fit(c,pg.tail[i],MaintLayout.RIGHT_L+14,t+22+i*21,w,i==0?12:13,i==0?MUTED:SOFT,Paint.Align.LEFT,false);}
    }

    private static int color(int st){return st==PAST?danger():st==SOON?AMBER:st==OK?BLUE:WHITE;}
    private static int ink(int st){return st==PAST?danger():st==SOON?AMBER:WHITE;}
    private static int tint(int color,int alpha){return (color&0xffffff)|(alpha<<24);}
    private int well(int st){return st==INFO?TRACK:tint(color(st),0x33);}

    private void card(Canvas c,Row r,float[] q,boolean tall){
        float l=q[0],t=q[1],rr=q[2],b=q[3];int st=r.state;
        round(c,l,t,rr,b,10,PANEL);
        if(tall){float cy=t+30;dot(c,l+34,cy,20,well(st));icon(c,r.icon,l+34,cy,9,color(st));
            float tx=l+64,aw=r.aside.length()==0?0:Math.min(width(r.aside,12,Typeface.DEFAULT),(rr-tx)/2);
            if(aw>0)fit(c,r.aside,rr-14,t+23,aw,12,MUTED,Paint.Align.RIGHT,false);
            fit(c,r.name,tx,t+23,rr-14-tx-aw-8,13,SOFT,Paint.Align.LEFT,false);
            fit(c,r.value,tx,t+50,rr-14-tx,20,ink(st),Paint.Align.LEFT,true);
            bar(c,r,l+14,rr-14,b-13,5);
        }else{float cy=(t+b)/2-1;dot(c,l+24,cy,14,well(st));icon(c,r.icon,l+24,cy,6.5f,color(st));
            float nw=Math.min(width(r.name,13,Typeface.DEFAULT),(rr-l)*0.45f);
            fit(c,r.name,l+46,cy+5,nw,13,SOFT,Paint.Align.LEFT,false);
            fit(c,r.value,rr-12,cy+5,rr-12-(l+46+nw+8),15,ink(st),Paint.Align.RIGHT,true);
            bar(c,r,l+46,rr-12,b-6,3);}
    }
    private void bar(Canvas c,Row r,float l,float rr,float t,float h){
        if(r.state==PAST&&!Float.isNaN(r.fraction)){round(c,l,t,rr,t+h,h/2,danger());return;}
        if(Float.isNaN(r.fraction))return;
        round(c,l,t,rr,t+h,h/2,TRACK);float w=(rr-l)*clamp(r.fraction,0,1);if(w>h)round(c,l,t,l+w,t+h,h/2,color(r.state));
    }

    private void hero(Canvas c,Page pg){
        float L=MaintLayout.HERO_L,T=MaintLayout.TOP,R=MaintLayout.HERO_R,B=MaintLayout.BOTTOM,w=R-L-32;Row h=pg.hero;
        if(h==null){round(c,L,T,R,B,14,PANEL);text(c,t("정비 기록"),L+16,T+26,13,MUTED,Paint.Align.LEFT,Typeface.DEFAULT);
            dot(c,L+44,T+70,28,TRACK);icon(c,I_CHECK,L+44,T+70,13,SOFT);
            fit(c,t("아직 기록이 없어요"),L+16,T+134,w,22,WHITE,Paint.Align.LEFT,true);
            String[] ln={"오른쪽 항목을 눌러","마지막 교환 시점을 넣으면","남은 거리가 여기 보여요"};
            for(int i=0;i<ln.length;i++)fit(c,t(ln[i]),L+16,T+166+i*22,w,14,SOFT,Paint.Align.LEFT,false);
            return;}
        int st=h.state;
        if(st==PAST||st==SOON){round(c,L,T,R,B,14,tint(color(st),0x1c));outline(c,L,T,R,B,14,1,tint(color(st),0x66));}
        else round(c,L,T,R,B,14,PANEL);
        text(c,pg.heroHead,L+16,T+26,13,st==PAST||st==SOON?color(st):MUTED,Paint.Align.LEFT,Typeface.DEFAULT);
        dot(c,L+44,T+70,28,well(st));icon(c,h.icon,L+44,T+70,13,color(st));
        fit(c,h.name,L+16,T+134,w,22,WHITE,Paint.Align.LEFT,true);
        fit(c,h.value,L+16,T+170,w,27,ink(st),Paint.Align.LEFT,true);
        if(h.aside.length()>0)fit(c,h.aside,L+16,T+196,w,14,SOFT,Paint.Align.LEFT,false);
        if(h.sub.length()>0)fit(c,h.sub,L+16,T+218,w,13,MUTED,Paint.Align.LEFT,false);
        if(pg.button!=null){float bl=MaintLayout.BTN_L,bt=MaintLayout.BTN_T,br=MaintLayout.BTN_R,bb=MaintLayout.BTN_B;
            round(c,bl,bt,br,bb,10,WHITE);float tw=width(pg.button,16,Typeface.DEFAULT_BOLD),x=(bl+br)/2-tw/2+(pg.buttonIcon?12:0),m=(bt+bb)/2;
            if(pg.buttonIcon)icon(c,I_CHECK,x-16,m,8,BG);bold(c,pg.button,x,m+6,16,BG,Paint.Align.LEFT);
            fit(c,pg.caption,(L+R)/2,bb+19,w,12,MUTED,Paint.Align.CENTER,false);}
    }
    /** 타이어's big card: the four pressures around a car seen from above, front up, and this period's low warnings. */
    private void tireHero(Canvas c){
        float L=MaintLayout.HERO_L,T=MaintLayout.TOP,R=MaintLayout.HERO_R,B=MaintLayout.BOTTOM,cx=(L+R)/2;
        round(c,L,T,R,B,14,PANEL);text(c,t("공기압 (psi)"),L+16,T+26,13,MUTED,Paint.Align.LEFT,Typeface.DEFAULT);
        float top=T+70,bot=T+290;
        outline(c,cx-32,top,cx+32,bot,24,2,EDGE);round(c,cx-22,top+44,cx+22,top+80,6,TRACK);   // body, windscreen
        String[] w={"앞왼","앞오","뒤왼","뒤오"};
        for(int i=0;i<4;i++){boolean left=i%2==0;float y=i<2?top+40:bot-40,wx=left?cx-32:cx+32;
            round(c,wx-5,y-20,wx+5,y+20,4,SOFT);
            float tx=left?cx-48:cx+48;Paint.Align a=left?Paint.Align.RIGHT:Paint.Align.LEFT;
            text(c,t(w[i]),tx,y-8,12,MUTED,a,Typeface.DEFAULT);bold(c,fmt("%.0f",psi[i]),tx,y+18,24,WHITE,a);}
        int n=tpmsLow;
        fit(c,t("이번 주기 저압 경고")+" "+n+t("회"),cx,B-26,R-L-32,14,n>0?AMBER:SOFT,Paint.Align.CENTER,false);
    }
    /** Text that shrinks to fit maxW (never under 9 px). */
    private void fit(Canvas c,String s,float x,float y,float maxW,float size,int color,Paint.Align a,boolean b){
        Typeface f=b?Typeface.DEFAULT_BOLD:Typeface.DEFAULT;float w=width(s,size,f);
        if(w>maxW&&w>0)size=Math.max(9,size*maxW/w);text(c,s,x,y,size,color,a,f);
    }
    /** Small drawn icons (no glyphs: the head unit's fonts may lack them), s = half their size. */
    private void icon(Canvas c,int k,float x,float y,float s,int col){
        switch(k){
            case I_FLUID:dot(c,x,y+0.3f*s,0.62f*s,col);path.reset();path.moveTo(x,y-s);path.lineTo(x+0.56f*s,y+0.1f*s);path.lineTo(x-0.56f*s,y+0.1f*s);path.close();poly(c,col);break;
            case I_FILTER:outline(c,x-s,y-0.7f*s,x+s,y+0.7f*s,2,2,col);for(int i=-1;i<=1;i++)line(c,x+i*0.5f*s,y-0.7f*s,x+i*0.5f*s,y+0.7f*s,1.6f,col);break;
            case I_SPARK:path.reset();path.moveTo(x+0.25f*s,y-s);path.lineTo(x-0.6f*s,y+0.15f*s);path.lineTo(x-0.05f*s,y+0.15f*s);path.lineTo(x-0.25f*s,y+s);
                path.lineTo(x+0.6f*s,y-0.2f*s);path.lineTo(x+0.05f*s,y-0.2f*s);path.close();poly(c,col);break;
            case I_DISC:ring(c,x,y,0.82f*s,2,col);dot(c,x,y,0.3f*s,col);break;
            case I_WIPER:line(c,x-0.8f*s,y+0.7f*s,x+0.7f*s,y-0.8f*s,2.4f,col);dot(c,x-0.8f*s,y+0.7f*s,0.25f*s,col);break;
            case I_TIRE:ring(c,x,y,0.72f*s,0.4f*s,col);dot(c,x,y,0.18f*s,col);break;
            case I_BATTERY:outline(c,x-s,y-0.55f*s,x+s,y+0.75f*s,2,2,col);fill(c,x-0.65f*s,y-0.85f*s,x-0.3f*s,y-0.55f*s,col);fill(c,x+0.3f*s,y-0.85f*s,x+0.65f*s,y-0.55f*s,col);break;
            case I_CHECK:line(c,x-0.75f*s,y,x-0.2f*s,y+0.55f*s,2.4f,col);line(c,x-0.2f*s,y+0.55f*s,x+0.8f*s,y-0.6f*s,2.4f,col);break;
            case I_LAMP:ring(c,x,y,0.85f*s,2,col);fill(c,x-1.2f,y-0.5f*s,x+1.2f,y+0.15f*s,col);dot(c,x,y+0.42f*s,1.5f,col);break;
            default:dot(c,x,y,0.4f*s,col);
        }
    }

    private void build(){
        builtAt=now;stale=false;
        MaintItems.Store s=store;builtStore=s;float odo=v(Telemetry.ODOMETER);int today=MaintItems.today(),first=MaintItems.firstRegDate(s);
        MaintLog.Usage u=MaintLog.sinceOil();
        MaintJudge.Mode md=MaintJudge.mode(s);MaintJudge.Verdict vd=MaintJudge.judge(md,u,odo);
        odoText=Float.isNaN(odo)?"ODO —":Fmt.f("ODO %,d km",Math.round(odo));
        String used=t(vd.severe?"가혹":"일반");
        mode=t("교환 주기")+": "+used+(md==MaintJudge.Mode.AUTO?" ("+t("자동")+(vd.insufficient?" · "+t("근거 부족"):"")+")":"")+"  ▸";
        List<MaintItems.Id> shown=MaintItems.shown(prof);

        Page p=reset(0);   // 교환
        List<Row> items=new ArrayList<Row>();
        for(MaintItems.Id id:shown)if(id.ordinal()<MaintItems.Id.TIRE_ROTATE.ordinal())items.add(item(id,s,vd,odo,today,first));
        Collections.sort(items,new Comparator<Row>(){public int compare(Row a,Row b){return Float.compare(key(a),key(b));}});
        split(p,items);
        if(!p.cards.isEmpty()){p.hero=p.cards.remove(0);int st=p.hero.state;
            p.heroHead=t(st==PAST?"지금 챙길 것":st==SOON?"곧 챙길 것":"다음 교환");p.button=t("교환했어요");p.caption=t("오늘 · 지금 주행거리로 기록");}
        p.cardsHead=t("급한 순서");finish(p,0);

        p=reset(1);   // 타이어
        items.clear();for(MaintItems.Id id:new MaintItems.Id[]{MaintItems.Id.TIRE_ROTATE,MaintItems.Id.TIRE_REPLACE,MaintItems.Id.TIRE_AIR,MaintItems.Id.ALIGNMENT})items.add(item(id,s,vd,odo,today,first));
        split(p,items);for(int i=0;i<4;i++)psi[i]=v(Telemetry.TPMS_FL+i);tpmsLow=u.tpmsLow;
        p.cardsHead=t("타이어 기록");finish(p,0);

        p=reset(2);   // 상태
        Row dt=new Row();dt.name=t("고장코드 · 경고등");dt.icon=I_LAMP;dt.action=ACT_DTC;int dc=dtcCount(s);float mil=v(Telemetry.MIL);
        dt.value=t(mil>0?"경고등 켜짐":"경고등 꺼짐");dt.aside=dc<0?t("조회 기록 없음"):dc==0?t("코드 없음"):dc+t("개");
        int dd=num(s==null?"":s.get("maint_dtc_date"));if(dd>0)dt.sub=t("마지막 조회")+" "+date(dd);
        dt.state=mil>0||dc>0?PAST:OK;p.hero=dt;p.heroHead=t("차 상태");p.button=t("지금 조회");p.caption=t("정차 중에만");p.buttonIcon=false;
        items.clear();items.add(item(MaintItems.Id.BATTERY,s,vd,odo,today,first));
        Row bv=new Row();bv.name=t("배터리 전압");bv.icon=I_BATTERY;
        if(Float.isNaN(u.restV)&&Float.isNaN(u.startV))bv.value=t("아직 잡힌 값 없음");
        else{bv.value=t("시동 전")+" "+fmt("%.1f V",u.restV);bv.aside=t("시동 직후")+" "+fmt("%.1f V",u.startV);}
        if(!Float.isNaN(u.restV))bv.state=u.restV<12.2f?PAST:u.restV<12.4f?SOON:INFO;items.add(bv);
        items.add(item(MaintItems.Id.INSPECTION,s,vd,odo,today,first));
        Row fr=new Row();fr.name=t("최초 등록일");fr.icon=I_CHECK;fr.action=ACT_FIRST_REG;if(first>0)fr.value=date(first);else fr.chip=true;items.add(fr);
        if(prof.trans==VehicleProfile.Trans.DCT){Row cl=new Row();cl.name=t("DCT 클러치");cl.icon=I_DISC;cl.value=t("과열")+" "+u.clutchHot+t("회");
            cl.aside=t("최고 온도")+" "+fmt("%.0f °C",u.clutchMax);if(u.clutchHot>0)cl.state=SOON;items.add(cl);}
        if(prof.fuel==VehicleProfile.Fuel.DIESEL){Row dp=new Row();dp.name="DPF";dp.icon=I_FILTER;dp.value=t("재생")+" "+u.dpfRegens+t("회");
            if(u.dpfRegens>0)dp.aside=t("평균")+Fmt.f(" %,d km",Math.round((float)u.movingKm/u.dpfRegens));items.add(dp);}
        split(p,items);p.cardsHead=t("기록 · 측정");
        float seen=Float.isNaN(odo)||Float.isNaN(u.odoAtOil)||odo<=u.odoAtOil?Float.NaN:(float)(100*u.seenKm/(odo-u.odoAtOil));
        p.tail=new String[]{t("가혹 근거")+(vd.severe&&vd.reason.length()>0?": "+reason(vd.reason):"")+" · "+t("앱이 켜져 있던 동안 센 값"),
            t("엔진")+Fmt.f(" %.0f h",u.engineS/3600)+" · "+t("공회전")+" "+pct(u.idleS,u.engineS)+" · "+t("앱이 본 거리")+" "+fmt("%.0f %%",seen),
            t("짧은 주행")+" "+pct(u.shortStarts,u.judgedStarts)+" · "+t("냉간 시동")+" "+u.coldStarts+" · "+t("고속")+" "+pct(u.highSpeedKm,u.movingKm)+" · "+t("고부하")+" "+pct(u.highLoadS,u.engineS)};
        finish(p,MaintLayout.TAIL_H);
    }
    private Page reset(int i){Page p=new Page();pages[i]=p;return p;}
    private static void split(Page p,List<Row> items){for(Row r:items)(r.chip?p.chips:p.cards).add(r);}
    private static void finish(Page p,float tail){
        p.grid=MaintLayout.grid(p.cards.size(),p.chips.size(),tail);
        if(p.hero!=null&&p.hero.state==PAST)p.past++;for(Row r:p.cards)if(r.state==PAST)p.past++;
    }
    private static int num(String v){try{return v.length()==0?0:Integer.parseInt(v);}catch(NumberFormatException e){return 0;}}
    /** MaintJudge's reason ("짧은 주행 62 %") with its label in the screen's language. */
    private static String reason(String r){for(String l:new String[]{"짧은 주행","공회전","저속 · 정체 평균","정지","고속","고부하"})if(r.startsWith(l))return t(l)+r.substring(l.length());return r;}
    private static String pct(double a,double b){return b>0?(int)Math.floor(100*a/b)+" %":"—";}
    /** Most urgent first: past due, then amber with no bar (a record to check), then the least left, then nothing to count down. */
    private static float key(Row r){return r.state==PAST?-1+(Float.isNaN(r.fraction)?0:r.fraction):Float.isNaN(r.fraction)?(r.state==SOON?-0.001f:5):r.fraction;}
    private static String date(int ymd){return Fmt.f("%04d-%02d-%02d",ymd/10000,ymd/100%100,ymd%100);}
    private static String km(float k){return Fmt.f("%,d km",Math.round(Math.abs(k)));}
    private static String left(String a){return String.format(t("%s 남음"),a);}
    private static String over(String a){return String.format(t("%s 지남"),a);}

    private Row item(MaintItems.Id id,MaintItems.Store s,MaintJudge.Verdict vd,float odo,int today,int first){
        Row r=new Row();r.id=id;r.name=t(name(id));r.icon=icon(id);
        MaintItems.Rec rec=MaintItems.load(s,id);boolean severe=vd.severe&&MaintItems.hasSevere(id);
        MaintItems.Left l=MaintItems.left(id,rec,severe,odo,today,first);
        if(id==MaintItems.Id.BATTERY){if(rec.date<=0){r.chip=true;return r;}
            int m=MaintItems.ageMonths(rec.date,today);r.value=t("교체 뒤")+" "+years(m);r.state=m>=48?PAST:m>=36?SOON:INFO;return r;}
        if(id==MaintItems.Id.TIRE_AIR){if(rec.date<=0){r.chip=true;return r;}r.value=date(rec.date);r.aside=t("마지막 보충");return r;}
        if(!l.recorded){r.chip=true;return r;}
        if(l.odoBad){r.value=t(l.ahead?"기록이 주행거리보다 큼":"오도미터 확인 필요");r.aside=t("눌러서 확인");r.state=SOON;return r;}   // ahead: the record may be the typo
        if(id==MaintItems.Id.TIRE_REPLACE){r.value=Float.isNaN(l.since)?t("기록됨"):t("교체 뒤")+" "+km(l.since);
            int m=MaintItems.dotAgeMonths(rec.dot,today);if(m>=0){r.aside=t("제조")+" "+years(m);r.state=m>=72?PAST:m>=60?SOON:INFO;}
            return r;}
        if(Float.isNaN(l.km)&&l.days==Integer.MIN_VALUE){r.value=Float.isNaN(l.since)?t("기록됨"):t("지난 뒤")+" "+km(l.since);return r;}
        r.fraction=l.fraction;
        boolean hasKm=!Float.isNaN(l.km),hasDays=l.days!=Integer.MIN_VALUE;
        String kmPart=hasKm?(l.km<0?over(km(l.km)):left(km(l.km))):"",dayPart=hasDays?(l.days<0?over(span(-l.days)):left(span(l.days))):"";
        int ek=l.everyKm,em=l.everyMo;   // what left() counted from (coolant's factory fill, not the default)
        boolean dayFirst=!hasKm||hasDays&&em>0&&ek>0&&l.days/(em*30.4f)<l.km/ek;   // the part closer to due goes big (it is what sorts the card)
        r.value=dayFirst?dayPart:kmPart;r.aside=dayFirst?kmPart:dayPart;
        r.state=l.overdue?PAST:MaintJudge.band(id,l)==MaintJudge.Band.SOON||l.fraction<=LOW?SOON:OK;
        if(ek>0||em>0)r.sub=t("주기")+" "+(ek>0?Fmt.f("%,d km",ek):"")+(ek>0&&em>0?" · ":"")+(em>0?years(em):"");
        return r;
    }
    private static int icon(MaintItems.Id id){
        switch(id){
            case OIL:case DCT_OIL:case MT_OIL:case BRAKE_FLUID:case COOLANT:return I_FLUID;
            case AIR:case CABIN:case FUEL_FILTER:return I_FILTER;
            case SPARK:return I_SPARK;
            case PADS_FRONT:case PADS_REAR:return I_DISC;
            case WIPER:return I_WIPER;
            case BATTERY:return I_BATTERY;
            case INSPECTION:return I_CHECK;
            default:return I_TIRE;
        }
    }
    private static String span(int days){return days>=60?(days/30)+t("개월"):days+t("일");}
    private static String years(int months){return months<12?months+t("개월"):months%12==0?months/12+t("년"):String.format(Locale.US,"%d%s %d%s",months/12,t("년"),months%12,t("개월"));}
    static String name(MaintItems.Id id){return MaintItems.label(id);}
}
