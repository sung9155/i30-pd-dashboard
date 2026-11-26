package com.veloster.vehicle;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import java.util.Locale;

/**
 * 신스웨이브 screen (was 네온 until 2026-11-27; the class keeps the old name): a night drive HUD in neon colours. A slim segmented rpm bar (cyan, magenta past the shift target, the target
 * marked in yellow), the speed in plain white in the middle, the gear in corner brackets, boost / water / oil / throttle
 * on the right, and below a faint perspective grid running to a magenta horizon with dark towers; on the grid a row of
 * five more readouts (G, brake, intake, battery, range). Near the shift target
 * the speed shows a small glitch and SHIFT blinks; at the target the speed turns yellow. Own palette in every theme; an
 * overheat warning is a solid chip in the theme's danger colour.
 *
 * Restraint is the point (2026-11-27, the owner: "the colours are good but it looks cheap"): no permanent colour split on
 * the digits, no rainbow bar, no lit windows, one accent per element.
 *
 * Built for the head unit: no per-frame blur. The glow is layered strokes drawn once into bitmaps (the face, and the
 * lit rpm bar of which each frame shows a part); each frame draws text, plain rects and clip rects.
 */
final class NeonCluster extends ClusterKit {
    int shiftTarget=6000;   // set by PitRenderer from 설정 → RPM
    private static final int BG=0xff07020f,MAG=0xffff2bd6,CYAN=0xff19f0ff,YEL=0xfff5ff3b,PURP=0xff7a2cff,FG=0xfff2f6ff,MUT=0xff8f7cb0,DIM=0xff1a1030;
    private static final int SEGS=48;private static final float BAR_L=24,BAR_R=776,BAR_T=18,BAR_B=34,SEG_GAP=3,SEG_W=(BAR_R-BAR_L-SEG_GAP*(SEGS-1))/SEGS;
    private static final float HOT=110,HORIZON=318;
    private Bitmap face,bar;private String faceKey="";

    private static Typeface num(){Typeface f=LambdaCluster.FACE_BOLD;return f!=null?f:Typeface.DEFAULT_BOLD;}
    private int shiftSeg(){return Math.round(clamp(shiftTarget/prof.maxRpm,0,1)*SEGS);}

