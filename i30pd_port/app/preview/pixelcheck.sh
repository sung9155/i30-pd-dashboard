#!/usr/bin/env bash
# 8-bit views stay 8-bit: every 4x4 scene pixel one colour, every colour from the renderer's palette.
# Usage (repo root): bash i30pd_port/app/preview/pixelcheck.sh [png-dir]
set -e
here=i30pd_port/app/preview; src=shared/src/com/veloster
list=$(grep -o '{CarPaint[^}]*}' $here/preview.sh | tr -d '{}' | tr ',' ' ')
tmp=$(mktemp -d)
javac -proc:none -encoding UTF-8 -d "$tmp" $here/android/graphics/*.java $src/i18n/UiText.java $src/vehicle/*Cluster*.java \
  $(for n in $list; do echo $src/vehicle/$n.java; done) $here/TabsPreview.java $here/PixelGridCheck.java
java -cp "$tmp" com.veloster.vehicle.PixelGridCheck $src/vehicle/PixelCluster.java "$@"
