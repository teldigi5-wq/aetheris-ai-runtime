#!/usr/bin/env python3
"""E3.9G-3 cross-language, cross-platform manifest parity and fail-closed QA."""
from __future__ import annotations

import json
import sys
from pathlib import Path

import e39g2_scoped_skill_advice as role_source
import skills_registry

REPO = Path(__file__).resolve().parents[2]
POLICY = REPO / "orchestrator-service/src/main/resources/enterprise/skill-advice-bindings-v1.json"
CLASS = "E39G3_TASK_BOUND_SKILL_REFERENCE_ONLY_NO_EXECUTION"


def validate(path: Path = POLICY) -> dict:
    if path.is_symlink() or not path.is_file() or path.stat().st_size > 20_000:
        raise ValueError("E39G3_UNSAFE_MANIFEST_LOCATION")
    obj = json.loads(path.read_text(encoding="utf-8"),
                     object_pairs_hook=skills_registry.no_duplicate_keys)
    if (type(obj) is not dict or set(obj) != {
        "schemaVersion", "evidenceClass", "enabledByDefault", "originalSourceCommit",
        "upstreamLicense", "roles", "skills"
    } or type(obj["schemaVersion"]) is not int or obj["schemaVersion"] != 1
            or obj["evidenceClass"] != CLASS or obj["enabledByDefault"] is not False
            or obj["originalSourceCommit"] != skills_registry.SOURCE_COMMIT
            or obj["upstreamLicense"] != "MIT"):
        raise ValueError("E39G3_UNSAFE_ROOT_SCHEMA")
    existing = role_source.frozen_roles()
    if len(existing) != 26:
        raise ValueError("E39G3_CANONICAL_ROSTER_DRIFT")
    roles = obj["roles"]
    if (type(roles) is not dict
            or set(roles) != set(role_source.ROLE_SKILL_ALLOWLIST)
            or any(type(skills) is not list or
                   skills != list(role_source.ROLE_SKILL_ALLOWLIST[role])
                   for role, skills in roles.items())):
        raise ValueError("E39G3_CROSS_LANGUAGE_ROLE_ESCALATION")
    original = skills_registry.load_registry()
    ref_map = {x["name"]:x for x in original}
    skills = obj["skills"]
    if (type(skills) is not list or len(skills) != len(ref_map)
            or len({x.get("name") for x in skills if type(x) is dict}) != len(skills)):
        raise ValueError("E39G3_MISSING_OR_DUPLICATE_SKILLS")
    for item in skills:
        if (type(item) is not dict or set(item) != {
            "name", "trust", "summary", "sourcePath"
        } or item["name"] not in ref_map
                or item["trust"] != ref_map[item["name"]]["trust"]
                or item["sourcePath"] != ref_map[item["name"]]["path"]
                or type(item["summary"]) is not str
                or not 20 <= len(item["summary"]) <= 180
                or any(ord(c) < 32 or ord(c) == 127
                       for c in item["summary"])):
            raise ValueError("E39G3_REFERENCE_SOURCE_OR_SUMMARY_MISMATCH")
    return {
        "class": CLASS, "roles": len(roles), "skills": len(skills),
        "canonicalRoleCount": len(existing),
        "javaServiceRuntimeDefaultEnabled": False,
        "sourceBytesVerifiedByRegistry": True,
        "ownerAuthenticationEstablished": False,
        "modelCalls": 0, "toolCalls": 0, "stage45Certified": False
    }


if __name__ == "__main__":
    if sys.argv[1:] != ["verify"]:
        raise ValueError("E39G3_VERIFICATION_ONLY")
    print(json.dumps(validate(), sort_keys=True))
