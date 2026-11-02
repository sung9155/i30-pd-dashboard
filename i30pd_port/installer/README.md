# 설치 프로그램 (Windows `설치.cmd` + `install.ps1`, macOS `설치.command`)

차주가 노트북에서 실행한다. [`INSTALL.md`](../../INSTALL.md)의 3~5단계(연결 확인 · 설치 · '내 앱' 실행)를 대신 한다. 1~2단계(엔지니어 모드 · Factory Test)는 차 화면에서 사람이 해야 하므로 콘솔에 안내만 띄운다.

## 왜 .exe 가 아니라 배치인가

예전에는 C# `.exe` 였는데, **서명을 안 한 exe 라 Windows SmartScreen("Windows의 PC 보호")·백신이 막아서** 잘 모르는 사람이 파란 차단창에서 포기하는 일이 생겼다. 서명을 하려면 유료 인증서가 필요하다. 그래서 배포 방식을 바꿨다.

- 사용자는 **`설치.cmd` 를 더블클릭**한다. 이게 Windows 기본 탑재된 **`powershell.exe`(Microsoft 서명본)** 로 `install.ps1` 을 실행한다.
- `powershell.exe` 는 평판이 있는 서명 프로그램이라 **SmartScreen 평판 차단이 없다.** `.cmd` 는 인터넷에서 받았을 때 파란 차단창 대신 **노란 경고 1번([실행])** 만 뜬다(무서명 exe의 숨은 "추가 정보 → 실행"보다 훨씬 쉬움).

## 쓰는 법 (차주)

1. 받은 ZIP 을 푼다. 폴더 안에 `설치.cmd`, `삭제.cmd`, `install.ps1` 이 있으면 된다. APK 2개는 **옆에 있으면 그걸 쓰고, 없으면 공개 배포 레포(`i30-pd-dashboard`)의 최신 릴리스에서 자동으로 받는다.**
2. **`설치.cmd` 더블클릭.** 노란 "보안 경고" 가 뜨면 [실행].
3. 콘솔 안내대로 차 화면에서 엔지니어 모드 · Factory Test(`0A 01` → `65 01` → `0A 02`)를 하고 USB 연결 → Enter.
4. 자동으로 차(pd_kr)를 찾아 앱 2개를 설치하고 '내 앱' 을 띄운다. 끝나면 '내 앱'에서 **실행 바로가기 → 가장자리 버튼 + 알림 → 저장**.

## 지우기 (원래대로)

**`삭제.cmd` 더블클릭**(맥은 `삭제.command`). 설치 때처럼 차 화면에서 엔지니어 모드 · Factory Test 를 하고 USB 를 연결하면 대시보드 · '내 앱' 두 앱을 지운다. 가장자리 버튼 · 알림 · 기록도 앱과 함께 사라진다. 순정 시스템 파일과 설정은 설치 때도 건드리지 않으므로 두 앱만 지우면 설치 전 상태다. USB 디버깅은 재시동하면 저절로 꺼진다.

**USB 디버깅 끄기** — 설치·삭제가 끝나면 두 설치기 모두 이 방법을 화면에 띄운다.
- 시동을 껐다 켜면 저절로 꺼진다(실차 확인).
- 바로 끄려면 엔지니어 모드 → Diagnosis → Factory Test에서 `0A 01` → `65 00` → `0A 02`를 한 줄씩 보낸다(켤 때의 `65 01` 대신 `65 00`). 원작(벨로스터 N) 코드 분석에서 나온 "일반 USB로 되돌리기" 순서이고, i30 PD 실차에서는 아직 안 해 봤다.
- 또는 '내 앱' → USB 디버깅 → OFF. 끄는 즉시 노트북 연결이 끊긴다.

설치기 없이 직접: `adb uninstall com.veloster.dashboard` · `adb uninstall com.veloster.carplayhub`

## macOS (`설치.command`)

`설치.command` 한 파일이 `install.ps1`과 같은 일을 한다(adb 준비 · APK 받기 · 차 안내 · pd_kr 대기 · 빌드 확인 · 설치/서명 충돌 재설치 · '내 앱' 실행). 기본 탑재 `bash`(3.2)·`curl`·`unzip`만 쓰고, adb가 없으면 `platform-tools-latest-darwin.zip`을 받는다.

