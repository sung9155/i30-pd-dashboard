# 설치 프로그램 (Windows `설치.cmd` + `install.ps1`, macOS·리눅스 `설치.command`)

차주가 노트북에서 실행한다. [`INSTALL.md`](../../INSTALL.md)의 3~5단계(연결 확인 · 설치 · '내 앱' 실행)를 대신 한다. 1~2단계(엔지니어 모드 · Factory Test)는 차 화면에서 사람이 해야 하므로 콘솔에 안내만 띄운다.

## 왜 .exe 가 아니라 배치인가

예전에는 C# `.exe` 였는데, **서명을 안 한 exe 라 Windows SmartScreen("Windows의 PC 보호")·백신이 막아서** 잘 모르는 사람이 파란 차단창에서 포기하는 일이 생겼다. 서명을 하려면 유료 인증서가 필요하다. 그래서 배포 방식을 바꿨다.

- 사용자는 **`설치.cmd` 를 더블클릭**한다. 이게 Windows 기본 탑재된 **`powershell.exe`(Microsoft 서명본)** 로 `install.ps1` 을 실행한다.
- `powershell.exe` 는 평판이 있는 서명 프로그램이라 **SmartScreen 평판 차단이 없다.** `.cmd` 는 인터넷에서 받았을 때 파란 차단창 대신 **노란 경고 1번([실행])** 만 뜬다(무서명 exe의 숨은 "추가 정보 → 실행"보다 훨씬 쉬움).

## 쓰는 법 (차주)

1. 받은 ZIP 을 푼다. 폴더 안에 `설치.cmd`, `삭제.cmd`, `진단기록.cmd`, `install.ps1` 이 있으면 된다. APK 2개는 **옆에 있으면 그걸 쓰고, 없으면 공개 배포 레포(`i30-pd-dashboard`)의 최신 릴리스에서 자동으로 받는다.**
2. **`설치.cmd` 더블클릭.** 노란 "보안 경고" 가 뜨면 [실행].
3. 콘솔 안내대로 차 화면에서 엔지니어 모드 · Factory Test(`0A 01` → `65 01` → `0A 02`)를 하고 USB 연결 → Enter.
4. 자동으로 차(pd_kr)를 찾아 앱 2개를 설치하고 '내 앱' 을 띄운다. 끝나면 '내 앱'에서 **바로가기 버튼 → 버튼 + 알림 → 저장**.

## 창 설치기 (`창 설치기.cmd` + `gui.ps1`, 맥 `창 설치기.command` + `gui.js`)

원작(벨로스터 N) `installer/gui.ps1` · `installer/mac/gui.js`를 옮겨 고친 것. 창 하나에서 단추를 누르면 같은 폴더의 콘솔 설치기를 숨은 창에서 `-Gui`(맥 `--gui`)로 돌리고 출력을 줄 단위로 기록 칸에 보여 준다. 설치 규칙은 모두 `install.ps1` / `설치.command`에 있고 창은 화면만 맡는다. 콘솔 설치기는 그대로 둔다(번호로 고르고 싶은 사람, 시험).

| 콘솔 | 창 |
|---|---|
| 번호 1) 안정화 · 2) 테스트 | [받을 버전] 라디오 + [설치] (`-Channel stable\|test`) |
| 번호 3) 진단 기록 · `진단기록.cmd` | [진단 기록] (`-Diag`), 끝나면 [파일 보기] |
| 번호 4) · 5) 시동 안내 화면 | [시동 안내 화면 (고급)]의 [4번: 10초 → 1초] · [5번: 원래대로] (`-BootFast` · `-BootFastRemove`) |
| `삭제.cmd` | [삭제] (`-Uninstall`). 창에서는 잘못 누르기 쉬워 한 번 먼저 묻는다 |
| 그 밖의 [Y/n] 질문 | 설치기가 `@@ASK`를 내면 예/아니오 창(바로 앞에 찍힌 안내 줄을 함께 보여 줌) |
| 시작할 때 새 설치기 묻기 | 창이 뜰 때 `-CheckUpdate`가 `@@UPDATE <버전>`을 내면 [업데이트] 단추 → `-Gui -Update`가 이 폴더의 파일을 바꾸고 `@@OPEN <새 창 설치기>` → 창이 그것을 열고 닫힌다 |

