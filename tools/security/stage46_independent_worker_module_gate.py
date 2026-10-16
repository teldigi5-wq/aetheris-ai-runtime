#!/usr/bin/env python3
"""Stage 46.24: inspect actual standalone test-only Maven worker module.

The lab has NO independent source copies, external dependencies, installer or
attached production path. Reading JAR central-directory entries (never
extracting/running them) plus Maven POMs is scoped packaging evidence only.
It cannot certify production, real model/audio, WFP egress, owner or release.
"""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path
import xml.etree.ElementTree as ET
from stage46_jar_packaging_evidence import _read_entries, _jar_names
ROOT=Path(__file__).resolve().parents[2]
NS={"m":"http://maven.apache.org/POM/4.0.0"}
PKG="io/aetheris/orchestrator/voice/"
FIXED=("SyntraSyntheticJobProbeMain","SyntraSyntheticPcmWorkerMain")
CLASSES={PKG+n+".class" for n in FIXED}
SOURCES={PKG+n+".java" for n in FIXED}
POM_PATH="synthetic-worker-lab/pom.xml"
EXTRA_MAVEN=("maven-compiler-plugin","maven-jar-plugin",
             "maven-install-plugin","maven-deploy-plugin")
BASE={
    "schema_version":1,"module_pom_trust_contract_valid":False,
    "only_two_canonical_sources_used":False,
    "only_two_worker_bytecodes_in_module_jar":False,
    "ordinary_spring_boot_contains_no_worker_main":False,
    "standalone_module_not_a_production_dependency":False,
    "module_dependencies_declared":0,
    "module_worker_bytecode_count":0,
    "production_worker_bytecode_count":0,
    "module_executable_synthetic_selftests_passed":False,
    "windows_job_and_reduced_token_regression_passed":False,
    "publisher_signature_approved":False,
    "real_network_egress_denial_verified":False,
    "source_migration_completed":False,
    "module_maven_install_authorized":False,
    "module_maven_deploy_authorized":False,
    "physical_owner_pc_pilot_authorized":False,
    "production_authorized":False,
    "decision":"NO_GO",
}

def parse_pom(source:object) -> ET.Element|None:
    if not isinstance(source,str) or len(source)>25000 or "<!DOCTYPE" in source or "<!ENTITY" in source:
        return None
    try: return ET.fromstring(source)
    except ET.ParseError: return None

def _plugin(root:ET.Element,artifact:str)->ET.Element|None:
    plugins=root.findall("./m:build/m:plugins/m:plugin",NS)
    match=[p for p in plugins if p.findtext("m:artifactId",namespaces=NS)==artifact]
    return match[0] if len(match)==1 else None

def _pom_ok(lab:object,prod:object)->bool:
    module=parse_pom(lab); runtime=parse_pom(prod)
    if module is None or runtime is None: return False
    if (module.tag!="{http://maven.apache.org/POM/4.0.0}project" or
        module.findtext("m:groupId",namespaces=NS)!="io.aetheris.synthetic.lab" or
        module.findtext("m:artifactId",namespaces=NS)!="syntra-synthetic-worker-lab-only" or
        module.findtext("m:version",namespaces=NS)!="0.2.0-SNAPSHOT" or
        module.findtext("m:packaging",namespaces=NS)!="jar" or
        module.find("m:dependencies",NS) is not None or
        module.find("m:parent",NS) is not None or
        module.find("m:modules",NS) is not None or
        module.find("m:profiles",NS) is not None or
        module.findtext("./m:build/m:sourceDirectory",namespaces=NS)
          !="\${project.basedir}/../orchestrator-service/src/main/java" or
        module.findtext("./m:properties/m:maven.compiler.release",namespaces=NS)!="21" or
        module.findtext("./m:properties/m:project.build.outputTimestamp",namespaces=NS)
          !="2020-01-01T00:00:00Z"):
        return False
    plugins=module.findall("./m:build/m:plugins/m:plugin",NS)
    if len(plugins)!=4 or {p.findtext("m:artifactId",namespaces=NS) for p in plugins}!=set(EXTRA_MAVEN):
        return False
    comp=_plugin(module,"maven-compiler-plugin")
    jar=_plugin(module,"maven-jar-plugin")
    inst=_plugin(module,"maven-install-plugin")
    deploy=_plugin(module,"maven-deploy-plugin")
    if any(p is None for p in (comp,jar,inst,deploy)): return False
    if (comp.findtext("m:version",namespaces=NS)!="3.13.0" or
        jar.findtext("m:version",namespaces=NS)!="3.5.1" or
        inst.findtext("m:version",namespaces=NS)!="3.1.2" or
        deploy.findtext("m:version",namespaces=NS)!="3.1.2" or
        inst.findtext("./m:configuration/m:skip",namespaces=NS)!="true" or
        deploy.findtext("./m:configuration/m:skip",namespaces=NS)!="true"):
        return False
    sources={e.text for e in comp.findall("./m:configuration/m:includes/m:include",NS)}
    outputs={e.text for e in jar.findall("./m:configuration/m:includes/m:include",NS)}
    if sources!=SOURCES or outputs!=CLASSES: return False
    if comp.findtext("./m:configuration/m:release",namespaces=NS)!="21": return False
    # Standalone module must not be brought into the runtime build by an
    # orchestrator POM dependency or reactor module declaration.
    if (runtime.find("m:modules",NS) is not None
        or any("syntra-synthetic-worker-lab-only"==e.text
               for e in runtime.findall(".//m:dependency/m:artifactId",NS))):
        return False
    return True

