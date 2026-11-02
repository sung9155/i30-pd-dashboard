#!/usr/bin/env bash
# Phone installer without a phone or a car: Flow + Adb on the desktop JDK against fake_adbd.py.
# Covers new-key prompt, known-key signature, signature clash reinstall, uninstall (☆ put back), and a non-pd_kr navi.
set -euo pipefail
cd "$(dirname "$0")"
T=$(mktemp -d); trap 'kill $FAKE 2>/dev/null; rm -rf "$T"' EXIT
mkdir -p "$T/server" "$T/key" "$T/cls"
python -X utf8 -c "
import os,sys
for n,k in (('dashboard-i30pd-platform.apk',170000),('myapps-i30pd-platform.apk',9000)):
    open(os.path.join(sys.argv[1],n),'wb').write(b'PK'+os.urandom(k))" "$T/server"
javac -encoding UTF-8 -d "$T/cls" ../src/com/i30/installer/Adb.java ../src/com/i30/installer/Flow.java FlowTest.java
PORT=$((20000 + RANDOM % 20000))
python -X utf8 fake_adbd.py $PORT "$T/fake.log" "$T/server" & FAKE=$!
for _ in $(seq 50); do grep -q ready "$T/fake.log" 2>/dev/null && break; sleep 0.1; done
BASE="file:///$(cd "$T/server" && pwd -W 2>/dev/null || pwd)"
run(){ java -Dsun.stdout.encoding=UTF-8 -cp "$T/cls" com.i30.installer.FlowTest $PORT "$BASE" "$T/key" "$1" > "$T/out$2.txt"; }
fail(){ echo "FAIL: $1"; echo "--- fake log"; cat "$T/fake.log"; for f in "$T"/out*.txt; do echo "--- $f"; cat "$f"; done; exit 1; }

run install 1 || fail "1 new key install"
run install 2 || fail "2 clash reinstall"
run uninstall 3 || fail "3 uninstall"
set +e; run install 4; rc=$?; set -e
[ $rc -eq 3 ] || fail "4 non-pd_kr should stop (rc=$rc)"

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
echo "OK: phone installer flow (4 scenarios)"
