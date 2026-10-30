"""No-network license provenance regression tests for the E6 CI evidence."""
import copy
import sys
import unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).parent / "next-studio" / "scripts"))
from report_dependency_licenses import build_report


class LicenseInventoryTests(unittest.TestCase):
    def setUp(self):
        self.lock = {"lockfileVersion": 3, "packages": {
            "": {"name": "research"},
            "node_modules/gsap": {"version": "3.15.0", "license": "Standard 'no charge' license: https://gsap.com/standard-license."},
            "node_modules/@img/sharp-libvips-linux-x64": {"version": "1.3.4", "license": "LGPL-3.0-or-later", "optional": True},
        }}
        self.bom = {"bomFormat": "CycloneDX", "specVersion": "1.5", "components": [
            {"name": "gsap", "version": "3.15.0"},
            {"name": "@img/sharp-libvips-linux-x64", "version": "1.3.4",
             "licenses": [{"license": {"id": "LGPL-3.0-or-later"}}]},
        ]}

    def test_reports_obligations_but_never_approves_release(self):
        report = build_report(self.lock, self.bom, b"test")
        self.assertEqual(report["lockedPackageCount"], 2)
        self.assertEqual(report["installedCiSbomComponentCount"], 2)
        self.assertEqual(report["missingSbomLicenseNames"], ["gsap"])
        self.assertEqual([p["name"] for p in report["reciprocalLicensePackages"]],
                         ["@img/sharp-libvips-linux-x64"])
        self.assertEqual([p["name"] for p in report["nonSpdxLockLicenses"]], ["gsap"])
        self.assertFalse(report["ownerReleaseApproval"])
        self.assertFalse(report["stage45Go"])

    def test_refuse_unlocked_or_wrong_version_component(self):
        for version in ("0.0.0", "3.15.1"):
            modified = copy.deepcopy(self.bom)
            modified["components"][0]["version"] = version
            with self.subTest(version=version), self.assertRaisesRegex(ValueError, "NOT_LOCKED"):
                build_report(self.lock, modified, b"test")

    def test_refuse_untrusted_sbom_format(self):
        modified = copy.deepcopy(self.bom)
        modified["bomFormat"] = "not-an-sbom"
        with self.assertRaisesRegex(ValueError, "SBOM_FORMAT"):
            build_report(self.lock, modified, b"test")

    def test_refuse_duplicate_components(self):
        modified = copy.deepcopy(self.bom)
        modified["components"].append(modified["components"][0])
        with self.assertRaisesRegex(ValueError, "DUPLICATE"):
            build_report(self.lock, modified, b"test")

    def test_missing_lock_license_flagged_not_silently_approved(self):
        modified = copy.deepcopy(self.lock)
        del modified["packages"]["node_modules/gsap"]["license"]
        report = build_report(modified, self.bom, b"test")
        self.assertEqual(report["missingLockLicenseNames"], ["gsap"])
        self.assertEqual(report["releaseStatus"], "REVIEW_REQUIRED")


if __name__ == "__main__":
    unittest.main()
