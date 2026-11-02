# 노트북 준비물 (실차 연결용)

실차 ADB 연결 + `hscandump` 빌드에 필요한 것. Windows 기준(맥/리눅스는 하단 메모).

> **이 노트북 현황(2026-10-28): 소프트웨어 준비 완료.** 아래 "현황" 표 참고.
> 남은 것은 실차에서만 되는 두 가지 — ① Windows USB 드라이버 수동 지정(6절), ② 덤프(7절).

| 도구 | 용도 | 필수? |
|---|---|---|
| ADB (Platform-Tools) | 실차 연결·앱 설치·로그 | **필수** |
| JDK 17 | `javac`(덤퍼 컴파일) | **필수** |
| android.jar (API17) | 덤퍼 컴파일 bootclasspath | **필수** |
| d8 (SDK build-tools) | `.class` → `.dex` | **필수** |
| git / gh | 저장소 받기 | 필수 |
| Python 3 | (선택) 펌웨어 추가 분석 | 선택 |

---

## 이 노트북 현황 (2026-10-28 점검)

| 항목 | 상태 | 위치 / 비고 |
|---|---|---|
| ADB 35.0.2 | ✅ PATH | `D:\Android\platform-tools\adb.exe` |
| JDK 17 (Microsoft 17.0.20) | ✅ | `JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot` |
| android.jar API17 | ✅ 이번에 설치 | `D:\Android\sdk\platforms\android-17\android.jar` (`sdkmanager "platforms;android-17"`) |
| d8 / build-tools 34.0.0 | ✅ 기존 | `D:\Android\sdk\build-tools\34.0.0\d8.bat` |
| sdkmanager | ✅ 기존 | `D:\Android\sdk\cmdline-tools\latest\bin` |
| `hscandump.jar` | ✅ 빌드됨 | `i30pd_port/hscandump.jar` (3.4KB, classes.dex 1개, min-api 17) |
| adb RSA 키 | ✅ 기존 | `~/.android/adbkey`, `adbkey.pub` |
| Python 3.11 + `ext4` | ✅ | 펌웨어 `system.ext4` 분석 가능 (`pip install ext4` 완료) |
| Google USB 드라이버 | ✅ 스토어 등록됨 (2026-10-29) | `D:\Android\usb_driver\android_winusb.inf` → `pnputil /add-driver` 완료. **INF에 PID D00D 없음 → 자동 매칭은 여전히 안 됨, 장치 관리자 수동 지정 필요** (6절) |
| 원본 저장소 | ✅ clone + 빌드 확인 | `D:\Utility\veloster-n-dashboard` ← `reikop/veloster-n-dashboard` (MIT) |
| 인포 연결 이력 | ❌ 없음 | 레지스트리에 `VID_18D1` 장치 기록 없음 → 첫 연결 때 드라이버 수동 지정 필요 |

`build_dump.sh`는 이제 `ANDROID_JAR`/`D8` 없이도 `ANDROID_HOME` → `%LOCALAPPDATA%\Android\Sdk` → `D:\Android\sdk` 순으로 자동 탐지한다.

---

## 1. ADB (Android SDK Platform-Tools)

- https://developer.android.com/tools/releases/platform-tools 에서 OS별 zip 다운로드.
- 압축 풀고 그 폴더를 PATH에 추가.
- 확인: `adb version`
- 성공 사례 USB 이름 **Telechips / DiPO** (VID `0x18d1` / PID `0xd00d`).

## 2. JDK 17

- Temurin: https://adoptium.net/temurin/releases/?version=17
- 확인: `java -version` → `17.x`

## 3. Android SDK — android.jar(API17) + d8

둘 다 SDK에 들어 있다. **방법 A(가벼움)** 권장.

### 방법 A — command-line tools만 (Android Studio 불필요)
1. https://developer.android.com/studio#command-line-tools-only 에서 commandline-tools 다운로드.
2. 압축 풀고 `cmdline-tools/latest/bin` 으로 배치 후:
   ```sh
   sdkmanager "platform-tools" "platforms;android-17" "build-tools;34.0.0"
   ```
   - `platforms/android-17/android.jar` ← ANDROID_JAR
   - `build-tools/34.0.0/d8(.bat)`      ← D8
   - `platform-tools/adb`               ← 1번 대체 가능

