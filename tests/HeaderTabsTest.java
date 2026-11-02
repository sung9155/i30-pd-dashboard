import com.veloster.vehicle.HeaderTabs;

/** Header tab strip: dynamic shown-screen tabs, hit testing, clamped scrolling that keeps the current tab in view. */
public final class HeaderTabsTest {
    private static int checks;
    private static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
    public static void main(String[] args){
        HeaderTabs h=new HeaderTabs();
        // three narrow tabs that fit: no scroll, each hit maps to its screen id
        h.setTabs(new int[]{0,3,5},new float[]{100,100,100},0);
        check(h.count()==3,"three shown tabs");
        check(h.x(0)==HeaderTabs.LEFT,"first tab starts at the strip's left edge");
        check(h.screenAt(h.hit(HeaderTabs.LEFT+10,20))==0,"left tab is screen 0");
        check(h.screenAt(h.hit(HeaderTabs.LEFT+110,20))==3,"second tab is screen 3");
        check(h.hit(100,20)==-1&&h.hit(700,20)==-1,"logo and back button are outside the strip");
        check(h.hit(130,60)==-1,"below the header is not a tab");
        check(h.maxScroll()==0&&!h.moreRight()&&!h.moreLeft(),"tabs that fit do not scroll");
        // a sideways drag on the strip is a scroll, not a tap (and clamps at 0 when nothing overflows)
        h.down(300,20);check(!h.move(305,22),"small wobble is not a drag");check(!h.up(),"wobble stays a tap");
        h.down(300,20);h.move(260,20);check(h.scroll()==0&&h.up(),"a sideways drag on the strip is not a tap");
        h.down(300,200);check(!h.move(200,200)&&!h.up(),"a drag that starts in the body is not the strip's");
        // many wide tabs overflow: the strip scrolls and keeps the current tab in view
        int n=12;int[] ids=new int[n];float[] w=new float[n];for(int i=0;i<n;i++){ids[i]=i;w[i]=90;}
        h.setTabs(ids,w,n-1);
        check(h.maxScroll()>0&&h.moreLeft()&&h.current()==n-1,"overflow scrolls and the last tab is current");
        check(h.x(n-1)+w[n-1]<=HeaderTabs.RIGHT+0.5f,"the current tab is scrolled fully into view");
        // gear button sits right of the strip, clear of the clock
        float gx=HeaderTabs.GEAR_L+HeaderTabs.GEAR_W/2,gy=HeaderTabs.TOP+HeaderTabs.H/2;
        check(HeaderTabs.gear(gx,gy)&&!HeaderTabs.gear(gx,60)&&!HeaderTabs.gear(130,gy),"gear button hits only itself");
        check(h.hit(gx,gy)==-1&&HeaderTabs.GEAR_L>=HeaderTabs.RIGHT,"gear sits right of the tab strip");
        check(HeaderTabs.GEAR_L+HeaderTabs.GEAR_W<=638,"gear stays clear of the clock (centred at 667)");
        System.out.println("PASS "+checks+" header tab checks");
    }
}
