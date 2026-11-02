import com.veloster.vehicle.Telemetry;

/** i30 PD hscan slot fixtures taken from in-car captures (2026-10-29); checks the documented decode only. */
public final class HscanPdTelemetryTest {
    private static int checks;
    private static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
    private static void equal(Telemetry t,int id,float expected,long now){
        Telemetry.Sample s=t.sample(id,now);check(s.valid&&Math.abs(s.value-expected)<0.01,"signal "+id+" expected "+expected+" got "+s.value+(s.valid?"":" (invalid)"));
    }
    private static byte[] b(int... v){byte[] r=new byte[v.length];for(int i=0;i<v.length;i++)r[i]=(byte)v[i];return r;}
    public static void main(String[] args){
        Telemetry t=new Telemetry();
        // Gate: wrong CID or wrong length never creates a value.
        t.hscanPd(0x8a,0x08,b(5,11,0xca,9,11,10,0,0x7f),1000);
        t.hscanPd(0xc7,0x08,b(5,11,0xca,9,11,10,0),1000);
        check(!t.sample(0,1000).seen,"wrong CID or length cannot create rpm");
        // EMS11 slot 0x08: idle frame captured 12:54 -> 626.5 rpm (little-endian bytes 2-3).
        t.hscanPd(0xc7,0x08,b(5,11,0xca,9,11,10,0,0x7f),1000);equal(t,0,626.5f,1000);
        t.hscanPd(0xc7,0x08,b(0,0,0xff,0xff,0,0,0,0),1001);check(!t.sample(0,1001).valid,"rpm 0xffff withheld");
        // CLU11 slot 0x02: drive capture peak 0x84 -> 66 km/h; decimal bits add 0.125 steps.
        t.hscanPd(0xc7,0x02,b(0x00,0x84,0xa4,0x40),1000);equal(t,7,66f,1000);
        t.hscanPd(0xc7,0x02,b(0x40,0x13,0xa4,0x40),1001);equal(t,7,9.625f,1001);
        t.speed(37,1002);equal(t,7,9.625f,1002);check(t.sample(7,1002).valid,"OEM speed event with unknown unit must not blank hscan speed");
        t.units(b(0,25,0),1003);equal(t,7,9.625f,1003);
        t.clear();t.speed(37,1004);check(!t.sample(7,1004).valid,"after clear the OEM path behaves as before (unit unknown)");
        // Battery voltage slot 0x0a: byte3 0x8e -> 14.2 V; 0xff withheld.
        t.hscanPd(0xc7,0x0a,b(0xd8,0x80,0,0x8e,0,0,0,0),1000);equal(t,Telemetry.BAT_VOLTAGE,14.2f,1000);
        t.hscanPd(0xc7,0x0a,b(0xd8,0x80,0,0xff,0,0,0,0),1001);check(!t.sample(Telemetry.BAT_VOLTAGE,1001).valid,"voltage sentinel withheld");
        // ESP12 slot 0x13: idle frame -> ~0 G and 1.2 bar; hard brake -> 83.1 bar; diag bit withholds pressure.
        t.hscanPd(0xc7,0x13,b(0xf3,0x43,0x82,0x30,0x00,0xf7,0x0f,0x39),1000);
        equal(t,11,(float)((1011*0.01-10.23)/9.80665),1000);
        equal(t,12,(float)((1042*0.01-10.23)/9.80665),1000);
        equal(t,10,1.2f,1000);
        t.hscanPd(0xc7,0x13,b(0xf3,0x43,0x82,0xfc,0x0c,0xf7,0x8f,0x39),1001);equal(t,10,83.1f,1001);
        t.hscanPd(0xc7,0x13,b(0xf3,0x43,0x82,0xfc,0x8c,0xf7,0x8f,0x39),1002);check(!t.sample(10,1002).valid,"CYL_PRES diag bit withholds pressure");
        // SAS11 slot 0x19: -13.5 deg at rest, +464.9 deg at left full lock, speed byte2*4.
        t.hscanPd(0xc7,0x19,b(0x79,0xff,0,7,0x2b),1000);equal(t,Telemetry.STEERING,-13.5f,1000);equal(t,Telemetry.STEERING_SPEED,0,1000);
        t.hscanPd(0xc7,0x19,b(0x29,0x12,0x0d,7,0x2b),1001);equal(t,Telemetry.STEERING,464.9f,1001);equal(t,Telemetry.STEERING_SPEED,52,1001);
        t.hscanPd(0xc7,0x19,b(0x29,0x12,0xff,7,0x2b),1002);check(!t.sample(Telemetry.STEERING_SPEED,1002).valid,"steering speed sentinel withheld");
        // Gear selector slot 0x1d (byte1 low nibble, LVR12 CF_Lvr_Gear): P=0 R=7 N=6 D=5 S/manual=8; anything else unknown.
        // 0xc8 is the manual gate from the 10/29 drive log (0x1f still reported gears 1-5) - it used to decode as P.
        int[][] sel={{0xc0,0},{0xc7,1},{0xc6,2},{0xc5,3},{0xc8,4}};
        for(int[] s:sel){t.hscanPd(0xc7,0x1d,b(0,s[0],0x71,0,0,0,0,0),2000);equal(t,Telemetry.GEAR_SELECTOR,s[1],2000);}
        t.hscanPd(0xc7,0x1d,b(0,0xc1,0x71,0,0,0,0,0),2001);check(!t.sample(Telemetry.GEAR_SELECTOR,2001).valid,"unknown selector code withheld");
        check(Telemetry.selectorLabel(0).equals("P")&&Telemetry.selectorLabel(1).equals("R")&&Telemetry.selectorLabel(2).equals("N")&&Telemetry.selectorLabel(3).equals("D")&&Telemetry.selectorLabel(4).equals("M"),"selector labels");
        // Current gear slot 0x1f (byte2 high nibble): 3rd gear valid; R (0xe) and P/N (0) are not forward gears.
        t.hscanPd(0xc7,0x1f,b(0,0xa0,0x30,0,0,0,0,0),1000);equal(t,8,3,1000);
        t.hscanPd(0xc7,0x1f,b(0,0xa0,0xe0,0,0,0,0,0),1001);check(!t.sample(8,1001).valid,"reverse is not a forward gear number");
        t.hscanPd(0xc7,0x1f,b(0,0xa0,0x00,0,0,0,0,0),1002);check(!t.sample(8,1002).valid,"P/N has no gear number");
        t.hscanPd(0xc7,0x1f,b(0,0x9d,0x70,0x0f,0,0,0,0),1003);equal(t,8,7,1003);check(Telemetry.gearLabel(7).equals("7"),"7th gear of the 7DCT");
        // EMS16 slot 0x0c raw 0 (-35.6 deg) is never real timing: a diesel's empty slot frozen at zeros must not read as petrol.
        Telemetry dz=new Telemetry();
        for(int k=0;k<5;k++){dz.hscanPd(0xc7,0x0c,b(0,0,0,0,0,0,0,0),1000+k);dz.hscanPd(0xc7,0x08,b(0,0,0xe0,0x2e,0,0,0,0),1000+k);}
        check(!dz.sample(Telemetry.SPARK,1005).valid,"spark raw 0 withheld");
        check(!dz.profile().hasSpark,"zeroed spark slot + 3000 rpm max stays diesel");
        // Shift target range and LEDs: petrol 3000-6700, diesel the same range scaled by the redline ratio; 15 LEDs over
        // the last quarter below the target (100 rpm each at 6000 as before, 50 at 3000).
        com.veloster.vehicle.VehicleProfile pet=com.veloster.vehicle.VehicleProfile.PETROL_DCT,dsl=com.veloster.vehicle.VehicleProfile.DIESEL_DCT;
        check(pet.shiftMin==3000&&pet.shiftMax==6700&&pet.shiftDefault==6000,"petrol shift range 3000-6700");
        check(dsl.shiftMin==2100&&dsl.shiftMax==4700&&dsl.shiftDefault==4200,"diesel shift range scaled by 4600/6500");
        check(com.veloster.vehicle.ShiftGuide.clamp(2500,pet.shiftMin,pet.shiftMax)==3000,"below the range clamps to 3000");
        check(com.veloster.vehicle.ShiftGuide.target(0,dsl)==4200&&com.veloster.vehicle.ShiftGuide.target(0,pet)==6000,"unset target = profile default");
        check(com.veloster.vehicle.ShiftGuide.target(6000,dsl)==4700&&com.veloster.vehicle.ShiftGuide.target(3500,pet)==3500,"saved target clamps to the profile");
        Telemetry sg=new Telemetry();sg.hscanPd(0xc7,0x1d,b(0,0xc5,0x71,0,0,0,0,0),5000);   // lever in D: a DCT
        sg.hscanPd(0xc7,0x02,b(0,100,0,0),5000);sg.hscanPd(0xc7,0x1f,b(0,0xa0,0x30,0,0,0,0,0),5000);
        int[][] cases={{6000,4499,0},{6000,4500,1},{6000,5950,15},{3000,2000,0},{3000,2250,1},{3000,2990,15}};
        for(int[] k:cases){int q=k[1]*4;sg.hscanPd(0xc7,0x08,b(0,0,q&255,q>>8,0,0,0,0),5000);
            com.veloster.vehicle.ShiftGuide.Cue cue=com.veloster.vehicle.ShiftGuide.evaluate(sg.snapshot(5000),k[0],pet);
            check(cue.leds==k[2]&&!cue.shift,"target "+k[0]+" at "+k[1]+" rpm: "+k[2]+" LEDs (got "+cue.leds+")");}
        sg.hscanPd(0xc7,0x08,b(0,0,(3000*4)&255,(3000*4)>>8,0,0,0,0),5000);
        check(com.veloster.vehicle.ShiftGuide.evaluate(sg.snapshot(5000),3000,pet).shift,"3000 target reached in 3rd: SHIFT");
        // Manual (6MT): a nonzero EMS_H12 shift-indicator gear (0x10 byte6 bits 2-5) marks the manual; the gear itself
        // comes from rpm / speed (EMS11 / CLU11) against the 6MT ratios. EMS and TCU slot values are not displayed.
        Telemetry mt=new Telemetry();
        mt.hscanPd(0xc7,0x10,b(0,0x26,0,0,0,0,1<<2,0),1000);check(!mt.sample(8,1000).valid,"the shift-indicator gear is not shown");
        mt.hscanPd(0xc7,0x02,b(0,120,0,0),1001);                     // 60 km/h
        mt.hscanPd(0xc7,0x08,b(0,0,0xe5,0x2b,0,0,0,0),1002);check(!mt.sample(8,1002).valid,"a match is not shown before it holds");
        mt.hscanPd(0xc7,0x08,b(0,0,0xe5,0x2b,0,0,0,0),1302);equal(mt,8,3,1302); // 2809 rpm at 60 km/h = 3rd, held 300 ms
        mt.hscanPd(0xc7,0x1f,b(0,0,0x10,0,0,0,0,0),1303);equal(mt,8,3,1303);   // a stale "1" in the TCU slot is ignored
        // Heel-toe 3->2 at 60 km/h: clutch in (1500), blip through 3200, past 2nd's window (4260) to an overshoot (5000),
        // back to 4260 and the clutch out. The 3 stays through the blip; 2 shows once it has held 300 ms.
        int[] blip={1500,3200,4260,5000,4260};long at=1400;
        for(int rpm:blip){int n=rpm*4;mt.hscanPd(0xc7,0x08,b(0,0,n&255,n>>8,0,0,0,0),at);equal(mt,8,3,at);at+=100;}
        mt.hscanPd(0xc7,0x08,b(0,0,(4260*4)&255,(4260*4)>>8,0,0,0,0),at+=250);equal(mt,8,2,at);
        int n6=Math.round(100*8.1f*4.467f*0.633f*4);mt.hscanPd(0xc7,0x02,b(0,200,0,0),at+=10);
        mt.hscanPd(0xc7,0x08,b(0,0,n6&255,n6>>8,0,0,0,0),at+=10);mt.hscanPd(0xc7,0x08,b(0,0,n6&255,n6>>8,0,0,0,0),at+=300);
        equal(mt,8,6,at);   // 100 km/h in 6th
        mt.hscanPd(0xc7,0x08,b(0,0,0x10,0x0e,0,0,0,0),at+=10);equal(mt,8,6,at);   // clutch in (900 rpm): still 6 at first
        mt.hscanPd(0xc7,0x08,b(0,0,0x10,0x0e,0,0,0,0),at+=300);check(!mt.sample(8,at).valid,"clutch held in: no gear");
        // With the clutch bit (EMS12 CLU_ACK, byte3 bit7) once it has toggled: clutch in at 60 km/h keeps 3rd through
        // 900 rpm, the clutch out at 4260 rpm shows 2nd after the hold, clutch in at a standstill shows no gear.
        Telemetry cl=new Telemetry();long t2=1000;
        cl.hscanPd(0xc7,0x10,b(0,0x26,0,0,0,0,1<<2,0),t2);cl.hscanPd(0xc7,0x02,b(0,120,0,0),t2);
        cl.hscanPd(0xc7,0x09,b(0,0xaf,0,0x8c,0,0,0,0x10),t2);cl.hscanPd(0xc7,0x09,b(0,0xaf,0,0x0c,0,0,0,0x10),t2);
        cl.hscanPd(0xc7,0x08,b(0,0,0xe5,0x2b,0,0,0,0),t2+=10);cl.hscanPd(0xc7,0x08,b(0,0,0xe5,0x2b,0,0,0,0),t2+=300);equal(cl,8,3,t2);
        cl.hscanPd(0xc7,0x09,b(0,0xaf,0,0x8c,0,0,0,0x10),t2+=10);
        cl.hscanPd(0xc7,0x08,b(0,0,0x10,0x0e,0,0,0,0),t2+=10);cl.hscanPd(0xc7,0x08,b(0,0,0x10,0x0e,0,0,0,0),t2+=1000);equal(cl,8,3,t2);
        cl.hscanPd(0xc7,0x09,b(0,0xaf,0,0x0c,0,0,0,0x10),t2+=10);
        int n2=4260*4;cl.hscanPd(0xc7,0x08,b(0,0,n2&255,n2>>8,0,0,0,0),t2+=10);equal(cl,8,3,t2);
        cl.hscanPd(0xc7,0x08,b(0,0,n2&255,n2>>8,0,0,0,0),t2+=300);equal(cl,8,2,t2);
        cl.hscanPd(0xc7,0x02,b(0,0,0,0),t2+=10);cl.hscanPd(0xc7,0x09,b(0,0xaf,0,0x8c,0,0,0,0x10),t2);
        cl.hscanPd(0xc7,0x08,b(0,0,0x10,0x0e,0,0,0,0),t2+=10);cl.hscanPd(0xc7,0x08,b(0,0,0x10,0x0e,0,0,0,0),t2+=300);
        check(!cl.sample(8,t2).valid,"clutch in at a standstill: no gear");
        // Clutch out at a standstill with the engine running can only be neutral: N.
        cl.hscanPd(0xc7,0x09,b(0,0xaf,0,0x0c,0,0,0,0x10),t2+=10);
        cl.hscanPd(0xc7,0x08,b(0,0,0x80,0x0c,0,0,0,0),t2+=10);cl.hscanPd(0xc7,0x08,b(0,0,0x80,0x0c,0,0,0,0),t2+=300);
        equal(cl,8,0,t2);check(Telemetry.gearText(cl.sample(8,t2).value,Float.NaN,com.veloster.vehicle.VehicleProfile.PETROL_MT).equals("N")
            &&Telemetry.gearLabel(0).equals("N"),"clutch out at a standstill: N");
        // Neutral rev-match blip with the clutch out, 60 km/h in 3rd: the rpm climbs slowly through 2nd's window
        // (3895-4660 rpm, 80 ms steps, 400 ms inside it) and drops. Every climbing step matches 2nd, so the plain 300 ms
        // hold showed 2; the rpm/speed ratio keeps moving (> 8 % within the hold), so 3rd stays.
        Telemetry nb=new Telemetry();long t3=1000;
        nb.hscanPd(0xc7,0x10,b(0,0x26,0,0,0,0,1<<2,0),t3);nb.hscanPd(0xc7,0x02,b(0,120,0,0),t3);
        nb.hscanPd(0xc7,0x09,b(0,0xaf,0,0x8c,0,0,0,0x10),t3);nb.hscanPd(0xc7,0x09,b(0,0xaf,0,0x0c,0,0,0,0x10),t3);
        nb.hscanPd(0xc7,0x08,b(0,0,0xe5,0x2b,0,0,0,0),t3+=10);nb.hscanPd(0xc7,0x08,b(0,0,0xe5,0x2b,0,0,0,0),t3+=300);equal(nb,8,3,t3);
        for(int rpm:new int[]{3900,4050,4200,4350,4500,4650,3600,2900}){
            int q=rpm*4;nb.hscanPd(0xc7,0x08,b(0,0,q&255,q>>8,0,0,0,0),t3+=80);equal(nb,8,3,t3);}
        nb.hscanPd(0xc7,0x08,b(0,0,0xe5,0x2b,0,0,0,0),t3+=80);nb.hscanPd(0xc7,0x08,b(0,0,0xe5,0x2b,0,0,0,0),t3+=300);equal(nb,8,3,t3);
        // Coasting in neutral at 40 km/h: 750 rpm, pedal off, idle fuel (0.5 L/h) and no gear matching -> N after the hold.
        // The same with a fuel cut (engine braking, so a gear is in) is not called N.
        nb.hscanPd(0xc7,0x02,b(0,80,0,0),t3+=10);nb.hscanPd(0xc7,0x21,b(11,0,0,0,0,0),t3);
        nb.hscanPd(0xc7,0x08,b(0,0,0xb8,0x0b,0,0,0,0),t3+=10);nb.hscanPd(0xc7,0x08,b(0,0,0xb8,0x0b,0,0,0,0),t3+=150);equal(nb,8,3,t3);
        nb.hscanPd(0xc7,0x08,b(0,0,0xb8,0x0b,0,0,0,0),t3+=200);equal(nb,8,0,t3);
        nb.hscanPd(0xc7,0x21,b(0,0,0,0,0,0),t3+=10);
        nb.hscanPd(0xc7,0x08,b(0,0,0xb8,0x0b,0,0,0,0),t3+=10);nb.hscanPd(0xc7,0x08,b(0,0,0xb8,0x0b,0,0,0,0),t3+=300);
        check(!nb.sample(8,t3).valid,"fuel cut off the pedal: not N");
        // A pedal press near idle (the blip starting) is not N either.
        nb.hscanPd(0xc7,0x21,b(11,0,0,0,0,0),t3+=10);nb.hscanPd(0xc7,0x09,b(0,0xaf,0,0x0c,0,0,40,0x10),t3);
        nb.hscanPd(0xc7,0x08,b(0,0,0xb8,0x0b,0,0,0,0),t3+=10);nb.hscanPd(0xc7,0x08,b(0,0,0xb8,0x0b,0,0,0,0),t3+=300);
        check(!nb.sample(8,t3).valid,"pedal pressed: not N");
        // Half-clutch crawl that reads as released: 10 km/h at 800 rpm, pedal off, idle fuel, no gear matching. Below
        // 15 km/h this is not called N.
        nb.hscanPd(0xc7,0x09,b(0,0xaf,0,0x0c,0,0,0,0x10),t3+=10);nb.hscanPd(0xc7,0x02,b(0,20,0,0),t3);
        nb.hscanPd(0xc7,0x08,b(0,0,0x80,0x0c,0,0,0,0),t3+=10);nb.hscanPd(0xc7,0x08,b(0,0,0x80,0x0c,0,0,0,0),t3+=300);
        check(!nb.sample(8,t3).valid,"half-clutch crawl under 15 km/h: not N");
        // Reverse: EMS_H12 DesCurGr with the R code (14 or 7, no arrow) shows R at once, even at a speed and rpm that
        // match 1st (reverse 3.583 vs 1st 3.308); leaving reverse goes back to the ratio match. The rear camera view
        // (PGS 1/3) means reverse too.
        Telemetry rv=new Telemetry();long t4=1000;
        rv.hscanPd(0xc7,0x10,b(0,0x26,0,0,0,0,1<<2,0),t4);rv.hscanPd(0xc7,0x02,b(0,20,0,0),t4);   // 10 km/h
        int r1=Math.round(10*8.1f*4.467f*3.583f*4);
        rv.hscanPd(0xc7,0x10,b(0,0x26,0,0,0,0,14<<2,0),t4+=10);rv.hscanPd(0xc7,0x08,b(0,0,r1&255,r1>>8,0,0,0,0),t4+=10);
        equal(rv,8,14,t4);check(Telemetry.gearText(rv.sample(8,t4).value,Float.NaN,com.veloster.vehicle.VehicleProfile.PETROL_MT).equals("R")
            &&Telemetry.gearLabel(14).equals("R"),"DesCurGr R code: R at once");
        rv.hscanPd(0xc7,0x10,b(0,0x26,0,0,0,0,7<<2,0),t4+=10);rv.hscanPd(0xc7,0x08,b(0,0,r1&255,r1>>8,0,0,0,0),t4+=10);equal(rv,8,14,t4);
        rv.hscanPd(0xc7,0x10,b(0,0x26,0,0,0,0,1<<2,0),t4+=10);
        int f1=Math.round(10*8.1f*4.467f*3.308f*4);
        rv.hscanPd(0xc7,0x08,b(0,0,f1&255,f1>>8,0,0,0,0),t4+=10);equal(rv,8,14,t4);
        rv.hscanPd(0xc7,0x08,b(0,0,f1&255,f1>>8,0,0,0,0),t4+=300);equal(rv,8,1,t4);
        rv.rearView(1);rv.hscanPd(0xc7,0x08,b(0,0,f1&255,f1>>8,0,0,0,0),t4+=10);equal(rv,8,14,t4);
        rv.rearView(0);rv.hscanPd(0xc7,0x08,b(0,0,f1&255,f1>>8,0,0,0,0),t4+=10);rv.hscanPd(0xc7,0x08,b(0,0,f1&255,f1>>8,0,0,0,0),t4+=300);
        equal(rv,8,1,t4);
        // Idle target (EMS_H12 byte5 *10 = 750 rpm). Neutral coast from 40 to 34 km/h at 750 rpm: at 34 km/h idle rpm fits
        // 6th's window, but the engine stays on its idle target while the speed has drifted > 5 %, so N stays (no 6).
        Telemetry it=new Telemetry();long t5=1000;
        it.hscanPd(0xc7,0x10,b(0,0x26,0,0,0,75,1<<2,0),t5);it.hscanPd(0xc7,0x21,b(11,0,0,0,0,0),t5);
        it.hscanPd(0xc7,0x09,b(0,0xaf,0,0x8c,0,0,0,0x10),t5);it.hscanPd(0xc7,0x09,b(0,0xaf,0,0x0c,0,0,0,0x10),t5);
        for(int v=40;v>=34;v--){it.hscanPd(0xc7,0x02,b(0,v*2,0,0),t5+=200);it.hscanPd(0xc7,0x08,b(0,0,0xb8,0x0b,0,0,0,0),t5);}
        check(Telemetry.mtGear(750,34)==6,"34 km/h at idle rpm fits 6th");
        it.hscanPd(0xc7,0x08,b(0,0,0xb8,0x0b,0,0,0,0),t5+=300);equal(it,8,0,t5);
        // A steady in-gear idle crawl: 3rd at 16 km/h sits on the idle target but keeps its speed, so it stays 3rd.
        Telemetry ic=new Telemetry();long t6=1000;
        ic.hscanPd(0xc7,0x10,b(0,0x26,0,0,0,75,1<<2,0),t6);ic.hscanPd(0xc7,0x21,b(11,0,0,0,0,0),t6);
        ic.hscanPd(0xc7,0x09,b(0,0xaf,0,0x8c,0,0,0,0x10),t6);ic.hscanPd(0xc7,0x09,b(0,0xaf,0,0x0c,0,0,0,0x10),t6);
        ic.hscanPd(0xc7,0x02,b(0,32,0,0),t6);int r3=Math.round(16*8.1f*4.467f*1.294f*4);
        for(int i=0;i<8;i++)ic.hscanPd(0xc7,0x08,b(0,0,r3&255,r3>>8,0,0,0,0),t6+=200);
        check(Math.abs(r3/4f-750)<=15,"3rd at 16 km/h runs at the idle target");equal(ic,8,3,t6);
        for(int g=1;g<=6;g++){float r=new float[]{3.308f,1.962f,1.294f,0.976f,0.778f,0.633f}[g-1];
            check(Telemetry.mtGear(40*8.1f*4.467f*r*1.07f,40)==g&&Telemetry.mtGear(40*8.1f*4.467f*r*0.93f,40)==g,"6MT gear "+g+" within +-7 %");}
        // The same EMS_H12 value is the cluster's shift recommendation: +gear with the up arrow (byte6 bit0), -gear down (bit1).
        mt.hscanPd(0xc7,0x10,b(0,0,0,0,0,0,(4<<2)|1,0),at+=1);equal(mt,Telemetry.SHIFT_HINT,4,at);
        check(Telemetry.shiftHint(mt.sample(Telemetry.SHIFT_HINT,at).value).equals("▲4"),"upshift hint ▲4");
        mt.hscanPd(0xc7,0x10,b(0,0,0,0,0,0,(2<<2)|2,0),at+=1);check(Telemetry.shiftHint(mt.sample(Telemetry.SHIFT_HINT,at).value).equals("▼2"),"downshift hint ▼2");
        mt.hscanPd(0xc7,0x10,b(0,0,0,0,0,0,1<<2,0),at+=1);check(!mt.sample(Telemetry.SHIFT_HINT,at).valid,"no arrow: no hint");
        check(Telemetry.shiftHint(Float.NaN).isEmpty(),"unknown hint draws nothing");
        mt=new Telemetry();  // 7DCT: EMS gear 0, the TCU gear stands
        mt.hscanPd(0xc7,0x1f,b(0,0xa0,0x30,0,0,0,0,0),1000);mt.hscanPd(0xc7,0x10,b(0,0x26,0,0,0,0,0,0),1001);equal(mt,8,3,1001);
        // CLU12 odometer slot 0x03: 24-bit LE * 0.1 km (opendbc) -> 103374.9 km.
        t.hscanPd(0xc7,0x03,b(0x15,0xc6,0x0f,0x00),1000);equal(t,Telemetry.ODOMETER,103374.9f,1000);
        // EMS11 TQI engine load (byte4 *0.390625) and ESP12 yaw rate (bytes5-6 13bit) within the confirmed frames.
        t.hscanPd(0xc7,0x08,b(5,11,0xca,9,0xb8,10,0,0x7f),1000);equal(t,Telemetry.ENGINE_LOAD,71.875f,1000);
        t.hscanPd(0xc7,0x08,b(5,11,0xca,9,0xff,10,0,0x7f),1001);check(!t.sample(Telemetry.ENGINE_LOAD,1001).valid,"load sentinel withheld");
        t.hscanPd(0xc7,0x13,b(0xf3,0x43,0x82,0x30,0x00,0x00,0x10,0x39),1002);equal(t,Telemetry.YAW,4096*0.01f-40.95f,1002);
        t.hscanPd(0xc7,0x13,b(0xf3,0x43,0x82,0x30,0x00,0x00,0x50,0x39),1003);check(!t.sample(Telemetry.YAW,1003).valid,"yaw diag bit withholds");
        // TPMS slot 0x20: unknown until byte1 flag is 1, then four psi values.
        t.hscanPd(0xc7,0x20,b(0,0,0xff,0xff,0xff,0xff),1000);
        for(int i=0;i<4;i++)check(!t.sample(Telemetry.TPMS_FL+i,1000).valid,"TPMS unknown before sensors report");
        t.hscanPd(0xc7,0x20,b(0,1,0x21,0x22,0x21,0x22),1001);
        equal(t,Telemetry.TPMS_FL,33,1001);equal(t,Telemetry.TPMS_FR,34,1001);equal(t,Telemetry.TPMS_RL,33,1001);equal(t,Telemetry.TPMS_RR,34,1001);
        t.hscanPd(0xc7,0x20,b(0,1,0x21,0xff,0x21,0x22),1002);check(!t.sample(Telemetry.TPMS_FR,1002).valid&&t.sample(Telemetry.TPMS_FL,1002).valid,"per-tire sentinel withheld independently");
        // Intake air temperature slot 0x0b byte2: 0x6e -> 34.5C, 0x66 -> 28.5C, 0xff withheld.
        t.hscanPd(0xc7,0x0b,b(0,0,0x6e,0x9d,0xd4,9,0x9d,4),1000);equal(t,Telemetry.INTAKE,34.5f,1000);
        t.hscanPd(0xc7,0x0b,b(0,0,0x66,0x9d,0xd4,9,0x9d,4),1001);equal(t,Telemetry.INTAKE,28.5f,1001);
        t.hscanPd(0xc7,0x0b,b(0,0,0xff,0x9d,0xd4,9,0x9d,4),1002);check(!t.sample(Telemetry.INTAKE,1002).valid,"intake sentinel withheld");
        // EMS12 slot 0x09: coolant byte1 (0x95 -> 63.75 C at warm-up start, 0xb0 -> 84 C) and pedal byte6.
        // The oil slot (1) mirrors the same head coolant sensor: this engine has no oil temperature sensor.
        t.hscanPd(0xc7,0x09,b(0xe3,0x95,0x80,0x8c,0x11,0x24,0x00,0x10),1000);equal(t,2,63.75f,1000);equal(t,9,0,1000);
        t.hscanPd(0xc7,0x09,b(0xe3,0xb0,0x80,0x8c,0x11,0x24,0x94,0x10),1001);equal(t,2,84f,1001);equal(t,9,57.8125f,1001);equal(t,1,84f,1001);
        t.hscanPd(0xc7,0x09,b(0xe3,0xff,0x80,0x8c,0x11,0x24,0xff,0x10),1002);check(!t.sample(2,1002).valid&&!t.sample(9,1002).valid&&!t.sample(1,1002).valid,"coolant/pedal sentinels withheld");
        // EMS19 slot 0x0e boost: withheld until EMS14 baro (0x0a byte0 0xd8 -> 748.7 mmHg) arrives, then gauge bar.
        Telemetry u=new Telemetry();
        u.hscanPd(0xc7,0x0e,b(0x1d,0x1d,0x39,0x2e,0,0,0x80,0),1000);check(!u.sample(Telemetry.BOOST,1000).valid,"boost needs baro first");
        u.hscanPd(0xc7,0x0a,b(0xd8,0x80,0,0x8e,0,0,0,0),1001);
        float baro=(27*10.731613f+458.98f)*1.33322f;
        u.hscanPd(0xc7,0x0e,b(0x1d,0x1d,0x39,0x2e,0,0,0x80,0),1002);equal(u,Telemetry.BOOST,(739*1.322f-baro)/1000f,1002);
        check(Math.abs(u.sample(Telemetry.BOOST,1002).value+0.02f)<0.01f,"idle boost about -0.02 bar");
        u.hscanPd(0xc7,0x0e,b(0x1d,0x1d,0x50,0x59,0,0,0x80,0),1003);check(u.sample(Telemetry.BOOST,1003).value<0,"idle to +0.89 in one sample is a lone jump: dropped");
        u.hscanPd(0xc7,0x0e,b(0x1d,0x1d,0x50,0x59,0,0,0x80,0),1004);check(u.sample(Telemetry.BOOST,1004).value>0.8f,"1885 hPa drive peak is about +0.89 bar once two samples agree");
        u.hscanPd(0xc7,0x0e,b(0x1d,0x1d,0x50,0xda,0,0,0x80,0),1005);u.hscanPd(0xc7,0x0e,b(0x1d,0x1d,0x50,0xda,0,0,0x80,0),1006);
        check(Math.abs(u.sample(Telemetry.BOOST,1006).value-0.89f)<0.01f,"3.62 bar (in-car lift-off MAX) is never shown, even twice");
        u.hscanPd(0xc7,0x0e,b(0x1d,0x1d,0xc0,0x8d,0,0,0x80,0),1007);check(u.sample(Telemetry.BOOST,1007).value<1f,"lone rise to +2.0 bar dropped");
        u.hscanPd(0xc7,0x0e,b(0x1d,0x1d,0xd0,0x7a,0,0,0x80,0),1008);check(u.sample(Telemetry.BOOST,1008).value>1.5f,"+0.7 bar rise to +1.6 (hard spool) shown");
        u.hscanPd(0xc7,0x0e,b(0x1d,0x1d,0x39,0x2e,0,0,0x80,0),1009);check(u.sample(Telemetry.BOOST,1009).value<0,"lift-off fall from +1.6 to idle shown at once");
        u.hscanPd(0xc7,0x0e,b(0x1d,0x1d,0x30,0x55,0,0,0x80,0),1010);check(u.sample(Telemetry.BOOST,1010).value<0,"+0.82 bar in one sample from idle held");
        u.hscanPd(0xc7,0x0e,b(0x1d,0x1d,0x30,0x55,0,0,0x80,0),1011);check(Math.abs(u.sample(Telemetry.BOOST,1011).value-0.80f)<0.01f,"and shown once the next sample agrees");
        u.hscanPd(0xc7,0x0e,b(0x1d,0x1d,0x39,0x2e,0,0,0x80,0),2500);check(u.sample(Telemetry.BOOST,2500).value<0,"after a 1 s gap any in-range value is taken");
        // CLU13 drive mode (slot 0x04 byte2 &3) and EPB11 park brake (slot 0x12 EPB_STATUS) - 10/30 tagged captures.
        int[][] mode={{0x00,0},{0x03,3},{0x01,1}};
        for(int[] m:mode){t.hscanPd(0xc7,0x04,b(0x80,0x19,m[0],0x11,0x05,0,0,0x78),3000);equal(t,Telemetry.DRIVE_MODE,m[1],3000);}
        check(Telemetry.driveModeLabel(0).equals("NORMAL")&&Telemetry.driveModeLabel(1).equals("SPORT")&&Telemetry.driveModeLabel(3).equals("ECO"),"drive mode labels");
        t.hscanPd(0xc7,0x04,b(0x80,0x19,0x02,0x11,0x05,0,0,0x78),3001);check(!t.sample(Telemetry.DRIVE_MODE,3001).valid,"unseen drive mode code withheld");
        t.hscanPd(0xc7,0x12,b(0x01,0x00,0x10,0x21,0x8d,0x02,0x3c),3000);equal(t,Telemetry.PARK_BRAKE,1,3000);
        check("ON".equals(Telemetry.label(Telemetry.PARK_BRAKE,1)),"park brake label");
        t.hscanPd(0xc7,0x12,b(0x00,0x00,0x08,0x21,0x00,0x00,0x3c),3001);equal(t,Telemetry.PARK_BRAKE,0,3001);
        t.hscanPd(0xc7,0x12,b(0x00,0x00,0x18,0x21,0x00,0x00,0x3c),3002);check(!t.sample(Telemetry.PARK_BRAKE,3002).valid,"unseen EPB state withheld");
        // Added 10/30: TPS, torque pair, range, trip average, fuel rate (bytes from the 10/29-30 logs).
        t.hscanPd(0xc7,0x09,b(0,0xb0,0,0x8c,0x11,0x24,0x10,0x10),3100);equal(t,Telemetry.THROTTLE_POS,0x24*0.4694836f-15.0234742f,3100);
        t.hscanPd(0xc7,0x08,b(0,0x95,0x40,0x0c,0x8f,0x07,0x30,0),3100);equal(t,Telemetry.TORQUE_CORR,0x95*0.390625f,3100);equal(t,Telemetry.TORQUE_FRICTION,0x07*0.390625f,3100);
        t.hscanPd(0xc7,0x04,b(0x00,0x19,0x01,0x10,0x05,0,0,0x38),3100);equal(t,Telemetry.RANGE,272,3100);equal(t,Telemetry.AVG_FUEL,10.0f,3100);
        t.hscanPd(0xc7,0x04,b(0xc0,0xff,0x00,0xff,0x07,0,0,0x38),3101);check(!t.sample(Telemetry.AVG_FUEL,3101).valid&&!t.sample(Telemetry.RANGE,3101).valid,"0x3ff average / range withheld");
        t.hscanPd(0xc7,0x21,b(0x0b,0x00,0x6f,0,0,0),3100);equal(t,Telemetry.FUEL_RATE,11*0.04608f,3100);
        check(Math.abs(t.sample(Telemetry.FUEL_RATE,3100).value-0.51f)<0.01f,"idle fuel about 0.5 L/h");
        // EMS16 spark timing, TCS13 DriverBraking, MDPS11 driver torque (bytes from the 10/29 drive log).
        t.hscanPd(0xc7,0x0c,b(0x01,0x4d,0x4f,0x30,0xff,0xde,0xa7,0x4a),3200);equal(t,Telemetry.SPARK,27.0f,3200);
        t.hscanPd(0xc7,0x0c,b(0x01,0x4d,0x4f,0x30,0xff,0xde,0xff,0x4a),3201);check(!t.sample(Telemetry.SPARK,3201).valid,"spark sentinel withheld");
        t.hscanPd(0xc7,0x1b,b(0x00,0xa0,0,0,0x6c,0x24,0x9d,0x80),3200);equal(t,Telemetry.BRAKE_SWITCH,1,3200);
        t.hscanPd(0xc7,0x1b,b(0x00,0xa0,0,0,0x6c,0x24,0x1d,0x80),3201);equal(t,Telemetry.BRAKE_SWITCH,0,3201);
        t.hscanPd(0xc7,0x17,b(0x80,0x70,0x44,0,0,0x13,0x4c,0x05),3200);equal(t,Telemetry.STEER_TORQUE,2190*0.01f-20.48f,3200);
        // CGW1 body: driver door (byte1 bit0) and wiper speed (byte3) from the 10/30 action-tagged captures.
        t.hscanPd(0xc7,0x01,b(0x03,0x04,0x61,0x00,0,0,0,0),3300);equal(t,Telemetry.DOOR_DRIVER,0,3300);equal(t,Telemetry.WIPER,0,3300);
        t.hscanPd(0xc7,0x01,b(0x03,0x05,0x61,0x00,0,0,0,0),3301);equal(t,Telemetry.DOOR_DRIVER,1,3301);
        t.hscanPd(0xc7,0x01,b(0x03,0x04,0x61,0x01,0,0,0,0),3302);equal(t,Telemetry.WIPER,1,3302);
        t.hscanPd(0xc7,0x01,b(0x03,0x04,0x61,0x02,0,0,0,0),3303);equal(t,Telemetry.WIPER,2,3303);
        t.hscanPd(0xc7,0x01,b(0x03,0x04,0x61,0x81,0xe0,0,0,0),3304);equal(t,Telemetry.WIPER,1,3304);
        equal(t,Telemetry.DOOR_PASSENGER,0,3304);equal(t,Telemetry.TRUNK,0,3304);equal(t,Telemetry.HOOD,0,3304);
        t.hscanPd(0xc7,0x01,b(0x03,0x14,0x63,0x80,0xe8,0,0,0),3305);equal(t,Telemetry.DOOR_PASSENGER,1,3305);
        equal(t,Telemetry.TRUNK,1,3305);equal(t,Telemetry.HOOD,1,3305);equal(t,Telemetry.DOOR_DRIVER,0,3305);equal(t,Telemetry.WIPER,0,3305);
        check("OPEN".equals(Telemetry.label(Telemetry.HOOD,1))&&"CLOSED".equals(Telemetry.label(Telemetry.DOOR_DRIVER,0))&&"HIGH".equals(Telemetry.label(Telemetry.WIPER,2)),"closure and wiper labels");
        // Turn signals (action-tagged): CGW1 byte2 bit3 left, byte7 bit6 right.
        t.hscanPd(0xc7,0x01,b(0x03,0x04,0x61,0x00,0,0,0,0x00),3306);equal(t,Telemetry.TURN_LEFT,0,3306);equal(t,Telemetry.TURN_RIGHT,0,3306);
        t.hscanPd(0xc7,0x01,b(0x03,0x04,0x69,0x00,0,0,0,0x00),3307);equal(t,Telemetry.TURN_LEFT,1,3307);equal(t,Telemetry.TURN_RIGHT,0,3307);
        t.hscanPd(0xc7,0x01,b(0x03,0x04,0x61,0x00,0,0,0,0x40),3308);equal(t,Telemetry.TURN_RIGHT,1,3308);equal(t,Telemetry.TURN_LEFT,0,3308);
        check("ON".equals(Telemetry.label(Telemetry.TURN_LEFT,1))&&"OFF".equals(Telemetry.label(Telemetry.TURN_RIGHT,0)),"turn-signal labels");
        // JSN-only slots are not decoded by the PD path.
        t.hscanPd(0xc7,0x07,b(0,0,74,64,31,0,0,0),1000);check(!t.sample(Telemetry.BAT_SOC,1000).seen,"JSN BAT11 slot ignored on PD");
        check(Telemetry.LABELS.length==Telemetry.UNITS.length&&Telemetry.LABELS.length==Telemetry.SHIFT_HINT+1,"label/unit tables cover the added signals");
        System.out.println("PASS "+checks+" i30 PD HSCAN model checks");
    }
}
