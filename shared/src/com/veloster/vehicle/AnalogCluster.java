package com.veloster.vehicle;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.graphics.Typeface;

/**
 * 아날로그 screen: an old car's dashboard. Two big chrome-bezel dials on walnut (speed 0-260 km/h with the odometer
 * drum, rpm with its red zone), and between them two small ones (boost, coolant with the ECU oil temperature) and the
 * gear in a drum window. Cream faces, black numerals, orange needles that swing a little lazily, as old needles do.
 * A missing value parks its needle at the stop and reads —. Overheating (coolant 110 °C) lights the warning lamp.
 *
 * Built for the head unit: wood, bezels, faces, ticks and numerals are drawn once into a bitmap (again when the rpm range,
 * redline, boost scale or language changes); each frame turns the canvas round four needle Paths built once.
 */
final class AnalogCluster extends ClusterKit {
    private static final float SX=180,TX=620,BY=214,BR=150,SMX=400,BOY=104,TMY=324,SR=52,SPAN=130,SMALL_SPAN=120,KMH_MAX=260,T_MIN=40,T_MAX=130,HOT=110,LAMP_X=SMX-82,LAMP_Y=TMY+34;
    private static final int RED_ZONE=0xffc0392b,DRUM=0xff17130f,DRUM_FG=0xfff4ecd8;
    /** Dial looks, in the order a tap on the screen steps through (by the face's hue, the colourless ones last): name,
     *  face, numerals, ticks, needle, face rim shade, backdrop (0 walnut, 1 dark leather, 2 brushed metal). */
    static final String[] STYLE_NAMES={"크림 · 월넛","브리티시 그린","네이비","화이트","실버","블랙"};
    private static final int[][] STYLES={
        {0xfff2e6c8,0xff2a2018,0xff3a3026,0xffe0521c,0xffb8a582,0},
        {0xff1f4a33,0xfff1ead2,0xffd9d2b8,0xfff4f1e6,0xff10291c,0},
        {0xff1a2a4a,0xfff2f2f2,0xffd6dce8,0xffff7a1a,0xff0c1528,1},
        {0xfff7f7f5,0xff141414,0xff2a2a2a,0xffd81e1e,0xffc9c9c4,2},
        {0xffc9ccd0,0xff15171a,0xff2a2d31,0xffd81e1e,0xff8e9297,2},
        {0xff101012,0xfff0f0f0,0xffd8d8d8,0xffff6a1a,0xff000000,1}};
    /** The look in use (saved by VehicleDash as analog_style). */
    static volatile int style;
    private int FACE,NUM,TICK,NEEDLE,RIM,BACK;
    private long styleShownAt=Long.MIN_VALUE/2;
    /** A tap anywhere on the screen: the next look. */
    @Override boolean tap(float x,float y){style=(style+1)%STYLES.length;styleShownAt=now;return true;}
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
        float bt=boostTop();
        int st=Math.max(0,Math.min(STYLES.length-1,style));int[] s=STYLES[st];FACE=s[0];NUM=s[1];TICK=s[2];NEEDLE=s[3];RIM=s[4];BACK=s[5];
        String key=prof.maxRpm+"/"+prof.redline+"/"+bt+"/"+com.veloster.i18n.UiText.language()+"/"+st;
        if(face==null||!key.equals(faceKey)){face=recycle(face);face=buildFace(bt);faceKey=key;}
        c.drawBitmap(face,0,0,null);
        float kmh=v(7),rpm=v(0),boost=v(Telemetry.BOOST),cool=v(2),oil=v(Telemetry.OIL_ECU),odo=v(Telemetry.ODOMETER);
        // needles ease toward the reading (old needles lag a little, about 0.1 s); a missing reading parks the needle at the
        // stop; on a first frame or after a pause they are simply there
        float dt=lastDraw==0?1:(now-lastDraw)/1000f;lastDraw=now;k=dt>=0.5f||dt<0?1:1-(float)Math.exp(-dt/0.1f);
        aSpeed=ease(aSpeed,Float.isNaN(kmh)?-SPAN:-SPAN+2*SPAN*clamp(kmh/KMH_MAX,0,1.03f));
        aRpm=ease(aRpm,Float.isNaN(rpm)?-SPAN:-SPAN+2*SPAN*clamp(rpm/rpmTop(),0,1.03f));
        aBoost=ease(aBoost,Float.isNaN(boost)?-SMALL_SPAN:-SMALL_SPAN+2*SMALL_SPAN*clamp((boost-boostMin())/(bt-boostMin()),0,1));
        aTemp=ease(aTemp,Float.isNaN(cool)?-SMALL_SPAN:-SMALL_SPAN+2*SMALL_SPAN*clamp((cool-T_MIN)/(T_MAX-T_MIN),0,1));
        // drums: odometer, gear; the small dials' readings
        text(c,Float.isNaN(odo)?"——————":String.format(java.util.Locale.US,"%06d",Math.max(0,Math.min(999999,(long)odo))),SX,BY+90,22,DRUM_FG,Paint.Align.CENTER,Typeface.MONOSPACE);
        String g=gear();if(manualGate(g))g=g.substring(1);
        text(c,g,SMX,BY+18,44,DRUM_FG,Paint.Align.CENTER,num());
        text(c,fmt("%+.2f",boost),SMX,BOY+39,14,NUM,Paint.Align.CENTER,Typeface.DEFAULT_BOLD);
        boolean hot=!Float.isNaN(cool)&&cool>=HOT;
        if(hot){dot(c,LAMP_X,LAMP_Y,11,danger());bold(c,"H",LAMP_X,LAMP_Y+5,13,0xffffffff,Paint.Align.CENTER);}
        text(c,"OIL "+fmt("%.0f",oil),SMX,TMY+39,13,NUM,Paint.Align.CENTER,Typeface.DEFAULT_BOLD);
        if(Float.isNaN(kmh))bold(c,"—",SX,BY+56,26,NUM,Paint.Align.CENTER);
        if(Float.isNaN(rpm))bold(c,"—",TX,BY+56,26,NUM,Paint.Align.CENTER);
        // needles, each with a soft shadow below it
        needle(c,SX,BY,aSpeed,bigNeedle,12);needle(c,TX,BY,aRpm,bigNeedle,12);
        needle(c,SMX,BOY,aBoost,smallNeedle,7);needle(c,SMX,TMY,aTemp,smallNeedle,7);
        if(now-styleShownAt<1500){round(c,SMX-110,392,SMX+110,422,6,0xcc000000);bold(c,t("다이얼")+": "+t(STYLE_NAMES[Math.max(0,Math.min(STYLES.length-1,style))]),SMX,413,16,0xffffffff,Paint.Align.CENTER);}   // the look just picked
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