- `-Gui` 상태 줄: `@@STEP <제목>` `@@WAIT <초> <안내>` `@@ASK <질문>`(표준 입력에서 y/n 한 줄) `@@FILE <경로>` `@@DONE ok|fail <내용>`. `-Gui`는 번호·Enter를 묻지 않고(채널이 없으면 안정화) 차를 5분까지 기다리며, 시작할 때의 새 설치기 확인을 건너뛴다(창의 [업데이트]가 맡음). 질문의 답은 묻는 때에만 ASCII 바이트로 쓴다(원작 a2f08a1: 미리 쓰면 adb가 읽어 갈 수 있고, UTF-8 콘솔에서는 BOM이 붙는다).
- **Windows**: `창 설치기.cmd`는 한 줄로 `powershell -STA -WindowStyle Hidden -File gui.ps1`을 부른다. cmd 창은 PowerShell이 뜨자마자 숨기므로 아주 잠깐만 깜빡인다(.cmd로는 이보다 줄일 수 없다. .vbs는 백신·Windows 정책에 막히기 쉬워 쓰지 않음). 한 줄로 둔 것은 [업데이트]가 실행 중인 .cmd를 바꿔도 cmd가 파일을 다시 읽지 않게 하려는 것. `gui.ps1`은 Windows 기본 PowerShell 5.1 + WinForms, 맑은 고딕, UTF-8 BOM + CRLF.
- **맥**: 설치기 ZIP은 Windows에서 만들고 `osacompile`은 맥에만 있어 `.app`을 미리 넣을 수 없다. 그래서 `창 설치기.command`가 처음 열 때(매번) 맥 기본 `osacompile -l JavaScript -s`로 `gui.js`를 같은 폴더의 `i30 PD 설치.app`으로 만들어 연다(1초 안팎). 그 맥에서 만든 앱이라 격리 표시가 없어 Gatekeeper가 앱을 다시 묻지 않고, 맥용 CI·서명이 필요 없다. 업데이트로 `gui.js`가 바뀌면 `@@OPEN`이 `창 설치기.command`를 가리켜 앱을 새로 만든다. 앱은 자기 옆의 `설치.command`를 돌린다(폴더 밖으로 옮기면 못 찾는다). 맥 창은 실제 맥에서 아직 확인하지 못했다.
- **리눅스**: 창 없음(콘솔만). 리눅스 ZIP은 그대로.
- 시험: `bash test/run.sh win|mac|linux`가 `-Gui`/`--gui` 줄(차 기다리기 · 진단 파일 · 질문 예/아니오(맥·리눅스, 표준 입력) · 창 업데이트)을 보고, Windows에서는 `gui.ps1` 창을 실제로 띄워(환경 변수 `VN_GUI_SMOKE`·`VN_GUI_AUTORUN`·`VN_GUI_ANSWER`·`VN_GUI_ARGS`) 열림·기록 칸 높이·설치·질문 아니오/예·삭제·[업데이트]를 가짜 adb로 확인한다.

## 지우기 (원래대로)

**`삭제.cmd` 더블클릭**(맥은 `삭제.command`). 설치 때처럼 차 화면에서 엔지니어 모드 · Factory Test 를 하고 USB 를 연결하면 대시보드 · '내 앱' 두 앱을 지운다. 가장자리 버튼 · 알림 · 기록도 앱과 함께 사라진다. 순정 시스템 파일과 설정은 설치 때도 건드리지 않으므로 두 앱만 지우면 설치 전 상태다. USB 디버깅은 재시동하면 저절로 꺼진다.

**USB 디버깅 끄기** — 설치·삭제가 끝나면 두 설치기 모두 이 방법을 화면에 띄운다.
- 시동을 껐다 켜면 저절로 꺼진다(실차 확인).
- 바로 끄려면 엔지니어 모드 → Diagnosis → Factory Test에서 `0A 01` → `65 00` → `0A 02`를 한 줄씩 보낸다(켤 때의 `65 01` 대신 `65 00`). 원작(벨로스터 N) 코드 분석에서 나온 "일반 USB로 되돌리기" 순서이고, '내 앱' OFF가 같은 줄을 보내 i30 PD 실차에서 확인했다(2026-11-03).
- 또는 '내 앱' → USB 디버깅 → OFF. 끄는 즉시 노트북 연결이 끊긴다.