def review(*, lab_pom:object,prod_pom:object,lab_entries:object,
           boot_entries:object, canonical_source_paths:object)->dict[str,object]:
    out=dict(BASE)
    if not isinstance(canonical_source_paths,list) or sorted(canonical_source_paths)!=sorted(SOURCES):
        return out
    if not _pom_ok(lab_pom,prod_pom): return out
    lab=_jar_names(lab_entries);boot=_jar_names(boot_entries)
    if lab is None or boot is None: return out
    names,_=lab; prodnames,_=boot
    classnames={p for p in names if p.endswith(".class")}
    bootmains={f"BOOT-INF/classes/{p}" for p in CLASSES} & prodnames
    if (not {"META-INF/MANIFEST.MF", "META-INF/"}.issubset(names)
        or not {"BOOT-INF/", "BOOT-INF/classes/", "META-INF/MANIFEST.MF"}.issubset(prodnames)
        or any(p.endswith((".exe",".dll",".ps1",".so",".pem",".p12",".key",".pfx"))
               for p in names)
        or any(p.startswith(("BOOT-INF/","META-INF/maven/")) for p in names)):
        return out
    allowed_directories={"io/","io/aetheris/","io/aetheris/orchestrator/",
                         PKG}
    if any(not (p.startswith("META-INF/") or p in allowed_directories or p in CLASSES)
           for p in names): return out
    out["module_pom_trust_contract_valid"]=True
    out["only_two_canonical_sources_used"]=True
    out["module_dependencies_declared"]=0
    out["module_worker_bytecode_count"]=len(classnames)
    out["production_worker_bytecode_count"]=len(bootmains)
    out["only_two_worker_bytecodes_in_module_jar"]=(classnames==CLASSES)
    out["ordinary_spring_boot_contains_no_worker_main"]=(len(bootmains)==0)
    out["standalone_module_not_a_production_dependency"]=True
    return out

def read_paths(root:Path)->dict[str,object]:
    try:
        lab=root/POM_PATH
        prod=root/"orchestrator-service/pom.xml"
        if any(p.is_symlink() or not p.is_file() or p.stat().st_size>25000 for p in (lab,prod)):
            return dict(BASE)
        source_root=root/"orchestrator-service/src/main/java"
        srcpaths=[p for p in SOURCES if (source_root/p).is_file()
                  and not (source_root/p).is_symlink()]
        labjar=root/"synthetic-worker-lab/target/syntra-synthetic-worker-lab-only-0.2.0-SNAPSHOT.jar"
        bootjar=root/"orchestrator-service/target/orchestrator-service-0.2.0-SNAPSHOT.jar"
        a,_=_read_entries(labjar);b,_=_read_entries(bootjar)
        return review(lab_pom=lab.read_text(encoding="utf-8"),
                      prod_pom=prod.read_text(encoding="utf-8"),
                      lab_entries=a,boot_entries=b,canonical_source_paths=srcpaths)
    except (OSError,UnicodeError,ValueError,Exception) as exc:
        # Unexpected errors do NOT constitute passing evidence.
        return dict(BASE)

def main()->int:
    ap=argparse.ArgumentParser()
    ap.add_argument("--root",type=Path,default=ROOT)
    ap.add_argument("--output",type=Path)
    args=ap.parse_args()
    verdict=read_paths(args.root.resolve())
    text=json.dumps(verdict,sort_keys=True,indent=2)+"\n"
    print(text,end="")
    if args.output:
        args.output.write_text(text,encoding="utf-8")
    return 0 if (verdict["module_pom_trust_contract_valid"]
                 and verdict["only_two_worker_bytecodes_in_module_jar"]
                 and verdict["ordinary_spring_boot_contains_no_worker_main"]
                 and verdict["decision"]=="NO_GO") else 1
if __name__=="__main__":
    raise SystemExit(main())
