#!/bin/bash
# i30 PD 대시보드 진단 기록 — 리눅스. install.sh 를 --diag 로 실행한다(읽기만, 설치·변경 없음).
exec bash "$(dirname "$0")/install.sh" --diag "$@"
