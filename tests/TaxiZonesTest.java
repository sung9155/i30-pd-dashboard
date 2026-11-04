import com.veloster.vehicle.TaxiFare;
import com.veloster.vehicle.TaxiTariff;
import com.veloster.vehicle.TaxiZones;

/** Taxi fare areas from GPS and each area's table: city halls land in their own area, the sea in none. */
public final class TaxiZonesTest {
    private static int checks;
    private static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
    private static void area(double lat,double lon,String tariff,String name){
        int z=TaxiZones.at(lat,lon);
        check(z>=0&&TaxiZones.tariff(z).equals(tariff)&&TaxiZones.name(z).equals(name),
            lat+","+lon+" should be "+tariff+"/"+name+": "+(z<0?"none":TaxiZones.tariff(z)+"/"+TaxiZones.name(z)));
    }
    private static void near(float got,float want,String s){check(Math.abs(got-want)<1e-4f,s+": "+got);}
    public static void main(String[] args){
        area(37.5663,126.9779,"서울","서울");          // Seoul City Hall
        area(37.6584,127.0390,"서울","서울");          // Dobong-gu office (a 구, still the one Seoul area)
        area(37.2636,127.0286,"경기","수원시");        // Suwon City Hall (일반구 merged into the 시)
        area(37.2411,127.1776,"경기","용인시");        // Yongin City Hall
        area(37.5034,126.7660,"경기","부천시");        // Bucheon City Hall (구 re-established 2024)
        area(37.1995,126.8312,"경기","화성시");        // Hwaseong City Hall (구 from 2026)
        area(35.1798,129.0750,"부산","부산");          // Busan City Hall
        area(35.3350,129.0373,"경남","양산시");        // Yangsan City Hall
        area(35.1601,126.8514,"광주","광주");          // Gwangju City Hall: its own table since the 2026 merger
        area(34.8118,126.3922,"전남","목포시");        // Mokpo City Hall
        area(36.4800,127.2890,"세종","세종");          // Sejong government complex
        area(33.4996,126.5312,"제주","제주");          // Jeju City Hall
        area(33.2541,126.5600,"제주","제주");          // Seogwipo: the island is one area
        area(37.7470,128.8760,"강원","강릉시");        // Gangneung
        check(TaxiZones.at(37.0,125.5)<0,"the Yellow Sea is in no area");
        check(TaxiZones.at(35.6762,139.6503)<0,"Tokyo is in no area");

        TaxiTariff seoul=TaxiTariff.of("서울"),busan=TaxiTariff.of("부산"),jeju=TaxiTariff.of("제주");
        near(seoul.surcharge(12,false),0,"Seoul at noon");
        near(seoul.surcharge(12,true),0.2f,"Seoul out of the city at noon");
        near(seoul.surcharge(22,true),0.4f,"Seoul 22 h out of the city: 20 + 20");
        near(seoul.surcharge(0,true),0.6f,"Seoul midnight out of the city: 40 + 20");
        near(busan.surcharge(23,true),0.4f,"Busan's combined rate is 40, not 20 + 30");
        near(busan.surcharge(1,true),0.5f,"Busan 24-02 h combined");
        near(jeju.surcharge(12,true),0,"Jeju has no out-of-area surcharge");
        check(TaxiTariff.of("세종").borrowed&&TaxiTariff.of("세종").base==4800,"Sejong borrows Seoul's table");
        check(TaxiTariff.of("어딘가")==seoul,"an unknown name falls back to Seoul");
        check(seoul.nightHours().equals("22~04")&&seoul.nightMax()==40,"Seoul night note: "+seoul.nightHours());
        check(TaxiTariff.of("전북").nightHours().equals("00~04"),"Jeonbuk night note: "+TaxiTariff.of("전북").nightHours());

        TaxiFare f=new TaxiFare();f.start(busan,0);
        for(int i=0;i<200;i++)f.add(36,0.1f,0);   // 200 m
        check(f.total()==4800&&f.distUnits==0,"Busan's base covers 2 km");
        TaxiFare far=new TaxiFare();far.start(jeju,0);
        for(int i=0;i<25000;i++)far.add(72,0.1f,0);   // 50 km
        check(far.distWon>far.distUnits*100,"past 20 km a Jeju step is 120 won");
        check(far.surcharge()==0,"and that is not a surcharge: "+far.surcharge());
        System.out.println("PASS "+checks+" taxi area checks");
    }
}
