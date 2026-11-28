package com.veloster.vehicle;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import java.util.Locale;

/**
 * WRC screen: a rally co-driver's pace-note book. Across the top fifteen shift lights (green, yellow, red over the last
 * 1,500 rpm before the shift target, all blue at it). The book takes most of the screen: the next call big (a yellow
 * distance tab, the bend drawn, "우 3" and its modifiers 길게 / 조임 / 풀림, a 주의 tag), the two after it in the ruled
 * lines below. Calls come ahead of the car: the corners recorded the last time this road was driven (PaceNote finds
 * them, Commute keeps them with the drive and places the car along it), and the stock navi's next guidance point when
 * it guides, merged by distance. On a new road the book records: it shows the corner the car is in once it counts as
 * one, else "기록 중", and the last corners found. Right, the gear, speed and stage timer (running from moving off,
 * stopped 10 s after the car stops, the last stage kept). Along the bottom the pedals, wheel angle and G.
 *
 * Built for the head unit: the panels, tabs and rules are drawn once into a bitmap; each frame adds text, the lit lamps
 * and bars (plain rects) and the bend as kept paths (a few shapes that repeat).
 */
final class WrcCluster extends ClusterKit {
    int shiftTarget=6000;   // set by PitRenderer from 설정 → RPM
    private static final int BG=0xff0a0c0f,PANEL=0xff15181d,PANEL2=0xff1c2026,LINE=0xff2c3138,FG=0xfff6f6f2,MUT=0xff8a929b,YEL=0xffffd200,
        INK=0xff111111,GRN=0xff3ddc84,RED=0xffff3b30,BLUE=0xff4aa8ff,DIM=0xff22262b,NAVY=0xff1d4fa0;
    private static final int LEDS=15;private static final float LED_L=16,LED_W=46.13f,LED_GAP=5,LED_T=6,LED_B=32;
    private static final long STOP_MS=10000;
    /** The bend drawn for severity 1..6: degrees turned. */
    private static final float[] TURN={150,120,90,70,50,30};
    private Bitmap face;private String faceKey="";
    // stage timer (tick: counts while the screen is away too)
    private long stageStart=-1,lastMove,stageMs=-1;
    // pace notes: the detector runs every frame on every page, so the book records whatever screen shows
    private final PaceNote.Detector det=new PaceNote.Detector();
    private final PaceNote.Corner[] book=new PaceNote.Corner[3];private final float[] bookD=new float[3];
    // the calls shown, nearest first: a corner from the book, or (corner null) the navi's guidance point
    private final PaceNote.Corner[] cc=new PaceNote.Corner[3];private final float[] cd=new float[3];private int calls;

    private static Typeface num(){Typeface f=LambdaCluster.FACE_BOLD;return f!=null?f:Typeface.DEFAULT_BOLD;}
    /** The numeral face, or the system bold for text with Hangul (the numeral face has none: it would fall back thin). */
    private static Typeface ff(String s){for(int i=0;i<s.length();i++)if(s.charAt(i)>=0x1100)return Typeface.DEFAULT_BOLD;return num();}

