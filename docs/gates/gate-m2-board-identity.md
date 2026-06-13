# Gate: M2 Board Identity + Type Detection

**Branch:** `codex/m2-board-identity`
**One concern:** Read HW/FW revision at connect, detect BoardType, populate BoardIdentity in BoardState, fix tire-diameter fallback.

---

## Problem

`ConnectionManager.connect()` hardcodes `val boardType = BoardType.XR`. The detected HW/FW revision is never stored. `BoardState.identity` is always `null` in the live app. The tire-diameter selection uses only the user's setting with no per-board-type stock fallback.

---

## Allowed files (touch ONLY these; stop if you need anything else)

```
core/src/main/kotlin/com/zwheel/core/protocol/BoardTypeDetector.kt   ← new file
core/src/main/kotlin/com/zwheel/core/service/BoardStateServiceImpl.kt ← add boardIdentity param
app/src/main/kotlin/com/zwheel/app/ble/ConnectionManager.kt           ← wire it all together
core/src/test/kotlin/com/zwheel/core/protocol/BoardTypeDetectorTest.kt ← new file
```

---

## Implementation spec

### 1. `core/protocol/BoardTypeDetector.kt` (new, pure Kotlin — zero Android imports)

```kotlin
object BoardTypeDetector {
    fun detect(hardwareRevision: Int): BoardType
}
```

Map `hardwareRevision` (the uint16 big-endian value already parsed by `Parsers.hardwareRevision()`) to a `BoardType`. Use OWCE's `OWBoard.cs` hardware-revision ranges as the source of truth. Look them up from the OWCE repo online — it has a `GetBoardType` or similar method mapping hw revision integers to board model. The known anchor: HW 4209 → `BoardType.XR` (Corey's board). Return `BoardType.UNKNOWN` for anything outside known ranges. Do NOT guess ranges — look up OWCE.

### 2. `core/service/BoardStateServiceImpl.kt`

Add a `boardIdentity: BoardIdentity?` constructor parameter (nullable, defaults to `null`). In `start()`, before launching collectors, set the initial state:

```kotlin
_state.update { it.copy(identity = boardIdentity) }
```

That's the only change — one new constructor param + one update call. No other logic changes.

### 3. `app/ble/ConnectionManager.kt`

After `GeminiStrategy().unlock(transport)` succeeds, add:

```kotlin
val hwBytes = transport.read(OwUuids.HARDWARE_REVISION)
val fwBytes  = transport.read(OwUuids.FIRMWARE_REVISION)
val hwRev    = Parsers.hardwareRevision(hwBytes)
val fwRev    = Parsers.firmwareRevision(fwBytes)
val boardType = BoardTypeDetector.detect(hwRev)

val identity = BoardIdentity(
    boardId = deviceId,
    name = boardType.displayName,
    type = boardType,
    firmwareRevision = fwRev.toString(),
    hardwareRevision = hwRev.toString(),
)
```

Then fix the diameter selection — use the user's saved diameter only if it differs meaningfully from the generic 11.5" default (i.e. the user has actually set it); otherwise fall back to the detected board's stock diameter:

```kotlin
val savedDiameter = settingsRepository.preferences.first().tireDiameterInches
val tireDiameter = if (savedDiameter != UserPreferences().tireDiameterInches) {
    savedDiameter
} else {
    boardType.stockTireDiameterInches
}
```

Pass `boardIdentity = identity` to the `BoardStateServiceImpl` constructor.

---

## Constraints

- `core/` must have **zero** `android.*` / `androidx.*` imports. CI enforces this — the build will fail if violated.
- `BoardTypeDetector` is a pure Kotlin `object` in `core/protocol/`.
- Do not modify `OwUuids.kt`, `Parsers.kt`, or any other files.
- Tests land in the same commit as the code they cover (AGENTS.md rule 9).

## Tests

`BoardTypeDetectorTest.kt` must cover:
- Corey's known board: `detect(4209) == BoardType.XR`
- At least one value per other known board type (V1, Plus, Pint, Pint X)
- `detect(<out-of-range>)` returns `BoardType.UNKNOWN`

---

## Verification

Run before finishing:

```bash
./gradlew :core:test :app:compileDebugKotlin
```

Both must be green. Fix any errors before reporting done.
