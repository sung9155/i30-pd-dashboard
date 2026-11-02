package com.veloster.vehicle;

/**
 * Body paint of the pixel i30 N in the 8-bit views: Performance Blue (the N colour, the default), then the i30 (PD)
 * colours sold in Korea, 2017 launch first, then the 2019 additions. Each paint is body, highlight, dark side and
 * shadow line. No Android: host-tested.
 */
public final class CarPaint {
    public static final String[] IDS={"blue","white","red","black","grey","navy","brown","intense","orange"};
    public static final String[] LABELS={"퍼포먼스 블루","폴라 화이트","파이어리 레드","팬텀 블랙","아이언 그레이","스타게이징 블루","데미타스 브라운","인텐스 블루","라바 오렌지"};
    private static final int[][] BODY={
        {0xffb3c9ea,0xffdce8f7,0xff8ea8d0,0xff6f88b4},
        {0xffeef0f1,0xffffffff,0xffc9cdd2,0xffa2a8b0},
        {0xffd9262e,0xfff2645c,0xffa9141d,0xff7d0d15},
        {0xff353a42,0xff5f6772,0xff272b31,0xff1d2025},
        {0xff63676d,0xff8e9298,0xff4b4f55,0xff3a3d42},
        {0xff2f4270,0xff53699a,0xff243457,0xff1a2642},
        {0xff5e4436,0xff8a6b5c,0xff49352a,0xff37281f},
        {0xff2063cc,0xff5a92e8,0xff194fa4,0xff133d80},
        {0xffe5641c,0xfff59a5c,0xffb84e14,0xff8a3a0f},
    };
    private static volatile int current;
    private CarPaint(){}
    public static int index(){return current;}
    public static String id(){return IDS[current];}
    public static String label(){return LABELS[current];}
    /** Body, highlight, dark side, shadow line. */
    public static int[] body(){return BODY[current].clone();}
    /** Unknown or null ids fall back to Performance Blue (a corrupt saved preference must still open). */
    public static void select(String id){int n=0;for(int i=0;i<IDS.length;i++)if(IDS[i].equals(id))n=i;current=n;}
    public static void next(){current=(current+1)%IDS.length;}
}