    @Override protected void tick(){
        float kmh=v(7);
        PaceNote.Corner done=det.feed(now,kmh,v(Telemetry.YAW),v(11),v(Telemetry.STEERING),Commute.along(now));
        if(done!=null)Commute.corner(done);
        if(Float.isNaN(kmh))return;
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
        notes(c,kmh,lat,steer);
        // right: gear (rpm in its corner), speed, stage
        boolean gate=manualGate(g);String gs=gate?g.substring(1):g;
        text(c,gs,684,176,gs.length()>1?84:124,shift&&on?BLUE:FG,Paint.Align.CENTER,num());
        if(gate)bold(c,"M",756,104,24,YEL,Paint.Align.CENTER);
        text(c,Float.isNaN(rpm)?"—":Fmt.f("%,d",Math.round(rpm/10)*10),772,64,20,!Float.isNaN(rpm)&&rpm>=prof.redline?RED:FG,Paint.Align.RIGHT,num());
        text(c,fmt("%.0f",kmh),728,258,54,FG,Paint.Align.RIGHT,num());
        boolean running=stageStart>=0;long ms=running?now-stageStart:stageMs;
        bold(c,t(running?"구간 진행 중":stageMs>=0?"지난 구간":"출발하면 시작"),598,298,14,running?YEL:MUT,Paint.Align.LEFT);
        text(c,ms<0?"--:--.-":Fmt.f("%02d:%02d.%d",ms/60000,ms/1000%60,ms/100%10),772,334,36,running?YEL:ms<0?MUT:FG,Paint.Align.RIGHT,num());
        // bottom strip: pedals, wheel, G
        bar(c,98,376,pedal/100,GRN);bar(c,98,404,Float.isNaN(brake)?Float.NaN:brake/80,RED);
        String wheel=Float.isNaN(steer)?"—":Math.abs(Math.round(steer))+"° "+(steer>0.5f?"R":steer<-0.5f?"L":"");
        text(c,wheel,610,402,28,FG,Paint.Align.RIGHT,num());
        float gsum=Float.isNaN(lat)||Float.isNaN(lon)?Float.NaN:(float)Math.hypot(lat,lon);
        text(c,fmt("%.2f",gsum),770,402,28,!Float.isNaN(gsum)&&gsum>=PaceNote.CAUTION_G?YEL:FG,Paint.Align.RIGHT,num());
        c.restore();
    }