설치기 없이 직접: `adb uninstall com.veloster.dashboard` · `adb uninstall com.veloster.carplayhub`

## macOS (`설치.command`)

`설치.command` 한 파일이 `install.ps1`과 같은 일을 한다(adb 준비 · APK 받기 · 차 안내 · pd_kr 대기 · 빌드 확인 · 설치/서명 충돌 재설치 · '내 앱' 실행). 기본 탑재 `bash`(3.2)·`curl`·`unzip`만 쓰고, adb가 없으면 `platform-tools-latest-darwin.zip`을 받는다.

1. `i30pd-installer-mac.zip`을 풀고 `설치.command`를 더블클릭한다.
2. "확인되지 않은 개발자" / "Apple에서 확인할 수 없습니다" 창이 뜨면: **시스템 설정 → 개인정보 보호 및 보안** 맨 아래 **[그래도 열기]**. (macOS 14 이하는 파일을 우클릭 → [열기]로도 된다.)
3. 위가 번거로우면 터미널을 열고 `bash ` (뒤에 띄어쓰기)를 친 뒤 `설치.command`를 터미널 창에 끌어다 놓고 Enter. 이 방법은 Gatekeeper 확인 없이 바로 돈다.
4. 나머지는 Windows와 같다. USB-C만 있는 맥은 USB-A 어댑터가 필요하다.

- 기록: `~/Library/Logs/i30pd-installer/install_<시각>.log`
- 삭제: `삭제.command` 더블클릭(= `bash 설치.command --uninstall`), 진단 기록: `진단기록.command`(= `--diag`)
- 시험 인자: `--adb <경로>` `--apk-base <URL>` `--yes` `--uninstall` `--diag` (종료 코드는 Windows와 같다)
- 진단 기록의 가림 처리는 macOS 기본 `perl`을 쓴다.
- 줄바꿈은 LF여야 하고(`.gitattributes`), ZIP 안에서 실행 권한(755)이 있어야 더블클릭으로 열린다. Windows `Compress-Archive`는 권한을 못 넣으므로 RELEASE.md의 Python 명령으로 만든다.
- 가짜 adb로 정상 설치 · 서명 충돌 재설치 · 삭제 · 폴더 APK 사용 · 공개 릴리스에서 APK 받기 · 404/비 APK 거르기 · 차 없음(종료 1)을 확인했다(Windows Git Bash에서). 실제 맥으로는 2026-11-03에 차에 설치하는 것까지 확인했다.

## 리눅스 (`i30pd-installer-linux.zip`)

맥과 같은 `설치.command`가 `uname`으로 리눅스를 알아보고 몇 가지만 다르게 한다. ZIP에는 그 파일을 `install.sh`로, `linux/uninstall.sh`·`linux/diag.sh`(= `--uninstall`·`--diag`)와 함께 넣는다(실행 권한 755, LF).

- **adb**: x86_64는 `platform-tools-latest-linux.zip`을 받는다(unzip이 없으면 `python3 -m zipfile`). ARM(라즈베리파이 등)은 Google 배포본이 없어 `sudo apt-get install adb`(안 되면 `android-tools-adb`)를 묻고 설치한다. 배포판 패키지는 USB 권한 규칙도 함께 깐다.
- **USB 권한**: `adb devices`에 `no permissions`가 보이면 처음 한 번, 차(VID 18d1, PID d00d)에만 `/etc/udev/rules.d/51-i30pd.rules`(MODE 0666)를 넣을지 묻고 `udevadm` 다시 읽기 → adb 서버 재시작 → 케이블 다시 꽂기 안내.
- **노트 요약**: 맥의 JavaScript(`osascript`) 대신 `python3`로 같은 규칙(`RELEASE-PY`). 2026-11-05 실제 릴리스 응답 두 개에서 node로 돈 `RELEASE-JS` 결과와 바이트까지 같음을 확인했다. python3가 없으면 요약만 빠진다.
- **그 밖**: 기록 `${XDG_STATE_HOME:-~/.local/state}/i30pd-installer/`, 진단 기록은 `xdg-user-dir DESKTOP`(없으면 홈), 화면이 있을 때만 `xdg-open`으로 폴더를 연다, 못 찾을 때 USB 목록은 `lsusb`. `curl`이 없으면 설치 방법을 알리고 멈춘다.
- 시험: `bash test/run.sh linux`(Windows Git Bash에서 `I30PD_OS=Linux`로 리눅스 경로를 돈다; `mac`도 이제 `I30PD_OS=Darwin`). 실제 리눅스 기기·실차로는 아직 안 해 봤다.

