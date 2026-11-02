package com.veloster.vehicle;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import java.util.Locale;

/**
 * Cluster tab, below the app header (y 52..480): three full-page instrument layouts after the Veloster N
 * community gauge (4th gen). 0 Classic: stepped rev curve, big italic gear between mirrored engine and tyre cells. 1 Retro: orange LCD. 2 Rally:
 * white boxed numbers. Only received signals; a missing one reads "—". Status and warning lamps light only while on.
 */
public final class ClusterRenderer {
    /** Every colour goes through the selected theme (identity for N). */
    private static int T(int c){return Theme.current().map(c);}
    /** Retro keeps its own LCD palette in every theme; only warnings follow the theme (danger + ring). */
    private boolean fixedPalette;
    private int col(int c){return fixedPalette?c:T(c);}
    private static int D(){return Theme.current().danger;}
    private final Paint p=new Paint(3);
    private final Path path=new Path();
    private final RectF r=new RectF();
    // N palette (remapped per theme) and the Classic layout's own yellow.
    private static final int BG=0xff040506,PANEL=0xff101315,LINE=0xff303638,TRACK=0xff1d2326,WHITE=0xffe1edf2,MUTED=0xff91a5ae,
        BLUE=0xffa4d9ef,AMBER=0xffffb84d,GREEN=0xff3ddc84,YELLOW=0xffffd21a,NIGHT=0xff2a2f6a;
    // Retro LCD palette (not themed).
    private static final int ORANGE=0xffff7a1a,R_AMBER=0xffffb02e,R_YELLOW=0xffffd23a,LCD_RED=0xffff3a2a,FRAME=0xff8a2a10,CELL=0xff140500,
        R_BG=0xff080200,R_DIM=0xff2a0a04,CYAN=0xff2cd4e8,PINK=0xffff9ad0;
    private static final float TIRE_LOW=30,TIRE_CRIT=26;
    private static final long POP_MS=150;

    private Telemetry.Sample[] s;
    private long now,popAt=Long.MIN_VALUE/2;
    private int lastGear;
    private DriveStats st;
    private VehicleProfile prof=VehicleProfile.PETROL_DCT;

    public void draw(Canvas c,int layout,Telemetry.Sample[] samples,long frameTime,DriveStats stats,VehicleProfile profile){
        s=samples;now=frameTime;st=stats;if(profile!=null)prof=profile;
        float gv=v(8);int gi=Float.isNaN(gv)?0:(int)gv;
        if(gi>=1&&gi<=7){if(lastGear>0&&gi!=lastGear)popAt=now;lastGear=gi;}
        fixedPalette=layout==1;
        fill(c,0,52,800,480,fixedPalette?R_BG:BG);
        if(layout==1)retro(c);else if(layout==2)rally(c);else classic(c);
    }

