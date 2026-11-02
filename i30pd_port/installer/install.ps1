<#
  i30 PD 대시보드 설치 — PowerShell. 사용자는 "설치.cmd"를 더블클릭한다(이 파일을 직접 열 필요 없음).
  INSTALL.md 3~5단계(연결 확인 · 설치 · '내 앱' 실행)를 대신 한다. 1~2단계(엔지니어 모드 · Factory Test)는
  차 화면에서 사람이 해야 하므로 안내만 한다. 차(product:pd_kr)가 아닌 기기에는 아무것도 하지 않는다.

  무서명 .exe 가 SmartScreen/백신에 막히던 문제를 없애려고 배치 + PowerShell 로 바꿨다. powershell.exe 는
  Microsoft 서명본이라 SmartScreen 평판 차단이 없고, .cmd 는 파란 차단창 없이 노란 경고 한 번만 뜬다.

  버전: 시작할 때 안정화(최신 정식 릴리스, 실차 확인) / 테스트('test' 사전 배포, 실차 검증 전) 중 고른다.
  테스트 인자: -AdbPath <adb 경로>  -Channel stable|test  -Yes(확인 모두 예, 대기/프롬프트 생략, 안정화)  -Uninstall(두 앱 삭제)
#>
param([string]$AdbPath, [string]$ApkBase, [string]$Channel, [switch]$Yes, [switch]$Uninstall)

# 'Continue' (not 'Stop'): adb writes its progress to stderr, and under 'Stop' a native
# command's stderr turns into a terminating error (NativeCommandError) that aborts the
# script mid-install. Cmdlets that need try/catch get -ErrorAction Stop explicitly.
$ErrorActionPreference = 'Continue'
try { [Console]::OutputEncoding = [Text.Encoding]::UTF8 } catch {}
$ROOT = Split-Path -Parent $MyInvocation.MyCommand.Path
$InstallerVersion = '2026-11-01b'   # 화면 맨 위에 찍혀, 스크린샷만으로 옛 설치기인지 알 수 있다. 고칠 때마다 올린다.
$VerifiedBuild = 'eng.vmadmin.20260114.084130'   # 블루링크 D-Audio Plus(HYUNDAIA) — 실차 설치 확인
# 비블루링크 D-Audio Plus LOW(HYUNDAILOWI, device daudiopluslow_*): 2026-01-14 펌웨어 비교상 USB 설정·차량 데이터 데몬이
# 위와 같고, 2026-10-31 실차 설치·동작 확인(다른 LOW 차 한 대는 USB "장치 설명자 요청 실패" — 그 차 포트·배선 문제로 봄).
$LowBuild = 'eng.vmadmin.20260114.084725'
$PT_URL = 'https://dl.google.com/android/repository/platform-tools-latest-windows.zip'
$USB_URL = 'https://dl.google.com/android/repository/usb_driver_r13-windows.zip'
# APK를 폴더에서 못 찾으면 공개 배포 레포에서 받는다: 안정화 = 최신 정식 릴리스, 테스트 = 'test' 사전 배포(덮어쓰며 갱신).
$Releases = 'https://github.com/sung9155/i30-pd-dashboard/releases'
$Apps = @(
  [pscustomobject]@{ apk='dashboard-i30pd-platform.apk'; pkg='com.veloster.dashboard'; name='대시보드' },
  [pscustomobject]@{ apk='myapps-i30pd-platform.apk';   pkg='com.veloster.carplayhub'; name="'내 앱'" }
)
$script:adbExe = $null
$LogDir = Join-Path $env:LOCALAPPDATA 'i30pd-installer'
New-Item -ItemType Directory -Force -Path $LogDir | Out-Null
$Log = Join-Path $LogDir ("install_{0}.log" -f (Get-Date -Format yyyyMMdd_HHmmss))

