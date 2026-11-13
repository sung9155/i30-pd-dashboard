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
    /** Each cell's graph: a bar under the value, the last 30 s as a line, or a row of lit segments (LED). */
    public static final int BAR=0,LINE=1,LED=2;
    public static final String[] GRAPH_NAMES={"막대","선 그래프","칸 막대"};
    public static final int[] graphs={BAR,BAR,BAR,BAR};
    /** The line graph's memory: per cell the last HIST samples, one every HIST_MS (30 s); cleared when the cell's signal changes. */
    static final int HIST=60;static final long HIST_MS=500;
    private static final float[][] hist=new float[4][HIST];private static final int[] histHead=new int[4],histN=new int[4],histSig={-1,-1,-1,-1};
    private static final long[] histAt=new long[4];
    /** Adds the cell's value to its line graph once every HIST_MS (called on every draw). */
    static void record(int k,int sig,float v,long now){
        if(histSig[k]!=sig||now<histAt[k]){histSig[k]=sig;histN[k]=0;histHead[k]=0;histAt[k]=0;}   // a new signal, or the clock went back: start over
        if(histN[k]>0&&now-histAt[k]<HIST_MS)return;
        histAt[k]=now;hist[k][histHead[k]]=v;histHead[k]=(histHead[k]+1)%HIST;if(histN[k]<HIST)histN[k]++;
    }

    private static int lastLook;
    /** Every frame tick: records the line graphs and says whether the window would look different from the last tick
     *  (text, colour, bar end to the pixel, a new line-graph sample), so an unchanged window is not drawn again. */
    public static boolean tick(int n,int size,Telemetry.Sample[] q,boolean diesel,long now){
        int h=17;
        for(int i=0;i<n;i++){int sig=signal(i,diesel);Telemetry.Sample m=q[sig];float v=m.valid?m.value:Float.NaN,f=Tiles.fraction(sig,v);record(i,sig,v,now);
            h=h*31+sig;h=h*31+Tiles.text(sig,v).hashCode();h=h*31+Tiles.level(sig,v);h=h*31+graphs[i];
            h=h*31+(Float.isNaN(f)?-1:Math.round(Math.max(0,Math.min(1,f))*cellW(size)));
            if(graphs[i]==LINE)h=h*31+histHead[i]*64+histN[i];}
        boolean changed=h!=lastLook;lastLook=h;return changed;
    }

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
    public static void draw(Canvas c,int shape,int n,int size,Telemetry.Sample[] q,boolean diesel,long now){
        int cols=cols(shape,n),rows=rows(shape,n),cw=cellW(size),ch=cellH(size),w=cols*cw,h=rows*ch;float k=SIZES[clampSize(size)];
        p.setStyle(Paint.Style.FILL);p.setColor(BG);r.set(0,0,w,h);c.drawRoundRect(r,10*k,10*k,p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1);p.setColor(EDGE);r.set(.5f,.5f,w-.5f,h-.5f);c.drawRoundRect(r,10*k,10*k,p);
        p.setColor(DIV);for(int i=1;i<cols;i++)c.drawLine(i*cw,8*k,i*cw,h-8*k,p);for(int i=1;i<rows;i++)c.drawLine(8*k,i*ch,w-8*k,i*ch,p);
        p.setStyle(Paint.Style.FILL);
        for(int i=0;i<n;i++){int sig=signal(i,diesel);float x=(i%cols)*cw,y=(i/cols)*ch;Telemetry.Sample m=q[sig];float v=m.valid?m.value:Float.NaN;
            int lv=Tiles.level(sig,v),col=lv==2?HOT:lv==1?WARM:TXT,g=graphs[i];record(i,sig,v,now);
            text(c,name(sig),x+8*k,y+17*k,12*k,MUT,Paint.Align.LEFT,Typeface.DEFAULT_BOLD);
            String unit=Telemetry.UNITS[sig],val=Tiles.text(sig,v);float f=Tiles.fraction(sig,v);
            if(Float.isNaN(f)||g==LINE&&!drawn(i,sig)){   // nothing to graph (no value yet, or the line has no history): the value alone, large, in the middle
                float vw=width(val,30*k,Typeface.DEFAULT_BOLD),uw=width(unit,12*k,Typeface.DEFAULT),x0=x+(cw-(vw+3*k+uw))/2;
                text(c,val,x0,y+ch-13*k,30*k,Float.isNaN(v)?MUT:col,Paint.Align.LEFT,Typeface.DEFAULT_BOLD);text(c,unit,x0+vw+3*k,y+ch-13*k,12*k,MUT,Paint.Align.LEFT,Typeface.DEFAULT);continue;}
            if(g==LINE){   // the value on the name's line (the unit gives way to a long name), the last 30 s below it
                float nw=width(name(sig),12*k,Typeface.DEFAULT_BOLD),vw=width(val,21*k,Typeface.DEFAULT_BOLD),uw=width(unit,10*k,Typeface.DEFAULT);
                boolean fits=nw+vw+uw+26*k<=cw;float right=x+cw-8*k;
                if(fits){text(c,unit,right,y+20*k,10*k,MUT,Paint.Align.RIGHT,Typeface.DEFAULT);right-=uw+3*k;}
                text(c,val,right,y+21*k,21*k,col,Paint.Align.RIGHT,Typeface.DEFAULT_BOLD);
                line(c,i,sig,x+8*k,y+29*k,x+cw-8*k,y+ch-7*k,k,lv);continue;}
            float uw=width(unit,11*k,Typeface.DEFAULT);
            text(c,unit,x+cw-10*k,y+ch-15*k,11*k,MUT,Paint.Align.RIGHT,Typeface.DEFAULT);
            text(c,val,x+cw-10*k-uw-3*k,y+ch-15*k,26*k,col,Paint.Align.RIGHT,Typeface.DEFAULT_BOLD);
            float bl=x+8*k,br=x+cw-8*k;
            if(g==LED){   // lit segments, the top fifth amber
                int segs=Math.max(8,Math.round((br-bl)/(8*k)));float sw=(br-bl)/segs,bt=y+ch-11*k,bb=bt+6*k;int lit=Float.isNaN(f)?0:Math.round(Math.max(0,Math.min(1,f))*segs);
                for(int s2=0;s2<segs;s2++){p.setColor(s2<lit?(s2>=segs*.8f?WARM:lv==2?HOT:ACC):TRACK);c.drawRect(bl+s2*sw+k,bt,bl+(s2+1)*sw-k,bb,p);}
                continue;}
            float bt=y+ch-9*k,bb=bt+4*k;p.setColor(TRACK);r.set(bl,bt,br,bb);c.drawRoundRect(r,2*k,2*k,p);
            f=Math.max(0,Math.min(1,f));
            float zero=sig==Telemetry.BOOST?Math.max(0,Math.min(1,Tiles.fraction(sig,0))):0,a=bl+(br-bl)*Math.min(zero,f),b=bl+(br-bl)*Math.max(zero,f);
            p.setColor(lv==2?HOT:lv==1?WARM:ACC);r.set(a,bt,Math.max(b,a+2),bb);c.drawRoundRect(r,2*k,2*k,p);
            if(zero>0){p.setColor(0x99ffffff);float z=bl+(br-bl)*zero;c.drawRect(z-.5f,bt-2*k,z+.5f,bb+2*k,p);}
        }
    }
    /** Cell k's line graph has at least two samples with a place on the scale. */
    private static boolean drawn(int k,int sig){int n=histN[k],ok=0;if(histSig[k]!=sig)return false;
        for(int j=0;j<n&&ok<2;j++)if(!Float.isNaN(Tiles.fraction(sig,hist[k][(histHead[k]-n+j+HIST*2)%HIST])))ok++;return ok>=2;}
    private static final android.graphics.Path path=new android.graphics.Path();
    /** Cell k's last 30 s in the box l,t-r,b: a filled area under a line, scaled to the signal's bar range, the latest value a dot at the right. */
    private static void line(Canvas c,int k,int sig,float l,float t,float r2,float b,float s,int lv){
        int n=histN[k];if(n<2)return;
        float px=(r2-l)/(HIST-1);boolean open=false;float lastX=0,lastY=0,firstX=0;path.reset();
        for(int j=0;j<n;j++){float v=hist[k][(histHead[k]-n+j+HIST*2)%HIST],f=Tiles.fraction(sig,v);
            if(Float.isNaN(f))continue;f=Math.max(0,Math.min(1,f));
            float xx=r2-(n-1-j)*px,yy=b-f*(b-t);if(!open){path.moveTo(xx,b);path.lineTo(xx,yy);firstX=xx;open=true;}else path.lineTo(xx,yy);lastX=xx;lastY=yy;}
        if(!open)return;
        path.lineTo(lastX,b);path.close();p.setStyle(Paint.Style.FILL);p.setColor(lv==2?0x55ff4b3c:lv==1?0x55ffaa3c:0x445ac8fa);c.drawPath(path,p);
        path.reset();open=false;
        for(int j=0;j<n;j++){float v=hist[k][(histHead[k]-n+j+HIST*2)%HIST],f=Tiles.fraction(sig,v);if(Float.isNaN(f))continue;f=Math.max(0,Math.min(1,f));
            float xx=r2-(n-1-j)*px,yy=b-f*(b-t);if(!open){path.moveTo(xx,yy);open=true;}else path.lineTo(xx,yy);}
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.6f*s);p.setColor(lv==2?HOT:lv==1?WARM:ACC);c.drawPath(path,p);p.setStyle(Paint.Style.FILL);
        p.setColor(0xffffffff);c.drawCircle(lastX,lastY,2.2f*s,p);
    }
    private static void text(Canvas c,String s,float x,float y,float size,int color,Paint.Align a,Typeface face){
        p.setTypeface(face);p.setTextSize(size);p.setTextAlign(a);p.setColor(color);c.drawText(s,x,y,p);}
    private static float width(String s,float size,Typeface face){p.setTypeface(face);p.setTextSize(size);return p.measureText(s);}
}
