# Gate: system-insets

Ensure the ZWheel app correctly handles Android system UI insets on all devices — visible status bar, visible navigation bar (gesture OR button-based). Do NOT hide system bars. Do NOT create a custom status bar. The system bars should remain fully visible; the app just needs to lay out its content so nothing is hidden behind them.

Read ONLY the files you need. Touch only what is necessary.

---

## Step 1 — Audit current state

Read these files:
- `app/src/main/kotlin/com/zwheel/app/MainActivity.kt`
- `app/src/main/kotlin/com/zwheel/app/ui/ZWheelAppScreen.kt`
- Run: `grep -rn "systemBarsPadding\|WindowCompat\|setDecorFitsSystemWindows\|WindowInsets\|imePadding\|navigationBarsPadding\|statusBarsPadding" app/src/main/kotlin/ --include="*.kt"`

Determine:
1. Is edge-to-edge enabled in the Activity (`WindowCompat.setDecorFitsSystemWindows(window, false)`)?
2. Does the root `Scaffold` in `ZWheelAppScreen.kt` handle insets (M3 Scaffold handles bottom insets by default)?
3. Which screens call `systemBarsPadding()` themselves?

---

## Step 2 — Apply the correct fix

**The correct pattern for this project:**

Edge-to-edge should be enabled in `MainActivity.kt` with:
```kotlin
WindowCompat.setDecorFitsSystemWindows(window, false)
```
(import `androidx.core.view.WindowCompat`)

If it is already enabled, leave it. If it is NOT enabled, add it **before** `super.onCreate(savedInstanceState)` or right after — inside `onCreate`, after `super.onCreate`.

Once edge-to-edge is confirmed enabled, the root `Scaffold` in `ZWheelAppScreen.kt` handles bottom navigation insets automatically (M3 Scaffold uses `WindowInsets.systemBars` by default). Do not override this.

Individual screens that have `systemBarsPadding()` applied at their root: keep `systemBarsPadding()` ONLY if that screen is shown WITHOUT the Scaffold's `innerPadding`. If a screen is wrapped inside `NavHost` with `Modifier.padding(innerPadding)`, it should NOT also call `systemBarsPadding()` — that would double-pad.

Specifically:
- `DashboardScreen.kt`: it is called inside the NavHost with `innerPadding`. Remove `.systemBarsPadding()` from its root `Box`. The top padding (status bar) is handled by the Scaffold; the bottom nav bar inset is in `innerPadding`.
- Check all other screens in `app/src/main/kotlin/com/zwheel/app/ui/` for the same double-padding pattern and remove duplicate `systemBarsPadding()` if it is inside the NavHost.
- Screens rendered OUTSIDE the NavHost (e.g., full-screen overlays, onboarding screens shown before the Scaffold) SHOULD keep `systemBarsPadding()`.

---

## Step 3 — Compile

```bash
./gradlew :app:compileDebugKotlin
```

Fix all errors. Report what you changed.
