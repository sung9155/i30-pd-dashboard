package com.veloster.vehicle;

/**
 * Settings (the gear): categories down the left, the chosen category's choices on the right. PitRenderer draws it and
 * VehicleDash hit-tests it; a choice applies at once. No Android: host-tested.
 */
public final class SettingsLayout {
    public static final int SCREENS=0,THEME=1,PAINT=2,MODE=3,RPM=4,SOUND=5,LANG=6,DIAG=7,FLOAT=8,DONE=9;
    /** 플로팅 pane rows: the window's shape (FloatPanel.STYLE_NAMES, cycles), whether it hides 내 앱's floating button, then
     *  what each of its four cells shows (cycles 자동 and FloatPanel.CHOICES). */
    public static final int FLOAT_STYLE=0,FLOAT_HIDE=1,FLOAT_CELL=2;
    /** Not a category: the screen chooser a mode row opens (every screen, then 원래 화면), under a title line. */
    public static final int PICK=10;
    /** RPM pane rows: RPM_SHIFT/RPM_RED and RPM_BEEP_VOL have − and + at the right (screenZone 1 and 2), the rest of the
     *  row resets it; RPM_BEEP is an on/off switch (the optional shift beep, ShiftBeep), RPM_BEEP_SOUND cycles its sound,
     *  RPM_BEEP_VOL is the beep volume in percent (the downshift sounds too). RPM_DOWN_SCOPE cycles DOWN_SCOPES, the
     *  screens with the downshift sounds (and the shift beep held while the clutch is in); RPM_DOWN1..+2 pick the sound
     *  for a blip matched one, two or three gears down (cycles 끔 and BEEP_SOUNDS). */
    public static final int RPM_SHIFT=0,RPM_RED=1,RPM_BEEP=2,RPM_BEEP_SOUND=3,RPM_BEEP_VOL=4,RPM_DOWN_SCOPE=5,RPM_DOWN1=6;
    public static final String[] DOWN_SCOPES={"람다만","람다 · 레이스 · F1 · 퍼포먼스","모든 화면"};
    /** Whether screen (ClusterPicker index) is in downshift-sound scope s: 람다 always, the rev-match screens (F1,
     *  퍼포먼스, 레이스: their rpm turns green on a match too) from 1, every screen at 2. */
    public static boolean downScreen(int s,int screen){
        return s>=2||screen==ClusterPicker.MANUAL||s==1&&screen>=ClusterPicker.F1&&screen<=ClusterPicker.F1+2;
    }
    /** Shift beep sounds, in ShiftBeep.SOUNDS order. */
    public static final String[] BEEP_SOUNDS={"삐빅","삑","상승 3음","레이스 버저","틱틱",
        "삐빅 · 3번","삐빅 · 4번 빠르게","연타 · 5번","삐빅 · 낮게","삐빅 · 높게","두 번 · 길고 짧게","상승 2음","하강 2음","하강 3음","빠른 상승 4음","5도 위로","옥타브 위로","맑은 차임","두 음 차임","낮은 차임","유리 톡","디지털 3틱","짧은 펄스 · 낮게","긴 비프","낮은 톤"};
    /** Default downshift sounds, one/two/three gears down: the count tells the gear (2, 3, 4 beeps). */
    public static final int[] DOWN_DEFAULT={0,5,6};
    /** Sound pane rows: the virtual engine sound switch, its output route (cycles), and its volume with − and +. */
    /** Virtual engine sound (off 2026-11-04, back 2026-11-05 at the user's word). false: the 사운드 pane shows one dimmed
     *  line and the sound never starts, whatever was saved. ponytail: a flag, not a removal. */
    public static final boolean ENGINE_SOUND=true;
    public static final int SOUND_ON=0,SOUND_ROUTE=1,SOUND_ENGINE=2,SOUND_EXHAUST=3,SOUND_VOLUME=4,SOUND_POP=5,SOUND_TURBO=6,SOUND_BOV=7,SOUND_LEAD=8,SOUND_COIN=9,SOUND_FAST=10,SOUND_CLICK=11,SOUND_CHIME=12,SOUND_CHIME_SOUND=13;
    /** Rows from SOUND_VOLUME on have − and + (screenZone 1 and 2); the rest of the row puts the default back. */
    public static final int[] SOUND_MIN={10,0,0,0,0},SOUND_MAX={200,200,200,200,150},SOUND_STEP={10,10,10,10,10},SOUND_DEFAULT={70,100,100,100,80};
    /** EngineSynth.ENGINES and EXHAUSTS names, in their order. */
    public static final String[] SOUND_ENGINES={"1.6 터보 4기통","직렬 6기통 터보","V6 트윈터보","V8 크로스플레인","V8 플랫플레인"};
    /** Engines with a turbo (whistle and blow-off rows apply), per SOUND_ENGINES. */
    public static final boolean[] SOUND_HAS_TURBO={true,true,true,false,false};
    public static final String[] SOUND_EXHAUSTS={"순정","스포츠 머플러","중통 제거","풀 배기 · 팝앤뱅"};
    /** A − / + row's value after a tap in zone z (1 −, 2 +, 0 default). */
    public static int soundStep(int row,int value,int z){
        int k=row-SOUND_VOLUME;
        return z==1?Math.max(SOUND_MIN[k],value-SOUND_STEP[k]):z==2?Math.min(SOUND_MAX[k],value+SOUND_STEP[k]):SOUND_DEFAULT[k];
    }
    /** Output routes the 출력 경로 row cycles through, in EngineSound's route order. */
    /** Checked in the car 2026-11-03: only the alarm channel sounds (front speakers, no bass); the others stay silent on
     *  this firmware and are kept for other head units. */
    public static final String[] SOUND_ROUTES={"알림 채널 · 앞 스피커","내비 효과음 · 차에서 무음","미디어 소스 · 차에서 무음","안드로이드 기본 · 차에서 무음"};
    /** Mode pane rows: the two switches, then one row per ClusterPicker.MODES. */
    public static final int MODE_COLOR=0,MODE_SCREEN=1,MODE_FIRST=2;
    public static final float TITLE_H=30;
    public static final String[] CATS={"화면","테마","차량 색","주행 모드","RPM","사운드","언어","진단","플로팅","완료"};
    /** Display names for Theme.IDS, in that order. */
    public static final String[] THEME_NAMES={"N","레드","와인","코퍼","앰버","람보","그린","에메랄드","틸","아이스","BMW","나이트","퍼플","마젠타","로즈","모노"};
    /** Language choices (LanguageSettings ids) and their names. */
    public static final String[] LANGS={"auto","ko","en"},LANG_NAMES={"시스템","한국어","English"};
    public static final float TOP=60,BOTTOM=472,CAT_L=12,CAT_W=152,CAT_H=37,CAT_GAP=4,GAP=6,PANE_L=178,PANE_R=788,MAX_ITEM_H=72;
    private SettingsLayout(){}

