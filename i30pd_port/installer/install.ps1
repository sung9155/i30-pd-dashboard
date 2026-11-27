<#
  i30 PD 대시보드 설치 — PowerShell. 사용자는 "설치.cmd"를 더블클릭한다(이 파일을 직접 열 필요 없음).
  INSTALL.md 3~5단계(연결 확인 · 설치 · '내 앱' 실행)를 대신 한다. 1~2단계(엔지니어 모드 · Factory Test)는
  차 화면에서 사람이 해야 하므로 안내만 한다. 차(product:pd_kr)가 아닌 기기에는 아무것도 하지 않는다.

  무서명 .exe 가 SmartScreen/백신에 막히던 문제를 없애려고 배치 + PowerShell 로 바꿨다. powershell.exe 는
  Microsoft 서명본이라 SmartScreen 평판 차단이 없고, .cmd 는 파란 차단창 없이 노란 경고 한 번만 뜬다.

  버전: 시작할 때 안정화(최신 정식 릴리스, 실차 확인) / 테스트('test' 사전 배포, 실차 검증 전) / 진단 기록 모으기 중 고른다.
  테스트 인자: -AdbPath <adb 경로>  -Channel stable|test  -Yes(확인 모두 예, 대기/프롬프트 생략, 안정화)  -Uninstall(두 앱 삭제)
              -Diag(설치하지 않고 진단 기록 파일만 만든다)
              -BootFast(시동 안내 화면 10초 → 1초: 이 차의 순정 SystemUI를 이 PC에서 고쳐 설치) -BootFastRemove(순정으로 되돌리기)
              시험용: -JavaPath <java.exe> -ToolDir <도구 폴더(받기 생략)>
  새 설치기: 시작할 때 이 설치기가 나온 릴리스($InstallerChannel: 안정화 = 최신 정식, 테스트 = 'test')의 설치기 ZIP이 더 새것이면
             묻고 이 폴더의 파일을 바꾼 뒤 같은 인자로 다시 시작한다. 시험용: I30PD_API(릴리스 API 주소, 가짜 서버)
  창 설치기(gui.ps1)용: -Gui  번호·Enter를 묻지 않고(-Channel이 없으면 안정화) 차를 스스로 기다리며, 줄 단위로 찍고 아래 상태 줄을 낸다.
              @@STEP <제목>  @@WAIT <초> <안내>  @@ASK <질문>(표준 입력에서 y/n 한 줄을 읽음)  @@FILE <경로>  @@DONE ok|fail <내용>
              새 설치기는 창의 [업데이트]가 맡는다: -CheckUpdate(더 새것이면 '@@UPDATE <버전>' 한 줄) · -Gui -Update(묻지 않고 바꾼 뒤
              '@@OPEN <새 창 설치기.cmd>'). -Gui일 때 시작할 때의 새 설치기 확인은 건너뛴다.
#>
param([string]$AdbPath, [string]$ApkBase, [string]$Channel, [switch]$Yes, [switch]$Uninstall, [switch]$Diag,
      [switch]$BootFast, [switch]$BootFastRemove, [string]$JavaPath, [string]$ToolDir,
      [switch]$Gui, [switch]$CheckUpdate, [switch]$Update)

