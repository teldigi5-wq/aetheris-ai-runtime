#!/usr/bin/env python3
"""E3.9G-12B4E: audit independently scored model repair denials, OFFLINE ONLY.

Reads an already-downloaded public-synthetic GitHub Actions evidence ZIP.
Does NOT run models, apply patches, execute JS/HTML, access repos or release gates.
The original B2/B4A policies are deliberately unchanged.
"""
from __future__ import annotations
from collections import Counter
from pathlib import Path
from zipfile import ZipFile
import json
import sys

CASES = (
    "studynest--keyboard-focus-visibility",
    "studynest--responsive-mobile-overflow",
    "cityline--reduced-motion",
    "cityline--safe-untrusted-text",
)
ARMS = ("original", "source-span")
REASONS = {
    "E39G12B2_UNTRUSTED_SCHEMA_WRONG": "schema_contract",
    "E39G12B2_OLD_NOT_UNIQUE_AUTHORIZED_LITERAL": "literal_targeting",
    "E39G12B4A_CROSS_FAULT_PROPOSAL_DENIED": "wrong_fault",
    "E39G12B2_DOM_HTML_SINK_NOT_REMOVED": "semantic_incomplete",
    "E39G12B2_FOCUS_NOT_VISIBLE_BOUNDED": "semantic_ineffective",
}
MAX_FILE = 180_000
MAX_ARCHIVE = 1_000_000


def strict_object(pairs):
    obj = {}
    for key, value in pairs:
        if key in obj:
            raise ValueError("B4E_DUPLICATE_JSON_KEY")
        obj[key] = value
    return obj


def parse_json(raw: bytes):
    if len(raw) > MAX_FILE:
        raise ValueError("B4E_FILE_OVERSIZE")
    result = json.loads(raw.decode("utf-8"), object_pairs_hook=strict_object)
    if type(result) is not dict:
        raise ValueError("B4E_EXPECTED_JSON_OBJECT")
    return result


def summarize_archive(path: Path) -> dict:
    if path.is_symlink() or not path.is_file() or not 0 < path.stat().st_size <= MAX_ARCHIVE:
        raise ValueError("B4E_UNSAFE_ARCHIVE")
    expected = {"comparison.json"}
    expected.update(
        f"evaluated/{arm}/repairs/{case}/patch.json"
        for arm in ARMS for case in CASES
    )
    with ZipFile(path) as zip_file:
        listing = zip_file.infolist()
        names = [entry.filename for entry in listing]
        if len(names) != len(set(names)):
            raise ValueError("B4E_DUPLICATE_ZIP_ENTRY")
        # The CI artifact legitimately includes screenshots and raw source, which
        # this audit must not parse or expose. Only allow known evidence tree.
        for entry in listing:
            name = entry.filename
            if (name.startswith("/") or "\\" in name or ".." in name.split("/")
                or entry.file_size > MAX_FILE or (entry.external_attr >> 16) & 0o170000 == 0o120000):
                raise ValueError("B4E_UNSAFE_ZIP_MEMBER")
        if not expected.issubset(names):
            raise ValueError("B4E_MISSING_OR_WRONG_EVIDENCE")
        if sum(item.file_size for item in listing) > MAX_ARCHIVE * 3:
            raise ValueError("B4E_DECOMPRESSION_LIMIT")
        comparison = parse_json(zip_file.read("comparison.json"))
        if (comparison.get("evidenceClass") != "E39G12B4D_TWO_ARM_MODEL_REPAIR_COMPARISON"
            or comparison.get("realModelCallsFromUntrustedReceipt") != 8
            or comparison.get("genuinelyHeldOut") is not False
            or comparison.get("stage45Go") is not False
            or comparison.get("autonomousWebsiteFactoryCertified") is not False
            or set(comparison.get("arms", {})) != set(ARMS)):
            raise ValueError("B4E_COMPARISON_PROVENANCE_DRIFT")
        result = {}
        for arm in ARMS:
            arm_data = comparison["arms"][arm]
            decisions = []
            reasons = Counter()
            accepted = 0
            for case in CASES:
                name = f"evaluated/{arm}/repairs/{case}/patch.json"
                patch = parse_json(zip_file.read(name))
                outcome = patch.get("policyOutcome")
                if (patch.get("ownerApprovalGranted") is not False
                    or patch.get("stage45Go") is not False
                    or outcome not in ("POLICY_REJECTED", "POLICY_APPLIED")):
                    raise ValueError("B4E_POLICY_AUTHORITY_DRIFT")
                if patch.get("fault") != case.split("--", 1)[1] or patch.get("site") != case.split("--")[0]:
                    raise ValueError("B4E_WRONG_CASE_MAPPING")
                if outcome == "POLICY_APPLIED":
                    accepted += 1
                    classification = "policy_accepted_not_necessarily_browser_correct"
                else:
                    reason = patch.get("reason")
                    if reason not in REASONS:
                        raise ValueError("B4E_UNRECOGNIZED_POLICY_REASON")
                    classification = REASONS[reason]
                    reasons[classification] += 1
                decisions.append({"case": case, "outcome": outcome, "class": classification})
            if (arm_data.get("policyAccepted") != accepted
                or len(arm_data.get("perFault", [])) != 4
                or any(
                    row.get("token") != decision["case"]
                    or row.get("accepted") != (decision["outcome"] == "POLICY_APPLIED")
                    for row, decision in zip(arm_data["perFault"], decisions)
                )):
                raise ValueError("B4E_SCORER_VS_REPAIR_MISMATCH")
            result[arm] = {
                "attempted": len(CASES),
                "policyAccepted": accepted,
                "browserVerifiedRepairs": arm_data["browserVerifiedRepairs"],
                "denialCategories": dict(sorted(reasons.items())),
                "decisions": decisions,
            }
        report = {
            "evidenceClass": "E39G12B4E_READ_ONLY_EIGHT_CASE_FAILURE_TAXONOMY",
            "sourceEvidenceClass": comparison["evidenceClass"],
            "modelTag": comparison["modelTag"],
            "arms": result,
            "genuinelyHeldOut": False,
            "modelCallsPerformed": 0,
            "modelOutputExecuted": False,
            "policyChanged": False,
            "newAgentPermissionGranted": False,
            "stage45Go": False,
            "autonomousRepairCertified": False,
        }
        return report


def main():
    if len(sys.argv) != 2:
        raise ValueError("B4E_EXPECT_EXACT_LOCAL_ARTIFACT_PATH")
    output = summarize_archive(Path(sys.argv[1]))
    print(json.dumps(output, indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
