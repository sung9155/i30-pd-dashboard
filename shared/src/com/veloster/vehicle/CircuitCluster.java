package com.veloster.vehicle;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import java.util.Locale;

/**
 * Circuit: at a known circuit (Circuits, from OpenStreetMap) the track is a minimap, north up, with the car on it from
 * the first fix and the start / finish line drawn; laps count by themselves from the first crossing (CircuitLap).
 * Elsewhere 출발선 지정 drops a line where the car is and the map is the car's own path. Every fix is taken in tick,
 * so timing goes on while another page is up. Laps live until 초기화 or the app closes (not saved).
 */
final class CircuitCluster extends ClusterKit {
    private static final int TX=0xffe6f1f6,MU=0xff91a5ae,LN=0xff1d2326,TRACK=0xff3a4246,ACC=0xffa4d9ef,PANEL=0xff101315,EDGE=0xff303638,GOOD=0xff2fe06a,BAD=0xffff4059;
    private static final float ML=14,MT=60,MR=470,MB=470;
    final CircuitLap lap=new CircuitLap();
    private int seen=-1;
    /** The circuit the car is at (-1: none), its track in the lap's metres, and the line's track direction. */
    private int circuit=-1;
    private float[] track;
    private float tx,ty;
    /** The last fix's time on the fix clock and when it was taken, to run the lap clock between fixes. */
    private long fixT,fixNano;
    { themed=true; }

    @Override protected void tick(){
        int q=GpsFix.seq;
        if(q!=seen&&GpsFix.any()){seen=q;feed(GpsFix.lat,GpsFix.lon,GpsFix.at);}
    }
    void feed(double lat,double lon,long t){
        int c=Circuits.pick(lat,lon,circuit);
        if(c!=circuit){circuit=c;atCircuit();}
        lap.fix(lat,lon,t);fixT=t;fixNano=System.nanoTime()/1000000;
    }
    /** A new circuit (or none): the lap starts over, on that circuit's line. */
    private void atCircuit(){
        lap.reset();track=null;
        if(circuit<0)return;
        double[] s=Circuits.start(circuit);lap.origin(s[0],s[1]);
        track=Circuits.outline(circuit,s[0],s[1]);
        tx=track[2]-track[0];ty=track[3]-track[1];   // the first stretch from the line: along the track
        lap.knownLine(0,0,tx,ty);
    }
    private long current(){return lap.state()==CircuitLap.TIMING?fixT+(System.nanoTime()/1000000-fixNano)-lap.startAt:-1;}

