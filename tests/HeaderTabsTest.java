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
        // the current tab is brought into view only when the current screen changes, so a drag can look past it
        h.setTabs(ids,w,0);check(h.scroll()==0,"a new current screen (first tab) scrolls back to it");
        h.down(500,20);h.move(300,20);h.up();h.setTabs(ids,w,0);
        check(h.scroll()==200,"next frame keeps the dragged position though the current tab is off-strip");
        h.setTabs(ids,w,n-1);check(h.x(n-1)+w[n-1]<=HeaderTabs.RIGHT+0.5f,"switching screens snaps to its tab again");
        // a drag may start just below the tabs (above the body's swipe zone)
        h.setTabs(ids,w,0);h.down(400,50);check(h.move(380,50)&&h.scroll()==20&&h.up(),"a drag starting at y 50 scrolls the strip");
        check(HeaderTabs.GRAB<=52,"the grab zone ends where the body swipe begins (y > 52)");
        // a quick flick coasts on and stops; a slow drag released at rest does not
        h.setTabs(ids,w,0);h.down(500,20);h.move(480,20,1000);h.move(460,20,1016);h.move(440,20,1032);
        float s0=h.scroll();check(h.up(1040)&&h.flinging(),"a flick released while moving coasts");
        h.step(1100);check(h.scroll()>s0,"the coast keeps scrolling the same way");
        for(long t=1116;t<4000&&h.flinging();t+=16)h.step(t);
        check(!h.flinging()&&h.scroll()<=h.maxScroll(),"the coast stops, clamped");
        h.down(300,20);check(!h.flinging(),"touching stops a coast");
        h.move(250,20,5000);h.move(250,20,5300);check(h.up(5300)&&!h.flinging(),"a drag that ends at rest does not coast");
        // gear button sits right of the strip, clear of the clock
        float gx=HeaderTabs.GEAR_L+HeaderTabs.GEAR_W/2,gy=HeaderTabs.TOP+HeaderTabs.H/2;
        check(HeaderTabs.gear(gx,gy)&&!HeaderTabs.gear(gx,60)&&!HeaderTabs.gear(130,gy),"gear button hits only itself");
        check(h.hit(gx,gy)==-1&&HeaderTabs.GEAR_L>=HeaderTabs.RIGHT,"gear sits right of the tab strip");
        check(HeaderTabs.GEAR_L+HeaderTabs.GEAR_W<=638,"gear stays clear of the clock (centred at 667)");
        System.out.println("PASS "+checks+" header tab checks");
    }
}
