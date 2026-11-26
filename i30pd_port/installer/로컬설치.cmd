@echo off
rem i30 PD dashboard: installs the APKs built on this PC (docker\build.ps1 -> build\apk in the repo). No internet is used.
rem Same script as the installer, run with -LocalBuild.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0install.ps1" -LocalBuild %*
echo.
pause
