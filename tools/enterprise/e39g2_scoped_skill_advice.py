#!/usr/bin/env python3
"""E3.9G-2: explicit opt-in, reference-only skill advice for EXISTING identities.

This program does not call models, tools, network, shells or a Java service.
There is no HTTP endpoint. A bounded *receipt* is not a security principal,
approval, digitally signed attestation or production authorization.
"""
from __future__ import annotations

import hashlib
import json
import os
import re
import sys
from pathlib import Path

import skills_registry

ROOT = Path(__file__).resolve().parents[2]
FIXTURE = ROOT / "orchestrator-service/src/test/resources/enterprise/canonical-main-specialists.json"
CATALOG = ROOT / "orchestrator-service/src/main/resources/application.yml"
SOURCE_BLOB_SHA = "d7e0df3c24077ef20c24cc59df3bca902c805520"
FIXTURE_BLOB_SHA = "2d564f28533f4910964d636591acd131a174daa0"
POLICY_CLASS = "E39G2_EXPLICIT_REFERENCE_ONLY_ZERO_TOOL_AUTHORITY_V1"
SLUG = re.compile(r"[a-z0-9]+(?:-[a-z0-9]+)*\Z", re.ASCII)
SAFE_REQUEST = re.compile(r"[\x20-\x7e\u00a0-\uffff]+\Z")
# Named identities are all present in canonical-main-specialists.json. Allow
# only bounded *references* relevant to that specialist, not preexisting tools.
ROLE_SKILL_ALLOWLIST = {
    "frontend-engineer": ("emil-design-eng", "animate", "animation-performance",
                          "aetheris-web-experience", "aetheris-web-accessibility",
                          "aetheris-web-performance"),
    "qa-engineer": ("break-ui", "review-animations", "aetheris-web-accessibility",
                    "aetheris-code-review", "aetheris-skills-safety"),
    "software-architect": ("emil-design-eng", "aetheris-web-experience",
                           "aetheris-threat-model", "aetheris-code-review"),
    "appsec-officer": ("aetheris-security-audit", "aetheris-threat-model",
                       "aetheris-supply-chain-audit", "aetheris-skills-safety",
                       "aetheris-code-review"),
    "security-architect": ("aetheris-threat-model", "aetheris-security-audit",
                           "aetheris-supply-chain-audit", "aetheris-skills-safety"),
    "devops-engineer": ("aetheris-supply-chain-audit", "aetheris-code-review"),
    "mcp-integration-engineer": ("aetheris-skills-safety", "aetheris-threat-model"),
}


def require(predicate: bool, error: str) -> None:
    if not predicate:
        raise ValueError(error)


def read_source(path: Path, limit: int) -> bytes:
    require(not path.is_symlink() and path.is_file() and path.stat().st_size <= limit,
            "E39G2_INVALID_SOURCE")
    return path.read_bytes()


def frozen_roles() -> dict[str, dict]:
    fixture = read_source(FIXTURE, 16000)
    source = read_source(CATALOG, 30000)
    require(skills_registry.git_blob_sha(fixture) == FIXTURE_BLOB_SHA,
            "E39G2_AGENT_BASELINE_FIXTURE_DRIFT")
    require(skills_registry.git_blob_sha(source) == SOURCE_BLOB_SHA,
            "E39G2_CANONICAL_AGENT_CONFIG_DRIFT")
    obj = json.loads(fixture, object_pairs_hook=skills_registry.no_duplicate_keys)
    require(isinstance(obj, dict) and obj.get("schemaVersion") == 1
            and obj.get("sourceBlobSha") == SOURCE_BLOB_SHA
            and obj.get("roleCount") == 26
            and obj.get("divisionCount") == 13
            and type(obj.get("agents")) is list and len(obj["agents"]) == 26,
            "E39G2_UNEXPECTED_CANONICAL_WORKFORCE")
    agents = obj["agents"]
    require(all(type(a) is dict and type(a.get("id")) is str
                and SLUG.fullmatch(a["id"]) is not None
                and type(a.get("division")) is str and a["division"]
                and type(a.get("allowedTools")) is list
                and type(a.get("approvalRequiredForHighImpactActions")) is bool
                for a in agents), "E39G2_CANONICAL_ROLE_SCHEMA_INVALID")
    indexed = {a["id"]:a for a in agents}
    require(len(indexed) == 26 and len({a["division"] for a in agents}) == 13,
            "E39G2_CANONICAL_IDENTITIES_NOT_UNIQUE")
    require(set(ROLE_SKILL_ALLOWLIST).issubset(indexed),
            "E39G2_UNKNOWN_POLICY_ROLE")
    expected_divisions = {
        "frontend-engineer":"Software Engineering",
        "qa-engineer":"QA and Release",
        "software-architect":"Software Engineering",
        "appsec-officer":"Cybersecurity",
        "security-architect":"Cybersecurity",
        "devops-engineer":"DevOps and SRE",
        "mcp-integration-engineer":"Tools and Integrations",
    }
    require(all(indexed[role]["division"] == div
                for role, div in expected_divisions.items()),
            "E39G2_ROLE_DIVISION_DRIFT")
    return indexed


