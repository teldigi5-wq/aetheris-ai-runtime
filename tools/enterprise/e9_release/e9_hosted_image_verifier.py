#!/usr/bin/env python3
"""E9 OFFLINE integrity preflight for an existing hosted Docker-save artifact.

The verifier makes NO owner approval, signer-authenticity, SBOM-completeness,
vulnerability-free, registry publication or production deployment claim.
Untrusted local files can all be forged together. It verifies their internal
consistency against actual archive bytes, Docker image config and OCI labels.
Default CLI is inert and never opens a file.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import stat
import sys
import tarfile

REPOSITORY = "teldigi5-wq/aetheris-ai-runtime"
IMAGE = "aetheris-ai-runtime"
SHA = re.compile(r"[0-9a-f]{40}\Z")
DIGEST = re.compile(r"[0-9a-f]{64}\Z")
IMAGE_ID = re.compile(r"sha256:([0-9a-f]{64})\Z")
MAX_ARCHIVE = 2_000_000_000
MAX_MEMBERS = 12000
MAX_JSON_BYTES = 2_000_000
MAX_META_BYTES = 12_000


class Denied(ValueError):
    """Untrusted, incomplete or contradictory hosted-image evidence."""


def deny(code: str):
    raise Denied("E9_" + code)


def _unique_pairs(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            deny("DUPLICATE_JSON_KEY")
        result[key] = value
    return result


def _json(raw: bytes) -> dict | list:
    try:
        return json.loads(raw.decode("utf-8", errors="strict"),
                          object_pairs_hook=_unique_pairs)
    except (UnicodeError, ValueError) as exc:
        raise Denied("E9_INVALID_JSON") from exc


def _root(folder: str) -> Path:
    if not isinstance(folder, str) or not os.path.isabs(folder):
        deny("ABSOLUTE_DIRECTORY_REQUIRED")
    path = Path(folder)
    info = path.lstat()
    if not stat.S_ISDIR(info.st_mode) or path.is_symlink():
        deny("UNSAFE_DIRECTORY")
    return path


def _open(path: Path, limit: int):
    # lstat + nofollow + same-inode handle verification prevents accidental
    # link traversals. It cannot defend malicious same-account path races on
    # all Windows filesystem / reparse-point implementations.
    before = path.lstat()
    if not stat.S_ISREG(before.st_mode) or path.is_symlink() or before.st_nlink != 1:
        deny("UNSAFE_ARTIFACT_FILE")
    if before.st_size <= 0 or before.st_size > limit:
        deny("ARTIFACT_FILE_BUDGET")
    fd = os.open(path, os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0))
    actual = os.fstat(fd)
    if (not stat.S_ISREG(actual.st_mode) or actual.st_nlink != 1
            or actual.st_size != before.st_size
            or actual.st_ino != before.st_ino
            or actual.st_dev != before.st_dev):
        os.close(fd)
        deny("ARTIFACT_FILE_RACE")
    return os.fdopen(fd, "rb")


def _read(path: Path, limit: int) -> bytes:
    with _open(path, limit) as f:
        raw = f.read(limit + 1)
    if len(raw) > limit:
        deny("ARTIFACT_FILE_BUDGET")
    return raw


def _digest_archive(path: Path) -> str:
    check = hashlib.sha256()
    with _open(path, MAX_ARCHIVE) as stream:
        for piece in iter(lambda: stream.read(1 << 20), b""):
            check.update(piece)
    return check.hexdigest()


def _safe_archive_name(name: str) -> None:
    if (not name or len(name) > 512 or "\\" in name or ":" in name
            or name.startswith("/") or "\x00" in name):
        deny("UNSAFE_TAR_MEMBER")
    parts = PurePosixPath(name).parts
    if not parts or any(p in ("", ".", "..") for p in parts):
        deny("UNSAFE_TAR_MEMBER")
    # Normalize before scanning, so '..' cannot be masked by pathlib collapse.
    if any(p in ("", ".", "..") for p in name.rstrip("/").split("/")):
        deny("UNSAFE_TAR_MEMBER")


def _docker_archive(path: Path, revision: str, image_id: str) -> dict:
    text_json = {}
    seen = set()
    files = set()
    count = 0
    with _open(path, MAX_ARCHIVE) as raw:
        try:
            with tarfile.open(fileobj=raw, mode="r|gz") as source:
                for entry in source:
                    count += 1
                    if count > MAX_MEMBERS:
                        deny("TAR_MEMBER_BUDGET")
                    _safe_archive_name(entry.name)
                    normalized = entry.name.rstrip("/")
                    if normalized in seen:
                        deny("DUPLICATE_TAR_MEMBER")
                    seen.add(normalized)
                    if entry.isdir():
                        continue
                    if not entry.isfile() or entry.size < 0:
                        deny("TAR_UNSAFE_ENTRY_TYPE")
                    files.add(normalized)
                    # Modern Docker 28+ uses an OCI-layout Docker save:
                    # Config and layers are blobs/sha256/<digest>, NOT
                    # legacy <digest>.json and <layer-id>/layer.tar. Verify
                    # every OCI blob's actual content hash while streaming;
                    # accepting the newer path without checking the digest
                    # would substantially weaken artifact integrity.
                    blob_name = re.fullmatch(r"blobs/sha256/([a-f0-9]{64})",
                                             normalized)
                    if blob_name:
                        stream = source.extractfile(entry)
                        if stream is None:
                            deny("OCI_BLOB_UNREADABLE")
                        digest = hashlib.sha256()
                        buffer = bytearray() if entry.size <= MAX_JSON_BYTES else None
                        for chunk in iter(lambda: stream.read(1 << 20), b""):
                            digest.update(chunk)
                            if buffer is not None:
                                buffer.extend(chunk)
                        if digest.hexdigest() != blob_name.group(1):
                            deny("OCI_BLOB_DIGEST_MISMATCH")
                        if buffer is not None:
                            text_json[normalized] = bytes(buffer)
                    elif normalized == "manifest.json" or normalized.endswith(".json"):
                        if entry.size > MAX_JSON_BYTES:
                            deny("TAR_JSON_TOO_LARGE")
                        member = source.extractfile(entry)
                        if member is None:
                            deny("TAR_MEMBER_UNREADABLE")
                        text_json[normalized] = member.read(MAX_JSON_BYTES + 1)
        except (tarfile.TarError, EOFError, OSError) as exc:
            raise Denied("E9_DOCKER_SAVE_UNREADABLE") from exc
    manifest = _json(text_json.get("manifest.json", b""))
    if not isinstance(manifest, list) or len(manifest) != 1:
        deny("DOCKER_MANIFEST_CARDINALITY")
    image = manifest[0]
    if not isinstance(image, dict):
        deny("DOCKER_MANIFEST_INVALID")
    config_name = image.get("Config")
    tag = IMAGE + ":" + revision
    is_legacy_config = isinstance(config_name, str) and bool(
        re.fullmatch(r"[a-f0-9]{64}\.json", config_name))
    is_oci_config = isinstance(config_name, str) and bool(
        re.fullmatch(r"blobs/sha256/[a-f0-9]{64}", config_name))
    if (not (is_legacy_config or is_oci_config)
            or image.get("RepoTags") != [tag]
            or not isinstance(image.get("Layers"), list)
            or not image["Layers"]
            or len(image["Layers"]) > 1200
            or len(set(image["Layers"])) != len(image["Layers"])):
        deny("DOCKER_IMAGE_IDENTITY")
    def valid_layer(item):
        if not isinstance(item, str) or item not in files:
            return False
        if is_oci_config:
            return bool(re.fullmatch(r"blobs/sha256/[a-f0-9]{64}", item))
        return item.endswith("/layer.tar")
    if not all(valid_layer(item) for item in image["Layers"]):
        deny("DOCKER_LAYER_REFERENCE")
    if is_oci_config:
        # OCI layout must not mix legacy and content-addressed layers.
        sources = image.get("LayerSources")
        if sources is not None:
            if not isinstance(sources, dict):
                deny("OCI_LAYER_SOURCES_INVALID")
            expected = {"sha256:" + layer.rsplit("/", 1)[-1]
                        for layer in image["Layers"]}
            if set(sources) != expected:
                deny("OCI_LAYER_SOURCES_MISMATCH")
            for digest_name, source_info in sources.items():
                if (not isinstance(source_info, dict) or
                        source_info.get("digest") != digest_name or
                        not isinstance(source_info.get("size"), int) or
                        source_info["size"] <= 0):
                    deny("OCI_LAYER_SOURCE_INVALID")
    if config_name not in files:
        deny("MISSING_DOCKER_CONFIG")
    cfg_bytes = text_json.get(config_name)
    if cfg_bytes is None or "sha256:" + hashlib.sha256(cfg_bytes).hexdigest() != image_id:
        deny("DOCKER_CONFIG_DIGEST_MISMATCH")
    config = _json(cfg_bytes)
    if not isinstance(config, dict) or not isinstance(config.get("config"), dict):
        deny("DOCKER_CONFIG_INVALID")
    labels = config["config"].get("Labels")
    if (not isinstance(labels, dict) or
            labels.get("org.opencontainers.image.revision") != revision or
            labels.get("org.opencontainers.image.source") !=
                "https://github.com/" + REPOSITORY or
            labels.get("io.aetheris.evidence-class") !=
                "HOSTED_RUNTIME_ARTIFACT" or
            config["config"].get("User") != "10001:10001"):
        deny("DOCKER_CONFIG_POLICY")
    return {"tag": tag, "imageConfigDigestMatched": True,
            "nonRootUserVerified": True, "sourceRevisionLabelVerified": True,
            "layerCount": len(image["Layers"]), "tarMembersChecked": count}


def verify(folder: str, revision: str) -> dict:
    if not isinstance(revision, str) or not SHA.fullmatch(revision):
        deny("INVALID_REVISION")
    root = _root(folder)
    archive = f"{IMAGE}-{revision}.tar.gz"
    names = {archive, archive + ".sha256", "runtime-image-id.txt",
             "runtime-workspace-write-probe.txt", "runtime-image-provenance.json"}
    if set(os.listdir(root)) != names:
        deny("EXACT_HOSTED_EVIDENCE_FILES_REQUIRED")
    for name in names:
        if (root / name).is_symlink():
            deny("ARTIFACT_SYMLINK_DENIED")
    meta = _json(_read(root / "runtime-image-provenance.json", MAX_META_BYTES))
    if not isinstance(meta, dict):
        deny("PROVENANCE_OBJECT_REQUIRED")
    image_id = _read(root / "runtime-image-id.txt", 256).decode("ascii").strip()
    if not IMAGE_ID.fullmatch(image_id):
        deny("INVALID_DOCKER_IMAGE_ID")
    check = _read(root / (archive + ".sha256"), 256).decode("ascii").strip()
    probe = _read(root / "runtime-workspace-write-probe.txt", 256).decode("ascii").strip()
    if probe != "PASS":
        deny("MISSING_NONROOT_WORKSPACE_PROBE")
    digest = _digest_archive(root / archive)
    if check != digest + "  " + archive:
        deny("ARCHIVE_CHECKSUM_MISMATCH")
    required = {
        "schema_version": 1, "status": "PASS",
        "evidence_class": "HOSTED_RUNTIME_ARTIFACT",
        "repository": REPOSITORY, "revision": revision,
        "image_tag": IMAGE + ":" + revision,
        "image_id": image_id, "archive": archive,
        "archive_sha256": digest,
        "release_tag": "runtime-" + revision,
        "release_publication_expected": False,
        "release_authorized": False,
        "owner_release_authorization_verified": False,
        "stage45Go": False,
        "runtime_user": "10001:10001",
        "workspace_write_probe": True,
        "registry_publication_claim": False,
        "production_deployment_claim": False,
        "physical_pc_validation": False,
    }
    if any(type(meta.get(k)) is not type(v) or meta.get(k) != v
           for k, v in required.items()):
        deny("PROVENANCE_CONTRADICTS_NO_GO")
    docker = _docker_archive(root / archive, revision, image_id)
    return {
        "status": "HOSTED_IMAGE_INTEGRITY_VERIFIED_RELEASE_NO_GO",
        "repository": REPOSITORY, "revision": revision,
        "artifactSha256": digest, "sourceImageId": image_id,
        "checks": docker,
        "selfReportedOriginIsNotCryptographicAttestation": True,
        "independentVulnerabilityScanPerformed": False,
        "completeSbomVerified": False,
        "hardwareAcceptanceVerified": False,
        "ownerApproved": False, "releaseAuthorized": False,
        "stage45Go": False, "productionDeploymentVerified": False,
    }


def main(argv=None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--verify-local", action="store_true")
    parser.add_argument("--artifact-dir")
    parser.add_argument("--revision")
    try:
        args = parser.parse_args(argv)
        if not args.verify_local:
            print(json.dumps({
                "status": "PLAN_ONLY_NO_FILES_OR_IMAGE_ACCESSED",
                "releaseAuthorized": False, "stage45Go": False
            }, sort_keys=True))
            return 0
        result = verify(args.artifact_dir, args.revision)
        print(json.dumps(result, sort_keys=True))
        return 0
    except (OSError, UnicodeError, Denied, ValueError, TypeError):
        print(json.dumps({
            "status": "E9_IMAGE_INTEGRITY_PREFLIGHT_DENIED",
            "releaseAuthorized": False, "stage45Go": False
        }, sort_keys=True))
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
