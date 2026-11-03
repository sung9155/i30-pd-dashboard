@echo off
rem i30 PD dashboard diagnostics. Double-click to read the car's state and our apps' log into a file to mail.
rem Same script as the installer, run with -Diag. Installs and changes nothing.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0install.ps1" -Diag %*
echo.
pause
