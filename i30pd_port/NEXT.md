# 이어서 할 일 (handoff)

다른 PC에서 이어가기: `git clone https://github.com/sung9155/i30-pd-dashboard && cd i30-pd-dashboard && git checkout beta`.
커밋 author는 `sung9155 <12976500+sung9155@users.noreply.github.com>`. 빌드/서명은 `i30pd_port/app/README.md` "재현" 절, 프리뷰는 `bash i30pd_port/app/preview/preview.sh .`, 테스트는
`python -X utf8 scripts/test.py --java "<jdk>/bin/java"` + `python -X utf8 -m unittest discover -s tests -p 'test_*.py'`.

## 남은 작업

1. **추격(8비트 chase) 바퀴 굴러가는 모습** — 현재 뒤차(`PixelCluster.rearArt`)의 바퀴가 정지 상태. 속도(`v(7)`)에 따라 바퀴가 구르는/도는 모습이 보이게.
   사이드뷰(`sideCarArt`)는 `spin`으로 바퀴를 굴린다(스포크 6단계). 후면 바퀴에도 비슷하게 — 스크롤/속도에 맞춰 허브캡 스포크나 타이어 패턴이 돌게. `rearCar[key]` 캐시 키에 바퀴 위상(몇 단계) 추가해 베이크.

2. ~~**G 레이아웃(마찰원 / G circle) 조향 방향 반대**~~ — 2026-11-03 끝냄. 화면 값 `v(STEERING)`은 이미 `Telemetry.displayValue`로 부호가 뒤집혀 **오른쪽 +**다. G원 조향각 줄과 `HudCluster` `STR`의 좌/우 글자만 교체(막대는 +가 오른쪽이라 그대로).

3. **레트로·랠리·80년대(VFD)도 테마 적용** — 기본 N일 때는 지금 고유색(레트로 주황 LCD / 랠리 흰 네모 / 80년대 앰버 VFD) 그대로 두고, **다른 테마로 바꾸면 액센트 색으로 바뀌게**.
   주의: `themed=true`만으론 안 됨 — `Theme.map()`은 N에서 identity, 다른 테마에서도 **팔레트에 없는 고유색은 그냥 통과**시켜서 색이 안 바뀐다(야간/엔진/워프는 액센트를 팔레트색 `0xffa4d9ef`로 바꿔 해결했지만, 그 방식은 N 모습도 파랑으로 바꿔버려 여기선 못 씀 — 고유색을 지켜야 하니까).
   방법: **N이면 고유색, 아니면 `Theme.current().accent`** 를 돌려주는 헬퍼를 각 레이아웃의 메인/액센트 색에 적용. 예) `Theme`에 `public boolean isN(){return keys==null;}` 추가(또는 `"n".equals(id)`), 레이아웃에서 `isN()?신호색:Theme.current().accent`.
   대상: `VfdCluster`(ClusterKit), `ClusterRenderer.retro()`·`rally()`(여긴 자체 `T()` 씀). 레트로/랠리는 ClusterRenderer 메서드라 거기 헬퍼를 두면 된다.

4. **레이아웃 기본 순서 그룹화 + 기본 화면 = 레이스** — 지금 기본 표시 순서는 `ClusterPicker.naturalOrder()`(NAMES 순서 그대로)이고 기본 시작 화면은 전체조망.
   - **기본 순서**: 연관된 것끼리 묶기. `NAMES` 인덱스는 그대로 두고(다른 코드가 인덱스에 의존) **`ClusterPicker.naturalOrder()`가 큐레이트된 순열을 돌려주게** 바꾸면 됨(저장된 순서 없을 때의 기본값). 묶음 예시: 데이터계(전체조망·게이지·그래프) / 레이스·주행계(레이스·F1·퍼포먼스·기록·쇼) / 레트로계(클래식·레트로·랠리·80년대·택시·카세트) / 테크·스포츠계(HUD·G원·야간·드래그·엔진·워프) / 게임계(8비트·추격). 최종 묶음·순서는 구현자가 정리.
   - **기본 시작 화면 = 레이스(screen index 5)**: `VehicleDash` 로드부 `p.getInt("cluster_screen",ClusterPicker.OVERVIEW)` 의 기본값을 레이스로. 깔끔하게 `ClusterPicker`에 `RACE=F1+2`(=5) 상수 추가해 쓰면 좋다. (SPORT 모드 기본도 이미 `F1+2`=레이스.)
   - 주의: 저장된 `cluster_order`/`cluster_screen` 가 있으면 사용자 설정 우선(기존 동작 유지). 기본값만 바꾸는 것.

