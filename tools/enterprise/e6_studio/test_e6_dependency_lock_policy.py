"""Regression tests for E6 lockfile metadata policy. Does not require npm."""
import copy
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent / "next-studio" / "scripts"))
from validate_dependency_lock import validate


class LockfilePolicyTests(unittest.TestCase):
    def setUp(self):
        self.manifest = {
            "name": "test", "version": "0.1.0",
            "dependencies": {"react": "19.3.1"}, "devDependencies": {}
        }
        self.lock = {
            "name": "test", "version": "0.1.0",
            "lockfileVersion": 3, "requires": True,
            "packages": {
                "": {"name": "test", "version": "0.1.0",
                     "dependencies": {"react": "19.3.1"}, "devDependencies": {}},
                "node_modules/react": {
                    "version": "19.3.1",
                    "resolved": "https://registry.npmjs.org/react/-/react-19.3.1.tgz",
                    "integrity": "sha512-AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
                }
            }
        }

    def test_approved_pinned_package(self):
        self.assertEqual(validate(self.manifest, self.lock), 1)

    def test_refuse_changed_direct_dependency(self):
        altered = copy.deepcopy(self.lock)
        altered["packages"][""]["dependencies"]["react"] = "19.3.0"
        with self.assertRaisesRegex(ValueError, "mismatch"):
            validate(self.manifest, altered)

    def test_refuse_non_registry_or_unpinned_integrity(self):
        for attribute, value in (("resolved", "https://evil.example/react.tgz"),
                                 ("integrity", "sha1-AAAA")):
            altered = copy.deepcopy(self.lock)
            altered["packages"]["node_modules/react"][attribute] = value
            with self.subTest(attribute=attribute), self.assertRaises(ValueError):
                validate(self.manifest, altered)

    def test_refuse_ranges_and_symlinks(self):
        altered = copy.deepcopy(self.manifest)
        altered["dependencies"]["react"] = "^19.3.1"
        self.lock["packages"][""]["dependencies"]["react"] = "^19.3.1"
        with self.assertRaisesRegex(ValueError, "non-exact"):
            validate(altered, self.lock)
        self.lock["packages"][""]["dependencies"]["react"] = "19.3.1"
        self.lock["packages"]["node_modules/react"]["link"] = True
        with self.assertRaisesRegex(ValueError, "symlink"):
            validate(self.manifest, self.lock)


if __name__ == "__main__":
    unittest.main()
