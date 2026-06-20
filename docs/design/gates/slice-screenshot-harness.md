# Gate: Screenshot Harness — Headless Compose PNG Rendering

You are adding a **Roborazzi** screenshot-testing harness to the ZWheel `app` module so that Compose
screens can be rendered to PNG files on the JVM with no Android emulator and no KVM. This is a
CI/dev-box capability: the images are inspection and diff artifacts only — they are never shipped.

Read ONLY this file. Do not read any other gate or docs file.

This slice is **test-only**. Do NOT touch anything under `core/`. Do NOT modify any file under
`app/src/main/` (no changes to production sources). Do NOT add any runtime network dependency.

---

## Scope (do all of these, in order)

### 1. `gradle/libs.versions.toml` — add Roborazzi entries

Add the following lines in the appropriate sections. Insert the version under `[versions]`, the
library aliases under `[libraries]`, and the plugin alias under `[plugins]`.

**Version** (use exactly this; see compatibility rationale at the bottom of this gate):

```toml
[versions]
roborazzi = "1.64.0"
```

**Libraries** (add after the existing `robolectric` line):

```toml
[libraries]
roborazzi-core          = { module = "io.github.takahirom.roborazzi:roborazzi",             version.ref = "roborazzi" }
roborazzi-compose       = { module = "io.github.takahirom.roborazzi:roborazzi-compose",     version.ref = "roborazzi" }
roborazzi-junit-rule    = { module = "io.github.takahirom.roborazzi:roborazzi-junit-rule",  version.ref = "roborazzi" }
```

**Plugin** (add after the `ksp` plugin line):

```toml
[plugins]
roborazzi = { id = "io.github.takahirom.roborazzi", version.ref = "roborazzi" }
```

### 2. `app/build.gradle.kts` — wire Roborazzi

**a. Apply the plugin** — add one line to the existing `plugins {}` block:

```kotlin
alias(libs.plugins.roborazzi)
```

**b. Enable Android resources in unit tests and NATIVE graphics mode** — add inside the `android {}`
block, after the existing `buildFeatures {}` block. If a `testOptions {}` block already exists,
merge into it; otherwise add it new:

```kotlin
testOptions {
    unitTests {
        isIncludeAndroidResources = true
    }
}
```

`isIncludeAndroidResources = true` is required by Robolectric to resolve theme/drawable resources
at test time. Roborazzi's NATIVE graphics mode (`@GraphicsMode(GraphicsMode.Mode.NATIVE)`) is set
per-test via annotation (see step 3); no extra Gradle property is needed for record mode because the
Roborazzi plugin registers the `recordRoborazziDebug` task automatically.

**c. Add test dependencies** — add these three lines inside the existing `dependencies {}` block,
grouped with the other `testImplementation` lines:

```kotlin
testImplementation(libs.roborazzi.core)
testImplementation(libs.roborazzi.compose)
testImplementation(libs.roborazzi.junit.rule)
```

Note: `libs.roborazzi.junit.rule` resolves from the `roborazzi-junit-rule` alias (Gradle converts
`-` to `.` in alias accessors).

Do NOT add `testImplementation(libs.androidx.compose.ui.test.junit4)` — the project does not
currently declare a separate `ui-test-junit4` entry; `createAndroidComposeRule` is pulled in
transitively by `roborazzi-compose`. If the compile step fails because
`androidx.compose.ui.test.junit4` is missing, add:

```toml
# libs.versions.toml [libraries]
compose-ui-test-junit4 = { module = "androidx.compose.ui:ui-test-junit4" }
```

and (no version needed — it is managed by the existing `androidx-compose-bom` platform):

```kotlin
// app/build.gradle.kts
testImplementation(platform(libs.androidx.compose.bom))
testImplementation(libs.compose.ui.test.junit4)
```

### 3. New test file

Create exactly this file:

**Path:** `app/src/test/kotlin/com/zwheel/app/ui/screenshots/DashboardScreenshotTest.kt`

