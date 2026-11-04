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

## Round N — warm / under load

_(to be filled from the drive capture)_
