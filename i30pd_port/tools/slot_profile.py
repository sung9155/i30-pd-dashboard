# -*- coding: utf-8 -*-
# 미확인 hscan 슬롯 ↔ DBC 메시지 비트 지문 매칭.
# 모든 로그에서 슬롯별 "변하는 비트"를 모아, 같은 DLC의 DBC 메시지마다
#   explained = 변하는 비트 중 DBC 신호가 덮는 비율, stray = 신호 없는 자리에서 변하는 비트 수
# 에 카운터·체크섬이 실제로 도는지, 숫자 신호가 LSB부터 변하는지를 더해 점수를 매긴다(score 함수).
# usage: py -3 -I slot_profile.py <dbc> [<dbc> ...] -- <log> [<log> ...] [--slots 0c,0d]
import sys, collections
sys.path.insert(0, __import__('os').path.dirname(__file__))
from hscan_match import parse_dbc, load_slots

def masks(logs):
    """slot -> (dlc, varying-bit set, frame count)"""
    seen = {}
    for fn in logs:
        for f, ps in load_slots(fn).items():
            for p in ps:
                d = seen.setdefault(f, [len(p), None, set(), 0])
                if len(p) != d[0]: continue
                v = int.from_bytes(p, 'little'); d[3] += 1
                if d[1] is None: d[1] = v
                else: d[2] |= {b for b in range(d[0] * 8) if ((v ^ d[1]) >> b) & 1}
    return {f: (d[0], d[2], d[3]) for f, d in seen.items()}

import re
COUNTER = re.compile(r'alive|cnt|count|chk|checksum|crc|parity', re.I)

def bits_of(s):
    if s['le']: return list(range(s['st'], s['st'] + s['ln']))     # LSB first
    out, b = [], s['st']
    for _ in range(s['ln']): out.append(b); b = b - 1 if b % 8 else b + 15
    return out[::-1]                                                  # Motorola: LSB first too

def score(m, vary):
    """+2 counter/checksum that moves, -3 one that is frozen (a live message always ticks it),
    +1 numeric field whose LSB moves, -1 one that moves only in upper bits, -1 per varying bit no signal covers."""
    sc, cov = 0, set()
    for s in m['sigs']:
        b = bits_of(s); cov |= set(b); v = [x in vary for x in b]
        if COUNTER.search(s['n']):
            sc += 2 if all(v) else -3 if not any(v) else 0
        elif s['ln'] >= 4 and any(v):
            sc += 1 if v[0] else -1
    stray = len(vary - cov)
    return sc - stray, len(vary & cov), stray

def rank(dlc, vary, msgs):
    out = [(score(m, vary), name, m['id']) for name, m in msgs.items() if m['dlc'] == dlc and m['sigs']]
    out.sort(key=lambda x: -x[0][0])
    return [(n, i, sc, hit, stray) for (sc, hit, stray), n, i in out]

if __name__ == '__main__':
    a = sys.argv[1:]; k = a.index('--'); dbcs, rest = a[:k], a[k + 1:]
    only = None
    if '--slots' in rest:
        j = rest.index('--slots'); only = {int(x, 16) for x in rest[j + 1].split(',')}; rest = rest[:j] + rest[j + 2:]
    msgs = {}
    for d in dbcs: msgs.update(parse_dbc(d))   # later files override (pass generic first, 2015_ccan last)
    for f, (dlc, vary, n) in sorted(masks(rest).items()):
        if only and f not in only: continue
        vb = ' '.join(f'b{i}:' + ''.join('x' if i * 8 + b in vary else '.' for b in range(7, -1, -1)) for i in range(dlc))
        print(f'0x{f:02x} dlc={dlc} frames={n} varying={len(vary)}  {vb}')
        if not vary: print('      (변화 없음 - 지문 없음)'); continue
        for name, cid, sc, hit, stray in rank(dlc, vary, msgs)[:4]:
            print(f'      {name:16s} 0x{cid:03x}  score {sc:+d}  explained {hit}/{len(vary)}  stray {stray}')
