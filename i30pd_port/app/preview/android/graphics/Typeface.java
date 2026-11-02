package android.graphics;

/** Preview-only subset of android.graphics.Typeface. */
public class Typeface {
    final boolean bold,mono;
    private Typeface(boolean bold,boolean mono){this.bold=bold;this.mono=mono;}
    public static final Typeface DEFAULT=new Typeface(false,false),DEFAULT_BOLD=new Typeface(true,false),MONOSPACE=new Typeface(false,true);
}
