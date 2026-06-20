# Gate: Slice 5 — Wear OS Watch Faces (Dark Redesign)

You are implementing the four Wear OS watch-face states of the ZWheel dark instrument-cluster
redesign. Read ONLY this file and `docs/design/spec.md` (§10 "Wear OS watch faces" and the Design
Tokens section). Do not read any other gate files.

This slice touches **three modules**:

| Module | Files touched |
|--------|---------------|
| `core` | `model/BoardModels.kt` (add one field to `WatchPayload`), `model/WatchDataLayerKeys.kt` (add one key constant) |
| `app` | `wear/WearDataLayerRepository.kt` (populate the new field) |
| `wear` | `ui/ZWheelWearScreen.kt` (full restyle — the only composable file), new `ui/WearColors.kt`, new `ui/WearType.kt`, new `res/font/` TTF assets |

Do NOT touch any other file. Do not modify phone UI screens. Do not add new BLE UUIDs. Do not make
network calls in `app/main` sources.

---

## Step 0 — Font assets (do this first, before any Kotlin)

The wear module has no fonts today. The `app` module already has the TTFs at
`app/src/main/res/font/`. Copy **exactly these five files** into `wear/src/main/res/font/`:

```
saira_black.ttf         (Saira weight 900)
saira_bold.ttf          (Saira weight 700)
jetbrains_mono_regular.ttf
jetbrains_mono_medium.ttf
jetbrains_mono_bold.ttf
```

The copy is a byte-for-byte duplicate of the existing app files — same OFL TTF, no modifications.
Use a shell `cp` rather than trying to re-download them.

---

## Step 1 — `core` changes (pure Kotlin — no Android imports allowed)

### 1a. Add the new `WatchPayload` field — `core/src/main/kotlin/com/zwheel/core/model/BoardModels.kt`

Add `safetyHeadroom: Int? = null` as a trailing field on the `WatchPayload` data class:

```kotlin
data class WatchPayload(
    val speedMetersPerSecondCorrected: Double?,
    val topSpeedMetersPerSecond: Double,
    val batteryPercent: Int?,
    val estimatedRangeMeters: Double?,
    val speedUnit: SpeedUnit,
    val isRiding: Boolean,
    val connectionState: ConnectionState,
    val safetyHeadroom: Int? = null,   // ← NEW; null = unknown / no data
)
```

**Why `safetyHeadroom: Int?` (not a precomputed `Boolean`):**
`safetyHeadroom` is the raw firmware integer already present on `BoardState.safetyHeadroom`. Passing
the raw value lets the watch derive its own threshold logic and display a numeric headroom if desired
in a future iteration. A precomputed `Boolean` would permanently bake today's threshold into the
phone-side serialization, making it impossible to adjust without a dual-app update. The `Int?`
default keeps backward compatibility — a watch running old firmware simply sees `null` and stays in
ACTIVE state (safe fallback).

### 1b. Add the DataMap key constant — `core/src/main/kotlin/com/zwheel/core/model/WatchDataLayerKeys.kt`

Append one line:

```kotlin
const val KEY_SAFETY_HEADROOM = "safety_headroom"
```

`KEY_SAFETY_HEADROOM` is a `String` constant in the `core` module, so no Android import is needed.

---

## Step 2 — Phone-side serialization — `app/src/main/kotlin/com/zwheel/app/wear/WearDataLayerRepository.kt`

Two edits in this file.

### 2a. Import the new key

Add to the existing import block:

```kotlin
import com.zwheel.core.model.KEY_SAFETY_HEADROOM
```

### 2b. Populate `safetyHeadroom` in `putPayload`

Inside `putPayload`, after the last existing `dataMap.put…` call, add:

```kotlin
// Sentinel: -1 = null/unknown (DataMap has no nullable int)
dataMap.putInt(KEY_SAFETY_HEADROOM, payload.safetyHeadroom ?: -1)
```

### 2c. Compute the field in `toWatchPayload`

The private `toWatchPayload` function currently ignores `boardState.safetyHeadroom`. Update its
`return WatchPayload(...)` block to include:

```kotlin
safetyHeadroom = boardState.safetyHeadroom,
// TODO(hardware-tune): safetyHeadroom is the raw firmware integer;
// the watch treats values ≤ 0 as "approaching pushback" and null as "unknown".
// Verify the exact firmware zero-crossing on real hardware before shipping.
```

