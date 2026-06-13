# M1 BLE Debug JSONL Schema

Debug BLE exports use newline-delimited JSON. Each line is one event object, ordered by
capture time. The format is designed for human review and AI-agent parsing.

## Version

`schemaVersion`: `m1-ble-debug-v1`

## Required Fields

- `schemaVersion`: schema string.
- `sessionId`: random UUID generated per app recorder instance.
- `offsetMs`: milliseconds since the recorder was created.
- `type`: stable event name.

## Optional Fields

- `deviceHash`: salted SHA-256 prefix for the BLE device ID. Raw MAC/address-like IDs
  must not appear in committed fixtures.
- `displayName`: BLE advertised name, if present.
- `rssi`: received signal strength in dBm.
- `characteristicUuid`: BLE characteristic UUID for reads, probes, and notifications.
- `characteristicName`: short stable name for known Onewheel characteristics.
- `rawValueHex`: raw characteristic bytes as lowercase hex without separators.
- `status`: short result, failure reason, or contextual detail.

## Event Types

- `scan_start`: debug scan began.
- `scan_discovery`: one BLE advertisement emitted by the transport.
- `scan_stop`: scan ended normally.
- `scan_failure`: scan failed.
- `selected_device`: user-selected board candidate.
- `connect_start`: connect attempt began.
- `gatt_ready`: GATT connection is ready for app-level reads/subscriptions.
- `metadata_read`: hardware, firmware, or ride-mode read result.
- `gemini_wait`: app is waiting for Gemini UART challenge.
- `gemini_trigger_write`: Gemini firmware-revision trigger write, with `status` set to
  `before` immediately before `io.write(FIRMWARE_REVISION, ...)` and `after` after a
  successful write.
- `gemini_raw_notification`: raw `UART_READ` notification observed during Gemini
  handshake, including short packets rejected before challenge parsing.
- `gemini_challenge_assembled`: complete 20-byte Gemini challenge assembled from one or
  more raw `UART_READ` notifications.
- `gemini_result`: Gemini strategy returned an unlock result.
- `connect_failure`: connect, metadata read, or unlock attempt failed.
- `telemetry_probe`: one probe characteristic notification result after Gemini timeout.
- `notification`: live characteristic notification captured after successful unlock.
- `disconnect_requested`: user tapped disconnect.
- `disconnect`: transport reported disconnected.

## Review Checklist

Before committing an exported fixture:

1. Confirm no raw BLE device IDs, phone identifiers, GPS, account, or network metadata.
2. Confirm every row has `schemaVersion=m1-ble-debug-v1`.
3. Confirm the capture includes enough events to diagnose scan, connect, Gemini,
   metadata reads, probes, or notifications relevant to the test.
