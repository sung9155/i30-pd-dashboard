package com.veloster.vehicle;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import java.util.Locale;

/**
 * WRC screen: a rally co-driver's view. Across the top fifteen shift lights (green, yellow, red over the last 1,500 rpm
 * before the shift target, all blue at it). Left, the pace-note card: the bend the car is in as a call (PaceNote: "3 L",
 * severity 1 tight to 6 slight, from the wheel angle, else lateral G and speed) with its bend drawn above, a ! at high
 * lateral G, and the last three corners. Centre the gear and rpm, right the speed and the stage timer (running from
 * moving off, stopped 10 s after the car stops, the last stage kept). Along the bottom the pedals, wheel angle and G.
 *
 * Built for the head unit: the panels, card and lamp sockets are drawn once into a bitmap; each frame adds text, the
 * lit lamps and bars (plain rects), the bend (arc angles from 12 fixed shapes) and its kept arrowhead.
 */
final class WrcCluster extends ClusterKit {
    int shiftTarget=6000;   // set by PitRenderer from 설정 → RPM
    private static final int BG=0xff0e1012,PANEL=0xff181b1f,LINE=0xff2c3137,FG=0xfff4f4f0,MUT=0xff8d949b,YEL=0xffffd200,GRN=0xff3ddc84,
        RED=0xffff3b30,BLUE=0xff4aa8ff,DIM=0xff22262b,PAPER=0xfff1ead6,INK=0xff1c1a17,RULE=0xffd3c8aa,INK_MUT=0xff7a7262;
    private static final int LEDS=15;private static final float LED_L=16,LED_W=46.13f,LED_GAP=5,LED_T=10,LED_B=42;
    private static final long STOP_MS=10000,SETTLE_MS=250;
    private Bitmap face;private String faceKey="";
    // stage timer (tick: counts while the screen is away too)
    private long stageStart=-1,lastMove,stageMs=-1;
    // pace note: the call settles for SETTLE_MS before it shows; a corner's tightest call goes to the list when it ends
    private int cand,shown,tight;private long candAt;private final int[] past=new int[3];

    private static Typeface num(){Typeface f=LambdaCluster.FACE_BOLD;return f!=null?f:Typeface.DEFAULT_BOLD;}

    @Override protected void tick(){
        float kmh=v(7);if(Float.isNaN(kmh))return;
        if(kmh>=3){if(stageStart<0)stageStart=now;lastMove=now;}
        else if(stageStart>=0&&now-lastMove>STOP_MS){stageMs=lastMove-stageStart;stageStart=-1;}
    }

