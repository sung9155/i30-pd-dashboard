import com.veloster.vehicle.HeaderTabs;

/** Header tab strip: hit testing, clamped scrolling, and drag-versus-tap. */
public final class HeaderTabsTest {
    private static int checks;
    private static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
    public static void main(String[] args){
        HeaderTabs h=new HeaderTabs();
        check(h.hit(130,20)==HeaderTabs.CLUSTER,"screen tab at the left edge");
        check(HeaderTabs.W.length==1,"language and theme live in the gear's edit screen, not the header");
        check(h.hit(100,20)==-1&&h.hit(700,20)==-1,"logo and back button are outside the strip");
        check(h.hit(130,60)==-1,"below the header is not a tab");
        check(h.maxScroll()==0&&!h.moreRight()&&!h.moreLeft(),"the tab fits the strip: nothing to scroll");
        h.scrollTo(1e6f);check(h.scroll()==0,"scroll stays clamped");
        h.down(300,20);check(!h.move(305,22),"small wobble is not a drag");check(!h.up(),"wobble stays a tap");
        h.down(300,20);h.move(260,20);check(h.scroll()==0&&h.up(),"a sideways drag on the strip is not a tap");
        h.down(300,200);check(!h.move(200,200)&&!h.up(),"drag that starts in the body is not the strip's");
        check(h.x(HeaderTabs.CLUSTER)==HeaderTabs.LEFT,"tabs start at the strip's left edge");
        float gx=HeaderTabs.GEAR_L+HeaderTabs.GEAR_W/2,gy=HeaderTabs.TOP+HeaderTabs.H/2;
        check(HeaderTabs.gear(gx,gy)&&!HeaderTabs.gear(gx,60)&&!HeaderTabs.gear(130,gy),"gear button hits only itself");
        check(h.hit(gx,gy)==-1&&HeaderTabs.GEAR_L>=HeaderTabs.RIGHT,"gear sits right of the tab strip");
        check(HeaderTabs.GEAR_L+HeaderTabs.GEAR_W<=638,"gear stays clear of the clock (centred at 667)");
        System.out.println("PASS "+checks+" header tab checks");
    }
}