### 방법 B — Android Studio가 이미 있으면
- SDK Manager에서 **Android 4.2 (API 17)** 플랫폼 + 아무 **Build-Tools** 설치.
- 경로(기본): `%LOCALAPPDATA%\Android\Sdk`
  - `platforms\android-17\android.jar`, `build-tools\<ver>\d8.bat`

> API 17 플랫폼은 구버전이라 SDK Manager 목록 하단에 있다. 반드시 **android-17**.
> `sdkmanager`가 "deprecated" 경고를 내도 동작한다(2026-10 기준).

## 4. git

- 저장소 받기:
  ```sh
  git clone https://github.com/sung9155/i30-pd-dashboard
  cd i30-pd-dashboard
  ```

---

## 5. 설치 확인 + 덤퍼 빌드

```sh
adb version
java -version
# SDK가 흔한 위치(D:\Android\sdk 등)에 있으면 환경변수 없이 바로:
bash i30pd_port/build_dump.sh
# 다른 위치면:
export ANDROID_JAR="$HOME/android-sdk/platforms/android-17/android.jar"
export D8="$HOME/android-sdk/build-tools/34.0.0/d8"
bash i30pd_port/build_dump.sh
# → i30pd_port/hscandump.jar 생성되면 준비 끝
```

Windows PowerShell이면:
```powershell
$env:ANDROID_JAR="C:\Users\<you>\AppData\Local\Android\Sdk\platforms\android-17\android.jar"
$env:D8="C:\Users\<you>\AppData\Local\Android\Sdk\build-tools\34.0.0\d8.bat"
bash i30pd_port/build_dump.sh   # Git Bash에서
```

빌드되면 [`INSTALL.md`](../INSTALL.md) 1~3단계(ADB 진입) 뒤 `car_session.sh`로 덤프한다.

---

## 6. Windows USB 드라이버 — 첫 연결 전 필독

**근거(펌웨어 boot.img ramdisk `init.rc`):** 인포가 USB debug를 켜면 `sys.usb.config=adb`가 되고
init이 VID `18D1` / PID **`D00D`**, 단일 인터페이스(adb만)로 USB를 다시 올린다.
(`mass_storage,adb`면 PID `DEED`, `accessory,adb`면 `2D01`.) 성공 사례 "Telechips / DiPO 18d1:d00d"와 일치.

Google `android_winusb.inf`(2014, v11)에는 `VID_18D1&PID_D001`만 있고 **D00D가 없다.**
그래서 Windows가 드라이버를 자동으로 잡지 못하고 장치 관리자에 "Telechips"/"DiPO"/"알 수 없는 장치"로 뜬다.
`adb devices`에는 안 보인다. 이 상태에서 아래를 한 번 해 주면 된다(관리자 권한, 차 안에서 1회).

**(선택, 미리 해 두면 편함) 드라이버 스토어 등록** — UAC 창에서 "예":
```powershell
powershell -ExecutionPolicy Bypass -File i30pd_port\windows\install_usb_driver.ps1
```
등록되면 아래 2단계에서 "디스크 있음" 없이 목록에서 바로 고를 수 있다.

**실차 절차**
1. 인포에서 Factory Test `0A 01` → `65 01` → `0A 02`([`INSTALL.md`](../INSTALL.md) 2단계) 후 케이블 연결.
2. 장치 관리자 → 느낌표 달린 장치(속성 → 세부 정보 → 하드웨어 ID가 `USB\VID_18D1&PID_D00D`) 우클릭
   → **드라이버 업데이트** → **내 컴퓨터에서 드라이버 찾아보기** → **컴퓨터의 사용 가능한 드라이버 목록에서 직접 선택**
   → (모든 장치 표시) → 제조업체 **Google, Inc.** → **Android ADB Interface** → 다음
   → "호환되지 않을 수 있음" 경고 → **예**.
   - 목록에 Google이 없으면 **디스크 있음** → `D:\Android\usb_driver\android_winusb.inf`.
3. `adb kill-server` → `adb devices -l` → 상태 `device`면 끝. (`unauthorized`가 뜨면 8절.)

