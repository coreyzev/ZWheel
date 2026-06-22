# Gate: Slice 1d — Restyle battery-optimization screen to dark golden

`app/src/main/kotlin/com/zwheel/app/ui/onboarding/OemBatteryAdvice.kt` was never restyled — it still
uses the light theme (white surfaces, `#eeeeee` bg, `FontWeight.Black`). Restyle `OemBatteryAdviceScreen`
to the dark instrument-cluster look matching the golden (`docs/golden-screenshots/FireShot Capture 004 - ZWheel.png`,
bottom section). Read ONLY this gate. App module only; `core/` untouched; tokens via `LocalZWheelColors.current`;
reuse `SairaFamily`/`JetBrainsMonoFamily`; no new BLE UUID; no network.

**Preserve Corey's existing advice copy** (`samsungBatteryAdvice`/`genericBatteryAdvice` strings and
`batteryAdviceForManufacturer`) — it is accurate battery-kill guidance. Only change presentation +
add the small items below. Do NOT rewrite the step wording.

## Changes
1. **Add a `deviceLabel: String = ""` param** to `OemBatteryAdviceScreen`. In
   `app/src/main/kotlin/com/zwheel/app/ui/ZWheelAppScreen.kt` where the `battery` route calls it, pass
   `deviceLabel = Build.MANUFACTURER.uppercase() + " DETECTED"` (the route already has `Build.MANUFACTURER`).
2. **Root**: `Column(fillMaxSize().background(c.screenBg).verticalScroll(...).padding(horizontal = 18.dp, vertical = 16.dp))`, `spacedBy(12.dp)`.
3. **Manufacturer pill** (if `deviceLabel` non-blank): a small rounded (pill, 999dp) `Surface` with
   `border = BorderStroke(1.dp, c.buttonBorder)`, containing a Row: a small filled dot (`c.lime`, 6dp circle)
   + `Text(deviceLabel, JetBrainsMonoFamily, 9.sp, letterSpacing 1.5.sp, c.textSecondary)`, padding 5×10dp.
4. **Title**: `Text(advice.title, SairaFamily, 24.sp, FontWeight.W800, c.textPrimary)`.
5. **Summary**: `Text(advice.summary, SairaFamily, 14.sp, FontWeight.Normal, c.textMuted, lineHeight 21.sp)`.
6. **Step cards**: render each entry of `advice.steps` (then `advice.secondarySteps`, continuing the
   number) as a card: `Surface(shape = RoundedCornerShape(16.dp), color = c.card, border = BorderStroke(1.dp, c.border))`
   containing a Row: a 26.dp lime circle (`Box`, `CircleShape`, `c.lime`) with the number centered in
   dark text (`c.screenBg`, Saira W800) + the step `Text` (`SairaFamily`, 14.sp, `c.textSecondary`).
   Keep the existing section split but render both as cards (no white surfaces, no tonalElevation).
7. **Green confirmation callout** (new, static): a card with `border = BorderStroke(1.dp, c.borderGreen)`,
   subtle green tint background (`c.rampGood.copy(alpha = 0.08f)`), a check icon (`Icons.Filled.CheckCircle`,
   `c.rampGood`, 18dp) + `Text("Once set, rides keep recording with the phone pocketed and screen off.", 13.sp, c.textSecondary)`.
8. **Footer**: a full-width lime pill button for `onOpenSettings`: `Button(colors = ButtonDefaults.buttonColors(containerColor = c.lime, contentColor = c.screenBg), shape = RoundedCornerShape(999.dp), modifier = fillMaxWidth())` with `Text("Open battery settings", SairaFamily, FontWeight.W800)`; below it a centered `TextButton(onDone)` → `Text("I've done this", c.textMuted)`.
9. Update the `@Preview` to pass `deviceLabel = "SAMSUNG DETECTED"`.

## Build & commit
Run with `GRADLE_USER_HOME=/root/zwheel-wt/.gradle-codex` and `-Djava.io.tmpdir=/root/zwheel-wt/.tmp-codex`
(do NOT write to /tmp): `./gradlew :app:compileDebugKotlin`. Add a Roborazzi screenshot test
`BatteryOptScreenshotTest` rendering `ZWheelTheme { OemBatteryAdviceScreen(advice = samsungBatteryAdvice, deviceLabel = "SAMSUNG DETECTED") }`
→ `app/build/outputs/roborazzi/battery_opt.png`; run `:app:recordRoborazziDebug` and confirm it renders.
Commits (no Co-Authored-By): `fix(ui): dark restyle of battery-optimization screen`, `test(ui): battery-opt screenshot test`.
