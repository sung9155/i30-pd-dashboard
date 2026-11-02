# -*- coding: utf-8 -*-
# hscan 슬롯 ↔ CAN 메시지 매칭 (DBC 디코딩 + 공회전·정차 물리 지문).
# usage: py -3 -I hscan_match.py <hyundai_2015_ccan.dbc> <raw log> [slot=MSG ...]
#   인자 없이: 지문별 후보 슬롯 출력.  slot=MSG(예: 0e=EMS19): 그 슬롯을 그 메시지로 전체 디코딩.
# DBC는 opendbc hyundai_2015_ccan.dbc (MIT). PD 파서(Can2BPDMessage)가 같은 메시지명을 씀.
import re, sys, collections

def parse_dbc(fn):
    msgs = {}; cur = None
    for ln in open(fn, encoding='latin1'):
        m = re.match(r'BO_ (\d+) (\w+): (\d+)', ln)
        if m: cur = m.group(2); msgs[cur] = {'id': int(m.group(1)), 'dlc': int(m.group(3)), 'sigs': []}; continue
        s = re.match(r'\s*SG_ (\w+)\s*:\s*(\d+)\|(\d+)@(\d)([+-])\s*\(([^,]+),([^)]+)\)\s*\[[^\]]*\]\s*"([^"]*)"', ln)
        if s and cur:
            msgs[cur]['sigs'].append(dict(n=s.group(1), st=int(s.group(2)), ln=int(s.group(3)), le=s.group(4) == '1',
                                          sg=s.group(5) == '-', sc=float(s.group(6)), off=float(s.group(7)), u=s.group(8)))
    return msgs

def val(p, s):
    v = 0
    if s['le']:
        for k in range(s['ln']):
            b = s['st'] + k; v |= ((p[b // 8] >> (b % 8)) & 1) << k
    else:  # Motorola: st = MSB
        b = s['st']
        for _ in range(s['ln']):
            v = (v << 1) | ((p[b // 8] >> (b % 8)) & 1); b = b - 1 if b % 8 else b + 15
    if s['sg'] and v >> (s['ln'] - 1): v -= 1 << s['ln']
    return v * s['sc'] + s['off']

def load_slots(fn):
    recs = collections.defaultdict(list)
    for line in open(fn, encoding='utf-8', errors='replace'):
        if not line.startswith('RAW '): continue
        b = bytes.fromhex(line[4:].strip().replace(' ', '')); n = b[9]; i = 11
        for _ in range(n):
            if i + 2 > len(b): break
            f, l = b[i], b[i + 1]; p = b[i + 2:i + 2 + l]
            if len(p) < l: break
            recs[f].append(p); i += 2 + l
    return recs

# 공회전·정차(브레이크 밟음)에서 기대 범위. 실측 1회로 만든 것 — 다른 상태 캡처엔 맞지 않음.
IDLE = {
    'EMS11': [('N', 500, 1000), ('VS', 0, 0)],
    'EMS12': [('ENG_VOL', 1.6, 1.6), ('TEMP_ENG', 15, 110)],
    'EMS14': [('VB', 12, 15.5), ('AMP_CAN', 600, 800)],       # 대기압 mmHg
    'EMS19': [('CR_Ems_BstPre', 900, 1100), ('CF_Ems_ModeledAmbTemp', 0, 40)],
    'EMS20': [('FCO', 0.1, 500), ('CF_Ems_PumpTPres', 200, 700)],
    'ESP12': [('LAT_ACCEL', -0.5, 0.5), ('LONG_ACCEL', -0.5, 0.5), ('YAW_RATE', -3, 3)],
    'WHL_SPD11': [('WHL_SPD_FL', 0, 0), ('WHL_SPD_FR', 0, 0), ('WHL_SPD_RL', 0, 0), ('WHL_SPD_RR', 0, 0)],
    'CLU12': [('CF_Clu_Odometer', 100, 999999)],
    'FATC11': [('CR_Fatc_OutTemp', 0, 40)],
}

def fingerprint(cc, recs):
    out = {}
    for m, checks in IDLE.items():
        sig = {s['n']: s for s in cc[m]['sigs']}; hits = []
        for f, ps in sorted(recs.items()):
            if len(ps[0]) != cc[m]['dlc']: continue
            rs = [(min(v), max(v)) for v in ([val(p, sig[c[0]]) for p in ps] for c in checks)]
            if all(lo - 1e-6 <= r[0] and r[1] <= hi + 1e-6 for (_, lo, hi), r in zip(checks, rs)):
                hits.append((f, rs))
        out[m] = hits
    return out

if __name__ == '__main__':
    cc = parse_dbc(sys.argv[1]); recs = load_slots(sys.argv[2])
    pairs = sys.argv[3:]
    if not pairs:
        for m, hits in fingerprint(cc, recs).items():
            print(f"{m}(0x{cc[m]['id']:x}): " + ("  ".join(
                f"0x{f:02x}[" + ",".join(f"{c[0]}={r[0]:.4g}..{r[1]:.4g}" for c, r in zip(IDLE[m], rs)) + "]"
                for f, rs in hits) or "후보 없음"))
    for pair in pairs:
        f, m = pair.split('='); ps = recs[int(f, 16)]
        print(f"== 0x{int(f,16):02x} as {m} (0x{cc[m]['id']:x})  ex={ps[0].hex(' ')}")
        for s in cc[m]['sigs']:
            vs = [val(p, s) for p in ps]
            print(f"   {s['n']:24s} {min(vs):10.4g} .. {max(vs):<10.4g} {s['u']}")
