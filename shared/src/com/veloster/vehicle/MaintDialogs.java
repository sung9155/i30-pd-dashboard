package com.veloster.vehicle;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.veloster.i18n.UiText;
import java.util.Locale;

/** 정비's input: one dialog per tapped row (VehicleDash opens them after the screen's tap). Parked only. */
final class MaintDialogs {
    private MaintDialogs(){}

    /** The car is seen standing (or its speed is not known, e.g. ignition data not in yet). */
    static boolean parked(Telemetry.Sample sp){return !sp.valid||Float.isNaN(sp.value)||sp.value<1;}
    static boolean refuse(Activity a,Telemetry.Sample sp){
        if(parked(sp))return false;
        Toast.makeText(a,UiText.t("정차 중에만 입력할 수 있습니다"),Toast.LENGTH_SHORT).show();return true;}

    static void item(final Activity a,final MaintItems.Store s,final MaintItems.Id id,final float odo,final Runnable after){
        final boolean dateOnly=id==MaintItems.Id.BRAKE_FLUID||id==MaintItems.Id.WIPER||id==MaintItems.Id.TIRE_AIR||id==MaintItems.Id.BATTERY;
        if(id==MaintItems.Id.INSPECTION){dateDialog(a,UiText.t("다음 정기검사일"),MaintItems.load(s,id).date,new DateSink(){public void set(int d){
            MaintItems.Rec r=MaintItems.load(s,id);r.date=d;MaintItems.save(s,id,r);MaintJudge.recorded(s,id);after.run();}});return;}
        String[] acts={UiText.t("오늘 교환함"),UiText.t("직접 입력"),UiText.t("주기 바꾸기"),UiText.t("기록 지우기")};
        new AlertDialog.Builder(a).setTitle(UiText.t(MaintenanceCluster.name(id))).setItems(acts,new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){
            if(w==0){if(Float.isNaN(odo)&&!dateOnly){Toast.makeText(a,UiText.t("오도미터를 아직 받지 못했습니다"),Toast.LENGTH_SHORT).show();return;}
                int km=Float.isNaN(odo)?-1:Math.round(odo);MaintItems.Rec r=MaintItems.load(s,id);r.km=km;r.date=MaintItems.today();
                if(id==MaintItems.Id.TIRE_REPLACE){dotDialog(a,s,r,id,after);return;}
                record(s,id,r);after.run();}
            else if(w==1)enter(a,s,id,odo,after);
            else if(w==2)interval(a,s,id,after);
            else new AlertDialog.Builder(a).setMessage(UiText.t("이 항목의 교환 기록을 지웁니다.")).setNegativeButton(UiText.t("취소"),null)
                .setPositiveButton(UiText.t("지우기"),new DialogInterface.OnClickListener(){public void onClick(DialogInterface d2,int w2){
                    MaintItems.Rec r=MaintItems.load(s,id);r.km=-1;r.date=0;r.dot=0;MaintItems.save(s,id,r);MaintJudge.recorded(s,id);after.run();}}).show();
        }}).show();
    }
    private static void record(MaintItems.Store s,MaintItems.Id id,MaintItems.Rec r){
        MaintItems.save(s,id,r);MaintJudge.recorded(s,id);
        if(id==MaintItems.Id.OIL)MaintLog.oilChanged(r.km>=0?r.km:Float.NaN);}
    private static EditText num(Activity a,String hint,String value){EditText e=new EditText(a);e.setSingleLine(true);e.setInputType(InputType.TYPE_CLASS_NUMBER);
        e.setHint(hint);if(value!=null)e.setText(value);return e;}
    private static LinearLayout column(Activity a,Object... rows){LinearLayout l=new LinearLayout(a);l.setOrientation(LinearLayout.VERTICAL);int p=(int)(16*a.getResources().getDisplayMetrics().density);l.setPadding(p,p/2,p,0);
        for(Object o:rows){if(o instanceof String){TextView t=new TextView(a);t.setText((String)o);l.addView(t);}else l.addView((android.view.View)o);}return l;}
    private static String ymd(int d){return d<=0?"":String.format(Locale.US,"%04d%02d%02d",d/10000,d/100%100,d%100);}
    private static int parseInt(String t,int dflt){try{return t.trim().length()==0?dflt:Integer.parseInt(t.trim());}catch(NumberFormatException e){return dflt;}}

    private static void enter(final Activity a,final MaintItems.Store s,final MaintItems.Id id,float odo,final Runnable after){
        final MaintItems.Rec cur=MaintItems.load(s,id);
        final EditText km=num(a,UiText.t("교환 때 주행거리 km"),cur.km>=0?String.valueOf(cur.km):Float.isNaN(odo)?"":String.valueOf(Math.round(odo)));
        final EditText date=num(a,"yyyymmdd",ymd(cur.date>0?cur.date:MaintItems.today()));
        final EditText dot=num(a,UiText.t("DOT 4자리 (주·연, 예 2324)"),cur.dot>0?String.format(Locale.US,"%04d",cur.dot):"");
        boolean tire=id==MaintItems.Id.TIRE_REPLACE;
        LinearLayout v=tire?column(a,UiText.t("교환 때 주행거리 km"),km,UiText.t("날짜 (yyyymmdd)"),date,UiText.t("타이어 DOT"),dot):column(a,UiText.t("교환 때 주행거리 km"),km,UiText.t("날짜 (yyyymmdd)"),date);
        new AlertDialog.Builder(a).setTitle(UiText.t(MaintenanceCluster.name(id))).setView(v).setNegativeButton(UiText.t("취소"),null)
            .setPositiveButton(UiText.t("확인"),new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){
                MaintItems.Rec r=MaintItems.load(s,id);r.km=parseInt(km.getText().toString(),-1);r.date=MaintItems.parseDate(date.getText().toString());
                if(id==MaintItems.Id.TIRE_REPLACE)r.dot=parseInt(dot.getText().toString(),0);
                if(r.km<0&&r.date<=0){Toast.makeText(a,UiText.t("주행거리나 날짜 중 하나는 넣어야 합니다"),Toast.LENGTH_SHORT).show();return;}
                record(s,id,r);after.run();}}).show();
    }
    private static void dotDialog(final Activity a,final MaintItems.Store s,final MaintItems.Rec r,final MaintItems.Id id,final Runnable after){
        final EditText dot=num(a,UiText.t("DOT 4자리 (주·연, 예 2324)"),"");
        new AlertDialog.Builder(a).setTitle(UiText.t("타이어 DOT")).setView(column(a,UiText.t("타이어 옆면 DOT 끝 4자리 (비워도 됨)"),dot)).setNegativeButton(UiText.t("건너뛰기"),new DialogInterface.OnClickListener(){
            public void onClick(DialogInterface d,int w){record(s,id,r);after.run();}})
            .setPositiveButton(UiText.t("확인"),new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){r.dot=parseInt(dot.getText().toString(),0);record(s,id,r);after.run();}}).show();
    }
    private static void interval(final Activity a,final MaintItems.Store s,final MaintItems.Id id,final Runnable after){
        final MaintItems.Rec cur=MaintItems.load(s,id);
        final EditText km=num(a,"km",String.valueOf(cur.everyKm>=0?cur.everyKm:MaintItems.defaultKm(id,false)));
        final EditText mo=num(a,UiText.t("개월"),String.valueOf(cur.everyMo>=0?cur.everyMo:MaintItems.defaultMonths(id,false)));
        new AlertDialog.Builder(a).setTitle(UiText.t("주기 바꾸기")+" · "+UiText.t(MaintenanceCluster.name(id)))
            .setView(column(a,UiText.t("km마다 (0 = 거리 없음)"),km,UiText.t("개월마다 (0 = 기간 없음)"),mo))
            .setNeutralButton(UiText.t("기본값으로"),new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){
                MaintItems.Rec r=MaintItems.load(s,id);r.everyKm=-1;r.everyMo=-1;MaintItems.save(s,id,r);after.run();}})
            .setNegativeButton(UiText.t("취소"),null)
            .setPositiveButton(UiText.t("확인"),new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){
                MaintItems.Rec r=MaintItems.load(s,id);r.everyKm=Math.max(0,parseInt(km.getText().toString(),0));r.everyMo=Math.max(0,parseInt(mo.getText().toString(),0));
                MaintItems.save(s,id,r);after.run();}}).show();
    }
    interface DateSink { void set(int yyyymmdd); }
    static void dateDialog(final Activity a,String title,int cur,final DateSink sink){
        final EditText date=num(a,"yyyymmdd",ymd(cur));
        new AlertDialog.Builder(a).setTitle(title).setView(column(a,UiText.t("날짜 (yyyymmdd)"),date)).setNegativeButton(UiText.t("취소"),null)
            .setPositiveButton(UiText.t("확인"),new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){
                int v=MaintItems.parseDate(date.getText().toString());if(v==0){Toast.makeText(a,UiText.t("날짜를 yyyymmdd로 넣으세요"),Toast.LENGTH_SHORT).show();return;}sink.set(v);}}).show();
    }
    static void mode(final Activity a,final MaintItems.Store s,String verdict,final Runnable after){
        String[] modes={UiText.t("자동 (주행 기록으로 판단)"),UiText.t("일반"),UiText.t("가혹 (비포장 · 견인 · 영업용 등)")};
        new AlertDialog.Builder(a).setTitle(verdict).setSingleChoiceItems(modes,MaintJudge.mode(s).ordinal(),new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){
            MaintJudge.setMode(s,MaintJudge.Mode.values()[w]);d.dismiss();after.run();}}).setNegativeButton(UiText.t("취소"),null).show();
    }
}
