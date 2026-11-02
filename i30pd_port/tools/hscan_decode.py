# -*- coding: utf-8 -*-
# 슬롯별 후보 신호를 실제 값 시계열로 디코딩해 추정을 수치 검증한다.
# usage: py -3.14 -I hscan_decode.py <raw log>
import sys, collections
recs = collections.defaultdict(list)
for line in open(sys.argv[1], encoding='utf-8', errors='replace'):
    if not line.startswith('RAW '): continue
    b = bytes.fromhex(line[4:].strip().replace(' ', ''))
    if len(b) < 12: continue
    n = b[9]; i = 11
    for _ in range(n):
        if i+2 > len(b): break
        f, l = b[i], b[i+1]; p = b[i+2:i+2+l]
        if len(p) < l: break
        recs[f].append(p); i += 2+l

def u16le(p,o): return p[o] | (p[o+1]<<8)
def u16be(p,o): return (p[o]<<8) | p[o+1]
def s16(v): return v-0x10000 if v>=0x8000 else v
def stats(xs): return f"min={min(xs):.2f} max={max(xs):.2f} mean={sum(xs)/len(xs):.2f}"

def counter(ps,k):
    """byte k 가 alive-counter(0..N 순환)인지: 연속 증가 비율"""
    seq=[p[k] for p in ps if len(p)>k]
    inc=sum(1 for a,b in zip(seq,seq[1:]) if (b-a)%16==1 or (b-a)%256==1)
    return inc/max(1,len(seq)-1), seq[:16]

print("== 0x08 EMS11 RPM 후보 ==")
ps=recs[0x08]
print(" RPM=(b1<<8|b2)*0.25 BE :", stats([u16be(p,1)*0.25 for p in ps]))
print(" RPM=(b2<<8|b1)*0.25 LE :", stats([u16le(p,1)*0.25 for p in ps]))
print(" b2 raw:", stats([p[2] for p in ps]), " b1 set:", sorted({p[1] for p in ps}))

print("\n== 0x11 BAT11 전압 후보 ==")
ps=recs[0x11]
print(" V=(b3|b4<<8)*0.001+6 LE :", stats([u16le(p,3)*0.001+6 for p in ps]))
print(" V=b1*0.1 :", stats([p[1]*0.1 for p in ps]), " (b1 last=0x84=132)")
print(" b0:", stats([p[0] for p in ps]))

print("\n== 0x19 SAS11 조향각 후보 ==")
ps=recs[0x19]
print(" angle=s16(b0|b1<<8)*0.1 LE :", stats([s16(u16le(p,0))*0.1 for p in ps]))
print(" b0:", sorted({p[0] for p in ps}), " b1:", sorted({p[1] for p in ps}), " b2(속도):", sorted({p[2] for p in ps}))
r,seq=counter(ps,4); print(f" b4 counter율={r:.2f} seq={seq}")

print("\n== 0x01 EMS15 흡기온 후보 (정적) ==")
ps=recs[0x01]; print(" b2:", sorted({p[2] for p in ps}), " ->", {p[2] for p in ps} and f"{next(iter({p[2] for p in ps}))*0.75-48:.2f}C")

print("\n== counter/checksum 스캔 (변동 바이트의 counter율) ==")
for f in sorted(recs):
    ps=recs[f]; size=max(len(p) for p in ps)
    hot=[k for k in range(size) if len({p[k] for p in ps if len(p)>k})>3]
    for k in hot:
        r,_=counter(ps,k)
        tag = "COUNTER" if r>0.8 else ("checksum?" if r<0.2 and len({p[k] for p in ps})>20 else "")
        if tag: print(f" 0x{f:02x} byte{k}: {tag} (inc율={r:.2f}, distinct={len({p[k] for p in ps})})")

print("\n== 0x21 고주기 (b0) 시퀀스 앞 40 ==")
print(" ", [p[0] for p in recs[0x21][:40]])
