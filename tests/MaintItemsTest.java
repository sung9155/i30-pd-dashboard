import com.veloster.vehicle.MaintItems;
import com.veloster.vehicle.MaintItems.Id;
import com.veloster.vehicle.VehicleProfile;
import java.util.HashMap;
import java.util.List;

/** 정비: the item catalogue per car, the default intervals, records and km / time left. */
public final class MaintItemsTest {
    static int n;
    static void check(boolean ok,String what){n++;if(!ok)throw new AssertionError(what);}
    static MaintItems.Store mem(){final HashMap<String,String> m=new HashMap<String,String>();
        return new MaintItems.Store(){public String get(String k){String v=m.get(k);return v==null?"":v;}public void put(String k,String v){m.put(k,v);}};}
    static MaintItems.Rec rec(int km,int date){MaintItems.Rec r=new MaintItems.Rec();r.km=km;r.date=date;return r;}
    public static void main(String[] a){
        List<Id> pd=MaintItems.shown(VehicleProfile.PETROL_DCT);
        check(pd.contains(Id.OIL)&&pd.contains(Id.SPARK)&&pd.contains(Id.DCT_OIL)&&!pd.contains(Id.FUEL_FILTER)&&!pd.contains(Id.MT_OIL),"petrol DCT items "+pd);
        List<Id> dd=MaintItems.shown(VehicleProfile.DIESEL_DCT);
        check(dd.contains(Id.FUEL_FILTER)&&!dd.contains(Id.SPARK),"diesel: fuel filter, no spark plugs");
        List<Id> pm=MaintItems.shown(VehicleProfile.PETROL_MT);
        check(pm.contains(Id.MT_OIL)&&!pm.contains(Id.DCT_OIL),"manual: gearbox oil, no DCT oil");

        check(MaintItems.defaultKm(Id.OIL,false)==10000&&MaintItems.defaultMonths(Id.OIL,false)==12,"oil normal 10,000 km / 12 months");
        check(MaintItems.defaultKm(Id.OIL,true)==5000&&MaintItems.defaultMonths(Id.OIL,true)==6,"oil severe 5,000 km / 6 months");
        check(MaintItems.defaultMonths(Id.BRAKE_FLUID,false)==24&&MaintItems.defaultKm(Id.TIRE_ROTATE,false)==10000&&MaintItems.defaultKm(Id.ALIGNMENT,false)==0,"brake fluid 24 months, rotation 10,000 km, alignment none");

        int none=Integer.MIN_VALUE;
        MaintItems.Left l=MaintItems.left(Id.OIL,rec(90000,20260101),false,97660,20260601,0);
        check(l.recorded&&Math.round(l.km)==2340&&l.days==214&&!l.overdue,"oil 2,340 km / 214 days left: "+l.km+" "+l.days);
        l=MaintItems.left(Id.OIL,rec(90000,20260101),false,100500,20260601,0);
        check(Math.round(l.km)==-500&&l.overdue,"oil 500 km past");
        l=MaintItems.left(Id.OIL,rec(90000,20250101),false,91000,20260115,0);
        check(l.overdue&&Math.round(l.km)==9000&&l.days<0,"the earlier of km and time: overdue by time");
        l=MaintItems.left(Id.OIL,new MaintItems.Rec(),false,97660,20260601,0);
        check(!l.recorded&&Float.isNaN(l.km)&&l.days==none&&!l.overdue,"no record: nothing left to count");
        l=MaintItems.left(Id.OIL,rec(90000,20260101),false,Float.NaN,20260601,0);
        check(Float.isNaN(l.km)&&l.days==214&&!l.odoBad,"odometer not received: km —, time still counts");
        l=MaintItems.left(Id.OIL,rec(90000,20260101),false,89000,20260601,0);
        check(l.odoBad&&Float.isNaN(l.km)&&!l.overdue,"odometer below the record: flagged, no km");
        check(l.ahead,"odometer below the record: the record is ahead (a typo there, or the odometer)");
        l=MaintItems.left(Id.OIL,rec(90000,20260101),false,0,20260601,0);
        check(l.odoBad&&Float.isNaN(l.km),"odometer 0: flagged");
        check(!l.ahead,"odometer 0: not the record's doing");

        l=MaintItems.left(Id.COOLANT,new MaintItems.Rec(),false,150000,20260601,20180301);
        check(l.recorded&&Math.round(l.km)==50000&&l.days==639,"coolant, factory fill from the first registration: 200,000 km / 120 months "+l.km+" "+l.days);
        l=MaintItems.left(Id.COOLANT,rec(150000,20260101),false,151000,20260601,20180301);
        check(Math.round(l.km)==39000,"coolant after a change: 40,000 km");
        check(l.everyKm==40000&&l.everyMo==24,"the interval counted from, after a change ("+l.everyKm+" km, "+l.everyMo+" mo)");
        l=MaintItems.left(Id.COOLANT,new MaintItems.Rec(),false,150000,20260601,20180301);
        check(l.everyKm==200000&&l.everyMo==120,"the interval counted from: the factory fill's, not the default ("+l.everyKm+" km, "+l.everyMo+" mo)");

        l=MaintItems.left(Id.INSPECTION,new MaintItems.Rec(),false,Float.NaN,20260601,20180301);
        check(l.recorded&&l.days==639,"inspection from the first registration: 4 years, then every 2 ("+l.days+")");
        l=MaintItems.left(Id.INSPECTION,rec(-1,20260701),false,Float.NaN,20260601,20180301);
        check(l.days==30,"an entered inspection date wins");

        MaintItems.Rec e=rec(90000,20260101);e.everyKm=8000;
        l=MaintItems.left(Id.OIL,e,true,97000,20260601,0);
        check(Math.round(l.km)==1000,"editedIntervalSurvivesSeverity: own 8,000 km kept under 가혹");

        l=MaintItems.left(Id.ALIGNMENT,rec(90000,20260101),false,97660,20260601,0);
        check(l.recorded&&Math.round(l.since)==7660&&Float.isNaN(l.km)&&!l.overdue,"no interval: km since only");

        check(MaintItems.dotAgeMonths(1030,20260601)==-1&&MaintItems.dotAgeMonths(5426,20260601)==-1&&MaintItems.dotAgeMonths(2226,20260601)==0,"a DOT from the future or week 54 is not a DOT; this week's is 0 months");
        check(MaintItems.kmAhead(98001,98000)&&!MaintItems.kmAhead(98000,98000.4f)&&!MaintItems.kmAhead(99000,Float.NaN)&&!MaintItems.kmAhead(-1,98000),"a typed km above the odometer is refused; unknown odometer or no km is not");
        check(Math.abs(MaintItems.dotAgeMonths(2324,20260601)-24)<=1,"DOT 2324 (week 23 of 2024) is about 24 months old: "+MaintItems.dotAgeMonths(2324,20260601));
        check(MaintItems.ageMonths(20230601,20260601)==36,"battery age 36 months");

        MaintItems.Store s=mem();MaintItems.Rec r=rec(12345,20251231);r.everyKm=7000;r.everyMo=-1;r.dot=4123;
        MaintItems.save(s,Id.TIRE_REPLACE,r);MaintItems.Rec b=MaintItems.load(s,Id.TIRE_REPLACE);
        check(b.km==12345&&b.date==20251231&&b.everyKm==7000&&b.everyMo==-1&&b.dot==4123,"save / load round trip");
        check(MaintItems.load(s,Id.OIL).km==-1&&MaintItems.load(s,Id.OIL).date==0,"missing record loads empty");
        MaintItems.setFirstRegDate(s,20180301);check(MaintItems.firstRegDate(s)==20180301,"first registration kept");
        check(MaintItems.parseDate("2026-03-01")==20260301&&MaintItems.parseDate("20260301")==20260301&&MaintItems.parseDate("2026-13-01")==0&&MaintItems.parseDate("")==0&&MaintItems.parseDate("2026-3-1")==0,"typed dates: yyyy-mm-dd or yyyymmdd only");
        check(MaintItems.label(Id.OIL).equals("엔진오일 · 필터")&&MaintItems.label(Id.INSPECTION).equals("정기검사"),"labels");
        System.out.println("PASS "+n+" maintenance item checks");
    }
}