5. ~~**RED 테마 경고 램프 테두리 흰색 → 테마색**~~ — 2026-11-04 beta 적용 완료(`ThemeShapes.ring`: `int col="red".equals(th.id)?th.accent:0xffffffff;`). RED 클래식 PARK/PARK BRAKE 노랑 램프에 빨간 링. amber는 흰 링 유지.

6. **지도 반 + 계기판 반 (순정 분할 칸)** — 실차 확인 필요. 순정 "내비 반 + 음악 반" 화면이 framework의 분할 창(창 종류 2040, 오른쪽 480 px)을 쓰는지 노트북에서 확인한다: `bash i30pd_port/split_capture.sh on` → `off` → (필요하면) `pull <패키지명>`. 배경·읽는 법·구현 계획은 [`SPLIT_SCREEN.md`](SPLIT_SCREEN.md). 결과는 그 문서 맨 아래 "실차 결과"에 적는다.

7. **엔진사운드 오디오 라우팅 — 전대역 출력 경로 찾기** (2026-11-03 실차 확인) — 현재 4개 경로 중 **`믹스 · 알림`(route 0, micom SYSTEM_ALARM `0x41`, STREAM_NAVI)만 소리가 난다.** 그런데 이건 경고음/차임 채널이라 **전면 스피커만·모노·베이스 없음** → 음질 나쁨. 나머지는 전부 무음:
   - `믹스 · 내비`(route 1, micom NAVI_SOUNDEFFECT `0x40`) — 무음
   - `미디어 소스`(route 2, micom MY_MUSIC `0x31`, STREAM_MUSIC) — 무음
   - `기본 (예전)`(route 3, plain STREAM_MUSIC, micom 없음) — 무음
   필요: **전스피커+서브우퍼로 나가는 micom 오디오 타입**을 찾기. `EngineSound.ROUTE_MICOM`/`ROUTE_STREAM`에 추가해 시험.
   - 조사: 순정 `com.hkmc.media.MicomAudioManager.startAudio(int type)`의 유효 타입 상수 전체를 펌웨어에서 추출(공개 펌웨어 또는 기기 `/system/framework` 의 hkmc media jar 디컴파일). 지금까지 아는 값: `0x41`(알림, 유일하게 울림)·`0x40`(내비효과음)·`0x31`(마이뮤직). 엔터테인먼트 믹스(미디어/AUX/라인인)에 해당하는 타입을 찾는다.
   - 가설: `0x31`(MY_MUSIC)이 무음인 건 micom만으론 부족하고 **음악 앱이 활성 소스**여야 하거나, STREAM_MUSIC AudioTrack이 amp까지 가려면 다른 micom "media/AUX" 경로를 열어야 할 수 있음. 알림만 되는 건 안전 차임이라 항상 열려서일 것.
   - 그때까지: 엔진사운드는 얇은 알림 채널에서만 가청 → 기본 OFF 유지, 경로 선택은 실험적 표시. 전대역 경로 찾으면 그걸 기본 route로. — 2026-11-03 실차 검증(렉 사라짐). 원인은 per-frame 렌더 비용 + 오디오 과부하 합쳐진 것:

