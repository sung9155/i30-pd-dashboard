#!/bin/bash
# PC installers without a car: install.ps1 / 설치.command against fakeadb.py, in Windows Git Bash (powershell.exe, cygpath).
# Covers install, the same install again (skipped), a new release (installed), diagnostics (masked, read only),
# uninstall (☆ put back), and the self-update against fakerelease.py (same version: quiet, tampered: refused, newer: replaced
# and restarted, the other channel's newer one: ignored, offline: carries on). No network needed.
# usage: bash run.sh win|mac|linux   (mac and linux both run 설치.command, with I30PD_OS set: this is Git Bash, neither)
F="$(cd "$(dirname "$0")" && pwd)"; I="$(dirname "$F")"; WHO="$1"; [ "$WHO" = linux ] && OSV=Linux || OSV=Darwin
SRV="$F/server"; FW="$(cygpath -w "$F")"
# fake GitHub releases for every run (never the real one: a newer real installer would replace these files under -Yes).
# Both channels start with ZIPs of this very installer, so no update is offered until a scenario puts a newer one there.
R="$F/rel"; rm -rf "$R" "$F/kit"; mkdir -p "$R"
for c in stable test; do python -X utf8 "$I/../../scripts/release.py" installers $c --out "$(cygpath -m "$R/$c")" > /dev/null || { echo "FAIL[$WHO]: zips"; exit 1; }; done
cp -r "$R/stable" "$R/orig"   # kit orig = a user folder unzipped from the current stable ZIP
python -X utf8 "$I/../../scripts/release.py" installers stable --label 2099-01-01 --out "$(cygpath -m "$R/new")" > /dev/null
python "$F/fakerelease.py" "$(cygpath -m "$R")" & RPID=$!; trap 'kill $RPID 2>/dev/null' EXIT
for _ in $(seq 50); do [ -s "$R/port" ] && break; sleep 0.1; done
export I30PD_API="http://127.0.0.1:$(cat "$R/port")"
case $WHO in win) ZIP=i30pd-installer-windows.zip; MAIN=install.ps1 ;; mac) ZIP=i30pd-installer-mac.zip; MAIN=설치.command ;; *) ZIP=i30pd-installer-linux.zip; MAIN=install.sh ;; esac
kit(){ rm -rf "$F/kit"; python -c "import sys,zipfile; zipfile.ZipFile(sys.argv[1]).extractall(sys.argv[2])" "$(cygpath -m "$R/$1/$ZIP")" "$(cygpath -m "$F/kit")"; SCRIPT="$F/kit/$MAIN"; }
apks(){ mkdir -p "$SRV"; python -c "
import os,sys
for n,k in (('dashboard-i30pd-platform.apk',170000),('myapps-i30pd-platform.apk',9000)):
    open(os.path.join(sys.argv[1],n),'wb').write(b'PK'+os.urandom(k))" "$SRV"; }
reset(){ rm -rf "$F/inst" "$F/tmp" "$F/calls.log" "$F/home"; echo com.veloster.carplayhub.STAR_KEY > "$F/star.txt"; }
run(){ # args after mode
  rm -f "$F/calls.log"
  if [ $WHO = win ]; then powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$(cygpath -w "${SCRIPT:-$I/install.ps1}")" -AdbPath "$FW\\adb.cmd" -Yes "$@"
  else HOME="$F/home" I30PD_OS=$OSV bash "${SCRIPT:-$I/설치.command}" --adb "$F/adb" --yes "$@"; fi > "$F/out.txt" 2>&1; echo $? > "$F/rc"
}
fail(){ echo "FAIL[$WHO]: $1"; cat "$F/out.txt"; exit 1; }
BASE="file:///$(cygpath -m "$SRV")"
reset; apks
if [ $WHO = win ]; then A=(-ApkBase "$BASE"); else A=(--apk-base "$BASE"); fi
run "${A[@]}"; [ "$(cat $F/rc)" = 0 ] && [ "$(grep -c 'pm install' $F/calls.log)" = 2 ] || fail "1 install"
run "${A[@]}"; [ "$(cat $F/rc)" = 0 ] && ! grep -q 'pm install' $F/calls.log && [ "$(grep -c '건너뜀' $F/out.txt)" = 2 ] || fail "2 same install skipped"
apks; run "${A[@]}"; [ "$(cat $F/rc)" = 0 ] && [ "$(grep -c 'pm install' $F/calls.log)" = 2 ] || fail "3 new release installs"
if [ $WHO = win ]; then run -Diag; else run --diag; fi
D="$(python -X utf8 "$F/madepath.py" "$F/out.txt")"; [ $WHO = win ] && D="$(cygpath -u "$D")"   # the line can wrap at a space (long OneDrive desktop path)
[ "$(cat $F/rc)" = 0 ] && [ -f "$D" ] || fail "4 diag file ($D)"
! grep -qE 'pm install|uninstall|service call|push' $F/calls.log || fail "4 diag must not change the car"
grep -q 'persist.sys.key.custom: com.veloster.carplayhub.STAR_KEY' "$D" && grep -q 'versionName=1.9' "$D" && grep -q 'draw avg' "$D" && grep -q '이 PC 설치기 기록' "$D" || fail "4 diag content"
! grep -qE '00:1A:7D|1234-5678|kim@example|FAKE123' "$D" && ! grep -qF "$([ $WHO = win ] && cygpath -w "$USERPROFILE" || echo "$F/home")" "$D" && grep -q '<MAC>' "$D" && grep -q '<전화번호>' "$D" && grep -q '<메일>' "$D" && grep -q '<기기번호>' "$D" || { cp "$D" "$F/diag_$WHO.txt"; fail "4 diag masking"; }
[ "$(grep -c 'draw avg' "$D")" -le 1500 ] || fail "4 log capped"
cp "$D" "$F/diag_$WHO.txt"; rm -f "$D"
if [ $WHO = win ]; then FAKE_GONE=1 run -Uninstall; else FAKE_GONE=1 run --uninstall; fi   # cable out mid-way: not "삭제 완료"
[ "$(cat $F/rc)" != 0 ] && grep -q '연결이 끊긴' $F/out.txt && ! grep -q '삭제 완료' $F/out.txt || fail "5 uninstall with the car gone"
echo com.veloster.carplayhub.STAR_KEY > "$F/star.txt"
if [ $WHO = win ]; then run -Uninstall; else run --uninstall; fi
[ "$(cat $F/rc)" = 0 ] && grep -q '원래대로' $F/out.txt && [ -z "$(ls $F/inst)" ] || fail "5 uninstall + ☆"
# 시동 안내 1초 (BootFast): needs a real SystemUI capture, tools and a Java 11+ (BOOT_CAP, BOOT_TOOLS with
# i30pd-consent-fast.jar, BOOT_JAVA); skipped without them (the capture is Hyundai code: never in the repo).
if [ -n "$BOOT_CAP" ] && [ -d "$BOOT_CAP" ]; then
  SUI="$F/inst/com.android.systemui.apk"; BW=(); BS=()
  if [ $WHO = win ]; then BW=(-JavaPath "$(cygpath -w "$BOOT_JAVA")" -ToolDir "$(cygpath -w "$BOOT_TOOLS")"); BF=-BootFast; BR=-BootFastRemove; UN=-Uninstall
  else BW=(--java "$BOOT_JAVA" --tool-dir "$BOOT_TOOLS"); BF=--boot-fast; BR=--boot-fast-remove; UN=--uninstall; fi
  CAPDIR="$([ $WHO = win ] && cygpath -u "$LOCALAPPDATA" || echo "$F/home/.i30pd")/i30pd-installer/bootfast/cap"
  rm -f "$SUI"
  FAKE_CAP="$BOOT_CAP" run $BF "${BW[@]}"
  [ "$(cat $F/rc)" = 0 ] && [ -f "$SUI" ] && grep -q '설치 완료' $F/out.txt && python -c "import zipfile,sys;z=zipfile.ZipFile(sys.argv[1]);assert 'classes.dex' in z.namelist() and 'META-INF/CERT.RSA' in z.namelist()" "$SUI" || fail "7 boot fast install"
  [ ! -e "$CAPDIR" ] || fail "7 the car's SystemUI copy must not stay on the PC ($CAPDIR)"
  FAKE_CAP="$BOOT_CAP" run $BF "${BW[@]}"; [ "$(cat $F/rc)" = 0 ] && grep -q '이미 설치돼' $F/out.txt && ! grep -q 'pm install' $F/calls.log || fail "8 boot fast already there"
  run $UN; [ "$(cat $F/rc)" = 0 ] && grep -q '시동 안내 화면: 순정으로 되돌림' $F/out.txt && [ ! -f "$SUI" ] || fail "9 uninstall reverts the boot fast"
  mkdir -p "$F/badcap"; cp -r "$BOOT_CAP"/. "$F/badcap/"; printf x >> "$F/badcap/HKMC_SystemUI.apk"
  FAKE_CAP="$F/badcap" run $BF "${BW[@]}"; rm -rf "$F/badcap"
  [ "$(cat $F/rc)" = 3 ] && [ ! -f "$SUI" ] && ! grep -q 'pm install' $F/calls.log && [ ! -e "$CAPDIR" ] || fail "10 unknown SystemUI stops"
  run $BR; [ "$(cat $F/rc)" = 0 ] && grep -q '이미 순정' $F/out.txt || fail "11 remove when stock"
  FAKE_CAP="$BOOT_CAP" run $BF "${BW[@]}"; run $BR; [ "$(cat $F/rc)" = 0 ] && grep -q '되돌림 완료' $F/out.txt && [ ! -f "$SUI" ] || fail "12 remove"
  echo "OK[$WHO]: boot fast install, already there, uninstall reverts, unknown build stops, remove"
fi
# self-update, on an unzipped release ZIP (a user's installer folder), in uninstall mode (quick, nothing installed)
if [ $WHO = win ]; then U=-Uninstall; else U=--uninstall; fi
ver(){ grep -aE "^.?(InstallerVersion|INSTALLER_VERSION) *= *'" "$SCRIPT" | head -1 | cut -d"'" -f2; }
kit orig; CUR="$(ver)"; echo keep > "$F/kit/platform-tools.txt"
run $U; [ "$(cat $F/rc)" = 0 ] && ! grep -q '새 설치기' $F/out.txt || fail "6 same version: no prompt"
I30PD_API=http://127.0.0.1:9 run $U; [ "$(cat $F/rc)" = 0 ] && ! grep -q '새 설치기' $F/out.txt && grep -q '삭제 완료' $F/out.txt || fail "7 offline carries on"
kit test; cp "$R/new/$ZIP" "$R/stable/$ZIP"   # newer stable only: a test-channel installer looks at 'test'
run $U; [ "$(cat $F/rc)" = 0 ] && ! grep -q '새 설치기' $F/out.txt && [ "$(ver)" = "$CUR" ] || fail "8 the other channel's newer installer is not offered"
kit orig; echo keep > "$F/kit/platform-tools.txt"; touch "$R/stable/$ZIP.bad"
run $U; [ "$(cat $F/rc)" = 0 ] && grep -q '확인이 맞지 않아 쓰지 않습니다: SHA-256' $F/out.txt && [ "$(ver)" = "$CUR" ] && grep -q '삭제 완료' $F/out.txt || fail "9 tampered download refused"
rm -f "$R/stable/$ZIP.bad"
run $U; [ "$(cat $F/rc)" = 0 ] && grep -q '새 설치기가 나왔습니다: 2099-01-01' $F/out.txt && grep -q '다시 시작합니다' $F/out.txt && [ "$(ver)" = 2099-01-01 ] \
  && grep -q '설치기 2099-01-01)' $F/out.txt && grep -q '삭제 완료' $F/out.txt && [ -f "$F/kit/platform-tools.txt" ] || fail "10 newer installer replaces itself and restarts"
run $U; [ "$(cat $F/rc)" = 0 ] && ! grep -q '새 설치기' $F/out.txt || fail "11 after the update: no prompt"
SCRIPT=; rm -rf "$F/kit" "$R"
echo "OK[$WHO]: install, skip identical, new release, diag (masked, read only), uninstall + ☆, self-update (same, offline, other channel, tampered, newer)"
