<#
  i30 PD 대시보드 창 설치기 — Windows. 사용자는 "창 설치기.cmd"를 더블클릭한다(원작 installer/gui.ps1을 옮겨 고친 것).
  창 하나(번호 입력·Enter 없음)에서 단추를 누르면 같은 폴더의 install.ps1을 -Gui로 숨은 창에서 돌리고 그 출력을 줄 단위로
  기록 칸에 보여 준다. 설치 규칙은 모두 install.ps1에 있고 이 파일은 화면만 맡는다. 콘솔 설치기(설치.cmd 등)는 그대로 둔다.
  콘솔 번호 → 단추: 1) 안정화·2) 테스트 = [받을 버전] + [설치], 3) = [진단 기록], 4)·5) = [시동 안내 화면 (고급)]의 두 단추,
  삭제.cmd = [삭제]. 새 설치기: 창이 뜨면 install.ps1 -CheckUpdate를 숨은 창에서 돌려 '@@UPDATE <버전>'이 오면 [업데이트]를
  보이고, 누르면 -Gui -Update가 이 폴더의 파일을 바꾼 뒤 '@@OPEN <새 창 설치기.cmd>'를 알린다. 이 창은 그것을 열고 닫힌다.
  install.ps1 -Gui 상태 줄: @@STEP <제목>  @@WAIT <초> <안내>  @@ASK <질문>(예/아니오 창 → y/n을 표준 입력으로)  @@FILE <경로>
                         @@DONE ok|fail <내용>
  시험용 환경 변수(test/run.sh win): VN_GUI_SMOKE=<파일>  창이 뜨면 제목·버전 줄·기록 칸 높이를 그 파일에 쓰고 닫는다.
    VN_GUI_AUTORUN=install|uninstall|diag|bootfast|bootfastremove|update  창이 뜨면 바로 그 단추를 누른다(확인 창 없이).
    VN_GUI_ANSWER=y|n  @@ASK에 창 대신 이 답(없으면 n).  VN_GUI_ARGS  install.ps1에 덧붙일 인자(가짜 adb 등).
    AUTORUN이 끝나면 VN_GUI_SMOKE 파일에 기록 칸 전체와 'BANNER ok|fail <내용>'을 쓰고 닫는다.
  Windows 기본 powershell.exe(5.1) + WinForms만 쓴다. 이 파일은 UTF-8 BOM + CRLF로 둔다(5.1이 한글을 읽도록).
#>
Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
[System.Windows.Forms.Application]::EnableVisualStyles()

$ROOT = Split-Path -Parent $MyInvocation.MyCommand.Path
$Script = Join-Path $ROOT 'install.ps1'
$UsbOff = "USB 디버깅 끄기: 시동을 껐다 켜면 저절로 꺼집니다. 바로 끄려면 Factory Test에서 0A 01 → 65 00 → 0A 02 (또는 '내 앱' → USB 디버깅 → OFF)"
$Smoke = $env:VN_GUI_SMOKE; $Auto = $env:VN_GUI_AUTORUN

# 이 설치기의 버전·채널 (install.ps1 맨 위의 두 줄)
$verText = '설치기 파일(install.ps1)이 없습니다. 설치기 ZIP을 다시 받아 압축을 푸세요.'
if(Test-Path -LiteralPath $Script){
  $src = [IO.File]::ReadAllText($Script)
  $v = ([regex]::Match($src, "\`$InstallerVersion = '([^']+)'")).Groups[1].Value
  $c = ([regex]::Match($src, "\`$InstallerChannel = '([^']+)'")).Groups[1].Value
  $verText = "설치기 $v · " + $(if($c -eq 'test'){ '테스트 채널(test 사전 배포에서 새 설치기를 찾습니다)' } else { '안정화 채널' })
}

$font = New-Object System.Drawing.Font('Malgun Gothic', 10)
$bold = New-Object System.Drawing.Font('Malgun Gothic', 10, [System.Drawing.FontStyle]::Bold)
$form = New-Object System.Windows.Forms.Form
$form.Text = 'i30 PD 대시보드 설치'
$form.Font = $font
$form.ClientSize = New-Object System.Drawing.Size(740, 860)
$form.MinimumSize = New-Object System.Drawing.Size(620, 680)
$form.StartPosition = 'CenterScreen'

