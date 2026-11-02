# -*- coding: utf-8 -*-
import sys, re, collections
log = sys.argv[1]
recs = collections.defaultdict(list)   # func -> list of payload tuples (in order)
nframes = 0
for line in open(log, encoding='utf-8', errors='replace'):
    if not line.startswith('RAW '): continue
    b = bytes.fromhex(line[4:].strip().replace(' ', ''))
    nframes += 1
    if len(b) < 12: continue
    n = b[9]            # record count
    i = 11
    for _ in range(n):
        if i + 2 > len(b): break
        f, l = b[i], b[i+1]
        p = b[i+2:i+2+l]
        if len(p) < l: break
        recs[f].append(p)
        i += 2 + l
print("frames=%d  header=%s" % (nframes, b[:11].hex(' ')))
print("func  n    size  per-byte distinct (min..max)            last")
for f in sorted(recs):
    ps = recs[f]
    size = max(len(p) for p in ps)
    cols = []
    for k in range(size):
        vals = [p[k] for p in ps if len(p) > k]
        d = len(set(vals))
        cols.append("%d" % d if d == 1 else "%d[%02x-%02x]" % (d, min(vals), max(vals)))
    print("0x%02x %4d  %d     %-40s %s" % (f, len(ps), size, ' '.join(cols), ps[-1].hex(' ')))
