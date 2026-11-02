import com.veloster.vehicle.TaxiFare;

/** Seoul medium-taxi meter: base 1.6 km, then 100 won per 131 m or per 30 s at 15 km/h or slower (one or the other). */
public final class TaxiFareTest {
    private static int checks;
    private static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
    /** Drive for sec seconds at kmh, in 0.1 s steps, with the night surcharge sur. */
    private static void drive(TaxiFare f,float kmh,float sec,float sur){for(int i=0;i<Math.round(sec*10);i++)f.add(kmh,0.1f,sur);}
    public static void main(String[] args){
        TaxiFare f=new TaxiFare();f.start(0);
        check(f.total()==4800&&Math.abs(f.toNext()-1731)<0.5f,"base fare; the first 100 won comes at 1.6 km + 131 m");
        drive(f,36,20,0);   // 200 m
        check(f.total()==4800&&Math.abs(f.toNext()-1531)<1,"inside the base distance the next step is 1,531 m away, not 131 m");
        drive(f,36,180,0);  // +1,800 m = 2,000 m
        check(f.distUnits==3&&f.timeUnits==0&&f.total()==5100,"2.0 km: three 131 m steps past 1.6 km");

        TaxiFare crawl=new TaxiFare();crawl.start(0);
        drive(crawl,12,600,0);   // 10 min in traffic: 20 x 30 s = 2,620 m equivalent
        check(crawl.timeUnits==7&&crawl.distUnits==0&&crawl.total()==5500,"crawling is charged by time only, not by time and distance: "+crawl.total());

        TaxiFare night=new TaxiFare();night.start(0.4f);
        check(night.total()==6700,"a trip started at 23-02 h: 4,800 + 40 %, rounded to 100 won");
        TaxiFare late=new TaxiFare();late.start(0);
        drive(late,36,200,0);    // 2,000 m before 22:00: 3 steps at 100 won
        drive(late,36,131*3/10f,0.2f);   // 393 m after 22:00: 3 steps at 120 won
        check(late.total()==4800+300+400&&late.surcharge()==100,"the surcharge applies only to steps charged after 22:00: "+late.total());
        check(late.total()==4800+300+400,"the total does not change afterwards (the receipt keeps it)");
        TaxiFare stopped=new TaxiFare();stopped.start(0);stopped.add(Float.NaN,5,0);
        check(stopped.total()==4800&&stopped.timeUnits==0,"no speed reading adds nothing");
        System.out.println("PASS "+checks+" taxi fare checks");
    }
}
