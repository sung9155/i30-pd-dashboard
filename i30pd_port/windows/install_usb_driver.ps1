# Google USB 드라이버(android_winusb.inf)를 Windows 드라이버 스토어에 미리 등록한다.
# 관리자 권한 필요 — 일반 창에서 실행하면 UAC 창을 띄워 스스로 승격한다.
#
# 용도: 실차(Telechips/DiPO, VID 18D1 / PID D00D)를 꽂았을 때 장치 관리자에서
#       "드라이버 업데이트 → 컴퓨터에서 찾기 → 목록에서 직접 선택 → Android Device →
#        Android ADB Interface" 를 고를 수 있게 드라이버를 스토어에 넣어 둔다.
#       (Google INF에는 PID D00D 항목이 없어 자동 매칭은 되지 않는다.)
#
# 사용:  powershell -ExecutionPolicy Bypass -File i30pd_port\windows\install_usb_driver.ps1 [-Inf <경로>]
param(
  [string]$Inf = "D:\Android\usb_driver\android_winusb.inf"
)

$isAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()
           ).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)

if (-not $isAdmin) {
  Write-Host "관리자 권한으로 다시 실행합니다 (UAC 창 승인 필요)..."
  $psArgs = @('-NoProfile','-ExecutionPolicy','Bypass','-File',"`"$PSCommandPath`"",'-Inf',"`"$Inf`"")
  try {
    $p = Start-Process -FilePath 'powershell.exe' -ArgumentList $psArgs -Verb RunAs -Wait -PassThru -ErrorAction Stop
  } catch {
    Write-Host "UAC 승인이 없어(취소/시간초과) 드라이버를 등록하지 못했습니다. 다시 실행해 '예'를 누르세요." -ForegroundColor Yellow
    exit 3
  }
  if ($null -eq $p) { exit 3 }
  exit $p.ExitCode
}

if (-not (Test-Path $Inf)) {
  Write-Host "INF 없음: $Inf" -ForegroundColor Red
  exit 2
}

Write-Host "== 드라이버 스토어 등록: $Inf"
$out = & pnputil.exe /add-driver "$Inf" /install 2>&1
$out | ForEach-Object { Write-Host $_ }

Write-Host ""
Write-Host "== 등록 확인"
$list = & pnputil.exe /enum-drivers 2>&1
$hit = ($list | Select-String -Pattern 'android_winusb' -SimpleMatch)
if ($hit) {
  Write-Host "OK: android_winusb.inf 가 드라이버 스토어에 있습니다." -ForegroundColor Green
  $list | Select-String -Pattern 'android_winusb' -Context 1,5 | ForEach-Object { $_.Line; $_.Context.PostContext }
  $code = 0
} else {
  Write-Host "실패: 스토어에서 android_winusb 를 찾지 못했습니다. 위 pnputil 출력을 확인하세요." -ForegroundColor Red
  $code = 1
}

# 승격된 새 창에서 실행된 경우 결과를 읽을 수 있게 잠시 대기
if ($Host.Name -eq 'ConsoleHost' -and -not $env:I30_NO_PAUSE) {
  Write-Host ""
  Write-Host "아무 키나 누르면 닫힙니다..."
  try { $null = $Host.UI.RawUI.ReadKey('NoEcho,IncludeKeyDown') } catch {}
}
exit $code
