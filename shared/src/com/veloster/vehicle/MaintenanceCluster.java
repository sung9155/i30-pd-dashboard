package com.veloster.vehicle;

import android.graphics.Canvas;
import android.graphics.Paint;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 정비 (screen 30): three tabs — 교환 (service items with km / time left), 타이어 (rotation, replacement with the DOT age,
 * air, alignment, the four pressures), 상태 (battery, inspection, first registration, trouble codes and lamp, clutch, DPF,
 * and what 자동 judged from). A tap on a row asks VehicleDash for its dialog (pendingEdit / pendingAction): input is
 * Android dialogs, parked only. The rows are worked out once a second; the frame only draws them.
 */
final class MaintenanceCluster extends ClusterKit {
    { themed=true; }
    /** pit_vehicle_ui behind maint_* keys (VehicleDash); null draws every item as not recorded. */
    static volatile MaintItems.Store store;
    static volatile int tab;
    static volatile MaintItems.Id pendingEdit;
    static volatile int pendingAction;
    static final int ACT_MODE=1,ACT_FIRST_REG=2,ACT_DTC=3;
    /** Last trouble-code read (DtcDialog): count and date, from the store. */
    static int dtcCount(MaintItems.Store s){String v=s==null?"":s.get("maint_dtc_count");try{return v.length()==0?-1:Integer.parseInt(v);}catch(NumberFormatException e){return -1;}}

    private static final int BG=0xff040506,PANEL=0xff101315,TRACK=0xff1d2326,WHITE=0xffe1edf2,MUTED=0xff91a5ae,BLUE=0xffa4d9ef,AMBER=0xffffb84d,GREEN=0xff3ddc84;
    private static final String[] TABS={"교환","타이어","상태"};
    static final float TAB_L=16,TAB_T=58,TAB_W=118,TAB_H=36,CHIP_T=102,CHIP_H=28,ROW_T=138,ROW_H=30,BAR_L=250,BAR_R=470;

    /** One drawn row: label, the bar fraction (NaN = none), the right text and its colour, and what a tap opens. */
    static final class Row { String name,right;float fraction=Float.NaN;int color=WHITE;MaintItems.Id id;int action;boolean warn; }
    private final List<Row> rows=new ArrayList<Row>();
    private String chip="",odoText="",foot="";
    private long builtAt=Long.MIN_VALUE;private int builtTab=-1;

    @Override boolean tap(float x,float y){
        for(int i=0;i<TABS.length;i++)if(x>=TAB_L+i*(TAB_W+8)&&x<=TAB_L+i*(TAB_W+8)+TAB_W&&y>=TAB_T&&y<=TAB_T+TAB_H){tab=i;builtAt=Long.MIN_VALUE;return true;}
        if(y>=CHIP_T&&y<=CHIP_T+CHIP_H&&x<=520){pendingAction=ACT_MODE;return true;}
        int i=(int)Math.floor((y-ROW_T)/ROW_H);
        if(y>=ROW_T&&i>=0&&i<rows.size()){Row r=rows.get(i);if(r.id!=null){pendingEdit=r.id;return true;}if(r.action!=0){pendingAction=r.action;return true;}}
        return false;
    }

    @Override protected void draw(Canvas c){
        if(now/1000!=builtAt/1000||tab!=builtTab)build();
        fill(c,0,52,800,480,BG);
        for(int i=0;i<TABS.length;i++){float l=TAB_L+i*(TAB_W+8);boolean on=i==tab;
            round(c,l,TAB_T,l+TAB_W,TAB_T+TAB_H,6,on?0xff233b46:PANEL);bold(c,t(TABS[i]),l+TAB_W/2,TAB_T+25,17,on?BLUE:MUTED,Paint.Align.CENTER);}
        bold(c,odoText,784,TAB_T+25,17,WHITE,Paint.Align.RIGHT);
        round(c,TAB_L,CHIP_T,520,CHIP_T+CHIP_H,6,PANEL);text(c,chip,TAB_L+10,CHIP_T+20,14,WHITE,Paint.Align.LEFT,android.graphics.Typeface.DEFAULT);
        for(int i=0;i<rows.size();i++){Row r=rows.get(i);float y=ROW_T+i*ROW_H;
            if(i%2==0)fill(c,8,y,792,y+ROW_H,0xff0b0f12);
            text(c,r.name,TAB_L,y+21,16,WHITE,Paint.Align.LEFT,android.graphics.Typeface.DEFAULT);
            if(!Float.isNaN(r.fraction)){round(c,BAR_L,y+11,BAR_R,y+19,4,TRACK);
                float w=(BAR_R-BAR_L)*clamp(r.fraction,0,1);if(w>1)round(c,BAR_L,y+11,BAR_L+w,y+19,4,r.color);}
            bold(c,r.right,784,y+21,15,r.warn?danger():r.color==AMBER?AMBER:WHITE,Paint.Align.RIGHT);}
        text(c,foot,TAB_L,470,12,MUTED,Paint.Align.LEFT,android.graphics.Typeface.DEFAULT);
    }