`boardState.safetyHeadroom` is already of type `Int?` on `BoardState`, so no extra computation is
required on the phone side.

### 2d. Update `toDataEntries` (test helper)

The `toDataEntries` function mirrors `putPayload` for tests. Add the same entry:

```kotlin
KEY_SAFETY_HEADROOM to (safetyHeadroom ?: -1),
```

---

## Step 3 — Watch-side deserialization — `wear/src/main/kotlin/com/zwheel/wear/WearDataLayerRepository.kt`

Three edits.

### 3a. Import the new key

```kotlin
import com.zwheel.core.model.KEY_SAFETY_HEADROOM
```

### 3b. Add parameter to `decodeWatchPayload`

The `internal fun decodeWatchPayload(...)` signature currently has 7 parameters. Add an 8th:

```kotlin
safetyHeadroomRaw: Int,
```

And inside the function body, add to the returned `WatchPayload(...)`:

```kotlin
safetyHeadroom = if (safetyHeadroomRaw < 0) null else safetyHeadroomRaw,
```

### 3c. Pass the value in the private `DataMap.toWatchPayload` extension

```kotlin
safetyHeadroomRaw = getInt(KEY_SAFETY_HEADROOM, -1),
```

`DataMap.getInt(key, defaultValue)` returns the default (`-1`) when the key is absent, ensuring
backward compatibility with payloads sent by an older version of the phone app.

### 3d. Update the existing unit test — `wear/src/test/kotlin/com/zwheel/wear/WearPayloadDecodeTest.kt`

The test's private `decode(...)` helper wraps `decodeWatchPayload`. Add an optional parameter with a
safe default so all existing tests continue to compile unchanged:

```kotlin
private fun decode(
    speedRaw: Float = 5.0f,
    topSpeedRaw: Float = 10.0f,
    batteryRaw: Int = 80,
    rangeRaw: Float = 15000f,
    speedUnitStr: String? = "MPH",
    isRiding: Boolean = true,
    connStateStr: String? = "SUBSCRIBED",
    safetyHeadroomRaw: Int = -1,   // ← NEW parameter with default
) = decodeWatchPayload(speedRaw, topSpeedRaw, batteryRaw, rangeRaw, speedUnitStr, isRiding, connStateStr, safetyHeadroomRaw)
```

Add two new test cases at the bottom of the test class:

```kotlin
@Test
fun `safety headroom sentinel -1 decodes to null`() {
    assertNull(decode(safetyHeadroomRaw = -1).safetyHeadroom)
}

@Test
fun `safety headroom value decodes correctly`() {
    assertEquals(3, decode(safetyHeadroomRaw = 3).safetyHeadroom)
}
```

---

## Step 4 — Wear color and type tokens

### 4a. New file `wear/src/main/kotlin/com/zwheel/wear/ui/WearColors.kt`

The wear module cannot import `ZWheelColors` from the `app` module (different APKs, no dependency
link). Define the small set of needed tokens locally:

```kotlin
package com.zwheel.wear.ui

import androidx.compose.ui.graphics.Color

/**
 * Wear-local color tokens. Mirrors the relevant subset of ZWheelColors from the app module.
 * Do not import from app — wear and app are separate APKs.
 */
internal object WearColors {
    val screenBlack  = Color(0xFF000000) // AMBIENT / always-on background
    val lime         = Color(0xFFC6F24E) // ACTIVE arc + speed (primary accent)
    val amber        = Color(0xFFFFB22E) // CAUTION arc + speed (rampCaution)
    val cyan         = Color(0xFF38E0FF) // DISCONNECTED scanning glyph
    val rampGood     = Color(0xFF4FE086) // arc fill at nominal battery
    val rampCaution  = Color(0xFFFFB22E) // same as amber; arc fill at caution
    val rampDanger   = Color(0xFFFF5A5A) // arc fill at danger
    val textPrimary  = Color(0xFFF2F4F7)
    val textSecondary= Color(0xFF9AA4B2)
    val textMuted    = Color(0xFF7C8696)
    val textDim      = Color(0xFF5A616E)
    val arcTrack     = Color(0xFF222222) // unfilled arc background
}
```

