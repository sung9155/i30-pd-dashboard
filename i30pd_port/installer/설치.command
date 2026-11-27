#!/bin/bash
# i30 PD 대시보드 설치 — macOS·리눅스(라즈베리파이 포함). 맥은 Finder에서 더블클릭, 리눅스는 터미널에서  bash install.sh
# (리눅스 ZIP의 install.sh는 이 파일과 같다). 맥은  bash 설치.command 로도 된다.
# install.ps1(Windows)과 같은 일을 한다: adb 준비 → APK 준비 → 차 화면 안내 → 차(product:pd_kr) 연결 대기 →
# 빌드 확인 → 두 앱 설치(서명 충돌 시 지우고 재설치) → '내 앱' 실행. 차가 아닌 기기에는 아무것도 하지 않는다.
# 기본 macOS bash 3.2 에서 돌아가야 하므로 bash 4 기능(연관 배열 등)은 쓰지 않는다.
#
# 버전: 시작할 때 안정화(최신 정식 릴리스, 실차 확인) / 테스트('test' 사전 배포, 실차 검증 전) / 진단 기록 모으기 중 고른다.
# 테스트 인자: --adb <adb 경로>  --apk-base <URL>  --channel stable|test  --yes(확인 모두 예, 대기/프롬프트 생략, 안정화)  --uninstall(두 앱 삭제)
#              --diag(설치하지 않고 진단 기록 파일만 만든다)
#              --boot-fast(시동 안내 화면 10초 → 1초: 이 차의 순정 SystemUI를 이 PC에서 고쳐 설치) --boot-fast-remove(순정으로 되돌리기)
#              시험용: --java <java> --tool-dir <도구 폴더(받기 생략)>
# 새 설치기: 시작할 때 이 설치기가 나온 릴리스(INSTALLER_CHANNEL: 안정화 = 최신 정식, 테스트 = 'test')의 설치기 ZIP이 더 새것이면
#            묻고 이 폴더의 파일을 바꾼 뒤 같은 인자로 다시 시작한다. 시험용: I30PD_API(릴리스 API 주소, 가짜 서버)
# 맥 창 설치기(mac/gui.js)용: --gui  번호·Enter를 묻지 않고(--channel이 없으면 안정화) 차를 스스로 기다리며, 줄 단위로 찍고 상태 줄을 낸다.
#              @@STEP <제목>  @@WAIT <초> <안내>  @@ASK <질문>(표준 입력에서 y/n 한 줄을 읽음)  @@FILE <경로>  @@DONE ok|fail <내용>
#              새 설치기는 창의 [업데이트]가 맡는다: --check-update(더 새것이면 '@@UPDATE <버전>' 한 줄) · --gui --update(묻지 않고 바꾼 뒤
#              '@@OPEN <새 창 설치기.command>'). --gui일 때 시작할 때의 새 설치기 확인은 건너뛴다.

ROOT="$(cd "$(dirname "$0")" && pwd)"; ME="$(basename "$0")"; ARGS=("$@")   # ARGS: 새 설치기로 다시 시작할 때 그대로 넘긴다
# OS: Darwin(맥) / Linux. I30PD_OS로 바꿀 수 있다(시험 도구가 Windows Git Bash에서 리눅스 경로를 돌릴 때).
OS="${I30PD_OS:-$(uname -s)}"; [ "$OS" = Darwin ] || OS=Linux
INSTALLER_VERSION='2026-11-27c'   # 화면 맨 위에 찍혀, 스크린샷만으로 옛 설치기인지 알 수 있다. 고칠 때마다 올린다.
INSTALLER_CHANNEL='stable'   # 이 설치기가 나온 곳: stable = 최신 정식 릴리스, test = 'test' 사전 배포. ZIP을 만들 때 scripts/release.py installers가 적는다.
VERIFIED_BUILD='eng.vmadmin.20260114.084130'   # 블루링크 D-Audio Plus(HYUNDAIA) — 실차 설치 확인
LOW_BUILD='eng.vmadmin.20260114.084725'        # 블루링크 없는 D-Audio Plus LOW — 2026-10-31 실차 설치·동작 확인
OLD_BUILD='eng.vmadmin.20240205.105401'        # 블루링크 D-Audio Plus 2024-02 빌드 — 2026-11-03 같은 차종 실차에서 설치·값 확인
# Google platform-tools: 맥, 리눅스 x86_64만 있다. 라즈베리파이 같은 ARM 리눅스는 배포판 adb 패키지(apt)를 쓴다.
PT_URL=''
if [ "$OS" = Darwin ]; then PT_URL='https://dl.google.com/android/repository/platform-tools-latest-darwin.zip'
else case "$(uname -m)" in x86_64|amd64) PT_URL='https://dl.google.com/android/repository/platform-tools-latest-linux.zip' ;; esac; fi
# 안정화 = 최신 정식 릴리스, 테스트 = 'test' 사전 배포(덮어쓰며 갱신). --apk-base 를 주면 그 주소를 그대로 쓴다.
RELEASES='https://github.com/sung9155/i30-pd-dashboard/releases'
API="${I30PD_API:-https://api.github.com/repos/sung9155/i30-pd-dashboard/releases}"; API="${API%/}"   # I30PD_API: 시험용
APK_BASE=''
APKS='dashboard-i30pd-platform.apk myapps-i30pd-platform.apk'
ADB=''; YES=''; UNINSTALL=''; CHANNEL=''; DIAG=''; BOOT_FAST=''; BOOT_REMOVE=''; JAVA_PATH=''; TOOL_DIR=''; GUI=''; CHECK_UPDATE=''; UPDATE=''
while [ $# -gt 0 ]; do
  case "$1" in
    --adb) ADB="$2"; shift ;;
    --apk-base) APK_BASE="$2"; shift ;;
    --channel) CHANNEL="$2"; shift ;;
    --yes) YES=1 ;;
    --uninstall) UNINSTALL=1 ;;
    --diag) DIAG=1 ;;
    --boot-fast) BOOT_FAST=1 ;;
    --boot-fast-remove) BOOT_REMOVE=1 ;;
    --java) JAVA_PATH="$2"; shift ;;
    --tool-dir) TOOL_DIR="$2"; shift ;;
    --gui) GUI=1 ;;
    --check-update) CHECK_UPDATE=1 ;;
    --update) UPDATE=1 ;;
  esac
  shift
done

# --- 시동 안내 화면 1초 (SystemUI 패치) -------------------------------------------------------------------------
# 순정 SystemUI는 현대 코드라 고친 APK를 배포하지 않는다. 이 PC가 이 차의 원본을 받아, 해시가 확인된 빌드일 때만 그 자리에서
# 고쳐(우리 빌더: 공개 릴리스 i30pd-consent-fast.jar) 설치하고, 원본과 결과는 끝나면 PC에서 지운다. 도구는 해시 고정(install.ps1과 같은 표).
SYSUI_KNOWN='V141 9a5f71ba436c368ff98a14b6a1c978a593644e30ae5b51df2cb03e7537eb0f82 2b4a2b62d9e24993f8f8dafd4dc876520b92c25a83b51309699db97b4fab7662'
SYSUI_BOOT='core core-junit bouncycastle ext framework telephony-common mms-common android.policy services apache-xml automotive'
BOOT_TOOLS='baksmali.jar https://bitbucket.org/JesusFreke/smali/downloads/baksmali-2.5.2.jar d3116248cce4f82ec5a31eb7f95ee75daff42ddf6eed0ab573973dc53fbad2e5 -
smali.jar https://bitbucket.org/JesusFreke/smali/downloads/smali-2.5.2.jar 9544299578b16f771d8aa8eaefe0d3718ca03478c16f3c356f2fcf1366bfb116 -
apksig.jar https://dl.google.com/dl/android/maven2/com/android/tools/build/apksig/8.3.0/apksig-8.3.0.jar 468ee1852fa12461c6629608fe47be70e50f60be7c4744bb7f62ca48e85c0a6c -
platform.pk8 https://android.googlesource.com/platform/build/+/android-4.2.2_r1/target/product/security/platform.pk8?format=TEXT 1ad8ef556870edb70f69a9d3c112544c07de5162ba440d84d33f8bb0c5962875 b64
platform.x509.pem https://android.googlesource.com/platform/build/+/android-4.2.2_r1/target/product/security/platform.x509.pem?format=TEXT 9837de028f460c35cc8d3fa45f14eecce30f6fbfe4b93d399aef1acb80c20d14 b64'
JRE_BASE='https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.21.1%2B2'
case "$OS-$(uname -m)" in   # Eclipse Temurin 17 JRE, hash-pinned per platform (only when no Java 11+ is there)
  Darwin-x86_64) JRE="OpenJDK17U-jre_x64_mac_hotspot_17.0.21.1_2.tar.gz 5b92ba9954ca63470e896cb50acd17c94dc95d03208803617d04dc53ea2c845b" ;;
  Darwin-arm64) JRE="OpenJDK17U-jre_aarch64_mac_hotspot_17.0.21.1_2.tar.gz 398046289ca1acef2d3800d74c55a31f08b2fee32a17ee7c1c4e4ad7489f8311" ;;
  Linux-x86_64|Linux-amd64) JRE="OpenJDK17U-jre_x64_linux_hotspot_17.0.21.1_2.tar.gz 5a092a0bc21de4c3d855480edd7945be9cfe157b38cdb84dac280511893bb009" ;;
  Linux-aarch64|Linux-arm64) JRE="OpenJDK17U-jre_aarch64_linux_hotspot_17.0.21.1_2.tar.gz ed968eb2cde06ba38c3d957956cab7faced9181aaeebed4904f89e2e6efa8d11" ;;
  *) JRE='' ;;
