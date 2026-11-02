package com.veloster.vehicle;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import java.util.Locale;

/**
 * Drag start tree for track use: stopped for 1.5 s stages the tree, three ambers count down, green waits for the
 * launch. Times run from the launch (speed > 0.5 km/h): reaction from green (a launch before green lights red),
 * 0-60, 0-100 km/h, 201 m (1/8 mile, integrated speed) and the speed there. Best of the session alongside.
 */
final class DragCluster extends ClusterKit {
    private static final int TX=0xffeef0f2,MU=0xff8a8f96,AM=0xffffb000,GR=0xff39ff5a,RD=0xffff2a2a,ACC=0xffa4d9ef;
    private long stoppedAt=-1,treeAt=-1,launchAt=-1,lastAt;
    private boolean foul;
    private float dist;
    private final float[] cur=new float[5],best={Float.NaN,Float.NaN,Float.NaN,Float.NaN,Float.NaN};   // reaction, 0-60, 0-100, 201 m, trap
    private final float[] curveT=new float[200],curveV=new float[200];
    private int curveN;
    { themed=true; java.util.Arrays.fill(cur,Float.NaN); }

    @Override protected void draw(Canvas c){
        float v=v(7);long gap=lastAt==0?0:now-lastAt;lastAt=now;
        if(gap>2000){launchAt=-1;treeAt=-1;stoppedAt=-1;}   // a hidden tab is no run
        boolean stopped=!Float.isNaN(v)&&v<0.5f;
        if(stopped){if(stoppedAt<0)stoppedAt=now;if(launchAt>=0)finish();if(treeAt<0&&now-stoppedAt>1500){treeAt=now;foul=false;}}
        else stoppedAt=-1;
        long green=treeAt<0?-1:treeAt+2000;
        if(!stopped&&!Float.isNaN(v)&&treeAt>=0&&launchAt<0){
            launchAt=now;foul=now<green;dist=0;curveN=0;java.util.Arrays.fill(cur,Float.NaN);cur[0]=(now-green)/1000f;
        }
        float e=launchAt<0?Float.NaN:(now-launchAt)/1000f;
        if(launchAt>=0&&!Float.isNaN(v)){
            dist+=v/3.6f*gap/1000f;
            if(Float.isNaN(cur[1])&&v>=60)cur[1]=e;
            if(Float.isNaN(cur[2])&&v>=100)cur[2]=e;
            if(Float.isNaN(cur[3])&&dist>=201){cur[3]=e;cur[4]=v;}
            if(e<=12&&curveN<200){curveT[curveN]=e;curveV[curveN]=v;curveN++;}
            if(e>20)finish();
        }
        fill(c,0,52,800,480,0xff0a0a0b);
        // tree
        fill(c,136,60,164,476,0xff1b1d20);round(c,70,66,230,468,12,0xff121315);outline(c,70,66,230,468,12,2,0xff2a2c30);
        boolean staged=treeAt>=0&&launchAt<0;
        float since=treeAt<0?-1:(now-treeAt)/1000f;
        for(int x:new int[]{110,190}){
            lamp(c,x,94,9,0xfffff7c2,stopped||staged);lamp(c,x,126,9,0xfffff7c2,staged);
            for(int k=0;k<3;k++)lamp(c,x,180+k*60,22,AM,staged&&since>=0.5f+k*0.5f&&since<0.5f+k*0.5f+0.4f);
            lamp(c,x,362,22,GR,(staged&&since>=2)||(launchAt>=0&&!foul&&now-launchAt<1200));
            lamp(c,x,420,18,RD,launchAt>=0&&foul);
        }
        text(c,"PRE",150,98,11,0xff55575c,Paint.Align.CENTER,Typeface.DEFAULT_BOLD);text(c,"STAGE",150,130,11,0xff55575c,Paint.Align.CENTER,Typeface.DEFAULT_BOLD);
        // right side
        float x0=300;
        String state=launchAt>=0?(foul?t("부정 출발"):"GO"):staged?(since<2?t("곧 출발"):t("출발하세요")):stopped?t("준비 · 정지 상태"):t("정차하면 트리가 켜집니다");
        bold(c,state,x0,86,18,launchAt>=0?(foul?RD:GR):staged?AM:MU,Paint.Align.LEFT);
        String big=launchAt>=0?String.format(Locale.US,"%.3f",e):!Float.isNaN(best[2])?String.format(Locale.US,"%.3f",best[2]):"—";
        bold(c,big,x0,160,84,TX,Paint.Align.LEFT);
        bold(c,launchAt>=0?t("경과 초"):t("최고 0→100 km/h 초"),x0+4,182,13,MU,Paint.Align.LEFT);
        bold(c,fmt("%.0f",v)+" km/h · "+gear(),790,160,30,ACC,Paint.Align.RIGHT);
        bold(c,launchAt>=0?String.format(Locale.US,"%.0f m",dist):"",790,182,15,MU,Paint.Align.RIGHT);
        round(c,x0,200,790,380,8,0xff111214);outline(c,x0,200,790,380,8,1,0xff25272b);
        bold(c,t("이번"),650,224,13,MU,Paint.Align.RIGHT);bold(c,t("최고"),770,224,13,AM,Paint.Align.RIGHT);
        String[] l={t("반응 시간"),"0→60 km/h","0→100 km/h",t("201 m (1/8마일)"),t("통과 속도")};
        for(int i=0;i<5;i++){float y=254+i*28;if(i>0)fill(c,x0+14,y-20,x0+476,y-19,0xff1c1e21);
            bold(c,l[i],x0+16,y,15,TX,Paint.Align.LEFT);bold(c,val(cur[i],i),650,y,19,TX,Paint.Align.RIGHT);bold(c,val(best[i],i),770,y,19,AM,Paint.Align.RIGHT);}
        float cy0=396,ch=78,cw=490;
        fill(c,x0,cy0,x0+cw,cy0+ch,0xff0f1012);
        for(int sp:new int[]{60,100}){float y=cy0+ch-sp/160f*ch;fill(c,x0,y,x0+cw,y+1,0xff24262a);text(c,String.valueOf(sp),x0+cw-4,y-3,12,MU,Paint.Align.RIGHT,Typeface.DEFAULT);}
        for(int i=1;i<curveN;i++)line(c,x0+curveT[i-1]/12*cw,cy0+ch-curveV[i-1]/160*ch,x0+curveT[i]/12*cw,cy0+ch-curveV[i]/160*ch,2,GR);
        text(c,t("트랙·서킷 전용 · 속도 곡선 0~12초"),x0,cy0-4,12,MU,Paint.Align.LEFT,Typeface.DEFAULT);
    }
    private void finish(){
        if(launchAt<0)return;
        if(!foul&&!Float.isNaN(cur[0])&&(Float.isNaN(best[0])||cur[0]<best[0]))best[0]=cur[0];
        for(int i=1;i<4;i++)if(!Float.isNaN(cur[i])&&(Float.isNaN(best[i])||cur[i]<best[i]))best[i]=cur[i];
        if(!Float.isNaN(cur[4])&&(Float.isNaN(best[4])||cur[4]>best[4]))best[4]=cur[4];
        launchAt=-1;treeAt=-1;
    }
    private static String val(float x,int i){return Float.isNaN(x)?"—":i==4?String.format(Locale.US,"%.0f km/h",x):String.format(Locale.US,i==0?"%.3f s":"%.2f s",x);}
    private void lamp(Canvas c,float x,float y,float r,int color,boolean on){
        dot(c,x,y,r+4,0xff050505);
        if(on){dot(c,x,y,r*1.6f,(color&0xffffff)|0x40000000);dot(c,x,y,r,color);dot(c,x-r*0.3f,y-r*0.3f,r*0.35f,0xffffffff);}
        else{dot(c,x,y,r,0xff1f1f22);dot(c,x-r*0.3f,y-r*0.3f,r*0.4f,0xff28282c);}
    }
}