### 4b. New file `wear/src/main/kotlin/com/zwheel/wear/ui/WearType.kt`

```kotlin
package com.zwheel.wear.ui

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.zwheel.wear.R

internal val SairaWearFamily = FontFamily(
    Font(R.font.saira_bold,  FontWeight.Bold),    // W700 — watch speed hero
    Font(R.font.saira_black, FontWeight.Black),   // W900 — unused but available
)

internal val MonoWearFamily = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium,  FontWeight.Medium),
    Font(R.font.jetbrains_mono_bold,    FontWeight.Bold),
)

/** 64sp speed hero, tabular nums */
internal val wearSpeedStyle = TextStyle(
    fontFamily = SairaWearFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 64.sp,
    fontFeatureSettings = "tnum",
    letterSpacing = 0.sp,
    lineHeight = 64.sp,
)

/** 13sp mono label (unit, stat captions) */
internal val wearLabelStyle = TextStyle(
    fontFamily = MonoWearFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 13.sp,
    letterSpacing = 0.5.sp,
)

/** 11sp mono small (stat values, batt/dist line) */
internal val wearSmallStyle = TextStyle(
    fontFamily = MonoWearFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 11.sp,
    fontFeatureSettings = "tnum",
    letterSpacing = 0.sp,
)
```

---

## Step 5 — Restyle `ZWheelWearScreen.kt` (the only composable file)

Replace the entire content of
`wear/src/main/kotlin/com/zwheel/wear/ui/ZWheelWearScreen.kt` with the implementation described
below. The soft limit is 300 lines; split into private composable functions within the same file
(or a sibling file `WearFaces.kt`) if needed.

Keep the existing public entry point signature — `MainScreen.kt` and `MainActivity.kt` must not
change:

```kotlin
@Composable
fun ZWheelWearScreen(payload: WatchPayload?, isAmbient: Boolean = false)
```

### 5a. `WearDashboardUiState` additions

Add `pushbackApproaching: Boolean` and a convenience to derive the active face:

```kotlin
private data class WearDashboardUiState(
    val speedDisplay: String,
    val speedDecimalDisplay: String,      // single decimal digit, dimmed
    val speedUnitLabel: String,
    val topSpeedDisplay: String,
    val batteryPercent: Int,
    val batteryDisplay: String,
    val rangeDisplay: String,
    val connectionLabel: String,
    val pushbackApproaching: Boolean,     // ← NEW: drives CAUTION face
    val isConnected: Boolean,
) {
    enum class Face { ACTIVE, CAUTION, AMBIENT, DISCONNECTED }

    fun activeFace(isAmbient: Boolean): Face = when {
        isAmbient                -> Face.AMBIENT
        !isConnected             -> Face.DISCONNECTED
        pushbackApproaching      -> Face.CAUTION
        else                     -> Face.ACTIVE
    }

    companion object {
        fun empty() = WearDashboardUiState(
            speedDisplay = "--",
            speedDecimalDisplay = "-",
            speedUnitLabel = "MPH",
            topSpeedDisplay = "--",
            batteryPercent = 0,
            batteryDisplay = "--%",
            rangeDisplay = "--",
            connectionLabel = "SCANNING",
            pushbackApproaching = false,
            isConnected = false,
        )
    }
}
```

### 5b. `WatchPayload.toUiState()` — derive `pushbackApproaching`

```kotlin
// TODO(hardware-tune): safetyHeadroom ≤ 0 is treated as "approaching pushback".
// Verify the exact zero-crossing on real hardware before shipping.
// If safetyHeadroom is null (older phone app), fall back to false (safe / no warning).
val pushbackApproaching = safetyHeadroom?.let { it <= 0 } ?: false
```

Include `pushbackApproaching` and `isConnected = (connectionState == ConnectionState.SUBSCRIBED ||
connectionState == ConnectionState.DEGRADED)` in the returned `WearDashboardUiState`.

For `speedDisplay` / `speedDecimalDisplay`, split integer and fractional parts so the decimal can
be shown smaller and dimmed:

```kotlin
val speedFloat = speedMetersPerSecondCorrected?.times(speedConversion)
val speedInt   = speedFloat?.toInt() ?: null
val speedDec   = speedFloat?.let { ((it % 1) * 10).toInt() }
val speedDisplay        = speedInt?.toString() ?: "--"
val speedDecimalDisplay = speedDec?.toString() ?: "-"
```

