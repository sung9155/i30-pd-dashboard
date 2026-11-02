package com.veloster.vehicle;

import com.veloster.i18n.UiText;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import java.util.Locale;

/** Draws six or twelve cards below the shared navigation. */
public final class GaugeRenderer {
    /** Every colour goes through the selected theme (identity for N). */
    private static int T(int c){return Theme.current().map(c);}
    private static int D(){return Theme.current().danger;}
    private final Paint p=new Paint(3);
    private static final int BLUE=0xffa4d9ef,WHITE=0xffe1edf2,MUTED=0xff8299a4,LINE=0xff293943;
    private void text(Canvas c,String s,float x,float y,float size,int color){p.setStyle(Paint.Style.FILL);p.setColor(T(color));p.setTypeface(Typeface.DEFAULT);p.setTextAlign(Paint.Align.CENTER);p.setTextSize(size);c.drawText(s,x,y,p);}
    /** Centered numeric readout with a fixed digit cell, so a narrow 1 does not make the whole value jitter as it changes. */
    private void number(Canvas c,String s,float cx,float y,float size,int color){
        p.setStyle(Paint.Style.FILL);p.setColor(T(color));p.setTypeface(Typeface.DEFAULT);p.setTextAlign(Paint.Align.LEFT);p.setTextSize(size);
        float dw=0;for(char d='0';d<='9';d++)dw=Math.max(dw,p.measureText(String.valueOf(d)));
        float total=0;for(int k=0;k<s.length();k++){char ch=s.charAt(k);total+=ch>='0'&&ch<='9'?dw:p.measureText(String.valueOf(ch));}
        float x=cx-total/2;
        for(int k=0;k<s.length();k++){char ch=s.charAt(k);String g=String.valueOf(ch);float gw=p.measureText(g);
            if(ch>='0'&&ch<='9'){c.drawText(g,x+(dw-gw)/2,y,p);x+=dw;}else{c.drawText(g,x,y,p);x+=gw;}}
        p.setTextAlign(Paint.Align.CENTER);
    }
    private void line(Canvas c,float x,float y,float xx,float yy,int color,float width){p.setColor(T(color));p.setStrokeWidth(width);c.drawLine(x,y,xx,yy,p);}
    static float min(int i,String u){return "G".equals(u)?-2:"°C".equals(u)?-40:i==Telemetry.STEERING?-540:i==Telemetry.SPARK?-20:i==Telemetry.STEER_TORQUE?-5:i==Telemetry.YAW?-50:i==Telemetry.SHIFT_HINT?-7:0;}
    static float max(int i,String u){
        if(i>=Telemetry.TCS&&i<=Telemetry.ABS)return 1;
        if(i==Telemetry.ELSD)return 6553.5f;
        if(i==Telemetry.BAT_VOLTAGE)return 18;if(i==Telemetry.STEERING_SPEED)return 1020;
        if("psi".equals(u))return 60;
        if("rpm".equals(u))return 8000;if("km/h".equals(u))return 300;if("%".equals(u))return 100;
        if("°C".equals(u))return 160;if("G".equals(u))return 2;if("PS".equals(u)||"hp".equals(u))return 350;
        if(i!=Telemetry.STEER_TORQUE&&("Nm".equals(u)||"N·m".equals(u)))return 500;if("kPa".equals(u))return 250;
        if("bar".equals(u))return i==10?100:2.5f;if(i==Telemetry.STEERING)return 540;
        if(i==Telemetry.RANGE)return 800;if(i==Telemetry.SPARK)return 50;if(i==Telemetry.STEER_TORQUE)return 5;if(i==Telemetry.BRAKE_SWITCH||i>=Telemetry.DOOR_DRIVER)return i==Telemetry.WIPER?2:1;if("L/h".equals(u))return 40;if("km/L".equals(u))return 30;
        if(i==Telemetry.YAW)return 50;if(i==Telemetry.SHIFT_HINT)return 7;if(i==Telemetry.ODOMETER)return 999999;
        return i==8?7:100;   // gear: the 7DCT has 7 forward gears
    }
    private static String axis(float value){return String.format(Locale.US,value==(int)value?"%.0f":"%.1f",value);}
    public void draw(Canvas c,Telemetry data,long now,GaugeLayout layout){
        int save=c.save();c.clipRect(0,52,800,480);p.setColor(T(0xff070b0e));c.drawRect(0,52,800,480,p);
        text(c,UiText.t("게이지를 길게 눌러 항목 · 모양 · 위치 편집"),300,71,13,MUTED);
        p.setColor(T(0xff233b46));c.drawRoundRect(new RectF(650,54,788,78),5,5,p);
        text(c,layout.dense()?UiText.t("3 × 2 보기"):UiText.t("4 × 3 보기"),719,71,14,BLUE);
        Telemetry.Sample[] samples=data.snapshot(now);
        for(int slot=0;slot<layout.count();slot++){
            int i=layout.signal(slot),style=layout.style(slot);Telemetry.Sample s=samples[i];
            String u=Telemetry.UNITS[i];float l=layout.left(slot),t=layout.top(slot),cx=l+layout.width()/2;
            // Scale the same card design uniformly; dense cards remain centered in their wider cell.
            int cardSave=c.save();float scale=layout.dense()?124f/190:1;
            c.translate(cx-126*scale,t);c.scale(scale,scale);l=0;t=0;cx=126;
            p.setStyle(Paint.Style.FILL);p.setColor(T(0xff101920));c.drawRoundRect(new RectF(l,t,l+252,t+190),10,10,p);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1);p.setColor(T(LINE));c.drawRoundRect(new RectF(l,t,l+252,t+190),10,10,p);p.setStyle(Paint.Style.FILL);
            text(c,UiText.t(Telemetry.LABELS[i]),cx,t+25,17,BLUE);
            boolean ok=s.valid&&!Float.isNaN(s.value)&&!Float.isInfinite(s.value);
            float displayed=Telemetry.displayValue(i,s.value);
            String named=ok?Telemetry.label(i,s.value):null;
            String value=ok?(named!=null?named:String.format(Locale.US,"rpm".equals(u)||"km/h".equals(u)||"km".equals(u)?"%.0f":"G".equals(u)?"%+.2f":i==Telemetry.STEERING?"%+.1f":"%.1f",displayed)):"—";
            float lo=min(i,u),hi=max(i,u),f=ok?Math.max(0,Math.min(1,(displayed-lo)/(hi-lo))):0;
            if(style==0){
                float cy=t+107,r=63;
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(5);p.setColor(T(LINE));RectF arc=new RectF(cx-r,cy-r,cx+r,cy+r);c.drawArc(arc,150,240,false,p);
                if(ok){p.setColor(T(BLUE));c.drawArc(arc,150,240*f,false,p);}
                for(int tick=0;tick<=8;tick++){double a=Math.toRadians(150+30*tick);line(c,cx+(float)Math.cos(a)*54,cy+(float)Math.sin(a)*54,cx+(float)Math.cos(a)*60,cy+(float)Math.sin(a)*60,MUTED,1);}
                if(ok){double a=Math.toRadians(150+240*f);line(c,cx,cy,cx+(float)Math.cos(a)*48,cy+(float)Math.sin(a)*48,WHITE,2);p.setStyle(Paint.Style.FILL);p.setColor(T(BLUE));c.drawCircle(cx,cy,4,p);}
                text(c,axis(lo),cx-69,t+155,10,MUTED);text(c,axis(hi),cx+69,t+155,10,MUTED);
                number(c,value,cx,t+163,29,WHITE);text(c,u,cx,t+181,12,MUTED);
            }else{
                number(c,value,cx,t+103,48,WHITE);text(c,u,cx,t+129,17,MUTED);
                if(style==1){p.setColor(T(LINE));c.drawRect(l+20,t+147,l+232,t+159,p);if(ok){p.setColor(T(BLUE));c.drawRect(l+20,t+147,l+20+212*f,t+159,p);}text(c,axis(lo),l+25,t+178,11,MUTED);text(c,axis(hi),l+226,t+178,11,MUTED);}
            }
            c.restoreToCount(cardSave);
        }
        c.restoreToCount(save);
    }
}
