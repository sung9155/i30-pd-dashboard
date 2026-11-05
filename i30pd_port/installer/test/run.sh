#!/bin/bash
# PC installers without a car: install.ps1 / 설치.command against fakeadb.py, in Windows Git Bash (powershell.exe, cygpath).
# Covers install, the same install again (skipped), a new release (installed), diagnostics (masked, read only),
# uninstall (☆ put back), and the new-installer notice (reads raw.githubusercontent.com, so it needs the network).
# usage: bash run.sh win|mac|linux   (mac and linux both run 설치.command, with I30PD_OS set: this is Git Bash, neither)
F="$(cd "$(dirname "$0")" && pwd)"; I="$(dirname "$F")"; WHO="$1"; [ "$WHO" = linux ] && OSV=Linux || OSV=Darwin
SRV="$F/server"; FW="$(cygpath -w "$F")"
apks(){ mkdir -p "$SRV"; python -c "
import os,sys
for n,k in (('dashboard-i30pd-platform.apk',170000),('myapps-i30pd-platform.apk',9000)):
    open(os.path.join(sys.argv[1],n),'wb').write(b'PK'+os.urandom(k))" "$SRV"; }
reset(){ rm -rf "$F/inst" "$F/tmp" "$F/calls.log" "$F/home"; echo com.veloster.carplayhub.STAR_KEY > "$F/star.txt"; }
run(){ # args after mode
  rm -f "$F/calls.log"
  if [ $WHO = win ]; then powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$(cygpath -w "$I/install.ps1")" -AdbPath "$FW\\adb.cmd" -Yes "$@"
  else HOME="$F/home" I30PD_OS=$OSV bash "$I/설치.command" --adb "$F/adb" --yes "$@"; fi > "$F/out.txt" 2>&1; echo $? > "$F/rc"
}
fail(){ echo "FAIL[$WHO]: $1"; cat "$F/out.txt"; exit 1; }
BASE="file:///$(cygpath -m "$SRV")"
reset; apks
if [ $WHO = win ]; then A=(-ApkBase "$BASE"); else A=(--apk-base "$BASE"); fi
run "${A[@]}"; [ "$(cat $F/rc)" = 0 ] && [ "$(grep -c 'pm install' $F/calls.log)" = 2 ] || fail "1 install"
run "${A[@]}"; [ "$(cat $F/rc)" = 0 ] && ! grep -q 'pm install' $F/calls.log && [ "$(grep -c '건너뜀' $F/out.txt)" = 2 ] || fail "2 same install skipped"
apks; run "${A[@]}"; [ "$(cat $F/rc)" = 0 ] && [ "$(grep -c 'pm install' $F/calls.log)" = 2 ] || fail "3 new release installs"
if [ $WHO = win ]; then run -Diag; else run --diag; fi
D="$(sed -n 's/.*만든 파일: //p' $F/out.txt | sed 's/\x1b\[[0-9;]*m//g' | tr -d '\r')"; [ $WHO = win ] && D="$(cygpath -u "$D")"
[ "$(cat $F/rc)" = 0 ] && [ -f "$D" ] || fail "4 diag file ($D)"
! grep -qE 'pm install|uninstall|service call|push' $F/calls.log || fail "4 diag must not change the car"
grep -q 'persist.sys.key.custom: com.veloster.carplayhub.STAR_KEY' "$D" && grep -q 'versionName=1.9' "$D" && grep -q 'draw avg' "$D" && grep -q '이 PC 설치기 기록' "$D" || fail "4 diag content"
! grep -qE '00:1A:7D|1234-5678|kim@example|FAKE123' "$D" && ! grep -qF "$([ $WHO = win ] && cygpath -w "$USERPROFILE" || echo "$F/home")" "$D" && grep -q '<MAC>' "$D" && grep -q '<전화번호>' "$D" && grep -q '<메일>' "$D" && grep -q '<기기번호>' "$D" || { cp "$D" "$F/diag_$WHO.txt"; fail "4 diag masking"; }
[ "$(grep -c 'draw avg' "$D")" -le 1500 ] || fail "4 log capped"
cp "$D" "$F/diag_$WHO.txt"; rm -f "$D"
if [ $WHO = win ]; then run -Uninstall; else run --uninstall; fi
[ "$(cat $F/rc)" = 0 ] && grep -q '원래대로' $F/out.txt && [ -z "$(ls $F/inst)" ] || fail "5 uninstall + ☆"
# an old installer must hear about the published one (network: raw.githubusercontent.com main)
if [ $WHO = win ]; then sed "s/^\(\$InstallerVersion = \)'[^']*'/\1'2026-01-01'/" "$I/install.ps1" > "$I/old.ps1"
  powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$(cygpath -w "$I/old.ps1")" -AdbPath "$FW\\adb.cmd" -Yes -Uninstall > "$F/out.txt" 2>&1; rm -f "$I/old.ps1"
else sed "s/^INSTALLER_VERSION='[^']*'/INSTALLER_VERSION='2026-01-01'/" "$I/설치.command" > "$I/old.command"
  HOME="$F/home" I30PD_OS=$OSV bash "$I/old.command" --adb "$F/adb" --yes --uninstall > "$F/out.txt" 2>&1; rm -f "$I/old.command"; fi
grep -q '새 설치기가 있습니다(안정화' $F/out.txt || fail "6 old installer notice"
grep -q '새 설치기가 있습니다' <(run "${A[@]}"; cat $F/out.txt) && fail "6 current installer must not be told" || true
echo "OK[$WHO]: install, skip identical, new release, diag (masked, read only), uninstall + ☆, installer notice"
