#!/usr/bin/env python3
"""Stage 46.23: inspect *real* production and optional fixture JAR separation.

No JAR is executed or extracted. This audit prevents two fixed synthetic
subprocess mains from silently returning to the ordinary Spring Boot JAR.
This is packaging containment only, NOT runtime sandboxing, real STT, WFP
denial, a signed distribution or approval to install/merge/deploy.
"""
from __future__ import annotations
import argparse
import hashlib
import json
import xml.etree.ElementTree as ET
from pathlib import Path
from stage46_jar_packaging_evidence import (
    _read_entries, _jar_names, expected_classes, BOOTS_CLASS_PREFIX,
    BOOTS_LIB_PREFIX, BANNED_RESOURCE_SUFFIXES, TEST_ONLY_NAMES,
)

ROOT = Path(__file__).resolve().parents[2]
JAR_PLUGIN = "maven-jar-plugin"
FIXTURE_PROFILE = "synthetic-worker-lab"
FIXTURE_CLASSIFIER = "synthetic-worker-lab-only"
POM_NS = {"m": "http://maven.apache.org/POM/4.0.0"}
SOURCE_PREFIX = "orchestrator-service/src/main/java/"
WORKER_PREFIX = "io/aetheris/orchestrator/voice/"
OUTPUT = {
    "schema_version": 1,
    "packaging_separation_verified": False,
    "production_jar_excludes_two_synthetic_mains": False,
    "test_fixtures_remain_compiled": False,
    "opt_in_fixture_jar_contains_only_two_mains": False,
    "maven_fixture_nonattached": False,
    "fixture_profile_not_active_by_default": False,
    "production_synthetic_helper_count": 0,
    "production_worker_main_count": 0,
    "test_fixture_worker_main_count": 0,
    "normal_build_fixture_jar_absent": False,
    "java_unit_tests_passed": False,
    "windows_job_appcontainer_tests_passed": False,
    "publisher_signature_approved": False,
    "runtime_isolation_certified": False,
    "physical_owner_pc_pilot_authorized": False,
    "production_authorized": False,
    "merge_authorized": False,
    "decision": "NO_GO",
}

def _jarexposure(names: set[str]) -> list[str]:
    return sorted(p for p in names
        if p.lower().endswith(BANNED_RESOURCE_SUFFIXES)
        or p.lower().split("/")[-1] == ".env"
        or p.lower().endswith((".exe", ".dll", ".ps1", ".so")))

def _config(xml: object) -> bool:
    if not isinstance(xml, str) or len(xml) > 50_000 or "<!DOCTYPE" in xml or "<!ENTITY" in xml:
        return False
    try:
        root = ET.fromstring(xml)
    except ET.ParseError:
        return False
    plugins = root.findall("./m:build/m:plugins/m:plugin", POM_NS)
    jar = next((p for p in plugins if p.findtext("m:artifactId", namespaces=POM_NS)
               == JAR_PLUGIN), None)
    if jar is None or jar.findtext("m:version", namespaces=POM_NS) != "3.5.1":
        return False
    exclusions = jar.find("./m:executions/m:execution[m:id='default-jar']/m:configuration/m:excludes", POM_NS)
    if exclusions is None:
        return False
    excluded = {node.text for node in exclusions.findall("m:exclude", POM_NS)}
    expected = {WORKER_PREFIX + n + ".class" for n in
                ("SyntraSyntheticPcmWorkerMain","SyntraSyntheticJobProbeMain")}
    if excluded != expected:
        return False
    profiles = root.findall("./m:profiles/m:profile", POM_NS)
    if len([x for x in profiles if x.findtext("m:id", namespaces=POM_NS) == FIXTURE_PROFILE]) != 1:
        return False
    profile = next(p for p in profiles
                   if p.findtext("m:id", namespaces=POM_NS) == FIXTURE_PROFILE)
    if profile.findtext("./m:activation/m:activeByDefault", namespaces=POM_NS) == "true":
        return False
    profplugins = profile.findall("./m:build/m:plugins/m:plugin", POM_NS)
    pjar = next((p for p in profplugins if
                 p.findtext("m:artifactId", namespaces=POM_NS) == JAR_PLUGIN), None)
    if pjar is None:
        return False
    executions = pjar.findall("./m:executions/m:execution", POM_NS)
    if len(executions) != 1:
        return False
    exe = executions[0]
    config = exe.find("m:configuration", POM_NS)
    if config is None:
        return False
    includes = config.find("m:includes", POM_NS)
    return bool(
        exe.findtext("m:id", namespaces=POM_NS) == "synthetic-worker-lab-only"
        and exe.findtext("m:phase", namespaces=POM_NS) == "package"
        and [g.text for g in exe.findall("./m:goals/m:goal", POM_NS)] == ["jar"]
        and config.findtext("m:classifier", namespaces=POM_NS) == FIXTURE_CLASSIFIER
        and config.findtext("m:attach", namespaces=POM_NS) == "false"
        and includes is not None
        and {n.text for n in includes.findall("m:include", POM_NS)} == expected
    )

