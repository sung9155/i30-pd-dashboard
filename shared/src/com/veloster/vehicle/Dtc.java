package com.veloster.vehicle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Trouble codes: parse the ECU's reply (UDS 59 02, KWP 58, OBD mode 43) and describe each code in Korean. No Android:
 * host-tested. Descriptions cover the SAE J2012 generic codes this car can raise; any other code still gets its system
 * and subsystem from the code's structure, plus the UDS failure type byte when the ECU sends one.
 */
public final class Dtc {
    public final String code;      // "P0301"
    public final int failureType;  // UDS 3rd byte (ISO 15031-6 FTB), -1 when the protocol has none
    public final int status;       // UDS/KWP status byte, -1 for OBD mode 03

    public Dtc(String code,int failureType,int status){this.code=code;this.failureType=failureType;this.status=status;}

    /** Two code bytes to the SAE form: top two bits pick P/C/B/U, then four hex digits. */
    public static String code(int hi,int lo){
        return "PCBU".charAt((hi>>6)&3)+Integer.toHexString((hi>>4)&3)+hex(hi&15)+hex((lo>>4)&15)+hex(lo&15);
    }
    private static char hex(int v){return Character.toUpperCase(Character.forDigit(v,16));}

    /** UDS ReadDTCInformation reply: 59 02 mask then [hi mid ftb status] per code. Null when it is not one. */
    public static List<Dtc> parseUds(byte[] r){
        if(r==null||r.length<3||(r[0]&255)!=0x59||(r[1]&255)!=0x02)return null;
        List<Dtc> out=new ArrayList<Dtc>();
        for(int i=3;i+3<r.length;i+=4){
            int hi=r[i]&255,lo=r[i+1]&255;
            if(hi==0&&lo==0&&(r[i+2]&255)==0)continue;
            out.add(new Dtc(code(hi,lo),r[i+2]&255,r[i+3]&255));
        }
        return out;
    }
    /** KWP2000 ReadDTCByStatus reply: 58 count then [hi lo status] per code. */
    public static List<Dtc> parseKwp(byte[] r){
        if(r==null||r.length<2||(r[0]&255)!=0x58)return null;
        List<Dtc> out=new ArrayList<Dtc>();
        for(int i=2;i+2<r.length;i+=3){
            int hi=r[i]&255,lo=r[i+1]&255;
            if(hi==0&&lo==0)continue;
            out.add(new Dtc(code(hi,lo),-1,r[i+2]&255));
        }
        return out;
    }
    /** OBD mode 03/07 reply on CAN: 43 count then [hi lo] per code; 0000 pads are dropped. */
    public static List<Dtc> parseObd(byte[] r){
        if(r==null||r.length<2||((r[0]&255)!=0x43&&(r[0]&255)!=0x47))return null;
        List<Dtc> out=new ArrayList<Dtc>();
        for(int i=2;i+1<r.length;i+=2){
            int hi=r[i]&255,lo=r[i+1]&255;
            if(hi==0&&lo==0)continue;
            out.add(new Dtc(code(hi,lo),-1,-1));
        }
        return out;
    }

    /** What the code means: the generic description when known, else system + subsystem from its digits. */
    public String description(){String d=table().get(code);return d!=null?d:category(code);}
    /** UDS failure type in words, or null (no FTB or 0x00 = no subtype). */
    public String failureText(){
        if(failureType<=0)return null;
        String d=FTB.get(failureType);
        return d!=null?d:String.format("유형 %02X",failureType);
    }
    /** Status in words: current, confirmed, pending, warning lamp. Empty when the protocol sends no status. */
    public String statusText(){
        if(status<0)return "";
        StringBuilder s=new StringBuilder();
        if((status&0x01)!=0)s.append("현재 고장");
        if((status&0x08)!=0)s.append(s.length()>0?" · ":"").append("확정");
        else if((status&0x04)!=0)s.append(s.length()>0?" · ":"").append("대기(1회 감지)");
        if((status&0x80)!=0)s.append(s.length()>0?" · ":"").append("경고등 요청");
        if(s.length()==0)s.append("과거 기록");
        return s.toString();
    }
    /** Lit/active codes first: warning lamp, then current, then confirmed. */
    public int severity(){
        if(status<0)return 2;
        return ((status&0x80)!=0?4:0)+((status&0x01)!=0?2:0)+((status&0x08)!=0?1:0);
    }

