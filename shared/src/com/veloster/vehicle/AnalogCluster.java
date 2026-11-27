package com.veloster.vehicle;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.graphics.Typeface;

/**
 * 아날로그 screen: needle gauges in five looks, a tap on the screen steps to the next (saved by VehicleDash as analog_look).
 * The first four share one layout: two big dials (speed 0-260 km/h with the odometer, rpm with its red zone), between them
 * two small ones (boost, coolant with the ECU oil temperature) and the gear.
 *  - 클래식: chrome bezels, black faces, white numerals, orange needles that swing a little lazily, on plain black.
 *  - 디지털: a ring of LED segments lit up to the reading (red past the redline / 110 °C), a thin glowing needle floating
 *    on the outer ring, the reading in big digits in the middle.
 *  - 미니멀: hairline arcs and ticks, thin amber needles, light digits; nothing else.
 *  - 아크: a glowing band filling up to the reading, cyan through violet to magenta, big digits in the middle.
 *  - 레이스: one big tachometer in the middle on carbon (a race car's cluster): the gear big in yellow, the speed below it,
 *    boost and coolant as bars at the sides; the bezel turns red near the redline and flashes at it.
 * A missing value parks its needle at the stop and reads —. Overheating (coolant 110 °C) lights the warning lamp.
 * (2026-11-27, the owner: of the six classic colours only black stays, without the leather; then a digital needle look;
 * then a few more looks.)
 *
 * Built for the head unit: faces, ticks, unlit segments and numerals are drawn once into a bitmap (again when the rpm
 * range, redline, boost scale, look or language changes); each frame draws the needles (Paths built once, turned with the
 * canvas, or plain lines), the lit segments / band as short lines, plain rects and text.
 */
final class AnalogCluster extends ClusterKit {
    private static final float SX=180,TX=620,BY=214,BR=150,SMX=400,BOY=104,TMY=324,SR=52,SPAN=130,SMALL_SPAN=120,KMH_MAX=260,T_MIN=40,T_MAX=130,HOT=110,LAMP_X=SMX-82,LAMP_Y=TMY+34;
    private static final int RED_ZONE=0xffd8302a,DRUM=0xff111111,DRUM_FG=0xfff4f4f4;
    { tintKey=0xff2fd6ff;keep=new int[]{RED_ZONE,0xff3a1412}; }   // other themes: needles, digital cyan and the arc follow the accent; the red zone stays
    // 클래식: black faces
    private static final int FACE=0xff101012,NUM=0xfff0f0f0,TICK=0xffd8d8d8,NEEDLE=0xffff6a1a,RIM=0xff000000;
    // 디지털
    private static final int D_RING=0xff262c33,D_OFF=0xff161b20,D_OFF_RED=0xff3a1412,D_ON=0xff2fd6ff,D_GLOW=0x552fd6ff,D_TXT=0xfff4f8fb,D_MUT=0xff7d8a96;
    private static final float SEG_STEP=5,SMALL_SEG_STEP=10;
    // 미니멀
    private static final int M_LINE=0x66ffffff,M_TICK=0xccffffff,M_NUM=0xffb8bec6,M_NEEDLE=0xffffa31a;
    // 아크: cyan → violet → magenta
    private static final int A_C0=0xff19f0ff,A_C1=0xff7a5cff,A_C2=0xffff2bd6,A_TRACK=0xff13171b;
    private static final float ARC_STEP=2,SMALL_ARC_STEP=4;
    // 레이스: one big tacho
    private static final float RX=400,RY=214,RR=190,RSPAN=135;
    private static final int R_FACE=0xff17191c,R_BEZEL=0xff3a3d42,R_NEEDLE=0xffff4a1c,R_GEAR=0xffffd21f;
    private static final float BAR_T=70,BAR_B=350,BAR_W=22,BOOST_L=46,COOL_L=800-46-22;

