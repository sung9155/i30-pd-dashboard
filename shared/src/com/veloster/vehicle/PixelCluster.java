package com.veloster.vehicle;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Locale;

/**
 * 8-bit game in two views of a pixel i30 N (PD) in the chosen CarPaint: the side view (traced from Hyundai's official
 * side photo) drives past a city, the Arcade view (the car from ArcadeCarFrames, turned into the bend) follows from
 * behind down a pseudo-3D road that
 * bends with lateral G. LV = gear, EXP = rpm, hearts = range (100 km each), SCORE = odometer, a coin per second of
 * fuel cut, TURBO = boost. The sky follows the clock (DayPhase: day, twilight, night); the car's lamps follow its
 * own signals at any hour: headlamps and tail lamps the HeadLampLow bit, brake lights the brake, and the turn
 * signals their bits (which blink with the real lamps), shown on the car's lamps only. Scene pixels are 4x4 screen pixels.
 * Drive mode (CLU13): a banner when it changes and its own idle line; ECO pays 2 coins a second of fuel cut and sheds
 * leaves, SPORT sits the body a pixel lower and adds speed lines. Any mode: lifting off hard at high rpm pops a backfire,
 * and any lift above 2500 rpm may flash a short overrun flame or two: timed by the engine sound's big pops when it
 * plays (EngineSynth.flames), by the lift itself when it is off.
 * Static art (both cars per lamp state, skyline and hill strips per sky, hearts, the headlamp beam) is painted once into
 * small bitmaps and stamped; the chase road stays one rectangle per row (about 200), the rest is a few dozen calls.
 */
