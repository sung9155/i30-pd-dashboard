package com.veloster.vehicle;

import com.veloster.i18n.UiText;
import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Trouble-code list: reads every ECU once when opened and lists codes with their meaning; an ECU with codes gets a
 *  지우기 button (not the airbag unit), which asks first and works only while the car is seen standing. */
public final class DtcDialog {
    private DtcDialog() {}

    public static void show(final Activity a){
        final LinearLayout list=new LinearLayout(a);list.setOrientation(LinearLayout.VERTICAL);
        int pad=(int)(16*a.getResources().getDisplayMetrics().density);list.setPadding(pad,pad/2,pad,pad);
        ScrollView scroll=new ScrollView(a);scroll.addView(list);
        final AlertDialog dialog=new AlertDialog.Builder(a).setTitle(UiText.t("고장코드 조회")).setView(scroll)
            .setPositiveButton(UiText.t("다시 조회"),null).setNegativeButton(UiText.t("닫기"),null).create();
        final Runnable[] read=new Runnable[1];
        read[0]=new Runnable(){public void run(){
            list.removeAllViews();
            final TextView progress=line(a,list,UiText.t("조회 중…"),16,0xffcfd8dc,false);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            new Thread(new Runnable(){public void run(){
                List<DtcReader.Result> results=null;String error=null;
                try{results=DtcReader.readAll(new DtcReader.Progress(){public void step(final String ecu){
                    a.runOnUiThread(new Runnable(){public void run(){progress.setText(UiText.t("조회 중…")+" "+UiText.t(ecu));}});
                }});}
                catch(Exception e){android.util.Log.e("DtcDialog","read failed",e);error=String.valueOf(e.getMessage());}
                final List<DtcReader.Result> done=results;final String failed=error;
                a.runOnUiThread(new Runnable(){public void run(){
                    if(!dialog.isShowing())return;
                    list.removeAllViews();
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                    if(done==null){line(a,list,UiText.t("진단 서비스에 연결하지 못했습니다.")+"\n"+failed,15,0xffff8a80,false);return;}
                    render(a,list,done,read[0]);
                }});
            }},"DtcRead").start();
        }};
        dialog.setOnShowListener(new android.content.DialogInterface.OnShowListener(){public void onShow(android.content.DialogInterface d){
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener(){public void onClick(View v){read[0].run();}});
            read[0].run();
        }});
        dialog.show();
    }

    private static void render(final Activity a,LinearLayout list,List<DtcReader.Result> results,final Runnable reread){
        int total=0,answered=0;
        for(DtcReader.Result r:results)if(r.codes!=null){total+=r.codes.size();answered++;}
        if(answered==0)line(a,list,UiText.t("응답한 제어기가 없습니다. 시동을 켠 상태에서 다시 조회하세요."),17,0xffff8a80,true);
        else line(a,list,total==0?UiText.t("저장된 고장코드가 없습니다."):UiText.t("고장코드")+" "+total,17,total==0?0xff69f0ae:0xffffd54f,true);
        for(DtcReader.Result r:results){
            String head=UiText.t(r.ecu)+" — "+(r.codes!=null?(r.codes.isEmpty()?UiText.t("없음"):r.codes.size()+""):UiText.t(r.error)+(r.detail==null?"":" ("+r.detail+")"));
            line(a,list,head,15,0xff90a4ae,true).setPadding(0,dp(a,14),0,dp(a,4));
            if(r.codes==null)continue;
            if(!r.codes.isEmpty()&&DtcReader.clearable(r.ecu)){final String ecu=r.ecu;
                TextView clear=line(a,list,UiText.t("이 제어기 고장코드 지우기")+"  ›",15,0xff80d8ff,true);clear.setPadding(0,dp(a,6),0,dp(a,2));
                clear.setOnClickListener(new View.OnClickListener(){public void onClick(View v){confirmClear(a,ecu,reread);}});}
            Collections.sort(r.codes,new Comparator<Dtc>(){public int compare(Dtc x,Dtc y){return y.severity()-x.severity();}});
            for(Dtc d:r.codes){
                String state=d.statusText();
                line(a,list,d.code+(state.isEmpty()?"":"   "+state),16,d.status>=0&&(d.status&0x81)!=0?0xffff8a80:0xffffffff,true)
                    .setPadding(0,dp(a,8),0,0);
                String ftb=d.failureText();
                line(a,list,d.description()+(ftb==null?"":" — "+ftb),15,0xffcfd8dc,false);
            }
        }
        line(a,list,UiText.t("지우기는 차가 멈춰 있을 때만 됩니다. 고장이 그대로면 코드는 다시 생깁니다. 에어백 코드는 정비소에서 지우세요."),13,0xff78909c,false).setPadding(0,dp(a,16),0,0);
    }
    /** Asks, checks the car is standing, clears on a thread and reads every ECU again. */
    private static void confirmClear(final Activity a,final String ecu,final Runnable reread){
        float v=VehicleDash.speedNow();
        if(Float.isNaN(v)||v>=1){new AlertDialog.Builder(a).setMessage(UiText.t("차가 멈춘 것을 확인하지 못했습니다. 정차한 뒤 다시 누르세요.")).setPositiveButton(UiText.t("닫기"),null).show();return;}
        String msg=UiText.t(ecu)+" — "+UiText.t("저장된 고장코드를 모두 지웁니다.");
        if(ecu.startsWith("엔진"))msg+="\n\n"+UiText.t("엔진 코드를 지우면 배출가스 점검 준비 상태도 초기화돼, 몇 번 주행하기 전까지 정기검사 전산 점검에서 '미완료'로 나옵니다.");
        new AlertDialog.Builder(a).setTitle(UiText.t("고장코드 지우기")).setMessage(msg).setNegativeButton(UiText.t("취소"),null)
            .setPositiveButton(UiText.t("지우기"),new android.content.DialogInterface.OnClickListener(){public void onClick(android.content.DialogInterface d,int w){
                new Thread(new Runnable(){public void run(){
                    String text;
                    try{byte[] r=DtcReader.clear(ecu);
                        text=r==null?UiText.t("응답이 없습니다."):(r[0]&255)==0x54?UiText.t("지웠습니다. 다시 조회합니다."):UiText.t("제어기가 거부했습니다.")+" ("+DtcReader.hex(r)+")";}
                    catch(Exception e){text=UiText.t("진단 서비스에 연결하지 못했습니다.")+"\n"+e.getMessage();}
                    final String shown=text;
                    a.runOnUiThread(new Runnable(){public void run(){android.widget.Toast.makeText(a,shown,android.widget.Toast.LENGTH_LONG).show();reread.run();}});
                }},"DtcClear").start();
            }}).show();
    }
    private static int dp(Activity a,int v){return (int)(v*a.getResources().getDisplayMetrics().density);}
    private static TextView line(Activity a,LinearLayout list,String text,int sp,int color,boolean bold){
        TextView t=new TextView(a);t.setText(text);t.setTextSize(sp);t.setTextColor(color);
        if(bold)t.setTypeface(Typeface.DEFAULT_BOLD);
        list.addView(t);return t;
    }
}
