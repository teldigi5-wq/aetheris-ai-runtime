#!/usr/bin/env python3
"""Offline structural validation for the E6 provisional npm lockfile.

This validates dependency source/integrity metadata; it does NOT replace an
npm registry vulnerability audit or prove downloaded artifact authenticity.
"""
import json
import re
from pathlib import Path
from urllib.parse import urlsplit

APP = Path(__file__).resolve().parents[1]
INTEGRITY = re.compile(r"^sha512-[A-Za-z0-9+/]+={0,2}$")


def validate(manifest: dict, lock: dict) -> int:
    if lock.get("lockfileVersion") != 3 or lock.get("requires") is not True:
        raise ValueError("npm lockfile v3 with requires=true is mandatory")
    packages = lock.get("packages")
    if not isinstance(packages, dict) or not packages:
        raise ValueError("missing lockfile package graph")
    root = packages.get("")
    if not isinstance(root, dict):
        raise ValueError("lockfile root is missing")
    if root.get("name") != manifest.get("name") or root.get("version") != manifest.get("version"):
        raise ValueError("manifest/lockfile root identity mismatch")
    for group in ("dependencies", "devDependencies"):
        declared = manifest.get(group, {})
        if root.get(group, {}) != declared:
            raise ValueError(f"manifest/lockfile {group} mismatch")
        for name, version in declared.items():
            if not re.fullmatch(r"\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?", version):
                raise ValueError(f"non-exact direct dependency: {name}")
    count = 0
    for location, entry in packages.items():
        if location == "":
            continue
        if not location.startswith("node_modules/") or not isinstance(entry, dict):
            raise ValueError(f"unexpected package location: {location}")
        resolved = entry.get("resolved")
        url = urlsplit(resolved) if isinstance(resolved, str) else None
        if not url or url.scheme != "https" or url.netloc != "registry.npmjs.org":
            raise ValueError(f"non-approved package source: {location}")
        if not INTEGRITY.fullmatch(entry.get("integrity", "")):
            raise ValueError(f"missing/invalid SHA-512 integrity: {location}")
        if entry.get("link"):
            raise ValueError(f"symlink package disallowed: {location}")
        if not isinstance(entry.get("version"), str):
            raise ValueError(f"missing package version: {location}")
        count += 1
    if not count:
        raise ValueError("empty dependency graph")
    return count


def main() -> None:
    manifest = json.loads((APP / "package.json").read_text(encoding="utf-8"))
    lock = json.loads((APP / "package-lock.json").read_text(encoding="utf-8"))
    count = validate(manifest, lock)
    print(f"E6_LOCKFILE_METADATA_VALIDATED packages={count} registry=npmjs sha512")


if __name__ == "__main__":
    main()
