import com.veloster.vehicle.ClusterPicker;

/** Screen list behind the one header tab: 6 x 5 tiles (the last is the edit button), hidden screens, stepping. */
public final class ClusterPickerTest {
    private static int checks;
    private static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
    public static void main(String[] args){
        check(ClusterPicker.COUNT==25&&ClusterPicker.NAMES.length==25&&ClusterPicker.DESC.length==25,"25 screens, one name and line each");
        check(ClusterPicker.NAMES[ClusterPicker.OVERVIEW].equals("전체 조망")&&ClusterPicker.NAMES[ClusterPicker.GAUGES].equals("게이지")&&ClusterPicker.NAMES[ClusterPicker.GRAPHS].equals("그래프"),"overview, gauges, graphs first");
        check(ClusterPicker.NAMES[ClusterPicker.F1].equals("F1")&&ClusterPicker.NAMES[ClusterPicker.CLASSIC].equals("클래식"),"then F1, the symmetric pages, the drawn layouts");
        check(ClusterPicker.page(ClusterPicker.F1+1)==1&&ClusterPicker.page(ClusterPicker.CLASSIC)==5&&ClusterPicker.page(ClusterPicker.COUNT-1)==21,"screen after F1 maps to its PitRenderer page");
        for(int i=0;i<ClusterPicker.SLOTS;i++){
            float x=ClusterPicker.left(i)+10,y=ClusterPicker.top(i)+10;
            check(ClusterPicker.hit(x,y)==i,"tile "+i+" hits itself");
        }
        check(ClusterPicker.EDIT==ClusterPicker.SLOTS-1&&ClusterPicker.SLOTS>ClusterPicker.COUNT,"완료 tile after every screen");
        check(ClusterPicker.DIAG>=ClusterPicker.COUNT&&ClusterPicker.DIAG<ClusterPicker.CAR,"trouble-code tile after the screens");
        check(ClusterPicker.LANG>=ClusterPicker.COUNT&&ClusterPicker.THEME>ClusterPicker.LANG&&ClusterPicker.THEME<ClusterPicker.EDIT,"language and theme tiles between the screens and 완료");
        check(ClusterPicker.hit(ClusterPicker.left(1)-2,ClusterPicker.top(1)+10)==-1,"gap between tiles is no tile");
        check(ClusterPicker.hit(400,30)==-1,"header is outside the list");
        int last=ClusterPicker.SLOTS-1;
        check(ClusterPicker.top(last)+ClusterPicker.TILE_H<=480&&ClusterPicker.left(last)+ClusterPicker.TILE_W<=800,"last tile on screen");

        int none=0;
        check(ClusterPicker.next(0,none)==1&&ClusterPicker.next(24,none)==0,"tap steps on and wraps");
        int hidden=(1<<1)|(1<<2)|(1<<24);
        check(ClusterPicker.next(0,hidden)==3,"stepping skips hidden screens");
        check(ClusterPicker.next(23,hidden)==0,"and wraps past a hidden last screen");
        check(ClusterPicker.shown(2,hidden)==3&&ClusterPicker.shown(4,hidden)==4,"a hidden saved screen opens the next shown one");
        check(ClusterPicker.toggle(none,6)==(1<<6)&&ClusterPicker.toggle(1<<6,6)==0,"toggle hides and shows");
        int allButOne=((1<<ClusterPicker.COUNT)-1)&~(1<<9);
        check(ClusterPicker.toggle(allButOne,9)==allButOne,"the last shown screen cannot be hidden");
        check(ClusterPicker.next(9,allButOne)==9,"one shown screen: a tap stays on it");

        int[] list=ClusterPicker.slots(hidden,false);
        check(list.length==22&&list[0]==0&&list[1]==3&&list[21]==23,"list shows only the shown screens, in order");
        check(ClusterPicker.slots(hidden,true).length==25,"edit mode shows every screen");
        check(ClusterPicker.valid(-1)==0&&ClusterPicker.valid(1<<25)==0&&ClusterPicker.valid((1<<25)-1)==0&&ClusterPicker.valid(hidden)==hidden,"bad saved masks show everything");
        System.out.println("PASS "+checks+" cluster picker checks");
    }
}
