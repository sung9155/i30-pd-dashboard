#!/bin/bash
# i30 PD 대시보드 창 설치기 — macOS. Finder에서 더블클릭한다(터미널이 잠깐 열린다).
# 옆의 gui.js(JXA, 원작 installer/mac/gui.js를 옮겨 고친 것)를 맥 기본 osacompile로 이 폴더의 "i30 PD 설치.app"으로 만들고 연다.
# 설치기 ZIP은 Windows에서 만들기 때문에 .app을 미리 만들어 넣을 수 없다(osacompile은 맥에만 있다). 이 맥에서 만든 앱은
# 인터넷에서 받은 표시(격리)가 없어 Gatekeeper가 앱을 다시 묻지 않는다. 매번 새로 만든다(1초 안팎): 새 설치기로 바뀐 gui.js도 바로 쓰인다.
# 앱은 옆의 설치.command를 --gui로 돌린다. 앱을 이 폴더 밖으로 옮기면 설치.command를 찾지 못한다.
DIR="$(cd "$(dirname "$0")" && pwd)"; APP="$DIR/i30 PD 설치.app"
[ "$(uname -s)" = Darwin ] || { echo '맥 전용입니다. 리눅스는 터미널에서  bash install.sh'; exit 2; }
if [ ! -f "$DIR/gui.js" ] || [ ! -f "$DIR/설치.command" ]; then
  echo 'gui.js · 설치.command가 이 폴더에 없습니다. 설치기 ZIP을 다시 받아 압축을 푸세요.'; read -r -p '창을 닫으려면 Enter ' _; exit 2
fi
rm -rf "$APP"
if ! osacompile -l JavaScript -s -o "$APP" "$DIR/gui.js"; then
  echo '창 설치기를 만들지 못했습니다. 대신 같은 폴더의 설치.command를 더블클릭하세요(번호로 고르는 설치기).'
  read -r -p '창을 닫으려면 Enter ' _; exit 1
fi
xattr -cr "$APP" 2>/dev/null
open "$APP"
echo '창 설치기를 열었습니다. 이 터미널 창은 닫아도 됩니다.'
echo '다음부터는 이 폴더의 "i30 PD 설치.app"을 바로 열어도 됩니다.'
