#!/usr/bin/env python3
"""E3.4 CI ONLY FAKE OLLAMA: fixture HTTP server, NOT LLM inference.

This process runs on an isolated GitHub Actions runner and is never launched
by the no-network worker. It must NOT be reported as a real AI-model call.
"""
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json

MODEL = "e34-ci-mock"
REPLY = '{"operation":"replace","old":"const p = 0.24;","new":"const p = progress();"}'


class FixtureModel(BaseHTTPRequestHandler):
    def do_POST(self):
        if self.path != "/api/generate":
            self.send_error(404)
            return
        if int(self.headers.get("Content-Length", "0")) > 36000:
            self.send_error(413)
            return
        data = self.rfile.read(int(self.headers.get("Content-Length", "0")))
        try:
            body = json.loads(data)
        except ValueError:
            self.send_error(400)
            return
        if (body.get("model") != MODEL or body.get("stream") is not False
                or "const p = 0.24;" not in str(body.get("prompt", ""))):
            self.send_error(400, "not the frozen CI fixture")
            return
        payload = json.dumps({"model": MODEL, "done": True, "response": REPLY},
                             separators=(",", ":")).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def log_message(self, *_):
        pass


if __name__ == "__main__":
    print("E34_MOCK_HTTP_FIXTURE_ONLY_NO_LLM_INFERENCE", flush=True)
    ThreadingHTTPServer(("127.0.0.1", 11434), FixtureModel).serve_forever()
