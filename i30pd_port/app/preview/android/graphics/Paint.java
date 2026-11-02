package android.graphics;

import java.awt.Font;

/** Preview-only subset of android.graphics.Paint. */
public class Paint {
    public enum Style{FILL,STROKE}
    public enum Cap{BUTT,ROUND}
    public enum Align{LEFT,CENTER,RIGHT}
    private static final Font BASE=Canvas.base();
    int color=0xff000000;float width,size=12;Style style=Style.FILL;Cap cap=Cap.BUTT;Align align=Align.LEFT;
    Shader shader;boolean bold,mono;float scaleX=1,skewX=0;
    int flags;
    public static final int FILTER_BITMAP_FLAG=2;
    public Paint(int flags){this.flags=flags;}
    public void setColor(int c){color=c;}
    public void setStyle(Style s){style=s;}
    public void setStrokeWidth(float w){width=w;}
    public void setStrokeCap(Cap c){cap=c;}
    public void setTextSize(float s){size=s;}
    public void setTextAlign(Align a){align=a;}
    public Shader setShader(Shader s){shader=s;return s;}
    public Typeface setTypeface(Typeface t){bold=t!=null&&t.bold;mono=t!=null&&t.mono;return t;}
    public void setTextScaleX(float v){scaleX=v;}
    public void setTextSkewX(float v){skewX=v;}
    public float measureText(String s){return Canvas.measure(this,s);}
    Font font(){return shaped(mono?new Font(Font.MONOSPACED,bold?Font.BOLD:Font.PLAIN,1).deriveFont(size):BASE.deriveFont(bold?Font.BOLD:Font.PLAIN,size));}
    /** Android's textScaleX / textSkewX as a glyph transform (negative skew leans right, as on Android). */
    private Font shaped(Font f){return scaleX==1&&skewX==0?f:f.deriveFont(new java.awt.geom.AffineTransform(scaleX,0,skewX,1,0,0));}
    /** Android 4.2 Roboto when -Dpreview.fonts points at it, else null (Hangul always falls back to font()). */
    Font latin(){return Canvas.ROBOTO==null||mono?null:shaped(Canvas.ROBOTO[bold?1:0].deriveFont(size));}
}
