#!/usr/bin/env python3
"""E3.9B model-output policy validator in EXISTING network-none Docker worker.

The model is never granted this worker, a shell, browser or filesystem. Its
text is merely read as untrusted evidence. E3.9A's unchanged exact canonical
CSS and SHA256 policy is independently applied before one scoped site write.
"""
from __future__ import annotations
import hashlib
import json
from pathlib import Path
import sys

import e34_model_adviser as strict
import e39_keyboard_patch_gate as gate

ALLOWED = frozenset(("hermes3:3b", "granite4:3b"))


def digest(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def model_proposal() -> tuple[dict, bytes, bytes, str]:
    gate.ensure_workspace()
    browser = gate.actual_browser_failure()
    source = gate.checked(gate.STYLE, 25_000)
    blob = gate.checked(gate.DIR / "real-model-response.json", 4600)
    claim = json.loads(blob)
    if (not isinstance(claim, dict)
            or claim.get("evidenceClass") != "E39B_REAL_LOCAL_MODEL_ADVISORY_NOT_ACTION"
            or claim.get("caseId") != "keyboard-skip-link"
            or claim.get("realModelCalls") != 1
            or claim.get("modelOutputExecutedDirectly") is not False
            or claim.get("ownerApproved") is not False
            or claim.get("productionPublished") is not False
            or claim.get("sourceSha256") != digest(source)
            or claim.get("browserBeforeSha256") != digest(browser)
            or claim.get("fixtureGoldenPatchConsulted") is not False):
        raise ValueError("E39B_MODEL_EVIDENCE_SCOPE_OR_HASH_INVALID")
    model = claim.get("model")
    if not isinstance(model, dict) or model.get("tag") not in ALLOWED:
        raise ValueError("E39B_UNALLOWLISTED_MODEL_ID")
    raw = claim.get("rawResponse")
    if (not isinstance(raw, str)
            or len(raw.encode("utf-8")) > 1200
            or claim.get("rawResponseSha256") != digest(raw.encode("utf-8"))
            or claim.get("responseClass") != "TEXT_REPLACEMENT_PROPOSED"):
        raise ValueError("E39B_NON_REPLACEMENT_MODEL_OUTPUT_REFUSED")
    try:
        parsed = strict.strict_json(raw)
    except ValueError as error:
        raise ValueError("E39B_MODEL_SCHEMA_DENIED") from error
    if parsed.get("operation") != "replace":
        raise ValueError("E39B_MODEL_ABSTENTION_NOT_PATCH")
    old, new = parsed["old"], parsed["new"]
    if (not isinstance(old, str) or not isinstance(new, str)
            or not 1 <= len(old) <= 160 or len(new) > 160
            or source.decode("utf-8").count(old) != 1):
        raise ValueError("E39B_MODEL_OLD_SPAN_AMBIGUOUS")
    proposal = {
        "version": 1, "kind": gate.KIND,
        "caseId": "keyboard-skip-link", "path": "site/styles.css",
        "baseSha256": digest(source), "beforeBrowserSha256": digest(browser),
        "resultSha256": digest(source.decode("utf-8").replace(old,new,1).encode("utf-8")),
        "old": old, "new": new,
    }
    return proposal, source, browser, model["tag"]


def apply() -> None:
    proposal, source, browser, model = model_proposal()
    manifest = json.loads(gate.checked(gate.SITE / "manifest.json", 4096))
    # Same immutable, independent E3.9A exact CSS semantics / canonical digest.
    accepted = gate.validate(proposal, source, browser, manifest)
    if gate.DIR.joinpath("receipt.json").exists() or gate.DIR.joinpath("receipt.json").is_symlink():
        raise ValueError("E39B_RECEIPT_ALREADY_EXISTS")
    gate.STYLE.write_bytes(accepted)
    if gate.checked(gate.STYLE, 25_000) != accepted:
        raise ValueError("E39B_APPLY_BYTES_DID_NOT_MATCH")
    receipt = {
        "evidenceClass": "E39B_INDEPENDENT_POLICY_ACCEPTED_MODEL_TEXT_BROWSER_PENDING",
        "caseId": "keyboard-skip-link", "modelTag": model,
        "beforeSha256": digest(source), "afterSha256": digest(accepted),
        "proposalSha256": digest(json.dumps(proposal,sort_keys=True).encode("utf-8")),
        "path": "site/styles.css", "modelResponseTextWasProposed": True,
        "modelWasCalledReportedByExternalInference": True,
        "modelProvenanceNotCryptographicallyAttested": True,
        "modelOutputExecutedDirectly": False, "ownerApproved": False,
        "productionPublished": False, "independentBrowserAfterPending": True,
    }
    with gate.DIR.joinpath("receipt.json").open("x", encoding="utf-8") as handle:
        json.dump(receipt, handle, indent=2,sort_keys=True)
        handle.write("\n")
    print("E39B_MODEL_TEXT_POLICY_ACCEPTED_BROWSER_QA_STILL_REQUIRED",model)


if __name__ == "__main__":
    if sys.argv[1:] != ["apply"]:
        raise ValueError("E39B_FIXED_APPLY_COMMAND_ONLY")
    apply()