esac
BUILDER_JAR='i30pd-consent-fast.jar'
pkg_of()  { case "$1" in dashboard-*) echo com.veloster.dashboard ;; *) echo com.veloster.carplayhub ;; esac; }
name_of() { case "$1" in dashboard-*) echo '대시보드' ;; *) echo "'내 앱'" ;; esac; }

if [ "$OS" = Darwin ]; then LOGDIR="$HOME/Library/Logs/i30pd-installer"; else LOGDIR="${XDG_STATE_HOME:-$HOME/.local/state}/i30pd-installer"; fi
mkdir -p "$LOGDIR"
LOG="$LOGDIR/install_$(date +%Y%m%d_%H%M%S).log"

log()  { printf '%s\n' "$1" >> "$LOG"; }   # 기록 파일에만 (문제 제보용)
# 파일을 파일 관리자에서 보여 준다: 맥 Finder, 리눅스는 화면이 있을 때 그 폴더를 연다(라즈베리파이 SSH 등 화면 없으면 경로만).
reveal() { if [ "$OS" = Darwin ]; then open -R "$1" 2>/dev/null
           elif [ -n "$DISPLAY$WAYLAND_DISPLAY" ] && command -v xdg-open >/dev/null; then xdg-open "$(dirname "$1")" >/dev/null 2>&1 & fi; }
os_name() { if [ "$OS" = Darwin ]; then echo "macOS $(sw_vers -productVersion 2>/dev/null)"
            else (. /etc/os-release 2>/dev/null; echo "${PRETTY_NAME:-Linux}"); fi; }
