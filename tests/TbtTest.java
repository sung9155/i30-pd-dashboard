import com.veloster.vehicle.Tbt;

/** The stock navigation's guidance from the ClusterService dump (the layout of a real 2026-11-12 dump, made-up values). */
public final class TbtTest {
    private static int checks;
    private static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
    static String dump(String type,String dir,String dist,String sub,String min,String dest){
        return "System Cluster:\n  Locale   = ko_KR\n\nNavigation Cluster:\n  TBT Trap Info:\n    Mobile  Speed = 0\n    OverspeedAlarm= 3\n\n"
            +"  TBT SubInfo:\n    distance      = "+sub+" METER\n    minutes       = "+min+"\n    time type     = 1\n\n"
            +"  TBT SubInfo2:\n    next 1        = 0.0 DISTANCE NONE\n\n"
            +"  TBT Info:\n    Display Type  = "+type+"\n    Side Street   = 0 1 2 3 \n    Direction     = "+dir+"\n    Distance      = "+dist+" METER\n    BarGraph Level= 0\n\n"
            +"  OperationStatus = 1\n  LineInfo = \n  DestName = "+dest+"\n  TurnList = \n  SLIF Navi Info:\n    Speed limit = 0\n";
    }
    public static void main(String[] a){
        String company="0xf4 0x00 0x8c 0xd6 0xac 0xc0 ";   // "회사" in UTF-16LE after the marker
        Tbt.State none=Tbt.parse(dump("NO ROUTE GUIDANCE(COMPASS)","262.5","0.0","0.0","0",""));
        check(!none.active&&!none.maneuver,"no route: nothing to show");
        Tbt.State toDest=Tbt.parse(dump("DIRECTION TO DESTINATION","270.0","0.0","15.5","39",company));
        check(toDest.active&&!toDest.maneuver&&toDest.dest.equals("회사")&&toDest.destKm==15.5f&&toDest.minutes==39,"destination set, no turn yet: "+toDest.dest);
        Tbt.State left=Tbt.parse(dump("TURN","270.0","300.0","12.0","30",company));
        check(left.maneuver&&left.label.equals("좌회전")&&Tbt.distance(left.metres).equals("300 m")&&Tbt.arrow(left)==270,"turn left in 300 m");
        Tbt.State right=Tbt.parse(dump("PREPARE TURN","90.0","0.8","12.0","30",company));
        check(right.label.equals("우회전")&&Tbt.distance(right.metres).equals("800 m"),"a fraction below 100 reads as km: "+Tbt.distance(right.metres));
        Tbt.State exit=Tbt.parse(dump("EXIT RIGHT","0.0","1500.0","12.0","30",company));
        check(exit.label.equals("오른쪽 출구")&&Tbt.arrow(exit)==45&&Tbt.distance(exit.metres).equals("1.5 km"),"exit right");
        Tbt.State u=Tbt.parse(dump("U TURN","180.0","200.0","1.0","3",company));
        check(u.label.equals("유턴")&&Tbt.arrow(u)==180,"u-turn");
        Tbt.State iconR=Tbt.parse(dump("Unknown display type(0x43)","0.0","500.0","2.9","1",company));   // icon 67: right
        check(iconR.maneuver&&iconR.label.equals("우회전")&&Tbt.arrow(iconR)==90,"projection icon 67: a right turn");
        Tbt.State sl=Tbt.parse(dump("Unknown display type(0x47)","0.0","500.0","2.9","1",company));   // icon 71: slight left
        check(sl.label.equals("왼쪽 방향")&&Tbt.arrow(sl)==-45,"projection icon 71: slight left");
        check(Tbt.parse(dump("Unknown display type(0x51)","0.0","900.0","2.9","1",company)).label.equals("오른쪽 출구"),"icon 81: exit right");
        Tbt.State kr=Tbt.parse(dump("Unknown display type(0x5b)","0.0","600.0","2.9","1",company));   // icon 91, seen on the car 2026-11-27: not known yet
        check(kr.active&&kr.maneuver&&kr.metres==600&&Tbt.arrow(kr)==0&&kr.label.equals("다음 안내"),"domestic code with a distance: guidance");
        check(!Tbt.parse(dump("Unknown display type(0x5b)","0.0","0.0","0.0","0","")).active,"domestic code without a distance: none");
        check(Tbt.parse("garbage").active==false&&Tbt.parse(null)==Tbt.NONE,"unreadable text");
        Tbt.set(left,1000);check(Tbt.current(2000)==left&&Tbt.current(1000+7000)==Tbt.NONE,"a stale reading drops");
        System.out.println("PASS "+checks+" guidance checks");
    }
}