    static final String[] STYLE_NAMES={"클래식","디지털","미니멀","아크","레이스"};
    static final int CLASSIC=0,DIGITAL=1,MINIMAL=2,ARC=3,RACE=4;
    /** The look in use (saved by VehicleDash as analog_look). */
    static volatile int style;
    private long styleShownAt=Long.MIN_VALUE/2;
    /** A tap anywhere on the screen: the next look. */
    @Override boolean tap(float x,float y){style=(look()+1)%STYLE_NAMES.length;styleShownAt=now;return true;}
    private static int look(){int s=style;return s<0||s>=STYLE_NAMES.length?0:s;}
    private Bitmap face;private String faceKey="";
    /** Needles point to 12 o'clock from the pivot at (0,0), with a short counterweight; built once, turned with the canvas. */
    private final Path bigNeedle=needle(BR-12,24,4.5f),smallNeedle=needle(SR-8,12,3),raceNeedle=needle(RR-18,30,6);
    private long lastDraw;private float k;
    private float aSpeed=-SPAN,aRpm=-SPAN,aBoost=-SMALL_SPAN,aTemp=-SMALL_SPAN,aRace=-RSPAN;

    private static Path needle(float len,float tail,float w){
        Path n=new Path();n.moveTo(-w,tail);n.lineTo(-1.2f,-len);n.lineTo(1.2f,-len);n.lineTo(w,tail);n.close();return n;
    }
    private static float boostMin(){return -1;}
    private float rpmTop(){return prof.maxRpm;}

