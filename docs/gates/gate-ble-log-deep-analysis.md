# Gate: BLE Log Deep Analysis

**Task:** Write and run Python analysis scripts against the ZWheel BLE debug log captured during a 34.8-minute XR ride. Commit the findings as markdown in `docs/ble-analysis/`. Do NOT touch any app source files.

**Log file (absolute path, read-only):**
```
/root/.claude/uploads/ab57cbc9-4009-4604-860e-878489835e1e/0e5a798a-zwheelble60b56f4d99cdc5a2.jsonl
```

The file is ~20MB JSONL. Each line is a JSON object. Key fields:
- `type`: `"notification"`, `"probe_read"`, `"metadata_read"`, `"gemini_result"`, etc.
- `characteristicUuid`: full UUID string, e.g. `"e659f317-ea98-11e3-ac10-0800200c9a66"`
- `characteristicName`: short name, e.g. `"safety_headroom"`
- `value`: hex string of raw bytes, e.g. `"005e"`
- `timestamp`: milliseconds since session start (integer)
- Other parsed fields vary by characteristic

**UUID reference:**
```
e659f303 = battery_percent       (uint16 BE, direct %)
e659f307 = pitch                 (int16 BE, /10 = degrees, 1800 = level)
e659f308 = roll                  (int16 BE, /10 = degrees, 1800 = level)  
e659f309 = yaw                   (uint16 BE, /10 = compass degrees)
e659f30a = odometer              (uint16 BE, wheel rotations)
e659f30b = rpm                   (uint16 BE, direct RPM)
e659f30f = status_error          (byte[0]=bitmask 0–31, byte[1]=0x00)
e659f310 = controller_motor_temp (byte[0]=controller °C, byte[1]=motor °C)
e659f312 = amps                  (int16 BE, /100 = motor phase amps, negative=regen)
e659f313 = trip_amp_hours        (uint16 BE, /1000 = Ah consumed)
e659f314 = trip_regen_amp_hours  (uint16 BE, /1000 = Ah regen)
e659f315 = battery_temperature   (uint16 BE, /256 = °C, Q8.8 fixed-point)
e659f316 = pack_voltage          (uint16 BE, /10 = volts)
e659f317 = safety_headroom       (uint16 BE, direct %, 0–100)
e659f31b = cell_voltages         (byte[0]=cell_index 0–14, byte[1]*20=mV; index 15 = sentinel, 15S pack)
e659f3fe = uart_read             (Gemini auth UART challenge)
```

**Tire: 11-inch diameter.** Circumference = π × 11 in = 0.8778 m. Use this for distance/speed from odometer/RPM.

**Prior findings to verify or expand (from a Gemini pass — do NOT assume they are correct):**
- Session: 77,281 lines, 34.8 min
- Battery 90% → 63%, pack 61.3V → 58.4V
- Peak RPM 553 (~18.9 mph at 11.5in — recompute with 11in tire)
- Trip odometer raw 10,295 rotations → ~5.6 miles at 11in
- Cell voltages: 15S pack (index 0–14), starts 4.08V balanced, ends 3.90V balanced  
- Safety headroom: min 0% at peak sprint, max 100%, normal 94–100%
- Status error (e659f30f): 5-bit bitmask, 29 and 31 are dominant riding states
- Controller temp peaked 49°C (one reading of 113°C is corrupted packet noise)
- Motor winding 24–44°C
- Battery temp 25.1–32.1°C

---

## What to produce

Write Python scripts to extract and analyze the following. Use `import json`, standard library only. Print results to stdout. Then write findings to `docs/ble-analysis/ride-001-analysis.md`.

### 1. Session overview
- Total lines, unique characteristic UUIDs seen, session duration (min ts to max ts)
- Lines per characteristic (sorted by count)
- Probe reads at session start: list each probe UUID and its raw+parsed value

### 2. Speed & distance
- Recompute using 11in tire (circumference 0.8778m)
- From RPM: speed_mph = RPM × 0.8778 / 60 × 2.237
- From odometer: total_miles = final_odo_raw × 0.8778 / 1609.344  
- Average speed (excluding RPM=0 periods), peak speed, time-at-speed histogram (0–5, 5–10, 10–12, 12–15, 15–18, 18+ mph)
- Total stop time (RPM=0 periods > 5s), number of stops, durations

