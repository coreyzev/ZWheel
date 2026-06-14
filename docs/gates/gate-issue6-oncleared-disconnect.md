# Gate: Issue #6 — BleDebugViewModel.onCleared() Disconnect Race

**Branch:** `codex/issue6-oncleared-disconnect`
**Base:** `main`
**Closes:** #6
**One concern:** `onCleared()` cancels `viewModelScope` immediately after launching disconnect, so the coroutine may never run. Fix the ordering so disconnect completes before the scope is cancelled.

---

## Context

Current `onCleared()` in `BleDebugViewModel`:

```kotlin
override fun onCleared() {
    dumpJobs.forEach(Job::cancel)
    scanJob?.cancel()
    super.onCleared()   // ← cancels viewModelScope here
    // any pending disconnect coroutine is now dead
}
```

The debug screen's `onDisconnectClicked()` launches disconnect in `viewModelScope`. If the
user navigates away without explicitly disconnecting, `onCleared()` is called. The
`viewModelScope` is cancelled at `super.onCleared()`, racing any in-flight disconnect.

Fix: call `transport.disconnect()` synchronously in `onCleared()` using `runBlocking`,
before `super.onCleared()`. This is acceptable for the debug screen (single-device
diagnostic tool, not on the critical path). The production ride path is unaffected —
`ConnectionManager` owns BLE for production.

---

## Allowed files (touch ONLY these)

```
app/src/main/kotlin/com/zwheel/app/ui/ble/BleDebugViewModel.kt   ← fix onCleared
```

---

## Implementation spec

In `BleDebugViewModel.onCleared()`:

```kotlin
override fun onCleared() {
    dumpJobs.forEach(Job::cancel)
    scanJob?.cancel()
    keepAliveJob?.cancel()
    runBlocking { runCatching { transport.disconnect() } }
    super.onCleared()
}
```

Add `import kotlinx.coroutines.runBlocking` if not already present.

Remove any existing `viewModelScope.launch { runCatching { transport.disconnect() } }` call
from `onCleared()` if one was added previously (there may not be one — check the current
file before editing).

Also remove the duplicate disconnect launch from `onDisconnectClicked()` if and only if
that method already calls `transport.disconnect()` via another path. Read the file first.
The `onDisconnectClicked()` path is user-initiated and should keep its own explicit
disconnect for responsiveness — do not remove it.

---

## Verification

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew --no-daemon :app:compileDebugKotlin
```

Must pass. Confirm `runBlocking` is imported and used in `onCleared()`.

Commit message: `fix(ble): complete disconnect before viewModelScope cancels in onCleared (#6)`
