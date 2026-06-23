# Gate: spec-surgical-fixes

Fix the following spec deviations. Touch only the files listed. Run `:app:compileDebugKotlin` at the end. Do NOT run tests. Do NOT modify any other files.

---

## Fix 1 — Type.kt speed hero letterSpacing
File: `app/src/main/kotlin/com/zwheel/app/ui/Type.kt`

Find the `displayLarge` (or speed hero) text style entry. Change `letterSpacing = (-3).sp` to `letterSpacing = (-6).sp`.

---

## Fix 2 — RideRow radius: 12dp → 14dp
File: `app/src/main/kotlin/com/zwheel/app/ui/history/RideRow.kt`

Find `RoundedCornerShape(12.dp)` and change to `RoundedCornerShape(14.dp)`.

---

## Fix 3 — Dev section text field border: 8dp → 10dp
File: `app/src/main/kotlin/com/zwheel/app/ui/settings/SettingsSections.kt`

Find `RoundedCornerShape(8.dp)` in the developer section's BasicTextField border and change to `RoundedCornerShape(10.dp)`.

---

## Fix 4 — RSSI format: remove space before dBm
File: `app/src/main/kotlin/com/zwheel/app/ui/dashboard/BoardHeader.kt`

Find:
```kotlin
Text(state.rssi?.let { "$it dBm" } ?: "--", style = mono11, color = c.textMuted)
```
Change to:
```kotlin
Text(state.rssi?.let { "${it}dBm" } ?: "--", style = mono11, color = c.textMuted)
```

---

## Fix 5 — Add "Connecting" BLE chip state
File: `app/src/main/kotlin/com/zwheel/app/ui/connect/ConnectScreen.kt`

The spec requires 5 BLE state chips: idle / scanning / connecting / connected / disconnected.
Find where the chip list is built (around line 171–175) and add `ConnectionState.Connecting` to the list with label "connecting".

---

## Fix 6 — Permissions: fix location permanently-denied callback
File: `app/src/main/kotlin/com/zwheel/app/ui/connect/PermissionsScreen.kt` (or wherever permissions live)

Find the location `PermissionCard` call that passes `onOpenSettings = onRequestLocation`. This is a bug — tapping "Open settings" re-requests the runtime permission instead of opening system settings. There must be a separate `onOpenLocationSettings: () -> Unit` parameter on `PermissionsScreen`. If it doesn't exist, add it and wire `onOpenSettings` to it. Then find where `PermissionsScreen` is called in the nav host and pass the correct lambda (use `LocalContext.current` + `Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)` to build an intent that opens app settings).

---

## Fix 7 — Permissions button copy
File: Same permissions file as Fix 6

Find `"Open in settings"` and change to `"Open settings"`.

---

## Fix 8 — Developer section label
File: `app/src/main/kotlin/com/zwheel/app/ui/settings/SettingsSections.kt`

Find the developer section toggle label `"BLE debug logging"` and change to `"BLE debug view"`.

---

## Compile check

```bash
./gradlew :app:compileDebugKotlin
```

Fix any errors before finishing.
