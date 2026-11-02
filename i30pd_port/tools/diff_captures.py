# -*- coding: utf-8 -*-
# 동작 캡처 vs 기준선(idle) 비교 — 어느 슬롯의 어느 바이트가 "그 동작 때문에" 변했는지 뽑는다.
# 동작 태깅 캡처(car_actions.sh 결과)를 idle 기준선과 비교해 슬롯을 신호로 확정하는 용도.
#
# usage: py -3.14 -I diff_captures.py <baseline raw log> <action raw log>
#   예:  py -3.14 -I tools/diff_captures.py logs/act_01_idle_*.log logs/act_05_brake_*.log
import sys, collections
try: sys.stdout.reconfigure(encoding='utf-8', errors='replace')
except Exception: pass

def load(path):
    recs = collections.defaultdict(list)
    for line in open(path, encoding='utf-8', errors='replace'):
        if not line.startswith('RAW '): continue
        b = bytes.fromhex(line[4:].strip().replace(' ', ''))
        if len(b) < 12: continue
        n = b[9]; i = 11
        for _ in range(n):
            if i+2 > len(b): break
            f, l = b[i], b[i+1]; p = b[i+2:i+2+l]
            if len(p) < l: break
            recs[f].append(p); i += 2+l
    return recs

def colstats(ps, k):
    vals = [p[k] for p in ps if len(p) > k]
    return (len(set(vals)), min(vals), max(vals)) if vals else (0, 0, 0)

def main():
    base = load(sys.argv[1]); act = load(sys.argv[2])
    print(f"baseline={sys.argv[1].split('/')[-1]}  action={sys.argv[2].split('/')[-1]}")
    print(f"슬롯: base {len(base)}개 / action {len(act)}개")
    print("\n변화 슬롯·바이트 (기준선 대비 새 값이 나타난 바이트만, 변화 큰 순):")
    print(f"  {'slot':5} {'byte':4} {'base(min-max,n)':18} {'action(min-max,n)':18} {'새범위?':6}")
    rows = []
    for f in sorted(set(base) | set(act)):
        size = max((len(p) for p in act.get(f, []) + base.get(f, [])), default=0)
        for k in range(size):
            bd, bmn, bmx = colstats(base.get(f, []), k)
            ad, amn, amx = colstats(act.get(f, []), k)
            if ad == 0: continue
            # action에서 기준선 범위를 벗어난 값이 있으면 그 동작의 신호 후보
            out = amn < bmn or amx > bmx
            # 변화 점수: 분포 폭 증가 + 범위 이탈
            score = (ad - bd) + (abs(amn - bmn) + abs(amx - bmx)) / 32.0 + (10 if out and bd <= 1 else 0)
            if ad > bd or out:
                rows.append((score, f, k, (bd, bmn, bmx), (ad, amn, amx), out))
    rows.sort(reverse=True)
    for score, f, k, (bd, bmn, bmx), (ad, amn, amx), out in rows[:40]:
        print(f"  0x{f:02x}  b{k:<3} {f'{bd}[{bmn:02x}-{bmx:02x}]':18} {f'{ad}[{amn:02x}-{amx:02x}]':18} {'★새' if out else ''}")
    if not rows:
        print("  (유의미한 변화 없음 — 캡처 타이밍/동작 확인)")
    print("\n★새 = 기준선에 없던 값 범위 → 그 슬롯이 이 동작의 신호일 가능성 높음.")

if __name__ == "__main__":
    if len(sys.argv) < 3:
        print("usage: diff_captures.py <baseline raw log> <action raw log>"); sys.exit(1)
    main()
