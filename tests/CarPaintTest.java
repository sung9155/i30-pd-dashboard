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
        System.out.println("PASS car paint checks");
    }
}
