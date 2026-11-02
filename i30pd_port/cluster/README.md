# 대칭 계기판 화면 (esp32CarInfo UI 이식)

esp32CarInfo `firmware/ui/ui.c`의 **대칭(Symmetric) 레이아웃**(`dash_build` case 0, Default 테마)을
원본 앱의 Android `Canvas` 렌더러로 다시 그린 것이다. 두 화면 모두 800×480이라 격자 치수·색·동작을 그대로 옮겼다.
원본 앱 상단 내비(52px) 아래 428px 영역에 그리므로 ESP32(444px)보다 중앙 행이 16px 낮고, 속도 숫자는 100→88px로 줄였다.

> **보관용:** 이 탭은 2026-10-30에 앱에서 삭제했다(`ClusterRenderer`·`PAGE_SYMMETRIC` 제거, 같은 정보는 레이스 탭). 현재 소스에는 들어 있지 않다. 다른 탭은 `../app/TABS.md`.

## 파일

| 파일 | 용도 |
|---|---|
| `ClusterRenderer.java` | 렌더러. 원본 클론 `shared/src/com/veloster/vehicle/`에 복사 |
| `cluster.patch` | 원본 3개 파일 수정: 내비에 **대칭** 탭 추가(`PitRenderer`·`VehicleDash`), 영문 번역 키(`translations/en.json`) |
| `../app/preview/` | Android 없이 모든 탭을 PNG로 그려 보는 도구(Java2D 대역 클래스). 앱에는 들어가지 않음 |

## 적용 (노트북, 원본 클론에서)

```sh
cp <이 저장소>/i30pd_port/cluster/ClusterRenderer.java shared/src/com/veloster/vehicle/
git apply <이 저장소>/i30pd_port/cluster/cluster.patch
python3 scripts/generate_translations.py          # en.json → UiText.java 재생성
python3 scripts/test.py --java <jdk17>/bin/java
python3 -m unittest discover -s tests -p 'test_*.py'
python3 scripts/build.py dashboard ...            # docs/BUILD.md 그대로
```

내비 버튼 배치가 바뀐다: 전체 조망 144–232 · F1 238–292 · **대칭 298–358** · 게이지 364–430 · 그래프 436–528 (터치 범위도 같이 수정).

## 값 연결 (Telemetry 인덱스)

| 화면 | 소스 | PD 예상 |
|---|---|---|
| RPM 바·숫자 (0–8000, 7000부터 레드) | 0 ← hscan 0x08 EMS11 | ✅ |
| BOOST (−0.8~+1.5 bar, 게이지압) | 17 ← hscan 0x0e EMS19 부스트압 − 0x0a EMS14 대기압 | ✅ 공회전 −0.02, 주행 최고 +0.89 bar |
| 부하율 | 0x08 EMS11 `TQI` | ✅ |
| 속도·사각 링 (최대 240) | 7 ← 0x02 CLU11 | ✅ |
| PRND | 8 ← 0x1d 셀렉터(LVR12, 수동 M 포함) + 0x1f 현재 단수 | ✅ |
| 주행 모드 · 주차 브레이크 Ⓟ | 34 ← 0x04 CLU13 · 35 ← 0x12 EPB11 | ✅ |
| G-볼 | 11 횡가속 · 12 종가속 ← hscan 0x13 ESP12 | ✅ |
| BRK / ACC | 10 브레이크 압력 ← 0x13 ESP12 `CYL_PRES` · 9 페달 ← 0x09 EMS12 `PV_AV_CAN` | ✅ 페달 r=0.90 (부하 대비) |
| 유온 · 냉각수 | 1 · 2 ← 0x09 EMS12 `TEMP_ENG` | 냉각수 ✅ 63.8→85.5°C 워밍업 실측 · 엔진오일온 — (EMS19 b4 항상 0) |
| 미션유온 · 점화시기 | 소스 없음 (TCU 0x1f TEMP_AT 바이트 비어 있음) | — |
| 흡기온 · BATT | 29 · 26 ← hscan 0x0b · 0x0a | ✅ |

값이 없으면 "—"와 빈 바로 둔다(값을 지어내지 않음). 실차 덤프에서 새 소스가 나오면 `CELL_SIGNAL`·`row(...)` 연결만 바꾸면 된다.

## 미리보기

```sh
bash i30pd_port/app/preview/preview.sh <업스트림 클론(패치 적용)>   # → i30pd_port/app/screens/*.png (모든 탭)
```

JDK만 있으면 된다. 렌더러·`Telemetry`·`UiText`는 업스트림 실제 소스를 쓰고 `android.graphics`만 Java2D 대역이다. 실차 스크린샷과 배치 일치 확인.

## 확인 안 된 것

- 실제 `android.jar`·원본 `Telemetry`로 컴파일하는 것은 이 PC에서 못 했다(SDK 없음). 노트북 빌드에서 확인.
- 폰트: ESP32는 숫자에 Montserrat Medium, 앱은 기기 기본 폰트(Roboto). 숫자 모양이 조금 다르다.
- 테마는 Default 하나만. 퀵 패널·제스처·부스트 글로우·변속 팝 애니메이션은 옮기지 않았다.
- 실차 화면·터치는 미검증.
