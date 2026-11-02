package com.veloster.vehicle;

/**
 * Settings (the gear): categories down the left, the chosen category's choices on the right. PitRenderer draws it and
 * VehicleDash hit-tests it; a choice applies at once. No Android: host-tested.
 */
public final class SettingsLayout {
    public static final int SCREENS=0,THEME=1,PAINT=2,MODE=3,LANG=4,DIAG=5,DONE=6;
    /** Not a category: the screen chooser a mode row opens (every screen, then 원래 화면), under a title line. */
    public static final int PICK=7;
    /** Mode pane rows: the two switches, then one row per ClusterPicker.MODES. */
    public static final int MODE_COLOR=0,MODE_SCREEN=1,MODE_FIRST=2;
    public static final float TITLE_H=30;
    public static final String[] CATS={"화면","테마","차량 색","주행 모드","언어","진단","완료"};
    /** Display names for Theme.IDS, in that order. */
    public static final String[] THEME_NAMES={"N","람보","BMW","아이스","모노","앰버","틸","나이트","로즈","코퍼"};
    /** Language choices (LanguageSettings ids) and their names. */
    public static final String[] LANGS={"auto","ko","en"},LANG_NAMES={"시스템","한국어","English"};
    public static final float TOP=60,BOTTOM=472,CAT_L=12,CAT_W=152,CAT_H=51,GAP=6,PANE_L=178,PANE_R=788,MAX_ITEM_H=72;
    private SettingsLayout(){}

    public static float catTop(int i){return TOP+i*(CAT_H+GAP);}
    /** Category under the point, or -1. */
    public static int cat(float x,float y){
        if(x<CAT_L||x>CAT_L+CAT_W)return -1;
        for(int i=0;i<CATS.length;i++){float t=catTop(i);if(y>=t&&y<=t+CAT_H)return i;}
        return -1;
    }

    /** Choices in a category's pane (완료 has none). */
    public static int count(int cat){
        switch(cat){case SCREENS:return ClusterPicker.COUNT;case THEME:return Theme.IDS.length;case PAINT:return CarPaint.IDS.length;
            case MODE:return MODE_FIRST+ClusterPicker.MODES.length;case LANG:return LANGS.length;case DIAG:return 1;
            case PICK:return ClusterPicker.COUNT+1;default:return 0;}
    }
    public static int cols(int cat){return cat==SCREENS||cat==PICK?4:cat==THEME||cat==PAINT?2:1;}
    private static float paneTop(int cat){return cat==PICK?TOP+TITLE_H:TOP;}
    private static int rows(int cat){return (count(cat)+cols(cat)-1)/cols(cat);}
    public static float itemW(int cat){int n=cols(cat);return (PANE_R-PANE_L-GAP*(n-1))/n;}
    public static float itemH(int cat){int r=Math.max(1,rows(cat));return Math.min(MAX_ITEM_H,(BOTTOM-paneTop(cat)-GAP*(r-1))/r);}
    public static float left(int cat,int i){return PANE_L+(i%cols(cat))*(itemW(cat)+GAP);}
    public static float top(int cat,int i){return paneTop(cat)+(i/cols(cat))*(itemH(cat)+GAP);}
    /** Choice under the point in cat's pane, or -1. */
    public static int item(int cat,float x,float y){
        for(int i=0;i<count(cat);i++){float l=left(cat,i),t=top(cat,i);if(x>=l&&x<=l+itemW(cat)&&y>=t&&y<=t+itemH(cat))return i;}
        return -1;
    }
}