    @Override protected void draw(Canvas c){
        c.save();c.translate(0,52);
        float bt=boostTop();int lk=look();
        String key=prof.maxRpm+"/"+prof.redline+"/"+bt+"/"+com.veloster.i18n.UiText.language()+"/"+lk+"/"+Theme.current().id;
        if(face==null||!key.equals(faceKey)){face=recycle(face);
            face=lk==DIGITAL?buildDigital():lk==MINIMAL?buildMinimal(bt):lk==ARC?buildArc():lk==RACE?buildRace():buildFace(bt);faceKey=key;}
        c.drawBitmap(face,0,0,null);
        float kmh=v(7),rpm=v(0),boost=v(Telemetry.BOOST),cool=v(2),oil=v(Telemetry.OIL_ECU),odo=v(Telemetry.ODOMETER);
        // needles ease toward the reading (about 0.1 s); a missing reading parks the needle at the stop; on a first frame
        // or after a pause they are simply there
        float dt=lastDraw==0?1:(now-lastDraw)/1000f;lastDraw=now;k=dt>=0.5f||dt<0?1:1-(float)Math.exp(-dt/0.1f);
        aSpeed=ease(aSpeed,Float.isNaN(kmh)?-SPAN:-SPAN+2*SPAN*clamp(kmh/KMH_MAX,0,1.03f));
        aRpm=ease(aRpm,Float.isNaN(rpm)?-SPAN:-SPAN+2*SPAN*clamp(rpm/rpmTop(),0,1.03f));
        aBoost=ease(aBoost,Float.isNaN(boost)?-SMALL_SPAN:-SMALL_SPAN+2*SMALL_SPAN*clamp((boost-boostMin())/(bt-boostMin()),0,1));
        aTemp=ease(aTemp,Float.isNaN(cool)?-SMALL_SPAN:-SMALL_SPAN+2*SMALL_SPAN*clamp((cool-T_MIN)/(T_MAX-T_MIN),0,1));
        aRace=ease(aRace,Float.isNaN(rpm)?-RSPAN:-RSPAN+2*RSPAN*clamp(rpm/rpmTop(),0,1.02f));
        boolean hot=!Float.isNaN(cool)&&cool>=HOT;
        String g=gear();if(manualGate(g))g=g.substring(1);
        String odoText=Float.isNaN(odo)?"——————":String.format(java.util.Locale.US,"%06d",Math.max(0,Math.min(999999,(long)odo)));
        String rpmText=Float.isNaN(rpm)?"—":String.format(java.util.Locale.US,"%,d",Math.round(rpm/10)*10);
        float redA=-SPAN+2*SPAN*prof.redline/rpmTop(),hotA=-SMALL_SPAN+2*SMALL_SPAN*(HOT-T_MIN)/(T_MAX-T_MIN);
        switch(lk){
            case DIGITAL:
                segments(c,SX,BY,BR,aSpeed,SPAN,SEG_STEP,Float.NaN,!Float.isNaN(kmh));segments(c,TX,BY,BR,aRpm,SPAN,SEG_STEP,redA,!Float.isNaN(rpm));
                segments(c,SMX,BOY,SR,aBoost,SMALL_SPAN,SMALL_SEG_STEP,Float.NaN,!Float.isNaN(boost));segments(c,SMX,TMY,SR,aTemp,SMALL_SPAN,SMALL_SEG_STEP,hotA,!Float.isNaN(cool));
                floatNeedle(c,SX,BY,BR,aSpeed);floatNeedle(c,TX,BY,BR,aRpm);floatNeedle(c,SMX,BOY,SR,aBoost);floatNeedle(c,SMX,TMY,SR,aTemp);
                middle(c,kmh,rpm,rpmText,g,boost,cool,hot,D_ON);
                text(c,"OIL "+fmt("%.0f",oil),SMX,TMY+SR+20,12,D_MUT,Paint.Align.CENTER,Typeface.DEFAULT_BOLD);
                text(c,"ODO "+odoText,SX,BY+BR-12,15,D_MUT,Paint.Align.CENTER,Typeface.MONOSPACE);
                break;
            case ARC:
                band(c,SX,BY,BR,aSpeed,SPAN,ARC_STEP,!Float.isNaN(kmh));band(c,TX,BY,BR,aRpm,SPAN,ARC_STEP,!Float.isNaN(rpm));
                band(c,SMX,BOY,SR,aBoost,SMALL_SPAN,SMALL_ARC_STEP,!Float.isNaN(boost));band(c,SMX,TMY,SR,aTemp,SMALL_SPAN,SMALL_ARC_STEP,!Float.isNaN(cool));
                middle(c,kmh,rpm,rpmText,g,boost,cool,hot,A_C2);
                text(c,"OIL "+fmt("%.0f",oil),SMX,TMY+SR+20,12,D_MUT,Paint.Align.CENTER,Typeface.DEFAULT_BOLD);
                text(c,"ODO "+odoText,SX,BY+BR-12,15,D_MUT,Paint.Align.CENTER,Typeface.MONOSPACE);
                break;
            case MINIMAL:
                thinNeedle(c,SX,BY,BR,aSpeed);thinNeedle(c,TX,BY,BR,aRpm);thinNeedle(c,SMX,BOY,SR,aBoost);thinNeedle(c,SMX,TMY,SR,aTemp);
                text(c,Float.isNaN(kmh)?"—":fmt("%.0f",kmh),SX,BY+74,36,0xffffffff,Paint.Align.CENTER,Typeface.DEFAULT);
                text(c,rpmText,TX,BY+74,30,!Float.isNaN(rpm)&&rpm>=prof.redline?RED_ZONE:0xffffffff,Paint.Align.CENTER,Typeface.DEFAULT);
                text(c,g,SMX,BY+18,40,0xffffffff,Paint.Align.CENTER,Typeface.DEFAULT);
                text(c,fmt("%+.2f",boost),SMX,BOY+34,13,M_NUM,Paint.Align.CENTER,Typeface.DEFAULT);
                text(c,fmt("%.0f",cool)+"°  OIL "+fmt("%.0f",oil),SMX,TMY+34,12,hot?RED_ZONE:M_NUM,Paint.Align.CENTER,Typeface.DEFAULT);
                text(c,odoText+" km",SX,BY+122,13,0xff6d747c,Paint.Align.CENTER,Typeface.DEFAULT);
                break;
            case RACE:race(c,kmh,rpm,boost,cool,oil,hot,g,odoText,bt);break;
            default:
                text(c,odoText,SX,BY+90,22,DRUM_FG,Paint.Align.CENTER,Typeface.MONOSPACE);
                text(c,g,SMX,BY+18,44,DRUM_FG,Paint.Align.CENTER,num());
                text(c,fmt("%+.2f",boost),SMX,BOY+39,14,NUM,Paint.Align.CENTER,Typeface.DEFAULT_BOLD);
                text(c,"OIL "+fmt("%.0f",oil),SMX,TMY+39,13,NUM,Paint.Align.CENTER,Typeface.DEFAULT_BOLD);
                if(Float.isNaN(kmh))bold(c,"—",SX,BY+56,26,NUM,Paint.Align.CENTER);
                if(Float.isNaN(rpm))bold(c,"—",TX,BY+56,26,NUM,Paint.Align.CENTER);
                // needles, each with a soft shadow below it
                needle(c,SX,BY,aSpeed,bigNeedle,12,NEEDLE);needle(c,TX,BY,aRpm,bigNeedle,12,NEEDLE);
                needle(c,SMX,BOY,aBoost,smallNeedle,7,NEEDLE);needle(c,SMX,TMY,aTemp,smallNeedle,7,NEEDLE);
        }
        if(hot&&lk!=RACE){dot(c,LAMP_X,LAMP_Y,11,danger());bold(c,"H",LAMP_X,LAMP_Y+5,13,0xffffffff,Paint.Align.CENTER);}
        if(now-styleShownAt<1500){round(c,SMX-90,392,SMX+90,422,6,0xcc000000);bold(c,t("다이얼")+": "+t(STYLE_NAMES[lk]),SMX,413,16,0xffffffff,Paint.Align.CENTER);}   // the look just picked
        c.restore();
    }
    private float ease(float a,float to){return Math.abs(to-a)<0.3f?to:a+(to-a)*k;}
    private static Typeface num(){Typeface f=LambdaCluster.FACE_BOLD;return f!=null?f:Typeface.DEFAULT_BOLD;}
    private void needle(Canvas c,float cx,float cy,float deg,Path n,float hub,int color){
        p.setStyle(Paint.Style.FILL);
        c.save();c.translate(cx+2.5f,cy+3);c.rotate(deg);p.setColor(0x55000000);c.drawPath(n,p);c.restore();
        c.save();c.translate(cx,cy);c.rotate(deg);p.setColor(col(color));c.drawPath(n,p);c.restore();
        dot(c,cx,cy,hub,0xffb9b9b9);dot(c,cx,cy,hub*0.45f,0xff3a3a3a);
    }
    /** 디지털 and 아크: the readings in big digits in the dials' middles, the gear between them. */
    private void middle(Canvas c,float kmh,float rpm,String rpmText,String g,float boost,float cool,boolean hot,int gearColor){
        text(c,Float.isNaN(kmh)?"—":fmt("%.0f",kmh),SX,BY+24,66,D_TXT,Paint.Align.CENTER,num());
        text(c,rpmText,TX,BY+18,46,!Float.isNaN(rpm)&&rpm>=prof.redline?RED_ZONE:D_TXT,Paint.Align.CENTER,num());
        text(c,g,SMX,BY+20,46,gearColor,Paint.Align.CENTER,num());
        text(c,fmt("%+.2f",boost),SMX,BOY+8,17,D_TXT,Paint.Align.CENTER,num());
        text(c,fmt("%.0f",cool)+"°",SMX,TMY+8,17,hot?RED_ZONE:D_TXT,Paint.Align.CENTER,num());
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
    private Bitmap buildDigital(){
        Bitmap b=bitmap(800,428);Canvas c=new Canvas(b);
        fill(c,0,0,800,428,0xff000000);
        float redA=-SPAN+2*SPAN*prof.redline/rpmTop(),hotA=-SMALL_SPAN+2*SMALL_SPAN*(HOT-T_MIN)/(T_MAX-T_MIN);
        ring(c,SX,BY,BR,SPAN,SEG_STEP,Float.NaN);ring(c,TX,BY,BR,SPAN,SEG_STEP,redA);ring(c,SMX,BOY,SR,SMALL_SPAN,SMALL_SEG_STEP,Float.NaN);ring(c,SMX,TMY,SR,SMALL_SPAN,SMALL_SEG_STEP,hotA);
        scaleNumbers(c,BR-38,D_MUT);
        bold(c,"km/h",SX,BY+50,14,D_MUT,Paint.Align.CENTER);bold(c,"r/min",TX,BY+46,14,D_MUT,Paint.Align.CENTER);
        gearWindow(c,D_RING);
        smallLabels(c,BOY+26,TMY+26,D_MUT);lamp(c);
        return b;
    }
    /** A thin outer ring and the unlit segments (dark red from red on). */
    private void ring(Canvas c,float cx,float cy,float rad,float span,float step,float red){
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.5f);p.setColor(D_RING);r.set(cx-rad,cy-rad,cx+rad,cy+rad);c.drawArc(r,-90-span-4,2*span+8,false,p);p.setStyle(Paint.Style.FILL);
        float o=segOut(rad),i=segIn(rad),w=rad>100?3.6f:3;
        for(float d=-span;d<=span+0.01f;d+=step)line(c,px(cx,i,d),py(cy,i,d),px(cx,o,d),py(cy,o,d),w,!Float.isNaN(red)&&d>=red-0.01f?D_OFF_RED:D_OFF);
    }
    private void scaleNumbers(Canvas c,float at,int color){
        for(int k=0;k<=KMH_MAX;k+=20){float d=-SPAN+2*SPAN*k/KMH_MAX;text(c,String.valueOf(k),px(SX,at,d),py(BY,at,d)+5,13,color,Paint.Align.CENTER,Typeface.DEFAULT_BOLD);}
        for(int k=0;k<=rpmTop();k+=1000){float d=-SPAN+2*SPAN*k/rpmTop();text(c,String.valueOf(k/1000),px(TX,at,d),py(BY,at,d)+5,15,k>=prof.redline?RED_ZONE:color,Paint.Align.CENTER,Typeface.DEFAULT_BOLD);}
    }
    private void gearWindow(Canvas c,int edge){round(c,SMX-40,BY-26,SMX+40,BY+30,6,edge);round(c,SMX-38,BY-24,SMX+38,BY+28,5,0xff05080b);}
    private void smallLabels(Canvas c,float boostY,float coolY,int color){bold(c,t("부스트"),SMX,boostY,11,color,Paint.Align.CENTER);bold(c,t("냉각수"),SMX,coolY,11,color,Paint.Align.CENTER);}
    private void lamp(Canvas c){dot(c,LAMP_X,LAMP_Y,15,D_RING);dot(c,LAMP_X,LAMP_Y,12,0xff120a08);bold(c,"H",LAMP_X,LAMP_Y+5,13,0xff4a2a22,Paint.Align.CENTER);}   // the overheat lamp, dark until 110 °C

