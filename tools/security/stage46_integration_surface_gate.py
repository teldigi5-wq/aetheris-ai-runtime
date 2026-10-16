#!/usr/bin/env python3
"""Stage 46.21 — concrete compiled-vs-test-only source inventory.

This is a read-only *review gate*, not runtime sandboxing or a release tool.
Compiled synthetic helper classes are NOT excluded from the production jar
merely because they lack Spring registration; report that risk explicitly.
"""
from __future__ import annotations

import argparse
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SCHEMA = 1
SOURCE_PREFIX = "orchestrator-service/src/main/java/io/aetheris/orchestrator/voice/"
SPRING_ENTRY = re.compile(
    r"@(?:Component|Service|Controller|RestController|Configuration|Bean|"
    r"Scheduled|EventListener|SpringBootApplication|PostConstruct)\b"
)
DANGEROUS_IMPORT = re.compile(
    r"\b(?:import\s+(?:javax\.sound\.|java\.awt\.Robot|"
    r"java\.net\.(?:Socket|ServerSocket)|"
    r"com\.sun\.jna\.)|System\.loadLibrary\s*\(|"
    r"Runtime\.getRuntime\s*\(\s*\)\s*\.exec\s*\()"
)
MAIN_METHOD = re.compile(r"\bstatic\s+void\s+main\s*\(")
CATEGORY_KEYS = (
    "COMPILED_MAIN_SYNTHETIC_REVIEW_REQUIRED",
    "JAVA_PRODUCTION_POTENTIAL_CALLER",
    "CI_NATIVE_AND_POWERSHELL_LAB",
    "TEST_ONLY",
    "READ_ONLY_SECURITY_POLICY",
    "SECURITY_POLICY_TEST_ONLY",
    "CI_WORKFLOW_ONLY",
    "DOCUMENTATION_ONLY",
)


def _stripped_java(source: str) -> str:
    # Conservative lexical review only, not a Java parser or call graph.
    source = re.sub(r"/\*.*?\*/", "", source, flags=re.DOTALL)
    return re.sub(r"//[^\n]*", "", source)