    // ---- the still parts, drawn once ----
    @Override void trim(){face=recycle(face);faceKey="";}
    private Bitmap buildFace(float bt){
        Bitmap b=bitmap(800,428);Canvas c=new Canvas(b);
        backdrop(c);
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
    /** Chrome bezel (light top, dark bottom), a dark lip, then the cream face shaded darker towards its rim. */
    private void dial(Canvas c,float cx,float cy,float rad,float bezel){
        p.setShader(new LinearGradient(0,cy-rad-bezel,0,cy+rad+bezel,new int[]{0xfff7f7f7,0xff7c7c7c,0xff3a3a3a,0xffd8d8d8},new float[]{0,0.45f,0.7f,1},Shader.TileMode.CLAMP));
        p.setStyle(Paint.Style.FILL);c.drawCircle(cx,cy,rad+bezel,p);
        p.setShader(new LinearGradient(0,cy-rad-bezel,0,cy+rad+bezel,new int[]{0xff2a2a2a,0xffcfcfcf},new float[]{0,1},Shader.TileMode.CLAMP));
        c.drawCircle(cx,cy,rad+bezel*0.45f,p);p.setShader(null);
        dot(c,cx,cy,rad+2,0xff1a140e);
        for(int k=0;k<6;k++)dot(c,cx,cy,rad*(1-k*0.06f),mix(FACE,RIM,0.55f+k*0.09f));
        dot(c,cx,cy,rad*0.66f,FACE);
    }
    /** Walnut (bands of brown with a fixed grain), dark leather (a fine stipple) or brushed metal (fine horizontal lines). */
    private void backdrop(Canvas c){
        java.util.Random rnd=new java.util.Random(30);
        if(BACK==0){int wood=0xff3b2416;fill(c,0,0,800,428,wood);
            for(int y=0;y<428;y+=2){int sh=rnd.nextInt(18)-9;line(c,0,y,800,y+rnd.nextInt(3)-1,2,mix(0xff5a3822,wood,0.35f+sh/40f));}
            for(int k=0;k<26;k++){float y=rnd.nextFloat()*428;line(c,0,y,800,y+rnd.nextFloat()*8-4,1,0x55201208);}}
        else if(BACK==1){fill(c,0,0,800,428,0xff151313);
            for(int k=0;k<5000;k++){float x=rnd.nextFloat()*800,y=rnd.nextFloat()*428;fill(c,x,y,x+2,y+2,rnd.nextBoolean()?0x14ffffff:0x22000000);}
            for(int y=40;y<428;y+=96)line(c,0,y,800,y,1,0x18ffffff);}   // stitched panels
        else{fill(c,0,0,800,428,0xff5d6065);
            for(int y=0;y<428;y++){int sh=rnd.nextInt(20)-10;line(c,0,y,800,y,1,mix(0xff7d8187,0xff4a4d52,0.5f+sh/30f));}}
    }
    private void tick(Canvas c,float cx,float cy,float r0,float len,float deg,float w,int color){line(c,px(cx,r0,deg),py(cy,r0,deg),px(cx,r0-len,deg),py(cy,r0-len,deg),w,color);}
    private void drum(Canvas c,float l,float t,float rr,float b){round(c,l-3,t-3,rr+3,b+3,4,0xff8c8c8c);round(c,l,t,rr,b,3,DRUM);}
}