    @Override protected void draw(Canvas c){
        c.save();c.translate(0,52);
        String key=prof.maxRpm+"/"+shiftTarget+"/"+com.veloster.i18n.UiText.language();
        if(face==null||!key.equals(faceKey)){face=recycle(face);bar=recycle(bar);face=buildFace();bar=buildBar();faceKey=key;}
        c.drawBitmap(face,0,0,null);
        float rpm=v(0),kmh=v(7),boost=v(Telemetry.BOOST),cool=v(2),oil=v(Telemetry.OIL_ECU),pedal=v(9);
        // rpm bar: the lit part of the kept bar, whole segments only
        int lit=Float.isNaN(rpm)?0:Math.round(clamp(rpm/prof.maxRpm,0,1)*SEGS);
        if(lit>0){float w=lit*(SEG_W+SEG_GAP)+6;blit(c,bar,0,0,(int)w,(int)(BAR_B+12),BAR_L-6,BAR_T-6,BAR_L-6+(int)w,BAR_T-6+(int)(BAR_B+12));}
        text(c,Float.isNaN(rpm)?"—":String.format(Locale.US,"%,d",Math.round(rpm/10)*10),BAR_L,92,26,CYAN,Paint.Align.LEFT,num());
        // speed: plain white; yellow at the target; near it a small glitch (two thin slices nudged sideways) and SHIFT
        boolean valid=!Float.isNaN(rpm),near=valid&&rpm>=shiftTarget-300,at=valid&&rpm>=shiftTarget;
        String sp=fmt("%.0f",kmh);float sx=400,sy=258,ss=150;
        text(c,sp,sx,sy,ss,at?YEL:FG,Paint.Align.CENTER,num());
        if(near){
            long f=now/90;
            for(int k=0;k<2;k++){int h=hash(f*7+k);float y0=sy-ss*0.7f+(h&127)/127f*ss*0.6f,hh=5+(h>>>7&7),dx=(((h>>>11)&1)==0?-1:1)*(at?5:3);
                c.save();c.clipRect(240,y0,560,y0+hh);   // the slice drawn again, shifted and tinted: a fringe, not a black band
                text(c,sp,sx+dx,sy,ss,(k&1)==0?MAG:CYAN,Paint.Align.CENTER,num());c.restore();}
            if(at||blink(now,260))mono(c,"SHIFT",sx,112,20,YEL,Paint.Align.CENTER);
        }
        // gear
        String g=gear();boolean gate=manualGate(g);if(gate)g=g.substring(1);
        text(c,g,115,252,g.length()>1?78:112,MAG,Paint.Align.CENTER,num());if(gate)mono(c,"M",168,146,18,CYAN,Paint.Align.CENTER);
        // readouts
        boolean hot=!Float.isNaN(cool)&&cool>=HOT;
        row(c,146,fmt("%+.2f",boost),Float.isNaN(boost)?Float.NaN:(boost+1)/(boostTop()+1),CYAN);
        row(c,194,fmt("%.0f",cool),Float.isNaN(cool)?Float.NaN:(cool-40)/90,hot?danger():CYAN);
        row(c,242,fmt("%.0f",oil),Float.isNaN(oil)?Float.NaN:(oil-40)/110,CYAN);
        row(c,290,fmt("%.0f",pedal),Float.isNaN(pedal)?Float.NaN:pedal/100,MAG);
        if(hot){round(c,612,68,776,96,5,danger());bold(c,t("과열")+" "+fmt("%.0f",cool)+"°C",694,89,18,0xffffffff,Paint.Align.CENTER);}
        // the row on the grid: combined G, brake pressure, intake, battery, range
        float lat=v(11),lon=v(12),gs=Float.isNaN(lat)||Float.isNaN(lon)?Float.NaN:(float)Math.hypot(lat,lon);
        tile(c,0,fmt("%.2f",gs));tile(c,1,fmt("%.0f",v(10)));tile(c,2,fmt("%.0f",v(Telemetry.INTAKE)));
        tile(c,3,fmt("%.1f",v(Telemetry.BAT_VOLTAGE)));tile(c,4,fmt("%.0f",v(Telemetry.RANGE)));
        c.restore();
    }
    private static final float TILE_T=332,TILE_B=414,TILE_W=144,TILE_GAP=8;
    private static final String[] TILE_LABEL={"G","BRAKE  bar","INTAKE  °C","BATTERY  V","RANGE  km"};
    private static float tileL(int i){return BAR_L+i*(TILE_W+TILE_GAP);}
    private void tile(Canvas c,int i,String val){text(c,val,tileL(i)+TILE_W-12,TILE_B-14,30,FG,Paint.Align.RIGHT,num());}
    private void row(Canvas c,float y,String val,float frac,int color){
        text(c,val,776,y,26,FG,Paint.Align.RIGHT,num());
        fill(c,618,y+8,776,y+9,DIM);
        float w=Float.isNaN(frac)?0:158*clamp(frac,0,1);if(w>1)fill(c,618,y+7,618+w,y+10,color);
    }
    /** A small integer hash (no Random per frame). */
    private static int hash(long x){x=(x^(x>>>33))*0xff51afd7ed558ccdL;x^=x>>>33;return (int)x&0x7fffffff;}

