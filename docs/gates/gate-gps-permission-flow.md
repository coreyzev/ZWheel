# Gate: GPS Runtime Permission Request

**Branch:** `codex/gps-permission-flow`
**Base:** `main`

---

## Context

`ACCESS_FINE_LOCATION` is declared in the manifest and used by `RideForegroundService` for GPS
capture, but the app never asks the user for it at runtime. On Android 12+ (API 31+),
`bleScanPermissions()` returns only `[BLUETOOTH_SCAN, BLUETOOTH_CONNECT]` — location is
missing from the request list. The service silently swallows `SecurityException`, so GPS is
never captured without the user manually granting location in Settings.

Fix: add `ACCESS_FINE_LOCATION` to the permissions requested in the existing BLE permission flow.
No new screens needed — the existing `rememberLauncherForActivityResult` in `ZWheelAppScreen.kt`
already handles multi-permission requests.

---

## Allowed files (touch ONLY these)

```
app/src/main/kotlin/com/zwheel/app/ui/ble/BlePermissionUtils.kt
```

Do NOT touch any other file.

---

## Implementation spec

### `BlePermissionUtils.kt` — add location to the permissions list

Currently on API >= 31 the function returns only BLE permissions. Location must be added:

```kotlin
internal fun bleScanPermissions(): List<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )
    } else {
        listOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
```

Add `import android.Manifest` if not already present (it already is, verify first).

No other changes — `ZWheelAppScreen.kt` already calls `bleScanPermissions()` and feeds the result
into the multi-permission launcher and all the "has permission" / "permanently denied" checks.
The existing flow handles the new permission automatically.

---

## Verification

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew --no-daemon :app:compileDebugKotlin
```

Must pass cleanly. The change is one line; verify the full function looks exactly as specified.

Commit message: `fix(gps): include ACCESS_FINE_LOCATION in runtime permission request`