$layout = New-Object System.Windows.Forms.TableLayoutPanel
$layout.Dock = 'Fill'; $layout.Padding = New-Object System.Windows.Forms.Padding(12); $layout.ColumnCount = 1
$form.Controls.Add($layout)
function Row($control, [string]$sizeType = 'AutoSize', [float]$h = 0){
  $control.Dock = 'Fill'; $control.Margin = New-Object System.Windows.Forms.Padding(0, 4, 0, 4)
  $style = New-Object System.Windows.Forms.RowStyle([System.Windows.Forms.SizeType]::$sizeType, $h)
  # 줄 번호를 정해 넣는다. 자동 배치에 맡기면 처음에 숨긴 완료 칸($banner)이 나중에 보일 때 기록 칸 뒤의
  # '남은 공간 전부' 줄로 밀려 기록 칸이 한 줄로 찌그러진다(원작 2ba017c, Windows 실기 화면).
  $layout.RowCount = $layout.RowStyles.Add($style) + 1; $layout.Controls.Add($control, 0, $layout.RowCount - 1)
}
function Label([string]$t, $f = $font, $color = [System.Drawing.Color]::Black){
  $l = New-Object System.Windows.Forms.Label; $l.Text = $t; $l.Font = $f; $l.ForeColor = $color; $l.AutoSize = $true
  $l.MaximumSize = New-Object System.Drawing.Size(700, 0); return $l
}
function New-Box([string]$t){ $g = New-Object System.Windows.Forms.GroupBox; $g.Text = $t; $g.AutoSize = $true; return $g }
function New-Flow(){ $p = New-Object System.Windows.Forms.FlowLayoutPanel; $p.AutoSize = $true; $p.Dock = 'Fill'; return $p }
function Btn($panel, [string]$t, [int]$w = 120){ $b = New-Object System.Windows.Forms.Button; $b.Text = $t; $b.Size = New-Object System.Drawing.Size($w, 40); $panel.Controls.Add($b); return $b }

Row (Label 'i30 PD 대시보드 설치' (New-Object System.Drawing.Font('Malgun Gothic', 15, [System.Drawing.FontStyle]::Bold)))
$verLabel = Label $verText $bold ([System.Drawing.Color]::FromArgb(0, 90, 140)); Row $verLabel
Row (Label '현대자동차 공식 앱이 아닙니다. 순정 시스템에 앱을 설치하는 작업이라 보증/AS 불이익이 있을 수 있고 책임은 본인에게 있습니다. 모든 작업은 정차 · 시동 켠 상태에서 하세요.' $font ([System.Drawing.Color]::FromArgb(150, 90, 0)))

# 차 화면에서 사람이 직접 (콘솔의 '2) 차 화면에서 먼저'와 같은 순서)
$steps = New-Box '차 화면에서 먼저 (직접)'
$sl = New-Object System.Windows.Forms.TableLayoutPanel; $sl.Dock = 'Fill'; $sl.AutoSize = $true; $sl.ColumnCount = 2
[void]$sl.ColumnStyles.Add((New-Object System.Windows.Forms.ColumnStyle([System.Windows.Forms.SizeType]::Percent, 100)))
[void]$sl.ColumnStyles.Add((New-Object System.Windows.Forms.ColumnStyle([System.Windows.Forms.SizeType]::AutoSize)))
$stepText = Label ("1. 설정 → 시스템 정보 → 저장 공간 화면`n" +
  "2. 내용 영역 좌하단 모서리 5회 → 우하단 모서리 1회 탭(엔지니어 모드), 암호 0567`n" +
  "3. Diagnosis → Factory Test 에서 오른쪽 세 줄을 한 줄씩 입력하고 매번 SEND`n" +
  "4. USB 케이블로 차와 이 PC를 연결한 뒤 아래 [설치]`n" +
  "   ('내 앱'이 있으면 '내 앱' → USB 디버깅 → ON 으로 2·3번 대신)")
