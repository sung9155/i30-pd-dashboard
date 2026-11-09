package com.veloster.vehicle;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

/**
 * 에코 screen: real-time economy for any PD (petrol, diesel, 6MT, 7DCT). Three panels and a two-minute graph.
 * Left (tap to switch): the fuel used dripping into 2 L PET bottles that shelve themselves when full, or the money spent
 * dropping out of the tailpipe (a render of an i30 3D model, the body in the 차량 색) onto moving asphalt. Centre: speed,
 * gear, rpm; the 3 s economy (tap: km/L or L/100km), or ∞ / 퓨얼 컷 on overrun fuel cut, or L/h on idle; the pedal.
 * Right: the trip's fuel cost as a taxi meter (tap it: fuel price − / +; hold it 1 s: a new trip), won per minute,
 * average, litres, km, and the money the fuel cuts saved. Graph: one bar a second, height = L/100km over that second
 * (frugal leaves it empty), one green-yellow-red gradient; grey = idle, teal dot = fuel cut, orange strip = DPF burn.
 *
 * Built for the head unit (Android 4.2): everything costly is drawn once into bitmaps (asphalt phases, the tinted car,
 * coin sprites) and each frame only places them; no clipPath, no per-frame shaders or allocations.
 */
final class EcoCluster extends ClusterKit {
    // Settings (VehicleDash loads and saves them) and the trip. Static: a recreated view keeps them; a new app start is a new trip.
    static volatile boolean coins,l100,coinSound,hintDone,holding;
    /** Won per litre; 0 = the default for the fuel. */
    static volatile int price;
    /** eco_car_rear / eco_car_paint (res/raw, 428 x 480), loaded by VehicleDash; null draws the coin view without the car. */
    static volatile Bitmap CAR,PAINT;
    /** A coin is about to hit the road: 10, 100 or 500 (first contact, the largest since the last read) or -10.. (its softer
     *  bounce); VehicleDash plays it when coinSound is on. */
    static volatile int clink;
    /** Something moves (driving, coins in the air): VehicleDash keeps the fast frame rate. */
    static volatile boolean animating;
    private static volatile boolean changed;
    // ponytail: rough 2026 pump prices (won/L); the driver sets theirs by tapping the fare
    static final int PRICE_PETROL=1650,PRICE_DIESEL=1550,PRICE_MIN=500,PRICE_MAX=3000;
    static int price(VehicleProfile p){return price>0?price:p.fuel==VehicleProfile.Fuel.DIESEL?PRICE_DIESEL:PRICE_PETROL;}
    /** Idle burn until one is measured standing still (EMS20: 0.4-0.8 L/h on the PD). */
    static final float IDLE_LPH=0.65f,BOTTLE_L=2,LMAX=40;

    private static float km,litres,coastS,savedL,idleLph=Float.NaN,wD,wF,secT,secD,secF,secCut,secIdle,secRegen;
    private static long lastTick;
    private static final int BARS=120;
    /** L/100km per second, -1 idle, -2 fuel cut; ring of BARS. */
    private static final float[] bar=new float[BARS];private static final boolean[] barRegen=new boolean[BARS];
    private static int barN,barHead;
    private static volatile long toastUntil;private static long editUntil,shownSince;

    /** A new trip (hold on the fare, or a new app start). */
    static void reset(long now){km=litres=coastS=savedL=wD=wF=secT=secD=secF=secCut=secIdle=secRegen=0;barN=barHead=0;toastUntil=now+1600;resync=true;}
    /** True once after a tap changed a setting (VehicleDash saves them all). */
    static boolean consumeChanged(){boolean c=changed;changed=false;return c;}
    static boolean onFare(float x,float y){return x>=R_L&&x<=R_R&&y>=TOP&&y<=BOT;}

    private static final float TOP=60,BOT=300,L_L=20,L_R=234,C_L=262,C_R=526,R_L=550,R_R=780,G_T=312,G_B=470;
    private static final int BG=0xff07090b,PANEL=0xff0f1417,LINE=0xff222b31,FG=0xffe8eef2,MUT=0xff8a979e,GRN=0xff3ddc84,TEA=0xff5fe3e0,
        AMB=0xffffb84d,ORG=0xffff7a3d,LED=0xff1a1306,IDLE_BAR=0xff3a444a;

