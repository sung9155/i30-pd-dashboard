package com.veloster.vehicle;

/**
 * The header's tab strip between the logo and the clock: one list of buttons that PitRenderer draws and
 * VehicleDash hit-tests, scrolled sideways by a finger drag that starts on the strip. No Android: host-tested.
 */
public final class HeaderTabs {
    public static final int CLUSTER=0;
    public static final float LEFT=120,RIGHT=580,TOP=8,H=38,GAP=4,DRAG=12;
    public static final float[] W={200};
    /** Gear button left of the clock: opens the screen list in edit mode. */
    public static final float GEAR_L=590,GEAR_W=44;
    public static boolean gear(float x,float y){return x>=GEAR_L&&x<=GEAR_L+GEAR_W&&y>=TOP&&y<=TOP+H;}

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
