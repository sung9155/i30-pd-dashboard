# 시동 안내 화면 1초 (SystemUI 패치)

시동 때 뜨는 순정 안내 화면("실제 교통 법규를 준수하여 운전해 주십시오…")은 10초 뒤 저절로 닫힌다. 이 패치는 그 10초를 약 1초로 줄인다.
화면이 뜨는 것, 부팅 애니메이션을 닫는 것, 동의 기록, 퀵부트 처리는 순정 그대로다.

차주는 PC 설치기의 **4) 시동 안내 화면 10초 → 1초**로 설치하고 **5) 원래대로**로 되돌린다(Windows `설치.cmd`, macOS·리눅스 `설치.command`).
휴대폰 설치 앱에는 없다(고치는 데 Java 도구가 필요하다).

## 왜 PC에서 직접 고치나

순정 SystemUI는 현대 코드라 고친 APK를 배포하지 않는다. 설치기가 그 차에서 원본을 받아 **해시가 확인된 빌드일 때만** 이 PC에서 고쳐 설치하고,
받은 원본과 결과는 끝나면 PC에서 지운다. 배포하는 것은 우리 코드(빌더와 훅 클래스 둘)뿐이다.

- 확인된 빌드: **V141**(우리 차 HYUNDAIA, `HKMC_SystemUI.apk` `9a5f71ba…` / `.odex` `2b4a2b62…`). 다른 빌드는 아무것도 바꾸지 않고 멈춘다(종료 코드 3).
- 2026-11-23 실차 설치, 순정 10초 → 약 6초(런처가 뜰 때까지 기다린 뒤) 닫힘 확인. 후방 카메라·검은 화면이 먼저 떠 있으면 순정처럼 그게 끝난 뒤 1초.
- **내비 소프트웨어 업데이트 전에 반드시 되돌린다.** 업데이트 뒤 옛 펌웨어용으로 고친 SystemUI가 남아 있으면 내비가 이상해질 수 있다.

## 원리

- 안내 화면 = `automotive.jar`의 `com.hkmc.internal.consentscreen.ConsentScreen`. SystemUI `StatusBarService.onIntentReloadSettings()`가 동의 전이면 만든다.
  10초는 프레임워크에 박힌 상수(`DELAY_AUTO_CLOSE = 10000`)라 직접 못 바꾼다. 자동 닫힘은 `mHandler.sendEmptyMessageDelayed(1, 10000)`이고 메시지 1은 순정 `confirm()`.
- 고친 SystemUI는 안내 화면을 만든 직후 `ConsentFast.hook()`([`src/.../ConsentFast.java`](src/com/android/systemui/statusbar/ConsentFast.java))으로
  그 private `mHandler`를 같은 looper·callback의 Handler로 바꿔 끼운다. 지연된 메시지 1만 1초로 당기고 나머지는 그대로. 실패하면 순정 handler가 남는다.
- 바뀐 곳: `StatusBarService` 한 줄 + 새 클래스 `ConsentFast`. **versionCode 17 → 18**(같으면 부팅 검사가 업데이트를 지운다).
- SystemUI는 공개 AOSP 4.2.2 테스트 키(`c8a2e9bc…`)로 서명돼 있어 같은 키로 업데이트 설치가 된다. 되돌리기 = `adb uninstall com.android.systemui`(업데이트만 지워진다).

## 빌더 (`i30pd-consent-fast.jar`, 릴리스 자산)

```
java -jar i30pd-consent-fast.jar <capture> <tools> <out.apk>
```
- `capture`: `HKMC_SystemUI.apk` + `HKMC_SystemUI.odex`(`/system/app`), `framework/<11개>.odex`(`/system/framework`).
- `tools`: `baksmali.jar`·`smali.jar`(2.5.2), `apksig.jar`(8.3.0, 이 jar 옆), `platform.pk8`·`platform.x509.pem`. 설치기가 해시 고정으로 받는다.
- 순서: 해시 확인 → odex 풀기(baksmali) → 한 줄 넣기 + 훅 smali → 다시 묶기(smali) → versionCode 18 → v1 서명(apksig, minSdk 17) → 4바이트 정렬.
  작업 폴더는 끝나면 지운다. 종료 코드: 0 만듦 · 2 파일 없음 · 3 모르는 빌드 · 4 코드가 예상과 다름 · 5 도구 실패.
- 만들기: `python scripts/build_consent_fast.py`. 훅 smali(`hook/`)는 `ConsentFast.java`를 r8 8.3.37 D8(`--min-api 17`)과 baksmali `d -a 17`로 만든 것.
- 확인: 실차에 깔린 결과와 `classes.dex`를 baksmali로 풀어 비교하면 똑같다(smali 어셈블러는 바이트가 실행마다 조금 달라 해시는 다르다).
  V141 에뮬레이터에서 설치(`/data/app`, versionCode 18)·되돌리기 확인. 설치기는 `installer/test/run.sh win|mac|linux`(실제 원본 캡처가 있을 때 `BOOT_CAP` 등으로)로 시험한다.
