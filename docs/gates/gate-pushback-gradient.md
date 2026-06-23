# Gate: pushback-gradient

Replace the solid-color pushback bar fill with a gradient that reflects speed zones — lime (nominal) → amber (approaching pushback) → red (danger) — with small smooth transitions between zones. The track background stays as-is. No zone segments; just a single gradient fill.

Touch only: `app/src/main/kotlin/com/zwheel/app/ui/dashboard/PushbackBar.kt`

---

## The change

The existing Canvas draws a `barColor` (single snap color) fill. Replace it with a Brush gradient fill.

The gradient should span the full track width with these stops, using the existing threshold constants:
- `0.0f` → `c.rampGood` (lime)
- `CAUTION_FRACTION - 0.05f` → `c.rampGood` (hold lime until just before threshold)
- `CAUTION_FRACTION + 0.03f` → `c.rampCaution` (amber; small transition zone)
- `DANGER_FRACTION - 0.03f` → `c.rampCaution` (hold amber until just before threshold)
- `DANGER_FRACTION + 0.02f` → `c.rampDanger` (red; small transition)
- `1.0f` → `c.rampDanger`

The gradient `startX = 0f`, `endX = size.width` so thresholds align with actual track positions.

The fill is drawn only up to `fraction * size.width` (same as current). Only the visible fill rectangle uses the gradient; the track background uses solid `c.border` (unchanged).

Remove the `barColor` variable and the single-color `drawRoundRect` for the fill. The right-side label text color should stay ramp-colored based on zone — keep the existing `barColor` logic for the label only (or rename it `labelColor`).

Replace:
```kotlin
Canvas(...) {
    val radius = CornerRadius(size.height / 2)
    drawRoundRect(c.border, topLeft = Offset.Zero, size = size, cornerRadius = radius)
    drawRoundRect(
        barColor,
        topLeft = Offset.Zero,
        size = Size((fraction * size.width).coerceIn(0f, size.width), size.height),
        cornerRadius = radius,
    )
}
```

With:
```kotlin
Canvas(...) {
    val radius = CornerRadius(size.height / 2)
    drawRoundRect(c.border, topLeft = Offset.Zero, size = size, cornerRadius = radius)
    val fillWidth = (fraction * size.width).coerceIn(0f, size.width)
    if (fillWidth > 0f) {
        val fillBrush = Brush.horizontalGradient(
            PushbackThresholds.CAUTION_FRACTION - 0.05f to c.rampGood,
            PushbackThresholds.CAUTION_FRACTION + 0.03f to c.rampCaution,
            PushbackThresholds.DANGER_FRACTION - 0.03f to c.rampCaution,
            PushbackThresholds.DANGER_FRACTION + 0.02f to c.rampDanger,
            startX = 0f,
            endX = size.width,
        )
        drawRoundRect(
            brush = fillBrush,
            topLeft = Offset.Zero,
            size = Size(fillWidth, size.height),
            cornerRadius = radius,
        )
    }
}
```

Note: `Brush.horizontalGradient` with ordered `(Float, Color)` pairs is the correct API — no explicit 0f/1f stops needed at the ends as long as pairs are in ascending order.

Add `import androidx.compose.ui.graphics.Brush` if not present. `CornerRadius`, `Offset`, `Size` are already imported.

---

## Compile

```bash
./gradlew :app:compileDebugKotlin
```

Fix any errors. Report what changed.
