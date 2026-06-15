# ZWheel

> ## WORK IN PROGRESS - NOT RIDE READY
>
> ZWheel is under active development. The app builds, the core BLE/handshake
> path has fixture tests, and debug APKs are published from `main`, but the
> current build has not yet passed the real-board M1 hardware gate. Do not rely
> on it as a finished ride companion.

ZWheel is a personal Android + Wear OS companion for stock Future Motion
Onewheels. It is built offline-first: no account, no cloud sync, no analytics,
and no runtime network permission in v1.

> Not endorsed by or affiliated with Future Motion in any way. Onewheel and
> Future Motion trademarks are used nominatively. Use at your own risk.

---

## Overall Progress

```text
Repo / Contracts        [####################] 100%  Done
Core protocol model     [##################..]  90%  UUID map, ports, parser harness
BLE library / transport [##############......]  70%  Kable transport exists; hardware gate open
Gemini handshake        [################....]  80%  Fixture tests pass; board unlock unverified
Debug screen            [##############......]  70%  Scan -> connect -> unlock -> dump path
Android permissions     [####################] 100%  Android 12+ BLE + location runtime flow
Dashboard UI            [##############......]  70%  Live speed/battery/temp/GPS, ride history
Ride data / service     [##################..]  90%  Session recorder, GPS, HA push, foreground service
Wear OS                 [##################..]  90%  Full dashboard, ambient mode, auto-installs with phone app
CI / release            [##################..]  90%  CI, APK artifact, latest debug release
Distribution            [####................]  20%  Debug APK only; no signed v1 release
Overall                 [##############......]  70%  Feature-complete; awaiting hardware verification
```

What "70%" means in practice: the full ride-companion feature set is implemented
(BLE, live dashboard, GPS tracking, ride history with maps, Wear OS companion,
Home Assistant battery push). The remaining gap is physical hardware
verification — the M1 gate (real-board connect + 10-minute telemetry soak) has
not been passed yet.

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

The project is between Phase 1 implementation and Gate M1.

Completed locally:

- Gradle modules for `core`, `app`, and `wear`.
- CI for app/wear builds and unit tests.
- Debug APK artifact upload.
- `latest` GitHub Release workflow for debug APKs.
- Core UUID map and parser test harness.
- Kable BLE transport.
- None and Gemini handshake strategies.
- Gemini fixture tests without hardware.
- Minimal debug screen for scan, connect, unlock, and characteristic dump.
- Samsung battery advice UI.
- Android 12+ BLE + location runtime permission request flow.
- Live dashboard: speed, battery, cell voltage, temperatures, pitch/roll/yaw.
- Foreground ride service with GPS capture, session recording, and top speed.
- Ride history list and detail screen with speed-colored GPS map overlay.
- Full-screen map tap-through from ride detail.
- Home Assistant battery push via REST API (no custom component).
- Wear OS companion with ambient always-on mode and automatic phone-bundled install.

Still required for M1:

- Connect to Corey's XR HW 4029.
- Verify Gemini unlock on real hardware.
- Keep telemetry flowing for 10+ minutes.
- Capture the characteristic dump and commit it as test fixtures.
- Confirm open questions around the RPM and lights characteristics.

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

### Automatic install (recommended)

The wear APK is bundled inside the phone APK via `wearApp(project(":wear"))`.
When you install the phone app on a device paired with a Wear OS watch, Android
automatically pushes the watch app — no separate step needed.

For the debug APK from CI: install `zwheel-debug.apk` on your phone normally.
The Wear OS system detects the embedded watch app and installs it within a few
minutes. Check **Play Store → My apps** on the watch to trigger it immediately.

### Manual sideload (dev / re-install)

If you need to push directly to the watch (e.g. iterating on watch UI):

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

## License

ZWheel is licensed under GPLv3. See `LICENSE` and `NOTICE.md`.

Protocol references include community projects such as pOnewheel, OWCE,
UWP-Onewheel, and Onewheel2Garmin. Do not copy proprietary/decompiled Future
Motion code into this repository.
