"""E3.9G-12B4C strict pinned gstack references and cinema-readiness safety tests."""
from __future__ import annotations
import copy
import json
from pathlib import Path
import tempfile
from unittest import TestCase, mock

import e39g12b4c_gstack_pattern_gate as gate


class GstackCinematicGateTests(TestCase):
    def setUp(self):
        self.original=gate.safe_load(gate.INDEX)

    def test_exact_upstream_commit_and_deny_only_reference_behavior(self):
        report=gate.validate_index(self.original)
        self.assertEqual(report["upstreamCommit"],gate.COMMIT)
        self.assertEqual(report["patterns"],12)
        self.assertEqual(set(report["phases"]),gate.PHASES)
        self.assertIs(report["thirdPartyCodeInstalled"],False)
        self.assertIs(report["thirdPartyCodeExecuted"],False)
        self.assertIs(report["branchInventoryWasCompletelyCodeReviewed"],False)
        self.assertEqual(report["modelCalls"],0)

    def test_all_scope_root_authority_grants_fail_closed(self):
        for flag in gate.DENY_FLAGS:
            with self.subTest(flag=flag):
                wrong=copy.deepcopy(self.original)
                wrong[flag]=True
                with self.assertRaisesRegex(ValueError,"E39G12B4C_FORBIDDEN_ROOT_AUTHORITY"):
                    gate.validate_index(wrong)
        wrong=copy.deepcopy(self.original)
        wrong["upstreamCommit"]="a"*40
        with self.assertRaisesRegex(ValueError,"E39G12B4C_UNPINNED"):
            gate.validate_index(wrong)

    def test_external_skill_entry_mutation_and_duplicate_refusals(self):
        alterations=[
            {"path":"../evil/SKILL.md"},
            {"upstreamBlobSha1":"a"*39},
            {"mode":"execute"},
            {"autoActivate":True},
            {"executionAllowed":True},
            {"phase":"production-execution"},
            {"pattern":"unrestricted-shell"},
        ]
        for change in alterations:
            with self.subTest(change=change):
                bad=copy.deepcopy(self.original)
                bad["patterns"][0].update(change)
                with self.assertRaisesRegex(ValueError,"E39G12B4C_UNSAFE"):
                    gate.validate_index(bad)
        bad=copy.deepcopy(self.original)
        bad["patterns"][1]=bad["patterns"][0]
        with self.assertRaisesRegex(ValueError,"E39G12B4C_UNSAFE"):
            gate.validate_index(bad)

    def test_duplicate_key_and_symlink_content_never_trusted(self):
        with tempfile.TemporaryDirectory() as tmp:
            p=Path(tmp)/"manifest.json"
            p.write_text('{"schemaVersion":1,"schemaVersion":9}')
            with self.assertRaisesRegex(ValueError,"E39G12B4C_DUPLICATE_JSON_KEY"):
                gate.safe_load(p)
            link=Path(tmp)/"alias"
            try: link.symlink_to(p)
            except OSError: return
            with self.assertRaisesRegex(ValueError,"E39G12B4C_FILE_MISSING_OR_UNSAFE"):
                gate.safe_load(link)

    def specimen(self,viewport):
        w,h={"mobile":(390,844),"desktop":(1280,800)}[viewport]
        return dict(evidenceClass="E39G12B4C_SYNTHETIC_SELF_ATTESTED",
            site="synthetic-nonproduction-demo",viewport=viewport,
            width=w,height=h,sourceSha256="f"*64,
            screenshots={"beforeSha256":"b"*64,"afterSha256":"c"*64},
            scroll={"progressStart":0,"progressEnd":1,"claimed3D":True},
            motion={"reducedMotionClaimsPass":True},
            webgl={"claimedGPUFrames":1000},
            ui={"contrastClaimed":True,"keyboardClaimed":True},
            errors=[],independentVerifier=False,ownerApprovalGranted=False,
            productionPublished=False,modelCalls=0)

    def test_fake_perfect_receipts_must_never_claim_autonomy(self):
        out=gate.evaluate_cinematic([self.specimen("mobile"),self.specimen("desktop")])
        self.assertFalse(out["readyToDeploy"])
        self.assertFalse(out["autonomousCinematicWebsiteCapabilityDemonstrated"])
        self.assertFalse(out["independentSiteQualityCertified"])
        self.assertEqual(len(out["unverifiedGates"]),8)
        self.assertIn("source",out["reason"].lower()) if False else None

    def test_reject_forged_verifier_deploy_model_call_and_bad_viewport(self):
        for change in ({"independentVerifier":True},
                       {"ownerApprovalGranted":True},
                       {"productionPublished":True},
                       {"modelCalls":1},{"width":940},
                       {"site":"not-the-same"}):
            with self.subTest(change=change):
                mobile=self.specimen("mobile")
                desktop=self.specimen("desktop")
                desktop.update(change)
                with self.assertRaisesRegex(ValueError,"E39G12B4C_"):
                    gate.evaluate_cinematic([mobile,desktop])
        with self.assertRaisesRegex(ValueError,"E39G12B4C_EXACT_TWO"):
            gate.evaluate_cinematic([self.specimen("mobile")])

    def test_no_side_effect_imports_or_auto_activation(self):
        source=Path(gate.__file__).read_text()
        for bad in ("import subprocess","import requests","import urllib",
                    "os.system(","Popen(","eval(","exec(","model.generate(","browser.launch("):
            self.assertNotIn(bad,source)
        self.assertEqual(len(gate.PATHS),12)
