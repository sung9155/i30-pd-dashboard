package com.veloster.vehicle;

import com.veloster.i18n.UiText;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import java.util.Locale;

/** esp32CarInfo "Symmetric" layout (ui.c dash_build case 0, Default theme) below the shared navigation.
 * Grid 160|fr|160 x 42/38/24/fr/96, pad 12/18, gap 8. Cells without a Telemetry source show a dash. */
public final class ClusterRenderer {
    private final Paint p=new Paint(3);
    private final RectF r=new RectF();
    // ui_theme.c "Default": bg cell track text muted accent good warn crit
    private static final int BG=0xff0b0d10,CELL=0xff10141a,TRACK=0xff1c2027,TEXT=0xffe8e8e8,MUTED=0xff9aa3ad,ACCENT=0xff4fc3f7,GOOD=0xff3ddc84,WARN=0xffffb300,CRIT=0xffff4545;
    // ui_settings.c defaults
    private static final float MAX_RPM=8000,REDLINE=7000,MAX_SPEED=240,OIL_WARN=110,OIL_CRIT=125,VOLT_LOW=12;
    private static final float TOP=52,L=18,R=782,C1=186,C2=614,C3=622,R0=TOP+12,R1=R0+50,R2=R1+46,R3=R2+32,R4=480-12-96;
    private static final int GTRAIL=6,GBALL=132;
    private static final String[] CELL_NAME={"유온","냉각수","미션유온","흡기온","점화시기","BATT"};
    private static final int[] CELL_SIGNAL={1,2,-1,Telemetry.INTAKE,-1,Telemetry.BAT_VOLTAGE};
    private static final float[][] CELL_RANGE={{40,135},{40,115},{40,120},{0,80},{-10,45},{11,15}};
    private static final float[][] CELL_LIMIT={{OIL_WARN,OIL_CRIT},{100,110},{100,115},{60,80}};
    private final float[] trailX=new float[GTRAIL],trailY=new float[GTRAIL];
    private Telemetry.Sample[] s;

    private float v(int i){return i>=0&&i<s.length&&s[i].valid&&!Float.isNaN(s[i].value)&&!Float.isInfinite(s[i].value)?Telemetry.displayValue(i,s[i].value):Float.NaN;}
    private static int alpha(int color,float a){return ((int)(a*255)<<24)|(color&0xffffff);}
    /** lv_color_mix(a,b,t): t=1 gives a. */
    private static int mix(int a,int b,float t){
        int o=0xff000000;for(int sh=0;sh<24;sh+=8){int x=(a>>sh)&255,y=(b>>sh)&255;o|=Math.round(y+(x-y)*t)<<sh;}return o;
    }
    private static float clamp(float f){return f<0?0:f>1?1:f;}
    private static int heat(float f){f=clamp(f);return f<.5f?mix(WARN,GOOD,f*2):mix(CRIT,WARN,(f-.5f)*2);}
    private static int tempcol(float t,float warn,float crit){return t<40?ACCENT:t>=crit?CRIT:t>=warn?WARN:TEXT;}
    private static int speedcol(float t){return t<100?ACCENT:t<180?mix(WARN,ACCENT,(t-100)/80):mix(CRIT,WARN,Math.min(1,(t-180)/60));}
    private void rect(Canvas c,float l,float t,float rr,float b,float radius,int color){p.setStyle(Paint.Style.FILL);p.setColor(color);r.set(l,t,rr,b);c.drawRoundRect(r,radius,radius,p);}
    private void frame(Canvas c,float l,float t,float rr,float b,float radius,int color,float width){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(width);p.setColor(color);r.set(l,t,rr,b);c.drawRoundRect(r,radius,radius,p);p.setStyle(Paint.Style.FILL);}
    private void line(Canvas c,float x,float y,float x2,float y2,int color,float width,boolean round){p.setStyle(Paint.Style.STROKE);p.setStrokeCap(round?Paint.Cap.ROUND:Paint.Cap.BUTT);p.setColor(color);p.setStrokeWidth(width);c.drawLine(x,y,x2,y2,p);p.setStrokeCap(Paint.Cap.BUTT);p.setStyle(Paint.Style.FILL);}
    private void dot(Canvas c,float x,float y,float radius,int color){p.setStyle(Paint.Style.FILL);p.setColor(color);c.drawCircle(x,y,radius,p);}
    private void ring(Canvas c,float x,float y,float radius,int color,float width){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(width);p.setColor(color);c.drawCircle(x,y,radius,p);p.setStyle(Paint.Style.FILL);}
    private void text(Canvas c,String t,float x,float y,float size,int color,Paint.Align align){p.setStyle(Paint.Style.FILL);p.setColor(color);p.setTypeface(Typeface.DEFAULT);p.setTextSize(size);p.setTextAlign(align);c.drawText(t,x,y,p);p.setTextAlign(Paint.Align.LEFT);}
    private static String fmt(String f,float x){return Float.isNaN(x)?"—":String.format(Locale.US,f,x);}

