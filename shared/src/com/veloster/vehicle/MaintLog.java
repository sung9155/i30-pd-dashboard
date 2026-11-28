package com.veloster.vehicle;

/**
 * 정비: what the app saw of the car's use, since the last oil change and in total — engine, idle and high-load time,
 * distance (and how much of it at speed), starts, short and cold starts, stops, clutch overheats, DPF regenerations, low
 * tyres, battery voltages. One static tracker: the dashboard and the floating window both feed it the same samples, and
 * a call within 200 ms of the last is ignored, so a drive with both up counts once. The open start is stored with a
 * wall-clock time, so an app restart within 3 minutes continues the same start; while the app runs its own clock decides
 * (the head unit's wall clock can be set mid-drive). Saved under one key, once a minute and only when something changed.
 * Only while the app runs: the screen says so. No Android: host-tested.
 */
public final class MaintLog {
    private MaintLog(){}

    public static final class Usage {
        public double engineS,idleS,highLoadS,movingS,movingKm,highSpeedKm,seenKm;
        public int starts,shortStarts,coldStarts,stops,clutchHot,dpfRegens,tpmsLow;
        /** Finished starts the app saw from the engine start: the short-trip share counts only these. */
        public int judgedStarts;
        public float clutchMax=Float.NaN,odoAtOil=Float.NaN,restV=Float.NaN,startV=Float.NaN;
        /** The odometer where this counting began (odoAtOil is where the oil was changed: earlier, when typed in later). */
        public float odoAtCount=Float.NaN;
        public float[] restHistory=new float[0];
        String encode(){StringBuilder b=new StringBuilder();
            double[] d={engineS,idleS,highLoadS,movingS,movingKm,highSpeedKm,seenKm,starts,shortStarts,coldStarts,stops,clutchHot,dpfRegens,tpmsLow,clutchMax,odoAtOil,restV,startV,judgedStarts,odoAtCount};
            for(int i=0;i<d.length;i++)b.append(i==0?"":",").append(d[i]);
            b.append(';');for(int i=0;i<restHistory.length;i++)b.append(i==0?"":",").append(restHistory[i]);return b.toString();}
        static Usage decode(String s){Usage u=new Usage();if(s==null||s.length()==0)return u;
            try{String[] p=s.split(";",-1),f=p[0].split(",");double[] d=new double[f.length];for(int i=0;i<f.length;i++)d[i]=Double.parseDouble(f[i]);
                if(d.length>=18){u.engineS=d[0];u.idleS=d[1];u.highLoadS=d[2];u.movingS=d[3];u.movingKm=d[4];u.highSpeedKm=d[5];u.seenKm=d[6];
                    u.starts=(int)d[7];u.shortStarts=(int)d[8];u.coldStarts=(int)d[9];u.stops=(int)d[10];u.clutchHot=(int)d[11];u.dpfRegens=(int)d[12];u.tpmsLow=(int)d[13];
                    u.clutchMax=(float)d[14];u.odoAtOil=(float)d[15];u.restV=(float)d[16];u.startV=(float)d[17];}
                if(d.length>=19)u.judgedStarts=(int)d[18];
                u.odoAtCount=d.length>=20?(float)d[19]:u.odoAtOil;
                if(p.length>1&&p[1].length()>0){String[] h=p[1].split(",");u.restHistory=new float[h.length];for(int i=0;i<h.length;i++)u.restHistory[i]=Float.parseFloat(h[i]);}
            }catch(RuntimeException e){return new Usage();}
            return u;}
    }

    static final float START_RPM=400,COLD_C=40,HOT_CLUTCH_C=200,LOW_TIRE_PSI=30,HIGH_SPEED=140,HIGH_RPM=4500,HIGH_LOAD=70;
    static final long NEW_START_MS=180000,DUP_MS=200,GAP_MS=5000,REST_MS=30000,START_V_MS=20000,SAVE_MS=60000;
    static final int HISTORY=10;
    static final long SEEN_RUNTIME_S=120,FRESH_BOOT_MS=180000;   // a start counts as seen from the beginning: the engine ran < 2 min, or the head unit came up < 3 min ago

    private static MaintItems.Store store;
    private static Usage since=new Usage(),total=new Usage();
    private static long lastTick=Long.MIN_VALUE,lastSave=Long.MIN_VALUE,restSince=-1,startedAt=-1;
    private static long lastRpmWall=0;   // wall-clock ms the engine was last seen running (persisted)
    private static long lastRpmNow=Long.MIN_VALUE;   // the same on the frame clock, this run of the app only
    private static String saved;   // what the store holds
    private static boolean restFresh;   // a rest voltage read since the last start
    private static double startMark=Double.NaN;   // total.movingKm when the open start began (persisted)
    private static float startAmbient=Float.NaN,startOdo=Float.NaN,lastOdo=Float.NaN;
    private static boolean startJudged;
    private static boolean coolantSeen,moved,startVSeen;
    private static float prevOdd=Float.NaN,prevEven=Float.NaN,prevRegen=Float.NaN;
    private static final boolean[] tireLow=new boolean[4];

