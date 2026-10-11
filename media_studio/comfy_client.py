"""Offline-first, opt-in ComfyUI media adapter. No hosted generation routes."""
from __future__ import annotations

import json
import os
import re
import secrets
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Protocol

MAX_RESPONSE_BYTES = 2 * 1024 * 1024
MAX_IMAGE_BYTES = 30 * 1024 * 1024
CHECKPOINT = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._ -]{0,127}\\.(?:safetensors|ckpt)$")
FILE = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._ -]{0,180}\\.(?:png|jpg|jpeg|webp)$", re.I)
PROMPT_ID = re.compile(r"^[a-f0-9-]{36}$", re.I)


class MediaDisabled(RuntimeError):
    """Operator has not explicitly enabled this local-only capability."""


class MediaTransportError(RuntimeError):
    """Local ComfyUI transport failed; never fall back to the cloud."""


@dataclass(frozen=True)
class MediaSettings:
    endpoint: str = "http://127.0.0.1:8188"
    enabled: bool = False
    allowed_checkpoints: tuple[str, ...] = ()

    @classmethod
    def from_environment(cls) -> "MediaSettings":
        enabled = os.getenv("AETHERIS_MEDIA_ENABLED", "false").lower() == "true"
        allowed = tuple(x.strip() for x in os.getenv("AETHERIS_MEDIA_ALLOWED_CHECKPOINTS", "").split(",") if x.strip())
        return cls(os.getenv("AETHERIS_COMFYUI_URL", "http://127.0.0.1:8188"), enabled, allowed)

    def validate(self) -> None:
        url = urllib.parse.urlsplit(self.endpoint)
        if (url.scheme != "http" or url.hostname != "127.0.0.1"
                or url.username or url.password or url.path not in ("", "/")
                or url.query or url.fragment or not url.port
                or not 1 <= url.port <= 65535):
            raise ValueError("ComfyUI must be an exact HTTP loopback endpoint with an explicit port")
        if len(self.allowed_checkpoints) != len(set(self.allowed_checkpoints)):
            raise ValueError("Duplicate checkpoints are forbidden")
        if any(not CHECKPOINT.fullmatch(x) or ".." in x for x in self.allowed_checkpoints):
            raise ValueError("Only explicit local checkpoint basenames may be allowlisted")


@dataclass(frozen=True)
class ImageJob:
    prompt: str
    checkpoint: str
    negative: str = ""
    width: int = 512
    height: int = 512
    steps: int = 20
    seed: int = 0
    cfg: float = 7.0

    def validate(self, settings: MediaSettings) -> None:
        if not settings.enabled:
            raise MediaDisabled("Image generation disabled until owner explicitly enables local media")
        settings.validate()
        if not settings.allowed_checkpoints or self.checkpoint not in settings.allowed_checkpoints:
            raise ValueError("Checkpoint must be explicitly allowlisted; never download models automatically")
        if (not isinstance(self.prompt, str) or not self.prompt.strip()
                or len(self.prompt) > 2000 or not isinstance(self.negative, str)
                or len(self.negative) > 1000):
            raise ValueError("Invalid prompt length")
        if any(type(v) is not int or v < 256 or v > 1024 or v % 64 for v in (self.width, self.height)):
            raise ValueError("Dimensions must be multiples of 64 from 256 to 1024")
        if type(self.steps) is not int or not 1 <= self.steps <= 35:
            raise ValueError("Steps must be 1–35")
        if type(self.seed) is not int or not 0 <= self.seed <= 2**32 - 1:
            raise ValueError("Seed must be a 32-bit unsigned integer")
        if isinstance(self.cfg, bool) or not isinstance(self.cfg, (int, float)) or not 1.0 <= self.cfg <= 12.0:
            raise ValueError("CFG must be within 1–12")


def image_workflow(job: ImageJob) -> dict[str, Any]:
    """Fixed built-in SD1.5-type graph based on the upstream ComfyUI API example."""
    return {
        "3": {"class_type": "KSampler", "inputs": {
            "cfg": job.cfg, "denoise": 1, "latent_image": ["5", 0],
            "model": ["4", 0], "negative": ["7", 0], "positive": ["6", 0],
            "sampler_name": "euler", "scheduler": "normal", "seed": job.seed,
            "steps": job.steps}},
        "4": {"class_type": "CheckpointLoaderSimple", "inputs": {"ckpt_name": job.checkpoint}},
        "5": {"class_type": "EmptyLatentImage", "inputs": {
            "batch_size": 1, "height": job.height, "width": job.width}},
        "6": {"class_type": "CLIPTextEncode", "inputs": {"clip": ["4", 1], "text": job.prompt}},
        "7": {"class_type": "CLIPTextEncode", "inputs": {"clip": ["4", 1], "text": job.negative}},
        "8": {"class_type": "VAEDecode", "inputs": {"samples": ["3", 0], "vae": ["4", 2]}},
        "9": {"class_type": "SaveImage", "inputs": {"filename_prefix": "AetherisMedia", "images": ["8", 0]}},
    }


class HttpTransport(Protocol):
    def request(self, path: str, *, method: str = "GET", payload: bytes | None = None,
                max_bytes: int = MAX_RESPONSE_BYTES) -> bytes: ...


