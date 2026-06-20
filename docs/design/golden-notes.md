# Golden-standard reference (text capture)

The user's four golden screenshots are the authoritative look (they supersede the flawed
`screens/*.png` in the original Claude Design bundle). The PNGs themselves need to be added to
`docs/design/golden/` once uploaded as files — until then this is the durable text capture.

## Image 1 — full app, LONG board name, RED/over-pushback state
- Dashboard speed **19.5** rendered **red** (rampDanger); `MPH` + `TIRE-CORRECTED` lime badge +
  `↑` top-speed (amber). Pushback bar nearly full, right label **"approaching pushback"** (red).
- Battery **64%** green with mini fill bar + "{N}S-pack" caption; Range **8.0 mi** in cyan
  ("EST. REMAINING / at current draw").
- Trip distance **3.24 mi** + small right stats (Ah, regen `+0.x`, avg `11.x`).
- Stat row: `bolt` draw `11.6` / `MISSION` / `lightbulb ON` (lime where active).
- Per-cell strip (green bars). Temps CTRL/MOTOR/BATT `96° / 112° / 84°`.
- Board name shown everywhere as **"BoardyMcBoardface McGee"** — must **ellipsis** in dashboard
  header, history rows, ride-detail board card, and Settings name.
- Ride detail: duration `24:08`, distance `3.24 mi`, top `19.6`, avg `11.8`, GPS distance `3.19`,
  Ah used `4.1`, board card shows the long name.

## Image 2 — full app, SHORT board name "Gemini", AMBER/approaching state
- Identical layout; speed **15.8** rendered **amber** (rampCaution); battery **67%**; range **8.3 mi**.
- Confirms the **speed value color ramps by magnitude vs pushback** (amber ~15.8, red ~19.5 on an XR).

## Image 3 — Settings, CONNECTED BOARD card, DEVICE INFO expanded (interaction #3)
- Glowing green dot + **"BoardyMcBoardface McGee"** + edit-pencil (tap name → inline edit, max 24).
- Secondary info row (wraps): `PINT X` lime badge · wifi glyph `-60dBm` · `Fw 4134 · 15S`.
- Buttons: **Disconnect** (red text) / **Forget board**.
- `DEVICE INFO` disclosure expanded (chevron up), inset rows (label left, mono value right):
  Serial `18694`, Battery serial `22136`, Hardware rev `4209`, Firmware `4134`, RSSI `-60dBm`.
- Below: `UNITS` → Speed segmented `MPH`(selected, lime fill, dark text)/`KPH`.

## Image 4 — Per-cell EXPANDED (interaction #4)
- Header `PER-CELL · 15 S` with up-chevron; min/max badges `↑ 3.70V` (rampGood) `↓ 3.41V` (rampDanger).
- Collapsed bar strip visible above the grid: all green bars except **one red bar** (the low cell).
- Expanded grid tiles `C01 3.68 · C02 3.67 · C03 3.69 · C04 3.66 · C05 3.70` (green mono values),
  second row `C06…C10` dimmer. Low cells flagged with a red border. Pack size `15S` = `cellVoltages.size`.

## Cross-cutting confirmations
- Lime `#C6F24E` = primary/brand; cyan `#38E0FF` = data/secondary (range, GPS); green→amber→red ramp
  = magnitude (battery, cells, speed-vs-pushback, temps).
- Bottom tab bar: Ride / History / Settings (active = lime).
