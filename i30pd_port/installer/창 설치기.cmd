@echo off
rem i30 PD dashboard window installer. Double-click this file: one window with buttons, no numbers or Enter to type.
rem It runs gui.ps1 (WinForms) through Windows' own signed powershell.exe; gui.ps1 runs install.ps1 -Gui hidden.
rem -WindowStyle Hidden hides this console as soon as PowerShell starts (a split-second flash is all cmd allows).
rem Everything is on one line so cmd never reads this file again after a self-update replaced it.
powershell -NoProfile -STA -ExecutionPolicy Bypass -WindowStyle Hidden -File "%~dp0gui.ps1" & exit /b
