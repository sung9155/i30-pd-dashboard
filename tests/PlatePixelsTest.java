import com.veloster.vehicle.ArcadeCarFrames;
import com.veloster.vehicle.PlatePixels;

/** The plates: wide (3D) and narrow (pixel cars) apart, "i30 PD" by default, the narrow one only what the font draws and
 *  fits, and an old Korean plate migrated to its number. */
public final class PlatePixelsTest {
    private static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
    public static void main(String[] args){
        // defaults
        check(PlatePixels.DEFAULT.equals("i30 PD")&&PlatePixels.shown("").equals("i30 PD")&&PlatePixels.shown(null).equals("i30 PD")&&PlatePixels.shown("  ").equals("i30 PD"),"blank shows i30 PD");
        check(PlatePixels.shown(PlatePixels.wide).equals("i30 PD")&&PlatePixels.shown(PlatePixels.narrow).equals("i30 PD"),"nothing set: both plates i30 PD");
        check(PlatePixels.width("i30 PD")<=PlatePixels.COLS&&PlatePixels.filter("i30 PD").equals("i30 PD"),"i30 PD fits the narrow plate as it is");
        // narrow: letters, digits, space and '-'; nothing else; no wider than the plate
        check(PlatePixels.filter("  n-1  a ").equals("n-1 a"),"letters, digits, dash, one space: "+PlatePixels.filter("  n-1  a "));
        check(PlatePixels.filter("12가 34").equals("12 34"),"Hangul dropped");
        check(PlatePixels.filter("I30N_!").equals("I30N"),"symbols dropped");
        check(PlatePixels.filter("1234567890").equals("12345"),"too long: cut where the plate ends");
        check(PlatePixels.width(PlatePixels.filter("ABCDEFGHIJ"))<=PlatePixels.COLS,"never wider than the plate");
        check(PlatePixels.width("12345")==19&&PlatePixels.width("i i")==5&&PlatePixels.width("N")==4&&PlatePixels.width("MW")==11&&PlatePixels.width("N-123")==PlatePixels.COLS,"glyph widths");
        for(char c:"0123456789-ABCDEFGHIJKLMNOPQRSTUVWXYZabcxyz ".toCharArray())check(PlatePixels.drawable(c),"drawable "+c);
        check(!PlatePixels.drawable('가')&&!PlatePixels.drawable('_')&&!PlatePixels.drawable('é'),"not drawable");
        check(PlatePixels.lit('8',0,0)&&!PlatePixels.lit('8',1,1)&&PlatePixels.lit('i',0,0)&&!PlatePixels.lit('i',0,1)&&PlatePixels.lit('I',1,1)&&!PlatePixels.lit('I',0,1)&&!PlatePixels.lit(' ',0,0),"glyphs");
        for(int y=0;y<5;y++)for(int x=0;x<3;x++)check(PlatePixels.lit('p',x,y)==PlatePixels.lit('P',x,y),"lower case drawn as upper");
        // migration of the one plate value saved before the split
        check(PlatePixels.migrate("12가 3456").equals("3456")&&PlatePixels.migrate("123허 4567").equals("4567"),"Korean plate: its number");
        check(PlatePixels.migrate("").equals("i30 PD")&&PlatePixels.migrate(null).equals("i30 PD")&&PlatePixels.migrate("서울").equals("i30 PD"),"nothing drawable: i30 PD");
        check(PlatePixels.migrate(" N 123 ").equals("N 123")&&PlatePixels.migrate("N 1234").equals("1234"),"drawable: kept if it fits, else its number");
        check(PlatePixels.migrate("I30N").equals("I30N")&&PlatePixels.migrate("1234").equals("1234"),"drawable values reused");
        check(PlatePixels.migrate("1234567890").equals("67890"),"too wide: the last digits that fit");
        // wide and narrow are separate values
        PlatePixels.wide="12가 3456";PlatePixels.narrow="I30N";
        check(PlatePixels.shown(PlatePixels.wide).equals("12가 3456")&&PlatePixels.shown(PlatePixels.narrow).equals("I30N"),"independent");
        PlatePixels.wide="";check(PlatePixels.shown(PlatePixels.narrow).equals("I30N"),"clearing the wide one leaves the narrow one");
        PlatePixels.narrow="";
        // every frame's plate (codes 20, 21) is wide enough for COLS on both screens, at 2 screen px a font pixel
        for(int f=0;f<ArcadeCarFrames.FRAMES.length;f++){int x0=99,x1=-1;
            for(int y=0;y<ArcadeCarFrames.H;y++)for(int x=0;x<ArcadeCarFrames.W;x++){int k=ArcadeCarFrames.code(f,x,y);if(k==20||k==21){x0=Math.min(x0,x);x1=Math.max(x1,x);}}
            int w=x1-x0+1;
            check((w*4-12)/2>=PlatePixels.COLS,"arcade plate wide enough, frame "+f);
            check((w*3-2)/2>=PlatePixels.COLS,"neon plate wide enough, frame "+f);}
        System.out.println("PASS plate pixel checks");
    }
}