    public void draw(Canvas c,Telemetry.Sample[] samples){
        s=samples;
        p.setStyle(Paint.Style.FILL);p.setColor(BG);c.drawRect(0,TOP,800,480,p);
        float rpm=v(0),boost=v(Telemetry.BOOST),speed=v(7);
        boolean red=!Float.isNaN(rpm)&&rpm>=REDLINE;
        int rpmColor=red?CRIT:heat(rpm/MAX_RPM);
        float bar=row(c,"RPM",R0,42,32,fmt("%.0f",rpm),red?CRIT:TEXT);
        if(!Float.isNaN(rpm))rect(c,126,R0+5,126+bar*clamp(rpm/MAX_RPM),R0+37,4,rpmColor);
        rect(c,126+bar*.87f,R0+5,126+bar,R0+37,0,alpha(CRIT,.2f));rect(c,126+bar*.87f,R0+5,128+bar*.87f,R0+37,0,CRIT);
        bar=row(c,"BOOST",R1,38,28,fmt("%+.2f",boost),TEXT);
        float zero=126+bar*.35f;
        if(!Float.isNaN(boost)){float b=126+bar*clamp((boost+.8f)/2.3f);rect(c,Math.min(zero,b),R1+5,Math.max(zero,b),R1+33,4,boost>=0?heat(boost/1.5f):ACCENT);}
        rect(c,zero,R1+5,zero+2,R1+33,0,alpha(TRACK,.5f));
        float load=v(Telemetry.ENGINE_LOAD);
        row(c,UiText.t("부하율"),R2,24,16,Float.isNaN(load)?"—":fmt("%.0f",load),TEXT); // EMS11 TQI % (opendbc)
        gball(c);speedBox(c,speed);pedals(c);cells(c);
    }
    /** Caption | track | value. Returns track width; track starts at x=126. */
    private float row(Canvas c,String name,float top,float h,float track,String value,int color){
        float mid=top+h/2,w=R-130-12-126;
        text(c,name,L,mid+6,18,MUTED,Paint.Align.LEFT);
        rect(c,126,mid-track/2,126+w,mid+track/2,4,TRACK);
        text(c,value,R,mid+10,28,color,Paint.Align.RIGHT);
        return w;
    }
    private void gball(Canvas c){
        rect(c,L,R3,L+160,R4-8,8,CELL);
        float cx=L+80,cy=R3+8+GBALL/2f,radius=GBALL/2f-12;
        ring(c,cx,cy,GBALL/2f-1,TRACK,2);ring(c,cx,cy,GBALL/4f,alpha(TRACK,.5f),1);
        line(c,cx,cy-GBALL/2f+5,cx,cy+GBALL/2f-5,alpha(TRACK,.4f),1,false);line(c,cx-GBALL/2f+5,cy,cx+GBALL/2f-5,cy,alpha(TRACK,.4f),1,false);
        float gx=v(11),gy=v(12);boolean ok=!Float.isNaN(gx)&&!Float.isNaN(gy);
        float x=ok?gx/1.2f*radius:0,y=ok?-gy/1.2f*radius:0,mag=ok?(float)Math.sqrt(gx*gx+gy*gy):0;
        int color=mag>=1?CRIT:mag>=.8f?WARN:GOOD;
        if(ok){
            for(int i=GTRAIL-1;i>0;i--){trailX[i]=trailX[i-1];trailY[i]=trailY[i-1];}
            trailX[0]=x;trailY[0]=y;
            for(int i=0;i<GTRAIL;i++)dot(c,cx+trailX[i],cy+trailY[i],3.5f,alpha(color,(30+i*25)/255f));
        }
        dot(c,cx+x,cy+y,7,color);
        text(c,ok?String.format(Locale.US,"%.2f g",mag):"— g",cx,cy+GBALL/2f-10,18,MUTED,Paint.Align.CENTER);
        text(c,UiText.t("G-볼 1.2g"),cx,R3+8+GBALL+20,18,MUTED,Paint.Align.CENTER);
    }
    /** ring_draw: left edge bottom-up, top edge, right edge top-down, with 20 km/h ticks and a comet trail. */
    private void speedBox(Canvas c,float speed){
        float l=C1,t=R3,rr=C2,b=R4-8;
        rect(c,l,t,rr,b,8,CELL);
        float x0=l+7,y0=t+7,x1=rr-7,y1=b-7,lh=y1-y0,lw=x1-x0,total=lh+lw+lh;
        float shown=Float.isNaN(speed)?0:Math.max(0,speed),f=Math.min(1,shown/MAX_SPEED),dmax=total*f;
        int color=speedcol(shown),n=(int)(MAX_SPEED/20);
        float[] at=new float[4];
        for(int i=0;i<=n;i++){
            edge(total*i/n,x0,y0,x1,y1,at);boolean big=i%5==0,passed=(float)i/n<=f;float len=big?11:6;
            line(c,at[0],at[1],at[0]+at[2]*len,at[1]+at[3]*len,passed?color:alpha(MUTED,.5f),big?3:1,true);
        }
        float[] a2=new float[4];
        for(int i=0;i<48;i++){edge(dmax*i/48,x0,y0,x1,y1,at);edge(dmax*(i+1)/48,x0,y0,x1,y1,a2);line(c,at[0],at[1],a2[0],a2[1],alpha(color,(30+225f*i/48)/255),4,false);}
        edge(dmax,x0,y0,x1,y1,at);
        float dx=(l+rr)/2-at[0],dy=(t+b)/2-at[1],d=Math.max(1,(float)Math.sqrt(dx*dx+dy*dy)),len=Math.min(42,d*.42f);
        line(c,at[0],at[1],at[0]+dx/d*len,at[1]+dy/d*len,color,3,true);dot(c,at[0],at[1],5,color);
        // 100 px in the 188 px ESP32 box; scaled to this 172 px box so the PRND strip stays clear.
        text(c,fmt("%.0f",speed),(l+rr)/2,(t+b)/2-10+32,88,TEXT,Paint.Align.CENTER);
        text(c,"km/h",(l+rr)/2,b-42,18,MUTED,Paint.Align.CENTER);
        float gear=v(8),sel=v(Telemetry.GEAR_SELECTOR);boolean manual=!Float.isNaN(gear)&&gear>=1&&gear<=6;
        // i30 PD: selector P/R/N/D from hscan 0x1d (0=P 1=R 2=N 3=D); in D the current gear number replaces the letter.
        int active=Float.isNaN(sel)?(manual?3:-1):(int)sel;
        String[] slot={"P","R","N",manual?Telemetry.gearLabel((int)gear):"D"};
        float x=(l+rr)/2-60;
        for(int i=0;i<4;i++){boolean act=i==active;
            text(c,slot[i],x+i*34+(act?6:0),b-12,act?28:18,act?(red()?CRIT:ACCENT):alpha(MUTED,.5f),Paint.Align.CENTER);}
    }
    private boolean red(){float rpm=v(0);return !Float.isNaN(rpm)&&rpm>=REDLINE;}
    private static void edge(float d,float x0,float y0,float x1,float y1,float[] o){
        float lh=y1-y0,lw=x1-x0;
        if(d<=lh){o[0]=x0;o[1]=y1-d;o[2]=1;o[3]=0;return;}d-=lh;
        if(d<=lw){o[0]=x0+d;o[1]=y0;o[2]=0;o[3]=1;return;}d-=lw;
        o[0]=x1;o[1]=y0+d;o[2]=-1;o[3]=0;
    }
    private void pedals(Canvas c){
        String[] name={"BRK","ACC"};int[] signal={10,9},color={CRIT,GOOD};float[] max={120,100};
        for(int i=0;i<2;i++){
            float l=C3+i*85,cx=l+37.5f,val=v(signal[i]);
            text(c,fmt("%.0f",val),cx,R3+16,18,MUTED,Paint.Align.CENTER);
            float t=R3+22,b=R4-8-26;
            rect(c,cx-16,t,cx+16,b,4,TRACK);
            if(!Float.isNaN(val)){float f=clamp(val/max[i]);if(f>0)rect(c,cx-16,b-(b-t)*f,cx+16,b,4,color[i]);}
            text(c,name[i],cx,R4-8-6,18,MUTED,Paint.Align.CENTER);
        }
    }
    private void cells(Canvas c){
        float w=(R-L-40)/6;
        for(int i=0;i<6;i++){
            float l=L+i*(w+8),cx=l+w/2,t=R4,b=480-12,val=v(CELL_SIGNAL[i]);
            rect(c,l,t,l+w,b,8,CELL);frame(c,l+.5f,t+.5f,l+w-.5f,b-.5f,8,alpha(TRACK,.6f),1);
            text(c,i==5?CELL_NAME[i]:UiText.t(CELL_NAME[i]),cx,t+22,18,MUTED,Paint.Align.CENTER);
            int color=Float.isNaN(val)?TEXT:i==5?(val<VOLT_LOW?WARN:TEXT):tempcol(val,CELL_LIMIT[i][0],CELL_LIMIT[i][1]);
            text(c,fmt(i==5?"%.1fV":"%.0f°",val),cx,t+60,28,alpha(color,.8f),Paint.Align.CENTER);
            float bl=cx-w*.31f,br=cx+w*.31f;rect(c,bl,b-6,br,b-2,2,TRACK);
            if(!Float.isNaN(val)){
                float f=clamp((val-CELL_RANGE[i][0])/(CELL_RANGE[i][1]-CELL_RANGE[i][0]));
                int bc=i==5?(val<VOLT_LOW||val>15.2f?CRIT:val<12.6f||val>14.9f?WARN:GOOD):f>=.85f?CRIT:f>=.6f?WARN:GOOD;
                if(f>0)rect(c,bl,b-6,bl+(br-bl)*f,b-2,2,bc);
            }
        }
    }
}
