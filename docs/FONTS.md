# 글꼴 관리 / Font manager

**계기판 → 설정(톱니) → 글꼴**에서 내비 전체의 기본 글꼴(순정 화면·내비·대부분의 앱)을 바꿉니다. 원작 [reikop/veloster-n-dashboard](https://github.com/reikop/veloster-n-dashboard)의 글꼴 관리(MIT)를 i30 PD로 옮긴 것이며, **i30 PD 실차에서는 아직 시험하지 않았습니다**(test 배포만).

## 앱에 포함된 글꼴

원작과 같은 글꼴 10종을 앱에 넣었습니다(파일 합계 약 53 MB, APK 안에서는 약 17 MB). 모두 현대 한글 11,172자를 담고 있고 파일은 고치지 않았습니다(부산단디체·이서윤체는 파일 이름만 영문으로 바꿈).

| 글꼴 | 종류 | 굵기 | 크기 | 만든 곳 · 라이선스 |
| --- | --- | --- | --- | --- |
| Asta Sans (Pleos · 42dot) | 고딕 | Regular·Bold | 4.9 MB | 42dot(현대자동차그룹) · OFL |
| 프리텐다드 (Pretendard) | 고딕 | Regular·Bold | 5.1 MB | 길형진 · OFL |
| 나눔스퀘어 네오 | 고딕 | Regular·Bold | 4.2 MB | 네이버 · OFL |
| 부산단디고딕 | 고딕 | Medium·Bold | 5.2 MB | 부산광역시 · 부산시 전용서체 조건 |
| 부산단디명조 | 명조 | Medium·Bold | 13.4 MB | 부산광역시 · 부산시 전용서체 조건 |
| 나눔손글씨 펜 | 손글씨 | Regular | 3.1 MB | 네이버 · OFL |
| 나눔손글씨 붓 | 손글씨 | Regular | 3.4 MB | 네이버 · OFL |
| 서툰이야기 (Poor Story) | 손글씨 | Regular | 3.8 MB | 윤디자인 · OFL |
| 부산단디손글씨 | 손글씨 | Regular | 8.8 MB | 부산광역시 · 부산시 전용서체 조건 |
| 이서윤체 | 손글씨 | Regular | 1.3 MB | 흥국생명 · 흥국생명 서체 조건 |

부산단디체는 "영상, 인쇄, 출판, 웹, 모바일 등 … 무료로 자유롭게 사용"할 수 있고 판매나 배포 대가 청구는 금지됩니다. 이서윤체는 무료·상업 사용이 가능하고 판매와 수정 후 재배포가 금지됩니다. 두 조건 모두 앱에 함께 넣는 무료 배포를 명시적으로 다루지는 않습니다. 원문은 `apps/dashboard/assets/fonts/LICENSE-*.txt`에, 출처는 [NOTICE.md](../NOTICE.md)에 있습니다.

목록에서 고르면 APK에서 바로 읽어 미리보기를 그립니다(한 번에 하나만 메모리에 둠). 적용할 때만 앱 저장소에 잠시 꺼내 SHA-256을 확인한 뒤 `/system/fonts/VN-*`로 복사하고, 꺼낸 파일은 지웁니다. 설정을 닫으면 목록과 미리보기 글꼴은 메모리에서 내려놓습니다.

## 사용법

1. 목록에는 순정 글꼴, 앱에 포함된 글꼴, 내비 `/system/fonts`에 이미 설치된 추가 글꼴만 나옵니다. USB나 내비 저장소의 글꼴 파일은 목록에 넣지 않습니다. 한 쪽에 6줄, ▲ ▼로 넘깁니다.
2. 줄마다 출처(앱에 포함·내비에 설치됨), 굵기(R·B·L), 한글 범위(한글 전체 = 현대 한글 11,172자)를 표시합니다.
3. 줄을 누르면 오른쪽에 그 글꼴로 미리보기를 그립니다. 내비 자체의 글꼴 엔진으로 그리므로 미리보기가 열리지 않는 글꼴은 적용할 수 없습니다.
4. **선택한 글꼴 적용** → 확인 → **지금 재부팅**. Android 4.2는 시작할 때만 글꼴 설정을 읽으므로 재부팅 후에 바뀝니다. 재부팅은 '내 앱' → 강제 재시작과 같은 Android 재부팅 요청이며, 나중에 '내 앱'에서 재시작해도 됩니다.
5. 처음 설정으로 돌아가려면 목록 맨 위 **순정 글꼴 (Mobis · Roboto)** 을 적용합니다.

## 바꾸기 전에 확인하는 것 (i30 PD)

아래 중 하나라도 맞지 않으면 아무것도 바꾸지 않고, 상태 칸에 이유를 주황색으로 보여 줍니다. 짐작해서 바꾸지 않습니다.

- 내비가 i30 PD(`ro.product.name` = `pd_kr`)인지. 계기판의 CAN·OBD 읽기와 설치기가 보는 것과 같은 값입니다.
- `/system/bin/amossu`가 있고, SHA-256이 확인한 파일(`5933f058…5c25c0`, 2020 V119와 2026 V141 펌웨어 이미지의 것, 둘이 같음)과 같은지. 부팅 화면 도구가 모르는 amossu에서 멈추는 것과 같은 방식입니다.
- 지금의 `/system/etc/system_fonts.xml`이 순정 목록(V119·V141 이미지와 같은 계열·이름·파일, 주석과 공백은 무관)이거나 이 앱이 쓴 것인지. 그래서 **순정 글꼴**을 적용하면 언제나 내비에 원래 있던 목록이 다시 쓰입니다.

## 하는 일

- 순정 루트 도구 `/system/bin/amossu`(setuid root, 받은 인자를 `/system/bin/sh`로 실행)로 스크립트를 돌려 `/system`을 잠시 쓰기 가능하게 하고, 글꼴 파일을 `/system/fonts/VN-<이름>-Regular.ttf` 등으로 복사한 뒤 `/system/etc/system_fonts.xml`을 바꾸고 다시 읽기 전용으로 되돌립니다.
- 바꾸는 계열: `devicedefault`, `sans-serif`, `Droid Sans`, `sans-serif-light/-thin/-condensed`. `serif`, `monospace`, 대체 글꼴(fallback) 목록은 그대로 둡니다. 한글이 모자란 글꼴은 빠진 글자가 기존 대체 글꼴로 보입니다.
- 파일을 모두 복사하고 확인한 다음 XML을 마지막에 바꾸므로 중간에 실패하면 기존 글꼴이 유지됩니다. 적용 후 XML과 복사한 파일(SHA-256)을 다시 읽어 대조합니다.
- 이 앱이 복사한 `VN-*` 파일 중 더 이상 쓰지 않는 것만 지웁니다. 순정 파일이나 다른 방법으로 넣은 글꼴은 지우지 않습니다. 지금 화면에서 쓰는 파일은 재부팅 전까지 남깁니다.
- 처음 바꾸기 전의 XML은 `/data/local/tmp/vn-fonts/original-system_fonts.xml`에, 직전 XML은 `previous-system_fonts.xml`에 보관합니다. 화면이 정상이 아니면 PC에서 아래를 실행한 뒤 재부팅하면 처음 상태로 돌아갑니다.

```sh
adb shell /system/bin/amossu /data/local/tmp/vn-fonts/restore.sh
```

## 한계

- `/system` 남은 공간 안에서만 복사합니다(파일당 40 MB 이하). 부산단디명조는 두 파일 합쳐 13.4 MB입니다. 글꼴 모음(`.ttc`)은 지원하지 않습니다. 가변 글꼴은 기본 굵기 하나만 쓰입니다.
- 자체 글꼴을 내장한 앱(이 계기판의 람다 화면 등)은 바뀌지 않습니다.
- i30 PD 실차 미확인: amossu 루트 실행, 스크립트(Android 4.2 toolbox·mksh), 적용 후 재부팅과 표시 모두 실차에서 아직 해 보지 않았습니다. 확인한 것은 펌웨어 이미지의 파일(amossu·system_fonts.xml·`/system/bin`의 cp·mv·chmod·chown·mount)과 PC의 시험(`tests/SystemFontsTest.java`: 임시 `/system`에 스크립트를 실제로 돌림)까지입니다. 원작(벨로스터 N)에서는 차주가 amossu 루트를 확인했습니다(2026-11-25).

---

**Dashboard → Settings (gear) → Fonts** changes the head unit's system font (factory screens, navigation and most apps). It is the font manager of [reikop/veloster-n-dashboard](https://github.com/reikop/veloster-n-dashboard) (MIT) ported to the i30 PD; **not yet tried on an i30 PD car** (test channel only).

The same ten fonts as upstream ship inside the app (unmodified, all 11,172 Hangul syllables, about 53 MB of files, about 17 MB in the APK): Asta Sans (42dot, Hyundai Motor Group), Pretendard, NanumSquare Neo, Nanum Pen Script, Nanum Brush Script and Poor Story under OFL 1.1; Busan Dandi Gothic, Myeongjo and Script under the Busan city font terms (free use in any medium, no fee for distribution); LeeSeoyun under the Heungkuk Life terms (free use, no sale, no modified redistribution). The non-OFL terms do not expressly cover bundling in an app; they are quoted in `apps/dashboard/assets/fonts/LICENSE-*.txt`. The list shows only the factory set, the in-app fonts and extra fonts already in `/system/fonts`. Pick a row to preview it with the head unit's own font engine, then **Apply selected font** → **Reboot now** (the same Android reboot request as My Apps → Restart head unit); Android 4.2 reads the font settings only at start-up. **Factory fonts (Mobis · Roboto)** at the top puts the original mapping back.

Before anything changes, the PD port checks that the head unit is an i30 PD (`ro.product.name` = `pd_kr`), that `/system/bin/amossu` has the SHA-256 seen in the 2020 V119 and 2026 V141 firmware images (`5933f058…5c25c0`), and that `/system/etc/system_fonts.xml` is either the factory list (same families, names and files as those images) or one this app wrote. Otherwise nothing is changed and the reason shows in the state box.

The change runs as root through the factory `/system/bin/amossu`: the files are copied to `/system/fonts/VN-*`, checked, and `/system/etc/system_fonts.xml` is swapped last (devicedefault, sans-serif, Droid Sans, sans-serif-light/-thin/-condensed; serif, monospace and the fallback chain stay). Both are read back and compared after the change. The XML from before the first change is kept in `/data/local/tmp/vn-fonts/`; `adb shell /system/bin/amossu /data/local/tmp/vn-fonts/restore.sh` and a reboot put it back. Only unused `VN-*` files are ever removed. Font collections (`.ttc`) are not supported; files over 40 MB are skipped. Unverified on an i30 PD: amossu as root, the script on the car's toolbox, the reboot and the font after it.
