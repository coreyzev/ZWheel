# Gate: PR #49 — Keep-Alive Cleanup

**Branch:** `fix/gemini-keepalive` (amend in place — this is cleanup for the draft PR)
**One concern:** Three code-quality issues flagged in the Claude Code review of PR #49.

---

## Context

PR #49 added a Gemini BLE keep-alive (write `FIRMWARE_REVISION` immediately after unlock
and every 15 s) to prevent the board's telemetry from going silent ~20 s after unlock.
The implementation is correct, but it introduced three problems that must be fixed before
the PR can merge:

1. **Triple duplication of format helpers.** `debugName()`, `toRawHexString()`, and
   `shortMessage()` are each defined in three places: `BleDebugFormat.kt` (correct,
   `internal`), `KableBleTransport.kt` (private copy, pre-existing), and
   `ConnectionManager.kt` (private copy, added by this PR). The canonical definitions
   must be consolidated in one place at the `ble` layer.

2. **60+ lines of keep-alive logic copy-pasted.** `startKeepAlive()`,
   `executeKeepAliveAction()`, and `recordKeepAlive()` are duplicated between
   `ConnectionManager` and `BleDebugViewModel`, differing only in one `appendLog()` call.
   Extract to a shared helper.

3. **Race condition: `startKeepAlive` is called before post-unlock reads.** In
   `ConnectionManager.connect()`, `startKeepAlive()` is called before
   `transport.read(HARDWARE_REVISION)` and `transport.read(FIRMWARE_REVISION)`. The
   keep-alive flow emits immediately, issuing a write that can interleave with those reads
   on the GATT channel. Move it to after the full connection setup completes.

---

## Allowed files (touch ONLY these)

```
app/src/main/kotlin/com/zwheel/app/ble/BleFormatExtensions.kt      ← NEW
app/src/main/kotlin/com/zwheel/app/ble/GeminiKeepAliveRunner.kt    ← NEW
app/src/main/kotlin/com/zwheel/app/ble/ConnectionManager.kt        ← update
app/src/main/kotlin/com/zwheel/app/ble/KableBleTransport.kt        ← update
app/src/main/kotlin/com/zwheel/app/ui/ble/BleDebugFormat.kt        ← update
app/src/main/kotlin/com/zwheel/app/ui/ble/BleDebugViewModel.kt     ← update
```

Do NOT touch `core/`, `GeminiUnlock.kt`, `GeminiStrategyTest.kt`, `AGENTS.md`,
`docs/adr/`, or any other file.

---

## Implementation spec

### 1. `BleFormatExtensions.kt` — NEW canonical home for shared BLE format helpers

```kotlin
package com.zwheel.app.ble

import com.zwheel.core.ports.ScanResult
import com.zwheel.core.protocol.GattCharacteristicId
import com.zwheel.core.protocol.OwUuids

internal fun ScanResult.deviceKey(): String = deviceId.lowercase()

internal fun GattCharacteristicId.debugName(): String =
    when (this) {
        OwUuids.BATTERY_PERCENT -> "battery_percent"
        OwUuids.RPM -> "rpm"
        OwUuids.PACK_VOLTAGE -> "pack_voltage"
        OwUuids.AMPS -> "amps"
        OwUuids.TEMPERATURE -> "temperature"
        OwUuids.RIDE_MODE -> "ride_mode"
        OwUuids.HARDWARE_REVISION -> "hardware_revision"
        OwUuids.FIRMWARE_REVISION -> "firmware_revision"
        OwUuids.UART_WRITE -> "uart_write"
        else -> uuid.toString().substring(startIndex = 4, endIndex = 8)
    }

internal fun ByteArray.toRawHexString(): String =
    joinToString(separator = "") { byte -> byte.toUByte().toString(radix = 16).padStart(2, '0') }

internal fun Throwable.shortMessage(): String =
    message ?: this::class.simpleName ?: "unknown"
```

Note: `toHexString()`, `toCompactDisplay()`, `displayValue()`, `shortName()`, and
`label()` are UI-only helpers that stay in `BleDebugFormat.kt`.

---

### 2. `GeminiKeepAliveRunner.kt` — NEW shared keep-alive launcher

```kotlin
package com.zwheel.app.ble

import com.zwheel.core.ports.GattIo
import com.zwheel.core.protocol.KeepAliveAction
import com.zwheel.core.protocol.debug.BleDebugRecorder
import com.zwheel.core.protocol.handshake.GeminiStrategy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal fun CoroutineScope.launchGeminiKeepAlive(
    strategy: GeminiStrategy,
    deviceId: String,
    transport: GattIo,
    recorder: BleDebugRecorder,
    onError: ((String) -> Unit)? = null,
): Job = launch {
    strategy.keepAlive().collect { action ->
        try {
            when (action) {
                is KeepAliveAction.Write -> {
                    recorder.recordKeepAlive(action, deviceId, "before")
                    transport.write(action.characteristicId, action.value)
                    recorder.recordKeepAlive(action, deviceId, "after")
                }
            }
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            recorder.recordKeepAlive(action, deviceId, "error:${error.shortMessage()}")
            onError?.invoke("Keep-alive failed: ${error.shortMessage()}")
            throw CancellationException("Gemini keep-alive failed", error)
        }
    }
}

private fun BleDebugRecorder.recordKeepAlive(
    action: KeepAliveAction.Write,
    deviceId: String,
    status: String,
) {
    record(
        type = "gemini_keep_alive_write",
        deviceId = deviceId,
        characteristicUuid = action.characteristicId.uuid.toString(),
        characteristicName = action.characteristicId.debugName(),
        rawValueHex = action.value.toRawHexString(),
        status = status,
    )
}
```

---

### 3. `ConnectionManager.kt` — use shared helpers, fix race

