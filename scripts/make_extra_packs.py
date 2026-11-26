#!/usr/bin/env python3
"""Build three more built-in sound packs (apps/dashboard/res/raw/enginepack_<id>.zip). Same format and player as the
i4t / i6 / v6 / v8 packs, so playback costs the same CPU; only the APK grows (about 0.4 MB each).

  sc8  supercharged V8: the v8 pack's loops with a blower whine added (16 whine cycles per crank turn, i.e. a
       twin-rotor blower geared ~2.7:1, so every loop still holds whole cycles and stays seamless and in phase)
  evs  EV sport: a deep synth voice that rises with road speed (pack line "follow speed"), warm under load, soft
       when coasting; an electric-motor whine on top
  evj  EV jet: a turbine whine over a low rumble and an air roar, following engine rpm like a virtual shift

All three are synthesized here (sums of sines, no recording of any car), dedicated to the public domain (CC0 1.0).
Loops are 0.5 s; every partial sits on a 2 Hz grid, so a loop is exactly periodic and repeats without a seam.
Loudness: each loop is matched to the v8 pack's loop at the same rpm and load, so switching packs keeps the level.

  python scripts/make_extra_packs.py            (needs numpy)
"""
import io
import os
import zipfile

import numpy as np

RATE = 22050
L = RATE // 2                       # 0.5 s: a 2 Hz grid is periodic in it
RAW = os.path.join(os.path.dirname(__file__), '..', 'apps', 'dashboard', 'res', 'raw')
CC0 = ('# Synthesized for the i30 PD dashboard by scripts/make_extra_packs.py (sums of sines; not a recording of any car).\n'
       '# These WAV loops and this file are dedicated to the public domain under CC0 1.0:\n'
       '# https://creativecommons.org/publicdomain/zero/1.0/\n')


def wav(pcm):
    b = io.BytesIO()
    data = np.clip(np.round(pcm), -32767, 32767).astype('<i2').tobytes()
    b.write(b'RIFF' + (36 + len(data)).to_bytes(4, 'little') + b'WAVEfmt ' + (16).to_bytes(4, 'little'))
    b.write((1).to_bytes(2, 'little') + (1).to_bytes(2, 'little') + RATE.to_bytes(4, 'little') + (RATE * 2).to_bytes(4, 'little'))
    b.write((2).to_bytes(2, 'little') + (16).to_bytes(2, 'little') + b'data' + len(data).to_bytes(4, 'little') + data)
    return b.getvalue()


def read_wav(b):
    i = b.index(b'data')
    n = int.from_bytes(b[i + 4:i + 8], 'little')
    return np.frombuffer(b[i + 8:i + 8 + n], dtype='<i2').astype(np.float64)


def rms(x):
    return float(np.sqrt(np.mean(np.square(x)))) or 1.0


def v8_reference():
    """{(rpm, load): rms} of the v8 pack, to match levels."""
    z = zipfile.ZipFile(os.path.join(RAW, 'enginepack_v8.zip'))
    txt = z.read('pack.txt').decode()
    ref, loops = {}, []
    for line in txt.splitlines():
        f = line.split()
        if not f or f[0] != 'loop':
            continue
        kv = dict(p.split('=') for p in f[2:])
        pcm = read_wav(z.read(f[1]))
        s, e = int(kv.get('start', 0)), int(kv.get('end', len(pcm)))
        ref[(int(kv['rpm']), kv['load'])] = rms(pcm[s:e])
        loops.append((f[1], kv, pcm))
    return txt, loops, ref


def level_at(ref, rpm, load):
    pts = sorted((r, v) for (r, l), v in ref.items() if l == load)
    rs, vs = [p[0] for p in pts], [p[1] for p in pts]
    return float(np.interp(rpm, rs, vs))


def tone(freqs, amps, n=L, phases=None):
    t = np.arange(n) / RATE
    out = np.zeros(n)
    rng = np.random.default_rng(7)
    for i, (f, a) in enumerate(zip(freqs, amps)):
        f = 2.0 * round(f / 2.0)    # the 2 Hz grid
        if f <= 0 or f >= RATE / 2 - 200 or a == 0:
            continue
        ph = phases[i] if phases is not None else rng.uniform(0, 2 * np.pi)
        out += a * np.sin(2 * np.pi * f * t + ph)
    return out


def noise_band(lo, hi, n_partials, rng, tilt=1.0):
    """A periodic noise band: many random-phase sines on the 2 Hz grid between lo and hi."""
    fs = rng.uniform(lo, hi, n_partials)
    amps = (lo / fs) ** tilt * rng.uniform(0.6, 1.0, n_partials)
    return tone(fs, amps, phases=rng.uniform(0, 2 * np.pi, n_partials))


