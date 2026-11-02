#!/usr/bin/env bash
# hscandump 빌드 — android.jar(API17)와 d8만 있으면 됨.
# ANDROID_JAR: platforms/android-17/android.jar 경로
# D8:          build-tools/*/d8(또는 d8.bat) 경로
set -euo pipefail
# 환경변수 없으면 흔한 SDK 위치에서 자동 탐지 (ANDROID_HOME → ANDROID_SDK_ROOT → 기본 경로들)
_find_sdk() {
  local d
  for d in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}"            "${LOCALAPPDATA:-}/Android/Sdk" "/d/Android/sdk" "/c/Android/sdk"            "$HOME/android-sdk" "$HOME/Android/Sdk" "$HOME/Library/Android/sdk"; do
    [ -n "$d" ] && [ -d "$d" ] && { echo "$d"; return 0; }
  done
  return 1
}
SDK="$(_find_sdk || true)"
ANDROID_JAR="${ANDROID_JAR:-${SDK:+$SDK/platforms/android-17/android.jar}}"
if [ -z "${D8:-}" ] && [ -n "$SDK" ]; then
  D8="$(ls -d "$SDK"/build-tools/*/d8.bat "$SDK"/build-tools/*/d8 2>/dev/null | sort -V | tail -1 || true)"
fi
D8="${D8:-d8}"
if [ -z "$ANDROID_JAR" ] || [ ! -f "$ANDROID_JAR" ]; then
  echo "android.jar(API17) 없음: '${ANDROID_JAR:-<미지정>}'" >&2
  echo "  → sdkmanager \"platforms;android-17\" 설치 후 ANDROID_JAR=<경로> 지정 (SETUP_laptop.md 3절)" >&2
  exit 1
fi
echo "ANDROID_JAR=$ANDROID_JAR"
echo "D8=$D8"
cd "$(dirname "$0")"
rm -rf out && mkdir -p out
javac -encoding UTF-8 -source 7 -target 7 -bootclasspath "$ANDROID_JAR" \
  -d out src/com/i30/diag/HscanDump.java
"$D8" --min-api 17 --output hscandump.jar out/com/i30/diag/HscanDump.class
echo "빌드 완료: $(pwd)/hscandump.jar"
echo "실행:"
echo "  adb push hscandump.jar /data/local/tmp/"
echo "  adb shell CLASSPATH=/data/local/tmp/hscandump.jar app_process /data/local/tmp com.i30.diag.HscanDump 30"