    @Override protected void tick(){
        long t=now;float dt=lastTick==0?0:(t-lastTick)/1000f;lastTick=t;
        if(dt<=0||dt>1)return;   // a gap (app paused, data dropout) is not driven
        Telemetry.Sample sv=s[7],sf=s[Telemetry.FUEL_RATE];
        if(!sv.valid||!sf.valid)return;
        float kmh=Math.max(0,Telemetry.displayValue(7,sv.value)),lph=Math.max(0,sf.value),dk=kmh*dt/3600,dl=lph*dt/3600;
        km+=dk;litres+=dl;
        boolean moving=kmh>3,cut=moving&&lph<0.05f;
        if(kmh<1&&lph>0.1f)idleLph=Float.isNaN(idleLph)?lph:idleLph+(lph-idleLph)*Math.min(1,dt/30);
        if(cut){coastS+=dt;savedL+=(Float.isNaN(idleLph)?IDLE_LPH:idleLph)*dt/3600;}
        float k=(float)Math.exp(-dt/3);wD=wD*k+dk;wF=wF*k+dl;   // about the last 3 s
        secT+=dt;secD+=dk;secF+=dl;if(cut)secCut+=dt;else if(!moving)secIdle+=dt;if(regen())secRegen+=dt;
        if(secT>=1){
            bar[barHead]=secCut>secT/2?-2:secIdle>secT/2?-1:secD>1e-5f?Math.min(LMAX,100*secF/secD):LMAX;
            barRegen[barHead]=secRegen>secT/2;barHead=(barHead+1)%BARS;barN=Math.min(BARS,barN+1);
            secT=secD=secF=secCut=secIdle=secRegen=0;
        }
    }
    private boolean regen(){return prof.fuel==VehicleProfile.Fuel.DIESEL&&s[Telemetry.DPF_REGEN].valid&&s[Telemetry.DPF_REGEN].value>0;}
    private static int money(VehicleProfile p){return (int)(litres*price(p)/10)*10;}

    // ---- animation state (draw only) ----
    private static volatile boolean resync=true;
    private long lastDraw;private int prevMoney,pend;private float emit,roadOff;
    private static final int MAXC=12,MAXG=40;
    private final float[] cx=new float[MAXC],cy=new float[MAXC],cvx=new float[MAXC],cvy=new float[MAXC],csp=new float[MAXC],cgy=new float[MAXC];
    private final int[] cden=new int[MAXC],cb=new int[MAXC],cs=new int[MAXC];private int cn;
    /** Clinks are asked this long before a coin reaches the road, for the audio path's start-up delay. ponytail: a guess, tune in the car. */
    private static final float CLINK_LEAD_S=0.12f;
    private final float[] gx=new float[MAXG],gy=new float[MAXG];private final int[] gden=new int[MAXG];private int gn;
    private final float[] dy=new float[8],dv=new float[8];private int dn;
    private final float[] slideT=new float[4];private final int[] slideN=new int[4];private int sn,prevDone=-1;
    private final float[] puffT=new float[6],puffX=new float[6],puffY=new float[6];private int pn;
    private final java.util.Random rnd=new java.util.Random(7);

    @Override protected void draw(Canvas c){
        long t=now;float dt=lastDraw==0?0:(t-lastDraw)/1000f;
        if(dt>0.5f||resync){resync=false;prevMoney=money(prof);pend=0;cn=gn=dn=sn=pn=0;prevDone=(int)(litres/BOTTLE_L);dt=0;shownSince=t;}
        lastDraw=t;dt=Math.min(dt,0.1f);
        fill(c,0,52,800,480,BG);
        float kmh=v(7),lph=v(Telemetry.FUEL_RATE);
        boolean known=!Float.isNaN(kmh)&&!Float.isNaN(lph),moving=known&&kmh>3,cut=moving&&lph<0.05f,idle=known&&!moving;
        int am=Theme.modeAccent(),acc=am!=0?0xff000000|am:Theme.current().accent;
        step(dt,known?kmh:0,known?lph:0,cut);
        animating=moving||cn>0||dn>0||sn>0||pn>0;
        // left
        round(c,L_L,TOP,L_R,BOT,10,PANEL);
        if(coins)tailpipe(c,cut,lph);else bottles(c,cut,lph);
        outline(c,L_L,TOP,L_R,BOT,10,1,LINE);
        if(!hintDone&&t-shownSince<8000){round(c,36,266,218,294,14,0xe0070a0b);outline(c,36,266,218,294,14,1,acc);
            bold(c,t(coins?"누르면 페트병 보기":"누르면 배기구 보기"),127,286,15,FG,Paint.Align.CENTER);}
        centre(c,acc,kmh,lph,known,cut,idle);
        fare(c,acc,lph,cut);
        graph(c);
    }

