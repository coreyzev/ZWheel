# ZWheel Hardware Validation Checklist

**Device setup:**
- Phone: Samsung S25 Ultra (One UI 7+)
- Watch: Galaxy Watch 7 Classic (Wear OS 5)
- Board: XR HW 4209, Gemini-era firmware

Run these tests in milestone order. Mark each ✅ pass or ❌ fail with notes.

---

## M1 — BLE Connect & Gemini Unlock

1. Install debug APK fresh (uninstall first if upgrading).
2. Grant all permissions when prompted: Location (precise), Nearby devices.
3. Tap **Scan**. Board must appear within 30 s.
4. Tap the board entry. App shows "Gemini wait UART 5s" then "Connected".
   - **Pass:** Dashboard shows live voltage/battery within 5 s of "Connected".
   - **Fail:** "Board unlock failed" or no telemetry after 10 s.
5. Watch telemetry for 30 s. Voltage and battery must remain non-zero.
   - **Pass:** Values stay live past the 20 s mark (keep-alive working).
   - **Fail:** Values go to zero ~20 s after connect.
6. Tap **Disconnect**. App returns to scan state. Board shows no active connection.
7. Upload debug JSONL from the export button. Confirm file uploads and shows `gemini_keep_alive_write` events.

---

## M1b — Battery Optimization Exemption

8. On first launch of a fresh install, connect to board.
9. Android shows "Allow ZWheel to run unrestricted in the background?" dialog.
   - **Pass:** Tapping Allow prevents Samsung killing the service while riding.
   - **Fail if:** Dialog never appears (check `hasRequestedBatteryOptimization` pref).
10. After granting, kill and reopen app. Dialog must NOT appear again.

---

## M2 — Telemetry Accuracy

11. Set tire diameter in **Settings** to actual tire size (default XR = 11.5 in).
12. Connect and ride a flat stretch of known distance (~0.5 mile GPS segment).
13. Compare ZWheel dashboard speed to GPS speed during steady riding.
    - **Pass:** Within ~5% of GPS speed at steady pace.
    - **Fail:** Shows 0 mph or implausibly high value (e.g. 29 mph while stationary).
14. Note amps display. Expected ~0.5–3 A while coasting, higher under load.
    Upload JSONL for review if values seem off.
15. Cell voltage card — note whether populated or empty (empty is expected until #28 ships).

---

## M3 — Ride Recording

16. Connect to board. Dashboard shows no "Recording" state yet.
17. Ride above walking speed for 4+ seconds. A ride session starts automatically.
    - **Pass:** Dashboard shows "Recording" indicator (or notification updates to show ride duration).
    - **Fail:** No recording indicator after 10 s of riding.
18. **Break test:** Stop the board for 2+ minutes while still connected.
    - **Pass:** Dashboard still shows the same active session when you start riding again.
    - **Fail:** Session ended and a new one starts when you resume (old bug, should be fixed).
19. Disconnect from the board (tap Disconnect or turn board off).
    - **Pass:** Ride session ends and appears in **History**.
    - **Fail:** History is empty or session has no end time.
20. Tap the ride in History. Ride detail shows start time, duration, distance, top speed.
    - **Pass:** All values are plausible for the ride you just did.
    - **Fail:** Zero values or "uncorrected" badge on speed/distance (check tire diameter setting).

---

## M3b — Foreground Service Survival

21. Start a ride session (see step 17).
22. Press Home button — app goes to background. Foreground notification must remain visible.
23. Swipe app from Recents. Notification must remain; service keeps running.
    - **Pass on S25 Ultra:** Notification stays, board stays connected.
    - **Fail:** Notification disappears within 60 s (battery optimization not granted — see step 9).
24. Screen off for 5 minutes while riding. Reconnect phone, verify ride still recording.
25. Lock screen — notification shows current speed and battery (e.g. "18 mph · 76%").
    - **Pass:** Speed and battery update every second or two.
    - **Fail:** Notification stuck on "ZWheel · Connecting…" or shows wrong unit.

---

## M3c — Speed Unit Preference

26. Go to **Settings**, change speed unit to **KPH**.
27. Connect and ride. Notification must show speed in kph (e.g. "28 kph · 76%").
    - **Pass:** Unit matches setting.
    - **Fail:** Still shows mph.
28. Switch back to MPH. Verify notification updates on next board state tick.

---

## M4 — Wear OS Dashboard

29. Ensure ZWheel is installed on the watch (deploy wear APK via ADB or Play).
30. Connect phone app to board. Watch face should update within ~5 s.
    - **Pass:** Watch shows live speed, battery, and connection state ("CONNECTED").
    - **Fail:** Watch shows "DISCONNECTED" or all zeros while phone is connected.
31. Ride briefly. Watch top speed must update above 0 after sustained riding.
32. Disconnect from board. Watch shows "DISCONNECTED" within ~5 s.
33. Ambient/always-on mode (press side button on watch to dim). Verify watch tile still visible.

---

## Reconnect & Edge Cases

34. **Board power-off while connected:** Turn board off without tapping Disconnect.
    - Expected: App may show stale telemetry briefly, then "Disconnected" within 30 s.
    - Note: Actual behavior may vary — upload log for analysis.
35. **Airplane mode:** Enable airplane mode while connected. App shows "Disconnected". Disable airplane mode. App does NOT auto-reconnect (by design in v1). Tap Scan to reconnect.
36. **Multiple scan attempt:** Two boards nearby (if available) — both must appear in scan list.

---

## Debug Log Upload

After each major test block:
- Open the BLE Debug screen (if available in debug build).
- Tap **Export** and note the session ID printed.
- Confirm JSONL upload succeeds and file is accessible to Corey.
- Key events to verify in each log:
  - `gemini_result: unlocked=true`
  - `gemini_keep_alive_write: status=after` (repeating every 15 s)
  - `ride_session_start` / `ride_session_end` (when implemented)

---

## Known Limitations (v1)

- No auto-reconnect after disconnect or board power-off.
- Cell voltages not yet populated (#28).
- GPS route capture not yet implemented (gate spec written, pending).
- Lights / ride-mode controls not yet implemented (ADR-009 pending).
- Live Updates / Samsung Now Bar not yet implemented (#48).
