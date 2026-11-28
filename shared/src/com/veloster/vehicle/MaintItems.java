package com.veloster.vehicle;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * 정비: what each car keeps (by fuel and gearbox), the default intervals (estimates from the 1.6 T-GDi manuals and
 * Hyundai's service tips: the i30 PD's own table was not found, so every interval can be changed), the owner's service
 * records, and the km / time left. Dates are yyyymmdd ints. km and time both count; the earlier one is what is left.
 * An odometer missing, 0 or below the record gives no km (never a made-up number). No Android: host-tested.
 */
public final class MaintItems {
    private MaintItems(){}

    /** Where records live (pit_vehicle_ui in the app, so the settings backup carries them). A missing key reads "". */
    public interface Store { String get(String key); void put(String key,String value); }

    public enum Id { OIL, AIR, CABIN, SPARK, FUEL_FILTER, DCT_OIL, MT_OIL, BRAKE_FLUID, PADS_FRONT, PADS_REAR, COOLANT, WIPER,
        TIRE_ROTATE, TIRE_REPLACE, TIRE_AIR, ALIGNMENT, BATTERY, INSPECTION }

    /** {normal km, normal months, severe km, severe months}; 0 = none. Coolant's are the later interval (see left). */
    private static final int[][] DEFAULTS={
        {10000,12,5000,6},      // OIL
        {40000,0,20000,0},      // AIR
        {10000,12,5000,6},      // CABIN
        {75000,0,40000,0},      // SPARK
        {30000,0,15000,0},      // FUEL_FILTER
        {60000,36,40000,24},    // DCT_OIL
        {100000,0,60000,0},     // MT_OIL
        {0,24,0,12},            // BRAKE_FLUID
        {20000,0,10000,0},      // PADS_FRONT (check)
        {20000,0,10000,0},      // PADS_REAR (check)
        {40000,24,40000,24},    // COOLANT (after the first change)
        {0,12,0,6},             // WIPER
        {10000,0,10000,0},      // TIRE_ROTATE
        {0,0,0,0},              // TIRE_REPLACE (DOT age instead)
        {0,0,0,0},              // TIRE_AIR (date only)
        {0,0,0,0},              // ALIGNMENT (km since only)
        {0,0,0,0},              // BATTERY (age instead)
        {0,0,0,0}};             // INSPECTION (a due date)
    static final int COOLANT_FIRST_KM=200000,COOLANT_FIRST_MO=120;

    public static List<Id> shown(VehicleProfile p){
        List<Id> out=new ArrayList<Id>();
        for(Id id:Id.values()){
            if(id==Id.SPARK&&p.fuel!=VehicleProfile.Fuel.PETROL)continue;
            if(id==Id.FUEL_FILTER&&p.fuel!=VehicleProfile.Fuel.DIESEL)continue;
            if(id==Id.DCT_OIL&&p.trans!=VehicleProfile.Trans.DCT)continue;
            if(id==Id.MT_OIL&&p.trans!=VehicleProfile.Trans.MT)continue;
            out.add(id);}
        return out;
    }
    public static int defaultKm(Id id,boolean severe){return DEFAULTS[id.ordinal()][severe?2:0];}
    public static int defaultMonths(Id id,boolean severe){return DEFAULTS[id.ordinal()][severe?3:1];}
    /** True when 가혹 changes this item's default interval. */
    public static boolean hasSevere(Id id){int[] d=DEFAULTS[id.ordinal()];return d[0]!=d[2]||d[1]!=d[3];}

    /** One record. -1 / 0 = not given; everyKm / everyMo -1 = the default. dot = the tyre's DOT week and year (wwyy). */
    public static final class Rec { public int km=-1,date=0,everyKm=-1,everyMo=-1,dot=0;
        public boolean any(){return km>=0||date>0;} }

    private static String key(Id id,String f){return "maint_"+id.name().toLowerCase(Locale.US)+"_"+f;}
    private static int num(String s,int dflt){if(s==null||s.length()==0)return dflt;try{return Integer.parseInt(s.trim());}catch(NumberFormatException e){return dflt;}}
    public static Rec load(Store s,Id id){Rec r=new Rec();if(s==null)return r;
        r.km=num(s.get(key(id,"km")),-1);r.date=num(s.get(key(id,"date")),0);r.everyKm=num(s.get(key(id,"every_km")),-1);
        r.everyMo=num(s.get(key(id,"every_mo")),-1);r.dot=num(s.get(key(id,"dot")),0);return r;}
    public static void save(Store s,Id id,Rec r){
        s.put(key(id,"km"),String.valueOf(r.km));s.put(key(id,"date"),String.valueOf(r.date));s.put(key(id,"every_km"),String.valueOf(r.everyKm));
        s.put(key(id,"every_mo"),String.valueOf(r.everyMo));s.put(key(id,"dot"),String.valueOf(r.dot));}
    public static int firstRegDate(Store s){return s==null?0:num(s.get("maint_first_reg"),0);}
    public static void setFirstRegDate(Store s,int yyyymmdd){s.put("maint_first_reg",String.valueOf(yyyymmdd));}