note() { printf '%s\n' "$1"; log "$1"; }
head_() { local t; t="$(date +%H:%M:%S)"; [ -n "$GUI" ] && printf '@@STEP %s
' "$1"; printf '\n\033[36m%s   [%s]\033[0m\n' "$1" "$t"; log "== $1 == $t"; }
ok()   { printf '\033[32m%s\033[0m\n' "$1"; printf '%s\n' "$1" >> "$LOG"; }
warn() { printf '\033[33m%s\033[0m\n' "$1"; printf '%s\n' "$1" >> "$LOG"; }
# finish <코드> [창 완료 칸에 쓸 말]
finish() { if [ -n "$GUI" ]; then if [ "$1" = 0 ]; then printf '@@DONE ok%s
' "${2:+ $2}"; else printf '@@DONE fail %s
' "${2:-사용자 취소}"; fi; exit "$1"; fi
          [ -z "$YES" ] && { echo; read -r -p '창을 닫으려면 Enter' _; }; exit "$1"; }
die()  { printf '\n\033[31m%s\033[0m\n' "$1"; log "ERROR: $1"
         warn "기록 파일: $LOG"
         warn '문제를 알릴 때 이 기록 파일을 함께 보내 주세요.'
         [ -n "$GUI" ] && { printf '@@FILE %s
' "$LOG"; finish "$2" "$(printf '%s
' "$1" | head -1)"; }
         [ -z "$YES" ] && reveal "$LOG"
         finish "$2"; }
# --gui: 창이 예/아니오 창을 띄우고 답 한 줄(y/n)을 표준 입력으로 보낸다. 답이 없으면(창이 닫힘) 아니오.
ask()  { if [ -n "$GUI" ]; then printf '@@ASK %s
' "$1"; r=''; read -r r; log "$1 → $r"; case "$r" in [Yy]*) return 0 ;; *) return 1 ;; esac; fi
         [ -n "$YES" ] && return 0; read -r -p "$1 [Y/n] " r; case "$r" in ''|[Yy]*) return 0 ;; *) return 1 ;; esac; }
usb_off() { note 'USB 디버깅 끄기: 시동을 껐다 켜면 저절로 꺼집니다. 바로 끄려면 Factory Test에서  0A 01 → 65 00 → 0A 02'
            note "  (또는 '내 앱' → USB 디버깅 → OFF). 끄면 노트북 연결이 끊깁니다."; }
# 진단 기록에서 사람을 가릴 수 있는 것: MAC 주소, 휴대폰 번호, 메일 주소, adb 기기 번호, 경로 속 사용자 이름($HOME). perl은 macOS·데비안 계열 기본, 바이트 단위(ASCII 패턴만이라 충분).
mask() { S="$SERIAL" perl -pe 's/\b(?:[0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}\b/<MAC>/g; s/\b01[016789][-. ]?\d{3,4}[-. ]?\d{4}\b/<전화번호>/g;
  s/[\w.+-]+@[\w-]+(?:\.[\w-]+)+/<메일>/g; s/\Q$ENV{S}\E/<기기번호>/g if length $ENV{S}; s/\Q$ENV{HOME}\E/~/g if length $ENV{HOME}' ; }
adb_() { local o; o="$("$ADB" "$@" 2>&1)"; printf '> adb %s\n%s\n' "$*" "$o" >> "$LOG"; printf '%s' "$o"; }
# 오래 걸릴 수 있는 adb(전송·설치)는 제한 시간 안에서 돌리고 기다리는 동안 경과 초를 같은 줄에 찍는다:
# 멈춘 화면의 스크린샷만 봐도 어느 단계에서 몇 초째 멈췄는지 보이게. 결과는 $OUT, 시간 초과면 return 1.
adb_wait() {
  local sec="$1" what="$2" f="$LOGDIR/adb_out.txt" t=0 pid; shift 2
  log "> adb $*  (최대 ${sec}초)"
  "$ADB" "$@" > "$f" 2>&1 & pid=$!
  [ -n "$GUI" ] && printf '@@WAIT 0 %s
' "$what"
  printf '  %s ' "$what"
  while kill -0 $pid 2>/dev/null; do
    sleep 1; t=$((t + 1)); [ $((t % 5)) -eq 0 ] && printf '\033[90m%ss\033[0m ' $t
    if [ $t -ge $sec ]; then
      kill $pid 2>/dev/null; printf '\033[31m멈춤(%s초)\033[0m\n' $sec; log "  → ${sec}초 넘게 응답 없음, 중단"; OUT=''; return 1
    fi
  done
  wait $pid; OUT="$(cat "$f")"; printf '\033[90m(%s초)\033[0m\n' $t; log "$OUT  (${t}초)"; return 0
}
# 전송·설치가 멈췄을 때: 멈춘 곳과 할 일, 내비의 설치 로그 끝부분을 화면에 띄우고 끝낸다.
stuck() {
  printf '\033[31m멈춘 곳: %s\033[0m\n' "$1"; log "멈춘 곳: $1"; warn "  $2"
  if adb_wait 15 '내비 설치 로그 읽는 중' -s "$SERIAL" logcat -d -t 300 -s 'PackageManager:*' 'PackageParser:*' 'installd:*' 'DefContainerService:*' 'dalvikvm:E' && [ -n "$OUT" ]; then
    note '  설치 로그(마지막 줄):'; printf '%s\n' "$OUT" | tr -d '\r' | grep . | tail -12 | while IFS= read -r l; do note "    $l"; done
  fi
  die "$1 단계에서 멈췄습니다. 이 화면을 찍어 보내 주시면 원인을 볼 수 있습니다." 1
}
PM_HINT='내비가 설치를 끝내지 않았습니다. 차 화면에 확인 창이 떠 있으면 누르고, 없으면 시동을 껐다 켠 뒤 다시 실행하세요.'

echo
if [ -n "$UNINSTALL" ]; then echo "  i30 PD 대시보드 삭제   (설치기 $INSTALLER_VERSION)"
elif [ -n "$DIAG" ]; then echo "  i30 PD 대시보드 진단 기록   (설치기 $INSTALLER_VERSION)"
else echo "  i30 PD 대시보드 설치   (설치기 $INSTALLER_VERSION)"; fi
echo '  ----------------------'
warn '현대자동차 공식 앱이 아닙니다. 순정 시스템에 앱을 설치하는 작업이라 보증/AS 불이익이 있을 수 있고,'
warn '책임은 본인에게 있습니다. 모든 작업은 정차 · 시동 켠 상태에서 하세요.'
note "기록: $LOG"

# --- 새 설치기 (막지는 않는다) ------------------------------------------------------
# 이 설치기가 나온 릴리스(안정화 = 최신 정식, 테스트 = 'test')의 ZIP(맥 -mac, 리눅스 -linux)을 받아, 릴리스 정보의 크기·SHA-256과 맞고
# 풀어서 이 스크립트가 있을 때만 그 안의 INSTALLER_VERSION과 비교한다. 더 새것이면 묻고, 예면 이 폴더의 파일을 바꾸고 같은 인자로
# 다시 시작한다. 인터넷이 없거나 무엇이든 틀리면 지금 설치기로 계속한다(platform-tools·APK 등 다른 파일은 건드리지 않는다).
sha_() { if command -v sha256sum >/dev/null; then sha256sum "$1"; else shasum -a 256 "$1"; fi | cut -d' ' -f1; }
asset_meta() {   # $1 = 릴리스 JSON, $2 = 자산 이름 → "크기 digest 주소" (맥은 기본 JavaScript, 그 밖은 python3; 못 읽으면 빈 줄)
  if command -v osascript >/dev/null; then osascript -l JavaScript - "$1" "$2" 2>>"$LOG" <<'JXA'
function run(argv){ObjC.import('Foundation');var r=JSON.parse($.NSString.stringWithContentsOfFileEncodingError(argv[0],$.NSUTF8StringEncoding,null).js);
  var a=(r.assets||[]).filter(function(x){return x.name===argv[1];})[0];return a?[a.size,a.digest||'-',a.browser_download_url].join(' '):'';}
JXA
  elif command -v python3 >/dev/null; then python3 -I -c 'import json,sys
a=[x for x in json.load(open(sys.argv[1],encoding="utf-8")).get("assets") or [] if x.get("name")==sys.argv[2]]
a and print(a[0]["size"], a[0].get("digest") or "-", a[0]["browser_download_url"])' "$1" "$2" 2>>"$LOG"; fi
}
self_update() {
  [ -n "$I30PD_UPDATED" ] && return 0   # 방금 바꿔 다시 시작한 것
  [ -n "$GUI" ] && [ -z "$UPDATE" ] && return 0   # 창 설치기: 창의 [업데이트] 단추가 맡는다
  command -v curl >/dev/null || { skip_ 'curl이 없습니다.'; return 0; }
  local ch=안정화 which=latest main=install.sh asset=i30pd-installer-linux.zip up="$LOGDIR/update" meta v='' bad='' f n
  [ "$INSTALLER_CHANNEL" = test ] && { ch=테스트; which=tags/test; }
  [ "$OS" = Darwin ] && { main=설치.command; asset=i30pd-installer-mac.zip; }
  rm -rf "$up"; mkdir -p "$up/new"
  curl -fsS --max-time 8 -H 'User-Agent: i30pd-installer' -o "$up/release.json" "$API/$which" 2>>"$LOG" || { log "새 설치기 확인 못 함($which)"; skip_ '새 설치기를 받지 못했습니다(인터넷 확인).'; return 0; }
  meta="$(asset_meta "$up/release.json" "$asset")"; [ -n "$meta" ] || { log "새 설치기 확인: 릴리스($which)에 $asset 없음(또는 읽지 못함)"; skip_ "릴리스($ch)에 $asset 이 없습니다."; return 0; }
  set -- $meta   # 크기 digest 주소
  curl -fsSL --connect-timeout 15 --max-time 60 -o "$up/installer.zip" "$3" 2>>"$LOG" || { log "새 설치기 받기 실패: $3"; skip_ '새 설치기를 받지 못했습니다(인터넷 확인).'; return 0; }
  if [ "$(wc -c < "$up/installer.zip" | tr -d ' ')" != "$1" ]; then bad='크기가 릴리스 정보와 다름'
  elif [ "$2" != - ] && [ "$2" != "sha256:$(sha_ "$up/installer.zip")" ]; then bad='SHA-256이 릴리스 정보와 다름'
  elif ! { if command -v unzip >/dev/null; then unzip -qo "$up/installer.zip" -d "$up/new"; else python3 -I -m zipfile -e "$up/installer.zip" "$up/new"; fi; } >>"$LOG" 2>&1; then bad='ZIP을 풀지 못함'
  elif [ ! -f "$up/new/$main" ]; then bad="ZIP에 $main 이 없음"
  else v="$(sed -n "s/^INSTALLER_VERSION='\([0-9]\{4\}-[0-9][0-9]-[0-9][0-9][^']*\)'.*/\1/p" "$up/new/$main" | head -1)"; [ -n "$v" ] || bad="$main 에서 버전을 읽지 못함"; fi
  if [ -n "$bad" ]; then skip_ "받은 새 설치기($ch)의 확인이 맞지 않아 쓰지 않습니다: $bad"; warn "새 설치기($ch)를 받았지만 확인이 맞지 않아 쓰지 않습니다: $bad. 지금 설치기로 계속합니다."; return 0; fi
  log "새 설치기 확인($which): $v, 지금 $INSTALLER_VERSION"
  { [ "$v" != "$INSTALLER_VERSION" ] && [ "$(printf '%s\n%s\n' "$v" "$INSTALLER_VERSION" | LC_ALL=C sort | tail -1)" = "$v" ]; } || return 0
  [ -n "$CHECK_UPDATE" ] && { printf '@@UPDATE %s
' "$v"; exit 0; }
  warn "새 설치기가 나왔습니다: $v ($ch, 지금 쓰는 것 $INSTALLER_VERSION)"
  [ -n "$UPDATE" ] || ask '새 설치기로 바꾸고 다시 시작할까요? (아니오: 지금 설치기로 계속)' || { log '새 설치기: 바꾸지 않음'; return 0; }
  # bash는 실행 중인 스크립트를 읽어 가며 돌아서 덮어쓰면 안 된다: 옆에 쓴 뒤 mv로 이름만 바꾼다(지금 도는 옛 파일은 끝까지 그대로).
  for f in "$up/new"/*; do
    n="$(basename "$f")"
    { cp "$f" "$ROOT/.$n.new" && chmod +x "$ROOT/.$n.new" && mv -f "$ROOT/.$n.new" "$ROOT/$n"; } 2>>"$LOG" \
      || { rm -f "$ROOT/.$n.new"; skip_ "새 설치기로 바꾸지 못했습니다($ROOT)."; warn "새 설치기로 바꾸지 못했습니다($ROOT). 지금 설치기로 계속합니다."; return 0; }
  done
  if [ -n "$UPDATE" ]; then   # 창: 새 창 설치기를 열게 알리고 끝낸다(이 앱은 닫힌다)
    ok "새 설치기($v)로 바꿨습니다."; [ -f "$ROOT/창 설치기.command" ] && printf '@@OPEN %s
' "$ROOT/창 설치기.command"
    finish 0 '새 설치기를 엽니다.'
  fi
  ok "새 설치기($v)로 바꿨습니다. 다시 시작합니다."
  I30PD_UPDATED=1 exec bash "$ROOT/$main" "${ARGS[@]}"
}
# 창의 [업데이트](--update): 무엇이든 틀리면 멈춰 창에 알린다. 창이 뜰 때의 확인(--check-update): 조용히 끝낸다.
skip_() { [ -n "$UPDATE" ] && die "$1" 2; [ -n "$CHECK_UPDATE" ] && exit 0; return 0; }
self_update
[ -n "$CHECK_UPDATE" ] && exit 0
[ -n "$UPDATE" ] && { ok '이미 최신 설치기입니다.'; finish 0 '이미 최신 설치기입니다.'; }

# --- adb 찾기/받기 ------------------------------------------------------------
head_ '1) adb 준비'
if [ -z "$ADB" ]; then
  for c in "$ROOT/platform-tools/adb" "$ROOT/adb"; do [ -x "$c" ] && { ADB="$c"; break; }; done
fi
[ -z "$ADB" ] && ADB="$(command -v adb)"
command -v curl >/dev/null || die 'curl이 없습니다. 리눅스는  sudo apt install curl  로 설치한 뒤 다시 실행하세요.' 2
SLOW='--connect-timeout 15 --speed-limit 1000 --speed-time 30'   # 내려받기: 연결 15초, 30초 동안 1 KB/s도 안 오면 그만둔다(끊긴 인터넷에서 멈춘 듯 보이지 않게)
if [ -z "$ADB" ] && [ -n "$PT_URL" ]; then
  note 'adb(platform-tools)가 없습니다.'
  ask 'Google 공식 주소에서 받을까요?' || die 'adb가 없어 중단합니다.' 2
  note '내려받는 중...'
  curl -fsSL $SLOW -o "$LOGDIR/platform-tools.zip" "$PT_URL" || die "다운로드 실패: $PT_URL
인터넷 연결을 확인한 뒤 다시 실행하세요." 2
  # unzip이 없는 리눅스도 있다: python3의 zipfile로 대신 푼다(그때는 실행 권한을 붙인다).
  if command -v unzip >/dev/null; then unzip -qo "$LOGDIR/platform-tools.zip" -d "$ROOT" || die '압축 해제 실패' 2
  else python3 -I -m zipfile -e "$LOGDIR/platform-tools.zip" "$ROOT" || die '압축 해제 실패 (unzip 또는 python3 필요)' 2
       chmod +x "$ROOT/platform-tools/adb" 2>/dev/null; fi
  ADB="$ROOT/platform-tools/adb"
  [ -x "$ADB" ] || die 'adb를 준비하지 못했습니다.' 2
elif [ -z "$ADB" ]; then
  # ARM 리눅스(라즈베리파이 등): Google이 리눅스 ARM용 adb를 내지 않는다. 배포판 패키지는 USB 권한 규칙(udev)도 함께 깐다.
  note "adb가 없습니다. 이 기기($(uname -m))용 adb는 배포판 패키지로 설치합니다."
  command -v apt-get >/dev/null || die 'adb를 직접 설치한 뒤 다시 실행하세요(데비안·우분투·라즈베리파이 OS:  sudo apt install adb ).' 2
  ask 'sudo apt-get install adb 로 설치할까요? (관리자 암호를 물을 수 있습니다)' || die 'adb가 없어 중단합니다.  sudo apt install adb  로 설치한 뒤 다시 실행하세요.' 2
  sudo apt-get install -y adb >>"$LOG" 2>&1 || sudo apt-get install -y android-tools-adb >>"$LOG" 2>&1 || die 'adb 설치 실패. 기록 파일을 확인하세요.' 2
  ADB="$(command -v adb)"; [ -n "$ADB" ] || die 'adb를 준비하지 못했습니다.' 2
fi
note "adb: $ADB"
log "환경: $(os_name) / $(uname -m) / $("$ADB" version 2>&1 | head -1)"

# --- 릴리스 정보: 고를 버전의 번호·날짜와 노트 요약 (install.ps1 NotesSummary, 휴대폰 앱 Flow.summary와 같은 규칙) ---
# 첫 줄 = 화면용 제목, 나머지 = 노트 요약. 받지 못하면 아무것도 내지 않는다. JSON은 macOS 기본 JavaScript(osascript)로 읽는다.
rel_info() {   # $1 = latest | tags/test, $2 = 1 이면 테스트
  local f="$LOGDIR/release_$2.json"
  curl -fsS --max-time 8 -H 'User-Agent: i30pd-installer' -o "$f" "$API/$1" 2>>"$LOG" || { log "릴리스 정보 실패($1)"; return 0; }
  [ "$OS" = Linux ] && { rel_info_py "$f" "$2"; return 0; }
  osascript -l JavaScript - "$f" "$2" 2>>"$LOG" <<'JXA'
// RELEASE-JS-BEGIN (규칙은 휴대폰 앱 Flow.summary와 같다; 2026-11-04 실제 릴리스 응답 두 개에서 node로 돌려 Java 결과와 글자까지 같음을 확인)
function clean(t){return t.replace(/\[([^\]]+)\]\([^)]+\)/g,'$1').split('**').join('').split('`').join('');}
function shorten(t){var i=t.indexOf('다. ');if(i<0)i=t.indexOf('. ');if(i>=0)t=t.substring(0,i+2).trim();return t.length>90?t.substring(0,89)+'…':t;}
// 소제목(### 또는 **굵은 줄**)마다 첫 항목의 첫 문장, 설치·서명 절이나 테스트 노트의 지난 기록('> ')에서 멈춘다.
function summary(body,heads){var out=[],n=0,want=false,ls=body.split(/\r?\n/);
  for(var k=0;k<ls.length;k++){var l=ls[k].trim();
    if(n>0&&(l.indexOf('> ')===0||l.indexOf('### 설치')===0||l.indexOf('### 서명')===0))break;
    var h=null;if(l.indexOf('### ')===0)h=l.substring(4);else if(l.length>4&&l.indexOf('**')===0&&l.slice(-2)==='**')h=l.substring(2,l.length-2);
    if(h!==null){if(n===heads)break;n++;out.push('■ '+clean(h));want=true;continue;}
    if(want&&l.indexOf('- ')===0){out.push('   '+shorten(clean(l.substring(2))));want=false;}}
  return out;}
function info(r,test){var d=r.published_at||'';
  if(test)(r.assets||[]).forEach(function(a){if(a.name==='dashboard-i30pd-platform.apk')d=a.updated_at;});   // 테스트: APK를 마지막으로 바꾼 날
  d=d.substring(0,10);
  var title=test?(r.name||'test').replace(/^테스트\s*[—-]\s*/,'')+' · 갱신 '+d:r.tag_name+' · '+d;
  return [title].concat(summary(r.body||'',6)).join('\n');}
// RELEASE-JS-END
function run(argv){ObjC.import('Foundation');var s=$.NSString.stringWithContentsOfFileEncodingError(argv[0],$.NSUTF8StringEncoding,null).js;return info(JSON.parse(s),argv[1]==='1');}
JXA
}
# 리눅스: 위 JavaScript와 같은 규칙을 python3로(라즈베리파이 OS·우분투 기본). python3가 없으면 노트 안내만 빠진다.
rel_info_py() {
  command -v python3 >/dev/null || { log 'python3 없음: 노트 요약 생략'; return 0; }
  python3 -I - "$1" "$2" 2>>"$LOG" <<'PY'
# RELEASE-PY-BEGIN (RELEASE-JS와 같은 규칙; 2026-11-05 실제 릴리스 응답 두 개에서 node로 돈 JS 결과와 글자까지 같음을 확인)
import json, re, sys
def clean(t): return re.sub(r'\[([^\]]+)\]\([^)]+\)', r'\1', t).replace('**', '').replace('`', '')
def shorten(t):
    i = t.find('다. ')
    if i < 0: i = t.find('. ')
    if i >= 0: t = t[:i + 2].strip()
    return t[:89] + '…' if len(t) > 90 else t
def summary(body, heads):
    out, n, want = [], 0, False
    for l in re.split(r'\r?\n', body):
        l = l.strip()
        if n > 0 and (l.startswith('> ') or l.startswith('### 설치') or l.startswith('### 서명')): break
        h = l[4:] if l.startswith('### ') else l[2:-2] if len(l) > 4 and l.startswith('**') and l.endswith('**') else None
        if h is not None:
            if n == heads: break
            n += 1; out.append('■ ' + clean(h)); want = True; continue
        if want and l.startswith('- '): out.append('   ' + shorten(clean(l[2:]))); want = False
    return out
r = json.load(open(sys.argv[1], encoding='utf-8')); test = sys.argv[2] == '1'; d = r.get('published_at') or ''
if test:
    for a in r.get('assets') or []:
        if a.get('name') == 'dashboard-i30pd-platform.apk': d = a.get('updated_at') or d   # 테스트: APK를 마지막으로 바꾼 날
d = d[:10]
title = re.sub(r'^테스트\s*[—-]\s*', '', r.get('name') or 'test') + ' · 갱신 ' + d if test else r['tag_name'] + ' · ' + d
sys.stdout.buffer.write(('\n'.join([title] + summary(r.get('body') or '', 6)) + '\n').encode('utf-8'))   # UTF-8 whatever the locale
# RELEASE-PY-END
PY
}
show_notes() {   # $1 = rel_info 결과, $2 = 전체 노트 주소
  if [ -z "$1" ]; then note "버전 정보를 받지 못했습니다(인터넷 확인). 설치는 그대로 계속합니다. 전체 설명: $2"; return; fi
  head_ "이번 버전에 들어 있는 것 ($(printf '%s\n' "$1" | head -1))"
  printf '%s\n' "$1" | tail -n +2 | while IFS= read -r l; do note "$l"; done
  note "전체 설명: $2"
}

