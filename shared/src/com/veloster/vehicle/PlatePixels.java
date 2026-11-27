package com.veloster.vehicle;

/**
 * The owner's plate text (내 차 → 번호판, pit_vehicle_ui "car3d_plate"; VehicleDash loads it, the 번호판 dialog updates it)
 * for the pixel cars' rear plates (아케이드, 네온 드라이브), in a 3 x 5 digit font: a pixel plate is only 20-ish font columns
 * wide, so "12가 3456" shows its number "3456". No Android: host-tested.
 */
public final class PlatePixels {
    public static volatile String text="";
    /** Font columns each plate fits, one font pixel = 2 screen px: 아케이드 (4 px a car pixel, less the rim and blue band of the
     *  narrowest frame's 14-pixel plate) and 네온 드라이브 (3 px a car pixel, a pixel of margin a side). */
    public static final int ARCADE=20,NEON=19;
    /** 0-9 then '-', 15 bits each (rows of 3, left column first). ponytail: digits only, Korean plates have no Latin letters. */
    private static final String CHARS="0123456789-";
    private static final String[] GLYPH={"111101101101111","010110010010111","111001111100111","111001011001111","101101111001001",
        "111100111001111","111100111101111","111001001010010","111101111101111","111101111001111","000000111000000"};
    private PlatePixels(){}

    /** Font columns of s: 3 per character (space included) and 1 between. */
    public static int width(String s){return s.isEmpty()?0:s.length()*4-1;}
    /** Whether column x (0..2), row y (0..4) of ch is lit; a space or anything else is blank. */
    public static boolean lit(char ch,int x,int y){int i=CHARS.indexOf(ch);return i>=0&&GLYPH[i].charAt(y*3+x)=='1';}

    /** What a plate cols font columns wide shows for s: all of it when it is digits, '-' and spaces and fits, else its last
     *  run of digits (the last cols+1 / 4 of them if even that is too wide); "" when there is none (a blank plate). */
    public static String fit(String s,int cols){
        String t=s==null?"":s.trim().replaceAll("\\s+"," ");
        if(t.matches("[0-9 -]*")&&width(t)<=cols)return t;
        int end=t.length();while(end>0&&!digit(t.charAt(end-1)))end--;
        int start=end;while(start>0&&digit(t.charAt(start-1)))start--;
        return t.substring(Math.max(start,end-(cols+1)/4),end);
    }
    private static boolean digit(char c){return c>='0'&&c<='9';}
}