## 동작

- **버전 선택** — 시작할 때 `1) 안정화(권장)` / `2) 테스트` 를 묻는다(Enter = 안정화, `-Yes` 도 안정화). 안정화는 공개 레포의 **최신 정식 릴리스**(`releases/latest/download`), 테스트는 **`test` 태그 사전 배포**(`releases/download/test`, 실차 검증 전 새 기능)에서 받는다. `-Channel stable|test`(맥 `--channel`)로 묻지 않고 고를 수 있다. 사전 배포는 `latest` 에 잡히지 않아 안정화 사용자에게 섞이지 않는다. 고르기 전에 GitHub 릴리스 API(`releases/latest`, `releases/tags/test`)에서 버전·날짜(테스트는 대시보드 APK를 마지막으로 바꾼 날)를 받아 두 줄 옆에 보여 주고, 고른 뒤 노트를 요약해 보여 준다: 소제목(`###` 또는 `**굵은 줄**`)마다 첫 항목의 첫 문장, 설치·서명 절이나 테스트 노트의 지난 기록(`> `)에서 멈춤, 최대 6개(휴대폰 앱 4개). 세 설치기가 같은 규칙(`install.ps1` NotesSummary, `설치.command` JXA, 휴대폰 앱 `Flow.summary` + FlowTest). 못 받으면 안내만 빠진다.
- **APK** 를 폴더 옆에서 먼저 찾고, 없으면 **고른 버전의 공개 릴리스에서 받는다**(기본 `github.com/sung9155/i30-pd-dashboard/releases/latest/download/*.apk` — 소스 레포는 비공개라도 상관없다). 받은 파일이 APK(ZIP, `PK` 시그니처)인지 확인해 404 안내문 등을 거른다. 받기 실패하면 "APK 를 옆에 두거나 · `-ApkBase <공개 URL>` 지정" 하라고 알려 준다. base URL 은 `-ApkBase` 로 바꿀 수 있다.
- **ADB** 를 이 순서로 찾는다: 폴더 옆 `platform-tools\adb.exe` → `adb.exe` → `PATH`. 셋 다 없으면 묻고 나서 Google 공식 주소(`dl.google.com/.../platform-tools-latest-windows.zip`)에서 받아 같은 폴더에 푼다. platform-tools 는 레포에 재배포하지 않는다.
- **차에만 설치.** `adb devices -l` 에서 `product:pd_kr` 이고 상태가 `device` 인 기기만 쓴다. 휴대폰 등 다른 기기는 "차가 아닌 기기만 보입니다" 라고 알리고 건드리지 않는다.
- **"알 수 없는 장치"로 뜰 때.** 차를 기다리는 동안 Windows 장치 목록(`Win32_PnPEntity`)도 본다. 차는 USB 디버깅을 켜면 `USB\VID_18D1&PID_D00D`로 잡히는데 Google USB 드라이버 INF에 `D00D`가 없어 드라이버가 자동으로 안 붙는다. 이 장치가 오류 상태면 드라이버를 직접 지정하는 순서를 보여 주고, 묻고 나서 Google 공식 드라이버(`usb_driver_r13-windows.zip`)를 `%LOCALAPPDATA%\i30pd-installer\usb_driver\`에 받아 풀고 장치 관리자를 연다(지정은 사람이: 드라이버 업데이트 → 직접 선택 → 디스크 있음 → `android_winusb.inf` → Android ADB Interface). `USB\VID_0000&PID_0002`("장치 설명자 요청 실패", 코드 43)만 보이면 드라이버가 아니라 연결 문제라 케이블·포트를 바꾸라고 안내한다. 맥은 드라이버가 필요 없다.
- **빌드 확인.** 내비 빌드가 검증본(`eng.vmadmin.20260114.084130`, LOW `…084725`, 2024-02 `eng.vmadmin.20240205.105401` — 2026-11-03 같은 차종 실차에서 설치·값 확인)과 다르면 "값이 틀릴 수 있다" 고 묻고 진행.
- **서명 충돌.** 예전에 다른 서명으로 깔린 앱이 있어 `INCONSISTENT_CERTIFICATES` 가 나면, 묻고 나서 지우고 다시 설치한다.
- **삭제.** `삭제.cmd`(= `설치.cmd -Uninstall`)로 두 앱을 지운다. 이때는 APK 를 받지 않는다. '내 앱'이 ☆ 버튼을 계기판으로 돌려 놨으면(`persist.sys.key.custom` = `com.veloster.carplayhub.STAR_KEY`) 먼저 `service call com.hkmc.micom.mode 34 s16 com.mobis.intent.action.CUSTOM_SHORTCUT`으로 순정 "없음"으로 되돌리고 다시 읽어 확인한다.
- **폴더 APK 확인.** 폴더 옆 APK 가 `PK` 로 시작하지 않으면(브라우저가 GitHub 페이지를 `.apk` 이름으로 저장한 경우) 무시하고 릴리스에서 받는다. 설치가 실패하면 adb 의 실패 이유를 화면에 같이 보여 준다.
- **기록.** `%LOCALAPPDATA%\i30pd-installer\install_<시각>.log`(맥 `~/Library/Logs/i30pd-installer/`)에 adb 명령과 출력을 남긴다. 실패하면 문제를 알릴 때 이 파일을 함께 보내 달라고 안내하고 기록 폴더를 열어 준다.
- **진단 기록.** `진단기록.cmd`(= `설치.cmd -Diag`, 맥 `진단기록.command` = `--diag`) 또는 버전 메뉴의 `3)`. 차에 연결해 **읽기만** 한다: 정해 둔 속성, 두 앱의 `dumpsys package` 중 버전·경로·설치 시각 줄, `logcat -d -v time -s VehicleDash VNCarPlayHub VNQuickEntry NDashboard DtcDialog AndroidRuntime:E PackageManager:I`의 마지막 3000줄, 이번 실행 기록. MAC·휴대폰 번호·메일 주소·adb 기기 번호를 가린 뒤 바탕화면 `i30pd_진단기록_<시각>.txt`(UTF-8)로 저장하고 그 파일을 탐색기/Finder에서 보여 준다. 전체 logcat은 블루투스 폰 이름·번호·연락처가 섞여 넣지 않는다.
- **같은 버전 건너뛰기.** 설치 전에 `pm path`로 차의 APK를 `adb pull`해 받은 파일과 해시(맥 `cmp`)를 비교하고, 같으면 그 앱은 설치하지 않는다(내비는 설치한 APK를 바이트 그대로 보관한다). 비교에 실패하면 그냥 설치한다.
- **새 설치기 (스스로 업데이트).** 시작할 때 이 설치기가 나온 릴리스(`InstallerChannel`/`INSTALLER_CHANNEL`: `stable` = 최신 정식 릴리스, `test` = `test` 사전 배포)의 설치기 ZIP(`i30pd-installer-windows/mac/linux.zip`)을 받는다. GitHub 릴리스 정보의 크기·SHA-256과 맞고, 풀어서 설치기 스크립트가 있을 때만 그 안의 설치기 버전과 비교한다. 더 새것이면 "새 설치기가 나왔습니다"라고 묻고, 예면 이 폴더의 같은 이름 파일만 바꾼 뒤(platform-tools·APK는 그대로) 같은 인자로 다시 시작한다. 아니오·인터넷 없음·확인 실패면 지금 설치기로 계속한다(설치를 막지 않음). 토큰 없이 공개 주소만 쓴다.
- **디버깅용 기록.** 원격으로 원인을 가를 수 있게 기록 파일에 더 남긴다.
  - 환경: Windows·PowerShell(맥은 macOS·CPU), adb 버전.
  - Windows는 USB 장치 목록 전체를 바뀔 때마다 시각과 함께 남긴다(케이블을 꽂는 순간 무엇이 나타났는지 보이게).
  - 맥은 시간 초과 때 `system_profiler SPUSBDataType`을 남긴다.
  - 차를 기다리는 시간은 5초 간격으로 **1분**, 사람이 할 일이 남은 상태(드라이버 지정, 차 화면 "USB 디버깅 허용")면 **3분**. 넘으면 끝내고 고친 뒤 다시 실행하라고 안내한다.
  - 시간 초과 때 마지막 상태를 한 줄로 요약한다: 허용 안 누름 / 드라이버 없음 / 장치 설명자 요청 실패 / pd_kr 아닌 기기만 / 차가 전혀 안 나타남.
- **진행 상황(스크린샷용).** 화면 맨 위에 설치기 버전(`설치기 2026-10-31c` — 고칠 때마다 올림), 단계 제목마다 시각을 찍는다.
  - 앱 설치는 `adb install` 대신 ① 전송(`adb push` → `/data/local/tmp`, 최대 60초) ② 설치(`adb shell pm install -r`, 최대 120초) 두 단계로 나누고, 기다리는 동안 경과 초(`5s 10s …`)를 같은 줄에 찍는다. 삭제도 단계마다 최대 60초.
  - 시간을 넘기면 `멈춘 곳: 대시보드 ① 전송`(USB 쪽 — 케이블·포트) 또는 `② 설치`(내비 쪽 — 차 화면 확인 창, 재시동)와 할 일, 내비 설치 로그(`logcat` PackageManager 등) 마지막 12줄을 화면에 띄우고 끝낸다. 그 화면 사진 한 장으로 원인을 가를 수 있다. Windows는 `taskkill /T`로 adb 프로세스 트리를 끝낸다.
- **내비 정보.** 연결되면 정해 둔 속성만 읽어 기록한다: 기기·빌드·지문·USB 상태(`sys.usb.*`, `persist.sys.dipo.state`)·☆ 버튼 동작(`persist.sys.key.custom`). `getprop` 전체는 시리얼·블루투스 주소 같은 개인 정보가 있어 기록에 넣지 않는다.
  - 보드를 `ro.product.device`로 구분해 화면에 표시한다: `daudioplus_*` = 블루링크 D-Audio Plus, `daudiopluslow_*` = 블루링크 없는 LOW 보드(1 GB).
  - LOW 빌드(`eng.vmadmin.20260114.084725`)는 확인된 빌드로 보고 묻지 않는다. 2026-01-14 펌웨어 비교에서 USB 설정·차량 데이터 데몬·엔지니어 모드가 블루링크판과 같았고, 2026-10-31 LOW 차에서 설치·동작을 확인했다. 다른 LOW 차 한 대는 "장치 설명자 요청 실패"로 연결되지 않았는데, 같은 케이블·PC로 다른 차는 됐으므로 그 차의 포트·배선 문제로 본다.
- **필요한 것.** Windows 10/11 에 기본으로 있는 PowerShell 만 있으면 된다. 추가 설치 없음.

> **한글 깨짐 주의 (유지보수용):** `install.ps1` 은 **UTF-8 BOM** 으로 저장해야 한다. Windows PowerShell 5.1 은 BOM 없는 UTF-8 을 ANSI 로 읽어 한글이 깨지고 파싱이 깨진다. 편집 후 첫 3바이트가 `EF BB BF` 인지 확인(`설치.cmd` 는 ASCII 라 BOM 불필요).

## 시동 안내 화면 1초 (4·5번, `-BootFast` / `-BootFastRemove`, 맥·리눅스 `--boot-fast` / `--boot-fast-remove`)

순정 SystemUI를 그 차의 원본으로 그 PC에서 고쳐 설치한다(현대 코드라 완성본은 배포하지 않음, [`../consent_fast/README.md`](../consent_fast/README.md)).
1. `pm path com.android.systemui`: 이미 `/data/app`(versionCode 18)이면 "이미 설치", 다른 업데이트면 멈춤.
2. `/system/app/HKMC_SystemUI.apk`·`.odex`를 받아 SHA-256이 확인된 빌드(V141)인지 본다. 아니면 지우고 종료 코드 3(차는 그대로).
3. `/system/framework`의 odex 11개를 받는다.
4. Java 11+를 찾고(설치기 폴더의 `jre17` → PATH) 없으면 Eclipse Temurin 17 JRE를 해시 확인 후 받는다(Windows x64, macOS x64·arm64, 리눅스 x64·aarch64).
   도구(baksmali·smali·apksig·AOSP 테스트 키)는 해시 고정, 빌더 `i30pd-consent-fast.jar`는 공개 릴리스(안정화, 없으면 test)에서 받는다.
5. 빌더 실행 → push → `pm install -r` → `/data/app`·versionCode 18 확인. 받은 원본과 결과 APK는 성공·실패 모두 PC에서 지운다.
- 되돌리기는 `adb uninstall com.android.systemui`(업데이트만 지움). `삭제`도 깔려 있으면 함께 되돌릴지 묻는다(`-Yes`면 되돌림).
- 시험 인자: `-JavaPath <java>` `-ToolDir <도구 폴더>`(맥·리눅스 `--java` `--tool-dir`). `test/run.sh`는 `BOOT_CAP`(실제 원본 캡처)·`BOOT_TOOLS`·`BOOT_JAVA`가 있으면 설치·이미 설치·삭제 시 되돌림·모르는 빌드·되돌리기를 시험한다.

## 배포 (관리자)

exe 가 아니므로 빌드 단계가 없다. APK 는 **공개 배포 레포 [`sung9155/i30-pd-dashboard`](https://github.com/sung9155/i30-pd-dashboard) 의 릴리스**로 나간다(소스 레포는 비공개라도 됨).

**릴리스 낼 때** — `i30pd_port/app/` 의 `dashboard-i30pd-platform.apk` · `myapps-i30pd-platform.apk` 를 릴리스 에셋으로 올린다. 파일명을 그대로 두면 안정화는 `releases/latest/download/...`(최신 정식 릴리스), 테스트는 `releases/download/test/...`(`test` 사전 배포)를 받는다. (예전 `i30pd-installer.exe` 에셋은 더 안 쓰니 새 릴리스엔 빼도 된다.)

**설치기 ZIP** — `python scripts/release.py installers stable --out DIR`(테스트 배포는 `test`)이 ZIP 3개를 만들고 채널을 스크립트에 적는다. 정식은 그 릴리스에, 테스트는 `gh release upload test DIR/*.zip --clobber`로 올린다. 손으로 묶으면 채널이 `stable`로 남아, 테스트 ZIP 사용자도 정식 릴리스만 보게 된다. 설치기를 고칠 때마다 설치기 버전(날짜)을 올려야 업데이트가 뜬다.

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
- `-Diag`: 진단 기록 파일만 만든다(차에는 쓰지 않음).
- 종료 코드: 0 = 성공, 1 = 취소/설치 실패, 2 = ADB/APK 없음.
- `I30PD_API`(환경 변수): 릴리스 API 주소를 바꾼다. `test/run.sh`는 `test/fakerelease.py`(가짜 릴리스 서버)에 붙여 업데이트를 시험한다: 같은 버전이면 조용함, 인터넷 없음이면 계속, 다른 채널의 새 설치기는 안 봄, SHA-256이 다르면 쓰지 않고 계속, 새 설치기면 바꾸고 다시 시작.

가짜 adb 는 `devices -l`(pd_kr 한 줄), `getprop`, `install`(`Success`), `uninstall` 에 정해진 출력을 내는 `.cmd` 면 된다. 정상 설치 · 삭제 · 서명 충돌 재설치, **그리고 `file://` 서버에서 APK 받아 설치하는 흐름** 과 비공개 레포 404 시 안내 메시지를 이 방식으로 확인했다(실차 설치는 아직 이 스크립트로 해 보지 않았다).