    private void build(){
        builtAt=now;builtTab=tab;rows.clear();
        MaintItems.Store s=store;float odo=v(Telemetry.ODOMETER);int today=MaintItems.today(),first=MaintItems.firstRegDate(s);
        MaintLog.Usage u=MaintLog.sinceOil();
        MaintJudge.Mode mode=MaintJudge.mode(s);MaintJudge.Verdict vd=MaintJudge.judge(mode,u,odo);
        odoText=Float.isNaN(odo)?"ODO —":String.format(Locale.US,"ODO %,d km",Math.round(odo));
        chip=t(mode==MaintJudge.Mode.AUTO?"자동":mode==MaintJudge.Mode.NORMAL?"일반":"가혹")+(mode==MaintJudge.Mode.AUTO?": "+t(vd.insufficient?"근거 부족 · 일반 주기 적용":vd.severe?"가혹":"일반")+(vd.severe&&vd.reason.length()>0?" · "+reason(vd.reason):""):"")+"  ▸";
        foot=t("기본값 · 바꿀 수 있음")+" · "+t("앱이 켜져 있던 동안 센 값");
        List<MaintItems.Id> shown=MaintItems.shown(prof);
        if(tab==0){List<Row> items=new ArrayList<Row>();
            for(MaintItems.Id id:shown){if(id.ordinal()>=MaintItems.Id.TIRE_ROTATE.ordinal())continue;items.add(item(id,s,vd,odo,today,first));}
            Collections.sort(items,new Comparator<Row>(){public int compare(Row a,Row b){return Float.compare(key(a),key(b));}});
            rows.addAll(items);}
        else if(tab==1){
            for(MaintItems.Id id:new MaintItems.Id[]{MaintItems.Id.TIRE_ROTATE,MaintItems.Id.TIRE_REPLACE,MaintItems.Id.TIRE_AIR,MaintItems.Id.ALIGNMENT})rows.add(item(id,s,vd,odo,today,first));
            Row p=new Row();p.name=t("공기압 (psi)");StringBuilder b=new StringBuilder();String[] w={"앞왼","앞오","뒤왼","뒤오"};
            for(int i=0;i<4;i++){float x=v(Telemetry.TPMS_FL+i);b.append(i==0?"":"  ").append(t(w[i])).append(' ').append(fmt("%.0f",x));}
            p.right=b.toString();rows.add(p);
            Row l=new Row();l.name=t("이번 주기 저압 경고");l.right=u.tpmsLow+t("회");if(u.tpmsLow>0)l.color=AMBER;rows.add(l);}
        else{
            rows.add(item(MaintItems.Id.BATTERY,s,vd,odo,today,first));
            Row bv=new Row();bv.name=t("배터리 전압");bv.right=Float.isNaN(u.restV)&&Float.isNaN(u.startV)?t("아직 잡힌 값 없음"):
                t("시동 전")+" "+fmt("%.1f V",u.restV)+"  "+t("시동 직후")+" "+fmt("%.1f V",u.startV);
            if(!Float.isNaN(u.restV)&&u.restV<12.2f)bv.warn=true;else if(!Float.isNaN(u.restV)&&u.restV<12.4f)bv.color=AMBER;rows.add(bv);
            rows.add(item(MaintItems.Id.INSPECTION,s,vd,odo,today,first));
            Row fr=new Row();fr.name=t("최초 등록일");fr.right=first>0?date(first):t("눌러서 입력");fr.action=ACT_FIRST_REG;rows.add(fr);
            Row dt=new Row();dt.name=t("고장코드 · 경고등");int dc=dtcCount(s);float mil=v(Telemetry.MIL);
            dt.right=(mil>0?t("경고등 켜짐"):t("경고등 꺼짐"))+" · "+(dc<0?t("조회 기록 없음"):dc==0?t("코드 없음"):dc+t("개"))+"  ▸ "+t("지금 조회");
            dt.warn=mil>0||dc>0;dt.action=ACT_DTC;rows.add(dt);
            if(prof.trans==VehicleProfile.Trans.DCT){Row cl=new Row();cl.name=t("DCT 클러치");cl.right=t("과열")+" "+u.clutchHot+t("회")+" · "+t("최고 온도")+" "+fmt("%.0f °C",u.clutchMax);if(u.clutchHot>0)cl.color=AMBER;rows.add(cl);}
            if(prof.fuel==VehicleProfile.Fuel.DIESEL){Row dp=new Row();dp.name="DPF";float span=(float)u.movingKm;
                dp.right=t("재생")+" "+u.dpfRegens+t("회")+(u.dpfRegens>0?" · "+t("평균")+String.format(Locale.US," %,d km",Math.round(span/u.dpfRegens)):"");rows.add(dp);}
            Row e1=new Row();e1.name=t("가혹 근거");float seen=Float.isNaN(odo)||Float.isNaN(u.odoAtOil)||odo<=u.odoAtOil?Float.NaN:(float)(100*u.seenKm/(odo-u.odoAtOil));
            e1.right=t("엔진")+String.format(Locale.US," %.0f h",u.engineS/3600)+" · "+t("공회전")+" "+pct(u.idleS,u.engineS)+" · "+t("앱이 본 거리")+" "+fmt("%.0f %%",seen);rows.add(e1);
            Row e2=new Row();e2.name="";e2.right=t("짧은 주행")+" "+pct(u.shortStarts,Math.max(0,u.starts-1))+" · "+t("냉간 시동")+" "+u.coldStarts+" · "+t("고속")+" "+pct(u.highSpeedKm,u.movingKm)+" · "+t("고부하")+" "+pct(u.highLoadS,u.engineS);rows.add(e2);}
    }
    /** MaintJudge's reason ("짧은 주행 62 %") with its label in the screen's language. */
    private static String reason(String r){for(String l:new String[]{"짧은 주행","공회전","저속 · 정체 평균","정지","고속","고부하"})if(r.startsWith(l))return t(l)+r.substring(l.length());return r;}
    private static String pct(double a,double b){return b>0?(int)Math.floor(100*a/b)+" %":"—";}
    /** Most urgent first: past due, then the least left, then the unrecorded ones. */
    private static float key(Row r){return r.id==null?9:r.warn?-1+(Float.isNaN(r.fraction)?0:r.fraction):Float.isNaN(r.fraction)?5:r.fraction;}
    private static String date(int ymd){return String.format(Locale.US,"%04d-%02d-%02d",ymd/10000,ymd/100%100,ymd%100);}

