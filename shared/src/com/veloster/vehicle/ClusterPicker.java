package com.veloster.vehicle;

/**
 * Every dashboard screen behind the one screen tab in the header, and the list a long press on it opens: 6 x 5 tiles
 * over the body (6 x 6 since 내 차 and 네온 드라이브). The header gear opens it in edit mode, where the last tiles are trouble codes (DIAG), car paint (CAR, the 8-bit views), language (LANG),
 * theme (THEME) and 완료 (EDIT). Overview, Gauges, Graphs and F1 are VehicleDash flags; the screens after
 * F1 are PitRenderer pages (page(s)). Hidden screens are a bit mask (bit i = screen i), saved by VehicleDash. A short
 * tap on the tab steps to the next shown screen. No Android: host-tested.
 */
public final class ClusterPicker {
    /** Header tab label per screen (short: the tab is 160 px wide). */
    public static final String[] NAMES={"전체 조망","게이지","그래프","F1","퍼포먼스","레이스","기록","쇼",
        "클래식","레트로","랠리","택시","80년대","HUD","8비트","아케이드","G 원","야간","드래그","엔진","카세트","워프","서킷","람다","에코","RPM","아날로그","신스웨이브","내 차","네온 드라이브","정비","WRC"};
    /** Second line on each tile. */
    public static final String[] DESC={"모든 값 한눈에","고른 값 게이지","시간 그래프","F1 스타일 계기판","랩 타이머","변속 표시·음악","주행 기록","쇼 화면",
        "계단 회전 곡선","주황 LCD","흰 네모 숫자","택시 미터기","80년대 디지털","전투기 HUD","8비트 게임","8비트 추격 시점","마찰원","야간 미니멀","드래그 출발 트리",
        "비행기 엔진 계기판","카세트 데크","우주선 워프","GPS 랩 타이머","Λ RPM·속도계","연비·연료비","원형 RPM·다운시프트",
        "크롬 원형 바늘","사이버펑크 네온","3D 모델·램프·핸들 연동","도트 드라이브 화면","소모품 · 교환 주기","랠리 페이스노트"};
    public static final int OVERVIEW=0,GAUGES=1,GRAPHS=2,F1=3,CLASSIC=8,ARCADE=15,MANUAL=23,ECO=24,TACHO=25,ANALOG=26,NEON=27,MAINT=30,WRC=31,COUNT=NAMES.length,COLS=6,SLOTS=36;   // MANUAL: the Λ screen (LambdaCluster), 6MT and 7DCT
    /** Screens that are their own Android views over the dashboard (VehicleDash.syncOverlay), not PitRenderer pages. */
    public static final int CAR3D=28,NEON_DRIVE=29;   // 2026-11-27: 내 차 and 네온 드라이브 before WRC, so the stable release (without WRC) has the same numbers
    public static final float LEFT=17,TOP=60,TILE_W=121,TILE_H=62,GAP=8;
    private ClusterPicker(){}
    /** The favourites line of the settings' screen list (2026-11-28): the screens above it are the header strip, in this
     *  order; the others are picked from the list (the header's list button). The line is a row of its own among the
     *  screens (LINE; COUNT+1 rows), so dragging a row across it, or the line itself, changes how many are above. */
    public static final int LINE=-1,FAV_DEFAULT=7;
    /** A saved count of favourites: at least one, at most every screen. */
    public static int validFav(int f){return Math.max(1,Math.min(COUNT,f));}
    public static int[] rows(int[] order,int fav){
        order=validOrder(order);fav=validFav(fav);int[] r=new int[COUNT+1];
        System.arraycopy(order,0,r,0,fav);r[fav]=LINE;System.arraycopy(order,fav,r,fav+1,COUNT-fav);return r;
    }
    public static int favOf(int[] rows){for(int i=0;i<rows.length;i++)if(rows[i]==LINE)return i;return FAV_DEFAULT;}
    public static int[] orderOf(int[] rows){int[] o=new int[COUNT];int k=0;for(int s:rows)if(s!=LINE&&k<COUNT)o[k++]=s;return o;}
    /** The row at from taken out and put back at to: a drag, ▲▼ (to = from ± 1), 맨 위로 (to = 0). The line never ends
     *  up above the first screen. Out of range keeps the rows. */
    public static int[] moveRow(int[] order,int fav,int from,int to){
        int[] r=rows(order,fav);if(from<0||from>=r.length||to<0||to>=r.length||from==to)return r;
        int t=r[from];
        if(from<to)System.arraycopy(r,from+1,r,from,to-from);else System.arraycopy(r,to,r,to+1,from-to);
        r[to]=t;
        if(r[0]==LINE){r[0]=r[1];r[1]=LINE;}
        return r;
    }
    public static boolean starred(int[] order,int fav,int s){order=validOrder(order);fav=validFav(fav);for(int i=0;i<fav;i++)if(order[i]==s)return true;return false;}
    /** The header strip: the screens above the line that are switched on. */
    public static int[] bar(int[] order,int hidden,int fav){
        order=validOrder(order);fav=validFav(fav);int[] out=new int[fav];int n=0;
        for(int i=0;i<fav;i++)if((hidden&(1<<order[i]))==0)out[n++]=order[i];
        return java.util.Arrays.copyOf(out,n);
    }
    /** The strip's tabs: the bar, and after it the screen on show when it is not one of them (picked from the list, or
     *  opened by a drive mode), so the strip always shows where you are. */
    public static int[] tabs(int[] order,int hidden,int fav,int cur){
        int[] b=bar(order,hidden,fav);if(cur<0||cur>=COUNT)return b;
        for(int s:b)if(s==cur)return b;
        int[] t=java.util.Arrays.copyOf(b,b.length+1);t[b.length]=cur;return t;
    }
    /** Not dashboards: the service log and the two screens you go into. The list keeps them on a row of their own. */
    public static boolean own(int s){return s==MAINT||s==CAR3D||s==NEON_DRIVE;}
    /** The list's tiles (SLOTS, -1 = empty): the dashboards switched on, in order; then, from the next row, the own kind. */
    public static int[] grid(int[] order,int hidden){
        int[] g=new int[SLOTS];java.util.Arrays.fill(g,-1);int[] shown=slots(order,hidden,false);int n=0;
        for(int s:shown)if(!own(s)&&n<SLOTS)g[n++]=s;
        n=(n+COLS-1)/COLS*COLS;
        for(int s:shown)if(own(s)&&n<SLOTS)g[n++]=s;
        return g;
    }
    /** PitRenderer page of a screen after F1. */
    public static int page(int s){return s-F1;}
    public static float left(int i){return LEFT+(i%COLS)*(TILE_W+GAP);}
    public static float top(int i){return TOP+(i/COLS)*(TILE_H+GAP);}
    /** Tile slot under the point, or -1 (gaps and the header are no tile). */
    public static int hit(float x,float y){
        for(int i=0;i<SLOTS;i++){float l=left(i),t=top(i);if(x>=l&&x<=l+TILE_W&&y>=t&&y<=t+TILE_H)return i;}
        return -1;
    }
    /** Every screen's bit; 32 screens fill the int (1<<32 would wrap to 1): the next new screen needs a long mask. */
    private static final int ALL=COUNT>=32?-1:(1<<COUNT)-1;
    /** A saved mask, or 0 (all shown) when it is out of range or hides every screen. */
    public static int valid(int hidden){return (hidden&~ALL)!=0||hidden==ALL?0:hidden;}
    /** Natural display order 0..COUNT-1 (screen numbers; the convenience calls below and host tests). */
    public static int[] naturalOrder(){int[] o=new int[COUNT];for(int i=0;i<COUNT;i++)o[i]=i;return o;}
    /** Default display order (2026-11-27): related screens together — everyday, shift / RPM, track and timing, rally,
     *  retro and themes, then the games and the screens you go into (아케이드, 네온 드라이브, 내 차) last. */
    private static final int[] DEFAULT_ORDER={0,1,2,8,26,17,24,11,30, 25,23,5,3, 4,22,18,16,6, 10,31, 9,12,20,19,13,27,21,7, 14,15,29,28};
    /** Bump when DEFAULT_ORDER changes: a saved order from before is replaced by it once (VehicleDash). */
    public static final int ORDER_VERSION=4;   // 3: screen numbers 28-30 changed (내 차, 네온 드라이브, WRC); 4: 정비 30, WRC 31 (the saved order kept)
    /** A v3 saved order in v4 numbers: WRC 30 -> 31, and 정비 (30) right after 택시 (11), or last when 택시 is not in it. */
    public static int[] migrateOrder4(int[] saved){
        if(saved==null)return null;
        int[] o=new int[saved.length+1];int k=0;boolean put=false;
        for(int s:saved){o[k++]=renumber4(s);if(s==11&&!put){o[k++]=MAINT;put=true;}}
        if(!put)o[k++]=MAINT;
        return java.util.Arrays.copyOf(o,k);
    }
    /** A v3 screen number in v4 numbers (only WRC moved). */
    public static int renumber4(int s){return s==30?WRC:s;}
    public static int renumberMask4(int m){return (m&~(1<<30))|((m&(1<<30))!=0?1<<31:0);}
    public static int[] defaultOrder(){return DEFAULT_ORDER.clone();}
    /** A screen number saved before ORDER_VERSION 3 (test builds: 28 WRC, 29 내 차, 30 네온 드라이브) as it is now; stable
     *  builds never had 28-30, so for them it changes nothing. */
    public static int renumber(int s){return s==28?WRC:s==29?CAR3D:s==30?NEON_DRIVE:s;}
    public static int renumberMask(int m){int out=m&~(7<<28);for(int s=28;s<=30;s++)if((m&(1<<s))!=0)out|=1<<renumber(s);return out;}
    /** A saved order repaired to a permutation of 0..COUNT-1 (the default when it is missing or malformed). An order saved
     *  before screens were added keeps its arrangement, with the new screens after it. */
    public static int[] validOrder(int[] o){
        if(o!=null&&o.length<COUNT){int[] w=new int[COUNT];System.arraycopy(o,0,w,0,o.length);for(int i=o.length;i<COUNT;i++)w[i]=i;o=w;}
        if(o==null||o.length!=COUNT)return defaultOrder();
        boolean[] seen=new boolean[COUNT];
        for(int s:o){if(s<0||s>=COUNT||seen[s])return defaultOrder();seen[s]=true;}
        int[] c=new int[COUNT];System.arraycopy(o,0,c,0,COUNT);return c;
    }
    private static int pos(int[] order,int screen){for(int i=0;i<order.length;i++)if(order[i]==screen)return i;return 0;}
    /** Swap the screen at display position i with its neighbour i+d (d = ±1); unchanged at the ends. */
    public static int[] reorder(int[] order,int i,int d){
        order=validOrder(order);int j=i+d;
        if(i<0||i>=order.length||j<0||j>=order.length)return order;
        int t=order[i];order[i]=order[j];order[j]=t;return order;
    }
    /** The screen at display position from taken out and put back at position to (a dragged row); out of range keeps the order. */
    public static int[] move(int[] order,int from,int to){
        order=validOrder(order);if(from<0||from>=order.length||to<0||to>=order.length||from==to)return order;
        int t=order[from];
        if(from<to)System.arraycopy(order,from+1,order,from,to-from);else System.arraycopy(order,to,order,to+1,from-to);
        order[to]=t;return order;
    }
    /** The shown screen after cur in display order, wrapping; cur itself when it is the only one. */
    public static int next(int[] order,int cur,int hidden){
        order=validOrder(order);int at=pos(order,cur);
        for(int k=1;k<=order.length;k++){int s=order[(at+k)%order.length];if((hidden&(1<<s))==0)return s;}
        return cur;
    }
    /** The shown screen before cur in display order, wrapping; cur itself when it is the only one. */
    public static int prev(int[] order,int cur,int hidden){
        order=validOrder(order);int at=pos(order,cur);
        for(int k=1;k<=order.length;k++){int s=order[((at-k)%order.length+order.length)%order.length];if((hidden&(1<<s))==0)return s;}
        return cur;
    }
    // Natural-order convenience for callers that keep the default order (drive-mode logic, host tests).
    public static int next(int cur,int hidden){return next(naturalOrder(),cur,hidden);}
    public static int prev(int cur,int hidden){return prev(naturalOrder(),cur,hidden);}
    public static int[] slots(int hidden,boolean editing){return slots(naturalOrder(),hidden,editing);}
    public static int shown(int s,int hidden){return shown(naturalOrder(),s,hidden);}
    /** Drive modes with a screen of their own, in settings order (CLU13 codes), and their default screens (-1 = 원래 화면). */
    public static final int[] MODES={1,3,0},MODE_DEFAULT={F1,-1,-1};
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
    /** s when shown, else the next shown screen in display order. */
    public static int shown(int[] order,int s,int hidden){return (hidden&(1<<s))==0?s:next(order,s,hidden);}
    /** Flip screen s; the last shown screen stays shown. */
    public static int toggle(int hidden,int s){int h=hidden^(1<<s);return h==ALL?hidden:h;}
    /** Screens in display order: the shown ones, or all of them while editing. */
    public static int[] slots(int[] order,int hidden,boolean editing){
        order=validOrder(order);
        int n=0;for(int s:order)if(editing||(hidden&(1<<s))==0)n++;
        int[] out=new int[n];n=0;
        for(int s:order)if(editing||(hidden&(1<<s))==0)out[n++]=s;
        return out;
    }
}
