import com.veloster.vehicle.HeaderClock;
import com.veloster.vehicle.HeaderTabs;

/** The header clock: its two forms, its cell between the gear and 돌아가기 for each, and the fit of a wide text. */
public final class HeaderClockTest {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        // 24-hour: as it was, two digits for the hour, no mark
        check(HeaderClock.digits(16,52,false).equals("16:52")&&HeaderClock.digits(4,5,false).equals("04:05")&&HeaderClock.digits(0,0,false).equals("00:00"),"24-hour digits");
        check(HeaderClock.mark(16,false).equals("")&&HeaderClock.mark(4,false).equals(""),"no mark on the 24-hour clock");
        // 12-hour: "4:52 PM", no leading zero; midnight and noon are 12
        check(HeaderClock.digits(16,52,true).equals("4:52")&&HeaderClock.mark(16,true).equals("PM"),"4:52 PM");
        check(HeaderClock.digits(4,5,true).equals("4:05")&&HeaderClock.mark(4,true).equals("AM"),"4:05 AM");
        check(HeaderClock.digits(0,7,true).equals("12:07")&&HeaderClock.mark(0,true).equals("AM"),"midnight is 12 AM");
        check(HeaderClock.digits(12,0,true).equals("12:00")&&HeaderClock.mark(12,true).equals("PM"),"noon is 12 PM");
        check(HeaderClock.digits(23,59,true).equals("11:59")&&HeaderClock.mark(23,true).equals("PM")&&HeaderClock.digits(11,59,true).equals("11:59")&&HeaderClock.mark(11,true).equals("AM"),"the last minute of each half");
        check(HeaderClock.digits(10,30,true).equals("10:30")&&HeaderClock.digits(22,30,true).equals("10:30"),"two-digit hours keep both digits");
        // the digits are seven points larger than the old 20; the mark is small beside them
        check(HeaderClock.SIZE==27&&HeaderClock.MARK_SIZE==14,"sizes");
        // up and down: the digits' middle (baseline less half their height, about 0.71 of the size) on the buttons' middle
        float mid=HeaderTabs.TOP+HeaderTabs.H/2,base=HeaderClock.baseline(HeaderClock.SIZE);
        check(base==Math.round(base)&&Math.abs(base-0.71f*HeaderClock.SIZE/2-mid)<=0.6f,"digits stand in the middle of the header's buttons, on a whole pixel: baseline "+base);
        check(HeaderClock.baseline(14)==mid+5,"the same rule as the button labels (14 px: centre + 5)");
        check(base>33&&base+1<=HeaderTabs.TOP+HeaderTabs.H&&base-0.71f*HeaderClock.SIZE>=HeaderTabs.TOP,"lower than it was, and within the buttons' height");
        // the cell, for each form: right of the gear, left of 돌아가기, the text's width and a margin on both sides
        float cy=HeaderTabs.TOP+HeaderTabs.H/2;
        for(boolean twelve:new boolean[]{false,true}){
            float cell=HeaderClock.cell(twelve);
            HeaderTabs.clockCell(cell);
            check(HeaderClock.R==HeaderTabs.BACK_L&&HeaderClock.R-HeaderClock.left()==cell&&HeaderClock.cx()==HeaderClock.left()+cell/2,"the cell runs from the gear to 돌아가기");
            check(HeaderClock.PAD>0&&cell-2*HeaderClock.PAD>=(twelve?96:70),"room for the widest text of the form (about 70 px for 00:00, 96 px for 12:52 PM, default bold face) with a margin on both sides");
            check(HeaderClock.hit(HeaderClock.cx(),cy)&&HeaderClock.hit(HeaderClock.left(),cy)&&!HeaderClock.hit(HeaderClock.R,cy),"the cell is held; 돌아가기 is not part of it");
            check(!HeaderClock.hit(HeaderClock.left()-1,cy)&&HeaderTabs.gear(HeaderClock.left()-1,cy)&&!HeaderTabs.gear(HeaderClock.left()+1,cy),"the gear ends where the clock begins");
            check(!HeaderClock.hit(HeaderClock.cx(),HeaderTabs.TOP+HeaderTabs.H+1)&&!HeaderClock.hit(HeaderClock.cx(),HeaderTabs.TOP-1),"the body below and the edge above are not the clock");
            // fit: untouched when it fits, shrunk to the room when it does not
            float room=cell-2*HeaderClock.PAD;
            check(HeaderClock.fit(room,0,cell)==1&&HeaderClock.fit(0,0,cell)==1,"a text that fits is drawn at its size");
            float k=HeaderClock.fit(100,40,cell);
            check(k<1&&Math.abs((100+HeaderClock.GAP+40)*k-room)<0.01f,"a wide face is shrunk to exactly the room");
            check(Math.abs(HeaderClock.fit(room*2,0,cell)-0.5f)<1e-6f,"digits alone: no gap is counted");
        }
        check(HeaderClock.cell(true)>HeaderClock.cell(false),"the 12-hour form takes the wider cell");
        HeaderTabs.clockCell(HeaderClock.cell(false));
        System.out.println("PASS: header clock forms, cells and fit");
    }
}
