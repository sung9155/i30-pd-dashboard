import com.veloster.vehicle.ClusterPicker;

/** Screen list behind the header tabs: 6 x 5 tiles (the last is the edit button), hidden screens, stepping. */
public final class ClusterPickerTest {
    private static int checks;
    private static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
    public static void main(String[] args){
        check(ClusterPicker.COUNT==23&&ClusterPicker.NAMES.length==23&&ClusterPicker.DESC.length==23,"23 screens, one name and line each");
        check(ClusterPicker.NAMES[ClusterPicker.OVERVIEW].equals("전체 조망")&&ClusterPicker.NAMES[ClusterPicker.GAUGES].equals("게이지")&&ClusterPicker.NAMES[ClusterPicker.GRAPHS].equals("그래프"),"overview, gauges, graphs first");
        check(ClusterPicker.NAMES[ClusterPicker.F1].equals("F1")&&ClusterPicker.NAMES[ClusterPicker.CLASSIC].equals("클래식"),"then F1, the symmetric pages, the drawn layouts");
        check(ClusterPicker.page(ClusterPicker.F1+1)==1&&ClusterPicker.page(ClusterPicker.CLASSIC)==5&&ClusterPicker.page(ClusterPicker.COUNT-1)==19,"screen after F1 maps to its PitRenderer page");
        for(int i=0;i<ClusterPicker.SLOTS;i++){
            float x=ClusterPicker.left(i)+10,y=ClusterPicker.top(i)+10;
            check(ClusterPicker.hit(x,y)==i,"tile "+i+" hits itself");
        }
        check(ClusterPicker.SLOTS>=ClusterPicker.COUNT,"a tile for every screen");
        check(ClusterPicker.hit(ClusterPicker.left(1)-2,ClusterPicker.top(1)+10)==-1,"gap between tiles is no tile");
        check(ClusterPicker.hit(400,30)==-1,"header is outside the list");
        int last=ClusterPicker.SLOTS-1;
        check(ClusterPicker.top(last)+ClusterPicker.TILE_H<=480&&ClusterPicker.left(last)+ClusterPicker.TILE_W<=800,"last tile on screen");

        int none=0;
        check(ClusterPicker.next(0,none)==1&&ClusterPicker.next(ClusterPicker.COUNT-1,none)==0,"tap steps on and wraps");
        check(ClusterPicker.prev(1,none)==0&&ClusterPicker.prev(0,none)==ClusterPicker.COUNT-1,"swipe-back steps back and wraps");
        int hidden=(1<<1)|(1<<2)|(1<<(ClusterPicker.COUNT-1));
        check(ClusterPicker.next(0,hidden)==3,"stepping skips hidden screens");
        check(ClusterPicker.next(ClusterPicker.COUNT-2,hidden)==0,"and wraps past a hidden last screen");
        check(ClusterPicker.shown(2,hidden)==3&&ClusterPicker.shown(4,hidden)==4,"a hidden saved screen opens the next shown one");
        check(ClusterPicker.toggle(none,6)==(1<<6)&&ClusterPicker.toggle(1<<6,6)==0,"toggle hides and shows");
        int allButOne=((1<<ClusterPicker.COUNT)-1)&~(1<<9);
        check(ClusterPicker.toggle(allButOne,9)==allButOne,"the last shown screen cannot be hidden");
        check(ClusterPicker.next(9,allButOne)==9,"one shown screen: a tap stays on it");

        int[] list=ClusterPicker.slots(hidden,false);
        check(list.length==ClusterPicker.COUNT-3&&list[0]==0&&list[1]==3&&list[list.length-1]==ClusterPicker.COUNT-2,"list shows only the shown screens, in order");
        check(ClusterPicker.slots(hidden,true).length==ClusterPicker.COUNT,"edit mode shows every screen");
        check(ClusterPicker.valid(-1)==0&&ClusterPicker.valid(1<<25)==0&&ClusterPicker.valid((1<<25)-1)==0&&ClusterPicker.valid(hidden)==hidden,"bad saved masks show everything");
        check(ClusterPicker.NAMES[ClusterPicker.ECO].equals("에코"),"eco index");
        // Mode screen, defaults: SPORT -> F1, ECO -> eco, NORMAL -> the screen from before.
        int[] def=ClusterPicker.MODE_DEFAULT;
        check(ClusterPicker.modeSlot(1)==0&&ClusterPicker.modeSlot(3)==1&&ClusterPicker.modeSlot(0)==2&&ClusterPicker.modeSlot(2)==-1,"mode slots");
        check(ClusterPicker.validTarget(-1)==-1&&ClusterPicker.validTarget(ClusterPicker.COUNT)==-1&&ClusterPicker.validTarget(4)==4,"saved targets checked");
        int[] st=ClusterPicker.onMode(-1,1,5,-1,0,def);check(st[0]==5,"first known mode moves nothing");
        st=ClusterPicker.onMode(0,1,5,-1,0,def);check(st[0]==ClusterPicker.F1&&st[1]==5,"SPORT opens F1, remembers the screen");
        st=ClusterPicker.onMode(1,3,st[0],st[1],0,def);check(st[0]==ClusterPicker.ECO&&st[1]==5,"SPORT -> ECO keeps the screen to return to");
        st=ClusterPicker.onMode(3,0,st[0],st[1],0,def);check(st[0]==5&&st[1]==-1,"NORMAL returns to it");
        st=ClusterPicker.onMode(0,1,5,-1,0,def);st=ClusterPicker.onMode(1,0,7,st[1],0,def);check(st[0]==7&&st[1]==-1,"driver moved off F1: NORMAL keeps their screen");
        st=ClusterPicker.onMode(0,1,5,-1,1<<ClusterPicker.F1,def);check(st[0]==5&&st[1]==-1,"hidden F1: SPORT stays put");
        st=ClusterPicker.onMode(1,1,3,5,0,def);check(st[0]==3&&st[1]==5,"same mode: nothing");
        st=ClusterPicker.onMode(1,-1,3,5,0,def);check(st[0]==3&&st[1]==5,"mode lost: nothing");
        // Customised: SPORT -> race (5 + F1), ECO -> 원래 화면, NORMAL -> overview.
        int race=ClusterPicker.F1+2;int[] mine={race,-1,ClusterPicker.OVERVIEW};
        st=ClusterPicker.onMode(0,1,9,-1,0,mine);check(st[0]==race&&st[1]==9,"SPORT opens the chosen screen");
        st=ClusterPicker.onMode(1,3,st[0],st[1],0,mine);check(st[0]==9&&st[1]==-1,"ECO set to 원래 화면 returns to the screen from before");
        st=ClusterPicker.onMode(3,0,9,-1,0,mine);check(st[0]==ClusterPicker.OVERVIEW,"NORMAL opens its chosen screen");
        st=ClusterPicker.onMode(0,1,st[0],st[1],0,mine);st=ClusterPicker.onMode(1,0,st[0],st[1],0,mine);check(st[0]==ClusterPicker.OVERVIEW,"NORMAL with a screen goes there again");
        st=ClusterPicker.onMode(0,1,4,-1,0,new int[]{ClusterPicker.COUNT+3,-1,-1});check(st[0]==4,"a bad saved target moves nothing");
        System.out.println("PASS "+checks+" cluster picker checks");
    }
}
