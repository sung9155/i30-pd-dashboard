package com.veloster.vehicle;

/**
 * Body paint of the pixel i30 N in the 8-bit views: Performance Blue (the N colour, the default), then the i30 (PD)
 * colours sold in Korea, launch colours first, then the later additions, then the three launch colours of September
 * 2016 that came in last, then two that are not factory colours: a deep metallic purple of the kind tuned cars wear
 * and a khaki (new ids go at the end so saved choices keep their place). Each paint is body, highlight, dark side and shadow line. No Android: host-tested.
 */
public final class CarPaint {
    public static final String[] IDS={"blue","white","red","black","grey","navy","brown","intense","orange","marina","silver","copper","purple","khaki"};
    public static final String[] LABELS={"퍼포먼스 블루","폴라 화이트","파이어리 레드","팬텀 블랙","아이언 그레이","스타게이징 블루","데미타스 브라운","인텐스 블루","라바 오렌지","마리나 블루","플래티넘 실버","인텐스 카퍼","미드나이트 퍼플","카키"};
    private static final int[][] BODY={
        {0xffb3c9ea,0xffdce8f7,0xff8ea8d0,0xff6f88b4},
        {0xffeef0f1,0xffffffff,0xffc9cdd2,0xffa2a8b0},
        {0xffd9262e,0xfff2645c,0xffa9141d,0xff7d0d15},
        {0xff353a42,0xff5f6772,0xff272b31,0xff1d2025},
        {0xff63676d,0xff8e9298,0xff4b4f55,0xff3a3d42},
        {0xff2f4270,0xff53699a,0xff243457,0xff1a2642},
        {0xff5e4436,0xff8a6b5c,0xff49352a,0xff37281f},
        {0xff3a6ea5,0xff6b97c8,0xff2c5683,0xff204166},   // Intense Blue: the deeper, greyer blue; ponytail: eyeballed, as are silver and copper, tune from daylight photos
        {0xffe5641c,0xfff59a5c,0xffb84e14,0xff8a3a0f},
        {0xff2063cc,0xff5a92e8,0xff194fa4,0xff133d80},   // Marina Blue: the vivid one
        {0xffb4b8bd,0xffdde0e3,0xff8f949a,0xff6e737a},
        {0xffa0532f,0xffcc8058,0xff7d3f23,0xff5c2e19},
        {0xff6a32a6,0xff9a68d2,0xff4f237f,0xff3a1860},   // not a factory colour: tuning purple
        {0xff7a7650,0xffa5a078,0xff5c593b,0xff44422b},   // not a factory colour: khaki
    };
    private static volatile int current;
    private CarPaint(){}
    public static int index(){return current;}
    public static String id(){return IDS[current];}
    public static String label(){return LABELS[current];}
    /** Body, highlight, dark side, shadow line. */
    public static int[] body(){return BODY[current].clone();}
    /** Body colour of paint i (the settings swatch). */
    public static int color(int i){return BODY[i][0];}
    public static void select(int i){if(i>=0&&i<IDS.length)current=i;}
    /** Unknown or null ids fall back to Performance Blue (a corrupt saved preference must still open). */
    public static void select(String id){int n=0;for(int i=0;i<IDS.length;i++)if(IDS[i].equals(id))n=i;current=n;}
    public static void next(){current=(current+1)%IDS.length;}
}
