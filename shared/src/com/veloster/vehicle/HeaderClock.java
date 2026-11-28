package com.veloster.vehicle;

/**
 * The header's clock, between the gear button and 돌아가기: "16:52", or "4:52 PM" (no leading zero; the mark much
 * smaller than the digits, on their baseline). Holding it changes between the two (VehicleDash keeps the choice). Its cell is as wide as
 * the form needs, with PAD free on either side of the text, and the gear and the tab strip give way to it
 * (HeaderTabs.clockCell, set by PitRenderer each frame); PitRenderer draws the text centred in the cell, shrinking it
 * when a wide font would not fit. No Android: host-tested.
 */
public final class HeaderClock {
    /** Right edge: where 돌아가기 starts. Free space either side of the text. */
    public static final float R=HeaderTabs.BACK_L,PAD=6;
    /** Digits and the AM / PM mark, in px of the 800 x 480 layout, and the space between them. */
    public static final float SIZE=27,MARK_SIZE=14,GAP=5;
    private HeaderClock(){}
    /** The cell's width: "00:00" is about 70 px and "12:52 PM" about 96 px in the default bold face. The 24-hour cell
     *  leaves about 16 px free on either side, as "9:28 PM" has in the 12-hour one (8 px looked tight beside it); with a
     *  two-digit hour the 12-hour text comes to about 8 px from the edges. Never less than PAD. */
    public static float cell(boolean twelve){return twelve?110:100;}
    /** Where the text's baseline goes so that digits of this size stand in the middle of the header's buttons (their
     *  centre, TOP + H / 2): digits and capitals are about 0.71 of the size tall in the unit's bold face, so half of that
     *  below the centre, on a whole pixel. The button labels follow the same rule (14 px: centre + 5). The old clock sat
     *  at 32 whatever its size, about 2 px above this, which showed once the digits grew. */
    public static float baseline(float size){return Math.round(HeaderTabs.TOP+HeaderTabs.H/2+size*0.356f);}
    /** The cell's left edge, the gear button's right one (as HeaderTabs.clockCell last placed it), and its centre. */
    public static float left(){return HeaderTabs.GEAR_L+HeaderTabs.GEAR_W;}
    public static float cx(){return (left()+R)/2;}
    public static boolean hit(float x,float y){return x>=left()&&x<R&&y>=HeaderTabs.TOP&&y<=HeaderTabs.TOP+HeaderTabs.H;}
    /** "16:52" / "04:52" on the 24-hour clock (as before); "4:52" / "12:05" on the 12-hour one. */
    public static String digits(int hour,int minute,boolean twelve){
        int h=twelve?(hour%12==0?12:hour%12):hour;
        return (twelve||h>=10?"":"0")+h+":"+(minute<10?"0":"")+minute;
    }
    /** "AM" / "PM" on the 12-hour clock (midnight is 12 AM, noon 12 PM); nothing on the 24-hour one. */
    public static String mark(int hour,boolean twelve){return !twelve?"":hour<12?"AM":"PM";}
    /** How much to shrink both texts so that digits + GAP + mark fit a cell less PAD on either side; 1 when they fit. */
    public static float fit(float digitsWidth,float markWidth,float cell){
        float want=digitsWidth+(markWidth>0?GAP+markWidth:0),room=cell-2*PAD;
        return want>room&&want>0?room/want:1;
    }
}
