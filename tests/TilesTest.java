import com.veloster.vehicle.Telemetry;
import com.veloster.vehicle.Tiles;
import com.veloster.vehicle.VehicleProfile;

public final class TilesTest {
    private static int checks;
    private static void check(boolean ok,String what){checks++;if(!ok)throw new AssertionError(what);}
    public static void main(String[] args){
        VehicleProfile[] cars={VehicleProfile.PETROL_DCT,VehicleProfile.PETROL_MT,VehicleProfile.DIESEL_DCT};
        // every Telemetry signal sits in exactly one group, and so does every several-value entry
        for(int s=0;s<Telemetry.LABELS.length;s++)check(Tiles.groupOf(s)>=0,"signal "+s+" ("+Telemetry.LABELS[s]+") is in a group");
        for(int s=Tiles.TIRES;s<=Tiles.CORNER_MAX_LAT;s++)check(Tiles.groupOf(s)>=0,"entry "+s+" is in a group");
        for(VehicleProfile p:cars)for(int s=0;s<Telemetry.LABELS.length;s++)if(Tiles.available(s,p)){
            int n=0;for(int g=0;g<Tiles.GROUPS.length;g++)if(contains(Tiles.group(g,p,true),s))n++;
            check(n==1,"signal "+s+" listed once on "+p.label);}
        // what each car does not have stays out of its lists
        check(Tiles.groups(VehicleProfile.PETROL_DCT,true).length==Tiles.GROUPS.length-1,"a petrol has no DPF group");
        check(Tiles.groups(VehicleProfile.DIESEL_DCT,true).length==Tiles.GROUPS.length,"a diesel has it");
        check(!contains(Tiles.group(3,VehicleProfile.PETROL_DCT,true),Telemetry.CLUTCH),"no clutch pedal on a DCT");
        check(contains(Tiles.group(3,VehicleProfile.PETROL_MT,true),Telemetry.CLUTCH),"a clutch pedal on a 6MT");
        check(!contains(Tiles.group(1,VehicleProfile.PETROL_MT,true),Tiles.CLUTCH),"no DCT clutch temperatures on a 6MT");
        check(!contains(Tiles.group(0,VehicleProfile.DIESEL_DCT,true),Telemetry.SPARK),"no ignition timing on a diesel");
        check(!contains(Tiles.group(6,VehicleProfile.PETROL_DCT,false),Tiles.TIRES)&&contains(Tiles.group(6,VehicleProfile.PETROL_DCT,true),Tiles.TIRES),
            "gauges and graphs get single signals only");
        for(VehicleProfile p:cars)for(int l=0;l<Tiles.KEYS.length;l++){
            int[] d=Tiles.defaults(l,p);
            check(d.length==Tiles.RECTS[l].length&&d.length==Tiles.picked[l].length,"one default per tile ("+Tiles.KEYS[l]+")");
            for(int s:d)check(Tiles.available(s,p),"default "+s+" on "+Tiles.KEYS[l]+" exists on this car");
            float[][] r=Tiles.RECTS[l];
            for(int i=0;i<r.length;i++)for(int j=i+1;j<r.length;j++)
                check(r[i][2]<=r[j][0]||r[j][2]<=r[i][0]||r[i][3]<=r[j][1]||r[j][3]<=r[i][1],Tiles.KEYS[l]+" tiles "+i+" and "+j+" do not overlap");
            for(int i=0;i<r.length;i++)check(Tiles.hit(l,(r[i][0]+r[i][2])/2,(r[i][1]+r[i][3])/2)==i,"the middle of tile "+i+" hits it");
        }
        check(Tiles.defaults(Tiles.CLASSIC,VehicleProfile.PETROL_DCT)[4]==Tiles.TIRES&&Tiles.defaults(Tiles.CLASSIC,VehicleProfile.PETROL_DCT)[7]==Tiles.CLUTCH,
            "classic right: the four tyres in one tile, the DCT clutches in another");
        check(Tiles.defaults(Tiles.CLASSIC,VehicleProfile.PETROL_MT)[7]==Telemetry.ENGINE_LOAD,"a 6MT gets engine load there");
        check(Tiles.defaults(Tiles.RACE,VehicleProfile.DIESEL_DCT)[3]==Tiles.DPF,"the race page keeps the diesel's DPF cell");
        // a pick shows; a pick this car does not have falls back to the default
        Tiles.picked[Tiles.RACE][0]=Telemetry.CAT_TEMP;check(Tiles.signal(Tiles.RACE,0,VehicleProfile.PETROL_DCT)==Telemetry.CAT_TEMP,"a pick shows");
        Tiles.picked[Tiles.RACE][0]=Telemetry.DPF_DP;check(Tiles.signal(Tiles.RACE,0,VehicleProfile.PETROL_DCT)==2,"a diesel pick on a petrol falls back");
        Tiles.picked[Tiles.RACE][0]=-1;
        // text, levels, bars
        check("—".equals(Tiles.text(2,Float.NaN))&&"85".equals(Tiles.text(2,85.4f))&&"+0.35".equals(Tiles.text(Telemetry.BOOST,0.35f)),"numbers");
        check("ON".equals(Tiles.text(Telemetry.PARK_BRAKE,1))&&"13.8".equals(Tiles.text(Telemetry.BAT_VOLTAGE,13.8f)),"words and decimals");
        check(Tiles.level(2,105)==1&&Tiles.level(2,112)==2&&Tiles.level(Telemetry.TPMS_FL,28)==1&&Tiles.level(Telemetry.CLUTCH_ODD,190)==2,"warning levels");
        check(Math.abs(Tiles.fraction(2,80)-0.5f)<1e-6&&Float.isNaN(Tiles.fraction(Telemetry.ODOMETER,1000)),"bars");
        System.out.println("PASS "+checks+" tile checks");
    }
    private static boolean contains(int[] a,int v){for(int x:a)if(x==v)return true;return false;}
}
