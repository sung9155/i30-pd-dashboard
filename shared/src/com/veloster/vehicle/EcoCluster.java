package com.veloster.vehicle;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import java.util.Locale;

/**
 * Eco coach: score ring, instant km/L bar against the trip average, fuel used, "free" distance driven on fuel cut
 * (engine braking), and the last 20 s of accelerator pedal from the telemetry history (kept while the tab is hidden).
 * ponytail: the score is a smoothness heuristic (pedal jerk, heavy load, hard braking, fuel cut), to be tuned on car logs.
 */
final class EcoCluster extends ClusterKit {
    private static final int BG=0xff060b08,LEAF=0xff7bd389,SAND=0xffd9c99a,WARN=0xffe0876a,TX=0xffdfe9e2,MU=0xff86a08f,CARD=0xff0d1711,EDGE=0xff1d3326;
    private float freeKm,fuelL,km,score=70;
    private long last;
    private final RectF arc=new RectF();

    private long tickAt;
    /** Distance, fuel and fuel-cut distance count every frame, whatever page shows. */
    @Override protected void tick(){
        float v=v(7),fuel=v(Telemetry.FUEL_RATE);
        float dt=tickAt==0||now-tickAt>2000?0:(now-tickAt)/1000f;tickAt=now;
        if(!Float.isNaN(v))km+=v*dt/3600;
        if(!Float.isNaN(fuel))fuelL+=fuel*dt/3600;
        if(!Float.isNaN(fuel)&&fuel==0&&!Float.isNaN(v)&&v>0)freeKm+=v*dt/3600;
    }
    @Override protected void draw(Canvas c){
        float v=v(7),fuel=v(Telemetry.FUEL_RATE),pedal=v(9),brake=v(10),avg=v(Telemetry.AVG_FUEL),range=v(Telemetry.RANGE);
        float dt=last==0||now-last>2000?0:(now-last)/1000f;last=now;
        boolean cut=!Float.isNaN(fuel)&&fuel==0&&!Float.isNaN(v)&&v>0;
        Telemetry.Series ph=model==null?null:model.history(9,now,20000);
        float jerk=jerk(ph);
        float target=clamp(100-Math.min(45,jerk*0.9f)-(!Float.isNaN(pedal)&&pedal>80?30:!Float.isNaN(pedal)&&pedal>50?12:0)-(!Float.isNaN(brake)&&brake>35?20:0)+(cut?10:0),0,100);
        score+=(target-score)*Math.min(1,dt*0.35f);
        fill(c,0,52,800,480,BG);
        // score ring
        float cx=128,cy=196,rad=84;int ringCol=score>70?LEAF:score>45?SAND:WARN;
        arc.set(cx-rad,cy-rad,cx+rad,cy+rad);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(16);p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor(0xff14231a);c.drawArc(arc,135,270,false,p);p.setColor(ringCol);c.drawArc(arc,135,270*score/100,false,p);
        p.setStrokeCap(Paint.Cap.BUTT);p.setStyle(Paint.Style.FILL);
        bold(c,String.valueOf(Math.round(score)),cx,cy+20,64,TX,Paint.Align.CENTER);
        bold(c,t("에코 점수"),cx,cy+46,14,MU,Paint.Align.CENTER);
        for(int i=0;i<5;i++){boolean on=score>=(i+1)*20-5;float x=52+i*40,y=322;round(c,x,y-14,x+18,y+14,9,on?LEAF:0xff16261c);line(c,x+4,y+10,x+14,y-10,1.5f,BG);}
        text(c,t("잎 1개 = 20점"),128,356,12,MU,Paint.Align.CENTER,Typeface.DEFAULT);
        // instant economy
        boolean stopped=Float.isNaN(v)||v<1;
        float inst=stopped||Float.isNaN(fuel)?Float.NaN:fuel<0.05f?Float.POSITIVE_INFINITY:v/fuel;
        bold(c,t("순간 연비"),270,86,14,MU,Paint.Align.LEFT);
        if(stopped)bold(c,t("정차 · 공회전 ")+fmt("%.1f",fuel)+" L/h",270,134,34,TX,Paint.Align.LEFT);
        else if(Float.isInfinite(inst))bold(c,t("연료 차단"),270,134,40,LEAF,Paint.Align.LEFT);
        else{String s=fmt("%.1f",inst);bold(c,s,270,136,58,TX,Paint.Align.LEFT);bold(c,"km/L",278+width(s,58,Typeface.DEFAULT_BOLD),136,18,MU,Paint.Align.LEFT);}
        float gx=270,gw=500,gy=154;
        for(int i=0;i<30;i++)fill(c,gx+i*gw/30+1,gy,gx+(i+1)*gw/30-1,gy+20,i<6?0xff5a2a22:i<12?0xff5a4a22:0xff1f4a2e);
        float fv=Float.isNaN(inst)?0:Math.min(30,inst);
        for(int i=0;i<Math.round(fv);i++)fill(c,gx+i*gw/30+1,gy,gx+(i+1)*gw/30-1,gy+20,i<6?WARN:i<12?SAND:LEAF);
        for(int k=0;k<=30;k+=10)text(c,String.valueOf(k),gx+k/30f*gw,gy-6,12,MU,Paint.Align.CENTER,Typeface.DEFAULT);
        if(!Float.isNaN(avg)){float ax=gx+Math.min(30,avg)/30*gw;fill(c,ax-1,gy-6,ax+1,gy+26,TX);bold(c,t("트립 평균 ")+fmt("%.1f",avg),ax,gy+44,13,TX,Paint.Align.CENTER);}
        // stat cards
        card(c,270,t("트립 평균"),fmt("%.1f",avg),"km/L",t("차량 계산값"));
        card(c,440,t("사용 연료"),String.format(Locale.US,"%.2f",fuelL),"L",fmt("%.1f",fuel)+t(" L/h 지금"));
        card(c,610,t("공짜 주행"),String.format(Locale.US,"%.2f",freeKm),"km",t("연료 차단 · ")+(km>0.05f?Math.round(freeKm/km*100):0)+"%");
        // pedal, last 20 s
        float x0=270,w=500,y0=348,h=88;
        bold(c,t("가속 페달 · 최근 20초"),x0,340,13,MU,Paint.Align.LEFT);
        String grade=score>80?"A":score>65?"B":score>45?"C":"D";
        bold(c,t("부드러움 ")+grade,x0+w,340,13,ringCol,Paint.Align.RIGHT);
        fill(c,x0,y0,x0+w,y0+h,0xff0b130e);
        for(int k=25;k<100;k+=25)fill(c,x0,y0+h-k/100f*h,x0+w,y0+h-k/100f*h+1,0xff12221a);
        if(ph!=null){float px=Float.NaN,py=0;
            for(int i=0;i<ph.size();i++){if(!ph.valid[i]){px=Float.NaN;continue;}
                float x=x0+w-(now-ph.times[i])/20000f*w,y=y0+h-clamp(ph.values[i],0,100)/100*h;
                if(x<x0)continue;fill(c,x,y,x+2,y0+h,0x307bd389);
                if(!Float.isNaN(px)&&!ph.breakBefore[i])line(c,px,py,x,y,2,LEAF);px=x;py=y;}
        }
        // coach line
        round(c,20,374,250,466,8,CARD);outline(c,20,374,250,466,8,1,EDGE);
        bold(c,t("코치"),34,396,13,LEAF,Paint.Align.LEFT);
        String[] tip=tip(stopped,cut,pedal,brake,inst,fuel);
        bold(c,tip[0],34,422,15,TX,Paint.Align.LEFT);bold(c,tip[1],34,444,15,TX,Paint.Align.LEFT);
        bold(c,t("주행가능 ")+fmt("%.0f",range)+" km",770,466,13,MU,Paint.Align.RIGHT);
    }
    private String[] tip(boolean stopped,boolean cut,float pedal,float brake,float inst,float fuel){
        if(stopped)return new String[]{t("공회전 중"),fmt("%.1f",fuel)+t(" L/h를 씁니다")};
        if(cut)return new String[]{t("연료 차단 중"),t("지금은 연료를 안 씁니다")};
        if(!Float.isNaN(brake)&&brake>35)return new String[]{t("급제동"),t("미리 발을 떼면 더 아낍니다")};
        if(!Float.isNaN(pedal)&&pedal>80)return new String[]{t("급가속"),Float.isNaN(inst)?"":t("지금 연비 ")+fmt("%.1f",inst)+" km/L"};
        if(!Float.isNaN(pedal)&&pedal>50)return new String[]{t("가속"),t("페달 50% 이하로 충분합니다")};
        return new String[]{t("정속 주행"),t("좋습니다")};
    }
    private void card(Canvas c,float x,String label,String val,String unit,String sub){
        round(c,x,226,x+158,320,8,CARD);outline(c,x,226,x+158,320,8,1,EDGE);
        bold(c,label,x+12,248,13,MU,Paint.Align.LEFT);
        bold(c,val,x+12,292,38,TX,Paint.Align.LEFT);bold(c,unit,x+18+width(val,38,Typeface.DEFAULT_BOLD),292,15,MU,Paint.Align.LEFT);
        text(c,sub,x+12,310,12,MU,Paint.Align.LEFT,Typeface.DEFAULT);
    }
    /** Mean pedal change per second over the window (0 when there is no history). */
    private static float jerk(Telemetry.Series s){
        if(s==null)return 0;float sum=0;int n=0;
        for(int i=1;i<s.size();i++)if(s.valid[i]&&s.valid[i-1]&&!s.breakBefore[i]&&s.times[i]>s.times[i-1]){sum+=Math.abs(s.values[i]-s.values[i-1])/((s.times[i]-s.times[i-1])/1000f);n++;}
        return n==0?0:sum/n;
    }
}
