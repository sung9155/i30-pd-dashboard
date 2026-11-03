# 이어서 할 일 (handoff)

다른 PC에서 이어가기: `git clone https://github.com/sung9155/i30-pd-dashboard && cd i30-pd-dashboard && git checkout beta`.
커밋 author는 `sung9155 <12976500+sung9155@users.noreply.github.com>`. 빌드/서명은 `i30pd_port/app/README.md` "재현" 절, 프리뷰는 `bash i30pd_port/app/preview/preview.sh .`, 테스트는
`python -X utf8 scripts/test.py --java "<jdk>/bin/java"` + `python -X utf8 -m unittest discover -s tests -p 'test_*.py'`.

## 남은 작업

1. **추격(8비트 chase) 바퀴 굴러가는 모습** — 현재 뒤차(`PixelCluster.rearArt`)의 바퀴가 정지 상태. 속도(`v(7)`)에 따라 바퀴가 구르는/도는 모습이 보이게.
   사이드뷰(`sideCarArt`)는 `spin`으로 바퀴를 굴린다(스포크 6단계). 후면 바퀴에도 비슷하게 — 스크롤/속도에 맞춰 허브캡 스포크나 타이어 패턴이 돌게. `rearCar[key]` 캐시 키에 바퀴 위상(몇 단계) 추가해 베이크.

2. ~~**G 레이아웃(마찰원 / G circle) 조향 방향 반대**~~ — 2026-11-03 끝냄. 화면 값 `v(STEERING)`은 이미 `Telemetry.displayValue`로 부호가 뒤집혀 **오른쪽 +**다. G원 조향각 줄과 `HudCluster` `STR`의 좌/우 글자만 교체(막대는 +가 오른쪽이라 그대로).

3. **레트로·랠리·80년대(VFD)도 테마 적용** — 기본 N일 때는 지금 고유색(레트로 주황 LCD / 랠리 흰 네모 / 80년대 앰버 VFD) 그대로 두고, **다른 테마로 바꾸면 액센트 색으로 바뀌게**.
   주의: `themed=true`만으론 안 됨 — `Theme.map()`은 N에서 identity, 다른 테마에서도 **팔레트에 없는 고유색은 그냥 통과**시켜서 색이 안 바뀐다(야간/엔진/워프는 액센트를 팔레트색 `0xffa4d9ef`로 바꿔 해결했지만, 그 방식은 N 모습도 파랑으로 바꿔버려 여기선 못 씀 — 고유색을 지켜야 하니까).
   방법: **N이면 고유색, 아니면 `Theme.current().accent`** 를 돌려주는 헬퍼를 각 레이아웃의 메인/액센트 색에 적용. 예) `Theme`에 `public boolean isN(){return keys==null;}` 추가(또는 `"n".equals(id)`), 레이아웃에서 `isN()?신호색:Theme.current().accent`.
   대상: `VfdCluster`(ClusterKit), `ClusterRenderer.retro()`·`rally()`(여긴 자체 `T()` 씀). 레트로/랠리는 ClusterRenderer 메서드라 거기 헬퍼를 두면 된다.

4. **레이아웃 기본 순서 그룹화 + 기본 화면 = 레이스** — 지금 기본 표시 순서는 `ClusterPicker.naturalOrder()`(NAMES 순서 그대로)이고 기본 시작 화면은 전체조망.
   - **기본 순서**: 연관된 것끼리 묶기. `NAMES` 인덱스는 그대로 두고(다른 코드가 인덱스에 의존) **`ClusterPicker.naturalOrder()`가 큐레이트된 순열을 돌려주게** 바꾸면 됨(저장된 순서 없을 때의 기본값). 묶음 예시: 데이터계(전체조망·게이지·그래프) / 레이스·주행계(레이스·F1·퍼포먼스·기록·쇼) / 레트로계(클래식·레트로·랠리·80년대·택시·카세트) / 테크·스포츠계(HUD·G원·야간·드래그·엔진·워프) / 게임계(8비트·추격). 최종 묶음·순서는 구현자가 정리.
   - **기본 시작 화면 = 레이스(screen index 5)**: `VehicleDash` 로드부 `p.getInt("cluster_screen",ClusterPicker.OVERVIEW)` 의 기본값을 레이스로. 깔끔하게 `ClusterPicker`에 `RACE=F1+2`(=5) 상수 추가해 쓰면 좋다. (SPORT 모드 기본도 이미 `F1+2`=레이스.)
   - 주의: 저장된 `cluster_order`/`cluster_screen` 가 있으면 사용자 설정 우선(기존 동작 유지). 기본값만 바꾸는 것.

