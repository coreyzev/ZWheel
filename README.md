# ZWheel

ZWheel is a personal Android + Wear OS companion for stock Future Motion Onewheels.
It is built offline-first: no account, no cloud sync, no analytics, and no runtime
network permission in v1.

## Scope

- Android phone app plus bundled Wear OS app.
- Stock Onewheel BLE telemetry, ride recording, local settings, and watch display.
- GPLv3 open source release model.
- No firmware updates, no OTA implementation, and no Future Motion cloud endpoints.

## Safety

ZWheel is not affiliated with or endorsed by Future Motion. Use it at your own risk.
The app must only write to the unlock, ride mode, and lights characteristics. Firmware
or OTA write paths are intentionally out of scope.

## Development

This repository uses a Gradle multi-module layout:

- `core`: pure Kotlin protocol, models, ports, and calculation logic.
- `app`: Android phone shell.
- `wear`: Wear OS shell.

Run the full local check with:

```bash
gradle clean check :app:assembleDebug :wear:assembleDebug
```

This environment does not currently include a Gradle binary or wrapper, so CI uses
`gradle/actions/setup-gradle` to install Gradle for pull requests.
