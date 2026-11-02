#!/bin/bash
# i30 PD 대시보드 설치 — macOS. Finder에서 더블클릭하거나, 터미널에서  bash 설치.command
# install.ps1(Windows)과 같은 일을 한다: adb 준비 → APK 준비 → 차 화면 안내 → 차(product:pd_kr) 연결 대기 →
# 빌드 확인 → 두 앱 설치(서명 충돌 시 지우고 재설치) → '내 앱' 실행. 차가 아닌 기기에는 아무것도 하지 않는다.
# 기본 macOS bash 3.2 에서 돌아가야 하므로 bash 4 기능(연관 배열 등)은 쓰지 않는다.
#
# 버전: 시작할 때 안정화(최신 정식 릴리스, 실차 확인) / 테스트('test' 사전 배포, 실차 검증 전) 중 고른다.
# 테스트 인자: --adb <adb 경로>  --apk-base <URL>  --channel stable|test  --yes(확인 모두 예, 대기/프롬프트 생략, 안정화)  --uninstall(두 앱 삭제)

ROOT="$(cd "$(dirname "$0")" && pwd)"
INSTALLER_VERSION='2026-11-01b'   # 화면 맨 위에 찍혀, 스크린샷만으로 옛 설치기인지 알 수 있다. 고칠 때마다 올린다.
VERIFIED_BUILD='eng.vmadmin.20260114.084130'   # 블루링크 D-Audio Plus(HYUNDAIA) — 실차 설치 확인
LOW_BUILD='eng.vmadmin.20260114.084725'        # 블루링크 없는 D-Audio Plus LOW — 2026-10-31 실차 설치·동작 확인
PT_URL='https://dl.google.com/android/repository/platform-tools-latest-darwin.zip'
# 안정화 = 최신 정식 릴리스, 테스트 = 'test' 사전 배포(덮어쓰며 갱신). --apk-base 를 주면 그 주소를 그대로 쓴다.
RELEASES='https://github.com/sung9155/i30-pd-dashboard/releases'
APK_BASE=''
APKS='dashboard-i30pd-platform.apk myapps-i30pd-platform.apk'
ADB=''; YES=''; UNINSTALL=''; CHANNEL=''
while [ $# -gt 0 ]; do
  case "$1" in
    --adb) ADB="$2"; shift ;;
    --apk-base) APK_BASE="$2"; shift ;;
    --channel) CHANNEL="$2"; shift ;;
    --yes) YES=1 ;;
    --uninstall) UNINSTALL=1 ;;
  esac
  shift
done

pkg_of()  { case "$1" in dashboard-*) echo com.veloster.dashboard ;; *) echo com.veloster.carplayhub ;; esac; }
name_of() { case "$1" in dashboard-*) echo '대시보드' ;; *) echo "'내 앱'" ;; esac; }

LOGDIR="$HOME/Library/Logs/i30pd-installer"
mkdir -p "$LOGDIR"
LOG="$LOGDIR/install_$(date +%Y%m%d_%H%M%S).log"

log()  { printf '%s\n' "$1" >> "$LOG"; }   # 기록 파일에만 (문제 제보용)
note() { printf '%s\n' "$1"; log "$1"; }
head_() { local t; t="$(date +%H:%M:%S)"; printf '\n\033[36m%s   [%s]\033[0m\n' "$1" "$t"; log "== $1 == $t"; }
ok()   { printf '\033[32m%s\033[0m\n' "$1"; printf '%s\n' "$1" >> "$LOG"; }
warn() { printf '\033[33m%s\033[0m\n' "$1"; printf '%s\n' "$1" >> "$LOG"; }
finish() { [ -z "$YES" ] && { echo; read -r -p '창을 닫으려면 Enter' _; }; exit "$1"; }
die()  { printf '\n\033[31m%s\033[0m\n' "$1"; log "ERROR: $1"
         warn "기록 파일: $LOG"
         warn '문제를 알릴 때 이 파일을 Issues(github.com/sung9155/i30-pd-dashboard/issues)에 올려 주세요.'
         [ -z "$YES" ] && open -R "$LOG" 2>/dev/null
         finish "$2"; }
