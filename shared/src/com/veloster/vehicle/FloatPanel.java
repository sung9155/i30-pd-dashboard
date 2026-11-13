package com.veloster.vehicle;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;

/**
 * 플로팅 창: a small see-through window of readings that the dashboard keeps over every other screen (navi, home, media)
 * while it is not on screen itself. Chosen in 설정 → 플로팅: the shape (카드, 아래 띠, 옆 세로), how many cells (1, 2 or 4)
 * and the size; each cell a short name, the value and a thin bar that follows it (from zero for a value that goes
 * negative, like boost). Off by default. The dashboard app's FloatService owns the window; this draws it and holds the
 * choices. Host-testable.
 */
public final class FloatPanel {
    public static final int OFF=0,CARD=1,STRIP=2,SIDE=3;
    public static final String[] SHAPE_NAMES={"끔","카드","아래 띠","옆 세로"};
    public static final int[] COUNTS={1,2,4};
    public static final String[] SIZE_NAMES={"작게","보통","크게","아주 크게"};
    public static final float[] SIZES={.85f,1,1.5f,2};
    /** A cell at size 보통, px. */
    static final int CELL_W=124,CELL_H=64;
    /** What a cell can show, in the order a tap on its settings row steps through, with the short name it shows. */
    public static final int[] CHOICES={Telemetry.BOOST,2,Telemetry.INTAKE,Telemetry.BAT_VOLTAGE,9,7,0,Telemetry.AMBIENT,Telemetry.FUEL_LEVEL,
        Telemetry.ENGINE_LOAD,10,Telemetry.CLUTCH_ODD,Telemetry.CLUTCH_EVEN,Telemetry.DPF_SOOT,Telemetry.DPF_TEMP};
    public static final String[] CHOICE_NAMES={"부스트","냉각수","흡기","배터리","페달","속도","RPM","외기온","연료","엔진 부하","브레이크","클러치 홀수","클러치 짝수","DPF 포집","DPF 온도"};
    /** -1 = automatic: boost, coolant, intake, battery; a diesel shows its DPF soot in the third. */
    public static final int[] AUTO_PETROL={Telemetry.BOOST,2,Telemetry.INTAKE,Telemetry.BAT_VOLTAGE},AUTO_DIESEL={Telemetry.BOOST,2,Telemetry.DPF_SOOT,Telemetry.BAT_VOLTAGE};
    public static volatile int shape=OFF,count=4,size=1;
    public static volatile boolean hideButton=true;
    public static final int[] cells={-1,-1,-1,-1};

    private FloatPanel(){}
    /** Columns of a shape with n cells: a card is 2 wide from 2 cells, the strip one row, the side one column. */
    public static int cols(int shape,int n){return shape==STRIP?n:shape==SIDE?1:Math.min(2,n);}
    public static int rows(int shape,int n){return (n+cols(shape,n)-1)/cols(shape,n);}
    public static int cellW(int size){return Math.round(CELL_W*SIZES[clampSize(size)]);}
    public static int cellH(int size){return Math.round(CELL_H*SIZES[clampSize(size)]);}
    public static int width(int shape,int n,int size){return shape<=OFF?0:cols(shape,n)*cellW(size);}
    public static int height(int shape,int n,int size){return shape<=OFF?0:rows(shape,n)*cellH(size);}
    private static int clampSize(int s){return Math.max(0,Math.min(SIZES.length-1,s));}
    /** Where the window starts (screen 800 × 480, below the 52 px status bar): the card and the side column top right, the strip bottom middle. */
    public static int defaultX(int shape,int n,int size){return shape==STRIP?(800-width(shape,n,size))/2:800-width(shape,n,size)-8;}
    public static int defaultY(int shape,int n,int size){return shape==STRIP?480-height(shape,n,size)-10:62;}
    /** The next cell count after the current one (1, 2, 4, 1 ...). */
    public static int nextCount(int n){return n>=4?1:n>=2?4:2;}
    /** The signal cell k shows (the automatic choice resolved for the car). */
    public static int signal(int k,boolean diesel){int c=cells[k];return c>=0?c:(diesel?AUTO_DIESEL:AUTO_PETROL)[k];}
    /** The next choice for a cell: automatic, then CHOICES in order, then automatic again. */
    public static int next(int current){if(current<0)return CHOICES[0];for(int i=0;i<CHOICES.length;i++)if(CHOICES[i]==current)return i+1<CHOICES.length?CHOICES[i+1]:-1;return -1;}
    public static String name(int sig){for(int i=0;i<CHOICES.length;i++)if(CHOICES[i]==sig)return CHOICE_NAMES[i];return Telemetry.LABELS[sig];}

    private static final Paint p=new Paint(3);private static final RectF r=new RectF();
    private static final int BG=0xd8080c10,EDGE=0x33ffffff,DIV=0x22ffffff,TXT=0xfff0f4f7,MUT=0xff96a0aa,TRACK=0x28ffffff,ACC=0xff5ac8fa,WARM=0xffffaa3c,HOT=0xffff4b3c;
    /** Draws the window (shape, n cells, size) at 0,0 from the samples (index = signal). */
    public static void draw(Canvas c,int shape,int n,int size,Telemetry.Sample[] q,boolean diesel){
        int cols=cols(shape,n),rows=rows(shape,n),cw=cellW(size),ch=cellH(size),w=cols*cw,h=rows*ch;float k=SIZES[clampSize(size)];
        p.setStyle(Paint.Style.FILL);p.setColor(BG);r.set(0,0,w,h);c.drawRoundRect(r,10*k,10*k,p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1);p.setColor(EDGE);r.set(.5f,.5f,w-.5f,h-.5f);c.drawRoundRect(r,10*k,10*k,p);
        p.setColor(DIV);for(int i=1;i<cols;i++)c.drawLine(i*cw,8*k,i*cw,h-8*k,p);for(int i=1;i<rows;i++)c.drawLine(8*k,i*ch,w-8*k,i*ch,p);
        p.setStyle(Paint.Style.FILL);
        for(int i=0;i<n;i++){int sig=signal(i,diesel);float x=(i%cols)*cw,y=(i/cols)*ch;Telemetry.Sample m=q[sig];float v=m.valid?m.value:Float.NaN;
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
