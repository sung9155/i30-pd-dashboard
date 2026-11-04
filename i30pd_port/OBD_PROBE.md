# OBD mode 22 probe — our car's raw replies

The read-only probe added to `ObdLive` (`20fb748`, `a9b634c`) asks the engine (EMS `0x27`) and the
transmission (TCU `0x26`) a few Hyundai data identifiers and logs each whole reply under tag
`VehicleDash`. This file records what our car (`pd_kr`, firmware `PD.KOR.0000.V141.260114`) actually
answered, so the layout can be decoded before anything is shown.

Collecting it: the log ring is small and the per-frame diagnostics line floods it, so a plain
`adb logcat -d` a few minutes after start no longer has round 1. Stream it instead:

```sh
adb logcat -v time -s VehicleDash:I | grep --line-buffered "OBD " >> obd.txt
```

Round 1 fires 20 s after the ignition gate opens, then a round every 5 minutes (up to 24). Every
round after the first re-logs round 1 ("OBD probe round 1 again ..."), so one capture that spans a
drive holds both the cold values and the warm/under-load values.

## Round 1 — cold, idle, ~20 s after start (2026-11-04)

```
ecu 27 22 E001: 62 E0 01 D9 BD FF FF D3 D3 AB 7C 0A 43 00 00 00 00 6E 00 32 22 90 1C 06 97 01 E8 00 8C 00 00 57 3D 03 AF 01 69 7C 13 C1 F9 00 00 00 00 00 00 00 00 00 00 (51 B)
ecu 27 22 ED02: 7F 22 31 (3 B)   -- negative, NRC 0x31 requestOutOfRange: identifier not supported
ecu 27 22 E011: 7F 22 31 (3 B)   -- not supported
ecu 27 22 E021: 7F 22 31 (3 B)   -- not supported
ecu 27 22 E00A: 62 E0 0A F0 F0 F0 FF 90 05 F5 08 4B 08 D0 05 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 FF 00 FF 00 00 00 00 00 00 00 00 00 00 00 00 (47 B)
ecu 27 22 E003: 62 E0 03 F2 7F EA E3 40 CC FF FF 00 05 00 00 00 00 1B 00 00 00 00 06 BA F6 7C 01 6F 0E 1C 6D 00 69 00 CD 01 20 0E 28 33 00 00 21 13 00 00 7F 26 00 00 00 00 00 00 3F 01 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 (104 B)
ecu 26 22 01A4: 62 01 A4 FF FF E0 00 0A 6E 00 25 00 00 00 00 00 03 E8 0A 6E 0A 6E C3 0E 01 01 00 00 F0 FC C0 FF 00 22 00 23 00 (37 B)
ecu 26 22 01A5: 62 01 A5 FF F0 00 00 00 36 00 36 00 C9 FF 34 00 03 00 06 00 01 00 01 03 CA 03 CA 01 08 (29 B)
```

### What answered

| ECU | DID | reply | length | note |
|-----|-----|-------|:------:|------|
| EMS 0x27 | E001 | positive `62 E0 01 …` | 51 B | large multi-signal block; oil temperature candidate somewhere inside |
| EMS 0x27 | ED02 | `7F 22 31` | — | not supported |
| EMS 0x27 | E011 | `7F 22 31` | — | not supported |
| EMS 0x27 | E021 | `7F 22 31` | — | not supported |
| EMS 0x27 | E00A | positive `62 E0 0A …` | 47 B | starts `F0 F0 F0` — per-cylinder knock retard candidate |
| EMS 0x27 | E003 | positive `62 E0 03 …` | 104 B | boost / wastegate / target block |
| TCU 0x26 | 01A4 | positive `62 01 A4 …` | 37 B | DCT clutch temperatures |
| TCU 0x26 | 01A5 | positive `62 01 A5 …` | 29 B | DCT clutch temperatures (second block) |

Three of the eight (ED02, E011, E021) return negative response code `0x31` (request out of range),
so this EMS does not carry them — drop them from the probe and decode the five that answer.

### Decoding needs a warm / under-load round

Round 1 above is a single cold-idle snapshot, so a byte that holds (say) oil temperature can't be
told from a constant yet. The next step is a drive: let the probe run, then capture one round after
the engine is warm and has seen some throttle. Comparing that round against "round 1 again" in the
same capture isolates the moving bytes (oil temp up, coolant up, boost under throttle, clutch temp
up), which pins the offsets. The warm/load round and the decode go below once captured.

