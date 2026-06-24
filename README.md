# ZWheel

> ## WORK IN PROGRESS — PRE-RELEASE
>
> ZWheel is under active development. The core BLE handshake has been verified
> on real hardware (M1 gate passed — Gemini unlock confirmed on XR HW 4209),
> but the app has not yet received a signed production release. Debug APKs are
> published from `main`. Do not rely on it as a finished ride companion.

ZWheel is a personal Android + Wear OS companion for stock Future Motion
Onewheels. It is built offline-first: no account, no cloud sync, no analytics,
and no runtime network permission in v1.

> Not endorsed by or affiliated with Future Motion in any way. Onewheel and
> Future Motion trademarks are used nominatively. Use at your own risk.

---

## Overall Progress

```text
Repo / Contracts        [####################] 100%  Done
Core protocol model     [####################] 100%  UUID map, ports, parsers, board-type detection
BLE library / transport [##################..]  90%  Kable transport; Gemini keep-alive; M1 verified
Gemini handshake        [####################] 100%  Verified on XR HW 4209 FW 4134 (M1 PASSED)
Dashboard UI            [##################..]  90%  Dark redesign complete; all spec items implemented
Android permissions     [####################] 100%  Android 12+ BLE + location runtime flow
Ride data / service     [##################..]  90%  Session recorder, GPS, HA push, foreground service
Wear OS                 [##################..]  90%  Full dashboard, ambient mode, standalone install
CI / release            [##################..]  90%  CI, APK artifact, workflow_dispatch release
Distribution            [####................]  20%  Debug APK only; no signed v1 release
Overall                 [##################..]  85%  Hardware-verified; dark redesign shipped; pre-v1
```

