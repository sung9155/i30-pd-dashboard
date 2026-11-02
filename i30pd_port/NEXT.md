# 이어서 할 일 (handoff)

다른 PC에서 이어가기: `git clone https://github.com/sung9155/i30-pd-dashboard && cd i30-pd-dashboard && git checkout beta`.
커밋 author는 `sung9155 <12976500+sung9155@users.noreply.github.com>`. 빌드/서명은 `i30pd_port/app/README.md` "재현" 절, 프리뷰는 `bash i30pd_port/app/preview/preview.sh .`, 테스트는
`python -X utf8 scripts/test.py --java "<jdk>/bin/java"` + `python -X utf8 -m unittest discover -s tests -p 'test_*.py'`.

## 남은 작업

1. **추격(8비트 chase) 바퀴 굴러가는 모습** — 현재 뒤차(`PixelCluster.rearArt`)의 바퀴가 정지 상태. 속도(`v(7)`)에 따라 바퀴가 구르는/도는 모습이 보이게.
   사이드뷰(`sideCarArt`)는 `spin`으로 바퀴를 굴린다(스포크 6단계). 후면 바퀴에도 비슷하게 — 스크롤/속도에 맞춰 허브캡 스포크나 타이어 패턴이 돌게. `rearCar[key]` 캐시 키에 바퀴 위상(몇 단계) 추가해 베이크.

2. **G 레이아웃(마찰원 / G circle) 조향 방향 반대** — 실차에서 핸들 방향이 화면과 반대로 나온다(2026-11-03 확인). `GCircleCluster.java`의 조향각 줄:
   `bar(c,258,t("조향각"),... steer/360 ..., (steer>0?t("왼쪽 "):steer<0?t("오른쪽 ")) ...)` 에서 좌/우가 뒤집힘.
   원인: 추격 도로도 `-steer`로 뒤집어야 맞았듯(커밋 `24fcfdd`), **SAS11 조향 부호가 디코드 주석의 "좌+"와 반대(실제 우+)**로 보인다. 디코드(`Telemetry`)는 건드리지 말고(다른 소비자·추격이 이미 그 값에 맞춰 튜닝됨) 이 레이아웃에서만 방향을 뒤집기: 라벨 좌/우 교체 + 막대값 `-steer/360`.
   **`HudCluster.java`의 `STR L/R`(약 54줄)도 같은 패턴** — 같이 뒤집혔을 가능성 높으니 실차에서 확인 후 함께 수정.

3. **레트로·랠리·80년대(VFD)도 테마 적용** — 기본 N일 때는 지금 고유색(레트로 주황 LCD / 랠리 흰 네모 / 80년대 앰버 VFD) 그대로 두고, **다른 테마로 바꾸면 액센트 색으로 바뀌게**.
   주의: `themed=true`만으론 안 됨 — `Theme.map()`은 N에서 identity, 다른 테마에서도 **팔레트에 없는 고유색은 그냥 통과**시켜서 색이 안 바뀐다(야간/엔진/워프는 액센트를 팔레트색 `0xffa4d9ef`로 바꿔 해결했지만, 그 방식은 N 모습도 파랑으로 바꿔버려 여기선 못 씀 — 고유색을 지켜야 하니까).
   방법: **N이면 고유색, 아니면 `Theme.current().accent`** 를 돌려주는 헬퍼를 각 레이아웃의 메인/액센트 색에 적용. 예) `Theme`에 `public boolean isN(){return keys==null;}` 추가(또는 `"n".equals(id)`), 레이아웃에서 `isN()?신호색:Theme.current().accent`.
   대상: `VfdCluster`(ClusterKit), `ClusterRenderer.retro()`·`rally()`(여긴 자체 `T()` 씀). 레트로/랠리는 ClusterRenderer 메서드라 거기 헬퍼를 두면 된다.

4. **레이아웃 기본 순서 그룹화 + 기본 화면 = 레이스** — 지금 기본 표시 순서는 `ClusterPicker.naturalOrder()`(NAMES 순서 그대로)이고 기본 시작 화면은 전체조망.
   - **기본 순서**: 연관된 것끼리 묶기. `NAMES` 인덱스는 그대로 두고(다른 코드가 인덱스에 의존) **`ClusterPicker.naturalOrder()`가 큐레이트된 순열을 돌려주게** 바꾸면 됨(저장된 순서 없을 때의 기본값). 묶음 예시: 데이터계(전체조망·게이지·그래프) / 레이스·주행계(레이스·F1·퍼포먼스·기록·쇼) / 레트로계(클래식·레트로·랠리·80년대·택시·카세트) / 테크·스포츠계(HUD·G원·야간·드래그·엔진·워프) / 게임계(8비트·추격). 최종 묶음·순서는 구현자가 정리.
   - **기본 시작 화면 = 레이스(screen index 5)**: `VehicleDash` 로드부 `p.getInt("cluster_screen",ClusterPicker.OVERVIEW)` 의 기본값을 레이스로. 깔끔하게 `ClusterPicker`에 `RACE=F1+2`(=5) 상수 추가해 쓰면 좋다. (SPORT 모드 기본도 이미 `F1+2`=레이스.)
   - 주의: 저장된 `cluster_order`/`cluster_screen` 가 있으면 사용자 설정 우선(기존 동작 유지). 기본값만 바꾸는 것.

## 이번 세션에 끝낸 것 (beta)
- `fdffe10` 능동 OBD 폴러(ObdLive): 외기온·연료잔량·촉매온도·절대부하 — 2026-11-03 실차 검증(게이지 실시간 표시, 값 정상)
- `f2623f7` 추격 차량 커브 각도(yaw)
- `09f827c` 야간·엔진·워프 테마 액센트 적용 (+프리뷰 라벨 수정)
- `c5bcaa9` 레이아웃 선택 설정 = 리스트 + ▲▼ 순서변경 + 라이브 미니 썸네일
- `dda16b9` 에코 화면 삭제 (오실로·노킹도 앞서 삭제)
- `24fcfdd` 추격 도로 방향 수정, `9d3b677` 플로팅 버튼 투명도 0.2

## 참고
- 차: `pd_kr`. 재부팅마다 USB 디버깅 꺼짐 → 엔지니어모드→Factory Test `0A 01`→`65 01`→`0A 02` 재진입.
- OBD 폴러는 `com.hkmc.micom.hscanmicomservice` 채널 공유(DtcReader와). 진단 조회 중엔 `ObdLive.diagBusy`로 양보.
- 능동 OBD 프로브(스크래치용): `com.i30.diag.UsefulPids`/`DidScan`/`DiagProbe` — 소스는 세션 스크래치패드, 재작성 시 `DtcReader` 메커니즘 참고.
