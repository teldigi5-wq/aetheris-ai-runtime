"""Stage 46.20 extended PR-chain review adversarial tests."""
import json
import unittest
from pathlib import Path

from stage46_integration_chain_review import (
    assess, EXPECTED_PR85_HEAD, EXPECTED_PR85_SHA,
)

MANIFEST = Path(__file__).resolve().parents[2] / "docs/runtime/stage46-draft-chain-integrity-manifest.json"


def fixtures():
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    earlier = [
        {**{key: item[key] for key in ("number","head","base","head_sha","base_sha")},
         "state": "open", "draft": True, "merged": False}
        for item in manifest["chain"]
    ]
    last = manifest["chain"][-1]
    pr85 = {
        "number": 85,
        "head": EXPECTED_PR85_HEAD,
        "head_sha": EXPECTED_PR85_SHA,
        "base": last["head"],
        "base_sha": last["head_sha"],
        "state": "open", "draft": True, "merged": False,
    }
    return manifest, earlier, pr85


def check(m, prior, pr85, **changes):
    params = dict(current_base=EXPECTED_PR85_HEAD, current_base_sha=EXPECTED_PR85_SHA)
    params.update(changes)
    return assess(m, prior, pr85, **params)


class Stage4620IntegrationChainReviewTest(unittest.TestCase):
    def assert_no_release(self, result):
        self.assertEqual(result["release_decision"], "NO_GO")
        self.assertIs(result["production_authorized"], False)
        self.assertIs(result["owner_pc_pilot_authorized"], False)

    def test_verified_fourteen_draft_prs_are_still_nonreleasable(self):
        m, live, p = fixtures()
        result = check(m, live, p)
        self.assertTrue(result["extended_chain_review_passed"])
        self.assertEqual(result["draft_count"], 14)
        self.assert_no_release(result)

    def test_pr85_merge_is_hard_reject(self):
        m, l, p = fixtures()
        p["merged"] = True
        self.assertFalse(check(m,l,p)["extended_chain_review_passed"])

    def test_pr85_ready_for_review_not_same_as_draft(self):
        m,l,p=fixtures()
        p["draft"] = False
        self.assertFalse(check(m,l,p)["extended_chain_review_passed"])

    def test_pr85_closed_rejected(self):
        m,l,p=fixtures()
        p["state"] = "closed"
        self.assertFalse(check(m,l,p)["extended_chain_review_passed"])

    def test_pr85_changed_head_sha_rejected(self):
        m,l,p=fixtures()
        p["head_sha"] = "e"*40
        self.assertFalse(check(m,l,p)["extended_chain_review_passed"])

    def test_previous_chain_changed_after_review_is_rejected(self):
        m,l,p=fixtures()
        l[9]["head_sha"] = "a"*40
        self.assertFalse(check(m,l,p)["extended_chain_review_passed"])

    def test_pr85_retargeted_to_main_is_rejected(self):
        m,l,p=fixtures()
        p["base"] = "main"
        self.assertFalse(check(m,l,p)["extended_chain_review_passed"])

    def test_pr85_base_sha_drift_rejected(self):
        m,l,p=fixtures()
        p["base_sha"] = "a"*40
        self.assertFalse(check(m,l,p)["extended_chain_review_passed"])

    def test_stage4620_pr_base_head_mismatch_rejected(self):
        m,l,p=fixtures()
        self.assertFalse(check(m,l,p,current_base="main")["extended_chain_review_passed"])

    def test_stage4620_pr_base_sha_mismatch_rejected(self):
        m,l,p=fixtures()
        self.assertFalse(check(m,l,p,current_base_sha="0"*40)["extended_chain_review_passed"])

    def test_claimed_production_approval_rejected(self):
        m,l,p=fixtures()
        p["production_authorized"] = True
        r=check(m,l,p)
        self.assertFalse(r["extended_chain_review_passed"])
        self.assert_no_release(r)

    def test_boolean_pr_number_rejected(self):
        m,l,p=fixtures()
        p["number"] = True
        self.assertFalse(check(m,l,p)["extended_chain_review_passed"])

    def test_missing_prior_review_is_not_allowlisted(self):
        m,l,p=fixtures()
        l.pop()
        self.assertFalse(check(m,l,p)["extended_chain_review_passed"])

    def test_bad_manifest_never_authorizes(self):
        m,l,p=fixtures()
        m["release_decision"] = "GO"
        result=check(m,l,p)
        self.assertFalse(result["extended_chain_review_passed"])
        self.assert_no_release(result)

    def test_malformed_response_rejected(self):
        m,l,p=fixtures()
        for bad in (None,[],{},{"number":85}):
            self.assertFalse(check(m,l,bad)["extended_chain_review_passed"])


if __name__ == "__main__":
    unittest.main()
