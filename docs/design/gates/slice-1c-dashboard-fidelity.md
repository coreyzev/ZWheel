# Gate: Slice 1c — Dashboard fidelity pass vs golden 004

A side-by-side of our dashboard render vs the golden (`docs/golden-screenshots/FireShot Capture 004 - ZWheel.png`) found 5 gaps. Fix all. Read ONLY this gate. App module only; `core/` untouched; tokens via `LocalZWheelColors.current`; no new BLE UUID; no network.

## Fix 1 — SpeedSlab: remove the dead space above the speed number
In `app/src/main/kotlin/com/zwheel/app/ui/dashboard/SpeedSlab.kt`, the speed sits far below the header; golden has it tight under the connection line. Change the root `Box` padding from `vertical = 24.dp` to `top = 4.dp, bottom = 10.dp`. Make the speed number hug the top: the integer `Text` keeps `fontSize = 96.sp` but set `lineHeight = 96.sp` AND add `platformStyle`/`includeFontPadding`-equivalent tightening by wrapping the integer+decimal in a `Row` whose `verticalAlignment = Alignment.Top` (see Fix 2). Net effect: the number block should start near the top of the slab with minimal leading.

## Fix 2 — SpeedSlab: decimal is a high superscript, not bottom-aligned
In the same speed `Row`, change `verticalAlignment = Alignment.Bottom` to `Alignment.Top` so the decimal (".5") sits at the TOP next to the integer like the golden, not dropped to the baseline. Keep the decimal `fontSize = 48.sp`; set its `lineHeight = 48.sp`.

## Fix 3 — PushbackBar: add the "PUSHBACK HEADROOM" label row + full-width bar
In `app/src/main/kotlin/com/zwheel/app/ui/dashboard/PushbackBar.kt`, restructure from a single Row (bar + trailing status) into a `Column`:
1. A header `Row(fillMaxWidth, SpaceBetween)`:
   - left: `Text("PUSHBACK HEADROOM", JetBrainsMonoFamily, 9.sp, letterSpacing 1.5.sp, color = c.textDimmest)` (eyebrow style)
   - right: the status `Text` (see Fix 4), colored by the zone color.
2. Below it, the bar `Canvas(fillMaxWidth, height 8.dp)` (full width now, no trailing text beside it).
Use `padding(horizontal = 18.dp)` on the column; small `8.dp` gap between the label row and the bar.

## Fix 4 — PushbackBar: bar style + status wording to match golden
- Make the bar a **solid filled progress bar**, not tri-zone tints + marker: draw the full track in `c.border` (dim), then draw a filled rect from x=0 to `fraction * width` in `barColor` (the current ramp color). Drop the three 30%-alpha zone rects and the separate marker.
- Status wording by zone: danger (`fraction >= DANGER_FRACTION`) → `"pushback · ease off"`; caution (`>= CAUTION_FRACTION`) → `"approaching pushback"`; else → `"nominal"`. Keep coloring the status text with `barColor`.

## Fix 5 — BatteryBand: caption format
In `app/src/main/kotlin/com/zwheel/app/ui/dashboard/BatteryBand.kt`, change the battery caption from `"{N}S-pack · {V}V"` to golden order: `"%.1fV · %dS PACK".format(state.packVoltage, state.cellVoltages.size)` → e.g. `55.8V · 15S PACK`.

## Build & commit
`GRADLE_OPTS="-Xmx4g" GRADLE_USER_HOME=/root/zwheel-wt/.gradle-codex ./gradlew -Djava.io.tmpdir=/root/zwheel-wt/.tmp-codex :app:compileDebugKotlin` then `:app:recordRoborazziDebug` (use those same GRADLE_USER_HOME + java.io.tmpdir flags so you do NOT write to /tmp). Confirm dashboard.png renders. One commit: `fix(ui): dashboard fidelity pass (speed slab spacing, pushback label, battery caption)`. No Co-Authored-By line.
