package com.veloster.vehicle;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import java.util.Locale;

/**
 * 동승자 screen: for the passenger seat, large type. Left a big G circle, the ball where the body is pushed with a short
 * trail (DriveStats' last samples); right a friendly line about what the car is doing, the speed, and four cards: this
 * drive's top G and top speed, 0-100 and 80-120 km/h (this drive's best, or the run as it happens, with the all-time
 * best from 기록실 under it). Follows the theme.
 *
 * Built for the head unit: rings, grid and card frames are drawn once into a bitmap (again on a theme or language change);
 * each frame draws text, the ball and the trail as circles of fixed sizes.
 */
final class PassengerCluster extends ClusterKit {
    private static final int BG=0xff040506,PANEL=0xff101315,LINE=0xff303638,RING=0xff1d2326,FG=0xffe6f1f6,MUT=0xff91a5ae,ACC=0xffa4d9ef,
        AMB=0xffffb84d,ORG=0xffff7a3d,GRN=0xff3ddc84;
    private static final float CX=210,CY=214,R=178,CARD_W=176,CARD_H=92;
    private static final float[][] CARDS={{424,226},{608,226},{424,326},{608,326}};
    private Bitmap face;private int faceKey;
    private String phrase="";private float phraseSize=26;
    { themed=true; }

    private static Typeface num(){Typeface f=LambdaCluster.FACE_BOLD;return f!=null?f:Typeface.DEFAULT_BOLD;}

    @Override protected void draw(Canvas c){
        c.save();c.translate(0,52);
        int key=System.identityHashCode(Theme.current())*31+com.veloster.i18n.UiText.language().hashCode();
        if(face==null||key!=faceKey){face=recycle(face);face=buildFace();faceKey=key;}
        c.drawBitmap(face,0,0,null);
        float lat=v(11),lon=v(12),kmh=v(7),pedal=v(9);
        boolean g=!Float.isNaN(lat)&&!Float.isNaN(lon);float gs=g?(float)Math.hypot(lat,lon):Float.NaN;
        // trail, oldest faintest and smallest; then the ball
        if(st!=null){int n=st.trailCount();
            for(int i=0;i<n;i++){float a=st.trailX(i),o=st.trailY(i),k=Telemetry.pinG(a,o,1),f=(i+1f)/n;
                dot(c,CX+a*k*R,CY+o*k*R,3+Math.round(f*7),mix(ACC,BG,0.15f+f*0.6f));}}
        if(g){float k=Telemetry.pinG(lat,lon,1);dot(c,CX+lat*k*R,CY+lon*k*R,22,gs>=0.6f?ORG:gs>=0.3f?AMB:ACC);ring(c,CX+lat*k*R,CY+lon*k*R,22,2,FG);}
        text(c,fmt("%.2f",gs),24,52,34,FG,Paint.Align.LEFT,num());
        // the line, the speed
        String ph=t(phrase(kmh,lat,lon,pedal));
        if(!ph.equals(phrase)){phrase=ph;phraseSize=26;while(phraseSize>16&&width(ph,phraseSize,Typeface.DEFAULT_BOLD)>CARD_W*2-24)phraseSize-=2;}   // measured on a change only
        bold(c,ph,CARDS[0][0]+CARD_W+4,54,phraseSize,FG,Paint.Align.CENTER);
        text(c,fmt("%.0f",kmh),700,200,118,FG,Paint.Align.RIGHT,num());
        // cards
        DriveStats d=st;
        card(c,0,d==null?"—":fmt("%.2f",d.maxG),null);
        card(c,1,d==null?"—":fmt("%.0f",d.maxSpeed),null);
        run(c,2,0);run(c,3,2);
        c.restore();
    }
    /** What the car is doing, in a friendly line (Korean source; English in translations/en.json). */
    private String phrase(float kmh,float lat,float lon,float pedal){
        DriveStats d=st;
        if(Float.isNaN(kmh))return "차량 정보를 기다리는 중이에요";
        if(d!=null&&d.running[0])return "0→100 재는 중!";
        if(d!=null&&!Float.isNaN(d.last[0])&&now-d.finishedAt[0]<6000)return "방금 0→100 기록이 나왔어요!";
        if(kmh<1)return d!=null&&d.coolDownLeft()>0?"터보를 식히는 중이에요":"정차 중";
        if(!Float.isNaN(lat)&&Math.abs(lat)>=0.35f)return lat>0?"왼쪽으로 돌아요 · 꽉 잡으세요":"오른쪽으로 돌아요 · 꽉 잡으세요";
        if(!Float.isNaN(lon)&&lon<=-0.35f)return "속도를 줄이는 중이에요";
        if(!Float.isNaN(lon)&&lon>=0.25f||!Float.isNaN(pedal)&&pedal>=70)return "힘껏 가속하는 중!";
        return "편안하게 달리는 중이에요";
    }
    private void card(Canvas c,int i,String value,String sub){
        float l=CARDS[i][0],y=CARDS[i][1];
        text(c,value,l+CARD_W-14,y+(sub==null?72:60),36,FG,Paint.Align.RIGHT,num());
        if(sub!=null)bold(c,sub,l+CARD_W-14,y+84,14,MUT,Paint.Align.RIGHT);
    }
    /** A run card: live time while it runs, else this drive's best; the all-time best under it. */
    private void run(Canvas c,int i,int k){
        DriveStats d=st;
        boolean live=d!=null&&d.running[k];float shown=live?(now-d.runStart[k])/1000f:d==null?Float.NaN:d.best[k];
        float all=Records.best(Records.runKey(k));
        card(c,i,fmt("%.2f",shown),t("역대")+" "+(Float.isNaN(all)?"—":String.format(Locale.US,"%.2f",all)));
        if(live)dot(c,CARDS[i][0]+14,CARDS[i][1]+44,6,GRN);
    }

