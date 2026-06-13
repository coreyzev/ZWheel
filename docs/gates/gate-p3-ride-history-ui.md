# Gate: Phase 3d — Ride History and Detail Screens

**Branch:** `codex/p3-ride-history-ui`
**Depends on:** `codex/p3-ride-recording` merged first (needs `RideRepository` + sessions in DB)
**One concern:** Compose screens for ride history list and ride detail.

---

## Allowed files

```
app/src/main/kotlin/com/zwheel/app/ui/history/RideHistoryViewModel.kt   ← new
app/src/main/kotlin/com/zwheel/app/ui/history/RideHistoryScreen.kt      ← new
app/src/main/kotlin/com/zwheel/app/ui/ZWheelAppScreen.kt                ← add nav entry
```

---

## ViewModel spec

### `RideHistoryViewModel.kt`

```kotlin
@HiltViewModel
class RideHistoryViewModel @Inject constructor(
    private val repository: RideRepository,
    private val prefs: SettingsRepository,
) : ViewModel() {
    val sessions: StateFlow<List<RideHistoryItem>> = combine(
        repository.getAllSessions(),
        prefs.preferences,
    ) { sessions, prefs ->
        sessions.map { session ->
            val correctedSpeedMph = UnitConversions.metersPerSecondToMph(
                session.maxSpeedMetersPerSecondCorrected,
            )
            val distanceMiles = UnitConversions.metersToMiles(session.distanceMetersCorrected)
            RideHistoryItem(
                id = session.id,
                dateLabel = formatDate(session.startEpochMillis),
                durationLabel = formatDuration(session.startEpochMillis, session.endEpochMillis),
                distanceLabel = if (prefs.speedUnit == SpeedUnit.MPH) {
                    "%.1f mi".format(distanceMiles)
                } else {
                    "%.1f km".format(UnitConversions.metersToKilometers(session.distanceMetersCorrected))
                },
                topSpeedLabel = if (prefs.speedUnit == SpeedUnit.MPH) {
                    "%.0f mph".format(correctedSpeedMph)
                } else {
                    "%.0f kph".format(UnitConversions.metersPerSecondToKph(session.maxSpeedMetersPerSecondCorrected))
                },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

data class RideHistoryItem(
    val id: String,
    val dateLabel: String,
    val durationLabel: String,
    val distanceLabel: String,
    val topSpeedLabel: String,
)
```

Private helper functions in the same file:
- `formatDate(epochMillis: Long): String` — format with `SimpleDateFormat("MMM d, yyyy", Locale.getDefault())`
- `formatDuration(startMs: Long, endMs: Long?): String` — `"${minutes}m"` or `"--"` if endMs null

Add `UnitConversions.metersToMiles()` and `metersToKilometers()` if not already present in `core/calc/UnitConversions.kt`. These are allowed additions to that file if needed (check first).

---

## Screen spec

### `RideHistoryScreen.kt`

A single Compose screen showing:
1. **Empty state** (if `sessions.isEmpty()`): centered text "No rides yet. Go ride!"
2. **List** (if sessions not empty): `LazyColumn` with one card per `RideHistoryItem`.

Each card (similar styling to dashboard cards — dark background, rounded corners):
```
[date]        [top speed]
[duration]    [distance]
```

No tap-to-detail for now — leave a comment `// TODO(m3): navigate to RideDetailScreen`. The detail screen is a v1.5 item per the architecture doc.

Stateless `@Composable fun RideHistoryScreen(viewModel: RideHistoryViewModel = hiltViewModel())`.

---

## Navigation wiring (`ZWheelAppScreen.kt`)

Add a "History" tab/button to the existing navigation. Look at how the Settings screen is navigated to and follow the same pattern. Add a `History` destination to whatever navigation structure exists.

---

## Constraints

- `core/` may only be touched to add `metersToMiles`/`metersToKilometers` to `UnitConversions.kt` if missing; nothing else in `core/`.
- No new dependencies beyond what's already in the project.
- AGENTS.md rule 7: no UI in services, no `MutableState` in non-`@Composable`. State comes from ViewModel `StateFlow`.

---

## Verification

```bash
./gradlew :app:compileDebugKotlin
```

Must pass. Fix errors before reporting done.
