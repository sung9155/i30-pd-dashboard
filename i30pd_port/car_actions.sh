#!/usr/bin/env bash
# 동작 태깅 raw 캡처 — 각 동작을 수행하는 동안 짧은 hscan raw 덤프를 떠서
# 슬롯(function)을 "변화한 신호"로 확정한다. 수신 전용(ECU에 아무것도 안 보냄).
#
# 사용:  bash i30pd_port/car_actions.sh            # 기본 세트, 각 구간 8초
#        bash i30pd_port/car_actions.sh 10         # 각 구간 10초
#        bash i30pd_port/car_actions.sh one <라벨> [초]   # 단일 캡처(비대화형) — 채팅/원격으로 한 동작씩 뜰 때
#        ADB_SERIAL=<일련번호> bash i30pd_port/car_actions.sh
#
# 각 구간마다 "준비되면 Enter"를 띄운다. Enter 치는 순간부터 N초간 캡처하므로
# **Enter 직후에 동작을 실행**한다(예: 핸들을 좌 끝까지 돌린 채 유지).
# 결과: logs/act_<순번>_<라벨>_<ts>.log  (각 파일 첫 줄에 라벨·PC 시각 기록)
#
# 안전: 정차·주차 브레이크 상태에서. 주행 캡처(마지막)는 안전한 장소에서 저속으로.
set -uo pipefail
export MSYS_NO_PATHCONV=1 MSYS2_ARG_CONV_EXCL='*'
cd "$(dirname "$0")"
MODE=""
PRESET="drive"
if [ "${1:-}" = "one" ]; then
  MODE=one; ONE_LABEL="${2:?라벨 필요: car_actions.sh one <label> [secs]}"; SECS="${3:-8}"
elif [ "${1:-}" = "body" ]; then
  PRESET=body; SECS="${2:-6}"
else
  SECS="${1:-8}"
fi
mkdir -p logs

# 동작 세트: "라벨|안내문". 라벨은 파일명에 쓰이므로 ASCII.
ACTIONS=(
  "idle|정차·발 떼고 공회전(기준선)"
  "steer_left|핸들 왼쪽 끝까지 돌려 유지"
  "steer_right|핸들 오른쪽 끝까지 돌려 유지"
  "steer_center|핸들 중앙 정렬 유지"
  "brake|브레이크 꾹 밟아 유지"
  "brake_release|브레이크 뗀 상태 유지"
  "accel_blip|악셀로 2000rpm 부근 유지(정차·N)"
  "turn_left|좌측 방향지시등 ON"
  "turn_right|우측 방향지시등 ON"
  "hazard|비상등 ON"
  "headlight|전조등 ON"
  "gear_R|기어 R (브레이크 밟은 채)"
  "gear_N|기어 N"
  "gear_D|기어 D (브레이크 밟은 채)"
  "gear_P|기어 P"
  "drive_slow|안전한 곳에서 저속 주행(차속·바퀴속)"
)

# 바디/모드 태깅 세트 — 미확정 신호(도어·벨트·주차브레이크·드라이브모드·와이퍼·오토홀드) 확정용.
# 정차·기어 P·시동 ON 상태에서. 각 단계 Enter 직후 그 상태로 N초 유지.
#   bash i30pd_port/car_actions.sh body        # 각 6초
BODY_ACTIONS=(
  "body_base|정차·P·문 닫힘·벨트 맨 기준선"
  "mode_normal|드라이브 모드 Normal"
  "mode_eco|드라이브 모드 Eco"
  "mode_sport|드라이브 모드 Sport"
  "autohold_on|오토홀드 ON (정차·브레이크 밟았다 뗌)"
  "autohold_off|오토홀드 OFF"
  "wiper_off|와이퍼 OFF"
  "wiper_int|와이퍼 간헐(INT)"
  "wiper_low|와이퍼 LOW"
  "wiper_high|와이퍼 HIGH"
  "door_driver|운전석 문 열어 유지"
  "door_close|모든 문 닫음"
  "belt_off|운전석 안전벨트 풂"
  "belt_on|운전석 안전벨트 맴"
  "parkbrake|전자 주차브레이크 체결(EPB)"
  "parkbrake_off|주차브레이크 해제"
  "hood|보닛 열어 유지(선택)"
)
[ "$PRESET" = "body" ] && ACTIONS=("${BODY_ACTIONS[@]}")

