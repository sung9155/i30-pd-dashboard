package android.graphics;

/** Preview-only subset of android.graphics.Typeface. */
public class Typeface {
    final boolean bold,mono;final java.awt.Font file;
    private Typeface(boolean bold,boolean mono){this(bold,mono,null);}
    private Typeface(boolean bold,boolean mono,java.awt.Font f){this.bold=bold;this.mono=mono;file=f;}
    public static Typeface createFromFile(java.io.File f){try{return new Typeface(false,false,java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT,f));}catch(Exception e){throw new RuntimeException(e);}}
    public static Typeface createFromFile(String path){return createFromFile(new java.io.File(path));}
    public static final Typeface DEFAULT=new Typeface(false,false),DEFAULT_BOLD=new Typeface(true,false),MONOSPACE=new Typeface(false,true);
}