final class PixelCluster extends ClusterKit {
    private static final int K=0xff17191d,K2=0xff2a2d33,SV=0xffc9ced4,
        Y=0xffffd84a,W=0xffffffff,AMBER=0xffffb020,N_RED=0xffe0312f;
    /** Body, highlight, dark side and shadow line of the CarPaint in use (paint = its index; -1 before the first frame). */
    private int B,BH,BD,BS,paint=-1;
    // Per DayPhase (day, twilight, night).
    private static final int[][] SKY_SIDE={{0xff4f9ee6,0xff63aaea,0xff79b8ee,0xff90c6f1,0xffa8d4f4,0xffc2e2f6},
        {0xff140b33,0xff1f1250,0xff2e1a68,0xff46247c,0xff6b3487,0xffa24a8a},{0xff04061a,0xff070a22,0xff0b0f2c,0xff101536,0xff151b40,0xff1b2249}};
    private static final int[][] SKY_CHASE={{0xff3f8fe0,0xff4f9ae4,0xff60a6e8,0xff72b2ec,0xff86bef0,0xff9acaf2,0xffb0d6f4,0xffc8e2f6},
        {0xff0f0a2e,0xff170e42,0xff22135a,0xff341a6e,0xff4c2280,0xff6e2c86,0xff954080,0xffc45a72},
        {0xff03050f,0xff050817,0xff080c1f,0xff0b1028,0xff0e1430,0xff121938,0xff161e40,0xff1b2448}};
    // City skyline: 40 buildings repeating, each with its own width, height, roof kit and a fixed window pattern.
    private static final int[] CB_X=new int[40],CB_W=new int[40],CB_H=new int[40],CB_KIT=new int[40];
    private static final int CITY_W;
    static{int x=0;for(int i=0;i<40;i++){int h=(i*7919+13)%97;CB_X[i]=x;CB_W[i]=6+h%8;CB_H[i]=10+(h*31)%23;CB_KIT[i]=h%5==0?1:h%7==1?2:0;x+=CB_W[i]+((h>>2)%2);}CITY_W=x;}
    private static final int[][] CITY_BODY={{0xff6d7f99,0xff5f7190},{0xff2a1a46,0xff24163d},{0xff10142a,0xff0c1022}};
    private static final int[] CITY_LIT={0xffa9bfd6,0xffffc870,0xffffd84a},CITY_DARK={0xffa9bfd6,0xff3a2a55,0xff1a2038};
    private static final int[] FAR_HILL={0xff6f93b8,0xff3a1f5c,0xff151a38},VERGE={0xff3f8a3f,0xff1b3a2a,0xff0e2218},
        MOUNT_FAR={0xff7d9fc6,0xff2b1652,0xff141933},GRASS_A={0xff3f9a44,0xff123d2c,0xff0b2a1d},GRASS_B={0xff368a3b,0xff0e3224,0xff082317};
    /** Side silhouette: Hyundai's side view downsampled to 46 columns, cleaned by hand (front to the right). */
    private static final String[] SIDE={
        "........hhhhhhhhhhhhhhhhh.....................",
        "....KKBBBBKKKKKKKKKKKKKKKBB...................",
        "....KBBBGGKGGGGGGGKKGGGGGGKKB.................",
        "...KBBBGGGKGGGGGGGKKGgggGGGKGBB...............",
        "..BBBBBBBKKKKKKKKKKKKKKKKKKKBBBB..............",
        ".RRRBBBBBBBBBBBBBBBBBBBBBBBBBKKKBBBBBBBB......",
        ".RBBBhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhBBB...",
        ".BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBLLLLB.",
        "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBKKKBB",
        "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBKK",
        "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBKK",
        "DDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDKK",
        "KDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDK",
        "..KKKKKKKKKKKKKKKKKKKKKKKKKKKKKKKKKKKKKKKrrrrr"};
    private final boolean chase;
    private static final double TAU=Math.PI*2;
    // Caches: side car [brake|left<<1|spin<<2], skyline/hills per sky, hearts, beam; the Arcade car in arcadeCar.
    private final Bitmap[] sideCar=new Bitmap[48],cityStrip=new Bitmap[3],hillStrip=new Bitmap[3],heartBmp=new Bitmap[9];
    private Bitmap beam,highBeam;
    private float pxScale=4,pxTop=52;
    private interface Art{void paint(Canvas c);}
    /** Paints art once into a bitmap at 1 pixel per scene pixel. */
    private Bitmap bake(int w,int h,Art a){Bitmap b=bitmap(w,h);float s=pxScale,t=pxTop;pxScale=1;pxTop=0;a.paint(new Canvas(b));pxScale=s;pxTop=t;return b;}
    /** Stamps a baked bitmap at scene position x,y (4x, no smoothing). */
    // ---- weather (Weather.java): colour swaps, clouds by day, rain and snow ------------------------------------------
    private static final Weather WEATHER=new Weather();   // one tracker for both views, so they agree
    private int wx=Weather.CLEAR,remapWx=-1,remapPh=-1;
    /** Colour pairs (from, to) the weather swaps in px(), or null; baked strips are redone when it changes. */
    private int[] remap,bakedRemap;
    private static final int[] SKY_DAY={0xff3f8fe0,0xff4f9ae4,0xff60a6e8,0xff72b2ec,0xff86bef0,0xff9acaf2,0xffb0d6f4,0xffc8e2f6},
        SIDE_DAY={0xff4f9ee6,0xff63aaea,0xff79b8ee,0xff90c6f1,0xffa8d4f4,0xffc2e2f6},
        SKY_GREY={0xff8994a2,0xff929caa,0xff9ba5b1,0xffa4adb8,0xffadb5bf,0xffb6bdc6,0xffbfc5cd,0xffc8cdd4},
        SKY_RAIN={0xff5b6470,0xff626b77,0xff69727e,0xff707985,0xff77808c,0xff7e8793,0xff858e9a,0xff8c95a1},
        SKY_SNOW={0xffb9c2cc,0xffc1c9d2,0xffc9d0d8,0xffd0d6dd,0xffd7dce2,0xffdee2e7,0xffe5e8ec,0xffeceef1},
        SKY_BLEAK={0xff6f9ccc,0xff7ba5d0,0xff88aed4,0xff95b7d8,0xffa3c1dc,0xffb1cbe0,0xffbfd4e4,0xffcddde8};
    private static final int[] SIDE_FROM={0,1,3,4,6,7};   // which of the eight chase bands each side band takes
    /** The swaps for weather w under sky phase ph. */
    private static int[] remapFor(int w,int ph){
        if(w==Weather.CLEAR)return null;
        java.util.ArrayList<Integer> m=new java.util.ArrayList<>();
        int ground=Weather.ground(w);boolean day=ph==DayPhase.DAY;
        if(day){
            int[] sky=w==Weather.RAIN||w==Weather.STORM?SKY_RAIN:w==Weather.SNOW?SKY_SNOW:Weather.overcast(w)?SKY_GREY:w==Weather.BLEAK?SKY_BLEAK:null;
            if(sky!=null){for(int i=0;i<8;i++){m.add(SKY_DAY[i]);m.add(sky[i]);}for(int i=0;i<6;i++){m.add(SIDE_DAY[i]);m.add(sky[SIDE_FROM[i]]);}}
            int a=0,b=0;   // grass bands A and B (and the side view's verge, as A)
            if(ground==Weather.SNOWY){a=0xffe9edf2;b=0xffd6dde5;}else if(ground==Weather.DRY){a=0xff8a7d4c;b=0xff7d7044;}
            else if(ground==Weather.WET){a=0xff307037;b=0xff2a6631;}else if(Weather.overcast(w)){a=0xff37853f;b=0xff30793a;}
            if(a!=0){m.add(GRASS_A[0]);m.add(a);m.add(GRASS_B[0]);m.add(b);m.add(VERGE[0]);m.add(a);}
            int hf=0,hc=0;   // far hills (side, chase)
            if(w==Weather.SNOW||ground==Weather.SNOWY){hf=0xffc9d0d8;hc=0xffd0d6dd;}else if(ground==Weather.WET){hf=0xff646d7a;hc=0xff6b7481;}
            else if(Weather.overcast(w)){hf=0xff8f9aa8;hc=0xff98a2ae;}else if(ground==Weather.DRY){hf=0xff8aa3bc;hc=0xff96aec4;}
            if(hf!=0){m.add(FAR_HILL[0]);m.add(hf);m.add(MOUNT_FAR[0]);m.add(hc);}
            if(ground==Weather.WET){int[][] city={{0xff6d7f99,0xff58657a},{0xff5f7190,0xff4d5a70},{0xff8ea6c0,0xff6f7e93},{0xffa9bfd6,0xff8494a8}};
                for(int[] k:city){m.add(k[0]);m.add(k[1]);}}
        }else{   // twilight and night keep their skies; only the ground changes
            int i=ph==DayPhase.TWILIGHT?1:2;
            if(ground==Weather.SNOWY){int a=i==1?0xff8a90a8:0xff5a6274,b=i==1?0xff7a8098:0xff4e5666;m.add(GRASS_A[i]);m.add(a);m.add(GRASS_B[i]);m.add(b);m.add(VERGE[i]);m.add(a);}
            else if(ground==Weather.DRY){int a=i==1?0xff3a3424:0xff2a2619,b=i==1?0xff332e20:0xff241f15;m.add(GRASS_A[i]);m.add(a);m.add(GRASS_B[i]);m.add(b);m.add(VERGE[i]);m.add(a);}
        }
        if(ground==Weather.WET){m.add(0xff3b3b48);m.add(0xff30303b);m.add(0xff363642);m.add(0xff2c2c36);m.add(0xff2b2b36);m.add(0xff23232c);}   // wet asphalt
        if(m.isEmpty())return null;
        int[] out=new int[m.size()];for(int i=0;i<out.length;i++)out[i]=m.get(i);return out;
    }
    /** Clouds by day when overcast, behind the hills and skyline; drift is in scene pixels. */
    private static final String[] CLOUD={".....oooo.........","...oooooooo..ooo..",".ooooooooooooooooo.","oooooooooooooooooooo",".dddddddddddddddddd."};
    private void clouds(Canvas c,float drift){
        if(phase!=DayPhase.DAY||!Weather.overcast(wx))return;
        boolean wet=wx==Weather.RAIN||wx==Weather.STORM;
        int lite=wet?0xff8a929d:wx==Weather.SNOW?0xffeef0f3:0xffcfd4db,dark=wet?0xff4f5762:wx==Weather.SNOW?0xffc3cad3:0xffa7afb9;
        int[][] at=wet?new int[][]{{57,0},{77,4},{99,1},{121,5},{143,0},{175,3}}:new int[][]{{67,3},{103,0},{133,6},{8,2}};
        for(int[] k:at){int x0=(int)(((k[0]+drift)%220+220)%220)-20;
            for(int r=0;r<CLOUD.length;r++){String row=CLOUD[r];int run=-1;
                for(int i=0;i<=row.length();i++){boolean on=i<row.length()&&row.charAt(i)!='.';   // runs, so a row is a few rects
                    if(on&&run<0)run=i;if(!on&&run>=0){px(c,x0+run,k[1]+r,i-run,1,row.charAt(run)=='d'?dark:lite);run=-1;}}}}
    }
    /** Spray off the tyres on a wet road above walking pace: a flickering dither, bigger in a storm (two spots, width w). */
    private void spray(Canvas c,float x1,float y1,float x2,float y2,int w){
        float sp=v(7);if(Weather.ground(wx)!=Weather.WET||Float.isNaN(sp)||sp<20)return;
        int k=(int)(now/70%2),h=wx==Weather.STORM?5:3,ww=wx==Weather.STORM?w+3:w;
        dither(c,x1-k,y1+k,ww,h,0xffa7b1bf);if(x2!=x1)dither(c,x2+k,y2+k,ww,h,0xffa7b1bf);}
    /** Rain and snow: solid scene pixels that fall, rain splashing where it lands (from groundTop down). */
    private final java.util.Random rnd=new java.util.Random(7);
    private final float[] dropX=new float[140],dropY=new float[140],dropG=new float[140],dropT=new float[140];
    private int dropN,dropKind=Weather.CLEAR;
    private void precip(Canvas c,float dt,int groundTop){
        int kind=Weather.fall(wx);
        if(kind!=dropKind){dropKind=kind;dropN=kind==Weather.STORM?140:kind==Weather.RAIN?60:kind==Weather.SNOW?75:0;
            for(int i=0;i<dropN;i++){dropX[i]=rnd.nextFloat()*200;dropY[i]=rnd.nextFloat()*107;dropG[i]=groundTop+rnd.nextFloat()*(107-groundTop);dropT[i]=0;}}
        boolean snow=kind==Weather.SNOW,storm=kind==Weather.STORM,side=!chase;float v=storm?80:60,vx=v*0.5f;   // side view: the rain blows back past the car
        float sp=v(7),speedK=Float.isNaN(sp)?0:sp/60f;
        for(int i=0;i<dropN;i++){
            if(snow){dropY[i]+=dt*6;dropT[i]+=dt*3;dropX[i]+=(float)Math.sin(dropT[i])*dt*4;if(dropY[i]>107){dropY[i]=-1;dropX[i]=rnd.nextFloat()*200;}
                px(c,dropX[i],dropY[i],1,1,W);continue;}
            if(dropT[i]>0){dropT[i]-=dt;px(c,dropX[i]-1,dropG[i],2,1,0xff7d8798);   // splash: a dark ripple on the wet road, not a road mark
                if(dropT[i]<=0){dropY[i]=-4;dropX[i]=rnd.nextFloat()*200;}continue;}
            // chase view: driving into the rain, the drops fly out from the middle the further out they are
            float sx=side?-vx:(dropX[i]-100)*0.9f*Math.min(1,speedK);
            dropY[i]+=dt*v;dropX[i]+=dt*sx;
            if(dropX[i]<0||dropX[i]>=200){if(side)dropX[i]+=dropX[i]<0?200:-200;else{dropX[i]=rnd.nextFloat()*200;dropY[i]=-4;}}   // chase: a new drop at the top
            if(dropY[i]>=dropG[i]){dropT[i]=0.1f;continue;}
            int col=storm?0xffd2dde7:0xffb4c4d4,n=storm?4:3;   // lighter than the rainy skyline, so the drops show in front of it
            float len=(float)Math.hypot(sx,v),ux=sx/len,uy=v/len;   // a streak along its own motion, one scene pixel per step,
            int runX=Math.round(dropX[i]),runY=Math.round(dropY[i]),runH=0;   // pixels in the same column merged into one rect
            for(int j=0;j<n;j++){int qx=Math.round(dropX[i]-ux*j),qy=Math.round(dropY[i]-uy*j);
                if(qx==runX&&(runH==0||qy==runY-runH)){runH++;continue;}
                px(c,runX,runY-runH+1,1,runH,col);runX=qx;runY=qy;runH=1;}
            px(c,runX,runY-runH+1,1,runH,col);
        }
    }
    private void put(Canvas c,Bitmap b,float x,float y){int xi=Math.round(x),yi=Math.round(y);blit(c,b,0,0,b.getWidth(),b.getHeight(),xi*4,52+yi*4,(xi+b.getWidth())*4,52+(yi+b.getHeight())*4);}
    /** Repeats a horizontal strip across the 200-pixel scene, scrolled by off. */
    private void strip(Canvas c,Bitmap b,float off,float y){int w=b.getWidth();float o=((off%w)+w)%w;for(float x=-o;x<200;x+=w)put(c,b,x,y);}
    private int phase;
    /** The car's lamps from CGW1, independent of the sky: low beam (HeadLampLow), tail/position (미등), high beam (상향). */
    private boolean lamps,tail,high;
    private float scroll,curve,bgX,coinSec,bumpE,frameDt;
    private final RoadJolt jolt=new RoadJolt();
    private int coins;
    private long last,shiftAt=Long.MIN_VALUE/2,modeAt=Long.MIN_VALUE/2,pedHiAt=Long.MIN_VALUE/2,fireAt=Long.MIN_VALUE/2,popAt=Long.MIN_VALUE/2,seenFlame,liftAt=Long.MIN_VALUE/2,nextPop;
    private int liftPops;private boolean liftArmed;
    /** CLU13 drive mode (0 NORMAL, 1 SPORT, 3 ECO, -1 unknown) and the last known one, for the change banner. */
    private int mode=-1,lastMode=-1;
    private static final int SPORT=1,ECO=3;
    private String lastGear="";

