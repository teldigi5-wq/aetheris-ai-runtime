"""Negative-control suite; no Docker engine needed for unit policy tests."""
from __future__ import annotations

from copy import deepcopy
from pathlib import Path
import unittest

from locked_worker_inspection import validate_inspection


def specimen():
    return {
        "HostConfig": {
            "NetworkMode": "none", "ReadonlyRootfs": True, "Privileged": False,
            "Memory": 268435456, "MemorySwap": 268435456,
            "NanoCpus": 1000000000, "PidsLimit": 64,
            "CapDrop": ["ALL"], "CapAdd": None,
            "SecurityOpt": ["no-new-privileges"], "PidMode": "", "IpcMode": "private",
            "Devices": [], "PortBindings": {}, "PublishAllPorts": False,
            "Tmpfs": {"/tmp": "rw,noexec,nosuid,size=16m"},
        },
        "Config": {
            "User": "1001:127",
            "Env": ["PYTHONDONTWRITEBYTECODE=1", "PYTHONUNBUFFERED=1"],
        },
        "Mounts": [
            {"Destination": "/workspace", "Source": "/checkout",
             "Type": "bind", "RW": False},
            {"Destination": "/workspace/target/e3-factory",
             "Source": "/checkout/target/e3-factory",
             "Type": "bind", "RW": True},
        ],
    }


class DockerWorkerBoundaryTests(unittest.TestCase):
    def test_reviewed_worker_passes(self):
        validate_inspection(specimen())

    def test_denies_broad_repository_target_write(self):
        mock = specimen()
        mock["Mounts"][1]["Destination"] = "/workspace/target"
        with self.assertRaisesRegex(ValueError, "UNREVIEWED_MOUNT"):
            validate_inspection(mock)

    def test_denies_extra_mount_even_when_read_only(self):
        mock = specimen()
        mock["Mounts"].append({"Destination": "/root/.ssh", "RW": False,
                               "Source": "/home/user/.ssh", "Type": "bind"})
        with self.assertRaisesRegex(ValueError, "UNREVIEWED_MOUNT"):
            validate_inspection(mock)

    def test_denies_wrong_output_host_source(self):
        mock = specimen()
        mock["Mounts"][1]["Source"] = "/etc"
        with self.assertRaisesRegex(ValueError, "OUTPUT_BIND_SOURCE"):
            validate_inspection(mock)

    def test_denies_broader_privileges_and_network(self):
        changes = [
            ("NetworkMode", "host"), ("ReadonlyRootfs", False), ("Privileged", True),
            ("Memory", 0), ("MemorySwap", -1), ("NanoCpus", 0),
            ("PidsLimit", 0), ("CapDrop", []), ("CapAdd", ["SYS_ADMIN"]),
            ("SecurityOpt", []), ("PidMode", "host"), ("IpcMode", "host"),
            ("Devices", [{"PathOnHost": "/dev/sda"}]), ("PublishAllPorts", True),
            ("PortBindings", {"8090/tcp": [{"HostPort": "8090"}]}),
            ("Tmpfs", {"/tmp": "rw"}),
        ]
        for key, value in changes:
            with self.subTest(key=key):
                mock = specimen()
                mock["HostConfig"][key] = value
                with self.assertRaisesRegex(ValueError, "E3_WORKER_INSPECTION_"):
                    validate_inspection(mock)

    def test_accepts_expected_nonsecret_python_base_image_environment(self):
        mock = specimen()
        mock["Config"]["Env"].extend([
            "PATH=/usr/local/bin:/usr/bin", "LANG=C.UTF-8",
            "GPG_KEY=public-fingerprint", "PYTHON_VERSION=3.12.12",
            "PYTHON_SHA256=public-source-digest",
        ])
        validate_inspection(mock)
        mock["Config"]["Env"].append("PATH=/tmp/other")
        with self.assertRaisesRegex(ValueError, "UNREVIEWED_WORKER_ENVIRONMENT"):
            validate_inspection(mock)

    def test_denies_root_or_env_inheritance(self):
        for user in ("", "0:0", "0:127", "1001:0", "root"):
            mock = specimen()
            mock["Config"]["User"] = user
            with self.subTest(user=user), self.assertRaisesRegex(ValueError, "ROOT_OR_UNSPECIFIED_USER"):
                validate_inspection(mock)
        mock = specimen()
        mock["Config"]["Env"].append("AWS_SECRET_ACCESS_KEY=untrusted")
        with self.assertRaisesRegex(ValueError, "UNREVIEWED_WORKER_ENVIRONMENT"):
            validate_inspection(mock)

    def test_shell_source_is_narrowly_scoped(self):
        root = Path(__file__).resolve().parents[2]
        source = (root / "tools/enterprise/e3_factory/run_locked_worker.sh").read_text("utf-8")
        self.assertIn('src=$repo/target/e3-factory,dst=/workspace/target/e3-factory', source)
        self.assertNotIn('src=$repo/target,dst=/workspace/target"', source)
        self.assertIn('validate_inspection(container)', source)
        self.assertIn('  --network none', source)
        self.assertIn('  --read-only --cap-drop ALL', source)


if __name__ == "__main__":
    unittest.main()
