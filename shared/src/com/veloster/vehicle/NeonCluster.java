package com.veloster.vehicle;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import java.util.Locale;

/**
 * 네온 screen: cyberpunk night city. A 40-segment neon rpm bar (cyan to magenta, the shift target marked), the speed huge
 * in the middle with a chromatic split, the gear in a neon hexagon, boost / water / oil / throttle readouts, a skyline
 * along the bottom. From 400 rpm before the shift target the speed glitches (sliced and shifted, noise bars, SHIFT
 * blinking); at the target it turns yellow. Own palette in every theme; an overheat warning is a solid chip in the
 * theme's danger colour.
 *
 * Built for the head unit: no per-frame blur. The glow is layered strokes drawn once into bitmaps (the face, and the
 * lit rpm bar of which each frame shows a part); each frame draws text, plain rects and clip rects.
 */
final class NeonCluster extends ClusterKit {
    int shiftTarget=6000;   // set by PitRenderer from 설정 → RPM
    private static final int BG=0xff07020f,MAG=0xffff2bd6,CYAN=0xff19f0ff,YEL=0xfff5ff3b,PURP=0xff7a2cff,FG=0xffeafcff,MUT=0xff9a7cc0,DIM=0xff1c0f2c;
    private static final int SEGS=40;private static final float BAR_L=20,BAR_R=780,BAR_T=16,BAR_B=60,SEG_GAP=4,SEG_W=(BAR_R-BAR_L-SEG_GAP*(SEGS-1))/SEGS;
    private static final float HOT=110;
    private Bitmap face,bar;private String faceKey="";

    private static Typeface num(){Typeface f=LambdaCluster.FACE_BOLD;return f!=null?f:Typeface.DEFAULT_BOLD;}
    private static int segColor(int i){return i>=SEGS-4?YEL:mix(MAG,CYAN,i/(float)(SEGS-5));}

    @Override protected void draw(Canvas c){
        c.save();c.translate(0,52);
        String key=prof.maxRpm+"/"+shiftTarget+"/"+com.veloster.i18n.UiText.language();
        if(face==null||!key.equals(faceKey)){face=recycle(face);bar=recycle(bar);face=buildFace();bar=buildBar();faceKey=key;}
        c.drawBitmap(face,0,0,null);
        float rpm=v(0),kmh=v(7),boost=v(Telemetry.BOOST),cool=v(2),oil=v(Telemetry.OIL_ECU),pedal=v(9);
        // rpm bar: the lit part of the kept bar, whole segments only
        int lit=Float.isNaN(rpm)?0:Math.round(clamp(rpm/prof.maxRpm,0,1)*SEGS);
        if(lit>0){float w=lit*(SEG_W+SEG_GAP)+8;blit(c,bar,0,0,(int)w,(int)(BAR_B+10),BAR_L-8,BAR_T-8,BAR_L-8+(int)w,BAR_T-8+(int)(BAR_B+10));}
        text(c,Float.isNaN(rpm)?"—":String.format(Locale.US,"%,d",Math.round(rpm/10)*10),BAR_L+44,104,28,CYAN,Paint.Align.LEFT,num());
        // speed with its chromatic split; the glitch near the shift point
        boolean valid=!Float.isNaN(rpm),near=valid&&rpm>=shiftTarget-400,at=valid&&rpm>=shiftTarget;
        String sp=fmt("%.0f",kmh);float sx=400,sy=262,ss=150;
        text(c,sp,sx-4,sy,ss,MAG,Paint.Align.CENTER,num());text(c,sp,sx+4,sy,ss,CYAN,Paint.Align.CENTER,num());
        text(c,sp,sx,sy,ss,at?YEL:FG,Paint.Align.CENTER,num());
        if(near){
            long f=now/70;   // a new glitch every 70 ms
            for(int k=0;k<3;k++){int h=hash(f*7+k);float y0=sy-ss*0.72f+(h&127)/127f*ss*0.62f,hh=8+(h>>>7&15),dx=((h>>>11&31)-15)*(at?1.2f:0.7f);
                c.save();c.clipRect(220,y0,580,y0+hh);fill(c,220,y0,580,y0+hh,BG);   // inside the speed frame: the readouts stay whole
                text(c,sp,sx+dx,sy,ss,(k&1)==0?MAG:CYAN,Paint.Align.CENTER,num());c.restore();}
            for(int k=0;k<2;k++){int h=hash(f*13+k+5);float y=128+(h&255)/255f*164,x0=220+(h>>>8&255);fill(c,x0,y,Math.min(580,x0+60+(h>>>16&127)),y+2,(k&1)==0?CYAN:MAG);}
            if(at||blink(now,240))bold(c,"SHIFT",sx,112,26,YEL,Paint.Align.CENTER);
        }
        // gear
        String g=gear();boolean gate=manualGate(g);if(gate)g=g.substring(1);
        text(c,g,115,262,g.length()>1?80:120,MAG,Paint.Align.CENTER,num());if(gate)bold(c,"M",168,150,22,CYAN,Paint.Align.CENTER);
        // readouts
        boolean hot=!Float.isNaN(cool)&&cool>=HOT;
        row(c,150,fmt("%+.2f",boost),Float.isNaN(boost)?Float.NaN:(boost+1)/(boostTop()+1),CYAN);
        row(c,204,fmt("%.0f",cool),Float.isNaN(cool)?Float.NaN:(cool-40)/90,hot?danger():MAG);
        row(c,258,fmt("%.0f",oil),Float.isNaN(oil)?Float.NaN:(oil-40)/110,MAG);
        row(c,312,fmt("%.0f",pedal),Float.isNaN(pedal)?Float.NaN:pedal/100,YEL);
        if(hot){round(c,604,320,780,352,6,danger());bold(c,t("과열")+" "+fmt("%.0f",cool)+"°C",692,343,20,0xffffffff,Paint.Align.CENTER);}
        c.restore();
    }
    private void row(Canvas c,float y,String val,float frac,int color){
        text(c,val,780,y,28,FG,Paint.Align.RIGHT,num());
        float w=Float.isNaN(frac)?0:170*clamp(frac,0,1);if(w>1)fill(c,610,y+6,610+w,y+10,color);
    }
    /** A small integer hash (no Random per frame). */
    private static int hash(long x){x=(x^(x>>>33))*0xff51afd7ed558ccdL;x^=x>>>33;return (int)x&0x7fffffff;}

