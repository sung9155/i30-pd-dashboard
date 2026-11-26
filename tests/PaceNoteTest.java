import com.veloster.vehicle.PaceNote;

/** WRC screen's pace-note call: severity from the wheel, else from lateral G and speed. */
public final class PaceNoteTest {
    private static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
    public static void main(String[] args){
        float n=Float.NaN;
        check(PaceNote.of(5,0.5f,80)==0,"a few degrees of wheel is straight");
        check(PaceNote.of(20,n,80)==6&&PaceNote.of(-20,n,80)==-6,"a slight bend is a 6, + right, - left");
        check(PaceNote.of(100,n,60)==3&&PaceNote.of(-400,n,30)==-1,"more wheel, a tighter call");
        check(PaceNote.of(300,n,3)==0,"walking pace is no stage");
        check(PaceNote.of(n,0.5f,80)==-4,"no wheel angle: 80 km/h at 0.5 G is a 100 m bend, a left one (+lat)");
        check(PaceNote.of(n,-0.9f,40)==1,"40 km/h at 0.9 G: a 14 m right hairpin, a 1");
        check(PaceNote.of(n,0.05f,80)==0&&PaceNote.of(n,n,n)==0&&PaceNote.of(n,0.5f,n)==0,"nothing to go on: straight");
        check(PaceNote.text(-3).equals("3 L")&&PaceNote.text(5).equals("5 R")&&PaceNote.text(0).isEmpty(),"the call as text");
        System.out.println("PaceNoteTest OK");
    }
}
