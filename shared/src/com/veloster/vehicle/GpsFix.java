package com.veloster.vehicle;

/** The last GPS position Gps received, for the taxi meter. No Android, so the preview and host tests can set it. */
public final class GpsFix {
    private GpsFix(){}
    public static volatile double lat,lon;
    public static volatile float accuracy=Float.NaN;
    /** System.nanoTime of the last fix in ms, 0 before the first. */
    public static volatile long at;
    public static void set(double latitude,double longitude,float acc){lat=latitude;lon=longitude;accuracy=acc;at=Math.max(1,System.nanoTime()/1000000);}
    public static boolean any(){return at>0;}
    /** Fresh enough to place the car (the receiver sends about one a second). */
    public static boolean fresh(){long t=at;return t>0&&System.nanoTime()/1000000-t<5000;}
}
