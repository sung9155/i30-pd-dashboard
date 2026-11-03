package com.veloster.vehicle;

/**
 * Weather for the 8-bit views, from what the car tells the head unit (no rain or light sensor reaches it):
 * wipers running for 20 s mean rain (HIGH a storm, at 1 °C or colder snow) until they have been off for a minute;
 * below freezing the ground is winter, under 5 °C bleak; daytime headlamps on for 5 min (switch on AUTO) mean
 * overcast until they have been off for a minute. The sky setting can pin the sky clear or overcast instead.
 * No Android: host-tested.
 */
public final class Weather {
    public static final int CLEAR=0,CLOUDY=1,BLEAK=2,BLEAK_CLOUDY=3,WINTER=4,WINTER_CLOUDY=5,RAIN=6,STORM=7,SNOW=8;
    /** ground(): plain grass, or wet, dry (bleak) or snow-covered. */
    public static final int GRASS=0,WET=1,DRY=2,SNOWY=3;
    public static final String[] SKY_IDS={"auto","clear","cloudy"},SKY_LABELS={"자동","맑음","흐림"};
    static final long WET_MS=20_000,DRY_MS=60_000,GREY_MS=300_000,UNGREY_MS=60_000;
    static final float SNOW_AT=1,BLEAK_BELOW=5;   // °C; below 0 is winter
    private static volatile int sky;
    private long wipeOn=-1,wipeOff=-1,lampOn=-1,lampOff=-1;
    private boolean wet,grey;

    public static void selectSky(String id){int n=0;for(int i=0;i<SKY_IDS.length;i++)if(SKY_IDS[i].equals(id))n=i;sky=n;}
    public static void nextSky(){sky=(sky+1)%SKY_IDS.length;}
    public static String skyId(){return SKY_IDS[sky];}
    public static String skyLabel(){return SKY_LABELS[sky];}

    /** One sample: wiper 0 off / 1 low / 2 high (NaN unknown), outside °C (NaN unknown), low beam, and daytime. */
    public int update(long now,float wiper,float ambient,boolean headlamp,boolean day){
        boolean wiping=!Float.isNaN(wiper)&&wiper>0;
        if(wiping){if(wipeOn<0)wipeOn=now;wipeOff=-1;if(now-wipeOn>=WET_MS)wet=true;}
        else{wipeOn=-1;if(wipeOff<0)wipeOff=now;if(now-wipeOff>=DRY_MS)wet=false;}
        boolean lit=headlamp&&day;
        if(lit){if(lampOn<0)lampOn=now;lampOff=-1;if(now-lampOn>=GREY_MS)grey=true;}
        else{lampOn=-1;if(lampOff<0)lampOff=now;if(now-lampOff>=UNGREY_MS)grey=false;}
        boolean known=!Float.isNaN(ambient),g=sky==2||(sky==0&&grey);
        if(wet)return known&&ambient<=SNOW_AT?SNOW:wiping&&wiper>=2?STORM:RAIN;
        if(known&&ambient<0)return g?WINTER_CLOUDY:WINTER;
        if(known&&ambient<BLEAK_BELOW)return g?BLEAK_CLOUDY:BLEAK;
        return g?CLOUDY:CLEAR;
    }
    public static boolean overcast(int w){return w==CLOUDY||w==BLEAK_CLOUDY||w==WINTER_CLOUDY||w>=RAIN;}
    public static int ground(int w){return w==SNOW||w==WINTER||w==WINTER_CLOUDY?SNOWY:w==BLEAK||w==BLEAK_CLOUDY?DRY:w==RAIN||w==STORM?WET:GRASS;}
    /** What falls: CLEAR (nothing), RAIN, STORM or SNOW. */
    public static int fall(int w){return w>=RAIN?w:CLEAR;}
}
