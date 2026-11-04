package com.veloster.vehicle;

/**
 * GPS lap timing on a circuit. A known circuit's line (knownLine) waits for the first crossing, either way round, which
 * starts lap 1 and fixes the direction. 출발선 지정 drops the line at the last fix; the first fix at least ARM_M away gives its
 * direction, and lap 1 runs from the anchor fix. After that a lap ends whenever the segment between two fixes crosses
 * the line (HALF_M either side) going the same way, at least MIN_LAP_MS after the last one; the crossing time is
 * interpolated along the segment, so a 1 Hz receiver still gives about a tenth of a second. The finished lap's path
 * is kept for the map. Positions are metres east / north of the line, from the first fix after a reset. No Android:
 * host-tested.
 */
public final class CircuitLap {
    static final double HALF_M=30,ARM_M=5;
    static final long MIN_LAP_MS=20000;
    static final int MAX_POINTS=3000;   // ponytail: about 50 min at 1 Hz in one lap; longer laps drop the map, not the timing
    /** No line, waiting for the car to move off the anchor, timing, or waiting to cross a known line. */
    public static final int NONE=0,ARMED=1,TIMING=2,WAIT=3;
    private int state=NONE;
    /** Local frame: origin and metres per degree of longitude there. */
    private double lat0=Double.NaN,lon0,kx;
    /** The line: a point and the unit direction the car crosses it. */
    private double ax,ay,hx,hy;
    private long anchorT;
    /** The previous fix in metres, and its time. */
    private double px=Double.NaN,py;
    private long pt;
    public int laps;
    /** Last and best lap (ms), -1 before one; the current lap's start (fix clock, ms). */
    public long last=-1,best=-1,startAt;
    /** The best lap before the last one finished, for the delta (-1: none). */
    public long bestBefore=-1;
    private float[] cur=new float[256];
    private int curN;
    /** The last finished lap's path, x then y in metres (null before one). */
    public float[] lastPath;

    public int state(){return state;}
    public void reset(){state=NONE;lat0=Double.NaN;px=Double.NaN;laps=0;last=-1;best=-1;bestBefore=-1;curN=0;lastPath=null;}
    /** Drop the line at the last fix (false: no fix yet). Laps so far are forgotten. */
    public boolean setLine(){
        if(Double.isNaN(px))return false;
        ax=px;ay=py;anchorT=pt;state=ARMED;laps=0;last=-1;best=-1;bestBefore=-1;lastPath=null;curN=0;add(px,py);
        return true;
    }
    /** Put the local frame's origin here (a known circuit's line), before any fix. */
    public void origin(double lat,double lon){lat0=lat;lon0=lon;kx=111320*Math.cos(Math.toRadians(lat));}
    /** A known line through (x, y) metres, across the track direction (tx, ty) in either sense. */
    public void knownLine(double x,double y,double tx,double ty){
        double n=Math.hypot(tx,ty);ax=x;ay=y;hx=tx/n;hy=ty/n;state=WAIT;laps=0;last=-1;best=-1;bestBefore=-1;lastPath=null;curN=0;
    }
    /** A GPS fix: degrees and the fix time in ms (any steady clock). */
    public void fix(double lat,double lon,long tMs){
        if(Double.isNaN(lat0)){lat0=lat;lon0=lon;kx=111320*Math.cos(Math.toRadians(lat));}
        double x=(lon-lon0)*kx,y=(lat-lat0)*110540;
        if(state==ARMED){
            double dx=x-ax,dy=y-ay,d=Math.hypot(dx,dy);
            if(d>=ARM_M){hx=dx/d;hy=dy/d;state=TIMING;startAt=anchorT;}
        }else if(state==WAIT&&!Double.isNaN(px)){
            double sp=(px-ax)*hx+(py-ay)*hy,sc=(x-ax)*hx+(y-ay)*hy;
            if((sp<0)!=(sc<0)){
                double f=-sp/(sc-sp),ix=px+f*(x-px),iy=py+f*(y-py);
                if(Math.abs((ix-ax)*-hy+(iy-ay)*hx)<=HALF_M){
                    if(sc<sp){hx=-hx;hy=-hy;}   // moving against the guessed direction (sp may be exactly 0)
                    state=TIMING;startAt=pt+Math.round(f*(tMs-pt));curN=0;add(ix,iy);
                }
            }
        }else if(state==TIMING&&!Double.isNaN(px)){
            double sp=(px-ax)*hx+(py-ay)*hy,sc=(x-ax)*hx+(y-ay)*hy;
            if(sp<0&&sc>=0){
                double f=-sp/(sc-sp),ix=px+f*(x-px),iy=py+f*(y-py);
                double side=(ix-ax)*-hy+(iy-ay)*hx;
                long t=pt+Math.round(f*(tMs-pt));
                if(Math.abs(side)<=HALF_M&&t-startAt>=MIN_LAP_MS){
                    long lap=t-startAt;last=lap;bestBefore=best;best=best<0?lap:Math.min(best,lap);laps++;startAt=t;
                    add(ix,iy);lastPath=java.util.Arrays.copyOf(cur,curN);curN=0;add(ix,iy);
                }
            }
        }
        if(state!=NONE)add(x,y);
        px=x;py=y;pt=tMs;
    }
    private void add(double x,double y){
        if(curN+2>MAX_POINTS*2)return;
        if(curN+2>cur.length)cur=java.util.Arrays.copyOf(cur,cur.length*2);
        cur[curN++]=(float)x;cur[curN++]=(float)y;
    }
    /** The current lap's path so far (x, y pairs; count = curPoints()). */
    public float[] curPath(){return cur;}
    public int curPoints(){return curN/2;}
    /** Where the car is and the line, in metres (NaN before a fix). */
    public double x(){return px;}
    public double y(){return py;}
    public double lineX(){return ax;}
    public double lineY(){return ay;}
    /** The line's crossing direction (unit). */
    public double dirX(){return hx;}
    public double dirY(){return hy;}
}
