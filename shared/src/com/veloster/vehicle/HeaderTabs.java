package com.veloster.vehicle;

/**
 * The header's tab strip between the logo and the clock: one list of buttons that PitRenderer draws and
 * VehicleDash hit-tests, scrolled sideways by a finger drag that starts on the strip. No Android: host-tested.
 */
public final class HeaderTabs {
    public static final int OVERVIEW=0,F1=1,PERF=2,RACE=3,GAUGES=4,GRAPHS=5,CLUSTER=6,LANG=7,THEME=8;
    public static final float LEFT=120,RIGHT=636,TOP=8,H=38,GAP=4,DRAG=12;
    public static final float[] W={76,40,66,56,56,56,66,40,40};

    private float scroll,startScroll,downX;
    private boolean tracking,dragging;

    public float scroll(){return scroll;}
    public float maxScroll(){float w=-GAP;for(float v:W)w+=v+GAP;return Math.max(0,w-(RIGHT-LEFT));}
    public void scrollTo(float v){scroll=Math.max(0,Math.min(maxScroll(),v));}
    public boolean moreLeft(){return scroll>0.5f;}
    public boolean moreRight(){return scroll<maxScroll()-0.5f;}

    /** Screen x of a tab's left edge, scroll applied (may lie outside the strip; callers clip). */
    public float x(int id){float x=LEFT-scroll;for(int i=0;i<id;i++)x+=W[i]+GAP;return x;}

    /** Tab under the point, or -1; only the part of a tab visible inside the strip counts. */
    public int hit(float x,float y){
        if(!inStrip(x,y))return -1;
        for(int i=0;i<W.length;i++){float l=x(i);if(x>=l&&x<=l+W[i])return i;}
        return -1;
    }
    private static boolean inStrip(float x,float y){return x>=LEFT&&x<=RIGHT&&y>=TOP&&y<=TOP+H;}

    public void down(float x,float y){tracking=inStrip(x,y);dragging=false;downX=x;startScroll=scroll;}
    /** True while this gesture is scrolling the strip; it becomes a drag once it moves DRAG sideways. */
    public boolean move(float x,float y){
        if(!tracking)return false;
        if(!dragging&&Math.abs(x-downX)>=DRAG)dragging=true;
        if(dragging)scrollTo(startScroll-(x-downX));
        return dragging;
    }
    /** Whether the gesture that just ended was a drag (then it is not a tap). */
    public boolean up(){boolean was=dragging;tracking=dragging=false;return was;}
}
