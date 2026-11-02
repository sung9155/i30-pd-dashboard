package com.veloster.vehicle;

/** Packet observations only. No CAN writes, preferences, simulation or Android dependency. */
public final class Telemetry {
    public static final int STEERING=13,AMBIENT=14,POWER=15,TORQUE=16,BOOST=17;
    public static final int TPMS_FL=18,TPMS_FR=19,TPMS_RL=20,TPMS_RR=21;
    public static final int TCS=22,ESC=23,ABS=24,BAT_SOC=25,BAT_VOLTAGE=26,STEERING_SPEED=27,ELSD=28;
    public static final int INTAKE=29,GEAR_SELECTOR=30,ODOMETER=31,ENGINE_LOAD=32,YAW=33,DRIVE_MODE=34,PARK_BRAKE=35;
    public static final int THROTTLE_POS=36,FUEL_RATE=37,RANGE=38,AVG_FUEL=39,TORQUE_CORR=40,TORQUE_FRICTION=41;
    public static final int SPARK=42,BRAKE_SWITCH=43,STEER_TORQUE=44,DOOR_DRIVER=45,WIPER=46,DOOR_PASSENGER=47,TRUNK=48,HOOD=49,TURN_LEFT=50,TURN_RIGHT=51,SHIFT_HINT=52,HEAD_LAMP=53,TAIL_LAMP=54,HIGH_BEAM=55;
    public static final String[] LABELS = {"엔진 회전수", "유온 (냉각수 대체)", "냉각수 온도", "앞 왼쪽 휠", "앞 오른쪽 휠", "뒤 왼쪽 휠", "뒤 오른쪽 휠", "차량 속도", "EMS 기어", "가속 페달", "브레이크 압력", "횡가속", "종가속", "조향각", "외기 온도", "출력 (계산)", "토크 (계산)", "부스트 (계산)", "앞 왼쪽 공기압", "앞 오른쪽 공기압", "뒤 왼쪽 공기압", "뒤 오른쪽 공기압", "TCS", "ESC", "ABS", "배터리 SOC", "배터리 전압", "조향 속도", "e-LSD 수치", "흡기 온도", "변속 레버", "주행거리", "엔진 부하", "요레이트", "주행 모드", "주차 브레이크", "스로틀 개도", "순간 연료소모", "주행가능거리", "평균 연비", "보정 토크", "마찰 토크", "점화 시기", "브레이크 스위치", "조향 토크", "운전석 문", "와이퍼", "조수석 문", "트렁크", "후드", "좌 방향지시등", "우 방향지시등", "변속 권장", "전조등", "미등", "상향등"};
    public static final String[] UNITS = {"rpm", "°C", "°C", "km/h", "km/h", "km/h", "km/h", "km/h", "", "%", "bar", "G", "G", "°", "°C", "PS", "N·m", "bar", "psi", "psi", "psi", "psi", "", "", "", "%", "V", "°/s", "", "°C", "", "km", "%", "°/s", "", "", "%", "L/h", "km", "km/L", "%", "%", "°", "", "Nm", "", "", "", "", "", "", "", "", "", "", ""};
    public static final long STALE_MS = 60000; // UI freshness threshold, not an OEM timeout.
    public static final long HISTORY_MS=300000,HISTORY_STEP_MS=250;
    private final History[] history=new History[LABELS.length];
    /** Display orientation follows the driver's reported left/right convention. Raw SAS stays unchanged. */
    public static float displayValue(int signal,float raw){return signal==STEERING?(raw==0?0:-raw):raw;}
    public static final class Series {
        public final long[] times;
        public final float[] values;
        public final boolean[] valid,breakBefore;
        Series(int n){times=new long[n];values=new float[n];valid=new boolean[n];breakBefore=new boolean[n];}
        public int size(){return times.length;}
        /** This series read at the given times: the nearest sample within tolMs, else invalid (times ascending). */
        public Series at(long[] at,long tolMs){
            Series r=new Series(at.length);int j=0;
            for(int i=0;i<at.length;i++){
                while(j+1<times.length&&Math.abs(times[j+1]-at[i])<=Math.abs(times[j]-at[i]))j++;
                r.times[i]=at[i];
                if(j<times.length&&Math.abs(times[j]-at[i])<=tolMs){r.values[i]=values[j];r.valid[i]=valid[j];r.breakBefore[i]=breakBefore[j];}
            }
            return r;
        }
    }
    private static final class History {
        private static final int CAPACITY=(int)(HISTORY_MS/HISTORY_STEP_MS)+1;
        final long[] times=new long[CAPACITY];
        final float[] values=new float[CAPACITY];
        final boolean[] valid=new boolean[CAPACITY],breakBefore=new boolean[CAPACITY];
        int start,count;
        void add(float value,boolean ok,long now){
            ok=ok&&!Float.isNaN(value)&&!Float.isInfinite(value);
            int last=(start+count-1+CAPACITY)%CAPACITY;
            if(count>0&&now<times[last])return;
            if(count>0&&now/HISTORY_STEP_MS==times[last]/HISTORY_STEP_MS){
                breakBefore[last]|=!ok||!valid[last];times[last]=now;values[last]=value;valid[last]=ok;return;
            }
            boolean gap=count>0&&!valid[last];
            int at=(start+count)%CAPACITY;
            if(count==CAPACITY){start=(start+1)%CAPACITY;}else count++;
            times[at]=now;values[at]=value;valid[at]=ok;breakBefore[at]=gap||!ok;
            while(count>0&&times[start]<now-HISTORY_MS){start=(start+1)%CAPACITY;count--;}
        }
        Series snapshot(long now,long window){
            int n=0;long left=now-window;
            for(int i=0;i<count;i++){int at=(start+i)%CAPACITY;if(times[at]>=left&&times[at]<=now)n++;}
            Series result=new Series(n);n=0;
            for(int i=0;i<count;i++){int at=(start+i)%CAPACITY;if(times[at]<left||times[at]>now)continue;
                result.times[n]=times[at];result.values[n]=values[at];result.valid[n]=valid[at];result.breakBefore[n]=breakBefore[at];n++;
            }
            return result;
        }
    }
    public synchronized Series history(int signal,long now,long window){
        if(signal<0||signal>=LABELS.length||window<=0||window>HISTORY_MS)throw new IllegalArgumentException("history range");
        return history[signal]==null?new Series(0):history[signal].snapshot(now,window);
    }
    private int speedUnit=-1, rearView, rawSpeed;
    /** Set once hscan CLU11 speed arrives (i30 PD); OEM speed events then no longer touch signal 7. */
    private boolean hscanSpeed;
    /** Latest EMS14 barometric pressure (hPa) from slot 0x0a; NaN until received. Boost is relative to it. */
    private float baroHpa=Float.NaN;
    private final long[] counts=new long[LABELS.length];
    private final float[] values = new float[LABELS.length];
    private final long[] times = new long[LABELS.length];
    private final boolean[] valid = new boolean[LABELS.length], changed = new boolean[LABELS.length], seen = new boolean[LABELS.length];
    private long epoch,ptVersion,gfVersion,ptTime,gfTime,stdTime,lastComputedPt=-1,lastComputedGf=-1;
    private boolean ptValid,gfValid,stdSeen;
    private float inputRpm,inputTqi,inputAcor,inputStd,inputBoost,inputAmbient,friction;
    private float observedPreviousRpm=-1;
    private boolean observedTorqueValid;
    private final ThreadLocal<Calculation> calculation=new ThreadLocal<Calculation>();
    private static final class Calculation {
        long epoch,pt,gf,time;
        float power,torque,boost;
        boolean valid;
    }
    public static final class Sample {
        public final float value;
        public final long ageMs;
        public final boolean valid, changed, seen, stale;
        Sample(float v, boolean ok, boolean diff, boolean received, boolean old,long age) {
            ageMs=age;value=v; valid=ok; changed=diff; seen=received; stale=old;
        }
    }
    private float boostGood=Float.NaN,boostLast=Float.NaN;private long boostGoodAt;
    /**
     * Boost (bar) minus samples this turbo cannot make: outside -1..+2.5 bar, or a lone rise of more than 0.8 bar over
     * the last good sample under 1 s ago (~0.17 s per sample; a full-throttle spool is about 0.35 bar per sample, gentle
     * drive logs 0.28 at most). Falls are never filtered: the recirculation valve dumps boost that fast on a lift. Two
     * samples in a row within 0.15 bar take a new level. A dropped sample never reaches the display, graphs or MAX;
     * the previous value stays. In-car: 3.62 bar MAX after a throttle lift that an aftermarket gauge did not show.
     * ponytail: 0.8 is from physics, not a sport-drive log; retune from one if real spools get held back.
     */
    private void boost(float v,boolean ok,long now){
        boolean confirmed=Math.abs(v-boostLast)<=0.15f;
        boostLast=ok?v:Float.NaN;
        if(ok&&(v<-1f||v>2.5f||(now-boostGoodAt<1000&&v-boostGood>0.8f&&!confirmed)))return;
        if(ok){boostGood=v;boostGoodAt=now;}
        put(BOOST,v,ok,now);
    }
    private void put(int i, float v, boolean ok, long now) {
        changed[i] = ok && valid[i] && seen[i] && now-times[i] <= STALE_MS && values[i] != v;
        counts[i]++; values[i]=v; valid[i]=ok; seen[i]=true; times[i]=now;
        if(history[i]==null)history[i]=new History();
        history[i].add(v,ok,now);
        if(ok){ if(i==SPARK)sparkSeen=true; else if(i==GEAR_SELECTOR&&v>0)selectorSeen=true; else if(i==0&&v>rpmMax)rpmMax=v; }
    }
    // Auto-detected powertrain variant: ignition timing -> petrol; a live shift lever -> DCT (its checksum byte cycles
    // every frame even parked in P, or it leaves P); a 6MT has no lever, so its LVR12 slot stays frozen at 0 = P, and
    // only a manual gets a gear from the EMS. A diesel (no spark, rpm capped low) or a manual (no lever, yet driving)
    // is inferred only once there is evidence, defaulting to the verified PETROL 7DCT otherwise. Display-only; the
    // decode is identical for every variant.
    private boolean sparkSeen, selectorSeen, leverAlive, emsGearSeen; private float rpmMax;
    private int leverCheck=-1, leverFrozen;
    // 6MT gear from rpm / speed. Ratios: Hyundai AU i30 PD spec sheet, 1.6 T-GDi 6MT (final drive 4.467, single);
    // Korean sources (catalog, Danawa) publish none. K = rpm per (km/h x overall ratio), measured on our 7DCT logs
    // with the same 225/40R18 tyres and CLU11 speed: 8.06-8.17 in 2nd/4th/5th. Adjacent gears are >=23 % apart, so a
    // +-9 % window cannot pick the wrong one; outside every window (clutch in, slipping, neutral) the gear is unknown.
    // ponytail: fixed table for the 1.6 T-GDi 6MT; another tyre size or gearbox would need its own K/ratios.
    private static final float[] MT_RATIO={3.308f,1.962f,1.294f,0.976f,0.778f,0.633f};
    private static final float MT_K=8.1f*4.467f;
    private static final long MT_HOLD_MS=300;
    // In gear the rpm/speed ratio is fixed; in neutral a rev-match blip moves the rpm while the speed stays put. So a new
    // gear also needs the ratio steady (max/min within 8 %) for the whole hold, or a slow blip's sweep through another
    // gear's window would show that gear. 8 %: on our 7DCT logs, 300 ms in-gear windows away from shifts exceed it 2-3 %
    // of the time above 20 km/h (4 % failed 10 %: speed lag under load); a blip moves the rpm tens of % in 300 ms.
    private static final float MT_STEADY=1.08f;
    private static final int MT_N=-1;   // mtShown/mtCand value for neutral; published as a valid gear 0
    private static final int MT_R=-2;   // reverse; published as a valid gear 14 (the TCU's R code)
    private boolean mtReverse;
    private float idleTarget=Float.NaN,idleKmh;   // EMS_H12 idle target rpm; speed when the engine settled at it
    private int mtCand,mtShown; private long mtCandAt; private float mtRatioMin,mtRatioMax;
    // EMS12 CLU_ACK (byte3 bit7) = drivetrain open: on our 7DCT 1 in P/N, 0 in D/R and driving (34 logs). Its receivers
    // are EPB/ESC (drive-away, hill-start); on the 6MT it follows the clutch pedal (owner-verified 2026-11-01, v1.5.4). It
    // counts only once it has toggled (a manual needs the clutch in to crank); a frozen bit keeps the plain match.
    private int clutchLast=-1; private boolean clutchToggled;
    public static int mtGear(float rpm,float kmh){
        if(kmh<8||rpm<600)return 0;
        float r=rpm/kmh/MT_K;
        for(int i=0;i<MT_RATIO.length;i++)if(Math.abs(Math.log(r/MT_RATIO[i]))<0.09)return i+1;
        return 0;
    }
    public VehicleProfile profile() {
        VehicleProfile.Fuel fuel = sparkSeen ? VehicleProfile.Fuel.PETROL
            : (rpmMax>2000 && rpmMax<5200 ? VehicleProfile.Fuel.DIESEL : VehicleProfile.Fuel.PETROL);
        // ponytail: 30 frozen lever frames is ~5 s at the 10/29 slot rate; never seen from a 6MT yet
        VehicleProfile.Trans trans = leverAlive || selectorSeen ? VehicleProfile.Trans.DCT
            : (emsGearSeen || leverFrozen>=30 || rpmMax>2000 ? VehicleProfile.Trans.MT : VehicleProfile.Trans.DCT);
        return VehicleProfile.of(fuel, trans);
    }
    public synchronized void clear() {
        speedUnit=-1; rearView=0; rawSpeed=0; hscanSpeed=false;
        epoch++;ptValid=false;gfValid=false;stdSeen=false;lastComputedPt=-1;lastComputedGf=-1;
        calculation.remove();
        observedPreviousRpm=-1;observedTorqueValid=false;
        sparkSeen=false;selectorSeen=false;leverAlive=false;emsGearSeen=false;rpmMax=0;
        leverCheck=-1;leverFrozen=0;mtCand=0;mtShown=0;mtCandAt=0;mtRatioMin=mtRatioMax=0;mtReverse=false;idleTarget=Float.NaN;idleKmh=0;clutchLast=-1;clutchToggled=false;
        for(int i=0;i<LABELS.length;i++) { counts[i]=0; valid[i]=false; changed[i]=false; seen[i]=false; history[i]=null; }
    }
    public synchronized Sample sample(int i, long now) {
        boolean stale=seen[i] && now-times[i]>=STALE_MS;
        return new Sample(values[i], valid[i] && !stale, changed[i] && !stale, seen[i], stale,seen[i]?Math.max(0,now-times[i]):Long.MAX_VALUE);
    }
    public synchronized Sample[] snapshot(long now) {
        Sample[] result=new Sample[LABELS.length];
        for(int i=0;i<result.length;i++)result[i]=sample(i,now);
        return result;
    }
    /** Gear number text; a valid 0 / 14 is only ever published for 6MT neutral / reverse (DCT and JSN publish them as invalid). */
    public static String gearLabel(int raw) { return raw>=1&&raw<=7?String.valueOf(raw):raw==0?"N":raw==14?"R":"—"; }
    /** GEAR_SELECTOR value: 0=P 1=R 2=N 3=D 4=M (manual gate, i30 PD hscan 0x1d). */
    public static String selectorLabel(int code) { return code==0?"P":code==1?"R":code==2?"N":code==3?"D":code==4?"M":"—"; }
    /** Selector code to trust, or -1. A 6MT has no lever: its LVR12 slot reads 0 (P) whatever gear is in, so the
     * selector is ignored there, and P next to a forward gear number is ignored too (on the DCT 0x1f reads 0 in P). */
    public static int selector(float sel,float gear,VehicleProfile prof) {
        int code=Float.isNaN(sel)?-1:(int)sel;
        return prof.trans==VehicleProfile.Trans.MT || code==0&&!Float.isNaN(gear)&&gear>=1&&gear<=7 ? -1 : code;
    }
    /** Gear box text: P / R / N letters, the gear number in D, M + gear in the manual gate, the bare number on a 6MT. */
    public static String gearText(float gear,float sel,VehicleProfile prof) {
        boolean known=!Float.isNaN(gear)&&gear>=1&&gear<=prof.topGear;String g=known?gearLabel((int)gear):"";
        int code=selector(sel,gear,prof);
        return code==0?"P":code==1?"R":code==2?"N":code==3?(known?g:"D"):code==4?"M"+g:known?g:gear==0?"N":gear==14?"R":"—";   // valid 0 / 14 = 6MT neutral / reverse
    }
    /** SHIFT_HINT text as the cluster shows it: "▲4" / "▼2", "" when there is no recommendation. */
    public static String shiftHint(float hint) { return Float.isNaN(hint)||hint==0?"":(hint>0?"▲":"▼")+(int)Math.abs(hint); }
    /** DRIVE_MODE value = CLU13 CF_Clu_DrivingModeSwi code: 0=NORMAL 1=SPORT 3=ECO. */
    public static String driveModeLabel(int code) { return code==0?"NORMAL":code==1?"SPORT":code==3?"ECO":"—"; }
    /** Text for enumerated signals; null means a plain number. */
    public static String label(int signal,float value) {
        if(signal==8)return gearLabel((int)value);
        if(signal==GEAR_SELECTOR)return selectorLabel((int)value);
        if(signal==SHIFT_HINT)return value==0?"—":shiftHint(value);
        if(signal==DRIVE_MODE)return driveModeLabel((int)value);
        if(signal==PARK_BRAKE||signal==BRAKE_SWITCH)return value>0?"ON":"OFF";
        if(signal==DOOR_DRIVER||signal==DOOR_PASSENGER||signal==TRUNK||signal==HOOD)return value>0?"OPEN":"CLOSED";
        if(signal==TURN_LEFT||signal==TURN_RIGHT||signal==HEAD_LAMP||signal==TAIL_LAMP||signal==HIGH_BEAM)return value>0?"ON":"OFF";
        if(signal==WIPER)return value==0?"OFF":value==1?"LOW":value==2?"HIGH":"—";
        return null;
    }
    public synchronized void speed(int raw,long now) {
        rawSpeed=raw;
        if(hscanSpeed)return; // PD: CLU11 speed is already valid; OEM events arrive with unit=-1 and would blank it.
        put(7,speedUnit==1?raw*1.609344f:raw,speedUnit>=0 && raw>=0 && raw<511,now);
    }
    public synchronized void units(byte[] b,long now) {
        speedUnit=b!=null && b.length>=3?(b[0]&1):-1;
        int unit=b!=null && b.length>=3?(b[0]>>1)&3:0;
        int raw=b!=null && b.length>=3?b[unit==2?2:1]&255:255;
        // JSN OEM ranges and offsets; the dashboard consistently shows Celsius.
        boolean ok=(unit==1&&raw<=100)||(unit==2&&raw<=180);
        put(AMBIENT,unit==2?(raw-40-32)*5f/9:raw-40,ok,now);
        if(seen[7]&&!hscanSpeed) {
            // Unit events must not refresh the timestamp or reception count of old speed.
            values[7]=speedUnit==1?rawSpeed*1.609344f:rawSpeed;
            valid[7]=speedUnit>=0 && rawSpeed>=0 && rawSpeed<511;
            changed[7]=false;
        }
    }
    public synchronized String diagnostics(long now) {
        StringBuilder s=new StringBuilder("unit=").append(speedUnit);
        for(int i=0;i<LABELS.length;i++)s.append(" ").append(i).append(":").append(counts[i])
            .append("/").append(seen[i]?now-times[i]:-1).append("ms/").append(sample(i,now).valid?"valid":"unknown");
        return s.toString();
    }
    public synchronized void rearView(int view) {
        rearView=view;
        if(view==1 || view==3) { valid[8]=false;changed[8]=false; }
        // A clear event alone cannot resurrect a previously received gear.
    }
    public synchronized void additional(byte[] b,long now) {
        pressures(b,now);
        if(b==null || (b.length!=19 && b.length!=40)) {
            for(int i=8;i<=10;i++)put(i,0,false,now);
            put(STEERING,0,false,now);
            return;
        }
        int gear=b[4];
        int throttle=((b[6]&255)<<8)|(b[7]&255);
        int brake=b.length==19?b[0]:((b[0]&255)<<8)|(b[1]&255);
        put(8,gear,gear>=1 && gear<=6 && b[5]!=1 && rearView!=1 && rearView!=3,now); // Verified manual forward gears only.
        put(9,Math.min(100,throttle*0.3906f),throttle!=65535,now);
        put(10,b.length==19?brake:brake*0.1f,brake>=0 && brake!=65535,now);
        if(b.length==40){
            int angle=((b[20]&255)<<8)|(b[21]&255);
            put(STEERING,(short)angle*0.1f,true,now);
        }
    }
    /** Only new IL frames from the verified JSN HSCAN bridge. No cached getter polling. */
    public synchronized void hscanJsn(int cid,int funcLow,byte[] b,long now){
        if(cid!=0xc7||(funcLow&0x80)!=0||b==null)return;
        if(funcLow==0x07&&b.length==8){
            int soc=b[2]&255,voltage=((b[4]&63)<<8)|(b[3]&255);
            boolean sensorOk=(b[6]&0x80)==0&&(b[7]&0x80)==0;
            put(BAT_SOC,soc,sensorOk&&soc<=100,now);
            put(BAT_VOLTAGE,voltage*0.001f+6,sensorOk&&voltage<=12000,now);
        }else if(funcLow==0x53&&b.length==5){
            int raw=b[2]&255;put(STEERING_SPEED,raw*4,raw<255,now);
        }else if(funcLow==0x23&&b.length==8){
            // JSN/Manual DBC call CAN 0x547 EMS15; AiM names its byte2 ECU_IAT.
            // 255 is conservatively withheld until validity is checked on this car.
            int raw=b[2]&255;put(INTAKE,raw*0.75f-48,raw<255,now);
        }else if(funcLow==0x6e&&b.length==8){
            // WHL_SPD11: LE14 per wheel; upper bits carry counters/checksum, not speed.
            for(int i=0;i<4;i++){
                int raw=(b[2*i]&255)|((b[2*i+1]&63)<<8);
                put(3+i,raw*0.03125f,raw<16352,now);
            }
        }else if(funcLow==0x5a&&b.length==8){
            boolean allFF=true;for(byte v:b)if(v!=(byte)255)allFF=false;
            put(TCS,(b[1]>>1)&1,!allFF,now);put(ESC,(b[1]>>6)&1,!allFF,now);put(ABS,(b[1]>>2)&1,!allFF,now);
        }
    }
    /** i30 PD (pd_kr) hscan_mux: each slot carries a raw CAN frame. Slot numbers and byte layouts were
     * verified in-car on 2026-10-29 with action-tagged captures (i30pd_port/HSCAN_DECODE.md).
     * Every byte layout/scale is the commaai/opendbc hyundai_2015_ccan definition (the database openpilot
     * uses), so e.g. brake pressure here is the exact value openpilot reads. Coolant, throttle, boost and
     * transmission temperature have no matching slot and stay unknown rather than showing a guess. */
    public synchronized void hscanPd(int cid,int funcLow,byte[] b,long now){
        if(cid!=0xc7||b==null)return;
        if(funcLow==0x02&&b.length==4){
            // CLU11: CF_Clu_Vanz 9 bit * 0.5 km/h at bits 8-16, CF_Clu_VanzDecimal 2 bit * 0.125 at bits 6-7.
            int vanz=(b[1]&255)|((b[2]&1)<<8);
            hscanSpeed=true;
            put(7,vanz*0.5f+((b[0]>>6)&3)*0.125f,vanz<511,now);
        }else if(funcLow==0x03&&b.length==4){
            // CLU12 odometer: CF_Clu_Odometer 0|24 little-endian * 0.1 km (opendbc hyundai_2015_ccan).
            // In-car: 103374.9 km, monotonically rising.
            int raw=(b[0]&255)|((b[1]&255)<<8)|((b[2]&255)<<16);
            put(ODOMETER,raw*0.1f,raw>0,now);
        }else if(funcLow==0x08&&b.length==8){
            // EMS11 (opendbc): N @16|16 LE *0.25 rpm (bytes 2-3); TQI @32|8 *0.390625 % indicated torque
            // (byte4, engine load); VS @48|8 km/h (byte6) cross-checks CLU11.
            int n=(b[2]&255)|((b[3]&255)<<8);
            put(0,n*0.25f,n!=65535,now);
            if(n!=65535&&profile().trans==VehicleProfile.Trans.MT){
                // A match (or no match) must hold MT_HOLD_MS before it is shown: a heel-toe / rev-match blip sweeps the
                // rpm through other gears' windows with the clutch in, so the last gear stays until the clutch is let out.
                // Clutch in while rolling: the gear stays until the clutch is let out, instead of a dash at low rpm.
                float kmh=valid[7]?values[7]:0,rpm=n*0.25f;
                int g=clutchToggled&&clutchLast==1&&kmh>=1?mtShown:mtGear(rpm,kmh);
                // Neutral (N) only with the clutch known released and no gear matching: standing with the engine running
                // (a gear would stall it), or rolling off the pedal near idle while fuel still flows; a fuel cut means engine
                // braking, so in gear, and the dash stays. Pedal-off idle fuel alone is not enough: on our 7DCT logs half the
                // in-gear pedal-off frames under 1300 rpm still burn fuel. During a blip (pedal pressed) the last gear stays
                // rather than flashing N for a fraction of a second.
                // Half clutch: the EMS switch closes early in the pedal travel, so slipping at the bite point reads as clutch
                // in (gear held). If it ever reads released while slipping, the moving ratio keeps any new gear out, and
                // rolling N needs >= 15 km/h, above where a half-clutch crawl at idle would look like neutral.
                if(g==0&&clutchToggled&&clutchLast==0&&rpm>=400&&(kmh<1||kmh>=15&&rpm<1300&&valid[9]&&values[9]<1
                    &&!(valid[FUEL_RATE]&&values[FUEL_RATE]==0)))g=MT_N;
                // Free-running at the ECU idle target while the speed drifts is neutral too, even where idle rpm happens to
                // fit a gear (6th at ~30-36 km/h). On our 7DCT logs a free-running engine sits within +-8 rpm of
                // R_NEngIdlTgC (p5-p95), and in gear off the pedal >= 15 km/h only 1.8 % of frames come within 40 rpm of
                // it; in gear the rpm follows the speed, so a 5 % speed change takes it 35+ rpm off. A steady in-gear idle
                // crawl (3rd at 16 km/h) keeps its speed and so its gear.
                boolean atIdle=clutchToggled&&clutchLast==0&&kmh>=15&&!Float.isNaN(idleTarget)&&Math.abs(rpm-idleTarget)<=15
                    &&valid[9]&&values[9]<1;
                if(!atIdle)idleKmh=0;else if(idleKmh==0)idleKmh=kmh;else if(Math.abs(kmh-idleKmh)>=idleKmh*0.05f)g=MT_N;
                float ratio=kmh>0?rpm/kmh:0;
                if(mtReverse||rearView==1||rearView==3){
                    // Reverse (EMS_H12 R code or the rear camera view): a switch, not a ratio, so shown at once. Also keeps a
                    // slow reverse from reading as 1st (reverse 3.583 is within 8 % of 1st's 3.308).
                    mtShown=mtCand=MT_R;mtCandAt=now;mtRatioMin=mtRatioMax=ratio;
                }else{
                    if(g!=mtCand||ratio>mtRatioMin*MT_STEADY||ratio*MT_STEADY<mtRatioMax){mtCand=g;mtCandAt=now;mtRatioMin=mtRatioMax=ratio;}
                    else{mtRatioMin=Math.min(mtRatioMin,ratio);mtRatioMax=Math.max(mtRatioMax,ratio);}
                    if(now-mtCandAt>=MT_HOLD_MS)mtShown=mtCand;
                }
                // a valid 0 is N, a valid 14 is R (gearLabel / gearText)
                put(8,mtShown==MT_N?0:mtShown==MT_R?14:mtShown,mtShown!=0,now);
            }
            int tqi=b[4]&255;
            put(ENGINE_LOAD,tqi*0.390625f,tqi<255,now);
            // TQI_ACOR @8|8: indicated torque after TCS/TCU intervention (equals TQI except during cuts);
            // TQFR @40|8: engine friction/accessory loss (1.6-4.3 % in-car). Net torque is about TQI_ACOR - TQFR.
            int acor=b[1]&255,fr=b[5]&255;
            put(TORQUE_CORR,acor*0.390625f,acor<255,now);
            put(TORQUE_FRICTION,fr*0.390625f,fr<255,now);
        }else if(funcLow==0x0b&&b.length==8){
            // Intake air temperature: byte2 * 0.75 - 48 (EMS15 IntAirTemp / EMS_DCT12 IndAirTemp, bit 16).
            // Cross-checked across captures: 45C right after a lunchtime drive (heat soak), 28.5C on an
            // evening cold start, rising 32->41C while driving - intake behaviour, not coolant (which would
            // sit near 90C when hot). byte1 bit changes are door-lock status on the shared frame; ignore here.
            int raw=b[2]&255;
            put(INTAKE,raw*0.75f-48,raw<255,now);
        }else if(funcLow==0x0a&&b.length==8){
            // Battery voltage = byte3 * 0.1 V (14.2 idle, 13.8 with headlights on, 13.4-14.0 driving).
            int raw=b[3]&255;
            put(BAT_VOLTAGE,raw*0.1f,raw>=60&&raw<=200,now);
            // Same frame is EMS14: AMP_CAN @3|5 *10.731613+458.98 mmHg (998 hPa in-car), reference for boost.
            baroHpa=(((b[0]&255)>>3)*10.731613f+458.98f)*1.33322f;
        }else if(funcLow==0x09&&b.length==8){
            // EMS12 (opendbc, ENG_VOL byte7 = 1.6 L in-car): TEMP_ENG @8|8 *0.75-48 C - coolant warm-up
            // 63.8 -> 85.5 C over 15 min, 80-85 C driving (this byte was earlier misread as door lock).
            // PV_AV_CAN @48|8 *0.3906 % accelerator pedal: 0-58 % driving, r=0.90 with EMS11 TQI.
            int coolant=b[1]&255,pedal=b[6]&255;
            put(2,coolant*0.75f-48,coolant>0&&coolant<255,now);
            // The Gamma 1.6 T-GDI has no oil temperature sensor (EMS19 CR_Ems_EngOilTemp is always 0), so the oil slot
            // mirrors this cylinder-head coolant sensor and every oil label says so. Real oil runs hotter under load.
            put(1,coolant*0.75f-48,coolant>0&&coolant<255,now);
            put(9,pedal*0.390625f,pedal<255,now);
            // TPS @40|8 *0.4694836-15.0234742 % throttle plate (0.9-68.5 % driving, r=0.85 with TQI).
            int tps=b[5]&255;
            put(THROTTLE_POS,tps*0.4694836f-15.0234742f,tps<255,now);
            int open=(b[3]>>7)&1;if(clutchLast>=0&&open!=clutchLast)clutchToggled=true;clutchLast=open;
        }else if(funcLow==0x0e&&b.length==8){
            // EMS19 CR_Ems_BstPre @20|12 *1.322 hPa absolute; shown as gauge bar against EMS14 baro.
            // In-car: idle -0.02 bar, peak +0.89 bar at 70 % load (r=0.72 with TQI).
            int raw=((b[2]&255)>>4)|((b[3]&255)<<4);
            boolean ok=raw!=0xfff&&!Float.isNaN(baroHpa);
            boost(ok?(raw*1.322f-baroHpa)/1000f:0,ok,now);
        }else if(funcLow==0x04&&b.length==8){
            // CLU13 CF_Clu_DrivingModeSwi @16|2: 0 normal, 1 sport, 3 eco (10/30 tagged captures; 2 never seen).
            int mode=b[2]&3;
            put(DRIVE_MODE,mode,mode!=2,now);
            // CF_Clu_DTE @24|10 km range; CF_Clu_AvgFCI @6|10 *0.1 trip average km/L (0x3ff until known).
            // km/L: the 10/29 drive matches FCO-integrated distance/fuel at every step (23 points, <0.05).
            int range=(b[3]&255)|((b[4]&3)<<8),avg=((b[0]&255)>>6)|((b[1]&255)<<2),unit=(b[0]>>3)&3;
            put(RANGE,range,range<1023,now);
            put(AVG_FUEL,avg*0.1f,avg<1023&&unit==0,now);
        }else if(funcLow==0x01&&b.length==8){
            // CGW1 body gateway (action-tagged 2026-10-30): byte1 bit0 = driver door, 0x04 closed / 0x05 open
            // (isolated against two closed references, the only byte that flips). byte3 = front wiper speed,
            // 0 off / 1 low / 2 high (stepped cleanly off->low->high). Both are discrete states.
            // Same opendbc CGW1 layout gives the other closures (0 closed; never opened in a capture yet):
            // AstDrSw @35|1, TrunkTgSw @12|2, HoodSw @17|2. Rear doors are CGW2, not found in hscan.
            // byte3 bit7 is HeadLampLow, so the wiper reads bits 0-2 only (DBC Int/Low/HighSw).
            put(DOOR_DRIVER,b[1]&1,true,now);
            put(DOOR_PASSENGER,(b[4]>>3)&1,true,now);
            int trunk=(b[1]>>4)&3,hood=(b[2]>>1)&3;
            put(TRUNK,trunk,trunk<2,now);
            put(HOOD,hood,hood<2,now);
            int wip=b[3]&7;
            put(WIPER,wip,wip<=3,now);
            // Turn signals (action-tagged): CGW1 byte2 bit3 left, byte7 bit6 right (opendbc TurnSigLh/Rh). The bit blinks with the lamp.
            put(TURN_LEFT,(b[2]>>3)&1,true,now);
            put(TURN_RIGHT,(b[7]>>6)&1,true,now);
            put(HEAD_LAMP,(b[3]>>7)&1,true,now);   // HeadLampLow: on 0x80 / off 0x00 (10/30 capture)
            put(TAIL_LAMP,(b[4]&0xc0)!=0?1:0,true,now);   // 미등/후미등: byte4 bit6 parking or bit7 head (11/02 lamp capture)
            put(HIGH_BEAM,b[4]&1,true,now);               // 상향등: byte4 bit0 on 0x01 (11/02 lamp capture)
        }else if(funcLow==0x21&&b.length==6){
            // EMS20 FCO @0|16 *0.128 uL injected per 10 ms message -> *0.36 = L/h (idle 0.4-0.8, drive peak 30).
            int fco=(b[0]&255)|((b[1]&255)<<8);
            put(FUEL_RATE,fco*0.04608f,fco!=65535,now);
        }else if(funcLow==0x12&&b.length==7){
            // EPB11 EPB_STATUS @19|3: 1 released, 2 applied (EPB_I_LAMP and EPB_FORCE follow); other states withheld.
            int state=(b[2]>>3)&7;
            put(PARK_BRAKE,state==2?1:0,state==1||state==2,now);
        }else if(funcLow==0x0c&&b.length==8){
            // EMS16 (TQI byte1 = EMS11 TQI r=1.00, AliveCounter/Checksum cycling): SPK_TIME_CUR @48|8 *0.375-35.625 deg.
            // In-car: idle -3.8, light cruise 31.5, >50 % load 2.3 (knock retard). Meaningless during fuel cut.
            int spark=b[6]&255;
            // raw 0 (-35.6 deg) never occurs on a running petrol engine; it is what a diesel's empty, zero-frozen slot sends.
            put(SPARK,spark*0.375f-35.625f,spark>0&&spark<255,now);
        }else if(funcLow==0x1b&&b.length==8){
            // TCS13 DriverBraking @55|1 (AliveCounterTCS/CheckSum cycling): agrees with ESP12 pressure >3 bar 98 %.
            put(BRAKE_SWITCH,(b[6]>>7)&1,true,now);
        }else if(funcLow==0x17&&b.length==8){
            // MDPS11 CR_Mdps_DrvTq @11|12 *0.01-20.48 Nm driver steering torque (-4.3..3.3 in-car, sign not yet verified).
            int tq=(((b[1]&255)>>3)|((b[2]&255)<<5))&0xfff;
            put(STEER_TORQUE,tq*0.01f-20.48f,tq!=0xfff,now);
        }else if(funcLow==0x13&&b.length==8){
            // ESP12 (standard HKMC layout): LAT_ACCEL 0|11 and LONG_ACCEL 13|11 in m/s^2 (*0.01-10.23),
            // CYL_PRES 26|12 in bar (*0.1). DIAG bits 12 / 25 / 39 flag invalid data.
            int lat=(b[0]&255)|((b[1]&7)<<8),lon=((b[1]&255)>>5)|((b[2]&255)<<3),pres=((b[3]&255)>>2)|((b[4]&63)<<6);
            boolean latOk=(b[1]&0x10)==0,lonOk=(b[3]&0x02)==0,presOk=(b[4]&0x80)==0;
            put(11,(float)((lat*0.01-10.23)/9.80665),latOk&&lat<2047,now);
            put(12,(float)((lon*0.01-10.23)/9.80665),lonOk&&lon<2047,now);
            put(10,pres*0.1f,presOk&&pres<4095,now);
            // YAW_RATE @40|13 *0.01 - 40.95 deg/s (bytes 5-6); DIAG bit 54; raw 0 is the no-data floor.
            int yaw=(b[5]&255)|((b[6]&0x1f)<<8);boolean yawOk=(b[6]&0x40)==0&&yaw!=0;
            put(YAW,yaw*0.01f-40.95f,yawOk,now);
        }else if(funcLow==0x19&&b.length==5){
            // SAS11: angle s16 little-endian * 0.1 deg (left positive, full lock about +-470), speed byte2 * 4 deg/s.
            int angle=(b[0]&255)|((b[1]&255)<<8),speed=b[2]&255;
            put(STEERING,(short)angle*0.1f,angle!=0x8000,now);
            put(STEERING_SPEED,speed*4,speed<255,now);
        }else if(funcLow==0x1d&&b.length==8){
            // LVR12 CF_Lvr_Gear, low nibble of byte1: P=0, D=5, N=6, R=7, S=8 = manual gate (each observed in-car;
            // 8 was read as P while only 3 bits were decoded). The manual gear number itself comes from 0x1f.
            int code=b[1]&15,sel=code==0?0:code==7?1:code==6?2:code==5?3:code==8?4:-1;
            put(GEAR_SELECTOR,sel,sel>=0,now);
            // byte7 (checksum) cycles every frame from a real lever, P included (10/29 idle log: 4 values in 115 frames).
            int check=b[7]&255;
            if(leverCheck>=0){ if(check!=leverCheck)leverAlive=true; else leverFrozen++; }
            leverCheck=check;
        }else if(funcLow==0x20&&b.length==6){
            // TPMS: 00 00 ff ff ff ff until the sensors report after moving, then byte1=1 and bytes 2-5 hold
            // FL/FR/RL/RR in psi (0x21/0x22 observed while the cluster showed 35). Order assumed FL FR RL RR.
            boolean ok=(b[1]&255)==1;
            for(int i=0;i<4;i++){int raw=b[2+i]&255;put(TPMS_FL+i,raw,ok&&raw<255&&raw<=100,now);}
        }else if(funcLow==0x1f&&b.length==8){
            // Current gear in the high nibble of byte2: 1-7 (7DCT) in D and in the manual gate; 0xe = R, 0 = P/N.
            // A 6MT has no TCU: there the slot is meaningless and the gear comes from rpm/speed (EMS11 above).
            int gear=(b[2]>>4)&15;
            if(profile().trans!=VehicleProfile.Trans.MT)put(8,gear,gear>=1&&gear<=7,now);
        }else if(funcLow==0x10&&b.length==8){
            // EMS_H12 CF_Ems_DesCurGr @50|4 (byte6 bits 2-5), next to UpTarGr/DownTarGr: the cluster's shift-indicator
            // gear. Always 0 on the 7DCT (10/29 drive log, 3581 frames), so nonzero marks a manual. Not the engaged gear:
            // a 6MT owner saw v1.5 show the cluster's recommended gear, so it is the shift hint, never the gear itself.
            // SHIFT_HINT = +gear with CF_Ems_UpTarGr (bit48 = byte6 bit0), -gear with DownTarGr (bit49); no arrow = none.
            int des=(b[6]>>2)&15,up=b[6]&1,down=(b[6]>>1)&1;
            if(des>=1&&des<=7)emsGearSeen=true;
            // In reverse the 6MT owner's cluster shows R in this spot, so DesCurGr has an R code. Not captured yet: 14 (TCU
            // CUR_GR's R) and 7 (CF_Lvr_Gear's R) are both taken, neither can be a 6MT gear.
            mtReverse=(des==14||des==7)&&up==0&&down==0;
            // R_NEngIdlTgC @40|8 *10 rpm (byte5): the ECU's idle target, 630-1000 rpm in-car.
            int it=b[5]&255;idleTarget=it>0&&it<255?it*10f:Float.NaN;
            put(SHIFT_HINT,up==1?des:-des,des>=1&&des<=7&&(up^down)==1,now);
        }
    }
    /** Explicit CAN-ID input only. No MICOM function has been mapped to e-LSD yet.
     * Retains the candidate scaled number without inventing a percent/current/torque unit. */
    public synchronized void canFrame(int canId,byte[] b,long now){
        if(canId!=0x3f0||b==null||b.length!=8)return;
        int raw=(b[1]&255)|((b[2]&255)<<8);
        put(ELSD,raw*0.1f,raw!=65535,now);
    }
    private void pressures(byte[] b,long now){
        // Captured NAdd40: status22, unit23, FL/FR/RL/RR BE16 at28..35.
        // Only status1/unit0 has been corroborated against this vehicle's cluster (psi).
        boolean known=b!=null&&b.length==40&&b[22]==1&&b[23]==0;
        for(int i=0;i<4;i++){
            int raw=known?((b[28+2*i]&255)<<8)|(b[29+2*i]&255):511;
            // Conservative display bound, not an asserted OEM validity enum.
            put(TPMS_FL+i,raw,known&&raw<=100,now);
        }
    }
    public synchronized void rpm(byte[] b, long now) {
        ptVersion++;ptTime=now;
        if(b==null || b.length<8) { ptValid=false;put(0,0,false,now);invalidateComputed(now);return; }
        int raw=((b[1]&255)<<8)|(b[2]&255);
        // OEM onPowerTorqueTurboBoostBitMasking: unsigned big-endian * 0.25.
        // 0xffff conservatively suppressed; protocol validity is not fully documented.
        put(0,raw*0.25f,raw!=65535,now);
        ptValid=raw!=65535;
        inputRpm=raw*0.25f;inputAcor=(b[0]&255)*0.390625f;inputTqi=(b[3]&255)*0.390625f;
        inputAmbient=(float)(((b[4]>>2)&31)*10.731613+458.98);
        inputBoost=(float)((((b[5]&255)<<4)|((b[6]>>4)&15))*1.322);
        if((b[4]&3)==3){
            inputStd=(((b[6]&15)<<2)|((b[7]>>6)&3))*10f;stdTime=now;stdSeen=inputStd>0;
        }
        if(!ptValid)invalidateComputed(now);
    }
    public synchronized void temperatures(byte[] b, long now) {
        for(int i=0;i<2;i++) {
            int raw=b!=null && b.length>=2 ? b[i]&255 : 255;
            // OEM onOilCoolantTemperatureBitMasking explicitly rejects 0xff.
            put(i+1,raw*0.75f-(i==0?40:48),raw<255,now);
        }
    }
    public synchronized void gforce(byte[] b,long now) {
        gfVersion++;gfTime=now;gfValid=b!=null&&b.length>=8;
        if(gfValid)friction=(b[3]&255)*0.390625f;else invalidateComputed(now);
        for(int axis=0;axis<2;axis++) {
            if(b==null || b.length<8) { put(11+axis,0,false,now);continue; }
            int offset=axis==0?0:4;
            int raw=((b[offset]&255)<<8)|(b[offset+1]&255);
            int status=(b[2]>>(axis*4))&15;
            float value=(float)(raw*0.000127465-4.17677312);
            if(axis==1)value=-value; // OEM longitudinal sign inversion.
            // Status 0 is observed in retained normal data; other states remain unknown.
            put(11+axis,value,status==0 && raw!=65535,now);
        }
    }
    private void invalidateComputed(long now){for(int i=POWER;i<=BOOST;i++)put(i,0,false,now);}
    private static boolean close(float a,float b){return !Float.isNaN(a)&&!Float.isInfinite(a)&&Math.abs(a-b)<=Math.max(0.0001f,Math.abs(b)*0.00001f);}
    /** Called at the start of OEM calculation, capturing the actual input generation. */
    public synchronized void beginComputed(float rpm,float tqi,float acor,float standard,float boost,float ambient){
        Calculation c=new Calculation();c.epoch=epoch;c.pt=ptVersion;c.gf=gfVersion;
        c.time=Math.min(ptTime,Math.min(gfTime,stdTime));
        c.valid=ptValid&&gfValid&&stdSeen&&close(rpm,inputRpm)&&close(tqi,inputTqi)&&close(acor,inputAcor)
            &&close(standard,inputStd)&&close(boost,inputBoost)&&close(ambient,inputAmbient);
        float rawTorque=(acor-friction)*standard/100;
        c.power=Math.max(0,(float)(rawTorque*((double)rpm/716.2/9.81)));
        c.torque=rawTorque<5.5f?0:rawTorque;
        float pressure=(float)(((double)boost-1.333*ambient)/10);
        c.boost=pressure<5.5f?0:pressure;
        calculation.set(c);
    }
    /** Receives instantaneous validated OEM outputs, never the peak/total log fields. */
    public synchronized void computed(float power,float torque,float boostKpa){
        Calculation c=calculation.get();calculation.remove();
        if(c==null||!c.valid||c.epoch!=epoch||c.pt!=ptVersion||c.gf!=gfVersion
                ||(c.pt==lastComputedPt&&c.gf==lastComputedGf))return;
        // Matching power also checks the friction sample used by the native calculation.
        // OEM startup/low-RPM policy may independently zero torque and boost.
        if(!close(power,c.power)||!(torque==0||close(torque,c.torque))||!(boostKpa==0||close(boostKpa,c.boost)))return;
        lastComputedPt=c.pt;lastComputedGf=c.gf;
        put(POWER,power,true,c.time);put(TORQUE,torque,true,c.time);put(BOOST,boostKpa/100,true,c.time);
    }
    /** Standalone calculation from received source frames, using the captured OEM formula.
     * No cached OEM app fields, peak values or synthetic receipts are involved. */
    public synchronized void calculateObserved(long now){
        if(!ptValid||!gfValid||!stdSeen||now-ptTime>=STALE_MS||now-gfTime>=STALE_MS||now-stdTime>=STALE_MS)return;
        if(ptVersion==lastComputedPt&&gfVersion==lastComputedGf)return;
        beginComputed(inputRpm,inputTqi,inputAcor,inputStd,inputBoost,inputAmbient);
        Calculation c=calculation.get();if(c==null||!c.valid)return;
        float torque=c.torque,boost=c.boost;
        if(inputRpm!=observedPreviousRpm){
            observedTorqueValid=!(inputRpm<=300||(observedPreviousRpm<inputRpm&&inputRpm<400));
        }
        if(!observedTorqueValid){torque=0;boost=0;}
        observedPreviousRpm=inputRpm;
        computed(c.power,torque,boost);
    }
    public synchronized void wheels(byte[] b, long now) {
        for(int i=0;i<4;i++) {
            int raw=b!=null && b.length>=8 ? ((b[2*i]&255)<<8)|(b[2*i+1]&255) : 65535;
            float speed=raw*0.03125f;
            // OEM uses this factor, then rejects aggregate speeds >=511. Apply
            // that range conservatively per wheel; this is not the cluster speed.
            put(i+3,speed,speed<511,now);
        }
    }
}
