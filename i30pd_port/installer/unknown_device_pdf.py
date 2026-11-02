"""i30 PD 대시보드 — PC에 '알 수 없는 장치'로 뜰 때 안내 PDF (A4)."""
import sys
from reportlab.lib.pagesizes import A4
from reportlab.lib import colors
from reportlab.lib.units import mm
from reportlab.lib.styles import ParagraphStyle
from reportlab.platypus import (SimpleDocTemplate, Paragraph, Spacer, Table, TableStyle, KeepTogether)
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont

pdfmetrics.registerFont(TTFont('Malgun', r'C:\Windows\Fonts\malgun.ttf'))
pdfmetrics.registerFont(TTFont('MalgunB', r'C:\Windows\Fonts\malgunbd.ttf'))
pdfmetrics.registerFontFamily('Malgun', normal='Malgun', bold='MalgunB', italic='Malgun', boldItalic='MalgunB')

INK = colors.HexColor('#1d2326'); MUTED = colors.HexColor('#5b6b73'); ACCENT = colors.HexColor('#1f6f9f')
WARN = colors.HexColor('#b4541a'); LINE = colors.HexColor('#c9d3d8'); SOFT = colors.HexColor('#eef4f7'); WARNBG = colors.HexColor('#fdf1e8')

base = ParagraphStyle('b', fontName='Malgun', fontSize=10, leading=15.5, textColor=INK)
small = ParagraphStyle('s', parent=base, fontSize=8.5, leading=12.5, textColor=MUTED)
title = ParagraphStyle('t', parent=base, fontName='MalgunB', fontSize=19, leading=25)
sub = ParagraphStyle('st', parent=base, fontSize=10.5, leading=15, textColor=MUTED)
h1 = ParagraphStyle('h1', parent=base, fontName='MalgunB', fontSize=13.5, leading=19, textColor=ACCENT, spaceBefore=10, spaceAfter=4)
h2 = ParagraphStyle('h2', parent=base, fontName='MalgunB', fontSize=11, leading=16, spaceBefore=6, spaceAfter=2)
cell = ParagraphStyle('c', parent=base, fontSize=9.2, leading=13.5)
cellb = ParagraphStyle('cb', parent=cell, fontName='MalgunB')

def P(t, s=base): return Paragraph(t, s)
def code(t):  # Courier has no Hangul and Malgun draws \ as the won sign: split into ASCII / Korean runs
    import itertools
    runs = itertools.groupby(t, str.isascii)
    return ''.join(f'<font name="{"Courier-Bold" if a else "MalgunB"}" color="#1f6f9f">{"".join(s)}</font>' for a, s in runs)
def ui(t): return f'<b>[{t}]</b>'

def steps(items):
    rows = [[P(f'<b>{i}</b>', ParagraphStyle('n', parent=cellb, alignment=1, textColor=colors.white)), P(t, cell)]
            for i, t in enumerate(items, 1)]
    tb = Table(rows, colWidths=[8*mm, 160*mm])
    tb.setStyle(TableStyle([
        ('BACKGROUND', (0, 0), (0, -1), ACCENT), ('VALIGN', (0, 0), (-1, -1), 'MIDDLE'),
        ('LINEBELOW', (1, 0), (1, -2), 0.4, LINE), ('LEFTPADDING', (1, 0), (1, -1), 8),
        ('TOPPADDING', (0, 0), (-1, -1), 4), ('BOTTOMPADDING', (0, 0), (-1, -1), 4)]))
    return tb

def box(content, bg=SOFT, edge=ACCENT):
    tb = Table([[content]], colWidths=[168*mm])
    tb.setStyle(TableStyle([('BACKGROUND', (0, 0), (-1, -1), bg), ('LINEBEFORE', (0, 0), (0, -1), 3, edge),
                            ('LEFTPADDING', (0, 0), (-1, -1), 10), ('RIGHTPADDING', (0, 0), (-1, -1), 10),
                            ('TOPPADDING', (0, 0), (-1, -1), 7), ('BOTTOMPADDING', (0, 0), (-1, -1), 7)]))
    return tb

