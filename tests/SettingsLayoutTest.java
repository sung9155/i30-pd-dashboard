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
        check(SettingsLayout.cat(SettingsLayout.PANE_L+10,SettingsLayout.TOP+10)==-1&&SettingsLayout.cat(SettingsLayout.CAT_L+10,SettingsLayout.catTop(1)-1)==-1,"pane and gaps are no category");
        check(SettingsLayout.THEME_NAMES.length==Theme.IDS.length,"a name per theme");
        check(SettingsLayout.count(SettingsLayout.SCREENS)==ClusterPicker.COUNT&&SettingsLayout.count(SettingsLayout.PAINT)==CarPaint.IDS.length+3&&SettingsLayout.PAINT_SKY==CarPaint.IDS.length&&SettingsLayout.PAINT_NARROW_PLATE==SettingsLayout.PAINT_WIDE_PLATE+1&&SettingsLayout.PAINT_WIDE_PLATE==CarPaint.IDS.length+1,"every screen and paint listed, then the 8-bit sky and the two plates");
        check(SettingsLayout.count(SettingsLayout.DONE)==0&&SettingsLayout.item(SettingsLayout.DONE,400,200)==-1,"완료 has no pane");
        check(SettingsLayout.count(SettingsLayout.MODE)==SettingsLayout.MODE_FIRST+ClusterPicker.MODES.length,"a row per mode after the three switches");
        check(SettingsLayout.count(SettingsLayout.PICK)==ClusterPicker.COUNT+1&&SettingsLayout.top(SettingsLayout.PICK,0)>=SettingsLayout.TOP+SettingsLayout.TITLE_H,"chooser: every screen and 원래 화면, under its title");
        for(int cat=0;cat<=SettingsLayout.PICK;cat++){
            if(SettingsLayout.rowScroll(cat))continue;   // scrollable panes (사운드, RPM): checked below
            for(int i=0;i<SettingsLayout.count(cat);i++){
                float l=SettingsLayout.left(cat,i),t=SettingsLayout.top(cat,i),w=SettingsLayout.itemW(cat),h=SettingsLayout.itemH(cat);
                check(l>=SettingsLayout.PANE_L&&l+w<=SettingsLayout.PANE_R+0.01f&&t>=SettingsLayout.TOP&&t+h<=SettingsLayout.BOTTOM+0.01f,"cat "+cat+" item "+i+" inside the pane");
                check(SettingsLayout.item(cat,l+w/2,t+h/2)==i,"cat "+cat+" item "+i+" hits itself");
                check(h>=40,"cat "+cat+" items tall enough to tap");
            }
        }
        for(int pc:new int[]{SettingsLayout.SOUND,SettingsLayout.RPM}){   // the − / + panes keep a tappable row and scroll: every row reachable
        // The 사운드 pane keeps a tappable row and scrolls: every row is reachable when scrolled into the viewport.
        check(SettingsLayout.itemH(pc)>=40,"pane "+pc+" rows tall enough to tap");
        check(SettingsLayout.maxPaneScroll(pc)>0,"the sound pane scrolls (its rows overflow)");
        {float mid=SettingsLayout.TOP+(SettingsLayout.BOTTOM-SettingsLayout.TOP)/2,max=SettingsLayout.maxPaneScroll(pc);
        for(int i=0;i<SettingsLayout.count(pc);i++){
            float ih=SettingsLayout.itemH(pc),t=SettingsLayout.top(pc,i);
            float scroll=Math.max(0,Math.min(max,t+ih/2-mid));   // bring this row's centre into the viewport
            float cx=SettingsLayout.left(pc,i)+SettingsLayout.itemW(pc)/2,cy=t-scroll+ih/2;
            check(cy>=SettingsLayout.TOP&&cy<=SettingsLayout.BOTTOM,"pane "+pc+" item "+i+" scrolls into view");
            check(SettingsLayout.item(pc,cx,cy,scroll)==i,"pane "+pc+" item "+i+" hits itself when scrolled in");
            check(SettingsLayout.left(pc,i)>=SettingsLayout.PANE_L&&SettingsLayout.left(pc,i)+SettingsLayout.itemW(pc)<=SettingsLayout.PANE_R+0.01f,"pane "+pc+" item "+i+" inside the pane width");
        }
        }}
        check(SettingsLayout.item(SettingsLayout.THEME,SettingsLayout.CAT_L+10,SettingsLayout.TOP+10)==-1,"the category column is not a choice");
        check(SettingsLayout.downScreen(0,ClusterPicker.MANUAL)&&SettingsLayout.downScreen(0,ClusterPicker.TACHO)&&!SettingsLayout.downScreen(0,ClusterPicker.F1)&&SettingsLayout.downScreen(1,ClusterPicker.F1+2)&&!SettingsLayout.downScreen(1,ClusterPicker.CLASSIC)&&SettingsLayout.downScreen(2,ClusterPicker.CLASSIC),"downshift-sound screens: 람다 and RPM, then +F1/퍼포먼스/레이스, then all");
        // 플로팅 pane: every button is big enough to hit in a car, inside the pane, clear of the others and of the preview
        int[] ft=SettingsLayout.F_TARGETS;
        for(int a=0;a<ft.length;a++){float[] b=SettingsLayout.floatBox(ft[a]);
            check(b[3]-b[1]>=36&&b[2]-b[0]>=52,"float button "+ft[a]+" at least 52 x 36");
            check(b[0]>=SettingsLayout.PANE_L&&b[2]<=SettingsLayout.PANE_R&&b[1]>=SettingsLayout.TOP&&b[3]<=SettingsLayout.BOTTOM-14,"float button "+ft[a]+" inside the pane, above the hint");
            check(b[0]>=SettingsLayout.FW_R||b[3]<=SettingsLayout.FW_B||b[1]>=SettingsLayout.FW_B,"float button "+ft[a]+" clear of the preview");
            for(int c=a+1;c<ft.length;c++){float[] o=SettingsLayout.floatBox(ft[c]);
                if(ft[a]==SettingsLayout.F_EDIT&&ft[c]>SettingsLayout.F_EDIT||ft[c]==SettingsLayout.F_EDIT)continue;
                check(b[2]<=o[0]||o[2]<=b[0]||b[3]<=o[1]||o[3]<=b[1],"float buttons "+ft[a]+" and "+ft[c]+" do not overlap");}
            float cx=(b[0]+b[2])/2,cy=(b[1]+b[3])/2;
            check(SettingsLayout.floatHit(cx,cy,2,2,124,64)==ft[a],"a tap in the middle of "+ft[a]+" hits it");}
        {float w=2*124,h=2*64,k=SettingsLayout.floatPreviewScale(w,h),x=SettingsLayout.floatPreviewX(w,h),y=SettingsLayout.floatPreviewY(w,h);
            check(k>1&&x>=SettingsLayout.FW_L&&x+w*k<=SettingsLayout.FW_R&&y+h*k<=SettingsLayout.FW_B-SettingsLayout.FW_CAPTION,"a 2 x 2 card at 보통 is shown larger than real, inside the well");
            check(SettingsLayout.floatPreviewScale(20,10)==2,"a tiny window at most twice its size");
            check(SettingsLayout.floatHit(x+(124+30)*k,y+(64+30)*k,2,2,124,64)==SettingsLayout.F_CELL+3,"the bottom right preview cell is cell 4");
            float ws=4*124*1.5f,ks=SettingsLayout.floatPreviewScale(ws,64*1.5f);check(ks<1&&SettingsLayout.floatPreviewX(ws,96)>=SettingsLayout.FW_L,"a wide strip is scaled down to fit");}
        // the chooser sheet: tabs (자동 + 9 groups) on the left, up to 15 readings, graph buttons under them, 취소 / 완료
        for(int t=0;t<10;t++){float[] b=SettingsLayout.sheetTab(t);check(b[3]<=SettingsLayout.SH_B-10&&b[2]<SettingsLayout.S_GX,"tab "+t+" on the sheet, left of the readings");
            check(SettingsLayout.sheetHit((b[0]+b[2])/2,(b[1]+b[3])/2,10,15,4)==SettingsLayout.S_TAB+t,"tab "+t+" hits");}
        for(int i=0;i<SettingsLayout.S_MAX_VALUES;i++){float[] b=SettingsLayout.sheetValue(i);check(b[3]<SettingsLayout.S_GRAPH_Y-6&&b[1]>SettingsLayout.S_CELL_Y+64,"reading "+i+" between the cell preview and the graph buttons");
            check(b[2]-b[0]>=140&&b[3]-b[1]>=40,"reading "+i+" at least 140 x 40");
            check(SettingsLayout.sheetHit((b[0]+b[2])/2,(b[1]+b[3])/2,10,15,4)==SettingsLayout.S_VAL+i,"reading "+i+" hits");}
        for(int g=0;g<4;g++){float[] b=SettingsLayout.sheetGraph(g);check(b[3]-b[1]>=50&&b[2]-b[0]>=100&&b[2]<=SettingsLayout.SH_R-10&&b[3]<SettingsLayout.S_DONE_BOX[1],"graph button "+g+" big and on the sheet");
            check(SettingsLayout.sheetHit((b[0]+b[2])/2,(b[1]+b[3])/2,10,15,4)==SettingsLayout.S_GRAPH+g,"graph button "+g+" hits");}
        check(SettingsLayout.sheetHit(500,430,10,15,4)==SettingsLayout.S_CANCEL&&SettingsLayout.sheetHit(620,430,10,15,4)==SettingsLayout.S_DONE,"취소 and 완료");
        check(SettingsLayout.sheetHit(30,30,10,15,4)==-1&&SettingsLayout.sheetHit(300,440,10,15,4)==SettingsLayout.S_IN,"outside / empty sheet");
        check(SettingsLayout.sheetHit(300,124,10,2,4)==SettingsLayout.S_VAL&&SettingsLayout.sheetHit(300,170,10,2,4)==SettingsLayout.S_IN,"only the readings there are hit (2 readings: row 2 is empty)");
        // the shape sheet: 9 tiles, each big enough and hit by its middle
        for(int i=0;i<SettingsLayout.FORM_SLOTS.length;i++){float[] b=SettingsLayout.tile5(SettingsLayout.FORM_SLOTS[i]);check(b[2]<=SettingsLayout.F_SH_R-10&&b[0]>=SettingsLayout.F_SH_L+10&&b[3]<=SettingsLayout.SH_B-10&&b[2]-b[0]>=130&&b[3]-b[1]>=90,"shape tile "+i+" big and on the sheet");
            check(SettingsLayout.hitForm((b[0]+b[2])/2,(b[1]+b[3])/2,SettingsLayout.FORM_SLOTS)==i,"shape tile "+i+" hits");}
        {float[][] cells={{38,0,119,40},{119,0,200,40}};float w=206*1.5f,h=40*1.5f,k=SettingsLayout.floatPreviewScale(w,h),x=SettingsLayout.floatPreviewX(w,h),y=SettingsLayout.floatPreviewY(w,h);
            for(float[] cc:cells){cc[0]*=1.5f;cc[1]*=1.5f;cc[2]*=1.5f;cc[3]*=1.5f;}
            check(SettingsLayout.floatHit(x+150*1.5f*k,y+20*1.5f*k,w,h,cells)==SettingsLayout.F_CELL+1&&SettingsLayout.floatHit(x+10*k,y+20*k,w,h,cells)==-1,"a layout's cells hit by their rectangles, the gear ring is no cell");}
        // the skin sheet: 9 tiles on the sheet, each big enough and hit by its middle; outside the sheet closes
        for(int i=0;i<12;i++){float[] b=SettingsLayout.tile4(i);check(b[2]<=SettingsLayout.SH_R-10&&b[3]<=SettingsLayout.SH_B-10&&b[2]-b[0]>=130&&b[3]-b[1]>=100,"look tile "+i+" big and on the sheet");
            check(SettingsLayout.hit4((b[0]+b[2])/2,(b[1]+b[3])/2,12)==i,"look tile "+i+" hits");}
        check(SettingsLayout.hit4(30,30,12)==-2&&SettingsLayout.hit4(SettingsLayout.SH_L+4,SettingsLayout.SH_T+10,12)==-1,"outside closes, the sheet's edge does nothing");
        // the 색감 row: 계기판 따라, 자동 / 낮 / 밤, the day look, the night look
        int[] row={SettingsLayout.F_AUTO,SettingsLayout.F_DN,SettingsLayout.F_DN+1,SettingsLayout.F_DN+2,SettingsLayout.F_SKIN_DAY,SettingsLayout.F_SKIN};
        for(int a=0;a<row.length;a++){float[] b=SettingsLayout.floatBox(row[a]);
            check(b!=null&&b[0]>=248&&b[2]<=788&&b[2]-b[0]>=48,"색감 row button "+a+" on the row, wide enough");
            if(a>0){float[] p=SettingsLayout.floatBox(row[a-1]);check(p[2]<b[0],"색감 row button "+a+" clear of the one before");}
            check(SettingsLayout.floatHit((b[0]+b[2])/2,(b[1]+b[3])/2,1,1,124,64)==row[a],"색감 row button "+a+" hits");}
        check(SettingsLayout.F_DN_NAMES.length==3&&SettingsLayout.SKIN_SHEET_DAY!=SettingsLayout.SKIN_SHEET&&SettingsLayout.SKIN_SHEET_DAY!=SettingsLayout.FORM_SHEET,"three choices, a day sheet of its own");
        // groups: 화면 · 주행 모드 · 플로팅 | 테마 · 차량 색 · 언어 | RPM · 엔진 사운드 · 효과음 | 진단 | 완료, a gap between groups
        check(SettingsLayout.CATS[SettingsLayout.SCREENS].equals("화면")&&SettingsLayout.CATS[SettingsLayout.MODE].equals("주행 모드")&&SettingsLayout.CATS[SettingsLayout.FLOAT].equals("플로팅")
            &&SettingsLayout.CATS[SettingsLayout.THEME].equals("테마")&&SettingsLayout.CATS[SettingsLayout.PAINT].equals("차량 색")&&SettingsLayout.CATS[SettingsLayout.LANG].equals("언어")
            &&SettingsLayout.CATS[SettingsLayout.RPM].equals("RPM")&&SettingsLayout.CATS[SettingsLayout.SOUND].equals("엔진 사운드")&&SettingsLayout.CATS[SettingsLayout.FX].equals("효과음")
            &&SettingsLayout.CATS[SettingsLayout.DIAG].equals("진단")&&SettingsLayout.CATS[SettingsLayout.FONT].equals("글꼴"),"categories in their groups");
        // 글꼴 pane: every target inside the pane, tall enough, apart from the others, and hit by its own centre
        for(int t=SettingsLayout.FONT_UP;t<SettingsLayout.FONT_PICK+SettingsLayout.FONT_ROWS;t++){float[] b=SettingsLayout.fontBox(t);if(b==null)continue;
            check(b[0]>=SettingsLayout.PANE_L&&b[2]<=SettingsLayout.PANE_R&&b[1]>=SettingsLayout.TOP&&b[3]<=SettingsLayout.BOTTOM&&b[3]-b[1]>=40,"font target "+t+" inside the pane, tappable");
            check(SettingsLayout.fontHit((b[0]+b[2])/2,(b[1]+b[3])/2)==t,"font target "+t+" hits itself");
            for(int u=t+1;u<SettingsLayout.FONT_PICK+SettingsLayout.FONT_ROWS;u++){float[] o=SettingsLayout.fontBox(u);if(o==null)continue;
                check(b[2]<=o[0]||o[2]<=b[0]||b[3]<=o[1]||o[3]<=b[1],"font targets "+t+" and "+u+" apart");}}
        check(SettingsLayout.fontBox(SettingsLayout.FONT_PICK+SettingsLayout.FONT_ROWS-1)[3]<SettingsLayout.FB_T&&SettingsLayout.FS_B<SettingsLayout.FA_T&&SettingsLayout.FP_B<SettingsLayout.FS_T,"font list above its buttons, preview above the state, state above apply");
        check(SettingsLayout.fontHit(SettingsLayout.CAT_L+10,SettingsLayout.TOP+10)==-1&&SettingsLayout.count(SettingsLayout.FONT)==0,"the font pane is its own layout");
        check(SettingsLayout.PICK>SettingsLayout.DONE,"the chooser is not a category");
        float step=SettingsLayout.CAT_H+SettingsLayout.CAT_GAP;
        check(SettingsLayout.catTop(1)-SettingsLayout.catTop(0)==step&&SettingsLayout.catTop(3)-SettingsLayout.catTop(2)==step+SettingsLayout.GROUP_GAP
            &&SettingsLayout.catTop(SettingsLayout.RPM)-SettingsLayout.catTop(SettingsLayout.RPM-1)==step+SettingsLayout.GROUP_GAP&&SettingsLayout.catTop(SettingsLayout.DIAG)-SettingsLayout.catTop(SettingsLayout.DIAG-1)==step+SettingsLayout.GROUP_GAP
            &&SettingsLayout.catTop(SettingsLayout.FONT)-SettingsLayout.catTop(SettingsLayout.LANG)==step,"a gap before each group; 글꼴 in the 테마 group");
        check(SettingsLayout.CAT_H>=31,"categories tall enough to tap (31 since 글꼴 made twelve rows)");
        // 효과음: the four sounds that are not the engine
        check(SettingsLayout.count(SettingsLayout.FX)==5&&SettingsLayout.count(SettingsLayout.SOUND)==(SettingsLayout.ENGINE_SOUND?SettingsLayout.SOUND_BOOST+1:1)&&SettingsLayout.SOUND_FAST==SettingsLayout.SOUND_LEAD+1&&SettingsLayout.SOUND_BOOST==SettingsLayout.SOUND_YIELD+1&&SettingsLayout.SOUND_YIELD==SettingsLayout.SOUND_FAST+1,"effects pane, engine sound pane ends at 양보 and 속도 따라 키우기");
        // 차량 색 shown by colour family: every paint once
        {boolean[] seen=new boolean[CarPaint.IDS.length];check(SettingsLayout.PAINT_ORDER.length==CarPaint.IDS.length,"a slot per paint");
            for(int p:SettingsLayout.PAINT_ORDER){check(p>=0&&p<seen.length&&!seen[p],"paint "+p+" once");seen[p]=true;}
            check(CarPaint.IDS[SettingsLayout.PAINT_ORDER[0]].equals("white")&&CarPaint.IDS[SettingsLayout.PAINT_ORDER[4]].equals("blue")&&CarPaint.IDS[SettingsLayout.PAINT_ORDER[8]].equals("red"),"white first, blues from 5, reds from 9");}
        // 화면 list: drag by the grip, drop rows clamp, auto-scroll at the edges
        check(SettingsLayout.onGrip(SettingsLayout.PANE_L+10)&&!SettingsLayout.onGrip(SettingsLayout.PANE_L+SettingsLayout.THUMB_W+2)&&!SettingsLayout.onGrip(SettingsLayout.PANE_L-4),"grip = the thumbnail column");
        check(SettingsLayout.dropRow(SettingsLayout.TOP+1,0)==0&&SettingsLayout.dropRow(SettingsLayout.TOP-50,0)==0&&SettingsLayout.dropRow(SettingsLayout.BOTTOM+50,SettingsLayout.maxScreenScroll())==ClusterPicker.COUNT-1
            &&SettingsLayout.dropRow(SettingsLayout.TOP+SettingsLayout.SCREEN_ROW_H*1.5f,0)==1,"drop rows clamp to the list");
        check(SettingsLayout.autoScroll(SettingsLayout.TOP+5)<0&&SettingsLayout.autoScroll(SettingsLayout.BOTTOM-5)>0&&SettingsLayout.autoScroll(260)==0,"auto-scroll near the edges only");
        check(SettingsLayout.scrolls(SettingsLayout.SCREENS)&&SettingsLayout.scrolls(SettingsLayout.SOUND)&&SettingsLayout.scrolls(SettingsLayout.RPM)&&!SettingsLayout.scrolls(SettingsLayout.THEME),"the panes that scroll");
        // 플로팅: size / transparency bars snap to their stops
        for(int i=0;i<4;i++)check(SettingsLayout.sliderStop(SettingsLayout.F_SIZE_BAR,SettingsLayout.F_SIZE_KNOB,4,SettingsLayout.sliderX(SettingsLayout.F_SIZE_BAR,SettingsLayout.F_SIZE_KNOB,4,i))==i,"size stop "+i);
        for(int i=0;i<3;i++)check(SettingsLayout.sliderStop(SettingsLayout.F_ALPHA_BAR,SettingsLayout.F_ALPHA_KNOB,3,SettingsLayout.sliderX(SettingsLayout.F_ALPHA_BAR,SettingsLayout.F_ALPHA_KNOB,3,i))==i,"alpha stop "+i);
        check(SettingsLayout.sliderStop(SettingsLayout.F_SIZE_BAR,SettingsLayout.F_SIZE_KNOB,4,0)==0&&SettingsLayout.sliderStop(SettingsLayout.F_SIZE_BAR,SettingsLayout.F_SIZE_KNOB,4,800)==3,"a finger off the bar ends keeps to the ends");
        check(SettingsLayout.floatHit(SettingsLayout.F_SIZE_BAR[0]+3,SettingsLayout.F_SIZE_BAR[1]+18,2,2,124,64)==SettingsLayout.F_SIZE&&SettingsLayout.floatHit(SettingsLayout.F_ALPHA_BAR[2]-3,SettingsLayout.F_ALPHA_BAR[1]+18,2,2,124,64)==SettingsLayout.F_ALPHA,"the bars hit anywhere along them");
        check(SettingsLayout.floatBox(SettingsLayout.F_MUSIC)[0]>=SettingsLayout.FC_L&&SettingsLayout.floatBox(SettingsLayout.F_MUSIC)[1]>=SettingsLayout.F_HIDE_BOX[3],"음악 띠 in the right column, under the button hiding");
        check(SettingsLayout.cols(SettingsLayout.THEME)==3,"themes in three columns");
        check(SettingsLayout.top(SettingsLayout.THEME,Theme.IDS.length-1)+SettingsLayout.itemH(SettingsLayout.THEME)<=SettingsLayout.BOTTOM&&SettingsLayout.itemH(SettingsLayout.THEME)>=44,"20 themes fit without scrolling, rows still tappable");
        // − / + rows: wide buttons, a gap goes to the nearer one, the value does nothing, the label resets
        {float m=SettingsLayout.stepMinusL(),p=SettingsLayout.stepPlusL(),w=SettingsLayout.STEP_W;
            check(SettingsLayout.stepZone(m+w/2)==1&&SettingsLayout.stepZone(p+w/2)==2&&SettingsLayout.stepZone(m-6)==1,"− and + by their centres, a little left of − is −");
            check(SettingsLayout.stepZone((m+w+p)/2-1)==1&&SettingsLayout.stepZone((m+w+p)/2+1)==2,"the gap between goes to the nearer button");
            check(SettingsLayout.stepZone(m-60)==-1&&SettingsLayout.stepZone(SettingsLayout.PANE_L+30)==0,"the value does nothing, the label resets");
            check(w>=64&&SettingsLayout.STEP_H>=40&&SettingsLayout.STEP_H<=SettingsLayout.SOUND_ROW_H-8,"buttons at least 64 x 40, with room above and below in a sound row");}
        System.out.println("PASS "+checks+" settings layout checks");
    }
}
