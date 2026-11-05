#!/bin/bash
# i30 PD 대시보드 삭제 — 리눅스. install.sh 를 --uninstall 로 실행한다(두 앱 삭제, ☆ 되돌리기).
exec bash "$(dirname "$0")/install.sh" --uninstall "$@"
