# Gate: fullmap-overlays

Add two missing overlay elements to the full-screen route map screen:
1. A ride-time chip in the top bar (beside the close button)
2. Key stats (distance + duration + top speed) in the bottom summary overlay

Touch only: `app/src/main/kotlin/com/zwheel/app/ui/history/MapFullScreenScreen.kt`

Read the file first. Then read `app/src/main/kotlin/com/zwheel/app/ui/history/RideDetailViewModel.kt` to understand what data is available in the `UiState` passed to this screen (or whatever state object the screen receives).

---

## Fix 1 — Ride time chip in top bar

The top bar currently has only a close button (`IconButton` with `Icons.Filled.Close`) inside a `Box` aligned to `TopStart` or similar.

Add a ride-time chip to the RIGHT side of the top bar. The chip should show the ride start time (e.g. "8:14 AM" or "Today · 8:14 AM" — use whatever time string is already available in the screen's state). Style:
- `Surface` with `color = c.legendCard.copy(alpha = 0.7f)`, `shape = RoundedCornerShape(999.dp)`
- Text: JetBrains Mono, 10sp, `textMuted` color
- Padding: `horizontal = 10.dp, vertical = 5.dp`
- Align to `TopEnd` of the same overlay box as the close button, with `padding(10.dp)`

If no time string is available in the state, use the ride date/duration formatted as a short string.

---

## Fix 2 — Key stats in bottom overlay

The bottom overlay currently shows the speed legend chip + start/end marker legends.

After the existing legend content, add a `Spacer(Modifier.height(8.dp))` then a Row of 3 stat chips. Each chip shows one stat:
- **Distance** — value from state (e.g. "3.2 mi"), label "DIST"
- **Duration** — value from state (e.g. "24 min"), label "TIME"
- **Top Speed** — value from state in amber color (e.g. "↑ 19.6 mph"), label "TOP"

Each stat chip:
```
Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(value, ...)  // Saira ExtraBold 16sp, textPrimary (amber for top speed)
    Text(label, ...)  // JetBrains Mono 9sp textLabel letterSpacing 1.5sp
}
```

Arrange them in a `Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth())`.

Use whatever fields are available in the map screen's state/argument for the values. Look at how the screen receives its data (it may be a `RideDetailUiState` or separate params passed via navigation).

---

## Compile

```bash
./gradlew :app:compileDebugKotlin
```

Fix all errors. Report what you added and what state fields you used.