    // ---- 아크 ----
    private static float bandOut(float rad){return rad-8;}
    private static float bandIn(float rad){return rad>100?rad-26:rad-15;}
    private static float bandW(float rad,float step){return (float)(bandOut(rad)*Math.toRadians(step))+0.8f;}
    /** The colour along the band: cyan at the stop, violet halfway, magenta at the top. */
    private static int arcColour(float f){return f<0.5f?mix(A_C1,A_C0,f*2):mix(A_C2,A_C1,(f-0.5f)*2);}
    /** The lit band up to the reading, in short radial strokes wide enough to touch; a soft glow, and a bright tip. */
    private void band(Canvas c,float cx,float cy,float rad,float deg,float span,float step,boolean has){
        if(!has)return;float o=bandOut(rad),i=bandIn(rad),w=bandW(rad,step),top=Math.min(deg,span);
        for(float d=-span;d<=top+0.01f;d+=step){int col=arcColour((d+span)/(2*span));
            line(c,px(cx,i-3,d),py(cy,i-3,d),px(cx,o+3,d),py(cy,o+3,d),w+3,(col&0xffffff)|0x30000000);
            line(c,px(cx,i,d),py(cy,i,d),px(cx,o,d),py(cy,o,d),w,col);}
        float m=(o+i)/2;dot(c,px(cx,m,top),py(cy,m,top),rad>100?5:3.5f,0xffffffff);
    }
    private Bitmap buildArc(){
        Bitmap b=bitmap(800,428);Canvas c=new Canvas(b);
        fill(c,0,0,800,428,0xff000000);
        track(c,SX,BY,BR,SPAN,ARC_STEP);track(c,TX,BY,BR,SPAN,ARC_STEP);track(c,SMX,BOY,SR,SMALL_SPAN,SMALL_ARC_STEP);track(c,SMX,TMY,SR,SMALL_SPAN,SMALL_ARC_STEP);
        scaleNumbers(c,BR-42,D_MUT);
        bold(c,"km/h",SX,BY+50,14,D_MUT,Paint.Align.CENTER);bold(c,"r/min",TX,BY+46,14,D_MUT,Paint.Align.CENTER);
        gearWindow(c,0xff3a1f4a);smallLabels(c,BOY+26,TMY+26,D_MUT);lamp(c);
        return b;
    }
    private void track(Canvas c,float cx,float cy,float rad,float span,float step){
        float o=bandOut(rad),i=bandIn(rad),w=bandW(rad,step);
        for(float d=-span;d<=span+0.01f;d+=step)line(c,px(cx,i,d),py(cy,i,d),px(cx,o,d),py(cy,o,d),w,A_TRACK);
    }

