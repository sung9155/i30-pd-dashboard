package com.veloster.vehicle;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import java.util.Locale;

/**
 * 수동 screen, after the layout of the Reventón's digital mode (not its artwork): thin glowing lines on black, two
 * mirrored Λ gauges. Left Λ = rpm, right Λ = speed; each fills its legs upward together, and its ring (the current gear,
 * the speed) is joined to that level on both legs. Under the left apex hangs the gear column (6MT: 6-1 N R; 7DCT: 7-1
 * N R P); the predicted gears are drawn on it the same way as the current one, joined to their rpm on the legs instead
 * of a number: each gear down that the engine can take in its colour (6MT one to three down, DCT the next one), the gear
 * up ringed cyan at where it would land (RevMatch: the 6MT's ratio table, else ratios learned while driving). On a 6MT
 * the gear the blip is matched to turns green. Under the right apex: the speed ring and lamps
 * (6MT: clutch, brake, throttle, heel-and-toe; DCT: manual gate, brake, throttle, clutch heat). Centre: a curved-grid
 * G meter, four small arcs, and the last shift's grade (6MT) or the clutch temperatures (DCT).
 */
final class LambdaCluster extends ClusterKit {
    private static final int CY_=0xff5fe3e0,WH=0xfff2f6f8,DIM=0xff17302f,OR=0xffff8a3a,LINE_=0xff36575a,GREY=0xff7f9a9c,
        GREEN=0xff3ddc84,BLUE=0xff4aa8ff,RED=0xffff4059,AMBER=0xffffb84d,YELLOW=0xffffe04a,INK=0xff000000;
    /** One colour per gear (index = gear), from the owner's sketch: 5 teal, 4 yellow, 3 red, 2 purple. */
    private static final int[] GEAR_INK={0,0xffff5fa8,0xff9b5cff,0xffff6a4d,0xffffe04a,0xff2fe0c4,0xff5ab4ff,0xffc0c8ff};
    private static final float TOP=112,BOT=446,HALF=150,APEX=24,BAND=11,SPEED_MAX=260;
    private static final float LCX=172,RCX=628,COL_Y0=TOP+32,COL_Y1=403;   // the two Λ centres, mirrored about x = 400
    int shiftTarget=6000;   // set by PitRenderer from 설정 → RPM
    private final int[] tg=new int[3];private final float[] tr=new float[3];

    private float legX(float cx,boolean left,float f){float b=left?cx-HALF:cx+HALF,t=left?cx-APEX:cx+APEX;return b+(t-b)*f;}
    private float legY(float f){return BOT+(TOP-BOT)*f;}
    private GearRatios learned(){return st==null?null:st.ratios;}