    static String category(String code){
        char sys=code.charAt(0);int d1=code.charAt(1)-'0';char d2=code.charAt(2);
        boolean oem=sys=='P'?d1==1||(d1==3&&d2<'4'):d1==1||d1==2;
        String owner=oem?"현대 전용 코드":"표준 코드";
        String area;
        switch(sys){
            case 'P':
                if(d1==3&&d2>='4')area="엔진 · 실린더 휴지";
                else if(d2=='A'||d2=='B'||d2=='C')area="하이브리드 구동";
                else switch(d2){
                    case '0':case '1':case '2':area="엔진 · 연료·공기 계량";break;
                    case '3':area="엔진 · 점화 계통 / 실화";break;
                    case '4':area="엔진 · 배출가스 보조 장치";break;
                    case '5':area="차속 · 공회전 제어 · 보조 입력";break;
                    case '6':area="제어기 · 출력 회로";break;
                    case '7':case '8':case '9':area="변속기";break;
                    default:area="파워트레인";
                }
                break;
            case 'C':area="섀시 (제동 · 조향 · 서스펜션)";break;
            case 'B':area="바디 (에어백 · 공조 · 편의장치)";break;
            default:area="네트워크 통신";
        }
        return area+" ("+owner+", 상세 설명 없음)";
    }

    private static Map<String,String> table;
    private static synchronized Map<String,String> table(){
        if(table==null){table=new HashMap<String,String>();for(int i=0;i+1<TEXT.length;i+=2)table.put(TEXT[i],TEXT[i+1]);}
        return table;
    }

    private static final Map<Integer,String> FTB=new HashMap<Integer,String>();
    static{
        Object[] f={
            0x01,"전기 계통 고장",0x02,"신호 이상",0x03,"주파수·PWM 신호 이상",0x04,"내부 고장",0x05,"프로그래밍 이상",
            0x06,"판정 로직상 고장",0x07,"기계적 고장",0x08,"통신 신호·메시지 이상",0x09,"부품 고장",
            0x11,"접지 단락",0x12,"전원(배터리) 단락",0x13,"단선",0x14,"접지 단락 또는 단선",0x15,"전원 단락 또는 단선",
            0x16,"전압 낮음",0x17,"전압 높음",0x18,"전류 낮음",0x19,"전류 높음",0x1A,"저항 낮음",0x1B,"저항 높음",
            0x1C,"전압 범위 벗어남",0x1D,"전류 범위 벗어남",0x1E,"저항 범위 벗어남",0x1F,"회로 간헐 불량",
            0x21,"신호 크기 최소 미만",0x22,"신호 크기 최대 초과",0x23,"신호 낮게 고착",0x24,"신호 높게 고착",
            0x25,"신호 파형 이상",0x26,"신호 변화율 낮음",0x27,"신호 변화율 높음",0x28,"영점·바이어스 이상",
            0x29,"신호 무효",0x2F,"신호 불규칙",
            0x31,"신호 없음",0x36,"주파수 낮음",0x37,"주파수 높음",0x38,"주파수 이상",0x39,"펄스 부족",0x3A,"펄스 과다",
            0x41,"체크섬 오류",0x42,"메모리 오류",0x44,"데이터 메모리 오류",0x45,"프로그램 메모리 오류",
            0x46,"보정값 메모리 오류",0x47,"감시 회로(워치독) 오류",0x49,"내부 전자 회로 고장",0x4A,"잘못된 부품 장착",0x4B,"과열",
            0x51,"프로그래밍 안 됨",0x52,"활성화 안 됨",0x53,"비활성화됨",0x54,"보정값 없음",0x55,"구성(코딩) 안 됨",
            0x61,"신호 계산 오류",0x62,"신호 비교 불일치",0x63,"보호 시간 초과",0x64,"신호 타당성 이상",
            0x65,"신호 전환 횟수 부족",0x66,"신호 전환 횟수 과다",0x67,"이벤트 후 신호 이상",
            0x71,"작동기 고착",0x72,"열린 채 고착",0x73,"닫힌 채 고착",0x74,"작동기 미끄러짐",0x75,"비상 위치 도달 불가",
            0x77,"지령 위치 도달 불가",0x78,"정렬·조정 이상",0x79,"기계 연결부 고장",0x7A,"누유·실링 불량",0x7B,"유량(오일) 부족",
            0x81,"잘못된 통신 데이터 수신",0x82,"메시지 카운터 이상",0x83,"메시지 보호값 이상",0x84,"신호 허용 범위 미만",
            0x85,"신호 허용 범위 초과",0x86,"신호 무효",0x87,"메시지 누락",0x88,"버스 오프",0x8F,"신호 불규칙",
            0x92,"성능 저하·작동 이상",0x93,"작동 안 함",0x94,"예상치 못한 작동",0x96,"부품 내부 고장",
            0x97,"작동 방해·막힘",0x98,"과열"};
        for(int i=0;i+1<f.length;i+=2)FTB.put((Integer)f[i],(String)f[i+1]);
    }