    private Row item(MaintItems.Id id,MaintItems.Store s,MaintJudge.Verdict vd,float odo,int today,int first){
        Row r=new Row();r.id=id;r.name=t(name(id));
        MaintItems.Rec rec=MaintItems.load(s,id);boolean severe=vd.severe&&MaintItems.hasSevere(id);
        MaintItems.Left l=MaintItems.left(id,rec,severe,odo,today,first);
        if(id==MaintItems.Id.BATTERY){if(rec.date<=0){r.right=t("기록 없음 · 눌러서 입력");r.color=MUTED;return r;}
            int m=MaintItems.ageMonths(rec.date,today);r.right=t("교체 뒤")+" "+years(m);if(m>=48)r.warn=true;else if(m>=36)r.color=AMBER;return r;}
        if(id==MaintItems.Id.TIRE_AIR){r.right=rec.date>0?t("마지막 보충")+" "+date(rec.date):t("기록 없음 · 눌러서 입력");if(rec.date<=0)r.color=MUTED;return r;}
        if(!l.recorded){r.right=t("기록 없음 · 눌러서 입력");r.color=MUTED;return r;}
        if(l.odoBad){r.right=t("오도미터 확인 필요");r.color=AMBER;return r;}
        StringBuilder b=new StringBuilder();
        if(id==MaintItems.Id.TIRE_REPLACE){if(!Float.isNaN(l.since))b.append(t("교체 뒤")).append(String.format(Locale.US," %,d km",Math.round(l.since)));
            if(rec.dot>0){int m=MaintItems.dotAgeMonths(rec.dot,today);b.append(b.length()>0?" · ":"").append(t("제조")).append(' ').append(years(m));if(m>=72)r.warn=true;else if(m>=60)r.color=AMBER;}
            r.right=b.length()==0?t("기록됨"):b.toString();return r;}
        if(Float.isNaN(l.km)&&l.days==Integer.MIN_VALUE){r.right=Float.isNaN(l.since)?t("기록됨"):t("지난 뒤")+String.format(Locale.US," %,d km",Math.round(l.since));return r;}
        r.fraction=l.fraction;
        if(l.overdue){r.warn=true;b.append(t("지남"));
            if(!Float.isNaN(l.km)&&l.km<0)b.append(String.format(Locale.US," %,d km",Math.round(-l.km)));
            if(l.days!=Integer.MIN_VALUE&&l.days<0)b.append(' ').append(span(-l.days));
        }else{if(!Float.isNaN(l.km))b.append(String.format(Locale.US,"%,d km",Math.round(l.km)));
            if(l.days!=Integer.MIN_VALUE)b.append(b.length()>0?" · ":"").append(span(l.days));b.append(' ').append(t("남음"));
            if(MaintJudge.band(id,l)==MaintJudge.Band.SOON)r.color=AMBER;else r.color=BLUE;}
        r.right=b.toString();return r;
    }
    private static String span(int days){return days>=60?(days/30)+t("개월"):days+t("일");}
    private static String years(int months){return months>=12?String.format(Locale.US,"%d%s %d%s",months/12,t("년"),months%12,t("개월")):months+t("개월");}
    static String name(MaintItems.Id id){return MaintItems.label(id);}
}
