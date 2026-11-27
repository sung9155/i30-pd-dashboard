import com.veloster.vehicle.ArcadeCarFrames;
import com.veloster.vehicle.PlatePixels;

/** The pixel cars' plate text: digits that fit show whole, a Korean plate shows its number, empty stays blank. */
public final class PlatePixelsTest {
    private static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
    public static void main(String[] args){
        check(PlatePixels.fit("12가 3456",PlatePixels.ARCADE).equals("3456"),"Korean plate: the number");
        check(PlatePixels.fit("123허 4567",PlatePixels.NEON).equals("4567"),"new 3-digit plate: the number");
        check(PlatePixels.fit("  1234 ",PlatePixels.NEON).equals("1234"),"digits only: all of it, trimmed");
        check(PlatePixels.fit("12-34",PlatePixels.ARCADE).equals("12-34"),"digits and a dash fit whole");
        check(PlatePixels.fit("1234567890",PlatePixels.ARCADE).equals("67890"),"too long: the last digits that fit");
        check(PlatePixels.fit("12가 3456",7).equals("56"),"tiny plate: the last two digits");
        check(PlatePixels.fit("",PlatePixels.ARCADE).isEmpty()&&PlatePixels.fit(null,PlatePixels.NEON).isEmpty(),"empty: blank");
        check(PlatePixels.fit("서울",PlatePixels.ARCADE).isEmpty(),"no digits: blank");
        for(String s:new String[]{"3456","12345"})check(PlatePixels.width(s)<=PlatePixels.ARCADE,"fits the arcade plate: "+s);
        check(PlatePixels.width("123456")>PlatePixels.ARCADE&&PlatePixels.width("12345")<=PlatePixels.NEON,"five digits at most");
        check(PlatePixels.lit('8',0,0)&&!PlatePixels.lit('8',1,1)&&!PlatePixels.lit(' ',1,0)&&!PlatePixels.lit('가',0,0),"glyphs");
        // Every frame's plate (codes 20, 21) is wide enough for the columns both screens assume, at 2 screen px a font pixel.
        for(int f=0;f<ArcadeCarFrames.FRAMES.length;f++){int x0=99,x1=-1;
            for(int y=0;y<ArcadeCarFrames.H;y++)for(int x=0;x<ArcadeCarFrames.W;x++){int k=ArcadeCarFrames.code(f,x,y);if(k==20||k==21){x0=Math.min(x0,x);x1=Math.max(x1,x);}}
            int w=x1-x0+1;
            check((w*4-12)/2>=PlatePixels.ARCADE,"arcade plate wide enough, frame "+f);
            check((w*3-2)/2>=PlatePixels.NEON,"neon plate wide enough, frame "+f);}
        System.out.println("PASS plate pixel checks");
    }
}
