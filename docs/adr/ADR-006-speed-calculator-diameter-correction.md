# ADR-006 — Speed Calculator & Diameter Correction Path

**Status:** Accepted  
**Date:** 2026-06-13  
**Author:** Claude Code (ZWheel reviewing agent)

---

## Context

`01_PROJECT_BRIEF §4.1` specifies two possible implementations of `SpeedCalculator`,
selected per board at connect time:

- **`RpmBased`** — derive speed from the RPM characteristic (e659f30b) and the
  user-configured tire diameter, computed from first principles.
- **`ScaledFirmware`** — scale firmware-derived distance increments (ODOMETER,
  e659f30a) by `userDiameter / stockDiameter`.

The ADR must pick the default for Corey's board (XR HW 4209, FW Gemini 4134) and
define the selection rule for other boards.

There is no explicit "speed" characteristic in `OwUuids`. Speed must be derived;
the question is from which source.

---

## Decision

**Use `RpmBased` as the primary implementation for all boards where e659f30b is
readable and returns a non-null value. Fall back to `ScaledFirmware` only when RPM
is absent.**

---

## Evidence

### RPM characteristic confirmed on XR

M1 capture (`xr4209-success-handshake-telemetry.jsonl`) shows e659f30b notifying
continuously at idle — raw value `0x001d` = 29 RPM. The characteristic is subscribed
without error and produces values throughout the session. `Parsers.rpm()` is already
implemented and tested against this fixture (PR #18).

No speed characteristic exists in the community-documented GATT map; every reference
implementation (OWCE `OWBoard.cs`, pOnewheel, UWP-Onewheel) derives speed from RPM on
XR-era boards. `ScaledFirmware` via ODOMETER is a valid fallback but introduces a
differentiation step (Δodometer / Δtime) that adds noise at low speeds.

### Formula verification

```
speed_m/s = rpm × π × (diameterInches × 0.0254) / 60
```

At M1 idle sample (29 RPM, 11.5" stock XR tire):

```
speed = 29 × π × (11.5 × 0.0254) / 60
      = 29 × π × 0.2921 / 60
      = 29 × 0.9176 / 60
      ≈ 0.44 m/s  (≈ 0.99 mph)
```

Consistent with a stationary board with slight motor engagement during handshake.
Formula passes dimensional analysis and matches OWCE's RPM path in `OWBoard.cs`.

---

## Implementation spec (for Codex)

### `RpmBased` (primary)

```kotlin
class RpmBased : SpeedCalculator {
    override fun correctedMetersPerSecond(
        rpm: Double?,
        firmwareSpeedMetersPerSecond: Double?,
        diameterInches: Double,
    ): Double? {
        rpm ?: return null
        val diameterMeters = diameterInches * 0.0254
        return rpm * Math.PI * diameterMeters / 60.0
    }
}
```

### `ScaledFirmware` (fallback)

```kotlin
class ScaledFirmware(private val stockDiameterInches: Double) : SpeedCalculator {
    override fun correctedMetersPerSecond(
        rpm: Double?,
        firmwareSpeedMetersPerSecond: Double?,
        diameterInches: Double,
    ): Double? {
        firmwareSpeedMetersPerSecond ?: return null
        return firmwareSpeedMetersPerSecond * (diameterInches / stockDiameterInches)
    }
}
```

`firmwareSpeedMetersPerSecond` is populated by the caller (BoardStateService) from
ODOMETER deltas or a future firmware speed characteristic. `stockDiameterInches` is
`boardType.stockTireDiameterInches`.

### Selection rule (in `BoardStateService` at connect time)

```
if (rpmCharacteristic successfully subscribed AND first notification arrives within 3s)
    calculator = RpmBased()
else
    calculator = ScaledFirmware(boardType.stockTireDiameterInches)
```

Practical note: on all confirmed boards (XR, Plus, Pint-era) the RPM characteristic
exists. `ScaledFirmware` exists as a safety net for V1 / unknown hardware revisions.

### `firmwareSpeedMetersPerSecond` population (for the ScaledFirmware path)

ODOMETER (e659f30a) reports cumulative distance; the unit is documented by OWCE as
miles × 42 (i.e., one tick = 1/42 mile). `BoardStateService` should track the previous
ODOMETER value and compute:

```
firmwareSpeed_m/s = (Δodometer_ticks / 42.0) × 1609.344 / Δtime_seconds
```

This is only wired when `ScaledFirmware` is active. The tick-to-miles constant must be
confirmed against Corey's board at M2 (compare odometer delta over a known distance).

---

## Consequences

- `RpmBased` and `ScaledFirmware` are both concrete classes in `core/calc/`. Both
  implement `SpeedCalculator`. No abstract base class needed.
- `BoardStateService` holds a `SpeedCalculator` reference injected at connect time;
  it never switches mid-ride.
- The `diameterInches` argument comes from the per-board DataStore preference
  (default: `boardType.stockTireDiameterInches`). The service reads it at connect
  and uses the same value for the entire session. A settings change takes effect on
  the next connect.
- `BoardState.speedMetersPerSecondRaw` is populated from ODOMETER deltas regardless
  of which impl is active (stored in ride records for debugging, never displayed).
- **Safety rule (from AGENTS.md §3):** if `diameterInches` is 0, null, or outside
  `[8.0, 16.0]`, `RpmBased` must return null and log a warning rather than dividing
  by zero or producing an implausible speed. The UI shows an "uncorrected" badge in
  this case.

---

## Tests required (same commit as impl, per Rule 9)

- `RpmBasedTest`: M1 idle fixture (29 RPM, 11.5") → 0.44 m/s ±0.01; custom diameter
  (10.5") → proportionally lower; rpm=null → null; diameter=0 → null.
- `ScaledFirmwareTest`: scale-up and scale-down cases; firmwareSpeed=null → null.
- Both tests live in `core/src/test/kotlin/com/zwheel/core/calc/`.
