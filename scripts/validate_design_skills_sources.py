#!/usr/bin/env python3
"""Offline validation only. Never fetch, install, import or run vendor code."""
from __future__ import annotations
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PATH = ROOT / "ecosystem" / "design_skills_sources.json"
EXPECTED = {
    "taste-skill": [
        "tasteskill/tasteskill",
        "37c8c376b92ebc02456f7c70776b514fddda88e1",
        "MIT",
        "frontend-design",
        "skills/taste-skill/SKILL.md"
    ],
    "impeccable": [
        "pbakaus/impeccable",
        "245ad82def45831dc56a21b683c7a1ff530b06cc",
        "Apache-2.0",
        "frontend-qa",
        ".agents/skills/impeccable/SKILL.md"
    ],
    "playwright-cli": [
        "microsoft/playwright-cli",
        "b85c7a736bb473bf55b584e54a09ffa698d6d871",
        "Apache-2.0",
        "browser-automation",
        "README.md"
    ],
    "awesome-design-skills": [
        "bergside/awesome-design-skills",
        "f631a09b4fcc0166f2e2c1a8c81906ef680c57e8",
        "MIT",
        "design-registry",
        "skills/index.json"
    ],
    "img2threejs": [
        "img2threejs/img2threejs",
        "d508b596cb221f1509f6e50ab68fce275a5cfb44",
        "Apache-2.0",
        "procedural-3d",
        "SKILL.md"
    ]
}
TOP = {"schema_version","scope","owner_pc_status","catalog_status","auto_fetch","auto_install","auto_execute","owner_approval_required","credential_grants","filesystem_grants","ci_runs_external_tools","sources"}
ENTRY = {"id","name","repository","revision","license","category","source_path","url","commit_url","trust","license_review","installed","enabled","execution_enabled","runtime_verified"}

def validate_data(data: object) -> list[str]:
    errors: list[str] = []
    if not isinstance(data, dict) or set(data) != TOP:
        return ["unexpected manifest structure"]
    if type(data["schema_version"]) is not int or data["schema_version"] != 1:
        errors.append("unexpected schema version")
    for key, required in {"scope":"EXTERNAL_DESIGN_SKILLS_INSTALL_PROPOSAL",
                          "owner_pc_status":"BLOCKED_PENDING_HARDWARE",
                          "catalog_status":"SOURCE_SNAPSHOTS_ONLY_NOT_INSTALLED"}.items():
        if data[key] != required: errors.append("invalid " + key)
    for key in ("auto_fetch","auto_install","auto_execute","credential_grants",
                "filesystem_grants","ci_runs_external_tools"):
        if data[key] is not False: errors.append(key + " must be false")
    if data["owner_approval_required"] is not True:
        errors.append("owner approval required")
    sources = data["sources"]
    if not isinstance(sources,list) or len(sources) != len(EXPECTED):
        return errors + ["source count mismatch"]
    seen = set()
    for entry in sources:
        if not isinstance(entry,dict) or set(entry) != ENTRY:
            errors.append("unexpected entry structure")
            continue
        ident = entry["id"]
        if not isinstance(ident,str) or ident in seen or ident not in EXPECTED:
            errors.append("unknown or duplicate source")
            continue
        seen.add(ident)
        repo, rev, license_id, category, path = EXPECTED[ident]
        for key, wanted in {"repository":repo,"revision":rev,
                            "license":license_id,"category":category,
                            "source_path":path,
                            "url":"https://github.com/"+repo,
                            "commit_url":"https://github.com/"+repo+"/commit/"+rev,
                            "trust":"UNTRUSTED_EXTERNAL",
                            "license_review":"PENDING"}.items():
            if entry[key] != wanted: errors.append(ident + ": " + key + " differs from reviewed snapshot")
        if not isinstance(entry["name"],str) or not 1<=len(entry["name"])<=60:
            errors.append(ident + ": invalid name")
        for field in ("installed","enabled","execution_enabled","runtime_verified"):
            if entry[field] is not False: errors.append(ident + ": " + field + " must be false")
    if seen != set(EXPECTED): errors.append("missing expected source")
    return errors

def validate_file(path: Path = PATH) -> list[str]:
    if path.is_symlink() or not path.is_file() or path.stat().st_size > 32_768:
        return ["missing, symlinked or oversized manifest"]
    try:
        return validate_data(json.loads(path.read_text(encoding="utf-8")))
    except (OSError,ValueError,UnicodeError,TypeError):
        return ["invalid UTF-8 or JSON"]

if __name__ == "__main__":
    errors = validate_file()
    for error in errors: print("ERROR: " + error)
    print("PASS: five pinned, untrusted, NOT-INSTALLED sources; no activation" if not errors else "FAIL")
    raise SystemExit(1 if errors else 0)