# --- 버전 고르기 --------------------------------------------------------------
if [ -z "$UNINSTALL$DIAG$BOOT_FAST$BOOT_REMOVE" ] && [ -z "$APK_BASE" ]; then
  REL_S="$(rel_info latest 0)"; REL_T="$(rel_info tags/test 1)"
  if [ "$CHANNEL" != stable ] && [ "$CHANNEL" != test ]; then
    if [ -n "$YES$GUI" ]; then CHANNEL=stable; else
      head_ '받을 버전'
      note "  1) 안정화 (권장)${REL_S:+ $(printf '%s\n' "$REL_S" | head -1)} — 실차에서 확인된 버전"
      note "  2) 테스트${REL_T:+ $(printf '%s\n' "$REL_T" | head -1)} — 새 기능, 아직 실차 검증 전. 문제가 있으면 안정화로 다시 설치하면 됩니다"
      note '  3) 진단 기록 모으기 — 설치하지 않고, 문제를 알릴 때 보낼 파일만 만듭니다'
      note '  4) 시동 안내 화면 10초 → 1초 — 순정 시스템 화면(SystemUI)을 이 PC에서 고쳐 설치합니다 (내비 업데이트 전엔 5번으로 되돌리기)'
      note '  5) 시동 안내 화면 원래대로 — 4번을 되돌립니다'
      read -r -p '번호를 입력하고 Enter [1] ' r
      case "$(printf '%s' "$r" | tr -d ' ')" in 2) CHANNEL=test ;; 3) DIAG=1 ;; 4) BOOT_FAST=1 ;; 5) BOOT_REMOVE=1 ;; *) CHANNEL=stable ;; esac
    fi
  fi
  if [ -z "$DIAG$BOOT_FAST$BOOT_REMOVE" ]; then
    if [ "$CHANNEL" = test ]; then APK_BASE="$RELEASES/download/test"; note '버전: 테스트 (실차 검증 전)'; show_notes "$REL_T" "$RELEASES/tag/test"
    else APK_BASE="$RELEASES/latest/download"; note '버전: 안정화'; show_notes "$REL_S" "$RELEASES/latest"; fi
  fi