ask()  { [ -n "$YES" ] && return 0; read -r -p "$1 [Y/n] " r; case "$r" in ''|[Yy]*) return 0 ;; *) return 1 ;; esac; }
usb_off() { note 'USB 디버깅 끄기: 시동을 껐다 켜면 저절로 꺼집니다. 바로 끄려면 Factory Test에서  0A 01 → 65 00 → 0A 02'
            note "  (또는 '내 앱' → USB 디버깅 → OFF). 끄면 노트북 연결이 끊깁니다."; }
adb_() { local o; o="$("$ADB" "$@" 2>&1)"; printf '> adb %s\n%s\n' "$*" "$o" >> "$LOG"; printf '%s' "$o"; }
# 오래 걸릴 수 있는 adb(전송·설치)는 제한 시간 안에서 돌리고 기다리는 동안 경과 초를 같은 줄에 찍는다:
# 멈춘 화면의 스크린샷만 봐도 어느 단계에서 몇 초째 멈췄는지 보이게. 결과는 $OUT, 시간 초과면 return 1.
adb_wait() {
  local sec="$1" what="$2" f="$LOGDIR/adb_out.txt" t=0 pid; shift 2
  log "> adb $*  (최대 ${sec}초)"
  "$ADB" "$@" > "$f" 2>&1 & pid=$!
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
[ -n "$UNINSTALL" ] && echo "  i30 PD 대시보드 삭제   (설치기 $INSTALLER_VERSION)" || echo "  i30 PD 대시보드 설치   (설치기 $INSTALLER_VERSION)"
echo '  ----------------------'
warn '현대자동차 공식 앱이 아닙니다. 순정 시스템에 앱을 설치하는 작업이라 보증/AS 불이익이 있을 수 있고,'
warn '책임은 본인에게 있습니다. 모든 작업은 정차 · 시동 켠 상태에서 하세요.'
note "기록: $LOG"

# --- adb 찾기/받기 ------------------------------------------------------------
head_ '1) adb 준비'
if [ -z "$ADB" ]; then
  for c in "$ROOT/platform-tools/adb" "$ROOT/adb"; do [ -x "$c" ] && { ADB="$c"; break; }; done
fi
[ -z "$ADB" ] && ADB="$(command -v adb)"
if [ -z "$ADB" ]; then
  note 'adb(platform-tools)가 없습니다.'
  ask 'Google 공식 주소에서 받을까요?' || die 'adb가 없어 중단합니다.' 2
  note '내려받는 중...'
  curl -fsSL -o "$LOGDIR/platform-tools.zip" "$PT_URL" || die "다운로드 실패: $PT_URL" 2
  unzip -qo "$LOGDIR/platform-tools.zip" -d "$ROOT" || die '압축 해제 실패' 2
  ADB="$ROOT/platform-tools/adb"
  [ -x "$ADB" ] || die 'adb를 준비하지 못했습니다.' 2
fi
note "adb: $ADB"
log "환경: macOS $(sw_vers -productVersion 2>/dev/null) / $(uname -m) / $("$ADB" version 2>&1 | head -1)"

# --- 버전 고르기 --------------------------------------------------------------
if [ -z "$UNINSTALL" ] && [ -z "$APK_BASE" ]; then
  if [ "$CHANNEL" != stable ] && [ "$CHANNEL" != test ]; then
    if [ -n "$YES" ]; then CHANNEL=stable; else
      head_ '받을 버전'
      note '  1) 안정화 (권장) — 실차에서 확인된 버전'
      note '  2) 테스트 — 새 기능, 아직 실차 검증 전. 문제가 있으면 안정화로 다시 설치하면 됩니다'
      read -r -p '번호를 입력하고 Enter [1] ' r
      if [ "$(printf '%s' "$r" | tr -d ' ')" = 2 ]; then CHANNEL=test; else CHANNEL=stable; fi
    fi
  fi
  if [ "$CHANNEL" = test ]; then APK_BASE="$RELEASES/download/test"; note '버전: 테스트 (실차 검증 전)'
  else APK_BASE="$RELEASES/latest/download"; note '버전: 안정화'; fi
fi

# --- APK 준비 (폴더에 있으면 쓰고, 없으면 공개 레포에서 받기) -----------------
[ -z "$UNINSTALL" ] && head_ '0) 앱 파일 준비'
for apk in $([ -z "$UNINSTALL" ] && echo $APKS); do
  # 브라우저로 받은 GitHub 페이지(HTML)가 .apk 이름으로 저장된 경우가 있어, 'PK'로 시작할 때만 폴더 것을 쓴다.
  if [ -f "$ROOT/$apk" ] && [ "$(head -c 2 "$ROOT/$apk")" = 'PK' ]; then warn "$apk: 설치기 폴더에 있는 파일을 씁니다 (위에서 고른 버전과 상관없이 이 파일이 설치됩니다)"; continue; fi
  [ -f "$ROOT/$apk" ] && warn "$apk: 폴더 파일이 APK가 아니라 무시하고 내려받습니다."
  note "$apk: 폴더에 없어 내려받습니다..."
  curl -fsSL -o "$LOGDIR/$apk" "$APK_BASE/$apk" || die "APK 다운로드 실패: $APK_BASE/$apk
APK를 설치.command 옆에 두거나 --apk-base <공개 URL> 로 지정하세요." 2
  # APK(=ZIP)는 'PK'로 시작. 404 안내문 등이 저장된 경우를 거른다.
  [ "$(head -c 2 "$LOGDIR/$apk")" = 'PK' ] || die "받은 파일이 APK가 아닙니다($apk): $APK_BASE/$apk" 2
  note "$apk: 받음 ($(( $(wc -c < "$LOGDIR/$apk") / 1024 )) KB)"
done

# --- 차 화면 안내 + 연결 대기 -------------------------------------------------
head_ '2) 차 화면에서 먼저 (사람이 직접)'
note '  (1) 설정 → 시스템 정보 → 저장 공간 화면'
note '  (2) 내용 영역 좌하단 모서리 5회 탭 → 우하단 모서리 1회 탭 (엔지니어 모드)'
note '  (3) 암호 입력 → Diagnosis → Factory Test 에서  0A 01 → 65 01 → 0A 02  전송'
note '  (4) USB 케이블 연결 (USB-C 맥이면 USB-A 어댑터 필요)'
[ -z "$YES" ] && read -r -p '위 단계가 끝나고 USB를 연결했으면 Enter ' _

head_ '3) 차 연결 확인'
"$ADB" start-server >/dev/null 2>&1
SERIAL=''; tries=0
while [ -z "$SERIAL" ]; do
  dl="$("$ADB" devices -l 2>&1)"; printf '%s\n' "$dl" >> "$LOG"
  SERIAL="$(printf '%s\n' "$dl" | awk '$2=="device" && /product:pd_kr( |$)/ {print $1; exit}')"   # exactly pd_kr, not pd_kr2
  [ -n "$SERIAL" ] && break
  [ -n "$YES" ] && die '차(pd_kr)를 찾지 못했습니다.' 1
  if printf '%s' "$dl" | grep -q unauthorized; then warn '→ 차 화면에 "USB 디버깅 허용" 창이 뜨면 [항상 허용] 하세요.'
  elif printf '%s\n' "$dl" | awk '$2=="device"' | grep -q .; then warn '→ 차가 아닌 기기만 보입니다(휴대폰 등). 그 기기는 건드리지 않습니다.'
  else note '→ 차를 기다리는 중... USB 디버깅/Factory Test(0A 01/65 01/0A 02) 다시 확인.'; fi
  tries=$((tries + 1))
  # 5초 간격으로 1분. 차 화면에서 허용을 눌러야 하는 상태면 3분까지 기다린다.
  limit=12; printf '%s' "$dl" | grep -q unauthorized && limit=36
  if [ $tries -ge $limit ]; then
    # 못 찾음: 맥이 본 USB 장치 목록을 기록에 남긴다(차가 USB에 아예 안 나타났는지 가르는 근거).
    log "== USB 장치 목록 (system_profiler) =="; system_profiler SPUSBDataType 2>/dev/null >> "$LOG"
    if printf '%s' "$dl" | grep -q unauthorized; then warn '마지막 상태: 차 화면에서 USB 디버깅 허용을 누르지 않음'
    elif printf '%s\n' "$dl" | awk '$2=="device"' | grep -q .; then warn '마지막 상태: pd_kr이 아닌 기기만 보임'
    else warn '마지막 상태: 맥에 차가 adb 기기로 나타나지 않음 — Factory Test(0A 01/65 01/0A 02)·케이블·데이터 포트 확인'
         warn '가장 흔한 원인은 Factory Test 입력 실수입니다: 케이블을 빼고 0A 01 / 65 01 / 0A 02 를 띄어쓰기까지 정확히, 한 줄씩 SEND 한 뒤 다시 꽂으세요.'
         warn '같은 케이블로 다른 차는 된다면 이 차의 USB 포트·배선 쪽입니다(블루링크 없는 내비도 설치된 사례가 있습니다).'; fi
    die "시간 초과($((limit * 5))초). 차를 찾지 못했습니다. 위 상태를 고친 뒤 설치.command를 다시 실행하세요." 1
  fi
  sleep 5
done
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
if [ "$build" = "$LOW_BUILD" ]; then
  note '블루링크 없는 내비(LOW 보드) — 설치가 확인된 빌드입니다.'
elif [ -n "$build" ] && [ "$build" != "$VERIFIED_BUILD" ]; then
  warn "주의: 검증된 빌드($VERIFIED_BUILD)와 다릅니다. 값이 틀릴 수 있습니다."
  ask '그래도 계속할까요?' || { warn '사용자 취소.'; finish 1; }
fi

# --- 삭제 모드 --------------------------------------------------------------
if [ -n "$UNINSTALL" ]; then
  head_ '앱 삭제'
  for apk in $APKS; do
    adb_wait 60 "$(name_of $apk) 삭제" -s "$SERIAL" uninstall "$(pkg_of $apk)" || stuck "$(name_of $apk) 삭제(내비)" "$PM_HINT"
    if printf '%s' "$OUT" | grep -q Success; then ok "$(name_of $apk): 삭제됨"; continue; fi
    # Not Success: never installed, or the uninstall failed. pm path tells the two apart.
    u="$OUT"
    if adb_wait 30 "$(name_of $apk) 확인" -s "$SERIAL" shell pm path "$(pkg_of $apk)" && printf '%s' "$OUT" | grep -q 'package:'; then
      die "$(name_of $apk) 삭제 실패 (앱이 아직 차에 있습니다):
$u" 1
    fi
    note "$(name_of $apk): 설치돼 있지 않음"
  done
  ok '삭제 완료. 순정 상태로 돌아갔습니다(순정 시스템은 원래 건드리지 않습니다).'; usb_off; finish 0
fi

# --- 설치 -------------------------------------------------------------------
head_ '4) 앱 설치'
# adb install 대신 ① 전송(push) ② 설치(pm install) 두 단계로 나눠, 멈추면 USB 쪽인지 내비 쪽인지 바로 갈린다.
PUSH_HINT='USB로 파일을 보내다 멈췄습니다. 케이블·포트를 바꾸고 Factory Test부터 다시 한 뒤 실행하세요.'
for apk in $APKS; do
  p="$ROOT/$apk"; [ "$(head -c 2 "$p" 2>/dev/null)" = 'PK' ] || p="$LOGDIR/$apk"; name="$(name_of $apk)"; remote="/data/local/tmp/$apk"
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

# --- '내 앱' 실행 -----------------------------------------------------------
head_ "5) '내 앱' 실행"
adb_ -s "$SERIAL" shell am start -n com.veloster.carplayhub/.HubActivity >/dev/null
echo
ok "완료! 차 화면 '내 앱'에서  실행 바로가기 → 가장자리 버튼 + 알림 → 저장  을 고르면"
ok "노트북 없이 가장자리 '계기판' 버튼으로 대시보드가 열립니다."
usb_off
finish 0