$stepText.MaximumSize = New-Object System.Drawing.Size(540, 0)
$codes = New-Object System.Windows.Forms.TextBox
$codes.Multiline = $true; $codes.ReadOnly = $true; $codes.Text = "0A 01`r`n65 01`r`n0A 02"
$codes.Font = New-Object System.Drawing.Font('Consolas', 13, [System.Drawing.FontStyle]::Bold); $codes.Size = New-Object System.Drawing.Size(110, 80)
$copy = New-Object System.Windows.Forms.Button; $copy.Text = '복사'; $copy.AutoSize = $true
$copy.Add_Click({ [System.Windows.Forms.Clipboard]::SetText("0A 01`r`n65 01`r`n0A 02"); $copy.Text = '복사됨' })
$codeBox = New-Object System.Windows.Forms.FlowLayoutPanel; $codeBox.FlowDirection = 'TopDown'; $codeBox.AutoSize = $true
$codeBox.Controls.Add($codes); $codeBox.Controls.Add($copy)
$sl.Controls.Add($stepText, 0, 0); $sl.Controls.Add($codeBox, 1, 0)
$steps.Controls.Add($sl)
Row $steps

# 받을 버전 (콘솔 1·2번)
$chGroup = New-Box '받을 버전 ([설치]에만 쓰입니다)'; $chFlow = New-Flow
$stable = New-Object System.Windows.Forms.RadioButton; $stable.Text = '안정화 (권장) — 실차에서 확인된 버전'; $stable.AutoSize = $true; $stable.Checked = $true
$test = New-Object System.Windows.Forms.RadioButton; $test.Text = '테스트 — 새 기능, 아직 실차 검증 전'; $test.AutoSize = $true
$chFlow.Controls.Add($stable); $chFlow.Controls.Add($test); $chGroup.Controls.Add($chFlow)
Row $chGroup

# 단추
$buttons = New-Flow; $buttons.Dock = 'Fill'
$installBtn = Btn $buttons '설치'; $installBtn.Font = $bold; $installBtn.BackColor = [System.Drawing.Color]::FromArgb(61, 220, 132)
$removeBtn = Btn $buttons '삭제'; $diagBtn = Btn $buttons '진단 기록'; $cancelBtn = Btn $buttons '취소'; $cancelBtn.Enabled = $false
$fileBtn = Btn $buttons '파일 보기'; $fileBtn.Visible = $false
$updateBtn = Btn $buttons '업데이트' 150; $updateBtn.Visible = $false; $updateBtn.BackColor = [System.Drawing.Color]::FromArgb(255, 214, 102)
Row $buttons

# 시동 안내 화면 (콘솔 4·5번)
$adv = New-Box '시동 안내 화면 (고급, 콘솔 설치기 4·5번)'
$al = New-Object System.Windows.Forms.TableLayoutPanel; $al.Dock = 'Fill'; $al.AutoSize = $true; $al.ColumnCount = 1
$advText = Label ("시동 때 뜨는 순정 안내 화면을 10초 대신 약 1초 뒤 닫히게 합니다. 이 차의 순정 시스템 화면(SystemUI)을 받아 이 PC에서 고쳐 설치하고, " +
  "처음 한 번은 Java와 도구(약 50 MB)를 받습니다. 내비 소프트웨어 업데이트 전에는 꼭 [5번: 원래대로]를 먼저 하세요.")
$advText.MaximumSize = New-Object System.Drawing.Size(680, 0)
$advFlow = New-Flow
$fastBtn = Btn $advFlow '4번: 10초 → 1초' 170; $stockBtn = Btn $advFlow '5번: 원래대로' 170
$al.Controls.Add($advText, 0, 0); $al.Controls.Add($advFlow, 0, 1); $adv.Controls.Add($al)
Row $adv

$status = Label '' $bold ([System.Drawing.Color]::FromArgb(180, 100, 0))
Row $status
$banner = Label '' $bold; $banner.Visible = $false; $banner.Padding = New-Object System.Windows.Forms.Padding(8)
Row $banner

$log = New-Object System.Windows.Forms.TextBox
$log.Multiline = $true; $log.ReadOnly = $true; $log.ScrollBars = 'Vertical'; $log.WordWrap = $true
$log.Font = New-Object System.Drawing.Font('Malgun Gothic', 9); $log.BackColor = [System.Drawing.Color]::White
Row $log 'Percent' 100
$intro = "[설치]는 차(pd_kr)에만 두 앱을 설치합니다. 차가 아닌 기기에는 아무것도 하지 않습니다.`r`n" +
  "[진단 기록]은 차를 읽기만 해서 문제를 알릴 때 보낼 파일을 바탕화면에 만듭니다.`r`n" +
  "콘솔(번호 입력)이 편하면 같은 폴더의 설치.cmd를 쓰세요.`r`n"
$log.AppendText($intro)

