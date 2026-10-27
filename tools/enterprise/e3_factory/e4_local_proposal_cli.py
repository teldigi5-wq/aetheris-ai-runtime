#!/usr/bin/env python3
"""E4 local-only untrusted model proposal CLI. Safe plan-only by default.

Executable local model access requires --run-local plus BOTH environment
opt-ins. This does not authenticate an owner or grant code write permission.
"""
from __future__ import annotations

import argparse
import json
import os
import sys
from typing import Sequence

import e36_free_model_router as e36
import e4_free_route_admission as e4
from e4_local_proposal_session import LOCAL_EXPERIMENT_OPT_IN, LocalProposalSession


def cli(argv: Sequence[str] | None = None) -> int:
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model",required=True,help="Exact allowlisted local Ollama model")
    parser.add_argument("--task",required=True,choices=sorted(e4.ALLOWED_TASKS))
    parser.add_argument("--run-local",action="store_true",
        help="Explicitly contact locally installed Ollama; requires TWO more opt-ins")
    args=parser.parse_args(argv)

    try:
        e36.checked_choice("ollama",args.model)
    except ValueError:
        print(json.dumps({
            "status":"DENIED_UNRECOGNIZED_MODEL","inferenceExecuted":False,
            "cloudFallbackPermitted":False,"stage45Go":False,
        },sort_keys=True))
        return 2

    if not args.run_local:
        print(json.dumps({
            "status":"PLAN_ONLY_NO_INFERENCE",
            "provider":"ollama","modelId":args.model,
            "task":args.task,
            "installedModelVerified":False,
            "highResourceOptInNeeded":args.model in e36.HIGH_RESOURCE_LOCAL_CANDIDATES,
            "localInferenceExecuted":False,
            "externalDisclosure":False,
            "cloudFallbackPermitted":False,
            "sourceTreeWritten":False,
            "ownerApprovalGranted":False,
            "stage45Go":False,
            "runRequires":"--run-local plus two explicit local environment opt-ins",
        },sort_keys=True))
        return 0

    if (os.environ.get(LOCAL_EXPERIMENT_OPT_IN)!="yes"
        or os.environ.get(e36.OPT_LOCAL)!="yes"):
        print(json.dumps({
            "status":"DENIED_EXPLICIT_DOUBLE_LOCAL_OPT_IN_REQUIRED",
            "localInferenceExecuted":False,"cloudFallbackPermitted":False,
            "ownerApprovalGranted":False,"stage45Go":False,
        },sort_keys=True))
        return 2
    try:
        result=LocalProposalSession().propose(args.model,args.task)
    except (ValueError,TimeoutError,OSError):
        # Do not print provider response, credentials, stack traces or host data
        # when a local request is refused or Ollama is not running.
        print(json.dumps({
            "status":"LOCAL_PROPOSAL_DENIED_OR_UNAVAILABLE",
            "cloudFallbackPermitted":False,
            "sourceTreeWritten":False,
            "ownerApprovalGranted":False,
            "stage45Go":False,
        },sort_keys=True))
        return 2

    print(json.dumps({
        "status":"UNTRUSTED_LOCAL_PROPOSAL_REQUIRES_INDEPENDENT_REVIEW",
        "proposal":result,
        "cloudFallbackPermitted":False,
        "sourceTreeWritten":False,
        "ownerApprovalGranted":False,
        "stage45Go":False,
    },sort_keys=True))
    return 0


if __name__=="__main__":
    sys.exit(cli())
