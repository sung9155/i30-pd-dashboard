package com.veloster.vehicle;

import com.veloster.i18n.UiText;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import java.util.Locale;

/** N Mode "performance" page of the N cars' head unit, laid out for the i30 PD below the shared navigation:
 * throttle / intake air / coolant on the left (no oil sensor on this engine), RPM strip + 7-speed gear dots + speed + selector and brake pressure in the
 * middle, G-force and a tap-driven lap timer on the right. The N page's circuit map needs GPS track data, so the
 * same blue-outline / red-dot card shows the G-ball instead. Only received samples are drawn; missing ones show a dash. */
public final class PerformanceRenderer {
    /** Every colour goes through the selected theme (identity for N). */
    private static int T(int c){return Theme.current().map(c);}
    private static int D(){return Theme.current().danger;}
    private final Paint p=new Paint(3);
    private final RectF r=new RectF();
    private static final int TEXT=0xfff2f6f8,MUTED=0xff8fa0a8,EDGE=0xff3a4a54,TRACK=0xff2a343b,BLUE=0xff8fd0f0,RED=0xffff4059;
    private VehicleProfile prof=VehicleProfile.PETROL_DCT;
    // 3 rows x 3 columns, pad 12, gap 8; the middle and right top cards span two rows.
    private static final float T0=64,T1=201,T2=339,B0=193,B1=331,B2=468,LL=12,LR=200,CL=208,CR=592,RL=600,RR=788;
    private Telemetry.Sample[] s;

    private float v(int i){return i>=0&&i<s.length&&s[i].valid&&!Float.isNaN(s[i].value)&&!Float.isInfinite(s[i].value)?Telemetry.displayValue(i,s[i].value):Float.NaN;}
    private static String fmt(String f,float x){return Float.isNaN(x)?"—":String.format(Locale.US,f,x);}

