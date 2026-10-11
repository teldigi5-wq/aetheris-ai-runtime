"""No real model, network, GPU, filesystem outside temporary test dirs."""
import json
import tempfile
import unittest
from pathlib import Path

from media_studio.comfy_client import (
    ComfyMediaClient, ImageJob, LoopbackTransport, MediaDisabled,
    MediaSettings, MediaTransportError, image_workflow,
)

ID = "123e4567-e89b-12d3-a456-426614174000"
CHECKPOINT = "sd15-local.safetensors"


class FakeLocalComfy:
    def __init__(self):
        self.calls = []
        self.pending = False
        self.history = {}
        self.image = b"\x89PNG\r\n\x1a\n" + b"some fake png test bytes"

    def request(self, path, *, method="GET", payload=None, max_bytes=2_097_152):
        self.calls.append((path, method, payload))
        if path == "/queue":
            return json.dumps({"queue_running": [[1]] if self.pending else [], "queue_pending": []}).encode()
        if path == "/prompt":
            return json.dumps({"prompt_id": ID}).encode()
        if path.startswith("/history/"):
            return json.dumps(self.history).encode()
        if path.startswith("/view?"):
            return self.image
        raise AssertionError("Unexpected HTTP request: " + path)


def settings(enabled=True):
    return MediaSettings(enabled=enabled, allowed_checkpoints=(CHECKPOINT,))


class MediaBoundaryTests(unittest.TestCase):
    def setUp(self):
        self.fake = FakeLocalComfy()

    def client(self, cfg=None):
        return ComfyMediaClient(cfg or settings(), self.fake)

    def test_default_config_fails_closed(self):
        self.assertFalse(MediaSettings().enabled)
        self.assertEqual((), MediaSettings().allowed_checkpoints)
        with self.assertRaises(MediaDisabled):
            self.client(settings(enabled=False)).queue_image(ImageJob("test", CHECKPOINT))
        self.assertEqual([], self.fake.calls)

    def test_external_and_ambiguous_urls_rejected(self):
        urls = (
            "https://api.example.com:443", "http://192.168.1.9:8188",
            "http://localhost:8188", "http://127.0.0.1:8188/evil",
            "http://127.0.0.1:8188?key=secret", "http://127.0.0.1:8188@evil.com",
            "http://127.0.0.1", "http://127.0.0.1:8188#frag",
        )
        for url in urls:
            with self.subTest(url=url), self.assertRaises(ValueError):
                MediaSettings(endpoint=url).validate()

    def test_transport_never_trusts_proxy_or_redirects(self):
        transport = LoopbackTransport("http://127.0.0.1:8188")
        self.assertEqual("http://127.0.0.1:8188", transport.endpoint)
        with self.assertRaises(ValueError):
            transport.request("//evil.example")

    def test_arbitrary_checkpoint_and_resource_exhaustion_rejected(self):
        for job in (
            ImageJob("test", "different.safetensors"),
            ImageJob("test", "../sd15-local.safetensors"),
            ImageJob("test", CHECKPOINT, width=8192),
            ImageJob("test", CHECKPOINT, width=513),
            ImageJob("test", CHECKPOINT, steps=100),
            ImageJob("test", CHECKPOINT, seed=-1),
            ImageJob("test", CHECKPOINT, cfg=float("inf")),
            ImageJob("x" * 2001, CHECKPOINT),
            ImageJob("test", CHECKPOINT, negative="x" * 1001),
        ):
            with self.subTest(job=job), self.assertRaises(ValueError):
                self.client().queue_image(job)
        self.assertEqual([], self.fake.calls)

    def test_exact_image_graph_only_and_no_cloud_fallback(self):
        client = self.client()
        job = ImageJob("cozy mountain landscape", CHECKPOINT, "blur", 512, 512, 18, 42, 6.5)
        returned = client.queue_image(job)
        self.assertEqual(ID, returned)
        self.assertEqual(["/queue", "/prompt"], [x[0] for x in self.fake.calls])
        payload = json.loads(self.fake.calls[1][2])
        graph = payload["prompt"]
        self.assertEqual({"3", "4", "5", "6", "7", "8", "9"}, set(graph))
        self.assertEqual("CheckpointLoaderSimple", graph["4"]["class_type"])
        self.assertEqual(CHECKPOINT, graph["4"]["inputs"]["ckpt_name"])
        self.assertEqual("cozy mountain landscape", graph["6"]["inputs"]["text"])
        self.assertEqual("blur", graph["7"]["inputs"]["text"])
        self.assertEqual(42, graph["3"]["inputs"]["seed"])
        self.assertEqual(1, graph["5"]["inputs"]["batch_size"])
        self.assertTrue(all(call[0].startswith("/") for call in self.fake.calls))
        self.assertNotIn("extra_data", payload)

    def test_media_queue_does_not_overload_existing_gpu_job(self):
        self.fake.pending = True
        with self.assertRaises(MediaDisabled):
            self.client().queue_image(ImageJob("test", CHECKPOINT))
        self.assertEqual(["/queue"], [c[0] for c in self.fake.calls])

    def test_queued_history_not_reported_as_completed(self):
        self.assertEqual([], self.client().image_outputs(ID))
        with self.assertRaises(ValueError):
            self.client().image_outputs("../../someotherjob")
        self.assertEqual(["/history/" + ID], [call[0] for call in self.fake.calls])

    def test_completed_image_must_be_safe_output(self):
        self.fake.history = {ID: {"outputs": {"9": {"images": [
            {"filename": "../private.png", "type": "output", "subfolder": ""},
            {"filename": "private.png", "type": "input", "subfolder": ""},
            {"filename": "private.png", "type": "output", "subfolder": "../private"},
            {"filename": "AetherisMedia_00001_.png", "type": "output", "subfolder": ""},
        ]}}}}
        self.assertEqual([{"filename": "AetherisMedia_00001_.png", "type": "output"}],
                         self.client().image_outputs(ID))

    def test_download_rejects_traversal_and_existing_destination(self):
        with tempfile.TemporaryDirectory() as path:
            out = Path(path) / "my-image.png"
            with self.assertRaises(ValueError):
                self.client().download_image("../secret.png", out)
            self.assertEqual(out, self.client().download_image("AetherisMedia_00001_.png", out))
            self.assertTrue(out.read_bytes().startswith(b"\x89PNG"))
            with self.assertRaises(FileExistsError):
                self.client().download_image("AetherisMedia_00001_.png", out)

    def test_nonimage_payload_rejected(self):
        self.fake.image = b"<html>error</html>"
        with tempfile.TemporaryDirectory() as path:
            with self.assertRaises(MediaTransportError):
                self.client().download_image("image.png", Path(path) / "test.png")


if __name__ == "__main__":
    unittest.main()
