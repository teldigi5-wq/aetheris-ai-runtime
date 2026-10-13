#!/usr/bin/env python3
"""Fail-closed, offline verification of REFERENCE-ONLY global extension metadata.

This tool does not contact GitHub, MCP registries, pip, npm, or any execution
runtime. Review status and permissions cannot be promoted by editing JSON.
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
# Exact trusted research index. Adding a third-party source REQUIRES a reviewed
# code change to this table, never a data-only change.
APPROVED_SOURCES = {
    "agentskills-standard": ("agentskills/agentskills", "skill-format", "Apache-2.0"),
    "official-mcp-registry": ("modelcontextprotocol/registry", "mcp-discovery", "NOASSERTION"),
    "mcp-reference-servers": ("modelcontextprotocol/servers", "mcp-examples", "NOASSERTION"),
    "github-mcp": ("github/github-mcp-server", "source-control", "MIT"),
    "playwright-mcp": ("microsoft/playwright-mcp", "browser-automation", "Apache-2.0"),
    "ollama": ("ollama/ollama", "local-llm", "MIT"),
    "comfyui": ("Comfy-Org/ComfyUI", "local-media", "GPL-3.0"),
    "faster-whisper": ("SYSTRAN/faster-whisper", "local-speech", "MIT"),
    "piper-tts": ("OHF-Voice/piper1-gpl", "local-speech", "GPL-3.0"),
    "qdrant": ("qdrant/qdrant", "local-memory", "Apache-2.0"),
    "chroma": ("chroma-core/chroma", "local-memory", "Apache-2.0"),
    "langchain": ("langchain-ai/langchain", "orchestration-library", "MIT"),
    "llama-index": ("run-llama/llama_index", "retrieval-library", "MIT"),
    "n8n": ("n8n-io/n8n", "workflow-automation", "NOASSERTION"),
}
TOP_LEVEL_KEYS = {
    "schema_version", "registry_name", "checked_on", "catalog_status",
    "auto_discovery", "network_fetch_during_runtime", "automatic_installation",
    "automatic_execution", "paid_services_enabled", "physical_pc_status",
    "policy_authority", "sources",
}
ENTRY_KEYS = {
    "id", "repository", "url", "category", "purpose",
    "reported_repository_license", "license_status",
    "dependency_and_model_licenses_reviewed", "source_revision", "trust",
    "status", "installed", "enabled", "execution_enabled", "auto_update",
    "owner_install_approval",
}
FALSE_ROOT = {
    "auto_discovery", "network_fetch_during_runtime", "automatic_installation",
    "automatic_execution", "paid_services_enabled",
}
FALSE_ENTRY = {
    "dependency_and_model_licenses_reviewed", "installed", "enabled",
    "execution_enabled", "auto_update", "owner_install_approval",
}

def validate_catalog(root: Path = ROOT) -> list[str]:
    errors: list[str] = []
    path = root / "ecosystem" / "sources.json"
    if path.is_symlink() or not path.is_file() or path.stat().st_size > 64_000:
        return ["research index missing, symlinked or exceeds size limit"]
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (ValueError, OSError, UnicodeError) as exc:
        return [f"unreadable research index: {type(exc).__name__}"]
    if not isinstance(data, dict):
        return ["research index must be a JSON object"]
    if set(data) != TOP_LEVEL_KEYS:
        errors.append("unexpected or missing research index fields")
    if type(data.get("schema_version")) is not int or data["schema_version"] != 1:
        errors.append("unsupported research index schema")
    if data.get("catalog_status") != "RESEARCH_AND_QUARANTINE_ONLY":
        errors.append("catalog cannot claim integration or enablement")
    if data.get("physical_pc_status") != "BLOCKED_PENDING_HARDWARE":
        errors.append("physical GPU truth boundary changed")
    if any(data.get(field) is not False for field in FALSE_ROOT):
        errors.append("automatic fetch/install/execution, or paid providers are disallowed")
    if not isinstance(data.get("registry_name"), str) or not data["registry_name"].strip():
        errors.append("registry label missing")
    if not isinstance(data.get("checked_on"), str) or not re.fullmatch(r"\d{4}-\d{2}-\d{2}", data["checked_on"]):
        errors.append("research snapshot date missing")
    if data.get("policy_authority") != "existing deterministic owner approval, MCP grants and host execution policy":
        errors.append("policy authority drifted")
    sources = data.get("sources")
    if not isinstance(sources, list) or len(sources) != len(APPROVED_SOURCES):
        return errors + ["unapproved, missing or duplicated source count"]
    names: set[str] = set()
    repos: set[str] = set()
    for source in sources:
        if not isinstance(source, dict):
            errors.append("source must be an object")
            continue
        if set(source) != ENTRY_KEYS:
            errors.append("unknown/missing source fields")
            continue
        name = source.get("id")
        if not isinstance(name, str) or name not in APPROVED_SOURCES:
            errors.append("non-allowlisted extension source")
            continue
        if name in names:
            errors.append(f"{name}: duplicate catalog id")
        names.add(name)
        repository, category, reported_license = APPROVED_SOURCES[name]
        if repository in repos:
            errors.append("duplicate upstream repository")
        repos.add(repository)
        if (source["repository"], source["category"], source["reported_repository_license"]) != (
            repository, category, reported_license
        ):
            errors.append(f"{name}: source identity/license metadata mismatch")
        if source["url"] != "https://github.com/" + repository:
            errors.append(f"{name}: source URL does not match pinned GitHub allowlist")
        if source["source_revision"] is not None:
            errors.append(f"{name}: reference-only registry cannot claim pinned or validated revision")
        if source["license_status"] != "NOT_REVIEWED" or source["status"] != "REFERENCE_ONLY" or source["trust"] != "UNTRUSTED_EXTERNAL":
            errors.append(f"{name}: source granted trust without audit")
        if any(source.get(field) is not False for field in FALSE_ENTRY):
            errors.append(f"{name}: implicit install/activation/approval is prohibited")
        if not isinstance(source["purpose"], str) or not 10 <= len(source["purpose"]) <= 240:
            errors.append(f"{name}: purpose missing or excessive")
    if names != set(APPROVED_SOURCES):
        errors.append("pinned extension sources missing")
    catalog_dir = root / "ecosystem"
    allowed = {"README.md", "sources.json"}
    for file in catalog_dir.rglob("*"):
        if file.is_symlink():
            errors.append("symlink in research index")
        elif file.is_file() and file.relative_to(catalog_dir).as_posix() not in allowed:
            errors.append("executable or unreviewed file in ecosystem folder")
    return errors

if __name__ == "__main__":
    errors = validate_catalog()
    if errors:
        for error in errors:
            print("FAIL:", error, file=sys.stderr)
        raise SystemExit(1)
    print("14 researched candidate repositories; zero imports, installs, enabled plugins or active tools")
