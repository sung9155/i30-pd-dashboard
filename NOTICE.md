# 출처와 권리

## 원작

이 프로젝트는 [reikop/veloster-n-dashboard](https://github.com/reikop/veloster-n-dashboard)(`2be141b`, MIT, Copyright (c) 2026 Veloster N Dashboard contributors)의 포크이고, 그 위에 i30 PD 포팅을 커밋으로 쌓았습니다. 원작 코드와 원작의 권리 고지([`NOTICE_veloster.md`](NOTICE_veloster.md))는 그대로 두었습니다. Releases의 APK는 이 레포의 소스를 빌드한 것입니다. 두 저작권 표시는 [`LICENSE`](LICENSE)에 있습니다.

## 신호 정의

CAN 메시지·신호의 배치와 배율은 [commaai/opendbc](https://github.com/commaai/opendbc)(MIT)의 Hyundai DBC(`hyundai_2015_ccan.dbc`, `hyundai_kia_generic.dbc`)를 참고했고, 실차 로그로 확인했습니다. DBC 파일 자체는 이 레포에 넣지 않았습니다. `i30pd_port/tools/`의 도구는 사용자가 받은 DBC 파일 경로를 인자로 받습니다.

## 진단 요청 (모드 22)

`ObdLive`의 시험 요청(엔진·변속기 모드 22 데이터 식별자)은 [OBDb/Hyundai-i30](https://github.com/OBDb/Hyundai-i30)의 신호 정의(CC BY-SA 4.0)를 보고 어떤 식별자를 물어볼지 골랐고, 응답은 원본 그대로 기록합니다. **7DCT 클러치 온도**(변속기 `22 01A4`)는 그 정의의 바이트 위치와 식(16비트, ×2 − 40 °C)을 따라 해석하며, 우리 차의 응답으로 위치와 홀수·짝수 구분을 확인했습니다(`i30pd_port/OBD_PROBE.md`). 타이어 온도 시험 요청(`22 C00B`, `21 06`)은 같은 프로젝트의 형제 차종 정의(OBDb Kia-Ceed 등, CC BY-SA 4.0)를 참고했습니다. 정의 파일 자체는 이 레포에 넣지 않았습니다.

## 택시 요금표와 사업구역 경계

택시 미터기의 시·도별 중형 요금은 전국개인택시운송사업조합연합회의 [시, 도별 개인택시 요금현황](https://gntx.kr/information/information_02_1.php)(2026-11-04에 읽음)의 숫자를 `TaxiTariff`에 옮긴 것입니다. 사업구역 경계(`TaxiZoneData`, `scripts/taxi_zones.py`로 생성)는 통계청 통계지리정보서비스(SGIS, https://sgis.kostat.go.kr)가 공공누리 제1유형으로 개방한 행정구역 경계를 [vuski/admdongkor](https://github.com/vuski/admdongkor)가 가공한 것(CC BY 4.0, `sgg_20261001`)을 시·군 단위로 합치고 200 m로 단순화한 것입니다.

## 서킷 지도

서킷 화면의 국내 서킷 7곳(인제 스피디움, 코리아 인터내셔널 서킷 풀코스·상설, 태백 스피드웨이, AMG 스피드웨이, 포천 레이스웨이, BMW 드라이빙 센터) 트랙 선은 © [OpenStreetMap](https://www.openstreetmap.org/copyright) 기여자 자료이며 ODbL 1.0을 따릅니다. 이 자료로 만든 `shared/src/com/veloster/vehicle/CircuitData.java`(`scripts/circuits.py`로 생성)는 이 레포의 다른 파일과 달리 ODbL로 배포합니다. 인제의 출발선은 2025 슈퍼레이스 6라운드 공식 서킷맵에 표시된 컨트롤라인 위치를 OSM 트랙 위에 옮긴 것이고, 태백·AMG 스피드웨이는 사용자가 보내 준 서킷맵(태백 10번과 1번 사이 직선 가운데, AMG 17번과 1번 사이 직선)의 출발선을, KIC 풀코스는 OSM의 결승선 표시, 나머지는 피트 옆 직선 가운데로 추정한 위치입니다.

## 아케이드 화면의 차 그림

8비트 아케이드 화면의 i30 N 뒷모습(`ArcadeCarFrames`)은 Sketchfab의 3D 모델 [hyundai i30 n](https://sketchfab.com/3d-models/hyundai-i30-n-90c3bd24390b46299cf01c16d5e8091f)(amogusstrikesback2, CC BY 표시)을 13개 각도로 그린 뒤 앱 팔레트의 도트 표(각도마다 81×41칸)로 줄인 것입니다. 모델 파일과 텍스처는 이 레포와 APK에 넣지 않았습니다. 모델 설명에 따르면 게임에서 가져온 모델이라 원래 권리자가 따로 있을 수 있습니다. 권리자가 요청하면 이 그림을 빼겠습니다.

## 이 레포에 넣지 않은 것

- 현대자동차 순정 펌웨어, 순정 앱, 그 디컴파일 결과. 문서의 "PD 펌웨어 대조"는 공개된 펌웨어를 받아 확인한 사실(클래스·액티비티 이름 등)만 적은 것입니다.
- 순정 시스템 앱을 수정·재서명하는 i30 PD용 도구, 펌웨어 업데이트 검증값을 다시 만드는 도구. 루트의 `menu-entry/`는 원작이 배포한 벨로스터 N용 메뉴 연결 도구 그대로이며 i30 PD에서는 확인하지 않았습니다.
- 서명용 개인키. APK는 원작 배포본과 같은 공개 AOSP platform 테스트 인증서로 서명했습니다. 이 인증서는 배포자 신원을 증명하지 않으니, 릴리스의 SHA-256과 받은 파일을 대조하세요.

## 상표

Hyundai, i30, Veloster, N, D-Audio 등은 각 권리자의 상표이며 대상 차량·기기를 설명하려고 썼습니다. 이 프로젝트는 현대자동차의 공식 앱이 아니고 승인받은 제품도 아닙니다. 패키지 이름 `com.veloster.*`는 원작에서 그대로 쓴 것입니다.

## 면책

차량 값은 내비가 받는 데이터를 읽기만 하고 차에 쓰지 않습니다. 그래도 순정 시스템에 앱을 설치하는 일이므로 보증·AS 불이익, 오작동, 그 밖의 결과는 사용자 책임입니다. 표시값은 참고용이며 차량 계기판·경고등을 대신하지 않습니다.
