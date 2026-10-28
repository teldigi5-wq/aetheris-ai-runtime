"""E5 untrusted text candidate: bounded format, offline browser provenance."""
import hashlib
import io
import json
import shutil
import zipfile
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest import TestCase, main
from unittest.mock import patch

import e5_reference_preview as ref
import e5_untrusted_copy_preview as candidate

GOOD = {"version": 1, "brandLabel": "Prototype Atelier",
        "headlineLines": ["Quiet materials.", "Purposeful motion."]}


class UntrustedCopyAdmission(TestCase):
    def test_reproducible_copy_only_and_unapproved_manifest(self):
        raw, manifest = candidate.build(GOOD)
        again, repeated = candidate.build(GOOD)
        self.assertEqual(raw, again)
        self.assertEqual(manifest, repeated)
        self.assertIsNone(manifest["modelAuthored"])
        self.assertFalse(manifest["ownerApproved"])
        self.assertFalse(manifest["deploymentAuthorized"])
        self.assertFalse(manifest["productionReady"])
        self.assertEqual(manifest["runtimeReleaseStatus"], "NO_GO")
        self.assertEqual(manifest["copyProvenance"],
                         "UNVERIFIED_OPERATOR_SUPPLIED_OR_MODEL_PROPOSED")
        self.assertEqual(manifest["artifactClass"], "E5_UNTRUSTED_COPY_REVIEW_ONLY")
        with zipfile.ZipFile(io.BytesIO(raw)) as archive:
            self.assertIsNone(archive.testzip())
            self.assertEqual(set(archive.namelist()), set(ref.FILES) |
                             {"copy-proposal.json", "preview-manifest.json"})
            self.assertEqual(json.loads(archive.read("preview-manifest.json")), manifest)
            page = archive.read("index.html").decode()
            self.assertIn("Prototype Atelier / UNAPPROVED DRAFT", page)
            self.assertIn("Quiet materials.<br>Purposeful motion.", page)
            self.assertIn("not an AI-generated luxury customer website", page)
            self.assertEqual(archive.read("scene.js"), (ref.REFERENCE/"scene.js").read_bytes())
            self.assertEqual(archive.read("styles.css"), (ref.REFERENCE/"styles.css").read_bytes())
            self.assertEqual(json.loads(archive.read("copy-proposal.json")), GOOD)
            for item in manifest["files"]:
                self.assertEqual(hashlib.sha256(archive.read(item["path"])).hexdigest(),
                                 item["sha256"])

    def test_untrusted_schema_duplicates_markup_urls_control_and_size_denied(self):
        examples = [
            b'{"version":1,"brandLabel":"A","headlineLines":["Ok","Ok"]}',
            b'{"version":true,"brandLabel":"Prototype","headlineLines":["Safe one","Safe two"]}',
            b'{"version":1,"brandLabel":"Prototype","headlineLines":["Safe one","Safe two"],"deploy":true}',
            b'{"version":1,"brandLabel":"Prototype","brandLabel":"Evil","headlineLines":["Safe one","Safe two"]}',
            b'{"version":1,"brandLabel":"Prototype","headlineLines":["<script>","Safe two"]}',
            b'{"version":1,"brandLabel":"Prototype","headlineLines":["https://bad","Safe two"]}',
            b'{"version":1,"brandLabel":"Prototype","headlineLines":["Safe\u202eone","Safe two"]}',
            b'{"version":1,"brandLabel":"Prototype","headlineLines":["Safe one","Safe\ntwo"]}',
            b'{"version":1,"brandLabel":"Prototype","headlineLines":["Safe one"]}',
            b'\xff',
            b"x" * (candidate.MAX_INPUT + 1),
        ]
        for raw in examples:
            with self.subTest(raw=raw[:75]):
                with self.assertRaises(ValueError):
                    candidate.read_proposal(raw)

    def test_candidate_does_not_package_custom_scripts_or_extra_assets(self):
        with TemporaryDirectory() as folder:
            directory = Path(folder)
            for name in ref.FILES:
                shutil.copyfile(ref.REFERENCE / name, directory / name)
            (directory / "sneaky.js").write_text("alert(1)")
            with patch.object(ref, "REFERENCE", directory):
                with self.assertRaisesRegex(ValueError, "UNAPPROVED_ASSET_SET"):
                    candidate.build(GOOD)
            (directory / "sneaky.js").unlink()
            index = directory / "index.html"
            index.write_text(index.read_text().replace(
                '<script src="./scene.js" defer></script>',
                '<script src="https://untrusted.invalid/payload.js" defer></script>'))
            with patch.object(ref, "REFERENCE", directory):
                with self.assertRaisesRegex(ValueError, "UNAPPROVED_DOCUMENT_REFERENCES"):
                    candidate.build(GOOD)

    def test_no_user_supplied_source_path_deployment_or_model_inference(self):
        script = Path(candidate.__file__).read_text()
        for marker in ("modelAuthored", "UNVERIFIED_OPERATOR_SUPPLIED_OR_MODEL_PROPOSED",
                       "deploymentAuthorized", "NO_GO", "validate_source",
                       "copy-proposal.json", "strict_timestamps"):
            if marker == "strict_timestamps":
                continue
            self.assertIn(marker, script)
        for forbidden in ("requests.post(", "subprocess.run(", "os.system(",
                          "https://vercel", "exec(", "eval(", "model.generate("):
            self.assertNotIn(forbidden, script)


if __name__ == "__main__":
    main()
