#!/usr/bin/env python3
"""Destination-owned Stage 26 runtime safety certification.

This verifier is offline, deterministic, and standard-library-only. It inspects
runtime-owned repository source contracts and never starts services, opens
sockets, runs shell commands, invokes tools/models, or performs financial actions.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[2]
DEFAULT_CONFIG = ROOT / "configs" / "stage26" / "runtime-safety-scenarios.json"


class CertificationError(RuntimeError):
    pass


def canonical_json(value: Any) -> bytes:
    return (json.dumps(value, sort_keys=True, indent=2, ensure_ascii=False) + "\n").encode("utf-8")


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def sha256_file(path: Path) -> str:
    return sha256_bytes(path.read_bytes())


def safe_repo_path(relative: str) -> Path:
    if not isinstance(relative, str) or not relative.strip():
        raise CertificationError("scenario file path must be a non-empty string")
    root = ROOT.resolve()
    candidate = (ROOT / relative).resolve()
    try:
        candidate.relative_to(root)
    except ValueError as exc:
        raise CertificationError(f"scenario path escapes repository root: {relative}") from exc
    if not candidate.is_file():
        raise CertificationError(f"scenario evidence file does not exist: {relative}")
    return candidate


def load_config(path: Path = DEFAULT_CONFIG) -> dict[str, Any]:
    try:
        config = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise CertificationError(f"unable to load Stage 26 runtime config: {exc}") from exc

    if config.get("schema_version") != 1 or config.get("stage") != 26:
        raise CertificationError("runtime Stage 26 config must declare schema_version=1 and stage=26")
    if config.get("ownership") != "AI_RUNTIME":
        raise CertificationError("runtime Stage 26 config must declare ownership=AI_RUNTIME")
    if config.get("simulation_only") is not True:
        raise CertificationError("runtime Stage 26 must remain simulation_only=true")
    if config.get("live_side_effects") is not False:
        raise CertificationError("runtime Stage 26 must remain live_side_effects=false")
    if config.get("physical_pc_status") != "BLOCKED_PENDING_HARDWARE":
        raise CertificationError("runtime Stage 26 must preserve BLOCKED_PENDING_HARDWARE")

    scenarios = config.get("scenarios")
    if not isinstance(scenarios, list) or len(scenarios) != 10:
        raise CertificationError("runtime Stage 26 requires exactly ten migrated runtime safety scenarios")

    seen: set[str] = set()
    for scenario in scenarios:
        if not isinstance(scenario, dict):
            raise CertificationError("every runtime Stage 26 scenario must be an object")
        scenario_id = scenario.get("id")
        if not isinstance(scenario_id, str) or not scenario_id.strip():
            raise CertificationError("every runtime Stage 26 scenario requires a non-empty id")
        if scenario_id in seen:
            raise CertificationError(f"duplicate runtime Stage 26 scenario id: {scenario_id}")
        seen.add(scenario_id)
        safe_repo_path(str(scenario.get("file", "")))
        for key in ("must_contain", "must_not_contain"):
            markers = scenario.get(key, [])
            if not isinstance(markers, list) or any(not isinstance(v, str) or not v for v in markers):
                raise CertificationError(f"{scenario_id}: {key} must be a list of non-empty strings")
    return config


def evaluate_scenario(scenario: dict[str, Any]) -> dict[str, Any]:
    relative = scenario["file"]
    path = safe_repo_path(relative)
    text = path.read_text(encoding="utf-8")
    required = list(scenario.get("must_contain", []))
    forbidden = list(scenario.get("must_not_contain", []))
    missing = [marker for marker in required if marker not in text]
    forbidden_present = [marker for marker in forbidden if marker in text]
    passed = not missing and not forbidden_present
    return {
        "id": scenario["id"],
        "category": scenario.get("category", "unspecified"),
        "status": "PASS" if passed else "FAIL",
        "evidence_file": relative,
        "evidence_sha256": sha256_file(path),
        "required_marker_count": len(required),
        "forbidden_marker_count": len(forbidden),
        "missing_markers": missing,
        "forbidden_markers_present": forbidden_present,
    }


def certify(config: dict[str, Any], *, config_path: Path = DEFAULT_CONFIG) -> dict[str, Any]:
    results = sorted((evaluate_scenario(item) for item in config["scenarios"]), key=lambda item: item["id"])
    failures = [item for item in results if item["status"] != "PASS"]
    return {
        "schema_version": 1,
        "stage": 26,
        "ownership": "AI_RUNTIME",
        "certification": "PASS" if not failures else "FAIL",
        "simulation_only": True,
        "live_side_effects": False,
        "external_action_attempted": False,
        "physical_pc_status": "BLOCKED_PENDING_HARDWARE",
        "network_required": False,
        "shell_execution_required": False,
        "scenario_count": len(results),
        "pass_count": len(results) - len(failures),
        "fail_count": len(failures),
        "config_sha256": sha256_file(config_path),
        "results": results,
    }


def write_evidence(report: dict[str, Any], output_dir: Path) -> None:
    output_dir.mkdir(parents=True, exist_ok=True)
    report_path = output_dir / "runtime-safety-report.json"
    report_path.write_bytes(canonical_json(report))
    rows = [f"{sha256_file(report_path)}  runtime-safety-report.json"]
    rows.append(f"{report['config_sha256']}  configs/stage26/runtime-safety-scenarios.json")
    for relative in sorted({item['evidence_file'] for item in report['results']}):
        rows.append(f"{sha256_file(safe_repo_path(relative))}  {relative}")
    (output_dir / "manifest.sha256").write_text("\n".join(rows) + "\n", encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description="Destination-owned Stage 26 runtime safety certification")
    parser.add_argument("--config", type=Path, default=DEFAULT_CONFIG)
    parser.add_argument("--output-dir", type=Path, required=True)
    args = parser.parse_args()
    try:
        config = load_config(args.config)
        report = certify(config, config_path=args.config)
        write_evidence(report, args.output_dir)
    except CertificationError as exc:
        print(f"Stage 26 runtime certification error: {exc}", file=sys.stderr)
        return 2
    print(json.dumps({"certification": report["certification"], "pass_count": report["pass_count"], "fail_count": report["fail_count"]}, sort_keys=True))
    return 0 if report["certification"] == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