**대안:** Zadig(https://zadig.akeo.ie) — 장치 선택 → WinUSB 설치. 서명 문제 없음. 인터넷이 필요하니 미리 받아 둘 것.
**하지 말 것:** INF를 열어 PID 줄을 추가하는 것 — 카탈로그 서명이 깨져 Windows 11에서 설치 거부된다.

---

## 7. 실차 세션 스크립트 (한 방)

```sh
bash i30pd_port/car_session.sh 30        # 30초 관찰
bash i30pd_port/car_session.sh 30 raw    # 전체 프레임 hex까지
ADB_SERIAL=<일련번호> bash i30pd_port/car_session.sh 30   # 장치가 여러 개일 때
```

하는 일(전부 수신 전용, 설치/설정 변경 없음):
1. `adb devices -l` → 없음/`offline`/`unauthorized` 분기별 안내 후 중단.
2. `getprop` 식별: `ro.product.name`, `ro.build.version.sdk`, `ro.secure`, `ro.adb.secure`, `sys.usb.config` 등 + `id`.
3. `hscandump.jar` 푸시 → `app_process`로 덤프 실행.
4. 전부 `i30pd_port/logs/session_<시각>.log`, 덤프만 `logs/hscandump_<시각>.log`에 저장(gitignore됨).

---

## 8. adb 동작 메모 (펌웨어 근거 — 실차에서 확인할 것)

추출 펌웨어(`update.zip` → `boot.img` ramdisk `default.prop`/`init.rc`, `system.ext4` `build.prop`)에서 확인한 값.

| 항목 | 값 | 의미 |
|---|---|---|
| `ro.secure` / `ro.debuggable` / `ro.build.type` | `1` / `0` / `user` | adb 셸은 비루트(`shell`). `/system` 쓰기 불가. 덤퍼는 `app_process`라 루트 불필요 |
| `ro.adb.secure` | **미설정** | 4.2.2 adbd 기본값 0 → RSA 승인창 없이 바로 `device` 기대. `unauthorized`면 케이블 재연결 + `adb kill-server` 1회 |
| `service adbd` | `disabled` | `sys.usb.config`에 `adb`가 들어갈 때만 `start adbd`. Factory Test `65 01`이 그 트리거 |
| `persist.sys.usb.config` | `iap2` (기본) | 평소엔 CarPlay/iAP 모드라 adb 없음. **재부팅 후 다시 `65 01` 필요할 수 있음** |
| `ro.product.name` (build.prop) | `hyundaiA_kr` | OneBinary 공용 이미지(`DAUDIO_PLUS_hyundaiA_KOR_..._OneBinary`). 문서의 `pd_kr`은 차종코드 기반 런타임 값으로 추정 → **실차 `getprop` 값을 기록**하고 2단계 게이트는 그 값으로 |

---

## 9. 원본 저장소 + 3단계 빌드 도구 (이 노트북)

- clone: `D:\Utility\veloster-n-dashboard` (`reikop/veloster-n-dashboard`, 2026-10-27 HEAD).
  읽을 문서: `docs/ADB.md`(진입·케이블·상태표), `docs/INSTALL.md`(install.py `--check`), `docs/BUILD.md`.
- `python scripts/verify_release.py` → PASS. `build.py dashboard` 비서명 빌드 → `dashboard-UNSIGNED.apk` 생성 확인(2026-10-28, 아래 인자 그대로).
- 3단계 비서명 빌드 인자(원본 `scripts/build.py`):
  ```sh
  cd /d/Utility/veloster-n-dashboard
  python scripts/build.py dashboard \
    --java "/c/Program Files/Microsoft/jdk-17.0.20.101-hotspot/bin/java.exe" \
    --android-jar /d/Android/sdk/platforms/android-17/android.jar \
    --aapt2 /d/Android/sdk/build-tools/34.0.0/aapt2.exe \
    --r8-jar /d/Android/sdk/build-tools/34.0.0/lib/d8.jar \
    --out build/dashboard
  ```
  서명은 `D:\Android\sdk\build-tools\34.0.0\apksigner.bat` (V1 전용, AOSP platform 테스트키 — [`app/README.md`](app/README.md) "빌드").

---

## 맥/리눅스 메모
- Platform-Tools·JDK·SDK 모두 해당 OS 버전 사용. `d8`는 확장자 없는 실행파일.
- 맥에서 USB 열거 확인: `ioreg -p IOUSB -l -w 0`
- `d8`가 PATH에 있으면 `D8=d8`로 생략 가능.
