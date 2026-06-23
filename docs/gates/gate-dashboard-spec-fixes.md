# Gate: Dashboard Spec Fixes

Apply six surgical fixes to bring the ZWheel dashboard UI into conformance with the design spec at docs/design/spec.md. Make only the changes described below — do not refactor or alter anything else.

---

## Fix 1 — SpeedSlab gradient top color

**File:** `app/src/main/kotlin/com/zwheel/app/ui/dashboard/SpeedSlab.kt`

The spec defines the speed slab background gradient as #13160D (warm dark-lime) → #0A0B0E (pure black). The current code uses `c.legendCard` (#101319, a cold grey) as the start color, which is wrong.

Change:
```kotlin
.background(Brush.verticalGradient(listOf(c.legendCard, c.screenBg)))
```
To:
```kotlin
.background(Brush.verticalGradient(listOf(Color(0xFF13160D), c.screenBg)))
```

The file already imports `androidx.compose.ui.graphics.Brush`. Add `import androidx.compose.ui.graphics.Color` if it is not already present in the import block (check before adding — do not duplicate).

---

## Fix 2 — Speed hero letterSpacing

**File:** `app/src/main/kotlin/com/zwheel/app/ui/dashboard/SpeedSlab.kt`

The spec specifies "Speed hero: -6px tracking". Both the integer Text composable (fontSize = 96.sp) and the decimal Text composable (fontSize = 38.sp) are missing `letterSpacing`.

Add `letterSpacing = (-6).sp` to the `TextStyle` of each of those two Text composables. The import `androidx.compose.ui.unit.sp` is already present.

After Fix 1 the integer Text style looks like:
```kotlin
style = TextStyle(
    fontFamily = SairaFamily,
    fontSize = 96.sp,
    fontWeight = FontWeight.Black,
    lineHeight = 96.sp,
    fontFeatureSettings = "tnum",
    platformStyle = PlatformTextStyle(includeFontPadding = false),
),
```
Add `letterSpacing = (-6).sp,` inside that TextStyle.

The decimal Text style (fontSize = 38.sp) similarly needs `letterSpacing = (-6).sp,` added to its TextStyle.

---

## Fix 3 — Battery hero card radius

**File:** `app/src/main/kotlin/com/zwheel/app/ui/dashboard/BatteryBand.kt`

The spec says battery + range cards use 18dp corner radius (same as TripCard). `DashboardCard` already accepts a `shape` parameter (default is `RoundedCornerShape(16.dp)`).

In `BatteryCard`, change:
```kotlin
DashboardCard(modifier = modifier) {
```
To:
```kotlin
DashboardCard(modifier = modifier, shape = RoundedCornerShape(18.dp)) {
```

In `RangeCard`, change:
```kotlin
DashboardCard(modifier = modifier) {
```
To:
```kotlin
DashboardCard(modifier = modifier, shape = RoundedCornerShape(18.dp)) {
```

`RoundedCornerShape` is already imported in BatteryBand.kt — do not add a duplicate import.

---

## Fix 4 — Cell label zero-padding

**File:** `app/src/main/kotlin/com/zwheel/app/ui/DashboardState.kt`

The spec shows cell labels as "C01", "C02", …, "C16" (zero-padded two-digit index).

There are two places that construct `CellVoltageUiState` with `label = "C${index + 1}"`:

1. In `mockDashboardState()` (around line 71):
   ```kotlin
   CellVoltageUiState(label = "C${index + 1}", volts = volts, isLow = volts < CellThresholds.LOW_VOLTS)
   ```
   Change to:
   ```kotlin
   CellVoltageUiState(label = "C%02d".format(index + 1), volts = volts, isLow = volts < CellThresholds.LOW_VOLTS)
   ```

2. In `BoardState.toDashboardUiState()` (around line 132):
   ```kotlin
   CellVoltageUiState(label = "C${index + 1}", volts = volts, isLow = volts < CellThresholds.LOW_VOLTS)
   ```
   Change to:
   ```kotlin
   CellVoltageUiState(label = "C%02d".format(index + 1), volts = volts, isLow = volts < CellThresholds.LOW_VOLTS)
   ```

Search for `"C${` in the file to confirm these are the only two occurrences.

---

## Fix 5 — Cell strip bars always visible

**File:** `app/src/main/kotlin/com/zwheel/app/ui/dashboard/CellStrip.kt`

The golden notes say "Collapsed bar strip visible above the grid". Currently the bar strip Row is wrapped in `AnimatedVisibility(visible = !expanded)` which hides it when the grid is expanded.

Remove the `AnimatedVisibility(visible = !expanded)` wrapper so the bar strip Row is always shown. Keep the `AnimatedVisibility(visible = expanded)` that wraps the `LazyVerticalGrid` — only remove the one wrapping the bars.

Before (around line 84):
```kotlin
AnimatedVisibility(visible = !expanded) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    ) {
        cells.forEach { cell ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(32.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(cellVoltageColor(cell.volts, c)),
            )
        }
    }
}
```

After (remove the AnimatedVisibility wrapper, keep the Row as-is):
```kotlin
Row(
    horizontalArrangement = Arrangement.spacedBy(3.dp),
    modifier = Modifier
        .fillMaxWidth()
        .padding(top = 8.dp),
) {
    cells.forEach { cell ->
        Box(
            modifier = Modifier
                .weight(1f)
                .height(32.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(cellVoltageColor(cell.volts, c)),
        )
    }
}
```

---

## Fix 6 — Battery caption: remove voltage prefix

**File:** `app/src/main/kotlin/com/zwheel/app/ui/dashboard/BatteryBand.kt`

The spec says the battery card caption should show only the pack size label, e.g. "16S PACK". The current code prepends the voltage.

In `BatteryCard`, change:
```kotlin
text = "%.1fV · %dS PACK".format(state.packVoltage, state.cellVoltages.size),
```
To:
```kotlin
text = "%dS PACK".format(state.cellVoltages.size),
```

---

## Verification

After making all changes, the affected files are:
- `app/src/main/kotlin/com/zwheel/app/ui/dashboard/SpeedSlab.kt` (Fix 1, Fix 2)
- `app/src/main/kotlin/com/zwheel/app/ui/dashboard/BatteryBand.kt` (Fix 3, Fix 6)
- `app/src/main/kotlin/com/zwheel/app/ui/DashboardState.kt` (Fix 4)
- `app/src/main/kotlin/com/zwheel/app/ui/dashboard/CellStrip.kt` (Fix 5)

Do not modify any other files. Do not add, remove, or rename any functions beyond what is described above.