function Log($m){ Add-Content -LiteralPath $Log -Value $m }   # 기록 파일에만 (문제 제보용)
function Note($m,[string]$c='Gray'){ Write-Host $m -ForegroundColor $c; Log $m }
function Head($m){ $t = Get-Date -Format HH:mm:ss; Write-Host ''; Write-Host "$m   [$t]" -ForegroundColor Cyan; Log "== $m == $t" }
function Die($m,$code){
  Write-Host ''; Write-Host $m -ForegroundColor Red; Log "ERROR: $m"
  Write-Host "기록 파일: $Log" -ForegroundColor Yellow
  Write-Host '문제를 알릴 때 이 파일을 Issues(github.com/sung9155/i30-pd-dashboard/issues)에 올려 주세요.' -ForegroundColor Yellow
  if(-not $Yes){ try { Start-Process explorer.exe "/select,`"$Log`"" } catch {} }
  exit $code
}
function UsbOffNote(){
  Note 'USB 디버깅 끄기: 시동을 껐다 켜면 저절로 꺼집니다. 바로 끄려면 Factory Test에서  0A 01 → 65 00 → 0A 02' Gray
  Note "  (또는 '내 앱' → USB 디버깅 → OFF). 끄면 노트북 연결이 끊깁니다." Gray
}
function Ask($m){ if($Yes){ return $true }; $r = Read-Host "$m [Y/n]"; return ($r -eq '' -or $r -match '^[Yy]') }
function Adb([Parameter(ValueFromRemainingArguments=$true)]$a){
  $o = & $script:adbExe @a 2>&1 | Out-String
  Add-Content -LiteralPath $Log -Value ("> adb " + ($a -join ' ') + "`n" + $o)
  return $o
}
# 오래 걸릴 수 있는 adb(전송·설치)는 제한 시간 안에서 돌리고, 기다리는 동안 경과 초를 같은 줄에 찍는다:
# 멈춘 화면의 스크린샷만 봐도 어느 단계에서 몇 초째 멈췄는지 보이게. 시간 초과면 $null.
function AdbWait([int]$sec,[string]$what,[string[]]$a){
  $out = Join-Path $LogDir 'adb_out.txt'; $err = Join-Path $LogDir 'adb_err.txt'
  $line = ($a | ForEach-Object { if($_ -match '\s'){ '"' + $_ + '"' } else { $_ } }) -join ' '
  Log "> adb $line  (최대 ${sec}초)"
  $p = Start-Process -FilePath $script:adbExe -ArgumentList $line -NoNewWindow -PassThru -RedirectStandardOutput $out -RedirectStandardError $err
  Write-Host "  $what " -NoNewline
  $t0 = Get-Date; $shown = 0
  while(-not $p.WaitForExit(1000)){
    $s = [int]((Get-Date) - $t0).TotalSeconds
    if($s -ge $shown + 5){ $shown = $s - $s % 5; Write-Host "${shown}s " -NoNewline -ForegroundColor DarkGray }
    if($s -ge $sec){
      & taskkill.exe /T /F /PID $p.Id *> $null   # 하위 프로세스까지 끝내야 창이 바로 닫힌다
      Write-Host "멈춤(${sec}초)" -ForegroundColor Red; Log "  → ${sec}초 넘게 응답 없음, 중단"; return $null
    }
  }
  $o = [string](Get-Content -Raw $out -ErrorAction SilentlyContinue) + [string](Get-Content -Raw $err -ErrorAction SilentlyContinue)
  $s = [math]::Round(((Get-Date) - $t0).TotalSeconds, 1)
  Write-Host "(${s}초)" -ForegroundColor DarkGray
  Log ("$o  (${s}초)")
  return $o
}
# 전송·설치가 멈췄을 때: 멈춘 곳과 할 일, 내비의 설치 로그 끝부분을 화면에 띄우고 끝낸다.
function Stuck($where,$hint){
  Note "멈춘 곳: $where" Red
  Note "  $hint" Yellow
  $lc = AdbWait 15 '내비 설치 로그 읽는 중' @('-s',$serial,'logcat','-d','-t','300','-s','PackageManager:*','PackageParser:*','installd:*','DefContainerService:*','dalvikvm:E')
  if($lc){ Note '  설치 로그(마지막 줄):' DarkGray; ($lc -split "`r?`n" | Where-Object { $_.Trim() } | Select-Object -Last 12) | ForEach-Object { Note "    $_" DarkGray } }
  Die "$where 단계에서 멈췄습니다. 이 화면을 찍어 보내 주시면 원인을 볼 수 있습니다." 1
}

Write-Host ''
Write-Host $(if($Uninstall){ "  i30 PD 대시보드 삭제   (설치기 $InstallerVersion)" } else { "  i30 PD 대시보드 설치   (설치기 $InstallerVersion)" }) -ForegroundColor White
Write-Host '  ----------------------' -ForegroundColor DarkGray
Note '현대자동차 공식 앱이 아닙니다. 순정 시스템에 앱을 설치하는 작업이라 보증/AS 불이익이 있을 수 있고,' Yellow
Note '책임은 본인에게 있습니다. 모든 작업은 정차 · 시동 켠 상태에서 하세요.' Yellow
Note "기록: $Log" DarkGray

# --- adb 찾기/받기 ------------------------------------------------------------
Head '1) adb 준비'
if($AdbPath){ $script:adbExe = $AdbPath }
if(-not $script:adbExe){
  foreach($c in @((Join-Path $ROOT 'platform-tools\adb.exe'), (Join-Path $ROOT 'adb.exe'))){
    if(Test-Path $c){ $script:adbExe = $c; break }
  }
}
if(-not $script:adbExe){
  $w = Get-Command adb.exe -ErrorAction SilentlyContinue
  if($w){ $script:adbExe = $w.Source }
}
if(-not $script:adbExe){
  Note 'adb(platform-tools)가 없습니다.' Gray
  if(-not (Ask 'Google 공식 주소에서 받을까요?')){ Die 'adb가 없어 중단합니다.' 2 }
  $zip = Join-Path $LogDir 'platform-tools.zip'
  Note '내려받는 중...' Gray
  try { Invoke-WebRequest -Uri $PT_URL -OutFile $zip -UseBasicParsing -ErrorAction Stop } catch { Die "다운로드 실패: $($_.Exception.Message)" 2 }
  try { Expand-Archive -LiteralPath $zip -DestinationPath $ROOT -Force -ErrorAction Stop } catch { Die "압축 해제 실패: $($_.Exception.Message)" 2 }
  $script:adbExe = Join-Path $ROOT 'platform-tools\adb.exe'
  if(-not (Test-Path $script:adbExe)){ Die 'adb를 준비하지 못했습니다.' 2 }
}
Note "adb: $script:adbExe" DarkGray
Log ("환경: Windows $([Environment]::OSVersion.Version) / PowerShell $($PSVersionTable.PSVersion) / " + ((& $script:adbExe version 2>&1 | Select-Object -First 1) -join ''))

# --- 버전 고르기 (-ApkBase 를 주면 그 주소를 그대로 쓴다) ---------------------
if(-not $Uninstall -and -not $ApkBase){
  if($Channel -ne 'stable' -and $Channel -ne 'test'){
    if($Yes){ $Channel = 'stable' } else {
      Head '받을 버전'
      Note '  1) 안정화 (권장) — 실차에서 확인된 버전' Gray
      Note '  2) 테스트 — 새 기능, 아직 실차 검증 전. 문제가 있으면 안정화로 다시 설치하면 됩니다' Gray
      $r = Read-Host '번호를 입력하고 Enter [1]'; $Channel = $(if($r.Trim() -eq '2'){ 'test' } else { 'stable' })
    }
  }
  $ApkBase = $(if($Channel -eq 'test'){ "$Releases/download/test" } else { "$Releases/latest/download" })
  if($Channel -eq 'test'){ Note '버전: 테스트 (실차 검증 전)' Yellow } else { Note '버전: 안정화' Green }
}

# --- APK 준비 (폴더에 있으면 쓰고, 없으면 공개 레포에서 받기) -----------------
if(-not $Uninstall){ Head '0) 앱 파일 준비' }
foreach($app in $(if($Uninstall){ @() } else { $Apps })){
  $local = Join-Path $ROOT $app.apk
  # 브라우저로 받은 GitHub 페이지(HTML)가 .apk 이름으로 저장된 경우가 있어, 'PK'로 시작할 때만 폴더 것을 쓴다.
  $ok=$false; if(Test-Path $local){ $fs=[IO.File]::OpenRead($local); $mg=New-Object byte[] 2; [void]$fs.Read($mg,0,2); $fs.Close(); $ok=($mg[0] -eq 0x50 -and $mg[1] -eq 0x4B) }
  if((Test-Path $local) -and -not $ok){ Note "$($app.apk): 폴더 파일이 APK가 아니라 무시하고 내려받습니다." Yellow }
  if($ok){ $app | Add-Member -NotePropertyName path -NotePropertyValue $local -Force; Note "$($app.apk): 설치기 폴더에 있는 파일을 씁니다 (위에서 고른 버전과 상관없이 이 파일이 설치됩니다)" Yellow; continue }
  $url = "$ApkBase/$($app.apk)"; $dst = Join-Path $LogDir $app.apk
  Note "$($app.apk): 폴더에 없어 내려받습니다..." Gray
  try { Invoke-WebRequest -Uri $url -OutFile $dst -UseBasicParsing -ErrorAction Stop } catch {
    Die ("APK 다운로드 실패: $($app.apk)`n" +
         "레포가 비공개면 받을 수 없습니다. 레포를 공개로 바꾸거나, APK를 설치.cmd 옆에 두거나, -ApkBase <공개 URL> 로 지정하세요.`n" +
         "$url`n$($_.Exception.Message)") 2
  }
  # APK(=ZIP)는 'PK'(0x50 0x4B)로 시작. 404 안내문 등이 저장된 경우를 거른다.
  $fs=[IO.File]::OpenRead($dst); $magic=New-Object byte[] 2; [void]$fs.Read($magic,0,2); $fs.Close()
  if($magic[0] -ne 0x50 -or $magic[1] -ne 0x4B){ Die "받은 파일이 APK가 아닙니다($($app.apk)). 공개 여부/URL 확인: $url" 2 }
  $app | Add-Member -NotePropertyName path -NotePropertyValue $dst -Force
  Note ("$($app.apk): 받음 ({0} KB)" -f [math]::Round((Get-Item $dst).Length/1KB)) DarkGray
}

# --- 차 화면 안내 + 연결 대기 -------------------------------------------------
Head '2) 차 화면에서 먼저 (사람이 직접)'
Note '  (1) 설정 → 시스템 정보 → 저장 공간 화면' Gray
Note '  (2) 내용 영역 좌하단 모서리 5회 탭 → 우하단 모서리 1회 탭 (엔지니어 모드)' Gray
Note '  (3) 암호 입력 → Diagnosis → Factory Test 에서  0A 01 → 65 01 → 0A 02  전송' Gray
Note '  (4) USB 케이블 연결' Gray
if(-not $Yes){ Read-Host '위 단계가 끝나고 USB를 연결했으면 Enter' | Out-Null }

Head '3) 차 연결 확인'
& $script:adbExe start-server *> $null

# adb에 안 보일 때 Windows 장치 목록으로 원인을 가른다. 차는 USB 디버깅을 켜면 Google VID 18D1로 잡힌다(검증 차:
# PID D00D = adb 단독; init.rc상 mass_storage,adb는 DEED, accessory,adb는 2D01 — 블루링크 없는 모델 등 기기가 다르면
# 다를 수 있다). Google USB 드라이버 INF에 이 PID들이 없어 드라이버가 자동으로 안 붙는다("알 수 없는 장치").
# VID_0000&PID_0002는 "장치 설명자 요청 실패"(코드 43) = USB 연결 자체가 안 된 것이라 드라이버로는 못 고친다.
# 오류 상태인 USB 장치 ID는 화면·기록에 남겨, 모르는 기기라도 사용자가 그대로 알려 줄 수 있게 한다.
# 기록 파일에는 USB 장치 목록 전체를 바뀔 때마다 시각과 함께 남긴다(케이블을 꽂는 순간 무엇이 나타났는지 보이게).
$script:badIds = ''; $script:usbLast = ''
function UsbProblem(){
  $usbAll = @(Get-CimInstance Win32_PnPEntity -ErrorAction SilentlyContinue | Where-Object { $_.PNPDeviceID -match '^USB\\' } | Sort-Object PNPDeviceID)
  $txt = ($usbAll | ForEach-Object { "$($_.PNPDeviceID) [$($_.Name)] $($_.Status) 코드 $($_.ConfigManagerErrorCode)" }) -join "`n  "
  if($txt -ne $script:usbLast){ Log ("[{0:HH:mm:ss}] USB 장치 목록:`n  {1}" -f (Get-Date), $txt); $script:usbLast = $txt }
  $bad = @($usbAll | Where-Object { $_.ConfigManagerErrorCode -ne 0 })
  $script:badIds = ($bad | ForEach-Object { "$($_.PNPDeviceID) [$($_.Name)] 코드 $($_.ConfigManagerErrorCode)" }) -join "`n  "
  if(@($bad | Where-Object { $_.PNPDeviceID -match 'VID_18D1&PID_' }).Count){ return 'driver' }
  if(@($bad | Where-Object { $_.PNPDeviceID -match 'VID_0000&PID_0002' }).Count){ return 'cable' }
  if($bad.Count){ return 'other' }
  return $null
}
function DriverHelp(){
  Note '→ 차는 연결됐지만 Windows에 드라이버가 없습니다(장치 관리자에 "알 수 없는 장치"/"DiPO"/"Telechips").' Yellow
  Note "  오류 장치: $script:badIds" DarkGray
  Note '  Google 드라이버 목록에 이 장치 번호가 없어 자동으로 안 잡힙니다. 한 번만 직접 지정하면 됩니다:' Yellow
  Note '  장치 관리자 → 그 장치 우클릭 → 드라이버 업데이트 → 내 컴퓨터에서 드라이버 찾아보기' Gray
  Note '  → 컴퓨터의 사용 가능한 드라이버 목록에서 직접 선택 → (모든 장치 표시) → 디스크 있음' Gray
  Note '  → 아래 android_winusb.inf → Android ADB Interface → "호환되지 않을 수 있음" 경고에 예' Gray
  Note '  (하드웨어 ID 끝에 &MI_01 처럼 MI_ 가 붙어 있으면 Android Composite ADB Interface 를 고릅니다.)' Gray
  Note '  (INF 파일에 장치 번호를 직접 추가하지 마세요 — 서명이 깨져 Windows가 설치를 거부합니다.)' DarkGray
  $inf = Join-Path $LogDir 'usb_driver\android_winusb.inf'
  if(-not (Test-Path $inf) -and (Ask 'Google 공식 USB 드라이버를 받을까요?')){
    $zip = Join-Path $LogDir 'usb_driver.zip'
    try { Invoke-WebRequest -Uri $USB_URL -OutFile $zip -UseBasicParsing -ErrorAction Stop; Expand-Archive -LiteralPath $zip -DestinationPath $LogDir -Force -ErrorAction Stop }
    catch { Note "드라이버 다운로드 실패: $($_.Exception.Message)" Yellow }
  }
  if(Test-Path $inf){ Note "  드라이버 파일: $inf" White }
  if(Ask '장치 관리자를 열까요?'){ Start-Process devmgmt.msc }
}

$serial = $null
$tries = 0
$hinted = $null
while(-not $serial){
  $dl = & $script:adbExe devices -l 2>&1 | Out-String
  Add-Content -LiteralPath $Log -Value $dl
  $pd = @(); $others = @()
  foreach($line in ($dl -split "`r?`n")){
    if($line -match '^(\S+)\s+device\b'){
      $sn = $Matches[1]   # capture before the next -match overwrites $Matches
      if($line -match 'product:pd_kr(\s|$)'){ $pd += $sn } else { $others += $sn }   # exactly pd_kr, not pd_kr2
    }
  }
  if($pd.Count -gt 0){ $serial = $pd[0]; break }
  if($Yes){ Die '차(pd_kr)를 찾지 못했습니다.' 1 }
  $usb = UsbProblem
  if($dl -match 'unauthorized'){ Note '→ 차 화면에 "USB 디버깅 허용" 창이 뜨면 [항상 허용] 하세요.' Yellow }
  elseif($usb -eq 'driver'){ if($hinted -ne 'driver'){ DriverHelp; $hinted='driver' } else { Note '→ 드라이버 지정을 기다리는 중... (위 안내 참고)' Gray } }
  elseif($usb -eq 'cable' -and $hinted -ne 'cable'){
    Note '→ USB 장치 하나가 연결에 실패했습니다("알 수 없는 USB 장치(장치 설명자 요청 실패)").' Yellow
    Note '  가장 흔한 원인은 Factory Test 입력 실수입니다: 케이블을 빼고  0A 01 / 65 01 / 0A 02  를 띄어쓰기까지 정확히,' Yellow
    Note '  한 줄씩 지우고 입력해 SEND 한 뒤 다시 꽂으세요(실제 사례: 코드를 고쳐 입력하자 바로 설치됨).' Yellow
    Note '  차라면 드라이버 문제가 아니라 연결 문제입니다. 케이블(데이터 지원, 성공 사례는 A-A)이나 PC 포트를 바꿔 보세요.' Yellow
    Note '  차 쪽도 확인: 음악 USB가 재생되는 데이터 포트인지, 폰(카플레이)을 빼고 재시동 후 Factory Test를 다시 했는지.' Yellow
    Note '  같은 케이블·PC로 다른 차는 된다면 이 차의 USB 포트·배선 쪽입니다(블루링크 없는 내비도 설치된 사례가 있습니다).' Yellow
    $hinted='cable'
  }
  elseif($usb -eq 'other' -and $hinted -ne 'other'){
    Note '→ 오류 상태인 USB 장치가 있습니다. 차를 꽂은 뒤 생긴 장치라면 아래 ID를 Issues에 알려 주세요(차종·블루링크 유무와 함께).' Yellow
    Note "  $script:badIds" DarkGray
    $hinted='other'
  }
  elseif($others.Count -gt 0){
    Note '→ 차가 아닌 기기만 보입니다(휴대폰 등). 그 기기는 건드리지 않습니다.' Yellow
    if($hinted -ne 'others'){
      Note '  차인데 여기 보인다면 기기 이름(product:)이 검증된 pd_kr과 다른 내비입니다. 이 줄을 Issues에 알려 주세요:' Gray
      foreach($line in ($dl -split "`r?`n")){ if($line -match '^\S+\s+device\b'){ Note "  $line" DarkGray } }
      $hinted='others'
    }
  }
  else { Note '→ 차를 기다리는 중... USB 디버깅/Factory Test(0A 01/65 01/0A 02) 다시 확인.' Gray }
  $tries++
  # 5초 간격으로 1분. 사람이 할 일이 남았으면(드라이버 지정, 차 화면 허용) 3분까지 기다린다.
  $limit = if($hinted -eq 'driver' -or $dl -match 'unauthorized'){ 36 } else { 12 }
  if($tries -ge $limit){
    # 못 찾음: 마지막 상태를 한눈에 요약한다(자세한 시간순 기록은 기록 파일에).
    $why = if($dl -match 'unauthorized'){ '차 화면에서 USB 디버깅 허용을 누르지 않음' }
      elseif($usb -eq 'driver'){ '차는 보였지만 Windows 드라이버가 지정되지 않음' }
      elseif($usb -eq 'cable'){ 'USB 연결 실패(장치 설명자 요청 실패) — Factory Test 입력부터 다시, 그다음 케이블·포트' }
      elseif($others.Count -gt 0){ 'pd_kr이 아닌 기기만 보임' }
      elseif($usb -eq 'other'){ '오류 상태인 USB 장치만 있음' }
      else { 'PC에 차가 전혀 나타나지 않음 — Factory Test(0A 01/65 01/0A 02)·케이블·포트 확인' }
    Note "마지막 상태: $why" Yellow
    if($script:badIds){ Note "  오류 장치: $script:badIds" DarkGray }
    Die "시간 초과($($limit*5)초). 차를 찾지 못했습니다. 위 상태를 고친 뒤 설치.cmd를 다시 실행하세요." 1
  }
  Start-Sleep -Seconds 5
}
Note "차 연결됨: $serial" Green

# --- 내비 정보 · 빌드 확인 -------------------------------------------------------
# 정해 둔 항목만 읽는다: getprop 전체에는 시리얼·블루투스 주소 등 개인 정보가 있어, 공개 Issues에 올라갈 기록 파일에 넣지 않는다.
$pv = @{}
foreach($k in 'ro.product.name','ro.product.device','ro.build.product','ro.build.version.incremental','ro.build.fingerprint',
              'sys.usb.config','sys.usb.state','persist.sys.usb.config','persist.sys.dipo.state'){
  $pv[$k] = (Adb '-s' $serial 'shell' 'getprop' $k).Trim()
}
$build = $pv['ro.build.version.incremental']; $device = $pv['ro.product.device']
$board = if($device -match 'daudiopluslow'){ 'D-Audio Plus LOW (블루링크 없음)' } elseif($device -match 'daudioplus'){ 'D-Audio Plus (블루링크)' } else { "확인 안 됨 ($device)" }
Note "내비: $board / 빌드 $build / USB $($pv['sys.usb.state'])" DarkGray
if($build -eq $LowBuild){
  Note '블루링크 없는 내비(LOW 보드) — 설치가 확인된 빌드입니다.' DarkGray
} elseif($build -and $build -ne $VerifiedBuild){
  Note "주의: 검증된 빌드($VerifiedBuild)와 다릅니다. 값이 틀릴 수 있습니다." Yellow
  if(-not (Ask '그래도 계속할까요?')){ Note '사용자 취소.' Yellow; exit 1 }
}

# --- 삭제 모드 --------------------------------------------------------------
if($Uninstall){
  Head '앱 삭제'
  foreach($app in $Apps){
    $o = AdbWait 60 "$($app.name) 삭제" @('-s',$serial,'uninstall',$app.pkg)
    if($null -eq $o){ Stuck "$($app.name) 삭제(내비)" '차 화면에 확인 창이 떠 있으면 누르고, 없으면 시동을 껐다 켠 뒤 다시 실행하세요.' }
    if($o -match 'Success'){ Note "$($app.name): 삭제됨" Green; continue }
    # Not Success: either it was never installed or the uninstall failed. pm path tells the two apart.
    $p = AdbWait 30 "$($app.name) 확인" @('-s',$serial,'shell','pm','path',$app.pkg)
    if($null -ne $p -and $p -match 'package:'){ Die "$($app.name) 삭제 실패 (앱이 아직 차에 있습니다):`n$($o.Trim())" 1 }
    Note "$($app.name): 설치돼 있지 않음" Gray
  }
  Note '삭제 완료. 순정 상태로 돌아갔습니다(순정 시스템은 원래 건드리지 않습니다).' Green; UsbOffNote; exit 0
}

# --- 설치 -------------------------------------------------------------------
Head '4) 앱 설치'
# adb install 대신 ① 전송(push) ② 설치(pm install) 두 단계로 나눠, 멈추면 USB 쪽인지 내비 쪽인지 바로 갈린다.
$pushHint = 'USB로 파일을 보내다 멈췄습니다. 케이블·노트북 포트(USB 2.0 권장)를 바꾸고 Factory Test부터 다시 한 뒤 실행하세요.'
$pmHint = '내비가 설치를 끝내지 않았습니다. 차 화면에 확인 창이 떠 있으면 누르고, 없으면 시동을 껐다 켠 뒤 다시 실행하세요.'
foreach($app in $Apps){
  $apk = $app.path; $remote = "/data/local/tmp/$($app.apk)"
  Note ("{0} ({1} KB)" -f $app.name, [math]::Round((Get-Item $apk).Length / 1KB)) White
  $o = AdbWait 60 '① 전송 (USB)' @('-s',$serial,'push',$apk,$remote)
  if($null -eq $o){ Stuck "$($app.name) ① 전송" $pushHint }
  if($o -match 'error|failed|No such'){ Die "$($app.name) 전송 실패:`n$($o.Trim())" 1 }
  $o = AdbWait 120 '② 설치 (내비)' @('-s',$serial,'shell','pm','install','-r',$remote)
  if($null -eq $o){ Stuck "$($app.name) ② 설치" $pmHint }
  # Only a signature clash is fixed by uninstalling; any other failure (storage full, ...) must not remove the working app.
  if($o -match 'INCONSISTENT_CERTIFICATES|UPDATE_INCOMPATIBLE'){
    Note "$($app.name): 기존에 다른 서명으로 설치돼 있습니다." Yellow
    if(Ask '지우고 다시 설치할까요? (앱 설정도 지워집니다)'){
      $u = AdbWait 60 '기존 앱 삭제' @('-s',$serial,'uninstall',$app.pkg)
      if($null -eq $u){ Stuck "$($app.name) 기존 앱 삭제" $pmHint }
      if($u -notmatch 'Success'){ Die "$($app.name) 기존 앱 삭제 실패:`n$($u.Trim())" 1 }
      $o = AdbWait 120 '② 설치 (내비)' @('-s',$serial,'shell','pm','install','-r',$remote)
      if($null -eq $o){ Stuck "$($app.name) ② 설치" $pmHint }
    }
  }
  AdbWait 15 '정리' @('-s',$serial,'shell','rm','-f',$remote) | Out-Null
  if($o -match 'Success'){ Note "$($app.name) 설치 완료" Green }
  else { Die "$($app.name) 설치 실패:`n$($o.Trim())" 1 }
}

# --- '내 앱' 실행 -----------------------------------------------------------
Head "5) '내 앱' 실행"
Adb '-s' $serial 'shell' 'am' 'start' '-n' 'com.veloster.carplayhub/.HubActivity' | Out-Null
Write-Host ''
Note "완료! 차 화면 '내 앱'에서  실행 바로가기 → 가장자리 버튼 + 알림 → 저장  을 고르면" Green
Note "노트북 없이 가장자리 '계기판' 버튼으로 대시보드가 열립니다." Green
UsbOffNote
exit 0
