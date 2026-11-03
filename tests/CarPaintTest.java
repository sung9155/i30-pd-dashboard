import com.veloster.vehicle.CarPaint;

/** Car paint choice: Performance Blue by default and for a corrupt saved id; next() walks every paint and wraps. */
public final class CarPaintTest {
    private static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
    public static void main(String[] args){
        check(CarPaint.id().equals("blue"),"Performance Blue is the default");
        CarPaint.select("red");check(CarPaint.id().equals("red")&&CarPaint.label().equals("파이어리 레드"),"a saved paint is restored");
        CarPaint.select("nope");check(CarPaint.id().equals("blue"),"an unknown id opens in Performance Blue");
        CarPaint.select(null);check(CarPaint.id().equals("blue"),"no saved id opens in Performance Blue");
        for(int i=0;i<CarPaint.IDS.length;i++)CarPaint.next();
        check(CarPaint.id().equals("blue"),"next() wraps after the last paint");
        check(CarPaint.IDS.length==CarPaint.LABELS.length,"one label per paint");
        // every colour of the 2016 launch (신형 i30 press release) is offered
        for(String n:new String[]{"팬텀 블랙","폴라 화이트","플래티넘 실버","데미타스 브라운","아이언 그레이","스타게이징 블루","파이어리 레드","마리나 블루","인텐스 카퍼"})
            check(java.util.Arrays.asList(CarPaint.LABELS).contains(n),"2016 launch paint offered: "+n);
        check(java.util.Arrays.asList(CarPaint.LABELS).contains("미드나이트 퍼플"),"a tuning purple is offered");
        for(int i=0;i<CarPaint.IDS.length;i++){CarPaint.select(i);check(CarPaint.body().length==4,"four shades for "+CarPaint.id());}
        System.out.println("PASS car paint checks");
    }
}