    // ---- centre: speed, gear, rpm; the number; the pedal ----
    private void centre(Canvas c,int acc,float kmh,float lph,boolean known,boolean cut,boolean idle){
        String g=gear();boolean num=g.length()==1&&Character.isDigit(g.charAt(0));
        box(c,C_L,fmt("%.0f",kmh),"km/h",acc);box(c,C_L+90,g,num?t("단"):"",acc);box(c,C_L+180,fmt("%.0f",v(0)),"rpm",acc);
        float avg=litres>0.002f?km/litres:Float.NaN,kml=wF>1e-6f?wD/wF:Float.POSITIVE_INFINITY;
        String big;int col;
        if(!known){big="—";col=MUT;}
        else if(cut){big="∞";col=TEA;}
        else if(idle){big=fmt("%.2f",lph);col=MUT;}
        else{big=economy(kml);float far=Float.isNaN(avg)||Float.isInfinite(kml)?1:kml/avg;col=far<0.6f?AMB:far>1.5f?GRN:FG;}   // white unless far from the trip's average
        float sz=104;Typeface f=cut?Typeface.DEFAULT_BOLD:numFace();while(sz>60&&width(big,sz,f)>C_R-C_L-16)sz-=4;
        text(c,big,(C_L+C_R)/2,224,sz,col,Paint.Align.CENTER,f);
        if(cut||idle){int cc=cut?TEA:FG;round(c,334,234,454,264,15,cut?0xff0e2526:0xff1c2328);outline(c,334,234,454,264,15,1,cc);
            bold(c,cut?t("퓨얼 컷"):"L/h · Idle",394,255,16,cc,Paint.Align.CENTER);}
        else text(c,l100?"L/100km":"km/L",394,256,22,FG,Paint.Align.CENTER,numFace());
        float pedal=v(9),pw=Float.isNaN(pedal)?0:264*clamp(pedal/100,0,1);
        round(c,C_L,272,C_R,282,5,PANEL);outline(c,C_L,272,C_R,282,5,1,LINE);
        if(pw>1)round(c,C_L,272,C_L+pw,282,5,pedal>60?ORG:pedal>30?AMB:GRN);
        bold(c,t("페달")+" "+fmt("%.0f",pedal)+" %",C_L,298,16,MUT,Paint.Align.LEFT);
    }
    private String economy(float kml){
        if(Float.isNaN(kml)||kml<=0)return "—";float x=l100?(Float.isInfinite(kml)?0:100/kml):kml;
        return Float.isInfinite(x)||x>=99.95f?"99.9":fmt("%.1f",x);
    }
    private void box(Canvas c,float l,String value,String label,int acc){
        round(c,l,64,l+84,120,8,PANEL);outline(c,l,64,l+84,120,8,1,mix(acc,LINE,0.45f));
        float sz=38;Typeface f=numFace();while(sz>20&&width(value,sz,f)>76)sz-=2;
        text(c,value,l+42,label.isEmpty()?106:100,sz,FG,Paint.Align.CENTER,f);
        if(!label.isEmpty())bold(c,label,l+42,115,12,MUT,Paint.Align.CENTER);
    }
    private static Typeface numFace(){Typeface f=LambdaCluster.FACE_BOLD;return f!=null?f:Typeface.DEFAULT_BOLD;}

    // ---- right: the fare ----
    private void fare(Canvas c,int acc,float lph,boolean cut){
        round(c,R_L,TOP,R_R,BOT,10,PANEL);outline(c,R_L,TOP,R_R,BOT,10,holding?2:1,holding?AMB:LINE);
        boolean rg=regen();
        bold(c,rg?t("DPF 재생 중"):t("이번 주행 연료비"),564,86,15,rg?AMB:MUT,Paint.Align.LEFT);
        round(c,564,94,766,146,8,LED);outline(c,564,94,766,146,8,flash>0?2:1,flash>0?AMB:acc);
        bold(c,"₩",578,132,26,AMB,Paint.Align.LEFT);
        text(c,won(money(prof)),752,134,44,flash>0?0xfffff3d2:AMB,Paint.Align.RIGHT,numFace());
        if(now<editUntil){priceEditor(c,acc);return;}
        float perMin=cut||Float.isNaN(lph)?0:lph*price(prof)/60;
        bold(c,won(Math.round(perMin))+t("원 / 분"),564,174,18,cut?TEA:AMB,Paint.Align.LEFT);
        float avg=litres>0.002f?km/litres:Float.NaN;
        row(c,t("평균 연비"),economy(avg)+(l100?" L/100km":" km/L"),202);
        row(c,t("사용 연료"),fmt("%.2f",litres)+" L",226);
        row(c,t("거리"),fmt("%.1f",km)+" km",250);
        line(c,564,262,766,262,1,LINE);
        leaf(c,572,284,8);bold(c,t("절약된 연료"),588,290,16,MUT,Paint.Align.LEFT);
        text(c,won(Math.round(savedL*price(prof)/10)*10)+t("원"),766,291,20,GRN,Paint.Align.RIGHT,numFace());
        if(now<toastUntil){round(c,574,184,756,224,10,0xeb070a0b);outline(c,574,184,756,224,10,1,AMB);bold(c,t("이번 주행 초기화"),665,210,16,AMB,Paint.Align.CENTER);}
    }
    /** Opened by a tap on the fare: the fuel price − / + (10 won) and the coin sound switch; closes 6 s after the last tap. */
    private void priceEditor(Canvas c,int acc){
        bold(c,t("연료 단가 (원/L)"),665,170,15,MUT,Paint.Align.CENTER);
        text(c,won(price(prof)),665,202,30,FG,Paint.Align.CENTER,numFace());
        key(c,564,208,660,252,"−",28,KEY_SLATE,acc);key(c,670,208,766,252,"+",28,KEY_SLATE,acc);
        key(c,564,254,766,296,t("동전 소리")+"  "+t(coinSound?"켬":"끔"),16,coinSound?KEY_GREEN:KEY_SLATE,0);
    }
    private void row(Canvas c,String k,String v,float y){bold(c,k,564,y,16,MUT,Paint.Align.LEFT);text(c,v,766,y,19,FG,Paint.Align.RIGHT,numFace());}
    private static String won(int w){return String.format(java.util.Locale.US,"%,d",w);}
    private void leaf(Canvas c,float x,float y,float sz){
        c.save();c.translate(x,y);c.rotate(-34);path.reset();path.moveTo(0,-sz);path.quadTo(sz*.9f,-sz*.2f,0,sz);path.quadTo(-sz*.9f,-sz*.2f,0,-sz);path.close();
        poly(c,GRN);line(c,0,-sz*.7f,0,sz*.8f,1.5f,PANEL);c.restore();
    }

