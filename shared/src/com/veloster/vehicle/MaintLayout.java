package com.veloster.vehicle;

/**
 * Where 정비 (MaintenanceCluster) puts things, 2026-11-28 look: three tab pills, a big card on the left (the item to see
 * to, the tyre pressures, the trouble codes) with a button, and on the right cards two to a row (what is recorded, most
 * urgent first) over "+ item" chips three to a row (nothing recorded yet), over an optional block (상태's evidence).
 * Cards are 84 px with two lines while they fit and shrink (one line) when many are recorded. No Android: host-tested.
 */
public final class MaintLayout {
    private MaintLayout(){}
    public static final float PILL_L=16,PILL_T=58,PILL_W=104,PILL_H=36,
        TOP=106,BOTTOM=468,HERO_L=16,HERO_R=252,RIGHT_L=264,RIGHT_R=784,
        BTN_L=32,BTN_R=236,BTN_T=394,BTN_B=440,
        GAP=8,LABEL_H=22,SECTION_GAP=10,CARD_MAX=84,TALL_MIN=64,CHIP_H=40,TAIL_H=74;

    /** Rectangles {l,t,r,b}; the headings' tops (cardsLabel, chipsLabel) and the block's top (tailT). */
    public static final class Grid {
        public float[][] cards,chips;public float cardsLabel=Float.NaN,chipsLabel=Float.NaN,tailT=Float.NaN;public boolean tall;
    }

    public static int pill(float x,float y){
        if(y<PILL_T||y>PILL_T+PILL_H)return -1;
        for(int i=0;i<3;i++){float l=PILL_L+i*(PILL_W+8);if(x>=l&&x<=l+PILL_W)return i;}
        return -1;
    }
    public static boolean hero(float x,float y){return x>=HERO_L&&x<=HERO_R&&y>=TOP&&y<=BOTTOM;}
    public static boolean button(float x,float y){return x>=BTN_L&&x<=BTN_R&&y>=BTN_T&&y<=BTN_B;}

    /** n cards, then m chips, then a block tail px tall, top-down on the right side. */
    public static Grid grid(int n,int m,float tail){
        Grid g=new Grid();g.cards=new float[n][];g.chips=new float[m][];
        int rows=(n+1)/2,crows=(m+2)/3;
        float fixed=(n>0?LABEL_H+(rows-1)*GAP:0)+(m>0?(n>0?SECTION_GAP:0)+LABEL_H+crows*CHIP_H+(crows-1)*GAP:0)+(tail>0?SECTION_GAP+tail:0);
        float h=rows==0?0:(float)Math.floor(Math.min(CARD_MAX,(BOTTOM-TOP-fixed)/rows));
        g.tall=h>=TALL_MIN;
        float y=TOP;
        if(n>0){g.cardsLabel=y;y+=LABEL_H;float w=(RIGHT_R-RIGHT_L-GAP)/2;
            for(int i=0;i<n;i++){float l=RIGHT_L+(i%2)*(w+GAP),t=y+(i/2)*(h+GAP);g.cards[i]=new float[]{l,t,l+w,t+h};}
            y+=rows*h+(rows-1)*GAP;}
        if(m>0){if(n>0)y+=SECTION_GAP;g.chipsLabel=y;y+=LABEL_H;float w=(RIGHT_R-RIGHT_L-2*GAP)/3;
            for(int i=0;i<m;i++){float l=RIGHT_L+(i%3)*(w+GAP),t=y+(i/3)*(CHIP_H+GAP);g.chips[i]=new float[]{l,t,l+w,t+CHIP_H};}
            y+=crows*CHIP_H+(crows-1)*GAP;}
        if(tail>0)g.tailT=y+(n+m>0?SECTION_GAP:0);
        return g;
    }
    /** The rectangle under (x,y), or -1. */
    public static int hit(float[][] rects,float x,float y){
        for(int i=0;i<rects.length;i++){float[] r=rects[i];if(x>=r[0]&&x<=r[2]&&y>=r[1]&&y<=r[3])return i;}
        return -1;
    }
}