say(){ printf '%s\n' "$*"; }
hr(){ say "------------------------------------------------------------"; }

# adb/serial 선택 (car_session.sh 와 동일 규칙)
command -v adb >/dev/null || { say "adb 없음 — PATH에 platform-tools 추가"; exit 2; }
adb start-server >/dev/null 2>&1
SERIAL="${ADB_SERIAL:-}"
if [ -z "$SERIAL" ]; then
  LINES="$(adb devices | awk 'NR>1 && NF>=2 {print $1}')"
  [ -z "$LINES" ] && { say "장치 없음 — car_session.sh 로 먼저 연결 확인(Factory Test/케이블)."; exit 1; }
  CNT="$(printf '%s\n' "$LINES" | wc -l)"
  [ "$CNT" -gt 1 ] && { say "장치 ${CNT}개 — ADB_SERIAL=<일련번호> 로 지정:"; echo "$LINES"; exit 1; }
  SERIAL="$LINES"
fi
[ "$(adb -s "$SERIAL" get-state 2>/dev/null)" = "device" ] || { say "상태 이상 — car_session.sh 로 점검."; exit 1; }
[ -f hscandump.jar ] || { say "hscandump.jar 없음 — bash build_dump.sh 먼저."; exit 2; }

capture_one() {  # $1=label $2=hint $3=out
  local label="$1" hint="$2" out="$3"
  {
    echo "# ACTION=$label  HINT=$hint"
    echo "# PC_START=$(date '+%Y-%m-%d %H:%M:%S.%3N')  SECS=$SECS  SERIAL=$SERIAL"
  } > "$out"
  adb -s "$SERIAL" shell "mkdir -p /data/local/tmp/dalvik-cache; cd /data/local/tmp; ANDROID_DATA=/data/local/tmp CLASSPATH=/data/local/tmp/hscandump.jar app_process /data/local/tmp com.i30.diag.HscanDump $SECS raw" \
    | tr -d '\r' >> "$out"
  echo "# PC_END=$(date '+%Y-%m-%d %H:%M:%S.%3N')" >> "$out"
}

say "hscandump.jar 푸시..."; adb -s "$SERIAL" push hscandump.jar /data/local/tmp/ | tr -d '\r'

if [ "$MODE" = "one" ]; then
  ts="$(date +%Y%m%d_%H%M%S)"
  out="logs/act_one_${ONE_LABEL}_${ts}.log"
  say "[one] ${ONE_LABEL}  ${SECS}s 캡처 시작 → $out"
  capture_one "$ONE_LABEL" "$ONE_LABEL" "$out"
  say "저장: $out  (RAW $(grep -c '^RAW ' "$out")프레임)"
  exit 0
fi

say "[actions] 장치=$SERIAL  구간=${SECS}s  동작 ${#ACTIONS[@]}개"
say "시동 ON 확인. 각 구간은 Enter 직후 ${SECS}초 캡처 — Enter 치고 바로 동작."
say "건너뛰려면 's' 입력 후 Enter. 중단은 Ctrl-C."

i=0
for a in "${ACTIONS[@]}"; do
  i=$((i+1))
  label="${a%%|*}"; hint="${a#*|}"
  hr
  printf '[%02d/%02d] %s\n' "$i" "${#ACTIONS[@]}" "$hint"
  printf '      준비되면 Enter(건너뛰기 s): '
  read ans
  [ "$ans" = "s" ] && { say "      skip"; continue; }
  ts="$(date +%Y%m%d_%H%M%S)"
  out="logs/act_$(printf '%02d' "$i")_${label}_${ts}.log"
  capture_one "$label" "$hint" "$out"
  frames="$(grep -c '^RAW ' "$out")"
  say "      저장: $out  (RAW ${frames}프레임)"
done

hr
say "완료. 분석:  for f in logs/act_*_*.log; do echo \"== \$f\"; python -X utf8 tools/hscan_stats.py \"\$f\"; done"
[ "$PRESET" = "body" ] && say "바디 비교:  python -X utf8 tools/diff_captures.py logs/act_*_body_base_*.log logs/act_*_door_driver_*.log  (각 동작 로그를 기준선과 대조)"
say "동작별로 어떤 슬롯 바이트가 변했는지 비교 → HSCAN_DECODE.md 표 채우기."