### 3. Safety headroom — full analysis
This is the primary interest. Safety headroom is the board's real-time power margin. 0% = board at pushback/nosedive risk. 100% = full headroom.
- Distribution: what % of ride time at each headroom range (0–10, 10–25, 25–50, 50–75, 75–100)
- Minimum seen and when (timestamp, correlate with RPM and amps at same time ±500ms)
- How fast does headroom recover after hitting low values?
- Correlation with speed: plot headroom vs RPM (bucket by 50 RPM steps, avg headroom per bucket)
- Correlation with amps: avg headroom at high-current (>50A) vs low-current periods
- Correlation with battery %: does headroom decrease as battery drains?
- Correlation with pack voltage: headroom vs pack voltage buckets
- How often does it hit below 20%? Below 10%? Below 5%?
- Time series: headroom over ride time in 60-second windows (avg, min per window)

### 4. Status error bitmask — deep decode
- List all unique byte[0] values seen, their binary representation, count, and timestamps of first/last occurrence
- For the dominant values 29 (11101b) and 31 (11111b): what triggers the toggle between them? Correlate with RPM, amps, safety headroom changes
- Document the shutdown sequence (when RPM drops to 0) and startup sequence (when RPM comes back up) with exact transitions and timing
- Bit 1 toggles most (47% of time set): characterize when it's set vs not set — is there a pattern with speed, load, or time?
- The 7 zero-value occurrences: exact timestamps, correlate with RPM and session position

### 5. Cell voltage deep analysis
- Print all 15 cells' voltage trajectory: start voltage, end voltage, min seen (under-load sag), max seen
- Cell with highest internal resistance (deepest sag under load — find timestamp of max-amps event and cell voltages at that time ±2s)
- Cell balance: max spread between cells at session start, mid-ride, session end
- Plot (text-based) pack voltage vs cell sum (15 × avg_cell_voltage) — do they track well?
- Under-load sag events: find the 5 largest amps readings and report cell voltages within ±3s

### 6. Power analysis
- Amps are motor phase current (int16 BE, /100). Report: peak discharge (highest positive), peak regen (most negative), average while moving, average while stopped
- Power curve: amps × pack_voltage at aligned timestamps — find peak instantaneous power
- Regen analysis: regen_ah / total_ah_consumed = % energy recovered
- Wh consumed estimate: integrate (amps/100 × voltage/10) over time using trapezoid rule on aligned samples

### 7. Thermal analysis
- Controller temp (byte[0]) trajectory: start, peak (excluding corrupted readings >80°C), end, rate of rise during first 10 min
- Motor temp (byte[1]) trajectory: same
- Battery temp (raw/256 in Q8.8): start, peak, end, °C rise per minute
- Find highest controller temp during high-current events (amps > 80A)
- Detect any thermal plateau or cooling events (sustained drops in temp)

### 8. Attitude data (pitch, roll, yaw)
- Pitch (1800 = level, /10 = degrees): mean during riding, std dev, range, "forward lean" events (pitch < 1780 = nose down)
- Roll (1800 = center): mean, std dev, max lean seen, count of events >15° lean, >20° lean, >25° lean
- Yaw: how many full compass rotations? Did the ride loop back to start direction?
- Find the 3 most aggressive lean moments: timestamp, roll value, simultaneous speed and amps

### 9. BLE keepalive pattern
- Identify all writes to firmware_revision (e659f311). Count them, compute inter-write intervals, verify 15s pattern
- Any gaps in keepalive that could indicate connection instability?

### 10. Anomaly detection
- Corrupted packets: any value that deviates >3 standard deviations from rolling mean for each characteristic — list them
- Notification gaps >10s for any high-rate characteristic (RPM, amps, voltage)
- Any characteristic that stopped updating before session end

---

## Output format

Write `docs/ble-analysis/ride-001-analysis.md` with:
- All findings from sections 1–10 above
- Structured markdown with tables where appropriate
- Concrete numbers throughout (no vague descriptions)
- Flag any findings that suggest app parser fixes needed
- Flag anything surprising or worth investigating further

## Compile check
No app code changes — skip compile step. Just write the analysis scripts, run them, capture output, write the markdown, and commit:
```
git add docs/ble-analysis/
git commit -m "analysis(ble): deep ride analysis of 34.8-min XR session (ride-001)"
```