    // ---- the still parts, drawn once ----
    @Override void trim(){face=recycle(face);bar=recycle(bar);faceKey="";}
    /** The lit bar: cyan up to the shift target, magenta past it, each segment with a soft glow. */
    private Bitmap buildBar(){
        Bitmap b=bitmap(800,(int)(BAR_B+12));Canvas c=new Canvas(b);c.translate(6-BAR_L,6-BAR_T);int sh=shiftSeg();
        for(int i=0;i<SEGS;i++){float l=BAR_L+i*(SEG_W+SEG_GAP);int col=i>=sh?MAG:CYAN;
            fill(c,l-2,BAR_T-3,l+SEG_W+2,BAR_B+3,(col&0xffffff)|0x26000000);fill(c,l,BAR_T,l+SEG_W,BAR_B,col);}
        return b;
    }
    /** Neon line: a faint wide stroke under a thin bright one. */
    private void neon(Canvas c,float x1,float y1,float x2,float y2,int color){
        line(c,x1,y1,x2,y2,6,(color&0xffffff)|0x30000000);line(c,x1,y1,x2,y2,1.8f,color);
    }
    /** Four thin corner brackets round a box. */
    private void brackets(Canvas c,float l,float t,float r,float b,float len,int color){
        neon(c,l,t,l+len,t,color);neon(c,l,t,l,t+len,color);neon(c,r-len,t,r,t,color);neon(c,r,t,r,t+len,color);
        neon(c,l,b,l+len,b,color);neon(c,l,b-len,l,b,color);neon(c,r-len,b,r,b,color);neon(c,r,b-len,r,b,color);
    }
    private Bitmap buildFace(){
        Bitmap b=bitmap(800,428);Canvas c=new Canvas(b);
        // night sky fading into the background, dark towers on the horizon (no lit windows)
        for(int y=0;y<(int)HORIZON;y+=4)fill(c,0,y,800,y+4,mix(0xff120630,BG,Math.min(1,y/HORIZON*1.3f)));
        java.util.Random rnd=new java.util.Random(2077);float x=0;
        while(x<800){float w=30+rnd.nextInt(50),h=14+rnd.nextInt(46);fill(c,x,HORIZON-h,x+w,HORIZON,mix(0xff1a0c30,0xff0e0620,rnd.nextFloat()));x+=w+rnd.nextInt(14);}
        // floor: a faint perspective grid running to the horizon
        fill(c,0,HORIZON,800,428,0xff0a0318);
        for(int k=1;k<=7;k++){float y=HORIZON+(428-HORIZON)*(float)Math.pow(k/7f,1.8);line(c,0,y,800,y,1,0x3a7a2cff);}
        for(int k=-9;k<=9;k++)line(c,400+k*14,HORIZON,400+k*120,428,1,0x2e7a2cff);
        neon(c,0,HORIZON,800,HORIZON,MAG);
        // scanlines over the whole face, very light
        for(int y=0;y<428;y+=3)fill(c,0,y,800,y+1,0x10000000);
        // rpm bar sockets and scale, the shift target marked
        for(int i=0;i<SEGS;i++){float l=BAR_L+i*(SEG_W+SEG_GAP);fill(c,l,BAR_T,l+SEG_W,BAR_B,DIM);}
        for(int k=0;k<=prof.maxRpm;k+=1000){float xx=BAR_L+(BAR_R-BAR_L)*k/prof.maxRpm;mono(c,String.valueOf(k/1000),Math.min(BAR_R-6,Math.max(BAR_L+6,xx)),BAR_B+18,12,MUT,Paint.Align.CENTER);}
        float st=BAR_L+(BAR_R-BAR_L)*clamp(shiftTarget/prof.maxRpm,0,1);neon(c,st,BAR_T-6,st,BAR_B+4,YEL);
        mono(c,"RPM",BAR_L+96,92,12,MUT,Paint.Align.LEFT);
        // gear and speed in corner brackets
        brackets(c,40,128,190,290,18,MAG);mono(c,"GEAR",115,310,13,MUT,Paint.Align.CENTER);
        brackets(c,236,118,564,290,22,CYAN);mono(c,"KM/H",400,310,14,CYAN,Paint.Align.CENTER);
        // the bottom row's boxes: dark glass over the grid, a thin cyan top edge
        for(int i=0;i<TILE_LABEL.length;i++){float l=tileL(i);fill(c,l,TILE_T,l+TILE_W,TILE_B,0xd80a0318);neon(c,l,TILE_T,l+TILE_W,TILE_T,i==1?MAG:CYAN);
            mono(c,TILE_LABEL[i],l+12,TILE_T+22,12,MUT,Paint.Align.LEFT);}
        // readout labels
        String[] lab={"BOOST  bar","WATER  °C","OIL  °C","THROTTLE  %"};
        for(int i=0;i<4;i++)mono(c,lab[i],618,120+i*48,12,MUT,Paint.Align.LEFT);
        return b;
    }
}
