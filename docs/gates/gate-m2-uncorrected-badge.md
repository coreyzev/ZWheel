# Gate: M2 Uncorrected Speed Badge

**Branch:** `codex/m2-uncorrected-badge`
**One concern:** Surface an "UNCORRECTED" indicator in the UI when corrected speed is unavailable (AGENTS.md §3 safety rule).

---

## Problem

AGENTS.md §3 (safety rule) says: "if calibration data is missing, show raw value with an 'uncorrected' badge rather than a guess." Currently, when `BoardState.speedMetersPerSecondCorrected == null`, the UI silently shows 0.0 with no indication. Users could mistake 0 for accurate data.

---

## Allowed files (touch ONLY these)

```
app/src/main/kotlin/com/zwheel/app/ui/DashboardState.kt       ← add isSpeedCorrected field
app/src/main/kotlin/com/zwheel/app/ui/DashboardComponents.kt  ← show badge in speed card
```

---

## Implementation spec

### 1. `DashboardState.kt`

Add one field to `DashboardUiState`:

```kotlin
val isSpeedCorrected: Boolean,
```

In `BoardState.toDashboardUiState()`, set it:

```kotlin
isSpeedCorrected = speedMetersPerSecondCorrected != null,
```

Update `mockDashboardState()` to include `isSpeedCorrected = true`.
Update `emptyDashboardState()` — it calls `BoardState().toDashboardUiState(...)` so it picks up the new field automatically as long as the mapping is correct.

### 2. `DashboardComponents.kt`

In the speed card composable (wherever `uiState.speedMph` / speed value is displayed), add a small label below the speed numeral that reads **"UNCORRECTED"** when `!uiState.isSpeedCorrected`. Use a `Text` with a warning color (e.g. `MaterialTheme.colorScheme.error` or amber — pick what looks reasonable in a dark theme). Only show it when `!isSpeedCorrected`.

Keep it unobtrusive — small font (e.g. `MaterialTheme.typography.labelSmall`), centered below the speed number.

---

## Constraints

- No new dependencies. Use only what's already imported.
- No changes to `core/` — this is a pure UI + mapping change.
- Do not change `BoardState`, `BoardStateServiceImpl`, or any BLE/transport files.

---

## Verification

```bash
./gradlew :app:compileDebugKotlin
```

Must compile clean. Fix any errors before reporting done. There are no unit tests for this UI change; a compile-clean check is sufficient.
