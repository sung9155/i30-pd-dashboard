package android.graphics;

/** Preview-only subset of android.graphics.Path. */
public class Path {
    final java.awt.geom.Path2D.Float shape=new java.awt.geom.Path2D.Float();
    public void reset(){shape.reset();}
    public void moveTo(float x,float y){shape.moveTo(x,y);}
    public void lineTo(float x,float y){shape.lineTo(x,y);}
    public void close(){shape.closePath();}
    public void quadTo(float x1,float y1,float x2,float y2){shape.quadTo(x1,y1,x2,y2);}
}
