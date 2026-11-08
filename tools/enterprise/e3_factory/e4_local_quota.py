#!/usr/bin/env python3
"""Local-only E4 UTC daily attempt ledger, reserved before any model request.

This is a best-effort operator-owned local budget, NOT owner authentication,
tamper-proof metering, cloud billing enforcement, or a multi-host quota.
All calls require an existing private local directory configured by the
operator. A missing/corrupt ledger fails closed, not silently reset.
"""
from __future__ import annotations

import json
import os
from pathlib import Path
import stat
import tempfile
from contextlib import contextmanager
from datetime import datetime, timezone

ROOT_ENV = "AETHERIS_E4_LOCAL_QUOTA_DIR"
MAX_DAILY_ATTEMPTS = 3
STATE_NAME = "local-attempt-budget-v1.json"
LOCK_NAME = ".local-attempt-budget-v1.lock"


class QuotaUnavailable(ValueError):
    """No safe operator budget store; do not contact the model."""


class QuotaExhausted(ValueError):
    """All UTC-day attempt slots already reserved."""


def _utc_day() -> str:
    return datetime.now(timezone.utc).date().isoformat()


def _operator_root() -> Path:
    supplied = os.environ.get(ROOT_ENV)
    if (not supplied or len(supplied) > 4096
            or not os.path.isabs(supplied)
            or ".." in Path(supplied).parts):
        raise QuotaUnavailable("E4_QUOTA_PRIVATE_OPERATOR_DIRECTORY_REQUIRED")
    root = Path(supplied)
    details = os.lstat(root)
    if not stat.S_ISDIR(details.st_mode) or root.is_symlink():
        raise QuotaUnavailable("E4_QUOTA_DIRECTORY_NOT_REGULAR")
    if os.name == "posix" and (
            details.st_uid != os.getuid()
            or stat.S_IMODE(details.st_mode) & 0o077):
        raise QuotaUnavailable("E4_QUOTA_DIRECTORY_NOT_PRIVATE")
    return root


def _safe_file(path: Path, flags: int) -> int:
    if path.is_symlink():
        raise QuotaUnavailable("E4_QUOTA_SYMLINK_DENIED")
    fd = os.open(path, flags | getattr(os, "O_NOFOLLOW", 0), 0o600)
    try:
        details = os.fstat(fd)
        if not stat.S_ISREG(details.st_mode) or details.st_nlink != 1:
            raise QuotaUnavailable("E4_QUOTA_NONREGULAR_FILE_DENIED")
        if os.name == "posix" and (
                details.st_uid != os.getuid()
                or stat.S_IMODE(details.st_mode) & 0o077):
            raise QuotaUnavailable("E4_QUOTA_FILE_NOT_PRIVATE")
        return fd
    except BaseException:
        os.close(fd)
        raise


@contextmanager
def _exclusive_lock(fd: int):
    if os.name == "nt":
        import msvcrt
        os.lseek(fd, 0, os.SEEK_SET)
        if os.fstat(fd).st_size == 0:
            os.write(fd, b"0")
        os.lseek(fd, 0, os.SEEK_SET)
        msvcrt.locking(fd, msvcrt.LK_LOCK, 1)
        try:
            yield
        finally:
            os.lseek(fd, 0, os.SEEK_SET)
            msvcrt.locking(fd, msvcrt.LK_UNLCK, 1)
    else:
        import fcntl
        fcntl.flock(fd, fcntl.LOCK_EX)
        try:
            yield
        finally:
            fcntl.flock(fd, fcntl.LOCK_UN)


def _load_state(path: Path) -> dict | None:
    try:
        fd = _safe_file(path, os.O_RDONLY)
    except FileNotFoundError:
        return None
    try:
        if os.fstat(fd).st_size > 1024:
            raise QuotaUnavailable("E4_QUOTA_STATE_TOO_LARGE")
        raw = os.read(fd, 1025)
    finally:
        os.close(fd)
    try:
        value = json.loads(raw)
        if (type(value) is not dict
                or set(value) != {"schema", "day", "used"}
                or type(value["schema"]) is not int or value["schema"] != 1
                or type(value["day"]) is not str or len(value["day"]) != 10
                or datetime.strptime(value["day"], "%Y-%m-%d").date().isoformat()
                    != value["day"]
                or type(value["used"]) is not int
                or not 1 <= value["used"] <= MAX_DAILY_ATTEMPTS):
            raise ValueError("invalid state")
    except (ValueError, TypeError, KeyError):
        raise QuotaUnavailable("E4_QUOTA_CORRUPT_STATE_DENIED") from None
    return value


def _write_state(root: Path, path: Path, value: dict) -> None:
    # Atomic replace under the independently held stable lock. A crash during
    # temp write leaves old state intact; no model call occurs on write error.
    temp_name = None
    try:
        fd, temp_name = tempfile.mkstemp(prefix=".e4-quota-", dir=root)
        with os.fdopen(fd, "wb") as out:
            payload = (json.dumps(value, sort_keys=True, separators=(",", ":"))
                       + "\n").encode("ascii")
            out.write(payload)
            out.flush()
            os.fsync(out.fileno())
        os.replace(temp_name, path)
        temp_name = None
        if os.name == "posix":
            dir_fd = os.open(root, os.O_RDONLY | getattr(os, "O_DIRECTORY", 0))
            try:
                os.fsync(dir_fd)
            finally:
                os.close(dir_fd)
    finally:
        if temp_name is not None:
            try:
                os.unlink(temp_name)
            except FileNotFoundError:
                pass


def reserve_local_attempt() -> int:
    """Return today's cumulative attempt number, else fail before networking.

    Locking is local-filesystem advisory cooperation only. Same-account
    tampering, clock manipulation, alternate roots and other host processes
    cannot be governed here; never interpret as a financial or security ACL.
    """
    try:
        root = _operator_root()
        lock_fd = _safe_file(root / LOCK_NAME, os.O_CREAT | os.O_RDWR)
        try:
            with _exclusive_lock(lock_fd):
                now = _utc_day()
                ledger_path = root / STATE_NAME
                old = _load_state(ledger_path)
                if old is not None and now < old["day"]:
                    raise QuotaUnavailable("E4_QUOTA_CLOCK_ROLLBACK_DENIED")
                used = old["used"] if old is not None and old["day"] == now else 0
                if used >= MAX_DAILY_ATTEMPTS:
                    raise QuotaExhausted("E4_SHARED_QUOTA_EXHAUSTED")
                next_count = used + 1
                _write_state(root, ledger_path,
                             {"schema": 1, "day": now, "used": next_count})
                return next_count
        finally:
            os.close(lock_fd)
    except (QuotaUnavailable, QuotaExhausted):
        raise
    except (OSError, UnicodeError, OverflowError):
        raise QuotaUnavailable("E4_QUOTA_STORE_UNAVAILABLE") from None