### 5c. Face router

```kotlin
@Composable
fun ZWheelWearScreen(payload: WatchPayload?, isAmbient: Boolean = false) {
    val state = payload?.toUiState() ?: WearDashboardUiState.empty()
    when (state.activeFace(isAmbient)) {
        WearDashboardUiState.Face.ACTIVE       -> ActiveFace(state)
        WearDashboardUiState.Face.CAUTION      -> CautionFace(state)
        WearDashboardUiState.Face.AMBIENT      -> AmbientFace(state)
        WearDashboardUiState.Face.DISCONNECTED -> DisconnectedFace(state)
    }
}
```

### 5d. Shared `ProgressArc` composable

All interactive faces share a progress arc. Extract it once:

```kotlin
@Composable
private fun ProgressArc(
    fraction: Float,           // 0f–1f fill fraction
    arcColor: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(200.dp)) {
        val stroke = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
        val inset  = 8.dp.toPx() / 2f
        val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
        val topLeft = Offset(inset, inset)
        // track
        drawArc(WearColors.arcTrack, -90f, 360f, false, topLeft, arcSize, style = stroke)
        // fill
        if (fraction > 0f) {
            drawArc(arcColor, -90f, 360f * fraction.coerceIn(0f, 1f), false, topLeft, arcSize, style = stroke)
        }
    }
}
```

### 5e. ACTIVE face

```
@Composable
private fun ActiveFace(state: WearDashboardUiState) {
    Box(
        Modifier.fillMaxSize().background(WearColors.screenBlack),
        contentAlignment = Alignment.Center,
    ) {
        ProgressArc(
            fraction = state.batteryPercent / 100f,
            arcColor  = WearColors.lime,
            modifier  = Modifier.align(Alignment.Center),
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Speed hero: integer in lime, decimal smaller + dimmed
            Row(verticalAlignment = Alignment.Bottom) {
                Text(state.speedDisplay,    style = wearSpeedStyle,  color = WearColors.lime)
                Text(".${state.speedDecimalDisplay}",
                    style = wearSpeedStyle.copy(fontSize = 32.sp),
                    color = WearColors.textMuted)
            }
            Text(state.speedUnitLabel, style = wearLabelStyle, color = WearColors.textSecondary)
            Spacer(Modifier.height(4.dp))
            // batt% and range on one line
            Text(
                text = "${state.batteryDisplay}  ${state.rangeDisplay}",
                style = wearSmallStyle,
                color = WearColors.textSecondary,
            )
        }
        // TOP caption at bottom
        Column(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("TOP  ${state.topSpeedDisplay}", style = wearSmallStyle, color = WearColors.textMuted)
        }
    }
}
```

### 5f. CAUTION face

```
@Composable
private fun CautionFace(state: WearDashboardUiState) {
    // Optional 1.1s blink animation on the warning banner
    val alpha by rememberInfiniteTransition(label = "caution-blink").animateFloat(
        initialValue = 1f, targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(550, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "alpha",
    )

    Box(
        Modifier.fillMaxSize().background(WearColors.screenBlack),
        contentAlignment = Alignment.Center,
    ) {
        ProgressArc(
            fraction = state.batteryPercent / 100f,
            arcColor  = WearColors.amber,
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Warning banner — blinking
            Text(
                text   = "▲ PUSHBACK SOON",
                style  = wearSmallStyle.copy(fontSize = 12.sp),
                color  = WearColors.amber.copy(alpha = alpha),
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(state.speedDisplay,
                    style = wearSpeedStyle,
                    color = WearColors.amber)
                Text(".${state.speedDecimalDisplay}",
                    style = wearSpeedStyle.copy(fontSize = 32.sp),
                    color = WearColors.amber.copy(alpha = 0.5f))
            }
            Text(state.speedUnitLabel, style = wearLabelStyle, color = WearColors.amber)
        }
    }
}
```

### 5g. AMBIENT face

Minimal for AOD power saving. Pure black background, no animations, no arcs.