# 'Continue' (not 'Stop'): adb writes its progress to stderr, and under 'Stop' a native
# command's stderr turns into a terminating error (NativeCommandError) that aborts the
# script mid-install. Cmdlets that need try/catch get -ErrorAction Stop explicitly.
$ErrorActionPreference = 'Continue'
$Bound = @{} + $PSBoundParameters   # 새 설치기로 다시 시작할 때 같은 인자를 넘긴다
# 한글이 두 번씩 찍히던 문제(2026-11-04 사진, Windows 터미널): Invoke-WebRequest의 진행률 상자가 창 위쪽을 덮었다가
# 원래 글자를 다시 써 넣을 때, 2칸 글자(한글)의 오른쪽 반 칸이 글자로 한 번 더 그려진다. 진행률 상자를 띄우지 않는다
# (받는 중 안내는 이 스크립트가 직접 찍고, PowerShell 5.1에서는 받기도 훨씬 빨라진다).
$ProgressPreference = 'SilentlyContinue'
try { [Console]::OutputEncoding = [Text.Encoding]::UTF8 } catch {}
# 긴 한글 줄이 겹쳐 찍히던 문제: UTF-8 코드 페이지의 구형 Windows 콘솔은 창 끝에서 줄을 넘길 때 한글(2칸)을 1칸으로 세어,
# 넘어간 부분을 앞 글자 위에 겹쳐 찍고 다음 줄까지 밀어 겹친다(2026-11-03 Windows 10 PC 사진, 노란 경고 두 줄).
# 그래서 이 스크립트의 Write-Host는 모두 아래를 거친다. 창 폭보다 긴 줄은 화면 칸(한글·기호 2칸)으로 세어 띄어쓰기에서
# 직접 나눈 뒤 찍으므로 콘솔이 줄을 넘길 일이 없다. -NoNewline(진행 표시)은 그대로 넘긴다.
function CellWidth([char]$ch){
  $n = [int]$ch
  if($n -eq 0xB7){ return 2 }   # 가운뎃점: 한국어 콘솔 글꼴에서 2칸
  if($n -lt 0x1100){ return 1 }
  if($n -le 0x115F -or ($n -ge 0x2010 -and $n -le 0x2BFF) -or ($n -ge 0x2E80 -and $n -le 0xA4CF) -or ($n -ge 0xAC00 -and $n -le 0xD7A3) -or
     ($n -ge 0xF900 -and $n -le 0xFAFF) -or ($n -ge 0xFE30 -and $n -le 0xFE4F) -or ($n -ge 0xFF00 -and $n -le 0xFF60) -or ($n -ge 0xFFE0 -and $n -le 0xFFE6)){ return 2 }
  return 1
}
function Fit([string]$m, [int]$max = 0){
  if($Gui){ return ,@($m) }   # 창 설치기가 줄을 직접 나눈다
  if($max -le 0){ $max = 78; try { $w = [Console]::WindowWidth; if($w -ge 40){ $max = $w - 2 } } catch {} }
  $out = New-Object System.Collections.Generic.List[string]
  $indent = ''; if($m -match '^( +)'){ $indent = $Matches[1] + '  ' }   # 이어지는 줄은 원래 들여쓰기 + 2칸
  $line = ''; $cells = 0; $space = -1
  foreach($ch in $m.ToCharArray()){
    $cw = CellWidth $ch
    if($cells + $cw -gt $max -and $line.Length -gt $indent.Length){
      if($space -gt $indent.Length){ $out.Add($line.Substring(0, $space)); $line = $indent + $line.Substring($space + 1) }
      else { $out.Add($line); $line = $indent }
      $cells = 0; foreach($c in $line.ToCharArray()){ $cells += CellWidth $c }
      $space = $line.LastIndexOf(' '); if($space -le $indent.Length){ $space = -1 }
    }
    if($ch -eq ' ' -and $line.Length -gt $indent.Length){ $space = $line.Length }
    $line += $ch; $cells += $cw
  }
  $out.Add($line)
  return ,$out.ToArray()
}
function Write-Host {
  param([Parameter(Position=0)][object]$Object = '', [ConsoleColor]$ForegroundColor, [switch]$NoNewline)
  $p = @{}; if($PSBoundParameters.ContainsKey('ForegroundColor')){ $p['ForegroundColor'] = $ForegroundColor }
  if($NoNewline){ Microsoft.PowerShell.Utility\Write-Host $Object -NoNewline @p; return }
  foreach($part in ([string]$Object -split "`r?`n")){ foreach($l in (Fit $part)){ Microsoft.PowerShell.Utility\Write-Host $l @p } }
}
$ROOT = Split-Path -Parent $MyInvocation.MyCommand.Path
$InstallerVersion = '2026-11-27c'   # 화면 맨 위에 찍혀, 스크린샷만으로 옛 설치기인지 알 수 있다. 고칠 때마다 올린다.
$InstallerChannel = 'stable'   # 이 설치기가 나온 곳: stable = 최신 정식 릴리스, test = 'test' 사전 배포. ZIP을 만들 때 scripts/release.py installers가 적는다.
$VerifiedBuild = 'eng.vmadmin.20260114.084130'   # 블루링크 D-Audio Plus(HYUNDAIA) — 실차 설치 확인
# 비블루링크 D-Audio Plus LOW(HYUNDAILOWI, device daudiopluslow_*): 2026-01-14 펌웨어 비교상 USB 설정·차량 데이터 데몬이
# 위와 같고, 2026-10-31 실차 설치·동작 확인(다른 LOW 차 한 대는 USB "장치 설명자 요청 실패" — 그 차 포트·배선 문제로 봄).
$LowBuild = 'eng.vmadmin.20260114.084725'
$OldBuild = 'eng.vmadmin.20240205.105401'   # 블루링크 D-Audio Plus 2024-02 빌드 — 2026-11-03 같은 차종 실차에서 설치·값 확인
$PT_URL = 'https://dl.google.com/android/repository/platform-tools-latest-windows.zip'
$USB_URL = 'https://dl.google.com/android/repository/usb_driver_r13-windows.zip'
# APK를 폴더에서 못 찾으면 공개 배포 레포에서 받는다: 안정화 = 최신 정식 릴리스, 테스트 = 'test' 사전 배포(덮어쓰며 갱신).
$Releases = 'https://github.com/sung9155/i30-pd-dashboard/releases'
$Api = $(if($env:I30PD_API){ $env:I30PD_API.TrimEnd('/') } else { 'https://api.github.com/repos/sung9155/i30-pd-dashboard/releases' })   # I30PD_API: 시험용
$Apps = @(
  [pscustomobject]@{ apk='dashboard-i30pd-platform.apk'; pkg='com.veloster.dashboard'; name='대시보드' },
  [pscustomobject]@{ apk='myapps-i30pd-platform.apk';   pkg='com.veloster.carplayhub'; name="'내 앱'" }
)
$script:adbExe = $null
# --- 시동 안내 화면 1초 (SystemUI 패치) -------------------------------------------------------------------------
# 순정 SystemUI는 현대 코드라 고친 APK를 배포하지 않는다. 이 PC가 이 차의 원본을 받아, 해시가 확인된 빌드일 때만 그 자리에서
# 고쳐(우리 빌더: 공개 릴리스 i30pd-consent-fast.jar) 설치하고, 원본과 결과는 끝나면 PC에서 지운다. 도구는 해시 고정.
$SysUiKnown = @{ 'V141' = @('9a5f71ba436c368ff98a14b6a1c978a593644e30ae5b51df2cb03e7537eb0f82','2b4a2b62d9e24993f8f8dafd4dc876520b92c25a83b51309699db97b4fab7662') }
$SysUiBoot = 'core','core-junit','bouncycastle','ext','framework','telephony-common','mms-common','android.policy','services','apache-xml','automotive'
$BootTools = @(
  @{ f='baksmali.jar'; u='https://bitbucket.org/JesusFreke/smali/downloads/baksmali-2.5.2.jar'; h='d3116248cce4f82ec5a31eb7f95ee75daff42ddf6eed0ab573973dc53fbad2e5' },
  @{ f='smali.jar'; u='https://bitbucket.org/JesusFreke/smali/downloads/smali-2.5.2.jar'; h='9544299578b16f771d8aa8eaefe0d3718ca03478c16f3c356f2fcf1366bfb116' },
  @{ f='apksig.jar'; u='https://dl.google.com/dl/android/maven2/com/android/tools/build/apksig/8.3.0/apksig-8.3.0.jar'; h='468ee1852fa12461c6629608fe47be70e50f60be7c4744bb7f62ca48e85c0a6c' },
  # 공개 AOSP 4.2.2 테스트 키: 이 차의 SystemUI가 이 키로 서명돼 있어 업데이트로 설치된다(googlesource는 base64 텍스트로 준다)
  @{ f='platform.pk8'; u='https://android.googlesource.com/platform/build/+/android-4.2.2_r1/target/product/security/platform.pk8?format=TEXT'; h='1ad8ef556870edb70f69a9d3c112544c07de5162ba440d84d33f8bb0c5962875'; b64=$true },
  @{ f='platform.x509.pem'; u='https://android.googlesource.com/platform/build/+/android-4.2.2_r1/target/product/security/platform.x509.pem?format=TEXT'; h='9837de028f460c35cc8d3fa45f14eecce30f6fbfe4b93d399aef1acb80c20d14'; b64=$true })
$JreUrl = 'https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.21.1%2B2/OpenJDK17U-jre_x64_windows_hotspot_17.0.21.1_2.zip'
$JreSha = '739d8f2fd286dc2736d55cc8c9260980baefd275ad48cda242335bb0f08e0795'
$BuilderJar = 'i30pd-consent-fast.jar'
# 차에 깔린 두 앱의 버전 한 줄. 1.5-i18n = 릴리스 이름을 넣기 전(v1.16.0까지)의 대시보드.
function Versions {
  ($Apps | ForEach-Object {
    $n = if([string](Adb '-s' $serial 'shell' 'dumpsys' 'package' $_.pkg) -match 'versionName=(\S+)'){ $Matches[1] } else { '없음' }
    "$($_.name) " + $(if($n -eq '1.5-i18n'){ 'v1.16.0 이하' } else { $n })
  }) -join ' · '
}
$LogDir = Join-Path $env:LOCALAPPDATA 'i30pd-installer'
New-Item -ItemType Directory -Force -Path $LogDir | Out-Null
$Log = Join-Path $LogDir ("install_{0}.log" -f (Get-Date -Format yyyyMMdd_HHmmss))