def pack_zip(pid, header, loops):
    b = io.BytesIO()
    with zipfile.ZipFile(b, 'w', zipfile.ZIP_DEFLATED) as z:
        lines = [header]
        for name, rpm, load, pcm, extra in loops:
            z.writestr(name, wav(pcm))
            lines.append(f'loop {name} rpm={rpm} load={load}{extra}')
        z.writestr('pack.txt', '\n'.join(lines) + '\n')
    path = os.path.join(RAW, f'enginepack_{pid}.zip')
    open(path, 'wb').write(b.getvalue())
    print(pid, len(loops), 'loops', len(b.getvalue()), 'B')


def supercharger(v8txt, v8loops):
    out = []
    for name, kv, pcm in v8loops:
        rpm, load = int(kv['rpm']), kv['load']
        s, e = int(kv.get('start', 0)), int(kv.get('end', len(pcm)))
        seg = pcm[s:e].copy()
        n = len(seg)
        # whole whine cycles in the loop: 16 per crank turn, so it keeps the loop seamless and in phase
        cycles = max(1, round(16 * rpm / 60 * n / RATE))
        t = np.arange(n)
        base = 2 * np.pi * cycles * t / n
        whine = np.sin(base) + 0.35 * np.sin(2 * base + 0.7) + 0.12 * np.sin(3 * base + 1.9)
        k = (0.42 if load == 'on' else 0.16) * min(1.0, (rpm / 6500) ** 0.6 + 0.15)
        mixed = seg + k * rms(seg) * whine / rms(whine)
        mixed *= rms(seg) / rms(mixed) * (1.05 if load == 'on' else 1.0)   # keep the v8 level, a touch more on load
        out.append((name.replace('v8', 'sc8'), rpm, load, mixed, ''))
    header = CC0 + '# sc8: the v8 pack (de22ev-ves tools/render, CC0) with a blower whine added.\nname sc8\ncylinders 8\nidle 800\nredline 6500\naligned yes'
    pack_zip('sc8', header, out)


RPMS = [800, 1200, 1800, 2600, 3600, 4800, 6200, 8000]


def ev_sport(ref):
    out = []
    rng = np.random.default_rng(11)
    for rpm in RPMS:
        f0 = 55.0 * rpm / 800                  # 55 Hz at standstill, x10 at the top (the player pitches by rpm ratio)
        for load in ('on', 'off'):
            k = np.arange(1, 13)
            tilt = 1.15 if load == 'on' else 2.0
            amps = 1.0 / k ** tilt
            voice = tone(f0 * k, amps) + 0.55 * tone(f0 * k + 2, amps * 0.9)          # a 2 Hz detuned twin: the chorus
            voice += 0.35 * tone(2 * f0 * k[:6] - 2, amps[:6])                         # an octave layer, the other way
            whine = tone([f0 * 9.5, f0 * 19], [1.0, 0.3])                               # motor whine
            air = noise_band(400, 3000, 160, rng, tilt=1.4)
            x = voice / rms(voice) + (0.22 if load == 'on' else 0.12) * whine / rms(whine) \
                + (0.10 if load == 'on' else 0.05) * min(1, rpm / 4000) * air / rms(air)
            x *= level_at(ref, rpm * 6500 / 8000, load) / rms(x)
            out.append((f'evs_{rpm}_{load}.wav', rpm, load, x, ''))
    header = CC0 + '# evs: EV sport voice, follows road speed (0 -> 800, top speed -> 8000).\nname evs\ncylinders 1\nidle 800\nredline 8000\nfollow speed\ntopspeed 220'
    pack_zip('evs', header, out)


def ev_jet(ref):
    out = []
    rng = np.random.default_rng(23)
    for rpm in [r for r in RPMS if r <= 6500] + [6500]:
        if any(o[1] == rpm for o in out):
            continue
        fw = rpm / 60 * 24                     # turbine whine
        fr = rpm / 60 * 2                      # rumble
        for load in ('on', 'off'):
            whine = tone([fw, fw * 2, fw * 1.5], [1.0, 0.25, 0.15])
            k = np.arange(1, 9)
            rumble = tone(fr * k, 1.0 / k ** 1.3)
            roar = noise_band(fr * 4, fr * 40, 220, rng, tilt=0.9)
            on = load == 'on'
            x = (0.9 if on else 1.0) * whine / rms(whine) + (0.8 if on else 0.35) * rumble / rms(rumble) \
                + (0.7 if on else 0.25) * roar / rms(roar)
            x *= level_at(ref, rpm, load) / rms(x)
            out.append((f'evj_{rpm}_{load}.wav', rpm, load, x, ''))
    header = CC0 + '# evj: EV jet voice (turbine whine, rumble, air roar), follows engine rpm like a virtual shift.\nname evj\ncylinders 1\nidle 800\nredline 6500'
    pack_zip('evj', header, out)


if __name__ == '__main__':
    txt, loops, ref = v8_reference()
    supercharger(txt, loops)
    ev_sport(ref)
    ev_jet(ref)