def review_inventory(spec: object, *, java_sources: object,
                     script_sources: object) -> dict[str, object]:
    result: dict[str, object] = {
        "schema_version": SCHEMA,
        "integration_review_valid": False,
        "compiled_synthetic_java_count": 0,
        "java_main_entrypoint_count": 0,
        "unexpected_main_class_registration": [],
        "unapproved_production_java_callers": [],
        "unapproved_production_packaging_wiring": [],
        "compiled_on_main_classpath": True,
        "excluded_from_production_artifact": False,
        "signed_release_artifacts_verified": False,
        "integration_candidate_created": False,
        "merge_authorized": False,
        "owner_pc_pilot_authorized": False,
        "microphone_authorized": False,
        "desktop_actions_authorized": False,
        "production_authorized": False,
        "decision": "NO_GO",
    }
    if not isinstance(spec, dict) or set(spec) != {
        "schema_version", "stage", "release_decision", "scope",
        "candidate_policy", "source_pr_range",
        "compiled_synthetic_java_sources", "allowed_synthetic_main_entrypoints",
        "file_category_rules", "unreviewed_production_entrypoints",
        "detected_release_risks", "mandatory_next_reviews", "controls",
    } or type(spec["schema_version"]) is not int or spec["schema_version"] != 1:
        return result
    if (spec["stage"] != "46.21"
        or spec["release_decision"] != "NO_GO"
        or spec["scope"] != "STAGED_DRAFT_INTEGRATION_REVIEW_NOT_PRODUCTION_RELEASE"
        or spec["candidate_policy"] != "MINIMAL_CONSOLIDATION_SPECIFICATION_ONLY_NO_MERGE"):
        return result
    stages = spec["source_pr_range"]
    if not isinstance(stages, dict) or stages != {
        "first": 72, "last": 86, "all_open_draft_unmerged_expected": True,
    }:
        return result
    paths = spec["compiled_synthetic_java_sources"]
    mainpaths = spec["allowed_synthetic_main_entrypoints"]
    if (not isinstance(paths, list) or not isinstance(mainpaths, list)
        or len(paths) != 9 or len(mainpaths) != 2
        or any(not isinstance(p, str) or not p.startswith(SOURCE_PREFIX)
               or not p.endswith(".java") or ".." in p for p in paths)
        or len(set(paths)) != len(paths)
        or len(set(mainpaths)) != len(mainpaths)
        or not set(mainpaths).issubset(paths)
        or not isinstance(java_sources, dict)
        or not isinstance(script_sources, dict)):
        return result
    if (spec["file_category_rules"] != {
        "orchestrator-service/src/main/java/io/aetheris/orchestrator/voice/":
            "COMPILED_MAIN_SYNTHETIC_REVIEW_REQUIRED",
        "orchestrator-service/src/test/java/": "TEST_ONLY",
        "workstation-agent/packaging/stage46/": "CI_NATIVE_AND_POWERSHELL_LAB",
        "tools/security/stage46_": "READ_ONLY_SECURITY_POLICY",
        "tools/security/test_stage46_": "SECURITY_POLICY_TEST_ONLY",
        ".github/workflows/runtime-stage46-": "CI_WORKFLOW_ONLY",
        "docs/runtime/stage46-": "DOCUMENTATION_ONLY",
    }):
        return result
    controls = spec["controls"]
    if (not isinstance(controls, dict)
        or controls != {
            "all_thirteen_stage45_blockers_active": True,
            "integration_candidate_created": False, "merge_authorized": False,
            "production_authorized": False, "owner_pc_pilot_authorized": False,
            "microphone_authorized": False, "desktop_actions_authorized": False,
            "wfp_firewall_changes_authorized": False,
        }):
        return result
    risks = spec["detected_release_risks"]
    if (not isinstance(risks, list) or len(set(map(str, risks))) != len(risks)
        or "JAVA_SYNTHETIC_HELPERS_COMPILED_ON_MAIN_CLASSPATH" not in risks
        or not isinstance(spec["mandatory_next_reviews"], list)
        or len(spec["mandatory_next_reviews"]) < 4
        or not isinstance(spec["unreviewed_production_entrypoints"], list)
        or "workstation-agent/packaging/install.ps1" not in
            spec["unreviewed_production_entrypoints"]):
        return result
    if not all(isinstance(p, str) and isinstance(content, str)
               for p, content in java_sources.items()):
        return result
    if not all(isinstance(p, str) and isinstance(content, str)
               for p, content in script_sources.items()):
        return result

    compiled = set(paths)
    actual_compiled = {p for p in java_sources
        if p.startswith(SOURCE_PREFIX)
        and (p.rsplit("/", 1)[-1].startswith("SyntraSynthetic")
            or p.rsplit("/", 1)[-1] in (
                "SyntraOfflineSpeechArtifactReview.java",
                "SyntraOfflineVoiceTurnCoordinator.java",
                "SyntraVoiceReadOnlyConversationBridge.java",
            ))
    }
    # A newly added Stage46 production-classpath helper must be reviewed and
    # deliberately enrolled, not silently waved into a production release.
    if actual_compiled != compiled:
        return result
    if not compiled.issubset(java_sources):
        return result
    unexpected_main = []
    active = []
    for path, source in sorted(java_sources.items()):
        clean = _stripped_java(source)
        if path in compiled:
            if SPRING_ENTRY.search(clean) or DANGEROUS_IMPORT.search(clean):
                unexpected_main.append(path)
            if bool(MAIN_METHOD.search(clean)) != (path in mainpaths):
                unexpected_main.append(path)
            if path in mainpaths:
                result["java_main_entrypoint_count"] += 1
        else:
            # Only production Java code, not tests or CI classes, can cause a
            # live runtime/classpath reference. Conservative code-token scan.
            if "/src/main/java/" in path:
                if any(re.search(r"\b" + re.escape(Path(lab).stem) + r"\b", clean)
                       for lab in compiled):
                    active.append(path)

    installed = []
    for path, source in sorted(script_sources.items()):
        if (path.startswith("workstation-agent/packaging/")
            and not path.startswith("workstation-agent/packaging/stage46/")):
            if re.search(r"(?i)(?:(?:^|[/\\])stage46[/\\]|syntra(?:synthetic|offlinevoice|voicereadonly|windows)|windowsSynthetic(?:Job|AppContainer|Wfp))", source):
                installed.append(path)

    result["compiled_synthetic_java_count"] = len(compiled)
    result["unexpected_main_class_registration"] = sorted(set(unexpected_main))
    result["unapproved_production_java_callers"] = sorted(set(active))
    result["unapproved_production_packaging_wiring"] = installed
    result["integration_review_valid"] = not (unexpected_main or active or installed)
    return result


def review_checkout(root: Path, spec_file: Path) -> dict[str, object]:
    try:
        if spec_file.stat().st_size > 16_000 or spec_file.is_symlink():
            raise ValueError("UNSAFE_SCOPE_DOCUMENT")
        spec = json.loads(spec_file.read_text(encoding="utf-8"))
        java = {}
        script = {}
        for path in (root / "orchestrator-service/src").rglob("*.java"):
            if (path.is_symlink() or not path.is_file() or path.stat().st_size > 500_000):
                raise ValueError("UNSAFE_JAVA_SOURCE")
            java[path.relative_to(root).as_posix()] = path.read_text(encoding="utf-8")
        for path in (root / "workstation-agent/packaging").rglob("*.ps1"):
            if (path.is_symlink() or not path.is_file() or path.stat().st_size > 500_000):
                raise ValueError("UNSAFE_PACKAGING_SCRIPT")
            script[path.relative_to(root).as_posix()] = path.read_text(encoding="utf-8")
        return review_inventory(spec, java_sources=java, script_sources=script)
    except (OSError, ValueError, UnicodeError):
        return review_inventory(None, java_sources={}, script_sources={})


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=ROOT)
    parser.add_argument("--spec", type=Path,
        default=ROOT / "docs/runtime/stage46-minimal-integration-scope.json")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    verdict = review_checkout(args.root.resolve(), args.spec)
    printed = json.dumps(verdict, indent=2, sort_keys=True) + "\n"
    print(printed, end="")
    if args.output:
        args.output.write_text(printed, encoding="utf-8")
    return 0 if verdict["integration_review_valid"] and verdict["decision"] == "NO_GO" else 1


if __name__ == "__main__":
    raise SystemExit(main())
