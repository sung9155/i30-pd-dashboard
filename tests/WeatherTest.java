import com.veloster.vehicle.Weather;

/** 8-bit weather from the car: wipers (held 20 s) rain, HIGH storm, cold wipers snow; below freezing a winter ground,
 * under 5 °C a bleak one; daytime headlamps for 5 min (or the sky setting) overcast. */
public final class WeatherTest {
    private static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
    private static final float NA=Float.NaN;
    public static void main(String[] args){
        Weather.selectSky("auto");
        Weather w=new Weather();long t=1_000_000;
        check(w.update(t,0,15,false,true)==Weather.CLEAR,"a dry mild day is clear");
        check(w.update(t+1,1,15,false,true)==Weather.CLEAR&&w.update(t+19_000,1,15,false,true)==Weather.CLEAR,"a washer squirt is not rain");
        check(w.update(t+20_001,1,15,false,true)==Weather.RAIN,"wipers for 20 s mean rain");
        check(w.update(t+21_000,2,15,false,true)==Weather.STORM,"HIGH wipers mean a storm");
        check(w.update(t+22_000,1,1,false,true)==Weather.SNOW,"wipers at 1 °C mean snow");
        check(w.update(t+23_000,0,15,false,true)==Weather.RAIN&&w.update(t+82_000,0,15,false,true)==Weather.RAIN,"rain holds through a minute of dry wipers");
        check(w.update(t+83_001,0,15,false,true)==Weather.CLEAR,"a minute without wipers ends the rain");

        w=new Weather();
        check(w.update(t,0,-1,false,true)==Weather.WINTER,"below freezing: a winter ground even without wipers");
        check(w.update(t,0,0,false,true)==Weather.BLEAK&&w.update(t,0,4.9f,false,true)==Weather.BLEAK,"0 to under 5 °C: bleak");
        check(w.update(t,0,5,false,true)==Weather.CLEAR,"5 °C is a normal day");
        check(w.update(t,0,NA,false,true)==Weather.CLEAR,"no outside temperature: a normal day");

        w=new Weather();
        check(w.update(t,0,15,true,true)==Weather.CLEAR&&w.update(t+299_000,0,15,true,true)==Weather.CLEAR,"headlamps briefly (a tunnel) keep it clear");
        check(w.update(t+300_001,0,15,true,true)==Weather.CLOUDY,"daytime headlamps for 5 min mean overcast");
        check(w.update(t+300_002,0,-3,true,true)==Weather.WINTER_CLOUDY&&w.update(t+300_003,0,3,true,true)==Weather.BLEAK_CLOUDY,"overcast keeps the cold grounds");
        check(w.update(t+330_000,0,15,false,true)==Weather.CLOUDY&&w.update(t+390_002,0,15,false,true)==Weather.CLEAR,"a minute without headlamps clears it");
        w=new Weather();
        check(w.update(t,0,15,true,false)==Weather.CLEAR&&w.update(t+600_000,0,15,true,false)==Weather.CLEAR,"headlamps at night say nothing about clouds");

        Weather.selectSky("cloudy");w=new Weather();
        check(w.update(t,0,15,false,true)==Weather.CLOUDY,"sky setting 흐림 is overcast at once");
        Weather.selectSky("clear");w=new Weather();
        check(w.update(t,0,15,true,true)==Weather.CLEAR&&w.update(t+600_000,0,15,true,true)==Weather.CLEAR,"sky setting 맑음 ignores the headlamps");
        w.update(t+600_001,1,15,false,true);
        check(w.update(t+621_000,1,15,false,true)==Weather.RAIN,"sky setting 맑음 still shows rain");
        Weather.selectSky("nope");check(Weather.skyId().equals("auto"),"an unknown saved sky opens on 자동");
        Weather.selectSky("auto");Weather.nextSky();check(Weather.skyId().equals("clear"),"next sky: 자동 -> 맑음");
        Weather.nextSky();Weather.nextSky();check(Weather.skyId().equals("auto"),"next sky wraps");

        check(Weather.overcast(Weather.RAIN)&&Weather.overcast(Weather.WINTER_CLOUDY)&&!Weather.overcast(Weather.BLEAK),"overcast skies");
        check(Weather.ground(Weather.SNOW)==Weather.SNOWY&&Weather.ground(Weather.BLEAK_CLOUDY)==Weather.DRY&&Weather.ground(Weather.RAIN)==Weather.WET,"grounds");
        check(Weather.fall(Weather.STORM)==Weather.STORM&&Weather.fall(Weather.WINTER)==Weather.CLEAR,"what falls");
        System.out.println("PASS weather checks");
    }
}
