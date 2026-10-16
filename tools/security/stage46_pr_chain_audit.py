#!/usr/bin/env python3
"""Stage 46.19 — read-only audit of the actual stacked draft GitHub PR chain.

Authenticated GitHub REST GET only. Never changes a PR, merges, installs, deploys,
adds an access grant, or trusts a JSON manifest as self-authorizing.
"""
from __future__ import annotations

import argparse
import json
import os
import re
import urllib.error
import urllib.request
from pathlib import Path

REPO = "teldigi5-wq/aetheris-ai-runtime"
SHA40 = re.compile(r"^[0-9a-f]{40}$")
STAGES = [f"46.{n}" for n in range(6, 19)]
NO_GO = {"production_authorized": False, "owner_pc_pilot_authorized": False, "decision": "NO_GO"}


def audit(manifest: object, live: object, *, current_base: str,
          current_base_sha: str) -> dict[str, object]:
    out = dict(NO_GO, chain_verified=False, chain_count=0,
               review_status="REJECTED", pending_pr_numbers=[])
    if not isinstance(manifest, dict) or set(manifest) != {
        "schema_version", "repo", "observed_on", "anchor_pr",
        "release_decision", "production_authorized",
        "owner_pc_pilot_authorized", "expected_pr_state",
        "expected_draft", "chain",
    }:
        return out
    if (
        type(manifest["schema_version"]) is not int or manifest["schema_version"] != 1
        or manifest["repo"] != REPO
        or manifest["release_decision"] != "NO_GO"
        or manifest["production_authorized"] is not False
        or manifest["owner_pc_pilot_authorized"] is not False
        or manifest["expected_pr_state"] != "open"
        or manifest["expected_draft"] is not True
        or type(manifest["anchor_pr"]) is not int or manifest["anchor_pr"] != 84
        or not isinstance(manifest["observed_on"], str)
        or not re.fullmatch(r"2026-\d\d-\d\d", manifest["observed_on"])
    ):
        return out
    chain = manifest["chain"]
    if not isinstance(chain, list) or len(chain) != 13 or not isinstance(live, list):
        return out
    if len(live) != len(chain) or not isinstance(current_base, str):
        return out
    expected_keys = {"number", "stage", "head", "base", "head_sha", "base_sha"}
    seen_names: set[str] = set()
    seen_nums: set[int] = set()
    for i, (expected, real) in enumerate(zip(chain, live)):
        if not isinstance(expected, dict) or set(expected) != expected_keys:
            return out
        if (type(expected["number"]) is not int or expected["number"] != 72 + i
            or expected["stage"] != STAGES[i]
            or not isinstance(expected["head"], str)
            or not expected["head"].startswith("feature/stage46-")
            or not isinstance(expected["base"], str)
            or expected["head"] == expected["base"]
            or expected["head"] in seen_names
            or expected["number"] in seen_nums
            or not isinstance(expected["head_sha"], str)
            or not SHA40.fullmatch(expected["head_sha"])
            or not isinstance(expected["base_sha"], str)
            or not SHA40.fullmatch(expected["base_sha"])):
            return out
        if i > 0 and (
            expected["base"] != chain[i-1]["head"]
            or expected["base_sha"] != chain[i-1]["head_sha"]
        ):
            return out
        if not isinstance(real, dict) or set(real) != {
            "number", "head", "base", "head_sha", "base_sha",
            "state", "draft", "merged",
        }:
            return out
        if any(real[k] != expected[k] for k in (
            "number", "head", "base", "head_sha", "base_sha"
        )):
            return out
        if real["state"] != "open" or real["draft"] is not True or real["merged"] is not False:
            return out
        seen_names.add(expected["head"])
        seen_nums.add(expected["number"])
    last = chain[-1]
    if current_base != last["head"] or current_base_sha != last["head_sha"]:
        return out

    out.update(chain_verified=True, chain_count=len(chain),
               review_status="OPEN_DRAFT_UNMERGED_INTEGRITY_CONFIRMED",
               pending_pr_numbers=[entry["number"] for entry in chain])
    return out


def _github_pr(number: int, token: str) -> dict[str, object]:
    url = f"https://api.github.com/repos/{REPO}/pulls/{number}"
    request = urllib.request.Request(url, headers={
        "Authorization": f"Bearer {token}",
        "Accept": "application/vnd.github+json",
        "X-GitHub-Api-Version": "2022-11-28",
        "User-Agent": "Syntra-Stage46-19-ReadOnly-Chain-Audit",
    }, method="GET")
    with urllib.request.urlopen(request, timeout=12) as response:
        raw = response.read(200_001)
    if len(raw) > 200_000:
        raise ValueError("OVERSIZED_PR_RESPONSE")
    item = json.loads(raw)
    return {
        "number": item["number"], "head": item["head"]["ref"],
        "head_sha": item["head"]["sha"], "base": item["base"]["ref"],
        "base_sha": item["base"]["sha"], "state": item["state"],
        "draft": item["draft"], "merged": item.get("merged") is True,
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--current-base", required=True)
    parser.add_argument("--current-base-sha", required=True)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    outcome = dict(NO_GO, chain_verified=False, chain_count=0,
                   review_status="REJECTED", pending_pr_numbers=[])
    try:
        if args.manifest.stat().st_size > 13_000:
            raise ValueError("OVERSIZED_MANIFEST")
        manifest = json.loads(args.manifest.read_text(encoding="utf-8"))
        token = os.environ.get("GITHUB_TOKEN", "")
        if not token:
            raise ValueError("MISSING_READ_ONLY_GITHUB_TOKEN")
        live = [_github_pr(n, token) for n in range(72, 85)]
        outcome = audit(manifest, live, current_base=args.current_base,
                        current_base_sha=args.current_base_sha)
    except (OSError, ValueError, KeyError, TypeError, json.JSONDecodeError,
            urllib.error.URLError):
        pass
    # No auth tokens, private user details or raw response bodies in logs.
    rendered = json.dumps(outcome, sort_keys=True, indent=2) + "\n"
    print(rendered, end="")
    if args.output is not None:
        args.output.write_text(rendered, encoding="utf-8")
    return 0 if outcome["chain_verified"] and outcome["decision"] == "NO_GO" else 1


if __name__ == "__main__":
    raise SystemExit(main())
