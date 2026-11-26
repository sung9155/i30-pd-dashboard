package com.veloster.vehicle;

import java.util.Locale;

/**
 * The stock navigation's turn-by-turn guidance, as it last sent it to the instrument cluster: the text of
 * `dumpsys com.hkmc.micom.cluster` (ClusterService keeps the last values; read by TbtReader with the DUMP permission,
 * which our platform-signed app holds). No Android here, so the parser runs on the host.
 *
 * The display types are the firmware's own names (automotive.jar Navigation$DisplayType, 48 of them). Confirmed on the
 * car (2026-11-12): "NO ROUTE GUIDANCE(COMPASS)" without a route, "DIRECTION TO DESTINATION" with a destination before
 * a turn is due, the SubInfo distance in km although it is labelled METER, minutes to go, and DestName as UTF-16LE
 * bytes after 0xf4 0x00. Not yet seen during guidance (to confirm with `carpi tbt` on a guided drive): the unit of the
 * TBT Info distance and which way Direction counts; read here as metres (km when below 100 with a fraction, like the
 * SubInfo) and as degrees clockwise from straight ahead.
 */
public final class Tbt {
    /** One reading. active = a route is being guided; maneuver = a turn or exit is announced (show the band). */
    public static final class State{
        public final boolean active,maneuver;public final String type,label;public final float direction,metres,destKm;public final int minutes;public final String dest;
        State(boolean active,boolean maneuver,String type,String label,float direction,float metres,float destKm,int minutes,String dest){
            this.active=active;this.maneuver=maneuver;this.type=type;this.label=label;this.direction=direction;this.metres=metres;this.destKm=destKm;this.minutes=minutes;this.dest=dest;}
    }
    public static final State NONE=new State(false,false,"","",Float.NaN,Float.NaN,Float.NaN,-1,"");
    private static volatile State current=NONE;private static volatile long at;
    /** The last reading, NONE when older than STALE_MS (the reader stopped or the service went away). */
    public static State current(long now){return now-at>STALE_MS?NONE:current;}
    public static void set(State s,long now){current=s;at=now;}
    static final long STALE_MS=6000;
    private Tbt(){}