1. `i30pd-installer-mac.zip`을 풀고 `설치.command`를 더블클릭한다.
2. "확인되지 않은 개발자" / "Apple에서 확인할 수 없습니다" 창이 뜨면: **시스템 설정 → 개인정보 보호 및 보안** 맨 아래 **[그래도 열기]**. (macOS 14 이하는 파일을 우클릭 → [열기]로도 된다.)
3. 위가 번거로우면 터미널을 열고 `bash ` (뒤에 띄어쓰기)를 친 뒤 `설치.command`를 터미널 창에 끌어다 놓고 Enter. 이 방법은 Gatekeeper 확인 없이 바로 돈다.
4. 나머지는 Windows와 같다. USB-C만 있는 맥은 USB-A 어댑터가 필요하다.

- 기록: `~/Library/Logs/i30pd-installer/install_<시각>.log`
- 삭제: `삭제.command` 더블클릭(= `bash 설치.command --uninstall`)
- 시험 인자: `--adb <경로>` `--apk-base <URL>` `--yes` `--uninstall` (종료 코드는 Windows와 같다)
- 줄바꿈은 LF여야 하고(`.gitattributes`), ZIP 안에서 실행 권한(755)이 있어야 더블클릭으로 열린다. Windows `Compress-Archive`는 권한을 못 넣으므로 RELEASE.md의 Python 명령으로 만든다.
- 가짜 adb로 정상 설치 · 서명 충돌 재설치 · 삭제 · 폴더 APK 사용 · 공개 릴리스에서 APK 받기 · 404/비 APK 거르기 · 차 없음(종료 1)을 확인했다(Windows Git Bash에서. 실제 맥·실차로는 아직 안 해 봤다).

## 동작

