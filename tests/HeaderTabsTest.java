import com.veloster.vehicle.HeaderTabs;

/** Header tab strip: hit testing, clamped scrolling, and drag-versus-tap. */
public final class HeaderTabsTest {
    private static int checks;
    private static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
    public static void main(String[] args){
        HeaderTabs h=new HeaderTabs();
        check(h.hit(130,20)==HeaderTabs.OVERVIEW,"first tab at the left edge");
        check(h.hit(100,20)==-1&&h.hit(700,20)==-1,"logo and back button are outside the strip");
        check(h.hit(130,60)==-1,"below the header is not a tab");
        check(h.maxScroll()>0&&h.moreRight()&&!h.moreLeft(),"nine tabs overflow the strip");
        h.scrollTo(1e6f);check(h.scroll()==h.maxScroll()&&!h.moreRight()&&h.moreLeft(),"clamped at the end");
        check(h.hit(630,20)==HeaderTabs.THEME,"theme button reachable after scrolling");
        h.scrollTo(-50);check(h.scroll()==0,"clamped at zero");
        h.down(300,20);check(!h.move(305,22),"small wobble is not a drag");check(!h.up(),"wobble stays a tap");
        h.down(300,20);h.move(260,20);check(h.scroll()==Math.min(40,h.maxScroll())&&h.up(),"drag scrolls and is not a tap");
        h.scrollTo(0);h.down(300,200);check(!h.move(200,200)&&h.scroll()==0&&!h.up(),"drag that starts in the body does not scroll");
        h.down(300,20);h.move(300,120);check(!h.up()&&h.scroll()==0,"vertical slide off the header is not a scroll");
        check(h.x(HeaderTabs.OVERVIEW)==HeaderTabs.LEFT,"tabs start at the strip's left edge");
        float before=h.x(HeaderTabs.F1);h.scrollTo(10);check(h.x(HeaderTabs.F1)==before-10,"x follows the scroll");
        System.out.println("PASS "+checks+" header tab checks");
    }
}
