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
    // ---- MODE 버튼 → 계기판 (ModeKey) ----
    /** The dashboard says it is on screen every 20 s: a little longer without it and it is taken as gone (killed while showing). */
    public static final long MODE_SHOWN_FOR=25000;
    /** Sources in the stock MODE list: a short MODE press then steps the source inside the mode service and nothing
     *  reaches us. An empty list works; one that could not be read (null) is not known to block. */
    public static boolean modeListBlocks(int[] list){return list!=null&&list.length>0;}
    /** The stock list for the owner to read when the switch is refused: the sources by the unit's own names (mode numbers
     *  from the stock Settings' SteeringModeButtonSetting), a number without a name as it is. "[Android Auto, CarPlay]". */
    public static String modeNames(int[] list){
        if(list==null)return "?";
        StringBuilder out=new StringBuilder("[");
        for(int i=0;i<list.length;i++){if(i>0)out.append(", ");out.append(modeName(list[i]));}
        return out.append(']').toString();
    }
    private static String modeName(int mode){
        switch(mode){
            case 0x0d:return "SXM";case 0x16:return "DMB";case 0x18:return "ISDB-T";
            case 0x31:return "USB Music";case 0x32:return "USB Video";case 0x34:return "iPod";case 0x35:return "AUX";
            case 0x36:return "Bluetooth Audio";case 0x37:return "My Music";
            case 0x45:return "MirrorLink";case 0x47:return "Pandora";
            case 0x73:return "CarPlay";case 0x74:return "Android Auto";case 0x77:return "CarLife";
            case 0x81:return "Nature Sound";
            default:return "0x"+Integer.toHexString(mode);
        }
    }
    /** A held MODE, told from a short one by the broadcast's com.hkmc.mode.EXTRA_RUNNING_STATE: the mode service sends
     *  1 (it logs "runningOption : FOREGROUND") for a held press and 2 (BACKGROUND) for the short press it turns into
     *  the same broadcast while the stock list is empty. Car, 2026-11-28: 3 held and 11 short presses on the dashboard,
     *  in My Apps and on the stock home, without exception; seen in the log, not read in the service's code. Any other
     *  value, or none, is a short press. */
    public static boolean modeHeld(int runningState){return runningState==1;}
    /** What one press opens: My Apps when it is held and MODE 길게 → 내 앱 is on; otherwise the picked app from the
     *  dashboard and the dashboard from anywhere else (a held press with the option off is as a short one). */
    public static final int MODE_DASHBOARD=0,MODE_APP=1,MODE_HUB=2;
    public static int modeOpens(boolean held,boolean holdOpensHub,boolean onDashboard){
        return held&&holdOpensHub?MODE_HUB:onDashboard?MODE_APP:MODE_DASHBOARD;
    }
    /** MODE opens the picked app only while the dashboard is known to be on screen; otherwise the dashboard. */
    public static boolean modeOnDashboard(long now,long shownUntil){return now<shownUntil;}
    /** The same press delivered again (to our second receiver): the service's time stamp when both have one, otherwise
     *  anything within 300 ms of the press already acted on. */
    public static boolean modeSamePress(long stamp,long lastStamp,long now,long lastAt){
        if(stamp>=0&&lastStamp>=0)return stamp==lastStamp;
        return now-lastAt<300;
    }
    /** A phone projection app by its package name (CarPlay is com.daudio.app.dipo on this unit): the picker starts on it. */
    public static boolean modeProjection(String name){
        if(name==null)return false;
        String n=name.toLowerCase(java.util.Locale.US);
        return n.contains("dipo")||n.contains("carplay")||n.contains("androidauto");
    }
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
