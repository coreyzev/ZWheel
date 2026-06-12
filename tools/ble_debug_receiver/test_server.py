import json
import threading
import unittest
from http.client import HTTPConnection
from pathlib import Path
from tempfile import TemporaryDirectory

from server import ReceiverState, build_handler
from http.server import ThreadingHTTPServer


class ReceiverTest(unittest.TestCase):
    def setUp(self):
        self.temp_dir = TemporaryDirectory()
        self.state = ReceiverState(
            pairing_password="correct-password",
            upload_dir=Path(self.temp_dir.name),
            max_upload_bytes=128,
        )
        self.server = ThreadingHTTPServer(("127.0.0.1", 0), build_handler(self.state))
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.host, self.port = self.server.server_address

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join(timeout=5)
        self.temp_dir.cleanup()

    def test_pair_fails_with_wrong_password(self):
        status, _ = self.request(
            "POST",
            "/pair",
            {"pairingPassword": "wrong"},
            {"Content-Type": "application/json"},
        )

        self.assertEqual(401, status)

    def test_pair_succeeds_with_correct_password(self):
        status, body = self.pair()

        self.assertEqual(200, status)
        self.assertIn("uploadToken", body)

    def test_upload_fails_without_token(self):
        status, _ = self.request(
            "POST",
            "/upload?filename=capture.jsonl",
            b'{"schemaVersion":"m1-ble-debug-v1"}\n',
            {"Content-Type": "application/x-ndjson"},
        )

        self.assertEqual(401, status)

    def test_upload_succeeds_with_token(self):
        _, pair_body = self.pair()

        status, body = self.request(
            "POST",
            "/upload?filename=capture.jsonl",
            b'{"schemaVersion":"m1-ble-debug-v1"}\n',
            {
                "Authorization": f"Bearer {pair_body['uploadToken']}",
                "Content-Type": "application/x-ndjson",
            },
        )

        self.assertEqual(200, status)
        written = list(Path(self.temp_dir.name).glob("*-capture.jsonl"))
        self.assertEqual(1, len(written))
        self.assertEqual(b'{"schemaVersion":"m1-ble-debug-v1"}\n', written[0].read_bytes())
        self.assertIn("uploadId", body)

    def test_oversized_upload_is_rejected(self):
        _, pair_body = self.pair()

        status, _ = self.request(
            "POST",
            "/upload?filename=capture.jsonl",
            b"x" * 129,
            {
                "Authorization": f"Bearer {pair_body['uploadToken']}",
                "Content-Type": "application/x-ndjson",
            },
        )

        self.assertEqual(413, status)

    def test_non_jsonl_upload_is_rejected(self):
        _, pair_body = self.pair()

        status, _ = self.request(
            "POST",
            "/upload?filename=capture.txt",
            b'{"schemaVersion":"m1-ble-debug-v1"}\n',
            {
                "Authorization": f"Bearer {pair_body['uploadToken']}",
                "Content-Type": "application/x-ndjson",
            },
        )

        self.assertEqual(400, status)

    def pair(self):
        return self.request(
            "POST",
            "/pair",
            {"pairingPassword": "correct-password"},
            {"Content-Type": "application/json"},
        )

    def request(self, method, path, body, headers):
        connection = HTTPConnection(self.host, self.port, timeout=5)
        if isinstance(body, dict):
            body = json.dumps(body).encode("utf-8")
        connection.request(method, path, body=body, headers=headers)
        response = connection.getresponse()
        raw_body = response.read()
        connection.close()
        parsed_body = json.loads(raw_body.decode("utf-8")) if raw_body else {}
        return response.status, parsed_body


if __name__ == "__main__":
    unittest.main()