story = [
    P('PC에서 차가 “알 수 없는 장치”로 뜰 때', title),
    P('i30 PD 대시보드 설치 — Windows 연결 문제 해결 안내', sub),
    P('현대자동차 공식 앱·문서가 아닙니다. 모든 작업은 정차, 시동 켠 상태에서 하세요. 2026-10-31 작성.', small),
    Spacer(1, 6),
    box(P('엔지니어 모드에서 Factory Test(' + code('0A 01') + ' → ' + code('65 01') + ' → ' + code('0A 02') +
          ')로 USB 디버깅을 켜고 케이블을 꽂았는데, 설치 프로그램이 차를 찾지 못하고 Windows 장치 관리자에 '
          '<b>“알 수 없는 장치”</b>(또는 “DiPO”, “Telechips”)가 보이는 경우의 해결 방법입니다. 맥은 드라이버가 필요 없어 해당하지 않습니다.', cell)),

    P('1. 어떤 경우인지 먼저 확인하기', h1),
    steps([
        '키보드에서 ' + ui('Windows 키 + X') + '를 누르고 ' + ui('장치 관리자') + '를 엽니다.',
        '노란 느낌표(!)가 붙은 장치를 찾습니다. 보통 ' + ui('기타 장치') + ' 또는 ' + ui('범용 직렬 버스 컨트롤러') + ' 아래에 있습니다.',
        '그 장치를 마우스 오른쪽 버튼으로 눌러 ' + ui('속성') + ' → ' + ui('자세히') + ' 탭 → 속성 목록에서 ' + ui('하드웨어 ID') + '를 고릅니다.',
        '아래 표에서 보이는 값을 찾아 해당 경우로 갑니다.',
    ]),
    Spacer(1, 6),
]

t = Table([
    [P('<b>하드웨어 ID에 보이는 값</b>', cell), P('<b>원인</b>', cell), P('<b>할 일</b>', cell)],
    [P(code('USB\\VID_18D1&amp;PID_D00D') + '<br/>또는 ' + code('VID_18D1') + '에 다른 PID<br/>(예: ' + code('PID_DEED&amp;MI_01') + ')', cell),
     P('차는 잘 연결됐지만 Windows에 맞는 드라이버가 없습니다.', cell), P('<b>경우 A</b> — 드라이버 지정', cell)],
    [P(code('USB\\VID_0000&amp;PID_0002') + '<br/>이름: “알 수 없는 USB 장치(장치 설명자 요청 실패)”', cell),
     P('USB 연결 자체가 실패했습니다. 드라이버로는 고쳐지지 않습니다.', cell), P('<b>경우 B</b> — 케이블·포트 확인', cell)],
    [P('그 밖의 값', cell), P('아직 확인되지 않은 기기입니다.', cell), P('<b>“그래도 안 되면”</b>대로 알려 주세요', cell)],
], colWidths=[62*mm, 58*mm, 48*mm])
t.setStyle(TableStyle([('GRID', (0, 0), (-1, -1), 0.5, LINE), ('BACKGROUND', (0, 0), (-1, 0), SOFT),
                       ('VALIGN', (0, 0), (-1, -1), 'MIDDLE'), ('TOPPADDING', (0, 0), (-1, -1), 5), ('BOTTOMPADDING', (0, 0), (-1, -1), 5)]))
