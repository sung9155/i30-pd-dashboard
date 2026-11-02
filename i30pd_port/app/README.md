# i30 PD 대시보드 앱 — 포팅 내용과 빌드

업스트림 `reikop/veloster-n-dashboard`(`main` = `2be141b`)에 PD 포팅을 적용한 결과. 이 레포가 업스트림의 포크라 바뀐 소스가 레포 루트(`apps/`·`shared/`·`tests/`)에 그대로 있다. 바뀐 내용 전체는 `git diff 2be141b`.

| 파일 | 내용 |
|---|---|
| 소스 | 레포 루트. 대시보드(`apps/dashboard`·`shared`)와 '내 앱'(`apps/hub`). 예전에는 `i30pd-upstream.patch` 한 파일로 배포했다 |
| `screenshot_2026-10-29_incar.png` | 실차 첫 실행 화면(대칭 탭, 지금은 삭제된 탭): RPM 2278, 20 km/h, D 2단, G 0.18 g, BRK 1 bar, BATT 13.8 V |
| APK | 이 레포에는 두지 않는다. [Releases](https://github.com/sung9155/i30-pd-dashboard/releases)에서 `dashboard-i30pd-platform.apk`·`myapps-i30pd-platform.apk`를 받는다. `com.veloster.dashboard`·`com.veloster.carplayhub`, minSdk 17, **AOSP *platform* 테스트 키 V1(JAR) 서명**(SHA1 `27:19:6E:38:6B:87:5E:76:AD:F7:00:E7:EA:84:E4:C6:EE:E3:3D:FA` = 업스트림 배포본과 같은 인증서) |

> **서명 키 주의:** AOSP `testkey`로 서명하면 PD에서 `INSTALL_PARSE_FAILED_INCONSISTENT_CERTIFICATES`로 거절된다(logcat에 PackageParser 오류 없이 조용히 실패 → OEM PMS 정책). 업스트림 배포본과 같은 **`platform`** 키(android-4.2.2_r1 `platform.pk8`/`platform.x509.pem`, 공개)로 서명하면 설치된다.

> **6단 수동 기어 표시 (v1.5, 실차 미확인):** 수동차는 변속 레버가 없어 `0x1d`가 늘 P(0)로 와서 레이스·퍼포먼스 탭이 단수와 상관없이 **P**를 그렸다. 이제 ① 수동으로 판정되면 레버 값을 무시하고 단수만 표시, ② 단수는 TCU(`0x1f`)에 없으면 엔진 ECU가 계기판에 보내는 `EMS_H12 CF_Ems_DesCurGr`(`0x10`)를 쓴다(DCT에선 항상 0), ③ 변속기 판별은 레버 체크섬(`0x1d` b7)이 바뀌면 DCT(P 공회전에서도 매 프레임 순환), 약 5초 고정이거나 EMS 단수가 오면 수동. 실차 DCT 로그 리플레이로 DCT 표시는 그대로임을 확인. 수동차에서 이 값이 현재 단인지 권장 단인지는 캡처로 확인이 필요하다.
>
> **수동 단수 = RPM ÷ 속도 (v1.5.1, 실차 미확인):** v1.5를 쓴 수동차 오너 제보 — 단수와 상관없이 **1**로 나옴. `0x10`은 계기판 변속 권장 표시용 값이고 수동차엔 TCU가 없어 `0x1f`도 의미가 없어서 둘 다 표시에서 뺐다(`0x10`은 수동 판별에만 사용). 대신 엔진 RPM ÷ 차속을 6MT 기어비(Hyundai AU 공식 사양서 1.6 T-GDi 6MT 3.308/1.962/1.294/0.976/0.778/0.633, 종감속 4.467, 225/40R18)와 맞춘다. 비율 상수는 7DCT 실차 로그로 측정(같은 타이어·속도 신호). 이웃 단과 23 % 이상 차이라 ±9 % 안에 들 때만 단수를 내고, 클러치·중립·저속에서는 `—`.
>
> **수동 변속 권장 표시 (v1.5.2):** 오너 확인으로 `0x10` 값이 계기판의 변속 권장 단수임이 확정 → 새 신호 `SHIFT_HINT`(변속 권장)로 디코드(올림 화살표 byte6 bit0 = +단, 내림 bit1 = −단, 화살표 없으면 무효). 퍼포먼스 탭은 단수 위에 작게 `▲4`, 레이스 탭은 GEAR 칸 아랫줄에 `SHIFT ▲4`. 화살표 비트는 DBC 기준(실차 미확인).
>
> **수동 단수 0.3초 유지 (v1.5.3):** 힐앤토·레브매칭 블립 때 RPM이 다른 단 비율 위를 지나가 단수가 깜빡이던 것을 막았다. 같은 판정이 300 ms 이어져야 표시를 바꾸므로, 블립 동안은 이전 단이 유지되고 클러치를 뗀 뒤 새 단으로 바뀐다. 클러치를 계속 밟고 있으면 0.3초 뒤 `—`.
>
> **클러치 밟는 동안 단수 유지 (v1.5.4):** 저속·저rpm에서 클러치를 밟거나 반클러치일 때 `—`가 뜨던 것을 줄였다. `0x09` EMS12 `CLU_ACK`(byte3 bit7)가 동력이 끊겼다는 비트(7DCT 로그에서 P·N 1, D·R·주행 0)라 6MT에선 클러치 스위치로 보고, 이 비트가 1인 채 굴러가는 동안 직전 단을 유지한다. 비트가 한 번이라도 0↔1로 바뀐 뒤에만 쓰므로, 고정이면 v1.5.3과 같다. 정차 중이거나 클러치를 뗀 채 8 km/h 미만이면 그대로 `—`. **6MT 실차에서 클러치 신호 동작 확인(2026-11-01).**
>
> **테마 8가지 + 클러스터 탭 (v1.6, 프리뷰 확인·실차 미확인):** 헤더의 테마 버튼(`N`→`LB`→`M`→`IC`→`MO`→`AM`→`EV`→`NT`)으로 모든 탭 색이 바뀌고 고른 테마는 저장된다. 기본 N은 지금까지와 같은 화면(헤더 아래 픽셀 동일, `preview/BodyDiff.java`로 비교). 아이스·모노·앰버·틸·나이트는 배경이 완전 검정(#000000)인 차량 계기판 색이라 밤에 덜 번진다. 경고색은 테마가 바꾸지 않고, 강조색이 경고 빨강과 가까운 앰버에선 흰 테두리를 더한다. 새 탭 **클러스터**(`ClusterRenderer`)는 클래식·레트로·랠리 3가지이고 다시 누르면 순환한다. 클래식은 좌우 대칭(왼쪽 냉각수·흡기·부스트·가속, 오른쪽 네 바퀴 공기압), 가운데 속도·RPM·큰 단수·주행 모드, 아래 전압·연료소모·평균연비·주행가능거리·주행거리·횡G. 헤더 탭 줄은 좌우로 밀어 넘긴다. 화면은 `TABS.md` 6절·테마 절.
>
> **수동 레브매칭·N 표시 (v1.6.1):** 클러치를 뗀 채 중립에서 레브매칭하면 단수가 튀던 것을 막았다. 새 단은 0.3초 유지에 더해 그동안 **RPM ÷ 속도 비율이 8 % 안에서 안정**해야 표시한다(기어가 물리면 비율이 고정, 중립 블립은 속도는 그대로인데 RPM만 움직임). 기준 8 %는 7DCT 로그에서 기어가 물린 구간이 20 km/h 이상에서 넘는 비율이 2~3 %인 값. 클러치를 뗀 것이 확인되고 맞는 단이 없으면 **N**: 정차 중 시동이 켜져 있을 때, 또는 15 km/h 이상에서 페달 0·1300 rpm 미만·연료가 흐를 때(연료 컷은 엔진 브레이크 = 기어 물림이라 제외; 페달을 뗀 공회전 연료만으로는 중립을 못 가림 — 7DCT에서 기어가 물린 채 페달을 뗀 저회전의 절반이 연료를 씀). 블립 중엔 N 대신 직전 단, 반클러치는 스위치가 초반에 닫혀 "밟음"으로 읽힘. (중립 타력 약 30~36 km/h에서 공회전 RPM이 6단 비율과 맞아 6이 뜨던 한계는 v1.6.3에서 해결.)
>
> **수동 후진 R (v1.6.2):** 후진하면 오너 계기판의 변속 권장 자리에 R이 뜨므로 EMS_H12 `DesCurGr`에 R 코드가 있다(값은 아직 캡처 못 함). 그 값이 **14 또는 7**이고 화살표가 없거나(현대 TCU·변속 레버 신호의 R 코드; 둘 다 6단 수동의 단 번호가 될 수 없음), 후방카메라 화면이 켜지면 **R**을 바로 띄운다. 후진 기어비가 1단과 비슷해 빠른 후진이 1로 보이던 것도 막는다. 실차 확인 전 — 안 뜨면 R 코드가 다른 값.
>
> **SHIFT 신호 모든 레이아웃 · 디젤 목표 범위 · 공회전 목표 중립 (v1.6.3):** 변속 목표 rpm(F1 탭 `−`/`+`)은 F1·레이스 탭만 썼는데, 이제 퍼포먼스·쇼·클러스터(클래식·레트로·랠리) 탭도 목표에 닿으면 화면 테두리가 빨갛게 점멸한다(레이스 탭과 같은 신호). 디젤에서 목표 표시가 6000으로 보이면서 실제로는 4600으로 동작하던 것을 디젤 범위(3000~4600)로 고쳤다. 수동차는 엔진 ECU가 보내는 공회전 목표 rpm(EMS_H12 byte5 ×10)을 읽어, 클러치를 뗀 채 회전수가 목표에 붙어 있는데 속도가 5 % 넘게 변하면 중립(N)으로 본다 — 엔진이 혼자 돌 때는 목표 ±8 rpm 안이고, 기어가 물리면 회전수가 속도를 따라가 목표에서 벗어난다. 중립 타력 30~36 km/h에서 6이 뜨던 한계가 이걸로 풀렸다.
>
> **변속 목표 rpm 범위 확대 (v1.6.4):** 목표 하한 6000은 평소 변속엔 너무 높아 가솔린 3000~6700(기본 6000)으로 넓혔고, 디젤은 같은 범위를 레드라인 비율(4600/6500)로 줄인 2100~4700(기본 4200). LED 15칸은 목표의 마지막 1/4 구간에 나눠 켠다(6000이면 4500부터 칸당 100 rpm으로 이전과 같고, 3000이면 2250부터 칸당 50 rpm) — 목표를 낮춰도 공회전부터 LED가 켜지지 않는다.
>
> **화면 겹침 수정 (v1.6.5):** v1.6.3·v1.6.4에서 변속 신호가 없을 때 퍼포먼스·레이스·기록·쇼·게이지·그래프·클러스터 화면 위에 전체 조망이 겹쳐 그려지던 버그를 고쳤다(전체 조망·F1은 영향 없음). 변속 신호 테두리 코드의 위치만 옮긴 한 줄 수정.
>
> **디젤 기본 변속 목표 (v1.6.6):** v1.6.4에서 디젤 기본 목표를 4200으로 정했지만 실제로는 적용되지 않아, 목표를 한 번도 바꾸지 않은 디젤 차는 4700(상한)으로 동작했다. 이제 저장된 목표가 없으면 차종 기본값(가솔린 6000, 디젤 4200)을 쓴다. 목표를 직접 바꾼 차는 그 값 그대로다.
>
> **전체 점검 수정 (v1.6.7):** 가속 측정은 속도 자료가 1초 넘게 끊기면 버린다(끊긴 사이를 이어 그려 기록을 지어내지 않음). 쇼 화면 기어는 값이 없을 때 "N" 대신 P/R/N/—. 점화시기 원시값 0은 무효라 디젤의 빈 슬롯이 가솔린으로 굳지 않는다. hscan 프레임 하나의 예외로 앱이 죽지 않는다. 없는 순정 서비스 재시도는 30초까지 늘린다. 게이지 범위(기어 7단, 요·변속 권장 ±, 주행거리계), 긴 곡 제목 줄임 최적화와 이모지 보호, F1+음악 영어 라벨 겹침. 설치기(2026-11-01b): 서명 충돌일 때만 기존 앱을 지우고, 삭제 실패를 성공으로 알리지 않으며, 폴더 APK가 고른 버전을 덮어쓸 때 알리고, 기기 이름은 pd_kr과 정확히 일치해야 한다.
>
> **부스트 튐 거름 (v1.6.8):** 악셀을 뗀 뒤 부스트 최고값이 3.62 bar처럼 이 터보로는 불가능한 값으로 남던 문제. −1~+2.5 bar 밖이거나 직전 값에서 0.5 bar 넘게 혼자 튄 샘플은 화면·그래프·최고값에 넣지 않는다(실제 부스트는 샘플 사이에 0.3 bar 이상 움직이지 않음). 같은 값이 두 번 이어지면 정상 변화로 받는다.
>
> **부스트 거름 기준 조정 (v1.6.9):** 세게 밟다 뗄 때 부스트가 빨리 빠지는 실제 하강은 거르지 않는다. 상승만 한 샘플에 0.8 bar 넘게 튈 때 잡는다(풀가속 스풀보다 넉넉한 기준). −1~+2.5 bar 범위 검사는 그대로.
>
> **테스트 버전 — 화면 탭 하나로 통일 (2026-11-01, `beta` 브랜치, 프리뷰 확인·실차 보류):** 머리의 탭을 화면 탭 하나로 줄였다(언어·테마는 톱니바퀴 편집 화면으로). 전체 조망·게이지·그래프·F1·퍼포먼스·레이스·기록·쇼와 레이아웃 17개, 모두 25개 화면이 길게 눌러 여는 목록(6×5)에 모였다. 시계 왼쪽 톱니바퀴를 누르면 편집 모드로 열려 화면마다 `사용`/`미사용`을 고르고, 미사용 화면은 목록과 넘기기에서 빠진다. 지금 화면과 미사용 목록은 `pit_vehicle_ui`(`cluster_screen`, `cluster_hidden`)에 저장돼 재부팅 뒤에도 유지된다. 자세한 내용은 `TABS.md` "화면 목록". 안정화 v1.6.4(변속 목표 범위·모든 레이아웃 SHIFT 신호·6MT 중립)를 합쳤고, 합치면서 v1.6.4의 그리기 순서 버그(변속 신호가 없을 때 퍼포먼스·레이스·쇼·클러스터 위에 전체 조망이 겹쳐 그려짐)를 고쳤다. 택시 레이아웃의 말은 해변을 달리는 말 영상에서 한 보폭(10장면, 0.4초)의 실루엣을 따서 48×28 점으로 옮겼다. 오른쪽 칸은 운행 중엔 카오디오, 운행을 끝내면 영수증. 전체 점검 수정(안정화 v1.6.7 포함): 화면 목록에서 길게 눌러도 아래 게이지·그래프 편집이 안 열림, 계기판을 닫으면 목록·언어 창도 닫힘, 언어를 바꾸면 순정 화면 진입 버튼 글자도 바뀜, 택시 요금 거리·시간 병산과 할증 시각 고정(`TaxiFare`, 테스트 추가)·다른 화면에서도 요금·거리 누적, 노킹 화면이 붐·페달을 점화 시각에 맞춰 읽음, 재생 상태·위치를 모를 때 지어내지 않음. 80년대 화면의 깜빡이 화살표 삭제(오른쪽이 RPM 막대 뒤에 가려졌음). 8비트 두 화면의 차 색을 톱니바퀴 편집 화면 `차량 색` 칸에서 고름(기본 퍼포먼스 블루, 그다음 국내 i30(PD) 출시 색 8가지, `car_paint`에 저장), 추격 시점 뒷모습을 공식 정후면 사진 기준으로 다시 그림. 테마 로즈(RS)·코퍼(CU) 추가(나이트 다음). 고장코드 조회: 톱니바퀴 편집 화면 `진단` 칸 → 엔진·변속기·제동·조향·에어백·바디 제어기의 저장 코드를 읽어(순정 텔레매틱스와 같은 `sendPacket(C7,40,80,40,80|ECU, 19 02 …)`, 거부 시 KWP `18 00 FF 00`, 읽기 전용 — 삭제·세션 변경·보안 해제 안 보냄) 코드·상태·한국어 설명·고장 유형으로 보여 줌(`Dtc`·`DtcReader`·`DtcDialog`, `TABS.md` "고장코드 조회"). 안정화 v1.6.9의 부스트 튐 거름 포함. `dashboard-i30pd-platform.apk`(173,881 B, sha256 `40c44d94…`).
>
> **테스트 버전 — 클러스터 레이아웃 17개 (2026-11-01, `beta` 브랜치, 프리뷰 확인·실차 보류):** 새 14개(택시·80년대·HUD·8비트·8비트 추격 시점·에코·마찰원·야간·드래그·엔진 계기판·카세트·워프·오실로·노킹)와 길게 눌러 여는 목록, 모든 탭의 부스트 막대 범위 자동 확장, 8비트 화면의 낮·노을·밤 하늘과 신호 그대로의 차 조명(새 신호 53 `HEAD_LAMP` = CGW1 `HeadLampLow`). 화면과 동작은 `TABS.md` "클러스터 레이아웃 17개". 이 브랜치를 빌드한 APK는 `test` 사전 배포로 나가고, 설치기에서 "테스트"를 고른 사람만 받는다. 실차 검증이 끝나면 `main`에 합치고 정식 릴리스로 낸다.

> **APK는 이 레포에서 직접 빌드할 수 있다.** 아래 "빌드" 절차대로 빌드·서명하면 Releases의 APK와 같은 결과가 나온다.

## 무엇을 바꿨나 (2단계)

1. **차종 게이트** `HscanDataClient.start()`: `jsn_kr`/`eng.vmadmin.20250902.081552` → **`pd_kr`만 확인**. 슬롯 배치는 `eng.vmadmin.20260114.084130`(실차 getprop)에서 검증했다. 다른 `pd_kr` 빌드도 읽되 logcat `VehicleDash`에 `Unverified pd_kr build …` 경고를 남긴다. 수신 전용이라 차에는 영향이 없고, 빌드가 다르면 값이 틀릴 수 있다.
2. **프레임 디코더 화이트리스트** `HscanFrameDecoder.expectedSize()`: JSN 슬롯(0x07/0x23/0x53/0x5a/0x6e) 대신 PD 슬롯만 통과 — `0x02` `0x03` `0x20`(4·4·6B) `0x04` `0x08` `0x09` `0x0a` `0x0b` `0x0c` `0x0e` `0x13` `0x17` `0x1b` `0x1d` `0x1f`(8B) `0x19`(5B) `0x12`(7B) `0x21`(6B).
3. **PD 디코더** `Telemetry.hscanPd()` (JSN용 `hscanJsn`은 그대로 둠, PD 경로만 호출):

   | 슬롯 | 신호(Telemetry 인덱스) | 공식 |
   |---|---|---|
   | 0x02 CLU11 | 7 차속 | `(b1 \| (b2&1)<<8)×0.5 + ((b0>>6)&3)×0.125` km/h |
   | 0x08 EMS11 | 0 RPM | `(b2 \| b3<<8)×0.25` |
   | 0x0a EMS14 | 26 배터리 전압 + 대기압(부스트 기준) | `b3×0.1` V (60~200 유효), `AMP_CAN = (b0>>3)×10.731613+458.98` mmHg ×1.33322 → hPa |
   | 0x13 ESP12 | 10 브레이크 압력 / 11 횡G / 12 종G | `CYL_PRES 26\|12 ×0.1 bar`, `LAT/LONG_ACCEL 11bit ×0.01−10.23 m/s² ÷9.80665`, DIAG 비트 → 무효 |
   | 0x19 SAS11 | 13 조향각 / 27 조향속도 | `s16(b0\|b1<<8)×0.1°` (좌 +), `b2×4` |
   | 0x1d | **30 변속 레버(신설 `GEAR_SELECTOR`)** | LVR12 `CF_Lvr_Gear` `b1&15`: 0→P(0) 7→R(1) 6→N(2) 5→D(3) **8→M(4, 수동 게이트)** — 예전엔 `b1&7`이라 수동이 P로 보였음 |
   | 0x1f | 8 EMS 기어(현재 단수) | `b2>>4` 1~7 유효(7DCT, D·수동 모두) (R=0xe, P/N=0 무효) |
   | 0x10 | 52 변속 권장 · 수동 판별 | EMS_H12 `CF_Ems_DesCurGr` @50\|4 (byte6 bit2~5) = 계기판 변속 권장 단수, `UpTarGr`/`DownTarGr`(bit0/1) 화살표. 0이 아니면 6단 수동. 단수로는 안 씀(수동 단수는 RPM ÷ 속도). 7DCT에선 항상 0 |
   | 0x09 EMS12 | 2 냉각수 · **1 유온(냉각수 대체)** · **9 가속 페달**(악셀 밟은 깊이) | `b1×0.75−48` °C (워밍업 63.8→85.5 실측), 페달 `b6×0.390625 %` (부하와 r=0.90). byte7 ENG_VOL=1.6 L로 프레임 확정 |
   | 0x01 CGW1 | **45 운전석 문** · **46 와이퍼** · 47 조수석 문 · 48 트렁크 · 49 후드 · **50 좌 방향지시** · **51 우 방향지시** | 문 `b1 bit0` (닫힘 0x04/열림 0x05), 와이퍼 `b3 bit0-2` (off 0/LOW 1/HIGH 2, bit7 전조등 제외). 운전석 문·와이퍼는 10/30 바디 태깅 캡처로 확정. 조수석 문 `b4 bit3`·트렁크 `b1 bit4-5`·후드 `b2 bit1-2`는 opendbc CGW1 위치(같은 프레임 7개 필드가 캡처와 일치), 실제 열림은 아직 미캡처. 방향지시 좌 `b2 bit3`·우 `b7 bit6`(opendbc `TurnSigLh/Rh`, 10/30 태깅 확정 — 좌 `0x61→0x69`, 우 `b7 0x40`): 램프와 함께 점멸하는 비트라 레이스 탭 좌우 끝 화살표가 앰버로 깜빡인다. 게이지에는 OPEN/CLOSED·OFF/LOW/HIGH·ON/OFF 글자로 표시 |
   | 0x0e EMS19 | 17 부스트 | `(((b2>>4)\|b3<<4)×1.322 − 대기압 hPa)/1000` bar — 공회전 −0.02, 주행 최고 +0.89 bar(부하 70%). 대기압 수신 전엔 무효 |
   | 0x20 | 18~21 공기압 FL/FR/RL/RR | `b2~b5` psi, `b1==1`일 때만(센서 보고 전 ff). 33~34 관측 vs 계기판 35 |
   | 0x0b | 29 흡기온 | `b2×0.75−48` (opendbc IntAirTemp) — 3캡처 교차검증 |
   | 0x03 | 31 주행거리(신설) | `u24le×0.1 km` (opendbc CLU12 CF_Clu_Odometer) — 실차 103374.9 km |
   | 0x04 CLU13 | **34 주행 모드(신설 `DRIVE_MODE`)** | `CF_Clu_DrivingModeSwi` `b2&3`: 0 NORMAL · 1 SPORT · 3 ECO (2는 미관측 → 무효) |
   | 0x12 EPB11 | **35 주차 브레이크(신설 `PARK_BRAKE`)** | `EPB_STATUS` `(b2>>3)&7`: 1 해제→0, 2 체결→1, 그 밖은 무효 |
   | 0x09 EMS12 | **36 스로틀 개도** | `TPS` `b5×0.4694836−15.0234742 %` (페달과 별개, 스로틀 밸브 개도). F1 탭에 가속 페달과 **나란히 병기**(악셀 19.9 % vs 스로틀판 30.5 % 실측) |
   | 0x21 EMS20 | **37 순간 연료소모** | `FCO` `u16le×0.128 µL`를 10 ms마다 분사량으로 보고 ×0.36 → **L/h** (공회전 0.4~0.8, 주행 최고 30) |
   | 0x04 CLU13 | **38 주행가능거리 · 39 평균 연비** | `CF_Clu_DTE` `b3\|(b4&3)<<8` km (1023 무효), `CF_Clu_AvgFCI` `((b0>>6)\|b1<<2)×0.1` **km/L** (0x3ff 무효, 단위 플래그 `AvgFCU`≠0이면 무효) |
   | 0x08 EMS11 | **40 보정 토크 · 41 마찰 토크** | `TQI_ACOR` `b1×0.390625 %`, `TQFR` `b5×0.390625 %` |
   | 0x0c EMS16 | **42 점화 시기** | `SPK_TIME_CUR` `b6×0.375−35.625 °` (공회전 −3.8, 정속 31.5, 고부하 2.3; 연료 컷 중엔 의미 없음) — 레이스 탭 "SPARK" 칸 |
   | 0x1b TCS13 | **43 브레이크 스위치** | `DriverBraking` `b6 bit7` (ON/OFF, 브레이크압 >3 bar와 98% 일치) |
   | 0x17 MDPS11 | **44 조향 토크** | `CR_Mdps_DrvTq` `((b1>>3)\|b2<<5)&0xfff ×0.01−20.48 Nm` (부호 미확인) |

   **재확정(2026-11-01)**: 한때 철회했던 스로틀·부스트와 "없음"이던 냉각수를 주행 로그 DBC 재검증으로 되살림 (`HSCAN_DECODE.md` 맨 아래 절).
   **여전히 "—"**: 미션유온·SOC·WHL_SPD11·TCS/ABS. **유온**은 감마 1.6 T-GDI에 오일온 센서가 없어(EMS19 `CR_Ems_EngOilTemp` 항상 0; 연료온 `TEMP_FUEL`·미션온 `TEMP_AT`도 비고 `ModeledAmbTemp`는 23.0 고정이라, hscan에서 실제로 움직이는 온도는 냉각수·흡기 둘뿐) F1·대칭·퍼포먼스 탭은 그 자리에 흡기 온도를 보여 준다. 신호 1번 자체는 실린더 헤드 냉각수 센서(ECT, `TEMP_ENG`) 값으로 대체하고, 화면 이름표에 "냉각수 대체"·"(대체)"·"OIL (ECT)"로 표시한다. 고부하에서 실제 오일은 냉각수보다 뜨겁다. opendbc(commaai + ajouatom 포크) 대조로도 대응 슬롯 없음.
   **기준 DBC**: commaai/opendbc `hyundai_2015_ccan.dbc` + ajouatom/openpilot `hyundai_kia_generic.dbc`. 브레이크압 = openpilot ESP12 CYL_PRES 동일 값.
   **속도 "—" 깜빡임 수정**: OEM 속도 이벤트(단위 미상)가 hscan 값을 지우던 것 → hscan 차속 수신 후엔 OEM 이벤트 무시(`Telemetry.speed`).
4. **점화 상태 폴백** `VehicleDataClient`: PD에 `BCMManager`(IGN 상태)가 없으면 hscan 구독이 영영 안 시작되므로, BCM 서비스 획득 실패 시 점화 ON으로 간주(`ignitionUnknown`). hscan은 수신 전용이라 안전.
5. **주행 모드·주차 브레이크 표시**: 게이지 탭에서 두 신호를 골라 `SPORT`·`ON`처럼 글자로 볼 수 있다(`Telemetry.label`). 처음 넣었던 클러스터 대칭 화면은 2026-10-30에 앱에서 뺐다(같은 정보는 레이스 탭, 보관 소스 `i30pd_port/cluster/`).
6. 번역 `translations/en.json`에 "변속 레버"(Shift lever) 등 추가 → `UiText.java` 재생성. 테스트: 디코더 화이트리스트 갱신, 실차 바이트 고정값으로 PD 디코더를 검사하는 `tests/HscanPdTelemetryTest.java` 신설(v1.6.9 기준 PD HSCAN 검사 186건, 전체 Java·Python 시험 통과).
7. **화면 차종 문구**: 상단 `N / PIT` → `i30 PD`, F1 기어 패널 `V E L O S T E R  N` → `i 3 0  P D`, F1+음악 `VELOSTER N` → `HYUNDAI i30` (`PitRenderer`). 앱 이름 `N Dashboard`는 그대로.

## 빌드 (노트북, Git Bash)

```sh
git clone https://github.com/sung9155/i30-pd-dashboard && cd i30-pd-dashboard
# 테스트 빌드는: git checkout beta
JAVA="<JDK 17 설치 경로>/bin/java.exe"   # 예: /c/Program Files/Eclipse Adoptium/jdk-17…/bin/java.exe
python -X utf8 scripts/generate_translations.py
python -X utf8 scripts/test.py --java "$JAVA" && python -X utf8 -m unittest discover -s tests -p 'test_*.py'
python -X utf8 scripts/build.py dashboard --java "$JAVA" \
  --android-jar /d/Android/sdk/platforms/android-17/android.jar \
  --aapt2 /d/Android/sdk/build-tools/34.0.0/aapt2.exe \
  --r8-jar /d/Android/sdk/build-tools/34.0.0/lib/d8.jar --out build/dashboard
# AOSP platform 테스트 키 (공개): android.googlesource.com platform/build android-4.2.2_r1 target/product/security/platform.{pk8,x509.pem}
#   (testkey.{pk8,x509.pem} 는 PD가 거절함)
BT=/d/Android/sdk/build-tools/34.0.0
"$BT/zipalign.exe" -f -p 4 build/dashboard/dashboard-UNSIGNED.apk build/dashboard/dashboard-aligned.apk
"$BT/apksigner.bat" sign --key build/keys/platform.pk8 --cert build/keys/platform.x509.pem \
  --v1-signing-enabled true --v2-signing-enabled false --v3-signing-enabled false --min-sdk-version 17 \
  --out build/dashboard/dashboard-i30pd-platform.apk build/dashboard/dashboard-aligned.apk
```

## 내 앱 (My Apps) — 가장자리 버튼으로 대시보드 열기

업스트림의 보조 앱 `apps/hub`(`com.veloster.carplayhub` v10 `1.9-i18n`, "My Apps companion")에도 PD용 수정을 넣었다. 대시보드와 별도 APK다.

1. **가장자리 버튼을 누르면 대시보드가 바로 열린다.** 48 dp 버튼이고 글자는 `계기판`(영어 `Dash`)이다. 대시보드가 없으면 '내 앱'이 열린다. 끌면 좌우 가장자리에 붙고 높이가 저장된다. 상시 알림을 누르면 '내 앱'이 열린다. 업스트림은 버튼이 '내 앱'을 열었다.
2. **"연결 점검"(시험 핫스팟) 버튼을 뺐다.** 벨N에서도 AP 실패로 끝난 실험이다.
3. 버튼 문구 `N 계기판` → `계기판`.
4. 업스트림 테스트 `test_quick_entry_runtime.py`에 "가장자리 버튼이 대시보드를 연다" 검사를 추가했다(PASS 17, 호스트 스텁). 실차에서는 아직 검증하지 않았다.

PD 펌웨어 대조(공개 펌웨어를 받아 확인, 이 레포에는 미포함):
- **엔지니어링 모드**: `com.hkmc.system.app.engineering/.EngineeringModeMainActivity`가 있고 MAIN 인텐트 필터가 있어 외부에서 실행된다. 숨은 탭과 암호 화면을 거치지 않으므로 **진단 메뉴를 함부로 건드리지 말 것.**
- **USB 디버깅**: 이 버튼이 쓰는 `com.hkmc.misc.MiscManager.setSystem(5/6)`를 PD 순정 엔지니어 모드도 쓴다. 상태 표시(`com.hkmc.Feature`·`persist.sys.dipo.state`)는 PD에서 "확인 불가"로 나올 수 있다. OFF를 누르면 ADB가 끊긴다.
- 강제 재시작(`REBOOT`), 다른 앱 위 표시(`SYSTEM_ALERT_WINDOW`, `TYPE_PHONE`)는 platform 키 서명이라 권한이 있다.

빌드·설치(노트북, 위 "빌드"와 같은 클론·도구):

```sh
python -X utf8 scripts/build.py hub --java "$JAVA"   --android-jar /d/Android/sdk/platforms/android-17/android.jar   --aapt2 /d/Android/sdk/build-tools/34.0.0/aapt2.exe   --r8-jar /d/Android/sdk/build-tools/34.0.0/lib/d8.jar --out build/hub
"$BT/zipalign.exe" -f -p 4 build/hub/hub-UNSIGNED.apk build/hub/hub-aligned.apk
"$BT/apksigner.bat" sign --key build/keys/platform.pk8 --cert build/keys/platform.x509.pem   --v1-signing-enabled true --v2-signing-enabled false --v3-signing-enabled false --min-sdk-version 17   --out build/hub/myapps-i30pd-platform.apk build/hub/hub-aligned.apk
adb install -r build/hub/myapps-i30pd-platform.apk
adb shell am start -n com.veloster.carplayhub/.HubActivity
```

'내 앱'에서 **실행 바로가기 → 가장자리 버튼 + 알림 → 저장**을 고른다. 기본값은 OFF이고 설치만으로는 켜지지 않는다. 그다음 HOME에서 가장자리 `계기판` 버튼으로 대시보드가 열리는지 확인한다. 실차에서 볼 것:
- 버튼이 보이는지
- 후방카메라·순정 조작부를 가리지 않는지
- 정차 상태에서 정상 재시동한 뒤에도 버튼이 복원되는지

복원은 버튼만 되고 앱 화면은 자동으로 열리지 않는다(업스트림 설계).

## ADB로 직접 설치·확인 (2026-10-29 첫 실차 설치·실행 성공)

```sh
export MSYS_NO_PATHCONV=1
adb devices -l                                   # pd_kr device 확인 (재부팅 후면 Factory Test 다시)
adb install -r dashboard-i30pd-platform.apk
adb shell am start -n com.veloster.dashboard/.DashboardActivity
adb logcat -s VehicleDash NDashboard             # 5초 주기 진단: 0:RPM 7:차속 13:조향각 26:전압 30:레버 가 valid 인지
```

- 설치 거절(`INSTALL_PARSE_FAILED_INCONSISTENT_CERTIFICATES`)은 서명 키 문제 → platform 키로 서명했는지 확인(위 주의).
- 첫 실행 logcat(2026-10-29): `VehicleDash` 진단 `0:64/6ms/valid 7:68 8:64 10:64 11:64 12:64 13:64 26:64 27:64 30:64/valid` — 5초에 64프레임(~13Hz), RPM·차속·기어·브레이크압·횡/종G·조향각·전압·조향속도·변속 레버 모두 valid. `NDashboard: Subscription unavailable` 로그 없음.
- `NDashboard: Subscription unavailable: power/NMode/speed` 로그는 PD에 해당 OEM 서비스가 없다는 뜻이며 hscan 값과 무관(폴백으로 계속).
- 화면에 값이 안 뜨면: `adb shell ps | grep veloster`로 실행 확인 → hscan 소켓은 `car_session.sh`로 별도 확인 → logcat `VehicleDash` 진단 줄에서 카운트가 0이면 게이트(product/incremental)부터 의심.
- 벨N 측 설치 도구 `scripts/install.py`·`menu-entry/profile.json`은 `jsn_kr` 고정이라 PD에선 쓰지 않는다.
