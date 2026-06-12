# M1 BLE Capture Runbook

This is the Phase 1 hardware-gate capture procedure for Corey's XR. Use it to
turn one real board session into small, redacted fixtures that future parser and
telemetry tests can rely on.

HCI snoop capture is not the normal debugging workflow. It should be rare: use it
when ZWheel needs low-level BLE evidence that the app cannot already show, such as
confirming characteristic UUIDs/properties, handshake bytes, missing notifications,
or Android/Kable behavior. For everyday debugging, prefer the app's debug screen
logs and, later, an in-app redacted BLE log export.

## Goals

The M1 capture should answer these questions:

- Does Gemini unlock hold for at least 10 minutes?
- Do the six debug-screen characteristics stream continuously?
- Does `e659f30b-ea98-11e3-ac10-0800200c9a66` behave like RPM on Corey's 4029 XR?
- Does the lights control need only `LIGHTS`, or do `LIGHTS_FRONT` and
  `LIGHTS_BACK` need separate write access?

## Privacy Rules

Do not commit the full raw `btsnoop_hci.log` or a complete bugreport. Those files
can include phone identifiers, board identifiers, and unrelated nearby BLE devices.

Commit only a reduced fixture containing:

- timestamp offset from the start of the useful capture window;
- Onewheel characteristic UUID;
- friendly characteristic name, if known;
- raw value bytes as hex;
- short notes needed to interpret the sample.

Redact or omit:

- phone MAC/device identifiers;
- board MAC, unless it is needed for a one-time local diagnosis;
- unrelated BLE devices;
- full HCI packets not needed for parser fixtures.

## Enable Android HCI Snoop Logging

Android's platform documentation covers the official logging path:
[Verify and debug Bluetooth on Android](https://source.android.com/docs/core/connect/bluetooth/verifying_debugging).

On the phone:

1. Enable Developer Options.
2. In Developer Options, enable `Bluetooth HCI snoop log`.
3. Restart Bluetooth for logging to take effect.
4. Force-stop the official Future Motion app so it cannot compete for the board.
5. Keep the phone within roughly 2 meters of the board.

## Install and Run the M1 Session

From this repo:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

On the phone:

1. Open ZWheel.
2. Open the BLE debug screen.
3. Grant BLE permissions.
4. Tap Scan.
5. Select the XR.
6. Tap Connect + Unlock.
7. Let the connection run for at least 10 minutes.
8. Confirm whether the debug log continues showing values for:
   - battery percent;
   - RPM;
   - pack voltage;
   - amps;
   - temperature;
   - ride mode.

During the run, write down:

- board name shown in scan results;
- whether unlock held for 10+ minutes;
- whether any characteristic stopped streaming;
- whether RPM changes when the wheel is spun very slightly and safely;
- what happened when lights were toggled, if tested.

## Pull the Bugreport

Immediately after the run:

```bash
adb bugreport /tmp/zwheel-m1-bugreport.zip
mkdir -p /tmp/zwheel-m1
unzip /tmp/zwheel-m1-bugreport.zip -d /tmp/zwheel-m1
find /tmp/zwheel-m1 -iname '*btsnoop*' -o -iname '*hci*'
```

Open the found `btsnoop`/HCI file in Wireshark. Wireshark's Bluetooth ATT tools
and filter references are useful when narrowing the capture:

- [Bluetooth ATT Server Attributes](https://www.wireshark.org/docs/wsug_html_chunked/ChWirelessBluetoothATTServerAttributes.html)
- [Bluetooth Attribute Protocol display filters](https://www.wireshark.org/docs/dfref/b/btatt.html)

Useful display filter:

```text
btatt
```

If the capture is noisy, narrow to the Onewheel connection and the Onewheel service
UUID:

```text
e659f300-ea98-11e3-ac10-0800200c9a66
```

## Create the Reduced Fixture

Create a fixture file:

```bash
mkdir -p core/src/test/resources/fixtures/m1
$EDITOR core/src/test/resources/fixtures/m1/xr-4029-characteristic-dump.jsonl
```

Use JSON Lines so future tests can read one sample at a time:

```json
{"ts_ms":0,"uuid":"e659f31c-ea98-11e3-ac10-0800200c9a66","name":"battery_percent","value_hex":"5f","note":"example only"}
{"ts_ms":1000,"uuid":"e659f30b-ea98-11e3-ac10-0800200c9a66","name":"rpm","value_hex":"0000","note":"example only"}
{"ts_ms":1000,"uuid":"e659f312-ea98-11e3-ac10-0800200c9a66","name":"pack_voltage","value_hex":"0f9a","note":"example only"}
```

Guidelines:

- keep at least several samples for each streaming characteristic;
- include a sample before and after a small safe wheel movement if validating RPM;
- include a short note when a value corresponds to a deliberate action, such as a
  light toggle;
- use lowercase hex with no separators in `value_hex`;
- keep timestamps as offsets, not wall-clock time.

## Commit the Fixture

Do not commit `/tmp/zwheel-m1-bugreport.zip` or `btsnoop_hci.log`.

Commit only the reduced fixture and any short notes:

```bash
git checkout -b fixture/m1-xr4029-capture
git add core/src/test/resources/fixtures/m1/xr-4029-characteristic-dump.jsonl
git commit -m "test(core): add m1 xr characteristic capture fixture"
git push -u origin fixture/m1-xr4029-capture
```

After the fixture lands, update `AGENTS.md` with the resolved M1 facts and use the
fixture to drive parser tests, `BoardStateService`, and ADR-006.