    // ---- the still parts, drawn once ----
    @Override void trim(){face=recycle(face);bar=recycle(bar);faceKey="";}
    private Bitmap buildBar(){
        Bitmap b=bitmap(800,(int)(BAR_B+10));Canvas c=new Canvas(b);c.translate(8-BAR_L,8-BAR_T);
        for(int i=0;i<SEGS;i++){float l=BAR_L+i*(SEG_W+SEG_GAP);int col=segColor(i);
            fill(c,l-3,BAR_T-3,l+SEG_W+3,BAR_B+3,(col&0xffffff)|0x30000000);fill(c,l-1,BAR_T-1,l+SEG_W+1,BAR_B+1,(col&0xffffff)|0x60000000);
            fill(c,l,BAR_T,l+SEG_W,BAR_B,col);fill(c,l+2,BAR_T+2,l+SEG_W-2,BAR_T+8,mix(0xffffffff,col,0.5f));}
        return b;
    }
    /** Neon line: wide faint strokes under a thin bright one. */
    private void neon(Canvas c,float x1,float y1,float x2,float y2,int color){
        line(c,x1,y1,x2,y2,10,(color&0xffffff)|0x22000000);line(c,x1,y1,x2,y2,6,(color&0xffffff)|0x44000000);line(c,x1,y1,x2,y2,2.5f,color);
    }
    private Bitmap buildFace(){
        Bitmap b=bitmap(800,428);Canvas c=new Canvas(b);
        fill(c,0,0,800,428,BG);
        // sky: horizontal haze bands toward the horizon
        for(int i=0;i<12;i++)fill(c,0,250+i*8,800,258+i*8,mix(0xff2a0838,BG,i/12f*0.9f));
        // skyline: towers with lit windows
        java.util.Random rnd=new java.util.Random(2077);float x=0;
        while(x<800){float w=24+rnd.nextInt(46),h=40+rnd.nextInt(80);int tone=mix(0xff160a26,0xff0b0516,rnd.nextFloat());fill(c,x,428-h,x+w,428,tone);
            for(float wy=428-h+8;wy<420;wy+=9)for(float wx=x+4;wx<x+w-5;wx+=7)if(rnd.nextInt(5)==0)fill(c,wx,wy,wx+3,wy+4,rnd.nextInt(3)==0?0xaaff2bd6:0x9919f0ff);
            x+=w+2;}
        neon(c,0,330,800,330,PURP);
        // rpm bar sockets and scale, the shift target marked
        for(int i=0;i<SEGS;i++){float l=BAR_L+i*(SEG_W+SEG_GAP);fill(c,l,BAR_T,l+SEG_W,BAR_B,DIM);}
        for(int k=0;k<=prof.maxRpm;k+=1000){float xx=BAR_L+(BAR_R-BAR_L)*k/prof.maxRpm;mono(c,String.valueOf(k/1000),Math.min(BAR_R-6,Math.max(BAR_L+6,xx)),BAR_B+20,14,MUT,Paint.Align.CENTER);}
        float st=BAR_L+(BAR_R-BAR_L)*clamp(shiftTarget/prof.maxRpm,0,1);neon(c,st,BAR_T-8,st,BAR_B+6,YEL);
        mono(c,"RPM",BAR_L,104,14,MUT,Paint.Align.LEFT);
        // gear hexagon
        float gx=115,gy=210,gr=86;
        for(int i=0;i<6;i++){double a0=Math.toRadians(60*i-30),a1=Math.toRadians(60*i+30);neon(c,gx+(float)Math.cos(a0)*gr,gy+(float)Math.sin(a0)*gr,gx+(float)Math.cos(a1)*gr,gy+(float)Math.sin(a1)*gr,MAG);}
        mono(c,"GEAR",gx,gy+gr+22,15,MUT,Paint.Align.CENTER);
        // speed frame, readout labels
        neon(c,220,118,580,118,CYAN);neon(c,220,300,580,300,CYAN);
        mono(c,"KM/H",400,322,16,CYAN,Paint.Align.CENTER);
        String[] lab={"BOOST bar","WATER °C","OIL °C","THROTTLE %"};
        for(int i=0;i<4;i++)mono(c,lab[i],610,124+i*54,14,MUT,Paint.Align.LEFT);
        neon(c,596,104,596,322,MAG);
        return b;
    }
}
