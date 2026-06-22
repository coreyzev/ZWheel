# Gate: Slice 2b — Fix ConnectScreen dark background

`ConnectScreen` renders on a white background because its root `LazyColumn` lacks a dark background,
unlike `PermissionsScreen` which correctly sets `.fillMaxSize().background(c.screenBg)`. This makes
the "Connect your board" title (textPrimary, light) nearly invisible. Read ONLY this gate.

App module only. `core/` untouched. No new BLE UUID, no network.

## Fix
In `app/src/main/kotlin/com/zwheel/app/ui/connect/ConnectScreen.kt`, give the screen's root composable
a full-size dark background: add `Modifier.fillMaxSize().background(LocalZWheelColors.current.screenBg)`
to the root `LazyColumn` (merge with any existing modifier; add the `fillMaxSize` and
`androidx.compose.foundation.background` imports). Match the pattern in
`app/src/main/kotlin/com/zwheel/app/ui/permissions/PermissionsScreen.kt` (its root uses
`.fillMaxSize().background(c.screenBg)`). Change nothing else.

## Build & commit
1. `GRADLE_OPTS="-Xmx4g" ./gradlew :app:compileDebugKotlin` then `:app:recordRoborazziDebug`; confirm
   `app/build/outputs/roborazzi/connect.png` now has a dark background.
2. One commit: `fix(ui): dark background on ConnectScreen`. No Co-Authored-By line.
