"""Review the *actual* Docker inspect result for the fixed CI E3 worker.

Not a generic shell/execution gateway, not an owner-PC approval. Called by the
existing fixed-phase Docker CI harness before starting its container.
"""
from __future__ import annotations

import re
from collections.abc import Mapping

READONLY = "/workspace"
WRITABLE = "/workspace/target/e3-factory"
CPU = 1_000_000_000
MEMORY = 256 * 1024 * 1024
PIDS = 64


def require(condition: bool, code: str) -> None:
    if not condition:
        raise ValueError("E3_WORKER_INSPECTION_" + code)


def validate_inspection(container: Mapping) -> None:
    """Reject incorrect or broadened Docker isolation from real inspect JSON."""
    require(isinstance(container, Mapping), "DOCUMENT_INVALID")
    host = container.get("HostConfig")
    cfg = container.get("Config")
    mounts = container.get("Mounts")
    require(isinstance(host, dict) and isinstance(cfg, dict) and isinstance(mounts, list),
            "FIELDS_INVALID")
    require(host.get("NetworkMode") == "none", "NETWORK_ENABLED")
    require(host.get("ReadonlyRootfs") is True, "ROOTFS_WRITABLE")
    require(host.get("Privileged") is False, "PRIVILEGED")
    require(host.get("Memory") == MEMORY and host.get("MemorySwap") == MEMORY,
            "MEMORY_LIMIT")
    require(host.get("NanoCpus") == CPU and host.get("PidsLimit") == PIDS,
            "CPU_PID_LIMIT")
    require(host.get("CapAdd") in (None, []), "EXTRA_CAPABILITIES")
    require("ALL" in (host.get("CapDrop") or []), "CAPABILITIES_NOT_DROPPED")
    require("no-new-privileges" in (host.get("SecurityOpt") or []),
            "NEW_PRIVILEGES_ALLOWED")
    require(host.get("PidMode") not in ("host",), "HOST_PID_NAMESPACE")
    require(host.get("IpcMode") not in ("host",), "HOST_IPC_NAMESPACE")
    require(not host.get("Devices"), "HOST_DEVICES")
    require(not host.get("PortBindings") and not host.get("PublishAllPorts"),
            "PORT_EXPOSURE")
    # Docker inspect user is UID:GID; neither may be root.
    require(bool(re.fullmatch(r"[1-9][0-9]*:[1-9][0-9]*", cfg.get("User", ""))),
            "ROOT_OR_UNSPECIFIED_USER")
    env = cfg.get("Env")
    require(isinstance(env, list), "UNREVIEWED_WORKER_ENVIRONMENT")
    # Docker images supply their own baseline values; Docker --env passes only
    # our two explicit flags. Permit known public Python base-image metadata,
    # but never inherit app secrets, startup hooks, proxies or script injection.
    approved_keys = {
        "PATH", "LANG", "GPG_KEY", "PYTHON_VERSION", "PYTHON_SHA256",
        "PYTHON_PIP_VERSION", "PYTHON_SETUPTOOLS_VERSION",
        "PYTHONDONTWRITEBYTECODE", "PYTHONUNBUFFERED",
    }
    parsed = {}
    for item in env:
        require(isinstance(item, str) and "=" in item, "UNREVIEWED_WORKER_ENVIRONMENT")
        key, value = item.split("=", 1)
        require(key in approved_keys and key not in parsed,
                "UNREVIEWED_WORKER_ENVIRONMENT")
        parsed[key] = value
    require(parsed.get("PYTHONDONTWRITEBYTECODE") == "1"
            and parsed.get("PYTHONUNBUFFERED") == "1",
            "UNREVIEWED_WORKER_ENVIRONMENT")
    tmpfs = (host.get("Tmpfs") or {}).get("/tmp", "")
    require(all(flag in tmpfs.split(",") for flag in ("rw", "noexec", "nosuid", "size=16m")),
            "TEMPORARY_FILESYSTEM")

    indexed = {}
    for item in mounts:
        require(isinstance(item, dict) and isinstance(item.get("Destination"), str),
                "MOUNT_INVALID")
        require(item["Destination"] not in indexed, "DUPLICATE_MOUNT")
        indexed[item["Destination"]] = item
    require(set(indexed) == {READONLY, WRITABLE}, "UNREVIEWED_MOUNT")
    require(indexed[READONLY].get("RW") is False, "REPOSITORY_MUST_BE_READONLY")
    require(indexed[WRITABLE].get("RW") is True, "FACTORY_OUTPUT_NOT_WRITABLE")
    require(indexed[READONLY].get("Type") == "bind"
            and indexed[WRITABLE].get("Type") == "bind", "MOUNT_TYPE")
    # Avoid a different host directory silently becoming the privileged output.
    require(indexed[WRITABLE].get("Source", "").replace("\\", "/").endswith("/target/e3-factory"),
            "OUTPUT_BIND_SOURCE")
