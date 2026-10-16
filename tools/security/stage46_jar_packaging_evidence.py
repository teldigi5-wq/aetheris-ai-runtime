#!/usr/bin/env python3
"""Stage 46.22 — inspect built Spring Boot and shaded host-agent JAR entries.

No extraction, Java execution, network use, user data, signing or installer
side effects. A passing inventory means the *observed* JAR matches the
reviewed synthetic-lab exposure and test-dependency boundaries. It can NEVER
authorize publishing, merging, production or an owner-PC pilot.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import zipfile
from pathlib import Path

SCHEMA = 1
MAX_ENTRIES = 100_000
MAX_UNCOMPRESSED_BYTES = 3_000_000_000
MAX_JAR_BYTES = 600_000_000
JAVA_SOURCE_PREFIX = "orchestrator-service/src/main/java/"
BOOTS_CLASS_PREFIX = "BOOT-INF/classes/"
BOOTS_LIB_PREFIX = "BOOT-INF/lib/"
TEST_ONLY_NAMES = ("junit", "mockito", "hamcrest", "byte-buddy-agent",
                   "assertj-core", "h2-")
BANNED_RESOURCE_SUFFIXES = (".pem", ".p12", ".pfx", ".jks", ".keystore", ".key")
_SHA64 = re.compile(r"^[0-9a-f]{64}$")
RESULT = {
    "schema_version": 1,
    "packaged_artifacts_inspected": False,
    "orchestrator_spring_boot_layout_verified": False,
    "workstation_host_agent_layout_verified": False,
    "compiled_synthetic_classes_embedded_count": 0,
    "compiled_synthetic_main_entrypoints_embedded_count": 0,
    "synthetic_classes_are_excluded_from_orchestrator_jar": False,
    "test_only_classes_or_dependencies_detected": [],
    "prohibited_sensitive_resource_entries": [],
    "unexpected_lab_native_payload_entries": [],
    "orchestrator_nested_dependency_count": 0,
    "workstation_class_entry_count": 0,
    "orchestrator_jar_sha256": None,
    "workstation_jar_sha256": None,
    "release_signer_verified": False,
    "minimum_integration_candidate_approved": False,
    "real_microphone_authorized": False,
    "desktop_actions_authorized": False,
    "owner_pc_pilot_authorized": False,
    "production_authorized": False,
    "decision": "NO_GO",
}


def expected_classes(spec: object) -> tuple[set[str], set[str]] | None:
    if not isinstance(spec, dict):
        return None
    paths = spec.get("compiled_synthetic_java_sources")
    mains = spec.get("allowed_synthetic_main_entrypoints")
    if (spec.get("schema_version") != 1
        or type(spec.get("schema_version")) is not int
        or spec.get("stage") != "46.21"
        or spec.get("release_decision") != "NO_GO"
        or not isinstance(paths, list) or not isinstance(mains, list)
        or len(paths) != 9 or len(mains) != 2
        or any(not isinstance(p, str) or not p.startswith(JAVA_SOURCE_PREFIX)
               or not p.endswith(".java") or ".." in p for p in paths + mains)):
        return None
    compiled = {
        BOOTS_CLASS_PREFIX + p[len(JAVA_SOURCE_PREFIX):-5] + ".class"
        for p in paths
    }
    child_mains = {
        BOOTS_CLASS_PREFIX + p[len(JAVA_SOURCE_PREFIX):-5] + ".class"
        for p in mains
    }
    if len(compiled) != 9 or len(child_mains) != 2 or not child_mains.issubset(compiled):
        return None
    return compiled, child_mains


def _jar_names(data: object) -> tuple[set[str], list[str]] | None:
    # Lists of ZipInfo entries are read but never extracted. Reject duplicate,
    # traversal, oversized or encrypted JAR input conservatively.
    if not isinstance(data, list) or len(data) > MAX_ENTRIES:
        return None
    names: list[str] = []
    uncompressed = 0
    for item in data:
        if not isinstance(item, dict) or set(item) != {"name", "size", "encrypted"}:
            return None
        name, size, encrypted = item["name"], item["size"], item["encrypted"]
        if (not isinstance(name, str) or not name or len(name) > 1024
            or name.startswith("/") or name.startswith("\\") or "\\" in name
            or "\x00" in name or "\r" in name or "\n" in name
            or any(part == ".." for part in name.split("/"))
            or type(size) is not int or size < 0 or encrypted is not False):
            return None
        uncompressed += size
        if uncompressed > MAX_UNCOMPRESSED_BYTES:
            return None
        names.append(name)
    if len(names) != len(set(names)):
        return None
    return set(names), names


def review(spec: object, orchestrator_entries: object,
           workstation_entries: object,
           *, orchestrator_digest: object, workstation_digest: object) -> dict[str, object]:
    result = {**RESULT,
              "test_only_classes_or_dependencies_detected": [],
              "prohibited_sensitive_resource_entries": [],
              "unexpected_lab_native_payload_entries": []}
    desired = expected_classes(spec)
    orch = _jar_names(orchestrator_entries)
    work = _jar_names(workstation_entries)
    if (desired is None or orch is None or work is None
        or not isinstance(orchestrator_digest, str)
        or not isinstance(workstation_digest, str)
        or not _SHA64.fullmatch(orchestrator_digest)
        or not _SHA64.fullmatch(workstation_digest)):
        return result
    compiled, mains = desired
    orch_names, _ = orch
    work_names, _ = work
    libraries = sorted(n for n in orch_names
                       if n.startswith(BOOTS_LIB_PREFIX) and n.endswith(".jar"))
    if (not libraries or not orch_names or not work_names
        or "BOOT-INF/" not in orch_names
        or "BOOT-INF/classes/" not in orch_names
        or "META-INF/MANIFEST.MF" not in orch_names
        or "io/aetheris/workstation/AetherisHostAgent.class" not in work_names
        or "META-INF/MANIFEST.MF" not in work_names):
        return result

    actual_compiled = compiled & orch_names
    actual_mains = mains & orch_names
    # Unexpected test-only bytecode or compile/test deps are never allowed
    # inside inspected output. Detection is specific, not a full malware scan.
    test_classes = sorted(n for n in orch_names | work_names
        if (n.startswith("BOOT-INF/classes/") or n.startswith("io/aetheris/"))
        and (n.endswith("Test.class") or n.endswith("Tests.class")
             or "/src/test/" in n))
    test_libs = sorted(n for n in libraries
        if any(term in n.split("/")[-1].lower() for term in TEST_ONLY_NAMES))
    sensitive = sorted(n for n in orch_names | work_names
        if n.lower().endswith(BANNED_RESOURCE_SUFFIXES)
        or n.lower().split("/")[-1] == ".env")
    native = sorted(n for n in orch_names | work_names
        if n.startswith("BOOT-INF/classes/") or n.startswith("io/aetheris/")
        if ("/packaging/stage46/" in n.lower()
            or "WindowsSyntheticWfp" in n or "WindowsSyntheticAppContainer" in n
            or n.lower().endswith((".exe", ".dll", ".ps1", ".so"))))
    result.update(
        compiled_synthetic_classes_embedded_count=len(actual_compiled),
        compiled_synthetic_main_entrypoints_embedded_count=len(actual_mains),
        test_only_classes_or_dependencies_detected=(test_classes + test_libs)[:30],
        prohibited_sensitive_resource_entries=sensitive[:30],
        unexpected_lab_native_payload_entries=native[:30],
        orchestrator_nested_dependency_count=len(libraries),
        workstation_class_entry_count=sum(n.endswith(".class") for n in work_names),
        orchestrator_jar_sha256=orchestrator_digest,
        workstation_jar_sha256=workstation_digest,
        orchestrator_spring_boot_layout_verified=True,
        workstation_host_agent_layout_verified=True,
    )
    if (len(actual_compiled) == len(compiled) == 9
        and len(actual_mains) == 2
        and not test_classes and not test_libs
        and not sensitive and not native):
        result["packaged_artifacts_inspected"] = True
    # Exclusion remains false because Stage46.21 classes are in the JAR.
    return result


def _read_entries(path: Path) -> tuple[list[dict[str, object]], str]:
    if path.is_symlink() or not path.is_file() or path.stat().st_size > MAX_JAR_BYTES:
        raise ValueError("JAR_NOT_REGULAR_OR_OVERSIZED")
    digest = hashlib.sha256()
    with path.open("rb") as fh:
        while chunk := fh.read(65536):
            digest.update(chunk)
    with zipfile.ZipFile(path, "r") as archive:
        entries = [
            {"name": item.filename, "size": item.file_size,
             "encrypted": bool(item.flag_bits & 1)}
            for item in archive.infolist()
        ]
    return entries, digest.hexdigest()


def review_built_artifacts(spec_path: Path, orchestrator_path: Path,
                           workstation_path: Path) -> dict[str, object]:
    try:
        if spec_path.is_symlink() or spec_path.stat().st_size > 15000:
            raise ValueError("UNSAFE_SCOPE_MANIFEST")
        scope = json.loads(spec_path.read_text(encoding="utf-8"))
        a, ah = _read_entries(orchestrator_path)
        b, bh = _read_entries(workstation_path)
        return review(scope, a, b,
                      orchestrator_digest=ah, workstation_digest=bh)
    except (OSError, ValueError, UnicodeError, zipfile.BadZipFile):
        return dict(RESULT)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--spec", type=Path, required=True)
    parser.add_argument("--orchestrator-jar", type=Path, required=True)
    parser.add_argument("--workstation-jar", type=Path, required=True)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    result = review_built_artifacts(args.spec, args.orchestrator_jar,
                                    args.workstation_jar)
    serialized = json.dumps(result, indent=2, sort_keys=True) + "\n"
    print(serialized, end="")
    if args.output:
        args.output.write_text(serialized, encoding="utf-8")
    return 0 if result["packaged_artifacts_inspected"] and result["decision"] == "NO_GO" else 1


if __name__ == "__main__":
    raise SystemExit(main())
