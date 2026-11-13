import com.veloster.vehicle.CarPaint;
import com.veloster.vehicle.ClusterPicker;
import com.veloster.vehicle.SettingsLayout;
import com.veloster.vehicle.Theme;

/** Settings: categories on the left, every category's choices fit the right pane and hit themselves. */
public final class SettingsLayoutTest {
    private static int checks;
    private static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
    public static void main(String[] args){
        int n=SettingsLayout.CATS.length;
        check(SettingsLayout.CATS[SettingsLayout.DONE].equals("완료")&&SettingsLayout.DONE==n-1,"완료 last");
        check(SettingsLayout.catTop(n-1)+SettingsLayout.CAT_H<=SettingsLayout.BOTTOM,"categories fit the height");
        for(int i=0;i<n;i++)check(SettingsLayout.cat(SettingsLayout.CAT_L+10,SettingsLayout.catTop(i)+10)==i,"category "+i+" hits itself");
        check(SettingsLayout.cat(SettingsLayout.PANE_L+10,SettingsLayout.TOP+10)==-1&&SettingsLayout.cat(SettingsLayout.CAT_L+10,SettingsLayout.catTop(1)-2)==-1,"pane and gaps are no category");
        check(SettingsLayout.THEME_NAMES.length==Theme.IDS.length,"a name per theme");
        check(SettingsLayout.count(SettingsLayout.SCREENS)==ClusterPicker.COUNT&&SettingsLayout.count(SettingsLayout.PAINT)==CarPaint.IDS.length+1,"every screen and paint listed, then the 8-bit sky");
        check(SettingsLayout.count(SettingsLayout.DONE)==0&&SettingsLayout.item(SettingsLayout.DONE,400,200)==-1,"완료 has no pane");
        check(SettingsLayout.count(SettingsLayout.MODE)==SettingsLayout.MODE_FIRST+ClusterPicker.MODES.length,"a row per mode after the two switches");
        check(SettingsLayout.count(SettingsLayout.PICK)==ClusterPicker.COUNT+1&&SettingsLayout.top(SettingsLayout.PICK,0)>=SettingsLayout.TOP+SettingsLayout.TITLE_H,"chooser: every screen and 원래 화면, under its title");
        for(int cat=0;cat<=SettingsLayout.PICK;cat++){
            if(cat==SettingsLayout.SOUND)continue;   // scrollable pane: checked below
            for(int i=0;i<SettingsLayout.count(cat);i++){
                float l=SettingsLayout.left(cat,i),t=SettingsLayout.top(cat,i),w=SettingsLayout.itemW(cat),h=SettingsLayout.itemH(cat);
                check(l>=SettingsLayout.PANE_L&&l+w<=SettingsLayout.PANE_R+0.01f&&t>=SettingsLayout.TOP&&t+h<=SettingsLayout.BOTTOM+0.01f,"cat "+cat+" item "+i+" inside the pane");
                check(SettingsLayout.item(cat,l+w/2,t+h/2)==i,"cat "+cat+" item "+i+" hits itself");
                check(h>=40,"cat "+cat+" items tall enough to tap");
            }
        }
        // The 사운드 pane keeps a tappable row and scrolls: every row is reachable when scrolled into the viewport.
        check(SettingsLayout.itemH(SettingsLayout.SOUND)>=40,"sound rows tall enough to tap");
        check(SettingsLayout.maxPaneScroll(SettingsLayout.SOUND)>0,"the sound pane scrolls (its rows overflow)");
        float mid=SettingsLayout.TOP+(SettingsLayout.BOTTOM-SettingsLayout.TOP)/2,max=SettingsLayout.maxPaneScroll(SettingsLayout.SOUND);
        for(int i=0;i<SettingsLayout.count(SettingsLayout.SOUND);i++){
            float ih=SettingsLayout.itemH(SettingsLayout.SOUND),t=SettingsLayout.top(SettingsLayout.SOUND,i);
            float scroll=Math.max(0,Math.min(max,t+ih/2-mid));   // bring this row's centre into the viewport
            float cx=SettingsLayout.left(SettingsLayout.SOUND,i)+SettingsLayout.itemW(SettingsLayout.SOUND)/2,cy=t-scroll+ih/2;
            check(cy>=SettingsLayout.TOP&&cy<=SettingsLayout.BOTTOM,"sound item "+i+" scrolls into view");
            check(SettingsLayout.item(SettingsLayout.SOUND,cx,cy,scroll)==i,"sound item "+i+" hits itself when scrolled in");
            check(SettingsLayout.left(SettingsLayout.SOUND,i)>=SettingsLayout.PANE_L&&SettingsLayout.left(SettingsLayout.SOUND,i)+SettingsLayout.itemW(SettingsLayout.SOUND)<=SettingsLayout.PANE_R+0.01f,"sound item "+i+" inside the pane width");
        }
        check(SettingsLayout.item(SettingsLayout.THEME,SettingsLayout.CAT_L+10,SettingsLayout.TOP+10)==-1,"the category column is not a choice");
        check(SettingsLayout.downScreen(0,ClusterPicker.MANUAL)&&SettingsLayout.downScreen(0,ClusterPicker.TACHO)&&!SettingsLayout.downScreen(0,ClusterPicker.F1)&&SettingsLayout.downScreen(1,ClusterPicker.F1+2)&&!SettingsLayout.downScreen(1,ClusterPicker.CLASSIC)&&SettingsLayout.downScreen(2,ClusterPicker.CLASSIC),"downshift-sound screens: 람다 and RPM, then +F1/퍼포먼스/레이스, then all");
        System.out.println("PASS "+checks+" settings layout checks");
    }
}