    /** The book: the next call big, the two after it on the ruled lines; on a new road the corner now and the last ones. */
    private void notes(Canvas c,float kmh,float lat,float steer){
        int n=Commute.ahead(now,book,bookD);
        Tbt.State nav=Tbt.current(now);boolean navi=nav.maneuver&&!Float.isNaN(nav.metres)&&nav.metres>0;
        calls=0;
        for(int i=0;i<Math.max(0,n);i++){if(navi&&nav.metres<=bookD[i]&&calls<3){cc[calls]=null;cd[calls++]=nav.metres;navi=false;}
            if(calls<3){cc[calls]=book[i];cd[calls++]=bookD[i];}}
        if(navi&&calls<3){cc[calls]=null;cd[calls++]=nav.metres;}
        boolean noData=Float.isNaN(kmh)&&Float.isNaN(lat)&&Float.isNaN(steer);
        String state=n>=0?t("이 길의 노트"):!Float.isNaN(Commute.delta(now))?t("노트 만드는 중"):t("이 길은 처음");
        boolean gps=!Float.isNaN(Commute.along(now));
        if(n<0)state=gps?state+" · "+Fmt.f(t("코너 %d개"),Commute.recorded()):"";   // without GPS the road is not known
        if(!noData)bold(c,state,544,62,15,MUT,Paint.Align.RIGHT);
        int live=det.live();
        if(calls>0){
            hero(c,0,nav);
            for(int i=1;i<3;i++)if(i<calls)row(c,i,cd[i],cc[i],nav);
        }else if(noData){
            tab(c,"—","",MUT);fit(c,t("차량 정보를 기다리는 중이에요"),40,226,40,500,MUT,Paint.Align.LEFT);
        }else{
            if(live!=0){tab(c,t("지금"),"",INK);glyph(c,TURN[Math.abs(live)-1]*Math.signum(live),FG);text(c,PaceNote.call(live),172,226,108,FG,Paint.Align.LEFT,ff(PaceNote.call(live)));
                if(det.liveCaution())caution(c);}
            else if(n==0){tab(c,"—","",INK);fit(c,t("남은 코너 없음"),40,226,64,500,FG,Paint.Align.LEFT);}
            else{tab(c,"REC","",INK);dot(c,46,104,7,danger());
                fit(c,t(gps?"기록 중":"GPS 기다리는 중"),40,216,gps?80:56,500,FG,Paint.Align.LEFT);
                if(gps)fit(c,t("다음에 이 길을 달리면 코너를 미리 불러요"),40,260,24,500,YEL,Paint.Align.LEFT);}
            for(int i=0;i<2;i++){PaceNote.Corner k=det.recent(i);if(k!=null)row(c,i+1,Float.NaN,k,nav);}
        }
    }
    /** The next call: distance tab, bend, call text, modifiers, 주의. */
    private void hero(Canvas c,int i,Tbt.State nav){
        PaceNote.Corner k=cc[i];String[] d=dist(cd[i]);tab(c,d[0],d[1],INK);
        if(k==null){   // the navi's next guidance point
            float deg=((Tbt.arrow(nav)+180)%360+360)%360-180;boolean u=nav.type.contains("U TURN")||nav.label.equals("유턴");   // deg: clockwise from up, as -180..180 (270 = left)
            glyph(c,u?-180:Math.abs(deg)<20?0:clamp(deg,-150,150),FG);
            outline(c,212,88,274,120,4,2,YEL);bold(c,t("내비"),243,110,16,YEL,Paint.Align.CENTER);
            fit(c,t(nav.label),172,226,96,364,FG,Paint.Align.LEFT);return;}
        glyph(c,TURN[Math.abs(k.note)-1]*Math.signum(k.note),FG);
        text(c,PaceNote.call(k.note),172,226,108,FG,Paint.Align.LEFT,ff(PaceNote.call(k.note)));
        String x=PaceNote.extras(k,false);if(x.length()>0)fit(c,x,174,266,32,364,YEL,Paint.Align.LEFT);
        if(k.caution)caution(c);
    }
    /** A ruled line: distance (or 지난 for a corner behind), the call and its modifiers. */
    private void row(Canvas c,int line,float m,PaceNote.Corner k,Tbt.State nav){
        float y=line==1?303:336;
        if(Float.isNaN(m))bold(c,t("지난"),108,y,18,MUT,Paint.Align.RIGHT);
        else{String[] d=dist(m);text(c,d[0]+(d[1].equals("km")?" km":""),108,y,26,FG,Paint.Align.RIGHT,num());}
        String call=k==null?t(nav.label):PaceNote.call(k.note),x=k==null?t("내비"):PaceNote.extras(k,true);
        text(c,call,124,y,30,k==null?YEL:FG,Paint.Align.LEFT,ff(call));
        if(x.length()>0)bold(c,x,124+width(call,30,ff(call))+14,y,20,k!=null&&k.caution?danger():YEL,Paint.Align.LEFT);
    }
    /** {"150", "m"} / {"1.2", "km"}: metres to the call, to 10 m. */
    private static String[] dist(float m){
        return m<995?new String[]{String.valueOf(Math.round(m/10)*10),"m"}:new String[]{Fmt.f("%.1f",m/1000),"km"};
    }
    /** The yellow tab's text (the tab is on the bitmap): the distance and its unit, centred. */
    private void tab(Canvas c,String big,String unit,int color){
        if(unit.length()==0){fit(c,big,118,124,40,150,color,Paint.Align.CENTER);return;}
        float w=width(big,44,num()),u=width(unit,20,Typeface.DEFAULT_BOLD),l=118-(w+4+u)/2;
        text(c,big,l,124,44,color,Paint.Align.LEFT,num());bold(c,unit,l+w+4,124,20,color,Paint.Align.LEFT);
    }
    private void caution(Canvas c){round(c,452,84,544,122,6,danger());bold(c,t("주의"),498,111,24,0xffffffff,Paint.Align.CENTER);}
    /** Text at x, baseline y, at up to size px, smaller until it fits in w. */
    private void fit(Canvas c,String s,float x,float y,float size,float w,int color,Paint.Align align){
        Typeface f=ff(s);float z=size;while(z>14&&width(s,z,f)>w)z-=2;
        text(c,s,x,y,z,color,align,f);
    }
    /** The bend, in the box left of the call: a lead-in from below, deg turned (+ right; 0 straight, -180 a U-turn over
     *  the left) on a fixed radius, and the arrowhead. Snapped to 10 degrees so the kept shapes repeat. */
    private void glyph(Canvas c,float deg,int color){
        float a=Math.round(Math.min(180,Math.abs(deg))/10f)*10;float s=deg>0?1:-1,R=a>=170?24:30;
        float x0=a==0?88:s>0?50:126,y0=a==0?166:206,bot=258;
        path.reset();path.moveTo(x0,bot);path.lineTo(x0,y0);
        float ex=x0,ey=y0,hx=0,hy=-1;
        if(a>0){float cx=x0+s*R;r.set(cx-R,y0-R,cx+R,y0+R);path.arcTo(r,s>0?180:0,s*a);
            double th=Math.toRadians(a);ex=cx-s*R*(float)Math.cos(th);ey=y0-R*(float)Math.sin(th);hx=s*(float)Math.sin(th);hy=-(float)Math.cos(th);
            float l=a>=170?8:16;ex+=hx*l;ey+=hy*l;path.lineTo(ex,ey);}
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(13);p.setColor(col(color));c.drawPath(path.kept(),p);p.setStyle(Paint.Style.FILL);
        path.reset();path.moveTo(ex+hx*24,ey+hy*24);path.lineTo(ex-hy*17,ey+hx*17);path.lineTo(ex+hy*17,ey-hx*17);path.close();poly(c,color);
    }
    private void bar(Canvas c,float l,float y,float frac,int color){
        float w=Float.isNaN(frac)?0:262*clamp(frac,0,1);if(w>1)fill(c,l,y-12,l+w,y,color);
    }