    PixelCluster(boolean chase){this.chase=chase;}
    /** Chase car's sideways offset in lo-res pixels (+ right), eased toward the steering. */
    private float sway;

    /** A rectangle of scene pixels, snapped to the grid: edges round to whole pixels, and anything with size keeps at least one. */
    private void px(Canvas c,float x,float y,float w,float h,int color){
        if(remap!=null)for(int i=0;i<remap.length;i+=2)if(remap[i]==color){color=remap[i+1];break;}
        int x0=Math.round(x),y0=Math.round(y),x1=Math.round(x+w),y1=Math.round(y+h);if(w>0&&x1==x0)x1++;if(h>0&&y1==y0)y1++;
        fill(c,x0*pxScale,pxTop+y0*pxScale,x1*pxScale,pxTop+y1*pxScale,color);}
    /** Half the scene pixels of a rectangle, in a checkerboard fixed to the grid: the 8-bit way to show light or haze. */
    private void dither(Canvas c,float x,float y,float w,float h,int color){
        int x0=Math.round(x),y0=Math.round(y),x1=Math.round(x+w),y1=Math.round(y+h);if(x1<=x0||y1<=y0)return;
        if(pxScale==1){for(int yy=y0;yy<y1;yy++)for(int xx=x0+((x0+yy)&1);xx<x1;xx+=2)px(c,xx,yy,1,1,color);return;}   // baking: pixel by pixel
        final int ww=x1-x0,hh=y1-y0,odd=(x0+y0)&1,col=color;   // on screen: one stamp of a baked pattern instead of a rect per pixel
        String key=ww+"x"+hh+"/"+odd+"/"+col;Bitmap b=ditherCache.get(key);
        if(b==null){b=bake(ww,hh,new Art(){public void paint(Canvas d){for(int yy=0;yy<hh;yy++)for(int xx=(odd+yy)&1;xx<ww;xx+=2)px(d,xx,yy,1,1,col);}});ditherCache.put(key,b);}
        put(c,b,x0,y0);}
    /** Dither stamps by size, grid parity and colour: a few lamp glows and spray sizes, so it stays small. */
    private final java.util.HashMap<String,Bitmap> ditherCache=new java.util.HashMap<>();
    /** 4 x 4 ordered-dither thresholds (0..15), for light that thins out with distance in baked art. */
    private static final int[] BAYER={0,8,2,10,12,4,14,6,3,11,1,9,15,7,13,5};
    private void run(Canvas c,int color,float ox,float oy,int y,int x1,int x2){px(c,ox+x1,oy+y,x2-x1+1,1,color);}