# --- 자식 프로세스 (install.ps1 -Gui) ---------------------------------------------
$script:proc = $null; $script:outTask = $null; $script:errTask = $null; $script:done = $false; $script:file = $null
$script:waitMsg = $null; $script:waitBase = 0; $script:waitAt = [Diagnostics.Stopwatch]::new(); $script:lastSaid = ''
$script:mode = ''; $script:openPath = $null
$PS = Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\powershell.exe'

function Say([string]$m){ $log.AppendText($m + "`r`n") }
function Idle([bool]$on){ foreach($b in $installBtn, $removeBtn, $diagBtn, $updateBtn, $fastBtn, $stockBtn, $stable, $test){ $b.Enabled = $on }; $cancelBtn.Enabled = -not $on }
function Finish([bool]$ok, [string]$msg){
  if($script:done){ return }; $script:done = $true
  $status.Text = ''; $script:waitMsg = $null
  $banner.BackColor = $(if($ok){ [System.Drawing.Color]::FromArgb(210, 245, 220) } else { [System.Drawing.Color]::FromArgb(250, 215, 210) })
  $banner.Text = $(if($ok){ "완료했습니다. $msg" } else { "중단했습니다: $msg`n기록 칸 내용과 기록 파일을 함께 보내 주시면 원인을 볼 수 있습니다." }) +
    $(if($script:mode -ne 'update'){ "`n$UsbOff" } else { '' })
  $banner.Visible = $true
  Idle $true
  if($Smoke -and $Auto){ [IO.File]::WriteAllText($Smoke, $log.Text + "`r`nBANNER $(if($ok){ 'ok' } else { 'fail' }) $msg`r`n", (New-Object Text.UTF8Encoding $false)); $form.Close(); return }
  # 업데이트를 받았으면 새 창 설치기를 열고 이 창은 닫는다.
  if($ok -and $script:mode -eq 'update' -and $script:openPath){
    try { Start-Process -FilePath $script:openPath -WorkingDirectory (Split-Path -Parent $script:openPath); $form.Close() } catch { Say "새 설치기를 열지 못했습니다: $($_.Exception.Message)`r`n직접 여세요: $($script:openPath)" }
  }
}
$Defaults = @{ install = "차 화면에 '내 앱'이 열립니다."; uninstall = '삭제했습니다.'; diag = '진단 기록 파일을 만들었습니다([파일 보기]).'
               bootfast = '시동 안내 화면 1초를 설치했습니다.'; bootfastremove = '시동 안내 화면을 원래대로 했습니다.'; update = '이미 최신 설치기입니다.' }