    // ---- the still parts, drawn once ----
    @Override void trim(){super.trim();face=recycle(face);faceKey="";}
    /** A panel with its left edge slanted in by k at the top (the TV graphics' cut). */
    private void slab(Canvas c,float l,float t,float rr,float b,float k,int color){
        path.reset();path.moveTo(l+k,t);path.lineTo(rr,t);path.lineTo(rr,b);path.lineTo(l,b);path.close();poly(c,color);
    }
    private Bitmap buildFace(){
        Bitmap b=bitmap(800,428);Canvas c=new Canvas(b);
        fill(c,0,0,800,428,BG);
        for(int i=0;i<LEDS;i++){float l=LED_L+i*(LED_W+LED_GAP);round(c,l,LED_T,l+LED_W,LED_B,6,0xff060708);round(c,l+3,LED_T+3,l+LED_W-3,LED_B-3,4,DIM);}
        // the book: a dark page cut on the right, a yellow spine, the distance tab, two ruled lines
        path.reset();path.moveTo(16,40);path.lineTo(572,40);path.lineTo(554,344);path.lineTo(16,344);path.close();poly(c,PANEL);
        fill(c,16,40,24,344,YEL);
        bold(c,t("페이스노트"),36,62,15,YEL,Paint.Align.LEFT);
        line(c,36,72,566,72,1,LINE);
        path.reset();path.moveTo(36,82);path.lineTo(204,82);path.lineTo(192,134);path.lineTo(36,134);path.close();poly(c,YEL);
        line(c,36,276,558,276,2,LINE);line(c,36,310,556,310,1,LINE);
        // gear, speed, stage
        slab(c,574,40,784,196,16,PANEL);bold(c,t("기어"),598,64,14,MUT,Paint.Align.LEFT);
        slab(c,574,204,784,270,8,PANEL);bold(c,"km/h",772,258,14,MUT,Paint.Align.RIGHT);
        slab(c,574,278,784,344,8,PANEL2);fill(c,582,278,590,344,YEL);
        // bottom strip
        round(c,16,352,784,414,8,PANEL);
        bold(c,t("가속"),30,376,14,MUT,Paint.Align.LEFT);bold(c,t("브레이크"),30,404,14,MUT,Paint.Align.LEFT);
        fill(c,98,364,360,376,DIM);fill(c,98,392,360,404,DIM);
        bold(c,t("조향"),400,376,14,MUT,Paint.Align.LEFT);bold(c,"G",640,376,14,MUT,Paint.Align.LEFT);
        // the WRC stripe
        fill(c,0,420,800,424,YEL);fill(c,0,424,800,428,NAVY);
        return b;
    }
}
