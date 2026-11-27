import com.veloster.vehicle.ClusterPicker;

/** Screen list behind the header tabs: 6 x 6 tiles (the last is the edit button), hidden screens, stepping. */
public final class ClusterPickerTest {
    private static int checks;
    private static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
    public static void main(String[] args){
        check(ClusterPicker.COUNT==32&&ClusterPicker.NAMES.length==32&&ClusterPicker.DESC.length==32,"32 screens, one name and line each");
        check(ClusterPicker.NAMES[ClusterPicker.ECO].equals("에코")&&ClusterPicker.NAMES[ClusterPicker.TACHO].equals("RPM")&&ClusterPicker.TACHO==25,"에코, then the RPM screen");
        check(ClusterPicker.WRC==26&&ClusterPicker.ANALOG==27&&ClusterPicker.NEON==28&&ClusterPicker.PASSENGER==29&&ClusterPicker.NAMES[ClusterPicker.PASSENGER].equals("동승자"),"WRC, 아날로그, 네온 and 동승자 after it");
        check(ClusterPicker.CAR3D==30&&ClusterPicker.NEON_DRIVE==ClusterPicker.COUNT-1&&ClusterPicker.NAMES[ClusterPicker.CAR3D].equals("내 차")&&ClusterPicker.NAMES[ClusterPicker.NEON_DRIVE].equals("네온 드라이브"),"내 차 and 네온 드라이브 last (their own views)");
        check(ClusterPicker.COUNT<=ClusterPicker.SLOTS&&ClusterPicker.COUNT<=32,"every screen has a tile, and the hidden mask (bit per screen) fits an int");
        check(ClusterPicker.NAMES[ClusterPicker.MANUAL].equals("람다"),"the 람다 (twin Λ) screen before it");
        {int[] old=new int[23];for(int i=0;i<23;i++)old[i]=22-i;int[] o=ClusterPicker.validOrder(old);
         check(o.length==32&&o[0]==22&&o[22]==0&&o[23]==23&&o[24]==24&&o[25]==25&&o[29]==29&&o[31]==31,"an order saved with 23 screens keeps its arrangement, the newer screens last");}
        {int[] old=new int[22];for(int i=0;i<22;i++)old[i]=21-i;int[] o=ClusterPicker.validOrder(old);
         check(o.length==32&&o[0]==21&&o[21]==0&&o[22]==22&&o[23]==23&&o[24]==24&&o[25]==25,"an order saved with 22 screens keeps its arrangement, the new screens last");}
        {int[] old=new int[26];for(int i=0;i<26;i++)old[i]=25-i;int[] o=ClusterPicker.validOrder(old);
         check(o.length==32&&o[0]==25&&o[25]==0&&o[26]==26&&o[27]==27&&o[28]==28&&o[29]==29,"an order saved with 26 screens keeps its arrangement, WRC to 동승자 after it");}
        {int[] old=new int[30];for(int i=0;i<30;i++)old[i]=29-i;int[] o=ClusterPicker.validOrder(old);
         check(o.length==32&&o[0]==29&&o[29]==0&&o[30]==30&&o[31]==31,"an order saved with 30 screens keeps its arrangement, 내 차 and 네온 드라이브 after it");}
        check(ClusterPicker.NAMES[ClusterPicker.OVERVIEW].equals("전체 조망")&&ClusterPicker.NAMES[ClusterPicker.GAUGES].equals("게이지")&&ClusterPicker.NAMES[ClusterPicker.GRAPHS].equals("그래프"),"overview, gauges, graphs first");
        check(ClusterPicker.NAMES[ClusterPicker.F1].equals("F1")&&ClusterPicker.NAMES[ClusterPicker.CLASSIC].equals("클래식"),"then F1, the symmetric pages, the drawn layouts");
        check(ClusterPicker.page(ClusterPicker.F1+1)==1&&ClusterPicker.page(ClusterPicker.CLASSIC)==5&&ClusterPicker.page(ClusterPicker.MANUAL-1)==19&&ClusterPicker.page(ClusterPicker.MANUAL)==20&&ClusterPicker.page(ClusterPicker.ECO)==21&&ClusterPicker.page(ClusterPicker.TACHO)==22&&ClusterPicker.page(ClusterPicker.PASSENGER)==26,"screen after F1 maps to its PitRenderer page (서킷 19, 수동 20)");
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
        int allButOne=-1&~(1<<9);   // 32 screens: every bit of the int
        check(ClusterPicker.toggle(allButOne,9)==allButOne,"the last shown screen cannot be hidden");
        check(ClusterPicker.next(9,allButOne)==9,"one shown screen: a tap stays on it");

        int[] list=ClusterPicker.slots(hidden,false);
        check(list.length==ClusterPicker.COUNT-3&&list[0]==0&&list[1]==3&&list[list.length-1]==ClusterPicker.COUNT-2,"list shows only the shown screens, in order");
        check(ClusterPicker.slots(hidden,true).length==ClusterPicker.COUNT,"edit mode shows every screen");
        check(ClusterPicker.valid(-1)==0&&ClusterPicker.valid(hidden)==hidden&&ClusterPicker.valid(1<<31)==(1<<31),"hiding every screen shows everything; screen 31 can be hidden");
        // Mode screen, defaults: SPORT -> F1, ECO -> 원래 화면 (stay), NORMAL -> 원래 화면 (stay).
        int[] def=ClusterPicker.MODE_DEFAULT;
        check(def[0]==ClusterPicker.F1&&def[1]==-1&&def[2]==-1,"default mode screens: SPORT opens F1, the rest stay");
        check(ClusterPicker.modeSlot(1)==0&&ClusterPicker.modeSlot(3)==1&&ClusterPicker.modeSlot(0)==2&&ClusterPicker.modeSlot(2)==-1,"mode slots");
        check(ClusterPicker.validTarget(-1)==-1&&ClusterPicker.validTarget(ClusterPicker.COUNT)==-1&&ClusterPicker.validTarget(4)==4,"saved targets checked");
        int[] st=ClusterPicker.onMode(-1,1,5,-1,0,def);check(st[0]==5,"first known mode moves nothing");
        st=ClusterPicker.onMode(0,1,5,-1,0,def);check(st[0]==ClusterPicker.F1&&st[1]==5,"SPORT opens F1, remembers the screen");
        st=ClusterPicker.onMode(1,0,st[0],st[1],0,def);check(st[0]==5&&st[1]==-1,"SPORT -> NORMAL returns to the remembered screen");
        st=ClusterPicker.onMode(0,3,5,-1,0,def);check(st[0]==5&&st[1]==-1,"ECO has no default screen: it stays put");
        st=ClusterPicker.onMode(0,1,5,-1,1<<ClusterPicker.F1,def);check(st[0]==5&&st[1]==-1,"hidden F1: SPORT stays put");
        st=ClusterPicker.onMode(1,1,3,5,0,def);check(st[0]==3&&st[1]==5,"same mode: nothing");
        st=ClusterPicker.onMode(1,-1,3,5,0,def);check(st[0]==3&&st[1]==5,"mode lost: nothing");
        // Customised: SPORT -> race (F1+2), ECO -> a chosen screen, NORMAL -> overview.
        int race=ClusterPicker.F1+2;int[] mine={race,ClusterPicker.GAUGES,ClusterPicker.OVERVIEW};
        st=ClusterPicker.onMode(0,1,9,-1,0,mine);check(st[0]==race&&st[1]==9,"SPORT opens the chosen screen");
        st=ClusterPicker.onMode(1,3,st[0],st[1],0,mine);check(st[0]==ClusterPicker.GAUGES&&st[1]==9,"ECO opens its chosen screen, keeps the return");
        st=ClusterPicker.onMode(3,0,st[0],st[1],0,mine);check(st[0]==ClusterPicker.OVERVIEW,"NORMAL opens its chosen screen");
        st=ClusterPicker.onMode(0,1,4,-1,0,new int[]{ClusterPicker.COUNT+3,-1,-1});check(st[0]==4,"a bad saved target moves nothing");
        {int[] o=ClusterPicker.validOrder(null);int a=o[0],b=o[1],c=o[2],d=o[3];
            int[] m=ClusterPicker.move(o.clone(),0,2);check(m[0]==b&&m[1]==c&&m[2]==a&&m[3]==d,"move: the first screen dropped at position 2");
            m=ClusterPicker.move(o.clone(),3,0);check(m[0]==d&&m[1]==a&&m[2]==b&&m[3]==c,"move: position 3 dropped at the top");
            m=ClusterPicker.move(o.clone(),2,2);check(java.util.Arrays.equals(m,o),"move: same place keeps the order");
            m=ClusterPicker.move(o.clone(),0,o.length+5);check(java.util.Arrays.equals(m,o),"move: out of range keeps the order");}
        System.out.println("PASS "+checks+" cluster picker checks");
    }
}
