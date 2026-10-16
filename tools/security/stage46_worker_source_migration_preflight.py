#!/usr/bin/env python3
"""Stage 46.25 — compiled-symbol and frozen-wire migration preflight.

The main orchestrator may store the fixed synthetic worker classname as a
bounded STRING but must not contain a JVM ClassRef to either lab-only worker.
Checks actual javap -verbose on the two reviewed producer classes. This is
narrow bytecode evidence, not a general callgraph, permission sandbox or
source migration/production authorization.
"""
from __future__ import annotations
import argparse
import json
import re
import subprocess
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
PACKAGE="io/aetheris/orchestrator/voice"
WORKER="SyntraSyntheticPcmWorkerMain"
JOB="SyntraSyntheticJobProbeMain"
CLASS_REF=re.compile(
    r"^\s*#\d+\s+=\s+Class\s+#\d+\s+//\s+"
    r"io/aetheris/orchestrator/voice/(?:SyntraSyntheticPcmWorkerMain|SyntraSyntheticJobProbeMain)\s*$",
    re.MULTILINE,
)
CONSTANTS={
    "io/aetheris/orchestrator/voice/SyntraSyntheticHostLimits.java":
        ('static final String FIXED_PCM_WORKER_CLASS',
         '"io.aetheris.orchestrator.voice.SyntraSyntheticPcmWorkerMain"'),
    "io/aetheris/orchestrator/voice/SyntraSyntheticProcessSpeechBoundary.java":
        ('static final int FIXED_PCM_MAGIC = 0x53463431',
         'static final String FIXED_PCM_PREFIX = "SYNTRA46_10_V1|"'),
}
RESULT={
    "schema_version":1,
    "frozen_wire_values_verified":False,
    "synthetic_worker_symbol_link_free":False,
    "compiled_jvm_constant_pool_checked":False,
    "regular_jar_worker_entrypoints_excluded":False,
    "worker_source_moved_out_of_orchestrator":False,
    "module_release_approved":False,
    "voice_microphone_authorized":False,
    "network_egress_certified":False,
    "physical_owner_pc_pilot_authorized":False,
    "production_authorized":False,
    "decision":"NO_GO",
}
def review(source:object,verbose:object, jar_worker_mains:object)->dict[str,object]:
    r=dict(RESULT)
    if (not isinstance(source,dict) or set(source)!=set(CONSTANTS)
        or not isinstance(verbose,dict) or set(verbose)!=set(CONSTANTS)
        or type(jar_worker_mains) is not int or jar_worker_mains<0):
        return r
    if any(not isinstance(source[p],str) or not isinstance(verbose[p],str)
           or len(source[p])>200_000 or len(verbose[p])>500_000
           for p in CONSTANTS):
        return r
    for path,anchors in CONSTANTS.items():
        for anchor in anchors:
            if source[path].count(anchor)!=1:
                return r
        # No main-source Java ClassRef/constant dependency on the lab worker.
        if re.search(r"\b(?:SyntraSyntheticPcmWorkerMain|SyntraSyntheticJobProbeMain)\.(?:class|MAGIC|PREFIX|SAMPLE_RATE)\b",
                     source[path]):
            return r
    r["frozen_wire_values_verified"]=True
    # 'javap -verbose' MUST provide an actual constant pool for both classes;
    # a fabricated blank/mock stdout cannot count as bytecode verification.
    for path,output in verbose.items():
        if "Constant pool:" not in output or "Classfile " not in output:
            return r
        if CLASS_REF.search(output):
            return r
    r["compiled_jvm_constant_pool_checked"]=True
    r["synthetic_worker_symbol_link_free"]=True
    r["regular_jar_worker_entrypoints_excluded"]=(jar_worker_mains==0)
    return r

def _javap(path:Path)->str:
    cp=subprocess.run(["javap","-verbose",str(path)],capture_output=True,
                      text=True,check=False,timeout=20)
    if cp.returncode!=0 or len(cp.stdout)>500_000:
        raise ValueError("NO_TRUSTED_JAVAP_EVIDENCE")
    return cp.stdout

def review_checkout(root:Path,jar_worker_mains:int)->dict[str,object]:
    try:
        sources={}
        verbose={}
        for relative in CONSTANTS:
            sourcepath=root/"orchestrator-service/src/main/java"/relative
            classname=relative.removesuffix(".java")+".class"
            classpath=root/"orchestrator-service/target/classes"/classname
            if (not sourcepath.is_file() or sourcepath.is_symlink()
                or not classpath.is_file() or classpath.is_symlink()
                or sourcepath.stat().st_size>200_000
                or classpath.stat().st_size>500_000):
                raise ValueError("SOURCE_OR_BYTECODE_UNAVAILABLE")
            sources[relative]=sourcepath.read_text(encoding="utf-8")
            verbose[relative]=_javap(classpath)
        return review(sources,verbose,jar_worker_mains)
    except (OSError,UnicodeError,ValueError,subprocess.TimeoutExpired):
        return dict(RESULT)

def main()->int:
    parser=argparse.ArgumentParser()
    parser.add_argument("--root",type=Path,default=ROOT)
    parser.add_argument("--jar-worker-mains",type=int,required=True)
    parser.add_argument("--output",type=Path)
    args=parser.parse_args()
    result=review_checkout(args.root.resolve(),args.jar_worker_mains)
    value=json.dumps(result,indent=2,sort_keys=True)+"\n"
    print(value,end="")
    if args.output:
        args.output.write_text(value,encoding="utf-8")
    return 0 if (result["frozen_wire_values_verified"]
                 and result["synthetic_worker_symbol_link_free"]
                 and result["compiled_jvm_constant_pool_checked"]
                 and result["regular_jar_worker_entrypoints_excluded"]
                 and result["decision"]=="NO_GO") else 1
if __name__=="__main__":
    raise SystemExit(main())
