// i30 PD 대시보드 창 설치기 — macOS (원작 installer/mac/gui.js를 옮겨 고친 것). 같은 폴더의 "창 설치기.command"가 이 파일을
// osacompile(JavaScript, stay-open)로 "i30 PD 설치.app"으로 만들어 연다. 앱은 자기 옆(설치기 폴더)의 설치.command를 --gui로 돌리고
// 출력을 줄 단위로 기록 칸에 보여 준다. 설치 규칙은 모두 설치.command에 있고 이 파일은 화면만 맡는다.
// 콘솔 번호 → 단추: 1)·2) = [받을 버전] + [설치], 3) = [진단 기록], 4)·5) = [시동 안내 화면 (고급)]의 두 단추, 삭제.command = [삭제].
// 설치.command --gui 상태 줄: @@STEP <제목>  @@WAIT <초> <안내>  @@ASK <질문>(NSAlert → y/n을 표준 입력으로)  @@FILE <경로>  @@DONE ok|fail <내용>
// 새 설치기: 창이 뜨면 설치.command --check-update를 뒤에서 돌려 '@@UPDATE <버전>'이 오면 [업데이트]를 보인다. 누르면 --gui --update가
// 이 폴더의 파일을 바꾸고 '@@OPEN <새 창 설치기.command>'를 알린다. 이 앱은 그것을 열고(앱을 새로 만든다) 끝난다.
// 시험용 환경 변수(원작과 같음, 맥에서만): VN_GUI_SMOKE=<파일>  창을 띄운 뒤 그 파일에 창 제목·버전 줄을 쓰고 끝낸다.
//   VN_GUI_AUTORUN=install|uninstall|diag|bootfast|bootfastremove|update  창이 뜨면 바로 그 단추를 누른다(대답은 VN_GUI_ANSWER=y|n, 없으면 n).
//   VN_GUI_KIT=<설치기 폴더>  VN_GUI_ARGS=<설치.command에 덧붙일 인자, 띄어쓰기로 나눔>. 끝나면 VN_GUI_SMOKE 파일에 기록 칸 전체를 쓰고 끝낸다.
ObjC.import('Cocoa');

var USB_OFF = "USB 디버깅 끄기: 시동을 껐다 켜면 저절로 꺼집니다. 바로 끄려면 Factory Test에서 0A 01 → 65 00 → 0A 02 (또는 '내 앱' → USB 디버깅 → OFF)";
var env = $.NSProcessInfo.processInfo.environment;
function getenv(k) { var v = env.objectForKey(k); return v.isNil() ? '' : ObjC.unwrap(v); }
var fm = $.NSFileManager.defaultManager;
var KIT = getenv('VN_GUI_KIT') || ObjC.unwrap($.NSBundle.mainBundle.bundlePath.stringByDeletingLastPathComponent);
var SCRIPT = KIT + '/설치.command';
var WORK = ObjC.unwrap($.NSHomeDirectory()) + '/Library/Logs/i30pd-installer';   // 설치.command의 기록 폴더
var DEFAULTS = { install: "차 화면에 '내 앱'이 열립니다.", uninstall: '삭제했습니다.', diag: '진단 기록 파일을 만들었습니다([파일 보기]).',
  bootfast: '시동 안내 화면 1초를 설치했습니다.', bootfastremove: '시동 안내 화면을 원래대로 했습니다.', update: '이미 최신 설치기입니다.' };
var ARGS = { install: [], uninstall: ['--uninstall'], diag: ['--diag'], bootfast: ['--boot-fast'], bootfastremove: ['--boot-fast-remove'], update: ['--update'] };

var win, logView, statusLabel, bannerLabel, versionLabel, buttons = {}, fileBtn, copyBtn, updateBtn, radioStable, radioTest, target, timer;
var checkTask = null, checkOut = '', newVersion = '', openPath = '';
var task = null, stdinPipe = null, outPath = '', outPos = 0, done = false, mode = '', resultFile = '';
var waitMsg = null, waitBase = 0, waitAt = 0, logText = '', lastSaid = '';