8. **DCT 클러치 잔량 조회 (넣고 싶은 후보, 2026-11-04 사용자 요청)** — 설정 → 진단에 DCT 전용으로 "클러치 잔량 홀 ○○% · 짝 ○○%". TCU가 잔량을 들고 있는 건 분명하다(국내 스캐너 사용기: 홀/짝으로 나옴, 2015년식 홀 75 · 짝 83.3; 해외 딜러는 마모량 출력). 그런데 요청·바이트 위치가 공개된 곳이 없다(OBDb i30·코나·씨드·스토닉 등 7DCT 차종 어디에도 wear/life 항목 없음).
   - 1단계(찾기): 진단 화면에 DCT 전용·정차 중에만 "변속기 데이터 찾기" — TCU(0x26)에 22 0100~01FF를 한 번씩(읽기 전용, 약 30초) 묻고 답한 것 원본을 모두 기록. 클러치 온도를 찾은 방식(`OBD_PROBE.md`)과 같다.
   - 2단계(확정·표시): 잔량은 주행 중 거의 안 변해서 값 변화로는 못 찾는다. **같은 날 다른 스캐너(GDS·Car Scanner 등)나 블루핸즈 점검으로 본 홀·짝 잔량 하나**가 있으면 기록에서 그 숫자를 찾아 위치·단위를 확정하고 진단 화면에 표시. 기준값이 없으면 0~100 범위 두 값 후보까지만.
   - 막힌 것: 기준값(진짜 잔량 한 번). 구하면 1·2단계 같이 진행.

9. **타이어 온도 → 공기압이 나오는 모든 화면 (사용자 결정 2026-11-04, 응답 오면 진행)** — 진단 요청 시험(`b471ae7`)이 TPMS(0x24)·BCM(0x30)에 `22 C00B`, TPMS에 `21 06`을 묻는다(`OBD_PROBE.md` "Tyre temperature candidates"). 몇 분 이상 달린 뒤의 진단 기록에서 `OBD probe … ecu 24/30` 줄을 본다.
   - 응답 오면: 어느 ECU·어느 요청이 답했는지, 바이트 배치(데이터 5/10/15/20 또는 5/9/13/17, raw−50 °C, `21 06`이면 1/5/9/13)를 같은 응답 속 공기압이 방송 공기압(0x593)과 맞는 쪽으로 확정 → `ObdLive`가 그 요청만 5~10초마다 → `Telemetry`에 TIRE_TEMP_FL..RR → 공기압을 보여 주는 모든 곳에 바퀴별 온도를 같이: 레이스 TPMS 칸(음악 패널을 열었을 때의 한 줄 `TPMS …` 포함), 전체 조망 "타이어 공기압" 카드, 클래식 FL·FR·RL·RR 칸, 레트로 TPMS 상자, 랠리 TIRE PRESS 칸, 그리고 게이지·그래프 항목(신호 4개 추가). 공기압 경고(30 psi 미만)만 쓰는 곳(클래식 경고등·야간·엔진 메시지)은 그대로. 자리마다 렌더링해서 보여 주고 고른다.
   - 답이 없으면(세 요청 모두 `7F` 또는 no reply): TPMS 제어기가 이 요청을 모르는 것 → 다른 DID 후보를 OBDb·GDS 자료에서 더 찾는다.
  - `e300012` 숫자 표시에서 per-call 정규식 제거, `e969711` 배경·헤더 스트립 캐시 비트맵화 (매 프레임 재그리기 제거) — 이 둘이 체감 대부분
  - `b8ad9b3` 엔진 합성 경량화: RATE 22050→11025, `Math.tanh`→softclip, audio 스레드 우선순위 AUDIO(-16)→DEFAULT, `AudioTrack.write`<0 시 정지
