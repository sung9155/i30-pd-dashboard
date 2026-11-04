package com.veloster.vehicle;

import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;
import android.os.SystemClock;

/**
 * The head unit's GPS into GpsFix through LocationManager "gps" (the firmware's gps.tcc893x.so HAL over /dev/ttyGPS0, a Trimble
 * receiver with dead reckoning), for the taxi meter's fare area. Runs while the dashboard is attached. Once a minute it
 * logs how many fixes came, their accuracy and the fare table they fall in (시·도 level, never the position), so a
 * [진단 기록] shows whether the GPS reaches the app at all on a given car.
 */
final class Gps implements LocationListener {
    private static Gps running;
    private LocationManager lm;
    private int fixes;
    private long logAt,startAt;

    static void start(Context c){
        if(running!=null)return;
        Gps g=new Gps();g.startAt=g.logAt=SystemClock.elapsedRealtime();
        try{
            g.lm=(LocationManager)c.getSystemService(Context.LOCATION_SERVICE);
            g.lm.requestLocationUpdates(LocationManager.GPS_PROVIDER,1000,0,g,Looper.getMainLooper());
            running=g;
            android.util.Log.i("VehicleDash","GPS requested (gps provider enabled="+g.lm.isProviderEnabled(LocationManager.GPS_PROVIDER)+")");
        }catch(RuntimeException e){android.util.Log.w("VehicleDash","GPS unavailable: "+e);}   // no permission in a host app, no provider
    }
    static void stop(){
        Gps g=running;running=null;
        if(g!=null)try{g.lm.removeUpdates(g);}catch(RuntimeException e){/* already gone */}
    }
    @Override public void onLocationChanged(Location l){
        long now=SystemClock.elapsedRealtime();
        if(!GpsFix.any())android.util.Log.i("VehicleDash","GPS first fix after "+(now-startAt)/1000+" s");
        GpsFix.set(l.getLatitude(),l.getLongitude(),l.hasAccuracy()?l.getAccuracy():Float.NaN);fixes++;
        if(now-logAt>=60000){
            int z=TaxiZones.at(GpsFix.lat,GpsFix.lon);
            android.util.Log.i("VehicleDash","GPS "+fixes+" fixes in "+(now-logAt)/1000+" s, accuracy "+Math.round(GpsFix.accuracy)+" m, fare table "+(z<0?"none":TaxiZones.tariff(z)));
            fixes=0;logAt=now;
        }
    }
    @Override public void onStatusChanged(String provider,int status,Bundle extras){}
    @Override public void onProviderEnabled(String provider){android.util.Log.i("VehicleDash","GPS provider enabled");}
    @Override public void onProviderDisabled(String provider){android.util.Log.i("VehicleDash","GPS provider disabled");}
}
