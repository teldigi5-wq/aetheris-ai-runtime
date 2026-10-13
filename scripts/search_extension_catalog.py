#!/usr/bin/env python3
"""Offline, read-only discovery over Aetheris' vetted research index.

A search result is NEVER an installed tool, security recommendation, license
approval, compatibility certification or permission to connect the server.
"""
from __future__ import annotations
import argparse
import json
from pathlib import Path
if __package__:
    from .validate_extension_ecosystem import ROOT, validate_catalog
else:
    from validate_extension_ecosystem import ROOT, validate_catalog

def search_sources(
    root: Path = ROOT, *,
    query: str = "", category: str = "", reported_license: str = "",
    limit: int = 15
) -> dict[str, object]:
    if type(limit) is not int or not 1 <= limit <= 25:
        raise ValueError("limit must be 1..25")
    for value in (query, category, reported_license):
        if not isinstance(value, str) or len(value) > 120:
            raise ValueError("search criteria must be text of at most 120 characters")
    violations = validate_catalog(root)
    if violations:
        raise ValueError("extension source index did not pass strict validation")
    payload = json.loads((root / "ecosystem" / "sources.json").read_text(encoding="utf-8"))
    found = []
    for entry in payload["sources"]:
        searchable = " ".join((
            entry["id"], entry["repository"], entry["category"], entry["purpose"]
        )).casefold()
        if query.casefold().strip() not in searchable:
            continue
        if category and entry["category"] != category:
            continue
        if reported_license and entry["reported_repository_license"] != reported_license:
            continue
        found.append({
            "id": entry["id"],
            "repository": entry["repository"],
            "url": entry["url"],
            "category": entry["category"],
            "purpose": entry["purpose"],
            "reportedLicense": entry["reported_repository_license"],
            "licenseReview": "NOT_REVIEWED",
            "installed": False,
            "enabled": False,
            "executionEnabled": False,
            "trust": "UNTRUSTED_EXTERNAL",
            "status": "REFERENCE_ONLY",
        })
    return {
        "schemaVersion": 1,
        "source": "CURATED_OFFLINE_GITHUB_METADATA",
        "totalMatched": len(found),
        "results": found[:limit],
        "autoInstallation": False,
        "networkUsed": False,
        "claimOfAvailability": False,
        "requiresOwnerApprovalAndIndependentReview": True,
        "physicalPcStatus": "BLOCKED_PENDING_HARDWARE",
    }

def main() -> int:
    parser = argparse.ArgumentParser(description="Search staged reference sources, never install or execute them")
    parser.add_argument("--query", default="")
    parser.add_argument("--category", default="")
    parser.add_argument("--reported-license", default="")
    parser.add_argument("--limit", type=int, default=15)
    args = parser.parse_args()
    try:
        result = search_sources(
            query=args.query, category=args.category,
            reported_license=args.reported_license, limit=args.limit
        )
    except ValueError as exc:
        parser.error(str(exc))
    print(json.dumps(result, indent=2))
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