    @Override protected void draw(Canvas c){
        fill(c,0,52,800,480,0xff040506);
        round(c,ML,MT,MR,MB,10,PANEL);outline(c,ML,MT,MR,MB,10,1.5f,EDGE);
        if(circuit>=0){
            bold(c,t(Circuits.name(circuit)),ML+14,MT+24,15,TX,Paint.Align.LEFT);
            text(c,String.format(Locale.US,"%.2f km",Circuits.lengthM(circuit)/1000f),MR-14,MT+24,13,MU,Paint.Align.RIGHT,Typeface.DEFAULT);
            if(Circuits.lineEstimated(circuit))text(c,t("출발선 위치는 추정"),ML+14,MT+44,12,MU,Paint.Align.LEFT,Typeface.DEFAULT);
        }else bold(c,"CIRCUIT",ML+14,MT+24,13,MU,Paint.Align.LEFT);
        map(c);
        int st=lap.state();
        if(!GpsFix.any())hint(c,t("GPS 신호를 기다리는 중"));
        else if(st==CircuitLap.WAIT)hint(c,t("출발선을 지나면 1랩이 시작됩니다"));
        else if(st==CircuitLap.ARMED)hint(c,t("출발하면 1랩이 시작됩니다"));
        else if(circuit<0&&st==CircuitLap.NONE){
            double[] n=Circuits.nearest(GpsFix.lat,GpsFix.lon);
            hint(c,String.format(Locale.US,t("등록된 서킷 밖 · 가장 가까운 %s %.0f km"),t(Circuits.name((int)n[0])),n[1]));
        }

        float l=486,r=786;
        bold(c,st==CircuitLap.TIMING?"LAP "+(lap.laps+1):"LAP —",l,86,16,ACC,Paint.Align.LEFT);
        text(c,t("GPS 자동 랩"),r,86,12,MU,Paint.Align.RIGHT,Typeface.DEFAULT);
        bold(c,LapTimer.format(current()),l,148,46,st==CircuitLap.TIMING?TX:MU,Paint.Align.LEFT);
        long d=lap.last<0||lap.bestBefore<0?Long.MIN_VALUE:lap.last-lap.bestBefore;
        String[] k={"LAST","BEST","Δ BEST"};
        String[] val={LapTimer.format(lap.last),LapTimer.format(lap.best),d==Long.MIN_VALUE?"—":LapTimer.formatDelta(d)};
        for(int i=0;i<3;i++){float y=196+i*36;line(c,l,y-26,r,y-26,1,LN);
            text(c,k[i],l,y,13,MU,Paint.Align.LEFT,Typeface.DEFAULT_BOLD);
            bold(c,val[i],r,y,24,i==2&&d!=Long.MIN_VALUE?(d<0?GOOD:BAD):TX,Paint.Align.RIGHT);}
        line(c,l,280,r,280,1,LN);
        bold(c,fmt("%.0f",v(7)),l,334,40,TX,Paint.Align.LEFT);
        text(c,"km/h",l+96,334,13,MU,Paint.Align.LEFT,Typeface.DEFAULT);
        String gps=!GpsFix.any()?t("GPS 없음"):!GpsFix.fresh()?t("GPS 끊김"):Float.isNaN(GpsFix.accuracy)?"GPS":String.format(Locale.US,"GPS ±%.0f m",GpsFix.accuracy);
        text(c,gps,r,334,13,GpsFix.fresh()?MU:BAD,Paint.Align.RIGHT,Typeface.DEFAULT);
        text(c,lap.laps>0?String.format(Locale.US,t("%d랩 완료"),lap.laps):"",r,362,13,MU,Paint.Align.RIGHT,Typeface.DEFAULT);
        key(c,l,388,632,456,t("출발선 지정"),20,GpsFix.any()?KEY_GREEN:KEY_GREY,0);
        key(c,642,388,r,456,t("초기화"),20,KEY_SLATE,0);
    }
    private void hint(Canvas c,String s){
        float size=15;while(size>11&&width(s,size,Typeface.DEFAULT_BOLD)>MR-ML-24)size--;
        bold(c,s,(ML+MR)/2,MB-16,size,MU,Paint.Align.CENTER);
    }
    /**
     * The circuit's track (fixed view) or, off any circuit, the last lap as the track; the lap so far over it, the line
     * and the car. Fitted to the panel, 300 m across at least.
     */
    private void map(Canvas c){
        float[] a=track!=null?track:lap.lastPath,b=lap.curPath();int an=a==null?0:a.length/2,bn=lap.curPoints();
        double cx=lap.x(),cy=lap.y();
        if(an==0&&Double.isNaN(cx))return;
        double x0=Double.MAX_VALUE,x1=-Double.MAX_VALUE,y0=Double.MAX_VALUE,y1=-Double.MAX_VALUE;
        for(int i=0;i<an;i++){x0=Math.min(x0,a[i*2]);x1=Math.max(x1,a[i*2]);y0=Math.min(y0,a[i*2+1]);y1=Math.max(y1,a[i*2+1]);}
        if(track==null){   // own path: the view follows the car and the lap so far
            if(!Double.isNaN(cx)){x0=Math.min(x0,cx);x1=Math.max(x1,cx);y0=Math.min(y0,cy);y1=Math.max(y1,cy);}
            for(int i=0;i<bn;i++){x0=Math.min(x0,b[i*2]);x1=Math.max(x1,b[i*2]);y0=Math.min(y0,b[i*2+1]);y1=Math.max(y1,b[i*2+1]);}
        }
        double mx=(x0+x1)/2,my=(y0+y1)/2;
        float w=MR-ML-48,h=MB-MT-96,k=(float)Math.min(Math.min(w/Math.max(1,x1-x0),h/Math.max(1,y1-y0)),Math.min(w,h)/300),ox=(ML+MR)/2,oy=(MT+36+MB-36)/2;
        for(int i=0;i<an;i++){   // the track: thick, joints rounded with a dot, closed when it is a circuit
            int j=(i+1)%an;if(j==0&&track==null)break;
            float xa=ox+(float)(a[i*2]-mx)*k,ya=oy-(float)(a[i*2+1]-my)*k;
            line(c,xa,ya,ox+(float)(a[j*2]-mx)*k,oy-(float)(a[j*2+1]-my)*k,10,TRACK);dot(c,xa,ya,5,TRACK);
        }
        for(int i=1;i<bn;i++)line(c,ox+(float)(b[i*2-2]-mx)*k,oy-(float)(b[i*2-1]-my)*k,ox+(float)(b[i*2]-mx)*k,oy-(float)(b[i*2+1]-my)*k,3,ACC);
        int st=lap.state();
        if(st==CircuitLap.TIMING||st==CircuitLap.WAIT){   // the line across the track, 30 m each side (at least 14 px), chequered
            float lx=ox+(float)(lap.lineX()-mx)*k,ly=oy-(float)(lap.lineY()-my)*k,half=Math.max(14,(float)CircuitLap.HALF_M*k);
            float nx=(float)-lap.dirY(),ny=(float)lap.dirX();
            line(c,lx-nx*half,ly+ny*half,lx+nx*half,ly-ny*half,6,0xfff2f2f4);
            for(int s=-2;s<2;s+=2){float f0=s/2f,f1=(s+1)/2f;line(c,lx+nx*half*f0,ly-ny*half*f0,lx+nx*half*f1,ly-ny*half*f1,6,0xff111111);}
        }
        if(!Double.isNaN(cx)){
            float px=ox+(float)(cx-mx)*k,py=oy-(float)(cy-my)*k;
            px=clamp(px,ML+10,MR-10);py=clamp(py,MT+34,MB-30);   // off the map (paddock, the road in): pinned to its edge
            dot(c,px,py,10,0xff040506);dot(c,px,py,7,BAD);ring(c,px,py,10,2,TX);
        }
    }
    @Override boolean tap(float x,float y){
        if(y<388||y>456)return false;
        if(x>=486&&x<=632){lap.setLine();return true;}
        if(x>=642&&x<=786){   // back to the circuit's own line, or nothing off a circuit
            if(circuit>=0){double[] s=Circuits.start(circuit);lap.reset();lap.origin(s[0],s[1]);lap.knownLine(0,0,tx,ty);}
            else lap.reset();
            seen=GpsFix.seq-1;return true;   // the next tick takes the last fix again
        }
        return false;
    }
}
