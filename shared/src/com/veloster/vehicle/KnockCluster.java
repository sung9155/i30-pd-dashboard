package com.veloster.vehicle;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import java.util.Locale;

/**
 * Spark and knock monitor. There is no knock-sensor value on the bus, so "retard" is estimated: at full load
 * (pedal > 70 %, boost > 0.3 bar) the spark is compared with the full-load line from our 10/30 drive
 * (about 13 - 9 x boost degrees; idle -3.8, light cruise 31.5, heavy load 2.3), and anything later than that
 * line counts. Another car, a tuned one above all, can sit off that line. 30 s boost/spark chart, a 60 s
 * spark-vs-boost map coloured by intake temperature, and the spark at the session's peak boost.
 */
final class KnockCluster extends ClusterKit {
    private static final int TX=0xffe6f1f6,MU=0xff91a5ae,CY=0xff3ad8ff,AM=0xffffb000,LN=0xff1d2326,CARD=0xff101315,EDGE=0xff303638;
    private float pkBoost=Float.NaN,pkSpark,pkRpm,pkIat;
    { themed=true; }

    private static float reference(float boost){return SparkRetard.reference(boost);}
    private static float retard(float spark,float boost,float pedal){return SparkRetard.estimate(spark,boost,pedal);}

    @Override protected void draw(Canvas c){
        float spark=v(Telemetry.SPARK),boost=v(Telemetry.BOOST),pedal=v(9),iat=v(Telemetry.INTAKE),rpm=v(0),load=v(Telemetry.ENGINE_LOAD),top=boostTop();
        float ret=retard(spark,boost,pedal);
        if(!Float.isNaN(boost)&&(Float.isNaN(pkBoost)||boost>pkBoost)){pkBoost=boost;pkSpark=spark;pkRpm=rpm;pkIat=iat;}
        Telemetry.Series hs=model==null?null:model.history(Telemetry.SPARK,now,60000),hb=model==null?null:model.history(Telemetry.BOOST,now,60000),
            hp=model==null?null:model.history(9,now,60000),hi=model==null?null:model.history(Telemetry.INTAKE,now,60000);
        // Each signal is stored as it arrives; read boost, pedal and intake at the spark sample times (pairing by index
        // would mix moments as soon as one of them misses a sample).
        if(hs!=null){if(hb!=null)hb=hb.at(hs.times,300);if(hp!=null)hp=hp.at(hs.times,300);if(hi!=null)hi=hi.at(hs.times,300);}
        int n=hs==null||hb==null||hp==null?0:Math.min(hs.size(),Math.min(hb.size(),hp.size()));
        int events=0;boolean wasHigh=false;
        for(int i=0;i<n;i++){boolean high=hs.valid[i]&&hb.valid[i]&&hp.valid[i]&&retard(hs.values[i],hb.values[i],hp.values[i])>2;if(high&&!wasHigh)events++;wasHigh=high;}
        fill(c,0,52,800,480,0xff07090b);
        String state=Float.isNaN(spark)?"—":v(Telemetry.FUEL_RATE)==0&&v(7)>0?t("연료 차단"):v(7)<1&&!Float.isNaN(pedal)&&pedal<2?t("공회전"):t("부하")+" "+fmt("%.0f",load)+"%";
        card(c,14,190,t("점화시기"),fmt("%+.1f",spark),"° BTDC",state,ret>0.5f?AM:TX);
        card(c,212,190,t("부스트"),fmt("%+.2f",boost),"bar",String.format(Locale.US,t("범위")+" %.1f bar · "+t("최고")+" %s",top,fmt("%.2f",pkBoost)),CY);
        card(c,410,190,t("지각 (추정)"),ret>0.05f?String.format(Locale.US,"-%.1f",ret):"0.0","°",t("최근 60초")+" "+events+t("회"),ret>0.5f?danger():TX);
        card(c,608,178,t("흡기온"),fmt("%.1f",iat),"°C",fmt("%,.0f",rpm)+" rpm",TX);
        // 30 s chart: boost (left axis) and spark (right axis), red ticks where retard is estimated
        float x0=44,y0=172,cw=470,ch=176;long t0=now-30000;
        fill(c,x0,y0,x0+cw,y0+ch,0xff0b1014);
        for(float b=-1;b<=top+0.01f;b+=0.5f){float y=yb(b,top,y0,ch);fill(c,x0,y,x0+cw,y+1,LN);text(c,String.format(Locale.US,"%.1f",b),x0-6,y+4,11,CY,Paint.Align.RIGHT,Typeface.DEFAULT);}
        for(int s=-10;s<=40;s+=10)text(c,String.valueOf(s),x0+cw+6,ys(s,y0,ch)+4,11,AM,Paint.Align.LEFT,Typeface.DEFAULT);
        float pbx=Float.NaN,pby=0,psx=Float.NaN,psy=0;
        for(int i=0;i<n;i++){
            if(hs.times[i]<t0){continue;}
            float x=x0+(hs.times[i]-t0)/30000f*cw;
            if(hs.valid[i]&&hb.valid[i]&&hp.valid[i]&&retard(hs.values[i],hb.values[i],hp.values[i])>2){fill(c,x-1,y0,x+1,y0+ch,0x40ff4a4a);bold(c,"▼",x,y0+12,11,danger(),Paint.Align.CENTER);}
            if(hb.valid[i]){float y=yb(hb.values[i],top,y0,ch);if(!Float.isNaN(pbx)&&!hb.breakBefore[i])line(c,pbx,pby,x,y,2,CY);pbx=x;pby=y;}else pbx=Float.NaN;
            if(hs.valid[i]){float y=ys(hs.values[i],y0,ch);if(!Float.isNaN(psx)&&!hs.breakBefore[i])line(c,psx,psy,x,y,2,AM);psx=x;psy=y;}else psx=Float.NaN;
        }
        bold(c,t("부스트 bar"),x0,y0-6,12,CY,Paint.Align.LEFT);bold(c,t("점화 °"),x0+cw,y0-6,12,AM,Paint.Align.RIGHT);
        bold(c,"▼ "+t("지각 추정"),x0+cw/2,y0-6,12,danger(),Paint.Align.CENTER);text(c,t("최근 30초"),x0+cw/2,y0+ch+16,11,MU,Paint.Align.CENTER,Typeface.DEFAULT);
        // map: spark against boost, last 60 s, blue (cool intake) to orange (hot)
        float sx0=574,sw=206,sy0=172,sh=176;
        fill(c,sx0,sy0,sx0+sw,sy0+sh,0xff0b1014);
        for(float b=0;b<=top+0.01f;b+=0.5f){float x=qx(b,top,sx0,sw);fill(c,x,sy0,x+1,sy0+sh,LN);}
        for(float b=0;b<top;b+=0.1f){float b2=Math.min(top,b+0.05f);line(c,qx(b,top,sx0,sw),ys(reference(b),sy0,sh),qx(b2,top,sx0,sw),ys(reference(b2),sy0,sh),1.5f,0xff5a6a74);}
        text(c,t("풀부하 기준"),qx(0,top,sx0,sw)+4,ys(13,sy0,sh)-8,11,0xff8a9aa4,Paint.Align.LEFT,Typeface.DEFAULT);
        int ni=hi==null?0:Math.min(n,hi.size());
        for(int i=0;i<n;i++){if(!hs.valid[i]||!hb.valid[i])continue;float temp=i<ni&&hi.valid[i]?hi.values[i]:35;
            int col=mix(0xffff8a3a,0xff3a9cff,clamp((temp-30)/15,0,1));
            float x=qx(hb.values[i],top,sx0,sw),y=ys(hs.values[i],sy0,sh);fill(c,x-1.5f,y-1.5f,x+1.5f,y+1.5f,(col&0xffffff)|0x8c000000);}
        if(!Float.isNaN(boost)&&!Float.isNaN(spark))ring(c,qx(boost,top,sx0,sw),ys(spark,sy0,sh),6,2,0xffffffff);
        bold(c,t("점화 맵 · 최근 60초"),sx0,sy0-6,12,TX,Paint.Align.LEFT);text(c,t("부스트 →"),sx0+sw,sy0+sh+16,11,MU,Paint.Align.RIGHT,Typeface.DEFAULT);
        // peak record, load and rpm, note
        round(c,14,372,314,468,8,CARD);outline(c,14,372,314,468,8,1,EDGE);
        bold(c,t("최고 부스트 순간"),28,394,13,MU,Paint.Align.LEFT);
        String[][] pk={{t("부스트"),fmt("%+.2f",pkBoost)+" bar"},{t("점화"),fmt("%+.1f",Float.isNaN(pkBoost)?Float.NaN:pkSpark)+"°"},{"RPM",fmt("%,.0f",Float.isNaN(pkBoost)?Float.NaN:pkRpm)},{t("흡기"),fmt("%.1f",Float.isNaN(pkBoost)?Float.NaN:pkIat)+"°C"}};
        for(int i=0;i<4;i++){float x=28+(i%2)*144,y=420+(i/2)*30;text(c,pk[i][0],x,y,12,MU,Paint.Align.LEFT,Typeface.DEFAULT);bold(c,pk[i][1],x+130,y,18,TX,Paint.Align.RIGHT);}
        round(c,324,372,514,468,8,CARD);outline(c,324,372,514,468,8,1,EDGE);
        bar(c,394,t("부하"),Float.isNaN(load)?0:load/100,fmt("%.0f",load)+" %",0xff3ddc84);
        bar(c,430,"RPM",Float.isNaN(rpm)?0:rpm/prof.maxRpm,fmt("%,.0f",rpm),!Float.isNaN(rpm)&&rpm>prof.redline-500?danger():0xffa4d9ef);
        round(c,524,372,786,468,8,CARD);outline(c,524,372,786,468,8,1,EDGE);
        String[] note={t("노킹 센서 값은 버스에 없어"),t("풀부하에서 점화가 기준선보다"),t("늦어진 정도로 추정합니다.")};
        for(int i=0;i<3;i++)text(c,note[i],538,396+i*22,12.5f,0xff9fb0ba,Paint.Align.LEFT,Typeface.DEFAULT);
    }
    private static float yb(float b,float top,float y0,float h){return y0+h-(b+1)/(top+1)*h;}
    private static float ys(float s,float y0,float h){return y0+h-(clamp(s,-15,40)+15)/55*h;}
    private static float qx(float b,float top,float x0,float w){return x0+(clamp(b,-1,top)+1)/(top+1)*w;}
    private void card(Canvas c,float x,float w,String label,String val,String unit,String sub,int col){
        round(c,x,58,x+w,142,8,CARD);outline(c,x,58,x+w,142,8,1,EDGE);
        bold(c,label,x+12,78,13,MU,Paint.Align.LEFT);bold(c,val,x+12,120,38,col,Paint.Align.LEFT);
        bold(c,unit,x+18+width(val,38,Typeface.DEFAULT_BOLD),120,15,MU,Paint.Align.LEFT);text(c,sub,x+12,136,12,MU,Paint.Align.LEFT,Typeface.DEFAULT);
    }
    private void bar(Canvas c,float y,String l,float f,String txt,int color){
        text(c,l,338,y,12,MU,Paint.Align.LEFT,Typeface.DEFAULT);bold(c,txt,500,y,14,TX,Paint.Align.RIGHT);
        fill(c,338,y+6,500,y+14,0xff18222a);fill(c,338,y+6,338+162*clamp(f,0,1),y+14,color);
    }
}
