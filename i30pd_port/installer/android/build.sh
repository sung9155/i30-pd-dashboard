#!/usr/bin/env bash
# Phone installer APK: aapt2 (manifest only) + javac + d8 + apksigner. No Gradle.
# SDK: build-tools 34 + platforms/android-17 (the same SDK the dashboard builds with).
# KS: release keystore, kept OUT of every repo. The same key must sign every version, or phones refuse the update.
# KS_PASS: its password, from the environment only.
set -euo pipefail
SDK="${ANDROID_HOME:-/d/Android/sdk}"
BT="$SDK/build-tools/34.0.0"
JAR="$SDK/platforms/android-17/android.jar"
KS="${KS:?set KS to the release keystore path}"
KS_PASS="${KS_PASS:?set KS_PASS to the keystore password}"
# versionName = Flow.VERSION (one place); versionCode goes up by one with every published APK.
VER_CODE="${VER_CODE:-8}"; VER_NAME="$(grep -o 'VERSION = "[^"]*"' "$(dirname "$0")/src/com/i30/installer/Flow.java" | head -1 | cut -d'"' -f2)"
cd "$(dirname "$0")"
rm -rf build && mkdir -p build/classes
"$BT/aapt2" link --manifest AndroidManifest.xml -I "$JAR" --min-sdk-version 26 --target-sdk-version 34 \
  --version-code "$VER_CODE" --version-name "$VER_NAME" -o build/base.apk
# java.* (Base64, nio.file) comes from the JDK's API 8 surface; d8 maps it onto the phone's runtime (API 26+).
javac --release 8 -encoding UTF-8 -cp "$JAR" -d build/classes src/com/i30/installer/*.java
java -cp "$BT/lib/d8.jar" com.android.tools.r8.D8 --min-api 26 --lib "$JAR" --output build $(find build/classes -name '*.class')
# [주행 기록] pushes the receive-only hscan dumper to the car; it rides along as an asset.
bash ../../build_dump.sh >/dev/null
python -X utf8 -c "import zipfile; z=zipfile.ZipFile('build/base.apk','a'); z.write('build/classes.dex','classes.dex'); z.write('../../hscandump.jar','assets/hscandump.jar')"
"$BT/zipalign" -f -p 4 build/base.apk build/aligned.apk
[ -f "$KS" ] || keytool -genkeypair -keystore "$KS" -storetype PKCS12 -storepass "$KS_PASS" -alias installer \
  -keyalg RSA -keysize 3072 -validity 36500 -dname "CN=i30 PD dashboard installer"
java -jar "$BT/lib/apksigner.jar" sign --ks "$KS" --ks-pass env:KS_PASS --out build/i30pd-installer-android.apk build/aligned.apk
ls -l build/i30pd-installer-android.apk