Three changes:

**a) Remove the three private duplicate extensions at the bottom of the class:**
Delete these functions entirely (they now live in `BleFormatExtensions.kt`):
```kotlin
private fun GattCharacteristicId.debugName(): String = ...
private fun ByteArray.toRawHexString(): String = ...
private fun Throwable.shortMessage(): String = ...
```

Also remove the private `deviceKey()` extension — it is already defined as `internal` in
`BleFormatExtensions.kt` in the same package, so no import is needed.

**b) Replace `startKeepAlive`, `executeKeepAliveAction`, `recordKeepAlive` with one line:**

Delete these three private methods entirely:
```kotlin
private fun startKeepAlive(...)
private suspend fun executeKeepAliveAction(...)
private fun recordKeepAlive(...)
```

Replace the body of each call site with `scope.launchGeminiKeepAlive(...)`:
```kotlin
private fun startKeepAlive(strategy: GeminiStrategy, deviceId: String) {
    keepAliveJob?.cancel()
    keepAliveJob = scope.launchGeminiKeepAlive(strategy, deviceId, transport, recorder)
}
```

**c) Move `startKeepAlive(...)` call in `connect()` to AFTER connection setup:**

Before (wrong — races with post-unlock reads):
```kotlin
val unlockResult = handshakeStrategy.unlock(transport)
check(unlockResult.unlocked) { "Board unlock failed: ${unlockResult.strategyName}" }
startKeepAlive(handshakeStrategy, deviceId)         // ← too early

val hwBytes = transport.read(OwUuids.HARDWARE_REVISION)
val fwBytes = transport.read(OwUuids.FIRMWARE_REVISION)
...
stateMirrorJob = scope.launch { service.state.collect { ... } }
```

After (correct — keep-alive starts once connection is fully established):
```kotlin
val unlockResult = handshakeStrategy.unlock(transport)
check(unlockResult.unlocked) { "Board unlock failed: ${unlockResult.strategyName}" }

val hwBytes = transport.read(OwUuids.HARDWARE_REVISION)
val fwBytes = transport.read(OwUuids.FIRMWARE_REVISION)
...
stateMirrorJob = scope.launch { service.state.collect { ... } }
startKeepAlive(handshakeStrategy, deviceId)         // ← after all GATT reads
```

The keep-alive flow emits immediately on collection; the 20 s board timeout is measured
from unlock, not from connection-setup completion. Connection setup takes ~2–4 s, leaving
~11–13 s of headroom before the 15 s keep-alive fires. This is safe.

Add the import: `import com.zwheel.app.ble.launchGeminiKeepAlive` is not needed since
`GeminiKeepAliveRunner.kt` is in the same package.

---

### 4. `KableBleTransport.kt` — remove private duplicates

Delete these two private methods from inside the class:
```kotlin
private fun GattCharacteristicId.debugName(): String = ...
private fun ByteArray.toRawHexString(): String = ...
```

`debugName()` and `toRawHexString()` are now `internal` top-level functions in the same
package (`com.zwheel.app.ble` via `BleFormatExtensions.kt`). No import is needed —
they're in the same package.

---

### 5. `BleDebugFormat.kt` — remove duplicated helpers, keep UI-only ones

Remove these four definitions (they now live in `BleFormatExtensions.kt`):
```kotlin
internal fun ScanResult.deviceKey(): String = ...
internal fun GattCharacteristicId.debugName(): String = ...
internal fun ByteArray.toRawHexString(): String = ...
internal fun Throwable.shortMessage(): String = ...
```

Keep everything else: `dumpCharacteristics`, `telemetryProbeCharacteristics`,
`ProbeCharacteristic`, `label()`, `shortName()`, `toHexString()`, `toCompactDisplay()`,
`displayValue()`.

Add import for the moved extensions if the compiler requires it — but since they are
`internal` in the same module and same package hierarchy, a star import is fine:
```kotlin
import com.zwheel.app.ble.debugName
import com.zwheel.app.ble.toRawHexString
import com.zwheel.app.ble.shortMessage
import com.zwheel.app.ble.deviceKey
```
Only add these imports if the file actually uses those symbols after removing local
definitions. Check the current file before importing.

---

### 6. `BleDebugViewModel.kt` — remove private methods, use shared helper

**a) Delete these three private methods entirely:**
```kotlin
private fun startKeepAlive(strategy: GeminiStrategy, deviceId: String) { ... }
private suspend fun executeKeepAliveAction(action: KeepAliveAction, deviceId: String) { ... }
private fun recordKeepAlive(action: KeepAliveAction, deviceId: String, status: String) { ... }
```

**b) Replace the `startKeepAlive(...)` call in `onConnectClicked` with:**
```kotlin
keepAliveJob = viewModelScope.launchGeminiKeepAlive(
    strategy,
    device.deviceId,
    transport,
    recorder,
    onError = { msg -> appendLog(msg) },
)
```

**c) Remove the `KeepAliveAction` import** — it is no longer referenced directly in
this file.

**d) Add the import for `launchGeminiKeepAlive`:**
```kotlin
import com.zwheel.app.ble.launchGeminiKeepAlive
```

---

## Constraints

- Do NOT change any logic — this is a pure refactor. Same runtime behavior, fewer lines.
- Do NOT touch `core/`. All new files are in `app/`.
- Rule 1: `core/` has zero Android imports and must stay that way.
- Rule 4: No new `object` or global `var`.

---

## Verification

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew --no-daemon :app:compileDebugKotlin :core:test
```

Must pass with zero errors. Fix any compilation errors before reporting done.
Confirm: no remaining private copies of `debugName`, `toRawHexString`, or `shortMessage`
in `ConnectionManager.kt` or `KableBleTransport.kt`.
