#!/usr/bin/env python3
"""Bound redacted local S-B62-A operator receipts to their declared SHA-256.

Review-only integrity check: neither file contents nor hashes are an authenticated
operator observation or production admission. No network, subprocess, host scan,
or secrets collection. Runs on POSIX/WSL with safe directory-relative opens.
"""
import argparse
import hashlib
import hmac
import json
import os
from pathlib import Path
import stat
import sys

import runtime_b62_operator_fencing_preflight as inventory

MAX_RECEIPT_BYTES = 1_048_576
SUFFIX = ".evidence.txt"


def _open_checked_dir(directory):
    """Pin a local directory so later path substitutions cannot redirect reads."""
    if not all(hasattr(os, key) for key in ("O_DIRECTORY", "O_NOFOLLOW", "O_NONBLOCK")) \
            or os.open not in os.supports_dir_fd:
        raise inventory.InventoryError("secure directory-relative open unsupported")
    before = directory.lstat()
    if not stat.S_ISDIR(before.st_mode):
        raise inventory.InventoryError("receipt directory must not be a symlink")
    fd = os.open(directory, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW)
    opened = os.fstat(fd)
    if (opened.st_dev, opened.st_ino) != (before.st_dev, before.st_ino):
        os.close(fd)
        raise inventory.InventoryError("receipt directory changed during open")
    return fd


def _hash_checked_receipt(directory_fd, filename):
    """Hash bounded bytes from an exact regular, non-hardlinked file descriptor."""
    before = os.stat(filename, dir_fd=directory_fd, follow_symlinks=False)
    if not stat.S_ISREG(before.st_mode) or before.st_nlink != 1:
        raise inventory.InventoryError("receipt must be a single-link regular file")
    if before.st_size > MAX_RECEIPT_BYTES:
        raise inventory.InventoryError("receipt exceeds 1 MiB")
    flags = os.O_RDONLY | os.O_NONBLOCK | os.O_NOFOLLOW | getattr(os, "O_CLOEXEC", 0)
    fd = os.open(filename, flags, dir_fd=directory_fd)
    with os.fdopen(fd, "rb") as stream:
        opened = os.fstat(stream.fileno())
        if (opened.st_dev, opened.st_ino) != (before.st_dev, before.st_ino) \
                or not stat.S_ISREG(opened.st_mode) or opened.st_nlink != 1:
            raise inventory.InventoryError("receipt changed during open")
        if opened.st_size > MAX_RECEIPT_BYTES:
            raise inventory.InventoryError("receipt exceeds 1 MiB")
        payload = stream.read(MAX_RECEIPT_BYTES + 1)
        after = os.fstat(stream.fileno())
    if len(payload) > MAX_RECEIPT_BYTES:
        raise inventory.InventoryError("receipt exceeds 1 MiB")
    if (opened.st_size, opened.st_mtime_ns, opened.st_ctime_ns) != \
            (after.st_size, after.st_mtime_ns, after.st_ctime_ns):
        raise inventory.InventoryError("receipt changed during read")
    return hashlib.sha256(payload).hexdigest()


def review_receipts(manifest, receipt_dir):
    """Check local content-addressed bytes, never authenticate their origin."""
    inventory.preflight(manifest)  # Strict keys/shape, never authorizes GO.
    expected = {name + SUFFIX: data["evidence_sha256"]
                for name, data in manifest["checks"].items()
                if data["result"] != "NOT_RUN"}
    directory_fd = _open_checked_dir(Path(receipt_dir))
    try:
        names = set(os.listdir(directory_fd))
        if names != set(expected):
            raise inventory.InventoryError("missing or unexpected receipt files")
        for filename, required_hash in sorted(expected.items()):
            actual_hash = _hash_checked_receipt(directory_fd, filename)
            if not hmac.compare_digest(actual_hash, required_hash):
                raise inventory.InventoryError("receipt SHA-256 mismatch")
    finally:
        os.close(directory_fd)
    return {"local_receipt_hashes_match": bool(expected),
            "receipt_files_checked": len(expected),
            "claims_are_self_reported": True,
            "independent_review_verified": False,
            "physical_fencing_certified": False,
            "release_decision": "STAGE45_NO_GO", "xerj_publication": "HOLD"}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", required=True, type=Path)
    parser.add_argument("--receipts-dir", required=True, type=Path,
                        help="local REDACTED evidence only; never commit raw operator logs")
    args = parser.parse_args()
    try:
        manifest = inventory.parse_manifest(args.manifest)
        result = review_receipts(manifest, args.receipts_dir)
    except (inventory.InventoryError, OSError, TypeError, ValueError):
        # Do not print paths, file bytes, private metadata, or secrets.
        print(json.dumps({"local_receipt_hashes_match": False,
                          "release_decision": "STAGE45_NO_GO",
                          "xerj_publication": "HOLD",
                          "review": "INVALID_OR_UNVERIFIED"}, sort_keys=True))
        return 2
    print(json.dumps(result, sort_keys=True, indent=2))
    return 1  # A local digest match is NEVER an independent attestation.


if __name__ == "__main__":
    sys.exit(main())
