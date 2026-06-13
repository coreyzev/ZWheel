# Gate: Phase 2 BLE Service — Tests

## Context

`BoardStateServiceImpl` has been written at
`core/src/main/kotlin/com/zwheel/core/service/BoardStateServiceImpl.kt`.

The class subscribes to BLE characteristic notifications and updates a
`StateFlow<BoardState>`. Dependencies (`Turbine`, `kotlinx-coroutines-test`) are
already wired in `gradle/libs.versions.toml` and `core/build.gradle.kts`.

`Parsers.unsignedInt16` has been added to `core/src/main/kotlin/com/zwheel/core/protocol/Parsers.kt`.

## Your task

Create **one test file**:
`core/src/test/kotlin/com/zwheel/core/service/BoardStateServiceImplTest.kt`

### Test helpers (put them at the top of the test file)

```kotlin
import app.cash.turbine.test
import com.zwheel.core.model.BoardState
import com.zwheel.core.ports.BleTransport
import com.zwheel.core.ports.Clock
import com.zwheel.core.ports.ScanResult
import com.zwheel.core.protocol.GattCharacteristicId
import com.zwheel.core.service.BoardStateServiceImpl
import com.zwheel.core.protocol.OwUuids
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

private class FakeBleTransport : BleTransport {
    private val flows = mutableMapOf<GattCharacteristicId, MutableSharedFlow<ByteArray>>()
    fun flowFor(id: GattCharacteristicId) =
        flows.getOrPut(id) { MutableSharedFlow(extraBufferCapacity = 64) }
    suspend fun emit(id: GattCharacteristicId, bytes: ByteArray) = flowFor(id).emit(bytes)
    override fun notifications(characteristicId: GattCharacteristicId): Flow<ByteArray> =
        flowFor(characteristicId)
    override suspend fun scan(): Flow<ScanResult> = emptyFlow()
    override suspend fun connect(deviceId: String) {}
    override suspend fun disconnect() {}
    override suspend fun read(characteristicId: GattCharacteristicId): ByteArray = ByteArray(0)
    override suspend fun write(characteristicId: GattCharacteristicId, value: ByteArray) {}
}

private class FakeClock(var currentMillis: Long = 0L) : Clock {
    override fun nowEpochMillis() = currentMillis
}
```

### Tests to write

**Test 1 — selects RpmBased when RPM arrives within 3 s**
- Use `runTest` with `UnconfinedTestDispatcher`
- Create `FakeBleTransport`, `FakeClock`
- Instantiate `BoardStateServiceImpl(transport, clock, diameterInches = 11.5, stockDiameterInches = 11.5)`
- `launch { service.start(this) }`
- Emit RPM bytes `byteArrayOf(0x00, 0x1D)` (= 29 RPM) on `OwUuids.RPM`
- Collect `service.state` with Turbine, skip initial null-speed states, assert that a
  state with non-null `speedMetersPerSecondCorrected` arrives and its value is within
  0.01 of 0.444

**Test 2 — batteryPercent updates on notification**
- Emit `byteArrayOf(0x00, 0x60)` (= 96) on `OwUuids.BATTERY_PERCENT`
- Assert `state.batteryPercent == 96`

**Test 3 — malformed payload is skipped, no crash**
- Emit a 1-byte payload `byteArrayOf(0x42)` on `OwUuids.BATTERY_PERCENT`
- Assert `state.batteryPercent` is still `null` (no exception propagated)

**Test 4 — odometer delta produces non-null raw speed**
- Emit odometer bytes `byteArrayOf(0x00, 0x00)` at `clock.currentMillis = 0`
- Set `clock.currentMillis = 1000`, emit `byteArrayOf(0x00, 0x01)` (1 tick in 1 s)
- Assert `state.speedMetersPerSecondRaw != null`

## Constraints

- Zero `android.*` or `androidx.*` imports
- All tests live in one file, one conventional commit:
  `test(ble): add BoardStateServiceImpl tests`
- Commit message must NOT include any `Co-Authored-By` lines
- Do not modify any existing file except to add the test file
- After committing, print: `DONE`
