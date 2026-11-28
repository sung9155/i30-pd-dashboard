import com.veloster.vehicle.ClusterPicker;

/** Screen list behind the header tabs: 6 x 6 tiles (the last is the edit button), hidden screens, stepping. */
public final class ClusterPickerTest {
    private static int checks;
    private static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
    public static void main(String[] args){
        check(ClusterPicker.COUNT==33&&ClusterPicker.NAMES.length==33&&ClusterPicker.DESC.length==33,"33 screens, one name and line each");
        check(ClusterPicker.NAMES[ClusterPicker.ECO].equals("에코")&&ClusterPicker.NAMES[ClusterPicker.TACHO].equals("RPM")&&ClusterPicker.TACHO==25,"에코, then the RPM screen");
        check(ClusterPicker.ANALOG==26&&ClusterPicker.NEON==27&&ClusterPicker.WRC==31&&ClusterPicker.NAMES[ClusterPicker.NEON].equals("신스웨이브")&&ClusterPicker.NAMES[ClusterPicker.WRC].equals("WRC"),"아날로그, 신스웨이브 and WRC after it");
        check(ClusterPicker.CAR3D==28&&ClusterPicker.NEON_DRIVE==29&&ClusterPicker.NAMES[ClusterPicker.CAR3D].equals("내 차")&&ClusterPicker.NAMES[ClusterPicker.NEON_DRIVE].equals("네온 드라이브"),"내 차 and 네온 드라이브 last (their own views)");
        check(ClusterPicker.RETRO8==32&&ClusterPicker.RETRO8==ClusterPicker.COUNT-1&&ClusterPicker.NAMES[ClusterPicker.RETRO8].equals("레트로 8종")&&ClusterPicker.NAMES[9].equals("레트로"),"레트로 8종 (upstream's 레트로 tab) after every older number; our 레트로 keeps its name");
        check(ClusterPicker.COUNT<=ClusterPicker.SLOTS&&ClusterPicker.COUNT<=64,"every screen has a tile, and the hidden mask (bit per screen) fits a long");
        check(ClusterPicker.mask(1<<31,1)==((1L<<31)|(1L<<32))&&ClusterPicker.maskLo((1L<<31)|(1L<<32))==(1<<31)&&ClusterPicker.maskHi((1L<<31)|(1L<<32))==1&&ClusterPicker.mask(5,0)==5L,"the mask is saved as two ints: a saved 32-screen mask reads back unchanged");
        check(ClusterPicker.NAMES[ClusterPicker.MANUAL].equals("람다"),"the 람다 (twin Λ) screen before it");
        {int[] old=new int[23];for(int i=0;i<23;i++)old[i]=22-i;int[] o=ClusterPicker.validOrder(old);
         check(o.length==33&&o[0]==22&&o[22]==0&&o[23]==23&&o[24]==24&&o[25]==25&&o[29]==29&&o[30]==30&&o[31]==31&&o[32]==32,"an order saved with 23 screens keeps its arrangement, the newer screens last");}
        {int[] old=new int[22];for(int i=0;i<22;i++)old[i]=21-i;int[] o=ClusterPicker.validOrder(old);
         check(o.length==33&&o[0]==21&&o[21]==0&&o[22]==22&&o[23]==23&&o[24]==24&&o[25]==25,"an order saved with 22 screens keeps its arrangement, the new screens last");}
        {int[] old=new int[26];for(int i=0;i<26;i++)old[i]=25-i;int[] o=ClusterPicker.validOrder(old);
         check(o.length==33&&o[0]==25&&o[25]==0&&o[26]==26&&o[27]==27&&o[28]==28&&o[29]==29,"an order saved with 26 screens keeps its arrangement, 아날로그 to 내 차 after it");}
        {int[] old=new int[30];for(int i=0;i<30;i++)old[i]=29-i;int[] o=ClusterPicker.validOrder(old);
         check(o.length==33&&o[0]==29&&o[29]==0&&o[30]==30&&o[31]==31&&o[32]==32,"an order saved with 30 screens keeps its arrangement, 네온 드라이브 after it");}
        check(ClusterPicker.NAMES[ClusterPicker.OVERVIEW].equals("전체 조망")&&ClusterPicker.NAMES[ClusterPicker.GAUGES].equals("게이지")&&ClusterPicker.NAMES[ClusterPicker.GRAPHS].equals("그래프"),"overview, gauges, graphs first");
        check(ClusterPicker.NAMES[ClusterPicker.F1].equals("F1")&&ClusterPicker.NAMES[ClusterPicker.CLASSIC].equals("클래식"),"then F1, the symmetric pages, the drawn layouts");
        check(ClusterPicker.page(ClusterPicker.F1+1)==1&&ClusterPicker.page(ClusterPicker.CLASSIC)==5&&ClusterPicker.page(ClusterPicker.MANUAL-1)==19&&ClusterPicker.page(ClusterPicker.MANUAL)==20&&ClusterPicker.page(ClusterPicker.ECO)==21&&ClusterPicker.page(ClusterPicker.TACHO)==22&&ClusterPicker.page(ClusterPicker.WRC)==28,"screen after F1 maps to its PitRenderer page (서킷 19, 수동 20)");
        for(int i=0;i<ClusterPicker.SLOTS;i++){
            float x=ClusterPicker.left(i)+10,y=ClusterPicker.top(i)+10;
            check(ClusterPicker.hit(x,y)==i,"tile "+i+" hits itself");
        }
        check(ClusterPicker.SLOTS>=ClusterPicker.COUNT,"a tile for every screen");
        check(ClusterPicker.hit(ClusterPicker.left(1)-2,ClusterPicker.top(1)+10)==-1,"gap between tiles is no tile");
        check(ClusterPicker.hit(400,30)==-1,"header is outside the list");
        int last=ClusterPicker.SLOTS-1;
        check(ClusterPicker.top(last)+ClusterPicker.TILE_H<=480&&ClusterPicker.left(last)+ClusterPicker.TILE_W<=800,"last tile on screen");

        long none=0;
        check(ClusterPicker.next(0,none)==1&&ClusterPicker.next(ClusterPicker.COUNT-1,none)==0,"tap steps on and wraps");
        check(ClusterPicker.prev(1,none)==0&&ClusterPicker.prev(0,none)==ClusterPicker.COUNT-1,"swipe-back steps back and wraps");
        long hidden=(1L<<1)|(1L<<2)|(1L<<(ClusterPicker.COUNT-1));
        check(ClusterPicker.next(0,hidden)==3,"stepping skips hidden screens");
        check(ClusterPicker.next(ClusterPicker.COUNT-2,hidden)==0,"and wraps past a hidden last screen");
        check(ClusterPicker.shown(2,hidden)==3&&ClusterPicker.shown(4,hidden)==4,"a hidden saved screen opens the next shown one");
        check(ClusterPicker.toggle(none,6)==(1L<<6)&&ClusterPicker.toggle(1L<<6,6)==0,"toggle hides and shows");
        long allButOne=((1L<<ClusterPicker.COUNT)-1)&~(1L<<9);   // every screen but one hidden
        check(ClusterPicker.toggle(allButOne,9)==allButOne,"the last shown screen cannot be hidden");
        check(ClusterPicker.next(9,allButOne)==9,"one shown screen: a tap stays on it");

        int[] list=ClusterPicker.slots(hidden,false);
        check(list.length==ClusterPicker.COUNT-3&&list[0]==0&&list[1]==3&&list[list.length-1]==ClusterPicker.COUNT-2,"list shows only the shown screens, in order");
        check(ClusterPicker.slots(hidden,true).length==ClusterPicker.COUNT,"edit mode shows every screen");
        check(ClusterPicker.valid(-1)==0&&ClusterPicker.valid((1L<<ClusterPicker.COUNT)-1)==0&&ClusterPicker.valid(hidden)==hidden&&ClusterPicker.valid(1L<<31)==(1L<<31)&&ClusterPicker.valid(1L<<32)==(1L<<32)&&ClusterPicker.valid(1L<<33)==0,"hiding every screen shows everything; screens 31 and 32 can be hidden; a bit past the last screen is refused");
        // Mode screen, defaults: SPORT -> F1, ECO -> 원래 화면 (stay), NORMAL -> 원래 화면 (stay).
        int[] def=ClusterPicker.MODE_DEFAULT;
        check(def[0]==ClusterPicker.F1&&def[1]==-1&&def[2]==-1,"default mode screens: SPORT opens F1, the rest stay");
        check(ClusterPicker.modeSlot(1)==0&&ClusterPicker.modeSlot(3)==1&&ClusterPicker.modeSlot(0)==2&&ClusterPicker.modeSlot(2)==-1,"mode slots");
        check(ClusterPicker.validTarget(-1)==-1&&ClusterPicker.validTarget(ClusterPicker.COUNT)==-1&&ClusterPicker.validTarget(4)==4,"saved targets checked");
        int[] st=ClusterPicker.onMode(-1,1,5,-1,0,def);check(st[0]==5,"first known mode moves nothing");
        st=ClusterPicker.onMode(0,1,5,-1,0,def);check(st[0]==ClusterPicker.F1&&st[1]==5,"SPORT opens F1, remembers the screen");
        st=ClusterPicker.onMode(1,0,st[0],st[1],0,def);check(st[0]==5&&st[1]==-1,"SPORT -> NORMAL returns to the remembered screen");
        st=ClusterPicker.onMode(0,3,5,-1,0,def);check(st[0]==5&&st[1]==-1,"ECO has no default screen: it stays put");
        st=ClusterPicker.onMode(0,1,5,-1,1L<<ClusterPicker.F1,def);check(st[0]==5&&st[1]==-1,"hidden F1: SPORT stays put");
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
        {int[] d=ClusterPicker.defaultOrder();boolean[] seen=new boolean[ClusterPicker.COUNT];boolean perm=d.length==ClusterPicker.COUNT;
         for(int s:d){perm&=s>=0&&s<ClusterPicker.COUNT&&!seen[s];if(perm)seen[s]=true;}
         check(perm,"default order lists every screen once");
         check(java.util.Arrays.equals(ClusterPicker.validOrder(null),d),"no saved order: the default");
         int n=d.length;check(d[0]==ClusterPicker.OVERVIEW&&d[n-1]==ClusterPicker.CAR3D&&d[n-2]==ClusterPicker.NEON_DRIVE&&d[n-3]==15,"overview first; 아케이드, 네온 드라이브, 내 차 (screens you go into) last");}
        check(ClusterPicker.renumber(28)==ClusterPicker.WRC&&ClusterPicker.renumber(29)==ClusterPicker.CAR3D&&ClusterPicker.renumber(30)==ClusterPicker.NEON_DRIVE&&ClusterPicker.renumber(5)==5,"old test numbers 28-30 move to the new ones");
        check(ClusterPicker.renumberMask((1L<<28)|(1L<<3))==((1L<<ClusterPicker.WRC)|(1L<<3)),"a hidden old WRC stays hidden");
        check(ClusterPicker.MAINT==30&&ClusterPicker.NAMES[ClusterPicker.MAINT].equals("정비")&&ClusterPicker.WRC==31&&ClusterPicker.ORDER_VERSION==4,"정비 30, WRC 31, order version 4");
        {int[] d=ClusterPicker.defaultOrder();int t=-1;for(int i=0;i<d.length;i++)if(d[i]==11)t=i;check(t>=0&&d[t+1]==ClusterPicker.MAINT,"default order: 정비 right after 택시");}
        {int[] saved=new int[31];for(int i=0;i<31;i++)saved[i]=30-i;   // a v3 order, reversed: 30 (then WRC) first, 11 at index 19
         int[] o=ClusterPicker.migrateOrder4(saved);
         check(o.length==32&&o[0]==31&&o[19]==11&&o[20]==ClusterPicker.MAINT&&o[31]==0&&ClusterPicker.validOrder(o).length==33&&ClusterPicker.validOrder(o)[32]==ClusterPicker.RETRO8,"v3 order kept: WRC 30 -> 31, 정비 inserted after 택시 ("+java.util.Arrays.toString(o)+")");
         int[] noTaxi=new int[]{0,1,2};int[] q=ClusterPicker.migrateOrder4(noTaxi);check(q[q.length-1]==ClusterPicker.MAINT,"no 택시 in the saved order: 정비 at the end");}
        check(ClusterPicker.renumber4(30)==31&&ClusterPicker.renumber4(5)==5&&ClusterPicker.renumberMask4((1L<<30)|(1L<<2))==((1L<<31)|(1L<<2)),"v3 screen and hidden bits: 30 -> 31");
        // The favourites line (2026-11-28): the screens above it are the header strip, the rest only the list.
        {int[] d=ClusterPicker.defaultOrder();int N=ClusterPicker.COUNT,LINE=ClusterPicker.LINE;
         int[] rows=ClusterPicker.rows(d,7);
         check(rows.length==N+1&&rows[7]==LINE&&rows[0]==d[0]&&rows[6]==d[6]&&rows[8]==d[7]&&rows[N]==d[N-1],"the line is a row of its own after the 7th screen");
         check(ClusterPicker.favOf(rows)==7&&java.util.Arrays.equals(ClusterPicker.orderOf(rows),d),"rows give the order and the line's place back");
         int[] bar=ClusterPicker.bar(d,0,7);check(bar.length==7&&bar[0]==d[0]&&bar[6]==d[6],"the strip = the screens above the line, in order");
         bar=ClusterPicker.bar(d,1L<<d[1],7);check(bar.length==6&&bar[1]==d[2],"a screen switched off leaves the strip (no other takes its place)");
         int[] tabs=ClusterPicker.tabs(d,0,7,d[20]);check(tabs.length==8&&tabs[7]==d[20],"the current screen from below the line rides at the strip's end");
         check(ClusterPicker.tabs(d,0,7,d[3]).length==7,"a favourite on show adds no tab");
         check(ClusterPicker.starred(d,7,d[6])&&!ClusterPicker.starred(d,7,d[7]),"starred = above the line");
         int[] r=ClusterPicker.moveRow(d,7,21,0);   // 맨 위로 from below the line (row 21 = the 21st screen: the line is row 7)
         check(ClusterPicker.orderOf(r)[0]==d[20]&&ClusterPicker.orderOf(r)[1]==d[0]&&ClusterPicker.favOf(r)==8,"to the top from below: one more favourite, the others keep their order");
         r=ClusterPicker.moveRow(d,7,2,10);check(ClusterPicker.favOf(r)==6&&ClusterPicker.orderOf(r)[9]==d[2],"a favourite dragged below the line: one fewer");
         r=ClusterPicker.moveRow(d,7,7,3);check(ClusterPicker.favOf(r)==3&&java.util.Arrays.equals(ClusterPicker.orderOf(r),d),"the line itself moves: the count changes, the order does not");
         r=ClusterPicker.moveRow(d,7,7,0);check(ClusterPicker.favOf(r)==1,"the line stops under the first row (at least one favourite)");
         r=ClusterPicker.moveRow(d,1,0,5);check(ClusterPicker.favOf(r)==1&&ClusterPicker.orderOf(r)[0]!=d[0],"the last favourite dragged out: the next screen takes its place");
         r=ClusterPicker.moveRow(d,7,8,7);check(ClusterPicker.favOf(r)==8&&java.util.Arrays.equals(ClusterPicker.orderOf(r),d),"▲ on the first row below the line crosses it");
         check(ClusterPicker.validFav(0)==1&&ClusterPicker.validFav(99)==N&&ClusterPicker.validFav(7)==7&&ClusterPicker.FAV_DEFAULT==7,"the saved count: 1 to every screen, 7 by default");
         int[] g=ClusterPicker.grid(d,0);int M=ClusterPicker.MAINT,C3=ClusterPicker.CAR3D,ND=ClusterPicker.NEON_DRIVE;
         check(g.length==ClusterPicker.SLOTS&&g[0]==d[0]&&g[29]>=0&&g[30]==M&&g[31]==ND&&g[32]==C3&&g[33]==-1,"the list: 30 dashboards (five full rows with 레트로 8종), then 정비 · 네온 드라이브 · 내 차 on a row of their own");
         for(int i=0;i<30;i++)check(g[i]!=M&&g[i]!=C3&&g[i]!=ND,"no tool or 3D screen among the dashboards (slot "+i+")");
         long hid=1L<<d[0];g=ClusterPicker.grid(d,hid);check(g[0]==d[1]&&g[28]>=0&&g[29]==-1&&g[30]==M,"a screen switched off is not in the list");
         g=ClusterPicker.grid(d,1L<<ClusterPicker.RETRO8);for(int x:g)check(x!=ClusterPicker.RETRO8,"screen 32 switched off (a bit an int mask did not have) is not in the list");
         check(ClusterPicker.bar(d,1L<<ClusterPicker.RETRO8,ClusterPicker.COUNT).length==ClusterPicker.COUNT-1&&ClusterPicker.bar(d,1L,ClusterPicker.COUNT).length==ClusterPicker.COUNT-1,"the strip tells screen 32 from screen 0");
         long few=-1L;for(int i=0;i<10;i++)few&=~(1L<<d[i]);few&=~(1L<<C3);g=ClusterPicker.grid(d,few);   // ten shown (d[0..9] holds 정비) and 내 차
         check(g[8]>=0&&g[9]==-1&&g[12]==M&&g[13]==C3&&g[14]==-1,"few screens: the own row comes right under the dashboards ("+g[9]+","+g[12]+","+g[13]+")");
         check(ClusterPicker.defaultFav(d,0)==7,"nothing saved: the line after the 7th screen");
         {long on=-1L;for(int i:new int[]{3,9,12,20,25})on&=~(1L<<d[i]);   // an owner who kept five screens, spread over the order
          check(ClusterPicker.defaultFav(d,on)==26&&ClusterPicker.bar(d,on,ClusterPicker.defaultFav(d,on)).length==5,"fewer than 7 switched on: all of them stay on the strip, the line right under the last (row 26)");
          long off=(1L<<d[0])|(1L<<d[4]);check(ClusterPicker.defaultFav(d,off)==9&&ClusterPicker.bar(d,off,9).length==7,"switched-off screens do not use up the 7");}
         check(ClusterPicker.own(M)&&ClusterPicker.own(C3)&&ClusterPicker.own(ND)&&!ClusterPicker.own(ClusterPicker.WRC),"own kind: 정비, 내 차, 네온 드라이브");}
        System.out.println("PASS "+checks+" cluster picker checks");
    }
}
