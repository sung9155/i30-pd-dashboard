package com.veloster.vehicle;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import java.util.Calendar;
import java.util.Locale;

/**
 * Taxi meter: red seven-segment fare, lamps, the classic running horse in an LED window, and a receipt. The fare is
 * TaxiFare (Seoul medium taxi, distance and time combined). The trip starts when the car moves and ends with the 빈차
 * button; it keeps counting while another page is up (tick runs every frame). The right panel is
 * the car radio (now playing) until a trip ends; then it is that trip's receipt until tapped or the next trip starts.
 */
final class TaxiCluster extends ClusterKit {
    private static final int RED=0xffff3622,RED_OFF=0xff2b0806,AMB=0xffffb02a,AMB_OFF=0xff251905;
    private boolean hired;
    private final TaxiFare fare=new TaxiFare();
    { fare.start(0); }   // the idle meter shows the base fare
    /** Hour of day, read once a second (the surcharge and its lamp). */
    private int hour=-1;private long hourAt;
    /** The last trip ended with the 빈차 button: show its receipt instead of the radio. */
    private boolean ended;
    /** Ended while still moving: the next trip waits for a stop (3 km/h or less) so the receipt stays up. */
    private boolean waitStop;
    private float km,slowSec,tripSec;
    private long last;
    private int frame;
    private long frameAt;
    /**
     * One gallop stride (10 frames, 0.4 s at 25 fps) traced from a video of a horse running on a beach: the dark
     * silhouette above the waterline, lined up on the torso and averaged into 48x28 cells (lit when 38 % covered).
     * Row-major bits, 4 per hex digit.
     */
    private static final String[] STRIDE={
        "00000000000000000000200000000007fc0000000007fc000000000ffc000000001ffc000000007ffc000001e0fffe000003ffff9e0003dfffff8f0007ffffff86000fffffff00003fcfffff00003f07ffff80000003ffff00000003cfff00000001c1ff00000001c01f80000003c003c0000006c001e0000006e001200000023003200000021806300000020c0c10000003060018000001820018000000c0000000000080000000",
        "00000000000000000000000000000001f8000000000ffc000000000ffc000000001ffe000000007ffe000000c0fffe000007ffffde00079fffff8f000fffffff03001fffffff00003f1fffff00003e0fffff00000007ffff000000078fff0000000783ff0000000f800f8000001d8003800000198001c0000011c000c0000010c000c00000104000e00000102000b000001030009800000c18008c00000c08000400001000000000",
        "00000000000000000000000000000000f80000000007fc000000001ffc000000000ffe000000007ffe000000807fff000007f1ffff00077fffffc7800fffffff83001fffffff80003c1fffff00003c1fffff0000301fffff0000001f1ffe0000000e07fe0000001c001e00000038001f00000070000b800000700009c00000e00008c00003b000086000001000083000001000081800001800080c00000c000c0e00000800000000",
        "00000000000000000000000000000000f80000000003f8000000001ffc000000000ffe000000003ffe000001c03fff00000ffdffff0003ffffffc7800fffffff81801fffffff80003c1fffff0000301fffff0000001ffffe0000001f9ffe0000003e07fe00000038007e00000060007e000000600066000003c000630000018000418000008000c1800000800080c000008000804000008000806000008000c03000000000000000",
        "00000000000000000000000000000000fc0000000001fc000000001ffe000000000ffe000000001fff000003f07fff80001fffffef8003ffffffc38007ffffff80001fffffff80003c1fffff0000381fffff0000001ffffe0000001fdffe0000003e03fe00000030007c00000070007c0000006000cc000000e0018c0000008001840000018003040000070002060000000006020000000004020000000004020000000002000000",
        "00000000000000000000380000000000f80000000001fc000000000ffe000000001ffe000000001fff000003f87fff80001fffffc38003ffffffc18007ffffff80000fffffff80003e1fffff0000001fffff0000001ffffe0000001fcffe0000003c01fc00000078007c0000006800700000004c0060000000c800e00000039000e00000038001a0000000000620000000001c200000000000200000000000200000000000000000",
        "00000000000000000000780000000000fc0000000003fc000000000ffe000000000fff000000e01fff800003feffef80000fffffc18001bfffffc00003ffffff80000fffffff80001f8fffff0000100fffff0000000fffff0000003fe3fe0000003f00fe0000003200780000002100f80000002080f00000002080f00000006180a0000000c10fe00000008001000000000001000000000003000000000001000000000000000000",
        "00000000300000000000780000000001fc0000000007fe0000000007fe000000000fff000001f83fff800003ffffe780000fffffc18001ffffffc00003ffffff800007ffffff80001fcfffff80000f07ffff80000007ffff0000001ff1fe0000001dc07c00000019807c00000008c07c00000008604c0000000c30cc0000000c11fc0000001831e0000000000200000000000600000000000c000000000008000000000008000000",
        "00000000f00000000003f80000000007fc0000000007fe000000000ffe000000001ffe000001f87fff000007ffffc700000fffffc30001bfffffc00003ffffff800003ffffff80000fe7ffff80001f03ffff80000203ffff00000003b3ff00000003307f000000073033800000033031c0000001b03040000000d830800000004fb10000000067ef0000000062000000000003000000000001000000000000000000000000000000",
        "00000000f00000000003f80000000007fc000000001ffc000000001ffe000000001ffe000001e07fff000007ffffcf00000fffffc38003ffffff830003ffffff800007ffffff80000fefffff80001e07ffff80001c01ffff00000001e7ff00000000e07fc0000000e01de0000001e00e70000000c00230000000e00630000000703c200000007838200000002c006000000014004000000018000000000008000000000000000000"
    };
    private static boolean[][] horse(){
        boolean[][] f=new boolean[STRIDE.length][48*28];
        for(int k=0;k<STRIDE.length;k++)for(int i=0;i<48*28;i++)f[k][i]=(Character.digit(STRIDE[k].charAt(i>>2),16)>>(3-(i&3))&1)!=0;
        return f;
    }
    /** Gallop frames on a 48x28 dot grid (STRIDE), decoded once. */
    private static final boolean[][] HORSE=horse();
    /** The same frames as small bitmaps, painted on first use (one stamp per frame instead of ~250 squares). */
    private final Bitmap[] horseBmp=new Bitmap[HORSE.length];