```kotlin
package com.zwheel.app.ui.screenshots

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.RoborazziRule
import com.github.takahirom.roborazzi.captureRoboImage
import com.zwheel.app.ui.DashboardScreen
import com.zwheel.app.ui.ZWheelTheme
import com.zwheel.app.ui.mockDashboardState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [34],
    qualifiers = RobolectricDeviceQualifiers.Pixel5,
)
class DashboardScreenshotTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun dashboard_record() {
        composeTestRule.setContent {
            ZWheelTheme {
                DashboardScreen(
                    state = mockDashboardState(),
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage(
            "build/outputs/roborazzi/dashboard.png",
        )
    }
}
```

**Import notes:**
- `DashboardScreen` lives in `com.zwheel.app.ui.dashboard.DashboardScreen` if the file is under
  `ui/dashboard/`; adjust the import package to match the actual package declaration in
  `DashboardScreen.kt`. The function signature expected is:
  `@Composable fun DashboardScreen(state: DashboardUiState, modifier: Modifier = Modifier, onRequestLocation: () -> Unit = {}, locationGranted: Boolean = true, locationPermanentlyDenied: Boolean = false)`
- `mockDashboardState()` is in `com.zwheel.app.ui.DashboardState.kt` (package `com.zwheel.app.ui`).
- `ZWheelTheme` is in `com.zwheel.app.ui.ZWheelTheme.kt` (package `com.zwheel.app.ui`).
- `RobolectricDeviceQualifiers` is provided by `roborazzi-compose`; `Pixel5` gives a 393×851 dp
  phone viewport at `xxhdpi` density (~1080×2340 px physical), which is a good baseline for a phone
  dashboard layout.
- Do NOT use `@RunWith(AndroidJUnit4::class)` — the project uses `RobolectricTestRunner` for all
  Robolectric tests (see `RideDaoTest.kt`) and the `junit-vintage-engine` to run JUnit4 tests on the
  JUnit5 platform (`useJUnitPlatform()` is set globally in `tasks.withType<Test>()`).
- Do NOT add a `@RoborazziRule` property unless you want automatic capture-on-every-test behavior.
  The explicit `captureRoboImage(path)` call above is simpler and sufficient.

### 4. `.gitignore` — exclude generated PNGs

The screenshots are build artifacts, not source. Append to the root `.gitignore` (or
`app/.gitignore` if one exists; prefer the root):

```gitignore
# Roborazzi headless screenshot artifacts (inspection only, not source)
build/outputs/roborazzi/
**/roborazzi/*.png
```

Do not commit any `.png` file.

---

## Build & commit

### Step 1 — compile check

```bash
GRADLE_OPTS="-Xmx4g" ./gradlew :app:compileDebugUnitTestKotlin
```

Fix all errors before proceeding. Common issues and fixes:
- **`Unresolved reference: DashboardScreen`** — the parallel slice that creates `DashboardScreen.kt`
  may not be merged yet. If so, create a minimal stub:
  ```kotlin
  // app/src/main/kotlin/com/zwheel/app/ui/dashboard/DashboardScreen.kt
  package com.zwheel.app.ui.dashboard
  import androidx.compose.runtime.Composable
  import androidx.compose.ui.Modifier
  import com.zwheel.app.ui.DashboardUiState
  @Composable
  fun DashboardScreen(
      state: DashboardUiState,
      modifier: Modifier = Modifier,
      onRequestLocation: () -> Unit = {},
      locationGranted: Boolean = true,
      locationPermanentlyDenied: Boolean = false,
  ) { /* stub for screenshot harness */ }
  ```
  Adjust the import in `DashboardScreenshotTest.kt` to
  `com.zwheel.app.ui.dashboard.DashboardScreen` if you use this path.
- **`Unresolved reference: captureRoboImage`** — check that `roborazzi-compose` is on the
  `testImplementation` classpath and that `libs.roborazzi.compose` resolved correctly.
- **`createAndroidComposeRule` not found** — add the `compose-ui-test-junit4` dependency as
  described in step 2c above.

### Step 2 — existing tests must still pass

