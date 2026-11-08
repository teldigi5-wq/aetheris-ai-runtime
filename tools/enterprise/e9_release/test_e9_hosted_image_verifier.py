"""E9 real archive stream/labels release-denial tests, Python stdlib only."""
from __future__ import annotations

import copy
import hashlib
import io
import json
import os
from pathlib import Path
import subprocess
import sys
import tarfile
import tempfile
import unittest
from unittest import mock

import e9_hosted_image_verifier as e9

SHA = "a" * 40
TAG = e9.IMAGE + ":" + SHA
ARCHIVE = e9.IMAGE + "-" + SHA + ".tar.gz"


def _blob(value: object) -> bytes:
    return json.dumps(value, sort_keys=True, separators=(",", ":")).encode("utf-8")


def fixture(root: Path, *, malicious_name: str | None = None,
            wrong_label: bool = False, duplicate_manifest: bool = False):
    labels = {
        "org.opencontainers.image.revision": SHA if not wrong_label else "b" * 40,
        "org.opencontainers.image.source": "https://github.com/" + e9.REPOSITORY,
        "io.aetheris.evidence-class": "HOSTED_RUNTIME_ARTIFACT"
    }
    config = _blob({"config": {"User": "10001:10001", "Labels": labels}})
    image_id = "sha256:" + hashlib.sha256(config).hexdigest()
    config_name = image_id[7:] + ".json"
    layer = "ab" * 32 + "/layer.tar"
    manifest = _blob([{
        "Config": config_name,
        "RepoTags": [TAG],
        "Layers": [layer]
    }])
    path = root / ARCHIVE
    with tarfile.open(path, "w:gz") as tar:
        for name, content in [
            ("manifest.json", manifest),
            (config_name, config),
            (layer, b"real synthetic layer bytes")
        ] + ([(malicious_name, b"attack")] if malicious_name else []) + (
                [("manifest.json", manifest)] if duplicate_manifest else []):
            info = tarfile.TarInfo(name)
            info.size = len(content)
            info.mode = 0o600
            tar.addfile(info, io.BytesIO(content))
    digest = hashlib.sha256(path.read_bytes()).hexdigest()
    (root / (ARCHIVE + ".sha256")).write_text(
        digest + "  " + ARCHIVE + "\n", encoding="ascii")
    (root / "runtime-image-id.txt").write_text(image_id + "\n", encoding="ascii")
    (root / "runtime-workspace-write-probe.txt").write_text("PASS\n", encoding="ascii")
    metadata = {
        "schema_version": 1, "status": "PASS",
        "evidence_class": "HOSTED_RUNTIME_ARTIFACT",
        "repository": e9.REPOSITORY, "revision": SHA,
        "image_tag": TAG, "image_id": image_id,
        "archive": ARCHIVE, "archive_sha256": digest,
        "release_tag": "runtime-" + SHA,
        "release_publication_expected": False,
        "release_authorized": False,
        "owner_release_authorization_verified": False,
        "stage45Go": False, "runtime_user": "10001:10001",
        "workspace_write_probe": True, "registry_publication_claim": False,
        "production_deployment_claim": False, "physical_pc_validation": False,
        "truth_boundary": "Hosted fixture, not real owner proof",
    }
    (root / "runtime-image-provenance.json").write_bytes(_blob(metadata))
    return metadata


