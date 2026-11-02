package com.veloster.vehicle;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import java.util.Locale;

/**
 * Cassette deck: the playing track on the tape label, tape winding onto the right reel with the track's progress,
 * reels spinning with rpm, two VU needles (accelerator, boost), a mechanical counter (odometer tenths) and a VFD speed.
 */
final class CassetteCluster extends ClusterKit {
    private static final int VF=0xff5effc0,VF_OFF=0xff0b2a20,INK=0xff1f3a8a,FACE=0xffefe3c2;
    private float rot,needleL,needleR;
    private long last;

    @Override protected void draw(Canvas c){
        float rpm=v(0),pedal=v(9),boost=v(Telemetry.BOOST),spd=v(7),top=boostTop();
        float dt=last==0||now-last>2000?0:(now-last)/1000f;last=now;
        rot+=dt*(0.4f+(Float.isNaN(rpm)?0:rpm)/1500);
        float tl=Float.isNaN(pedal)?0:pedal/100,tr=Float.isNaN(boost)?0:clamp((boost+0.7f)/(top+0.7f),0,1);
        needleL+=(tl-needleL)*Math.min(1,dt*(tl>needleL?10:4));needleR+=(tr-needleR)*Math.min(1,dt*(tr>needleR?10:4));
        boolean has=music!=null&&music.title!=null&&!music.title.isEmpty();
        boolean known=has&&music.hasProgress();   // position and duration both reported
        long pos=!known?0:Math.min(music.durationMs,music.positionMs+(music.playState==1?music.timeAgeMs:0));
        float prog=known?clamp(pos/(float)music.durationMs,0,1):0.35f;
        fill(c,0,52,800,480,0xff17181a);
        // door, shell, screws
        round(c,20,60,528,356,10,0xff060607);outline(c,20,60,528,356,10,2,0xff3a3b3e);
        round(c,40,76,508,340,12,0xff2a2c30);outline(c,40,76,508,340,12,1.5f,0xff4a4c52);
        for(float[] s:new float[][]{{52,88},{496,88},{52,328},{496,328},{274,328}}){dot(c,s[0],s[1],5,0xff55585e);line(c,s[0]-3,s[1],s[0]+3,s[1],1.5f,0xff2a2c30);}
        // label
        round(c,58,94,490,250,6,0xffefe6cf);fill(c,58,94,490,116,0xffd9542b);fill(c,58,116,490,122,0xfff0a050);
        bold(c,"A",74,111,18,0xffffffff,Paint.Align.LEFT);bold(c,"C-60 · TYPE II",480,111,12,0xffffffff,Paint.Align.RIGHT);
        String title=has?music.title:t("재생 중인 곡 없음"),artist=has&&music.artist!=null?music.artist:"";
        text(c,ellipsize(title,30,404),76,154,30,INK,Paint.Align.LEFT,Typeface.DEFAULT_BOLD);
        String sub=has?(artist.isEmpty()?"":ellipsize(artist,18,250)+(known?"  ·  ":""))+(known?mmss(pos)+" / "+mmss(music.durationMs):""):"";   // blank when nothing is playing
        text(c,sub,76,180,17,0xff3a4f9a,Paint.Align.LEFT,Typeface.DEFAULT);
        fill(c,76,188,480,189,0x401f3a8a);
        // reel window
        round(c,146,194,402,246,26,0xff0b0b0c);
        c.save();c.clipRect(146,194,402,246);
        reel(c,206,13+26*(1-prog),-rot*30/(13+26*(1-prog)));reel(c,342,13+26*prog,-rot*30/(13+26*prog));
        c.restore();
        outline(c,146,194,402,246,26,1.5f,0xff5a5c62);
        path.reset();path.moveTo(110,270);path.lineTo(438,270);path.lineTo(460,336);path.lineTo(88,336);path.close();poly(c,0xff232528);
        fill(c,100,324,448,327,0xff3a2516);
        for(float x:new float[]{140,226,300,386})round(c,x,312,x+22,330,3,0xff0b0b0c);
        // piano keys, PLAY down when music plays
        String[] keys={"●","◀◀","▶","▶▶","■","❚❚"};
        boolean playing=has&&music.playState==1;
        int pressed=!has||music.playState<0?-1:playing?2:5;   // unknown play state: no key down
        for(int i=0;i<6;i++){boolean down=i==pressed;float x=24+i*84,y=372+(down?5:0);
            round(c,x,y,x+78,y+72,6,down?0xff2c2e32:0xff3a3c41);outline(c,x,y,x+78,y+72,6,1,0xff55585e);fill(c,x+4,y+4,x+74,y+10,down?0xff202225:0xff4a4d52);
            bold(c,keys[i],x+39,y+46,20,i==0?0xffe0312f:i==2?0xff3ddc84:0xffdddddd,Paint.Align.CENTER);
            dot(c,x+39,364,3.5f,down?0xff3ddc84:0xff1d2a22);}
        // VU meters
        vu(c,540,62,needleL,t("L · 가속 페달"),fmt("%.0f",pedal)+" %");
        vu(c,540,188,needleR,t("R · 부스트"),fmt("%+.2f",boost)+String.format(Locale.US," / %.1f bar",top));
        // counter drums
        round(c,540,314,784,354,6,0xff0d0d0f);
        float odo=v(Telemetry.ODOMETER);String cnt=Float.isNaN(odo)?"----":String.format(Locale.US,"%04d",((int)(odo*10))%10000);
        for(int i=0;i<4;i++){float x=552+i*26;round(c,x,320,x+22,348,2,0xffe8e8e8);bold(c,cnt.substring(i,i+1),x+11,342,20,0xff111111,Paint.Align.CENTER);}
        bold(c,"COUNTER",664,332,11,0xff8a8c90,Paint.Align.LEFT);text(c,t("주행 0.1 km"),664,346,11,0xff8a8c90,Paint.Align.LEFT,Typeface.DEFAULT);
        // VFD
        round(c,540,364,784,472,6,0xff06100c);outline(c,540,364,784,472,6,1,0xff2b3a33);
        String sp=Float.isNaN(spd)?"---":String.valueOf(Math.round(spd));while(sp.length()<3)sp=" "+sp;
        seg7(c,sp,662,378,52,VF,VF_OFF,false);
        bold(c,"km/h",672,428,13,VF,Paint.Align.LEFT);
        bold(c,gear(),774,402,22,VF,Paint.Align.RIGHT);bold(c,fmt("%,.0f",rpm)+" rpm",774,428,14,VF,Paint.Align.RIGHT);
        bold(c,playing?"AUTO REVERSE · PLAY":"AUTO REVERSE · STOP",552,462,11,0xff3a9a78,Paint.Align.LEFT);
    }
    private void reel(Canvas c,float x,float rad,float a){
        dot(c,x,220,rad,0xff3a2516);ring(c,x,220,rad-1.5f,1,0xff4a3220);
        dot(c,x,220,12,0xffe8e8ea);dot(c,x,220,7,0xff0b0b0c);
        for(int k=0;k<6;k++){double b=a+k*Math.PI/3;line(c,x+(float)Math.sin(b)*5,220-(float)Math.cos(b)*5,x+(float)Math.sin(b)*9,220-(float)Math.cos(b)*9,3,0xffe8e8ea);}
    }
    private void vu(Canvas c,float x,float y,float n,String label,String sub){
        round(c,x,y,x+244,y+118,8,0xff0d0d0f);outline(c,x,y,x+244,y+118,8,1.5f,0xff3a3b3e);round(c,x+8,y+8,x+236,y+110,5,FACE);
        float cx=x+122,cy=y+128,rad=96;
        r.set(cx-rad,cy-rad,cx+rad,cy+rad);p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.5f);p.setColor(0xff222222);c.drawArc(r,-140,72*1.0f,false,p);
        p.setStrokeWidth(4);p.setColor(0xffc0261f);c.drawArc(r,-68,28,false,p);p.setStyle(Paint.Style.FILL);
        String[] l={"-20","-10","-7","-5","-3","0","+3"};float[] f={0,0.25f,0.38f,0.5f,0.62f,0.72f,1};
        for(int i=0;i<7;i++){float d=-50+f[i]*100;int col=f[i]>=0.72f?0xffc0261f:0xff222222;
            line(c,px(cx,rad,d),py(cy,rad,d),px(cx,rad+8,d),py(cy,rad+8,d),1.5f,col);
            float ly=py(cy,rad+18,d);if(ly>y+14)bold(c,l[i],px(cx,rad+18,d),ly+4,11,col,Paint.Align.CENTER);}
        text(c,"VU",cx,y+80,18,0xff3a3122,Paint.Align.CENTER,Typeface.DEFAULT_BOLD);
        bold(c,label,x+16,y+102,12,0xff3a3122,Paint.Align.LEFT);bold(c,sub,x+228,y+102,12,0xff3a3122,Paint.Align.RIGHT);
        c.save();c.clipRect(x+8,y+8,x+236,y+110);
        float d=-50+clamp(n,0,1.02f)*100;line(c,cx,cy,px(cx,rad+4,d),py(cy,rad+4,d),1.6f,0xff111111);
        c.restore();
        dot(c,x+226,y+18,4,n>0.72f?0xffff3a2a:0xff4a1a14);
    }
    private String ellipsize(String s,int max,float widthPx){
        String o=s.length()>max?s.substring(0,max)+"…":s;
        while(o.length()>2&&width(o,30,Typeface.DEFAULT_BOLD)>widthPx)o=o.substring(0,o.length()-2)+"…";
        return o;
    }
    private static String mmss(long ms){long s=ms/1000;return String.format(Locale.US,"%d:%02d",s/60,s%60);}
}
