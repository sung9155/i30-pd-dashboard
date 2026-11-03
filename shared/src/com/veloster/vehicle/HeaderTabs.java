package com.veloster.vehicle;

/**
 * The header's tab strip between the logo and the clock: every shown screen is a button that PitRenderer draws and
 * VehicleDash hit-tests. PitRenderer measures the labels and feeds the shown screens and their widths each frame
 * (setTabs); a finger drag that starts on the strip (or just below it) scrolls it sideways when they overflow and
 * coasts on after a quick flick (step). The strip brings the current tab into view only when the current screen
 * changes, so a drag can look past it. No Android: host-tested.
 */
public final class HeaderTabs {
    public static final float LEFT=120,RIGHT=580,TOP=8,H=38,GAP=4,DRAG=8,GRAB=52;   // GRAB: a drag may start down to the body's edge
    /** Gear button left of the clock: opens the screen list in edit mode. */
    public static final float GEAR_L=590,GEAR_W=44;
    public static boolean gear(float x,float y){return x>=GEAR_L&&x<=GEAR_L+GEAR_W&&y>=TOP&&y<=TOP+H;}

    private int[] ids=new int[0];     // ClusterPicker screen index per tab, left to right
    private float[] w=new float[0];   // pixel width per tab
    private int current=-1;           // tab index of the current screen, or -1
    private float scroll,startScroll,downX,lastX,speed,flingV;   // speed: finger px/ms; flingV: scroll px/ms while coasting
    private long lastT,flingT;
    private int snapped=-1;           // screen whose tab was last brought into view
    private boolean tracking,dragging;

    /** PitRenderer sets the shown screens, their widths and which one is current, each frame. */
    public void setTabs(int[] screenIds,float[] widths,int currentIndex){
        ids=screenIds;w=widths;current=currentIndex;
        scrollTo(scroll);                      // clamp to the new content
        int cur=current>=0&&current<ids.length?ids[current]:-1;
        if(cur>=0&&cur!=snapped){              // a new current screen: bring its tab fully into view, once
            snapped=cur;flingV=0;
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

    public void down(float x,float y){tracking=x>=LEFT&&x<=RIGHT&&y>=0&&y<=GRAB;dragging=false;downX=lastX=x;startScroll=scroll;speed=flingV=0;lastT=0;}
    public boolean move(float x,float y){return move(x,y,0);}
    /** True while this gesture is scrolling the strip; it becomes a drag once it moves DRAG sideways. t: event time, ms. */
    public boolean move(float x,float y,long t){
        if(!tracking)return false;
        if(!dragging&&Math.abs(x-downX)>=DRAG)dragging=true;
        if(dragging)scrollTo(startScroll-(x-downX));
        if(t>lastT&&lastT>0)speed=0.5f*speed+0.5f*(x-lastX)/(t-lastT);
        lastX=x;lastT=t;
        return dragging;
    }
    public boolean up(){return up(0);}
    /** Whether the gesture that just ended was a drag (then it is not a tap). A flick still moving at t coasts on. */
    public boolean up(long t){
        boolean was=dragging;
        if(was&&t>0&&t-lastT<80&&Math.abs(speed)>0.2f){flingV=-Math.max(-3,Math.min(3,speed));flingT=t;}
        tracking=dragging=false;return was;
    }
    /** Advance a flick's coast to time now (ms); true while it still moves. */
    public boolean step(long now){
        if(flingV==0)return false;
        long dt=Math.max(0,now-flingT);flingT=now;
        float before=scroll;scrollTo(scroll+flingV*dt);
        flingV*=(float)Math.pow(0.996,dt);   // ponytail: fixed friction, about 250 px per px/ms of flick
        if(Math.abs(flingV)<0.05f||(dt>0&&scroll==before))flingV=0;
        return flingV!=0;
    }
    public boolean flinging(){return flingV!=0;}
}
