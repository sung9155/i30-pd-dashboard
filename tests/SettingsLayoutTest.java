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
        check(SettingsLayout.count(SettingsLayout.SCREENS)==ClusterPicker.COUNT&&SettingsLayout.count(SettingsLayout.PAINT)==CarPaint.IDS.length,"every screen and paint listed");
        check(SettingsLayout.count(SettingsLayout.DONE)==0&&SettingsLayout.item(SettingsLayout.DONE,400,200)==-1,"완료 has no pane");
        check(SettingsLayout.count(SettingsLayout.MODE)==SettingsLayout.MODE_FIRST+ClusterPicker.MODES.length,"a row per mode after the two switches");
        check(SettingsLayout.count(SettingsLayout.PICK)==ClusterPicker.COUNT+1&&SettingsLayout.top(SettingsLayout.PICK,0)>=SettingsLayout.TOP+SettingsLayout.TITLE_H,"chooser: every screen and 원래 화면, under its title");
        for(int cat=0;cat<=SettingsLayout.PICK;cat++)for(int i=0;i<SettingsLayout.count(cat);i++){
            float l=SettingsLayout.left(cat,i),t=SettingsLayout.top(cat,i),w=SettingsLayout.itemW(cat),h=SettingsLayout.itemH(cat);
            check(l>=SettingsLayout.PANE_L&&l+w<=SettingsLayout.PANE_R+0.01f&&t>=SettingsLayout.TOP&&t+h<=SettingsLayout.BOTTOM+0.01f,"cat "+cat+" item "+i+" inside the pane");
            check(SettingsLayout.item(cat,l+w/2,t+h/2)==i,"cat "+cat+" item "+i+" hits itself");
            check(h>=40,"cat "+cat+" items tall enough to tap");
        }
        check(SettingsLayout.item(SettingsLayout.THEME,SettingsLayout.CAT_L+10,SettingsLayout.TOP+10)==-1,"the category column is not a choice");
        System.out.println("PASS "+checks+" settings layout checks");
    }
}
