#!/usr/bin/env python3
"""E3.9G-12B4F: diagnose raw B4D model replies against original broken bytes.

Offline, read-only, evaluator-side only. No inference, source edits, JS/HTML
execution, source patch acceptance, or grants. The frozen B2 typed gate is
imported only to compare canonical old literals, not to bypass validation.
"""
from __future__ import annotations

from collections import Counter
import hashlib
import json
from pathlib import Path
import sys
from zipfile import ZipFile

import e39g12b4e_failure_taxonomy as evidence
import e39g12b2_typed_policy as frozen

MAX_RESPONSE = 1800
CASE_SET = set(evidence.CASES)
ARMS = evidence.ARMS


def digest(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def check_replies(archive: Path) -> dict:
    # Existing B4E validator checks ZIP paths, bounded size, provenance,
    # all eight frozen policy decisions, and forbids owner/Stage45 grants.
    baseline = evidence.summarize_archive(archive)
    if any(baseline["arms"][arm]["policyAccepted"] != 0 for arm in ARMS):
        raise ValueError("B4F_REQUIRES_UNMODIFIED_REJECTED_SOURCE")
    with ZipFile(archive) as z:
        decisions = {}
        for arm in ARMS:
            incoming = evidence.parse_json(
                z.read(f"evaluated/{arm}/incoming/proposals.json")
            )
            if (
                incoming.get("evidenceClass")
                != "E39G12B4A_FOUR_REAL_LOCAL_OLLAMA_COMPLETIONS"
                or incoming.get("modelTag") != "qwen2.5-coder:7b"
                or incoming.get("realModelCalls") != 4
                or incoming.get("modelTextExecuted") is not False
                or incoming.get("ownerApprovalGranted") is not False
                or incoming.get("productionPublished") is not False
                or incoming.get("goldenReferenceAvailable") is not False
                or set(incoming.get("trials", {})) != CASE_SET
            ):
                raise ValueError("B4F_BAD_MODEL_RECEIPT_BOUNDARY")
            rows = []
            for token in evidence.CASES:
                site, fault = token.split("--", 1)
                row = incoming["trials"][token]
                patch = evidence.parse_json(
                    z.read(f"evaluated/{arm}/repairs/{token}/patch.json")
                )
                source = z.read(
                    f"evaluated/{arm}/repairs/{token}/index.html"
                )
                raw = row.get("rawModelResponse")
                if (
                    patch.get("policyOutcome") != "POLICY_REJECTED"
                    or patch.get("patchApplied") is not False
                    or patch.get("ownerApprovalGranted") is not False
                    or patch.get("stage45Go") is not False
                    or patch.get("sourceBeforeSha256") != digest(source)
                    or patch.get("sourceAfterSha256") != digest(source)
                    or patch.get("modelRawSha256") != row.get("rawResponseSha256")
                    or row.get("site") != site
                    or row.get("fault") != fault
                    or row.get("sourceSha256") != digest(source)
                    or row.get("modelTag") != "qwen2.5-coder:7b"
                    or not isinstance(raw, str)
                    or not 0 < len(raw.encode("utf-8")) <= MAX_RESPONSE
                    or row.get("rawResponseSha256")
                    != digest(raw.encode("utf-8"))
                ):
                    raise ValueError("B4F_UNTRUSTED_SOURCE_OR_RECEIPT_DRIFT")
                old_text_present_once = False
                authorized_old_quote = False
                schema_exact = False
                identity_exact = False
                source_digest_exact = False
                try:
                    if len(raw.encode("utf-8")) > 800:
                        raise ValueError("B4F_FROZEN_JSON_LIMIT")
                    candidate = json.loads(raw, object_pairs_hook=evidence.strict_object)
                    schema_exact = type(candidate) is dict and set(candidate) == frozen.SCHEMA
                    if not schema_exact:
                        category = "schema_incomplete_or_malformed"
                    else:
                        identity_exact = (candidate["site"], candidate["fault"]) == (site, fault)
                        source_digest_exact = candidate["sourceSha256"] == digest(source)
                        old = candidate["old"]
                        old_text_present_once = (
                            type(old) is str and 1 <= len(old) <= 180
                            and source.count(old.encode("utf-8")) == 1
                        )
                        authorized_old_quote = (
                            identity_exact and old_text_present_once
                            and (site, fault) in frozen.RULES
                            and old == frozen.RULES[(site, fault)][0]
                        )
                        if not identity_exact:
                            category = "wrong_case_or_fault"
                        elif not source_digest_exact:
                            category = "source_digest_mismatch"
                        elif not old_text_present_once:
                            category = "old_literal_not_unique_in_source"
                        elif not authorized_old_quote:
                            category = "wrong_authorized_literal"
                        else:
                            # Still rejected by trusted B2 due to new expression,
                            # semantics, and/or controls; never assert approval.
                            category = "correct_source_quote_but_policy_rejected"
                except (json.JSONDecodeError, ValueError, TypeError, UnicodeError):
                    category = "schema_incomplete_or_malformed"
                rows.append({
                    "case": token,
                    "category": category,
                    "schemaExact": schema_exact,
                    "siteAndFaultExact": identity_exact,
                    "sourceDigestExact": source_digest_exact,
                    "oldPresentExactlyOnce": old_text_present_once,
                    "oldEqualsCanonicalAuthorizedLiteral": authorized_old_quote,
                    "unchangedFrozenGate": "POLICY_REJECTED",
                })
            count = Counter(row["category"] for row in rows)
            decisions[arm] = {"attempts": 4, "categories": dict(sorted(count.items())), "cases": rows}
    return {
        "evidenceClass": "E39G12B4F_OFFLINE_SOURCE_QUOTE_ERROR_DIAGNOSTICS",
        "sourceEvidenceClass": baseline["sourceEvidenceClass"],
        "modelTag": "qwen2.5-coder:7b",
        "arms": decisions,
        "attempts": 8,
        "acceptedPatches": 0,
        "browserVerifiedRepairs": 0,
        "modelCallsPerformed": 0,
        "untrustedResponseTextEmitted": False,
        "originalSourceModified": False,
        "frozenPolicyModified": False,
        "genuinelyHeldOut": False,
        "ownerAuthorizationGranted": False,
        "stage45Go": False,
        "automatedCodingCertified": False,
    }


def main() -> None:
    if len(sys.argv) != 2:
        raise ValueError("B4F_LOCAL_ARCHIVE_PATH_REQUIRED")
    print(json.dumps(check_replies(Path(sys.argv[1])), indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