What "85%" means in practice: the full ride-companion feature set is implemented and
the dark instrument-cluster redesign is complete. BLE/Gemini handshake has been verified
on real hardware (M1 gate passed). The remaining gaps are open-ride hardware testing
(Wave 3 issues #102/#109), a signed production release, and a few minor open issues.

---

## Planned Features

- Live OWCE-style dashboard: speed, battery, voltage, temperatures, current,
  ride mode, and lights.
- Gemini unlock for XR/Plus/Pint-era boards, plus open/no-handshake support for
  older boards.
- Trip top speed and lifetime top speed.
- Per-board tire diameter correction for speed, distance, and range.
- Local ride recording to SQLite with GPS and telemetry. No cloud.
- Wear OS companion: speed, top speed, battery, estimated range, ambient always-on mode.
- Samsung-aware battery optimization onboarding for reliable background rides.
- GitHub Releases with sideloadable Android debug APKs during development.

Out of scope for v1:

- iOS or watchOS.
- VESC boards.
- GT / GT-S Polaris support.
- Accounts, leaderboards, analytics, cloud sync, or runtime network calls.
- Firmware updates, OTA, firmware downloads, or Future Motion cloud endpoints.

---

## Safety Posture

ZWheel is intentionally limited to the community-documented BLE surface used by
other Onewheel companion apps.

- No firmware/OTA UUIDs are defined in the codebase.
- Writable BLE operations are limited to unlock, ride mode, and lights.
- A unit test guards the writable UUID allowlist.
- `INTERNET` permission is granted solely for OSMDroid map tiles and optional Home Assistant push; no analytics, no accounts, no cloud sync.
- Speed, distance, and range features must fail conservatively when calibration
  data is missing.

Anything requiring a real board is explicitly hardware-gated. The implementing
agent does not claim board verification; Corey performs physical-device tests.

---

## Architecture

```text
core/
  Pure Kotlin protocol, models, ports, parser logic, and calculation contracts.
  This module has zero Android imports.

app/
  Android phone app: Compose UI, Hilt, Kable BLE transport, debug BLE screen,
  onboarding, future foreground service, Room repositories, and settings.

wear/
  Wear OS app shell. The v1 design uses the phone as the BLE owner and pushes
  ride state to the watch through the Wear Data Layer.

docs/
  Project brief, architecture, build plan, runbook, gates, and ADRs.
```

Key decisions are recorded in `docs/adr/`, including the Kable BLE choice in
ADR-003.

---

## Current Milestone

**M1 PASSED** — Gemini unlock verified on XR HW 4209 FW 4134. Telemetry confirmed
flowing. Keep-alive write cadence (every 15 s) locks telemetry on indefinitely.

### Completed (shipped to main)

- Gradle modules for `core`, `app`, and `wear`.
- CI for app/wear builds and unit tests; `workflow_dispatch` release pipeline.
- Core UUID map, parsers, board-type detection, and parser test harness.
- Kable BLE transport with Gemini handshake + 15 s keep-alive.
- Dark instrument-cluster redesign: dark theme, Saira + JetBrains Mono fonts,
  all spec screens implemented (connect, permissions, dashboard, history list +
  detail + full-screen map, settings, Wear OS).
- Edge-to-edge layout; pushback headroom bar with lime→amber→red gradient;
  ride-detail full-screen map with speed-colored route overlay and stats.
- Samsung battery advice UI; Android 12+ BLE + location runtime permission flow.
- Foreground ride service: GPS capture, session recording, top speed, range estimate.
- Home Assistant battery push via REST API (no custom component).
- Wear OS companion with ambient always-on mode and standalone distribution.
- BLE debug logging toggle in Settings with JSONL export / upload.

### Open (Wave 3)

- **#102** Orphan session recovery on START_STICKY restart.
- **#109** BLE auto-reconnect on unexpected disconnection.
- **#104** Remaining test coverage gaps (SettingsRepository, ConnectionManager, Wear).
- Signed production release (Phase 5).
- Confirm LIGHTS_FRONT / LIGHTS_BACK writability (#AGENTS.md open question).

---

## Development

This project is developed with AI agent assistance under the rules in
`AGENTS.md`. Read that file before making code changes.

Prerequisites:

- JDK 17
- Android SDK with API 35 and build-tools 35.0.0
- Gradle wrapper from this repository

Run the full local check:

```bash
./gradlew clean check :app:assembleDebug :wear:assembleDebug
```

Build only the phone debug APK:

```bash
./gradlew :app:assembleDebug
```

The debug APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

---

## Wear OS

The watch app shows speed, battery, top speed, and estimated range. It stays
on-screen in ambient mode (dimmed, black-and-white) when the wrist drops, so
raise-to-wake always shows ZWheel instead of the watch face.

### Installation

ZWheel for Wear OS is a standalone app. It is NOT bundled with the phone APK.

- **Production:** The app is published to the Google Play Store for independent
  installation on the watch.
- **Development / Sideload:** Use ADB to install the watch APK directly.

#### Sideloading via ADB

**Prerequisites:**
- Developer options enabled on the watch (tap **Build number** 7× in
  Settings → System → About)
- ADB debugging enabled

**Connect ADB — WiFi (Wear OS 3+)**
```bash
# On the watch: Settings → Developer options → Wireless debugging → Pair new device
adb pair <watch-ip>:<pair-port>   # enter pairing code when prompted
adb connect <watch-ip>:5555
```

**Connect ADB — Bluetooth (older Wear OS)**
```bash
# On phone: Wear OS app → Advanced Settings → enable Bluetooth debugging
adb forward tcp:4444 localabstract:/adb-hub
adb connect localhost:4444
```

**Build and install**
```bash
./gradlew :wear:assembleDebug
adb -s <watch-device-id> install wear/build/outputs/apk/debug/wear-debug.apk
```

The app appears in the watch app drawer and shows live data whenever the phone
app's foreground service is running (i.e. while connected to your board).

---

## Distribution

Development builds are published from `main`:

- CI artifact: `zwheel-debug`
- GitHub Release tag: `latest`
- Release asset: `zwheel-debug.apk`

This is a debug build signed with the Android debug keystore. A proper signed
release pipeline and v1.0.0 tag are Phase 5 work.

---

## Home Assistant Integration

ZWheel can push your board's battery percentage to a Home Assistant instance
whenever it changes. No custom component is required — ZWheel uses the standard
HA REST API to write a sensor state directly.

### What gets pushed

ZWheel writes to `sensor.onewheel_battery` each time the battery percent changes
while connected. The entity is created automatically on the first push; you do not
need to pre-configure it in HA.

Payload written per push:
```json
{
  "state": "<battery_pct>",
  "attributes": {
    "unit_of_measurement": "%",
    "device_class": "battery",
    "friendly_name": "Onewheel Battery"
  }
}
```

### Home Assistant setup

1. In Home Assistant, go to your **Profile** (bottom-left avatar) → scroll to
   **Long-Lived Access Tokens** → click **Create Token**.
2. Give it a name (e.g., `ZWheel`) and copy the token — it is shown **only once**.
3. Note your HA base URL. Examples:
   - Local network: `http://homeassistant.local:8123`
   - Remote/HTTPS: `https://your-ha.duckdns.org`
   - Nabu Casa: `https://abcdef1234567890.ui.nabu.casa`

> The URL must be reachable from your phone at ride time. If you ride outside
> your home network, use a remote URL (HTTPS strongly recommended).

### App setup

1. Open **Settings** in ZWheel → scroll to **Home Assistant (Optional)**.
2. Toggle **Push battery % to HA** on.
3. Enter your **HA URL** (include the scheme and port if non-standard).
4. Enter your **Long-Lived Access Token**.
5. Tap **Test connection** — a live 50% test push is sent. A green confirmation
   means HA accepted it and `sensor.onewheel_battery` now exists.

Once set up, ZWheel pushes automatically while the foreground ride service is
running (i.e., while connected to the board).

### Using the sensor in Home Assistant

After the first push, `sensor.onewheel_battery` is available in:

- **Dashboard cards** — add an Entities or Gauge card pointing to
  `sensor.onewheel_battery`.
- **Automations** — e.g., notify when battery drops below 20%.
- **History** — HA records every state change automatically.

---

## License

ZWheel is licensed under GPLv3. See `LICENSE` and `NOTICE.md`.

Protocol references include community projects such as pOnewheel, OWCE,
UWP-Onewheel, and Onewheel2Garmin. Do not copy proprietary/decompiled Future
Motion code into this repository.