    // ---- 미니멀 ----
    /** A thin amber needle from near the hub, with a small hub. */
    private void thinNeedle(Canvas c,float cx,float cy,float rad,float deg){
        float r0=rad>100?-14:-6,r1=rad-4;
        line(c,px(cx,r0,deg),py(cy,r0,deg),px(cx,r1,deg),py(cy,r1,deg),rad>100?2.2f:1.6f,M_NEEDLE);
        dot(c,cx,cy,rad>100?5:3.5f,M_NEEDLE);dot(c,cx,cy,rad>100?2:1.5f,0xff000000);
    }
    private Bitmap buildMinimal(float bt){
        Bitmap b=bitmap(800,428);Canvas c=new Canvas(b);
        fill(c,0,0,800,428,0xff000000);
        float redA=-SPAN+2*SPAN*prof.redline/rpmTop();
        hair(c,SX,BY,BR,SPAN,Float.NaN);hair(c,TX,BY,BR,SPAN,redA);hair(c,SMX,BOY,SR,SMALL_SPAN,Float.NaN);hair(c,SMX,TMY,SR,SMALL_SPAN,-SMALL_SPAN+2*SMALL_SPAN*(HOT-T_MIN)/(T_MAX-T_MIN));
        for(int k=0;k<=KMH_MAX;k+=10){float d=-SPAN+2*SPAN*k/KMH_MAX;boolean maj=k%20==0;tick(c,SX,BY,BR-2,maj?10:5,d,maj?1.5f:1,maj?M_TICK:M_LINE);
            if(maj&&k%40==0)text(c,String.valueOf(k),px(SX,BR-26,d),py(BY,BR-26,d)+5,14,M_NUM,Paint.Align.CENTER,Typeface.DEFAULT);}
        for(int k=0;k<=rpmTop();k+=500){float d=-SPAN+2*SPAN*k/rpmTop();boolean maj=k%1000==0;tick(c,TX,BY,BR-2,maj?10:5,d,maj?1.5f:1,k>=prof.redline?RED_ZONE:maj?M_TICK:M_LINE);
            if(maj)text(c,String.valueOf(k/1000),px(TX,BR-26,d),py(BY,BR-26,d)+5,15,k>=prof.redline?RED_ZONE:M_NUM,Paint.Align.CENTER,Typeface.DEFAULT);}
        for(float k=-1;k<=bt+0.01f;k+=0.5f){float d=-SMALL_SPAN+2*SMALL_SPAN*(k+1)/(bt+1);tick(c,SMX,BOY,SR-2,Math.abs(k-Math.round(k))<0.01f?7:4,d,1,M_TICK);}
        for(int k=(int)T_MIN;k<=T_MAX;k+=10){float d=-SMALL_SPAN+2*SMALL_SPAN*(k-T_MIN)/(T_MAX-T_MIN);tick(c,SMX,TMY,SR-2,k%30==10?7:4,d,1,k>=HOT?RED_ZONE:M_TICK);}
        text(c,"km/h",SX,BY+92,12,0xff6d747c,Paint.Align.CENTER,Typeface.DEFAULT);text(c,"r/min",TX,BY+92,12,0xff6d747c,Paint.Align.CENTER,Typeface.DEFAULT);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1);p.setColor(M_LINE);r.set(SMX-34,BY-22,SMX+34,BY+28);c.drawRoundRect(r,6,6,p);p.setStyle(Paint.Style.FILL);
        text(c,t("부스트"),SMX,BOY+48,10,0xff6d747c,Paint.Align.CENTER,Typeface.DEFAULT);text(c,t("냉각수"),SMX,TMY+48,10,0xff6d747c,Paint.Align.CENTER,Typeface.DEFAULT);
        lamp(c);
        return b;
    }
    /** A hairline arc over the span, red from red on (when red is a number). */
    private void hair(Canvas c,float cx,float cy,float rad,float span,float red){
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.2f);p.setColor(M_LINE);r.set(cx-rad,cy-rad,cx+rad,cy+rad);c.drawArc(r,-90-span,2*span,false,p);
        if(!Float.isNaN(red)){p.setStrokeWidth(2.5f);p.setColor(RED_ZONE);c.drawArc(r,red-90,span-red,false,p);}
        p.setStyle(Paint.Style.FILL);
    }

    // ---- 레이스 ----
    private void race(Canvas c,float kmh,float rpm,float boost,float cool,float oil,boolean hot,String g,String odoText,float bt){
        boolean valid=!Float.isNaN(rpm);
        if(valid&&rpm>=prof.redline-400&&(rpm<prof.redline||blink(now,160))){   // the bezel warns near the redline, flashes at it
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(9);p.setColor(col(RED_ZONE));c.drawCircle(RX,RY,RR+4,p);p.setStyle(Paint.Style.FILL);}
        // the gear and the speed sit in the dial's open bottom, where the needle never goes
        text(c,g,RX,RY+108,80,R_GEAR,Paint.Align.CENTER,num());
        text(c,Float.isNaN(kmh)?"—":fmt("%.0f",kmh),RX,RY+152,30,0xffffffff,Paint.Align.CENTER,num());
        needle(c,RX,RY,aRace,raceNeedle,16,R_NEEDLE);dot(c,RX,RY,6,R_NEEDLE);
        // side bars: boost left, coolant right
        vbar(c,BOOST_L,Float.isNaN(boost)?Float.NaN:(boost+1)/(bt+1),0xff2fd6ff);
        vbar(c,COOL_L,Float.isNaN(cool)?Float.NaN:(cool-T_MIN)/(T_MAX-T_MIN),hot?danger():0xff3ddc84);
        text(c,fmt("%+.2f",boost),BOOST_L+BAR_W/2,BAR_B+26,16,0xffffffff,Paint.Align.CENTER,num());
        text(c,fmt("%.0f",cool)+"°",COOL_L+BAR_W/2,BAR_B+26,16,hot?danger():0xffffffff,Paint.Align.CENTER,num());
        text(c,"OIL "+fmt("%.0f",oil),COOL_L+BAR_W/2,BAR_B+46,11,0xff9aa3ad,Paint.Align.CENTER,Typeface.DEFAULT_BOLD);
        if(hot){round(c,COOL_L-40,BAR_T-40,COOL_L+BAR_W+4,BAR_T-14,5,danger());bold(c,"H "+t("과열"),COOL_L-18+BAR_W/2,BAR_T-21,13,0xffffffff,Paint.Align.CENTER);}
    }
    private void vbar(Canvas c,float l,float frac,int color){
        if(Float.isNaN(frac))return;float h=(BAR_B-BAR_T-4)*clamp(frac,0,1);if(h>1)fill(c,l+2,BAR_B-2-h,l+BAR_W-2,BAR_B-2,color);
    }
    private Bitmap buildRace(){
        Bitmap b=bitmap(800,428);Canvas c=new Canvas(b);
        // carbon: a fine weave of two dark greys
        fill(c,0,0,800,428,0xff0c0d0f);
        for(int y=0;y<428;y+=4)for(int x=(y/4)%2*4;x<800;x+=8)fill(c,x,y,x+4,y+4,0xff141518);
        // the tacho: bezel, face, red zone, 500 rpm ticks, numbers every 1,000
        dot(c,RX,RY,RR+8,R_BEZEL);dot(c,RX,RY,RR+2,0xff08090a);dot(c,RX,RY,RR,R_FACE);
        float a0=-RSPAN+2*RSPAN*prof.redline/rpmTop();
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(14);p.setColor(RED_ZONE);float rr=RR-9;r.set(RX-rr,RY-rr,RX+rr,RY+rr);c.drawArc(r,a0-90,RSPAN-a0,false,p);p.setStyle(Paint.Style.FILL);
        for(int k=0;k<=rpmTop();k+=250){float d=-RSPAN+2*RSPAN*k/rpmTop();boolean maj=k%1000==0,half=k%500==0;
            tick(c,RX,RY,RR-3,maj?20:half?12:7,d,maj?4:half?2:1.2f,k>=prof.redline?0xffffffff:0xffe8e8e8);
            if(maj)text(c,String.valueOf(k/1000),px(RX,RR-48,d),py(RY,RR-48,d)+11,30,k>=prof.redline?RED_ZONE:0xffffffff,Paint.Align.CENTER,num());}
        text(c,"x1000 r/min",RX,RY-64,12,0xff9aa3ad,Paint.Align.CENTER,Typeface.DEFAULT_BOLD);
        text(c,"km/h",RX,RY+168,11,0xff9aa3ad,Paint.Align.CENTER,Typeface.DEFAULT_BOLD);
        // the side bars' frames
        for(float l:new float[]{BOOST_L,COOL_L}){round(c,l,BAR_T,l+BAR_W,BAR_B,4,0xff2a2d31);round(c,l+2,BAR_T+2,l+BAR_W-2,BAR_B-2,3,0xff060708);}
        bold(c,t("부스트"),BOOST_L+BAR_W/2,BAR_T-10,12,0xff9aa3ad,Paint.Align.CENTER);bold(c,t("냉각수"),COOL_L+BAR_W/2,BAR_T-10,12,0xff9aa3ad,Paint.Align.CENTER);
        return b;
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
