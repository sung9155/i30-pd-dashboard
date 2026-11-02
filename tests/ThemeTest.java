import com.veloster.vehicle.Theme;

/** Theme selection, cycling and the N-palette remap. */
public final class ThemeTest {
    private static int checks;
    private static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
    public static void main(String[] args){
        Theme.select("n");check(Theme.current().map(0xffa4d9ef)==0xffa4d9ef,"N keeps colors");
        check(Theme.current().map(0x80a4d9ef)==0x80a4d9ef,"N keeps alpha");
        Theme.select("lambo");check(Theme.current().map(0xffa4d9ef)!=0xffa4d9ef,"lambo remaps the N accent");
        check((Theme.current().map(0x80a4d9ef)>>>24)==0x80,"remap keeps alpha");
        check(Theme.current().map(0xff3ddc84)==0xff3ddc84,"semantic green is shared");
        check(Theme.current().map(0xff123456)==0xff123456,"unknown color passes through");
        Theme.select("redblack");check(Theme.current().id.equals("n"),"removed red-black falls back to N");
        check(Theme.next("n").equals("lambo")&&Theme.next("lambo").equals("bmw")&&Theme.next("bmw").equals("ice")&&Theme.next("night").equals("rose")&&Theme.next("rose").equals("copper")&&Theme.next("copper").equals("n")&&Theme.next("bogus").equals("lambo"),"cycle order");
        // Car-cluster themes: true black background so the screen does not glow at night.
        for(String id:new String[]{"ice","mono","amber","teal","night","rose","copper"}){Theme.select(id);
            check(Theme.current().id.equals(id)&&Theme.current().map(0xff040506)==0xff000000,id+": true black background");}
        Theme.select("bogus");check(Theme.current().id.equals("n"),"unknown id falls back to N");
        Theme.select(null);check(Theme.current().id.equals("n"),"null falls back to N");
        Theme.select("amber");check(Theme.current().dangerRing,"amber marks danger by shape");
        Theme.select("lambo");check(!Theme.current().dangerRing,"other themes keep the plain danger color");
        check(Theme.current().label.equals("LB")&&Theme.current().shape==Theme.SHAPE_HEX,"lambo label and hex cells");
        Theme.select("bmw");check(Theme.current().mStripe&&Theme.current().shape==Theme.SHAPE_ROUND&&Theme.current().label.equals("M"),"bmw stripe and round cells");
        Theme.select("rose");check(Theme.current().label.equals("RS")&&Theme.current().shape==Theme.SHAPE_ROUND&&!Theme.current().mStripe,"rose label and round cells without the M stripe");
        Theme.select("copper");check(Theme.current().label.equals("CU")&&Theme.current().shape==Theme.SHAPE_HEX,"copper label and hex cells");
        Theme.select("n");check(Theme.current().shape==Theme.SHAPE_CUT&&!Theme.current().mStripe,"N keeps today's cut corner");
        for(String id:Theme.IDS){Theme.select(id);Theme t=Theme.current();check(t.map(t.danger)==t.danger,id+": danger is never remapped");}
        // A warning written on the accent-filled gear box must stay readable (WARM-UP / COOL-DOWN), and a calm
        // state must never be drawn in the danger colour (overview ESC/ABS/TCS "off").
        for(String id:Theme.IDS){Theme.select(id);Theme t=Theme.current();
            if(!id.equals("n"))check(Theme.contrast(t.readableOn(t.map(0xffa4d9ef),t.danger),t.map(0xffa4d9ef))>=3,id+": warning readable on the accent fill");
            check(t.calm(0xffa4d9ef)!=t.danger,id+": calm state is not the danger colour");}
        Theme.select("n");check(Theme.current().readableOn(0xffa4d9ef,0xffff4059)==0xffff4059,"N keeps today's colours (pixel-identical)");
        check(Theme.current().calm(0xffa4d9ef)==0xffa4d9ef,"N calm colour unchanged");
        System.out.println("PASS "+checks+" theme checks");
    }
}
