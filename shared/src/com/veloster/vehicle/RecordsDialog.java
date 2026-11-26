package com.veloster.vehicle;

import com.veloster.i18n.UiText;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.Locale;

/** 기록실: the kept records (Records) as a list — the three acceleration runs, 100-0 braking, 400 m, then each circuit that has a lap. */
public final class RecordsDialog {
    private RecordsDialog() {}

    public static void show(final Activity a){
        StringBuilder b=new StringBuilder();
        for(int i=0;i<DriveStats.RUNS.length;i++)
            section(b,String.format(Locale.US,"%.0f-%.0f km/h",DriveStats.RUNS[i][0],DriveStats.RUNS[i][1]),Records.entries(Records.runKey(i)),false);
        section(b,"100-0 km/h",Records.entries(Records.BRAKE),"%.1f m");
        section(b,"100-0 km/h ("+UiText.t("시간")+")",Records.entries(Records.BRAKE_TIME),"%.2f s");
        section(b,"400 m",Records.entries(Records.QUARTER),"%.2f s");
        java.util.ArrayList<long[]> routes=Commute.routes();   // 출퇴근 비교: routes driven at least twice
        if(!routes.isEmpty()){b.append(UiText.t("자주 다니는 길 (자동)")).append('\n');
            for(int i=0;i<routes.size();i++){long[] r=routes.get(i);
                b.append(String.format(Locale.US," %d. %s %2d · %s %s · %s %s · %s %s\n",i+1,UiText.t("횟수"),r[0],UiText.t("최고"),Commute.clock(r[1]),
                    UiText.t("평균"),Commute.clock(r[2]),UiText.t("최근"),Commute.clock(r[3])));}
            b.append('\n');}
        for(int i=0;i<Circuits.count();i++){
            String[] laps=Records.entries(Records.lapKey(Circuits.name(i)));
            if(laps.length>0)section(b,UiText.t(Circuits.name(i)),laps,true);
        }
        TextView text=new TextView(a);text.setText(b.toString().trim());text.setTextSize(17);text.setTextColor(0xffffffff);
        text.setTypeface(Typeface.MONOSPACE);text.setLineSpacing(6,1);
        int pad=(int)(18*a.getResources().getDisplayMetrics().density);text.setPadding(pad,pad/2,pad,pad/2);
        ScrollView scroll=new ScrollView(a);scroll.addView(text);
        new AlertDialog.Builder(a).setTitle(UiText.t("기록실")).setView(scroll)
            .setPositiveButton(UiText.t("닫기"),null)
            .setNeutralButton(UiText.t("기록 지우기"),new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){
                new AlertDialog.Builder(a).setMessage(UiText.t("저장된 기록을 모두 지울까요?"))
                    .setPositiveButton(UiText.t("지우기"),new DialogInterface.OnClickListener(){public void onClick(DialogInterface d2,int w2){Records.clear();}})
                    .setNegativeButton(UiText.t("취소"),null).show();
            }}).show();
    }
    /** A list shown with its own unit (format of the value). */
    private static void section(StringBuilder b,String title,String[] entries,String format){
        b.append(title).append('\n');
        if(entries.length==0)b.append("   —\n");
        for(int i=0;i<entries.length;i++)
            b.append(String.format(Locale.US," %d. %8s   %s\n",i+1,String.format(Locale.US,format,Records.value(entries[i])),Records.date(entries[i])));
        b.append('\n');
    }
    private static void section(StringBuilder b,String title,String[] entries,boolean lap){
        b.append(title).append('\n');
        if(entries.length==0)b.append("   —\n");
        for(int i=0;i<entries.length;i++){
            float v=Records.value(entries[i]);
            b.append(String.format(Locale.US," %d. %8s   %s\n",i+1,lap?LapTimer.format(Math.round(v*1000)):String.format(Locale.US,"%.2f s",v),Records.date(entries[i])));
        }
        b.append('\n');
    }
}
