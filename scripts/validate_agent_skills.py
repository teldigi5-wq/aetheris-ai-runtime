#!/usr/bin/env python3
"""Fail-closed validation of declarative Agent Skills; does NOT enable execution."""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CAPABILITIES = {
    "github-readonly": {
        "tools": {"github.repo.read", "github.commit.read", "github.pr.read", "github.checks.read"},
        "hosts": {"github.com", "api.github.com"},
    },
    "incident-investigation": {
        "tools": {"observability.health.read", "observability.metrics.read", "observability.logs.read", "observability.traces.read"},
        "hosts": set(),
    },
}
SKILL_NAME = re.compile(r"^[a-z][a-z0-9]*(?:-[a-z0-9]+)*$")
FRONTMATTER = re.compile(r"\A---\nname: ([a-z0-9-]+)\ndescription: ([^\n]+)\n---\n", re.ASCII)

def validate_skills(root: Path = ROOT) -> list[str]:
    errors: list[str] = []
    catalog_path = root / "skills" / "catalog.json"
    try:
        catalog = json.loads(catalog_path.read_text(encoding="utf-8"))
    except (OSError, ValueError) as exc:
        return [f"catalog unreadable: {exc}"]
    if set(catalog) != {"schema_version", "catalog_status", "execution_enabled", "physical_pc_status", "policy_authority", "skills"}:
        errors.append("catalog schema contains missing/unknown properties")
    if type(catalog.get("schema_version")) is not int or catalog.get("schema_version") != 1:
        errors.append("unsupported catalog schema")
    if catalog.get("catalog_status") != "DECLARATIVE_ONLY" or catalog.get("execution_enabled") is not False:
        errors.append("catalog must be non-executable and declarative")
    if catalog.get("physical_pc_status") != "BLOCKED_PENDING_HARDWARE":
        errors.append("hardware boundary changed")
    if not isinstance(catalog.get("policy_authority"), str) or not catalog["policy_authority"].strip():
        errors.append("deterministic policy authority missing")
    skills = catalog.get("skills")
    if not isinstance(skills, list) or len(skills) != len(CAPABILITIES):
        return errors + ["skill registry missing or unapproved skill count"]
    seen = set()
    expected_fields = {
        "name", "path", "description", "risk", "effect", "status",
        "execution_enabled", "allowed_tools", "external_hosts",
        "owner_approval_required_for_mutations", "max_evidence_items",
    }
    for entry in skills:
        if not isinstance(entry, dict):
            errors.append("entry not an object")
            continue
        if set(entry) != expected_fields:
            errors.append("invalid manifest fields")
            continue
        name = entry["name"]
        if not isinstance(name, str) or not SKILL_NAME.fullmatch(name) or name not in CAPABILITIES:
            errors.append("unapproved skill name")
            continue
        if name in seen:
            errors.append("duplicate skill")
        seen.add(name)
        expected_path = f"skills/{name}/SKILL.md"
        if entry["path"] != expected_path or entry["path"].startswith("/"):
            errors.append(f"{name}: skill path outside approved directory")
            continue
        if entry["risk"] != "LOW" or entry["effect"] != "READ_ONLY" or entry["status"] != "TEMPLATE_ONLY":
            errors.append(f"{name}: capability or validation gate expanded")
        if entry["execution_enabled"] is not False or entry["owner_approval_required_for_mutations"] is not True:
            errors.append(f"{name}: execution/approval policy widened")
        if type(entry["max_evidence_items"]) is not int or not 1 <= entry["max_evidence_items"] <= 20:
            errors.append(f"{name}: unbounded evidence")
        if not isinstance(entry["description"], str) or not 1 <= len(entry["description"]) <= 500:
            errors.append(f"{name}: invalid description")
        for field, allowed in (("allowed_tools", CAPABILITIES[name]["tools"]), ("external_hosts", CAPABILITIES[name]["hosts"])):
            vals = entry[field]
            if not isinstance(vals, list) or any(not isinstance(x, str) for x in vals) or len(vals) != len(set(vals)):
                errors.append(f"{name}: malformed {field}")
            elif set(vals) != allowed:
                errors.append(f"{name}: {field} outside pinned allowlist")
        path = root / expected_path
        if path.is_symlink() or not path.is_file():
            errors.append(f"{name}: missing or symlink SKILL.md")
            continue
        try:
            content = path.read_text(encoding="utf-8")
        except (UnicodeError, OSError):
            errors.append(f"{name}: invalid UTF-8 skill")
            continue
        m = FRONTMATTER.match(content)
        if not m or m.group(1) != name or not m.group(2).strip():
            errors.append(f"{name}: invalid Agent Skills frontmatter")
        if len(content) > 12000 or "TEMPLATE_ONLY" not in content or "untrusted data" not in content.lower():
            errors.append(f"{name}: skill safety/truth markers missing")
        if re.search(r"(?m)^\\`\\`\\`(?:bash|sh|powershell|pwsh|python|javascript)", content):
            errors.append(f"{name}: executable fenced content prohibited")
    if seen != set(CAPABILITIES):
        errors.append("missing pinned skills")
    skills_dir = root / "skills"
    expected_files = {"catalog.json"} | {f"{name}/SKILL.md" for name in CAPABILITIES}
    for path in skills_dir.rglob("*"):
        if path.is_symlink():
            errors.append("symlink is prohibited in skills catalog")
        elif path.is_file() and path.relative_to(skills_dir).as_posix() not in expected_files:
            errors.append("unapproved script or file in skills catalog")
    return errors

if __name__ == "__main__":
    problems = validate_skills()
    if problems:
        for issue in problems:
            print("FAIL:", issue, file=sys.stderr)
        raise SystemExit(1)
    print("Agent Skills catalog: validated, declarative only, not wired")
