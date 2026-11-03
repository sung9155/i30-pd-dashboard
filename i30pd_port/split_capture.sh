#!/usr/bin/env bash
# 순정 분할 화면(내비 반 + 음악 반) 조사 — Git Bash에서 실행. 자세한 배경과 읽는 법은 SPLIT_SCREEN.md.
#
# 사용:  bash i30pd_port/split_capture.sh [이름=split]
#        ADB_SERIAL=<일련번호> bash i30pd_port/split_capture.sh on    # 장치가 여러 개일 때
#   1) 차에서 순정 분할 화면(내비 + 음악)을 띄운 채 실행:   bash i30pd_port/split_capture.sh on
#   2) 분할을 끄고 내비만 전체 화면으로 둔 채 한 번 더:    bash i30pd_port/split_capture.sh off
#   3) (선택) 음악 칸을 띄운 앱의 APK와 framework 파일 받기: bash i30pd_port/split_capture.sh pull <패키지명>
#
# 읽기 전용. 설정·설치·창을 바꾸지 않는다(dumpsys·getprop·pm path·pull만).
set -uo pipefail
export MSYS_NO_PATHCONV=1 MSYS2_ARG_CONV_EXCL='*'   # /system/... 기기 경로를 Git Bash가 바꾸지 않게
cd "$(dirname "$0")"
NAME="${1:-split}"
TS="$(date +%Y%m%d_%H%M%S)"
mkdir -p logs/split
A=(adb); [ -n "${ADB_SERIAL:-}" ] && A=(adb -s "$ADB_SERIAL")
sh() { "${A[@]}" shell "$@" | tr -d '\r'; }

state="$("${A[@]}" get-state 2>/dev/null || true)"
[ "$state" = "device" ] || { echo "adb 장치 없음(상태: ${state:-없음}) — car_session.sh / SETUP_laptop.md 참고"; exit 1; }

if [ "$NAME" = "pull" ]; then
  PKG="${2:-}"; OUT="logs/split/pull_${TS}"; mkdir -p "$OUT"
  for f in framework.odex framework.jar android.policy.odex android.policy.jar automotive.odex automotive-service.odex; do
    "${A[@]}" pull "/system/framework/$f" "$OUT/$f" >/dev/null 2>&1 && echo "받음: $OUT/$f"
  done
  if [ -n "$PKG" ]; then
    for p in $(sh pm path "$PKG" | sed 's/^package://'); do
      "${A[@]}" pull "$p" "$OUT/" >/dev/null 2>&1 && echo "받음: $OUT/$(basename "$p")"
      o="${p%.apk}.odex"; "${A[@]}" pull "$o" "$OUT/" >/dev/null 2>&1 && echo "받음: $OUT/$(basename "$o")"
    done
  fi
  exit 0
fi

LOG="logs/split/${NAME}_${TS}.txt"
{
  echo "# split_capture $NAME $TS"
  echo "## 기기";  for p in ro.product.name ro.build.version.incremental; do echo "$p=$(sh getprop "$p")"; done
  echo "## 분할 관련 시스템 값 (ro.splitscreen.enable 이 true 여야 창 종류 2039/2040 배치가 켜짐)"
  sh getprop | grep -i split
  echo "## 화면 크기"; sh wm size 2>/dev/null; sh dumpsys window displays | grep -iE "init=|cur=|app=" | head -5
  echo "## 창 목록 (type=2040 오른쪽 칸 / 2039 왼쪽 칸, mOwnerUid·package·mFrame 확인)"
  sh dumpsys window windows | grep -iE "Window #|mOwnerUid|package=|mAttrs|ty=|type=|mFrame=|mShownFrame|mHasSurface|isVisible|SplitScreen"
  echo "## 창 배치 경계 (분할이 켜지면 mDock/mContent 오른쪽 끝이 320 근처로 줄어드는지)"
  sh dumpsys window policy | grep -iE "split|mDock|mContent|mRestricted|mStable|mSystem|mTopIsFullscreen|mFocusedWindow"
  echo "## 맨 위 액티비티"
  sh dumpsys activity activities | grep -iE "mFocusedActivity|mResumedActivity|Run #" | head -12
  echo "## 분할 서비스 (SplitScreenService 를 상속한 앱 서비스가 떠 있는지)"
  sh dumpsys activity services | grep -iE "ServiceRecord|intent=|split" | grep -iE -B1 -A1 "split" | head -40
  echo "## 분할 관련 패키지"
  sh pm list packages | grep -iE "split|media|music|navi|launcher|home"
} > "$LOG" 2>&1
echo "저장: i30pd_port/$LOG"
grep -nE "type=204[01]|ty=204[01]|SplitScreen|splitscreen" "$LOG" | head -20 || true
echo "위에 type=2040 또는 SplitScreen 줄이 있으면 순정 분할 창 구조를 쓰는 것. SPLIT_SCREEN.md '결과 읽는 법' 참고."