class NoRedirects(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


class LoopbackTransport:
    def __init__(self, endpoint: str):
        settings = MediaSettings(endpoint=endpoint)
        settings.validate()
        self.endpoint = endpoint.rstrip("/")
        self.opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirects())

    def request(self, path: str, *, method: str = "GET", payload: bytes | None = None,
                max_bytes: int = MAX_RESPONSE_BYTES) -> bytes:
        if not path.startswith("/") or path.startswith("//") or "\\" in path:
            raise ValueError("Unsupported local ComfyUI path")
        request = urllib.request.Request(
            self.endpoint + path, data=payload, method=method,
            headers={"Content-Type": "application/json"} if payload is not None else {},
        )
        try:
            with self.opener.open(request, timeout=20) as response:
                if response.status != 200:
                    raise MediaTransportError("ComfyUI responded with an unexpected status")
                size = response.headers.get("Content-Length")
                if size and int(size) > max_bytes:
                    raise MediaTransportError("Local ComfyUI response exceeds size limit")
                data = response.read(max_bytes + 1)
                if len(data) > max_bytes:
                    raise MediaTransportError("Local ComfyUI response exceeds size limit")
                return data
        except (urllib.error.URLError, TimeoutError, OSError, ValueError) as exc:
            raise MediaTransportError("Local ComfyUI unavailable or response invalid") from exc


class ComfyMediaClient:
    def __init__(self, settings: MediaSettings, transport: HttpTransport | None = None):
        settings.validate()
        self.settings = settings
        self.transport = transport if transport is not None else LoopbackTransport(settings.endpoint)

    def _json(self, path: str, *, payload: dict | None = None) -> dict:
        data = json.dumps(payload, separators=(",", ":")).encode("utf-8") if payload is not None else None
        response = self.transport.request(path, method="POST" if data is not None else "GET", payload=data)
        try:
            value = json.loads(response)
        except (ValueError, UnicodeDecodeError) as exc:
            raise MediaTransportError("ComfyUI returned invalid JSON") from exc
        if not isinstance(value, dict):
            raise MediaTransportError("ComfyUI returned an unexpected JSON structure")
        return value

    def queue_image(self, job: ImageJob) -> str:
        job.validate(self.settings)
        # This is a best-effort concurrency limit: ComfyUI itself owns its GPU queue.
        queue = self._json("/queue")
        if queue.get("queue_running") or queue.get("queue_pending"):
            raise MediaDisabled("ComfyUI is already processing or waiting for another job")
        reply = self._json("/prompt", payload={
            "prompt": image_workflow(job),
            "client_id": "aetheris-media-" + secrets.token_hex(12),
        })
        prompt_id = reply.get("prompt_id")
        if not isinstance(prompt_id, str) or not PROMPT_ID.fullmatch(prompt_id):
            raise MediaTransportError("ComfyUI did not return a valid prompt identifier")
        return prompt_id

    def image_outputs(self, prompt_id: str) -> list[dict[str, str]]:
        if not self.settings.enabled:
            raise MediaDisabled("Media execution is disabled")
        if not PROMPT_ID.fullmatch(prompt_id):
            raise ValueError("Invalid prompt identifier")
        history = self._json("/history/" + prompt_id)
        item = history.get(prompt_id)
        if item is None:
            return []  # queued or no history available yet; not a success claim
        if not isinstance(item, dict):
            raise MediaTransportError("Unexpected ComfyUI history payload")
        if item.get("status", {}).get("status_str") == "error":
            raise MediaTransportError("ComfyUI reported job failure")
        outputs = item.get("outputs", {})
        if not isinstance(outputs, dict):
            raise MediaTransportError("Unexpected output structure")
        found: list[dict[str, str]] = []
        for group in outputs.values():
            if not isinstance(group, dict):
                continue
            for image in group.get("images", []):
                if not isinstance(image, dict):
                    continue
                name, folder, kind = image.get("filename"), image.get("subfolder"), image.get("type")
                if (isinstance(name, str) and FILE.fullmatch(name) and ".." not in name
                        and folder in ("", None) and kind == "output"):
                    found.append({"filename": name, "type": "output"})
        return found[:1]  # fixed batch_size=1; do not enumerate arbitrary outputs

    def download_image(self, filename: str, destination: Path) -> Path:
        if not self.settings.enabled:
            raise MediaDisabled("Media execution is disabled")
        if not isinstance(filename, str) or not FILE.fullmatch(filename) or ".." in filename:
            raise ValueError("Invalid image filename")
        if destination.suffix.lower() not in (".png", ".jpg", ".jpeg", ".webp"):
            raise ValueError("Output must be a local image file")
        if not destination.parent.is_dir():
            raise ValueError("Output directory does not exist")
        query = urllib.parse.urlencode({"filename": filename, "type": "output", "subfolder": ""})
        data = self.transport.request("/view?" + query, max_bytes=MAX_IMAGE_BYTES)
        # Validate signatures before exposing a generated artifact.
        if not (data.startswith(b"\\x89PNG\\r\\n\\x1a\\n") or
                data.startswith(b"\\xff\\xd8\\xff") or data.startswith(b"RIFF") and data[8:12] == b"WEBP"):
            raise MediaTransportError("ComfyUI returned non-image bytes")
        fd = os.open(destination, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        try:
            with os.fdopen(fd, "wb") as handle:
                handle.write(data)
        except BaseException:
            destination.unlink(missing_ok=True)
            raise
        return destination
