#!/usr/bin/env python3
"""Static-only inspection of a user-supplied, untrusted Agent Skill directory.

Never fetches, installs, imports, executes or copies third-party code.
A 'no findings' report is NOT a trust decision or permission to activate.
"""
from __future__ import annotations
import argparse
import json
import re
from pathlib import Path

NAME = re.compile(r"^[a-z0-9]+(?:-[a-z0-9]+)*$", re.ASCII)
FRONT = re.compile(r"\A---\n(?P<front>.*?)\n---\n", re.DOTALL)
SUSPICIOUS = re.compile(
    r"(?i)(ignore (?:all |any )?(?:previous|above|system|developer) instructions|"
    r"bypass (?:approval|policy|security)|reveal (?:the )?(?:api key|token|secret)|"
    r"curl\s+|wget\s+|pip\s+install|npm\s+install|npx\s+|"
    r"powershell\s+-|chmod\s+\+x|sudo\s+|os\.system|subprocess\.)"
)
ALLOWED_SUFFIX = {".md", ".txt", ".json"}

def report(findings: list[str], count: int = 0, size: int = 0) -> dict[str, object]:
    return {
        "schemaVersion": 1,
        "inspectionMode": "OFFLINE_STATIC_ONLY",
        "reviewResult": "NEEDS_SECURITY_REVIEW" if findings else "STATIC_CONSTRAINTS_PASSED_NOT_APPROVED",
        "trust": "UNTRUSTED_EXTERNAL",
        "installAllowed": False,
        "executionAllowed": False,
        "autoImportAllowed": False,
        "networkUsed": False,
        "ownerApprovalRequired": True,
        "physicalPcStatus": "BLOCKED_PENDING_HARDWARE",
        "reviewedFileCount": count,
        "reviewedBytes": size,
        "findings": sorted(set(findings)),
    }

def inspect_bundle(folder: Path) -> dict[str, object]:
    findings: list[str] = []
    try:
        if folder.is_symlink() or not folder.is_dir():
            return report(["not a regular non-symlink directory"])
        items = list(folder.rglob("*"))
        if len(items) > 32:
            findings.append("too many bundle entries")
        files: list[Path] = []
        total = 0
        for item in items:
            if item.is_symlink():
                findings.append("symlink in bundle")
                continue
            if item.is_dir():
                if item.name.startswith(".") or item.name in {"scripts", "bin", "node_modules", ".git"}:
                    findings.append("hidden/executable directory")
                continue
            if not item.is_file():
                findings.append("special filesystem entry")
                continue
            rel = item.relative_to(folder)
            if any(part.startswith(".") for part in rel.parts):
                findings.append("hidden file")
            if item.suffix.lower() not in ALLOWED_SUFFIX:
                findings.append("executable or non-document file")
            size = item.stat().st_size
            total += size
            if size > 48_000:
                findings.append("oversized file")
            files.append(item)
        if total > 256_000:
            findings.append("oversized bundle")
        main = folder / "SKILL.md"
        if main.is_symlink() or main not in files:
            findings.append("missing safe SKILL.md")
        elif main.stat().st_size <= 48_000:
            try:
                body = main.read_text(encoding="utf-8")
            except (UnicodeError, OSError):
                findings.append("SKILL.md not UTF-8")
            else:
                m = FRONT.match(body)
                if m is None:
                    findings.append("missing frontmatter")
                else:
                    meta: dict[str, str] = {}
                    for line in m.group("front").splitlines():
                        if ":" not in line:
                            findings.append("unreviewable frontmatter")
                            continue
                        key, value = (x.strip() for x in line.split(":", 1))
                        if key in meta:
                            findings.append("duplicate frontmatter field")
                        meta[key] = value
                    name, description = meta.get("name", ""), meta.get("description", "")
                    if not NAME.fullmatch(name) or name != folder.name or len(name) > 64:
                        findings.append("invalid skill name")
                    if not 1 <= len(description) <= 1024:
                        findings.append("invalid description")
                    if set(meta) != {"name", "description"}:
                        findings.append("unreviewed frontmatter fields")
                if SUSPICIOUS.search(body):
                    findings.append("possible instruction injection or executable command")
                if chr(96) * 3 in body:
                    findings.append("fenced content requires manual review")
        return report(findings, len(files), total)
    except (OSError, ValueError):
        return report(["inspection failed closed"])

if __name__ == "__main__":
    args = argparse.ArgumentParser(description="Review an already-local Agent Skill without execution")
    args.add_argument("--folder", type=Path, required=True)
    opts = args.parse_args()
    result = inspect_bundle(opts.folder)
    print(json.dumps(result, indent=2))
    if result["findings"]:
        raise SystemExit(2)
