package com.veloster.vehicle;

/**
 * The header's tab strip between the logo and the clock: every shown screen is a button that PitRenderer draws and
 * VehicleDash hit-tests. PitRenderer measures the labels and feeds the shown screens and their widths each frame
 * (setTabs); a finger drag that starts on the strip scrolls it sideways when they overflow, and the strip keeps the
 * current tab in view. No Android: host-tested.
 */
public final class HeaderTabs {
    public static final float LEFT=120,RIGHT=580,TOP=8,H=38,GAP=4,DRAG=12;
    /** Gear button left of the clock: opens the screen list in edit mode. */
    public static final float GEAR_L=590,GEAR_W=44;
    public static boolean gear(float x,float y){return x>=GEAR_L&&x<=GEAR_L+GEAR_W&&y>=TOP&&y<=TOP+H;}

    private int[] ids=new int[0];     // ClusterPicker screen index per tab, left to right
    private float[] w=new float[0];   // pixel width per tab
    private int current=-1;           // tab index of the current screen, or -1
    private float scroll,startScroll,downX;
    private boolean tracking,dragging;

    /** PitRenderer sets the shown screens, their widths and which one is current, each frame. */
    public void setTabs(int[] screenIds,float[] widths,int currentIndex){
        ids=screenIds;w=widths;current=currentIndex;
        scrollTo(scroll);                      // clamp to the new content
        if(current>=0&&current<ids.length){    // keep the current tab fully in view
            float l=xRel(current),r=l+w[current];
            if(l<0)scrollTo(scroll+l);else if(r>RIGHT-LEFT)scrollTo(scroll+r-(RIGHT-LEFT));
        }
    }
    public int count(){return ids.length;}
    public int screenAt(int i){return i>=0&&i<ids.length?ids[i]:-1;}
    public float width(int i){return i>=0&&i<w.length?w[i]:0;}
    public int current(){return current;}

    private float xRel(int i){float x=-scroll;for(int k=0;k<i&&k<w.length;k++)x+=w[k]+GAP;return x;}
    /** Screen x of a tab's left edge, scroll applied (may lie outside the strip; callers clip). */
    public float x(int i){return LEFT+xRel(i);}
    public float scroll(){return scroll;}
    public float maxScroll(){float t=-GAP;for(float v:w)t+=v+GAP;return Math.max(0,t-(RIGHT-LEFT));}
    public void scrollTo(float v){scroll=Math.max(0,Math.min(maxScroll(),v));}
    public boolean moreLeft(){return scroll>0.5f;}
    public boolean moreRight(){return scroll<maxScroll()-0.5f;}

    /** Tab under the point, or -1; only the part of a tab visible inside the strip counts. */
    public int hit(float x,float y){
        if(!inStrip(x,y))return -1;
        for(int i=0;i<ids.length;i++){float l=x(i);if(x>=l&&x<=l+w[i])return i;}
        return -1;
    }
    public boolean onStrip(float x,float y){return inStrip(x,y);}
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
