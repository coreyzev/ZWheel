#!/usr/bin/env python3
"""Debug-only BLE JSONL upload receiver for ZWheel."""

from __future__ import annotations

import json
import os
import re
import secrets
from dataclasses import dataclass, field
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any
from urllib.parse import parse_qs, urlparse


SAFE_FILENAME = re.compile(r"^[A-Za-z0-9._-]+\.jsonl$")


@dataclass
class ReceiverState:
    pairing_password: str
    upload_dir: Path
    max_upload_bytes: int = 1_048_576
    token_file: Path | None = None
    tokens: set[str] = field(default_factory=set)

    def load_tokens(self) -> None:
        if self.token_file is None or not self.token_file.exists():
            return
        self.tokens.update(
            line.strip()
            for line in self.token_file.read_text(encoding="utf-8").splitlines()
            if line.strip()
        )

    def add_token(self, token: str) -> None:
        self.tokens.add(token)
        if self.token_file is None:
            return
        self.token_file.parent.mkdir(parents=True, exist_ok=True)
        with self.token_file.open("a", encoding="utf-8") as handle:
            handle.write(f"{token}\n")


def build_handler(state: ReceiverState) -> type[BaseHTTPRequestHandler]:
    class Handler(BaseHTTPRequestHandler):
        server_version = "ZWheelBleDebugReceiver/1"

        def do_POST(self) -> None:
            parsed = urlparse(self.path)
            if parsed.path == "/pair":
                self._handle_pair()
            elif parsed.path == "/upload":
                self._handle_upload(parse_qs(parsed.query))
            else:
                self._json_response(404, {"error": "not_found"})

        def log_message(self, format: str, *args: Any) -> None:
            return

        def _handle_pair(self) -> None:
            body = self._read_body(state.max_upload_bytes)
            if body is None:
                return
            try:
                payload = json.loads(body.decode("utf-8"))
            except json.JSONDecodeError:
                self._json_response(400, {"error": "invalid_json"})
                return

            if payload.get("pairingPassword") != state.pairing_password:
                self._json_response(401, {"error": "invalid_pairing_password"})
                return

            token = secrets.token_urlsafe(32)
            state.add_token(token)
            self._json_response(200, {"uploadToken": token})

        def _handle_upload(self, query: dict[str, list[str]]) -> None:
            token = self.headers.get("Authorization", "").removeprefix("Bearer ").strip()
            if token not in state.tokens:
                self._json_response(401, {"error": "invalid_token"})
                return

            content_type = self.headers.get("Content-Type", "").split(";")[0].strip()
            if content_type not in {"application/x-ndjson", "application/jsonl", "text/plain"}:
                self._json_response(415, {"error": "unsupported_content_type"})
                return

            requested_name = query.get("filename", [""])[0]
            if not SAFE_FILENAME.fullmatch(requested_name):
                self._json_response(400, {"error": "invalid_filename"})
                return

            body = self._read_body(state.max_upload_bytes)
            if body is None:
                return
            if not body.strip().startswith(b"{"):
                self._json_response(400, {"error": "invalid_jsonl"})
                return

            state.upload_dir.mkdir(parents=True, exist_ok=True)
            upload_id = secrets.token_hex(8)
            safe_name = f"{upload_id}-{requested_name}"
            (state.upload_dir / safe_name).write_bytes(body)
            self._json_response(200, {"uploadId": upload_id, "filename": safe_name})

        def _read_body(self, max_bytes: int) -> bytes | None:
            length_header = self.headers.get("Content-Length")
            if length_header is None:
                self._json_response(411, {"error": "content_length_required"})
                return None
            try:
                length = int(length_header)
            except ValueError:
                self._json_response(400, {"error": "invalid_content_length"})
                return None
            if length > max_bytes:
                self._json_response(413, {"error": "upload_too_large"})
                return None
            return self.rfile.read(length)

        def _json_response(self, status: int, payload: dict[str, Any]) -> None:
            body = json.dumps(payload, separators=(",", ":")).encode("utf-8")
            self.send_response(status)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

    return Handler


def main() -> None:
    password = os.environ.get("ZWHEEL_PAIRING_PASSWORD")
    if not password:
        raise SystemExit("ZWHEEL_PAIRING_PASSWORD is required")

    host = os.environ.get("ZWHEEL_RECEIVER_HOST", "0.0.0.0")
    port = int(os.environ.get("ZWHEEL_RECEIVER_PORT", "8765"))
    upload_dir = Path(os.environ.get("ZWHEEL_UPLOAD_DIR", "/tmp/zwheel-ble-uploads"))
    max_upload_bytes = int(os.environ.get("ZWHEEL_MAX_UPLOAD_BYTES", "1048576"))
    token_file = Path(os.environ.get("ZWHEEL_TOKEN_FILE", str(upload_dir / ".upload_tokens")))
    state = ReceiverState(
        pairing_password=password,
        upload_dir=upload_dir,
        max_upload_bytes=max_upload_bytes,
        token_file=token_file,
    )
    state.load_tokens()

    server = ThreadingHTTPServer((host, port), build_handler(state))
    print(f"Listening on http://{host}:{port}; store={upload_dir}; tokens={token_file}")
    server.serve_forever()


if __name__ == "__main__":
    main()