```
@Composable
private fun AmbientFace(state: WearDashboardUiState) {
    Box(
        Modifier.fillMaxSize().background(WearColors.screenBlack),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                state.speedDisplay,
                style = wearSpeedStyle.copy(fontSize = 52.sp),
                color = WearColors.textDim,
            )
            Text(
                "SPEED · ${state.speedUnitLabel}",
                style = wearLabelStyle,
                color = WearColors.textDim,
                letterSpacing = 2.sp,
            )
        }
    }
}
```

### 5h. DISCONNECTED face

Cyan scanning glyph with an expanding ring pulse.

```
@Composable
private fun DisconnectedFace(state: WearDashboardUiState) {
    val ringAlpha by rememberInfiniteTransition(label = "scan-ring").animateFloat(
        initialValue = 0.6f, targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ring-alpha",
    )
    val ringRadius by rememberInfiniteTransition(label = "scan-radius").animateFloat(
        initialValue = 40f, targetValue = 100f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ring-radius",
    )

    Box(
        Modifier.fillMaxSize().background(WearColors.screenBlack),
        contentAlignment = Alignment.Center,
    ) {
        // Expanding ring drawn behind icon
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                color  = WearColors.cyan.copy(alpha = ringAlpha),
                radius = ringRadius.dp.toPx(),
                style  = Stroke(width = 2.dp.toPx()),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector       = Icons.Filled.BluetoothSearching,
                contentDescription = "Searching",
                tint              = WearColors.cyan,
                modifier          = Modifier.size(36.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text("SCANNING", style = wearLabelStyle, color = WearColors.cyan)
            Spacer(Modifier.height(4.dp))
            Text("looking for board…", style = wearLabelStyle.copy(fontSize = 11.sp),
                color = WearColors.textMuted)
        }
    }
}
```

Icon import: `Icons.Filled.BluetoothSearching` —
`androidx.compose.material.icons.filled.BluetoothSearching`. If unavailable in
`Icons.Filled`, use `Icons.Outlined.BluetoothSearching`.

---

## Step 6 — `@Preview` annotations (required, no screenshot test needed)

Add four `@Preview` functions at the bottom of `ZWheelWearScreen.kt` (or in a companion
`WearScreenPreviews.kt` file if needed to stay under 300 lines).

Use Wear-specific preview annotation:

```kotlin
import androidx.wear.compose.ui.tooling.preview.WearPreviewRound
```

If `WearPreviewRound` is not on the classpath (check `wear/build.gradle.kts` dependencies first),
fall back to the standard `@Preview(widthDp = 240, heightDp = 240, showBackground = true,
backgroundColor = 0xFF000000)`.

Provide a sample `WatchPayload` for each. Keep them self-contained; do NOT use a mock function from
another module.

```kotlin
@Preview(widthDp = 240, heightDp = 240, showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PreviewActiveFace() {
    ZWheelWearScreen(
        payload = WatchPayload(
            speedMetersPerSecondCorrected = 5.8,
            topSpeedMetersPerSecond = 8.0,
            batteryPercent = 72,
            estimatedRangeMeters = 9000.0,
            speedUnit = SpeedUnit.MPH,
            isRiding = true,
            connectionState = ConnectionState.SUBSCRIBED,
            safetyHeadroom = 5,
        ),
        isAmbient = false,
    )
}

@Preview(widthDp = 240, heightDp = 240, showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PreviewCautionFace() {
    ZWheelWearScreen(
        payload = WatchPayload(
            speedMetersPerSecondCorrected = 8.4,
            topSpeedMetersPerSecond = 8.4,
            batteryPercent = 48,
            estimatedRangeMeters = 5000.0,
            speedUnit = SpeedUnit.MPH,
            isRiding = true,
            connectionState = ConnectionState.SUBSCRIBED,
            safetyHeadroom = 0,   // ≤ 0 → CAUTION
        ),
        isAmbient = false,
    )
}

@Preview(widthDp = 240, heightDp = 240, showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PreviewAmbientFace() {
    ZWheelWearScreen(
        payload = WatchPayload(
            speedMetersPerSecondCorrected = 6.1,
            topSpeedMetersPerSecond = 8.0,
            batteryPercent = 60,
            estimatedRangeMeters = 7200.0,
            speedUnit = SpeedUnit.MPH,
            isRiding = true,
            connectionState = ConnectionState.SUBSCRIBED,
            safetyHeadroom = 4,
        ),
        isAmbient = true,
    )
}

@Preview(widthDp = 240, heightDp = 240, showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PreviewDisconnectedFace() {
    ZWheelWearScreen(
        payload = WatchPayload(
            speedMetersPerSecondCorrected = null,
            topSpeedMetersPerSecond = 0.0,
            batteryPercent = null,
            estimatedRangeMeters = null,
            speedUnit = SpeedUnit.MPH,
            isRiding = false,
            connectionState = ConnectionState.SCANNING,
            safetyHeadroom = null,
        ),
        isAmbient = false,
    )
}
```

