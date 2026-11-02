package com.veloster.vehicle;

import android.graphics.Canvas;
import android.graphics.Paint;
import java.util.Calendar;
import java.util.Locale;

/**
 * Airliner engine display (EICAS style): grey sector dials with amber/red limits and boxed readouts for N1
 * (rpm % of redline), coolant and engine load; secondary rows FF/MAP/SPK/IAT/SPD; a colour-coded message stack
 * (red warning, amber caution, cyan advisory, white memo); the gear in a green landing-gear box.
 */
final class EngineCluster extends ClusterKit {
    private static final int WH=0xfff2f2f2,CY=0xff4fd1ff,AM=0xffffb000,RD=0xffff3030,GR=0xff3ddc56,GY=0xff3b4046,SUB=0xff6f8c99;

    @Override protected void draw(Canvas c){
        float rpm=v(0),cool=v(2),load=v(Telemetry.ENGINE_LOAD),fuel=v(Telemetry.FUEL_RATE),boost=v(Telemetry.BOOST),spark=v(Telemetry.SPARK),iat=v(Telemetry.INTAKE),spd=v(7),top=boostTop();
        fill(c,0,52,800,480,0xff000000);
        float n1=Float.isNaN(rpm)?Float.NaN:rpm/prof.redline*100;
        dial(c,92,152,62,n1,0,110,92,100,"N1","RPM %",fmt("%.1f",n1));
        dial(c,270,152,62,cool,40,130,105,115,"TEMP",t("냉각수 °C"),fmt("%.0f",cool));
        dial(c,448,152,62,load,0,110,95,105,"LOAD",t("부하 %"),fmt("%.0f",load));
        fill(c,20,254,540,255,0xff1d2328);
        row(c,0,"FF",fmt("%.1f",fuel),"L/h",Float.isNaN(fuel)?Float.NaN:fuel/40,WH,Float.NaN);
        row(c,1,"MAP",fmt("%+.2f",boost),"bar",Float.isNaN(boost)?Float.NaN:(boost+1)/(top+1),!Float.isNaN(boost)&&boost>top*0.9f?AM:WH,(top*0.9f+1)/(top+1));
        row(c,2,"SPK",fmt("%+.1f",spark),"°",Float.isNaN(spark)?Float.NaN:(spark+15)/55,WH,Float.NaN);
        row(c,3,"IAT",fmt("%.0f",iat),"°C",Float.isNaN(iat)?Float.NaN:(iat-20)/60,!Float.isNaN(iat)&&iat>60?AM:WH,Float.NaN);
        row(c,4,"SPD",fmt("%.0f",spd),"km/h",Float.isNaN(spd)?Float.NaN:spd/260,WH,Float.NaN);
        mono(c,String.format(Locale.US,"%.1f",top),522,284+38-18,11,SUB,Paint.Align.RIGHT);
        // right column
        fill(c,552,60,553,472,0xff1d2328);
        mono(c,"IAT",566,80,15,CY,Paint.Align.LEFT);mono(c,fmt("%+.0f",iat)+"c",620,80,15,WH,Paint.Align.LEFT);
        mono(c,"BATT",690,80,15,CY,Paint.Align.LEFT);mono(c,fmt("%.1f",v(Telemetry.BAT_VOLTAGE))+"V",786,80,15,WH,Paint.Align.RIGHT);
        round(c,562,94,788,308,4,0xff020405);outline(c,562,94,788,308,4,1,0xff1d2328);
        int n=0;
        if(!Float.isNaN(rpm)&&rpm>prof.redline)n=msg(c,n,"RPM LIMIT",RD);
        if(!Float.isNaN(cool)&&cool>=110)n=msg(c,n,"ENG TEMP",RD);
        if(v(Telemetry.DOOR_DRIVER)>0||v(Telemetry.DOOR_PASSENGER)>0)n=msg(c,n,"DOOR OPEN",AM);
        if(v(Telemetry.PARK_BRAKE)>0)n=msg(c,n,"PARK BRAKE",AM);
        if(v(10)>35)n=msg(c,n,"HARD BRAKING",AM);
        for(int i=Telemetry.TPMS_FL;i<=Telemetry.TPMS_RR;i++){float t=v(i);if(!Float.isNaN(t)&&t<30){n=msg(c,n,"TIRE PRESS",AM);break;}}
        if(!Float.isNaN(boost)&&boost>0.4f)n=msg(c,n,"TURBO BOOST",CY);
        if(!Float.isNaN(fuel)&&fuel==0&&!Float.isNaN(spd)&&spd>0)n=msg(c,n,"FUEL CUT",CY);
        float mode=v(Telemetry.DRIVE_MODE);
        n=msg(c,n,"DRIVE MODE "+(Float.isNaN(mode)?"—":Telemetry.driveModeLabel((int)mode)),WH);
        msg(c,n,"ODO "+fmt("%.0f",v(Telemetry.ODOMETER)),WH);
        mono(c,"GEAR",600,342,16,CY,Paint.Align.CENTER);outline(c,570,352,630,402,0,2,GR);mono(c,gear(),600,392,36,GR,Paint.Align.CENTER);
        mono(c,"SPD",710,342,16,CY,Paint.Align.CENTER);outline(c,652,352,770,402,0,2,WH);
        mono(c,Float.isNaN(spd)?"---":String.format(Locale.US,"%03d",Math.round(spd)),760,392,36,WH,Paint.Align.RIGHT);
        mono(c,"FUEL",574,444,16,CY,Paint.Align.LEFT);mono(c,fmt("%.0f",v(Telemetry.RANGE))+" KM",786,444,20,WH,Paint.Align.RIGHT);
        Calendar k=Calendar.getInstance();
        mono(c,"TIME",574,470,16,CY,Paint.Align.LEFT);
        mono(c,String.format(Locale.US,"%02d:%02d:%02d",k.get(Calendar.HOUR_OF_DAY),k.get(Calendar.MINUTE),k.get(Calendar.SECOND)),786,470,20,GR,Paint.Align.RIGHT);
    }
    private int msg(Canvas c,int i,String s,int color){if(i<8)mono(c,s,574,120+i*25,17,color,Paint.Align.LEFT);return i+1;}
    private void dial(Canvas c,float cx,float cy,float rad,float val,float min,float max,float amb,float red,String label,String sub,String txt){
        boolean ok=!Float.isNaN(val);float f=ok?clamp((val-min)/(max-min),0,1):0;int col=!ok?SUB:val>=red?RD:val>=amb?AM:WH;
        r.set(cx-rad,cy-rad,cx+rad,cy+rad);
        if(ok){p.setStyle(Paint.Style.FILL);p.setColor(GY);c.drawArc(r,180,210*f,true,p);}
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);p.setColor(WH);c.drawArc(r,180,210,false,p);p.setStyle(Paint.Style.FILL);
        mark(c,cx,cy,rad,(amb-min)/(max-min),AM);mark(c,cx,cy,rad,(red-min)/(max-min),RD);
        if(ok){double a=Math.toRadians(180+210*f);line(c,cx,cy,cx+(float)Math.cos(a)*(rad-2),cy+(float)Math.sin(a)*(rad-2),3,col);}
        fill(c,cx+4,cy-32,cx+90,cy-2,0xff000000);outline(c,cx+4,cy-32,cx+90,cy-2,0,1.5f,col);
        mono(c,txt,cx+84,cy-9,22,col,Paint.Align.RIGHT);
        mono(c,label,cx-12,cy+30,18,CY,Paint.Align.CENTER);text(c,t(sub),cx-12,cy+48,12,SUB,Paint.Align.CENTER,android.graphics.Typeface.DEFAULT);
    }
    private void mark(Canvas c,float cx,float cy,float rad,float f,int color){
        double a=Math.toRadians(180+210*clamp(f,0,1));float co=(float)Math.cos(a),si=(float)Math.sin(a);
        line(c,cx+co*(rad-8),cy+si*(rad-8),cx+co*(rad+8),cy+si*(rad+8),3,color);
    }
    private void row(Canvas c,int i,String l,String v,String u,float f,int color,float limit){
        float y=284+i*38;
        mono(c,l,30,y,18,CY,Paint.Align.LEFT);
        outline(c,96,y-20,192,y+6,0,1.2f,color);mono(c,v,184,y,19,color,Paint.Align.RIGHT);mono(c,u,200,y,13,0xff8aa2ad,Paint.Align.LEFT);
        fill(c,250,y-14,520,y-2,0xff14191d);if(!Float.isNaN(f))fill(c,250,y-14,250+270*clamp(f,0,1),y-2,color==WH?0xffd9dde0:color);
        if(!Float.isNaN(limit)){float x=250+270*limit;fill(c,x-1,y-18,x+1,y+2,AM);}
    }
}
