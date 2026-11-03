"""E6 W5-L: read-only, fail-closed exact Qwen2.5:0.5b local blob verification.

Validates manifest metadata AND every downloaded file's SHA-256 and exact size.
No inference, downloads, secrets, writes, or weight-execution attestations.
"""
import hashlib
import json
import os
from pathlib import Path
import re
import stat
import sys

MODEL = "qwen2.5:0.5b"
MANIFEST_RELATIVE = Path("manifests/registry.ollama.ai/library/qwen2.5/0.5b")
MANIFEST_TYPE = "application/vnd.docker.distribution.manifest.v2+json"
# Ollama registry manifest content: digest and sizes independently cross-checked.
CONFIG = ("application/vnd.docker.container.image.v1+json",
          "005f95c7475154a17e84b85cd497949d6dd2a4f9d77c096e3c66e4d9c32acaf5", 490)
LAYERS = (
    ("application/vnd.ollama.image.model",
     "c5396e06af294bd101b30dce59131a76d2b773e76950acc870eda801d3ab0515", 397807936),
    ("application/vnd.ollama.image.system",
     "66b9ea09bd5b7099cbb4fc820f31b575c0366fa439b08245566692c6784e281e", 68),
    ("application/vnd.ollama.image.template",
     "eb4402837c7829a690fa845de4d7f3fd842c2adee476d5341da8a46ea9255175", 1482),
    ("application/vnd.ollama.image.license",
     "832dd9e00a68dd83b3c3fb9f5588dad7dcf337a0db50f7d9483f310cd292e92e", 11343),
)


def reject(code):
    raise ValueError("E6_W5L_" + code)


def descriptor(entry):
    kind, digest, size = entry
    if (not isinstance(kind, str) or not re.fullmatch(r"[a-z0-9./+-]{1,100}", kind)
            or not isinstance(digest, str) or not re.fullmatch(r"[0-9a-f]{64}", digest)
            or not isinstance(size, int) or isinstance(size, bool)
            or size < 1 or size > 500_000_000):
        reject("PINNED_BLOB_DESCRIPTOR_INVALID")
    return {"mediaType": kind, "digest": "sha256:" + digest, "size": size}


def reject_duplicate_keys(pairs):
    value = {}
    for key, item in pairs:
        if key in value:
            reject("MANIFEST_DUPLICATE_JSON_KEY")
        value[key] = item
    return value


def read_manifest(root):
    path = root / MANIFEST_RELATIVE
    # Newer Ollama may store a manifest as an internal CAS symlink.
    try:
        canonical = path.resolve(strict=True)
        canonical.relative_to(root)
        if not canonical.is_file() or canonical.stat().st_size > 20000:
            reject("MANIFEST_PATH_OR_SIZE_UNTRUSTED")
        return json.loads(canonical.read_bytes().decode("utf-8"),
                          object_pairs_hook=reject_duplicate_keys)
    except (OSError, ValueError, UnicodeError, json.JSONDecodeError):
        reject("MANIFEST_PATH_OR_JSON_UNTRUSTED")


def verify_local_model(root, *, config=CONFIG, layers=LAYERS):
    """Inject tiny synthetic descriptors in unit tests; production CLI uses pinned defaults."""
    root = Path(root)
    if root.is_symlink() or not root.is_dir():
        reject("MODELS_ROOT_UNTRUSTED")
    root = root.resolve(strict=True)
    manifest = read_manifest(root)
    if (not isinstance(manifest, dict)
            or set(manifest) != {"schemaVersion", "mediaType", "config", "layers"}
            or manifest["schemaVersion"] != 2
            or manifest["mediaType"] != MANIFEST_TYPE
            or manifest["config"] != descriptor(config)
            or manifest["layers"] != [descriptor(x) for x in layers]):
        reject("MODEL_MANIFEST_NOT_PINNED")
    # Enforce every byte of the model, template, system, license and config.
    # Never trust a blob merely because its filename contains a digest.
    for entry in (config, *layers):
        _, expected, size = entry
        path = root / "blobs" / ("sha256-" + expected)
        try:
            fd = os.open(path, os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0))
        except OSError:
            reject("PINNED_BLOB_MISSING_OR_SYMLINK")
        with os.fdopen(fd, "rb") as stream:
            meta = os.fstat(stream.fileno())
            if not stat.S_ISREG(meta.st_mode) or meta.st_size != size:
                reject("PINNED_BLOB_SIZE_INVALID")
            actual = hashlib.file_digest(stream, "sha256").hexdigest()
        if actual != expected:
            reject("PINNED_BLOB_HASH_MISMATCH")
    return {
        "kind": "E6_W5L_LOCAL_PINNED_MODEL_BLOBS_CHECK_V1",
        "modelTag": MODEL,
        "modelFileSha256": layers[0][1],
        "manifestDescriptorsMatched": True,
        "allFiveLocalBlobsSha256Matched": True,
        "modelWeightsActuallyExecutedAttested": False,
        "modelProviderAuthenticated": False,
        "releaseGo": False,
        "stage45Go": False,
    }


def main():
    if len(sys.argv) != 1:
        reject("NO_EXTERNAL_MODEL_OR_FILE_ARGUMENTS")
    runner = os.environ.get("RUNNER_TEMP", "")
    expected_runner = "/home/runner/work/_temp"
    if runner != expected_runner or os.environ.get("GITHUB_ACTIONS") != "true":
        reject("EPHEMERAL_GITHUB_RUNNER_REQUIRED")
    models_root = os.environ.get("OLLAMA_MODELS", "")
    if models_root != expected_runner + "/w5l-ollama-models":
        reject("FIXED_CPU_MODELS_ROOT_REQUIRED")
    result = verify_local_model(models_root)
    print(result["kind"] + " MODEL_BLOBS_MATCH; NO_MODEL_EXECUTION_ATTESTATION")


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        code = str(error)
        if not re.fullmatch(r"E6_W5L_[A-Z0-9_]{1,96}", code):
            code = "E6_W5L_MODEL_PIN_CHECK_FAILED"
        print(code, file=sys.stderr)
        sys.exit(1)
