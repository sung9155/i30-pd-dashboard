package android.graphics;

/** Preview-only subset of android.graphics.Path. */
public class Path {
    final java.awt.geom.Path2D.Float shape=new java.awt.geom.Path2D.Float();
    public void reset(){shape.reset();}
    public void moveTo(float x,float y){shape.moveTo(x,y);}
    public void lineTo(float x,float y){shape.lineTo(x,y);}
    public void close(){shape.closePath();}
    public void arcTo(RectF o,float start,float sweep){shape.append(new java.awt.geom.Arc2D.Float(o.left,o.top,o.right-o.left,o.bottom-o.top,-start,-sweep,java.awt.geom.Arc2D.OPEN),true);}
    public void quadTo(float x1,float y1,float x2,float y2){shape.quadTo(x1,y1,x2,y2);}
}