    // ---- taps (VehicleDash routes them while this screen shows) ----
    @Override boolean tap(float x,float y){
        if(now<editUntil&&x>=564&&x<=766&&y>=208&&y<=252){int d=x<=665?-10:10;price=Math.max(PRICE_MIN,Math.min(PRICE_MAX,price(prof)+d));editUntil=now+6000;changed=true;return true;}
        if(now<editUntil&&x>=564&&x<=766&&y>=254&&y<=296){coinSound=!coinSound;if(coinSound)clink=500;editUntil=now+6000;changed=true;return true;}   // a sample clink
        if(x>=564&&x<=766&&y>=94&&y<=146){editUntil=now<editUntil?0:now+6000;return true;}
        if(x>=L_L&&x<=L_R&&y>=TOP&&y<=BOT){coins=!coins;hintDone=true;resync=true;changed=true;return true;}
        if(x>=C_L&&x<=C_R&&y>=124&&y<=266){l100=!l100;changed=true;return true;}
        return false;
    }

    // ---- motion ----
    private float flash;
    private void step(float dt,float kmh,float lph,boolean cut){
        int m=money(prof);if(m>prevMoney){flash=0.35f;pend+=m-prevMoney;prevMoney=m;}else if(m<prevMoney)prevMoney=m;
        if(flash>0)flash-=dt;
        float ws=kmh*1.25f;roadOff=(roadOff+ws*dt)%(TH*1000);
        if(coins){
            // the tailpipe spits what was spent, at most one coin per 1/4 s, so a heavy foot backs money up into 100s and 500s
            emit-=dt;if(emit<=0&&pend>=10&&cn<MAXC){int den=pend>=500?500:pend>=100?100:10;pend-=den;emit=0.25f;
                cx[cn]=PIPE_X;cy[cn]=PIPE_Y+2;cvx[cn]=-60+rnd.nextFloat()*120;cvy[cn]=-30-rnd.nextFloat()*50;csp[cn]=rnd.nextFloat()*6;cb[cn]=0;cgy[cn]=228+rnd.nextFloat()*44;cden[cn]=den;cs[cn]=0;cn++;
                if(pn<puffT.length){puffT[pn]=0;puffX[pn]=PIPE_X;puffY[pn]=PIPE_Y;pn++;}}
            for(int i=cn-1;i>=0;i--){cvy[i]+=520*dt;cy[i]+=cvy[i]*dt;cx[i]+=cvx[i]*dt;csp[i]+=dt*6;cgy[i]+=groundDy(cgy[i],ws)*dt;
                // one clink per contact (the bounce softer: -den), asked just before the coin gets there
                if(cvy[i]>0&&cs[i]<=cb[i]&&cgy[i]-cy[i]<cvy[i]*CLINK_LEAD_S){cs[i]++;if(cb[i]==0)clink=Math.max(clink,cden[i]);else if(clink==0)clink=-cden[i];}
                if(cy[i]>cgy[i]){if(cb[i]<1){cy[i]=cgy[i];cvy[i]=-Math.abs(cvy[i])*.3f;cvx[i]*=.5f;cb[i]++;}
                    else{if(gn==MAXG)shift(0);gx[gn]=cx[i];gy[gn]=cgy[i];gden[gn]=cden[i];gn++;removeCoin(i);}}}
            for(int i=gn-1;i>=0;i--){gy[i]+=groundDy(gy[i],ws)*dt;if(gy[i]>316)shift(i);}
            for(int i=pn-1;i>=0;i--){puffT[i]+=dt;if(puffT[i]>0.8f){pn--;puffT[i]=puffT[pn];puffX[i]=puffX[pn];puffY[i]=puffY[pn];}}
        }else{
            pend=0;
            float rate=Math.min(lph*1.1f+0.35f,22);if(lph>0.01f&&dn<dy.length&&rnd.nextFloat()<rate*dt){dy[dn]=0;dv[dn]=70+rnd.nextFloat()*30;dn++;}
            float lvl=272-110*frac();
            for(int i=dn-1;i>=0;i--){dv[i]+=380*dt;dy[i]+=dv[i]*dt;if(90+dy[i]>lvl){dn--;dy[i]=dy[dn];dv[i]=dv[dn];}}
            int done=(int)(litres/BOTTLE_L);if(prevDone>=0&&done>prevDone&&sn<slideT.length){slideT[sn]=0;slideN[sn]=done-1;sn++;}prevDone=done;
            for(int i=sn-1;i>=0;i--){slideT[i]+=dt/0.7f;if(slideT[i]>=1){sn--;slideT[i]=slideT[sn];slideN[i]=slideN[sn];}}
        }
    }
    private void removeCoin(int i){cn--;cx[i]=cx[cn];cy[i]=cy[cn];cvx[i]=cvx[cn];cvy[i]=cvy[cn];csp[i]=csp[cn];cgy[i]=cgy[cn];cden[i]=cden[cn];cb[i]=cb[cn];cs[i]=cs[cn];}
    private void shift(int i){for(int k=i;k<gn-1;k++){gx[k]=gx[k+1];gy[k]=gy[k+1];gden[k]=gden[k+1];}gn--;}
    private static float frac(){return (litres%BOTTLE_L)/BOTTLE_L;}