    public static State parse(String dump){
        if(dump==null)return NONE;
        String nav=section(dump,"Navigation Cluster:");if(nav==null)return NONE;
        String info=section(nav,"TBT Info:"),sub=section(nav,"TBT SubInfo:");
        String type=field(info,"Display Type");if(type==null)return NONE;
        float dir=num(field(info,"Direction")),dist=num(field(info,"Distance")),dkm=num(field(sub,"distance"));int min=(int)num(field(sub,"minutes"));
        if(!Float.isNaN(dist)&&dist<100&&dist!=Math.floor(dist))dist*=1000;   // a fraction below 100: km, like the SubInfo
        // The domestic navi sends its own codes, which the firmware's table lacks: "Unknown display type(0x5b)" while
        // guiding on the car (2026-11-27, Distance in metres: 600 / 800 m, matching the SCC NextTurnDist). Until the
        // codes are matched to turns, a code with a distance counts as guidance, shown as the next guidance point.
        boolean kr=type.startsWith("Unknown display type")&&!Float.isNaN(dist)&&dist>0;
        boolean active=kr||!(type.startsWith("NO ROUTE")||type.equals("NO INFO")||type.equals("NO SYMBOL")||type.startsWith("Unknown")||type.equals("OFF MAP")||type.equals("OFF ROAD"));
        String label=kr?"다음 안내":label(type,dir);
        boolean maneuver=active&&label.length()>0&&!type.startsWith("DIRECTION")&&!type.startsWith("CALC")&&!type.startsWith("RECALC");
        return new State(active,maneuver,type,label,dir,dist,dkm,min,utf16(field(nav,"DestName")));
    }
    /** What to do, in a few Korean words, from the firmware's display type (and Direction for a plain turn). */
    static String label(String t,float dir){
        if(t.startsWith("ARRIVED"))return "도착";
        if(t.equals("NEAR DESTINATION"))return "목적지 근처";
        if(t.startsWith("CALC"))return "경로 계산 중";
        if(t.startsWith("RECALC"))return "경로 다시 찾는 중";
        if(t.contains("U TURN"))return "유턴";
        if(t.contains("ROUNDABOUT")||t.contains("ROUNABOUT"))return "회전교차로";
        if(t.contains("SQUARE"))return "광장";
        if(t.startsWith("FORK"))return "갈림길";
        if(t.equals("CHANGE LANE"))return "차로 변경";
        if(t.equals("FOLLOW STREET"))return "직진";
        boolean right=t.contains("RIGHT"),left=t.contains("LEFT");
        if(t.startsWith("EXIT")||t.startsWith("SERVICE")||t.startsWith("PREPARE EXIT"))return right?"오른쪽 출구":left?"왼쪽 출구":"출구";
        if(t.startsWith("OVERPASS"))return right?"오른쪽 고가":"왼쪽 고가";
        if(t.startsWith("UNDERPASS"))return right?"오른쪽 지하차도":"왼쪽 지하차도";
        if(t.startsWith("TURN")||t.startsWith("PREPARE TURN")){
            if(Float.isNaN(dir))return "회전";
            float d=((dir%360)+360)%360;
            return d<20||d>340?"직진":d<160?"우회전":d<=200?"유턴":"좌회전";
        }
        if(t.startsWith("DIRECTION"))return "목적지 방향";
        return "";
    }
    /** Arrow angle for the screen: degrees clockwise from straight up. */
    public static float arrow(State s){
        String t=s.type;
        if(t.startsWith("Unknown"))return 0;   // a domestic code: not matched to a turn yet (see parse)
        if(t.contains("U TURN"))return 180;
        if(t.equals("FOLLOW STREET")||t.equals("CHANGE LANE"))return 0;
        if(!t.startsWith("TURN")&&!t.startsWith("PREPARE TURN")&&!t.contains("ROUNDABOUT")&&!t.contains("ROUNABOUT")&&!t.startsWith("DIRECTION")){
            if(t.contains("RIGHT"))return 45;if(t.contains("LEFT"))return -45;return 0;}
        return Float.isNaN(s.direction)?0:s.direction;
    }
    /** "300 m" / "1.2 km" / "". */
    public static String distance(float m){
        if(Float.isNaN(m)||m<=0)return "";
        return m<1000?String.format(Locale.US,"%d m",Math.round(m/10)*10):String.format(Locale.US,"%.1f km",m/1000);
    }

    /** The block under a heading line (indented lines that follow it), or null. */
    static String section(String s,String head){
        if(s==null)return null;int i=s.indexOf(head);if(i<0)return null;i=s.indexOf('\n',i);if(i<0)return "";
        int indent=-1;StringBuilder b=new StringBuilder();
        for(String line:s.substring(i+1).split("\n",-1)){
            int lead=0;while(lead<line.length()&&line.charAt(lead)==' ')lead++;
            if(line.trim().length()==0){if(indent<0)continue;b.append('\n');continue;}
            if(indent<0)indent=lead;else if(lead<indent)break;
            b.append(line).append('\n');
        }
        return b.toString();
    }
    /** "Name = value" in a block (trimmed), or null. */
    static String field(String block,String name){
        if(block==null)return null;
        for(String line:block.split("\n")){int e=line.indexOf('=');if(e<0)continue;if(line.substring(0,e).trim().equals(name))return line.substring(e+1).trim();}
        return null;
    }
    static float num(String v){
        if(v==null)return Float.NaN;int i=0;while(i<v.length()&&"-+.0123456789".indexOf(v.charAt(i))>=0)i++;
        try{return i==0?Float.NaN:Float.parseFloat(v.substring(0,i));}catch(NumberFormatException e){return Float.NaN;}
    }
    /** "0xf4 0x00 0xb0 0xc6 ..." → the UTF-16LE text after the 0xf4 0x00 marker. */
    static String utf16(String hex){
        if(hex==null)return "";String[] p=hex.trim().split("\\s+");
        int start=p.length>=2&&p[0].equalsIgnoreCase("0xf4")&&p[1].equalsIgnoreCase("0x00")?2:0;
        StringBuilder b=new StringBuilder();
        for(int i=start;i+1<p.length;i+=2){try{int lo=Integer.decode(p[i]),hi=Integer.decode(p[i+1]);int c=hi<<8|lo;if(c==0)break;b.append((char)c);}catch(NumberFormatException e){break;}}
        return b.toString();
    }
}