```bash
GRADLE_OPTS="-Xmx4g" ./gradlew :app:testDebugUnitTest
```

All pre-existing tests (including `RideDaoTest`) must remain green. If any break, fix before
proceeding.

### Step 3 — record screenshots

```bash
GRADLE_OPTS="-Xmx4g" ./gradlew :app:recordRoborazziDebug
```

After this completes, confirm a non-empty PNG was written:

```bash
ls -lh app/build/outputs/roborazzi/dashboard.png
file app/build/outputs/roborazzi/dashboard.png
```

The `file` command must report `PNG image data` and the size must be non-trivially large (> 10 KB).
If the file is 0 bytes or absent, the NATIVE graphics mode is not functioning — see the fallback
note at the bottom of this gate. Do NOT commit until a valid PNG is confirmed.

### Step 4 — commit

One commit, exactly:

```
test(ui): add Roborazzi headless screenshot harness for dashboard
```

Do not add a Co-Authored-By line. Stage only:
- `gradle/libs.versions.toml`
- `app/build.gradle.kts`
- `app/src/test/kotlin/com/zwheel/app/ui/screenshots/DashboardScreenshotTest.kt`
- `.gitignore` (root) or `app/.gitignore`
- `app/src/main/kotlin/com/zwheel/app/ui/dashboard/DashboardScreen.kt` **only if** you had to
  create a stub (omit if the real file was already present from the parallel slice)

Do NOT stage any `.png` file.

---

## Constraints

- `core/` untouched. No Android imports added to `core/`.
- No runtime network. No URLs hardcoded in `app/src/main/`. ADR-010 scan excludes `**/test/**`
  sources, so test files are exempt, but do not add network calls there either.
- Soft 300-line limit per file; this test file is well under it.
- The Roborazzi plugin and `recordRoborazziDebug` task must NOT be wired into the `check` lifecycle
  — screenshot recording is an on-demand developer/CI action, not a gate on every build.

---

## Compatibility rationale (for reference — do not act on this section)

**Roborazzi 1.64.0** is selected based on the following version matrix read from the project and
Roborazzi's repository at gate-write time (2026-06-20):

| Axis | ZWheel project | Roborazzi 1.64.0 requirement | Status |
|---|---|---|---|
| Robolectric | 4.13 | ≥ 4.10 alpha 1 (RNG introduced there) | Compatible |
| AGP | 8.7.3 | Supports up to AGP 9.0 (migrated deprecated APIs in recent releases) | Compatible |
| Kotlin | 2.0.21 | Tested with Kotlin 2.x series | Compatible |
| Compose BOM | 2024.11.00 | No minimum BOM pinned; tracks AndroidX Compose | Compatible |
| JUnit platform | JUnit5 + vintage-engine for JUnit4 | Uses `RobolectricTestRunner` (JUnit4 runner) via vintage-engine | Compatible |

`@GraphicsMode(GraphicsMode.Mode.NATIVE)` (Robolectric Native Graphics, RNG) requires the JVM to
have a software pixel-pipeline — Robolectric 4.10+ ships this on the JVM without KVM or a GPU,
which is exactly the no-emulator constraint of this environment.

**Fallback — if NATIVE mode produces a blank/zero-byte PNG:** Robolectric's RNG sometimes fails on
headless Linux without `libGL` / Mesa software rendering in the PATH. In that case, switch to
**Paparazzi** (Cash App) as an alternative:
- Plugin: `app.cash.paparazzi` (version ≥ 1.3.5 for Compose BOM 2024.x / AGP 8.x)
- Paparazzi renders entirely on the JVM via its own LayoutLib fork — no Robolectric, no RNG needed.
- Dependency: `testImplementation("app.cash.paparazzi:paparazzi:[version]")`
- Task: `./gradlew :app:recordPaparazzi`
- Trade-off: Paparazzi brings its own LayoutLib (large download, ~100 MB) and does not share the
  Robolectric runner already used by `RideDaoTest`. Only fall back if Roborazzi RNG cannot produce a
  valid PNG on this host.