## Round 2 — ~5 min after start, short drive (2026-11-04)

```
ecu 27 22 E001: 62 E0 01 D9 BD FF FF D3 D3 AC 92 13 55 00 00 00 00 CE 00 58 40 40 2C 1B 97 01 A8 00 9D 00 00 57 3D 03 92 01 6E 8A 13 31 F8 00 00 00 00 00 00 00 00 00 00 (51 B)
ecu 27 22 E00A: 62 E0 0A F0 F0 F0 FF DA 0A BD 0D F0 0E ED 0C 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 FF 00 FF 00 00 00 00 00 00 00 00 00 00 00 00 (47 B)
ecu 27 22 E003: 62 E0 03 F2 7F EA E3 40 CC FF FF 00 05 00 00 00 00 49 00 00 00 00 35 47 E2 7C 01 6F 0E 3C 68 03 61 03 0A 04 E6 0B 40 36 00 00 46 37 00 00 7F 26 00 00 00 00 00 00 53 01 ... (104 B)
ecu 26 22 01A4: 62 01 A4 FF FF E0 00 18 7A 11 9E 6F 67 0B 3D 5D 27 6D EF 42 FF EE C5 5D 08 07 01 02 F0 FC C0 FF 00 24 00 25 00 (37 B)
ecu 26 22 01A5: 62 01 A5 FF F0 00 00 01 ED 2D 6D 00 00 FF 30 03 2B 03 FE 00 00 00 01 03 CB 0F CC 00 02 (29 B)
```

(ED02 / E011 / E021 still `7F 22 31`, left out.)

### Cold (round 1) vs ~5 min (round 2) — what moved

Byte positions below are indices into the whole reply (byte 0 = `62`).

**TCU 01A4 — clutch temperatures, found.** The tail reads `… 00 22 00 23 00` cold and `… 00 24 00 25 00`
at 5 min: bytes **[33] = 0x22→0x24 (34→36)** and **[35] = 0x23→0x25 (35→37)**. Two single bytes, each
behind a `00`, rising ~2 over a short drive — DCT clutch #1 and #2 temperature in raw °C. Needs a
fully warm round to confirm the scale is plain °C (a hot clutch should read ~0x3C–0x64 = 60–100 °C).

**TCU 01A5 — shaft speeds, not temperature.** Cold holds `03 CA 03 CA` (two equal 0x03CA = 970); at
5 min it is `03 CB 0F CC` (970 / 4044). Two u16 that sit equal at idle and split while driving look
like input/output shaft speed (rpm), so 01A5 is probably not where the clutch temperature is — 01A4
is. Bytes [4..15] also move a lot (gear state, slip).

**EMS E001 — oil/engine block, offset not yet pinned.** Many bytes move idle→warm: data bytes (after
`62 E0 01`) at offsets 6–9, 14, 16–20, 23, 25, 31, 33–37. No single byte can be called oil
temperature from two lukewarm points with no reference reading; a fully warm round (and the dash's
own coolant/oil reading as ground truth) is needed to pick it.

**EMS E00A — knock / per-cylinder.** Leads `F0 F0 F0 FF` in both. Then four little-endian u16 that
grow with rpm: cold `0590 08F5 084B 05D0`, 5 min `0ADA 0DBD 0EF0 0CED` — per-cylinder knock-sensor
level rather than retard (the `F0` bytes look like the retard, flat = none). Confirm with a round
under real load.

**EMS E003 — boost / target.** Head `F2 7F EA E3 40 CC FF FF 00 05` steady. Movers include data
offset 14 (0x1B→0x49), 19–21, 26–36, 39–40. A manifold-pressure byte should jump on a wide-open pull;
this short drive never loaded the engine, so the boost byte can't be isolated yet.

### Still needed: one longer drive with a wide-open pull

Round 2 is only lukewarm and saw no boost, so only the clutch-temperature bytes are pinned. A drive
that gets the engine fully warm and includes a hard (wide-open-throttle) pull, captured as another
round, would move the oil-temperature, boost and knock bytes far enough to pin them against the
dash's own gauges. The probe logs a round every 5 minutes on its own; keep the cable connected and it
is captured.
