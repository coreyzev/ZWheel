# M1 BLE Capture Runbook

This is the Phase 1 hardware-gate capture procedure for Corey's XR. The normal capture
path is now the debug screen's redacted JSONL export to the fixed paired receiver at
`http://116.203.200.55:8765`. HCI snoop is not needed for the standard M1 gate.

The exported JSONL is designed for human review and AI-agent parsing. Schema details are
in `docs/M1_BLE_DEBUG_JSONL_SCHEMA.md`.

## Goals

The M1 capture should answer these questions:

- Does Gemini unlock hold for at least 10 minutes?
- Do the debug-screen characteristics stream continuously after unlock?
- Does `e659f30b-ea98-11e3-ac10-0800200c9a66` behave like RPM on Corey's 4029 XR?
- Does the lights control need only `LIGHTS`, or do `LIGHTS_FRONT` and
  `LIGHTS_BACK` need separate write access?

## Privacy Rules

The app-level export redacts stable BLE device identifiers by default. Before committing
any fixture, still review it and confirm it contains no raw BLE IDs, phone identifiers,
GPS, account, or unrelated network metadata.

Commit only reviewed JSONL fixtures under:

```text
core/src/test/resources/fixtures/m1/
```

Do not commit raw bugreports, HCI snoop logs, screenshots with personal data, or receiver
runtime secrets.

## Start the Receiver

Run the receiver from the repo root on `116.203.200.55`:

```bash
export ZWHEEL_PAIRING_PASSWORD='choose-a-one-time-password'
export ZWHEEL_UPLOAD_DIR=/tmp/zwheel-ble-uploads
python3 tools/ble_debug_receiver/server.py
```

Defaults for this workflow:

- host: `0.0.0.0`
- port: `8765`
- upload folder: `/tmp/zwheel-ble-uploads`
- token file: `/tmp/zwheel-ble-uploads/.upload_tokens`
- max upload size: `1048576` bytes

If running as a long-lived service, set the same environment variables in the service
unit. The debug app is already hardcoded to the server IP, so the phone does not need a
server URL.

The pairing password is still required. Keep it private and rotate it after test
sessions if needed.

## Pair the Phone Once

Install a debug APK. The upload UI and `INTERNET` permission exist only in debug builds.

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

On the phone:

1. Open ZWheel.
2. Open the BLE debug screen.
3. Tap `Pair upload`.
4. Confirm the dialog shows `http://116.203.200.55:8765`.
5. Enter `ZWHEEL_PAIRING_PASSWORD`.
6. Tap `Pair`.
7. Confirm the status line says `Paired with 116.203.200.55`.

Pairing sends the one-time password to `POST /pair`. The receiver returns an upload token,
and the debug app stores that token locally for later `Upload log` taps. No upload token
or pairing password is committed to the repo. Receiver-side tokens are persisted in
`/tmp/zwheel-ble-uploads/.upload_tokens`, so restarting the receiver should not force
the phone to pair again.

## Run the M1 Board Session

On the phone:

1. Force-stop the official Future Motion app so it cannot compete for the board.
2. Keep the phone within roughly 2 meters of the board.
3. Open ZWheel's BLE debug screen.
4. Grant BLE permissions if prompted.
5. Tap `Scan`.
6. Select the XR.
7. Tap `Connect + Unlock`.
8. Let the connection run for at least 10 minutes.
9. Watch the debug log for battery percent, RPM, pack voltage, amps, temperature, and
   ride mode notifications.

During the run, write down:

- board name shown in scan results;
- whether unlock held for 10+ minutes;
- whether any characteristic stopped streaming;
- whether RPM changes when the wheel is spun very slightly and safely;
- what happened when lights were toggled, if tested.

## Export the Log

Preferred path:

1. Tap `Upload log`.
2. Confirm the status line shows `Uploaded <id>`.
3. On the receiver machine, list the upload folder:

```bash
ls -lh /tmp/zwheel-ble-uploads
```

Fallback path if upload is not reachable:

1. Tap `Share log`.
2. Share the `.jsonl` file through a local path that preserves the file unchanged.
3. Send the file to the reviewing agent.

Each JSONL row should have `schemaVersion=m1-ble-debug-v1` and a `type` such as
`scan_discovery`, `gatt_ready`, `metadata_read`, `gemini_result`,
`telemetry_probe`, or `notification`.

## Review and Commit a Fixture

Inspect the uploaded/shared JSONL before committing:

```bash
head -n 20 /tmp/zwheel-ble-uploads/<uploaded-file>.jsonl
rg '([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}' /tmp/zwheel-ble-uploads/<uploaded-file>.jsonl
```

The `rg` command should find no raw MAC-address-like values.

Copy the reviewed file into the fixture directory with a descriptive name:

```bash
mkdir -p core/src/test/resources/fixtures/m1
cp /tmp/zwheel-ble-uploads/<uploaded-file>.jsonl \
  core/src/test/resources/fixtures/m1/xr4029-gemini-session.jsonl
```

Commit only the reviewed fixture and any short notes:

```bash
git checkout -b fixture/m1-xr4029-capture
git add core/src/test/resources/fixtures/m1/xr4029-gemini-session.jsonl
git commit -m "test(core): add m1 xr ble capture fixture"
git push -u origin fixture/m1-xr4029-capture
```

After the fixture lands, update `AGENTS.md` with resolved M1 facts and use the fixture
to drive parser tests, `BoardStateService`, and ADR-006.

## HCI Snoop Fallback

Use Android HCI snoop only if the app-level JSONL is insufficient, for example when
debugging Android/Kable behavior below the app's GATT API or confirming characteristic
properties that never surface through the debug screen.

If HCI snoop is used, do not commit the raw `btsnoop_hci.log` or bugreport. Reduce it to
the same reviewed fixture shape described above, and keep the raw capture out of git.
