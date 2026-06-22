# Gate: Slice 0 — Dark Theme Foundation

You are implementing the foundation of a dark "instrument-cluster" visual redesign of the ZWheel
Android app. Read ONLY this file and `docs/design/spec.md`. Do not read any other docs.

This slice is **app module only**. Do NOT touch the `core/` module (it must stay Android-free).
Everything else in the redesign builds on this slice, so the goal is: install the dark token system,
the two fonts, and re-skin the shared component helpers — **without breaking compilation**. Per-screen
recoloring happens in later slices; here, keep every existing function signature intact so current
callers still compile.

## Scope (do all of these)

### 1. New file: `app/src/main/kotlin/com/zwheel/app/ui/ZWheelColors.kt`
Define the design token object as a Kotlin `data class` instance plus a `CompositionLocal`:

```kotlin
package com.zwheel.app.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class ZWheelColors(
    val screenBg: Color, val navBg: Color, val mapBg: Color, val insetRow: Color,
    val legendCard: Color, val card: Color, val cardElevated: Color,
    val border: Color, val divider: Color, val frameBorder: Color, val buttonBorder: Color,
    val borderGreen: Color, val borderRed: Color, val borderLime: Color, val borderBattery: Color,
    val textPrimary: Color, val textStatus: Color, val textSecondary: Color, val textMuted: Color,
    val textLabel: Color, val textDim: Color, val textDimmest: Color, val separator: Color,
    val lime: Color, val cyan: Color,
    val rampGood: Color, val rampCaution: Color, val rampDanger: Color,
)

val ZWheelDarkColors = ZWheelColors(
    screenBg = Color(0xFF0A0B0E), navBg = Color(0xFF0C0D11), mapBg = Color(0xFF0C0E12),
    insetRow = Color(0xFF0E1014), legendCard = Color(0xFF101319), card = Color(0xFF121419),
    cardElevated = Color(0xFF181B22),
    border = Color(0xFF20242D), divider = Color(0xFF1A1D24), frameBorder = Color(0xFF23262F),
    buttonBorder = Color(0xFF262B35), borderGreen = Color(0xFF1C4030), borderRed = Color(0xFF3A1C1C),
    borderLime = Color(0xFF2A3520), borderBattery = Color(0xFF223018),
    textPrimary = Color(0xFFF2F4F7), textStatus = Color(0xFFCFD5DE), textSecondary = Color(0xFF9AA4B2),
    textMuted = Color(0xFF7C8696), textLabel = Color(0xFF6B7484), textDim = Color(0xFF5A616E),
    textDimmest = Color(0xFF4A5260), separator = Color(0xFF2A2E36),
    lime = Color(0xFFC6F24E), cyan = Color(0xFF38E0FF),
    rampGood = Color(0xFF4FE086), rampCaution = Color(0xFFFFB22E), rampDanger = Color(0xFFFF5A5A),
)

val LocalZWheelColors = staticCompositionLocalOf { ZWheelDarkColors }
```

Composables access tokens via `LocalZWheelColors.current`.

Also add a single shared helper here for the green→amber→red magnitude ramp (used widely later):
```kotlin
// fraction 0f (worst) .. 1f (best). Returns rampDanger .. rampCaution .. rampGood.
fun ZWheelColors.ramp(fraction: Float): Color
```
Implement as: `< 0.33f` → rampDanger, `< 0.66f` → rampCaution, else rampGood. (Simple buckets; tuned later.)

### 2. Rewrite `app/src/main/kotlin/com/zwheel/app/ui/ZWheelTheme.kt`
Replace the current light scheme. Use Material3 `darkColorScheme` mapped to the new tokens
(primary = lime, onPrimary = a near-black like screenBg, secondary/tertiary = cyan,
background = screenBg, surface = card, onBackground/onSurface = textPrimary, error = rampDanger).
The current file has a private `val ZWheelColors` of type ColorScheme — remove/rename it (the name
`ZWheelColors` is now the token data class). `ZWheelTheme` must wrap content in
`CompositionLocalProvider(LocalZWheelColors provides ZWheelDarkColors)` AND set the Material typography
from Type.kt (step 3). Keep the existing public signature `fun ZWheelTheme(content: @Composable () -> Unit)`.

