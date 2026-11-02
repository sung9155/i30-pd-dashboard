package com.veloster.vehicle;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import java.util.Locale;

/**
 * Oscilloscope: XY mode draws lateral against longitudinal G with a 3 s afterglow, a yellow rpm trace runs across
 * the top two divisions, and a frequency counter shows the crank frequency (rpm / 60). Traces come from the
 * telemetry history, so they are already there when the tab opens.
 */
final class ScopeCluster extends ClusterKit {
    private static final int G=0xff57ff9a,YEL=0xffffd65a,MU=0xff9aa0a6;
    private static final float SX=40,SY=74,DV=48,SW=DV*10,SH=DV*8;
    private float maxLat;

    @Override protected void draw(Canvas c){
        float rpm=v(0),lat=v(11),lon=v(12);
        if(!Float.isNaN(lat))maxLat=Math.max(maxLat,Math.abs(lat));
        fill(c,0,52,800,480,0xff1b1d20);
        round(c,14,58,546,474,18,0xff0a0b0c);outline(c,14,58,546,474,18,2,0xff33373c);
        round(c,SX-6,SY-6,SX+SW+6,SY+SH+6,10,0xff041a10);
        for(int i=0;i<=10;i++)fill(c,SX+i*DV,SY,SX+i*DV+1,SY+SH,0x2457ff9a);
        for(int j=0;j<=8;j++)fill(c,SX,SY+j*DV,SX+SW,SY+j*DV+1,0x2457ff9a);
        for(int k=0;k<=50;k++){float x=SX+k*DV/5;fill(c,x,SY+SH/2-3,x+1,SY+SH/2+3,0x4d57ff9a);}
        for(int k=0;k<=40;k++){float y=SY+k*DV/5;fill(c,SX+SW/2-3,y,SX+SW/2+3,y+1,0x4d57ff9a);}
        // CH3: rpm, last 5 s across the screen, top two divisions = 0..8000
        Telemetry.Series hr=model==null?null:model.history(0,now,5000);
        if(hr!=null){float px=Float.NaN,py=0;
            for(int i=0;i<hr.size();i++){if(!hr.valid[i]){px=Float.NaN;continue;}
                float x=SX+SW-(now-hr.times[i])/5000f*SW,y=SY+2*DV-clamp(hr.values[i]/8000,0,1)*2*DV;
                if(!Float.isNaN(px)&&!hr.breakBefore[i])line(c,px,py,x,y,1.5f,0xbfffd65a);px=x;py=y;}
        }
        // XY afterglow, 0.2 g/div
        Telemetry.Series hl=model==null?null:model.history(11,now,3000),ho=model==null?null:model.history(12,now,3000);
        if(hl!=null&&ho!=null){int n=Math.min(hl.size(),ho.size());float px=Float.NaN,py=0;
            for(int i=0;i<n;i++){if(!hl.valid[i]||!ho.valid[i]){px=Float.NaN;continue;}
                float x=xg(hl.values[i]),y=yg(ho.values[i]),age=(now-hl.times[i])/3000f;
                if(!Float.isNaN(px))line(c,px,py,x,y,2.4f,((int)(clamp(1-age,0,1)*0.85f*255)<<24)|0x57ff9a);px=x;py=y;}
            if(!Float.isNaN(px)&&!Float.isNaN(lat)&&!Float.isNaN(lon))line(c,px,py,xg(lat),yg(lon),2.4f,G);
        }
        if(!Float.isNaN(lat)&&!Float.isNaN(lon)){dot(c,xg(lat),yg(lon),7,0x5557ff9a);dot(c,xg(lat),yg(lon),4,0xffe9fff2);}
        text(c,t("CH1 횡G 0.2 g/div"),SX+6,SY+SH-28,14,G,Paint.Align.LEFT,Typeface.MONOSPACE);
        text(c,t("CH2 종G 0.2 g/div"),SX+6,SY+SH-10,14,G,Paint.Align.LEFT,Typeface.MONOSPACE);
        mono(c,"CH3 RPM 4000/div",SX+6,SY+16,14,YEL,Paint.Align.LEFT);
        text(c,t("XY · 잔상 3 s"),SX+SW-6,SY+16,14,G,Paint.Align.RIGHT,Typeface.MONOSPACE);
        text(c,t("최대 횡G")+String.format(Locale.US," %.2f",maxLat),SX+SW-6,SY+SH-28,14,G,Paint.Align.RIGHT,Typeface.MONOSPACE);
        mono(c,"TRIG AUTO",SX+SW-6,SY+SH-10,14,G,Paint.Align.RIGHT);
        // instrument panel
        bold(c,"i30 PD",566,82,18,0xffe6e8ea,Paint.Align.LEFT);bold(c,"DUAL TRACE OSCILLOSCOPE",566,98,11,MU,Paint.Align.LEFT);
        dot(c,776,78,5,G);bold(c,"POWER",766,98,10,MU,Paint.Align.RIGHT);
        round(c,562,110,784,184,6,0xff020a06);outline(c,562,110,784,184,6,1,0xff2c3a32);
        seg7(c,Float.isNaN(rpm)?"--.-":String.format(Locale.US,"%.1f",rpm/60),744,122,42,G,0xff0a2216,false);
        bold(c,"Hz",774,164,14,G,Paint.Align.RIGHT);text(c,t("엔진 회전 주파수 = RPM ÷ 60"),570,178,11,0xff4f8a68,Paint.Align.LEFT,Typeface.DEFAULT);
        knob(c,610,238,-30,"VOLTS/DIV CH1","0.2 g");knob(c,736,238,-30,"VOLTS/DIV CH2","0.2 g");
        knob(c,610,338,40,"TIME/DIV",t("잔상 3 s"));knob(c,736,338,Float.isNaN(rpm)?-135:clamp(rpm/8000,0,1)*270-135,"CH3 RPM",fmt("%,.0f",rpm));
        String[] b={"XY","CH1","CH2","CH3"};
        for(int i=0;i<4;i++){float x=566+i*55;boolean on=i==0;round(c,x,414,x+48,436,4,on?0xff1e5a3a:0xff2a2d31);outline(c,x,414,x+48,436,4,1,0xff44484d);
            mono(c,b[i],x+24,430,12,on?0xff9dffc6:0xffb9bdc2,Paint.Align.CENTER);}
        bold(c,fmt("%.0f",v(7))+" km/h · "+gear(),780,466,15,0xffd8dadc,Paint.Align.RIGHT);
    }
    private static float xg(float lat){return SX+SW/2-clamp(lat,-1,1)/0.2f*DV;}
    private static float yg(float lon){return SY+SH/2-clamp(lon,-0.8f,0.8f)/0.2f*DV;}
    private void knob(Canvas c,float x,float y,float ang,String label,String val){
        dot(c,x,y,27,0xff111214);dot(c,x,y,22,0xff34373c);dot(c,x-4,y-4,12,0xff44484d);
        line(c,x,y,px(x,18,ang),py(y,18,ang),2.5f,0xffe6e8ea);
        for(int k=-135;k<=135;k+=27)line(c,px(x,30,k),py(y,30,k),px(x,34,k),py(y,34,k),1,0xff5a5e64);
        bold(c,label,x,y+48,10,MU,Paint.Align.CENTER);mono(c,val,x,y+62,12,0xffd8dadc,Paint.Align.CENTER);
    }
}
