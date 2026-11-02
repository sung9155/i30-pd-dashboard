@echo off
rem i30 PD dashboard uninstaller. Double-click this file to remove both apps from the car.
rem Same script as the installer, run with -Uninstall.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0install.ps1" -Uninstall %*
echo.
pause