    @Override protected void draw(Canvas c){
        fill(c,0,52,800,480,0xff000000);
        float rpm=v(0),kmh=v(7),gear=v(8);boolean mt=prof.trans==VehicleProfile.Trans.MT;int matched=RevMatch.matched(s,prof);
        boolean shift=!Float.isNaN(rpm)&&rpm>=shiftTarget;
        String shown=Telemetry.gearText(gear,v(Telemetry.GEAR_SELECTOR),prof);
        int rc=matched>0?GREEN:shift?(blink(now,120)?BLUE:DIM):CY_;
        lambda(c,LCX,Float.isNaN(rpm)?0:rpm/prof.maxRpm,prof.redline/prof.maxRpm,rc,matched>0||shift);
        for(int k=1;k*1000<=prof.maxRpm;k++)scale(c,LCX,k*1000/prof.maxRpm,String.valueOf(k),k*1000>=prof.redline?RED:WH);
        gears(c,rpm,kmh,gear,shown,mt,matched);
        bold(c,fmt("%.0f",rpm),LCX,BOT+4,20,matched>0?GREEN:WH,Paint.Align.CENTER);mono(c,"x1000 rpm",LCX,BOT+22,11,GREY,Paint.Align.CENTER);
        lambda(c,RCX,Float.isNaN(kmh)?0:kmh/SPEED_MAX,2,GREEN,false);
        for(int k=20;k<=SPEED_MAX;k+=40)scale(c,RCX,k/SPEED_MAX,String.valueOf(k),WH);
        lamps(c,kmh,shown,mt);
        bold(c,"MAX "+fmt("%.0f",st==null?Float.NaN:st.maxSpeed),RCX,BOT+4,18,WH,Paint.Align.CENTER);mono(c,"km/h",RCX,BOT+22,11,GREY,Paint.Align.CENTER);
        centre(c,mt);
    }
    /** Both legs of a Λ and their inner bands filled to fraction f; red past fraction red (unless over). */
    private void lambda(Canvas c,float cx,float f,float red,int col,boolean over){
        line(c,legX(cx,true,0),BOT,legX(cx,true,1),TOP,2,LINE_);line(c,legX(cx,false,0),BOT,legX(cx,false,1),TOP,2,LINE_);
        f=clamp(f,0,1);int seg=40;
        for(int i=0;i<seg;i++){float a=i/(float)seg,b=(i+1)/(float)seg,e=b;
            int k=a<f?(!over&&a>=red?RED:col):DIM;if(a<f)e=Math.min(b,f);
            band(c,cx,true,a,e,k);band(c,cx,false,a,e,k);if(e<b){band(c,cx,true,e,b,DIM);band(c,cx,false,e,b,DIM);}}
    }
    private void band(Canvas c,float cx,boolean left,float a,float b,int col){
        float sg=left?1:-1,xa=legX(cx,left,a)+3*sg,xb=legX(cx,left,b)+3*sg;
        path.reset();path.moveTo(xa,legY(a));path.lineTo(xb,legY(b));path.lineTo(xb+BAND*sg,legY(b));path.lineTo(xa+BAND*sg,legY(a));path.close();poly(c,col);
    }
    /** Lines from both legs' inner edge at fraction f to the two sides of a ring at (cx, y). */
    private void join(Canvas c,float cx,float f,float y,float half,float w,int col){
        float ly=legY(clamp(f,0,1));
        line(c,legX(cx,true,clamp(f,0,1))+3+BAND,ly,cx-half,y,w,col);line(c,legX(cx,false,clamp(f,0,1))-3-BAND,ly,cx+half,y,w,col);
    }
    /** A tick and numeral outside both legs at fraction f. */
    private void scale(Canvas c,float cx,float f,String label,int col){
        float y=legY(f),xl=legX(cx,true,f),xr=legX(cx,false,f);
        line(c,xl-10,y,xl-2,y,2,col);line(c,xr+2,y,xr+10,y,2,col);
        mono(c,label,xl-14,y+5,14,col,Paint.Align.RIGHT);mono(c,label,xr+14,y+5,14,col,Paint.Align.LEFT);
    }
    /** The gear column under the rpm apex, with the downshift targets drawn onto it. */
    private void gears(Canvas c,float rpm,float kmh,float gear,String shown,boolean mt,int matched){
        String[] gs=mt?new String[]{"6","5","4","3","2","1","N","R"}:new String[]{"7","6","5","4","3","2","1","N","R","P"};
        String cur=shown.startsWith("M")?shown.substring(1):shown;   // the DCT manual gate: M4 rings 4
        float dy=(COL_Y1-COL_Y0)/(gs.length-1),rad=Math.min(13,dy/2-2);
        int n=RevMatch.targets(s,prof,learned(),tg,tr),alpha=RevMatch.active(s)?0xff:0x80;
        int g=Float.isNaN(gear)?0:(int)gear;   // the gear up and where it would land
        float up=g>=1&&g<prof.topGear&&!Float.isNaN(kmh)&&kmh>=10?RevMatch.rpmIn(prof,learned(),kmh,g+1):Float.NaN;
        for(int i=0;i<gs.length;i++){float y=COL_Y0+i*dy;boolean on=gs[i].equals(cur);
            int t=-1;for(int j=0;j<n;j++)if(String.valueOf(tg[j]).equals(gs[i]))t=j;
            boolean isUp=!Float.isNaN(up)&&gs[i].equals(String.valueOf(g+1))&&!gs[i].equals(cur);
            if(t>=0){boolean ok=tg[t]==matched;int col=((ok?GREEN:GEAR_INK[tg[t]])&0xffffff)|(alpha<<24);
                join(c,LCX,tr[t]/prof.maxRpm,y,rad+1,2,col);   // a gear down: joined to its rpm, like the current gear
                dot(c,LCX,y,rad,col);mono(c,gs[i],LCX,y+6,rad>12?17:15,INK,Paint.Align.CENTER);}
            else if(isUp){join(c,LCX,up/prof.maxRpm,y,rad+1,2,CY_);   // the gear up: where it would land
                dot(c,LCX,y,rad,INK);ring(c,LCX,y,rad,2.5f,CY_);mono(c,gs[i],LCX,y+6,rad>12?17:15,CY_,Paint.Align.CENTER);}
            else{ring(c,LCX,y,rad,on?3:1.2f,on?CY_:LINE_);mono(c,gs[i],LCX,y+6,on?17:rad>12?14:13,on?WH:GREY,Paint.Align.CENTER);}
            if(on&&!Float.isNaN(rpm))join(c,LCX,rpm/prof.maxRpm,y,rad,2,WH);}   // the current gear points at the rpm level
    }
    /** Mirroring the gear column: the speed ring and the lamps. */
    private void lamps(Canvas c,float kmh,String shown,boolean mt){
        float dy=(COL_Y1-COL_Y0)/7,ry=COL_Y0+1.5f*dy;
        if(!Float.isNaN(kmh))join(c,RCX,kmh/SPEED_MAX,ry,38,2,WH);   // the speed ring points at the speed level
        dot(c,RCX,ry,38,INK);ring(c,RCX,ry,38,3,WH);bold(c,fmt("%.0f",kmh),RCX,ry+11,30,WH,Paint.Align.CENTER);
        float y=COL_Y0+2.3f*dy;
        float brake=v(10),pedal=v(9);
        if(mt){Telemetry.Sample k0=s[Telemetry.CLUTCH];boolean clutch=k0.valid&&k0.value>0;
            boolean heel=clutch&&brake>ShiftCoach.HEEL_BRAKE_BAR&&pedal>ShiftCoach.HEEL_PEDAL;
            lamp(c,y+1.05f*dy,"C",clutch,BLUE);lamp(c,y+3.9f*dy,"HT",heel,OR);}
        else{int lv=Math.max(Telemetry.clutchLevel(v(Telemetry.CLUTCH_ODD)),Telemetry.clutchLevel(v(Telemetry.CLUTCH_EVEN)));
            lamp(c,y+1.05f*dy,"M",shown.startsWith("M"),CY_);lamp(c,y+3.9f*dy,"CL",lv>0,lv==2?RED:AMBER);}
        lamp(c,y+2.0f*dy,"B",brake>1,RED);lamp(c,y+2.95f*dy,"T",pedal>5,GREEN);
    }
    private void lamp(Canvas c,float y,String k,boolean on,int col){
        if(on)dot(c,RCX,y,13,col);else ring(c,RCX,y,13,1.2f,LINE_);
        mono(c,k,RCX,y+5,k.length()>1?12:15,on?INK:GREY,Paint.Align.CENTER);
    }
    /** Centre column: G grid, four small arcs, the last shift (6MT) or the clutch temperatures (DCT). */
    private void centre(Canvas c,boolean mt){
        float cx=400,top=64,bot=124,wt=130,wb=96;
        for(int i=0;i<=6;i++){float f=i/6f;line(c,cx-wt/2+wt*f,top,cx-wb/2+wb*f,bot,1,i==3?CY_:LINE_);}
        for(int j=0;j<=4;j++){float f=j/4f,y=top+(bot-top)*f,w=wt+(wb-wt)*f,bow=5*(1-Math.abs(f-.5f)*2);
            line(c,cx-w/2,y,cx,y+bow,1,j==2?CY_:LINE_);line(c,cx,y+bow,cx+w/2,y,1,j==2?CY_:LINE_);}
        float lat=v(11),lon=v(12);
        if(!Float.isNaN(lat)&&!Float.isNaN(lon)){float k=Telemetry.pinG(lat,lon,1.2f)/1.2f,fy=clamp(.5f-lon*k*.5f,0,1),w=wt+(wb-wt)*fy;
            dot(c,cx+lat*k*w/2,top+(bot-top)*fy,6,GREEN);}
        mono(c,String.format(Locale.US,"%.2f G",Float.isNaN(lat)||Float.isNaN(lon)?0:Math.hypot(lat,lon)),cx,bot+18,12,WH,Paint.Align.CENTER);
        mini(c,364,196,t("냉각수"),v(2),40,120,"%.0f");mini(c,436,196,"BOOST",v(Telemetry.BOOST),-1,1.5f,"%+.1f");
        mini(c,364,290,t("흡기"),v(Telemetry.INTAKE),0,80,"%.0f");mini(c,436,290,"BATT",v(Telemetry.BAT_VOLTAGE),10,15,"%.1f");
        if(!mt){float o=v(Telemetry.CLUTCH_ODD),e=v(Telemetry.CLUTCH_EVEN);
            mono(c,"CLUTCH",cx,344,11,GREY,Paint.Align.CENTER);
            clutchTemp(c,cx-36,"ODD",o);clutchTemp(c,cx+36,"EVEN",e);return;}
        ShiftCoach co=st==null?null:st.coach;ShiftCoach.Shift sh=co==null?null:co.last;
        mono(c,"SHIFT",cx,344,11,GREY,Paint.Align.CENTER);
        if(sh!=null){int gc=sh.grade=='S'?GREEN:sh.grade=='A'?CY_:sh.grade=='B'?YELLOW:GREY;
            bold(c,String.valueOf(sh.grade),cx,402,52,gc,Paint.Align.CENTER);
            mono(c,sh.from+" > "+sh.to+(sh.heelToe?"  H&T":""),cx,424,13,WH,Paint.Align.CENTER);
            mono(c,String.format(Locale.US,"%+,d rpm",Math.round(sh.revErr)),cx,442,12,GREY,Paint.Align.CENTER);
            mono(c,String.format(Locale.US,"x%d  BEST %d",co.streak,co.bestStreak),cx,460,12,co.streak>=3?GREEN:GREY,Paint.Align.CENTER);}
    }
    private void clutchTemp(Canvas c,float x,String name,float t){
        int lv=Telemetry.clutchLevel(t);
        bold(c,fmt("%.0f",t),x,392,30,lv==2?RED:lv==1?AMBER:WH,Paint.Align.CENTER);mono(c,name+" °C",x,412,11,GREY,Paint.Align.CENTER);
    }
    private void mini(Canvas c,float cx,float cy,String name,float val,float lo,float hi,String f){
        float r=24,a0=-55,a1=55,on=Float.isNaN(val)?-1:clamp((val-lo)/(hi-lo),0,1);
        for(int i=0;i<=14;i++){float a=a0+(a1-a0)*i/14f;line(c,px(cx,r,a),py(cy,r,a),px(cx,r+6,a),py(cy,r+6,a),2,i/14f<=on?CY_:DIM);}
        mono(c,name,cx,cy-r-10,10,GREY,Paint.Align.CENTER);mono(c,fmt(f,val),cx,cy+8,14,WH,Paint.Align.CENTER);
    }
}
