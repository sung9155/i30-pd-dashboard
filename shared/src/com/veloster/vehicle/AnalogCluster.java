package com.veloster.vehicle;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.graphics.Typeface;

/**
 * 아날로그 screen: two big dials (speed 0-260 km/h with the odometer, rpm with its red zone), and between them two small
 * ones (boost, coolant with the ECU oil temperature) and the gear. Two looks, a tap on the screen switches (saved by
 * VehicleDash as analog_look):
 *  - 클래식: chrome bezels, black faces, white numerals, orange needles that swing a little lazily, on plain black.
 *  - 디지털: the same dials as a digital cluster draws them: a ring of LED segments lit up to the reading (red past the
 *    redline / 110 °C), a thin glowing needle floating on the outer ring, and the reading in big digits in the middle.
 * A missing value parks its needle at the stop and reads —. Overheating (coolant 110 °C) lights the warning lamp.
 * (2026-11-27, the owner: of the six classic colours only black stays, without the leather; add a digital needle look.)
 *
 * Built for the head unit: faces, ticks, unlit segments and numerals are drawn once into a bitmap (again when the rpm
 * range, redline, boost scale, look or language changes); each frame draws the needles (Paths built once, turned with the
 * canvas, or plain lines) and, in the digital look, the lit segments as short lines.
 */
final class AnalogCluster extends ClusterKit {
    private static final float SX=180,TX=620,BY=214,BR=150,SMX=400,BOY=104,TMY=324,SR=52,SPAN=130,SMALL_SPAN=120,KMH_MAX=260,T_MIN=40,T_MAX=130,HOT=110,LAMP_X=SMX-82,LAMP_Y=TMY+34;
    private static final int RED_ZONE=0xffd8302a,DRUM=0xff111111,DRUM_FG=0xfff4f4f4;
    // 클래식: black faces
    private static final int FACE=0xff101012,NUM=0xfff0f0f0,TICK=0xffd8d8d8,NEEDLE=0xffff6a1a,RIM=0xff000000;
    // 디지털
    private static final int D_RING=0xff262c33,D_OFF=0xff161b20,D_OFF_RED=0xff3a1412,D_ON=0xff2fd6ff,D_GLOW=0x552fd6ff,D_TXT=0xfff4f8fb,D_MUT=0xff7d8a96;
    private static final float SEG_STEP=5,SMALL_SEG_STEP=10;
    static final String[] STYLE_NAMES={"클래식","디지털"};
    /** The look in use: 0 클래식, 1 디지털 (saved by VehicleDash as analog_look). */
    static volatile int style;
    private long styleShownAt=Long.MIN_VALUE/2;
    /** A tap anywhere on the screen: the other look. */
    @Override boolean tap(float x,float y){style=style==1?0:1;styleShownAt=now;return true;}
    private boolean digital(){return style==1;}
    private Bitmap face;private String faceKey="";
    /** Needles point to 12 o'clock from the pivot at (0,0), with a short counterweight; built once, turned with the canvas. */
    private final Path bigNeedle=needle(BR-12,24,4.5f),smallNeedle=needle(SR-8,12,3);
    private long lastDraw;private float k;
    private float aSpeed=-SPAN,aRpm=-SPAN,aBoost=-SMALL_SPAN,aTemp=-SMALL_SPAN;

    private static Path needle(float len,float tail,float w){
        Path n=new Path();n.moveTo(-w,tail);n.lineTo(-1.2f,-len);n.lineTo(1.2f,-len);n.lineTo(w,tail);n.close();return n;
    }
    private static float boostMin(){return -1;}
    private float rpmTop(){return prof.maxRpm;}