- **버전 선택** — 시작할 때 `1) 안정화(권장)` / `2) 테스트` 를 묻는다(Enter = 안정화, `-Yes` 도 안정화). 안정화는 공개 레포의 **최신 정식 릴리스**(`releases/latest/download`), 테스트는 **`test` 태그 사전 배포**(`releases/download/test`, 실차 검증 전 새 기능)에서 받는다. `-Channel stable|test`(맥 `--channel`)로 묻지 않고 고를 수 있다. 사전 배포는 `latest` 에 잡히지 않아 안정화 사용자에게 섞이지 않는다.
- **APK** 를 폴더 옆에서 먼저 찾고, 없으면 **고른 버전의 공개 릴리스에서 받는다**(기본 `github.com/sung9155/i30-pd-dashboard/releases/latest/download/*.apk` — 소스 레포는 비공개라도 상관없다). 받은 파일이 APK(ZIP, `PK` 시그니처)인지 확인해 404 안내문 등을 거른다. 받기 실패하면 "APK 를 옆에 두거나 · `-ApkBase <공개 URL>` 지정" 하라고 알려 준다. base URL 은 `-ApkBase` 로 바꿀 수 있다.
- **ADB** 를 이 순서로 찾는다: 폴더 옆 `platform-tools\adb.exe` → `adb.exe` → `PATH`. 셋 다 없으면 묻고 나서 Google 공식 주소(`dl.google.com/.../platform-tools-latest-windows.zip`)에서 받아 같은 폴더에 푼다. platform-tools 는 레포에 재배포하지 않는다.
- **차에만 설치.** `adb devices -l` 에서 `product:pd_kr` 이고 상태가 `device` 인 기기만 쓴다. 휴대폰 등 다른 기기는 "차가 아닌 기기만 보입니다" 라고 알리고 건드리지 않는다.
- **"알 수 없는 장치"로 뜰 때.** 차를 기다리는 동안 Windows 장치 목록(`Win32_PnPEntity`)도 본다. 차는 USB 디버깅을 켜면 `USB\VID_18D1&PID_D00D`로 잡히는데 Google USB 드라이버 INF에 `D00D`가 없어 드라이버가 자동으로 안 붙는다. 이 장치가 오류 상태면 드라이버를 직접 지정하는 순서를 보여 주고, 묻고 나서 Google 공식 드라이버(`usb_driver_r13-windows.zip`)를 `%LOCALAPPDATA%\i30pd-installer\usb_driver\`에 받아 풀고 장치 관리자를 연다(지정은 사람이: 드라이버 업데이트 → 직접 선택 → 디스크 있음 → `android_winusb.inf` → Android ADB Interface). `USB\VID_0000&PID_0002`("장치 설명자 요청 실패", 코드 43)만 보이면 드라이버가 아니라 연결 문제라 케이블·포트를 바꾸라고 안내한다. 맥은 드라이버가 필요 없다.
- **빌드 확인.** 내비 빌드가 검증본(`eng.vmadmin.20260114.084130`)과 다르면 "값이 틀릴 수 있다" 고 묻고 진행.
- **서명 충돌.** 예전에 다른 서명으로 깔린 앱이 있어 `INCONSISTENT_CERTIFICATES` 가 나면, 묻고 나서 지우고 다시 설치한다.
- **삭제.** `삭제.cmd`(= `설치.cmd -Uninstall`)로 두 앱을 지운다. 이때는 APK 를 받지 않는다.
- **폴더 APK 확인.** 폴더 옆 APK 가 `PK` 로 시작하지 않으면(브라우저가 GitHub 페이지를 `.apk` 이름으로 저장한 경우) 무시하고 릴리스에서 받는다. 설치가 실패하면 adb 의 실패 이유를 화면에 같이 보여 준다.
- **기록.** `%LOCALAPPDATA%\i30pd-installer\install_<시각>.log`(맥 `~/Library/Logs/i30pd-installer/`)에 adb 명령과 출력을 남긴다. 실패하면 이 파일을 Issues 에 올려 달라고 안내하고 기록 폴더를 열어 준다.
- **디버깅용 기록.** 원격으로 원인을 가를 수 있게 기록 파일에 더 남긴다.
  - 환경: Windows·PowerShell(맥은 macOS·CPU), adb 버전.
  - Windows는 USB 장치 목록 전체를 바뀔 때마다 시각과 함께 남긴다(케이블을 꽂는 순간 무엇이 나타났는지 보이게).
  - 맥은 시간 초과 때 `system_profiler SPUSBDataType`을 남긴다.
  - 차를 기다리는 시간은 5초 간격으로 **1분**, 사람이 할 일이 남은 상태(드라이버 지정, 차 화면 "USB 디버깅 허용")면 **3분**. 넘으면 끝내고 고친 뒤 다시 실행하라고 안내한다.
  - 시간 초과 때 마지막 상태를 한 줄로 요약한다: 허용 안 누름 / 드라이버 없음 / 장치 설명자 요청 실패 / pd_kr 아닌 기기만 / 차가 전혀 안 나타남.
- **진행 상황(스크린샷용).** 화면 맨 위에 설치기 버전(`설치기 2026-10-31c` — 고칠 때마다 올림), 단계 제목마다 시각을 찍는다.
  - 앱 설치는 `adb install` 대신 ① 전송(`adb push` → `/data/local/tmp`, 최대 60초) ② 설치(`adb shell pm install -r`, 최대 120초) 두 단계로 나누고, 기다리는 동안 경과 초(`5s 10s …`)를 같은 줄에 찍는다. 삭제도 단계마다 최대 60초.
  - 시간을 넘기면 `멈춘 곳: 대시보드 ① 전송`(USB 쪽 — 케이블·포트) 또는 `② 설치`(내비 쪽 — 차 화면 확인 창, 재시동)와 할 일, 내비 설치 로그(`logcat` PackageManager 등) 마지막 12줄을 화면에 띄우고 끝낸다. 그 화면 사진 한 장으로 원인을 가를 수 있다. Windows는 `taskkill /T`로 adb 프로세스 트리를 끝낸다.
- **내비 정보.** 연결되면 정해 둔 속성만 읽어 기록한다: 기기·빌드·지문·USB 상태(`sys.usb.*`, `persist.sys.dipo.state`). `getprop` 전체는 시리얼·블루투스 주소 같은 개인 정보가 있어 공개 Issues에 올라갈 기록에 넣지 않는다.
  - 보드를 `ro.product.device`로 구분해 화면에 표시한다: `daudioplus_*` = 블루링크 D-Audio Plus, `daudiopluslow_*` = 블루링크 없는 LOW 보드(1 GB).
  - LOW 빌드(`eng.vmadmin.20260114.084725`)는 확인된 빌드로 보고 묻지 않는다. 2026-01-14 펌웨어 비교에서 USB 설정·차량 데이터 데몬·엔지니어 모드가 블루링크판과 같았고, 2026-10-31 LOW 차에서 설치·동작을 확인했다. 다른 LOW 차 한 대는 "장치 설명자 요청 실패"로 연결되지 않았는데, 같은 케이블·PC로 다른 차는 됐으므로 그 차의 포트·배선 문제로 본다.
- **필요한 것.** Windows 10/11 에 기본으로 있는 PowerShell 만 있으면 된다. 추가 설치 없음.

> **한글 깨짐 주의 (유지보수용):** `install.ps1` 은 **UTF-8 BOM** 으로 저장해야 한다. Windows PowerShell 5.1 은 BOM 없는 UTF-8 을 ANSI 로 읽어 한글이 깨지고 파싱이 깨진다. 편집 후 첫 3바이트가 `EF BB BF` 인지 확인(`설치.cmd` 는 ASCII 라 BOM 불필요).

## 배포 (관리자)

exe 가 아니므로 빌드 단계가 없다. APK 는 **공개 배포 레포 [`sung9155/i30-pd-dashboard`](https://github.com/sung9155/i30-pd-dashboard) 의 릴리스**로 나간다(소스 레포는 비공개라도 됨).

**릴리스 낼 때** — `i30pd_port/app/` 의 `dashboard-i30pd-platform.apk` · `myapps-i30pd-platform.apk` 를 릴리스 에셋으로 올린다. 파일명을 그대로 두면 안정화는 `releases/latest/download/...`(최신 정식 릴리스), 테스트는 `releases/download/test/...`(`test` 사전 배포)를 받는다. (예전 `i30pd-installer.exe` 에셋은 더 안 쓰니 새 릴리스엔 빼도 된다.)

**차주에게 주는 것** — 두 가지:

- **스크립트 2개만 (추천).** `설치.cmd` + `install.ps1` 만 ZIP 으로 주면, 실행 때 APK 를 위 공개 릴리스에서 자동으로 받는다. 릴리스가 갱신돼도 ZIP 은 그대로.
- **APK 동봉 (오프라인).** 네트워크 없이 할 거면 APK 2개를 같이 넣는다. 그러면 받지 않고 그걸 쓴다.

  ```
  i30pd-설치/
    설치.cmd
    install.ps1
    dashboard-i30pd-platform.apk   (i30pd_port/app/ 에서)
    myapps-i30pd-platform.apk      (i30pd_port/app/ 에서)
  ```

platform-tools 를 같이 넣으면 adb 도 오프라인에서 되고, 안 넣으면 첫 실행 때 Google 에서 받는다.

## 시험 (차 없이)

```
설치.cmd -AdbPath <가짜 adb> -Yes [-Uninstall]
```
또는
```
powershell -NoProfile -ExecutionPolicy Bypass -File install.ps1 -AdbPath <가짜 adb> -Yes
```

- `-AdbPath`: 지정한 adb 를 쓴다.
- `-ApkBase`: APK 받을 base URL 을 바꾼다. 로컬 폴더를 `file:///C:/.../server` 로 줘서 다운로드 경로를 시험할 수 있다.
- `-Yes`: 확인 모두 '예', 대기/프롬프트 생략, 끝나면 종료 코드 반환.
- `-Uninstall`: 두 앱 삭제.
- 종료 코드: 0 = 성공, 1 = 취소/설치 실패, 2 = ADB/APK 없음.

가짜 adb 는 `devices -l`(pd_kr 한 줄), `getprop`, `install`(`Success`), `uninstall` 에 정해진 출력을 내는 `.cmd` 면 된다. 정상 설치 · 삭제 · 서명 충돌 재설치, **그리고 `file://` 서버에서 APK 받아 설치하는 흐름** 과 비공개 레포 404 시 안내 메시지를 이 방식으로 확인했다(실차 설치는 아직 이 스크립트로 해 보지 않았다).