    public static synchronized void attach(MaintItems.Store s){
        store=s;saved=s==null?"":s.get("maint_log");String[] all=saved.split("\\|",-1);
        boolean one=all.length==3;   // else the three keys of the first test build (2026-11-28)
        since=s==null?new Usage():Usage.decode(one?all[0]:s.get("maint_since_oil"));total=s==null?new Usage():Usage.decode(one?all[1]:s.get("maint_total"));
        lastTick=Long.MIN_VALUE;lastSave=Long.MIN_VALUE;lastRpmNow=Long.MIN_VALUE;restFresh=false;restSince=-1;startedAt=-1;coolantSeen=true;moved=false;startVSeen=true;
        prevOdd=prevEven=prevRegen=Float.NaN;java.util.Arrays.fill(tireLow,false);
        String[] o=s==null?new String[0]:(one?all[2]:s.get("maint_open")).split(",");
        lastRpmWall=0;startMark=Double.NaN;startAmbient=Float.NaN;startOdo=lastOdo=Float.NaN;startJudged=false;
        try{if(o.length>=3){lastRpmWall=Long.parseLong(o[0]);startMark=Double.parseDouble(o[1]);startAmbient=Float.parseFloat(o[2]);}
            if(o.length>=6){startJudged="1".equals(o[3]);startOdo=Float.parseFloat(o[4]);lastOdo=Float.parseFloat(o[5]);}}catch(RuntimeException e){lastRpmWall=0;}
    }
    public static synchronized Usage sinceOil(){return since;}
    public static synchronized Usage total(){return total;}
    /** The oil was changed at odoKm. What was counted wholly after it stays (a past change typed in, or its km corrected:
     *  the counting began at or after odoKm); a change later than where the counting began starts over. */
    public static synchronized void oilChanged(float odoKm){
        if(!Float.isNaN(odoKm)&&!Float.isNaN(since.odoAtCount)&&odoKm<=since.odoAtCount)since.odoAtOil=odoKm;
        else{since=new Usage();since.odoAtOil=odoKm;}
        flush();}
    public static synchronized void flush(){
        if(store==null)return;
        String v=since.encode()+"|"+total.encode()+"|"+lastRpmWall+","+startMark+","+startAmbient+","+(startJudged?1:0)+","+startOdo+","+lastOdo;
        if(v.equals(saved))return;   // parked: nothing changed, nothing written
        saved=v;store.put("maint_log",v);}

    private static boolean ok(Telemetry.Sample[] s,int i){return s!=null&&i>=0&&i<s.length&&s[i]!=null&&s[i].valid&&!Float.isNaN(s[i].value);}
    private static float val(Telemetry.Sample[] s,int i){return ok(s,i)?s[i].value:Float.NaN;}

