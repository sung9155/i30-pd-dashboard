package com.veloster.carplayhub;

/** Android-independent decisions for an explicitly enabled quick-entry service. */
public final class QuickEntryPolicy {
    public static final int OFF=0, NOTIFICATION=1, FLOATING=2;
    private QuickEntryPolicy() {}
    public static int mode(int value){return value==NOTIFICATION||value==FLOATING?value:OFF;}
    public static boolean restore(int value){return mode(value)!=OFF;}
    public static boolean visible(int value,boolean screenOn,boolean locked){return mode(value)==FLOATING&&screenOn&&!locked;}
    public static float fraction(float value){
        if(Float.isNaN(value)||Float.isInfinite(value))return 0.5f;
        return Math.max(0f,Math.min(1f,value));
    }
    public static int bound(int position,int span,int size){return Math.max(0,Math.min(Math.max(0,span-size),position));}
    public static int y(float fraction,int height,int size){return bound(Math.round(fraction(fraction)*Math.max(0,height-size)),height,size);}
    public static float relativeY(int position,int height,int size){
        int space=Math.max(0,height-size);return space==0?0.5f:fraction((float)bound(position,height,size)/space);
    }
    public static boolean right(int x,int width,int size){return x+size/2>=width/2;}
    public static int edge(boolean right,int width,int size){return right?Math.max(0,width-size):0;}
    /** ☆ button action that opens the dashboard; the OEM sends unlisted actions as a broadcast. */
    public static final String STAR_ACTION="com.veloster.carplayhub.STAR_KEY";
    /** What ☆ goes back to when switched off: the remembered action, or the stock "none". */
    public static final String STAR_NONE="com.mobis.intent.action.CUSTOM_SHORTCUT";
    public static String starRestore(String saved){
        return saved==null||saved.length()==0||STAR_ACTION.equals(saved)?STAR_NONE:saved;
    }
    /** A stock function ☆ had before us (not ours, not the stock "none"): the one the toggle hands ☆ back to. */
    public static boolean starLendable(String saved){return saved!=null&&saved.length()>0&&!STAR_ACTION.equals(saved)&&!STAR_NONE.equals(saved);}
    /** ☆ toggle (계기판에서는 ☆ 원래 기능): what to set ☆ to when the dashboard comes on screen or leaves it, or null to leave
     *  it alone. On screen: ours becomes the remembered stock function, so the stock mode service runs it itself (we never
     *  run a stock function for it). Gone: only what we lent, and only while ☆ is still that function, becomes ours again;
     *  a function the user picked meanwhile in the stock settings stays. Without a stock function to lend, nothing moves. */
    public static String starToggle(boolean on,boolean shown,String now,String saved,boolean lent){
        if(!starLendable(saved))return null;
        if(shown)return on&&STAR_ACTION.equals(now)?saved:null;
        return lent&&saved.equals(now)?STAR_ACTION:null;
    }
}
