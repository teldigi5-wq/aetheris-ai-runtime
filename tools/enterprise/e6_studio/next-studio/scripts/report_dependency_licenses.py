#!/usr/bin/env python3
"""Offline npm license inventory from a tracked lockfile and CI CycloneDX SBOM.
Review queue only: not a legal opinion, license clearance or release approval.
"""
from __future__ import annotations
import hashlib
import json
import sys
from pathlib import Path

APP = Path(__file__).resolve().parents[1]


def build_report(lock: dict, bom: dict, lock_bytes: bytes) -> dict:
    if lock.get("lockfileVersion") != 3 or not isinstance(lock.get("packages"), dict):
        raise ValueError("E6_LOCK_METADATA_INVALID")
    if bom.get("bomFormat") != "CycloneDX" or bom.get("specVersion") != "1.5":
        raise ValueError("E6_UNSUPPORTED_SBOM_FORMAT")
    components = bom.get("components")
    if not isinstance(components, list) or not components:
        raise ValueError("E6_SBOM_COMPONENTS_MISSING")
    packages = {
        path.removeprefix("node_modules/"): data
        for path, data in lock["packages"].items()
        if path.startswith("node_modules/")
    }
    if len(packages) != len(lock["packages"]) - 1:
        raise ValueError("E6_NESTED_OR_NONSTANDARD_LOCK_PACKAGE_PATH")
    missing, non_spdx, reciprocal = [], [], []
    for name, item in sorted(packages.items()):
        license_id = item.get("license")
        if not isinstance(license_id, str) or not license_id.strip():
            missing.append(name)
        elif license_id.startswith("Standard 'no charge' license:"):
            non_spdx.append({"name": name, "version": item["version"], "license": license_id})
        elif "LGPL" in license_id or "GPL" in license_id or "AGPL" in license_id:
            reciprocal.append({"name": name, "version": item["version"], "license": license_id,
                               "optional": bool(item.get("optional", False))})
    checked = set()
    bom_missing_metadata = []
    for component in components:
        name, version = component.get("name"), component.get("version")
        if not isinstance(name, str) or not isinstance(version, str):
            raise ValueError("E6_SBOM_INCOMPLETE_COMPONENT")
        key = f"{name}@{version}"
        if key in checked:
            raise ValueError("E6_SBOM_DUPLICATE_COMPONENT")
        checked.add(key)
        locked = packages.get(name)
        if locked is None or locked.get("version") != version:
            raise ValueError(f"E6_SBOM_NOT_LOCKED: {key}")
        if not component.get("licenses"):
            bom_missing_metadata.append(name)
    return {
        "kind": "E6_NPM_LICENSE_INVENTORY_REVIEW_ONLY",
        "lockSha256": hashlib.sha256(lock_bytes).hexdigest(),
        "lockedPackageCount": len(packages),
        "installedCiSbomComponentCount": len(checked),
        "missingLockLicenseNames": missing,
        "nonSpdxLockLicenses": non_spdx,
        "reciprocalLicensePackages": reciprocal,
        "missingSbomLicenseNames": sorted(bom_missing_metadata),
        "notes": [
            "Some optional packages are platform-specific and not installed on this CI runner.",
            "SBOM covers the CI installation only, not every possible target operating system.",
            "Custom license metadata needs separate human review; missing SPDX does not prove unlicensed software.",
            "LGPL and other reciprocal notices need distribution/linking and notice review.",
            "No independent legal review, asset licenses, or production distribution certification was performed."
        ],
        "gsapOfficialLicenseReference": "https://gsap.com/standard-license",
        "ownerReleaseApproval": False,
        "stage45Go": False,
        "releaseStatus": "REVIEW_REQUIRED",
    }


def main() -> None:
    if len(sys.argv) != 3:
        raise SystemExit("usage: report_dependency_licenses.py SBOM_PATH OUTPUT_PATH")
    sbom_path, output_path = map(Path, sys.argv[1:])
    lock_bytes = (APP / "package-lock.json").read_bytes()
    report = build_report(
        json.loads(lock_bytes),
        json.loads(sbom_path.read_text(encoding="utf-8")),
        lock_bytes,
    )
    if output_path.is_symlink() or output_path.exists():
        raise SystemExit("E6_LICENSE_REPORT_NO_REPLACE")
    output_path.write_text(json.dumps(report, sort_keys=True, indent=2) + "\n", encoding="utf-8")
    print(f"E6_LICENSE_INVENTORY_ONLY locked={report['lockedPackageCount']} "
          f"installed={report['installedCiSbomComponentCount']} "
          f"reviewItems={len(report['reciprocalLicensePackages'])+len(report['nonSpdxLockLicenses'])} "
          "stage45=NO_GO")


if __name__ == "__main__":
    main()
