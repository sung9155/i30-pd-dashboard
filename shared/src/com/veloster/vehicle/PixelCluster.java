package com.veloster.vehicle;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Locale;

/**
 * 8-bit game in two views of a pixel i30 N (PD) in Performance Blue, traced from Hyundai's official side and rear
 * photos: the side view drives past a night city, the chase view follows from behind down a pseudo-3D road that
 * bends with lateral G. LV = gear, EXP = rpm, hearts = range (100 km each), SCORE = odometer, a coin per second of
 * fuel cut, TURBO = boost. The sky follows the clock (DayPhase: day, twilight, night); the car's lamps follow its
 * own signals at any hour: headlamps and tail lamps the HeadLampLow bit, brake lights the brake, and the turn
 * signals their bits (which blink with the real lamps), shown on the car's lamps only. Scene pixels are 4x4 screen pixels.
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
    // Caches: side car [brake|left<<1|spin<<2], rear car [brake|left|right|lamps|tilt], skyline/hills per sky, hearts, beam.
    private final Bitmap[] sideCar=new Bitmap[24],rearCar=new Bitmap[48],cityStrip=new Bitmap[3],hillStrip=new Bitmap[3],heartBmp=new Bitmap[9];
    private Bitmap beam;
    private float pxScale=4,pxTop=52;
    private interface Art{void paint(Canvas c);}
    /** Paints art once into a bitmap at 1 pixel per scene pixel. */
    private Bitmap bake(int w,int h,Art a){Bitmap b=bitmap(w,h);float s=pxScale,t=pxTop;pxScale=1;pxTop=0;a.paint(new Canvas(b));pxScale=s;pxTop=t;return b;}
    /** Stamps a baked bitmap at scene position x,y (4x, no smoothing). */
    private void put(Canvas c,Bitmap b,float x,float y){blit(c,b,0,0,b.getWidth(),b.getHeight(),x*4,52+y*4,(x+b.getWidth())*4,52+(y+b.getHeight())*4);}
    /** Repeats a horizontal strip across the 200-pixel scene, scrolled by off. */
    private void strip(Canvas c,Bitmap b,float off,float y){int w=b.getWidth();float o=((off%w)+w)%w;for(float x=-o;x<200;x+=w)put(c,b,x,y);}
    private int phase;
    /** The car's lamps from CGW1, independent of the sky: low beam (HeadLampLow), tail/position (미등), high beam (상향). */
    private boolean lamps,tail,high;
    private float scroll,curve,bgX,coinSec,bumpE,prevG;
    private int coins;
    private long last,shiftAt=Long.MIN_VALUE/2;
    private String lastGear="";

    PixelCluster(boolean chase){this.chase=chase;}

    private void px(Canvas c,float x,float y,float w,float h,int color){fill(c,x*pxScale,pxTop+y*pxScale,(x+w)*pxScale,pxTop+(y+h)*pxScale,color);}
    private void run(Canvas c,int color,float ox,float oy,int y,int x1,int x2){px(c,ox+x1,oy+y,x2-x1+1,1,color);}

    @Override protected void draw(Canvas c){
        if(paint!=CarPaint.index()){paint=CarPaint.index();int[] b=CarPaint.body();B=b[0];BH=b[1];BD=b[2];BS=b[3];Arrays.fill(sideCar,null);Arrays.fill(rearCar,null);}
        float v=v(7),rpm=v(0),boost=v(Telemetry.BOOST),fuel=v(Telemetry.FUEL_RATE),lat=v(11),lon=v(12);
        float dt=last==0||now-last>2000?0:(now-last)/1000f;last=now;
        Calendar cal=Calendar.getInstance();
        phase=DayPhase.of(cal.get(Calendar.MONTH)+1,cal.get(Calendar.HOUR_OF_DAY)*60+cal.get(Calendar.MINUTE));lamps=v(Telemetry.HEAD_LAMP)>0;tail=v(Telemetry.TAIL_LAMP)>0;high=v(Telemetry.HIGH_BEAM)>0;
        float vk=Float.isNaN(v)?0:v,la=Float.isNaN(lat)?0:lat;
        if(!Float.isNaN(fuel)&&fuel==0&&vk>0){coinSec+=dt;while(coinSec>=1){coinSec-=1;coins++;}}
        String g=gear();if(!g.equals(lastGear)){if(!lastGear.isEmpty())shiftAt=now;lastGear=g;}
        boolean brake=v(10)>5,left=v(Telemetry.TURN_LEFT)>0,right=v(Telemetry.TURN_RIGHT)>0;   // the bits blink with the real lamps
        if(chase){
            scroll+=vk*dt*0.06f;
            float steer=v(Telemetry.STEERING),turn=Float.isNaN(steer)?0:Math.max(-1.3f,Math.min(1.3f,steer/110f));   // the road bends with the steering wheel, not lateral G
            curve+=(-turn*1.7f-curve)*Math.min(1,dt*2.2f);bgX+=curve*vk*dt*0.05f;
            float lo=Float.isNaN(lon)?0:lon,gmag=(float)Math.hypot(la,lo);   // G is the ride: a sharp change jolts the camera, then settles
            bumpE=bumpE*Math.max(0f,1-dt*3.5f)+Math.abs(gmag-prevG)*7f;prevG=gmag;
            float bump=Math.min(6f,bumpE)*(float)Math.sin(now/38.0);
            chaseScene(c,turn,lo,bump,brake,left,right,boost,rpm);
        }
        else{scroll+=vk*dt*1.4f;sideScene(c,vk,brake,left,boost,rpm);}
        hud(c,g,rpm,boost,v);
    }

    // ---- side view ------------------------------------------------------------------------------------------
    private void sideScene(Canvas c,float v,boolean brake,boolean left,float boost,float rpm){
        int[] sky=SKY_SIDE[phase];
        for(int i=0;i<sky.length;i++)px(c,0,i*9,200,9,sky[i]);
        if(phase==DayPhase.NIGHT)for(int i=0;i<24;i++){int x=200-(int)((i*37+scroll*0.02f)%200),y=(i*13)%28;if((i+now/500)%5!=0)px(c,x,y,1,1,W);}
        if(phase==DayPhase.NIGHT){px(c,120,8,8,8,0xffffe7a8);px(c,119,10,10,4,0xffffe7a8);px(c,123,9,4,4,sky[1]);}          // moon
        else if(phase==DayPhase.DAY){px(c,115,16,10,10,0xfffff2a8);px(c,113,18,14,6,0xfffff2a8);}                            // sun
        else for(int dy=-9;dy<=0;dy++){float w=(float)Math.sqrt(81-dy*dy);px(c,110-w,33+dy,w*2,1,dy<-5?0xffffd27a:0xffff8a5c);}   // setting sun
        final int ph=phase;
        if(hillStrip[ph]==null)hillStrip[ph]=bake(400,26,new Art(){public void paint(Canvas b){   // periodic over 400 so it tiles
            for(int x=0;x<400;x++){int h=12+(int)Math.round(8*Math.sin(x*TAU*3/400)+5*Math.sin(x*TAU*8/400));px(b,x,26-h,1,h,FAR_HILL[ph]);}}});
        strip(c,hillStrip[ph],scroll*0.05f,24);
        strip(c,skyline(1),scroll*0.25f,58-skyline(1).getHeight());
        px(c,0,58,200,4,VERGE[phase]);px(c,0,62,200,45,0xff2b2b36);px(c,0,62,200,1,0xffc8c8d8);px(c,0,88,200,1,0xffc8c8d8);
        int off=(int)(scroll*0.9f)%24;for(int x=-off;x<200;x+=24)px(c,x,75,12,2,0xfff5f5ff);
        if(v>60)for(int i=0;i<5;i++){float x=200-((scroll*2+i*53)%220);px(c,x,64+i*5,8+v/20,1,0x80ffffff);}
        float cx=20,cy=56+(!Float.isNaN(rpm)&&rpm>prof.redline-1000?(int)Math.round(Math.sin(now/25.0)):0);
        // headlight beam ahead, tail glow behind
        if(lamps){if(beam==null)beam=bake(56,18,new Art(){public void paint(Canvas b){
            for(int dx=0;dx<56;dx++){int a=(int)(0.22f*(1-dx/56f)*255),top=Math.round(7-dx*0.04f),bot=Math.round(8+dx*0.16f);px(b,dx,top,1,bot-top+1,(a<<24)|0xffecbe);}}});
            put(c,beam,cx+46,cy);
            if(high)for(int dx=0;dx<80;dx++){int a=(int)(0.14f*(1-dx/80f)*255),tp=Math.round(7-dx*0.03f),bt=Math.round(8+dx*0.15f);px(c,cx+46+dx,cy+tp,1,bt-tp+1,(a<<24)|0xffffffe6);}}   // high beam: a longer, whiter cone
        if(tail||lamps||brake)for(int k=0;k<3;k++)px(c,cx-9+3*k,cy+3+k,9-3*k,6-2*k,((int)((brake?0.10f+0.06f*k:0.04f+0.03f*k)*255)<<24)|0xff281e);
        if(left)for(int k=0;k<2;k++){int a=((int)((0.15f+0.15f*k)*255)<<24)|0xffaa1e;px(c,cx+43+k,cy+5+k,6-2*k,4-2*k,a);px(c,cx-3+k,cy+3+k,5-2*k,4-2*k,a);}
        double per=TAU/5;int spin=(int)(((scroll/5)%per+per)%per/per*6)%6;   // 5 spokes: 6 steps cover a turn
        int key=(brake?1:0)|(left?2:0)|(spin<<2);
        if(sideCar[key]==null){final boolean b0=brake,l0=left;final double a0=spin*per/6;sideCar[key]=bake(47,19,new Art(){public void paint(Canvas b){sideCarArt(b,0,1,b0,l0,a0);}});}
        put(c,sideCar[key],cx,cy-1);
        if(!Float.isNaN(boost)&&boost>0.3f)for(int i=0;i<4;i++){float age=((now/60+i*3)%12)/12f;px(c,cx+2-age*14,cy+12-age*3,2,2,boost>0.9f&&age<0.3f?0xffffcc33:age<0.6f?0xffbbbbbb:0xff666677);}
        float ped=v(9);   // exhaust smoke from the tailpipe under throttle (more gas, more smoke)
        if(!Float.isNaN(ped)&&ped>20)for(int i=0;i<2+(int)(ped/40);i++){float ag=((now/70+i*5)%14)/14f;int a=(int)(0.26f*(1-ag)*255);px(c,cx-4-ag*18,cy+11-ag*2,2+ag*3,2+ag*2,(a<<24)|0xff9aa0a8);}
    }
    /** The car's side view, sprite origin cx,cy (wheels spun to spin radians). */
    private void sideCarArt(Canvas c,float cx,float cy,boolean brake,boolean left,double spin){
        for(int y=0;y<SIDE.length;y++)for(int x=0;x<SIDE[y].length();x++){int col=sideColour(SIDE[y].charAt(x),brake);if(col!=0)px(c,cx+x,cy+y,1,1,col);}
        px(c,cx+16,cy-1,2,1,K);
        for(int[] d:new int[][]{{33,5,12},{20,4,12},{9,4,12}})px(c,cx+d[0],cy+d[1],1,d[2]-d[1]+1,BS);
        px(c,cx+22,cy+6,2,1,BS);px(c,cx+11,cy+6,2,1,BS);px(c,cx+6,cy+6,1,1,BS);
        if(left){px(c,cx+29,cy+5,3,1,AMBER);px(c,cx+44,cy+7,2,1,AMBER);px(c,cx+1,cy+5,2,1,AMBER);}
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
        if(phase==DayPhase.NIGHT){for(int i=0;i<22;i++){int x=(int)((i*53+bgX*0.3f)%200+200)%200,y=(i*7)%Math.max(4,hz-24);if((i+now/500)%6!=0)px(c,x,y,1,1,W);}
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
            rowX[y]=100+curve*(1-p)*(1-p)*80+lat*12*p;rowW[y]=6+184*p;
            if(y>hz&&bands[y]!=bands[y-1]){px(c,0,runAt,200,y-runAt,bands[y-1]?GRASS_A[phase]:GRASS_B[phase]);runAt=y;}
        }
        px(c,0,runAt,200,bottom+1-runAt,bands[bottom]?GRASS_A[phase]:GRASS_B[phase]);
        for(int y=hz+1;y<=bottom;y++){
            float cx=rowX[y],w=rowW[y];boolean band=bands[y];
            px(c,cx-w/2-w*0.07f,y,w*0.07f,1,band?0xffe23b3b:0xfff2f2f2);px(c,cx+w/2,y,w*0.07f,1,band?0xffe23b3b:0xfff2f2f2);
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
                px(c,x-side*7*s-s,y-33*s,4*s,Math.max(1,2*s),phase!=DayPhase.DAY?0xffffe9a8:0xff9a9ea6);if(phase!=DayPhase.DAY)px(c,x-side*7*s-6*s,y-30*s,12*s,26*s,0x1fffe9a8);}
            else{px(c,x-s,y-6*s,Math.max(1,2*s),6*s,0xff3a2416);for(int k=0;k<3;k++)px(c,x-(7-k*2)*s,y-(10+k*6)*s,(14-k*4)*s,7*s,k%2!=0?0xff1f5a3a:0xff18492f);}
        }
        // headlight pool ahead of the car
        int yt=Math.round(hz+(bottom-hz)*0.18f);
        if(lamps)for(int y=yt;y<=71;y+=2){float f=(y-yt)/(float)(72-yt);px(c,rowX[y]-rowW[y]*0.36f,y,rowW[y]*0.72f,2,((int)((0.03f+0.16f*f)*255)<<24)|0xffecbe);}
        rear(c,lat,bump,brake,left,right,boost,rpm);
    }
    /** i30 N from straight behind, 49 x 39 lo-res pixels traced from Hyundai's rear view (width : height 1.24). */
    private void rear(Canvas c,float lat,float bump,boolean brake,boolean left,boolean right,float boost,float rpm){
        float ox=76-Math.round(lat*8),oy=68+Math.round(bump)+(!Float.isNaN(rpm)&&rpm>prof.redline-1000?(int)Math.round(Math.sin(now/25.0)):0);
        int tilt=Math.abs(lat)>0.25f?(lat>0?-1:1):0;
        px(c,ox-3,oy+37,55,3,0x80000000);
        if(tail||lamps||brake)for(int k=0;k<3;k++){int red=((int)((brake?0.10f+0.05f*k:0.04f+0.02f*k)*255)<<24)|0xff281e;px(c,ox-6+2*k,oy+12+k,18-4*k,8-2*k,red);px(c,ox+37+2*k,oy+12+k,18-4*k,8-2*k,red);}
        for(int k=0;k<3;k++){int amb=((int)((0.10f+0.07f*k)*255)<<24)|0xffaa1e;if(left)px(c,ox-8+2*k,oy+10+k,20-4*k,10-2*k,amb);if(right)px(c,ox+35+2*k,oy+10+k,20-4*k,10-2*k,amb);}
        int key=(brake?1:0)|(left?2:0)|(right?4:0)|((tail||lamps)?8:0)|((tilt+1)<<4);
        if(rearCar[key]==null){final boolean b0=brake,l0=left,r0=right,lp=tail||lamps;final int t0=tilt;rearCar[key]=bake(51,40,new Art(){public void paint(Canvas b){rearArt(b,1,0,t0,b0,l0,r0,lp);}});}
        put(c,rearCar[key],ox-1,oy);
        if(!Float.isNaN(boost)&&boost>0.9f&&blink(now,120)){for(int ex:new int[]{8,37}){run(c,0xffffcc33,ox,oy,34,ex,ex+3);run(c,0xffff7a1a,ox,oy,35,ex+1,ex+2);}}
        else if(!Float.isNaN(boost)&&boost>0.3f){int k=(int)(now/125%3);for(int ex:new int[]{8,37})px(c,ox+ex+k,oy+34+k%2,3,1,0x99c8c8d2);}
        float ped=v(9);   // exhaust smoke from both tailpipes under throttle (more gas, more smoke)
        if(!Float.isNaN(ped)&&ped>20){int puffs=1+(int)(ped/35);for(int ex:new int[]{8,37})for(int i=0;i<puffs;i++){float ag=((now/80+i*4)%12)/12f;int a=(int)(0.26f*(1-ag)*255);px(c,ox+ex-(int)(ag*5),oy+34+(int)(ag*10),2+(int)(ag*3),2+(int)(ag*2),(a<<24)|0xff9aa0a8);}}
    }
    /** The rear view's body and lamps, origin ox,oy; the cabin shifts tilt pixels into a bend. */
    private void rearArt(Canvas c,float ox,float oy,int tilt,boolean brake,boolean left,boolean right,boolean lamps){
        for(int tx:new int[]{0,41}){px(c,ox+tx,oy+29,8,10,K);for(int y=31;y<38;y+=2)px(c,ox+tx+1,oy+y,6,1,0xff2a2c30);}
        int stop=brake?0xffff4a3a:0xffa01818;
        top(c,K,ox,oy,tilt,0,7,41);top(c,K,ox,oy,tilt,1,6,42);top(c,K,ox,oy,tilt,2,8,40);top(c,K2,ox,oy,tilt,0,9,39);   // wing over the glass
        for(int y=3;y<=11;y++){int l=6-Math.round((y-3)*0.25f),r=48-l;top(c,B,ox,oy,tilt,y,l,r);top(c,0xff1c2532,ox,oy,tilt,y,l+2,r-2);}
        top(c,stop,ox,oy,tilt,2,22,26);top(c,stop,ox,oy,tilt,3,23,25);top(c,stop,ox,oy,tilt,4,24,24);
        for(int r=0;r<4;r++)top(c,0xff46566c,ox,oy,tilt,4+r,31+r*2,32+r*2);
        top(c,K,ox,oy,tilt,10,24,35);top(c,K,ox,oy,tilt,11,23,24);
        for(int[] m:new int[][]{{9,0,3},{10,0,4},{11,1,3}}){top(c,K,ox,oy,tilt,m[0],m[1],m[2]);top(c,K,ox,oy,tilt,m[0],48-m[2],48-m[1]);}
        if(left)top(c,AMBER,ox,oy,tilt,10,0,2);if(right)top(c,AMBER,ox,oy,tilt,10,46,48);
        run(c,B,ox,oy,12,2,46);for(int y=13;y<=20;y++)run(c,B,ox,oy,y,1,47);run(c,BH,ox,oy,12,4,44);
        int tl=brake?0xffff3a2a:0xff5a0c14,led=brake?0xffffd0c4:(tail||lamps)?0xffe0323c:0xff7a1a22;
        mir(c,tl,ox,oy,12,3,14);mir(c,tl,ox,oy,13,2,14);mir(c,tl,ox,oy,14,2,12);mir(c,tl,ox,oy,15,2,8);
        mir(c,led,ox,oy,13,4,14);mir(c,0xff5a0c14,ox,oy,14,9,12);mir(c,led,ox,oy,15,3,6);
        if(left){run(c,AMBER,ox,oy,13,2,6);run(c,AMBER,ox,oy,14,2,5);}if(right){run(c,AMBER,ox,oy,13,42,46);run(c,AMBER,ox,oy,14,43,46);}
        run(c,SV,ox,oy,13,22,26);run(c,SV,ox,oy,14,21,27);run(c,BD,ox,oy,14,23,25);run(c,SV,ox,oy,15,22,26);
        run(c,SV,ox,oy,18,9,13);run(c,SV,ox,oy,18,34,39);run(c,N_RED,ox,oy,18,36,36);run(c,BS,ox,oy,19,18,30);
        // bumper down to the diffuser (photo: blue wraps the corners), plate in the middle, reflectors under the lamps
        for(int y=21;y<=29;y++)run(c,B,ox,oy,y,0,48);run(c,BS,ox,oy,21,4,44);run(c,BH,ox,oy,22,1,47);
        int refl=brake?0xffff3030:0xffa01818;mir(c,refl,ox,oy,19,4,10);mir(c,refl,ox,oy,20,3,10);
        for(int y=23;y<=26;y++)run(c,0xfff2f2f2,ox,oy,y,17,31);run(c,0xff5a5a66,ox,oy,24,19,29);run(c,0xff5a5a66,ox,oy,25,19,29);
        for(int y=26;y<=29;y++){mir(c,K,ox,oy,y,y==26?3:2,11);for(int x=3;x<=10;x++)if(((x+y)&1)!=0)mir(c,K2,ox,oy,y,x,x);}   // honeycomb corner vents
        run(c,BD,ox,oy,29,12,36);
        // diffuser between the wheels: straight red strip between the round tips, fins under it; the road shows below
        run(c,K,ox,oy,30,5,43);run(c,K,ox,oy,31,5,43);run(c,K,ox,oy,32,6,42);run(c,K,ox,oy,33,7,41);
        run(c,N_RED,ox,oy,30,12,12);run(c,N_RED,ox,oy,30,36,36);run(c,N_RED,ox,oy,31,13,35);
        for(int x=17;x<=31;x+=3)for(int y=32;y<=33;y++)run(c,K2,ox,oy,y,x,x);
        run(c,K2,ox,oy,34,8,40);run(c,K2,ox,oy,35,9,39);
        for(int ex:new int[]{8,37}){run(c,SV,ox,oy,30,ex+1,ex+2);run(c,SV,ox,oy,31,ex,ex+3);run(c,SV,ox,oy,32,ex,ex+3);run(c,SV,ox,oy,33,ex+1,ex+2);
            run(c,0xff0b0b0d,ox,oy,31,ex+1,ex+2);run(c,0xff0b0b0d,ox,oy,32,ex+1,ex+2);}
    }
    private void top(Canvas c,int color,float ox,float oy,int tilt,int y,int x1,int x2){run(c,color,ox+tilt,oy,y,x1,x2);}
    private void mir(Canvas c,int color,float ox,float oy,int y,int x1,int x2){run(c,color,ox,oy,y,x1,x2);run(c,color,ox,oy,y,48-x2,48-x1);}

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
        sh(c,String.format(Locale.US,"%.1fBAR",boostTop()),chase?146:210,chase?by+12:ty,11,0xff9a9aaa);
        String sp=Float.isNaN(v)?"---":String.format(Locale.US,"%03d",Math.round(v));
        if(chase){sh(c,sp,720,464,36,W,Paint.Align.RIGHT);sh(c,"KM/H",780,464,13,W,Paint.Align.RIGHT);}
        else{sh(c,sp,720,214,40,W,Paint.Align.RIGHT);sh(c,"KM/H",780,214,13,W,Paint.Align.RIGHT);}
        String msg=message(rpm,v);
        if(chase){
            if(msg!=null){float w=width(msg,17,Typeface.DEFAULT_BOLD)+28;fill(c,400-w/2,164,400+w/2,194,0xbf000000);outline(c,400-w/2+2,166,400+w/2-2,192,0,2,Y);
                sh(c,msg,400,186,17,blink(now,500)?Y:W,Paint.Align.CENTER);}
        }else{
            fill(c,20,410,780,468,0xff000000);outline(c,24,414,776,464,0,4,W);outline(c,31,421,769,457,0,2,W);
            sh(c,msg==null?"CRUISING...":msg,46,446,17,W);if(blink(now,600))sh(c,"v",752,448,15,Y);
        }
    }
    /** The event of the moment, or null for a plain cruise. */
    private String message(float rpm,float v){
        float brake=v(10),lat=v(11),lon=v(12),fuel=v(Telemetry.FUEL_RATE),pedal=v(9);
        if(!Float.isNaN(rpm)&&rpm>prof.redline-300)return "REV LIMIT! SHIFT!";
        if(now-shiftAt<800)return "SHIFT! LV "+lastGear+" REACHED";
        if(!Float.isNaN(brake)&&brake>35)return String.format(Locale.US,"BRAKE!! %.2fG",Float.isNaN(lon)?0:lon);
        if(!Float.isNaN(lat)&&Math.abs(lat)>0.35f)return String.format(Locale.US,"DRIFT! LAT %.2fG",Math.abs(lat));
        if(!Float.isNaN(fuel)&&fuel==0&&v>0)return "FUEL CUT  +1 COIN/s";
        if(Float.isNaN(v)||v<1)return "READY? PRESS GAS!";
        return !Float.isNaN(pedal)&&pedal>80?"FULL THROTTLE!!":null;
    }
    private void sh(Canvas c,String s,float x,float y,float size,int color){sh(c,s,x,y,size,color,Paint.Align.LEFT);}
    private void sh(Canvas c,String s,float x,float y,float size,int color,Paint.Align a){
        text(c,s,x+2,y+2,size,0xff000000,a,Typeface.DEFAULT_BOLD);text(c,s,x,y,size,color,a,Typeface.DEFAULT_BOLD);
    }
    private static final String[] HEART={"01100110","11111111","11111111","01111110","00111100","00011000"};
    private void heart(Canvas c,float x,float y,float f){
        final int lv=Math.round(clamp(f,0,1)*8);
        if(heartBmp[lv]==null)heartBmp[lv]=bake(8,6,new Art(){public void paint(Canvas b){
            for(int r=0;r<HEART.length;r++)for(int k=0;k<8;k++)if(HEART[r].charAt(k)=='1')px(b,k,r,1,1,k<lv?0xffff3b5c:0xff3a2030);}});
        blit(c,heartBmp[lv],0,0,8,6,x,y,x+20,y+15);
    }
}