    /** now: elapsedRealtime (frame clock); wallMs: System.currentTimeMillis (survives a restart). */
    public static synchronized void tick(Telemetry.Sample[] s,long now,long wallMs){
        if(lastTick!=Long.MIN_VALUE&&now>=lastTick&&now-lastTick<DUP_MS)return;
        double dt=lastTick==Long.MIN_VALUE||now<lastTick||now-lastTick>GAP_MS?0:(now-lastTick)/1000.0;
        lastTick=now;
        float rpm=val(s,0),kmh=val(s,7);boolean eng=!Float.isNaN(rpm)&&rpm>START_RPM;
        // A new start: the engine seen running again after 3 min without it (engine off, or the app not seeing it; a restart
        // within that is the same drive). This run of the app measures the gap on its own clock (the wall clock gets set
        // mid-drive); only the first sight after a restart uses the saved wall time, and one that went back is a gap.
        boolean gap=lastRpmNow!=Long.MIN_VALUE&&now>=lastRpmNow?now-lastRpmNow>=NEW_START_MS:lastRpmWall==0||wallMs-lastRpmWall>=NEW_START_MS||wallMs<lastRpmWall;
        if(eng&&(gap||Double.isNaN(startMark)))newStart(s,now);
        if(eng){lastRpmWall=wallMs;lastRpmNow=now;}
        if(eng){since.engineS+=dt;total.engineS+=dt;if(!Float.isNaN(kmh)&&kmh<1)idleAdd(dt);
            float load=val(s,Telemetry.ENGINE_LOAD);if(rpm>=HIGH_RPM||!Float.isNaN(load)&&load>=HIGH_LOAD){since.highLoadS+=dt;total.highLoadS+=dt;}}
        if(!Float.isNaN(kmh)&&kmh>=1){double km=kmh*dt/3600;
            for(Usage u:new Usage[]{since,total}){u.movingS+=dt;u.movingKm+=km;u.seenKm+=km;if(kmh>=HIGH_SPEED)u.highSpeedKm+=km;}}
        if(!Float.isNaN(kmh)){if(kmh>=10)moved=true;else if(moved&&kmh<3){moved=false;since.stops++;total.stops++;}}
        float odo=val(s,Telemetry.ODOMETER);if(odo>0&&Float.isNaN(since.odoAtOil))since.odoAtOil=odo;   // no oil record yet: count from here
        if(odo>0&&Float.isNaN(since.odoAtCount))since.odoAtCount=odo;
        if(odo>0){lastOdo=odo;if(Float.isNaN(startOdo)&&!Double.isNaN(startMark))startOdo=odo;}
        float cool=val(s,2);if(eng&&!coolantSeen&&!Float.isNaN(cool)){coolantSeen=true;if(cool<COLD_C){since.coldStarts++;total.coldStarts++;}}
        float amb=val(s,Telemetry.AMBIENT);if(eng&&Float.isNaN(startAmbient)&&!Float.isNaN(amb))startAmbient=amb;
        prevOdd=clutch(val(s,Telemetry.CLUTCH_ODD),prevOdd);prevEven=clutch(val(s,Telemetry.CLUTCH_EVEN),prevEven);
        float regen=val(s,Telemetry.DPF_REGEN);if(!Float.isNaN(regen)){if(regen>0&&!(prevRegen>0)&&!Float.isNaN(prevRegen)){since.dpfRegens++;total.dpfRegens++;}prevRegen=regen;}
        for(int i=0;i<4;i++){float p=val(s,Telemetry.TPMS_FL+i);if(Float.isNaN(p))continue;boolean low=p<LOW_TIRE_PSI;
            if(low&&!tireLow[i]){since.tpmsLow++;total.tpmsLow++;}tireLow[i]=low;}
        float volt=val(s,Telemetry.BAT_VOLTAGE);
        if(!Float.isNaN(rpm)&&rpm<=0){if(restSince<0)restSince=now;if(!Float.isNaN(volt)&&now-restSince>=REST_MS){since.restV=volt;total.restV=volt;restFresh=true;}}
        else if(eng&&restSince>=0){restSince=-1;}
        if(eng&&!startVSeen&&!Float.isNaN(volt)&&now-startedAt<=START_V_MS){startVSeen=true;since.startV=volt;total.startV=volt;}
        if(lastSave==Long.MIN_VALUE)lastSave=now;
        else if(now-lastSave>=SAVE_MS||now<lastSave){lastSave=now;flush();}
    }
    private static void idleAdd(double dt){since.idleS+=dt;total.idleS+=dt;}
    private static float clutch(float c,float prev){if(Float.isNaN(c))return prev;
        if(!Float.isNaN(prev)&&prev<HOT_CLUTCH_C&&c>=HOT_CLUTCH_C){since.clutchHot++;total.clutchHot++;}
        if(Float.isNaN(since.clutchMax)||c>since.clutchMax)since.clutchMax=c;if(Float.isNaN(total.clutchMax)||c>total.clutchMax)total.clutchMax=c;return c;}
    private static void newStart(Telemetry.Sample[] s,long now){
        if(!Double.isNaN(startMark)&&startJudged){   // the drive's length: by the odometer when known (the app may have missed its middle)
            double d=!Float.isNaN(startOdo)&&!Float.isNaN(lastOdo)&&lastOdo>=startOdo?lastOdo-startOdo:total.movingKm-startMark;
            float lim=!Float.isNaN(startAmbient)&&startAmbient<0?16:8;
            since.judgedStarts++;total.judgedStarts++;
            if(d<lim){since.shortStarts++;total.shortStarts++;}}
        float run=val(s,Telemetry.ENGINE_RUNTIME);
        startJudged=!Float.isNaN(run)&&run<SEEN_RUNTIME_S||now<FRESH_BOOT_MS;
        float odo=val(s,Telemetry.ODOMETER);startOdo=lastOdo=odo>0?odo:Float.NaN;
        if(restFresh){restFresh=false;if(!Float.isNaN(since.restV))pushRest(since);if(!Float.isNaN(total.restV))pushRest(total);}   // each reading once
        since.starts++;total.starts++;startMark=total.movingKm;startAmbient=Float.NaN;coolantSeen=false;startVSeen=false;startedAt=now;}
    private static void pushRest(Usage u){float[] h=u.restHistory;int n=Math.min(HISTORY,h.length+1);float[] o=new float[n];
        o[n-1]=u.restV;System.arraycopy(h,h.length-(n-1),o,0,n-1);u.restHistory=o;}
}