    @Override protected void draw(Canvas c){
        c.save();c.translate(0,52);
        String key=com.veloster.i18n.UiText.language();
        if(face==null||!key.equals(faceKey)){face=buildFace();faceKey=key;}
        c.drawBitmap(face,0,0,null);
        float rpm=v(0),kmh=v(7),lat=v(11),lon=v(12),steer=v(Telemetry.STEERING),pedal=v(9),brake=v(10);
        String g=gear();
        boolean shift=!Float.isNaN(rpm)&&rpm>=shiftTarget,on=blink(now,160);
        // shift lights
        float lit=Float.isNaN(rpm)?0:clamp((rpm-(shiftTarget-1500))/1500f,0,1)*LEDS;
        for(int i=0;i<LEDS;i++){if(!shift&&lit<=i)continue;float l=LED_L+i*(LED_W+LED_GAP);
            round(c,l+3,LED_T+3,l+LED_W-3,LED_B-3,4,shift?(on?BLUE:DIM):i<5?GRN:i<10?YEL:RED);}
        paceNote(c,steer,lat,kmh);
        // centre: gear, rpm
        boolean gate=manualGate(g);String gs=gate?g.substring(1):g;
        text(c,gs,400,232,gs.length()>1?110:170,shift&&on?BLUE:FG,Paint.Align.CENTER,num());
        if(gate)bold(c,"M",462,110,26,YEL,Paint.Align.CENTER);
        text(c,Float.isNaN(rpm)?"—":String.format(Locale.US,"%,d",Math.round(rpm/10)*10),400,300,34,!Float.isNaN(rpm)&&rpm>=prof.redline?RED:FG,Paint.Align.CENTER,num());
        // right: speed, stage
        text(c,fmt("%.0f",kmh),770,190,120,FG,Paint.Align.RIGHT,num());
        boolean running=stageStart>=0;long ms=running?now-stageStart:stageMs;
        bold(c,t(running?"구간 진행 중":stageMs>=0?"지난 구간":"출발하면 시작"),516,240,15,running?YEL:MUT,Paint.Align.LEFT);
        text(c,ms<0?"--:--.-":String.format(Locale.US,"%02d:%02d.%d",ms/60000,ms/1000%60,ms/100%10),770,302,52,running?YEL:ms<0?MUT:FG,Paint.Align.RIGHT,num());
        // bottom strip: pedals, wheel, G
        bar(c,92,361,pedal/100,GRN);bar(c,92,393,Float.isNaN(brake)?Float.NaN:brake/80,RED);
        String wheel=Float.isNaN(steer)?"—":Math.abs(Math.round(steer))+"° "+(steer>0.5f?"R":steer<-0.5f?"L":"");
        text(c,wheel,600,396,32,FG,Paint.Align.RIGHT,num());
        float gsum=Float.isNaN(lat)||Float.isNaN(lon)?Float.NaN:(float)Math.hypot(lat,lon);
        text(c,fmt("%.2f",gsum),770,396,32,!Float.isNaN(gsum)&&gsum>=PaceNote.CAUTION_G?YEL:FG,Paint.Align.RIGHT,num());
        c.restore();
    }

    /** The card: the settled call, the bend above it, the last three corners below. */
    private void paceNote(Canvas c,float steer,float lat,float kmh){
        int raw=PaceNote.of(steer,lat,kmh);
        if(raw!=cand){cand=raw;candAt=now;}
        if(now-candAt>=SETTLE_MS&&cand!=shown){
            boolean flip=shown!=0&&(cand==0||Integer.signum(cand)!=Integer.signum(shown));
            if(flip){past[2]=past[1];past[1]=past[0];past[0]=tight;}
            if(cand!=0&&(flip||shown==0||Math.abs(cand)<Math.abs(tight)))tight=cand;   // a new corner, or this one tightens
            shown=cand;
        }
        float cx=158;
        Tbt.State nav=Tbt.current(now);
        if(nav.maneuver){   // the stock navi is guiding: its next point takes the card (the corner list stays below)
            float deg=Tbt.arrow(nav);int note=Math.abs(deg)<20?0:Math.abs(deg)>160?(deg<0?-1:1):(deg<0?-3:3);
            if(note==0)straight(c,cx);else bend(c,cx,note);
            String d=Tbt.distance(nav.metres);text(c,d.length()>0?d:"—",cx,216,50,INK,Paint.Align.CENTER,num());
            bold(c,t(nav.label),cx,248,20,INK_MUT,Paint.Align.CENTER);
        }
        else if(Float.isNaN(steer)&&(Float.isNaN(lat)||Float.isNaN(kmh)))text(c,"—",cx,232,96,INK_MUT,Paint.Align.CENTER,num());
        else if(shown==0){straight(c,cx);bold(c,t("직진"),cx,232,48,INK,Paint.Align.CENTER);}
        else{bend(c,cx,shown);text(c,PaceNote.text(shown),cx,250,84,INK,Paint.Align.CENTER,num());
            if(!Float.isNaN(lat)&&Math.abs(lat)>=PaceNote.CAUTION_G){dot(c,262,96,20,danger());bold(c,"!",262,106,30,0xffffffff,Paint.Align.CENTER);}}
        StringBuilder b=new StringBuilder();for(int n:past)if(n!=0){if(b.length()>0)b.append("  ·  ");b.append(PaceNote.text(n));}
        bold(c,b.length()==0?"—":b.toString(),cx,304,24,b.length()==0?INK_MUT:INK,Paint.Align.CENTER);
    }
    /** The bend: a straight lead-in, then a quarter turn whose radius grows with the call (1 tight .. 6 wide), and its arrowhead. */
    private void bend(Canvas c,float cx,int note){
        int sev=Math.abs(note);boolean right=note>0;float rr=12+sev*7,bot=166,y=136,x0=right?cx-rr/2:cx+rr/2;
        line(c,x0,bot,x0,y,9,INK);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(9);p.setColor(col(INK));
        r.set(right?x0:x0-2*rr,y-rr,right?x0+2*rr:x0,y+rr);c.drawArc(r,right?180:0,right?90:-90,false,p);p.setStyle(Paint.Style.FILL);
        float ex=right?x0+rr:x0-rr,ey=y-rr,d=right?1:-1;
        path.reset();path.moveTo(ex+d*16,ey);path.lineTo(ex-d*4,ey-13);path.lineTo(ex-d*4,ey+13);path.close();poly(c,INK);
    }
    private void straight(Canvas c,float cx){
        line(c,cx,166,cx,106,9,INK);
        path.reset();path.moveTo(cx,86);path.lineTo(cx-13,108);path.lineTo(cx+13,108);path.close();poly(c,INK);
    }
    private void bar(Canvas c,float l,float y,float frac,int color){
        float w=Float.isNaN(frac)?0:276*clamp(frac,0,1);if(w>1)fill(c,l,y-14,l+w,y,color);
    }

