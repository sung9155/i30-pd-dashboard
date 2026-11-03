import com.veloster.vehicle.ArcadeCarFrames;

/** Arcade car frames: the nearest of 13 yaws (clamped), whole grids, every code points into the table. */
public final class ArcadeCarFramesTest {
    private static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
    public static void main(String[] args){
        check(ArcadeCarFrames.frameFor(0)==6,"straight ahead is the middle frame");
        check(ArcadeCarFrames.frameFor(1.9f)==6&&ArcadeCarFrames.frameFor(2.1f)==7,"rounds to the nearest 4 deg");
        check(ArcadeCarFrames.frameFor(16)==10&&ArcadeCarFrames.frameFor(-16)==2,"a 90 deg wheel turn (16 deg) picks its frame");
        check(ArcadeCarFrames.frameFor(40)==12&&ArcadeCarFrames.frameFor(-40)==0,"beyond 24 deg stays on the last frame");
        check(ArcadeCarFrames.FRAMES.length==13&&ArcadeCarFrames.YAWS.length==13,"13 frames");
        int n=ArcadeCarFrames.CODES.length;
        for(int i=0;i<13;i++){
            check(ArcadeCarFrames.FRAMES[i].length()==ArcadeCarFrames.W*ArcadeCarFrames.H,"frame "+i+" is a whole grid");
            int filled=0;
            for(int y=0;y<ArcadeCarFrames.H;y++)for(int x=0;x<ArcadeCarFrames.W;x++){int c=ArcadeCarFrames.code(i,x,y);
                check(c>=0&&c<=n,"frame "+i+" code in the table");if(c>0)filled++;}
            check(filled>1500,"frame "+i+" has a car in it");
        }
        for(int[] e:ArcadeCarFrames.CODES){
            check(e[0]>=1&&e[0]<=4,"known code kind");
            if(e[0]==1)check(e[1]>=0&&e[1]<=3,"paint shade 0..3");
            if(e[0]==3)check(e[1]>=0&&e[1]<=4,"lamp 0..4");
            if(e[0]==4)check((e[1]==1||e[1]==-1)&&e[2]>=1&&e[2]<=n&&ArcadeCarFrames.CODES[e[2]-1][0]!=4,"turn pixel: a side and a plain code under it");
        }
        boolean left=false,right=false;for(int[] e:ArcadeCarFrames.CODES)if(e[0]==4){if(e[1]<0)left=true;else right=true;}
        check(left&&right,"both sides have turn signals");
        System.out.println("PASS arcade car frame checks");
    }
}
