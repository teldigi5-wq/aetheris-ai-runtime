#!/usr/bin/env python3
"""Dependency-lockdown verifier with explicit core and AI-runtime ownership scopes."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ERRORS: list[str] = []
EXACT_SEMVER = re.compile(r"^\d+\.\d+\.\d+(?:[-+][0-9A-Za-z.-]+)?$")
FORBIDDEN_LICENSE_MARKERS = ("AGPL-", "SSPL-", "BUSL-", "COMMONS CLAUSE", "GPL-3.0-ONLY")
CORE_MAVEN_MODULES = ("gateway", "user-service", "identity-service", "audit-service")
AI_MAVEN_MODULES = ("orchestrator-service", "workstation-agent")
AI_RUNTIME_BUILDER_IMAGE = "eclipse-temurin:21.0.12_8-jdk-noble@sha256:5dd9dbc6f6eccb82184b340da9488b70b68d2af81474cf8f280f8ce6b2933282"
AI_RUNTIME_JRE_IMAGE = "eclipse-temurin:21.0.12_8-jre-noble@sha256:86883d2dc1d0e57d4fb2c539f5fd3a2155749c0bdcdccb9cb452828bdc8b0caf"
AI_RUNTIME_MAVEN_SHA512 = "bcfe4fe305c962ace56ac7b5fc7a08b87d5abd8b7e89027ab251069faebee516b0ded8961445d6d91ec1985dfe30f8153268843c89aa392733d1a3ec956c9978"


def fail(message: str) -> None:
    ERRORS.append(message)


def require_file(relative: str) -> Path:
    path = ROOT / relative
    if not path.is_file():
        fail(f"missing required evidence: {relative}")
    return path


def verify_node() -> None:
    package_path = require_file("dashboard/package.json")
    lock_path = require_file("dashboard/package-lock.json")
    if not package_path.is_file() or not lock_path.is_file():
        return
    package = json.loads(package_path.read_text(encoding="utf-8"))
    lock = json.loads(lock_path.read_text(encoding="utf-8"))
    for section in ("dependencies", "devDependencies"):
        declared = package.get(section, {})
        for name, version in declared.items():
            if not EXACT_SEMVER.fullmatch(version):
                fail(f"dashboard {name} must be exact semver, got {version!r}")
    if package.get("engines", {}).get("node") != "22.23.2":
        fail("dashboard Node engine must remain pinned to 22.23.2")
    if lock.get("lockfileVersion") != 3:
        fail("dashboard package-lock.json must use lockfileVersion 3")
    root_lock = lock.get("packages", {}).get("", {})
    for section in ("dependencies", "devDependencies"):
        if root_lock.get(section, {}) != package.get(section, {}):
            fail(f"dashboard package.json and package-lock.json disagree in {section}")
    integrity_count = 0
    for name, metadata in lock.get("packages", {}).items():
        if not name:
            continue
        if metadata.get("integrity"):
            integrity_count += 1
        license_value = str(metadata.get("license", "")).upper()
        for marker in FORBIDDEN_LICENSE_MARKERS:
            if marker in license_value:
                fail(f"forbidden npm license marker {marker!r} in {name}")
    if integrity_count == 0:
        fail("dashboard lockfile contains no package integrity hashes")


def verify_quant_python() -> None:
    source_path = require_file("aetheris-quant/requirements.in")
    lock_path = require_file("aetheris-quant/requirements.lock.txt")
    reasoning = require_file("aetheris-reasoning/pyproject.toml")
    if reasoning.is_file() and "setuptools>=68" not in reasoning.read_text(encoding="utf-8"):
        fail("reasoning build-system setuptools floor changed unexpectedly")
    if not source_path.is_file() or not lock_path.is_file():
        return
    for raw in source_path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        if "==" not in line or any(token in line for token in (">=", "<=", "~=", "!=", "*")):
            fail(f"Python direct dependency must be exactly pinned: {line}")
    lines = lock_path.read_text(encoding="utf-8").splitlines()
    starts = [i for i, line in enumerate(lines) if line and not line[0].isspace() and not line.startswith("#") and "==" in line]
    if not starts:
        fail("Python lockfile contains no pinned requirements")
        return
    for position, start in enumerate(starts):
        end = starts[position + 1] if position + 1 < len(starts) else len(lines)
        block = "\n".join(lines[start:end])
        requirement = lines[start].split("\\", 1)[0].strip()
        if "--hash=sha256:" not in block:
            fail(f"Python lock entry has no SHA-256 hash: {requirement}")


def verify_common_toolchain() -> None:
    java = require_file(".java-version")
    if java.is_file() and java.read_text(encoding="utf-8").strip() != "21.0.12":
        fail(".java-version must contain 21.0.12")
    wrapper = require_file(".mvn/wrapper/maven-wrapper.properties")
    if wrapper.is_file():
        text = wrapper.read_text(encoding="utf-8")
        if "wrapperVersion=3.3.4" not in text:
            fail("Maven wrapper must remain pinned to wrapper 3.3.4")
        if "/apache-maven/3.9.11/" not in text:
            fail("Maven wrapper distribution must remain pinned to Maven 3.9.11")


def verify_core_toolchain() -> None:
    verify_common_toolchain()
    node = require_file(".nvmrc")
    if node.is_file() and node.read_text(encoding="utf-8").strip() != "22.23.2":
        fail(".nvmrc must contain 22.23.2")


def verify_ai_toolchain() -> None:
    verify_common_toolchain()
    python = require_file("aetheris-quant/.python-version")
    if python.is_file() and python.read_text(encoding="utf-8").strip() != "3.13.15":
        fail("aetheris-quant/.python-version must contain 3.13.15")


def verify_ai_container_policy() -> None:
    dockerfile = require_file("orchestrator-service/Dockerfile")
    if not dockerfile.is_file():
        return
    text = dockerfile.read_text(encoding="utf-8")
    lines = [line.strip() for line in text.splitlines() if line.strip()]
    from_lines = [line for line in lines if line.startswith("FROM ")]
    expected_from = [
        f"FROM {AI_RUNTIME_BUILDER_IMAGE} AS build",
        f"FROM {AI_RUNTIME_JRE_IMAGE}",
    ]
    if from_lines != expected_from:
        fail(f"orchestrator runtime base images must remain exact digest pins: {from_lines!r}")
    if "ARG MAVEN_VERSION=3.9.11" not in lines:
        fail("orchestrator runtime Maven version must remain pinned to 3.9.11")
    if f"ARG MAVEN_SHA512={AI_RUNTIME_MAVEN_SHA512}" not in lines:
        fail("orchestrator runtime Maven archive SHA-512 pin changed unexpectedly")
    if "sha512sum --check --strict" not in text:
        fail("orchestrator runtime Maven download must be verified with sha512sum --check --strict")
    user_lines = [line for line in lines if line.startswith("USER ")]
    if user_lines != ["USER 10001:10001"]:
        fail(f"orchestrator runtime must run as exact non-root UID/GID 10001:10001: {user_lines!r}")
    if "COPY --from=build --chown=10001:10001 /app/target/*.jar app.jar" not in lines:
        fail("orchestrator runtime JAR must be copied with non-root ownership")
    if "install -d -o 10001 -g 10001 /app/workspace" not in text:
        fail("orchestrator runtime must provision /app/workspace for the non-root runtime user")
    if "EXPOSE 8090" not in lines:
        fail("orchestrator runtime container must keep the certified 8090 boundary")


def verify_maven(modules: tuple[str, ...], include_root: bool) -> None:
    pom_paths = (["pom.xml"] if include_root else []) + [f"{module}/pom.xml" for module in modules]
    for relative in pom_paths:
        path = require_file(relative)
        if not path.is_file():
            continue
        text = path.read_text(encoding="utf-8")
        if re.search(r"<version>\s*(?:LATEST|RELEASE)\s*</version>", text, re.I):
            fail(f"floating Maven version in {relative}")
        if re.search(r"<version>\s*[\[(]", text):
            fail(f"Maven version range in {relative}")
    for module in modules:
        evidence = require_file(f"build-evidence/maven/{module}.txt")
        if evidence.is_file() and evidence.stat().st_size == 0:
            fail(f"empty Maven dependency evidence for {module}")


def verify_core_boundaries() -> None:
    for relative in (
        "scripts/stage22/release_gate.py",
        "scripts/stage22/schema_guard.py",
        "scripts/stage22/generate_sbom.py",
        "scripts/stage22/release_manifest.py",
        "scripts/stage22/merge_readiness.py",
        "scripts/stage23/contract_baseline.py",
        "scripts/stage23/contract_guard.py",
        "scripts/stage23/expected-contract.sha256",
        "docker-compose.core.yml",
    ):
        require_file(relative)


def verify_ai_boundaries() -> None:
    for relative in (
        "orchestrator-service/Dockerfile",
        "orchestrator-service/pom.xml",
        "workstation-agent/pom.xml",
        "aetheris-quant/requirements.in",
        "aetheris-quant/requirements.lock.txt",
        "aetheris-reasoning/pyproject.toml",
    ):
        require_file(relative)


def write_hash_manifest(scope: str) -> None:
    if scope == "core":
        evidence_files = [
            "dashboard/package.json", "dashboard/package-lock.json", "dashboard/Dockerfile",
            ".mvn/wrapper/maven-wrapper.properties",
            *(f"build-evidence/maven/{module}.txt" for module in CORE_MAVEN_MODULES),
        ]
        output = ROOT / "build-evidence/core-dependency-lock-sha256.txt"
    elif scope == "ai-runtime":
        evidence_files = [
            "orchestrator-service/Dockerfile",
            "aetheris-quant/requirements.in", "aetheris-quant/requirements.lock.txt",
            "aetheris-reasoning/pyproject.toml", ".mvn/wrapper/maven-wrapper.properties",
            *(f"build-evidence/maven/{module}.txt" for module in AI_MAVEN_MODULES),
        ]
        output = ROOT / "build-evidence/ai-runtime-dependency-lock-sha256.txt"
    else:
        evidence_files = [
            "dashboard/package.json", "dashboard/package-lock.json", "dashboard/Dockerfile",
            "orchestrator-service/Dockerfile",
            "aetheris-quant/requirements.in", "aetheris-quant/requirements.lock.txt",
            "aetheris-reasoning/pyproject.toml", ".mvn/wrapper/maven-wrapper.properties",
            *(f"build-evidence/maven/{module}.txt" for module in (*CORE_MAVEN_MODULES, *AI_MAVEN_MODULES)),
        ]
        output = ROOT / "build-evidence/dependency-lock-sha256.txt"
    output.parent.mkdir(parents=True, exist_ok=True)
    rows: list[str] = []
    for relative in evidence_files:
        path = require_file(relative)
        if path.is_file():
            rows.append(f"{hashlib.sha256(path.read_bytes()).hexdigest()}  {relative}")
    output.write_text("\n".join(rows) + "\n", encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--scope", choices=("core", "ai-runtime", "all"), default="all")
    parser.add_argument("--write-hashes", action="store_true")
    args = parser.parse_args()

    if args.scope in ("core", "all"):
        verify_node()
        verify_core_toolchain()
        verify_maven(CORE_MAVEN_MODULES, include_root=True)
        verify_core_boundaries()
    if args.scope in ("ai-runtime", "all"):
        verify_quant_python()
        verify_ai_toolchain()
        verify_ai_container_policy()
        verify_maven(AI_MAVEN_MODULES, include_root=False)
        verify_ai_boundaries()
    if args.write_hashes:
        write_hash_manifest(args.scope)

    if ERRORS:
        print(f"Aetheris dependency lockdown ({args.scope}) FAILED:", file=sys.stderr)
        for error in ERRORS:
            print(f" - {error}", file=sys.stderr)
        return 1
    print(f"Aetheris dependency lockdown ({args.scope}): PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