function Log($m){ Add-Content -LiteralPath $Log -Value $m -Encoding UTF8 }   # 기록 파일에만 (문제 제보용)
function Note($m,[string]$c='Gray'){ Write-Host $m -ForegroundColor $c; Log $m }
function Head($m){ $t = Get-Date -Format HH:mm:ss; Write-Host ''; if($Gui){ Write-Host "@@STEP $m" }; Write-Host "$m   [$t]" -ForegroundColor Cyan; Log "== $m == $t" }
function Die($m,$code){
  Write-Host ''; Write-Host $m -ForegroundColor Red; Log "ERROR: $m"
  Write-Host "기록 파일: $Log" -ForegroundColor Yellow
  Write-Host '문제를 알릴 때 이 기록 파일을 함께 보내 주세요.' -ForegroundColor Yellow
  if($Gui){ Write-Host "@@FILE $Log"; Write-Host ('@@DONE fail ' + ([string]$m -split "`r?`n")[0]) }
  elseif(-not $Yes){ try { Start-Process explorer.exe "/select,`"$Log`"" } catch {} }
  exit $code
}
function Done([string]$m = ''){ if($Gui){ Write-Host (("@@DONE ok $m").TrimEnd()) }; exit 0 }   # 창 설치기: 완료 칸에 $m
function Cancel(){ Note '사용자 취소.' Yellow; if($Gui){ Write-Host '@@DONE fail 사용자 취소' }; exit 1 }
function UsbOffNote(){
  Note 'USB 디버깅 끄기: 시동을 껐다 켜면 저절로 꺼집니다. 바로 끄려면 Factory Test에서  0A 01 → 65 00 → 0A 02' Gray
  Note "  (또는 '내 앱' → USB 디버깅 → OFF). 끄면 노트북 연결이 끊깁니다." Gray
}
# 진단 기록에서 사람을 가릴 수 있는 것: MAC 주소, 휴대폰 번호, 메일 주소, adb 기기 번호, 경로 속 Windows 사용자 이름.
function Mask([string]$t){
  $t = $t -replace '\b(?:[0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}\b','<MAC>'
  $t = $t -replace '\b01[016789][-. ]?\d{3,4}[-. ]?\d{4}\b','<전화번호>'
  $t = $t -replace '[\w.+-]+@[\w-]+(?:\.[\w-]+)+','<메일>'
  if($serial){ $t = $t.Replace($serial,'<기기번호>') }
  if($env:USERPROFILE){ $t = $t.Replace($env:USERPROFILE,'%USERPROFILE%') }
  return $t
}
# -Gui: 창 설치기가 예/아니오 창을 띄우고 답 한 줄(y/n)을 표준 입력으로 보낸다. 답이 없으면(창이 닫힘) 아니오.
# 답 앞의 ASCII 아닌 글자는 뗀다: 콘솔 입력 인코딩이 UTF-8인 Windows에서 BOM이 붙어 '예'가 '아니오'로 읽힌 일이 있다(원작 a2f08a1).
function Ask($m){
  if($Gui){ Write-Host "@@ASK $m"; $r = (([string][Console]::In.ReadLine()) -replace '^[^\x21-\x7E]+', '').Trim(); Log "$m → $r"; return ($r -match '^[Yy]') }
  if($Yes){ return $true }; $r = Read-Host "$m [Y/n]"; return ($r -eq '' -or $r -match '^[Yy]') }
function Adb([Parameter(ValueFromRemainingArguments=$true)]$a){
  $o = & $script:adbExe @a 2>&1 | Out-String
  Add-Content -LiteralPath $Log -Value ("> adb " + ($a -join ' ') + "`n" + $o) -Encoding UTF8
  return $o
}
# 오래 걸릴 수 있는 adb(전송·설치)는 제한 시간 안에서 돌리고, 기다리는 동안 경과 초를 같은 줄에 찍는다:
# 멈춘 화면의 스크린샷만 봐도 어느 단계에서 몇 초째 멈췄는지 보이게. 시간 초과면 $null.
function AdbWait([int]$sec,[string]$what,[string[]]$a){
  $out = Join-Path $LogDir 'adb_out.txt'; $err = Join-Path $LogDir 'adb_err.txt'
  $line = ($a | ForEach-Object { if($_ -match '\s'){ '"' + $_ + '"' } else { $_ } }) -join ' '
  Log "> adb $line  (최대 ${sec}초)"
  $p = Start-Process -FilePath $script:adbExe -ArgumentList $line -NoNewWindow -PassThru -RedirectStandardOutput $out -RedirectStandardError $err
  if($Gui){ Write-Host "@@WAIT 0 $what" }
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
try { $Host.UI.RawUI.WindowTitle = "i30 PD 대시보드 — Windows 설치기 $InstallerVersion" } catch {}   # 창 제목이 cmd 경로 대신 이 이름으로
Write-Host $(if($Uninstall){ "  i30 PD 대시보드 삭제   (Windows 설치기 $InstallerVersion)" } elseif($Diag){ "  i30 PD 대시보드 진단 기록   (Windows 설치기 $InstallerVersion)" } else { "  i30 PD 대시보드 설치   (Windows 설치기 $InstallerVersion)" }) -ForegroundColor White
Write-Host '  ----------------------' -ForegroundColor DarkGray
Note '현대자동차 공식 앱이 아닙니다. 순정 시스템에 앱을 설치하는 작업이라 보증/AS 불이익이 있을 수 있고,' Yellow
Note '책임은 본인에게 있습니다. 모든 작업은 정차 · 시동 켠 상태에서 하세요.' Yellow
Note "기록: $Log" DarkGray

# --- 새 설치기 (막지는 않는다) ------------------------------------------------------
# 이 설치기가 나온 릴리스(안정화 = 최신 정식, 테스트 = 'test')의 Windows ZIP을 받아, 릴리스 정보의 크기·SHA-256과 맞고 풀어서
# install.ps1·설치.cmd가 있을 때만 그 안의 $InstallerVersion과 비교한다. 더 새것이면 묻고, 예면 이 폴더의 파일을 바꾸고 같은 인자로
# 다시 시작한다. 인터넷이 없거나 무엇이든 틀리면 지금 설치기로 계속한다(platform-tools·APK 등 다른 파일은 건드리지 않는다).
function SelfUpdate(){
  if($env:I30PD_UPDATED){ return }   # 방금 바꿔 다시 시작한 것
  if($Gui -and -not $Update){ return }   # 창 설치기: 창의 [업데이트] 단추가 맡는다
  # 창의 [업데이트](-Update): 무엇이든 틀리면 멈춰 창에 알린다. 창이 뜰 때의 확인(-CheckUpdate): 조용히 끝낸다.
  function Skip($m){ if($Update){ Die $m 2 }; if($CheckUpdate){ exit 0 } }
  $ch =$(if($InstallerChannel -eq 'test'){ '테스트' } else { '안정화' })
  $up = Join-Path $LogDir 'update'; $zip = Join-Path $up 'installer.zip'; $dir = Join-Path $up 'new'
  try {
    try { [Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor 3072 } catch {}   # TLS 1.2 on older Windows
    $which = $(if($InstallerChannel -eq 'test'){ 'tags/test' } else { 'latest' })
    $r = Invoke-WebRequest -Uri "$Api/$which" -UseBasicParsing -TimeoutSec 8 -Headers @{ 'User-Agent' = 'i30pd-installer' } -ErrorAction Stop
    $a = @(([Text.Encoding]::UTF8.GetString($r.RawContentStream.ToArray()) | ConvertFrom-Json).assets | Where-Object { $_.name -eq 'i30pd-installer-windows.zip' })[0]
    if(-not $a){ Log "새 설치기 확인: 릴리스($which)에 Windows 설치기 ZIP 없음"; Skip "릴리스($ch)에 Windows 설치기 ZIP이 없습니다."; return }
    Remove-Item -Recurse -Force -LiteralPath $up -ErrorAction SilentlyContinue
    New-Item -ItemType Directory -Force -Path $up | Out-Null
    Invoke-WebRequest -Uri $a.browser_download_url -OutFile $zip -UseBasicParsing -TimeoutSec 60 -ErrorAction Stop
  } catch { Log "새 설치기 확인 못 함: $($_.Exception.Message)"; Skip "새 설치기를 받지 못했습니다(인터넷 확인): $($_.Exception.Message)"; return }
  $v = $null; $bad = $null
  try {
    $len = (Get-Item -LiteralPath $zip).Length; $sha = (Get-FileHash -LiteralPath $zip -Algorithm SHA256).Hash.ToLower()
    if($len -ne [long]$a.size){ $bad = "크기가 릴리스 정보와 다름($len B)" }
    elseif($a.digest -and $a.digest -ne "sha256:$sha"){ $bad = "SHA-256이 릴리스 정보와 다름($sha)" }
    else {
      Expand-Archive -LiteralPath $zip -DestinationPath $dir -Force -ErrorAction Stop
      $new = Join-Path $dir 'install.ps1'
      if(-not (Test-Path -LiteralPath $new) -or -not (Test-Path -LiteralPath (Join-Path $dir '설치.cmd'))){ $bad = 'ZIP에 install.ps1·설치.cmd가 없음' }
      elseif([IO.File]::ReadAllText($new) -match "InstallerVersion = '(\d{4}-\d\d-\d\d[^']*)'"){ $v = $Matches[1] }
      else { $bad = 'install.ps1에서 버전을 읽지 못함' }
    }
  } catch { $bad = "ZIP을 풀지 못함($($_.Exception.Message))" }
  if($bad){ Skip "받은 새 설치기($ch)의 확인이 맞지 않아 쓰지 않습니다: $bad"; Note "새 설치기($ch)를 받았지만 확인이 맞지 않아 쓰지 않습니다: $bad. 지금 설치기로 계속합니다." Yellow; return }
  Log "새 설치기 확인($which): $v, 지금 $InstallerVersion"
  if([string]::CompareOrdinal($v, $InstallerVersion) -le 0){ return }
  if($CheckUpdate){ Write-Host "@@UPDATE $v"; exit 0 }
  Note "새 설치기가 나왔습니다: $v ($ch, 지금 쓰는 것 $InstallerVersion)" Yellow
  if(-not $Update -and -not (Ask '새 설치기로 바꾸고 다시 시작할까요? (아니오: 지금 설치기로 계속)')){ Log '새 설치기: 바꾸지 않음'; return }
  try {
    # 내용이 같은 파일은 건드리지 않는다: 설치.cmd는 cmd가 실행 중에도 다음 줄을 다시 읽는다. install.ps1은 이미 다 읽어 둔 상태라 바꿔도 된다.
    foreach($f in (Get-ChildItem -LiteralPath $dir -File)){
      $dst = Join-Path $ROOT $f.Name
      if((Test-Path -LiteralPath $dst) -and (Get-FileHash -LiteralPath $dst).Hash -eq (Get-FileHash -LiteralPath $f.FullName).Hash){ continue }
      Copy-Item -LiteralPath $f.FullName -Destination $dst -Force -ErrorAction Stop
    }
  } catch { if($Update){ Die "새 설치기로 바꾸지 못했습니다($($_.Exception.Message))." 2 }; Note "새 설치기($v)로 바꾸지 못했습니다($($_.Exception.Message)). 지금 설치기로 계속합니다." Yellow; return }
  if($Update){   # 창: 새 창 설치기를 열게 알리고 끝낸다(이 창은 닫힌다)
    Note "새 설치기($v)로 바꿨습니다." Green
    $w = Join-Path $ROOT '창 설치기.cmd'; if(Test-Path -LiteralPath $w){ Write-Host "@@OPEN $w" }
    Done '새 설치기를 엽니다.'
  }
  Note "새 설치기($v)로 바꿨습니다. 다시 시작합니다." Green
  $env:I30PD_UPDATED = '1'
  $argv = @(); foreach($k in $Bound.Keys){ if($Bound[$k] -is [switch]){ if($Bound[$k]){ $argv += "-$k" } } else { $argv += "-$k"; $argv += [string]$Bound[$k] } }
  & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $ROOT 'install.ps1') @argv
  exit $LASTEXITCODE
}
SelfUpdate
if($CheckUpdate){ exit 0 }
if($Update){ Note '이미 최신 설치기입니다.' Green; Done '이미 최신 설치기입니다.' }

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

# --- 릴리스 정보: 고를 버전의 번호·날짜와 노트 요약 (휴대폰 앱 Flow.describe/summary, 설치.command와 같은 규칙) ---
function CleanMd([string]$t){ $t = [regex]::Replace($t, '\[([^\]]+)\]\([^)]+\)', '$1'); return $t.Replace('**','').Replace('`','') }
function ShortLine([string]$t){
  $i = $t.IndexOf('다. '); if($i -lt 0){ $i = $t.IndexOf('. ') }
  if($i -ge 0){ $t = $t.Substring(0, $i + 2).TrimEnd() }
  if($t.Length -gt 90){ $t = $t.Substring(0, 89) + '…' }
  return $t
}
# 노트를 몇 줄로: 소제목(### 또는 **굵은 줄**)마다 첫 항목의 첫 문장, 설치·서명 절이나 테스트 노트의 지난 기록('> ')에서 멈춘다.
function NotesSummary([string]$body,[int]$heads=6){
  $out = New-Object System.Collections.Generic.List[string]; $n = 0; $want = $false
  foreach($raw in ($body -split "`r?`n")){
    $l = $raw.Trim()
    if($n -gt 0 -and ($l.StartsWith('> ') -or $l.StartsWith('### 설치') -or $l.StartsWith('### 서명'))){ break }
    $h = $null
    if($l.StartsWith('### ')){ $h = $l.Substring(4) } elseif($l.Length -gt 4 -and $l.StartsWith('**') -and $l.EndsWith('**')){ $h = $l.Substring(2, $l.Length - 4) }
    if($h){ if($n -eq $heads){ break }; $n++; $out.Add('■ ' + (CleanMd $h)); $want = $true; continue }
    if($want -and $l.StartsWith('- ')){ $out.Add('   ' + (ShortLine (CleanMd $l.Substring(2)))); $want = $false }
  }
  return ,$out
}
function ReleaseInfo([string]$which,[bool]$test){
  try {
    $r = Invoke-WebRequest -Uri "$Api/$which" -UseBasicParsing -TimeoutSec 8 -Headers @{ 'User-Agent' = 'i30pd-installer' } -ErrorAction Stop
    $j = [Text.Encoding]::UTF8.GetString($r.RawContentStream.ToArray()) | ConvertFrom-Json
    $d = $j.published_at
    if($test){ foreach($a in $j.assets){ if($a.name -eq 'dashboard-i30pd-platform.apk'){ $d = $a.updated_at } } }   # 테스트: APK를 마지막으로 바꾼 날
    $d = $(if($d -is [datetime]){ $d.ToString('yyyy-MM-dd') } else { ([string]$d).Substring(0, 10) })
    $t = $(if($test){ ([string]$j.name -replace '^테스트\s*[—-]\s*', '') + " · 갱신 $d" } else { "$($j.tag_name) · $d" })
    return [pscustomobject]@{ title = $t; lines = (NotesSummary ([string]$j.body)) }
  } catch { Log "릴리스 정보 실패($which): $($_.Exception.Message)"; return $null }
}
function ShowNotes($rel,[string]$url){
  if(-not $rel){ Note "버전 정보를 받지 못했습니다(인터넷 확인). 설치는 그대로 계속합니다. 전체 설명: $url" DarkGray; return }
  Head "이번 버전에 들어 있는 것 ($($rel.title))"
  foreach($l in $rel.lines){ Note $l Gray }
  Note "전체 설명: $url" DarkGray
}

# --- 버전 고르기 (-ApkBase 를 주면 그 주소를 그대로 쓴다) ---------------------
if(-not $Uninstall -and -not $Diag -and -not $BootFast -and -not $BootFastRemove -and -not $ApkBase){
  $Rel = @{ stable = (ReleaseInfo 'latest' $false); test = (ReleaseInfo 'tags/test' $true) }
  if($Channel -ne 'stable' -and $Channel -ne 'test'){
    if($Yes -or $Gui){ $Channel = 'stable' } else {
      Head '받을 버전'
      Note ('  1) 안정화 (권장)' + $(if($Rel.stable){ " $($Rel.stable.title)" }) + ' — 실차에서 확인된 버전') Gray
      Note ('  2) 테스트' + $(if($Rel.test){ " $($Rel.test.title)" }) + ' — 새 기능, 아직 실차 검증 전. 문제가 있으면 안정화로 다시 설치하면 됩니다') Gray
      Note '  3) 진단 기록 모으기 — 설치하지 않고, 문제를 알릴 때 보낼 파일만 만듭니다' Gray
      Note '  4) 시동 안내 화면 10초 → 1초 — 순정 시스템 화면(SystemUI)을 이 PC에서 고쳐 설치합니다 (내비 업데이트 전엔 5번으로 되돌리기)' Gray
      Note '  5) 시동 안내 화면 원래대로 — 4번을 되돌립니다' Gray
      $r = (Read-Host '번호를 입력하고 Enter [1]').Trim()
      if($r -eq '3'){ $Diag = $true } elseif($r -eq '4'){ $BootFast = $true } elseif($r -eq '5'){ $BootFastRemove = $true }
      else { $Channel = $(if($r -eq '2'){ 'test' } else { 'stable' }) }
    }
  }
  if(-not $Diag -and -not $BootFast -and -not $BootFastRemove){
    $ApkBase = $(if($Channel -eq 'test'){ "$Releases/download/test" } else { "$Releases/latest/download" })
    if($Channel -eq 'test'){ Note '버전: 테스트 (실차 검증 전)' Yellow } else { Note '버전: 안정화' Green }
    ShowNotes $Rel[$Channel] $(if($Channel -eq 'test'){ "$Releases/tag/test" } else { "$Releases/latest" })
  }
}

# --- APK 준비 (폴더에 있으면 쓰고, 없으면 공개 레포에서 받기) -----------------
$NoApps = $Uninstall -or $Diag -or $BootFast -or $BootFastRemove   # these modes install no app
if(-not $NoApps){ Head '0) 앱 파일 준비' }
foreach($app in $(if($NoApps){ @() } else { $Apps })){
  $local = Join-Path $ROOT $app.apk
  # 브라우저로 받은 GitHub 페이지(HTML)가 .apk 이름으로 저장된 경우가 있어, 'PK'로 시작할 때만 폴더 것을 쓴다.
  $ok=$false; if(Test-Path $local){ $fs=[IO.File]::OpenRead($local); $mg=New-Object byte[] 2; [void]$fs.Read($mg,0,2); $fs.Close(); $ok=($mg[0] -eq 0x50 -and $mg[1] -eq 0x4B) }
  if((Test-Path $local) -and -not $ok){ Note "$($app.apk): 폴더 파일이 APK가 아니라 무시하고 내려받습니다." Yellow }
  if($ok){ $app | Add-Member -NotePropertyName path -NotePropertyValue $local -Force; Note "$($app.apk): 설치기 폴더에 있는 파일을 씁니다 (위에서 고른 버전과 상관없이 이 파일이 설치됩니다)" Yellow; continue }
  $url = "$ApkBase/$($app.apk)"; $dst = Join-Path $LogDir $app.apk
  Note "$($app.apk): 폴더에 없어 내려받습니다..." Gray
  try { Invoke-WebRequest -Uri $url -OutFile $dst -UseBasicParsing -ErrorAction Stop } catch {
    Die ("APK 다운로드 실패: $($app.apk)`n" +
         "인터넷 연결을 확인한 뒤 다시 실행하세요. 인터넷이 안 되는 곳이라면, 다른 곳에서 받은 APK 두 개를 설치.cmd 옆에 두고 실행하면 됩니다.`n" +
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
if(-not $Yes -and -not $Gui){ Read-Host '위 단계가 끝나고 USB를 연결했으면 Enter' | Out-Null }

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
# 기다리는 동안 같은 안내를 5초마다 다시 찍지 않는다: 바뀔 때만 새 줄, 같으면 그 줄 끝의 경과 초만 고친다.
$script:waitMsg = $null; $waitT0 = Get-Date
function Wait($m,[string]$c='Gray'){
  if($Gui){ if($m -ne $script:waitMsg){ Log $m; $script:waitMsg = $m }; Write-Host ("@@WAIT {0} {1}" -f [int]((Get-Date) - $waitT0).TotalSeconds, $m); return }
  if($m -ne $script:waitMsg){ if($script:waitMsg){ Write-Host '' }; Log $m; $script:waitMsg = $m }
  Write-Host ("`r{0}  {1}초 " -f $m, [int]((Get-Date) - $waitT0).TotalSeconds) -NoNewline -ForegroundColor $c
}
function WaitEnd(){ if($script:waitMsg){ if(-not $Gui){ Write-Host '' }; $script:waitMsg = $null } }
while(-not $serial){
  $dl = & $script:adbExe devices -l 2>&1 | Out-String
  Add-Content -LiteralPath $Log -Value $dl -Encoding UTF8
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
  if($dl -match 'unauthorized'){ Wait '→ 차 화면에 "USB 디버깅 허용" 창이 뜨면 [항상 허용] 하세요.' Yellow }
  elseif($usb -eq 'driver'){ if($hinted -ne 'driver'){ WaitEnd; DriverHelp; $hinted='driver' } else { Wait '→ 드라이버 지정을 기다리는 중... (위 안내 참고)' } }
  elseif($usb -eq 'cable' -and $hinted -ne 'cable'){
    WaitEnd
    Note '→ USB 장치 하나가 연결에 실패했습니다("알 수 없는 USB 장치(장치 설명자 요청 실패)").' Yellow
    Note '  가장 흔한 원인은 Factory Test 입력 실수입니다: 케이블을 빼고  0A 01 / 65 01 / 0A 02  를 띄어쓰기까지 정확히,' Yellow
    Note '  한 줄씩 지우고 입력해 SEND 한 뒤 다시 꽂으세요(실제 사례: 코드를 고쳐 입력하자 바로 설치됨).' Yellow
    Note '  차라면 드라이버 문제가 아니라 연결 문제입니다. 케이블(데이터 지원, 성공 사례는 A-A)이나 PC 포트를 바꿔 보세요.' Yellow
    Note '  차 쪽도 확인: 음악 USB가 재생되는 데이터 포트인지, 폰(카플레이)을 빼고 재시동 후 Factory Test를 다시 했는지.' Yellow
    Note '  같은 케이블·PC로 다른 차는 된다면 이 차의 USB 포트·배선 쪽입니다(블루링크 없는 내비도 설치된 사례가 있습니다).' Yellow
    $hinted='cable'
  }
  elseif($usb -eq 'other' -and $hinted -ne 'other'){
    WaitEnd
    Note '→ 오류 상태인 USB 장치가 있습니다. 차를 꽂은 뒤 생긴 장치라면 아래 ID를 Issues에 알려 주세요(차종·블루링크 유무와 함께).' Yellow
    Note "  $script:badIds" DarkGray
    $hinted='other'
  }
  elseif($others.Count -gt 0){
    if($hinted -ne 'others'){
      WaitEnd
      Note '  차인데 여기 보인다면 기기 이름(product:)이 검증된 pd_kr과 다른 내비입니다. 이 줄을 Issues에 알려 주세요:' Gray
      foreach($line in ($dl -split "`r?`n")){ if($line -match '^\S+\s+device\b'){ Note "  $line" DarkGray } }
      $hinted='others'
    }
    Wait '→ 차가 아닌 기기만 보입니다(휴대폰 등). 그 기기는 건드리지 않습니다.' Yellow
  }
  else { Wait '→ 차를 기다리는 중... USB 디버깅/Factory Test(0A 01/65 01/0A 02) 다시 확인.' }
  $tries++
  # 5초 간격으로 1분. 사람이 할 일이 남았으면(드라이버 지정, 차 화면 허용) 3분까지 기다린다.
  $limit = if($hinted -eq 'driver' -or $dl -match 'unauthorized'){ 36 } else { 12 }
  if($Gui){ $limit = 60 }   # 창 설치기: Enter로 준비를 알리지 않으므로 차 화면 단계를 할 시간까지 5분 기다린다
  if($tries -ge $limit){
    WaitEnd
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
WaitEnd
Note "차 연결됨: $serial" Green

# --- 내비 정보 · 빌드 확인 -------------------------------------------------------
# 정해 둔 항목만 읽는다: getprop 전체에는 시리얼·블루투스 주소 등 개인 정보가 있어, 공개 Issues에 올라갈 기록 파일에 넣지 않는다.
$pv = @{}
foreach($k in 'ro.product.name','ro.product.device','ro.build.product','ro.build.version.incremental','ro.build.fingerprint',
              'sys.usb.config','sys.usb.state','persist.sys.usb.config','persist.sys.dipo.state','persist.sys.key.custom'){
  $pv[$k] = (Adb '-s' $serial 'shell' 'getprop' $k).Trim()
}
$build = $pv['ro.build.version.incremental']; $device = $pv['ro.product.device']
$board = if($device -match 'daudiopluslow'){ 'D-Audio Plus LOW (블루링크 없음)' } elseif($device -match 'daudioplus'){ 'D-Audio Plus (블루링크)' } else { "확인 안 됨 ($device)" }
Note "내비: $board / 빌드 $build / USB $($pv['sys.usb.state'])" DarkGray
$cur = Versions
Note "차에 깔린 버전: $cur" DarkGray
# 받을 버전을 바로 아래에: 안정화는 차의 대시보드와 이름이 같으면 그 사실을 알린다(테스트 빌드 이름은 릴리스 정보에 없다).
if(-not $NoApps -and $Rel -and $Channel -and $Rel[$Channel]){
  $tag = ($Rel[$Channel].title -split ' ')[0]
  if($Channel -eq 'test'){ Note "받을 버전: 테스트 ($($Rel.test.title))" DarkGray }
  elseif((" $cur ").Contains(" $tag ")){ Note "받을 버전: $tag — 차에 이미 같은 버전이 있습니다. 그대로 다시 설치합니다." DarkGray }
  else { Note "받을 버전: $tag" DarkGray }
}

# --- 진단 기록 모드: 읽기만 한다 -------------------------------------------------
# 정해 둔 속성, 두 앱의 버전, 우리 앱 태그·충돌·설치 기록만. 차 기록 전체에는 블루투스로 붙은 폰 이름·번호·연락처가 섞인다.
if($Diag){
  Head '진단 기록 모으기 (차에는 아무것도 바꾸지 않습니다)'
  $sb = New-Object Text.StringBuilder
  [void]$sb.AppendLine("i30 PD 진단 기록 — $(Get-Date -Format 'yyyy-MM-dd HH:mm') / PC 설치기 $InstallerVersion (Windows)").AppendLine().AppendLine('[내비]')
  foreach($k in ($pv.Keys | Sort-Object)){ [void]$sb.AppendLine("${k}: $($pv[$k])") }
  [void]$sb.AppendLine("시각: $((Adb '-s' $serial 'shell' 'date').Trim())").AppendLine().AppendLine('[앱]')
  foreach($app in $Apps){
    $v = @((Adb '-s' $serial 'shell' 'dumpsys' 'package' $app.pkg) -split "`r?`n" | Where-Object { $_ -match '(versionCode|versionName|firstInstallTime|lastUpdateTime|codePath)=' } | ForEach-Object { '  ' + $_.Trim() } | Select-Object -Unique)
    [void]$sb.AppendLine("$($app.name) ($($app.pkg))")
    if($v.Count){ $v | ForEach-Object { [void]$sb.AppendLine($_) } } else { [void]$sb.AppendLine('  설치돼 있지 않음') }
  }
  Note '기록 읽는 중...' Gray
  $tags = @('VehicleDash:V','VNCarPlayHub:V','VNQuickEntry:V','NDashboard:V','DtcDialog:V','AndroidRuntime:E','PackageManager:I')
  $lc = & $script:adbExe -s $serial shell logcat -d -v time -s @tags 2>&1 | Out-String   # 설치기 기록에는 안 남긴다(아래에 그 기록을 붙임)
  [void]$sb.AppendLine().AppendLine("[기록: $($tags -join ' ')]").AppendLine(((($lc -split "`r?`n") | Select-Object -Last 3000) -join "`r`n"))
  [void]$sb.AppendLine().AppendLine('[이 PC 설치기 기록]').Append((Get-Content -Raw -LiteralPath $Log -Encoding UTF8))
  $desk = [Environment]::GetFolderPath('Desktop'); if(-not $desk -or -not (Test-Path $desk)){ $desk = $LogDir }
  $out = Join-Path $desk ("i30pd_진단기록_{0}.txt" -f (Get-Date -Format yyyyMMdd_HHmmss))
  [IO.File]::WriteAllText($out, (Mask $sb.ToString()), (New-Object Text.UTF8Encoding $true))
  Note "만든 파일: $out" Green
  Note '문제를 알릴 때 이 파일을 보내 주세요. 증상(언제, 어떤 화면에서, 무엇이)도 함께 알려 주세요.' White
  if($Gui){ Write-Host "@@FILE $out" } elseif(-not $Yes){ Start-Process explorer.exe "/select,`"$out`"" }
  UsbOffNote; Done '진단 기록 파일을 만들었습니다([파일 보기]).'
}
# --- 시동 안내 화면 1초: 되돌리기 · 설치 (위 $SysUiKnown 설명 참고) -----------------------------------------
function SysUiPath(){ ((Adb '-s' $serial 'shell' 'pm' 'path' 'com.android.systemui') -replace '\s','') -replace '^package:','' }
function SysUiCode(){ if([string](Adb '-s' $serial 'shell' 'dumpsys' 'package' 'com.android.systemui') -match 'versionCode=(\d+)'){ $Matches[1] } else { '?' } }
$BootHint = '내비가 응답하지 않습니다. 차 화면에 확인 창이 떠 있으면 누르고, 없으면 시동을 껐다 켠 뒤 다시 실행하세요.'
if($BootFastRemove){
  Head '시동 안내 화면 원래대로'
  $p = SysUiPath
  if($p -notmatch '^/data/app/'){ Note "이미 순정입니다 ($p)." Green; UsbOffNote; Done '이미 순정입니다.' }
  $o = AdbWait 90 '되돌리는 중' @('-s',$serial,'uninstall','com.android.systemui')
  if($null -eq $o){ Stuck '시동 안내 되돌리기' $BootHint }
  $p = SysUiPath
  if($p -match '^/system/'){
    Note '되돌림 완료: 다음 시동부터 순정(10초 뒤 닫힘)입니다. 화면 위 상태 표시줄이 잠깐 다시 그려질 수 있습니다.' Green
    UsbOffNote; Done '시동 안내 화면을 원래대로 되돌렸습니다.' }
  Die "되돌리지 못했습니다 (지금: $p):`n$("$o".Trim())" 1
}
if($BootFast){
  Head '시동 안내 화면 10초 → 1초'
  Note '시동 때 뜨는 순정 안내 화면("실제 교통 법규를 준수하여…")이 10초 대신 약 1초 뒤 스스로 닫히게 합니다.' Gray
  Note '순정 시스템 화면(SystemUI)을 이 차에서 받아 이 PC에서 고쳐 설치합니다. 고친 파일은 어디에도 올리지 않고 끝나면 이 PC에서도 지웁니다.' Gray
  Note '내비 소프트웨어 업데이트 전에는 반드시 설치기 5번(원래대로)을 먼저 하세요. 업데이트 뒤 고친 화면이 남아 있으면 내비가 이상해질 수 있습니다.' Yellow
  $p = SysUiPath
  if($p -match '^/data/app/'){
    $vc = SysUiCode
    if($vc -eq '18'){ Note '이미 설치돼 있습니다(시동 안내 1초). 되돌리려면 설치기 5번.' Green; UsbOffNote; Done '이미 설치돼 있습니다(시동 안내 1초).' }
    Die "SystemUI에 이미 다른 업데이트가 깔려 있습니다($p, versionCode $vc). 손대지 않고 멈춥니다." 1
  }
  if($p -notmatch '^/system/app/HKMC_SystemUI\.apk$'){ Die "순정 SystemUI 위치가 예상과 다릅니다($p). 손대지 않고 멈춥니다." 1 }
  if(-not (Ask '진행할까요?')){ Cancel }
  try { [Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor 3072 } catch {}   # TLS 1.2 on older Windows
  $cf = Join-Path $LogDir 'bootfast'; $cap = Join-Path $cf 'cap'; $outApk = Join-Path $cf 'SystemUI-1s.apk'
  function BootClean(){ Remove-Item -Recurse -Force -LiteralPath $cap,$outApk -ErrorAction SilentlyContinue }   # Hyundai code: never left on this PC
  function BDie($m,$c){ BootClean; Die $m $c }
  function Sha($f){ (Get-FileHash -LiteralPath $f -Algorithm SHA256).Hash.ToLower() }
  BootClean; New-Item -ItemType Directory -Force -Path (Join-Path $cap 'framework') | Out-Null

  Note '① 이 차의 SystemUI 받기' White
  foreach($f in 'HKMC_SystemUI.apk','HKMC_SystemUI.odex'){
    $o = AdbWait 120 $f @('-s',$serial,'pull',"/system/app/$f",(Join-Path $cap $f))
    if($null -eq $o){ BootClean; Stuck "$f 받기" $BootHint }
    if(-not (Test-Path (Join-Path $cap $f))){ BDie "차에서 $f 를 받지 못했습니다.`n$("$o".Trim())" 1 }
  }
  $ha = Sha (Join-Path $cap 'HKMC_SystemUI.apk'); $ho = Sha (Join-Path $cap 'HKMC_SystemUI.odex')
  $known = @($SysUiKnown.Keys | Where-Object { $SysUiKnown[$_][0] -eq $ha -and $SysUiKnown[$_][1] -eq $ho })
  Log "SystemUI apk $ha odex $ho -> $($known -join ',')"
  if($known.Count -eq 0){ BDie ("이 내비의 SystemUI는 아직 확인되지 않은 빌드라 멈춥니다. 차에는 아무것도 바꾸지 않았습니다.`n" +
                        "이 기능을 원하시면 이 기록 파일을 보내 주세요(내비 빌드 $build).") 3 }
  Note "  확인된 빌드: $($known[0])" DarkGray
  foreach($b in $SysUiBoot){
    $o = AdbWait 120 "$b.odex" @('-s',$serial,'pull',"/system/framework/$b.odex",(Join-Path $cap "framework\$b.odex"))
    if($null -eq $o){ BootClean; Stuck "$b.odex 받기" $BootHint }
    if(-not (Test-Path (Join-Path $cap "framework\$b.odex"))){ BDie "차에서 $b.odex 를 받지 못했습니다.`n$("$o".Trim())" 1 }
  }

  Note '② 도구 준비 (처음 한 번만 받습니다)' White
  $java = $JavaPath
  if(-not $java){ $j = Get-ChildItem -Path (Join-Path $LogDir 'jre17') -Recurse -Filter java.exe -ErrorAction SilentlyContinue | Select-Object -First 1; if($j){ $java = $j.FullName } }
  if(-not $java){ try { $c = (Get-Command java -ErrorAction Stop).Source; if((& $c -version 2>&1 | Out-String) -match 'version "(\d+)' -and [int]$Matches[1] -ge 11){ $java = $c } } catch {} }
  if(-not $java){
    Note '  Java(Eclipse Temurin 17, 약 44 MB) 받는 중...' Gray
    $z = Join-Path $LogDir 'jre17.zip'
    try { Invoke-WebRequest -Uri $JreUrl -OutFile $z -UseBasicParsing -ErrorAction Stop } catch { BDie "Java를 받지 못했습니다(인터넷 확인).`n$JreUrl`n$($_.Exception.Message)" 2 }
    if((Sha $z) -ne $JreSha){ Remove-Item -Force $z; BDie 'Java 파일 해시가 맞지 않아 쓰지 않습니다. 다시 실행해 보세요.' 2 }
    Expand-Archive -LiteralPath $z -DestinationPath (Join-Path $LogDir 'jre17') -Force; Remove-Item -Force $z
    $java = (Get-ChildItem -Path (Join-Path $LogDir 'jre17') -Recurse -Filter java.exe | Select-Object -First 1).FullName
  }
  Log "java: $java"
  $td = if($ToolDir){ $ToolDir } else { Join-Path $cf 'tools' }
  New-Item -ItemType Directory -Force -Path $td | Out-Null
  foreach($t in $BootTools){
    $f = Join-Path $td $t.f
    if((Test-Path $f) -and (Sha $f) -eq $t.h){ continue }
    Note "  $($t.f) 받는 중..." Gray
    try { Invoke-WebRequest -Uri $t.u -OutFile "$f.part" -UseBasicParsing -ErrorAction Stop } catch { BDie "$($t.f) 를 받지 못했습니다(인터넷 확인).`n$($t.u)`n$($_.Exception.Message)" 2 }
    if($t.b64){ [IO.File]::WriteAllBytes($f, [Convert]::FromBase64String([IO.File]::ReadAllText("$f.part").Trim())); Remove-Item -Force "$f.part" }
    else { Move-Item -Force "$f.part" $f }
    if((Sha $f) -ne $t.h){ Remove-Item -Force $f; BDie "$($t.f) 해시가 맞지 않아 쓰지 않습니다." 2 }
  }
  $bj = Join-Path $td $BuilderJar
  if(-not $ToolDir){   # our builder: from the public release (stable first, then the test pre-release)
    Remove-Item -Force -LiteralPath $bj -ErrorAction SilentlyContinue
    foreach($u in "$Releases/latest/download/$BuilderJar","$Releases/download/test/$BuilderJar"){
      try { Invoke-WebRequest -Uri $u -OutFile $bj -UseBasicParsing -ErrorAction Stop; break } catch { Log "빌더 받기 실패: $u $($_.Exception.Message)" } }
  }
  $ok = $false; if(Test-Path $bj){ $fs=[IO.File]::OpenRead($bj); $mg=New-Object byte[] 2; [void]$fs.Read($mg,0,2); $fs.Close(); $ok=($mg[0] -eq 0x50 -and $mg[1] -eq 0x4B) }
  if(-not $ok){ BDie "빌더($BuilderJar)를 받지 못했습니다. $Releases" 2 }

  Note '③ 고치기 (1~2분)' White
  $bo = (& $java -jar $bj $cap $td $outApk 2>&1 | Out-String); $rc = $LASTEXITCODE; Log $bo
  if($rc -eq 3){ BDie "확인되지 않은 SystemUI 빌드라 멈춥니다. 차에는 아무것도 바꾸지 않았습니다.`n$($bo.Trim())" 3 }
  if($rc -ne 0 -or -not (Test-Path $outApk)){ BDie "고치는 중 멈췄습니다(코드 $rc). 차에는 아무것도 바꾸지 않았습니다.`n$($bo.Trim())" 1 }
  Note "  $(($bo.Trim() -split "`r?`n")[-1])" DarkGray

  Note '④ 설치' White
  $remote = '/data/local/tmp/SystemUI-1s.apk'
  $o = AdbWait 60 '전송 (USB)' @('-s',$serial,'push',$outApk,$remote)
  if($null -eq $o){ BootClean; Stuck '시동 안내 전송' 'USB로 파일을 보내다 멈췄습니다. 케이블·포트를 바꾸고 Factory Test부터 다시 한 뒤 실행하세요.' }
  $o = AdbWait 120 '설치 (내비)' @('-s',$serial,'shell','pm','install','-r',$remote)
  AdbWait 15 '정리' @('-s',$serial,'shell','rm','-f',$remote) | Out-Null
  BootClean
  if($null -eq $o){ Stuck '시동 안내 설치' $BootHint }
  $p = SysUiPath; $vc = SysUiCode
  if($o -notmatch 'Success' -or $p -notmatch '^/data/app/' -or $vc -ne '18'){ Die "설치되지 않았습니다 (지금: $p, versionCode $vc):`n$("$o".Trim())" 1 }
  Note '설치 완료.' Green
  Note '시동을 완전히 끄고(문을 잠가 내비가 꺼질 때까지) 다시 켜면 그때부터 안내 화면이 약 1초 뒤 닫힙니다.' Green
  Note '후방 카메라·검은 화면이 먼저 떠 있으면 그게 끝난 뒤 1초입니다(순정 동작).' Gray
  Note '되돌리기: 설치기 5번(시동 안내 화면 원래대로). 내비 소프트웨어 업데이트 전에 꼭 되돌리세요.' Yellow
  UsbOffNote; Done '시동을 완전히 껐다 켜면 안내 화면이 약 1초 뒤 닫힙니다.'
}

if($build -eq $LowBuild){
  Note '블루링크 없는 내비(LOW 보드) — 설치가 확인된 빌드입니다.' DarkGray
} elseif($build -eq $OldBuild){
  Note '블루링크 내비 2024-02 빌드 — 설치와 값이 확인된 빌드입니다.' DarkGray
} elseif($build -and $build -ne $VerifiedBuild -and -not $Uninstall -and -not $BootFast -and -not $BootFastRemove){   # BootFast checks the SystemUI files themselves   # removing needs no "values may be wrong" question
  Note "주의: 검증된 빌드($VerifiedBuild)와 다릅니다. 값이 틀릴 수 있습니다." Yellow
  if(-not (Ask '그래도 계속할까요?')){ Cancel }
}

# --- 삭제 모드 --------------------------------------------------------------
if($Uninstall){
  Head '앱 삭제'
  # '내 앱'이 ☆ 버튼을 계기판으로 돌려 놨으면 순정 "없음"으로 되돌린다(앱이 사라지면 ☆가 아무것도 안 하게 됨).
  # IModeManager.setCustomKey(트랜잭션 34): '내 앱'과 같은 호출, 서비스 쪽 권한 검사 없음.
  if($pv['persist.sys.key.custom'] -eq 'com.veloster.carplayhub.STAR_KEY'){
    Adb '-s' $serial 'shell' 'service' 'call' 'com.hkmc.micom.mode' '34' 's16' 'com.mobis.intent.action.CUSTOM_SHORTCUT' | Out-Null
    if((Adb '-s' $serial 'shell' 'getprop' 'persist.sys.key.custom').Trim() -eq 'com.veloster.carplayhub.STAR_KEY'){
      Note '☆ 버튼: 되돌리지 못했습니다. 삭제 뒤 ☆를 길게 눌러 설정 화면에서 기능을 다시 고르세요.' Yellow
    } else { Note '☆ 버튼: 원래대로(없음) 되돌림' Green }
  }
  # 시동 안내 1초(설치기 4번)가 깔려 있으면 함께 되돌릴지 묻는다(-Yes면 되돌림: 삭제는 순정으로 돌아가는 것).
  $sysUiKept = $false
  if((SysUiPath) -match '^/data/app/' -and (SysUiCode) -eq '18'){
    if(Ask '시동 안내 화면 1초(설치기 4번)도 순정으로 되돌릴까요?'){
      $o = AdbWait 90 '시동 안내 화면 되돌리기' @('-s',$serial,'uninstall','com.android.systemui')
      if((SysUiPath) -match '^/system/'){ Note '시동 안내 화면: 순정으로 되돌림' Green } else { Note "시동 안내 화면: 되돌리지 못했습니다. 설치기 5번으로 다시 해 보세요. $("$o".Trim())" Yellow; $sysUiKept = $true }
    } else { Note '시동 안내 화면 1초는 그대로 둡니다(되돌리기: 설치기 5번).' Gray; $sysUiKept = $true }
  }
  foreach($app in $Apps){
    $o = AdbWait 60 "$($app.name) 삭제" @('-s',$serial,'uninstall',$app.pkg)
    if($null -eq $o){ Stuck "$($app.name) 삭제(내비)" '차 화면에 확인 창이 떠 있으면 누르고, 없으면 시동을 껐다 켠 뒤 다시 실행하세요.' }
    if($o -match 'Success'){ Note "$($app.name): 삭제됨" Green; continue }
    # Not Success: either it was never installed or the uninstall failed. pm path tells the two apart.
    $p = AdbWait 30 "$($app.name) 확인" @('-s',$serial,'shell','pm','path',$app.pkg)
    if($null -ne $p -and $p -match 'package:'){ Die "$($app.name) 삭제 실패 (앱이 아직 차에 있습니다):`n$($o.Trim())" 1 }
    # pm path prints nothing for an app that is not there. Anything else (an adb error, no answer) is a lost connection, not a removed app.
    if($null -eq $p -or $p.Trim() -ne ''){ Die "차와 연결이 끊긴 것 같습니다. $($app.name) 삭제를 확인하지 못했습니다.`n케이블을 확인하고 다시 실행하세요.`n$("$p".Trim())" 1 }
    Note "$($app.name): 설치돼 있지 않음" Gray
  }
  if($sysUiKept){ Note '앱 삭제 완료. 시동 안내 화면 1초만 남아 있습니다(설치기 5번으로 되돌리기).' Green }
  else { Note '삭제 완료. 순정 상태로 돌아갔습니다.' Green }
  UsbOffNote; Done $(if($sysUiKept){ '앱을 지웠습니다. 시동 안내 화면 1초는 남아 있습니다(5번으로 되돌리기).' } else { '삭제 완료. 순정 상태로 돌아갔습니다.' })
}

# --- 설치 -------------------------------------------------------------------
Head '4) 앱 설치'
# adb install 대신 ① 전송(push) ② 설치(pm install) 두 단계로 나눠, 멈추면 USB 쪽인지 내비 쪽인지 바로 갈린다.
$pushHint = 'USB로 파일을 보내다 멈췄습니다. 케이블·노트북 포트(USB 2.0 권장)를 바꾸고 Factory Test부터 다시 한 뒤 실행하세요.'
$pmHint = '내비가 설치를 끝내지 않았습니다. 차 화면에 확인 창이 떠 있으면 누르고, 없으면 시동을 껐다 켠 뒤 다시 실행하세요.'
foreach($app in $Apps){
  $apk = $app.path; $remote = "/data/local/tmp/$($app.apk)"
  # 내비는 설치한 APK를 바이트 그대로 보관한다: 받아 와서 같으면 같은 버전이니 건너뛴다.
  $pp = ((Adb '-s' $serial 'shell' 'pm' 'path' $app.pkg) -replace '\s','') -replace '^package:',''
  $have = Join-Path $LogDir "installed_$($app.apk)"; Remove-Item -LiteralPath $have -ErrorAction SilentlyContinue
  if($pp -match '^/.+\.apk$' -and $null -ne (AdbWait 60 "$($app.name) 설치된 파일 비교" @('-s',$serial,'pull',$pp,$have)) -and
     (Test-Path $have) -and (Get-FileHash $have).Hash -eq (Get-FileHash $apk).Hash){
    Note "$($app.name): 이미 같은 버전이 설치돼 있습니다 — 건너뜀" Green; continue
  }
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
Note "지금 차에 깔린 버전: $(Versions)" Green

# --- '내 앱' 실행 -----------------------------------------------------------
Head "5) '내 앱' 실행"
Adb '-s' $serial 'shell' 'am' 'start' '-n' 'com.veloster.carplayhub/.HubActivity' | Out-Null
Write-Host ''
Note "완료! 차 화면 '내 앱'에서  바로가기 버튼 → 버튼 + 알림 → 저장  을 고르면" Green
Note "노트북 없이 가장자리 '계기판' 버튼으로 대시보드가 열립니다." Green
UsbOffNote
Done "차 화면에 '내 앱'이 열립니다."
