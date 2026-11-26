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
        System.out.println("PASS: quick-entry opt-in restore, lock/screen visibility, drag bounds, rotation position, star key restore and toggle");
    }
}