5. ~~**RED 테마 경고 램프 테두리 흰색 → 테마색**~~ — 2026-11-03 끝냄(아래 수정 그대로 적용, `ThemeShapes.ring`). — RED 테마 클래식에서 PARK(상단 띠)·PARK BRAKE(하단 경고줄)에 켜질 때 **흰색 테두리**가 생겨 빨간 팔레트와 안 어울림.
   - 현상: 켜진 경고 램프 = danger색(RED 테마는 노랑 `0xffffd21f`) 채움 + 흰색 2px 링. 흰 링이 빨강 테마에서 튄다.
   - 원인: `ThemeShapes.ring()`(shared/.../vehicle/ThemeShapes.java:31)이 링 색을 `0xffffffff`로 하드코딩. 링은 `Theme.dangerRing`인 테마에서만 그림 = amber·red 둘뿐(Theme.java:56,64). amber는 흰 링이 맞지만(앰버 UI에서 danger와 accent가 가까워 흰색으로 구분), red는 danger가 노랑이라 흰 링이 불필요하게 튄다. 주석(Theme.java:62)이 "red는 amber의 링을 유지"라 **삭제 말고 색만 교체**가 의도에 맞음.
   - 적용할 수정(ThemeShapes.java, 검증 완료 — 로컬에선 되돌려 둠, 다른 PC에서 적용):
     ```java
     /**
      * 2px ring that marks a warning when the danger colour is close to the accent. Amber frames it in white;
      * red's danger lamp is yellow on a red field, where a white ring clashes, so red frames it in the accent red.
      */
     static void ring(Canvas c,Paint p,float l,float t,float rr,float b){
         Theme th=Theme.current();
         if(!th.dangerRing)return;
         int col="red".equals(th.id)?th.accent:0xffffffff;
         p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);p.setColor(col);c.drawRect(l+2,t+2,rr-2,b-2,p);p.setStyle(Paint.Style.FILL);
     }
     ```
     RED accent = `0xffff3b30`(Theme.java:64). 같은 `ring()`을 PARK(상단)·PARK BRAKE(하단)·기타 danger 램프가 공유하므로 한 곳 고치면 전부 적용.
   - 검증: 프리뷰로 RED+클래식+PARK_BRAKE 강제 점등 렌더 → 노랑 램프에 **빨간 링**(흰색 아님) 확인. 테스트 영향 없음(ThemeShapes 단독, 상수/카운트 안 건드림). 적용 후 `preview.sh`로 재확인 + 실차 1회 확인 권장.
   - 미결: amber 테마는 그대로 흰 링 유지. 다른 danger색 테마(없음)는 해당 없음.

6. **지도 반 + 계기판 반 (순정 분할 칸)** — 실차 확인 필요. 순정 "내비 반 + 음악 반" 화면이 framework의 분할 창(창 종류 2040, 오른쪽 480 px)을 쓰는지 노트북에서 확인한다: `bash i30pd_port/split_capture.sh on` → `off` → (필요하면) `pull <패키지명>`. 배경·읽는 법·구현 계획은 [`SPLIT_SCREEN.md`](SPLIT_SCREEN.md). 결과는 그 문서 맨 아래 "실차 결과"에 적는다.

## 이번 세션에 끝낸 것 (beta)
- 엔진사운드 ON 시 심한 UI 렉 해결 — 2026-11-03 실차 검증(렉 사라짐). 원인은 per-frame 렌더 비용 + 오디오 과부하 합쳐진 것:
  - `e300012` 숫자 표시에서 per-call 정규식 제거, `e969711` 배경·헤더 스트립 캐시 비트맵화 (매 프레임 재그리기 제거) — 이 둘이 체감 대부분
  - `b8ad9b3` 엔진 합성 경량화: RATE 22050→11025, `Math.tanh`→softclip, audio 스레드 우선순위 AUDIO(-16)→DEFAULT, `AudioTrack.write`<0 시 정지
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