    public static float catTop(int i){return TOP+i*(CAT_H+CAT_GAP);}
    /** Category under the point, or -1. */
    public static int cat(float x,float y){
        if(x<CAT_L||x>CAT_L+CAT_W)return -1;
        for(int i=0;i<CATS.length;i++){float t=catTop(i);if(y>=t&&y<=t+CAT_H)return i;}
        return -1;
    }

    /** Choices in a category's pane (완료 has none). */
    public static int count(int cat){
        switch(cat){case SCREENS:return ClusterPicker.COUNT;case THEME:return Theme.IDS.length;case PAINT:return CarPaint.IDS.length+1;   // the last tile is the 8-bit sky (Weather)
            case MODE:return MODE_FIRST+ClusterPicker.MODES.length;case RPM:return RPM_DOWN1+3;case SOUND:return ENGINE_SOUND?SOUND_CHIME_SOUND+1:1;case LANG:return LANGS.length;case DIAG:return 4;case FLOAT:return FLOAT_CELL+4;
            case PICK:return ClusterPicker.COUNT+1;default:return 0;}
    }
    public static int cols(int cat){return cat==SCREENS||cat==PICK?4:cat==THEME||cat==PAINT?2:1;}
    private static float paneTop(int cat){return cat==PICK?TOP+TITLE_H:TOP;}
    private static int rows(int cat){return (count(cat)+cols(cat)-1)/cols(cat);}
    public static float itemW(int cat){int n=cols(cat);return (PANE_R-PANE_L-GAP*(n-1))/n;}
    /** The 사운드 pane keeps a fixed, comfortably tappable row and scrolls when the rows overflow; every other pane fits its rows to the height. */
    public static final float SOUND_ROW_H=46;
    public static float itemH(int cat){if(cat==SOUND)return SOUND_ROW_H;int r=Math.max(1,rows(cat));return Math.min(MAX_ITEM_H,(BOTTOM-paneTop(cat)-GAP*(r-1))/r);}
    public static float left(int cat,int i){return PANE_L+(i%cols(cat))*(itemW(cat)+GAP);}
    public static float top(int cat,int i){return paneTop(cat)+(i/cols(cat))*(itemH(cat)+GAP);}
    /** How far a pane can scroll (0 = it fits). The 화면 list and the 사운드 pane overflow; the rest fit. */
    public static float maxPaneScroll(int cat){
        if(cat==SCREENS)return maxScreenScroll();
        int n=count(cat);if(n==0)return 0;
        return Math.max(0,top(cat,n-1)+itemH(cat)-BOTTOM);
    }

    // --- SCREENS as a reorderable, scrollable list (one row per screen, in display order) ---
    public static final float SCREEN_ROW_H=62,THUMB_W=104,SBTN_W=46;
    public static float maxScreenScroll(){return Math.max(0,ClusterPicker.COUNT*SCREEN_ROW_H-(BOTTOM-TOP));}
    public static float upBtnL(){return PANE_R-2*SBTN_W-14;}
    public static float downBtnL(){return PANE_R-SBTN_W-6;}
    /** Display position of the screen row under y given the scroll, or -1. */
    public static int screenRow(float y,float scroll){
        if(y<TOP||y>BOTTOM)return -1;
        int p=(int)((y-TOP+scroll)/SCREEN_ROW_H);
        return p>=0&&p<ClusterPicker.COUNT?p:-1;
    }
    /** For an x inside a screen row: 1 = move up, 2 = move down, 0 = toggle (the row body). */
    public static int screenZone(float x){
        if(x>=upBtnL()&&x<upBtnL()+SBTN_W)return 1;
        if(x>=downBtnL()&&x<downBtnL()+SBTN_W)return 2;
        return 0;
    }
    /** Choice under the point in cat's pane, or -1. */
    public static int item(int cat,float x,float y){return item(cat,x,y,0);}
    /** Choice under the point given the pane's scroll, or -1 (nothing outside the TOP..BOTTOM viewport). */
    public static int item(int cat,float x,float y,float scroll){
        if(y<TOP||y>BOTTOM)return -1;
        for(int i=0;i<count(cat);i++){float l=left(cat,i),t=top(cat,i)-scroll;if(x>=l&&x<=l+itemW(cat)&&y>=t&&y<=t+itemH(cat))return i;}
        return -1;
    }
}
