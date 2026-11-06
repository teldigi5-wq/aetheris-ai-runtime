#!/usr/bin/env python3
"""S-B62-A structural review of redacted operator evidence; NEVER release admission.

No network, subprocess, host scan, secrets collection, or write operation.
An unsigned manifest is a CLAIM, not independent verification.
"""
import argparse
import json
from pathlib import Path
import re
import sys

SCHEMA = "aetheris.s-b62-a.operator-inventory.v1"
ROLES = ("old_primary", "promoted_primary", "epoch_authority", "external_fencer")
CHECKS = (
    "old_primary_write_after_promotion",
    "privileged_witness_epoch_rollback",
    "partition_and_authority_outage",
    "delayed_stale_transaction_commit",
    "direct_backend_cross_owner_scope",
)
SAFE_LABEL = re.compile(r"^[a-z][a-z0-9-]{1,23}$")
SHA256 = re.compile(r"^[0-9a-f]{64}$")
COMMIT = re.compile(r"^[0-9a-f]{40}$")


class InventoryError(ValueError):
    pass


def _strict_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise InventoryError("duplicate JSON object key")
        result[key] = value
    return result


def _fields(value, expected, where):
    if not isinstance(value, dict) or set(value) != set(expected):
        raise InventoryError("unexpected or missing fields at " + where)


def parse_manifest(path):
    if not path.is_file() or path.is_symlink():
        raise InventoryError("manifest must be a regular file, not a symlink")
    if path.stat().st_size > 16_384:
        raise InventoryError("manifest exceeds 16 KiB")
    try:
        return json.loads(path.read_text(encoding="utf-8"),
                          object_pairs_hook=_strict_object,
                          parse_constant=lambda _: (_ for _ in ()).throw(
                              InventoryError("non-JSON numeric constant")))
    except (UnicodeError, json.JSONDecodeError) as exc:
        raise InventoryError("invalid UTF-8 JSON manifest") from exc


def preflight(manifest):
    """Validate shapes; never infer that submitted evidence is authentic."""
    _fields(manifest, ("schema", "scope", "runtime_commit", "platform_commit",
                       "roles", "checks", "release_decision"), "root")
    if manifest["schema"] != SCHEMA or manifest["scope"] != "operator-redacted-review":
        raise InventoryError("wrong schema or unsupported scope")
    if manifest["release_decision"] != "NO_GO":
        raise InventoryError("operator inputs cannot authorize release")
    for field in ("runtime_commit", "platform_commit"):
        if not isinstance(manifest[field], str) or not COMMIT.fullmatch(manifest[field]):
            raise InventoryError("invalid pinned revision field")
    roles = manifest["roles"]
    _fields(roles, ROLES, "roles")
    host_domains = {}
    admin_domains = {}
    for role in ROLES:
        value = roles[role]
        _fields(value, ("host_domain", "admin_domain"), "role")
        for key in ("host_domain", "admin_domain"):
            label = value[key]
            if not isinstance(label, str) or not SAFE_LABEL.fullmatch(label):
                raise InventoryError("domain identifiers must be pseudonyms")
        host_domains[role] = value["host_domain"]
        admin_domains[role] = value["admin_domain"]
    checks = manifest["checks"]
    _fields(checks, CHECKS, "checks")
    missing = []
    for name in CHECKS:
        item = checks[name]
        _fields(item, ("result", "evidence_sha256"), "check")
        status = item["result"]
        receipt = item["evidence_sha256"]
        if status not in ("DENIED", "ALLOWED", "NOT_RUN"):
            raise InventoryError("invalid observed test outcome")
        if receipt is not None and (not isinstance(receipt, str)
                                    or not SHA256.fullmatch(receipt)):
            raise InventoryError("evidence must be SHA-256 digest only")
        if status != "NOT_RUN" and receipt is None:
            raise InventoryError("observed test requires evidence digest")
        if status == "NOT_RUN" and receipt is not None:
            raise InventoryError("unperformed test cannot claim evidence")
        if status != "DENIED":
            missing.append(name)
    if len(set(host_domains.values())) != len(ROLES):
        missing.append("shared_host_failure_domain")
    if len(set(admin_domains.values())) != len(ROLES):
        missing.append("shared_administrative_authority")
    # An unsigned inventory cannot prove privileged access, STONITH, witness
    # rollback protection, authentic identity or physical owner acceptance.
    # Even a structurally complete manifest is NEVER a positive release gate.
    return {
        "preflight": "BLOCKED" if missing else "STRUCTURALLY_COMPLETE_UNVERIFIED",
        "unverified_or_failed_checks": sorted(missing),
        "claims_are_self_reported": True,
        "independent_review_verified": False,
        "physical_fencing_certified": False,
        "release_decision": "STAGE45_NO_GO",
        "xerj_publication": "HOLD",
        "next_step": "independent operator and security review on real failure domains",
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", required=True, type=Path,
                        help="local REDACTED inventory JSON; never commit real host details")
    args = parser.parse_args()
    try:
        report = preflight(parse_manifest(args.manifest))
    except (InventoryError, OSError, TypeError) as exc:
        # Do not emit input values or secret material from malformed documents.
        print(json.dumps({"preflight": "INVALID_MANIFEST",
                          "release_decision": "STAGE45_NO_GO",
                          "xerj_publication": "HOLD"}), sort_keys=True)
        return 2
    print(json.dumps(report, sort_keys=True, indent=2))
    return 0 if report["preflight"] == "STRUCTURALLY_COMPLETE_UNVERIFIED" else 1


if __name__ == "__main__":
    sys.exit(main())
