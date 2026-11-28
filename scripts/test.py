#!/usr/bin/env python3
"""Run synthetic host tests without Android, private logs, network or ADB."""
import argparse
import os
from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--java", default="java")
    args = parser.parse_args()
    found = shutil.which(args.java)
    if not found:
        raise SystemExit("JDK 17 java is required")
    java = Path(found).resolve()
    javac = java.with_name("javac.exe" if os.name == "nt" else "javac")
    hub = ROOT / "apps/hub/src/com/veloster/carplayhub"
    sources = [ROOT / "shared/src/com/veloster/vehicle/Telemetry.java",
               ROOT / "shared/src/com/veloster/vehicle/VehicleProfile.java",
               ROOT / "shared/src/com/veloster/vehicle/ShiftGuide.java",
               ROOT / "shared/src/com/veloster/vehicle/Dtc.java",
               ROOT / "shared/src/com/veloster/vehicle/DriveStats.java",
               ROOT / "shared/src/com/veloster/vehicle/Records.java",
               ROOT / "shared/src/com/veloster/vehicle/Theme.java",
               ROOT / "shared/src/com/veloster/vehicle/HeaderTabs.java",
               ROOT / "shared/src/com/veloster/vehicle/HeaderClock.java",
               ROOT / "shared/src/com/veloster/vehicle/ClusterPicker.java",
               ROOT / "shared/src/com/veloster/vehicle/SettingsLayout.java",
               ROOT / "shared/src/com/veloster/vehicle/SparkRetard.java",
               ROOT / "shared/src/com/veloster/vehicle/DayPhase.java",
               ROOT / "shared/src/com/veloster/vehicle/DayNight.java",
               ROOT / "shared/src/com/veloster/vehicle/TaxiFare.java",
               ROOT / "shared/src/com/veloster/vehicle/TaxiTariff.java",
               ROOT / "shared/src/com/veloster/vehicle/TaxiZones.java",
               ROOT / "shared/src/com/veloster/vehicle/TaxiZoneData.java",
               ROOT / "shared/src/com/veloster/vehicle/LapTimer.java",
               ROOT / "shared/src/com/veloster/vehicle/CircuitLap.java",
               ROOT / "shared/src/com/veloster/vehicle/Circuits.java",
               ROOT / "shared/src/com/veloster/vehicle/TrackPath.java",
               ROOT / "shared/src/com/veloster/vehicle/CircuitData.java",
               ROOT / "shared/src/com/veloster/vehicle/CarPaint.java",
               ROOT / "shared/src/com/veloster/vehicle/RoadJolt.java",
               ROOT / "shared/src/com/veloster/vehicle/Weather.java",
               ROOT / "shared/src/com/veloster/vehicle/ArcadeCarFrames.java",
               ROOT / "shared/src/com/veloster/vehicle/PlatePixels.java",
               ROOT / "shared/src/com/veloster/vehicle/EngineSynth.java",
               ROOT / "shared/src/com/veloster/vehicle/SpeedChime.java",
               ROOT / "shared/src/com/veloster/vehicle/ObdSchedule.java",
               ROOT / "shared/src/com/veloster/vehicle/Tiles.java",
               ROOT / "shared/src/com/veloster/vehicle/RevMatch.java",
               ROOT / "shared/src/com/veloster/vehicle/ShiftCoach.java",
               ROOT / "shared/src/com/veloster/vehicle/GearRatios.java",
               ROOT / "shared/src/com/veloster/vehicle/ArcadeGame.java",
               ROOT / "shared/src/com/veloster/vehicle/SpeedCal.java",
               ROOT / "shared/src/com/veloster/vehicle/SettingsBackup.java",
               ROOT / "shared/src/com/veloster/vehicle/Glide.java",
               ROOT / "shared/src/com/veloster/vehicle/SoundYield.java",
               ROOT / "shared/src/com/veloster/vehicle/FontFile.java",
               ROOT / "shared/src/com/veloster/vehicle/SystemFonts.java",
               ROOT / "shared/src/com/veloster/vehicle/BundledFonts.java",
               ROOT / "shared/src/com/veloster/i18n/UiText.java",
               ROOT / "shared/src/com/veloster/diagnostics/ErrorLog.java",
               ROOT / "apps/dashboard/src/com/veloster/dashboard/HscanFrameDecoder.java"]
    sources += [hub / (name + ".java") for name in ("RadioTrial", "BluetoothSession", "RebootRequest", "QuickEntryPolicy")]
    # 네온 드라이브 and 내 차 (ported from upstream): the signals, the model, the i30 sprite; the mesh reader and lamp state.
    neon = ROOT / "apps/neondrive/src/com/veloster/neondrive"
    sources += [neon / (name + ".java") for name in ("DriveSignals", "PdSignals", "DriveModel", "CarSprite", "Theme", "SolarClock", "PixelFont")]
    sources += [ROOT / "apps/dashboard/src/com/veloster/car3d" / (name + ".java") for name in ("CarModel", "CarState", "CarScene", "CarPlate")]
    sources += sorted((ROOT / "i30pd_port/app/preview/android/graphics").glob("*.java"))   # CarPlate draws with the preview's Java2D stand-ins
    tests = sorted((ROOT / "tests").glob("*Test.java"))
    sources += tests
    with tempfile.TemporaryDirectory(prefix="vn-public-tests-") as temporary:
        subprocess.run([str(javac), "-encoding", "UTF-8", "-d", temporary,
                        *map(str, sources)], check=True)
        for test in tests:
            cls = {"HscanFrameDecoderTest": "com.veloster.dashboard.", "ArcadeGameTest": "com.veloster.vehicle.", "SoundYieldTest": "com.veloster.vehicle."}.get(test.stem, "") + test.stem   # tests of package-private classes
            subprocess.run([str(java), "-ea", "-Dvn.root=" + str(ROOT), "-cp", temporary, cls], check=True, cwd=ROOT)   # tests read repo files by relative path
    print("PASS: " + str(len(tests)) + " synthetic host test programs. Vehicle operation is outside this scope.")


if __name__ == "__main__":
    main()