    @Override protected void draw(Canvas c){
        c.save();c.translate(0,52);
        float bt=boostTop();boolean dg=digital();
        String key=prof.maxRpm+"/"+prof.redline+"/"+bt+"/"+com.veloster.i18n.UiText.language()+"/"+(dg?1:0);
        if(face==null||!key.equals(faceKey)){face=recycle(face);face=dg?buildDigital(bt):buildFace(bt);faceKey=key;}
        c.drawBitmap(face,0,0,null);
        float kmh=v(7),rpm=v(0),boost=v(Telemetry.BOOST),cool=v(2),oil=v(Telemetry.OIL_ECU),odo=v(Telemetry.ODOMETER);
        // needles ease toward the reading (about 0.1 s); a missing reading parks the needle at the stop; on a first frame
        // or after a pause they are simply there
        float dt=lastDraw==0?1:(now-lastDraw)/1000f;lastDraw=now;k=dt>=0.5f||dt<0?1:1-(float)Math.exp(-dt/0.1f);
        aSpeed=ease(aSpeed,Float.isNaN(kmh)?-SPAN:-SPAN+2*SPAN*clamp(kmh/KMH_MAX,0,1.03f));
        aRpm=ease(aRpm,Float.isNaN(rpm)?-SPAN:-SPAN+2*SPAN*clamp(rpm/rpmTop(),0,1.03f));
        aBoost=ease(aBoost,Float.isNaN(boost)?-SMALL_SPAN:-SMALL_SPAN+2*SMALL_SPAN*clamp((boost-boostMin())/(bt-boostMin()),0,1));
        aTemp=ease(aTemp,Float.isNaN(cool)?-SMALL_SPAN:-SMALL_SPAN+2*SMALL_SPAN*clamp((cool-T_MIN)/(T_MAX-T_MIN),0,1));
        boolean hot=!Float.isNaN(cool)&&cool>=HOT;
        String g=gear();if(manualGate(g))g=g.substring(1);
        String odoText=Float.isNaN(odo)?"——————":String.format(java.util.Locale.US,"%06d",Math.max(0,Math.min(999999,(long)odo)));
        if(dg){
            float redA=-SPAN+2*SPAN*prof.redline/rpmTop();
            segments(c,SX,BY,BR,aSpeed,SPAN,SEG_STEP,Float.NaN,!Float.isNaN(kmh));segments(c,TX,BY,BR,aRpm,SPAN,SEG_STEP,redA,!Float.isNaN(rpm));
            float hotA=-SMALL_SPAN+2*SMALL_SPAN*(HOT-T_MIN)/(T_MAX-T_MIN);
            segments(c,SMX,BOY,SR,aBoost,SMALL_SPAN,SMALL_SEG_STEP,Float.NaN,!Float.isNaN(boost));segments(c,SMX,TMY,SR,aTemp,SMALL_SPAN,SMALL_SEG_STEP,hotA,!Float.isNaN(cool));
            floatNeedle(c,SX,BY,BR,aSpeed);floatNeedle(c,TX,BY,BR,aRpm);floatNeedle(c,SMX,BOY,SR,aBoost);floatNeedle(c,SMX,TMY,SR,aTemp);
            text(c,Float.isNaN(kmh)?"—":fmt("%.0f",kmh),SX,BY+24,66,D_TXT,Paint.Align.CENTER,num());
            text(c,Float.isNaN(rpm)?"—":String.format(java.util.Locale.US,"%,d",Math.round(rpm/10)*10),TX,BY+18,46,rpm>=prof.redline?RED_ZONE:D_TXT,Paint.Align.CENTER,num());
            text(c,g,SMX,BY+20,46,D_ON,Paint.Align.CENTER,num());
            text(c,fmt("%+.2f",boost),SMX,BOY+8,17,D_TXT,Paint.Align.CENTER,num());
            text(c,fmt("%.0f",cool)+"°",SMX,TMY+8,17,hot?RED_ZONE:D_TXT,Paint.Align.CENTER,num());
            text(c,"OIL "+fmt("%.0f",oil),SMX,TMY+SR+20,12,D_MUT,Paint.Align.CENTER,Typeface.DEFAULT_BOLD);
            text(c,"ODO "+odoText,SX,BY+BR-12,15,D_MUT,Paint.Align.CENTER,Typeface.MONOSPACE);
        }else{
            text(c,odoText,SX,BY+90,22,DRUM_FG,Paint.Align.CENTER,Typeface.MONOSPACE);
            text(c,g,SMX,BY+18,44,DRUM_FG,Paint.Align.CENTER,num());
            text(c,fmt("%+.2f",boost),SMX,BOY+39,14,NUM,Paint.Align.CENTER,Typeface.DEFAULT_BOLD);
            text(c,"OIL "+fmt("%.0f",oil),SMX,TMY+39,13,NUM,Paint.Align.CENTER,Typeface.DEFAULT_BOLD);
            if(Float.isNaN(kmh))bold(c,"—",SX,BY+56,26,NUM,Paint.Align.CENTER);
            if(Float.isNaN(rpm))bold(c,"—",TX,BY+56,26,NUM,Paint.Align.CENTER);
            // needles, each with a soft shadow below it
            needle(c,SX,BY,aSpeed,bigNeedle,12);needle(c,TX,BY,aRpm,bigNeedle,12);
            needle(c,SMX,BOY,aBoost,smallNeedle,7);needle(c,SMX,TMY,aTemp,smallNeedle,7);
        }
        if(hot){dot(c,LAMP_X,LAMP_Y,11,danger());bold(c,"H",LAMP_X,LAMP_Y+5,13,0xffffffff,Paint.Align.CENTER);}
        if(now-styleShownAt<1500){round(c,SMX-90,392,SMX+90,422,6,0xcc000000);bold(c,t("다이얼")+": "+t(STYLE_NAMES[dg?1:0]),SMX,413,16,0xffffffff,Paint.Align.CENTER);}   // the look just picked
        c.restore();
    }
    private float ease(float a,float to){return Math.abs(to-a)<0.3f?to:a+(to-a)*k;}
    private static Typeface num(){Typeface f=LambdaCluster.FACE_BOLD;return f!=null?f:Typeface.DEFAULT_BOLD;}
    private void needle(Canvas c,float cx,float cy,float deg,Path n,float hub){
        p.setStyle(Paint.Style.FILL);
        c.save();c.translate(cx+2.5f,cy+3);c.rotate(deg);p.setColor(0x55000000);c.drawPath(n,p);c.restore();
        c.save();c.translate(cx,cy);c.rotate(deg);p.setColor(NEEDLE);c.drawPath(n,p);c.restore();
        dot(c,cx,cy,hub,0xffb9b9b9);dot(c,cx,cy,hub*0.45f,0xff3a3a3a);
    }
    // ---- 디지털 ----
    /** Segment radii: the outer ring the needle floats on. */
    private static float segOut(float rad){return rad-6;}
    private static float segIn(float rad){return rad>100?rad-22:rad-13;}
    /** The lit segments, from the stop up to the needle (whole segments; red at and past red, when red is a number). */
    private void segments(Canvas c,float cx,float cy,float rad,float deg,float span,float step,float red,boolean has){
        if(!has)return;float o=segOut(rad),i=segIn(rad),w=rad>100?3.6f:3;
        for(float d=-span;d<=deg+0.01f&&d<=span+0.01f;d+=step){int col=!Float.isNaN(red)&&d>=red-0.01f?RED_ZONE:D_ON;
            line(c,px(cx,i,d),py(cy,i,d),px(cx,o,d),py(cy,o,d),w,col);}
    }
    /** A thin needle on the outer ring only (the middle keeps the digits): a soft glow under a bright line. */
    private void floatNeedle(Canvas c,float cx,float cy,float rad,float deg){
        float r0=rad>100?rad*0.62f:rad*0.5f,r1=rad-2;
        line(c,px(cx,r0,deg),py(cy,r0,deg),px(cx,r1,deg),py(cy,r1,deg),rad>100?9:7,D_GLOW);
        line(c,px(cx,r0,deg),py(cy,r0,deg),px(cx,r1,deg),py(cy,r1,deg),rad>100?2.6f:2,0xffffffff);
    }
    private Bitmap buildDigital(float bt){
        Bitmap b=bitmap(800,428);Canvas c=new Canvas(b);
        fill(c,0,0,800,428,0xff000000);
        float redA=-SPAN+2*SPAN*prof.redline/rpmTop(),hotA=-SMALL_SPAN+2*SMALL_SPAN*(HOT-T_MIN)/(T_MAX-T_MIN);
        ring(c,SX,BY,BR,SPAN,SEG_STEP,Float.NaN);ring(c,TX,BY,BR,SPAN,SEG_STEP,redA);ring(c,SMX,BOY,SR,SMALL_SPAN,SMALL_SEG_STEP,Float.NaN);ring(c,SMX,TMY,SR,SMALL_SPAN,SMALL_SEG_STEP,hotA);
        // scale numbers inside the segments
        for(int k=0;k<=KMH_MAX;k+=20){float d=-SPAN+2*SPAN*k/KMH_MAX;text(c,String.valueOf(k),px(SX,BR-38,d),py(BY,BR-38,d)+5,13,D_MUT,Paint.Align.CENTER,Typeface.DEFAULT_BOLD);}
        for(int k=0;k<=rpmTop();k+=1000){float d=-SPAN+2*SPAN*k/rpmTop();text(c,String.valueOf(k/1000),px(TX,BR-38,d),py(BY,BR-38,d)+5,15,k>=prof.redline?RED_ZONE:D_MUT,Paint.Align.CENTER,Typeface.DEFAULT_BOLD);}
        bold(c,"km/h",SX,BY+50,14,D_MUT,Paint.Align.CENTER);bold(c,"r/min",TX,BY+46,14,D_MUT,Paint.Align.CENTER);
        // gear window between the big dials
        round(c,SMX-40,BY-26,SMX+40,BY+30,6,D_RING);round(c,SMX-38,BY-24,SMX+38,BY+28,5,0xff05080b);
        bold(c,t("부스트"),SMX,BOY+26,11,D_MUT,Paint.Align.CENTER);bold(c,t("냉각수"),SMX,TMY+26,11,D_MUT,Paint.Align.CENTER);
        dot(c,LAMP_X,LAMP_Y,15,D_RING);dot(c,LAMP_X,LAMP_Y,12,0xff120a08);bold(c,"H",LAMP_X,LAMP_Y+5,13,0xff4a2a22,Paint.Align.CENTER);   // the overheat lamp, dark until 110 °C
        return b;
    }
    /** A thin outer ring and the unlit segments (dark red from red on). */
    private void ring(Canvas c,float cx,float cy,float rad,float span,float step,float red){
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.5f);p.setColor(D_RING);r.set(cx-rad,cy-rad,cx+rad,cy+rad);c.drawArc(r,-90-span-4,2*span+8,false,p);p.setStyle(Paint.Style.FILL);
        float o=segOut(rad),i=segIn(rad),w=rad>100?3.6f:3;
        for(float d=-span;d<=span+0.01f;d+=step)line(c,px(cx,i,d),py(cy,i,d),px(cx,o,d),py(cy,o,d),w,!Float.isNaN(red)&&d>=red-0.01f?D_OFF_RED:D_OFF);
    }

    // ---- 클래식, the still parts, drawn once ----
    @Override void trim(){face=recycle(face);faceKey="";}
    private Bitmap buildFace(float bt){
        Bitmap b=bitmap(800,428);Canvas c=new Canvas(b);
        fill(c,0,0,800,428,0xff000000);
        // the big dials
        dial(c,SX,BY,BR,16);dial(c,TX,BY,BR,16);dial(c,SMX,BOY,SR,10);dial(c,SMX,TMY,SR,10);
        // speed: 10 km/h ticks, numbers every 20
        for(int k=0;k<=KMH_MAX;k+=10){float d=-SPAN+2*SPAN*k/KMH_MAX;boolean maj=k%20==0;tick(c,SX,BY,BR-4,maj?16:9,d,maj?3:1.5f,TICK);
            if(maj)text(c,String.valueOf(k),px(SX,BR-34,d),py(BY,BR-34,d)+7,k>=100?17:19,NUM,Paint.Align.CENTER,Typeface.DEFAULT_BOLD);}
        bold(c,"km/h",SX,BY-50,15,NUM,Paint.Align.CENTER);
        drum(c,SX-56,BY+68,SX+56,BY+98);
        // rpm: red zone, 500 rpm ticks, numbers every 1,000
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(10);p.setColor(RED_ZONE);float rr=BR-9;r.set(TX-rr,BY-rr,TX+rr,BY+rr);
        float a0=-SPAN+2*SPAN*prof.redline/rpmTop(),a1=SPAN;c.drawArc(r,a0-90,a1-a0,false,p);p.setStyle(Paint.Style.FILL);
        for(int k=0;k<=rpmTop();k+=500){float d=-SPAN+2*SPAN*k/rpmTop();boolean maj=k%1000==0;tick(c,TX,BY,BR-4,maj?16:9,d,maj?3:1.5f,k>=prof.redline?RED_ZONE:TICK);
            if(maj)text(c,String.valueOf(k/1000),px(TX,BR-36,d),py(BY,BR-36,d)+8,22,k>=prof.redline?RED_ZONE:NUM,Paint.Align.CENTER,Typeface.DEFAULT_BOLD);}
        bold(c,"x1000 r/min",TX,BY-50,13,NUM,Paint.Align.CENTER);
        // gear drum between the dials
        round(c,SMX-40,BY-26,SMX+40,BY+30,6,0xff8c8c8c);round(c,SMX-36,BY-22,SMX+36,BY+26,4,DRUM);
        // boost: -1 .. top, ticks every 0.5 bar
        for(float k=-1;k<=bt+0.01f;k+=0.5f){float d=-SMALL_SPAN+2*SMALL_SPAN*(k+1)/(bt+1);boolean maj=Math.abs(k-Math.round(k))<0.01f;tick(c,SMX,BOY,SR-3,maj?9:5,d,maj?2:1,TICK);
            if(maj)text(c,String.valueOf(Math.round(k)),px(SMX,SR-18,d),py(BOY,SR-18,d)+5,12,NUM,Paint.Align.CENTER,Typeface.DEFAULT_BOLD);}
        bold(c,t("부스트"),SMX,BOY+23,11,NUM,Paint.Align.CENTER);
        // coolant: C .. H, red from 110 °C
        for(int k=(int)T_MIN;k<=T_MAX;k+=10){float d=-SMALL_SPAN+2*SMALL_SPAN*(k-T_MIN)/(T_MAX-T_MIN);tick(c,SMX,TMY,SR-3,k%30==10?9:5,d,1.5f,k>=HOT?RED_ZONE:TICK);}
        bold(c,"C",px(SMX,SR-18,-SMALL_SPAN),py(TMY,SR-18,-SMALL_SPAN)+5,13,NUM,Paint.Align.CENTER);bold(c,"H",px(SMX,SR-18,SMALL_SPAN),py(TMY,SR-18,SMALL_SPAN)+5,13,RED_ZONE,Paint.Align.CENTER);
        bold(c,t("냉각수"),SMX,TMY+23,11,NUM,Paint.Align.CENTER);
        dot(c,LAMP_X,LAMP_Y,15,0xff9a9a9a);dot(c,LAMP_X,LAMP_Y,12,0xff2a1a12);bold(c,"H",LAMP_X,LAMP_Y+5,13,0xff5a3a2a,Paint.Align.CENTER);   // the overheat lamp, dark until 110 °C
        return b;
    }
    /** Chrome bezel (light top, dark bottom), a dark lip, then the black face shaded darker towards its rim. */
    private void dial(Canvas c,float cx,float cy,float rad,float bezel){
        p.setShader(new LinearGradient(0,cy-rad-bezel,0,cy+rad+bezel,new int[]{0xfff7f7f7,0xff7c7c7c,0xff3a3a3a,0xffd8d8d8},new float[]{0,0.45f,0.7f,1},Shader.TileMode.CLAMP));
        p.setStyle(Paint.Style.FILL);c.drawCircle(cx,cy,rad+bezel,p);
        p.setShader(new LinearGradient(0,cy-rad-bezel,0,cy+rad+bezel,new int[]{0xff2a2a2a,0xffcfcfcf},new float[]{0,1},Shader.TileMode.CLAMP));
        c.drawCircle(cx,cy,rad+bezel*0.45f,p);p.setShader(null);
        dot(c,cx,cy,rad+2,0xff1a140e);
        for(int k=0;k<6;k++)dot(c,cx,cy,rad*(1-k*0.06f),mix(FACE,RIM,0.55f+k*0.09f));
        dot(c,cx,cy,rad*0.66f,FACE);
    }
    private void tick(Canvas c,float cx,float cy,float r0,float len,float deg,float w,int color){line(c,px(cx,r0,deg),py(cy,r0,deg),px(cx,r0-len,deg),py(cy,r0-len,deg),w,color);}
    private void drum(Canvas c,float l,float t,float rr,float b){round(c,l-3,t-3,rr+3,b+3,4,0xff8c8c8c);round(c,l,t,rr,b,3,DRUM);}
}
