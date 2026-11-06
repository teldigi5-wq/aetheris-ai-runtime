#!/usr/bin/env python3
"""Read-only local capability discovery for S-B62-A physical-fencing planning.

Only command availability is inspected. No subprocesses are launched, no
network connections are made, and no host, user, IP, paths, secrets or
environment variable values are collected or emitted.

A tool existing on PATH says nothing about whether Docker is running, whether
there are independent physical hosts, or whether external fencing works.
Every outcome remains NO_GO and exits nonzero by design.
"""
import json
import shutil
import sys

SCHEMA = "aetheris.s-b62-a.owner-cli-readiness.v1"
TOOLS = ("docker", "psql", "pg_ctl", "kubectl", "helm", "openssl", "ssh", "git")


def readiness(which=None):
    """Deterministic, non-secret-leaking command presence indicators only."""
    if which is None:
        which = shutil.which
    # Some local command/PATH lookups fail with OS or decoding errors.
    # Treat them as absent; never expose an exception containing host paths.
    found = {}
    for name in TOOLS:
        try:
            found[name] = which(name) is not None
        except (OSError, ValueError):
            found[name] = False
    return {
        "schema": SCHEMA,
        "scope": "local-command-presence-only",
        "tools_present": found,
        "deployed_runtime_image_verified": False,
        "independent_host_domains_verified": False,
        "independent_admin_domains_verified": False,
        "physical_old_primary_fence_verified": False,
        "privileged_epoch_rollback_denied_verified": False,
        "independent_security_review_approved": False,
        "stage": "S-B62-A_IN_PROGRESS",
        "release_decision": "STAGE45_NO_GO",
        "xerj_publication": "HOLD",
        "note": "Command presence only. No external host or fencing tests were performed.",
    }


def main():
    if len(sys.argv) != 1:
        print(json.dumps({"release_decision": "STAGE45_NO_GO",
                          "error": "no command arguments supported"}, sort_keys=True))
        return 2
    print(json.dumps(readiness(), sort_keys=True, indent=2))
    return 1  # Even all tools being installed never certifies independent fencing.


if __name__ == "__main__":
    sys.exit(main())
