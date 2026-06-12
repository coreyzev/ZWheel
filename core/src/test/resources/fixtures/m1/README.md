# M1 BLE Debug Fixtures

This directory holds reviewed, redacted JSONL exports from the debug BLE screen.

Exported logs should be committed here only after Corey or the reviewing agent confirms:

- stable BLE device identifiers are absent;
- no GPS, account, phone, or network metadata is present;
- every row is a single JSON object using `schemaVersion=m1-ble-debug-v1`;
- the capture contains enough context for an agent to diagnose scan, connect, Gemini,
  metadata, probe, and notification behavior.

Use descriptive filenames, for example `xr4029_gemini_unlock_2026-06-12.jsonl`.
