# 출처와 권리

## 원작

이 프로젝트는 [reikop/veloster-n-dashboard](https://github.com/reikop/veloster-n-dashboard)(`2be141b`, MIT, Copyright (c) 2026 Veloster N Dashboard contributors)의 포크이고, 그 위에 i30 PD 포팅을 커밋으로 쌓았습니다. 원작 코드와 원작의 권리 고지([`NOTICE_veloster.md`](NOTICE_veloster.md))는 그대로 두었습니다. Releases의 APK는 이 레포의 소스를 빌드한 것입니다. 두 저작권 표시는 [`LICENSE`](LICENSE)에 있습니다.

## 신호 정의

CAN 메시지·신호의 배치와 배율은 [commaai/opendbc](https://github.com/commaai/opendbc)(MIT)의 Hyundai DBC(`hyundai_2015_ccan.dbc`, `hyundai_kia_generic.dbc`)를 참고했고, 실차 로그로 확인했습니다. DBC 파일 자체는 이 레포에 넣지 않았습니다. `i30pd_port/tools/`의 도구는 사용자가 받은 DBC 파일 경로를 인자로 받습니다.

## 이 레포에 넣지 않은 것

- 현대자동차 순정 펌웨어, 순정 앱, 그 디컴파일 결과. 문서의 "PD 펌웨어 대조"는 공개된 펌웨어를 받아 확인한 사실(클래스·액티비티 이름 등)만 적은 것입니다.
- 순정 시스템 앱을 수정·재서명하는 i30 PD용 도구, 펌웨어 업데이트 검증값을 다시 만드는 도구. 루트의 `menu-entry/`는 원작이 배포한 벨로스터 N용 메뉴 연결 도구 그대로이며 i30 PD에서는 확인하지 않았습니다.
- 서명용 개인키. APK는 원작 배포본과 같은 공개 AOSP platform 테스트 인증서로 서명했습니다. 이 인증서는 배포자 신원을 증명하지 않으니, 릴리스의 SHA-256과 받은 파일을 대조하세요.

## 상표

Hyundai, i30, Veloster, N, D-Audio 등은 각 권리자의 상표이며 대상 차량·기기를 설명하려고 썼습니다. 이 프로젝트는 현대자동차의 공식 앱이 아니고 승인받은 제품도 아닙니다. 패키지 이름 `com.veloster.*`는 원작에서 그대로 쓴 것입니다.

## 면책

차량 값은 내비가 받는 데이터를 읽기만 하고 차에 쓰지 않습니다. 그래도 순정 시스템에 앱을 설치하는 일이므로 보증·AS 불이익, 오작동, 그 밖의 결과는 사용자 책임입니다. 표시값은 참고용이며 차량 계기판·경고등을 대신하지 않습니다.