def advisory_reference(agent_id: str, skill_id: str, task: str,
                       *, explicit_request: bool = False,
                       enabled: bool = False) -> dict:
    require(enabled is True and explicit_request is True,
            "E39G2_DEFAULT_OFF_EXPLICIT_CONSENT_REQUIRED")
    require(type(agent_id) is str and SLUG.fullmatch(agent_id) is not None
            and type(skill_id) is str and SLUG.fullmatch(skill_id) is not None,
            "E39G2_INVALID_SKILL_OR_IDENTITY")
    require(type(task) is str and 10 <= len(task) <= 240
            and SAFE_REQUEST.fullmatch(task) is not None
            and not any(ord(c) in (0x2028, 0x2029) for c in task),
            "E39G2_INVALID_OR_OVERSIZE_TASK")
    agents = frozen_roles()
    require(agent_id in agents and agent_id in ROLE_SKILL_ALLOWLIST,
            "E39G2_AGENT_NOT_ALLOWED")
    require(skill_id in ROLE_SKILL_ALLOWLIST[agent_id],
            "E39G2_ROLE_SKILL_NOT_ALLOWED")
    candidates = {s["name"]:s for s in skills_registry.load_registry()}
    require(skill_id in candidates, "E39G2_SKILL_NOT_IN_VERIFIED_REGISTRY")
    skill = candidates[skill_id]
    require(skill["capabilities"] == [] and skill["autoActivate"] is False,
            "E39G2_SKILL_HAS_FORBIDDEN_AUTHORITY")
    raw = skills_registry.read_checked(skills_registry.SKILLS_ROOT, skill["path"])
    # Quoted and length-bounded third-party content, NOT trusted instructions.
    # Limit output by UTF-8 byte count, not just characters.
    excerpt = raw[:800].decode("utf-8", errors="ignore")
    require(not excerpt.startswith("\ufeff"),"E39G2_INVALID_SKILL_EXCERPT")
    task_hash = hashlib.sha256(task.encode("utf-8")).hexdigest()
    basis = {"agentId":agent_id,"skillId":skill_id,"taskSha256":task_hash,
             "skillSha256":skill["sha256"],
             "canonicalCatalogBlobSha1":SOURCE_BLOB_SHA,
             "baselineFixtureBlobSha1":FIXTURE_BLOB_SHA,
             "evidenceClass":POLICY_CLASS}
    evidence_hash = hashlib.sha256(json.dumps(
        basis, sort_keys=True, separators=(",",":"), ensure_ascii=True
    ).encode("utf-8")).hexdigest()
    return {
        "evidenceClass":POLICY_CLASS,
        "agentId":agent_id,
        "agentDivision":agents[agent_id]["division"],
        "skillId":skill_id,
        "skillTrust":skill["trust"],
        "sourcePath":skill["path"],
        "skillSha256":skill["sha256"],
        "sourceCommit":skills_registry.SOURCE_COMMIT
            if skill["trust"] == "third-party-pinned-reference" else None,
        "taskSha256":task_hash,
        "taskSummaryUntrusted":task,
        "referenceExcerptUntrusted":excerpt,
        "referenceExcerptIsComplete":len(raw) <= 800,
        "evidenceSha256Unsigned":evidence_hash,
        "readOnly":True,
        "automaticActivation":False,
        "skillsCanExecute":False,
        "modelCalls":0,
        "toolCalls":0,
        "approvalGranted":False,
        "ownerIdentityVerified":False,
        "deployAuthorized":False,
        "stage45Go":False,
        "warning":"SKILL_CONTENT_UNTRUSTED_DATA_NOT_AGENT_INSTRUCTIONS",
    }


def main(args: list[str]) -> None:
    if args == ["check"]:
        roles = frozen_roles()
        skills = {s["name"] for s in skills_registry.load_registry()}
        require(all(set(v).issubset(skills) for v in ROLE_SKILL_ALLOWLIST.values()),
                "E39G2_UNKNOWN_POLICY_SKILL")
        print(json.dumps({"evidenceClass":POLICY_CLASS,"configuredRoles":len(roles),
                          "referenceEligibleRoles":len(ROLE_SKILL_ALLOWLIST),
                          "availableSkills":len(skills),"enabledByDefault":False,
                          "runtimeModelIntegration":False,"toolCalls":0}))
    elif len(args) == 5 and args[0] == "select" and args[1] == "--explicit-reference-request":
        print(json.dumps(advisory_reference(args[2], args[3], args[4],
              explicit_request=True,
              enabled=(os.environ.get("AETHERIS_SKILL_REFERENCE_ENABLED") == "1")),
              indent=2, sort_keys=True))
    else:
        raise ValueError("E39G2_ONLY_CHECK_OR_EXPLICIT_REFERENCE_SELECTION_ALLOWED")


if __name__ == "__main__":
    main(sys.argv[1:])