- `fdffe10` 능동 OBD 폴러(ObdLive): 외기온·연료잔량·촉매온도·절대부하 — 2026-11-03 실차 검증(게이지 실시간 표시, 값 정상)
- `f2623f7` 추격 차량 커브 각도(yaw)
- `09f827c` 야간·엔진·워프 테마 액센트 적용 (+프리뷰 라벨 수정)
- `c5bcaa9` 레이아웃 선택 설정 = 리스트 + ▲▼ 순서변경 + 라이브 미니 썸네일
- `dda16b9` 에코 화면 삭제 (오실로·노킹도 앞서 삭제)
- `24fcfdd` 추격 도로 방향 수정, `9d3b677` 플로팅 버튼 투명도 0.2
- 2026-11-04 beta 반영분(다른 PC):
  - `a1de4ee` main v1.8.0 병합 — 사운드(엔진사운드·시프트비프·8비트 플레임) stable 편입(`cd9bf58`), 폰 설치기 주행 녹화(`6e29916`)
  - `ceafc85` 퍼포먼스 실 랩타이머(랩·last·best·delta, 1:23.45 표기)
  - `16b8d0f`/`4a7e355` 시프트 비프(옵션, 기본 OFF): 시프트 큐 점등 시 더블 차임, 알림음 5종(설정→RPM→알림음 종류)
  - `af78d7d`/`35d7a39` 드래그: 측정 준비 키로만 트리 작동(1회/암, 10 s 그린 타임아웃), 암 키 트리 아래 268×96로 이동
  - `a292de3`/`4d1cac9` 택시: 상태 라벨 단일 버튼(운행 시작/종료/정차 후 다음), 상태 램프 달린 미터 키
  - `8bc4b62`/`d38f403` 아케이드: 도로가 차를 중심으로 회전, 차도 도로 따라 회전
  - `750b354` 택시 미터: 말 더 빨리 질주 + LED 테마 추종
  - `ebc5e50` 페이지 chrome 캐시 비트맵(main 쪽)
  - `1887801` 렉 수정: PD에 없는 BCM 서비스 조회 중단, DTC unregister 실패가 OBD 폴러 정지 안 시키게
  - `20fb748`/`a9b634c` OBD 프로브(읽기전용): 현대 mode 22 식별자 원시 응답 로깅, 라운드 반복(최대 2시간)
  - `e1abfa4` My Apps 플로팅 버튼 = 소형 속도계 face
  - 2026-11-05 실차 신호 점검 (코드 변경 없음 — 임시 디버그 로그로 CGW1 0x01 프레임 관찰 후 되돌림):
    - GPS 양호(1 Hz, 정확도 3 m). 속도계 보정(SpeedCal)은 설계상 장시간 — 샘플 120개(`MIN_SAMPLES`), 각 샘플이 40~130 km/h·직진·3초 정속(±2 km/h)·GPS 정확도 ≤10 m 요구, 주행 누적(prefs 저장)·최근 600개 중앙값. 정상, 주차 중엔 속도 0이라 진행 없음.
    - 전면 와이퍼 정상 수신: CGW1(0x01) byte3 0/1/2 = off/low/high 라이브 확인(byte3 00→01→02→00). 순간/분무는 byte3 순간 1, CGW1 주기 ~80 ms라 한 사이클 넘으면 잡힘.
    - **주의 — 바디 신호는 IG ON에서만 방송**: 주차 ACC(시동 OFF)에선 바디 게이트웨이가 와이퍼를 CAN에 안 실어 byte3=0 고정 → "신호 없음"으로 오판하기 쉬움. 문·램프·방향등 등 CGW1 0x01 바디 신호 전반 동일. 바디 신호 점검은 시동 켜고.
    - **후면 와이퍼: hscan 버스에 없음.** 조작해도 CGW1 0x01 한 바이트도 안 변함. `REAR_WIPER` 상수 없고 읽을 소스도 없어 표시 불가. 넣으려면 후면 와이퍼 CAN id를 버스에서 찾아야 하는데 현재 캡처에 없음(Telemetry.java:521 "Rear doors are CGW2, not found in hscan" 와 일관).

## 참고
- 차: `pd_kr`. 재부팅마다 USB 디버깅 꺼짐 → 엔지니어모드→Factory Test `0A 01`→`65 01`→`0A 02` 재진입.
- OBD 폴러는 `com.hkmc.micom.hscanmicomservice` 채널 공유(DtcReader와). 진단 조회 중엔 `ObdLive.diagBusy`로 양보.
- 능동 OBD 프로브(스크래치용): `com.i30.diag.UsefulPids`/`DidScan`/`DiagProbe` — 소스는 세션 스크래치패드, 재작성 시 `DtcReader` 메커니즘 참고.