    public void draw(Canvas c,Telemetry.Sample[] samples,long lapMs,boolean lapRunning,VehicleProfile profile){
        s=samples;prof=profile;
        value(c,UiText.t("스로틀"),fmt("%.0f",v(Telemetry.THROTTLE_POS)),"%",LL,T0,LR,B0);
        value(c,UiText.t("흡기 온도"),fmt("%.0f",v(Telemetry.INTAKE)),"°C",LL,T1,LR,B1);
        value(c,UiText.t("냉각수 온도"),fmt("%.0f",v(2)),"°C",LL,T2,LR,B2);
        rpmCard(c);
        value(c,UiText.t("브레이크 압력"),fmt("%.1f",v(10)),"bar",CL,T2,CR,B2);
        gforce(c);
        long cs=lapMs/10;
        String lap=String.format(Locale.US,"%02d:%02d:%02d",cs/6000%100,cs/100%60,cs%100);
        value(c,UiText.t("랩 타이머"),lap,"s",RL,T2,RR,B2);
        text(c,UiText.t(lapRunning?"탭: 정지":lapMs>0?"탭: 초기화":"탭: 시작"),RL+14,T2+46,12,lapRunning?RED:MUTED,Paint.Align.LEFT);
    }
    /** Glass card: top-lit vertical gradient, 1 px edge, caption top-left. */
    private void card(Canvas c,String caption,float l,float t,float rr,float b){
        p.setStyle(Paint.Style.FILL);p.setShader(new LinearGradient(0,t,0,b,T(0xff1d262d),T(0xff0b0f12),Shader.TileMode.CLAMP));
        r.set(l,t,rr,b);c.drawRoundRect(r,6,6,p);p.setShader(null);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1);p.setColor(T(EDGE));c.drawRoundRect(r,6,6,p);p.setStyle(Paint.Style.FILL);
        text(c,caption,l+14,t+26,15,TEXT,Paint.Align.LEFT);
    }
    /** Caption top-left, numerals bottom-right with a small unit. */
    private void value(Canvas c,String caption,String value,String unit,float l,float t,float rr,float b){
        card(c,caption,l,t,rr,b);
        float unitX=rr-14;text(c,unit,unitX,b-20,18,TEXT,Paint.Align.RIGHT);
        p.setTextSize(18);p.setTypeface(Typeface.DEFAULT);
        text(c,value,unitX-p.measureText(unit)-6,b-20,value.length()>6?34:value.length()>4?40:52,TEXT,Paint.Align.RIGHT);
    }
    private void rpmCard(Canvas c){
        card(c,"",CL,T0,CR,B1);
        float rpm=v(0),x0=CL+24,x1=CR-70,ty=T0+40;
        // x1000 labels, 500 rpm ticks, red from the profile redline; the scale follows the profile's max rpm.
        int ticks=Math.round(prof.maxRpm/500f),lblEvery=prof.maxRpm<=6000?2:4;
        for(int i=0;i<=ticks;i++){
            float x=x0+(x1-x0)*i/(float)ticks;boolean major=i%2==0,red=i*500>=prof.redline;
            if(major&&i%lblEvery==0)text(c,String.valueOf(i/2),x,ty,15,red?RED:TEXT,Paint.Align.CENTER);
            p.setColor(T(red?RED:major?TEXT:MUTED));c.drawRect(x-(major?1.5f:1),ty+8,x+(major?1.5f:1),ty+(major?24:18),p);
        }
        float by=ty+32;r.set(x0,by,x1,by+16);p.setColor(T(TRACK));c.drawRoundRect(r,3,3,p);
        if(!Float.isNaN(rpm)){
            float f=Math.max(0,Math.min(1,rpm/prof.maxRpm)),xr=x0+(x1-x0)*f,xred=x0+(x1-x0)*prof.redline/prof.maxRpm;
            p.setShader(new LinearGradient(0,by,0,by+16,T(0xffffffff),T(0xffaebfc8),Shader.TileMode.CLAMP));
            r.set(x0,by,Math.min(xr,xred),by+16);c.drawRoundRect(r,3,3,p);p.setShader(null);
            if(xr>xred){p.setColor(T(RED));r.set(xred,by,xr,by+16);c.drawRoundRect(r,3,3,p);}
        }
        // gear dots: topGear at the top (7 on the DCT, 6 on the 6MT), current gear lit.
        float gear=v(8),gx=CR-30,gt=T0+28,gb=B1-46;int top=prof.topGear;
        text(c,String.valueOf(top),gx,gt,13,TEXT,Paint.Align.CENTER);text(c,"1",gx,gb+22,13,TEXT,Paint.Align.CENTER);
        for(int g=1;g<=top;g++){
            float y=gb-(gb-gt-12)*(g-1)/(float)(top-1);boolean on=!Float.isNaN(gear)&&(int)gear==g;
            p.setColor(T(on?TEXT:0xff4a5860));c.drawCircle(gx,y,on?6:4.5f,p);
        }
        float speed=v(7),sel=v(Telemetry.GEAR_SELECTOR),base=B1-40;
        String sp=fmt("%.0f",speed);
        text(c,"km/h",CL+156,base,16,TEXT,Paint.Align.LEFT);
        text(c,sp,CL+150,base,76,TEXT,Paint.Align.RIGHT);
        // Selector as on the N page: P / R / N letters, the gear number in D, M + gear in the manual gate.
        String shown=Telemetry.gearText(gear,sel,prof);
        text(c,shown,CR-108,base,shown.length()>1?66:84,"R".equals(shown)?RED:TEXT,Paint.Align.CENTER);
        // 6MT: the cluster's shift recommendation (EMS_H12), small above the gear.
        text(c,Telemetry.shiftHint(v(Telemetry.SHIFT_HINT)),CR-108,base-80,24,BLUE,Paint.Align.CENTER);
    }
    private void gforce(Canvas c){
        card(c,UiText.t("G-포스"),RL,T0,RR,B1);
        float cx=(RL+RR)/2,cy=(T0+B1)/2+8,rad=72;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(8);p.setColor(T(0x338fd0f0));c.drawCircle(cx,cy,rad,p);
        p.setStrokeWidth(3);p.setColor(T(BLUE));c.drawCircle(cx,cy,rad,p);
        p.setStrokeWidth(1);p.setColor(T(0x668fd0f0));c.drawCircle(cx,cy,rad/2,p);
        c.drawLine(cx-rad,cy,cx+rad,cy,p);c.drawLine(cx,cy-rad,cx,cy+rad,p);
        p.setStyle(Paint.Style.FILL);
        float gx=v(11),gy=v(12);boolean ok=!Float.isNaN(gx)&&!Float.isNaN(gy);
        float x=ok?Math.max(-1,Math.min(1,gx/1.2f))*rad:0,y=ok?-Math.max(-1,Math.min(1,gy/1.2f))*rad:0;
        p.setColor(T(0x55ff4059));c.drawCircle(cx+x,cy+y,12,p);p.setColor(T(RED));c.drawCircle(cx+x,cy+y,7,p);
        text(c,ok?String.format(Locale.US,"%.2f g",(float)Math.sqrt(gx*gx+gy*gy)):"— g",RR-14,B1-14,15,TEXT,Paint.Align.RIGHT);
    }
    /** Overview typeface (Typeface.DEFAULT) throughout. */
    private void text(Canvas c,String t,float x,float y,float size,int color,Paint.Align align){
        p.setStyle(Paint.Style.FILL);p.setColor(T(color));p.setTypeface(Typeface.DEFAULT);p.setTextSize(size);
        p.setTextAlign(align);c.drawText(t,x,y,p);p.setTextAlign(Paint.Align.LEFT);
    }
}
