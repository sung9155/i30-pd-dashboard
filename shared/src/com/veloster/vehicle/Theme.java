package com.veloster.vehicle;

import java.util.Arrays;

/**
 * Colour themes. Renderers keep drawing with today's "N" palette constants and pass each colour through
 * {@link #map(int)} at draw time, so the N theme is pixel-identical to the original and every other theme is one
 * remap table. Semantic green / amber (ok / caution) are never remapped. No Android imports: host-tested.
 */
public final class Theme {
    public static final String[] IDS={"n","lambo","bmw","ice","mono","amber","teal","night","rose","copper"};
    public static final int SHAPE_CUT=0,SHAPE_HEX=1,SHAPE_ROUND=2;

    // N palette literals grouped by role (RGB only; alpha is kept from the caller).
    private static final int BG=0,HEADER=1,PANEL=2,LINE=3,TRACK=4,TEXT=5,MUTED=6,ACCENT=7,ACCENT_DIM=8,RED=9;
    private static final int[][] N_ROLES={
        {0x040506,0x030405,0x05070a,0x000000,0x070b0e,0x0b0f12,0x111315},          // BG (incl. background gradient ends)
        {0x090b0c},                                                                // HEADER
        {0x101315,0x111415,0x111516,0x0e1318,0x101920,0x142129,0x1d262d},          // PANEL
        {0x303638,0x252a2c,0x263038,0x293943,0x3a4650,0x3a4a54,0x294653},          // LINE
        {0x1d2326,0x1b2228,0x2a343b,0x4a5860,0x4a5a63,0x49616c,0x3f4948},          // TRACK
        {0xe1edf2,0xe6f1f6,0xf2f6f8,0xe9f7ff,0xd0ebf8,0xaebfc8},                   // TEXT
        {0x91a5ae,0x86969f,0x8fa0a8,0x8299a4},                                     // MUTED
        {0xa4d9ef,0x8fd0f0,0x5bc8f5},                                              // ACCENT
        {0x233b46,0x1c303b,0x12212a,0x0c1a20},                                     // ACCENT_DIM (selected tab, accent wells)
        {0xff4059,0xff4a5e},                                                       // RED (decoration; warnings use danger)
    };

    public final String id,label;
    public final int shape,digitWeight;
    public final boolean mStripe,dangerRing;
    public final int danger,accent,onAccent;
    private final int[] keys,values;   // sorted N RGB -> theme RGB

    private Theme(String id,String label,int shape,int digitWeight,boolean mStripe,boolean dangerRing,int danger,int onAccent,int[] roles){
        this.id=id;this.label=label;this.shape=shape;this.digitWeight=digitWeight;this.mStripe=mStripe;this.dangerRing=dangerRing;
        this.danger=danger;this.onAccent=onAccent;
        if(roles==null){keys=values=null;accent=0xffa4d9ef;return;}
        accent=0xff000000|roles[ACCENT];
        int n=0;for(int[] r:N_ROLES)n+=r.length;
        long[] pairs=new long[n];int k=0;
        for(int role=0;role<N_ROLES.length;role++)for(int rgb:N_ROLES[role])pairs[k++]=((long)rgb<<32)|(roles[role]&0xffffffffL);
        Arrays.sort(pairs);
        keys=new int[n];values=new int[n];
        for(int i=0;i<n;i++){keys[i]=(int)(pairs[i]>>>32);values[i]=(int)pairs[i];}
    }

