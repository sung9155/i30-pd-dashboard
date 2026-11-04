#!/usr/bin/env bash
# Phone installer without a phone or a car: Flow + Adb on the desktop JDK against fake_adbd.py.
# Covers new-key prompt, known-key signature, signature clash reinstall, uninstall (☆ put back), a non-pd_kr navi,
# skipping an identical install, the masked diagnostics file, and installing from the kept APKs when the download fails.
set -euo pipefail
cd "$(dirname "$0")"
T=$(mktemp -d); trap 'kill $FAKE 2>/dev/null; rm -rf "$T"' EXIT
mkdir -p "$T/server" "$T/key" "$T/cls"
apks(){ python -X utf8 -c "
import os,sys
for n,k in (('dashboard-i30pd-platform.apk',170000),('myapps-i30pd-platform.apk',9000)):
    open(os.path.join(sys.argv[1],n),'wb').write(b'PK'+os.urandom(k))" "$T/server"; }
apks
javac -encoding UTF-8 -d "$T/cls" ../src/com/i30/installer/Adb.java ../src/com/i30/installer/Flow.java FlowTest.java
java -ea -Dsun.stdout.encoding=UTF-8 -cp "$T/cls" com.i30.installer.FlowTest notes || { echo "FAIL: release notes"; exit 1; }
PORT=$((20000 + RANDOM % 20000))
python -X utf8 fake_adbd.py $PORT "$T/fake.log" "$T/server" & FAKE=$!
for _ in $(seq 50); do grep -q ready "$T/fake.log" 2>/dev/null && break; sleep 0.1; done
BASE="file:///$(cd "$T/server" && pwd -W 2>/dev/null || pwd)"
run(){ java -Dsun.stdout.encoding=UTF-8 -cp "$T/cls" com.i30.installer.FlowTest $PORT "$BASE" "$T/key" "$1" > "$T/out$2.txt"; }
fail(){ echo "FAIL: $1"; echo "--- fake log"; cat "$T/fake.log"; for f in "$T"/out*.txt; do echo "--- $f"; cat "$f"; done; exit 1; }

run install 1 || fail "1 new key install"
apks   # a new release: different bytes, so 2 really installs
run install 2 || fail "2 clash reinstall"
run uninstall 3 || fail "3 uninstall"
set +e; run install 4; rc=$?; set -e
[ $rc -eq 3 ] || fail "4 non-pd_kr should stop (rc=$rc)"
run install 5 || fail "5 install"
run install 6 || fail "6 same install again"
run diag 7 || fail "7 diagnostics"
BASE="file:///nowhere/download" run install 8 || fail "8 kept APKs"
printf 'DEX-JAR' > "$T/key/hscandump.jar"
run record 9 || fail "9-10 drive recording"

L="$T/fake.log"
grep -q 'FAKE ERROR' "$L" && fail "fake adbd rejected something"
c(){ awk -v n="== connection $1" '$0==n{on=1;next} /^== connection/{on=0} on' "$L"; }
c 1 | head -1 | grep -qx 'auth pubkey' || fail "1 should send the public key"
[ "$(c 1 | grep -c '^push ')" = 2 ] && c 1 | grep -q 'push /data/local/tmp/dashboard-i30pd-platform.apk 170002' || fail "1 pushes"
[ "$(c 1 | grep -c 'pm install')" = 2 ] && c 1 | grep -q 'am start' || fail "1 installs + 내 앱"
c 2 | head -1 | grep -qx 'auth signature' || fail "2 should sign with the saved key"
c 2 | grep -q 'pm uninstall com.veloster.dashboard' && [ "$(c 2 | grep -c 'pm install')" = 3 ] || fail "2 clash → uninstall → reinstall"
grep -q '다른 서명' "$T/out2.txt" || fail "2 should ask before uninstalling"
[ "$(c 3 | grep -c 'pm uninstall')" = 2 ] && ! c 3 | grep -q 'push' || fail "3 uninstall only"
c 3 | grep -q 'service call com.hkmc.micom.mode 34 s16 com.mobis.intent.action.CUSTOM_SHORTCUT' && grep -q '원래대로' "$T/out3.txt" || fail "3 puts ☆ back"
! c 1 | grep -q 'service call' || fail "1 install must not touch ☆"
! c 4 | grep -qE 'push|pm ' && grep -q 'pd_kr2' "$T/out4.txt" || fail "4 must not touch a non-pd_kr navi"
[ "$(c 5 | grep -c '^push ')" = 2 ] || fail "5 installs both"
! c 6 | grep -qE '^push |pm install' && [ "$(c 6 | grep -c '^pull ')" = 2 ] && [ "$(grep -c '건너뜀' "$T/out6.txt")" = 2 ] || fail "6 skips identical apps"
! c 7 | grep -qE '^push |pm |service call' || fail "7 diagnostics must not change the car"
D=$(sed -n 's/^DIAG //p' "$T/out7.txt"); [ -f "$D" ] || fail "7 no diagnostics file"
grep -q 'persist.sys.key.custom: com.hkmc.intent.action.MYMENU_SHORTCUT' "$D" && grep -q 'versionName=1.9' "$D" && grep -q 'draw avg' "$D" || fail "7 diagnostics content"
! grep -qE '00:1A:7D|1234-5678|kim@example' "$D" && grep -q '<MAC>' "$D" && grep -q '<전화번호>' "$D" && grep -q '<메일>' "$D" || fail "7 diagnostics must be masked"
[ "$(grep -c 'draw avg' "$D")" -le 1000 ] || fail "7 log tail is capped"
grep -q '전에 받아 둔' "$T/out8.txt" && ! c 8 | grep -q '^push ' || fail "8 falls back to the kept APKs"
for n in 9 10; do
  c $n | grep -q '^push /data/local/tmp/hscandump.jar 7' && c $n | grep -q 'HscanDump [0-9]* raw' || fail "$n pushes the dumper and runs it"
  ! c $n | grep -qE 'pm |service call|rm ' || fail "$n recording must not change the car"
done
Z=$(sed -n 's/^DIAG //p' "$T/out9.txt"); [ -f "$Z" ] && [ "${Z%.zip}" != "$Z" ] || fail "9 no zip"
python -X utf8 -c "
import sys, zipfile
z = zipfile.ZipFile(sys.argv[1]); [n] = z.namelist(); t = z.read(n).decode()
assert n.startswith('i30pd_drive_') and t.startswith('# DRIVE'), n
assert t.count('RAW ') == 600 and 'conn9 0299' in t and 'conn10 0299' in t, 'both parts'
assert t.index('# DROP') < t.index('conn10 0000'), 'drop marked between the parts'
" "$Z" || fail "9-10 zip content"
grep -q '다시 연결합니다' "$T/out9.txt" && grep -q '주행 기록을 만들었습니다' "$T/out9.txt" || fail "9-10 messages"
[ ! -f "${Z%.zip}.txt" ] || fail "9 text left next to the zip"
echo "OK: phone installer flow (9 scenarios)"
