# Gate: Issue #2 — Run Both BLE Scan Paths Concurrently

**Branch:** `codex/issue2-scan-concurrency`
**Base:** `main`
**Closes:** #2
**One concern:** The name-prefix fallback scan only starts when the UUID-filter scan produces zero results. Run both paths concurrently so boards that don't advertise the service UUID are always discovered.

---

## Context

Current behavior in `KableBleTransport.scan()`:

```kotlin
val primaryHadResults = AtomicBoolean(false)
val primary = scanner(serviceUuid = OwUuids.ONEWHEEL_SERVICE)
    .advertisements
    .onEach { primaryHadResults.set(true) }
    .map { ... }

val fallback = flow {
    delay(NAME_FALLBACK_DELAY_MS)
    if (!primaryHadResults.get()) {        // ← only runs if primary found nothing
        emitAll(scanner(serviceUuid = null)...)
    }
}
```

If any UUID-advertising board is nearby, `primaryHadResults` becomes `true` and the
fallback never starts. A second board that only advertises by name is invisible.

Fix: always start the fallback after the delay, regardless of primary results. The
existing `ConnectionManager` dedup by `deviceId.lowercase()` already prevents duplicates.

---

## Allowed files (touch ONLY these)

```
app/src/main/kotlin/com/zwheel/app/ble/KableBleTransport.kt   ← update scan()
```

---

## Implementation spec

In `KableBleTransport.scan()`:

1. Delete `val primaryHadResults = AtomicBoolean(false)`.
2. Remove `.onEach { primaryHadResults.set(true) }` from the `primary` flow chain.
3. In the `fallback` flow, remove the `if (!primaryHadResults.get())` conditional so
   `emitAll(...)` always runs after the delay.
4. Remove the `import java.util.concurrent.atomic.AtomicBoolean` import — it is no
   longer used.

The result should look like:

```kotlin
override suspend fun scan(): Flow<ScanResult> {
    val primary = scanner(serviceUuid = OwUuids.ONEWHEEL_SERVICE)
        .advertisements
        .map { advertisement -> advertisement.toScanResult() }

    val fallback = flow {
        delay(NAME_FALLBACK_DELAY_MS)
        emitAll(
            scanner(serviceUuid = null)
                .advertisements
                .filter { advertisement -> advertisement.onewheelName() != null }
                .map { advertisement -> advertisement.toScanResult(displayName = advertisement.onewheelName()) },
        )
    }

    return merge(primary, fallback)
        .onStart { _connectionState.value = ConnectionState.Scanning }
        .onCompletion {
            if (_connectionState.value == ConnectionState.Scanning) {
                _connectionState.value = ConnectionState.Idle
            }
        }
}
```

No other changes. Do not touch scan settings, dedup logic, or any other file.

---

## Verification

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew --no-daemon :app:compileDebugKotlin
```

Must pass. Confirm `AtomicBoolean` import is gone and `primaryHadResults` has no remaining references.

Commit message: `fix(ble): run name-fallback scan concurrently with UUID scan (#2)`