### 3. New file: `app/src/main/kotlin/com/zwheel/app/ui/Type.kt`
Define two `FontFamily`s from the bundled fonts (step 4) and a Material3 `Typography`:
- `val SairaFamily = FontFamily(...)` mapping weights: 400/600/700/800/900.
- `val JetBrainsMonoFamily = FontFamily(...)` mapping weights: 400/500/700.
- A `Typography` instance whose families default to Saira (so Material components inherit the brand
  face), with tabular figures where the role is numeric.
- Expose convenience `TextStyle`s OR document that screens compose styles inline. Use the role table
  in `docs/design/spec.md` § Typography as the reference for sizes/weights/tracking. You do NOT need to
  create a style for every row now — create the families + Typography + a small set of obviously-reused
  styles (screen title, eyebrow/mono label, telemetry-meta mono). Later slices add more.
- All numeric/telemetry styles set `fontFeatureSettings = "tnum"`.

### 4. Bundle the fonts in `app/src/main/res/font/`
The release build has **no INTERNET permission** (offline-first), so fonts MUST be bundled as static
`.ttf` files — do NOT use downloadable Google Fonts. Fetch these static weights (you have network
access) and save with lowercase resource-safe names:
- Saira: 400→`saira_regular.ttf`, 600→`saira_semibold.ttf`, 700→`saira_bold.ttf`,
  800→`saira_extrabold.ttf`, 900→`saira_black.ttf`
- JetBrains Mono: 400→`jetbrains_mono_regular.ttf`, 500→`jetbrains_mono_medium.ttf`,
  700→`jetbrains_mono_bold.ttf`

Source (fontsource static TTFs, reliable for `curl`):
- `https://cdn.jsdelivr.net/fontsource/fonts/saira@latest/latin-<WEIGHT>-normal.ttf`
- `https://cdn.jsdelivr.net/fontsource/fonts/jetbrains-mono@latest/latin-<WEIGHT>-normal.ttf`
  where `<WEIGHT>` ∈ {400,500,600,700,800,900}. Verify each file is a real TTF (non-trivial size,
  starts with the TTF/OTF magic) before committing. Both fonts are OFL-licensed — add a short note to
  `NOTICE.md` crediting Saira and JetBrains Mono (OFL).

### 5. Re-skin `app/src/main/kotlin/com/zwheel/app/ui/DashboardComponents.kt`
Keep ALL function signatures exactly as they are (`DashboardCard`, `SpeedGauge`, `Label`, `Metric`,
`SmallStat`) so existing callers compile. Only change styling:
- `DashboardCard`: default fill = `card` token, add a 1dp border in `border` token, radius **16.dp**
  (not 8), default `contentColor` = `textPrimary`. Keep the `color`/`contentColor` params (callers
  still pass values; later slices update them).
- `Label`: default color = `textLabel`; switch to mono (JetBrainsMono) eyebrow style — small, uppercase
  tracking per spec.
- `Metric` / `SmallStat`: default colors to `textPrimary`/`textLabel`; use SairaFamily; numeric uses
  tabular figures. Replace the hardcoded `Color(0xff111111)` / `Color(0xff8a1f11)` defaults.
- `SpeedGauge`: recolor track to `border`/`divider` and progress to `lime`.
- Access tokens via `LocalZWheelColors.current` inside each composable (do not hardcode hex).

## Constraints (self-review before commit)
- `core/` untouched. No Android imports added to core. No new BLE UUID, no network calls in `app/main`.
- Soft 300-line limit per file.
- Do not change `DashboardComposables.kt`, `ZWheelAppScreen.kt`, or any screen file in this slice — only
  the four files above + `res/font/` + `NOTICE.md`. If a screen fails to compile because a default
  color changed, prefer adjusting the default rather than editing the screen.

## Build & commit
1. From the worktree root run: `GRADLE_OPTS="-Xmx4g" ./gradlew :app:compileDebugKotlin` and fix ALL
   errors until it succeeds.
2. Commit everything (fonts included) with: `feat(ui): dark theme tokens, Saira + JetBrains Mono fonts, re-skin shared components`
   (Conventional Commits, one commit). Do NOT add any Co-Authored-By line.
