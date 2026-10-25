"""B4D adversarial contract tests: controlled inputs, no model calls."""
from __future__ import annotations
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from zipfile import ZipFile

import e39g12b4d_trial_contract as trial


def fixture():
    source_a=b"<main><style>.skip:focus{top:-200px;} @media(max-width:680px){.schedule-grid{width:900px}}</style></main>"
    source_b=b"<main>@media(prefers-reduced-motion:reduce){.ticker{animation:x}} function render(name){output.innerHTML=name;}</main>"
    sources = {"studynest": source_a, "cityline": source_b}
    observed={}
    span_map={
        "studynest--keyboard-focus-visibility":".skip:focus{top:-200px;}",
        "studynest--responsive-mobile-overflow":".schedule-grid{width:900px}",
        "cityline--reduced-motion":"@media(prefers-reduced-motion:reduce){.ticker{animation:x}}",
        "cityline--safe-untrusted-text":"output.innerHTML=name;",
    }
    for token,site,fault in trial.CASES:
        span=span_map[token]
        observed[token]={"site":site,"fault":fault,
            "sourceSha256":trial.sha(sources[site]),
            "existingSourceSpan":span,
            "existingSpanSha256":trial.sha(span.encode()),
            "proposedReplacement":None}
    return ({"sites/"+site+"/index.html":src for site,src in sources.items()},
            {"sites":{site:{"sha256":trial.sha(src)} for site,src in sources.items()}},
            {"observations":observed})


