# USB 업데이트 / USB update

## 한국어

컴퓨터나 폰 없이 USB 메모리로 i30 계기판과 '내 앱'을 업데이트합니다. **이 기능이 들어간 '내 앱'(1.19 이상)을 처음 한 번은 설치기로 설치해야** 하고, 그다음 버전부터 USB로 됩니다.

1. `i30-usb-update.zip`을 받습니다. (릴리스에 올라온 경우에만 있습니다. 아래 "개발자용" 참고)
2. 압축을 풀어 나온 **`i30-update` 폴더를 USB 메모리 맨 위**에 넣습니다(FAT32/exFAT). 폴더 안에는 `manifest.txt`, `manifest.sig`, `dashboard.apk`, `hub.apk`가 있어야 합니다.
3. 시동을 켠 차에 USB 메모리를 꽂습니다. 설치된 것보다 새 버전이면 **USB 업데이트** 창이 뜹니다(같은 업데이트는 한 번만 저절로 뜹니다). 뜨지 않으면 **'내 앱' → USB 업데이트**를 누릅니다.
4. **[설치]** → **[지금 설치]**. 계기판이 먼저, '내 앱'이 나중에 바뀌고 앱마다 진행 상태(확인 중 → 설치 중 → 완료)가 보입니다. 그동안 두 앱이 잠시 닫히고, 끝나면 "업데이트 완료" 화면이 뜹니다. 거기서 **[계기판 열기]**를 누르면 됩니다.
5. 창이 "USB에서 i30-update 폴더를 찾지 못했습니다", "이미 최신 버전입니다", "이 USB 업데이트는 쓸 수 없습니다"라고 하면 그 아래 줄에 할 일이 적혀 있습니다.

안전장치
- 두 앱은 누구나 구할 수 있는 AOSP 플랫폼 시험 키로 서명되어 있어, APK 서명만으로는 우리 파일인지 알 수 없습니다. 그래서 `manifest.txt`는 별도의 **업데이트 키**(RSA 2048)로 서명하고, '내 앱'은 이 서명과 각 APK의 크기·SHA-256·서명자·패키지·버전을 모두 확인한 뒤에만 설치합니다. 하나라도 다르면 설치하지 않습니다.
- i30 PD 헤드유닛(`pd_kr`, API 17)용 번들만 받습니다. 원작(벨로스터 N)용 번들은 키와 형식이 달라 받지 않습니다. 다른 앱은 설치할 수 없습니다.
- 새 버전 판정: 이 빌드는 릴리스마다 versionCode가 같고 빌드 이름(`v1.20.3`)만 바뀝니다. 그래서 이름이 둘 다 정식 버전이면 **더 높은 버전일 때만**, 한쪽이 테스트 빌드면 이름이 다르면 보여 줍니다(화면에 현재 → 새 이름이 나옵니다). versionCode가 낮은 것은 받지 않습니다.
- 화면에서 [설치]를 누르기 전에는 아무것도 설치하지 않습니다. USB를 꽂기만 해서 설치되는 일은 없습니다.
- '내 앱'이 설치 권한(`INSTALL_PACKAGES`, 플랫폼 서명 앱에 주어짐)을 받지 못하는 헤드유닛에서는 순정 설치 화면이 앱마다 한 번씩 열립니다. 거기서 [설치]를 누르세요.

확인 범위: 호스트 시험(번들 생성·서명·거부 경우·버전 판정), 안드로이드 4.2(API 17) 에뮬레이터에서 USB 장착 신호 → 창 → 설치 화면 없이 두 앱 교체(계기판은 versionCode가 같고 이름만 다른 경우) → 완료 알림 → 같은 USB를 다시 꽂으면 창 안 뜸. **i30 PD 실차에서는 아직 확인하지 않았습니다**(USB 장착 신호를 앱이 받는지, 설치 권한이 주어지는지, 설치 화면 없이 바뀌는지). 막 설치한 '내 앱'은 한 번 열기 전에는 USB 장착 신호를 받지 못합니다(안드로이드 규칙).

### 개발자용

```
python scripts/usb_update.py --dashboard dashboard-i30pd-platform.apk --hub myapps-i30pd-platform.apk --out i30-usb-update.zip
```

- 서명된 릴리스 APK 두 개에서 번들을 만듭니다. 버전은 APK에서 읽습니다(aapt2).
- 업데이트 키(비밀키)는 저장소에 없습니다. `--key`로 주거나 `KEY_DIR`(release.py가 플랫폼 키를 찾는 폴더)의 `usb-update-key.pem`을 씁니다. 공개키는 `apps/hub/src/com/veloster/carplayhub/usb/UsbUpdateKey.java`에 있습니다. **비밀키를 잃으면 USB 업데이트를 더 만들 수 없으니**(설치기로 새 키가 든 '내 앱'을 다시 설치해야 함) 따로 백업해 두세요.
- `scripts/release.py`는 아직 번들을 만들지 않습니다. 릴리스에 올리려면 위 명령으로 만들어 자산으로 추가합니다.
- 형식: `apps/hub/src/com/veloster/carplayhub/usb/UsbBundle.java`. 원작 벨로스터 N 계기판의 USB 업데이트(upstream bcc6141)를 옮긴 것입니다.

## English

Update the i30 Dashboard and My Apps from a USB stick, without a computer or phone. **My Apps with this feature (1.19 or later) goes in with an installer once**; later versions can come by USB.

1. Get `i30-usb-update.zip` (only when a release carries it; see "For developers").
2. Unzip it and put the **`i30-update` folder at the top of a USB stick** (FAT32/exFAT): `manifest.txt`, `manifest.sig`, `dashboard.apk`, `hub.apk`.
3. Plug the stick into the car with the ignition on. When it holds newer versions than installed, **USB update** opens (once per update); otherwise use **My Apps → USB update**.
4. **Install** → **Install now**. The dashboard is replaced first, My Apps last, each with its step shown; both close for a moment and the screen ends on "Update complete". When there is nothing to install the screen says why and what to do.

Safeguards: `manifest.txt` is signed with a separate **update key** (the APKs carry the public AOSP platform test key, which proves nothing); My Apps checks that signature and every APK's size, SHA-256, signer, package and version, only for this head unit (`pd_kr`, API 17; a Veloster N bundle has another key and format), only the two apps, and only after the tap. This build keeps versionCode between releases and names each build (`v1.20.3`): two release names install only upwards, a test build on either side is offered when the names differ, a lower versionCode never. Without `INSTALL_PACKAGES` it falls back to the system installer screen, one per app.

Checked: host tests and an Android 4.2 (API 17) emulator (mount broadcast → screen → both apps replaced with no installer screen, the dashboard under the same versionCode and another name → done toast → the same stick again opens nothing). **Not yet checked in the car.** A freshly installed My Apps gets no mount broadcast until it has been opened once (Android's rule).

For developers: `scripts/usb_update.py` makes the bundle from the two signed release APKs; the update key's private half is outside the repository (`--key`, or `usb-update-key.pem` in `KEY_DIR`) and must be backed up. `scripts/release.py` does not add the bundle yet. Format: `apps/hub/src/com/veloster/carplayhub/usb/UsbBundle.java`. Ported from the Veloster N dashboard's USB update (upstream bcc6141).