    @Override protected void draw(Canvas c){
        if(paint!=CarPaint.index()){paint=CarPaint.index();int[] b=CarPaint.body();B=b[0];BH=b[1];BD=b[2];BS=b[3];Arrays.fill(sideCar,null);arcadeCar.clear();}
        float v=v(7),rpm=v(0),boost=v(Telemetry.BOOST),fuel=v(Telemetry.FUEL_RATE),lat=v(11),lon=v(12);
        float dt=last==0||now-last>2000?0:(now-last)/1000f;last=now;frameDt=dt;
        Calendar cal=Calendar.getInstance();
        phase=DayPhase.of(cal.get(Calendar.MONTH)+1,cal.get(Calendar.HOUR_OF_DAY)*60+cal.get(Calendar.MINUTE));lamps=v(Telemetry.HEAD_LAMP)>0;tail=v(Telemetry.TAIL_LAMP)>0;high=v(Telemetry.HIGH_BEAM)>0;
        wx=WEATHER.update(now,v(Telemetry.WIPER),v(Telemetry.AMBIENT),lamps,phase==DayPhase.DAY);
        if(wx!=remapWx||phase!=remapPh){remapWx=wx;remapPh=phase;remap=remapFor(wx,phase);}
        if(!Arrays.equals(remap,bakedRemap)){bakedRemap=remap;Arrays.fill(cityStrip,null);Arrays.fill(cityChase,null);Arrays.fill(hillStrip,null);}
        float vk=Float.isNaN(v)?0:v,la=Float.isNaN(lat)?0:lat;
        float dm=v(Telemetry.DRIVE_MODE);mode=Float.isNaN(dm)?-1:(int)dm;
        if(mode>=0&&mode!=lastMode){if(lastMode>=0)modeAt=now;lastMode=mode;}
        if(!Float.isNaN(fuel)&&fuel==0&&vk>0){coinSec+=dt;while(coinSec>=1){coinSec-=1;coins+=mode==ECO?2:1;}}
        float ped=v(9);   // backfire: the pedal let go from 40 %+ within 0.7 s while the engine is above 3000 rpm
        if(!Float.isNaN(ped)){if(ped>=40)pedHiAt=now;else if(ped<10&&now-pedHiAt<700&&!Float.isNaN(rpm)&&rpm>3000){fireAt=now;pedHiAt=Long.MIN_VALUE/2;}}
        long fa=EngineSynth.flameAt;if(fa!=seenFlame&&now>=fa){seenFlame=fa;if(now-fa<300)popAt=now;}   // a big overrun pop of the engine sound: a short flame
        // Without the engine sound: a lift from 20 %+ to under 5 % above 2500 rpm gives one short flame 0.15-0.5 s later and,
        // a third of the time, a second one 0.5-0.9 s after that, all inside 1.3 s (about what the stock exhaust's sound does).
        if(!EngineSynth.voiced&&!Float.isNaN(ped)){
            if(ped>=20)liftArmed=true;
            else if(ped<5&&liftArmed){liftArmed=false;if(!Float.isNaN(rpm)&&rpm>2500){liftAt=now;liftPops=0;nextPop=now+150+(long)(Math.random()*350);}}
            if(now-liftAt<1300&&liftPops<2&&now>=nextPop){popAt=now;liftPops++;nextPop=Math.random()<.35?now+500+(long)(Math.random()*400):Long.MAX_VALUE;}
        }
        String g=gear();if(!g.equals(lastGear)){if(!lastGear.isEmpty())shiftAt=now;lastGear=g;}
        boolean brake=v(10)>5,left=v(Telemetry.TURN_LEFT)>0,right=v(Telemetry.TURN_RIGHT)>0;   // the bits blink with the real lamps
        if(chase){
            scroll+=vk*dt*0.06f;
            float steer=v(Telemetry.STEERING),turn=Float.isNaN(steer)?0:Math.max(-1.3f,Math.min(1.3f,-steer/110f));   // the road bends with the steering wheel (steer left -> road curves left), not lateral G
            curve+=(-turn*1.7f-curve)*Math.min(1,dt*2.2f);bgX+=curve*vk*dt*0.05f;
            // The car leans a few pixels toward the steering: enough to see it respond, never enough to distract. The
            // first 2 deg are the SAS wobble straight ahead; past ~30 deg it stays at 6 px.
            float sv=Float.isNaN(steer)?0:steer,dz=sv>0?Math.max(0,sv-2):Math.min(0,sv+2);
            sway+=(6*(float)Math.tanh(dz/12f)-sway)*Math.min(1,dt*4);
            float lo=Float.isNaN(lon)?0:lon;
            // A road jolt (a G spike no shift, pedal, brake or steering explains) bounces the car and camera, then settles;
            // braking and shifts still show only as the horizon and lean.
            bumpE=bumpE*Math.max(0f,1-dt*3.5f)+jolt.update(now,lat,lon,v(9),v(10),steer,v(8))*15f;
            float bump=Math.min(4f,bumpE)*(float)Math.sin(now/38.0);
            chaseScene(c,curve/1.7f,lo,bump,brake,left,right,boost,rpm);   // the eased bend: road and car turn together
        }
        else{scroll+=vk*dt*1.4f;sideScene(c,vk,brake,left,boost,rpm);}
        hud(c,g,rpm,boost,v);
        musicNotes(c,vk);
        if(!chase)nowPlaying(c);
    }
    /** The 8-bit side view shows the track itself (the box below), so the generic track-change bar stays off. */
    @Override boolean showsMusic(){return !chase&&playing();}
    private boolean playing(){return music!=null&&music.playState==1&&music.title!=null&&music.title.length()>0;}
    // ---- now playing: a dialog box on the right, the cover shrunk to 14 x 14 pixels in a small palette ----------------
    private static final float NP_L=468,NP_T=222,NP_R=780,NP_B=344,BTN_T=NP_B-42,BTN_B=NP_B-12,BTN_MID=(NP_L+NP_R)/2;
    private Bitmap npArt,npArtFrom;private String npTrack="";private long npSince,npPressAt;private int npPress;
    private void nowPlaying(Canvas c){
        if(!playing())return;
        String trk=music.title+"|"+music.artist;if(!trk.equals(npTrack)){npTrack=trk;npSince=now;}   // a new track starts at its first letter, 1.5 s still
        fill(c,NP_L,NP_T,NP_R,NP_B,0xff000000);outline(c,NP_L+3,NP_T+3,NP_R-3,NP_B-3,0,3,W);
        float tx=NP_L+16;
        if(artwork!=null){
            if(npArtFrom!=artwork){npArtFrom=artwork;npArt=pixelArt(artwork);}
            blit(c,npArt,0,0,16,16,NP_L+12,NP_T+12,NP_L+12+64,NP_T+12+64);outline(c,NP_L+10,NP_T+10,NP_L+78,NP_T+78,0,2,0xff3a3a4a);tx=NP_L+88;
        }
        float avail=NP_R-14-tx;
        Bitmap tb=label(music.title,W);
        c.save();c.clipRect(tx,NP_T+10,tx+avail,NP_T+38);marquee(c,tb,tx,NP_T+12,avail);c.restore();
        String ar=music.artist==null?"":music.artist;
        if(ar.length()>0){Bitmap ab=label(ar,0xffb0b0c0);c.save();c.clipRect(tx,NP_T+40,tx+avail,NP_T+66);marquee(c,ab,tx,NP_T+42,avail);c.restore();}
        // progress as blocks, like the EXP bar
        if(music.durationMs>0&&music.positionMs>=0){int n=12,lit=Math.round(clamp(music.positionMs/(float)music.durationMs,0,1)*n);float bw=avail/n;
            for(int i=0;i<n;i++){fill(c,tx+i*bw,NP_T+68,tx+(i+1)*bw-2,NP_T+76,0xff000000);fill(c,tx+i*bw+1,NP_T+69,tx+(i+1)*bw-3,NP_T+75,i<lit?0xff3ad8ff:0xff2a2a3a);}}
        // previous / next: two pixel keys along the bottom, yellow for a moment when pressed
        for(int k=0;k<2;k++){float l=k==0?NP_L+12:BTN_MID+4,r=k==0?BTN_MID-4:NP_R-12;boolean hot=npPress==k+1&&now-npPressAt<220;
            fill(c,l,BTN_T,r,BTN_B,hot?Y:0xff1a1a26);outline(c,l,BTN_T,r,BTN_B,0,2,hot?Y:W);
            int ink=hot?0xff000000:W;float cx=(l+r)/2,cy=(BTN_T+BTN_B)/2;
            for(int t=0;t<2;t++){float ox=cx+(t==0?-14:2);   // two pixel triangles: ◀◀ or ▶▶, 3 px rows
                for(int row=0;row<7;row++){float w2=(row<4?row+1:7-row)*3,x0=k==0?ox+12-w2:ox;fill(c,x0,cy-10.5f+row*3,x0+w2,cy-7.5f+row*3,ink);}}}
    }
    /** The previous / next keys of the now-playing box: the media command goes out the way the cassette's keys send theirs. */
    @Override boolean tap(float x,float y){
        if(chase||!playing()||y<BTN_T-4||y>BTN_B+4||x<NP_L+8||x>NP_R-8)return false;
        boolean prev=x<BTN_MID;CassetteCluster.keyCmd=prev?-1:1;npPress=prev?1:2;npPressAt=now;return true;
    }
    /** A label at 2 x, still when it fits, else (after 1.5 s at its start) scrolling left in whole pixels, a gap before it comes round. */
    private void marquee(Canvas c,Bitmap b,float x,float y,float avail){
        float w=b.getWidth()*2,h=b.getHeight()*2;
        if(w<=avail){blit(c,b,0,0,b.getWidth(),b.getHeight(),x,y,x+w,y+h);return;}
        float gap=40,off=((Math.max(0,now-npSince-1500)/30)*2)%(w+gap);for(float xx=x-off;xx<x+avail;xx+=w+gap)blit(c,b,0,0,b.getWidth(),b.getHeight(),xx,y,xx+w,y+h);
    }
    /** The cover at 16 x 16 with 3 bits a channel: drawn back at 4 x 4 per pixel it reads as game art. */
    private static Bitmap pixelArt(Bitmap src){
        Bitmap b=bitmap(16,16);android.graphics.Rect sr=new android.graphics.Rect();sr.set(0,0,src.getWidth(),src.getHeight());android.graphics.RectF dr=new android.graphics.RectF();dr.set(0,0,16,16);new Canvas(b).drawBitmap(src,sr,dr,new Paint(Paint.FILTER_BITMAP_FLAG));
        int[] px=new int[256];b.getPixels(px,0,16,0,0,16,16);for(int i=0;i<px.length;i++)px[i]=0xff000000|(px[i]&0xe0e0e0);b.setPixels(px,0,16,0,0,16,16);return b;
    }
    /** A label at half scale with a one-pixel shadow: the pixel font when it has every letter, otherwise the system face
     *  drawn small without smoothing (Hangul and the like), so both come out as chunky pixels at 2 x. */
    private final java.util.LinkedHashMap<String,Bitmap> labelCache=new java.util.LinkedHashMap<String,Bitmap>(16,0.75f,true){
        @Override protected boolean removeEldestEntry(java.util.Map.Entry<String,Bitmap> e){return size()>8;}};
    private Bitmap label(String s,int color){
        String key=color+"|"+s;Bitmap b=labelCache.get(key);if(b!=null)return b;
        boolean ascii=true;for(int i=0;i<s.length();i++){char ch=s.charAt(i);if(FONT_CHARS.indexOf(Character.toUpperCase(ch))<0&&ch!=' '){ascii=false;break;}}
        if(ascii)b=textBitmap(s.toUpperCase(Locale.US),color);
        else{Paint tp=new Paint(0);tp.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);tp.setTextSize(9);int w=Math.max(1,(int)Math.ceil(tp.measureText(s))+2);
            b=bitmap(w,11);Canvas k=new Canvas(b);tp.setColor(0xff000000);k.drawText(s,1,9,tp);tp.setColor(color);k.drawText(s,0,8,tp);}
        labelCache.put(key,b);return b;
    }
    /** Pixel music notes drifting up while a track plays, like sound out of the car. */
    /** Notes stream out of the car: born as a dot, growing as they drift, and swept backwards faster the quicker it goes. */
    private void musicNotes(Canvas c,float vk){
        if(music==null||music.playState!=1)return;
        float back=5+Math.min(11,Math.abs(vk)*0.16f);   // backward drift (px/s), rises with speed but capped so notes stay readable
        final int N=3;   // few notes, well spaced in time
        for(int i=0;i<N;i++){
            float life=2.4f,ph=now/1000f/life+i*(1f/N);int cyc=(int)ph;float t=ph-cyc;   // one note per loop per voice, staggered
            if(t>0.95f)continue;
            int h=(cyc*131+i*977)&0xffff;float age=t*life,jx=(h&7)-3,jy=((h>>3)&3)-1;
            float s=1+t*7;   // a dot that grows into a full note
            float nx,ny;
            if(chase){boolean rw=((cyc+i)&1)==1;float wx=rw?112:88;   // out of the left and right side windows
                nx=wx+jx+(rw?1:-1)*age*(4+back*0.12f);ny=70+jy-age*(11+back*0.1f);}   // up and out, a diagonal to the side where they show
            else{nx=42+jx-back*age;ny=58+jy-age*18;}                                   // side: rises out of the cabin window, drifting back
            int col=t<0.82f?W:((int)(255*(1-(t-0.82f)/0.14f))<<24)|0xffffff;   // fade as it goes
            note(c,nx,ny,s,col,((h>>8)&1)==0);
        }
    }
    private void note(Canvas c,float x,float y,float s,int col,boolean beamed){
        if(s<2){px(c,x,y,1,1,col);return;}   // a small dot at first
        float th=Math.max(1,s*0.14f);
        px(c,x,y+s*0.6f,s*0.5f,s*0.35f,col);px(c,x+s*0.5f-th,y,th,s*0.8f,col);   // head + stem
        if(beamed){px(c,x+s*0.6f,y+s*0.6f,s*0.5f,s*0.35f,col);px(c,x+s*1.1f-th,y,th,s*0.8f,col);px(c,x+s*0.5f-th,y,s*0.6f+th,th,col);}
        else px(c,x+s*0.5f-th,y,s*0.35f,th,col);   // flag
    }

    // ---- side view ------------------------------------------------------------------------------------------
    private void sideScene(Canvas c,float v,boolean brake,boolean left,float boost,float rpm){
        int[] sky=SKY_SIDE[phase];
        for(int i=0;i<sky.length;i++)px(c,0,i*9,200,i==sky.length-1?58-i*9:9,sky[i]);   // the last band reaches the verge: no gap between buildings
        boolean clear=!Weather.overcast(wx);
        if(phase==DayPhase.NIGHT&&clear)for(int i=0;i<24;i++){int x=200-(int)((i*37+scroll*0.02f)%200),y=(i*13)%28;if((i+now/500)%5!=0)px(c,x,y,1,1,W);}
        if(!clear)clouds(c,scroll*0.03f);
        else if(phase==DayPhase.NIGHT){px(c,120,8,8,8,0xffffe7a8);px(c,119,10,10,4,0xffffe7a8);px(c,123,9,4,4,sky[1]);}     // moon
        else if(phase==DayPhase.DAY){px(c,115,16,10,10,0xfffff2a8);px(c,113,18,14,6,0xfffff2a8);}                            // sun
        else for(int dy=-9;dy<=0;dy++){float w=(float)Math.sqrt(81-dy*dy);px(c,110-w,33+dy,w*2,1,dy<-5?0xffffd27a:0xffff8a5c);}   // setting sun
        final int ph=phase;
        if(hillStrip[ph]==null)hillStrip[ph]=bake(400,26,new Art(){public void paint(Canvas b){   // periodic over 400 so it tiles
            for(int x=0;x<400;x++){int h=12+(int)Math.round(8*Math.sin(x*TAU*3/400)+5*Math.sin(x*TAU*8/400));px(b,x,26-h,1,h,FAR_HILL[ph]);}}});
        strip(c,hillStrip[ph],scroll*0.05f,24);
        strip(c,skyline(1),scroll*0.25f,58-skyline(1).getHeight());
        px(c,0,58,200,4,VERGE[phase]);px(c,0,62,200,45,0xff2b2b36);px(c,0,62,200,1,0xffc8c8d8);px(c,0,88,200,1,0xffc8c8d8);
        int off=(int)(scroll*0.9f)%24;for(int x=-off;x<200;x+=24)px(c,x,75,12,2,0xfff5f5ff);
        boolean sport=mode==SPORT;
        if(v>(sport?30:60))for(int i=0;i<(sport?6:5);i++){float x=200-((scroll*2+i*53)%220);px(c,x,sport?63+i*4:64+i*5,(8+v/20)*(sport?1.25f:1),1,0xff6a6a7a);}
        float cx=20,cy=56+(!Float.isNaN(rpm)&&rpm>prof.redline-1000?(int)Math.round(Math.sin(now/25.0)):0);
        // headlight beam ahead, tail glow behind
        if(lamps){if(beam==null)beam=bake(56,18,new Art(){public void paint(Canvas b){
            for(int dx=0;dx<56;dx++){float d=0.5f*(1-dx/56f);int top=Math.round(7-dx*0.04f),bot=Math.round(8+dx*0.16f);   // dithered: denser near the lamp
                for(int y=top;y<=bot;y++)if(BAYER[(y&3)*4+(dx&3)]<d*16)px(b,dx,y,1,1,0xff8a8670);}}});
            put(c,beam,cx+46,cy);
            if(high){if(highBeam==null)highBeam=bake(96,12,new Art(){public void paint(Canvas b){   // rows 0..11 below the car's top (the cone spans 1..11)
                for(int dx=0;dx<96;dx++){float d=0.4f*(1-dx/96f);int tp=Math.round(6-dx*0.05f),bt=Math.round(8+dx*0.03f);
                    for(int y=tp;y<=bt;y++)if(BAYER[(y&3)*4+(dx&3)]<d*16)px(b,dx,y,1,1,0xff9a9ca8);}}});
                put(c,highBeam,cx+46,cy);}}   // high beam: aimed level and far, a faint whiter cone over the low beam
        if(phase!=DayPhase.DAY){   // lamp glow only after dark, as a dither
            if(tail||lamps||brake)dither(c,cx-7,cy+4,7,4,brake?0xffa02018:0xff5a1410);
            if(left){dither(c,cx+43,cy+5,5,3,0xff8a5a10);dither(c,cx-3,cy+3,4,3,0xff8a5a10);}}
        double per=TAU/5;int spin=(int)(((scroll/5)%per+per)%per/per*6)%6;   // 5 spokes: 6 steps cover a turn
        int key=((brake?1:0)|(left?2:0)|(spin<<2))+(sport?24:0);
        if(sideCar[key]==null){final boolean b0=brake,l0=left;final double a0=spin*per/6;final int d0=sport?1:0;sideCar[key]=bake(47,19,new Art(){public void paint(Canvas b){sideCarArt(b,0,1,b0,l0,a0,d0);}});}
        put(c,sideCar[key],cx,cy-1);
        if(!Float.isNaN(boost)&&boost>0.3f)for(int i=0;i<4;i++){float age=((now/60+i*3)%12)/12f;px(c,cx+2-age*14,cy+12-age*3,2,2,boost>0.9f&&age<0.3f?0xffffcc33:age<0.6f?0xffbbbbbb:0xff666677);}
        float ped=v(9);   // exhaust smoke from the tailpipe under throttle (more gas, more smoke)
        if(!Float.isNaN(ped)&&ped>20)for(int i=0;i<2+(int)(ped/40);i++){float ag=((now/70+i*5)%14)/14f;if(ag<0.66f)px(c,cx-4-ag*18,cy+11-ag*2,2+ag*3,2+ag*2,smoke(ag));}
        if(flame()){boolean big=(now/60)%2==0;px(c,cx-(big?9:7),cy+(big?10:11),big?6:4,big?3:2,0xffff7a1a);px(c,cx-(big?6:5),cy+11,big?3:2,1,0xffffe14a);}   // backfire pop
        if(mode==ECO&&v>5)for(int i=0;i<3;i++){float ag=((now/90+i*8)%24)/24f;leaf(c,cx-2-ag*26,cy+8-ag*12+(float)Math.sin(ag*9+i)*2,i);}   // ECO: leaves blow off behind
        spray(c,cx-8,cy+14,cx-8,cy+14,9);   // rear tyre only, thrown back
        precip(c,frameDt,62);
    }
    /** The car's side view, sprite origin cx,cy (wheels spun to spin radians). */
    private void sideCarArt(Canvas c,float cx,float wy,boolean brake,boolean left,double spin,int drop){
        float cy=wy+drop;   // body (SPORT: one pixel lower); the wheels stay on wy
        for(int y=0;y<SIDE.length;y++)for(int x=0;x<SIDE[y].length();x++){int col=sideColour(SIDE[y].charAt(x),brake);if(col!=0)px(c,cx+x,cy+y,1,1,col);}
        px(c,cx+16,cy-1,2,1,K);
        for(int[] d:new int[][]{{33,5,12},{20,4,12},{9,4,12}})px(c,cx+d[0],cy+d[1],1,d[2]-d[1]+1,BS);
        px(c,cx+22,cy+6,2,1,BS);px(c,cx+11,cy+6,2,1,BS);px(c,cx+6,cy+6,1,1,BS);
        if(left){px(c,cx+29,cy+5,3,1,AMBER);px(c,cx+44,cy+7,2,1,AMBER);px(c,cx+1,cy+5,2,1,AMBER);}
        cy=wy;
        for(int wx:new int[]{7,38}){
            for(int dy=-5;dy<=1;dy++)for(int dx=-5;dx<=5;dx++)if(dx*dx+dy*dy<=21)px(c,cx+wx+dx,cy+12+dy,1,1,0xff0b0c0f);
            for(int dy=-4;dy<=4;dy++)for(int dx=-4;dx<=4;dx++){int d=dx*dx+dy*dy;if(d>15)continue;px(c,cx+wx+dx,cy+12+dy,1,1,d>=10?0xff1c1d20:d<=1?0xff2a2c30:0xff4a4e55);}
            px(c,cx+wx+1,cy+11,2,2,0xffd8282a);
            for(int k=0;k<5;k++){double a=spin+k*TAU/5;for(double rr:new double[]{1.5,2.6})px(c,cx+wx+Math.round(Math.cos(a)*rr),cy+12+Math.round(Math.sin(a)*rr),1,1,0xff8a9098);}
        }
    }
    /** Skyline strip for the current sky: full height for the side view, 0.6 for the chase horizon. */
    private Bitmap skyline(final float scaleH){
        final int ph=phase,slot=ph;Bitmap[] cache=scaleH<1?cityChase:cityStrip;
        if(cache[slot]==null){final int h=Math.round(32*scaleH)+4;cache[slot]=bake(CITY_W,h,new Art(){public void paint(Canvas b){buildings(b,h,scaleH,ph);}});}
        return cache[slot];
    }
    private final Bitmap[] cityChase=new Bitmap[3];
    /** All 40 buildings standing on base, windows lit by sky ph. */
    private void buildings(Canvas c,float base,float scaleH,int phase){
        for(int i=0;i<40;i++){
            int x=CB_X[i],w=CB_W[i];
            int ht=Math.max(4,Math.round(CB_H[i]*scaleH));float top=base-ht;int col=CITY_BODY[phase][i%2];
            px(c,x,top,w,ht,col);if(CB_KIT[i]==1)px(c,x+w/2,top-3,1,3,col);else if(CB_KIT[i]==2)px(c,x+1,top-2,3,2,col);
            // each building: its own share of lit windows (offices bright, some nearly dark) and window style
            int hb=hash3(i,7,11),style=hb%3,stepX=style==1?3:2,wW=style==1?2:1,stepY=style==2?1:3;float density=0.08f+(hb%70)/100f;
            for(int rr=0;top+2+rr*stepY<base-1;rr++){
                boolean floorLit=hash3(i,rr,3)%9==0;   // now and then a whole floor is on
                if(style==2&&rr%4==3)continue;         // vertical strips break every few rows
                for(int cc=0;x+1+cc*stepX+wW<=x+w-1;cc++){
                    float k=hash3(i,rr,cc)%1000/1000f;
                    boolean on=phase==DayPhase.DAY||(phase==DayPhase.TWILIGHT?k<density*0.35f||floorLit&&k<0.5f:k<density||floorLit&&k<0.8f);
                    px(c,x+1+cc*stepX,top+2+rr*stepY,wW,1,phase==DayPhase.DAY?(k<0.5f?0xffa9bfd6:0xff8ea6c0):on?CITY_LIT[phase]:CITY_DARK[phase]);
                }
            }
        }
    }
    /** Integer hash with good mixing, so lit windows scatter instead of drawing diagonals. */
    private static int hash3(int a,int b,int c){int h=a*374761393+b*668265263+c*(int)2246822519L;h=(h^(h>>>13))*1274126177;return (h^(h>>>16))&0x7fffffff;}
    private int sideColour(char ch,boolean brake){
        switch(ch){
            case 'B':return B;case 'h':return BH;case 'D':return BD;case 'K':return K;case 'G':return 0xff2b3644;case 'g':return 0xff56677d;
            case 'L':return 0xffe8eef5;case 'r':return N_RED;case 'R':return brake?0xffff3030:0xff7a1820;default:return 0;
        }
    }

    // ---- chase view -----------------------------------------------------------------------------------------
    private void chaseScene(Canvas c,float lat,float lon,float bump,boolean brake,boolean left,boolean right,float boost,float rpm){
        int hz=Math.round(46-lon*7+bump),bottom=107;
        int[] sky=SKY_CHASE[phase];float bh=hz/(float)sky.length;
        for(int i=0;i<sky.length;i++)px(c,0,i*bh,200,bh+1,sky[i]);
        boolean clear=!Weather.overcast(wx);
        if(!clear)clouds(c,bgX*0.2f);
        else if(phase==DayPhase.NIGHT){for(int i=0;i<22;i++){int x=(int)((i*53+bgX*0.3f)%200+200)%200,y=(i*7)%Math.max(4,hz-24);if((i+now/500)%6!=0)px(c,x,y,1,1,W);}
            px(c,150,8,8,8,0xffffe7a8);px(c,149,10,10,4,0xffffe7a8);px(c,153,9,4,4,sky[1]);}
        else if(phase==DayPhase.DAY){px(c,122,14,10,10,0xfffff2a8);px(c,120,16,14,6,0xfffff2a8);}
        else for(int dy=-16;dy<=0;dy++){if(dy>-8&&dy%3==0)continue;float w=(float)Math.sqrt(256-dy*dy);px(c,100-w,hz-4+dy,w*2,1,dy<-9?0xffffd27a:0xffff8a5c);}
        final int ph=phase;
        if(hillStrip[ph]==null||hillStrip[ph].getHeight()!=(chase?15:26))hillStrip[ph]=bake(400,15,new Art(){public void paint(Canvas b){
            for(int x=0;x<400;x++){int h=6+(int)Math.round(5*Math.sin(x*TAU*3/400)+3*Math.sin(x*TAU*7/400));px(b,x,15-h,1,h,MOUNT_FAR[ph]);}}});
        strip(c,hillStrip[ph],bgX*0.4f,hz-15);
        Bitmap sk=skyline(0.6f);strip(c,sk,bgX,hz-sk.getHeight());
        float[] rowX=new float[bottom+1],rowW=new float[bottom+1];
        boolean[] bands=new boolean[bottom+1];
        int runAt=hz;
        for(int y=hz;y<=bottom;y++){   // grass first, merged into runs of one band
            float p=(y-hz)/(float)(bottom-hz);float z=1/Math.max(p,0.001f);
            bands[y]=((int)Math.floor(z*3+scroll))%2==0;
            // The road is pinned under the car: on the car's ground row it runs through the car's centre, leaving in
            // the car's heading (60 px at the horizon per unit bend), and ahead it bends as a perspective arc. Behind the
            // car it continues that line, so the near road never slides against the car.
            float pc=Math.max(0.3f,(105+bump-hz)/(float)(bottom-hz)),sd=1/Math.max(p,0.016f)-1/pc;
            rowX[y]=100+sway+lat*60*(1-p/pc)+lat*1.3f*p*sd*sd;rowW[y]=6+184*p;
            if(y>hz&&bands[y]!=bands[y-1]){px(c,0,runAt,200,y-runAt,bands[y-1]?GRASS_A[phase]:GRASS_B[phase]);runAt=y;}
        }
        px(c,0,runAt,200,bottom+1-runAt,bands[bottom]?GRASS_A[phase]:GRASS_B[phase]);
        for(int y=hz+1;y<=bottom;y++){
            float cx=rowX[y],w=rowW[y];boolean band=bands[y];
            px(c,cx-w/2-w*0.07f,y,w*1.14f,1,band?0xffe23b3b:0xfff2f2f2);   // both kerbs share the row's colour: one rect under the road
            px(c,cx-w/2,y,w,1,band?0xff3b3b48:0xff363642);
            if(band)px(c,cx-w*0.012f,y,Math.max(1,w*0.024f),1,0xffe8e8f0);
        }
        // roadside lamps and pines, far to near
        for(float m=(float)Math.floor(scroll+15);m>=Math.floor(scroll);m--){
            float z=(m-scroll)/3*2;if(z<0.75f||z>14)continue;
            float p=1/z,y=hz+p*(bottom-hz);if(y>bottom+30)continue;
            int yi=Math.min(bottom,Math.round(y));int mi=(int)m;
            float side=mi%2!=0?1:-1,x=rowX[yi]+side*(rowW[yi]/2+rowW[yi]*0.16f),s=p;
            if(((mi%4)+4)%4<2){px(c,x-s,y-34*s,Math.max(1,2*s),34*s,0xff5a5f6a);px(c,x-side*7*s,y-34*s,8*s,Math.max(1,1.5f*s),0xff5a5f6a);
                px(c,x-side*7*s-s,y-33*s,4*s,Math.max(1,2*s),phase!=DayPhase.DAY?0xffffe9a8:0xff9a9ea6);}
            else{px(c,x-s,y-6*s,Math.max(1,2*s),6*s,0xff3a2416);for(int k=0;k<3;k++)px(c,x-(7-k*2)*s,y-(10+k*6)*s,(14-k*4)*s,7*s,k%2!=0?0xff1f5a3a:0xff18492f);}
        }
        // headlight pool ahead of the car
        int yt=Math.round(hz+(bottom-hz)*0.18f);
        // lit asphalt in two steps (brighter near the car), each in the row's band colour, centre line kept on top
        if(lamps)for(int y=yt;y<=71;y++){boolean near=(y-yt)*2>=72-yt;litRow(c,rowX[y],rowW[y],0.72f,y,bands[y],near?0xff4a4a58:0xff42424f,near?0xff444452:0xff3d3d4a);}
        if(lamps&&high)for(int y=hz+1;y<yt;y++)litRow(c,rowX[y],rowW[y],0.9f,y,bands[y],0xff40404e,0xff3a3a47);   // high beam reaches toward the horizon
        float vk=v(7);
        if(mode==SPORT&&vk>30)for(int i=0;i<4;i++){   // SPORT: speed lines stream out of the vanishing point toward the camera, speeding up
            float t=((now/40+i*11)%36)/36f,dx=i%2==0?-1:1,dy=0.18f+(i/2)*0.22f,n=(float)Math.hypot(dx,dy),r=16+t*t*140,len=2+t*12;
            int col=t<0.2f||t>0.8f?0xff5a5a6a:0xff9a9eaa;   // dim at both ends instead of a fade
            for(int j=0;j<len;j++)px(c,100+dx/n*(r+j),hz+dy/n*(r+j),1,1,col);}
        rear(c,lat,bump,brake,left,right,boost,rpm);
        precip(c,frameDt,hz);
    }
    /** The Arcade car from behind, turned into the bend: one of ArcadeCarFrames' 13 yaws, painted for its lamps and
     * the CarPaint, kept in a small cache (frame x lamp state; about 13 KB each). */
    private final java.util.LinkedHashMap<Integer,Bitmap> arcadeCar=new java.util.LinkedHashMap<Integer,Bitmap>(32,0.75f,true){
        @Override protected boolean removeEldestEntry(java.util.Map.Entry<Integer,Bitmap> e){return size()>24;}};   // ponytail: 24 ~ 320 KB, a turn's worth of frames in two lamp states
    private void rear(Canvas c,float lat,float bump,boolean brake,boolean left,boolean right,float boost,float rpm){
        float ox=76+Math.round(sway),oy=68+Math.round(bump)+(!Float.isNaN(rpm)&&rpm>prof.redline-1000?(int)Math.round(Math.sin(now/25.0)):0);
        px(c,ox-3,oy+37,55,3,0xff1d1d24);   // shadow: one solid dark tone
        if(phase!=DayPhase.DAY){   // lamp glow only after dark, as a dither beside each lamp
            if(tail||lamps||brake){int red=brake?0xffa02018:0xff5a1410;dither(c,ox-5,oy+12,6,5,red);dither(c,ox+48,oy+12,6,5,red);}
            if(left)dither(c,ox-7,oy+10,7,6,0xff8a5a10);if(right)dither(c,ox+49,oy+10,7,6,0xff8a5a10);}
        // lat here is the eased road bend (the same value the road uses, so both turn together); the car yaws 20 times
        // that into the bend (30 deg wheel -> 4 deg, 90 -> 16, max 24)
        final int fi=ArcadeCarFrames.frameFor(-lat*20);final boolean b0=brake,l0=left,r0=right,lp=tail||lamps;
        int key=fi|(brake?32:0)|(left?64:0)|(right?128:0)|(lp?256:0);Bitmap car=arcadeCar.get(key);
        if(car==null){car=bake(ArcadeCarFrames.W,ArcadeCarFrames.H,new Art(){public void paint(Canvas b){arcadeArt(b,fi,b0,l0,r0,lp);}});arcadeCar.put(key,car);}
        put(c,car,ox+ArcadeCarFrames.DX,oy+ArcadeCarFrames.DY);
        if(!Float.isNaN(boost)&&boost>0.9f&&blink(now,120)){for(int ex:new int[]{8,37}){run(c,0xffffcc33,ox,oy,34,ex,ex+3);run(c,0xffff7a1a,ox,oy,35,ex+1,ex+2);}}
        else if(!Float.isNaN(boost)&&boost>0.3f){int k=(int)(now/125%3);for(int ex:new int[]{8,37})px(c,ox+ex+k,oy+34+k%2,3,1,0xffc8c8d2);}
        float ped=v(9);   // exhaust smoke from both tailpipes under throttle (more gas, more smoke)
        if(!Float.isNaN(ped)&&ped>20){int puffs=1+(int)(ped/35);for(int ex:new int[]{8,37})for(int i=0;i<puffs;i++){float ag=((now/80+i*4)%12)/12f;if(ag<0.66f)px(c,ox+ex-(int)(ag*5),oy+34+(int)(ag*10),2+(int)(ag*3),2+(int)(ag*2),smoke(ag));}}
        if(flame()){boolean big=(now/60)%2==0;for(int ex:new int[]{8,37}){px(c,ox+ex-(big?1:0),oy+34,big?6:4,big?4:3,0xffff7a1a);px(c,ox+ex+1,oy+34,2,big?2:1,0xffffe14a);}}   // backfire pop
        spray(c,ox-4,oy+34,ox+41,oy+34,12);   // a wet road throws spray off both rear tyres
        if(mode==ECO)for(int i=0;i<3;i++){float ag=((now/90+i*11)%32)/32f;int side=i%2==0?-1:1;   // ECO: leaves kicked off the rear wheels: a hop, then out and toward the camera (growing)
            leaf(c,ox+24+side*(20+ag*60),oy+34-(float)Math.sin(ag*Math.PI)*12+ag*6,i,ag<0.5f?1:2);}
    }
    /** Exhaust smoke by age (0..1): pale, then darker; gone after two thirds instead of fading out. */
    private static int smoke(float ag){return ag<0.33f?0xffb8bcc2:0xff8e939b;}
    /** One row of lit road: the band's lit tone over the middle share of the lane, the centre line drawn again on top. */
    private void litRow(Canvas c,float cx,float rw,float share,int y,boolean band,int litA,int litB){
        px(c,cx-rw*share/2,y,rw*share,1,band?litA:litB);
        if(band)px(c,cx-rw*0.012f,y,Math.max(1,rw*0.024f),1,0xffe8e8f0);}
    /** A 3 x 2 pixel leaf, its colour and tilt by i. */
    private void leaf(Canvas c,float x,float y,int i){leaf(c,x,y,i,1);}
    /** The leaf at k times its size (nearer the camera). */
    private void leaf(Canvas c,float x,float y,int i,int k){
        int col=i%3==0?0xff4fbf3a:i%3==1?0xff8fd84a:0xffc8e05a;
        if(i%2==0){px(c,x,y,2*k,k,col);px(c,x+k,y+k,2*k,k,col);}else{px(c,x+k,y,2*k,k,col);px(c,x,y+k,2*k,k,col);}
    }
    /** One Arcade frame, row runs of one colour each. */
    private void arcadeArt(Canvas c,int fi,boolean brake,boolean left,boolean right,boolean lp){
        for(int y=0;y<ArcadeCarFrames.H;y++){int run=0,col=0;
            for(int x=0;x<=ArcadeCarFrames.W;x++){int k=x<ArcadeCarFrames.W?ArcadeCarFrames.code(fi,x,y):0,cc=k==0?0:arcadeColour(k,brake,left,right,lp);
                if(cc!=col||k==0){if(col!=0)px(c,x-run,y,run,1,col);col=k==0?0:cc;run=0;}
                if(k!=0)run++;}}
    }
    /** A frame code's colour: paint shades from the CarPaint, lamps as the hand-drawn rear lit them, amber for a turn signal. */
    private int arcadeColour(int code,boolean brake,boolean left,boolean right,boolean lp){
        int[] e=ArcadeCarFrames.CODES[code-1];
        if(e[0]==4){if(e[1]<0?left:right)return AMBER;e=ArcadeCarFrames.CODES[e[2]-1];}
        if(e[0]==1)return e[1]==0?B:e[1]==1?BH:e[1]==2?BD:BS;
        if(e[0]==2)return e[1];
        switch(e[1]){   // tail, LED line, high stop lamp, reflector, clear lens
            case 0:return brake?0xffff3a2a:0xff5a0c14;
            case 1:return brake?0xffffd0c4:lp?0xffe0323c:0xff7a1a22;
            case 2:return brake?0xffff4a3a:0xffa01818;
            case 3:return brake?0xffff3030:0xffa01818;
            default:return 0xff9a9496;
        }
    }

    // ---- HUD (both views) -----------------------------------------------------------------------------------
    private void hud(Canvas c,String g,float rpm,float boost,float v){
        sh(c,"LV",20,82,15,W);sh(c,g,56,90,34,Y);
        sh(c,"EXP",20,114,13,W);
        int blocks=24,litN=Float.isNaN(rpm)?0:Math.round(rpm/prof.maxRpm*blocks),red=Math.round(prof.redline/prof.maxRpm*blocks);
        for(int i=0;i<blocks;i++){fill(c,58+i*8,102,65+i*8,116,0xff000000);fill(c,59+i*8,103,64+i*8,115,i<litN?(i>=red?(blink(now,200)?0xffff3b3b:W):i>=red-3?Y:0xff4ad66d):0xff2a2a3a);}
        if(!Float.isNaN(rpm)&&rpm>prof.redline)sh(c,"MAX!",258,114,13,0xffff3b3b);
        sh(c,"HP",20,142,13,W);
        float range=v(Telemetry.RANGE),hearts=Float.isNaN(range)?0:range/100;
        for(int i=0;i<5;i++)heart(c,58+i*22,128,clamp(hearts-i,0,1));
        float odo=v(Telemetry.ODOMETER);
        sh(c,"SCORE",780,82,13,W,Paint.Align.RIGHT);sh(c,Float.isNaN(odo)?"-------":String.format(Locale.US,"%07d",(int)odo),780,104,18,Y,Paint.Align.RIGHT);
        sh(c,String.format(Locale.US,"COIN x%03d",coins),780,128,13,Y,Paint.Align.RIGHT);
        Calendar k=Calendar.getInstance();sh(c,String.format(Locale.US,"TIME %02d:%02d",k.get(Calendar.HOUR_OF_DAY),k.get(Calendar.MINUTE)),780,150,13,W,Paint.Align.RIGHT);
        boolean hot=!Float.isNaN(boost)&&boost>0.4f;
        int tb=Float.isNaN(boost)?0:Math.round(clamp((boost+0.7f)/(boostTop()+0.7f),0,1)*12);
        float ty=chase?436:168;
        sh(c,"TURBO",20,ty,13,hot?(blink(now,300)?0xffff8a3a:Y):0xff7a7a8a);
        float bx=chase?20:84,by=chase?ty+8:ty-12;
        for(int i=0;i<12;i++){fill(c,bx+i*10,by,bx+9+i*10,by+14,0xff000000);fill(c,bx+1+i*10,by+1,bx+8+i*10,by+13,i<tb?(i>8?0xffff8a3a:0xff3ad8ff):0xff2a2a3a);}
        sh(c,Float.isNaN(boost)?"-.-BAR":String.format(Locale.US,"%+.1fBAR",Math.abs(boost)<0.05f?0f:boost),chase?146:210,chase?by+12:ty,11,hot?W:0xff9a9aaa);   // the pressure now (vacuum below 0)
        String sp=Float.isNaN(v)?"---":String.format(Locale.US,"%03d",Math.round(v));
        if(chase){sh(c,sp,720,464,36,W,Paint.Align.RIGHT);sh(c,"KM/H",780,464,13,W,Paint.Align.RIGHT);}
        else{sh(c,sp,720,214,40,W,Paint.Align.RIGHT);sh(c,"KM/H",780,214,13,W,Paint.Align.RIGHT);}
        String msg=message(rpm,v);
        if(chase){
            if(msg!=null){float w=fontWidth(msg,17)+28;fill(c,400-w/2,164,400+w/2,194,0xff000000);outline(c,400-w/2+2,166,400+w/2-2,192,0,2,Y);
                sh(c,msg,400,186,17,blink(now,500)?Y:W,Paint.Align.CENTER);}
        }else{
            fill(c,20,410,780,468,0xff000000);outline(c,24,414,776,464,0,4,W);outline(c,31,421,769,457,0,2,W);
            sh(c,msg!=null?msg:mode==SPORT?"BATTLE MODE... PUSH IT!":mode==ECO?"ECO RUN... EASY ON THE GAS":"CRUISING...",46,446,17,W);if(blink(now,600))sh(c,"v",752,448,15,Y);
        }
    }
    /** Backfire on screen: a hard lift (450 ms, with the banner) or a big pop of the engine sound (160 ms). */
    private boolean flame(){return now-fireAt<450||now-popAt<160;}
    /** The event of the moment, or null for a plain cruise. */
    private String message(float rpm,float v){
        float brake=v(10),lat=v(11),lon=v(12),fuel=v(Telemetry.FUEL_RATE),pedal=v(9);
        if(!Float.isNaN(rpm)&&rpm>prof.redline-300)return "REV LIMIT! SHIFT!";
        if(now-modeAt<1500)return mode==SPORT?"SPORT MODE - BATTLE START!":mode==ECO?"ECO MODE - SAVE FUEL!":"NORMAL MODE";
        float co=v(Telemetry.CLUTCH_ODD),ce=v(Telemetry.CLUTCH_EVEN);boolean ev=Float.isNaN(co)||!Float.isNaN(ce)&&ce>co;   // 7DCT: the hotter clutch, once warm
        int lv=Telemetry.clutchLevel(ev?ce:co);
        if(lv>0)return String.format(Locale.US,lv==2?"CLUTCH HOT! %s %.0fC - COOL DOWN":"CLUTCH WARM... %s %.0fC",ev?"EVEN":"ODD",ev?ce:co);
        if(now-fireAt<450)return "BACKFIRE! POP POP!";
        if(now-shiftAt<800)return "SHIFT! LV "+lastGear+" REACHED";
        if(!Float.isNaN(brake)&&brake>35)return String.format(Locale.US,"BRAKE!! %.2fG",Float.isNaN(lon)?0:lon);
        if(!Float.isNaN(lat)&&Math.abs(lat)>0.35f)return String.format(Locale.US,"DRIFT! LAT %.2fG",Math.abs(lat));
        if(!Float.isNaN(fuel)&&fuel==0&&v>0)return mode==ECO?"FUEL CUT  +2 COIN/s":"FUEL CUT  +1 COIN/s";
        if(Float.isNaN(v)||v<1)return "READY? PRESS GAS!";
        return !Float.isNaN(pedal)&&pedal>80?"FULL THROTTLE!!":null;
    }
    // ---- 5 x 7 pixel font: every HUD string, glyphs baked once per colour and stamped at a whole-pixel scale --------
    private static final String FONT_CHARS=" !%+-./0123456789:?ABCDEFGHIJKLMNOPQRSTUVWXYZsvx";
    /** 7 rows per glyph, 5 bits each (bit 4 = left column), as hex pairs in FONT_CHARS order. */
    private static final String FONT_ROWS="0000000000000004040404040004181902040813030004041F0404000000001F00000000000000000C0C000102040810000E11131519110E040C040404040E0E11010204081F1F02040201110E02060A121F02021F101E0101110E0608101E11110E1F0102040808080E11110E11110E0E11110F01020C000C0C000C0C000E1101020400040E1111111F11111E11111E11111E0E11101010110E1C12111111121C1F10101E10101F1F10101E1010100E11101711110F1111111F1111110E04040404040E0702020202120C111214181412111010101010101F111B1515111111111119151311110E11111111110E1E11111E1010100E11111115120D1E11111E1412110F10100E01011E1F0404040404041111111111110E11111111110A041111111515150A11110A040A11111111110A0404041F01020408101F00000E100E011E00001F1F0E04000000110A040A11";
    private static final int[] FONT_BITS=new int[FONT_ROWS.length()/2];
    static{for(int i=0;i<FONT_BITS.length;i++)FONT_BITS[i]=Integer.parseInt(FONT_ROWS.substring(i*2,i*2+2),16);}
    /** Strings drawn lately, text and its one-pixel shadow baked at font scale: one blit each. Values change (speed, time),
     * so it is a small LRU; ponytail: 64 entries is about 20 frames of the busiest HUD, raise it if the frame log shows misses. */
    private final java.util.LinkedHashMap<String,Bitmap> textCache=new java.util.LinkedHashMap<String,Bitmap>(80,0.75f,true){
        @Override protected boolean removeEldestEntry(java.util.Map.Entry<String,Bitmap> e){return size()>64;}};
    private Bitmap textBitmap(final String s,final int color){
        String key=color+"|"+s;Bitmap b=textCache.get(key);
        if(b==null){b=bake(Math.max(1,s.length()*6),8,new Art(){public void paint(Canvas a){
            for(int pass=0;pass<2;pass++)for(int i=0;i<s.length();i++){int g=glyph(s.charAt(i));if(g==0)continue;
                for(int r=0;r<7;r++){int bits=FONT_BITS[g*7+r];for(int k=0;k<5;k++)if((bits&(16>>k))!=0)px(a,i*6+k+(pass==0?1:0),r+(pass==0?1:0),1,1,pass==0?0xff000000:color);}}}});
            textCache.put(key,b);}
        return b;
    }
    /** Font pixel size for a text size: 2 for HUD labels (14 px caps), 4 for the big numbers; never 1, 3 or 5, so every
     * HUD pixel is half or all of a scene pixel. */
    private static int fontPx(float size){return size>=26?4:2;}
    private static float fontWidth(String s,float size){int k=fontPx(size);return s.length()*6*k-k;}
    /** Glyph of a character: lower case shown as capitals except s, v (the ▼ prompt) and x; anything else as '-'. */
    private static int glyph(char ch){int g=FONT_CHARS.indexOf(ch);if(g<0)g=FONT_CHARS.indexOf(Character.toUpperCase(ch));return g<0?FONT_CHARS.indexOf('-'):g;}
    private void sh(Canvas c,String s,float x,float y,float size,int color){sh(c,s,x,y,size,color,Paint.Align.LEFT);}
    /** Pixel text with a one-pixel black drop shadow, baseline y: one blit of the baked string. */
    private void sh(Canvas c,String s,float x,float y,float size,int color,Paint.Align a){
        if(s.isEmpty())return;
        int k=fontPx(size);float w=fontWidth(s,size),x0=a==Paint.Align.CENTER?x-w/2:a==Paint.Align.RIGHT?x-w:x,top=y-7*k;
        Bitmap b=textBitmap(s,color);blit(c,b,0,0,b.getWidth(),8,x0,top,x0+b.getWidth()*k,top+8*k);
    }
    private static final String[] HEART={"01100110","11111111","11111111","01111110","00111100","00011000"};
    private void heart(Canvas c,float x,float y,float f){
        final int lv=Math.round(clamp(f,0,1)*8);
        if(heartBmp[lv]==null)heartBmp[lv]=bake(8,6,new Art(){public void paint(Canvas b){
            for(int r=0;r<HEART.length;r++)for(int k=0;k<8;k++)if(HEART[r].charAt(k)=='1')px(b,k,r,1,1,k<lv?0xffff3b5c:0xff3a2030);}});
        blit(c,heartBmp[lv],0,0,8,6,x,y,x+16,y+12);   // 2 px per heart pixel, like the HUD font
    }
}