    // ponytail: generic SAE J2012 titles for what a 1.6 T-GDi / 1.6 CRDi with DCT or 6MT can raise, not the whole
    // catalogue; anything else falls back to category(). Add a line when an owner reports a code that shows no text.
    private static final String[] TEXT={
        "P0010","흡기 캠 위상(CVVT) 작동기 회로 이상","P0011","흡기 캠 타이밍 과진각 / 성능 이상","P0012","흡기 캠 타이밍 과지각",
        "P0013","배기 캠 위상(CVVT) 작동기 회로 이상","P0014","배기 캠 타이밍 과진각 / 성능 이상","P0015","배기 캠 타이밍 과지각",
        "P0016","크랭크-흡기 캠 위치 상관관계 이상","P0017","크랭크-배기 캠 위치 상관관계 이상",
        "P0030","산소센서 히터 제어 회로 (촉매 앞)","P0031","산소센서 히터 회로 낮음 (촉매 앞)","P0032","산소센서 히터 회로 높음 (촉매 앞)",
        "P0036","산소센서 히터 제어 회로 (촉매 뒤)","P0037","산소센서 히터 회로 낮음 (촉매 뒤)","P0038","산소센서 히터 회로 높음 (촉매 뒤)",
        "P0068","흡기압(MAP)·공기량과 스로틀 위치 불일치",
        "P0087","연료 레일 압력 너무 낮음","P0088","연료 레일 압력 너무 높음","P0089","연료 압력 조절기 성능 이상",
        "P0090","연료 압력 조절기 제어 회로 이상","P0091","연료 압력 조절기 제어 회로 낮음","P0092","연료 압력 조절기 제어 회로 높음",
        "P0101","공기유량센서(MAF) 범위·성능 이상","P0102","공기유량센서(MAF) 회로 낮음","P0103","공기유량센서(MAF) 회로 높음",
        "P0106","흡기압(MAP)·대기압 센서 범위·성능 이상","P0107","흡기압(MAP) 센서 회로 낮음","P0108","흡기압(MAP) 센서 회로 높음",
        "P0111","흡기온 센서 범위·성능 이상","P0112","흡기온 센서 회로 낮음","P0113","흡기온 센서 회로 높음",
        "P0116","냉각수온 센서 범위·성능 이상","P0117","냉각수온 센서 회로 낮음","P0118","냉각수온 센서 회로 높음",
        "P0121","스로틀 위치 센서 A 범위·성능 이상","P0122","스로틀 위치 센서 A 회로 낮음","P0123","스로틀 위치 센서 A 회로 높음",
        "P0128","냉각수 온도가 서모스탯 정상 온도보다 낮음 (서모스탯 열림 고착 의심)",
        "P0130","산소센서 회로 이상 (촉매 앞)","P0131","산소센서 전압 낮음 (촉매 앞)","P0132","산소센서 전압 높음 (촉매 앞)",
        "P0133","산소센서 응답 느림 (촉매 앞)","P0134","산소센서 신호 없음 (촉매 앞)","P0135","산소센서 히터 회로 이상 (촉매 앞)",
        "P0136","산소센서 회로 이상 (촉매 뒤)","P0137","산소센서 전압 낮음 (촉매 뒤)","P0138","산소센서 전압 높음 (촉매 뒤)",
        "P0139","산소센서 응답 느림 (촉매 뒤)","P0140","산소센서 신호 없음 (촉매 뒤)","P0141","산소센서 히터 회로 이상 (촉매 뒤)",
        "P0171","연료 계통 희박 (공기 과다·연료 부족)","P0172","연료 계통 농후 (연료 과다)",
        "P0191","연료 레일 압력 센서 범위·성능 이상","P0192","연료 레일 압력 센서 회로 낮음","P0193","연료 레일 압력 센서 회로 높음",
        "P0201","1번 인젝터 회로 이상","P0202","2번 인젝터 회로 이상","P0203","3번 인젝터 회로 이상","P0204","4번 인젝터 회로 이상",
        "P0221","스로틀·페달 위치 센서 B 범위·성능 이상","P0222","스로틀·페달 위치 센서 B 회로 낮음","P0223","스로틀·페달 위치 센서 B 회로 높음",
        "P0234","터보 과급 압력 과다 (오버부스트)","P0236","터보 부스트 센서 범위·성능 이상","P0237","터보 부스트 센서 회로 낮음",
        "P0238","터보 부스트 센서 회로 높음","P0243","터보 웨이스트게이트 솔레노이드 회로 이상","P0244","터보 웨이스트게이트 솔레노이드 범위·성능 이상",
        "P0245","터보 웨이스트게이트 솔레노이드 회로 낮음","P0246","터보 웨이스트게이트 솔레노이드 회로 높음",
        "P0261","1번 인젝터 회로 낮음","P0262","1번 인젝터 회로 높음","P0264","2번 인젝터 회로 낮음","P0265","2번 인젝터 회로 높음",
        "P0267","3번 인젝터 회로 낮음","P0268","3번 인젝터 회로 높음","P0270","4번 인젝터 회로 낮음","P0271","4번 인젝터 회로 높음",
        "P0299","터보 과급 압력 부족 (언더부스트)",
        "P0300","여러 실린더 / 무작위 실화 감지","P0301","1번 실린더 실화 감지","P0302","2번 실린더 실화 감지",
        "P0303","3번 실린더 실화 감지","P0304","4번 실린더 실화 감지","P0313","연료 부족 상태에서 실화 감지",
        "P0325","노크 센서 회로 이상","P0326","노크 센서 범위·성능 이상","P0327","노크 센서 회로 낮음","P0328","노크 센서 회로 높음",
        "P0335","크랭크 위치 센서 회로 이상","P0336","크랭크 위치 센서 범위·성능 이상",
        "P0340","캠 위치 센서 A(흡기) 회로 이상","P0341","캠 위치 센서 A(흡기) 범위·성능 이상",
        "P0365","캠 위치 센서 B(배기) 회로 이상","P0366","캠 위치 센서 B(배기) 범위·성능 이상",
        "P0380","예열 플러그 회로 이상","P0400","EGR 유량 이상","P0401","EGR 유량 부족","P0403","EGR 제어 회로 이상",
        "P0404","EGR 제어 범위·성능 이상","P0405","EGR 센서 회로 낮음","P0406","EGR 센서 회로 높음",
        "P0420","촉매 효율 저하 (기준 미달)",
        "P0441","증발가스(EVAP) 퍼지 유량 이상","P0442","증발가스(EVAP) 계통 미세 누설","P0443","증발가스 퍼지 밸브 회로 이상",
        "P0444","증발가스 퍼지 밸브 회로 단선","P0445","증발가스 퍼지 밸브 회로 단락","P0446","증발가스 벤트 제어 회로 이상",
        "P0449","증발가스 벤트 밸브 회로 이상","P0450","증발가스 압력 센서 회로 이상","P0451","증발가스 압력 센서 범위·성능 이상",
        "P0452","증발가스 압력 센서 회로 낮음","P0453","증발가스 압력 센서 회로 높음","P0455","증발가스(EVAP) 계통 큰 누설 (주유구 캡 확인)",
        "P0456","증발가스(EVAP) 계통 아주 작은 누설","P0457","증발가스 누설 (주유구 캡 풀림·없음)","P0459","증발가스 퍼지 밸브 회로 높음",
        "P0480","냉각 팬 1 제어 회로 이상",
        "P0500","차속 센서 이상","P0501","차속 센서 범위·성능 이상","P0504","브레이크 스위치 A/B 신호 불일치",
        "P0506","공회전 회전수가 목표보다 낮음","P0507","공회전 회전수가 목표보다 높음",
        "P0544","배기온 센서 회로 이상","P0545","배기온 센서 회로 낮음","P0546","배기온 센서 회로 높음",
        "P0560","배터리(시스템) 전압 이상","P0561","배터리 전압 불안정","P0562","배터리 전압 낮음","P0563","배터리 전압 높음",
        "P0571","브레이크 스위치 회로 이상",
        "P0600","제어기 간 통신 링크 이상","P0601","엔진 제어기 메모리 체크섬 오류","P0602","엔진 제어기 프로그래밍 오류",
        "P0603","엔진 제어기 상시 메모리(KAM) 오류","P0604","엔진 제어기 RAM 오류","P0605","엔진 제어기 ROM 오류",
        "P0606","엔진 제어기 프로세서 이상","P0607","엔진 제어기 성능 이상",
        "P0627","연료 펌프 제어 회로 단선","P0628","연료 펌프 제어 회로 낮음","P0629","연료 펌프 제어 회로 높음",
        "P0638","스로틀 작동기 제어 범위·성능 이상","P0641","센서 기준 전압 A 회로 이상","P0651","센서 기준 전압 B 회로 이상",
        "P0670","예열 플러그 제어 모듈 회로 이상","P0671","1번 예열 플러그 회로 이상","P0672","2번 예열 플러그 회로 이상",
        "P0673","3번 예열 플러그 회로 이상","P0674","4번 예열 플러그 회로 이상",
        "P0700","변속기 제어 계통 고장 (변속기 제어기에 상세 코드 있음)","P0705","변속 레버 위치 센서 회로 이상",
        "P0710","변속기 오일 온도 센서 회로 이상","P0711","변속기 오일 온도 센서 범위·성능 이상","P0712","변속기 오일 온도 센서 회로 낮음",
        "P0713","변속기 오일 온도 센서 회로 높음","P0715","변속기 입력축 속도 센서 회로 이상","P0716","변속기 입력축 속도 센서 범위·성능 이상",
        "P0717","변속기 입력축 속도 센서 신호 없음","P0720","변속기 출력축 속도 센서 회로 이상","P0721","변속기 출력축 속도 센서 범위·성능 이상",
        "P0722","변속기 출력축 속도 센서 신호 없음","P0730","기어비 불일치","P0731","1단 기어비 불일치","P0732","2단 기어비 불일치",
        "P0733","3단 기어비 불일치","P0734","4단 기어비 불일치","P0735","5단 기어비 불일치","P0736","후진 기어비 불일치",
        "P0805","클러치 위치 센서 회로 이상","P0806","클러치 위치 센서 범위·성능 이상","P0807","클러치 위치 센서 회로 낮음",
        "P0808","클러치 위치 센서 회로 높음","P0810","클러치 위치 제어 오류",
        "P0900","클러치 작동기 회로 단선","P0901","클러치 작동기 범위·성능 이상","P0902","클러치 작동기 회로 낮음","P0903","클러치 작동기 회로 높음",
        "P1326","노크 센서 감지 시스템(KSDS) 경고 — 엔진 베어링 손상 의심, 즉시 점검 권장",
        "P2002","DPF 효율 저하 (기준 미달)","P2096","촉매 뒤 연료 보정 희박","P2097","촉매 뒤 연료 보정 농후",
        "P2122","가속 페달 위치 센서 D 회로 낮음","P2123","가속 페달 위치 센서 D 회로 높음",
        "P2127","가속 페달 위치 센서 E 회로 낮음","P2128","가속 페달 위치 센서 E 회로 높음",
        "P2135","스로틀 위치 센서 A/B 전압 불일치","P2138","가속 페달 위치 센서 D/E 전압 불일치",
        "P2187","공회전 시 연료 계통 희박","P2188","공회전 시 연료 계통 농후",
        "P2195","산소센서 신호 희박 쪽 고착 (촉매 앞)","P2196","산소센서 신호 농후 쪽 고착 (촉매 앞)",
        "P242F","DPF 막힘 (재 축적)","P2452","DPF 차압 센서 회로 이상","P2453","DPF 차압 센서 범위·성능 이상",
        "P2454","DPF 차압 센서 회로 낮음","P2455","DPF 차압 센서 회로 높음","P2458","DPF 재생 시간 초과","P2463","DPF 막힘 (매연 축적)",
        "C0035","왼쪽 앞 바퀴속도 센서 회로 이상","C0040","오른쪽 앞 바퀴속도 센서 회로 이상",
        "C0045","왼쪽 뒤 바퀴속도 센서 회로 이상","C0050","오른쪽 뒤 바퀴속도 센서 회로 이상",
        "U0001","고속 CAN 통신 버스 이상","U0100","엔진 제어기(ECM)와 통신 끊김","U0101","변속기 제어기(TCM)와 통신 끊김",
        "U0121","ABS 제어기와 통신 끊김","U0122","차체 자세 제어기(ESC)와 통신 끊김","U0126","조향각 센서와 통신 끊김",
        "U0131","전동 조향(MDPS) 제어기와 통신 끊김","U0140","바디 제어기(BCM)와 통신 끊김","U0146","게이트웨이와 통신 끊김",
        "U0151","에어백 제어기와 통신 끊김","U0155","계기판과 통신 끊김","U0164","공조 제어기와 통신 끊김",
        "U0401","엔진 제어기(ECM)에서 잘못된 데이터 수신","U0402","변속기 제어기(TCM)에서 잘못된 데이터 수신",
        "U0415","ABS 제어기에서 잘못된 데이터 수신","U0416","차체 자세 제어기(ESC)에서 잘못된 데이터 수신",
        "U0428","조향각 센서에서 잘못된 데이터 수신"};
}
