#!/bin/bash
# i30 PD 대시보드 삭제 — macOS. Finder에서 더블클릭. 설치.command 를 --uninstall 로 실행한다.
exec bash "$(dirname "$0")/설치.command" --uninstall "$@"