    // ---- the still parts, drawn once ----
    @Override void trim(){face=recycle(face);faceKey="";}
    private Bitmap buildFace(){
        Bitmap b=bitmap(800,428);Canvas c=new Canvas(b);
        fill(c,0,0,800,428,BG);
        for(int i=0;i<LEDS;i++){float l=LED_L+i*(LED_W+LED_GAP);round(c,l,LED_T,l+LED_W,LED_B,6,0xff060708);round(c,l+3,LED_T+3,l+LED_W-3,LED_B-3,4,DIM);}
        // pace-note card: paper, rules, a ring-bound top
        round(c,16,56,300,330,8,PAPER);
        for(float y=262;y<=318;y+=28)line(c,30,y+10,286,y+10,1,RULE);
        for(int i=0;i<7;i++)ring(c,44+i*34,62,5,2,0xff6e6a60);
        bold(c,t("페이스노트"),30,88,15,INK_MUT,Paint.Align.LEFT);
        bold(c,t("지난 코너"),30,270,13,INK_MUT,Paint.Align.LEFT);
        // gear, speed panels
        round(c,316,56,484,330,8,PANEL);outline(c,316,56,484,330,8,1,LINE);
        round(c,500,56,784,330,8,PANEL);outline(c,500,56,784,330,8,1,LINE);
        bold(c,t("기어"),330,80,14,MUT,Paint.Align.LEFT);bold(c,"rpm",400,322,13,MUT,Paint.Align.CENTER);
        bold(c,"km/h",770,214,16,MUT,Paint.Align.RIGHT);fill(c,516,222,770,224,YEL);
        bold(c,t("구간 시간"),516,80,14,MUT,Paint.Align.LEFT);
        // bottom strip
        round(c,16,342,784,416,8,PANEL);outline(c,16,342,784,416,8,1,LINE);
        bold(c,t("가속"),30,360,14,MUT,Paint.Align.LEFT);bold(c,t("브레이크"),30,392,14,MUT,Paint.Align.LEFT);
        fill(c,92,347,368,361,DIM);fill(c,92,379,368,393,DIM);
        bold(c,t("조향"),400,370,14,MUT,Paint.Align.LEFT);bold(c,"G",640,370,14,MUT,Paint.Align.LEFT);
        // the WRC stripe
        fill(c,0,422,800,425,YEL);fill(c,0,425,800,428,0xff1d4fa0);
        return b;
    }
}
