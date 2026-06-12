# BLE Debug Receiver

Small receiver for debug-only ZWheel BLE JSONL exports. The debug Android app is
currently hardcoded to upload to:

```text
http://116.203.200.55:8765
```

Run it on that server with a pairing password:

```bash
ZWHEEL_PAIRING_PASSWORD='one-time-password' \
ZWHEEL_UPLOAD_DIR=/tmp/zwheel-ble-uploads \
python3 tools/ble_debug_receiver/server.py
```

The receiver binds to `0.0.0.0:8765` by default and persists issued upload tokens in
`$ZWHEEL_UPLOAD_DIR/.upload_tokens`. Keep the pairing password private and rotate it
after capture sessions if needed.

Endpoints:

- `POST /pair` with `{"pairingPassword":"..."}` returns `{"uploadToken":"..."}`.
- `POST /upload?filename=name.jsonl` with `Authorization: Bearer <token>` stores the
  JSONL body and returns `{"uploadId":"..."}`.

The receiver rejects missing or wrong tokens, non-JSONL filenames/content types, unsafe
filenames, and uploads larger than `ZWHEEL_MAX_UPLOAD_BYTES` (default `1048576`).