story += [t, Spacer(1, 4),
          P('차는 USB 디버깅을 켜면 Google 장치 번호(' + code('VID_18D1') + ')로 PC에 나타납니다. 확인된 차(블루링크 있는 내비)는 '
            + code('PID_D00D') + '였고, 블루링크가 없는 모델 등 내비가 다르면 뒷번호가 다를 수 있습니다. Google 공식 USB 드라이버의 장치 목록에 이 번호들이 없어서 '
            'Windows가 드라이버를 자동으로 붙이지 못합니다. 고장이 아니며, 처음 한 번만 직접 지정하면 됩니다.', small),

          P('경우 A. 드라이버 지정하기 (VID_18D1)', h1),
          P('방법 1 — 설치 프로그램의 도움 받기 (가장 쉬움)', h2),
          P(code('설치.cmd') + '를 실행한 채 차를 기다리는 동안 설치 프로그램이 이 상태를 스스로 알아보고 안내를 띄웁니다. '
            '“Google 공식 USB 드라이버를 받을까요?”에 ' + ui('Y') + ', “장치 관리자를 열까요?”에 ' + ui('Y') + '를 누르면 '
            '드라이버를 받아 풀고 장치 관리자를 열어 줍니다. 드라이버 파일 위치는 창에 표시됩니다(보통 '
            + code('%LOCALAPPDATA%\\i30pd-installer\\usb_driver\\android_winusb.inf') + '). 그다음 아래 방법 2의 3번부터 하면 됩니다.', base),
          P('방법 2 — 직접 하기', h2),
          steps([
              'Google USB 드라이버를 받아 압축을 풉니다: ' + code('developer.android.com/studio/run/win-usb') +
              '<br/>(설치 프로그램이 이미 받아 두었다면 건너뜁니다.)',
              '장치 관리자에서 느낌표가 붙은 그 장치를 오른쪽 버튼으로 눌러 ' + ui('드라이버 업데이트') + '를 고릅니다.',
              ui('내 컴퓨터에서 드라이버 찾아보기') + ' → ' + ui('컴퓨터의 사용 가능한 드라이버 목록에서 직접 선택') + '을 누릅니다.',
              '장치 종류를 묻는 목록이 나오면 ' + ui('모든 장치 표시') + '를 고르고 ' + ui('다음') + '을 누릅니다.',
              ui('디스크 있음...') + ' → ' + ui('찾아보기') + '에서 압축을 푼 폴더의 ' + code('android_winusb.inf') + '를 고르고 ' + ui('확인') + '을 누릅니다.',
              '모델 목록에서 <b>Android ADB Interface</b>를 고르고 ' + ui('다음') + '을 누릅니다. '
              '단, 하드웨어 ID 끝에 ' + code('&amp;MI_01') + '처럼 ' + code('MI_') + '가 붙어 있으면 <b>Android Composite ADB Interface</b>를 고릅니다.',
              '“드라이버 업데이트 경고 — 호환되지 않을 수 있음” 창이 뜨면 ' + ui('예') + '를 누릅니다. 이 장치용으로 맞는 선택입니다.',
              '장치 관리자에 ' + ui('Android Device') + ' → <b>Android ADB Interface</b>가 느낌표 없이 보이면 끝입니다.',
          ]),
          Spacer(1, 6),
          box(P('<b>그다음:</b> ' + code('설치.cmd') + '가 아직 기다리고 있으면 저절로 이어서 설치합니다. 창을 닫았다면 다시 실행합니다. '
                '차 화면에 <b>“USB 디버깅을 허용하시겠습니까?”</b> 창이 뜨면 ' + ui('항상 허용') + '을 누릅니다.', cell)),
          P('방법 3 — Zadig 사용 (방법 2가 안 될 때)', h2),
          P(code('zadig.akeo.ie') + '에서 Zadig를 받아 실행 → ' + ui('Options') + ' → ' + ui('List All Devices') +
            ' 체크 → 목록에서 그 장치(DiPO / Telechips 등, USB ID가 ' + code('18D1') + '로 시작)를 고르고 → 드라이버를 <b>WinUSB</b>로 두고 '
            + ui('Install Driver') + '. 다른 장치(마우스·키보드 등)를 고르지 않도록 USB ID를 꼭 확인하세요.', base),
          Spacer(1, 4),
          box(P('<b>하지 마세요:</b> ' + code('android_winusb.inf') + ' 파일을 열어 ' + code('D00D') + ' 줄을 직접 추가하는 것. '
                '파일 서명이 깨져 Windows 10/11이 설치를 거부합니다.', cell), WARNBG, WARN),

          P('경우 B. “장치 설명자 요청 실패” (VID_0000 &amp; PID_0002)', h1),
          P('PC와 차가 USB로 처음 통신하는 단계에서 실패한 상태입니다. 드라이버를 깔아도 해결되지 않습니다. '
            '블루링크 유무와 상관없이 같은 내비 소프트웨어이므로 아래를 위에서부터 하나씩 확인합니다.', base),
          Spacer(1, 4),
          box(P('<b>가장 먼저: Factory Test 코드를 다시 입력하세요.</b> 실제로 이 오류가 난 차가 코드 입력 실수였고, 다시 입력하자 바로 설치됐습니다. '
                '케이블을 뺀 상태에서 ' + code('0A 01') + ', ' + code('65 01') + ', ' + code('0A 02') + '를 <b>띄어쓰기까지 정확히</b>, '
                '앞 내용을 지우고 한 줄씩 입력해 ' + ui('SEND') + '합니다(' + code('6501') + '처럼 붙여 쓰거나 여러 줄을 한 번에 보내면 안 됩니다). 그다음 케이블을 꽂습니다.', cell)),
          Spacer(1, 4),
          steps([
              '<b>차의 USB 포트</b>: 충전 전용이 아니라 카플레이·USB 음악에 쓰는 데이터 포트에 꽂습니다. 음악이 든 USB 메모리를 꽂아 내비에서 재생되는 포트가 데이터 포트입니다. 포트가 여러 개면 다른 포트도 시험합니다.',
              '<b>내비 상태 초기화</b>: 휴대폰(카플레이·안드로이드 오토)과 USB 기기를 모두 뺍니다. 시동을 껐다 켜서 내비를 재시작한 뒤 엔지니어 모드와 Factory Test부터 다시 합니다.',
              '<b>케이블</b>: 짧고 데이터를 지원하는 USB A–A 케이블을 씁니다(성공 사례). A–C 케이블 + 변환 젠더, 연장선, 긴 케이블은 피합니다.',
              '<b>PC 포트</b>: 노트북 본체에 직접 꽂습니다(허브·도킹·모니터 USB 제외). 가능하면 USB 2.0 포트(검은색)를 씁니다. USB 3 포트(파란색)에서 이 오류가 나는 경우가 있습니다.',
              '<b>Windows 정리</b>: 장치 관리자에서 “알 수 없는 USB 장치”를 오른쪽 버튼 → ' + ui('디바이스 제거') + ' → 케이블을 다시 꽂거나 ' + ui('동작') + ' → ' + ui('하드웨어 변경 사항 검색') + '.',
              '<b>다른 PC</b>: 다른 노트북(맥도 됨)에서도 같으면 차 쪽 포트·케이블 문제, 다른 PC에서 되면 원래 PC의 USB 문제입니다.',
              '하드웨어 ID가 ' + code('VID_18D1') + '로 시작하는 장치로 바뀌면 경우 A로 갑니다.',
          ]),
          Spacer(1, 4),
          box(P('<b>주의:</b> A–A 케이블은 양쪽 기기가 모두 전원을 내보내는 연결입니다. 케이블을 잘라 개조하거나 전원선을 손대지 마세요.', cell), WARNBG, WARN),

          KeepTogether([P('그래도 안 되면', h1),
          P('아래 네 가지를 ' + code('github.com/sung9155/i30-pd-dashboard/issues') + '에 올려 주세요. 기기가 달라 생기는 문제는 이 정보가 있어야 고칠 수 있습니다.', base),
          steps([
              '설치 기록 파일 ' + code('%LOCALAPPDATA%\\i30pd-installer\\install_날짜.log') + ' (오류 상태인 USB 장치 ID가 기록됩니다)',
              '장치 관리자에서 그 장치의 하드웨어 ID 화면 사진',
              '차종·연식과 <b>블루링크 유무</b>, 내비 화면 크기',
              '내비 설정 → 시스템 정보 화면 사진(소프트웨어 버전이 보이게)',
          ])]),

          KeepTogether([
              P('설치가 끝나면 — USB 디버깅 끄기', h1),
              P('셋 중 하나로 끕니다. 끄면 노트북 연결이 바로 끊깁니다.', base),
              steps([
                  '<b>시동을 껐다 켭니다.</b> 재시동하면 저절로 꺼집니다.',
                  '<b>Factory Test에서 끄기:</b> ' + code('0A 01') + ' → ' + code('65 00') + ' → ' + code('0A 02') +
                  ' 를 한 줄씩 보냅니다(켤 때의 ' + code('65 01') + ' 대신 ' + code('65 00') + '). 원작 분석에서 나온 순서로, i30 PD 실차에서는 아직 확인 전입니다.',
                  '<b>' + "'내 앱'" + '</b> → ' + ui('USB 디버깅') + ' → ' + ui('OFF') + '를 누릅니다.',
              ]),
          ]),
          ]

def footer(c, doc):
    c.saveState(); c.setFont('Malgun', 8); c.setFillColor(MUTED)
    c.drawString(20*mm, 10*mm, 'i30 PD 대시보드 — “알 수 없는 장치” 해결 안내')
    c.drawRightString(190*mm, 10*mm, f'{doc.page}쪽'); c.restoreState()

doc = SimpleDocTemplate(sys.argv[1], pagesize=A4, leftMargin=21*mm, rightMargin=21*mm, topMargin=18*mm, bottomMargin=18*mm,
                        title='알 수 없는 장치 해결 안내', author='i30 PD dashboard')
doc.build(story, onFirstPage=footer, onLaterPages=footer)
