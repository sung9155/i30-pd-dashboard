#!/usr/bin/env bash
# 실차 세션 한 방 스크립트 — Git Bash에서 실행.
#   1) adb 장치 상태 확인(없음/offline/unauthorized 분기 안내)
#   2) 기기 식별 getprop (pd_kr / API17 / adb 보안 속성)
#   3) hscandump.jar 푸시 → 수신 전용 덤프 실행
#   4) 전부 logs/ 에 타임스탬프 로그로 저장
#
# 사용:  bash i30pd_port/car_session.sh [관찰초=30] [raw]
#        ADB_SERIAL=<일련번호> bash i30pd_port/car_session.sh 30   # 장치 여러 개일 때
#
# 수신 전용. ECU에 아무것도 보내지 않고 설치/설정 변경도 하지 않는다.
set -uo pipefail
# Git Bash(MSYS)가 /data/local/tmp 같은 기기 경로를 C:/Program Files/Git/... 로 바꿔 버리는 것 방지
export MSYS_NO_PATHCONV=1 MSYS2_ARG_CONV_EXCL='*'
cd "$(dirname "$0")"
SECS="${1:-30}"
RAW="${2:-}"
mkdir -p logs
TS="$(date +%Y%m%d_%H%M%S)"
LOG="logs/session_${TS}.log"
DUMP="logs/hscandump_${TS}.log"

say() { printf '%s\n' "$*"; }
hr()  { say "------------------------------------------------------------"; }

main() {
  say "[session] $TS   log=$LOG"
  command -v adb >/dev/null || { say "adb 없음 — PATH에 platform-tools 추가"; return 2; }
  adb start-server >/dev/null 2>&1

  hr; say "== adb devices -l"
  adb devices -l
  # 상태 집계
  local serial="${ADB_SERIAL:-}"
  local lines; lines="$(adb devices | awk 'NR>1 && NF>=2 {print $1" "$2}')"
  if [ -z "$lines" ]; then
    hr
    say "장치 없음. 순서대로 확인:"
    say "  1. 인포 쪽: Factory Test 0A 01 → 65 01 → 0A 02 보냈는지 (INSTALL.md 2단계)"
    say "  2. 케이블/포트: 성공 사례는 A-A 케이블. 충전만 되는 건 증거 아님"
    say "  3. Windows 장치 관리자: 'Telechips'/'DiPO'/'알 수 없는 장치'(VID_18D1&PID_D00D)가 보이면"
    say "     드라이버 업데이트 → 컴퓨터에서 찾기 → 목록에서 직접 선택 → Android Device → Android ADB Interface"
    say "     (SETUP_laptop.md 'Windows USB 드라이버' 절)"
    say "  4. adb kill-server && adb start-server 후 재조회"
    return 1
  fi
  if [ -z "$serial" ]; then
    local n; n="$(printf '%s\n' "$lines" | wc -l)"
    if [ "$n" -gt 1 ]; then
      say "장치가 ${n}개. ADB_SERIAL=<일련번호> 로 지정해 다시 실행:"; printf '%s\n' "$lines"; return 1
    fi
    serial="${lines%% *}"
  fi
  local state; state="$(adb -s "$serial" get-state 2>/dev/null || true)"
  say "선택 장치: $serial  상태: ${state:-?}"
  case "$state" in
    device) ;;
    unauthorized)
      hr; say "unauthorized — 인포 화면에 USB 디버깅 허용 창이 떴으면 허용(항상 허용 체크)."
      say "창이 안 뜨는 OEM 빌드면 ro.adb.secure 정책 문제. 케이블 재연결 + adb kill-server 후 1회 재시도."; return 1;;
    *)
      hr; say "상태 '$state' — 케이블 재연결, adb kill-server/start-server 후 재시도."; return 1;;
  esac

  hr; say "== 기기 식별 (getprop)"
  local p
  for p in ro.product.name ro.product.device ro.product.model ro.build.version.sdk \
           ro.build.version.incremental ro.build.fingerprint ro.build.type \
           ro.secure ro.adb.secure ro.debuggable persist.sys.usb.config sys.usb.config sys.usb.state; do
    printf '  %-32s %s\n' "$p" "$(adb -s "$serial" shell getprop "$p" | tr -d '\r')"
  done
  say "  id: $(adb -s "$serial" shell id | tr -d '\r')"
  local prod; prod="$(adb -s "$serial" shell getprop ro.product.name | tr -d '\r')"
  local sdk;  sdk="$(adb -s "$serial" shell getprop ro.build.version.sdk | tr -d '\r')"
  # 분석 문서는 pd_kr 를 기대하지만, 추출 펌웨어 build.prop 은 hyundaiA_kr(공용 이미지)였다.
  # 실차 값이 둘 중 무엇이든 기록해 두고, 2단계 차종 게이트(HscanDataClient)는 이 값으로 맞춘다.
  if [ "$sdk" != "17" ]; then
    say "!! API 레벨이 17이 아님(sdk=$sdk) — 대상 기기가 아닐 수 있음. 덤프는 수신 전용이라 계속."
  elif [ "$prod" = "pd_kr" ]; then
    say "OK: product=pd_kr / API17 — 문서 기대값과 일치."
  else
    say "주의: product=$prod (문서 기대값 pd_kr 아님). API17이면 같은 D-Audio Plus 계열일 가능성 큼."
    say "      → 2단계 차종 게이트 문자열을 '$prod' 로 맞출 것. 이 값을 기록해 둔다."
  fi

  hr; say "== hscan 관련 프로세스"
  adb -s "$serial" shell ps 2>/dev/null | tr -d '\r' | grep -i -E 'hscan|PID' | head -5 || true

  hr
  if [ ! -f hscandump.jar ]; then
    say "hscandump.jar 없음 — 먼저: bash i30pd_port/build_dump.sh"; return 2
  fi
  say "== hscandump.jar 푸시"
  adb -s "$serial" push hscandump.jar /data/local/tmp/ | tr -d '\r'
  say "== 덤프 실행 (${SECS}s${RAW:+, $RAW})  → $DUMP"
  say "   시동 ON 상태여야 프레임이 흐른다. 가능하면 공회전/주행."
  # shell(uid 2000)은 /data/dalvik-cache 에 못 쓰므로 ANDROID_DATA 를 /data/local/tmp 로 돌려 그 아래 dalvik-cache 사용.
  # (안 하면 app_process 가 Segmentation fault / dalvikvm 은 ClassNotFoundException)
  # 순수 dalvikvm 은 LocalSocket JNI 가 없어 UnsatisfiedLinkError → 반드시 app_process.
  adb -s "$serial" shell "mkdir -p /data/local/tmp/dalvik-cache; cd /data/local/tmp; ANDROID_DATA=/data/local/tmp CLASSPATH=/data/local/tmp/hscandump.jar app_process /data/local/tmp com.i30.diag.HscanDump $SECS $RAW" \
    | tr -d '\r' | tee "$DUMP"
  hr
  say "덤프 저장: $DUMP"
  say "읽는 법: HSCAN_DECODE.md 슬롯 표와 대조 — i30 PD는 슬롯 0x00~0x22가 보이면 정상."
}

main "$@" 2>&1 | tee "$LOG"
exit "${PIPESTATUS[0]}"
