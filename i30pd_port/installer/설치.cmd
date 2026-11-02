@echo off
rem i30 PD dashboard installer launcher. Double-click this file.
rem It runs install.ps1 through Windows' own signed powershell.exe, so there is no
rem SmartScreen "protected your PC" block the way an unsigned .exe gets.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0install.ps1" %*
echo.
pause