class HostedImagePreflightTests(unittest.TestCase):
    def setUp(self):
        self._dir = tempfile.TemporaryDirectory()
        self.addCleanup(self._dir.cleanup)
        self.root = Path(self._dir.name)
        self.report = fixture(self.root)

    def _write_meta(self, data):
        (self.root / "runtime-image-provenance.json").write_bytes(_blob(data))

    def test_hosted_archive_manifest_id_labels_and_digest_verified(self):
        result = e9.verify(str(self.root), SHA)
        self.assertEqual(result["status"], "HOSTED_IMAGE_INTEGRITY_VERIFIED_RELEASE_NO_GO")
        self.assertEqual(result["revision"], SHA)
        self.assertEqual(result["checks"]["layerCount"], 1)
        self.assertTrue(result["checks"]["imageConfigDigestMatched"])
        self.assertTrue(result["checks"]["nonRootUserVerified"])
        self.assertFalse(result["releaseAuthorized"])
        self.assertFalse(result["stage45Go"])
        self.assertFalse(result["completeSbomVerified"])
        self.assertFalse(result["independentVulnerabilityScanPerformed"])
        self.assertTrue(result["selfReportedOriginIsNotCryptographicAttestation"])

    def test_default_is_plan_only_and_never_reads_missing_files(self):
        cli = Path(e9.__file__)
        result = subprocess.run([sys.executable, str(cli),
             "--artifact-dir", "/nonexistent/e9-proof", "--revision", SHA],
             capture_output=True, text=True, timeout=10)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(json.loads(result.stdout)["status"],
                         "PLAN_ONLY_NO_FILES_OR_IMAGE_ACCESSED")

    def test_cli_verification_returns_nonrelease_result(self):
        result = subprocess.run([sys.executable, str(Path(e9.__file__)),
             "--verify-local", "--artifact-dir", str(self.root),
             "--revision", SHA], capture_output=True, text=True, timeout=20)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertFalse(json.loads(result.stdout)["releaseAuthorized"])

    def test_archive_checksum_corruption_fails_closed(self):
        with (self.root / ARCHIVE).open("ab") as out:
            out.write(b"corrupted archive")
        with self.assertRaisesRegex(e9.Denied, "CHECKSUM"):
            e9.verify(str(self.root), SHA)

    def test_meta_claims_cannot_turn_image_pass_into_release(self):
        for key, value in [
            ("release_publication_expected", True),
            ("release_authorized", True),
            ("owner_release_authorization_verified", True),
            ("stage45Go", True), ("production_deployment_claim", True),
            ("physical_pc_validation", True),
            ("repository", "other/private-repo"),
            ("runtime_user", "root")
        ]:
            with self.subTest(key=key):
                bad = {**self.report, key: value}
                self._write_meta(bad)
                with self.assertRaises(e9.Denied):
                    e9.verify(str(self.root), SHA)
        self._write_meta(self.report)

    def test_missing_extra_or_symlinked_inputs_fail(self):
        (self.root / "unexpected.txt").write_text("synthetic")
        with self.assertRaisesRegex(e9.Denied, "EXACT"):
            e9.verify(str(self.root), SHA)
        (self.root / "unexpected.txt").unlink()
        target = self.root / "runtime-image-id.txt"
        src = target.read_bytes()
        target.unlink()
        try:
            target.symlink_to(self.root / "runtime-workspace-write-probe.txt")
        except (OSError, NotImplementedError):
            target.write_bytes(src)
            self.skipTest("Host forbids symlink creation")
        with self.assertRaises(e9.Denied):
            e9.verify(str(self.root), SHA)

    def test_traversal_and_duplicate_members_rejected(self):
        for name, duplicate in [
            ("../private_credentials.txt", False),
            ("C:\\windows\\metadata", False),
            (None, True)
        ]:
            with self.subTest(name=name, duplicate=duplicate):
                fixture(self.root, malicious_name=name,
                        duplicate_manifest=duplicate)
                with self.assertRaises(e9.Denied):
                    e9.verify(str(self.root), SHA)

    def test_docker_config_label_change_detected_even_when_metadata_rewritten(self):
        fixture(self.root, wrong_label=True)
        with self.assertRaisesRegex(e9.Denied, "DOCKER_CONFIG_POLICY"):
            e9.verify(str(self.root), SHA)

    def test_revision_mismatch_and_missing_probe_denied(self):
        with self.assertRaisesRegex(e9.Denied, "INVALID_REVISION"):
            e9.verify(str(self.root), "main")
        (self.root / "runtime-workspace-write-probe.txt").write_text("SKIP\n")
        with self.assertRaisesRegex(e9.Denied, "PROBE"):
            e9.verify(str(self.root), SHA)

    def test_inconsistent_image_id_and_manifest_denied(self):
        image_id = self.root / "runtime-image-id.txt"
        image_id.write_text("sha256:" + "f" * 64 + "\n", encoding="ascii")
        bad = {**self.report, "image_id": "sha256:" + "f" * 64}
        self._write_meta(bad)
        with self.assertRaisesRegex(e9.Denied, "DIGEST_MISMATCH"):
            e9.verify(str(self.root), SHA)

    def test_unchanged_workflow_forbids_publication_and_privileged_token(self):
        source = (Path(e9.__file__).resolve().parents[3] /
                  ".github/workflows/ai-runtime-image-provenance.yml")
        workflow = source.read_text(encoding="utf-8")
        self.assertIn("contents: read", workflow)
        self.assertNotIn("contents: write", workflow)
        self.assertNotIn("gh release create", workflow)
        self.assertNotIn("release upload", workflow)
        self.assertIn("'release_publication_expected': False", workflow)
        self.assertIn("'release_authorized': False", workflow)
        self.assertIn("'owner_release_authorization_verified': False", workflow)
        self.assertIn("'stage45Go': False", workflow)
        self.assertIn("docker save", workflow)
        self.assertIn("docker load", workflow)
        self.assertIn("actions/upload-artifact", workflow)


if __name__ == "__main__":
    unittest.main()