function Answer([string]$a){
  # ASCII 바이트로 직접 쓴다: StandardInput(StreamWriter)은 콘솔 입력 인코딩이 UTF-8이면 첫 쓰기에 BOM을 붙인다(원작 a2f08a1).
  try { $b = [Text.Encoding]::ASCII.GetBytes("$a`r`n"); $script:proc.StandardInput.BaseStream.Write($b, 0, $b.Length); $script:proc.StandardInput.BaseStream.Flush() } catch {}
}
function Line([string]$l){
  $l = $l.TrimStart([char]0xFEFF).TrimEnd("`r")
  if($l -match '^@@STEP (.*)$'){ $status.Text = $Matches[1]; $script:waitMsg = $null; return }
  if($l -match '^@@WAIT (\d+) (.*)$'){ $script:waitBase = [int]$Matches[1]; $script:waitMsg = $Matches[2]; $script:waitAt.Restart(); return }
  if($l -match '^@@ASK (.*)$'){
    $q = $Matches[1]
    if($Auto){ $yes = ($env:VN_GUI_ANSWER -eq 'y') }
    else {
      # 바로 앞에 찍힌 줄(주의·안내)을 함께 보여 준다: 질문만으로는 무엇을 묻는지 모를 때가 있다(예: '진행할까요?').
      $text = $(if($script:lastSaid.Trim()){ $script:lastSaid.Trim() + "`n`n" + $q } else { $q })
      $yes = ([System.Windows.Forms.MessageBox]::Show($form, $text, '확인', 'YesNo', 'Question') -eq 'Yes')
    }
    Say "$q → $(if($yes){ '예' } else { '아니오' })"; Answer $(if($yes){ 'y' } else { 'n' })
    return
  }
  if($l -match '^@@FILE (.*)$'){ $script:file = $Matches[1]; $fileBtn.Visible = $true; return }
  if($l -match '^@@OPEN (.*)$'){ $script:openPath = $Matches[1]; Say "새 설치기: $($Matches[1])"; return }
  if($l -match '^@@DONE ok ?(.*)$'){ $m = $Matches[1].Trim(); if(-not $m){ $m = $Defaults[$script:mode] }; Finish $true $m; return }
  if($l -match '^@@DONE fail ?(.*)$'){ Finish $false $Matches[1]; return }
  if($l -match '^@@'){ return }
  $script:waitMsg = $null
  Say $l; if($l.Trim()){ $script:lastSaid = $l }
}
function Start-Run([string]$mode){
  $script:mode = $mode; $script:done = $false; $script:file = $null; $script:openPath = $null; $script:lastSaid = ''
  $fileBtn.Visible = $false; $banner.Visible = $false; $copy.Text = '복사'; $log.Clear(); Idle $false
  $extra = @{ install = " -Channel $(if($test.Checked){ 'test' } else { 'stable' })"; uninstall = ' -Uninstall'; diag = ' -Diag'
              bootfast = ' -BootFast'; bootfastremove = ' -BootFastRemove'; update = ' -Update' }[$mode]
  $psi = New-Object System.Diagnostics.ProcessStartInfo
  $psi.FileName = $PS
  $psi.Arguments = "-NoProfile -ExecutionPolicy Bypass -File `"$Script`" -Gui$extra $env:VN_GUI_ARGS"
  $psi.UseShellExecute = $false; $psi.CreateNoWindow = $true
  $psi.RedirectStandardOutput = $true; $psi.RedirectStandardError = $true; $psi.RedirectStandardInput = $true
  $psi.StandardOutputEncoding = New-Object System.Text.UTF8Encoding($false)
  $psi.StandardErrorEncoding = New-Object System.Text.UTF8Encoding($false)
  $psi.WorkingDirectory = $ROOT
  try { $script:proc = [System.Diagnostics.Process]::Start($psi) } catch { Say "install.ps1을 시작하지 못했습니다: $($_.Exception.Message)"; Finish $false '시작 실패'; return }
  $script:outTask = $script:proc.StandardOutput.ReadLineAsync(); $script:errTask = $script:proc.StandardError.ReadLineAsync()
  $timer.Start()
}

# 화면 스레드에서 0.2초마다: 다 읽힌 줄을 처리하고 다음 줄을 기다린다(창이 멈추지 않게). 기다리는 줄에는 경과 초를 붙인다.
$timer = New-Object System.Windows.Forms.Timer; $timer.Interval = 200
# @@ASK의 예/아니오 창이 떠 있는 동안에도 타이머는 돈다. 다시 들어오지 않게 막고, 줄을 처리하기 전에 다음 줄 읽기를 건다.
# (막지 않으면 같은 @@ASK 줄로 질문 창이 계속 겹쳐 뜨다 '호출 수준 오버플로'로 죽었다 — 원작 2ba017c)
$script:inTick = $false
$timer.Add_Tick({
  if($script:inTick){ return }; $script:inTick = $true
  try {
  if($script:proc){
    for($i = 0; $i -lt 200 -and $script:outTask -and $script:outTask.IsCompleted; $i++){
      $l = $script:outTask.Result
      if($null -eq $l){ $script:outTask = $null; break }
      $script:outTask = $script:proc.StandardOutput.ReadLineAsync(); Line $l
      if(-not $script:proc){ break }
    }
    if(-not $script:proc){ return }
    for($i = 0; $i -lt 200 -and $script:errTask -and $script:errTask.IsCompleted; $i++){
      $l = $script:errTask.Result
      if($null -eq $l){ $script:errTask = $null; break }
      if($l.Trim()){ Say $l }; $script:errTask = $script:proc.StandardError.ReadLineAsync()
    }
    if(-not $script:outTask -and -not $script:errTask -and $script:proc.HasExited){
      if(-not $script:done){ Finish $false "설치기가 끝났습니다(종료 코드 $($script:proc.ExitCode))." }
      $script:proc = $null
    }
  }
  if($script:waitMsg){ $status.Text = "⏳ $($script:waitMsg)  $($script:waitBase + [int]$script:waitAt.Elapsed.TotalSeconds)초" }
  # 새 설치기 확인(창이 뜰 때 한 번): 끝나면 '@@UPDATE <버전>'이 있는지 본다.
  if($script:chk -and $script:chk.HasExited){
    $o = ''; try { $o = $script:chkOut.Result } catch {}
    $script:chk = $null
    if($o -match '@@UPDATE (\S+)'){ $script:newVersion = $Matches[1]; $updateBtn.Text = "업데이트 $($Matches[1])"; $updateBtn.Visible = $true
      Say "새 설치기 $($Matches[1])이(가) 나왔습니다. [업데이트 $($Matches[1])]를 누르면 받아서 확인한 뒤 새 창 설치기를 엽니다." }
  }
  } finally { $script:inTick = $false }
})

$installBtn.Add_Click({ Start-Run 'install' })
$diagBtn.Add_Click({ Start-Run 'diag' })
$fastBtn.Add_Click({ Start-Run 'bootfast' })      # 설치기가 차를 확인한 뒤 '진행할까요?'를 묻는다(콘솔과 같음)
$stockBtn.Add_Click({ Start-Run 'bootfastremove' })
$removeBtn.Add_Click({
  # 콘솔 삭제.cmd는 묻지 않지만, 창에서는 잘못 누르기 쉬워 한 번 묻는다.
  $q = "차에서 대시보드와 '내 앱', 그 설정을 지웁니다.`n`n☆ 버튼을 계기판으로 써 왔다면 원래대로(없음) 되돌립니다. " +
       "시동 안내 화면 1초가 깔려 있으면 함께 되돌릴지 따로 묻습니다.`n`n지울까요?"
  if([System.Windows.Forms.MessageBox]::Show($form, $q, '삭제', 'YesNo', 'Warning') -eq 'Yes'){ Start-Run 'uninstall' }
})
$updateBtn.Add_Click({
  $q = "새 설치기 $($script:newVersion)을(를) 받습니다.`n`n릴리스 정보의 크기·SHA-256으로 확인한 뒤 이 폴더의 설치기 파일을 바꾸고, 새 창 설치기를 열고 이 창은 닫습니다. 차에는 아무것도 하지 않습니다.`n`n받을까요?"
  if([System.Windows.Forms.MessageBox]::Show($form, $q, '업데이트', 'YesNo', 'Question') -eq 'Yes'){ Start-Run 'update' }
})
function Stop-Child(){ if($script:proc -and -not $script:proc.HasExited){ & taskkill.exe /T /F /PID $script:proc.Id *> $null } }
$cancelBtn.Add_Click({ Stop-Child; Say '취소했습니다. 설치 중이었다면 차에서는 그 단계가 끝날 수 있습니다. 다시 실행하면 이어서 맞춥니다.'; Finish $false '취소' })
$fileBtn.Add_Click({ if($script:file){ Start-Process explorer.exe "/select,`"$($script:file)`"" } })
$form.Add_FormClosing({ Stop-Child })

# 창이 뜨면: 숨은 창에서 새 설치기를 확인한다(인터넷이 없으면 아무 일도 없다). 시험 때는 그 대신 단추를 누르거나 닫는다.
$script:chk = $null; $script:chkOut = $null; $script:newVersion = ''
$form.Add_Shown({
  $timer.Start()
  if($Auto){ Start-Run $Auto; return }
  if($Smoke){
    $script:smokeT = New-Object System.Windows.Forms.Timer; $script:smokeT.Interval = 1000
    $script:smokeT.Add_Tick({ $script:smokeT.Stop(); [IO.File]::WriteAllText($Smoke, "$($form.Text)`r`n$($verLabel.Text)`r`nLOG $($log.Height)`r`n", (New-Object Text.UTF8Encoding $false)); $form.Close() })
    $script:smokeT.Start(); return
  }
  try {
    $c = New-Object System.Diagnostics.ProcessStartInfo
    $c.FileName = $PS; $c.Arguments = "-NoProfile -ExecutionPolicy Bypass -File `"$Script`" -CheckUpdate"
    $c.UseShellExecute = $false; $c.CreateNoWindow = $true; $c.RedirectStandardOutput = $true
    $c.StandardOutputEncoding = New-Object System.Text.UTF8Encoding($false); $c.WorkingDirectory = $ROOT
    $script:chk = [System.Diagnostics.Process]::Start($c); $script:chkOut = $script:chk.StandardOutput.ReadToEndAsync()
  } catch {}
})
[void]$form.ShowDialog()