    private static final Theme[] ALL={
        new Theme("n","N",SHAPE_CUT,1,false,false,0xffff4059,0xff000000,null),
        //                                                                   BG       HEADER   PANEL    LINE     TRACK    TEXT     MUTED    ACCENT   ACC_DIM  RED
        new Theme("lambo","LB",SHAPE_HEX,2,false,false,0xffff2d4a,0xff000000,new int[]{0x030303,0x0a0a08,0x111110,0x3a3520,0x22201a,0xf5f2e6,0x9a9480,0xffc21a,0x2a2410,0xff3b1f}),
        new Theme("bmw","M",SHAPE_ROUND,0,true,false,0xffff2d4a,0xffffffff,new int[]{0x080b10,0x0c1118,0x111821,0x24303e,0x1a2430,0xf2f5f8,0x8a97a6,0x4a8fe8,0x14243a,0xe22718}),
        // Car-cluster themes: true black background, so the screen does not glow at night.
        new Theme("ice","IC",SHAPE_CUT,1,false,false,0xffff3b30,0xff000000,new int[]{0x000000,0x040608,0x0a0d10,0x24303a,0x162029,0xe8f4ff,0x7d8c99,0x5ac8fa,0x0b2633,0x2f7fd8}),
        new Theme("mono","MO",SHAPE_CUT,1,false,false,0xffff3b30,0xff000000,new int[]{0x000000,0x050505,0x0b0b0b,0x2a2a2a,0x1a1a1a,0xffffff,0x8c8c8c,0xe6e6e6,0x1f1f1f,0x9a9a9a}),
        new Theme("amber","AM",SHAPE_CUT,1,false,true,0xffff3b30,0xff000000,new int[]{0x000000,0x070402,0x0d0904,0x3a2810,0x22180a,0xffd9a8,0xa8865c,0xff9a1f,0x2e1c06,0xff6a00}),
        new Theme("teal","EV",SHAPE_CUT,1,false,false,0xffff3b30,0xff000000,new int[]{0x000000,0x020606,0x081010,0x1f3836,0x122422,0xe6fffb,0x7aa09b,0x00e0c6,0x06302b,0x00a3ff}),
        new Theme("night","NT",SHAPE_CUT,1,false,false,0xffe0453a,0xff000000,new int[]{0x000000,0x030405,0x07090b,0x1c2329,0x12171b,0xb8c4cc,0x66737c,0x3f86d9,0x0c1d30,0x2a5fa0}),
        // Rose: round cells like M, without its stripe. Copper: hex cells like LB, normal digit weight.
        new Theme("rose","RS",SHAPE_ROUND,0,false,false,0xffff3b30,0xff000000,new int[]{0x000000,0x080506,0x110b0d,0x3a2830,0x24181d,0xfff2f6,0xa8909a,0xff8fb1,0x3a1624,0xff4f7b}),
        new Theme("copper","CU",SHAPE_HEX,1,false,false,0xffff3b30,0xff000000,new int[]{0x000000,0x070504,0x0e0a08,0x3a2c22,0x241b15,0xf8ece2,0xa69282,0xe08a52,0x33200f,0xc0502a}),
    };
    private static volatile Theme current=ALL[0];

    public static Theme current(){return current;}
    /** Unknown or null ids fall back to N (a corrupt saved preference must still open). */
    public static void select(String id){current=byId(id);}
    public static String next(String id){
        for(int i=0;i<IDS.length;i++)if(IDS[i].equals(id))return IDS[(i+1)%IDS.length];
        return IDS[1];
    }
    private static Theme byId(String id){for(Theme t:ALL)if(t.id.equals(id))return t;return ALL[0];}

    /** WCAG contrast ratio of two opaque colours (1..21). */
    public static double contrast(int a,int b){double x=lum(a),y=lum(b);return (Math.max(x,y)+0.05)/(Math.min(x,y)+0.05);}
    private static double lum(int c){return 0.2126*ch(c>>16)+0.7152*ch(c>>8)+0.0722*ch(c);}
    private static double ch(int v){double s=(v&255)/255.0;return s<=0.03928?s/12.92:Math.pow((s+0.055)/1.055,2.4);}
    /** want, unless it would not read on fill (contrast < 3): then black or white, whichever reads. N is left as is. */
    public int readableOn(int fill,int want){
        if(keys==null||contrast(want,fill)>=3)return want;
        return contrast(0xff000000,fill)>=contrast(0xffffffff,fill)?0xff000000:0xffffffff;
    }
    /** Themed colour for a non-warning state; never the danger colour (amber's accent sits close to its danger red). */
    public int calm(int nColor){int c=map(nColor);return (c&0xffffff)==(danger&0xffffff)?map(0xff91a5ae):c;}

    /** Theme colour for an N palette colour; alpha comes from the input. Colours outside the table pass through. */
    public int map(int nColor){
        if(keys==null)return nColor;
        int i=Arrays.binarySearch(keys,nColor&0xffffff);
        return i<0?nColor:(nColor&0xff000000)|values[i];
    }
}
