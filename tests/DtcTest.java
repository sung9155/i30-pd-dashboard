import com.veloster.vehicle.Dtc;
import java.util.List;

/** Trouble-code parsing and descriptions. Synthetic replies only; no car. */
public final class DtcTest {
    static int checks;
    static void check(boolean ok,String m){checks++;if(!ok)throw new AssertionError(m);}
    static byte[] b(int... v){byte[] r=new byte[v.length];for(int i=0;i<v.length;i++)r[i]=(byte)v[i];return r;}
    public static void main(String[] a){
        check(Dtc.code(0x03,0x01).equals("P0301"),"P0301");
        check(Dtc.code(0x24,0x2f).equals("P242F"),"hex digits upper case");
        check(Dtc.code(0x41,0x23).equals("C0123"),"C");
        check(Dtc.code(0x91,0x23).equals("B1123"),"B");
        check(Dtc.code(0xc1,0x00).equals("U0100"),"U");

        // UDS 59 02 <mask> then code hi, mid, failure type, status
        List<Dtc> u=Dtc.parseUds(b(0x59,0x02,0xff, 0x03,0x01,0x00,0x09, 0x01,0x13,0x13,0x05, 0,0,0,0));
        check(u!=null&&u.size()==2,"two UDS codes, zero pad dropped");
        check(u.get(0).code.equals("P0301")&&u.get(0).description().contains("1번 실린더 실화"),"P0301 text");
        check(u.get(0).statusText().equals("현재 고장 · 확정"),"status 0x09 = current + confirmed: "+u.get(0).statusText());
        check(u.get(1).failureText().equals("단선"),"FTB 0x13 = open circuit");
        check(u.get(1).statusText().equals("현재 고장 · 대기(1회 감지)"),"status 0x05: "+u.get(1).statusText());
        check(Dtc.parseUds(b(0x59,0x02,0xff)).isEmpty(),"no codes");
        check(Dtc.parseUds(b(0x7f,0x19,0x11))==null,"negative reply is not a list");
        check(new Dtc("P0420",0,0x88).statusText().equals("확정 · 경고등 요청"),"lamp");
        check(new Dtc("P0420",0,0x28).statusText().equals("확정"),"stored, not current");
        check(new Dtc("P0420",0,0x00).statusText().equals("과거 기록"),"history only");
        check(new Dtc("P0420",0,0x88).severity()>new Dtc("P0300",0,0x08).severity(),"lamp sorts first");

        List<Dtc> k=Dtc.parseKwp(b(0x58,0x02, 0x01,0x71,0xe0, 0x04,0x20,0x60));
        check(k!=null&&k.size()==2&&k.get(0).code.equals("P0171")&&k.get(1).code.equals("P0420"),"KWP 58");
        check(k.get(0).failureText()==null,"KWP has no failure type");

        List<Dtc> o=Dtc.parseObd(b(0x43,0x02, 0x02,0x99, 0x01,0x28, 0,0));
        check(o!=null&&o.size()==2&&o.get(0).code.equals("P0299")&&o.get(0).description().contains("언더부스트"),"OBD 43");
        check(o.get(0).statusText().isEmpty(),"OBD mode 03 has no status");

        // codes without a table entry still say where they belong
        check(new Dtc("P1A23",-1,-1).description().contains("현대 전용"),"P1xxx is manufacturer: "+new Dtc("P1A23",-1,-1).description());
        check(new Dtc("P0A80",-1,-1).description().startsWith("하이브리드"),"P0Axx hybrid");
        check(new Dtc("P0999",-1,-1).description().startsWith("변속기"),"P09xx transmission");
        check(new Dtc("P3400",-1,-1).description().contains("실린더 휴지")&&new Dtc("P3400",-1,-1).description().contains("표준"),"P34xx generic");
        check(new Dtc("C1234",-1,-1).description().startsWith("섀시"),"C");
        check(new Dtc("B0001",-1,-1).description().contains("표준"),"B0 generic");
        check(new Dtc("U1100",-1,-1).description().contains("현대 전용"),"U1 manufacturer");
        check(new Dtc("P0301",0x55,8).failureText().equals("구성(코딩) 안 됨"),"FTB table");
        check(new Dtc("P0301",0xee,8).failureText().equals("유형 EE"),"unknown FTB shows its byte");
        System.out.println("PASS "+checks+" trouble code checks");
    }
}