function readVersion() {
  var s = $.NSString.stringWithContentsOfFileEncodingError(SCRIPT, $.NSUTF8StringEncoding, null);
  if (s.isNil()) return '설치.command가 이 앱 옆에 없습니다. 앱을 설치기 폴더에 두고 "창 설치기.command"로 다시 여세요.';
  s = ObjC.unwrap(s);
  var v = /^INSTALLER_VERSION='([^']+)'/m.exec(s), c = /^INSTALLER_CHANNEL='([^']+)'/m.exec(s);
  return '설치기 ' + (v ? v[1] : '?') + ' · ' + (c && c[1] === 'test' ? '테스트 채널(test 사전 배포에서 새 설치기를 찾습니다)' : '안정화 채널');
}

function label(text, x, y, w, h, size, bold, color) {
  var l = $.NSTextField.wrappingLabelWithString(text);
  l.frame = $.NSMakeRect(x, y, w, h);
  l.font = bold ? $.NSFont.boldSystemFontOfSize(size) : $.NSFont.systemFontOfSize(size);
  if (color) l.textColor = color;
  win.contentView.addSubview(l);
  return l;
}
function button(title, sel, x, y, w) {
  var b = $.NSButton.buttonWithTitleTargetAction(title, target, sel);
  b.frame = $.NSMakeRect(x, y, w, 36);
  b.bezelStyle = $.NSBezelStyleRounded;
  win.contentView.addSubview(b);
  return b;
}
function radio(title, x, y, w) {
  var b = $.NSButton.radioButtonWithTitleTargetAction(title, target, 'doChannel:');   // 같은 동작의 라디오끼리 하나만 켜진다
  b.frame = $.NSMakeRect(x, y, w, 22);
  win.contentView.addSubview(b);
  return b;
}
function say(m) {
  logText += m + '\n';
  logView.textStorage.mutableString.appendString(m + '\n');
  logView.scrollRangeToVisible($.NSMakeRange(logView.string.length, 0));
}
function idle(on) {
  ['install', 'uninstall', 'diag', 'bootfast', 'bootfastremove'].forEach(function (k) { buttons[k].enabled = on; });
  radioStable.enabled = on; radioTest.enabled = on;
  if (updateBtn) updateBtn.enabled = on;
  buttons.cancel.enabled = !on;
}
function finish(ok, msg) {
  if (done) return;
  done = true; waitMsg = null; statusLabel.stringValue = '';
  bannerLabel.textColor = ok ? $.NSColor.systemGreenColor : $.NSColor.systemRedColor;
  bannerLabel.stringValue = (ok ? '완료했습니다. ' + msg : '중단했습니다: ' + msg + '\n기록 칸 내용과 기록 파일을 함께 보내 주시면 원인을 볼 수 있습니다.') +
    (mode === 'update' ? '' : '\n' + USB_OFF);
  idle(true);
  var smoke = getenv('VN_GUI_SMOKE');
  if (smoke && getenv('VN_GUI_AUTORUN')) {
    $(logText + '\nBANNER ' + (ok ? 'ok' : 'fail') + ' ' + msg + '\n').writeToFileAtomicallyEncodingError(smoke, true, $.NSUTF8StringEncoding, null);
    $.NSApp.terminate(null);
    return;
  }
  // 업데이트를 받았으면 새 창 설치기(.command: 앱을 새로 만들어 연다)를 열고 이 앱은 끝낸다.
  if (ok && mode === 'update' && openPath) {
    $.NSWorkspace.sharedWorkspace.openURL($.NSURL.fileURLWithPath(openPath));
    $.NSApp.terminate(null);
  }
}
function line(l) {
  l = l.replace(/\x1b\[[0-9;]*m/g, '').replace(/\r/g, '');
  var m;
  if ((m = /^@@STEP (.*)$/.exec(l))) { statusLabel.stringValue = m[1]; waitMsg = null; return; }
  if ((m = /^@@WAIT (\d+) (.*)$/.exec(l))) { waitBase = +m[1]; waitMsg = m[2]; waitAt = Date.now(); return; }
  if ((m = /^@@ASK (.*)$/.exec(l))) {
    var yes;
    if (getenv('VN_GUI_AUTORUN')) yes = getenv('VN_GUI_ANSWER') === 'y';
    else {
      var alert = $.NSAlert.alloc.init;
      alert.messageText = m[1];
      if (lastSaid.trim()) alert.informativeText = lastSaid.trim();   // 바로 앞의 주의·안내 줄: 질문만으로는 무엇을 묻는지 모를 때가 있다
      alert.addButtonWithTitle('예'); alert.addButtonWithTitle('아니오');
      yes = alert.runModal === $.NSAlertFirstButtonReturn;
    }
    say(m[1] + ' → ' + (yes ? '예' : '아니오'));
    stdinPipe.fileHandleForWriting.writeData($(yes ? 'y\n' : 'n\n').dataUsingEncoding($.NSUTF8StringEncoding));
    return;
  }
  if ((m = /^@@FILE (.*)$/.exec(l))) { resultFile = m[1]; fileBtn.hidden = false; return; }
  if ((m = /^@@OPEN (.*)$/.exec(l))) { openPath = m[1]; say('새 설치기: ' + openPath); return; }
  if ((m = /^@@DONE ok ?(.*)$/.exec(l))) { finish(true, m[1].trim() || DEFAULTS[mode]); return; }
  if ((m = /^@@DONE fail ?(.*)$/.exec(l))) { finish(false, m[1]); return; }
  if (/^@@/.test(l)) return;
  waitMsg = null;
  say(l);
  if (l.trim()) lastSaid = l;
}

// 출력 파일에서 새로 생긴 완전한 줄만 읽는다(UTF-8 글자가 중간에 잘리지 않게 마지막 줄바꿈까지만).
function drain() {
  if (!outPath) return;
  var h = $.NSFileHandle.fileHandleForReadingAtPath(outPath);
  if (h.isNil()) return;
  h.seekToFileOffset(outPos);
  var data = h.readDataToEndOfFile;
  h.closeFile;
  if (Number(data.length) === 0) return;
  var nl = data.rangeOfDataOptionsRange($('\n').dataUsingEncoding($.NSUTF8StringEncoding), $.NSDataSearchBackwards, $.NSMakeRange(0, data.length));
  var end = Number(nl.location) + 1, len = Number(data.length);   // the bridge may hand NSRange fields over as strings
  if (!(end > 0 && end <= len)) return;   // NSNotFound: no full line yet
  var chunk = data.subdataWithRange($.NSMakeRange(0, end));
  outPos += end;
  var s = $.NSString.alloc.initWithDataEncoding(chunk, $.NSUTF8StringEncoding);
  if (s.isNil()) return;
  ObjC.unwrap(s).split('\n').slice(0, -1).forEach(line);
}

function shellEnv() {
  var e = env.mutableCopy;
  e.setObjectForKey('/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin:' + getenv('PATH'), 'PATH');   // Finder로 연 앱은 PATH가 짧다
  return e;
}

function start(m) {
  if (task && task.running) return;
  mode = m; done = false; resultFile = ''; openPath = ''; fileBtn.hidden = true; bannerLabel.stringValue = ''; logText = ''; lastSaid = '';
  logView.string = ''; idle(false); copyBtn.title = '복사';
  if (!fm.fileExistsAtPath(SCRIPT)) { say('설치.command를 찾지 못했습니다: ' + SCRIPT); finish(false, '설치 파일 없음'); return; }
  fm.createDirectoryAtPathWithIntermediateDirectoriesAttributesError(WORK, true, $(), null);
  outPath = WORK + '/gui_out.txt'; outPos = 0;
  fm.createFileAtPathContentsAttributes(outPath, $.NSData.data, $());
  var args = [SCRIPT, '--gui'].concat(ARGS[m]);
  if (m === 'install') args.push('--channel', Number(radioTest.state) === 1 ? 'test' : 'stable');
  getenv('VN_GUI_ARGS').split(' ').forEach(function (a) { if (a) args.push(a); });
  task = $.NSTask.alloc.init;
  task.launchPath = '/bin/bash';
  task.arguments = $(args);
  task.environment = shellEnv();
  task.currentDirectoryPath = KIT;
  var out = $.NSFileHandle.fileHandleForWritingAtPath(outPath);
  task.standardOutput = out; task.standardError = out;
  stdinPipe = $.NSPipe.pipe; task.standardInput = stdinPipe;
  task.launch;
}

function cancel() {
  if (task && task.running) {
    var k = $.NSTask.alloc.init; k.launchPath = '/usr/bin/pkill'; k.arguments = $(['-TERM', '-P', String(task.processIdentifier)]); k.launch; k.waitUntilExit;
    task.terminate;
  }
  say('취소했습니다. 설치 중이었다면 차에서는 그 단계가 끝날 수 있습니다. 다시 실행하면 이어서 맞춥니다.');
  finish(false, '취소');
}

// 창이 뜰 때 한 번: 설치.command --check-update를 뒤에서 돌린다(인터넷이 없으면 아무 일도 없다).
function startCheck() {
  try {
    if (!fm.fileExistsAtPath(SCRIPT)) return;
    fm.createDirectoryAtPathWithIntermediateDirectoriesAttributesError(WORK, true, $(), null);
    checkOut = WORK + '/check_out.txt';
    fm.createFileAtPathContentsAttributes(checkOut, $.NSData.data, $());
    checkTask = $.NSTask.alloc.init;
    checkTask.launchPath = '/bin/bash';
    checkTask.arguments = $([SCRIPT, '--check-update']);
    checkTask.environment = shellEnv();
    var out = $.NSFileHandle.fileHandleForWritingAtPath(checkOut);
    checkTask.standardOutput = out; checkTask.standardError = $.NSFileHandle.fileHandleWithNullDevice;
    checkTask.launch;
  } catch (e) { checkTask = null; }
}
function checkDone() {
  if (!checkTask || checkTask.running) return;
  checkTask = null;
  var s = $.NSString.stringWithContentsOfFileEncodingError(checkOut, $.NSUTF8StringEncoding, null);
  var m = s.isNil() ? null : /@@UPDATE (\S+)/.exec(ObjC.unwrap(s));
  if (!m) return;
  newVersion = m[1];
  updateBtn.title = '업데이트 ' + newVersion; updateBtn.hidden = false;
  say('새 설치기 ' + newVersion + '이(가) 나왔습니다. [업데이트 ' + newVersion + ']를 누르면 받아서 확인한 뒤 새 창 설치기를 엽니다.');
}

function tick() {
  checkDone();
  drain();
  if (task && !task.running) {
    drain();
    if (!done) finish(false, '설치기가 끝났습니다(종료 코드 ' + task.terminationStatus + ').');
    task = null;
  }
  if (waitMsg) statusLabel.stringValue = '⏳ ' + waitMsg + '  ' + (waitBase + Math.floor((Date.now() - waitAt) / 1000)) + '초';
}

function confirm(title, text, ok) {
  if (getenv('VN_GUI_AUTORUN')) return true;
  var a = $.NSAlert.alloc.init;
  a.messageText = title; a.informativeText = text;
  a.addButtonWithTitle(ok); a.addButtonWithTitle('취소');
  return a.runModal === $.NSAlertFirstButtonReturn;
}

ObjC.registerSubclass({
  name: 'I30InstallerTarget',
  superclass: 'NSObject',
  protocols: ['NSWindowDelegate'],
  methods: {
    'doInstall:': { types: ['void', ['id']], implementation: function () { start('install'); } },
    'doDiag:': { types: ['void', ['id']], implementation: function () { start('diag'); } },
    // 4번: 설치.command가 차를 확인한 뒤 '진행할까요?'를 묻는다(콘솔과 같음). 5번: 묻지 않는다(콘솔과 같음).
    'doBootFast:': { types: ['void', ['id']], implementation: function () { start('bootfast'); } },
    'doBootRemove:': { types: ['void', ['id']], implementation: function () { start('bootfastremove'); } },
    'doUninstall:': { types: ['void', ['id']], implementation: function () {
      // 콘솔 삭제.command는 묻지 않지만, 창에서는 잘못 누르기 쉬워 한 번 묻는다.
      if (confirm('두 앱을 지울까요?', "차에서 대시보드와 '내 앱', 그 설정을 지웁니다.\n\n☆ 버튼을 계기판으로 써 왔다면 원래대로(없음) 되돌립니다. " +
        '시동 안내 화면 1초가 깔려 있으면 함께 되돌릴지 따로 묻습니다.', '삭제')) start('uninstall');
    } },
    'doCancel:': { types: ['void', ['id']], implementation: function () { cancel(); } },
    'doUpdate:': { types: ['void', ['id']], implementation: function () {
      if (confirm('새 설치기 ' + newVersion + '을(를) 받을까요?', '릴리스 정보의 크기·SHA-256으로 확인한 뒤 이 폴더의 설치기 파일을 바꾸고, 새 창 설치기를 열고 이 창은 닫습니다. 차에는 아무것도 하지 않습니다.', '받기')) start('update');
    } },
    'doChannel:': { types: ['void', ['id']], implementation: function () {} },
    'doCopy:': { types: ['void', ['id']], implementation: function () {
      var pb = $.NSPasteboard.generalPasteboard; pb.clearContents; pb.setStringForType('0A 01\n65 01\n0A 02', $.NSPasteboardTypeString);
      copyBtn.title = '복사됨';
    } },
    'doFile:': { types: ['void', ['id']], implementation: function () {
      if (resultFile) $.NSWorkspace.sharedWorkspace.activateFileViewerSelectingURLs($([$.NSURL.fileURLWithPath(resultFile)]));
    } },
    'tick:': { types: ['void', ['id']], implementation: function () { tick(); } },
    'autorun:': { types: ['void', ['id']], implementation: function () {
      var a = getenv('VN_GUI_AUTORUN'), s = getenv('VN_GUI_SMOKE');
      if (a) { start(a); return; }
      if (s) {
        $(ObjC.unwrap(win.title) + '\n' + ObjC.unwrap(versionLabel.stringValue) + '\n').writeToFileAtomicallyEncodingError(s, true, $.NSUTF8StringEncoding, null);
        $.NSApp.terminate(null);
      }
    } },
    'windowWillClose:': { types: ['void', ['id']], implementation: function () { if (task && task.running) cancel(); $.NSApp.terminate(null); } }
  }
});

function build() {
  target = $.I30InstallerTarget.alloc.init;
  var W = 740, H = 900, x = 20, w = W - 40;
  win = $.NSWindow.alloc.initWithContentRectStyleMaskBackingDefer($.NSMakeRect(0, 0, W, H),
    $.NSWindowStyleMaskTitled | $.NSWindowStyleMaskClosable | $.NSWindowStyleMaskMiniaturizable, $.NSBackingStoreBuffered, false);
  win.title = 'i30 PD 대시보드 설치';
  win.releasedWhenClosed = false;
  win.delegate = target;
  label('i30 PD 대시보드 설치', x, 855, w, 28, 20, true);
  updateBtn = button('업데이트', 'doUpdate:', x + w - 170, 852, 170);
  updateBtn.hidden = true;
  versionLabel = label(readVersion(), x, 828, w, 20, 13, true, $.NSColor.systemBlueColor);
  label('현대자동차 공식 앱이 아닙니다. 순정 시스템에 앱을 설치하는 작업이라 보증/AS 불이익이 있을 수 있고 책임은 본인에게 있습니다. 모든 작업은 정차 · 시동 켠 상태에서 하세요.',
    x, 788, w, 36, 12, false, $.NSColor.systemOrangeColor);
  label('차 화면에서 먼저 (직접)', x, 762, w, 20, 14, true);
  label('1. 설정 → 시스템 정보 → 저장 공간 화면\n' +
    '2. 내용 영역 좌하단 모서리 5회 → 우하단 모서리 1회 탭(엔지니어 모드), 암호 0567\n' +
    '3. Diagnosis → Factory Test 에서 오른쪽 세 줄을 한 줄씩 입력하고 매번 SEND\n' +
    '4. USB 케이블로 차와 이 맥을 연결한 뒤 아래 [설치] (USB-C 맥이면 USB-A 어댑터 필요)\n' +
    "    ('내 앱'이 있으면 '내 앱' → USB 디버깅 → ON 으로 2·3번 대신)", x, 655, 540, 105, 12, false);
  var codes = $.NSTextField.labelWithString('0A 01\n65 01\n0A 02');
  codes.frame = $.NSMakeRect(580, 690, 120, 70);
  codes.font = $.NSFont.monospacedSystemFontOfSizeWeight(17, 0.4);
  codes.selectable = true;
  win.contentView.addSubview(codes);
  copyBtn = button('복사', 'doCopy:', 580, 652, 100);
  label('받을 버전 ([설치]에만 쓰입니다)', x, 622, w, 20, 14, true);
  radioStable = radio('안정화 (권장) — 실차에서 확인된 버전', x, 597, 330);
  radioTest = radio('테스트 — 새 기능, 아직 실차 검증 전', x + 340, 597, 330);
  radioStable.state = 1;
  buttons.install = button('설치', 'doInstall:', x, 550, 120);
  buttons.install.keyEquivalent = '\r';
  buttons.uninstall = button('삭제', 'doUninstall:', x + 130, 550, 120);
  buttons.diag = button('진단 기록', 'doDiag:', x + 260, 550, 120);
  buttons.cancel = button('취소', 'doCancel:', x + 390, 550, 120);
  fileBtn = button('파일 보기', 'doFile:', x + 520, 550, 120);
  fileBtn.hidden = true;
  label('시동 안내 화면 (고급, 콘솔 설치기 4·5번)', x, 515, w, 20, 14, true);
  label('시동 때 뜨는 순정 안내 화면을 10초 대신 약 1초 뒤 닫히게 합니다. 이 차의 순정 시스템 화면(SystemUI)을 받아 이 맥에서 고쳐 설치하고, ' +
    '처음 한 번은 Java와 도구(약 50 MB)를 받습니다. 내비 소프트웨어 업데이트 전에는 꼭 [5번: 원래대로]를 먼저 하세요.', x, 470, w, 42, 12, false);
  buttons.bootfast = button('4번: 10초 → 1초', 'doBootFast:', x, 428, 170);
  buttons.bootfastremove = button('5번: 원래대로', 'doBootRemove:', x + 180, 428, 170);
  statusLabel = label('', x, 398, w, 22, 13, true, $.NSColor.systemOrangeColor);
  bannerLabel = label('', x, 340, w, 55, 12, true);
  var sv = $.NSTextView.scrollableTextView;
  sv.frame = $.NSMakeRect(x, 20, w, 315);
  logView = sv.documentView;
  logView.editable = false;
  logView.font = $.NSFont.monospacedSystemFontOfSizeWeight(11, 0);
  win.contentView.addSubview(sv);
  say('[설치]는 차(pd_kr)에만 두 앱을 설치합니다. 차가 아닌 기기에는 아무것도 하지 않습니다.');
  say('[진단 기록]은 차를 읽기만 해서 문제를 알릴 때 보낼 파일을 바탕화면에 만듭니다.');
  say('번호로 고르는 설치기가 편하면 같은 폴더의 설치.command를 쓰세요.');
  idle(true);
  win.center;
  win.makeKeyAndOrderFront(null);
  $.NSApp.activateIgnoringOtherApps(true);
  timer = $.NSTimer.scheduledTimerWithTimeIntervalTargetSelectorUserInfoRepeats(0.2, target, 'tick:', null, true);
  if (!getenv('VN_GUI_AUTORUN') && !getenv('VN_GUI_SMOKE')) startCheck();
  if (getenv('VN_GUI_SMOKE') || getenv('VN_GUI_AUTORUN'))
    $.NSTimer.scheduledTimerWithTimeIntervalTargetSelectorUserInfoRepeats(1.0, target, 'autorun:', null, false);
}

function run() { build(); }
function reopen() { if (win) win.makeKeyAndOrderFront(null); }
