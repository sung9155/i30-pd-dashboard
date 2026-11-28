package com.veloster.vehicle;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 정비: whether the car is used hard (Hyundai's 가혹 conditions the app can see), and which items to announce once.
 * 자동 judges from MaintLog since the oil change: short starts ≥ 50 % (8 km, 16 km below 0 °C), idle ≥ 30 % of engine time,
 * average moving speed < 20 km/h or ≥ 2 stops a km, ≥ 20 % of the distance at 140 km/h or more, ≥ 15 % of engine time at
 * high load. When the app saw under 30 % of the odometer's distance since then, it does not judge: 일반 intervals.
 * Reminders: an item is told once on entering 다가옴 (≤ 1,000 km or ≤ 14 days; inspection ≤ 30 days), once on turning
 * 지남, then every 7 days while it stays past; a new record starts it over. No Android: host-tested.
 */
public final class MaintJudge {
    private MaintJudge(){}

    public enum Mode { AUTO, NORMAL, SEVERE }
    public static Mode mode(MaintItems.Store s){String v=s==null?"":s.get("maint_mode");return "normal".equals(v)?Mode.NORMAL:"severe".equals(v)?Mode.SEVERE:Mode.AUTO;}
    public static void setMode(MaintItems.Store s,Mode m){s.put("maint_mode",m.name().toLowerCase(Locale.US));}

    public static final class Verdict { public boolean severe,insufficient;public String reason=""; }
    static final float SHORT=0.5f,IDLE=0.3f,SLOW_KMH=20,STOPS_PER_KM=2,FAST_SHARE=0.2f,LOAD_SHARE=0.15f,SEEN_SHARE=0.3f,MIN_SPAN_KM=50;

    public static Verdict judge(Mode m,MaintLog.Usage u,float odoKm){
        Verdict v=new Verdict();
        if(m==Mode.NORMAL)return v;
        if(m==Mode.SEVERE){v.severe=true;return v;}
        float span=odoKm-u.odoAtOil;
        if(Float.isNaN(span)||span<MIN_SPAN_KM||u.seenKm<SEEN_SHARE*span){v.insufficient=true;v.reason="근거 부족";return v;}
        int finished=u.starts-1;
        if(finished>0&&u.shortStarts>=SHORT*finished)return severe(v,"짧은 주행 "+pct(u.shortStarts,finished));
        if(u.engineS>0&&u.idleS>=IDLE*u.engineS)return severe(v,"공회전 "+pct(u.idleS,u.engineS));
        if(u.movingS>0&&u.movingKm/(u.movingS/3600)<SLOW_KMH)return severe(v,String.format(Locale.US,"저속 · 정체 평균 %.0f km/h",u.movingKm/(u.movingS/3600)));
        if(u.movingKm>=1&&u.stops>=STOPS_PER_KM*u.movingKm)return severe(v,String.format(Locale.US,"정지 %.1f회/km",u.stops/u.movingKm));
        if(u.movingKm>0&&u.highSpeedKm>=FAST_SHARE*u.movingKm)return severe(v,"고속 "+pct(u.highSpeedKm,u.movingKm));
        if(u.engineS>0&&u.highLoadS>=LOAD_SHARE*u.engineS)return severe(v,"고부하 "+pct(u.highLoadS,u.engineS));
        return v;
    }
    private static Verdict severe(Verdict v,String why){v.severe=true;v.reason=why;return v;}
    private static String pct(double part,double whole){return (int)Math.floor(100*part/whole)+" %";}

    public enum Band { NONE, SOON, OVERDUE }
    public static Band band(MaintItems.Id id,MaintItems.Left l){
        if(l==null||!l.recorded)return Band.NONE;
        if(l.overdue)return Band.OVERDUE;
        boolean hasDays=l.days!=Integer.MIN_VALUE;
        if(id==MaintItems.Id.INSPECTION)return hasDays&&l.days<=30?Band.SOON:Band.NONE;
        if(!Float.isNaN(l.km)&&l.km<=1000||hasDays&&l.days<=14)return Band.SOON;
        return Band.NONE;
    }
    private static String key(MaintItems.Id id,String f){return "maint_"+id.name().toLowerCase(Locale.US)+"_"+f;}
    private static int num(String s){try{return s==null||s.length()==0?0:Integer.parseInt(s.trim());}catch(NumberFormatException e){return 0;}}

    /** The items to announce now (see the class comment); remembers what it told in the store. */
    public static List<MaintItems.Id> due(Map<MaintItems.Id,MaintItems.Left> lefts,MaintItems.Store s,int today){
        List<MaintItems.Id> out=new ArrayList<MaintItems.Id>();
        for(Map.Entry<MaintItems.Id,MaintItems.Left> e:lefts.entrySet()){
            MaintItems.Id id=e.getKey();Band b=band(id,e.getValue());int was=num(s.get(key(id,"band"))),told=num(s.get(key(id,"told")));
            boolean tell=b!=Band.NONE&&(b.ordinal()!=was||b==Band.OVERDUE&&told>0&&MaintItems.daysBetween(told,today)>=7);
            if(b.ordinal()!=was)s.put(key(id,"band"),String.valueOf(b.ordinal()));
            if(tell){s.put(key(id,"told"),String.valueOf(today));out.add(id);}
        }
        return out;
    }
    public static void recorded(MaintItems.Store s,MaintItems.Id id){s.put(key(id,"band"),"0");s.put(key(id,"told"),"0");}
}