fi

# --- APK 준비 (폴더에 있으면 쓰고, 없으면 공개 레포에서 받기) -----------------
NO_APPS="$UNINSTALL$DIAG$BOOT_FAST$BOOT_REMOVE"   # these modes install no app
[ -z "$NO_APPS" ] && head_ '0) 앱 파일 준비'
for apk in $([ -z "$NO_APPS" ] && echo $APKS); do
  # 브라우저로 받은 GitHub 페이지(HTML)가 .apk 이름으로 저장된 경우가 있어, 'PK'로 시작할 때만 폴더 것을 쓴다.
  if [ -f "$ROOT/$apk" ] && [ "$(head -c 2 "$ROOT/$apk")" = 'PK' ]; then warn "$apk: 설치기 폴더에 있는 파일을 씁니다 (위에서 고른 버전과 상관없이 이 파일이 설치됩니다)"; continue; fi
  [ -f "$ROOT/$apk" ] && warn "$apk: 폴더 파일이 APK가 아니라 무시하고 내려받습니다."
  note "$apk: 폴더에 없어 내려받습니다..."
  curl -fsSL $SLOW -o "$LOGDIR/$apk" "$APK_BASE/$apk" || die "APK 다운로드 실패: $APK_BASE/$apk
인터넷 연결을 확인한 뒤 다시 실행하세요. 인터넷이 안 되는 곳이라면, 다른 곳에서 받은 APK 두 개를 $ME 옆에 두고 실행하면 됩니다." 2
  # APK(=ZIP)는 'PK'로 시작. 404 안내문 등이 저장된 경우를 거른다.
  [ "$(head -c 2 "$LOGDIR/$apk")" = 'PK' ] || die "받은 파일이 APK가 아닙니다($apk): $APK_BASE/$apk" 2
  note "$apk: 받음 ($(( $(wc -c < "$LOGDIR/$apk") / 1024 )) KB)"
done

# --- 차 화면 안내 + 연결 대기 -------------------------------------------------
head_ '2) 차 화면에서 먼저 (사람이 직접)'
note '  (1) 설정 → 시스템 정보 → 저장 공간 화면'
note '  (2) 내용 영역 좌하단 모서리 5회 탭 → 우하단 모서리 1회 탭 (엔지니어 모드)'
note '  (3) 암호 입력 → Diagnosis → Factory Test 에서  0A 01 → 65 01 → 0A 02  전송'
if [ "$OS" = Darwin ]; then note '  (4) USB 케이블 연결 (USB-C 맥이면 USB-A 어댑터 필요)'
else note '  (4) USB 케이블 연결 (데이터가 되는 케이블. 라즈베리파이는 USB-A 포트에)'; fi
[ -z "$YES$GUI" ] && read -r -p '위 단계가 끝나고 USB를 연결했으면 Enter ' _

head_ '3) 차 연결 확인'
"$ADB" start-server >/dev/null 2>&1
SERIAL=''; tries=0
# 기다리는 동안 같은 안내를 5초마다 다시 찍지 않는다: 바뀔 때만 새 줄, 같으면 그 줄 끝의 경과 초만 고친다.
WAIT_MSG=''; WAIT_T0=$(date +%s)
wait_() { if [ -n "$GUI" ]; then [ "$1" != "$WAIT_MSG" ] && { log "$1"; WAIT_MSG="$1"; }; printf '@@WAIT %s %s
' $(( $(date +%s) - WAIT_T0 )) "$1"; return; fi
          if [ "$1" != "$WAIT_MSG" ]; then [ -n "$WAIT_MSG" ] && echo; log "$1"; WAIT_MSG="$1"; fi
          printf '\r\033[%sm%s  %s초 \033[0m' "${2:-0}" "$1" $(( $(date +%s) - WAIT_T0 )); }
wait_end() { [ -n "$WAIT_MSG" ] && [ -z "$GUI" ] && echo; WAIT_MSG=''; }
udev_fix() {
  warn '이 PC가 USB 장치(차)를 읽을 권한이 없습니다(리눅스 udev 규칙 없음).'
  note '  /etc/udev/rules.d/51-i30pd.rules 에 차(VID 18d1, PID d00d) 한 줄만 넣으면 됩니다. 다른 기기는 건드리지 않습니다.'
  if ask '규칙을 넣을까요? (sudo 관리자 암호를 물을 수 있습니다)'; then
    if printf '%s\n' 'SUBSYSTEM=="usb", ATTR{idVendor}=="18d1", ATTR{idProduct}=="d00d", MODE="0666"' | sudo tee /etc/udev/rules.d/51-i30pd.rules >>"$LOG" 2>&1 \
       && sudo udevadm control --reload-rules >>"$LOG" 2>&1 && sudo udevadm trigger >>"$LOG" 2>&1; then
      "$ADB" kill-server >/dev/null 2>&1; ok '규칙을 넣었습니다. USB 케이블을 뺐다가 다시 꽂으세요.'
    else warn '규칙을 넣지 못했습니다.  sudo adb kill-server; sudo adb devices  로 차가 보이는지 확인하세요.'; fi
  else note '  직접 하려면:  sudo adb kill-server; sudo adb devices  (또는 위 규칙 파일을 넣고 다시 꽂기)'; fi
}
while [ -z "$SERIAL" ]; do
  dl="$("$ADB" devices -l 2>&1)"; printf '%s\n' "$dl" >> "$LOG"
  SERIAL="$(printf '%s\n' "$dl" | awk '$2=="device" && /product:pd_kr( |$)/ {print $1; exit}')"   # exactly pd_kr, not pd_kr2
  [ -n "$SERIAL" ] && break
  [ -n "$YES" ] && die '차(pd_kr)를 찾지 못했습니다.' 1
  if printf '%s' "$dl" | grep -q unauthorized; then wait_ '→ 차 화면에 "USB 디버깅 허용" 창이 뜨면 [항상 허용] 하세요.' 33
  elif printf '%s\n' "$dl" | awk '$2=="device"' | grep -q .; then wait_ '→ 차가 아닌 기기만 보입니다(휴대폰 등). 그 기기는 건드리지 않습니다.' 33
  elif printf '%s' "$dl" | grep -q 'no permissions'; then
    # 리눅스: USB 장치를 읽을 권한이 없다(udev 규칙 없음). 처음 한 번 규칙을 넣을지 묻는다.
    if [ -z "$UDEV_ASKED" ] && [ "$OS" = Linux ]; then UDEV_ASKED=1; wait_end; udev_fix; fi
    wait_ '→ USB 권한이 없어 차를 읽지 못합니다. 케이블을 뺐다가 다시 꽂으세요.' 33
  else wait_ '→ 차를 기다리는 중... USB 디버깅/Factory Test(0A 01/65 01/0A 02) 다시 확인.'; fi
  tries=$((tries + 1))
  # 5초 간격으로 1분. 차 화면에서 허용을 눌러야 하는 상태면 3분까지 기다린다.
  limit=12; printf '%s' "$dl" | grep -q unauthorized && limit=36
  [ -n "$GUI" ] && limit=60   # 창 설치기: Enter로 준비를 알리지 않으므로 차 화면 단계를 할 시간까지 5분 기다린다
  if [ $tries -ge $limit ]; then
    wait_end
    # 못 찾음: 이 PC가 본 USB 장치 목록을 기록에 남긴다(차가 USB에 아예 안 나타났는지 가르는 근거).
    if [ "$OS" = Darwin ]; then log "== USB 장치 목록 (system_profiler) =="; system_profiler SPUSBDataType 2>/dev/null >> "$LOG"
    else log "== USB 장치 목록 (lsusb) =="; lsusb 2>/dev/null >> "$LOG"; fi
    if printf '%s' "$dl" | grep -q unauthorized; then warn '마지막 상태: 차 화면에서 USB 디버깅 허용을 누르지 않음'
    elif printf '%s\n' "$dl" | awk '$2=="device"' | grep -q .; then warn '마지막 상태: pd_kr이 아닌 기기만 보임'
    elif printf '%s' "$dl" | grep -q 'no permissions'; then warn '마지막 상태: USB 권한 없음 — 위 udev 규칙을 넣고 케이블을 다시 꽂거나, sudo 로 실행'
    else warn '마지막 상태: 이 PC에 차가 adb 기기로 나타나지 않음 — Factory Test(0A 01/65 01/0A 02)·케이블·데이터 포트 확인'
         warn '가장 흔한 원인은 Factory Test 입력 실수입니다: 케이블을 빼고 0A 01 / 65 01 / 0A 02 를 띄어쓰기까지 정확히, 한 줄씩 SEND 한 뒤 다시 꽂으세요.'
         warn '같은 케이블로 다른 차는 된다면 이 차의 USB 포트·배선 쪽입니다(블루링크 없는 내비도 설치된 사례가 있습니다).'; fi
    die "시간 초과($((limit * 5))초). 차를 찾지 못했습니다. 위 상태를 고친 뒤 $ME 를 다시 실행하세요." 1
  fi
  sleep 5