    // ---- the still parts, drawn once ----
    @Override void trim(){face=recycle(face);faceKey=0;}
    private Bitmap buildFace(){
        Bitmap b=bitmap(800,428);Canvas c=new Canvas(b);
        fill(c,0,0,800,428,BG);
        dot(c,CX,CY,R,0xff080b0d);
        for(float gr:new float[]{0.25f,0.5f,0.75f,1f})ring(c,CX,CY,R*gr,gr==1?2.5f:1,gr==1?LINE:RING);
        line(c,CX-R,CY,CX+R,CY,1,RING);line(c,CX,CY-R,CX,CY+R,1,RING);
        text(c,"0.5",CX+R*0.5f*0.707f+4,CY-R*0.5f*0.707f-4,14,MUT,Paint.Align.LEFT,Typeface.DEFAULT);
        text(c,"1 G",CX+R*0.707f+4,CY-R*0.707f-4,14,MUT,Paint.Align.LEFT,Typeface.DEFAULT);
        bold(c,t("제동"),CX,CY-R+22,15,MUT,Paint.Align.CENTER);bold(c,t("가속"),CX,CY+R-10,15,MUT,Paint.Align.CENTER);
        bold(c,t("지금 G"),24,18,14,MUT,Paint.Align.LEFT);
        round(c,CARDS[0][0],12,CARDS[1][0]+CARD_W,78,12,PANEL);outline(c,CARDS[0][0],12,CARDS[1][0]+CARD_W,78,12,1.5f,ACC);
        bold(c,"km/h",712,200,18,MUT,Paint.Align.LEFT);bold(c,t("지금 속도"),CARDS[0][0],112,15,MUT,Paint.Align.LEFT);
        String[] name={"최고 G","최고 속도","0→100 km/h","80→120 km/h"},unit={"G","km/h",t("초"),t("초")};
        for(int i=0;i<4;i++){float l=CARDS[i][0],y=CARDS[i][1];round(c,l,y,l+CARD_W,y+CARD_H,10,PANEL);outline(c,l,y,l+CARD_W,y+CARD_H,10,1,LINE);
            bold(c,t(name[i]),l+14,y+24,15,MUT,Paint.Align.LEFT);bold(c,unit[i],l+CARD_W-14,y+24,13,MUT,Paint.Align.RIGHT);}
        return b;
    }
}
