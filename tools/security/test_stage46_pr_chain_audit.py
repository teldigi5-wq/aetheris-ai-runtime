"""Adversarial checks for live (but mocked) GitHub PR chain consistency."""
import copy
import json
import unittest
from pathlib import Path
from stage46_pr_chain_audit import audit

MANIFEST = (Path(__file__).resolve().parents[2] / "docs/runtime"
            / "stage46-draft-chain-integrity-manifest.json")


def fixture():
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    live = [
        {**{field: p[field] for field in (
            "number", "head", "base", "head_sha", "base_sha"
        )}, "state": "open", "draft": True, "merged": False}
        for p in manifest["chain"]
    ]
    return manifest, live


def run(manifest, live):
    last = manifest["chain"][-1]
    return audit(manifest, live, current_base=last["head"],
                 current_base_sha=last["head_sha"])


class Stage4619LiveDraftChainTest(unittest.TestCase):
    def no_approval(self, result):
        self.assertEqual(result["decision"], "NO_GO")
        self.assertIs(result["production_authorized"], False)
        self.assertIs(result["owner_pc_pilot_authorized"], False)

    def test_exact_thirteen_open_draft_unmerged_entries(self):
        m, l = fixture()
        r = run(m, l)
        self.assertTrue(r["chain_verified"])
        self.assertEqual(r["chain_count"], 13)
        self.assertEqual(r["pending_pr_numbers"], list(range(72, 85)))
        self.no_approval(r)

    def test_prior_pr_merged_is_a_hard_stop(self):
        m, l = fixture()
        l[8]["merged"] = True
        self.assertFalse(run(m, l)["chain_verified"])

    def test_closed_pull_request_is_rejected(self):
        m, l = fixture()
        l[4]["state"] = "closed"
        self.assertFalse(run(m, l)["chain_verified"])

    def test_ready_for_review_cannot_be_claimed_open_draft(self):
        m, l = fixture()
        l[11]["draft"] = False
        self.assertFalse(run(m, l)["chain_verified"])

    def test_updated_head_sha_rejected_until_reaudited(self):
        m, l = fixture()
        l[12]["head_sha"] = "a" * 40
        self.assertFalse(run(m, l)["chain_verified"])

    def test_retargeted_base_branch_rejected(self):
        m, l = fixture()
        l[5]["base"] = "main"
        self.assertFalse(run(m, l)["chain_verified"])

    def test_changed_base_commit_rejected(self):
        m, l = fixture()
        l[5]["base_sha"] = "b" * 40
        self.assertFalse(run(m, l)["chain_verified"])

    def test_manifest_cannot_disconnect_stack(self):
        m, l = fixture()
        m["chain"][9]["base"] = "main"
        self.assertFalse(run(m, l)["chain_verified"])

    def test_nonexistent_intermediate_pr_rejected(self):
        m, l = fixture()
        l.pop(3)
        self.assertFalse(run(m, l)["chain_verified"])

    def test_duplicate_pr_or_branch_rejected(self):
        m, l = fixture()
        m["chain"][3]["head"] = m["chain"][2]["head"]
        self.assertFalse(run(m, l)["chain_verified"])

    def test_injected_release_authorization_rejected(self):
        m, l = fixture()
        m["production_authorized"] = True
        result = run(m, l)
        self.assertFalse(result["chain_verified"])
        self.no_approval(result)

    def test_new_pr_must_be_based_on_exact_last_open_draft_sha(self):
        m, l = fixture()
        wrong = audit(m, l, current_base=m["chain"][-1]["head"],
                      current_base_sha="f" * 40)
        self.assertFalse(wrong["chain_verified"])

    def test_new_pr_base_cannot_point_directly_to_main(self):
        m, l = fixture()
        wrong = audit(m, l, current_base="main",
                      current_base_sha=m["chain"][-1]["head_sha"])
        self.assertFalse(wrong["chain_verified"])

    def test_boolean_pr_number_or_schema_version_rejected(self):
        for mutation in ("number", "schema_version"):
            m, l = fixture()
            if mutation == "number":
                m["chain"][0]["number"] = True
            else:
                m["schema_version"] = True
            self.assertFalse(run(m, l)["chain_verified"])

    def test_manifest_cannot_self_enroll_extra_secret_fields(self):
        m, l = fixture()
        m["oauth_token"] = "DO_NOT_PRINT"
        self.assertFalse(run(m, l)["chain_verified"])

    def test_bad_sha_and_unexpected_branch_rejected(self):
        m, l = fixture()
        m["chain"][1]["head_sha"] = "bad"
        self.assertFalse(run(m, l)["chain_verified"])
        m, l = fixture()
        m["chain"][1]["head"] = "main"
        self.assertFalse(run(m, l)["chain_verified"])

    def test_missing_base_outside_chain_is_rejected(self):
        m, l = fixture()
        m["chain"][0]["base_sha"] = ""
        self.assertFalse(run(m, l)["chain_verified"])

    def test_forged_live_response_field_rejected(self):
        m, l = fixture()
        l[0]["forced_merge"] = True
        self.assertFalse(run(m, l)["chain_verified"])


if __name__ == "__main__":
    unittest.main()