    /** What is left. km / days: negative = past due; NaN / MIN_VALUE = not counted. since = km since the record. */
    public static final class Left { public boolean recorded,overdue,odoBad;public float km=Float.NaN,since=Float.NaN,fraction=Float.NaN;
        public int days=Integer.MIN_VALUE; }

    public static Left left(Id id,Rec r,boolean severe,float odoKm,int today,int firstReg){
        Left l=new Left();
        int baseKm=r.km,baseDate=r.date,everyKm=r.everyKm>=0?r.everyKm:defaultKm(id,severe),everyMo=r.everyMo>=0?r.everyMo:defaultMonths(id,severe);
        if(id==Id.INSPECTION){   // a due date, entered or from the first registration (4 years, then every 2)
            int due=r.date>0?r.date:firstReg>0?inspectionDue(firstReg,today):0;
            if(due==0)return l;
            l.recorded=true;l.days=daysBetween(today,due);l.overdue=l.days<0;return l;}
        if(id==Id.COOLANT&&!r.any()&&firstReg>0){baseKm=0;baseDate=firstReg;
            if(r.everyKm<0)everyKm=COOLANT_FIRST_KM;if(r.everyMo<0)everyMo=COOLANT_FIRST_MO;}
        if(baseKm<0&&baseDate<=0)return l;
        l.recorded=true;
        boolean odoOk=!Float.isNaN(odoKm)&&odoKm>0;
        if(baseKm>=0&&odoOk&&odoKm<baseKm){l.odoBad=true;odoOk=false;}
        else if(baseKm>=0&&!Float.isNaN(odoKm)&&odoKm<=0)l.odoBad=true;
        float fk=Float.NaN,fd=Float.NaN;
        if(baseKm>=0&&odoOk){l.since=odoKm-baseKm;
            if(everyKm>0){l.km=everyKm-l.since;fk=l.km/everyKm;}}
        if(baseDate>0&&everyMo>0){int due=addMonths(baseDate,everyMo);l.days=daysBetween(today,due);
            fd=l.days/(float)Math.max(1,daysBetween(baseDate,due));}
        l.overdue=!Float.isNaN(l.km)&&l.km<0||l.days!=Integer.MIN_VALUE&&l.days<0;
        float f=Float.isNaN(fk)?fd:Float.isNaN(fd)?fk:Math.min(fk,fd);
        l.fraction=Float.isNaN(f)?f:Math.max(0,Math.min(1,f));
        return l;
    }

    /** Next periodic inspection on or after today: 4 years from the first registration, then every 2. */
    static int inspectionDue(int firstReg,int today){int due=addMonths(firstReg,48);while(due<today)due=addMonths(due,24);return due;}

    public static int dotAgeMonths(int dot,int today){   // wwyy: week ww of 20yy
        int week=dot/100,yy=dot%100;if(week<1||week>53)return -1;
        GregorianCalendar c=cal(2000+yy,1,1);c.add(Calendar.DAY_OF_YEAR,(week-1)*7);
        int made=c.get(Calendar.YEAR)*10000+(c.get(Calendar.MONTH)+1)*100+c.get(Calendar.DAY_OF_MONTH);
        return ageMonths(made,today);}
    public static int ageMonths(int date,int today){
        int m=(today/10000-date/10000)*12+(today/100%100-date/100%100);if(today%100<date%100)m--;return m;}

    static GregorianCalendar cal(int y,int m,int d){GregorianCalendar c=new GregorianCalendar(TimeZone.getTimeZone("UTC"));c.clear();c.set(y,m-1,d);return c;}
    static GregorianCalendar cal(int ymd){return cal(ymd/10000,ymd/100%100,ymd%100);}
    public static int addMonths(int ymd,int months){GregorianCalendar c=cal(ymd);c.add(Calendar.MONTH,months);
        return c.get(Calendar.YEAR)*10000+(c.get(Calendar.MONTH)+1)*100+c.get(Calendar.DAY_OF_MONTH);}
    public static int daysBetween(int from,int to){return (int)Math.round((cal(to).getTimeInMillis()-cal(from).getTimeInMillis())/86400000.0);}
    public static int today(){Calendar c=Calendar.getInstance();return c.get(Calendar.YEAR)*10000+(c.get(Calendar.MONTH)+1)*100+c.get(Calendar.DAY_OF_MONTH);}
}