---

## Step 7 — `wear/build.gradle.kts` — add material icons extended

The `DisconnectedFace` uses `Icons.Filled.BluetoothSearching`, which lives in the extended icons
artifact. Check whether `androidx.compose.material:material-icons-extended` (or the BOM-resolved
version) is already listed. If it is NOT present, add:

```kotlin
implementation(libs.androidx.compose.material.icons.extended)
```

If the version catalog alias does not exist, add this line directly under the BOM line:

```kotlin
implementation("androidx.compose.material:material-icons-extended")
// version resolved from compose BOM already on classpath
```

The existing `implementation(platform(libs.androidx.compose.bom))` in `wear/build.gradle.kts`
ensures the correct version is picked without a hardcoded version string.

---

## Constraints (self-review before committing)

1. **`core/` stays Android-free.** The only change is adding `safetyHeadroom: Int? = null` to
   `WatchPayload` and `KEY_SAFETY_HEADROOM` to `WatchDataLayerKeys.kt`. No `android.*` or
   `androidx.*` imports may be introduced in `core/`.
2. **No new BLE UUID.** The pushback signal travels in the existing Wearable Data Layer item at
   `/zwheel/state` via the new `KEY_SAFETY_HEADROOM` DataMap entry.
3. **No phone UI touched.** Do not edit any file under `app/src/main/kotlin/.../ui/`.
4. **No screenshot/Roborazzi tests for wear faces.** Round Wear faces do not render reliably under
   Robolectric. Instead, provide the four `@Preview` composables (Step 6) so faces can be
   eyeballed in Android Studio. The pass criterion for tests is `:wear:compileDebugKotlin` + the
   existing `:wear:test` (unit tests in `WearPayloadDecodeTest.kt`) passing after your changes.
5. **300-line soft limit per file.** If `ZWheelWearScreen.kt` exceeds 300 lines, move the four
   face composables to a new file `wear/src/main/kotlin/com/zwheel/wear/ui/WearFaces.kt` and keep
   only `ZWheelWearScreen`, `WearDashboardUiState`, and `WatchPayload.toUiState` in
   `ZWheelWearScreen.kt`.
6. **`WearColors` and `WearType` are internal.** Do not expose them outside the wear module;
   all declarations use `internal` or `private`.
7. **Backward compatibility.** The new `safetyHeadroom` field on `WatchPayload` has a default of
   `null`. An older phone app that does not send `KEY_SAFETY_HEADROOM` will be decoded as `null`
   (via `DataMap.getInt(key, -1)` → `-1` sentinel → `null`), which the watch treats as "no
   pushback warning" — the safe fallback.
8. **`MainScreen.kt` and `MainActivity.kt` must not change.** The entry point signature
   `ZWheelWearScreen(payload: WatchPayload?, isAmbient: Boolean = false)` is unchanged.

---

## Build & commit

```bash
# From the worktree root:
GRADLE_OPTS="-Xmx4g" ./gradlew :core:compileDebugKotlin :app:compileDebugKotlin :wear:compileDebugKotlin :wear:test
```

Fix ALL errors before committing.

Two commits, in order:

```
feat(wear): plumb pushback signal through WatchPayload
```
Contains: `core/model/BoardModels.kt`, `core/model/WatchDataLayerKeys.kt`,
`app/wear/WearDataLayerRepository.kt`, `wear/WearDataLayerRepository.kt`,
`wear/WearPayloadDecodeTest.kt`.

```
feat(wear): dark redesign of the 4 watch faces
```
Contains: `wear/res/font/` (5 TTFs), `wear/ui/WearColors.kt`, `wear/ui/WearType.kt`,
`wear/ui/ZWheelWearScreen.kt` (full restyle), `wear/build.gradle.kts` (if icons-extended added).

Do NOT add any Co-Authored-By line to commit messages.
