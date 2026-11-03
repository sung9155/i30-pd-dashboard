# 지도 반 + 계기판 반 — 순정 분할 화면 조사 (노트북 실차 세션용)

목표: 순정의 "내비 반 + 음악 반" 화면처럼, **내비 옆 칸에 계기판**을 띄운다. 덮어 그리는 방식(내비 가운데 내 차 위치가 가려짐)이 아니라, 순정 분할 칸을 써서 내비가 남은 영역에 맞춰 다시 그려지게 하는 것이 목표다.

상태: 순정 framework 정적 분석까지 끝남(2020 이미지). **실차 확인 전.** 실차 소프트웨어는 2026 빌드라 다를 수 있다.

## 1. 순정 framework에서 찾은 구조

| 항목 | 내용 |
|---|---|
| 서비스 기반 클래스 | `android.service.splitscreen.SplitScreenService` (모비스). 앱 서비스가 상속해 `setContentView(view, 2040)`을 부르면 분할 칸 창이 붙는다. `onSplitScreenResumed/Paused` 콜백 |
| 창 종류 | `2040` = 오른쪽 칸(`TYPE_SPLIT_WINDOW_RIGHT`), `2039` = 왼쪽 칸(`TYPE_SPLIT_WINDOW_LEFT`). 창 제목은 `SplitScreenright` / `SplitScreenleft` |
| 칸 위치 (PhoneWindowManager) | 오른쪽 칸 x = 800−480 = **320~800**(폭 480), 왼쪽 칸 x = 0~80(폭 80). 높이는 화면 전체 |
| 내비 영역 | 분할 창이 보이면 일반 앱 영역(`mDockRight`·`mContentRight`)이 칸 경계까지 줄어든다. 그래서 내비가 **남은 칸에 맞춰 다시 그려진다** |
| 자동 숨김 | 맨 위 앱이 전체 화면(`FLAG_FULLSCREEN`)이면 분할 창을 숨긴다 |
| 하나만 | 이미 살아 있는 2040 창이 있으면 새 2040 창은 거절(-7). 순정 음악 칸이 떠 있으면 우리 창은 못 붙는다 |
| 보이기/숨기기 방송 | `com.mobis.intent.action.SPLIT_SCREEN_SHOW` / `..._HIDE` (extra `type` = `left`/`right`, 없으면 둘 다) |
| 권한 | 2039/2040은 `android.permission.INTERNAL_SYSTEM_WINDOW` 필요. 대시보드 앱은 platform 키 서명이라 매니페스트에 선언하면 받을 수 있다 |
| 켜는 조건 | `BaseFeature.FUNCTION_WM_SUPPORT_SPLIT_WINDOW` = 시스템 값 **`ro.splitscreen.enable` == `true`**. 2020 이미지(build.prop·boot ramdisk)에는 이 값이 없음(=꺼짐). 실차에 분할 화면이 실제로 나오므로 2026 빌드에선 켜져 있거나, 내비 앱이 자체 방식으로 그리는 것일 수 있다 |
| 기타 흔적 | RAM 스냅샷에 `ro.device.splitscreen`, 공조·카카오 i·음성 안내 앱의 분할 리소스(`split_bg_screen_title_on` 등). 내비·음악 APK는 분석 이미지에 없음 |

## 2. 실차에서 할 일 (노트북)

준비: `SETUP_laptop.md`대로 adb 연결(`car_session.sh`로 `device` 확인). 시동 ON.

```sh
# 1) 순정 분할 화면(내비 + 음악)을 띄운 상태
bash i30pd_port/split_capture.sh on
# 2) 분할을 끄고 내비만 전체 화면인 상태
bash i30pd_port/split_capture.sh off
# 3) 1)에서 음악 칸 창의 패키지명을 찾았으면, 그 APK와 framework 파일 받기(분석용, 로컬에만 보관)
bash i30pd_port/split_capture.sh pull <패키지명>
```

결과는 `i30pd_port/logs/split/`에 저장된다(`logs/`는 커밋하지 않음). 읽기 전용이라 차 설정은 바뀌지 않는다.

## 3. 결과 읽는 법

1. **`ro.splitscreen.enable=true`가 보이고, 창 목록에 `type=2040`(또는 `ty=2040`, 제목 `SplitScreenright`) 창이 있다**
   → 순정 분할 창 구조를 쓰는 것. 그 창의 `package=`/`mOwnerUid`가 음악 칸을 띄운 앱이다. `mFrame`으로 실제 칸 크기(320~800인지)를 확인한다.
   → `on`과 `off`의 `mDock/mContent` 오른쪽 끝을 비교해, 분할일 때 내비 영역이 실제로 줄어드는지 확인한다.
2. **값이 없거나 `false`이고 2040 창도 없다**
   → 내비 앱이 음악 칸까지 스스로 그리는 것. 이 경우 순정 칸을 쓸 수 없고, 덮어 그리는 방식(가장자리 버튼과 같은 `TYPE_PHONE` 창)만 가능하다.
3. **2040 창은 있는데 값이 `false`**
   → 창은 붙지만 배치가 안 되는 상태일 수 있다. 칸 크기(`mFrame`)를 그대로 기록해 둔다.

## 4. 1번이면 구현 계획

- 대시보드 앱에 `SplitScreenService`를 상속한(또는 같은 일을 하는) 서비스를 두고, 반쪽 계기판 뷰를 창 종류 2040으로 붙인다. 매니페스트에 `INTERNAL_SYSTEM_WINDOW`를 선언한다. compile-stubs에 `SplitScreenService` 스텁이 필요하다.
- 칸 폭 480 px × 480 px에 맞는 반쪽 계기판 레이아웃(RPM·속도·기어·부스트·냉각수 등)을 새로 만든다. 기존 화면은 800×480 기준이다.
- 순정 음악 칸이 이미 2040을 차지하고 있으면 우리 창이 거절되므로, "순정 분할을 끄고 계기판 칸을 켜는" 흐름과 안내 문구가 필요하다. 분할 모드를 켜고 끄는 순정 API(automotive의 `setSplitMode`·`ID_SPLIT_SCREEN`·`SPLIT_MODE_ONOFF_STATUS`)도 `pull`로 받은 파일에서 확인한다.
- 전체 화면 앱이 위에 오면 시스템이 칸을 자동으로 숨기므로 따로 처리할 필요가 없다.

## 5. 결과를 남길 곳

- 위 1~3 중 어느 경우였는지와 칸 크기, 음악 칸 패키지명을 이 문서 맨 아래 "실차 결과"에 적고 커밋한다(로그 원본은 커밋하지 않음).

## 실차 결과

(아직 없음)
