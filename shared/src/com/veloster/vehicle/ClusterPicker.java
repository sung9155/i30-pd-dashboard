package com.veloster.vehicle;

/**
 * Every dashboard screen behind the one screen tab in the header, and the list a long press on it opens: 6 x 5 tiles
 * over the body. The header gear opens it in edit mode, where the last tiles are trouble codes (DIAG), car paint (CAR, the 8-bit views), language (LANG),
 * theme (THEME) and 완료 (EDIT). Overview, Gauges, Graphs and F1 are VehicleDash flags; the screens after
 * F1 are PitRenderer pages (page(s)). Hidden screens are a bit mask (bit i = screen i), saved by VehicleDash. A short
 * tap on the tab steps to the next shown screen. No Android: host-tested.
 */
public final class ClusterPicker {
    /** Header tab label per screen (short: the tab is 160 px wide). */
    public static final String[] NAMES={"전체 조망","게이지","그래프","F1","퍼포먼스","레이스","기록","쇼",
        "클래식","레트로","랠리","택시","80년대","HUD","8비트","추격","에코","G 원","야간","드래그","엔진","카세트","워프"};
    /** Second line on each tile. */
    public static final String[] DESC={"모든 값 한눈에","고른 값 게이지","시간 그래프","F1 스타일 계기판","랩 타이머","변속 표시·음악","주행 기록","쇼 화면",
        "계단 회전 곡선","주황 LCD","흰 네모 숫자","택시 미터기","80년대 디지털","전투기 HUD","8비트 게임","8비트 추격 시점","에코 코치","마찰원","야간 미니멀","드래그 출발 트리",
        "비행기 엔진 계기판","카세트 데크","우주선 워프"};
    public static final int OVERVIEW=0,GAUGES=1,GRAPHS=2,F1=3,CLASSIC=8,COUNT=NAMES.length,COLS=6,SLOTS=30,ECO=16;
    public static final float LEFT=17,TOP=60,TILE_W=121,TILE_H=76,GAP=8;
    private ClusterPicker(){}
    /** PitRenderer page of a screen after F1. */
    public static int page(int s){return s-F1;}
    public static float left(int i){return LEFT+(i%COLS)*(TILE_W+GAP);}
    public static float top(int i){return TOP+(i/COLS)*(TILE_H+GAP);}
    /** Tile slot under the point, or -1 (gaps and the header are no tile). */
    public static int hit(float x,float y){
        for(int i=0;i<SLOTS;i++){float l=left(i),t=top(i);if(x>=l&&x<=l+TILE_W&&y>=t&&y<=t+TILE_H)return i;}
        return -1;
    }
    private static final int ALL=(1<<COUNT)-1;
    /** A saved mask, or 0 (all shown) when it is out of range or hides every screen. */
    public static int valid(int hidden){return (hidden&~ALL)!=0||hidden==ALL?0:hidden;}
    /** The shown screen after cur, wrapping; cur itself when it is the only one. */
    public static int next(int cur,int hidden){
        for(int i=1;i<=COUNT;i++){int s=(cur+i)%COUNT;if((hidden&(1<<s))==0)return s;}
        return cur;
    }
    /** The shown screen before cur, wrapping; cur itself when it is the only one. */
    public static int prev(int cur,int hidden){
        for(int i=1;i<=COUNT;i++){int s=((cur-i)%COUNT+COUNT)%COUNT;if((hidden&(1<<s))==0)return s;}
        return cur;
    }
    /** Drive modes with a screen of their own, in settings order (CLU13 codes), and their default screens (-1 = 원래 화면). */
    public static final int[] MODES={1,3,0},MODE_DEFAULT={F1,ECO,-1};
    public static final String[] MODE_NAMES={"SPORT","ECO","NORMAL"};
    /** Slot of a CLU13 mode code in MODES, or -1. */
    public static int modeSlot(int mode){for(int i=0;i<MODES.length;i++)if(MODES[i]==mode)return i;return -1;}
    /** A saved mode screen: a screen index, or -1 (원래 화면) when out of range. */
    public static int validTarget(int s){return s>=0&&s<COUNT?s:-1;}
    /**
     * Mode screen option: a change of drive mode opens that mode's screen (targets[modeSlot], when shown); a mode set to
     * 원래 화면 (-1) brings back the screen from before the first jump, unless the driver moved off the mode screen
     * meanwhile. state = {screen, back (-1 none)}; mode codes are CLU13's (0 NORMAL, 1 SPORT, 3 ECO, -1 unknown).
     * Only a change between two known modes moves anything.
     */
    public static int[] onMode(int prev,int mode,int screen,int back,int hidden,int[] targets){
        if(prev<0||mode<0||prev==mode)return new int[]{screen,back};
        int from=target(prev,hidden,targets),to=target(mode,hidden,targets);
        if(back>=0&&from>=0&&screen!=from)back=-1;   // driver left the screen a jump opened: keep their choice
        if(to>=0)return new int[]{to,back>=0?back:screen};
        if(back>=0)return new int[]{shown(back,hidden),-1};
        return new int[]{screen,back};
    }
    /** The mode's screen, or -1 for 원래 화면, an unknown mode or a hidden screen. */
    private static int target(int mode,int hidden,int[] targets){
        int k=modeSlot(mode),s=k<0?-1:validTarget(targets[k]);
        return s>=0&&(hidden&(1<<s))==0?s:-1;
    }
    /** s when shown, else the next shown screen. */
    public static int shown(int s,int hidden){return (hidden&(1<<s))==0?s:next(s,hidden);}
    /** Flip screen s; the last shown screen stays shown. */
    public static int toggle(int hidden,int s){int h=hidden^(1<<s);return h==ALL?hidden:h;}
    /** Screens in tile order: the shown ones, or all of them while editing. */
    public static int[] slots(int hidden,boolean editing){
        int n=0;for(int s=0;s<COUNT;s++)if(editing||(hidden&(1<<s))==0)n++;
        int[] out=new int[n];n=0;
        for(int s=0;s<COUNT;s++)if(editing||(hidden&(1<<s))==0)out[n++]=s;
        return out;
    }
}
