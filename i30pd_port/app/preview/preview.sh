#!/usr/bin/env bash
# Usage: preview.sh <this repo root> [raw log] [out dir] [position 0..1]
# Renders every dashboard tab from the real renderers + a real hscan log, through a Java2D android.graphics shim.
# Layout check only: the real compile against android.jar happens in the app build (app/README.md).
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
src="$1/shared/src/com/veloster"
log="${2:-$here/../../logs/act_one_drive_10min_20261029_183922.log}"
out="${3:-$here/../screens}"
tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT
javac -proc:none -encoding UTF-8 -d "$tmp" "$here"/android/graphics/*.java "$src/i18n/UiText.java" \
  "$src"/vehicle/*Cluster*.java "$src"/vehicle/{CarPaint,TaxiFare,Telemetry,Theme,ThemeShapes,HeaderTabs,SettingsLayout,PitRenderer,SparkRetard,DayPhase,GaugeRenderer,GraphRenderer,PerformanceRenderer,RaceRenderer,ShowRenderer,VehicleProfile,DriveStats,GaugeLayout,GraphLayout,OverviewLayout,ShiftGuide,MediaState}.java \
  "$here/TabsPreview.java"
# Android 4.2 Roboto (see TABS.md "다시 그리기"); without it Latin text falls back to the CJK font.
fonts="${PREVIEW_FONTS:-$here/fonts}"
[ -f "$fonts/Roboto-Regular.ttf" ] || echo "note: no Roboto in $fonts, Latin text uses the CJK font" >&2
java -Djava.awt.headless=true -Dpreview.fonts="$fonts" -Dpreview.theme="${PREVIEW_THEME:-n}" -Dpreview.paint="${PREVIEW_PAINT:-blue}" -cp "$tmp" com.veloster.vehicle.TabsPreview "$log" "$out" "${4:-1}"
ls "$out"
# Body regression check (header excluded): java "$here/BodyDiff.java" <before_dir> <after_dir> [y0=52]