done
wait_end
ok "차 연결됨: $SERIAL"

# --- 내비 정보 · 빌드 확인 -------------------------------------------------------
# 정해 둔 항목만 읽는다: getprop 전체에는 시리얼·블루투스 주소 등 개인 정보가 있어, 공개 Issues에 올라갈 기록 파일에 넣지 않는다.
prop() { adb_ -s "$SERIAL" shell getprop "$1" | tr -d '\r\n'; }
for k in ro.product.name ro.build.product ro.build.fingerprint sys.usb.config persist.sys.usb.config persist.sys.dipo.state; do prop $k >/dev/null; done
device="$(prop ro.product.device)"; build="$(prop ro.build.version.incremental)"; usbst="$(prop sys.usb.state)"
case "$device" in
  *daudiopluslow*) board='D-Audio Plus LOW (블루링크 없음)' ;;
  *daudioplus*)    board='D-Audio Plus (블루링크)' ;;
  *)               board="확인 안 됨 ($device)" ;;
esac
note "내비: $board / 빌드 $build / USB $usbst"
# 차에 깔린 두 앱의 버전 한 줄. 1.5-i18n = 릴리스 이름을 넣기 전(v1.16.0까지)의 대시보드.
versions() {
  local apk n o=''
  for apk in $APKS; do
    n="$(adb_ -s "$SERIAL" shell dumpsys package "$(pkg_of $apk)" | tr -d '\r' | sed -n 's/.*versionName=\([^ ]*\).*/\1/p' | head -1)"
    [ "$n" = 1.5-i18n ] && n='v1.16.0 이하'
    o="$o${o:+ · }$(name_of $apk) ${n:-없음}"
  done
  printf '%s' "$o"
}
cur="$(versions)"
note "차에 깔린 버전: $cur"
# 받을 버전을 바로 아래에: 안정화는 차의 대시보드와 이름이 같으면 그 사실을 알린다(테스트 빌드 이름은 릴리스 정보에 없다).
if [ -z "$NO_APPS" ]; then
  if [ "$CHANNEL" = test ] && [ -n "${REL_T:-}" ]; then note "받을 버전: 테스트 ($(printf '%s\n' "$REL_T" | head -1))"
  elif [ "$CHANNEL" = stable ] && [ -n "${REL_S:-}" ]; then
    tag="$(printf '%s\n' "$REL_S" | head -1 | cut -d' ' -f1)"
    case " $cur " in *" $tag "*) note "받을 버전: $tag — 차에 이미 같은 버전이 있습니다. 그대로 다시 설치합니다." ;; *) note "받을 버전: $tag" ;; esac
  fi
fi

# --- 진단 기록 모드: 읽기만 한다 -------------------------------------------------
# 정해 둔 속성, 두 앱의 버전, 우리 앱 태그·충돌·설치 기록만. 차 기록 전체에는 블루투스로 붙은 폰 이름·번호·연락처가 섞인다.
if [ -n "$DIAG" ]; then
  head_ '진단 기록 모으기 (차에는 아무것도 바꾸지 않습니다)'
  TAGS='VehicleDash:V VNCarPlayHub:V VNQuickEntry:V NDashboard:V DtcDialog:V AndroidRuntime:E PackageManager:I'
  desk="$HOME/Desktop"; [ "$OS" = Linux ] && desk="$(xdg-user-dir DESKTOP 2>/dev/null || echo "$HOME/Desktop")"
  [ -d "$desk" ] || desk="$HOME"; [ -d "$desk" ] || desk="$LOGDIR"
  out="$desk/i30pd_진단기록_$(date +%Y%m%d_%H%M%S).txt"
  {
    echo "i30 PD 진단 기록 — $(date '+%Y-%m-%d %H:%M') / PC 설치기 $INSTALLER_VERSION ($(os_name))"
    echo; echo '[내비]'
    for k in ro.product.name ro.product.device ro.build.product ro.build.version.incremental ro.build.fingerprint \
             sys.usb.config sys.usb.state persist.sys.usb.config persist.sys.dipo.state persist.sys.key.custom; do echo "$k: $(prop $k)"; done
    echo "시각: $(adb_ -s "$SERIAL" shell date | tr -d '\r')"
    echo; echo '[앱]'
    for apk in $APKS; do
      echo "$(name_of $apk) ($(pkg_of $apk))"
      v="$(adb_ -s "$SERIAL" shell dumpsys package "$(pkg_of $apk)" | tr -d '\r' | grep -E '(versionCode|versionName|firstInstallTime|lastUpdateTime|codePath)=' | sed 's/^ */  /' | awk '!seen[$0]++')"
      [ -n "$v" ] && printf '%s\n' "$v" || echo '  설치돼 있지 않음'
    done
    echo; echo "[기록: $TAGS]"
    "$ADB" -s "$SERIAL" shell logcat -d -v time -s $TAGS 2>&1 | tr -d '\r' | tail -3000   # 설치기 기록에는 안 남긴다(아래에 그 기록을 붙임)
    echo; echo '[이 PC 설치기 기록]'; cat "$LOG"
  } | mask > "$out"
  ok "만든 파일: $out"
  note '문제를 알릴 때 이 파일을 보내 주세요. 증상(언제, 어떤 화면에서, 무엇이)도 함께 알려 주세요.'
  if [ -n "$GUI" ]; then printf '@@FILE %s
' "$out"; elif [ -z "$YES" ]; then reveal "$out"; fi
  usb_off; finish 0 '진단 기록 파일을 만들었습니다([파일 보기]).'
