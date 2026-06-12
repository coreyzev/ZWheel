# BLE Debug Receiver

Small local receiver for debug-only ZWheel BLE JSONL exports.

Run it behind HTTPS using a reverse proxy or tunnel. Do not expose the plain HTTP server
directly on the public internet.

```bash
ZWHEEL_PAIRING_PASSWORD='one-time-password' \
ZWHEEL_UPLOAD_DIR=/tmp/zwheel-ble-uploads \
python3 tools/ble_debug_receiver/server.py
```

Endpoints:

- `POST /pair` with `{"pairingPassword":"..."}` returns `{"uploadToken":"..."}`.
- `POST /upload?filename=name.jsonl` with `Authorization: Bearer <token>` stores the
  JSONL body and returns `{"uploadId":"..."}`.

The receiver rejects missing or wrong tokens, non-JSONL filenames/content types, unsafe
filenames, and uploads larger than `ZWHEEL_MAX_UPLOAD_BYTES` (default `1048576`).
