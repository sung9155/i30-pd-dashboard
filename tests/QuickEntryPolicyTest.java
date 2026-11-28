import com.veloster.carplayhub.QuickEntryPolicy;

public final class QuickEntryPolicyTest {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        for(int mode=-2;mode<=4;mode++){
            boolean enabled=mode==1||mode==2;
            check(QuickEntryPolicy.restore(mode)==enabled,"unknown/off setting must never restore");
            for(boolean screen:new boolean[]{false,true})for(boolean locked:new boolean[]{false,true})
                check(QuickEntryPolicy.visible(mode,screen,locked)==(mode==2&&screen&&!locked),"locked/off/notification-only overlay hidden");
        }
        check(QuickEntryPolicy.fraction(Float.NaN)==0.5f,"corrupt NaN preference");
        check(QuickEntryPolicy.fraction(Float.POSITIVE_INFINITY)==0.5f,"corrupt infinite preference");
        check(QuickEntryPolicy.fraction(-1)==0&&QuickEntryPolicy.fraction(2)==1,"out of range preference");
        for(int width:new int[]{0,24,48,800,1280})for(int height:new int[]{0,24,48,480,720}){
            int size=48;
            for(int x:new int[]{-999,0,20,600,99999}){
                int bound=QuickEntryPolicy.bound(x,width,size);
                check(bound>=0&&bound<=Math.max(0,width-size),"drag outside screen");
                int edge=QuickEntryPolicy.edge(QuickEntryPolicy.right(bound,width,size),width,size);
                check(edge==0||edge==Math.max(0,width-size),"snap to either edge");
            }
            for(float fraction:new float[]{-1,0,0.3f,0.5f,1,2,Float.NaN}){
                int y=QuickEntryPolicy.y(fraction,height,size);
                check(y>=0&&y<=Math.max(0,height-size),"vertical bound on rotation/small display");
                float normalized=QuickEntryPolicy.relativeY(y,height,size);
                check(normalized>=0&&normalized<=1,"stored relative position");
                check(QuickEntryPolicy.y(normalized,height,size)==y,"position roundtrip");
            }
        }
        float saved=QuickEntryPolicy.relativeY(216,480,48);
        check(QuickEntryPolicy.y(saved,800,48)==376,"relative position survives rotation");
        check(QuickEntryPolicy.starRestore("com.hkmc.intent.action.MYMENU_SHORTCUT").equals("com.hkmc.intent.action.MYMENU_SHORTCUT"),"star key restores the remembered action");
        for(String lost:new String[]{null,"",QuickEntryPolicy.STAR_ACTION})
            check(QuickEntryPolicy.starRestore(lost).equals("com.mobis.intent.action.CUSTOM_SHORTCUT"),"star key never restores itself or nothing");
        // ☆ toggle: on the dashboard ☆ is the stock function it had before us, and ours again when the dashboard leaves
        String ours=QuickEntryPolicy.STAR_ACTION,stock="com.hkmc.intent.action.MYMENU_SHORTCUT",other="com.hkmc.intent.action.OTHER_SHORTCUT";
        check(QuickEntryPolicy.starLendable(stock),"a remembered stock function can be handed back");
        for(String none:new String[]{null,"",ours,QuickEntryPolicy.STAR_NONE})
            check(!QuickEntryPolicy.starLendable(none)&&QuickEntryPolicy.starToggle(true,true,ours,none,false)==null&&QuickEntryPolicy.starToggle(true,false,none,none,true)==null,
                "no stock function before us: the toggle moves nothing");
        check(stock.equals(QuickEntryPolicy.starToggle(true,true,ours,stock,false)),"dashboard on screen: ours becomes the stock function");
        check(QuickEntryPolicy.starToggle(false,true,ours,stock,false)==null,"toggle off: ☆ stays ours on the dashboard");
        check(QuickEntryPolicy.starToggle(true,true,stock,stock,true)==null,"the 20 s repeat while on screen changes nothing");
        check(QuickEntryPolicy.starToggle(true,true,other,stock,false)==null&&QuickEntryPolicy.starToggle(true,true,QuickEntryPolicy.STAR_NONE,stock,false)==null,
            "☆ not ours (switched off, or released by the stock settings): never taken on the dashboard");
        check(ours.equals(QuickEntryPolicy.starToggle(true,false,stock,stock,true)),"dashboard gone: what we lent is ours again");
        check(ours.equals(QuickEntryPolicy.starToggle(false,false,stock,stock,true)),"lent, then the toggle switched off: still handed back");
        check(QuickEntryPolicy.starToggle(true,false,stock,stock,false)==null,"not lent by us: a stock function stays (☆ switched off by the user)");
        check(QuickEntryPolicy.starToggle(true,false,other,stock,true)==null&&QuickEntryPolicy.starToggle(true,false,QuickEntryPolicy.STAR_NONE,stock,true)==null,
            "the user picked another function meanwhile: left alone");
        check(QuickEntryPolicy.starToggle(true,false,ours,stock,true)==null,"already ours: nothing to do");
        // MODE 버튼 → 계기판: the stock list has to be empty; the app only from the dashboard; one press acted on once
        check(QuickEntryPolicy.modeListBlocks(new int[]{116,115})&&!QuickEntryPolicy.modeListBlocks(new int[0])&&!QuickEntryPolicy.modeListBlocks(null),"sources in the stock MODE list block; empty works; unread is not known to block");
        check(QuickEntryPolicy.modeNames(new int[]{116,115}).equals("[Android Auto, CarPlay]")&&QuickEntryPolicy.modeNames(new int[]{0x36,0x99}).equals("[Bluetooth Audio, 0x99]")&&QuickEntryPolicy.modeNames(new int[0]).equals("[]")&&QuickEntryPolicy.modeNames(null).equals("?"),"the stock list by name, an unknown number as it is");
        check(QuickEntryPolicy.modeOnDashboard(1000,1000+QuickEntryPolicy.MODE_SHOWN_FOR)&&!QuickEntryPolicy.modeOnDashboard(26000,25000)&&!QuickEntryPolicy.modeOnDashboard(5,0),"on the dashboard only while its signal is fresh; never after it left");
        check(QuickEntryPolicy.modeSamePress(2340658,2340658,9000,100)&&!QuickEntryPolicy.modeSamePress(2340700,2340658,5010,5000),"same time stamp = the same press, however far apart; another stamp = another press, however close");
        check(QuickEntryPolicy.modeSamePress(-1,2340658,5100,5000)&&!QuickEntryPolicy.modeSamePress(-1,-1,5400,5000)&&!QuickEntryPolicy.modeSamePress(2340658,-1,5400,5000),"without a stamp: within 300 ms is the same press");
        // a held MODE: running state 1 in the broadcast (2 for the short press); it opens My Apps only with its option on
        check(QuickEntryPolicy.modeHeld(1)&&!QuickEntryPolicy.modeHeld(2)&&!QuickEntryPolicy.modeHeld(-1)&&!QuickEntryPolicy.modeHeld(0),"held is running state 1; 2, none and anything else are a short press");
        check(QuickEntryPolicy.modeOpens(true,true,false)==QuickEntryPolicy.MODE_HUB&&QuickEntryPolicy.modeOpens(true,true,true)==QuickEntryPolicy.MODE_HUB,"held with the option on: My Apps, from the dashboard too");
        check(QuickEntryPolicy.modeOpens(true,false,false)==QuickEntryPolicy.MODE_DASHBOARD&&QuickEntryPolicy.modeOpens(true,false,true)==QuickEntryPolicy.MODE_APP,"held with the option off: as a short press");
        check(QuickEntryPolicy.modeOpens(false,true,false)==QuickEntryPolicy.MODE_DASHBOARD&&QuickEntryPolicy.modeOpens(false,true,true)==QuickEntryPolicy.MODE_APP&&QuickEntryPolicy.modeOpens(false,false,true)==QuickEntryPolicy.MODE_APP,"short: the dashboard, or the picked app from the dashboard, whatever the option");
        check(QuickEntryPolicy.modeProjection("com.daudio.app.dipo")&&QuickEntryPolicy.modeProjection("com.example.AndroidAuto")&&!QuickEntryPolicy.modeProjection("com.android.settings")&&!QuickEntryPolicy.modeProjection(null),"phone projection apps by name");
        System.out.println("PASS: quick-entry opt-in restore, lock/screen visibility, drag bounds, rotation position, star key restore and toggle, MODE key list check and press");
    }
}
