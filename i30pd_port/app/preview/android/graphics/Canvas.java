package android.graphics;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayDeque;

/** Preview-only Java2D stand-in for the android.graphics.Canvas calls the dashboard renderers make. */
public class Canvas {
    private final Graphics2D g;
    private final ArrayDeque<Object[]> stack=new ArrayDeque<>();
    public Canvas(Graphics2D g){this.g=g;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,RenderingHints.VALUE_FRACTIONALMETRICS_ON);}
    private void apply(Paint p){
        if(p.shader instanceof LinearGradient){LinearGradient s=(LinearGradient)p.shader;
            g.setPaint(new java.awt.GradientPaint(s.x0,s.y0,new Color(s.c0,true),s.x1,s.y1,new Color(s.c1,true)));}
        else g.setColor(new Color(p.color,true));
        g.setStroke(new BasicStroke(Math.max(p.width,1),p.cap==Paint.Cap.ROUND?BasicStroke.CAP_ROUND:BasicStroke.CAP_BUTT,BasicStroke.JOIN_ROUND));
    }
    private void shape(Shape s,Paint p){apply(p);if(p.style==Paint.Style.STROKE)g.draw(s);else g.fill(s);}
    public int save(){stack.push(new Object[]{g.getTransform(),g.getClip()});return stack.size();}
    public void restore(){Object[] s=stack.pop();g.setTransform((AffineTransform)s[0]);g.setClip((Shape)s[1]);}
    public void restoreToCount(int n){while(stack.size()>=n)restore();}
    public void scale(float x,float y){g.scale(x,y);}
    public void translate(float x,float y){g.translate(x,y);}
    public boolean clipRect(float l,float t,float r,float b){g.clip(new Rectangle2D.Float(l,t,r-l,b-t));return true;}
    public void drawColor(int color){AffineTransform t=g.getTransform();g.setTransform(new AffineTransform());g.setColor(new Color(color,true));
        g.fillRect(0,0,10000,10000);g.setTransform(t);}
    public void drawRect(float l,float t,float r,float b,Paint p){shape(new Rectangle2D.Float(l,t,r-l,b-t),p);}
    public void drawRoundRect(RectF r,float rx,float ry,Paint p){shape(new RoundRectangle2D.Float(r.left,r.top,r.right-r.left,r.bottom-r.top,rx*2,ry*2),p);}
    public void drawCircle(float x,float y,float radius,Paint p){shape(new Ellipse2D.Float(x-radius,y-radius,radius*2,radius*2),p);}
    public void drawLine(float x,float y,float x2,float y2,Paint p){apply(p);g.draw(new Line2D.Float(x,y,x2,y2));}
    public void drawPath(Path path,Paint p){shape(path.shape,p);}
    // Android sweeps clockwise from 3 o'clock; Java2D counter-clockwise, hence the negated angles.
    public void drawArc(RectF r,float start,float sweep,boolean center,Paint p){
        shape(new Arc2D.Float(r.left,r.top,r.right-r.left,r.bottom-r.top,-start,-sweep,center?Arc2D.PIE:Arc2D.OPEN),p);}
    public void drawBitmap(Bitmap b,Object src,RectF dst,Paint p){}  // ponytail: no album art in previews
    // Like Android's font fallback: Roboto for the glyphs it has, the CJK base font for the rest (Hangul).
    public void drawText(String s,float x,float y,Paint p){
        apply(p);
        float w=measure(p,s),at=p.align==Paint.Align.CENTER?x-w/2:p.align==Paint.Align.RIGHT?x-w:x;
        for(int i=0,j;i<s.length();i=j){Font f=fontFor(p,s.charAt(i));
            for(j=i+1;j<s.length()&&fontFor(p,s.charAt(j)).equals(f);j++);
            g.setFont(f);g.drawString(s.substring(i,j),at,y);at+=width(f,s.substring(i,j));}
    }
    static float measure(Paint p,String s){
        float w=0;
        for(int i=0,j;i<s.length();i=j){Font f=fontFor(p,s.charAt(i));
            for(j=i+1;j<s.length()&&fontFor(p,s.charAt(j)).equals(f);j++);
            w+=width(f,s.substring(i,j));}
        return w;
    }
    private static final java.awt.font.FontRenderContext FRC=new java.awt.font.FontRenderContext(null,true,true);
    private static float width(Font f,String s){return (float)f.getStringBounds(s,FRC).getWidth();}
    private static Font fontFor(Paint p,char ch){Font r=p.latin();return r!=null&&r.canDisplay(ch)?r:p.font();}
    static final Font[] ROBOTO=roboto();
    private static Font[] roboto(){
        String dir=System.getProperty("preview.fonts","");
        try{return new Font[]{Font.createFont(Font.TRUETYPE_FONT,new java.io.File(dir,"Roboto-Regular.ttf")),
            Font.createFont(Font.TRUETYPE_FONT,new java.io.File(dir,"Roboto-Bold.ttf"))};}
        catch(Exception e){return null;}
    }
    static Font base(){
        for(String name:new String[]{"Malgun Gothic","Noto Sans CJK KR","NanumGothic"})
            if(new Font(name,Font.PLAIN,12).getFamily().equals(name))return new Font(name,Font.PLAIN,1);
        return new Font(Font.SANS_SERIF,Font.PLAIN,1);
    }
}
