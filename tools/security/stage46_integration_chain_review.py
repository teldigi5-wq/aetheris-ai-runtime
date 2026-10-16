#!/usr/bin/env python3
"""Stage 46.20 read-only extended chain check before integration review.

Reuses Stage 46.19's reviewed #72-84 manifest and validates #85 separately.
The manifest is not auto-mutated: a changed earlier PR requires deliberate
review and updates. No GitHub write operations or release authorization.
"""
from __future__ import annotations

import argparse
import json
import os
import urllib.error
from pathlib import Path

from stage46_pr_chain_audit import audit, _github_pr, REPO

EXPECTED_PR85_SHA = "cf7c02b25f15b91a100c060bc5b35008c0965c8d"
EXPECTED_PR85_HEAD = "feature/stage46-cross-runner-provenance-and-chain-audit-20261016"
EXPECTED_PR85_NUMBER = 85


def assess(manifest: object, old_live: object, pr85: object,
           *, current_base: object, current_base_sha: object) -> dict[str, object]:
    result = {
        "schema_version": 1,
        "prior_chain_verified": False,
        "pr85_verified": False,
        "draft_count": 0,
        "extended_chain_review_passed": False,
        "production_authorized": False,
        "owner_pc_pilot_authorized": False,
        "release_decision": "NO_GO",
    }
    if not isinstance(manifest, dict) or not isinstance(pr85, dict):
        return result
    entries = manifest.get("chain")
    if not isinstance(entries, list) or len(entries) != 13:
        return result
    last = entries[-1]
    if not isinstance(last, dict):
        return result
    prior = audit(manifest, old_live, current_base=last.get("head"),
                  current_base_sha=last.get("head_sha"))
    if not prior["chain_verified"]:
        return result
    result["prior_chain_verified"] = True
    if set(pr85) != {
        "number", "head", "base", "head_sha", "base_sha",
        "state", "draft", "merged",
    }:
        return result
    if (type(pr85["number"]) is not int
        or pr85["number"] != EXPECTED_PR85_NUMBER
        or pr85["head"] != EXPECTED_PR85_HEAD
        or pr85["head_sha"] != EXPECTED_PR85_SHA
        or pr85["base"] != last["head"]
        or pr85["base_sha"] != last["head_sha"]
        or pr85["state"] != "open"
        or pr85["draft"] is not True
        or pr85["merged"] is not False
        or current_base != pr85["head"]
        or current_base_sha != pr85["head_sha"]):
        return result
    result.update(pr85_verified=True, draft_count=14,
                  extended_chain_review_passed=True)
    return result


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--current-base", required=True)
    parser.add_argument("--current-base-sha", required=True)
    args = parser.parse_args()
    result = assess(None, None, None, current_base="", current_base_sha="")
    try:
        if args.manifest.stat().st_size > 13000:
            raise ValueError("OVERSIZED_MANIFEST")
        doc = json.loads(args.manifest.read_text(encoding="utf-8"))
        token = os.environ.get("GITHUB_TOKEN")
        if not token:
            raise ValueError("GITHUB_READ_ONLY_TOKEN_REQUIRED")
        previous = [_github_pr(n, token) for n in range(72, 85)]
        pr85 = _github_pr(85, token)
        result = assess(doc, previous, pr85,
                        current_base=args.current_base,
                        current_base_sha=args.current_base_sha)
    except (OSError, ValueError, KeyError, TypeError, urllib.error.URLError):
        pass
    print(json.dumps(result, sort_keys=True, indent=2))
    return 0 if result["extended_chain_review_passed"] and result["release_decision"] == "NO_GO" else 1


if __name__ == "__main__":
    raise SystemExit(main())