    // ---- bottles ----
    private final Path bigPath=bottlePath(60,112),smallPath=bottlePath(17,36);
    private static Path bottlePath(float w,float h){
        Path p=new Path();float nw=w*0.36f,nh=h*0.16f,top=-h;
        p.moveTo(-w/2,-4);p.quadTo(-w/2,0,-w/2+4,0);p.lineTo(w/2-4,0);p.quadTo(w/2,0,w/2,-4);
        p.lineTo(w/2,top+h*0.12f);p.quadTo(w/2,top-2,nw/2,top-nh*0.5f);p.lineTo(nw/2,top-nh);p.lineTo(-nw/2,top-nh);p.lineTo(-nw/2,top-nh*0.5f);
        p.quadTo(-w/2,top-2,-w/2,top+h*0.12f);p.close();return p;
    }
    /** A bottle at (x, base yb) drawn from the 60 x 112 outline scaled by sx, sy: liquid as rectangles (no clipPath on 4.2). */
    private void bottle(Canvas c,Path shape,float x,float yb,float w,float h,float sx,float sy,float fill,int col,boolean capped){
        c.save();c.translate(x,yb);c.scale(sx,sy);p.setStyle(Paint.Style.FILL);p.setColor(0x12b4d2e6);c.drawPath(shape,p);c.restore();
        float W=w*sx,H=h*sy;
        if(fill>0){float body=Math.min(fill,0.88f);fill(c,x-W/2+1.5f,yb-H*body,x+W/2-1.5f,yb-1,(col&0xffffff)|0xd8000000);
            if(fill>0.88f)fill(c,x-W*0.3f,yb-H*fill,x+W*0.3f,yb-H*0.88f,(col&0xffffff)|0xd8000000);
            fill(c,x-W/2+1.5f,yb-H*fill,x+W/2-1.5f,yb-H*fill+2,0x40ffffff);}
        c.save();c.translate(x,yb);c.scale(sx,sy);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.2f/Math.min(sx,sy));p.setColor(0xff6f8796);c.drawPath(shape,p);p.setStyle(Paint.Style.FILL);c.restore();
        if(capped){float nw=W*0.36f,nh=H*0.16f;fill(c,x-nw/2-1,yb-H-nh-4,x+nw/2+1,yb-H-nh,GRN);}
        if(W>30)for(int k=1;k<4;k++)line(c,x-W/2+3,yb-H*k/4,x-W/2+W*0.22f,yb-H*k/4,1,0x47b4d2e6);
    }
    private void bottles(Canvas c,boolean cut,float lph){
        int col=cut?GRN:lph/16>.5f?ORG:AMB;
        fill(c,70,68,96,78,0xff2a3238);fill(c,77,78,89,86,0xff2a3238);
        for(int i=0;i<dn;i++)dot(c,83,90+dy[i],3.5f,col);
        bottle(c,bigPath,83,272,60,112,1,1,frac(),regen()?ORG:cut?AMB:col,false);
        if(cut)leaf(c,83,154+(float)Math.sin(now/400.0)*2,16);
        int done=(int)(litres/BOTTLE_L),shown=Math.min(done,8);
        for(int n=0;n<shown;n++){boolean sl=false;for(int k=0;k<sn;k++)if(slideN[k]==n)sl=true;if(!sl)bottle(c,bigPath,shelfX(n),shelfY(n),60,112,17/60f,36/112f,1,AMB,true);}
        for(int k=0;k<sn;k++){if(slideN[k]>=8)continue;float e=slideT[k]*slideT[k]*(3-2*slideT[k]);int n=slideN[k];
            bottle(c,bigPath,83+(shelfX(n)-83)*e,272+(shelfY(n)-272)*e,60,112,1+(17/60f-1)*e,1+(36/112f-1)*e,1,AMB,true);}
        if(done>8)bold(c,"+"+(done-8),222,164,15,MUT,Paint.Align.RIGHT);
        bold(c,t("페트병 2 L"),222,86,16,MUT,Paint.Align.RIGHT);
        text(c,done+t("개"),222,116,30,FG,Paint.Align.RIGHT,numFace());
        bold(c,"+ "+Math.round(frac()*100)+" %",222,138,16,AMB,Paint.Align.RIGHT);
    }
    private static float shelfX(int n){return 150+(n%4)*21;}
    private static float shelfY(int n){return 272-((n/4)%2)*56;}

    // ---- tailpipe: asphalt phases + the tinted car + coin sprites ----
    private static final float PIPE_X=124,PIPE_Y=176,HY=108,NEAR=192,KD=60;
    private static final int TW=512,TH=64,PH=24,AW=214,AY=132,AH=168;
    private static Bitmap[] asphalt;private static Bitmap car;private static int carFor;private static Bitmap[] flat,spin;
    private static float groundDy(float y,float ws){float d=Math.max(4,y-HY);return ws*d*d/(NEAR*KD);}
    private void tailpipe(Canvas c,boolean cut,float lph){
        if(asphalt==null)asphalt=buildAsphalt();
        fill(c,L_L,TOP,L_R,AY,0xff050606);
        int ph=(int)((roadOff%TH)/TH*PH)%PH;blit(c,asphalt[ph],0,0,AW,AH,L_L,AY,L_L+AW,AY+AH);
        int paint=CarPaint.body()[0];
        if(CAR!=null&&PAINT!=null&&(car==null||carFor!=paint)){car=tint(CAR,PAINT,paint);carFor=paint;}
        if(car!=null)blitSmooth(c,car,0,0,AW,240,L_L,TOP,L_L+AW,BOT);
        for(int i=0;i<pn;i++){float k=puffT[i];p.setColor(((int)(90*(1-k/0.8f))<<24)|0xaab4b9);c.drawCircle(puffX[i]+k*8,puffY[i]+8+k*12,8+k*24,p);}
        if(flat==null)buildCoins();
        for(int i=0;i<gn;i++)sprite(c,flat[di(gden[i])],gx[i],gy[i],(1.3f+(gy[i]-224)/90)/2.2f);
        for(int i=0;i<cn;i++){int a=(int)(csp[i]/(Math.PI*2)*12)%12;sprite(c,spin[di(cden[i])*12+a],cx[i],cy[i],(1+Math.min(.8f,(cy[i]-PIPE_Y)/90))/2.2f);}
        fill(c,L_L,278,L_R,BOT-1,0x99070a0b);
        boolean fc=cut;bold(c,fc?t("퓨얼 컷"):won(Math.round(Float.isNaN(lph)?0:lph*price(prof)/60))+t("원 / 분"),127,295,16,fc?TEA:MUT,Paint.Align.CENTER);
    }
    private static int di(int den){return den==500?2:den==100?1:0;}
    private final Paint smooth=new Paint(Paint.FILTER_BITMAP_FLAG);private final Rect sr=new Rect();private final RectF dr=new RectF();
    private void blitSmooth(Canvas c,Bitmap b,int sx,int sy,int sw,int sh,float l,float t,float r,float bt){sr.set(sx,sy,sx+sw,sy+sh);dr.set(l,t,r,bt);c.drawBitmap(b,sr,dr,smooth);}
    private void sprite(Canvas c,Bitmap b,float x,float y,float sc){
        float w=b.getWidth()*sc,h=b.getHeight()*sc;blitSmooth(c,b,0,0,b.getWidth(),b.getHeight(),x-w/2,y-h*0.4f,x+w/2,y+h*0.6f);
    }
    /** Grain tile (stones, tar spots) seen in perspective: PH frames one TH-unit period apart, the lane left of the wheel,
     *  the fog toward the car and its shadow baked in. One frame is one bitmap per draw. */
    private static Bitmap[] buildAsphalt(){
        java.util.Random r=new java.util.Random(3);int[] tile=new int[TW*TH];
        for(int i=0;i<tile.length;i++){float q=r.nextFloat(),v=38+r.nextFloat()*16;if(q<.035f)v=70+r.nextFloat()*45;else if(q<.06f)v=24+r.nextFloat()*8;
            int g=(int)v;tile[i]=0xff000000|(g<<16)|((g+1)<<8)|(g+3);}
        for(int k=0;k<10;k++){int ex=r.nextInt(TW),ey=r.nextInt(TH),rx=12+r.nextInt(40),ry=4+r.nextInt(10);   // tar patches
            for(int y=-ry;y<=ry;y++)for(int x=-rx;x<=rx;x++){if((x*x)/(float)(rx*rx)+(y*y)/(float)(ry*ry)>1)continue;int i=((ey+y+TH)%TH)*TW+(ex+x+TW)%TW;tile[i]=shade(tile[i],0.82f);}}
        Bitmap[] out=new Bitmap[PH];int[] px=new int[AW*AH];
        for(int f=0;f<PH;f++){float off=f*TH/(float)PH;
            for(int y=0;y<AH;y++){float sy=AY+y,dd=sy-HY,dep=NEAR/dd,sw=Math.min(TW-2,150*dep),u0=TW/2f-sw/2+((int)sy*37%60);
                int row=((int)(dep*KD+off)%TH+TH)%TH;float lw=Math.max(2.5f,16/dep),lx=127-150/dep-lw;boolean dash=((dep*KD+off)%TH+TH)%TH<TH/2f;
                float fog=sy<204?1-(sy-AY)/72f:0,la=.5f/Math.max(1,dep*.6f);
                for(int x=0;x<AW;x++){int col=tile[row*TW+(int)(u0+x*sw/AW)%TW];float X=L_L+x;
                    if(dash&&X>=lx&&X<lx+lw)col=blend(col,0xffcdc8b4,la);
                    float ex=(X-130)/130,ey=(sy-216)/28,sh=1-(ex*ex+ey*ey);if(sh>0)col=shade(col,1-.85f*Math.min(1,sh*1.4f));
                    if(fog>0)col=blend(col,0xff050606,fog);
                    px[y*AW+x]=col;}}
            out[f]=bitmap(AW,AH);out[f].setPixels(px,0,AW,0,0,AW,AH);}
        return out;
    }
    /** The car at half size with its paint mask multiplied by the car colour (built when the colour changes). */
    private static Bitmap tint(Bitmap carSrc,Bitmap paintSrc,int color){
        int w=carSrc.getWidth(),h=carSrc.getHeight();int[] a=new int[w*h],b=new int[w*h],o=new int[AW*240];
        carSrc.getPixels(a,0,w,0,0,w,h);paintSrc.getPixels(b,0,w,0,0,w,h);
        int cr=(color>>16)&255,cg=(color>>8)&255,cbl=color&255;
        for(int y=0;y<240;y++)for(int x=0;x<AW;x++){int A=0,R=0,G=0,B=0;
            for(int k=0;k<4;k++){int sx=Math.min(w-1,x*2+(k&1)),sy=Math.min(h-1,y*2+(k>>1)),i=sy*w+sx,cp=a[i],pp=b[i];
                int al=(cp>>>24),pa=(pp>>>24),gr=(pp>>16)&255;
                int r=(cp>>16)&255,g=(cp>>8)&255,bb=cp&255;
                if(pa>0){float m=pa/255f;r=Math.round(r*(1-m)+gr*cr/255f*m);g=Math.round(g*(1-m)+gr*cg/255f*m);bb=Math.round(bb*(1-m)+gr*cbl/255f*m);}
                A+=al;R+=r*al;G+=g*al;B+=bb*al;}
            o[y*AW+x]=A==0?0:((A/4)<<24)|((R/A)<<16)|((G/A)<<8)|(B/A);}
        Bitmap out=bitmap(AW,240);out.setPixels(o,0,AW,0,0,AW,240);return out;
    }
    private static final int[][] COIN={{8,0xffe0a061,0xff9c5e27,0xff6b3c14,0},{10,0xffc9ced2,0xff7f878c,0xff555c61,1},{11,0xffe3e6e8,0xff8f989e,0xff5f676c,1}};   // 10, 100, 500: radius, face, rim, edge, milled
    /** Coin sprites at 2.2x: one lying flat per coin, twelve turns per coin in the air, each a short cylinder. */
    private void buildCoins(){
        flat=new Bitmap[3];spin=new Bitmap[36];
        for(int d=0;d<3;d++){int[] k=COIN[d];float r=k[0]*2.2f,th=Math.max(1.5f,r*.22f);
            int w=(int)(2*r+4),h=(int)(r*.84f+th+4);Bitmap b=bitmap(w,h);Canvas bc=new Canvas(b);float x=w/2f,y=r*.42f+2;
            ellipse(bc,x,y+th,r,r*.42f,k[3]);fillOn(bc,x-r,y,x+r,y+th,k[2]);
            if(k[4]==1)for(float i=x-r+2;i<x+r;i+=Math.max(1.6f,r/6))fillOn(bc,i,y+r*.08f,i+.8f,y+th,0x47000000);
            ellipse(bc,x,y,r,r*.42f,k[2]);ellipse(bc,x,y,r-3.5f,r*.42f-1.5f,k[1]);flat[d]=b;
            for(int a=0;a<12;a++){double ang=a*Math.PI*2/12;float cs=(float)Math.cos(ang),sn=(float)Math.sin(ang),rx=Math.max(.6f,r*Math.abs(cs)),off=th*sn/2;
                int sw=(int)(2*r+th+4),shh=(int)(2*r+4);Bitmap sb=bitmap(sw,shh);Canvas sc=new Canvas(sb);float mx=sw/2f,my=shh/2f;
                if(Math.abs(off)>.2f){ellipse(sc,mx+off,my,rx,r,k[3]);fillOn(sc,Math.min(mx-off,mx+off),my-r,Math.max(mx-off,mx+off),my+r,k[2]);}
                ellipse(sc,mx-off,my,rx,r,k[2]);ellipse(sc,mx-off,my,Math.max(.3f,rx-3.5f*Math.abs(cs)),r-3.5f,k[1]);spin[d*12+a]=sb;}}
        // sprites are drawn with their centre 40 % down (flat) like the browser draft; the spinning ones too
    }
    private final Paint op=new Paint(1);
    private void ellipse(Canvas c,float x,float y,float rx,float ry,int col){op.setColor(col);c.save();c.translate(x,y);c.scale(rx,ry);c.drawCircle(0,0,1,op);c.restore();}
    private void fillOn(Canvas c,float l,float t,float r,float b,int col){op.setColor(col);c.drawRect(l,t,r,b,op);}
    private static int shade(int c,float k){return (c&0xff000000)|((int)(((c>>16)&255)*k)<<16)|((int)(((c>>8)&255)*k)<<8)|(int)((c&255)*k);}
    private static int blend(int a,int b,float t){return mix(b,a,t);}

    // ---- graph ----
    private LinearGradient barShade,legendShade;private final Paint gp=new Paint(1);
    private void graph(Canvas c){
        round(c,L_L,G_T,R_R,G_B,10,PANEL);outline(c,L_L,G_T,R_R,G_B,10,1,LINE);
        bold(c,t("최근 2분"),34,G_T+24,16,MUT,Paint.Align.LEFT);
        float x0=34,bw=(760-28)/(float)BARS,yb=G_B-12,ymax=G_B-G_T-46;
        int[] rgb={0xff2fd36b,0xffffd23f,0xffff3b30};float[] pos={0,.45f,1};
        if(barShade==null){barShade=new LinearGradient(0,yb,0,yb-ymax,rgb,pos,Shader.TileMode.CLAMP);legendShade=new LinearGradient(648,0,724,0,rgb,pos,Shader.TileMode.CLAMP);}
        bold(c,t("적음"),640,G_T+24,14,MUT,Paint.Align.RIGHT);gp.setShader(legendShade);r.set(648,G_T+13,724,G_T+25);c.drawRoundRect(r,3,3,gp);gp.setShader(null);
        bold(c,t("많음"),730,G_T+24,14,MUT,Paint.Align.LEFT);
        gp.setShader(barShade);
        for(int i=0;i<barN;i++){float v=bar[(barHead-barN+i+BARS)%BARS];float x=x0+(BARS-barN+i)*bw,w=Math.max(1,bw-1);
            if(v>=0){float h=Math.max(1,v/LMAX*ymax);c.drawRect(x,yb-h,x+w,yb,gp);}}
        gp.setShader(null);
        for(int i=0;i<barN;i++){int k=(barHead-barN+i+BARS)%BARS;float v=bar[k],x=x0+(BARS-barN+i)*bw,w=Math.max(1,bw-1);
            if(v==-1)fill(c,x,yb-12,x+w,yb,IDLE_BAR);else if(v==-2)dot(c,x+w/2,yb-3,2.5f,TEA);
            if(barRegen[k])fill(c,x,yb+3,x+w,yb+8,ORG);}
        float avgL=km>0.05f?100*litres/km:Float.NaN;
        if(!Float.isNaN(avgL)){float ay=yb-Math.min(LMAX,avgL)/LMAX*ymax;for(float x=x0;x<x0+bw*BARS;x+=9)fill(c,x,ay,Math.min(x+5,x0+bw*BARS),ay+1,0xff5a7480);
            String al=t("평균");round(c,x0,ay-22,x0+width(al,14,Typeface.DEFAULT_BOLD)+10,ay-2,4,0xd90f1417);bold(c,al,x0+5,ay-7,14,MUT,Paint.Align.LEFT);}
    }
}