class PairedTrialTest(unittest.TestCase):
    def test_four_cases_are_exactly_two_balanced_arms(self):
        with patch.object(trial,"inspected_source",return_value=fixture()):
            packet=trial.prompt_bundle()
        self.assertEqual(len(packet["trials"]),8)
        self.assertEqual(len(set(packet["order"])),8)
        self.assertEqual(set(packet["order"]),set(packet["trials"]))
        self.assertEqual(packet["realModelCalls"],0)
        self.assertIs(packet["containsGolden"],False)
        self.assertEqual(packet["arms"],["original","source-span"])
        for token,site,fault in trial.CASES:
            original=packet["trials"][token+"--original"]["prompt"]
            hinted=packet["trials"][token+"--source-span"]["prompt"]
            before,suffix=hinted.split("\nSOURCE-ONLY OBSERVATION ",1)
            self.assertEqual(before,original.split("\nBEGIN READ-ONLY DEFECTIVE HTML")[0])
            self.assertIn("NOT a repair answer",suffix)
            self.assertEqual(
                suffix.split("\nBEGIN READ-ONLY DEFECTIVE HTML",1)[1],
                original.split("\nBEGIN READ-ONLY DEFECTIVE HTML",1)[1])
            self.assertNotEqual(trial.sha(original.encode()),trial.sha(hinted.encode()))
            self.assertIn("UNTRUSTED data",original)
            self.assertIn("No external requests",original)
            self.assertIn("sourceSha256",original)

    def test_pack_is_exactly_one_member_with_no_reference(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            with (patch.object(trial,"inspected_source",return_value=fixture()),
                  patch.object(trial,"ROOT",root),
                  patch.object(trial,"INPUT",root/"model-input-prompts.zip")):
                trial.pack()
                with ZipFile(root/"model-input-prompts.zip") as z:
                    self.assertEqual(z.namelist(),["prompts.json"])
                    packet=trial.loads(z.read("prompts.json"))
                    self.assertEqual(len(packet["trials"]),8)
                    self.assertFalse(packet["containsGolden"])
                with self.assertRaisesRegex(ValueError,"B4D_OUTPUT_REPLAY"):
                    trial.pack()

    def test_reject_source_ambiguity_and_solution_leak(self):
        files,orig,obs=fixture()
        orig["evidenceClass"]="E39G12B2_MODEL_FACING_ONLY_BROKEN_PUBLIC_SYNTHETIC"
        orig["referenceVariantsIncluded"]=False
        orig["goldenReplacementIncluded"]=False
        obs["evidenceClass"]="E39G12B4B_SOURCE_SPANS_FROM_BROKEN_ONLY"
        obs["replacementAnswersProvided"]=False
        obs["referenceInputsAvailable"]=False
        with tempfile.TemporaryDirectory() as tmp:
            path=Path(tmp)/"seed.zip"
            def write():
                with ZipFile(path,"w") as z:
                    for item in trial.ZIP_MEMBERS:
                        if item=="proposer-manifest.json": blob=json.dumps(orig).encode()
                        elif item=="observed-declarations.json": blob=json.dumps(obs).encode()
                        else:blob=files[item]
                        z.writestr(item,blob)
            write()
            with patch.object(trial,"SEED",path):
                source,_,_=trial.inspected_source()
                self.assertEqual(set(source),set(trial.ZIP_MEMBERS))
                obs["observations"]["studynest--keyboard-focus-visibility"]["proposedReplacement"]="FAKE_GOLD"
                write()
                with self.assertRaisesRegex(ValueError,"B4D_SOURCE_SPAN_DRIFT_OR_GOLD_EXPOSURE"):
                    trial.inspected_source()
                obs["observations"]["studynest--keyboard-focus-visibility"]["proposedReplacement"]=None
                obs["observations"]["studynest--keyboard-focus-visibility"]["existingSourceSpan"]=".skip:focus{top:-200px;}"*2
                write()
                with self.assertRaisesRegex(ValueError,"B4D_SOURCE_SPAN_DRIFT_OR_GOLD_EXPOSURE"):
                    trial.inspected_source()

    def test_untrusted_receipt_is_never_owner_approval(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            incoming=root/"incoming"
            incoming.mkdir()
            packet=None
            with patch.object(trial,"inspected_source",return_value=fixture()):
                packet=trial.prompt_bundle()
            data=json.dumps(packet,sort_keys=True).encode()
            model_zip=root/"model-input-prompts.zip"
            with ZipFile(model_zip,"w") as z:z.writestr("prompts.json",data)
            sample={
                "evidenceClass":"E39G12B4D_EIGHT_REAL_LOCAL_MODEL_COMPLETIONS_UNTRUSTED",
                "modelTag":trial.MODEL,"realModelCalls":8,
                "promptBundleSha256":trial.sha(model_zip.read_bytes()),
                "ownerApprovalGranted":True,"modelSourceEdited":False,
                "goldenReferenceAvailable":False,"productionPublished":False,
                "ollamaSelfReportedDigest":trial.PREFIX+"b"*52,
                "sizeBytes":4700000000,"trials":{},
            }
            for token, row in packet["trials"].items():
                raw='{"operation":"abstain"}'
                sample["trials"][token]={
                    "site":row["site"],"fault":row["fault"],"arm":row["arm"],
                    "modelTag":trial.MODEL,"promptSha256":row["promptSha256"],
                    "sourceSha256":row["sourceSha256"],"rawModelResponse":raw,
                    "rawResponseSha256":trial.sha(raw.encode()),
                    "generatedTokens":8,"elapsedWallMs":1000}
            (incoming/"real-model-results.json").write_text(json.dumps(sample))
            with (patch.object(trial,"INPUT",model_zip),
                  patch.object(trial,"INCOMING",incoming/"real-model-results.json"),
                  patch.object(trial,"inspected_source",return_value=fixture())):
                with self.assertRaisesRegex(ValueError,"B4D_INVALID_OR_FORGED"):
                    trial.verify_model_receipt()
                sample["ownerApprovalGranted"]=False
                sample["trials"][next(iter(sample["trials"]))]["promptSha256"]="f"*64
                (incoming/"real-model-results.json").write_text(json.dumps(sample))
                with self.assertRaisesRegex(ValueError,"B4D_UNTRUSTED_MODEL_ROW_DRIFT"):
                    trial.verify_model_receipt()

    def test_nonexecuting_scope_and_unchanged_frozen_policy(self):
        contract=Path(trial.__file__).read_text(encoding="utf-8")
        worker=Path(trial.__file__).with_name("run_locked_worker.sh").read_text(encoding="utf-8")
        self.assertIn("e39g12b4a-apply",worker)
        self.assertIn("--network none",worker)
        self.assertIn('frozen.validate',Path(trial.__file__).with_name("e39g12b4a_policy_worker.py").read_text(encoding="utf-8"))
        self.assertNotIn("subprocess.run(",contract)
        self.assertNotIn("urllib.request",contract)
        self.assertIn("stage45Go",contract)
        self.assertIn("ownerApprovalGranted",contract)


if __name__=="__main__":
    unittest.main()