    // ---- Classic ---------------------------------------------------------------------------------------------
    private void classic(Canvas c){
        int[] sig={Telemetry.WIPER,Telemetry.PARK_BRAKE,Telemetry.DOOR_DRIVER,Telemetry.DOOR_PASSENGER,Telemetry.TRUNK,Telemetry.HOOD};
        String[] name={"WIPER","PARK","DOOR L","DOOR R","TRUNK","HOOD"};
        lamps(c,sig,name,56,72,true);
        revCurve(c,12,78,788,128);
        int hero=hero();
        // Left: engine 2x2, mirrored on the right by the four tyres (same cell, same bar).
        float cool=v(2),in=v(Telemetry.INTAKE),boost=v(Telemetry.BOOST),acc=v(9);
        meter(c,12,138,133,258,"COOLANT","°C",fmt("%.0f",cool),temp(cool,100,110),(cool-40)/80,hero,false);
        meter(c,141,138,262,258,"INTAKE","°C",fmt("%.0f",in),temp(in,60,80),in/80,hero,false);
        meter(c,12,266,133,386,"BOOST","bar",fmt("%+.2f",boost),WHITE,boost/1.5f,hero,false);
        meter(c,141,266,262,386,"ACCEL","%",fmt("%.0f",acc),WHITE,acc/100,hero,false);
        int[] ts={Telemetry.TPMS_FL,Telemetry.TPMS_FR,Telemetry.TPMS_RL,Telemetry.TPMS_RR};String[] tn={"FL","FR","RL","RR"};
        for(int i=0;i<4;i++){
            float val=v(ts[i]),l=i%2==0?538:667,t=i<2?138:266;boolean crit=!Float.isNaN(val)&&val<TIRE_CRIT;
            int state=Float.isNaN(val)?TRACK:crit?D():val<TIRE_LOW?AMBER:GREEN;
            meter(c,l,t,l+121,t+120,tn[i],"psi",fmt("%.0f",val),crit?D():WHITE,val/45,state,crit);
        }
        // Centre: speed | rpm, gear, drive mode (and the 6MT shift hint).
        bigItalic(c,fmt("%.0f",v(7)),335,170,34,hero,Paint.Align.CENTER);text(c,"km/h",335,188,11,MUTED,Paint.Align.CENTER,Typeface.DEFAULT_BOLD,1,0);
        bigItalic(c,fmt("%.0f",v(0)),465,170,34,WHITE,Paint.Align.CENTER);text(c,"rpm",465,188,11,MUTED,Paint.Align.CENTER,Typeface.DEFAULT_BOLD,1,0);
        gearNumeral(c,400,350,180,hero);
        float mode=v(Telemetry.DRIVE_MODE);String hint=Telemetry.shiftHint(v(Telemetry.SHIFT_HINT));
        String m=Float.isNaN(mode)?"—":Telemetry.driveModeLabel((int)mode);
        bigItalic(c,hint.isEmpty()?m:m+"   "+hint,400,382,20,BLUE,Paint.Align.CENTER);
        // Bottom line: six labelled readouts, then the warning lamps.
        float lat=v(11);
        String[] bl={"BATT","FUEL","AVG","RANGE","ODO","LAT G"};
        String[] bv={fmt("%.1f",v(Telemetry.BAT_VOLTAGE))+"V",fmt("%.1f",v(Telemetry.FUEL_RATE))+"L/h",fmt("%.1f",v(Telemetry.AVG_FUEL))+"km/L",
            fmt("%.0f",v(Telemetry.RANGE))+"km",fmt("%.0f",v(Telemetry.ODOMETER))+"km",fmt("%.2f",Float.isNaN(lat)?Float.NaN:Math.abs(lat))};
        fill(c,12,394,788,395,LINE);
        for(int i=0;i<6;i++){float x=12+776*(i+.5f)/6;
            text(c,bl[i],x,409,10,MUTED,Paint.Align.CENTER,Typeface.DEFAULT_BOLD,1,0);bigItalic(c,bv[i],x,428,16,WHITE,Paint.Align.CENTER);}
        warnings(c,436,472);
    }
    /** Classic's own colour: the community gauge's yellow in N, the theme accent everywhere else. */
    private static int hero(){return Theme.current().id.equals("n")?YELLOW:Theme.current().accent;}
    /** Label + unit on top, big value, thin bar (0..1) at the bottom; ring marks a warning. */
    private void meter(Canvas c,float l,float t,float rr,float b,String name,String unit,String value,int valueCol,float frac,int barCol,boolean ring){
        if(!ThemeShapes.cell(c,p,path,r,l,t,rr,b,col(PANEL),col(LINE))){
            fill(c,l,t,rr,b,PANEL);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1);p.setColor(col(LINE));c.drawRect(l+.5f,t+.5f,rr-.5f,b-.5f,p);p.setStyle(Paint.Style.FILL);
        }
        text(c,name,l+10,t+20,12,MUTED,Paint.Align.LEFT,Typeface.DEFAULT_BOLD,1,0);
        text(c,unit,rr-10,t+20,11,MUTED,Paint.Align.RIGHT,Typeface.DEFAULT_BOLD,1,0);
        bigItalic(c,value,rr-10,b-30,value.length()>4?30:38,valueCol,Paint.Align.RIGHT);
        fill(c,l+10,b-16,rr-10,b-11,TRACK);
        if(!Float.isNaN(frac))fill(c,l+10,b-16,l+10+(rr-l-20)*Math.max(0,Math.min(1,frac)),b-11,barCol);
        if(ring)ThemeShapes.ring(c,p,l,t,rr,b);
    }
    /** Stepped rev curve: columns rise like a torque curve; lit up to the current rpm, red past the redline. */
    private void revCurve(Canvas c,float l,float t,float rr,float b){
        int n=48,hero=hero(),off=Theme.current().id.equals("n")?NIGHT:TRACK;
        float w=(rr-l)/n,rpm=v(0),lit=Float.isNaN(rpm)?0:rpm/prof.maxRpm,red=prof.redline/prof.maxRpm;
        for(int i=0;i<n;i++){
            float f=(i+1f)/n,h=(b-t-14)*Math.min(1f,(float)Math.pow(Math.min(1f,f/0.3f),0.7f))*0.85f+(b-t-14)*0.15f;
            int col=f<=lit?(f>red?D():hero):off;
            fill(c,l+i*w+1,b-h,l+(i+1)*w-1,b,col);
        }
        for(int g=1;g<=prof.topGear;g++)bigItalic(c,String.valueOf(g),l+(rr-l)*(g-.5f)/prof.topGear,t+10,13,hero,Paint.Align.CENTER);
    }

    // ---- Retro -----------------------------------------------------------------------------------------------
    private void retro(Canvas c){
        // Segment rev bar with gear numbers.
        int n=18;float rpm=v(0),lit=Float.isNaN(rpm)?0:rpm/prof.maxRpm*n,red=prof.redline/prof.maxRpm*n,w=(776-2f*(n-1))/n;
        for(int i=0;i<n;i++)fill(c,12+i*(w+2),58,12+i*(w+2)+w,72,i<lit?(i>=red?LCD_RED:R_YELLOW):i>=red?0xff3a0a06:R_DIM);
        for(int g=1;g<=prof.topGear;g++)mono(c,String.valueOf(g),12+776*(g-.5f)/prof.topGear,88,12,g==prof.topGear?LCD_RED:ORANGE,Paint.Align.CENTER);
        // Left: boost with session and last-pull peaks, then four bar graphs.
        lcdBox(c,12,96,262,372);
        lcdLabel(c,"BOOST",20,112);mono(c,fmt("%+.2f",v(Telemetry.BOOST)),20,140,26,R_AMBER,Paint.Align.LEFT);
        lcdLabel(c,"MAX",170,112);mono(c,fmt("%.2f",st==null?Float.NaN:st.maxBoost),170,140,20,LCD_RED,Paint.Align.LEFT);
        lcdLabel(c,"PULL",170,156);mono(c,fmt("%.2f",st==null?Float.NaN:st.pullMaxBoost),170,182,20,R_AMBER,Paint.Align.LEFT);
        String[] cap={"CLT","INT","THR","BRK"};int[] sig={2,Telemetry.INTAKE,9,10};float[] lo={40,0,0,0},hi={120,80,100,150};
        String[] f={"%.0f","%.0f","%.0f","%.1f"};
        for(int i=0;i<4;i++){
            float x=28+i*58,val=v(sig[i]);
            lcdLabel(c,cap[i],x,200);
            segBar(c,x,206,x+18,340,Float.isNaN(val)?0:(val-lo[i])/(hi[i]-lo[i]));
            mono(c,fmt(f[i],val),x+9,362,14,R_AMBER,Paint.Align.CENTER);
        }
        // Centre: speed, gear, drive mode.
        mono(c,fmt("%.0f",v(7)),400,140,40,R_YELLOW,Paint.Align.CENTER);
        float pop=Math.max(0,1-(now-popAt)/(float)POP_MS);float g=v(8);
        String gt=Telemetry.gearText(g,v(Telemetry.GEAR_SELECTOR),prof);
        mono(c,gt,400,320,big(gt,170)*(1+.2f*pop),pop>0?mix(0xffffffff,R_AMBER,pop):R_AMBER,Paint.Align.CENTER);
        float mode=v(Telemetry.DRIVE_MODE);String m=Float.isNaN(mode)?"—":Telemetry.driveModeLabel((int)mode);
        lcdBox(c,340,334,460,364);mono(c,m,400,356,18,PINK,Paint.Align.CENTER);
        // Right: car outline with the four tyres.
        lcdBox(c,538,96,788,372);lcdLabel(c,"TPMS psi",663,112);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.5f);p.setColor(col(ORANGE));r.set(638,128,688,352);c.drawRoundRect(r,14,14,p);
        c.drawLine(663,148,663,332,p);p.setStyle(Paint.Style.FILL);
        int[] ts={Telemetry.TPMS_FL,Telemetry.TPMS_FR,Telemetry.TPMS_RL,Telemetry.TPMS_RR};
        for(int i=0;i<4;i++){
            boolean right=i%2==1,rear=i>=2;float val=v(ts[i]);boolean crit=!Float.isNaN(val)&&val<TIRE_CRIT;
            float x=right?694:616,y=rear?268:148;
            fill(c,x,y,x+16,y+40,Float.isNaN(val)?R_DIM:crit?D():val<TIRE_LOW?R_AMBER:CYAN);
            if(crit)ThemeShapes.ring(c,p,x-3,y-3,x+19,y+43);
            mono(c,fmt("%.0f",val),right?780:548,y+28,22,R_AMBER,right?Paint.Align.RIGHT:Paint.Align.LEFT);
        }
        // Bottom: last corner, live lateral G, battery.
        String[] bl={"BTM SPEED","PREV LAT G","LAT G","BATT"};
        float lat=v(11);
        String[] bv={fmt("%.0f",st==null?Float.NaN:st.cornerMinSpeed),fmt("%.2f",st==null?Float.NaN:st.cornerMaxLat),
            fmt("%.2f",Float.isNaN(lat)?Float.NaN:Math.abs(lat)),fmt("%.1f",v(Telemetry.BAT_VOLTAGE))};
        for(int i=0;i<4;i++){float x=12+i*196.5f;lcdBox(c,x,382,x+192,472);lcdLabel(c,bl[i],x+10,400);mono(c,bv[i],x+10,456,36,R_AMBER,Paint.Align.LEFT);}
    }
    private void lcdBox(Canvas c,float l,float t,float rr,float b){
        fill(c,l,t,rr,b,CELL);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1);p.setColor(col(FRAME));c.drawRect(l+.5f,t+.5f,rr-.5f,b-.5f,p);p.setStyle(Paint.Style.FILL);
    }
    private void lcdLabel(Canvas c,String str,float x,float y){text(c,str,x,y,10,0xffff5a3a,Paint.Align.LEFT,Typeface.MONOSPACE,1,0);}
    private void mono(Canvas c,String str,float x,float y,float size,int color,Paint.Align align){text(c,str,x,y,size,color,align,Typeface.MONOSPACE,1,-0.2f);}
    /** Segmented vertical bar, filled bottom-up to fraction 0..1. */
    private void segBar(Canvas c,float l,float t,float rr,float b,float fraction){
        int n=15;float h=(b-t)/n,lit=Math.max(0,Math.min(1,fraction))*n;
        for(int i=0;i<n;i++){float y=b-(i+1)*h;fill(c,l,y+1,rr,y+h-1,i<lit?ORANGE:R_DIM);}
    }

    // ---- Rally -----------------------------------------------------------------------------------------------
    private void rally(Canvas c){
        fill(c,0,52,800,480,0xff000000);
        // Rev bar: one cell per gear, green to red, white marker at the current rpm.
        int n=prof.topGear;float l=12,rr=788,w=(rr-l)/n,rpm=v(0);
        for(int i=0;i<n;i++){
            fill(c,l+i*w,60,l+(i+1)*w,94,mix(0xffee2222,0xff55ff22,n==1?0:i/(n-1f)));
            wide(c,String.valueOf(i+1),l+(i+.5f)*w,87,24,0xff111111);
        }
        if(!Float.isNaN(rpm)){float x=l+(rr-l)*Math.max(0,Math.min(1,rpm/prof.maxRpm));fill(c,x-2,56,x+2,98,0xffffffff);}
        frame(c,l,60,rr,94,0xffdddddd);
        // Speed | gear | boost, tyres | gear | temperatures, then four small boxes.
        frame(c,12,102,262,162,0xffdddddd);wide(c,fmt("%.0f",v(7)),137,150,40,0xffffffff);
        frame(c,538,102,788,162,0xffdddddd);wide(c,fmt("%.2f",v(Telemetry.BOOST)),663,150,36,0xffffffff);
        frame(c,270,102,530,356,0xffdddddd);
        float pop=Math.max(0,1-(now-popAt)/(float)POP_MS),g=v(8);
        int gearCol=Theme.current().id.equals("n")?0xff77ffff:Theme.current().accent;
        String gt=Telemetry.gearText(g,v(Telemetry.GEAR_SELECTOR),prof);
        wide(c,gt,400,320,big(gt,220)*(1+.2f*pop),pop>0?mix(0xffffffff,gearCol,pop):gearCol);
        frame(c,12,170,262,356,0xffdddddd);caption(c,"TIRE PRESS",137,190);
        int[] ts={Telemetry.TPMS_FL,Telemetry.TPMS_FR,Telemetry.TPMS_RL,Telemetry.TPMS_RR};
        for(int i=0;i<4;i++){float val=v(ts[i]);boolean crit=!Float.isNaN(val)&&val<TIRE_CRIT;
            wide(c,fmt("%.0f",val),i%2==0?80:194,i<2?256:330,40,crit?D():!Float.isNaN(val)&&val<TIRE_LOW?AMBER:0xffffffff);}
        frame(c,538,170,788,356,0xffdddddd);caption(c,"COOLANT · INTAKE",663,190);
        wide(c,fmt("%.0f",v(2)),606,290,44,temp(v(2),100,110));wide(c,fmt("%.0f",v(Telemetry.INTAKE)),722,290,44,temp(v(Telemetry.INTAKE),60,80));
        float mode=v(Telemetry.DRIVE_MODE);
        frame(c,12,364,262,428,0xffdddddd);wide(c,Float.isNaN(mode)?"—":Telemetry.driveModeLabel((int)mode),137,408,26,0xffffffff);
        frame(c,270,364,396,428,0xff22dddd);wide(c,fmt("%.0f",rpm),333,406,22,0xffffffff);
        frame(c,404,364,530,428,0xffee7722);wide(c,fmt("%.0f",v(Telemetry.RANGE)),467,406,22,0xffffffff);
        frame(c,538,364,788,428,0xffdddddd);wide(c,fmt("%.0f",v(9))+"%",663,408,28,0xffffffff);
        warnings(c,436,472);
    }
    /** Rounded white outline (radius 7), the Rally look in every theme. */
    private void frame(Canvas c,float l,float t,float rr,float b,int color){
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.5f);p.setColor(col(color));r.set(l,t,rr,b);c.drawRoundRect(r,7,7,p);p.setStyle(Paint.Style.FILL);
    }
    private void wide(Canvas c,String str,float x,float y,float size,int color){text(c,str,x,y,size,color,Paint.Align.CENTER,Typeface.DEFAULT_BOLD,1.25f,0);}
    private void caption(Canvas c,String str,float x,float y){text(c,str,x,y,12,0xffcccccc,Paint.Align.CENTER,Typeface.DEFAULT_BOLD,1,0);}

    // ---- shared pieces -----------------------------------------------------------------------------------------
    /** Lamp strip: each cell lights only while its signal is on (wiper in the accent, everything else as a warning). */
    private void lamps(Canvas c,int[] sig,String[] name,float t,float b,boolean wiperFirst){
        float l=12,rr=788,gap=4,w=(rr-l-gap*(sig.length-1))/sig.length;
        for(int i=0;i<sig.length;i++){
            float x=l+i*(w+gap),val=v(sig[i]);boolean on=!Float.isNaN(val)&&val>0;
            int col=on?(wiperFirst&&i==0?BLUE:D()):PANEL;
            fill(c,x,t,x+w,b,col);
            if(on&&col==D())ThemeShapes.ring(c,p,x,t,x+w,b);
            text(c,name[i],x+w/2,b-4,10,on?BG:Float.isNaN(val)?TRACK:MUTED,Paint.Align.CENTER,Typeface.DEFAULT_BOLD,1,0);
        }
    }
    private void warnings(Canvas c,float t,float b){
        boolean low=false,tpms=false;for(int i=Telemetry.TPMS_FL;i<=Telemetry.TPMS_RR;i++){float x=v(i);if(!Float.isNaN(x)){tpms=true;if(x<TIRE_LOW)low=true;}}
        int[] sig={Telemetry.PARK_BRAKE,Telemetry.ESC,Telemetry.ABS,Telemetry.TCS};
        String[] name={"PARK BRAKE","ESC","ABS","TCS","TPMS"};
        float l=12,rr=788,gap=4,w=(rr-l-gap*4)/5;
        for(int i=0;i<5;i++){
            float x=l+i*(w+gap),val=i<4?v(sig[i]):!tpms?Float.NaN:low?1:0;boolean on=!Float.isNaN(val)&&val>0,seen=!Float.isNaN(val);
            fill(c,x,t,x+w,b,on?D():PANEL);
            if(on)ThemeShapes.ring(c,p,x,t,x+w,b);
            text(c,name[i],x+w/2,b-11,13,on?BG:seen?MUTED:TRACK,Paint.Align.CENTER,Typeface.DEFAULT_BOLD,1,0);
        }
    }
    /** Gear numeral with the 150 ms change pop: flashes white and shrinks from 1.2x back to size. */
    private void gearNumeral(Canvas c,float x,float y,float size,int color){
        float pop=Math.max(0,1-(now-popAt)/(float)POP_MS);
        float g=v(8);String shown=Telemetry.gearText(g,v(Telemetry.GEAR_SELECTOR),prof);
        bigItalic(c,shown,x,y,big(shown,size)*(1+.2f*pop),pop>0?mix(0xffffffff,color,pop):color,Paint.Align.CENTER);
    }
    /** Two-character gears (M4, the manual gate) shrink so they stay inside the gear box. */
    private static float big(String gear,float size){return gear.length()>1?size*0.62f:size;}
    private int temp(float t,float warn,float crit){return Float.isNaN(t)?WHITE:t>=crit?D():t>=warn?AMBER:WHITE;}

    private float v(int i){return i>=0&&i<s.length&&s[i].valid&&!Float.isNaN(s[i].value)&&!Float.isInfinite(s[i].value)?Telemetry.displayValue(i,s[i].value):Float.NaN;}
    private static String fmt(String f,float x){return Float.isNaN(x)?"—":String.format(Locale.US,f,x);}
    private void fill(Canvas c,float l,float t,float rr,float b,int color){p.setStyle(Paint.Style.FILL);p.setColor(col(color));c.drawRect(l,t,rr,b,p);}
    private void bigItalic(Canvas c,String str,float x,float y,float size,int color,Paint.Align align){
        text(c,str,x,y,size,color,align,Typeface.DEFAULT_BOLD,1,-0.2f);
    }
    private void text(Canvas c,String str,float x,float y,float size,int color,Paint.Align align,Typeface face,float scaleX,float skewX){
        p.setStyle(Paint.Style.FILL);p.setColor(col(color));p.setTypeface(face);p.setTextSize(size);p.setTextAlign(align);
        p.setTextScaleX(scaleX);p.setTextSkewX(skewX);c.drawText(str,x,y,p);p.setTextScaleX(1);p.setTextSkewX(0);
    }
    private static int mix(int a,int b,float t){
        int o=0xff000000;for(int sh=0;sh<24;sh+=8){int x=(a>>sh)&255,y=(b>>sh)&255;o|=Math.round(y+(x-y)*t)<<sh;}return o;
    }
}