fi
# --- 시동 안내 화면 1초: 되돌리기 · 설치 (위 SYSUI_KNOWN 설명 참고) -----------------------------------------
sysui_path() { adb_ -s "$SERIAL" shell pm path com.android.systemui | tr -d '\r\n ' | sed 's/^package://'; }
sysui_code() { adb_ -s "$SERIAL" shell dumpsys package com.android.systemui | tr -d '\r' | sed -n 's/.*versionCode=\([0-9]*\).*/\1/p' | head -1; }
BOOT_HINT='내비가 응답하지 않습니다. 차 화면에 확인 창이 떠 있으면 누르고, 없으면 시동을 껐다 켠 뒤 다시 실행하세요.'
if [ -n "$BOOT_REMOVE" ]; then
  head_ '시동 안내 화면 원래대로'
  p="$(sysui_path)"
  case "$p" in /data/app/*) ;; *) ok "이미 순정입니다 ($p)."; usb_off; finish 0 '이미 순정입니다.' ;; esac
  adb_wait 90 '되돌리는 중' -s "$SERIAL" uninstall com.android.systemui || stuck '시동 안내 되돌리기' "$BOOT_HINT"
  p="$(sysui_path)"
  case "$p" in /system/*) ok '되돌림 완료: 다음 시동부터 순정(10초 뒤 닫힘)입니다. 화면 위 상태 표시줄이 잠깐 다시 그려질 수 있습니다.'; usb_off; finish 0 '시동 안내 화면을 원래대로 되돌렸습니다.' ;; esac
  die "되돌리지 못했습니다 (지금: $p):
$OUT" 1
fi
if [ -n "$BOOT_FAST" ]; then
  head_ '시동 안내 화면 10초 → 1초'
  note '시동 때 뜨는 순정 안내 화면("실제 교통 법규를 준수하여…")이 10초 대신 약 1초 뒤 스스로 닫히게 합니다.'
  note '순정 시스템 화면(SystemUI)을 이 차에서 받아 이 PC에서 고쳐 설치합니다. 고친 파일은 어디에도 올리지 않고 끝나면 이 PC에서도 지웁니다.'
  warn '내비 소프트웨어 업데이트 전에는 반드시 설치기 5번(원래대로)을 먼저 하세요. 업데이트 뒤 고친 화면이 남아 있으면 내비가 이상해질 수 있습니다.'
  p="$(sysui_path)"
  case "$p" in
    /data/app/*) vc="$(sysui_code)"
      [ "$vc" = 18 ] && { ok '이미 설치돼 있습니다(시동 안내 1초). 되돌리려면 설치기 5번.'; usb_off; finish 0 '이미 설치돼 있습니다(시동 안내 1초).'; }
      die "SystemUI에 이미 다른 업데이트가 깔려 있습니다($p, versionCode $vc). 손대지 않고 멈춥니다." 1 ;;
    /system/app/HKMC_SystemUI.apk) ;;
    *) die "순정 SystemUI 위치가 예상과 다릅니다($p). 손대지 않고 멈춥니다." 1 ;;
  esac
  ask '진행할까요?' || { warn '사용자 취소.'; finish 1; }
  CF="$LOGDIR/bootfast"; CAP="$CF/cap"; OUT_APK="$CF/SystemUI-1s.apk"
  boot_clean() { rm -rf "$CAP" "$OUT_APK"; }   # Hyundai code: never left on this PC
  bdie() { boot_clean; die "$1" "$2"; }
  boot_clean; mkdir -p "$CAP/framework"

  note '① 이 차의 SystemUI 받기'
  for f in HKMC_SystemUI.apk HKMC_SystemUI.odex; do
    adb_wait 120 "$f" -s "$SERIAL" pull "/system/app/$f" "$CAP/$f" || { boot_clean; stuck "$f 받기" "$BOOT_HINT"; }
    [ -f "$CAP/$f" ] || bdie "차에서 $f 를 받지 못했습니다.
$OUT" 1
  done
  ha="$(sha_ "$CAP/HKMC_SystemUI.apk")"; ho="$(sha_ "$CAP/HKMC_SystemUI.odex")"; known=''
  while read -r n a o; do [ "$a" = "$ha" ] && [ "$o" = "$ho" ] && known="$n"; done <<EOF_KNOWN
$SYSUI_KNOWN
EOF_KNOWN
  log "SystemUI apk $ha odex $ho -> $known"
  [ -n "$known" ] || bdie "이 내비의 SystemUI는 아직 확인되지 않은 빌드라 멈춥니다. 차에는 아무것도 바꾸지 않았습니다.
이 기능을 원하시면 이 기록 파일을 보내 주세요(내비 빌드 $build)." 3
  note "  확인된 빌드: $known"
  for b in $SYSUI_BOOT; do
    adb_wait 120 "$b.odex" -s "$SERIAL" pull "/system/framework/$b.odex" "$CAP/framework/$b.odex" || { boot_clean; stuck "$b.odex 받기" "$BOOT_HINT"; }
    [ -f "$CAP/framework/$b.odex" ] || bdie "차에서 $b.odex 를 받지 못했습니다.
$OUT" 1
  done

  note '② 도구 준비 (처음 한 번만 받습니다)'
  JAVA="$JAVA_PATH"
  [ -z "$JAVA" ] && JAVA="$(find "$LOGDIR/jre17" -path '*/bin/java' -type f 2>/dev/null | head -1)"
  if [ -z "$JAVA" ] && command -v java >/dev/null; then
    v="$(java -version 2>&1 | sed -n 's/.*version "\([0-9]*\).*/\1/p' | head -1)"; [ "${v:-0}" -ge 11 ] 2>/dev/null && JAVA="$(command -v java)"
  fi
  if [ -z "$JAVA" ]; then
    [ -n "$JRE" ] || bdie "이 컴퓨터 종류($(uname -s) $(uname -m))는 Java를 자동으로 받을 수 없습니다. Java 11 이상을 설치한 뒤 다시 실행하세요." 2
    set -- $JRE; note '  Java(Eclipse Temurin 17, 약 45 MB) 받는 중...'
    curl -fsSL -o "$LOGDIR/jre17.tar.gz" "$JRE_BASE/$1" || bdie "Java를 받지 못했습니다(인터넷 확인).
$JRE_BASE/$1" 2
    [ "$(sha_ "$LOGDIR/jre17.tar.gz")" = "$2" ] || { rm -f "$LOGDIR/jre17.tar.gz"; bdie 'Java 파일 해시가 맞지 않아 쓰지 않습니다. 다시 실행해 보세요.' 2; }
    mkdir -p "$LOGDIR/jre17" && tar -xzf "$LOGDIR/jre17.tar.gz" -C "$LOGDIR/jre17" && rm -f "$LOGDIR/jre17.tar.gz"
    JAVA="$(find "$LOGDIR/jre17" -path '*/bin/java' -type f | head -1)"
  fi
  log "java: $JAVA"
  TD="${TOOL_DIR:-$CF/tools}"; mkdir -p "$TD"
  while read -r f u h enc; do
    [ -f "$TD/$f" ] && [ "$(sha_ "$TD/$f")" = "$h" ] && continue
    note "  $f 받는 중..."
    curl -fsSL -o "$TD/$f.part" "$u" || bdie "$f 를 받지 못했습니다(인터넷 확인).
$u" 2
    if [ "$enc" = b64 ]; then { base64 -d < "$TD/$f.part" > "$TD/$f" 2>/dev/null || base64 -D -i "$TD/$f.part" -o "$TD/$f"; } && rm -f "$TD/$f.part"
    else mv -f "$TD/$f.part" "$TD/$f"; fi
    [ "$(sha_ "$TD/$f")" = "$h" ] || { rm -f "$TD/$f"; bdie "$f 해시가 맞지 않아 쓰지 않습니다." 2; }
  done <<EOF_TOOLS
$BOOT_TOOLS
EOF_TOOLS
  BJ="$TD/$BUILDER_JAR"
  if [ -z "$TOOL_DIR" ]; then   # our builder: from the public release (stable first, then the test pre-release)
    rm -f "$BJ"
    curl -fsSL -o "$BJ" "$RELEASES/latest/download/$BUILDER_JAR" 2>>"$LOG" || curl -fsSL -o "$BJ" "$RELEASES/download/test/$BUILDER_JAR" 2>>"$LOG" || rm -f "$BJ"
  fi
  [ "$(head -c 2 "$BJ" 2>/dev/null)" = 'PK' ] || bdie "빌더($BUILDER_JAR)를 받지 못했습니다. $RELEASES" 2

  note '③ 고치기 (1~2분)'
  bo="$("$JAVA" -jar "$BJ" "$CAP" "$TD" "$OUT_APK" 2>&1)"; rc=$?; log "$bo"
  [ $rc -eq 3 ] && bdie "확인되지 않은 SystemUI 빌드라 멈춥니다. 차에는 아무것도 바꾸지 않았습니다.
$bo" 3
  { [ $rc -eq 0 ] && [ -f "$OUT_APK" ]; } || bdie "고치는 중 멈췄습니다(코드 $rc). 차에는 아무것도 바꾸지 않았습니다.
