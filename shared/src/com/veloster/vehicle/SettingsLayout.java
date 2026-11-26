package com.veloster.vehicle;

/**
 * Settings (the gear): categories down the left, the chosen category's choices on the right. PitRenderer draws it and
 * VehicleDash hit-tests it; a choice applies at once. No Android: host-tested.
 */
public final class SettingsLayout {
    /** Category order = the column top to bottom, in groups (a GROUP_GAP before each of GROUP_STARTS): 화면 · 주행 모드 · 플로팅 |
     *  테마 · 차량 색 · 언어 | RPM · 엔진 사운드 · 효과음 | 진단 | 완료. */
    public static final int SCREENS=0,MODE=1,FLOAT=2,THEME=3,PAINT=4,LANG=5,RPM=6,SOUND=7,FX=8,DIAG=9,DONE=10;
    public static final int[] GROUP_STARTS={THEME,RPM,DIAG,DONE};
    /** 플로팅 pane (its own layout, see the float* methods below): a preview of the window top left (a tap on a cell
     *  opens that cell's chooser), the on switch, 내 앱 button hiding and the chosen cell top right, then one row of
     *  buttons each for the shape, cell count and size, corners and colours. Every button sets its value directly. */
    public static final int FLOAT_STYLE=0,FLOAT_COUNT=1,FLOAT_SIZE=2,FLOAT_HIDE=3,FLOAT_CELL=4;
    /** Not a category: the screen chooser a mode row opens (every screen, then 원래 화면), under a title line. */
    public static final int PICK=11;
    /** RPM pane rows: RPM_SHIFT/RPM_RED and RPM_BEEP_VOL have − and + at the right (screenZone 1 and 2), the rest of the
     *  row resets it; RPM_BEEP is an on/off switch (the optional shift beep, ShiftBeep), RPM_BEEP_SOUND cycles its sound,
     *  RPM_BEEP_VOL is the beep volume in percent (the downshift sounds too). RPM_DOWN_SCOPE cycles DOWN_SCOPES, the
     *  screens with the downshift sounds (and the shift beep held while the clutch is in); RPM_DOWN1..+2 pick the sound
     *  for a blip matched one, two or three gears down (cycles 끔 and BEEP_SOUNDS). */
    public static final int RPM_SHIFT=0,RPM_RED=1,RPM_BEEP=2,RPM_BEEP_SOUND=3,RPM_BEEP_VOL=4,RPM_DOWN_SCOPE=5,RPM_DOWN1=6;
    /** 변속 알림음's third step, 항상 (pref float_beep): the beep and the downshift sounds also while only the floating
     *  window is up (VehicleDash.shiftTick). Set with the beep on; the row steps 끔 -> 계기판 -> 항상 -> 끔. */
    static volatile boolean floatBeep;
    /** 엔진 사운드 = 항상: it goes on while the floating window is up (the dashboard away), not only on the dashboard. */
    static volatile boolean floatSound;
    public static final String[] DOWN_SCOPES={"람다 · RPM","람다 · RPM · 레이스 · F1 · 퍼포먼스","모든 화면"};
    /** Whether screen (ClusterPicker index) is in downshift-sound scope s: 람다 and RPM (its dial shows the downshift targets) always, the rev-match screens (F1,
     *  퍼포먼스, 레이스: their rpm turns green on a match too) from 1, every screen at 2. */
    public static boolean downScreen(int s,int screen){
        return s>=2||screen==ClusterPicker.MANUAL||screen==ClusterPicker.TACHO||s==1&&screen>=ClusterPicker.F1&&screen<=ClusterPicker.F1+2;
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
    public static final int SOUND_ON=0,SOUND_ROUTE=1,SOUND_ENGINE=2,SOUND_EXHAUST=3,SOUND_VOLUME=4,SOUND_MIX=5,SOUND_POP=6,SOUND_TURBO=7,SOUND_BOV=8,SOUND_LEAD=9,SOUND_FAST=10,SOUND_YIELD=11,SOUND_BOOST=12;
    /** 안내·통화 양보 (SoundYield.OFF, DUCK, STOP) and 속도 따라 키우기 (SoundYield.speedBoost levels 0..3). */
    public static final String[] SOUND_YIELDS={"끔","안내 때 줄이기","안내 때 멈추기"},SOUND_BOOSTS={"끔","약 (+3 dB)","중 (+6 dB)","강 (+9 dB)"};
    /** 효과음 pane rows: sounds that are not the engine (each its own switch, independent of ENGINE_SOUND). */
    public static final int FX_CLICK=0,FX_COIN=1,FX_CHIME=2,FX_CHIME_SOUND=3,FX_VOL=4;
    /** 효과음 volume in percent (0..200, − / + on FX_VOL): coin, cassette click and speed chime; the shift and downshift beeps keep RPM_BEEP_VOL. */
    public static volatile int fxVol=70;
    /** Rows SOUND_VOLUME..SOUND_LEAD have − and + (screenZone 1 and 2); the rest of the row puts the default back.
     *  SOUND_MIX (엔진음): the engine against the turbo whistle and blow-off, 0 % leaves only those two. */
    public static final int[] SOUND_MIN={10,0,0,0,0,0},SOUND_MAX={200,100,200,200,200,150},SOUND_STEP={10,10,10,10,10,10},SOUND_DEFAULT={70,100,100,100,100,80};
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
    public static final String[] CATS={"화면","주행 모드","플로팅","테마","차량 색","언어","RPM","엔진 사운드","효과음","진단","완료"};
    /** 차량 색 tiles by colour family, four to a family (two rows of the 2-column grid): white, silver, grey, black | the blues |
     *  red, orange, copper, brown | khaki, purple. Values are CarPaint indexes (its order stays: saved ids and the Arcade cars use it). */
    public static final int[] PAINT_ORDER={1,10,4,3, 0,9,7,5, 2,8,11,6, 13,12};
    /** Display names for Theme.IDS, in that order. */
    public static final String[] THEME_NAMES={"N","레드","와인","코퍼","앰버","샴페인","네이비","람보","그린","세이지","에메랄드","틸","아이스","그라파이트","BMW","나이트","퍼플","마젠타","로즈","모노"};
    /** Language choices (LanguageSettings ids) and their names. */
    public static final String[] LANGS={"auto","ko","en"},LANG_NAMES={"시스템","한국어","English"};
    public static final float TOP=60,BOTTOM=472,CAT_L=12,CAT_W=152,CAT_H=33,CAT_GAP=2,GROUP_GAP=6,GAP=6,PANE_L=178,PANE_R=788,MAX_ITEM_H=72;
    private SettingsLayout(){}

    public static float catTop(int i){int g=0;for(int s:GROUP_STARTS)if(i>=s)g++;return TOP+i*(CAT_H+CAT_GAP)+g*GROUP_GAP;}
    /** Category under the point, or -1. */
    public static int cat(float x,float y){
        if(x<CAT_L||x>CAT_L+CAT_W)return -1;
        for(int i=0;i<CATS.length;i++){float t=catTop(i);if(y>=t&&y<=t+CAT_H)return i;}
        return -1;
    }

    /** Choices in a category's pane (완료 has none). */
    public static int count(int cat){
        switch(cat){case SCREENS:return ClusterPicker.COUNT;case THEME:return Theme.IDS.length;case PAINT:return CarPaint.IDS.length+1;   // the last tile is the 8-bit sky (Weather)
            case MODE:return MODE_FIRST+ClusterPicker.MODES.length;case RPM:return RPM_DOWN1+3;case SOUND:return ENGINE_SOUND?SOUND_BOOST+1:1;case FX:return FX_VOL+1;case LANG:return LANGS.length;case DIAG:return 5;case FLOAT:return FLOAT_CELL+4;
            case PICK:return ClusterPicker.COUNT+1;default:return 0;}
    }
    public static int cols(int cat){return cat==SCREENS||cat==PICK?4:cat==THEME?3:cat==PAINT?2:1;}
    private static float paneTop(int cat){return cat==PICK?TOP+TITLE_H:TOP;}
    private static int rows(int cat){return (count(cat)+cols(cat)-1)/cols(cat);}
    public static float itemW(int cat){int n=cols(cat);return (PANE_R-PANE_L-GAP*(n-1))/n;}
    /** The 사운드 and RPM panes (the − / + rows) keep a fixed, comfortably tappable row and scroll when the rows overflow; every other pane fits its rows to the height. */
    public static final float SOUND_ROW_H=56;
    public static boolean rowScroll(int cat){return cat==SOUND||cat==RPM;}
    public static float itemH(int cat){if(rowScroll(cat))return SOUND_ROW_H;int r=Math.max(1,rows(cat));return Math.min(MAX_ITEM_H,(BOTTOM-paneTop(cat)-GAP*(r-1))/r);}
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
    /** The panes whose rows can overflow (they draw the scroll bar and the "more below" hint). */
    public static boolean scrolls(int cat){return cat==SCREENS||rowScroll(cat);}
    /** Rows are dragged by the grip column (the grip and the thumbnail); the rest of the row scrolls or taps. */
    public static boolean onGrip(float x){return x>=PANE_L&&x<PANE_L+THUMB_W;}
    /** The display position a dragged row lands on for a finger at y, clamped to the list. */
    public static int dropRow(float y,float scroll){return Math.max(0,Math.min(ClusterPicker.COUNT-1,(int)Math.floor((y-TOP+scroll)/SCREEN_ROW_H)));}
    /** Scroll step per tick while a row is held near the top or bottom edge (40 px), else 0. */
    public static float autoScroll(float y){return y<TOP+40?-8:y>BOTTOM-40?8:0;}
    public static float maxScreenScroll(){return Math.max(0,ClusterPicker.COUNT*SCREEN_ROW_H-(BOTTOM-TOP));}
    public static float upBtnL(){return PANE_R-2*SBTN_W-14;}
    public static float downBtnL(){return PANE_R-SBTN_W-6;}
    /** Display position of the screen row under y given the scroll, or -1. */
    public static int screenRow(float y,float scroll){
        if(y<TOP||y>BOTTOM)return -1;
        int p=(int)((y-TOP+scroll)/SCREEN_ROW_H);
        return p>=0&&p<ClusterPicker.COUNT?p:-1;
    }
    /** − / + rows (RPM, 엔진 사운드, 효과음): two wide buttons STEP_W x STEP_H at the right. A finger that lands between them
     *  goes to the nearer one, a little left of − still counts as −; the label half of the row puts the default back (0);
     *  anywhere else (the value) does nothing (-1), so a near miss never resets a volume (2026-11-26: the 46 px squares
     *  sat edge to edge with the next row's, and a miss reset the value). */
    public static final float STEP_W=70,STEP_H=44;
    public static float stepMinusL(){return PANE_R-2*STEP_W-12;}
    public static float stepPlusL(){return PANE_R-STEP_W-4;}
    public static int stepZone(float x){
        float mid=(stepMinusL()+STEP_W+stepPlusL())/2;
        if(x>=stepMinusL()-12&&x<mid)return 1;
        if(x>=mid&&x<=PANE_R)return 2;
        return x<PANE_L+(PANE_R-PANE_L)*0.45f?0:-1;
    }
    /** For an x inside a screen row: 1 = move up, 2 = move down, 0 = toggle (the row body). */
    public static int screenZone(float x){
        if(x>=upBtnL()&&x<upBtnL()+SBTN_W)return 1;
        if(x>=downBtnL()&&x<downBtnL()+SBTN_W)return 2;
        return 0;
    }
    // --- 플로팅 pane ---
    /** The preview well (the window drawn at its real size, or smaller when it does not fit), its caption below it. */
    public static final float FW_L=PANE_L,FW_T=TOP+2,FW_R=566,FW_B=252,FW_CAPTION=16;
    /** The right column: the on switch, the button-hiding switch, and the chosen cell's box with its change button. */
    public static final float FC_L=576,FC_R=PANE_R;
    public static final float[] F_ON_BOX={FC_L,FW_T,FC_R,FW_T+38},F_HIDE_BOX={FC_L,FW_T+42,FC_R,FW_T+78},F_MUSIC_BOX={FC_L,FW_T+82,FC_R,FW_T+118},
        F_CELL_BOX={FC_L,FW_T+124,FC_R,FW_B},F_EDIT_BOX={FC_L+12,FW_B-42,FC_R-12,FW_B-6};
    /** Button rows under the preview: label at PANE_L+6, buttons from F_ROW_X; ROW_H tall, F_GAP apart. */
    public static final float F_ROW_X=PANE_L+70,F_ROW_H=36,F_GAP=8;
    public static final float[] F_ROW_Y={262,306,350,394};
    public static final String[] F_ROW_LABELS={"모양","색감","크기","추천"};
    /** Tap targets: on, hide, edit (the chosen cell), a preview cell (+ k), then each row's buttons (+ index). */
    public static final int F_ON=1,F_HIDE=2,F_EDIT=3,F_MUSIC=7,F_CELL=10,F_SHAPE=20,F_COUNT=30,F_SIZE=40,F_CORNER=50,F_COLOR=60,F_ALPHA=70,F_SKIN=4,F_SKIN_DAY=8,F_DN=90,SKIN_SHEET=100,SKIN_SHEET_DAY=102,F_FORM=5,FORM_SHEET=101,F_AUTO=6,F_PRESET=80;
    /** 크기 and 투명도 as two bars on one row, each snapping to its stops; the knob is a chip with the value's name. */
    public static final float[] F_SIZE_BAR={248,F_ROW_Y[2],500,F_ROW_Y[2]+F_ROW_H},F_ALPHA_BAR={566,F_ROW_Y[2],788,F_ROW_Y[2]+F_ROW_H};
    public static final float F_SIZE_KNOB=78,F_ALPHA_KNOB=66,F_ALPHA_LABEL_X=512;
    /** Knob centre for stop i of stops. */
    public static float sliderX(float[] bar,float knob,int stops,int i){float a=bar[0]+knob/2,b=bar[2]-knob/2;return stops<2?a:a+(b-a)*i/(stops-1);}
    /** The stop nearest x (a finger past either end keeps to that end). */
    public static int sliderStop(float[] bar,float knob,int stops,float x){float a=bar[0]+knob/2,b=bar[2]-knob/2;if(stops<2)return 0;
        return Math.max(0,Math.min(stops-1,Math.round((x-a)/((b-a)/(stops-1)))));}
    /** 추천 row: one tap sets shape, count, look, size and transparency together (FloatPanel.applyPreset). */
    public static final String[] F_PRESETS={"기본","레이스","미니","음악"};
    public static final float F_PRESET_X=248,F_PRESET_W=129;
    /** 모양 at the start of the first row: opens the shape sheet (FloatPanel.FORMS). */
    public static final float[] F_FORM_BOX={248,262,470,298};
    /** The 색감 row: the 계기판 따라 switch, 자동 / 낮 / 밤 (F_DN + DayNight value), then the day and the night look chips that open
     *  their look sheet (VehicleDash.floatEdit = SKIN_SHEET_DAY / SKIN_SHEET). */
    public static final float[] F_AUTO_BOX={248,306,380,342},F_SKIN_DAY_BOX={556,306,668,342},F_SKIN_BOX={676,306,788,342};
    public static final String[] F_DN_NAMES={"자동","낮","밤"};
    /** 칸 수 sits on the 모양 row, right of the shape chip: the shape decides which counts there are. */
    public static final float F_COUNT_X=540;
    public static final String[] F_SHAPES={"카드","가로 띠","세로 띠"},F_COUNTS={"1","2","4"},
        F_SIZES={"작게","보통","크게","아주 크게"},F_CORNERS={"테마","직각","둥글게","사선 컷"},F_COLORS={"테마","파랑","흰색"},F_ALPHAS={"진하게","보통","연하게"};
    private static final float[] W_SHAPE={92,92,92},W_COUNT={52,52,52},W_CORNER={100,74,82,112},W_COLOR={100,100,74};
    /** The button for target t ({l, t, r, b}), or null (preview cells are placed by floatPreview*). */
    public static float[] floatBox(int t){
        if(t==F_SIZE)return F_SIZE_BAR;if(t==F_ALPHA)return F_ALPHA_BAR;
        if(t>=F_PRESET&&t<F_PRESET+F_PRESETS.length){float x=F_PRESET_X+(t-F_PRESET)*(F_PRESET_W+F_GAP);return new float[]{x,F_ROW_Y[3],x+F_PRESET_W,F_ROW_Y[3]+F_ROW_H};}
        if(t>=F_DN&&t<F_DN+F_DN_NAMES.length){float x=388+(t-F_DN)*56;return new float[]{x,306,x+52,342};}if(t==F_SKIN_DAY)return F_SKIN_DAY_BOX;   // 자동 / 낮 / 밤 52 wide: the car tap minimum
        if(t==F_FORM)return F_FORM_BOX;if(t==F_MUSIC)return F_MUSIC_BOX;if(t==F_SKIN)return F_SKIN_BOX;if(t==F_AUTO)return F_AUTO_BOX;if(t==F_ON)return F_ON_BOX;if(t==F_HIDE)return F_HIDE_BOX;if(t==F_EDIT)return F_EDIT_BOX;
        int g=t/10*10,i=t-g;float[] w;float x,y;
        switch(g){case F_SHAPE:w=W_SHAPE;x=F_ROW_X;y=F_ROW_Y[0];break;case F_COUNT:w=W_COUNT;x=F_COUNT_X;y=F_ROW_Y[0];break;
            case F_CORNER:w=W_CORNER;x=F_ROW_X;y=F_ROW_Y[2];break;
            case F_COLOR:w=W_COLOR;x=F_ROW_X;y=F_ROW_Y[3];break;default:return null;}
        if(i<0||i>=w.length)return null;
        for(int k=0;k<i;k++)x+=w[k]+F_GAP;
        return new float[]{x,y,x+w[i],y+F_ROW_H};
    }
    /** Every button target in drawing order. */
    public static final int[] F_TARGETS={F_ON,F_HIDE,F_EDIT,F_SKIN,F_FORM,F_AUTO,F_DN,F_DN+1,F_DN+2,F_SKIN_DAY,F_MUSIC,F_COUNT,F_COUNT+1,F_COUNT+2,F_SIZE,F_ALPHA,
        F_PRESET,F_PRESET+1,F_PRESET+2,F_PRESET+3};
    /** The preview's scale for a window w x h: 1, or less so it fits the well with an 8 px margin. */
    public static float floatPreviewScale(float w,float h){return Math.min(1,Math.min((FW_R-FW_L-16)/w,(FW_B-FW_CAPTION-FW_T-12)/h));}
    public static float floatPreviewX(float w,float h){return (FW_L+FW_R)/2-w*floatPreviewScale(w,h)/2;}
    public static float floatPreviewY(float w,float h){return (FW_T+FW_B-FW_CAPTION)/2-h*floatPreviewScale(w,h)/2;}
    /** The target under x,y on the 플로팅 pane, or -1. The preview has cols x rows cells of cw x ch at size 1. */
    public static int floatHit(float x,float y,int cols,int rows,float cw,float ch){
        float[][] cells=new float[cols*rows][];for(int i=0;i<cells.length;i++)cells[i]=new float[]{(i%cols)*cw,(i/cols)*ch,(i%cols+1)*cw,(i/cols+1)*ch};
        return floatHit(x,y,cols*cw,rows*ch,cells);
    }
    /** The same for any window w x h whose cells are these rectangles (FloatPanel.cellRects). */
    public static int floatHit(float x,float y,float w,float h,float[][] cells){
        for(int t:F_TARGETS){float[] b=floatBox(t);if(x>=b[0]&&x<=b[2]&&y>=b[1]&&y<=b[3])return t;}
        float k=floatPreviewScale(w,h),px=floatPreviewX(w,h),py=floatPreviewY(w,h),lx=(x-px)/k,ly=(y-py)/k;
        for(int i=0;i<cells.length;i++){float[] b=cells[i];if(lx>=b[0]&&lx<b[2]&&ly>=b[1]&&ly<b[3])return F_CELL+i;}
        return -1;
    }

    /** The cell chooser sheet over the whole screen: a column of tabs on the left (자동, then Tiles.GROUPS that this car
     *  has), the open group's readings in S_COLS columns, the graph buttons under them, 취소 / 완료. */
    public static final float SH_L=96,SH_T=24,SH_R=704,SH_B=466;
    public static final float S_TAB_X=110,S_TAB_W=106,S_TAB_Y=104,S_TAB_H=30,S_TAB_GAP=4;
    public static final float S_GX=228,S_GY=104,S_GW=460,S_ROW_H=40,S_VGAP=5,S_HGAP=6;
    public static final int S_COLS=3,S_MAX_VALUES=15,S_VAL=100,S_GRAPH=200,S_CANCEL=300,S_DONE=301,S_TAB=400,S_IN=0;
    public static final float S_GRAPH_Y=334,S_GRAPH_H=50,S_GRAPH_X=S_GX,S_GRAPH_W=107,S_GRAPH_GAP=10;
    public static final float[] S_CANCEL_BOX={440,410,560,452},S_DONE_BOX={568,410,688,452};
    /** The cell preview at the sheet's top right (one cell at size 1). */
    public static final float S_CELL_X=560,S_CELL_Y=32;
    public static float[] sheetTab(int i){float y=S_TAB_Y+i*(S_TAB_H+S_TAB_GAP);return new float[]{S_TAB_X,y,S_TAB_X+S_TAB_W,y+S_TAB_H};}
    public static float[] sheetValue(int i){float cw=(S_GW-(S_COLS-1)*S_HGAP)/S_COLS,x=S_GX+(i%S_COLS)*(cw+S_HGAP),y=S_GY+(i/S_COLS)*(S_ROW_H+S_VGAP);return new float[]{x,y,x+cw,y+S_ROW_H};}
    public static float[] sheetGraph(int g){float x=S_GRAPH_X+g*(S_GRAPH_W+S_GRAPH_GAP);return new float[]{x,S_GRAPH_Y,x+S_GRAPH_W,S_GRAPH_Y+S_GRAPH_H};}
    private static boolean in(float[] b,float x,float y){return x>=b[0]&&x<=b[2]&&y>=b[1]&&y<=b[3];}
    /** What the sheet has under x,y with tabs tabs, n readings and graphs graph buttons: S_TAB + i, S_VAL + i,
     *  S_GRAPH + g, S_CANCEL, S_DONE, S_IN (on the sheet, nothing there) or -1 (outside the sheet). */
    /** The 모양 sheet: wider than the other sheets, a 5 x 4 grid of slots (FORM_SLOTS), each a shape drawn small. */
    public static final float F_SH_L=24,F_SH_R=776,F5_X=40,F5_W=(F_SH_R-F_SH_L-32-4*10)/5f;
    /** Each of FloatPanel.FORMS' slot (row * 5 + column): rows 칸·목록, 계기, 변속·RPM, then 세로·분할·음악 — like shapes together. */
    public static final int[] FORM_SLOTS={0,1,2,3,4, 5,6,7,8, 10,11,12,13,14, 15,16,17,18,19};
    public static final float K_X=112,K_Y=72,K_GAP=10,K_W=(SH_R-SH_L-32-3*K_GAP)/4,K_H=(SH_B-K_Y-14-2*K_GAP)/3;
    public static final float F4_Y=64,F4_H=(SH_B-F4_Y-10-3*K_GAP)/4;   // four rows: 16 shapes
    public static float[] tile5(int slot){float x=F5_X+(slot%5)*(F5_W+K_GAP),y=F4_Y+(slot/5)*(F4_H+K_GAP);return new float[]{x,y,x+F5_W,y+F4_H};}
    /** A skin tile, -1 elsewhere on the sheet, -2 outside it (closes). */
    /** The 색감 sheet: 4 x 3 tiles, each look on the shape chosen now. */
    public static float[] tile4(int i){float x=K_X+(i%4)*(K_W+K_GAP),y=K_Y+(i/4)*(K_H+K_GAP);return new float[]{x,y,x+K_W,y+K_H};}
    public static int hit4(float x,float y,int n){
        if(x<SH_L||x>SH_R||y<SH_T||y>SH_B)return -2;
        for(int i=0;i<n;i++)if(in(tile4(i),x,y))return i;
        return -1;
    }
    /** The shape (index into slots) under x,y on the 모양 sheet, -1 elsewhere on it, -2 outside (closes). */
    public static int hitForm(float x,float y,int[] slots){
        if(x<F_SH_L||x>F_SH_R||y<SH_T||y>SH_B)return -2;
        for(int i=0;i<slots.length;i++)if(in(tile5(slots[i]),x,y))return i;
        return -1;
    }
    public static int sheetHit(float x,float y,int tabs,int n,int graphs){
        if(x<SH_L||x>SH_R||y<SH_T||y>SH_B)return -1;
        for(int i=0;i<tabs;i++)if(in(sheetTab(i),x,y))return S_TAB+i;
        for(int i=0;i<Math.min(n,S_MAX_VALUES);i++)if(in(sheetValue(i),x,y))return S_VAL+i;
        for(int g=0;g<graphs;g++)if(in(sheetGraph(g),x,y))return S_GRAPH+g;
        if(in(S_CANCEL_BOX,x,y))return S_CANCEL;
        if(in(S_DONE_BOX,x,y))return S_DONE;
        return S_IN;
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
