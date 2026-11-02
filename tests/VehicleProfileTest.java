import com.veloster.vehicle.Telemetry;
import com.veloster.vehicle.VehicleProfile;

/** Profile constants and the auto-detection from received signals. Synthetic only; no private capture. */
public final class VehicleProfileTest {
    static int checks;
    static void check(boolean ok,String m){checks++;if(!ok)throw new AssertionError(m);}
    static byte[] b(int... v){byte[] r=new byte[v.length];for(int i=0;i<v.length;i++)r[i]=(byte)v[i];return r;}
    public static void main(String[] a){
        check(VehicleProfile.PETROL_DCT.topGear==7 && VehicleProfile.PETROL_DCT.hasSpark
            && VehicleProfile.PETROL_DCT.maxRpm==8000 && VehicleProfile.PETROL_DCT.redline==6500,"petrol dct constants");
        check(VehicleProfile.DIESEL_DCT.topGear==7 && !VehicleProfile.DIESEL_DCT.hasSpark
            && VehicleProfile.DIESEL_DCT.maxRpm==5500 && VehicleProfile.DIESEL_DCT.redline==4600,"diesel dct constants");
        check(VehicleProfile.PETROL_MT.topGear==6 && VehicleProfile.PETROL_MT.hasSpark,"petrol 6mt constants");
        check(VehicleProfile.DIESEL_MT.topGear==6 && !VehicleProfile.DIESEL_MT.hasSpark,"diesel 6mt constants");
        check(VehicleProfile.of(VehicleProfile.Fuel.DIESEL,VehicleProfile.Trans.DCT)==VehicleProfile.DIESEL_DCT,"of() returns the shared instance");

        Telemetry t=new Telemetry();
        check(t.profile()==VehicleProfile.PETROL_DCT,"defaults to the verified petrol 7DCT");
        // rpm only, in the diesel band, no spark and no shift lever -> diesel 6MT
        t.hscanPd(0xc7,0x08,b(5,11,0xe0,0x2e,11,10,0,0x7f),1000);   // EMS11 rpm 3000
        check(t.sample(0,1000).valid && Math.abs(t.sample(0,1000).value-3000)<1,"rpm 3000 decoded");
        VehicleProfile d=t.profile();
        check(d.fuel==VehicleProfile.Fuel.DIESEL && d.trans==VehicleProfile.Trans.MT,"no spark + no lever + low rpm -> diesel 6MT");
        // a 6MT has no lever: its LVR12 slot sits at 0 (P) whatever gear is in, so P alone is no evidence of a DCT
        t.hscanPd(0xc7,0x1d,b(0,0,0,0,0,0,0,0),1001);              // CF_Lvr_Gear = 0 (P)
        check(t.profile().trans==VehicleProfile.Trans.MT,"a lever stuck at P does not mark a DCT");
        // the gear box shows the gear number, never P, while the 6MT changes gears
        for(int g=1;g<=6;g++)check(Telemetry.gearText(g,0,VehicleProfile.PETROL_MT).equals(String.valueOf(g)),"6MT gear "+g+" not P");
        check(Telemetry.gearText(3,0,VehicleProfile.PETROL_DCT).equals("3"),"a forward gear beats a P selector before the MT is inferred");
        check(Telemetry.gearText(Float.NaN,0,VehicleProfile.PETROL_MT).equals("—"),"6MT without a gear number is not P");
        check(Telemetry.selector(0,4,VehicleProfile.PETROL_DCT)==-1 && Telemetry.selector(0,Float.NaN,VehicleProfile.PETROL_MT)==-1,"P selector ignored");
        // the DCT still shows its lever
        check(Telemetry.gearText(Float.NaN,0,VehicleProfile.PETROL_DCT).equals("P"),"DCT parked -> P");
        check(Telemetry.gearText(Float.NaN,1,VehicleProfile.PETROL_DCT).equals("R"),"DCT reverse -> R");
        check(Telemetry.gearText(4,3,VehicleProfile.PETROL_DCT).equals("4"),"DCT D -> gear number");
        check(Telemetry.gearText(3,4,VehicleProfile.PETROL_DCT).equals("M3"),"DCT manual gate -> M3");
        // a shift lever (LVR12) marks the transmission as a DCT
        t.hscanPd(0xc7,0x1d,b(0,5,0,0,0,0,0,0),1002);              // CF_Lvr_Gear = 5 (D)
        check(t.profile().trans==VehicleProfile.Trans.DCT,"a shift lever -> DCT");
        // ignition timing (EMS16) marks the engine as petrol
        t.hscanPd(0xc7,0x0c,b(0,0,0,0,0,0,0x66,0),1003);          // SPK_TIME present
        check(t.profile().fuel==VehicleProfile.Fuel.PETROL,"ignition timing -> petrol");
        // 7DCT: the LVR12 checksum byte (0x1d b7) cycles every frame even parked in P (10/29 idle log) -> DCT at once,
        // even when revved past 2000 rpm in P
        Telemetry c=new Telemetry();
        c.hscanPd(0xc7,0x1d,b(0,0xc0,0x71,0xff,0,0,0,0x68),1000);c.hscanPd(0xc7,0x1d,b(0,0xc0,0x71,0xff,0,0,0,0xe0),1001);
        c.hscanPd(0xc7,0x08,b(5,11,0xe0,0x2e,11,10,0,0x7f),1002);
        check(c.profile().trans==VehicleProfile.Trans.DCT,"a live lever checksum -> DCT, even revved in P");
        // 6MT: no lever, the slot stays frozen -> MT before any rpm evidence
        Telemetry m=new Telemetry();
        for(int i=0;i<31;i++)m.hscanPd(0xc7,0x1d,b(0,0,0,0,0,0,0,0),1000+i);
        check(m.profile().trans==VehicleProfile.Trans.MT,"frozen lever slot -> 6MT");
        // 6MT: a gear from the EMS (cluster shift indicator) -> MT
        Telemetry e=new Telemetry();e.hscanPd(0xc7,0x10,b(0,0,0,0,0,0,3<<2,0),1000);
        check(e.profile().trans==VehicleProfile.Trans.MT,"EMS cluster gear -> 6MT");
        System.out.println("PASS "+checks+" vehicle profile checks");
    }
}
