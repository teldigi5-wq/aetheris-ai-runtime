#!/usr/bin/env python3
"""Stage 28 deterministic PC-care diagnostic and remediation planner.

Pre-PC CI mode consumes synthetic JSON snapshots only. It does not inspect the
runner host, start subprocesses, use the network, delete files, change settings,
or execute repairs. Mutating actions remain a future owner-approved hardware step.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[2]
DEFAULT_POLICY = ROOT / "configs" / "stage28" / "pc-care-policy.json"
SEVERITY_RANK = {"HEALTHY": 0, "WARNING": 1, "CRITICAL": 2}


class PcCareError(RuntimeError):
    pass


def load_json(path: Path) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise PcCareError(f"unable to load JSON from {path}: {exc}") from exc
    if not isinstance(value, dict):
        raise PcCareError(f"expected JSON object in {path}")
    return value


def validate_policy(policy: dict[str, Any]) -> None:
    if policy.get("schema_version") != 1 or policy.get("stage") != 28:
        raise PcCareError("Stage 28 policy must declare schema_version=1 and stage=28")
    if policy.get("mode") != "PRE_PC_SYNTHETIC_ONLY":
        raise PcCareError("Stage 28 pre-PC policy mode changed unexpectedly")
    if policy.get("physical_pc_status") != "BLOCKED_PENDING_HARDWARE":
        raise PcCareError("Stage 28 must not claim physical-PC validation")

    remediation = policy.get("remediation", {})
    if remediation.get("recommendations_only") is not True:
        raise PcCareError("Stage 28 must remain recommendations-only")
    if remediation.get("owner_approval_required_for_mutation") is not True:
        raise PcCareError("host mutation must require explicit owner approval")
    for key, value in sorted(remediation.items()):
        if key.endswith("_allowed") and value is not False:
            raise PcCareError(f"pre-PC mutation boundary must remain false: {key}")

    boundaries = policy.get("truth_boundaries", {})
    if not boundaries or any(value is not False for value in boundaries.values()):
        raise PcCareError("all Stage 28 pre-PC truth boundaries must remain false")


def validate_snapshot(snapshot: dict[str, Any], policy: dict[str, Any]) -> None:
    source = snapshot.get("source")
    if source not in policy.get("allowed_ci_snapshot_sources", []):
        raise PcCareError(f"CI snapshot source is not allowed: {source!r}")
    if not isinstance(snapshot.get("snapshot_id"), str) or not snapshot["snapshot_id"].strip():
        raise PcCareError("snapshot_id is required")
    metrics = snapshot.get("metrics")
    if not isinstance(metrics, dict):
        raise PcCareError("metrics object is required")

    required = set(policy.get("thresholds", {}))
    missing = sorted(required - set(metrics))
    if missing:
        raise PcCareError(f"snapshot is missing required metrics: {missing}")

    for name in sorted(required):
        value = metrics[name]
        if not isinstance(value, (int, float)) or isinstance(value, bool):
            raise PcCareError(f"metric {name} must be numeric")
        if name.endswith("_percent") and not 0 <= float(value) <= 100:
            raise PcCareError(f"metric {name} must be between 0 and 100")
        if name == "temperature_c" and not -30 <= float(value) <= 130:
            raise PcCareError("temperature_c is outside the accepted diagnostic range")


def metric_severity(value: float, threshold: dict[str, Any]) -> str:
    direction = threshold.get("direction")
    warning = float(threshold["warning"])
    critical = float(threshold["critical"])
    if direction == "high_bad":
        if value >= critical:
            return "CRITICAL"
        if value >= warning:
            return "WARNING"
        return "HEALTHY"
    if direction == "low_bad":
        if value <= critical:
            return "CRITICAL"
        if value <= warning:
            return "WARNING"
        return "HEALTHY"
    raise PcCareError(f"unsupported threshold direction: {direction!r}")


def recommendation(metric: str, severity: str) -> dict[str, Any] | None:
    if severity == "HEALTHY":
        return None
    guidance = {
        "cpu_percent": "Inspect sustained CPU consumers and workload scheduling before considering any process action.",
        "memory_percent": "Review memory-heavy applications and workload limits; prefer graceful workload reduction over forced termination.",
        "disk_free_percent": "Review large or temporary files and storage growth; do not delete anything automatically.",
        "temperature_c": "Reduce load and verify cooling, airflow and sensor accuracy before continuing sustained workloads.",
    }[metric]
    return {
        "metric": metric,
        "severity": severity,
        "action": "RECOMMEND_ONLY",
        "owner_approval_required_before_mutation": True,
        "guidance": guidance,
    }


def diagnose(snapshot: dict[str, Any], policy: dict[str, Any]) -> dict[str, Any]:
    validate_policy(policy)
    validate_snapshot(snapshot, policy)

    metric_results: list[dict[str, Any]] = []
    recommendations: list[dict[str, Any]] = []
    overall = "HEALTHY"

    for metric in sorted(policy["thresholds"]):
        value = float(snapshot["metrics"][metric])
        severity = metric_severity(value, policy["thresholds"][metric])
        metric_results.append({"metric": metric, "value": value, "severity": severity})
        if SEVERITY_RANK[severity] > SEVERITY_RANK[overall]:
            overall = severity
        item = recommendation(metric, severity)
        if item:
            recommendations.append(item)

    return {
        "schemaVersion": 1,
        "stage": 28,
        "status": "PASS",
        "diagnosticSeverity": overall,
        "snapshotId": snapshot["snapshot_id"],
        "snapshotSource": snapshot["source"],
        "physicalPcStatus": policy["physical_pc_status"],
        "simulationOnly": True,
        "recommendationsOnly": True,
        "hostMutationAttempted": False,
        "networkRequired": False,
        "shellExecutionRequired": False,
        "metrics": metric_results,
        "recommendations": recommendations,
        "hardBoundaries": policy["remediation"],
        "truthBoundaries": policy["truth_boundaries"],
    }


def canonical_bytes(value: Any) -> bytes:
    return (json.dumps(value, indent=2, sort_keys=True) + "\n").encode("utf-8")


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser(description="Stage 28 deterministic PC-care diagnostics")
    parser.add_argument("--policy", type=Path, default=DEFAULT_POLICY)
    parser.add_argument("--snapshot", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    try:
        policy = load_json(args.policy)
        snapshot = load_json(args.snapshot)
        report = diagnose(snapshot, policy)
    except PcCareError as exc:
        print(f"Stage 28 diagnostic error: {exc}", file=sys.stderr)
        return 2

    rendered = canonical_bytes(report)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_bytes(rendered)
    print(json.dumps({"status": report["status"], "severity": report["diagnosticSeverity"], "sha256": sha256(rendered)}, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
