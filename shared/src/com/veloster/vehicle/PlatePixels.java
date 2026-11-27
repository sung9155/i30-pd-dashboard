package com.veloster.vehicle;

/**
 * The owner's two plate texts (pit_vehicle_ui; VehicleDash loads them, plateDialog sets them): 넓은 번호판 (wide, WIDE_KEY)
 * for the 3D 내 차 plates, free text like "12가 3456"; 좁은 번호판 (narrow, NARROW_KEY) for the pixel cars' rear plates
 * (아케이드, 네온 드라이브), only what the 3 x 5 font here draws and only as wide as the narrowest pixel plate. Blank =
 * DEFAULT. A narrow value from before the split comes from the wide one (migrate). No Android: host-tested.
 */
public final class PlatePixels {
    public static final String DEFAULT="i30 PD",WIDE_KEY="car3d_plate",NARROW_KEY="plate_narrow";
    /** Narrow plate width in font columns at 2 screen px a font pixel: the narrowest frame's plate (14 car pixels) on
     *  네온 드라이브 (3 px a pixel, a px of margin a side) gives 20; 아케이드 (4 px, less rim and blue band) has 22. */
    public static final int COLS=20;
    /** 넓은 번호판: characters at most (car3d.CarPlate.MAX). */
    public static final int WIDE_MAX=10;
    public static volatile String wide="",narrow="";
    /** Glyphs: 5 rows of 1 to 5 columns (width = length / 5), left first; lower case is drawn as upper case, except a dotted i. */
    private static final String CHARS="0123456789-ABCDEFGHIJKLMNOPQRSTUVWXYZi ";
    private static final String[] GLYPH={"111101101101111","010110010010111","111001111100111","111001011001111","101101111001001",
        "111100111001111","111100111101111","111001001010010","111101111101111","111101111001111","000000111000000",
        "010101111101101","110101110101110","011100100100011","110101101101110","111100110100111","111100110100100",
        "011100101101011","101101111101101","111010010010111","001001001101010","101101110101101","100100100100111",
        "1000111011101011000110001","10011101101110011001","010101101101010","110101110100100","010101101110011","110101110101101",
        "011100010001110","111010010010010","101101101101111","101101101101010","1000110001101011101110001","101101010101101",
        "101101010010010","111001010100111","10111","00000"};
    private PlatePixels(){}

    /** The text a plate shows: trimmed, blank = DEFAULT. */
    public static String shown(String s){String t=s==null?"":s.trim();return t.isEmpty()?DEFAULT:t;}
    private static int index(char c){int i=CHARS.indexOf(c);return i>=0||c>127?i:CHARS.indexOf(Character.toUpperCase(c));}
    public static boolean drawable(char c){return index(c)>=0;}
    /** Font columns of s: each glyph's width (1 for i and space, 4 for N, 5 for M and W, else 3) and 1 between. */
    public static int width(String s){int w=0;for(int i=0;i<s.length();i++){int g=index(s.charAt(i));w+=(g<0?3:GLYPH[g].length()/5)+(i>0?1:0);}return w;}
    /** Whether column x, row y (0..4) of ch is lit; anything the font lacks is blank. */
    public static boolean lit(char ch,int x,int y){int g=index(ch);if(g<0)return false;int w=GLYPH[g].length()/5;return x<w&&GLYPH[g].charAt(y*w+x)=='1';}

    /** s in the font, ink on transparent: ARGB rows of width(s), 5 rows (a renderer's cached plate bitmap). */
    public static int[] pixels(String s,int ink){
        int w=Math.max(1,width(s));int[] px=new int[w*5];
        for(int i=0,x0=0;i<s.length();i++){char c=s.charAt(i);int g=index(c),gw=g<0?3:GLYPH[g].length()/5;
            for(int y=0;y<5;y++)for(int x=0;x<gw;x++)if(lit(c,x,y))px[y*w+x0+x]=ink;
            x0+=gw+1;}
        return px;
    }

    /** What the 좁은 번호판 field keeps of s: drawable characters only, one space between words, no wider than COLS. */
    public static String filter(String s){
        StringBuilder b=new StringBuilder();
        for(int i=0;s!=null&&i<s.length();i++){char c=s.charAt(i);
            if(Character.isWhitespace(c)){if(b.length()>0&&b.charAt(b.length()-1)!=' ')b.append(' ');continue;}
            if(!drawable(c))continue;
            b.append(c);if(width(b.toString())>COLS){b.setLength(b.length()-1);break;}}
        return b.toString().trim();
    }

    /** The narrow value for a wide value saved before the split: itself when the font draws it all and it fits, else its
     *  last run of digits (a Korean plate "12가 3456" gives "3456", the last COLS+1 / 4 of them if longer), else DEFAULT. */
    public static String migrate(String old){
        String t=old==null?"":old.trim().replaceAll("\\s+"," ");
        if(t.isEmpty())return DEFAULT;
        if(filter(t).equals(t))return t;
        int end=t.length();while(end>0&&!digit(t.charAt(end-1)))end--;
        int start=end;while(start>0&&digit(t.charAt(start-1)))start--;
        return end==start?DEFAULT:t.substring(Math.max(start,end-(COLS+1)/4),end);
    }
    private static boolean digit(char c){return c>='0'&&c<='9';}
}
