"""S-B62-A docs source-blob provenance; source checks only, not approval."""
import hashlib
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]
INVENTORY = ROOT / "docs/security/RUNTIME_B62_TRUST_BOUNDARY_SOURCE_INVENTORY_20261106.md"
ADR = ROOT / "docs/security/RUNTIME_B62_INDEPENDENT_FENCING_AUTHORITY_ADR_20261106.md"
DECISION = ROOT / "docs/security/RUNTIME_S_B62_A_FENCING_DEPLOYMENT_DECISION_20261106.md"

PINNED = {
    "orchestrator-service/src/main/java/io/aetheris/orchestrator/security/RuntimeUncertifiedApiHttpFilter.java": "d74d380e051dfb828baedcb6f920a0a760cda7bc",
    "orchestrator-service/src/main/java/io/aetheris/orchestrator/security/RuntimeSyntheticFencedEffectProbe.java": "091b4d21d1c571c87594894c19feb4c706f3dcd1",
    "orchestrator-service/src/main/java/io/aetheris/orchestrator/security/RuntimePinnedClosureAckVerifier.java": "ba1a263d2796021e7df50fafcf13d154768b8a43",
    "orchestrator-service/src/main/resources/application.properties": "dd6fbc95cd6f4c59dfa6980aca88fd69f29c116b",
}


class IndependentFencingSourceInventory(unittest.TestCase):
    def test_current_inventory_matches_real_source_git_blobs(self):
        text = INVENTORY.read_text(encoding="utf-8")
        start = text.index("<!-- S-B62-A CURRENT REVIEW SOURCE PINS START -->")
        end = text.index("<!-- S-B62-A CURRENT REVIEW SOURCE PINS END -->", start)
        table = text[start:end]
        for name, expected in PINNED.items():
            with self.subTest(name=name):
                self.assertIn("| `" + name + "` | `" + expected + "` |", table)
                data = (ROOT / name).read_bytes()
                actual = hashlib.sha1(
                    b"blob " + str(len(data)).encode("ascii") + bytes([0]) + data
                ).hexdigest()
                self.assertEqual(actual, expected, "source changed: review/re-pin inventory")

    def test_current_review_docs_preserve_no_go_and_provenance(self):
        for path in (ADR, INVENTORY, DECISION):
            with self.subTest(path=path.name):
                content = path.read_text(encoding="utf-8")
                self.assertIn("S-B62-A", content)
                self.assertIn("NO_GO", content)
                self.assertIn("HOLD", content)
        self.assertIn("NOT APPROVED", DECISION.read_text(encoding="utf-8"))
        self.assertIn("PROPOSED", ADR.read_text(encoding="utf-8"))
        self.assertIn("PARTIAL INVENTORY", INVENTORY.read_text(encoding="utf-8"))
        self.assertIn("without merging or reconciling the diverged code branches",
                      ADR.read_text(encoding="utf-8"))

    def test_local_links_and_cross_repo_paths_not_stale(self):
        decision = DECISION.read_text(encoding="utf-8")
        self.assertIn("](RUNTIME_B62_INDEPENDENT_FENCING_AUTHORITY_ADR_20261106.md)", decision)
        self.assertIn("](RUNTIME_B62_TRUST_BOUNDARY_SOURCE_INVENTORY_20261106.md)", decision)
        inventory = INVENTORY.read_text(encoding="utf-8")
        self.assertIn("io/aetheris/users/security/DirectApiJwtFilter.java", inventory)
        self.assertIn("io/aetheris/orchestrator/security/RuntimeUncertifiedApiHttpFilter.java", inventory)


if __name__ == "__main__":
    unittest.main()
