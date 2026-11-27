# 출처와 권리

## 원작

이 프로젝트는 [reikop/veloster-n-dashboard](https://github.com/reikop/veloster-n-dashboard)(`2be141b`, MIT, Copyright (c) 2026 Veloster N Dashboard contributors)의 포크이고, 그 위에 i30 PD 포팅을 커밋으로 쌓았습니다. 원작 코드와 원작의 권리 고지([`NOTICE_veloster.md`](NOTICE_veloster.md))는 그대로 두었습니다. Releases의 APK는 이 레포의 소스를 빌드한 것입니다. 두 저작권 표시(원작, i30 PD 포팅 sung9155)는 [`LICENSE`](LICENSE)에 있습니다.

## 출처 표기 부탁

이 레포를 포크하거나 코드 · 화면 · 문서를 가져다 쓸 때는 원작 [reikop/veloster-n-dashboard](https://github.com/reikop/veloster-n-dashboard)와 i30 PD 포팅 [sung9155/i30-pd-dashboard](https://github.com/sung9155/i30-pd-dashboard)를 함께 밝혀 주세요. README, 배포 노트, 앱의 정보 화면처럼 사람들이 보는 곳이면 됩니다. [`LICENSE`](LICENSE)의 두 저작권 표시와 허가 문구는 MIT의 조건이라 사본에 그대로 두어야 합니다.

## 신호 정의

CAN 메시지·신호의 배치와 배율은 [commaai/opendbc](https://github.com/commaai/opendbc)(MIT)의 Hyundai DBC(`hyundai_2015_ccan.dbc`, `hyundai_kia_generic.dbc`)를 참고했고, 실차 로그로 확인했습니다. DBC 파일 자체는 이 레포에 넣지 않았습니다. `i30pd_port/tools/`의 도구는 사용자가 받은 DBC 파일 경로를 인자로 받습니다.

## 진단 요청 (모드 22)

`ObdLive`의 시험 요청(엔진·변속기 모드 22 데이터 식별자)은 [OBDb/Hyundai-i30](https://github.com/OBDb/Hyundai-i30)의 신호 정의(CC BY-SA 4.0)를 보고 어떤 식별자를 물어볼지 골랐고, 응답은 원본 그대로 기록합니다. **7DCT 클러치 온도**(변속기 `22 01A4`)는 그 정의의 바이트 위치와 식(16비트, ×2 − 40 °C)을 따라 해석하며, 우리 차의 응답으로 위치와 홀수·짝수 구분을 확인했습니다(`i30pd_port/OBD_PROBE.md`). 타이어 온도 시험 요청(`22 C00B`, `21 06`)은 같은 프로젝트의 형제 차종 정의(OBDb Kia-Ceed 등, CC BY-SA 4.0)를 참고했습니다. 정의 파일 자체는 이 레포에 넣지 않았습니다.

## 택시 요금표와 사업구역 경계

택시 미터기의 시·도별 중형 요금은 전국개인택시운송사업조합연합회의 [시, 도별 개인택시 요금현황](https://gntx.kr/information/information_02_1.php)(2026-11-04에 읽음)의 숫자를 `TaxiTariff`에 옮긴 것입니다. 사업구역 경계(`TaxiZoneData`, `scripts/taxi_zones.py`로 생성)는 통계청 통계지리정보서비스(SGIS, https://sgis.kostat.go.kr)가 공공누리 제1유형으로 개방한 행정구역 경계를 [vuski/admdongkor](https://github.com/vuski/admdongkor)가 가공한 것(CC BY 4.0, `sgg_20261001`)을 시·군 단위로 합치고 200 m로 단순화한 것입니다.

## 서킷 지도

서킷 화면의 국내 서킷 6곳(인제 스피디움, 코리아 인터내셔널 서킷 풀코스·상설, 태백 스피드웨이, AMG 스피드웨이, 포천 레이스웨이) 트랙 선은 © [OpenStreetMap](https://www.openstreetmap.org/copyright) 기여자 자료이며 ODbL 1.0을 따릅니다. 이 자료로 만든 `shared/src/com/veloster/vehicle/CircuitData.java`(`scripts/circuits.py`로 생성)는 이 레포의 다른 파일과 달리 ODbL로 배포합니다. 인제의 출발선은 2025 슈퍼레이스 6라운드 공식 서킷맵에 표시된 컨트롤라인 위치를 OSM 트랙 위에 옮긴 것이고, 태백·AMG 스피드웨이는 사용자가 보내 준 서킷맵(태백 10번과 1번 사이 직선 가운데, AMG 17번과 1번 사이 직선)의 출발선을, KIC 풀코스는 OSM의 결승선 표시, 나머지는 피트 옆 직선 가운데로 추정한 위치입니다.

## 아케이드 화면의 차 그림

8비트 아케이드 화면의 i30 N 뒷모습(`ArcadeCarFrames`)은 Sketchfab의 3D 모델 [hyundai i30 n](https://sketchfab.com/3d-models/hyundai-i30-n-90c3bd24390b46299cf01c16d5e8091f)(amogusstrikesback2, CC BY 표시)을 13개 각도로 그린 뒤 앱 팔레트의 도트 표(각도마다 81×41칸)로 줄인 것입니다. 이 화면에는 모델 파일과 텍스처를 넣지 않았습니다(내 차 화면이 쓰는 변환본은 아래 "내 차 화면의 3D 모델"). 모델 설명에 따르면 게임에서 가져온 모델이라 원래 권리자가 따로 있을 수 있습니다. 권리자가 요청하면 이 그림을 빼겠습니다.

## 람다 화면 글꼴

람다 화면(Λ 계기)의 글자는 Chakra Petch(Medium·Bold)로 그립니다. Copyright 2018 The Chakra Petch Project Authors (https://github.com/m4rc1e/Chakra-Petch), SIL Open Font License 1.1. 글꼴 파일은 `apps/dashboard/res/raw/`에, 라이선스 전문은 `i30pd_port/licenses/ChakraPetch-OFL.txt`에 있습니다. 글꼴은 고치지 않고 그대로 앱에 넣었습니다.

## 에코 화면의 차 그림

에코 화면 배기구 보기의 차(뒷범퍼 왼쪽 아래)는 3D 모델 "hyundai i30 n"(amogusstrikesback2, https://sketchfab.com/3d-models/hyundai-i30-n-90c3bd24390b46299cf01c16d5e8091f)을 렌더한 그림이며 CC BY 4.0(https://creativecommons.org/licenses/by/4.0/)을 따릅니다. 바꾼 점: 번호판을 숨기고 차체를 회색으로 칠해 낮은 뒤쪽 시점에서 렌더했고, 차체 부분만 따로 떼어 앱이 차량 색을 곱해 입힙니다(`apps/dashboard/res/raw/eco_car_rear.png`, `eco_car_paint.png`). 아케이드 화면과 같은 모델이라 같은 사정(게임에서 가져온 모델일 수 있음)이 있고, 권리자가 요청하면 이 그림을 빼겠습니다.

## 엔진 사운드: 안내·통화 양보

`SoundYield`(안내·통화 양보, 속도 따라 키우기)는 [jihoon-seo/de22ev-ves](https://github.com/jihoon-seo/de22ev-ves)(`0561cae`, MIT, Copyright (c) 2026 Jihoon Seo)의 `AudioPolicy`·`SharedChannel`·`GainEnvelope.speedBoost`를 옮긴 것입니다. 마이컴 리스너 번호와 스트림 종류는 우리 펌웨어(V141)에 맞게 바꿨고, 출력 필터와 EV 엔진 모델은 뺐습니다. 위 MIT 허가 문구가 이 파일들에도 적용됩니다.

## 설정 → 글꼴

글꼴 관리(`FontFile`·`SystemFonts`·`BundledFonts`·`FontManager`, `tests/SystemFontsTest.java`, [`docs/FONTS.md`](docs/FONTS.md))는 원작 [reikop/veloster-n-dashboard](https://github.com/reikop/veloster-n-dashboard)(`3e86fea`·`abccbd4`, MIT)의 것을 옮겼습니다. 화면은 이 레포의 설정 모양으로 다시 그렸고, i30 PD에서만·확인한 amossu와 system_fonts.xml에서만 바꾸도록 막는 검사를 더했습니다.

앱 포함 글꼴(`apps/dashboard/assets/fonts/`, 글꼴 데이터는 고치지 않음, 원작과 같은 파일): SIL Open Font License 1.1(전문은 같은 폴더의 `OFL-*.txt`) — Asta Sans, Copyright 2024 The Asta Sans Project Authors([42dot/Asta-Sans](https://github.com/42dot/Asta-Sans) `fonts/ttf/`, 커밋 `69ecccd`); 프리텐다드, Copyright (c) 2021 Kil Hyung-jin, Reserved Font Name Pretendard([orioncactus/pretendard](https://github.com/orioncactus/pretendard), npm 1.3.9); 나눔스퀘어 네오, Copyright 2022 NAVER Corp.([네이버 글꼴 모음](https://hangeul.naver.com/font) 배포 zip); 나눔손글씨 펜·붓, Copyright (c) 2010 NHN Corporation, Reserved Font Name Nanum 외([google/fonts](https://github.com/google/fonts) `ofl/`); 서툰이야기(Poor Story), Copyright (c) YoonDesign Inc.(google/fonts `ofl/poorstory`). 그 밖의 조건(원문은 `LICENSE-*.txt`) — 부산단디고딕·명조·손글씨, Copyright © Busan Metropolitan City([부산시 누리집](https://www.busan.go.kr/bhbusan), 부산시 전용서체 조건: 무료 사용, 판매·배포 대가 청구 금지); 이서윤체, Copyright (c) 2021 Heungkuk Life Insurance Co., Ltd.([산돌구름](https://www.sandollcloud.com/free-font/17229/LeeSeoyun), 무료·상업 사용 가능, 판매·수정 재배포 금지). 이 두 조건은 앱에 함께 넣는 무료 배포를 명시적으로 다루지 않으며, 파일은 이름만 영문으로 바꿨습니다. 권리자가 요청하면 그 글꼴을 빼겠습니다.

## 네온 드라이브 · 내 차

네온 드라이브(`apps/neondrive/src/`: `DriveSignals`·`DriveModel`·`NeonRenderer`·`NeonView`·`RoadRenderer`·`PixelFont`·`SolarClock`·`Theme`, `tests/NeonDriveTest.java`, `tests/neondrive-preview/`)와 내 차(`apps/dashboard/src/com/veloster/car3d/`, `tests/Car3DTest.java`, `scripts/car3d_model.py`·`scripts/car3d_backdrops.py`)는 원작 [reikop/veloster-n-dashboard](https://github.com/reikop/veloster-n-dashboard)(`7de6571`, MIT)의 것을 옮겼습니다. i30 PD에서는 원작의 벨로스터 N hscand 해석 대신 계기판의 `Telemetry` 값을 쓰고(`PdSignals`), 네온 드라이브의 차는 원작의 AI 생성 벨로스터 N 그림 대신 아케이드 화면의 i30 N(`ArcadeCarFrames`, 위 "아케이드 화면의 차 그림")으로 그리며, 차 색은 계기판의 차량 색을 따릅니다. 따로 실행하는 네온 드라이브 앱은 넣지 않았습니다.

### 내 차 화면의 3D 모델 (CC BY 4.0)

계기판 `내 차` 화면의 차 모델 `apps/dashboard/assets/car3d/`(`i30n.bin`·`lamp_base.png`·`lamp_glow.png`)은 아케이드·에코 화면과 같은 3D 모델을 `scripts/car3d_model.py`로 변환한 2차 저작물입니다.

This work is based on "hyundai i30 n" (https://sketchfab.com/3d-models/hyundai-i30-n-90c3bd24390b46299cf01c16d5e8091f) by amogusstrikesback2 (https://sketchfab.com/amogusstrikesback2) licensed under CC-BY-4.0 (http://creativecommons.org/licenses/by/4.0/)

바꾼 점: 전조등 빛줄기·렌즈 플레어 판·테일램프 그림 판 제거, 앞뒤 번호판을 앱이 그리는 판으로(빈 판 또는 사용자가 넣은 글자, 앞 번호판 가장자리는 그릴 검정), 차체·타이어·휠 텍스처를 정점 색으로 굽기, 도장 면 표시(앱이 차량 색으로 칠함), 바퀴·캘리퍼 분리, 램프 분류(테일램프 렌즈를 잘게 나눔), 방향지시등·미등·보조 제동등 빛 색 바꿈. 램프·크롬 부품 텍스처만 그림으로 남겼습니다. 이 파일들은 MIT가 아니라 CC BY 4.0을 따르며 조건은 같은 폴더의 `LICENSE.txt`에 있고, 화면에도 모델 이름·작성자·라이선스를 표시합니다. 변환 스크립트 자체는 MIT입니다. 아케이드 화면과 같은 사정(게임에서 가져온 모델일 수 있음)이 있어 권리자가 요청하면 빼겠습니다. 비공식 모델이며 현대자동차가 만들거나 승인한 것이 아닙니다. 같은 폴더의 배경 파노라마 `bg_*.jpg`는 `scripts/car3d_backdrops.py`가 절차적으로 그린 것(사진·외부 그림 없음)으로 MIT입니다.

## 이 레포에 넣지 않은 것

- 현대자동차 순정 펌웨어, 순정 앱, 그 디컴파일 결과. 문서의 "PD 펌웨어 대조"는 공개된 펌웨어를 받아 확인한 사실(클래스·액티비티 이름 등)만 적은 것입니다.
- 순정 시스템 앱을 수정·재서명하는 i30 PD용 도구, 펌웨어 업데이트 검증값을 다시 만드는 도구. 루트의 `menu-entry/`는 원작이 배포한 벨로스터 N용 메뉴 연결 도구 그대로이며 i30 PD에서는 확인하지 않았습니다.
- 서명용 개인키. APK는 원작 배포본과 같은 공개 AOSP platform 테스트 인증서로 서명했습니다. 이 인증서는 배포자 신원을 증명하지 않으니, 릴리스의 SHA-256과 받은 파일을 대조하세요.

## 상표

Hyundai, i30, Veloster, N, D-Audio 등은 각 권리자의 상표이며 대상 차량·기기를 설명하려고 썼습니다. 이 프로젝트는 현대자동차의 공식 앱이 아니고 승인받은 제품도 아닙니다. 패키지 이름 `com.veloster.*`는 원작에서 그대로 쓴 것입니다.

## 면책

차량 값은 내비가 받는 데이터를 읽기만 하고 차에 쓰지 않습니다. 그래도 순정 시스템에 앱을 설치하는 일이므로 보증·AS 불이익, 오작동, 그 밖의 결과는 사용자 책임입니다. 표시값은 참고용이며 차량 계기판·경고등을 대신하지 않습니다.