def review(*, spec: object, pom: object, boot_entries: object,
           fixture_entries: object, compiled_entries: object,
           default_profile_fixture_exists: object) -> dict[str, object]:
    result = dict(OUTPUT)
    expected = expected_classes(spec)
    prod = _jar_names(boot_entries)
    fixture = _jar_names(fixture_entries)
    if (expected is None or prod is None or fixture is None
        or not isinstance(compiled_entries, list)
        or any(not isinstance(n,str) for n in compiled_entries)
        or type(default_profile_fixture_exists) is not bool):
        return result
    allclasses,mains=expected
    production, _ = prod
    fixturezip, _ = fixture
    names = sorted(p.removeprefix(BOOTS_CLASS_PREFIX) for p in mains)
    keep = allclasses - mains
    src_mains = set(names)
    if (not _config(pom)
        or not {"BOOT-INF/", "BOOT-INF/classes/", "META-INF/MANIFEST.MF"}.issubset(production)
        or not any(p.startswith(BOOTS_LIB_PREFIX) and p.endswith(".jar") for p in production)
        or "META-INF/MANIFEST.MF" not in fixturezip
        or not all(p.startswith(("META-INF/", WORKER_PREFIX)) for p in fixturezip)
        or _jarexposure(production | fixturezip)
        or any("/src/test/" in p or p.endswith("Test.class") for p in production | fixturezip)):
        return result

    # A fixture ZIP is a bytecode carrier only: its only .class entries must be
    # exactly the two worker mains, with no third class or nested dependencies.
    actual_fixture_class = {x for x in fixturezip if x.endswith(".class")}
    actual_production_helper = allclasses & production
    result["production_synthetic_helper_count"] = len(actual_production_helper)
    result["production_worker_main_count"] = len(mains & production)
    result["test_fixture_worker_main_count"] = len(actual_fixture_class & src_mains)
    result["maven_fixture_nonattached"] = True
    result["fixture_profile_not_active_by_default"] = True
    result["normal_build_fixture_jar_absent"] = not default_profile_fixture_exists
    result["production_jar_excludes_two_synthetic_mains"] = (
        not (mains & production)
        and keep.issubset(production) and len(actual_production_helper) == 7
    )
    result["test_fixtures_remain_compiled"] = src_mains.issubset(set(compiled_entries))
    result["opt_in_fixture_jar_contains_only_two_mains"] = (
        actual_fixture_class == src_mains and len(actual_fixture_class) == 2
        and not any(x.startswith("BOOT-INF/") for x in fixturezip)
    )
    result["packaging_separation_verified"] = all((
        result["production_jar_excludes_two_synthetic_mains"],
        result["test_fixtures_remain_compiled"],
        result["opt_in_fixture_jar_contains_only_two_mains"],
        result["maven_fixture_nonattached"],
        result["fixture_profile_not_active_by_default"],
        result["normal_build_fixture_jar_absent"],
    ))
    # 7 other main-classpath synthetic helpers remain packaged, and process
    # spawning the missing child from a runtime JAR may now fail closed.
    # This policy NEVER authorizes real production voice execution.
    return result


def review_paths(specpath: Path, pompath: Path, prodjar: Path, fixturejar: Path,
                 compiledroot: Path, default_fixture_present: bool) -> dict[str, object]:
    try:
        if specpath.is_symlink() or pompath.is_symlink():
            raise ValueError("UNSAFE_MANIFEST")
        if specpath.stat().st_size > 15000 or pompath.stat().st_size > 50000:
            raise ValueError("OVERSIZED_SPEC")
        spec = json.loads(specpath.read_text(encoding="utf-8"))
        pom = pompath.read_text(encoding="utf-8")
        boot,_ = _read_entries(prodjar)
        worker,_ = _read_entries(fixturejar)
        if compiledroot.is_symlink() or not compiledroot.is_dir():
            raise ValueError("UNSAFE_COMPILED_DIR")
        mains = ["SyntraSyntheticPcmWorkerMain.class","SyntraSyntheticJobProbeMain.class"]
        paths = [WORKER_PREFIX + n for n in mains if (compiledroot/WORKER_PREFIX/n).is_file()
                 and not (compiledroot/WORKER_PREFIX/n).is_symlink()]
        return review(spec=spec,pom=pom,boot_entries=boot,fixture_entries=worker,
                      compiled_entries=paths,
                      default_profile_fixture_exists=default_fixture_present)
    except (OSError,ValueError,UnicodeError):
        return dict(OUTPUT)

def main() -> int:
    parser=argparse.ArgumentParser()
    parser.add_argument("--spec",type=Path,required=True)
    parser.add_argument("--pom",type=Path,required=True)
    parser.add_argument("--orchestrator-jar",type=Path,required=True)
    parser.add_argument("--lab-fixture-jar",type=Path,required=True)
    parser.add_argument("--compiled-classes-dir",type=Path,required=True)
    parser.add_argument("--default-build-fixture-present",choices=("true","false"),required=True)
    parser.add_argument("--output",type=Path)
    args=parser.parse_args()
    value=review_paths(args.spec,args.pom,args.orchestrator_jar,
                       args.lab_fixture_jar,args.compiled_classes_dir,
                       args.default_build_fixture_present=="true")
    rendered=json.dumps(value,sort_keys=True,indent=2)+"\n"
    print(rendered,end="")
    if args.output:
        args.output.write_text(rendered,encoding="utf-8")
    return 0 if value["packaging_separation_verified"] and value["decision"]=="NO_GO" else 1

if __name__=="__main__":
    raise SystemExit(main())