$bo" 1
  note "  $(printf '%s\n' "$bo" | tail -1)"

  note '④ 설치'
  remote=/data/local/tmp/SystemUI-1s.apk
  adb_wait 60 '전송 (USB)' -s "$SERIAL" push "$OUT_APK" "$remote" || { boot_clean; stuck '시동 안내 전송' 'USB로 파일을 보내다 멈췄습니다. 케이블·포트를 바꾸고 Factory Test부터 다시 한 뒤 실행하세요.'; }
  adb_wait 120 '설치 (내비)' -s "$SERIAL" shell pm install -r "$remote"; irc=$?; io="$OUT"
  adb_wait 15 '정리' -s "$SERIAL" shell rm -f "$remote" >/dev/null
  boot_clean
  [ $irc -eq 0 ] || stuck '시동 안내 설치' "$BOOT_HINT"
  p="$(sysui_path)"; vc="$(sysui_code)"
  case "$p" in /data/app/*) ;; *) vc="($p)" ;; esac
  { printf '%s' "$io" | grep -q Success && [ "$vc" = 18 ]; } || die "설치되지 않았습니다 (지금: $p, versionCode $vc):
$io" 1
  ok '설치 완료.'
  ok '시동을 완전히 끄고(문을 잠가 내비가 꺼질 때까지) 다시 켜면 그때부터 안내 화면이 약 1초 뒤 닫힙니다.'
  note '후방 카메라·검은 화면이 먼저 떠 있으면 그게 끝난 뒤 1초입니다(순정 동작).'
  warn '되돌리기: 설치기 5번(시동 안내 화면 원래대로). 내비 소프트웨어 업데이트 전에 꼭 되돌리세요.'
  usb_off; finish 0 '시동을 완전히 껐다 켜면 안내 화면이 약 1초 뒤 닫힙니다.'
fi

if [ "$build" = "$LOW_BUILD" ]; then
  note '블루링크 없는 내비(LOW 보드) — 설치가 확인된 빌드입니다.'
elif [ "$build" = "$OLD_BUILD" ]; then
  note '블루링크 내비 2024-02 빌드 — 설치와 값이 확인된 빌드입니다.'
elif [ -n "$build" ] && [ "$build" != "$VERIFIED_BUILD" ] && [ -z "$UNINSTALL$BOOT_FAST$BOOT_REMOVE" ]; then   # removing needs no "values may be wrong" question; boot fast checks the SystemUI files
  warn "주의: 검증된 빌드($VERIFIED_BUILD)와 다릅니다. 값이 틀릴 수 있습니다."
  ask '그래도 계속할까요?' || { warn '사용자 취소.'; finish 1; }
fi

# --- 삭제 모드 --------------------------------------------------------------
if [ -n "$UNINSTALL" ]; then
  head_ '앱 삭제'
  # '내 앱'이 ☆ 버튼을 계기판으로 돌려 놨으면 순정 "없음"으로 되돌린다(앱이 사라지면 ☆가 아무것도 안 하게 됨).
  # IModeManager.setCustomKey(트랜잭션 34): '내 앱'과 같은 호출, 서비스 쪽 권한 검사 없음.
  if [ "$(prop persist.sys.key.custom)" = com.veloster.carplayhub.STAR_KEY ]; then
    adb_ -s "$SERIAL" shell service call com.hkmc.micom.mode 34 s16 com.mobis.intent.action.CUSTOM_SHORTCUT >/dev/null
    if [ "$(prop persist.sys.key.custom)" = com.veloster.carplayhub.STAR_KEY ]; then
      warn '☆ 버튼: 되돌리지 못했습니다. 삭제 뒤 ☆를 길게 눌러 설정 화면에서 기능을 다시 고르세요.'
    else ok '☆ 버튼: 원래대로(없음) 되돌림'; fi
  fi
  # 시동 안내 1초(설치기 4번)가 깔려 있으면 함께 되돌릴지 묻는다(--yes면 되돌림: 삭제는 순정으로 돌아가는 것).
  SYSUI_KEPT=''
  case "$(sysui_path)" in /data/app/*) if [ "$(sysui_code)" = 18 ]; then
    if ask '시동 안내 화면 1초(설치기 4번)도 순정으로 되돌릴까요?'; then
      adb_wait 90 '시동 안내 화면 되돌리기' -s "$SERIAL" uninstall com.android.systemui
      case "$(sysui_path)" in /system/*) ok '시동 안내 화면: 순정으로 되돌림' ;; *) warn "시동 안내 화면: 되돌리지 못했습니다. 설치기 5번으로 다시 해 보세요. $OUT"; SYSUI_KEPT=1 ;; esac
    else note '시동 안내 화면 1초는 그대로 둡니다(되돌리기: 설치기 5번).'; SYSUI_KEPT=1; fi
  fi ;; esac
  for apk in $APKS; do
    adb_wait 60 "$(name_of $apk) 삭제" -s "$SERIAL" uninstall "$(pkg_of $apk)" || stuck "$(name_of $apk) 삭제(내비)" "$PM_HINT"
    if printf '%s' "$OUT" | grep -q Success; then ok "$(name_of $apk): 삭제됨"; continue; fi
    # Not Success: never installed, or the uninstall failed. pm path tells the two apart.
    u="$OUT"
    # pm path prints nothing for an app that is not there. Anything else (an adb error, no answer) is a lost connection, not a removed app.
    if ! adb_wait 30 "$(name_of $apk) 확인" -s "$SERIAL" shell pm path "$(pkg_of $apk)" || { ! printf '%s' "$OUT" | grep -q 'package:' && [ -n "$(printf '%s' "$OUT" | tr -d '[:space:]')" ]; }; then
      die "차와 연결이 끊긴 것 같습니다. $(name_of $apk) 삭제를 확인하지 못했습니다.
케이블을 확인하고 다시 실행하세요.
$OUT" 1
    fi
    if printf '%s' "$OUT" | grep -q 'package:'; then
      die "$(name_of $apk) 삭제 실패 (앱이 아직 차에 있습니다):
$u" 1
    fi
    note "$(name_of $apk): 설치돼 있지 않음"
  done
  if [ -n "$SYSUI_KEPT" ]; then ok '앱 삭제 완료. 시동 안내 화면 1초만 남아 있습니다(설치기 5번으로 되돌리기).'
  else ok '삭제 완료. 순정 상태로 돌아갔습니다.'; fi
  usb_off; finish 0 "$([ -n "$SYSUI_KEPT" ] && echo '앱을 지웠습니다. 시동 안내 화면 1초는 남아 있습니다(5번으로 되돌리기).' || echo '삭제 완료. 순정 상태로 돌아갔습니다.')"
fi

# --- 설치 -------------------------------------------------------------------
head_ '4) 앱 설치'
# adb install 대신 ① 전송(push) ② 설치(pm install) 두 단계로 나눠, 멈추면 USB 쪽인지 내비 쪽인지 바로 갈린다.
PUSH_HINT='USB로 파일을 보내다 멈췄습니다. 케이블·포트를 바꾸고 Factory Test부터 다시 한 뒤 실행하세요.'
for apk in $APKS; do
  p="$ROOT/$apk"; [ "$(head -c 2 "$p" 2>/dev/null)" = 'PK' ] || p="$LOGDIR/$apk"; name="$(name_of $apk)"; remote="/data/local/tmp/$apk"
  # 내비는 설치한 APK를 바이트 그대로 보관한다: 받아 와서 같으면 같은 버전이니 건너뛴다.
  have="$LOGDIR/installed_$apk"; rm -f "$have"
  pp="$(adb_ -s "$SERIAL" shell pm path "$(pkg_of $apk)" | tr -d '\r\n ')"; pp="${pp#package:}"
  if [ "${pp%.apk}" != "$pp" ] && adb_wait 60 "$name 설치된 파일 비교" -s "$SERIAL" pull "$pp" "$have" && cmp -s "$p" "$have"; then
    ok "$name: 이미 같은 버전이 설치돼 있습니다 — 건너뜀"; continue
  fi
  printf '%s (%s KB)\n' "$name" $(( $(wc -c < "$p") / 1024 )); log "$name"
  adb_wait 60 '① 전송 (USB)' -s "$SERIAL" push "$p" "$remote" || stuck "$name ① 전송" "$PUSH_HINT"
  printf '%s' "$OUT" | grep -qiE 'error|failed|No such' && die "$name 전송 실패:
$OUT" 1
  adb_wait 120 '② 설치 (내비)' -s "$SERIAL" shell pm install -r "$remote" || stuck "$name ② 설치" "$PM_HINT"
  # Only a signature clash is fixed by uninstalling; any other failure (storage full, ...) must not remove the working app.
  if printf '%s' "$OUT" | grep -qE 'INCONSISTENT_CERTIFICATES|UPDATE_INCOMPATIBLE'; then
    warn "$name: 기존에 다른 서명으로 설치돼 있습니다."
    if ask '지우고 다시 설치할까요? (앱 설정도 지워집니다)'; then
      adb_wait 60 '기존 앱 삭제' -s "$SERIAL" uninstall "$(pkg_of $apk)" || stuck "$name 기존 앱 삭제" "$PM_HINT"
      printf '%s' "$OUT" | grep -q Success || die "$name 기존 앱 삭제 실패:
$OUT" 1
      adb_wait 120 '② 설치 (내비)' -s "$SERIAL" shell pm install -r "$remote" || stuck "$name ② 설치" "$PM_HINT"
    fi
  fi
  res="$OUT"; adb_wait 15 '정리' -s "$SERIAL" shell rm -f "$remote" >/dev/null
  printf '%s' "$res" | grep -q Success && ok "$name 설치 완료" || die "$name 설치 실패:
$res" 1
done
ok "지금 차에 깔린 버전: $(versions)"

# --- '내 앱' 실행 -----------------------------------------------------------
head_ "5) '내 앱' 실행"
adb_ -s "$SERIAL" shell am start -n com.veloster.carplayhub/.HubActivity >/dev/null
echo
ok "완료! 차 화면 '내 앱'에서  바로가기 버튼 → 버튼 + 알림 → 저장  을 고르면"
ok "노트북 없이 가장자리 '계기판' 버튼으로 대시보드가 열립니다."
usb_off
finish 0 "차 화면에 '내 앱'이 열립니다."
