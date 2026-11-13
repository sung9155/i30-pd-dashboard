package com.veloster.vehicle;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;

/**
 * 플로팅 창: a small see-through window of four readings that the dashboard keeps over every other screen (navi, home,
 * media) while it is not on screen itself. Four shapes (작은 카드 2×2, 큰 카드 2×2, 아래 띠 4칸, 옆 세로 4칸); each cell a short
 * name, the value and a thin bar that follows it (from zero for a value that goes negative, like boost). Off by
 * default. The dashboard app's FloatService owns the window; this draws it and holds the choices. Host-testable.
 */
public final class FloatPanel {
    public static final int OFF=0,SMALL=1,LARGE=2,STRIP=3,SIDE=4;
    public static final String[] STYLE_NAMES={"끔","작은 카드 2×2","큰 카드 2×2","아래 띠 4칸","옆 세로 4칸"};
    private static final int[] COLS={0,2,2,4,1},CW={0,124,250,125,124},CH={0,66,132,60,64};
    /** What a cell can show, in the order a tap on its settings row steps through, with the short name it shows. */
    public static final int[] CHOICES={Telemetry.BOOST,2,Telemetry.INTAKE,Telemetry.BAT_VOLTAGE,9,7,0,Telemetry.AMBIENT,Telemetry.FUEL_LEVEL,
        Telemetry.ENGINE_LOAD,10,Telemetry.CLUTCH_ODD,Telemetry.CLUTCH_EVEN,Telemetry.DPF_SOOT,Telemetry.DPF_TEMP};
    public static final String[] CHOICE_NAMES={"부스트","냉각수","흡기","배터리","페달","속도","RPM","외기온","연료","엔진 부하","브레이크","클러치 홀수","클러치 짝수","DPF 포집","DPF 온도"};
    /** -1 = automatic: boost, coolant, intake, battery; a diesel shows its DPF soot in the third. */
    public static final int[] AUTO_PETROL={Telemetry.BOOST,2,Telemetry.INTAKE,Telemetry.BAT_VOLTAGE},AUTO_DIESEL={Telemetry.BOOST,2,Telemetry.DPF_SOOT,Telemetry.BAT_VOLTAGE};
    public static volatile int style=OFF;
    public static volatile boolean hideButton=true;
    public static final int[] cells={-1,-1,-1,-1};

    private FloatPanel(){}
    public static int width(int s){return s<=OFF||s>=COLS.length?0:COLS[s]*CW[s];}
    public static int height(int s){return s<=OFF||s>=COLS.length?0:(4/COLS[s])*CH[s];}
    /** Where the window starts (screen 800 × 480, below the 52 px status bar): cards and the side column top right, the strip bottom middle. */
    public static int defaultX(int s){return s==STRIP?(800-width(s))/2:800-width(s)-8;}
    public static int defaultY(int s){return s==STRIP?480-height(s)-10:62;}
    /** The signal cell k shows (the automatic choice resolved for the car). */
    public static int signal(int k,boolean diesel){int c=cells[k];return c>=0?c:(diesel?AUTO_DIESEL:AUTO_PETROL)[k];}
    /** The next choice for a cell: automatic, then CHOICES in order, then automatic again. */
    public static int next(int current){if(current<0)return CHOICES[0];for(int i=0;i<CHOICES.length;i++)if(CHOICES[i]==current)return i+1<CHOICES.length?CHOICES[i+1]:-1;return -1;}
    public static String name(int sig){for(int i=0;i<CHOICES.length;i++)if(CHOICES[i]==sig)return CHOICE_NAMES[i];return Telemetry.LABELS[sig];}

    private static final Paint p=new Paint(3);private static final RectF r=new RectF();
    private static final int BG=0xd8080c10,EDGE=0x33ffffff,DIV=0x22ffffff,TXT=0xfff0f4f7,MUT=0xff96a0aa,TRACK=0x28ffffff,ACC=0xff5ac8fa,WARM=0xffffaa3c,HOT=0xffff4b3c;
    /** Draws style s at 0,0 from the samples (index = signal). */
    public static void draw(Canvas c,int s,Telemetry.Sample[] q,boolean diesel){
        int cols=COLS[s],cw=CW[s],ch=CH[s],w=width(s),h=height(s);float k=s==LARGE?2:1;
        p.setStyle(Paint.Style.FILL);p.setColor(BG);r.set(0,0,w,h);c.drawRoundRect(r,10,10,p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1);p.setColor(EDGE);r.set(.5f,.5f,w-.5f,h-.5f);c.drawRoundRect(r,10,10,p);
        p.setColor(DIV);for(int i=1;i<cols;i++)c.drawLine(i*cw,8,i*cw,h-8,p);for(int i=1;i<4/cols;i++)c.drawLine(8,i*ch,w-8,i*ch,p);
        p.setStyle(Paint.Style.FILL);
        for(int i=0;i<4;i++){int sig=signal(i,diesel);float x=(i%cols)*cw,y=(i/cols)*ch;Telemetry.Sample m=q[sig];float v=m.valid?m.value:Float.NaN;
            int lv=Tiles.level(sig,v),col=lv==2?HOT:lv==1?WARM:TXT;
            text(c,name(sig),x+8*k,y+17*k,12*k,MUT,Paint.Align.LEFT,Typeface.DEFAULT_BOLD);
            String unit=Telemetry.UNITS[sig],val=Tiles.text(sig,v);float uw=width(unit,11*k,Typeface.DEFAULT);
            text(c,unit,x+cw-10*k,y+ch-15*k,11*k,MUT,Paint.Align.RIGHT,Typeface.DEFAULT);
            text(c,val,x+cw-10*k-uw-3*k,y+ch-15*k,26*k,Float.isNaN(v)?MUT:col,Paint.Align.RIGHT,Typeface.DEFAULT_BOLD);
            float bl=x+8*k,br=x+cw-8*k,bt=y+ch-9*k,bb=bt+4*k;p.setColor(TRACK);r.set(bl,bt,br,bb);c.drawRoundRect(r,2*k,2*k,p);
            float f=Tiles.fraction(sig,v);if(Float.isNaN(f))continue;f=Math.max(0,Math.min(1,f));
            float zero=sig==Telemetry.BOOST?Math.max(0,Math.min(1,Tiles.fraction(sig,0))):0,a=bl+(br-bl)*Math.min(zero,f),b=bl+(br-bl)*Math.max(zero,f);
            p.setColor(lv==2?HOT:lv==1?WARM:ACC);r.set(a,bt,Math.max(b,a+2),bb);c.drawRoundRect(r,2*k,2*k,p);
            if(zero>0){p.setColor(0x99ffffff);float z=bl+(br-bl)*zero;c.drawRect(z-.5f,bt-2*k,z+.5f,bb+2*k,p);}
        }
    }
    private static void text(Canvas c,String s,float x,float y,float size,int color,Paint.Align a,Typeface face){
        p.setTypeface(face);p.setTextSize(size);p.setTextAlign(a);p.setColor(color);c.drawText(s,x,y,p);}
    private static float width(String s,float size,Typeface face){p.setTypeface(face);p.setTextSize(size);return p.measureText(s);}
}