    /** +20 % 22-23 and 02-04 h, +40 % 23-02 h. */
    private float sur(){return hour<0?0:hour>=23||hour<2?0.4f:hour>=22||hour<4?0.2f:0;}   // -1: not read yet
    @Override protected void tick(){
        float v=v(7);
        long dt=last==0?0:now-last;last=now;
        if(hour<0||now-hourAt>=1000){hour=Calendar.getInstance().get(Calendar.HOUR_OF_DAY);hourAt=now;}
        float sur=sur();
        if(waitStop&&!Float.isNaN(v)&&v<=3)waitStop=false;
        if(!hired&&!waitStop&&!Float.isNaN(v)&&v>3){hired=true;ended=false;km=0;slowSec=0;tripSec=0;fare.start(sur);}
        if(dt>0&&dt<2000&&hired&&!Float.isNaN(v)){km+=v*dt/3600000f;tripSec+=dt/1000f;if(v<=15)slowSec+=dt/1000f;fare.add(v,dt/1000f,sur);}
    }
    @Override protected void draw(Canvas c){
        float v=v(7),sur=sur();
        if(hired&&!Float.isNaN(v)&&v>0.5f&&now-frameAt>Math.max(40,260-v*2.2f)){frame=(frame+1)%HORSE.length;frameAt=now;}
        int total=fare.total();

        fill(c,0,52,800,480,0xff14110e);
        round(c,14,60,522,470,14,0xff1d1d20);round(c,22,68,514,462,10,0xff0c0c0d);
        text(c,"KOREA DIGITAL TAXIMETER",34,88,12,0xff5d5d63,Paint.Align.LEFT,Typeface.DEFAULT_BOLD);
        // lamps
        String[] ln={t("빈차"),t("주행"),t("할증"),t("예약"),t("호출")};boolean[] on={!hired,hired,sur>0,false,false};int[] lc={0xffff3b2f,0xff2fe06a,0xffffb020,0xff3fa2ff,0xffff3b2f};
        for(int i=0;i<5;i++){float x=34+i*94;round(c,x,98,x+84,128,5,on[i]?lc[i]:0xff1a1a1c);bold(c,ln[i],x+42,119,16,on[i]?0xff111111:0xff4a4a50,Paint.Align.CENTER);}
        // fare window: horse + fare
        round(c,34,140,502,290,8,0xff070000);outline(c,34,140,502,290,8,1.5f,0xff3a1310);
        bold(c,v>0.5f?t("요금 · 주행"):t("요금 · 정차"),46,162,13,0xff8a2a20,Paint.Align.LEFT);
        boolean[] dots=HORSE[hired&&v>0.5f?frame:3];
        fill(c,44,174,154,240,0xff120302);   // ponytail: lit dots only (about 250 squares), no ghost grid: cheap in software
        int hf=hired&&v>0.5f?frame:3;
        if(horseBmp[hf]==null){Bitmap b=bitmap(108,63);Canvas bc=new Canvas(b);
            for(int y=0;y<28;y++)for(int x=0;x<48;x++)if(dots[y*48+x])fill(bc,x*2.25f,y*2.25f,1.8f+x*2.25f,1.8f+y*2.25f,RED);horseBmp[hf]=b;}
        blit(c,horseBmp[hf],0,0,108,63,46,176,154,239);
        String shown=String.valueOf(total);while(shown.length()<5)shown=" "+shown;
        seg7(c,shown,452,160,90,RED,RED_OFF,false);
        bold(c,t("원"),494,252,22,0xffff4a36,Paint.Align.RIGHT);
        boolean slowNow=!Float.isNaN(v)&&v<=15;
        text(c,!hired?t("출발하면 미터가 켜집니다"):slowNow?t("시간 요금 · 30초당 100원"):String.format(Locale.US,t("다음 100원까지 %.0f m"),Math.ceil(fare.toNext())),46,280,12,0xffa3413a,Paint.Align.LEFT,Typeface.DEFAULT);
        fill(c,290,272,490,280,RED_OFF);fill(c,290,272,290+200*fare.progress(),280,RED);
        // distance / time / slow time
        String[] lab={t("주행 거리 km"),t("주행 시간"),t("저속 시간")};
        String[] val={String.format(Locale.US,"%.2f",km),mmss(tripSec),mmss(slowSec)};
        for(int i=0;i<3;i++){float x=34+i*158;round(c,x,302,x+148,374,6,0xff070300);text(c,lab[i],x+10,320,12,0xff8a6a3a,Paint.Align.LEFT,Typeface.DEFAULT);
            seg7(c,val[i],x+138,330,34,AMB,AMB_OFF,false);}
        // end-trip button
        round(c,34,388,268,450,8,0xff232326);round(c,38,392,264,446,6,hired?0xff3a1d1b:0xff1d1d20);
        bold(c,t("빈차 · 운행 종료"),151,425,17,hired?0xffd8d8dc:0xff5a5a60,Paint.Align.CENTER);
        text(c,t("기본 4,800원 (1.6 km)"),282,414,13,0xff8a8a90,Paint.Align.LEFT,Typeface.DEFAULT);
        text(c,t("할증 22~04시 · 23~02시 40%"),282,438,13,0xff8a8a90,Paint.Align.LEFT,Typeface.DEFAULT);
        if(ended)receipt(c,total,v);else radio(c);
    }
    /** Taxi dash radio: source, play state, title over two lines, artist, progress, in the meter's amber. */
    private void radio(Canvas c){
        float l=538,tp=64,rr=784,b=452;
        round(c,l,tp,rr,b,12,0xff1d1d20);round(c,l+4,tp+4,rr-4,b-4,9,0xff0c0c0d);
        boolean has=music!=null&&music.title!=null&&!music.title.isEmpty();
        text(c,"CAR AUDIO",l+16,tp+26,12,0xff5d5d63,Paint.Align.LEFT,Typeface.DEFAULT_BOLD);
        if(has&&music.source!=null)bold(c,music.source,rr-16,tp+26,12,AMB,Paint.Align.RIGHT);
        round(c,l+14,tp+40,rr-14,tp+250,6,0xff120c04);outline(c,l+14,tp+40,rr-14,tp+250,6,1.5f,0xff3a2a10);
        if(!has){
            bold(c,t("재생 중인 곡 없음"),(l+rr)/2,tp+140,17,0xff8a6a3a,Paint.Align.CENTER);
        }else{
            boolean playing=music.playState==1;
            if(music.playState>=0){   // -1: the source never said; show neither
                bold(c,playing?"▶":"❚❚",l+30,tp+72,16,AMB,Paint.Align.LEFT);
                text(c,playing?t("재생 중"):t("일시정지"),l+54,tp+71,12,0xff8a6a3a,Paint.Align.LEFT,Typeface.DEFAULT);
            }
            String[] lines=wrap(music.title,20,rr-l-56);
            for(int i=0;i<lines.length;i++)bold(c,lines[i],l+28,tp+108+i*28,20,AMB,Paint.Align.LEFT);
            if(music.artist!=null&&!music.artist.isEmpty())text(c,wrap(music.artist,14,rr-l-56)[0],l+28,tp+108+lines.length*28+4,14,0xffc89a50,Paint.Align.LEFT,Typeface.DEFAULT);
            long pos=Math.min(music.durationMs,music.positionMs+(playing?music.timeAgeMs:0));
            if(music.hasProgress()){   // position and duration both known
                float f=clamp(pos/(float)music.durationMs,0,1);
                fill(c,l+28,tp+214,rr-28,tp+218,AMB_OFF);fill(c,l+28,tp+214,l+28+(rr-l-56)*f,tp+218,AMB);
                mono(c,clock(pos),l+28,tp+238,13,0xffc89a50,Paint.Align.LEFT);mono(c,clock(music.durationMs),rr-28,tp+238,13,0xff8a6a3a,Paint.Align.RIGHT);
            }
        }
        // volume and tune knobs
        for(float x:new float[]{l+66,rr-66}){ring(c,x,tp+306,26,3,0xff3a3a40);dot(c,x,tp+306,20,0xff232326);dot(c,x,tp+290,3,0xff8a8a90);}
        text(c,t("운행을 마치면 영수증이 나옵니다"),(l+rr)/2,b-20,12,0xff5a5a60,Paint.Align.CENTER,Typeface.DEFAULT);
    }
    /** Up to two lines that fit maxW at this size, breaking anywhere (titles may have no spaces); the rest becomes …. */
    private String[] wrap(String s,float size,float maxW){
        java.util.List<String> out=new java.util.ArrayList<String>();int i=0;
        while(i<s.length()&&out.size()<2){
            int j=i+1;while(j<s.length()&&width(s.substring(i,j+1),size,Typeface.DEFAULT_BOLD)<=maxW)j++;
            String line=s.substring(i,j);
            if(out.size()==1&&j<s.length()){while(line.length()>1&&width(line+"…",size,Typeface.DEFAULT_BOLD)>maxW)line=line.substring(0,line.length()-1);line+="…";}
            out.add(line.trim());i=j;
        }
        return out.toArray(new String[0]);
    }
    private static String clock(long ms){long sec=Math.max(0,ms)/1000;return String.format(Locale.US,"%d:%02d",sec/60,sec%60);}
    private void receipt(Canvas c,int total,float v){
        float l=538,t=64,rr=784,b=444;
        fill(c,l+6,t+6,rr+4,b+6,0x80000000);fill(c,l,t,rr,b,0xfff1ede3);
        for(float x=l;x<rr;x+=12)fill(c,x,b,x+6,b+8,0xfff1ede3);
        int ink=0xff2a2724,soft=0xff7a756c;
        bold(c,t("영 수 증"),(l+rr)/2,t+30,18,0xff1d1b18,Paint.Align.CENTER);
        for(float x=l+16;x<rr-16;x+=6)fill(c,x,t+44,x+3,t+45,0xffbdb7aa);
        mono(c,t("거리  ")+String.format(Locale.US,"%.2f km",km),l+16,t+68,13,ink,Paint.Align.LEFT);
        mono(c,t("시간  ")+mmss(tripSec),l+16,t+88,13,ink,Paint.Align.LEFT);
        for(float x=l+16;x<rr-16;x+=6)fill(c,x,t+102,x+3,t+103,0xffbdb7aa);
        String[] a={t("기본요금 1.6 km"),t("거리 131 m × ")+fare.distUnits,t("시간 30초 × ")+fare.timeUnits,t("심야 할증")};
        int[] w={4800,fare.distUnits*100,fare.timeUnits*100,fare.surcharge()};
        for(int i=0;i<4;i++){text(c,a[i],l+16,t+126+i*24,13,ink,Paint.Align.LEFT,Typeface.DEFAULT);mono(c,String.format(Locale.US,"%,d",w[i]),rr-16,t+126+i*24,14,ink,Paint.Align.RIGHT);}
        fill(c,l+16,t+212,rr-16,t+214,ink);
        bold(c,t("합계"),l+16,t+242,16,0xff1d1b18,Paint.Align.LEFT);bold(c,String.format(Locale.US,t("%,d원"),total),rr-16,t+242,20,0xffb3261e,Paint.Align.RIGHT);
        text(c,hired?t("운행 중"):t("운행 종료"),l+16,t+268,12,soft,Paint.Align.LEFT,Typeface.DEFAULT);
        text(c,t("현재 ")+fmt("%.0f",v)+" km/h · "+gear(),l+16,t+288,12,soft,Paint.Align.LEFT,Typeface.DEFAULT);
        for(int i=0,x=(int)l+30;x<rr-30;i++){int bw=1+((i*7)%3);fill(c,x,t+310,x+bw,t+344,ink);x+=bw+1+((i*5)%2);}
        text(c,t("누르면 음악으로"),(l+rr)/2,t+370,12,soft,Paint.Align.CENTER,Typeface.DEFAULT);
    }
    @Override boolean tap(float x,float y){
        if(x>=34&&x<=268&&y>=388&&y<=450&&hired){hired=false;ended=true;waitStop=true;return true;}
        if(x>=538&&x<=784&&y>=64&&y<=452&&ended){ended=false;return true;}   // receipt back to the radio
        return false;
    }
    private static String mmss(float sec){int s=(int)sec;return String.format(Locale.US,"%02d:%02d",s/60,s%60);}

}
