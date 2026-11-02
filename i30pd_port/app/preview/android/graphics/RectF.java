package android.graphics;

/** Preview-only subset of android.graphics.RectF. */
public class RectF {
    public float left,top,right,bottom;
    public RectF(){}
    public RectF(float l,float t,float r,float b){set(l,t,r,b);}
    public void set(float l,float t,float r,float b){left=l;top=t;right=r;bottom=b;}
}
