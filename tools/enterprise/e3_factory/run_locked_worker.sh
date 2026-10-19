#!/usr/bin/env bash
# E3.1 fixed-command disposable Docker runner. Never for owner's PC.
set -euo pipefail
if [[ $# -ne 1 ]]; then echo "E31_PHASE_REQUIRED" >&2; exit 2; fi
phase="$1"
case "$phase" in
  build) entry="worker.py"; args=();;
  seed|repair) entry="fault_repair.py"; args=("$phase");;
  *) echo "E31_UNKNOWN_WORKER_PHASE" >&2; exit 2;;
esac

repo="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd -P)"
cd "$repo"
mkdir -p target/e3-factory
if [[ -L target || -L target/e3-factory ]]; then
  echo "E31_WORKSPACE_SYMLINK_REJECTED" >&2; exit 2
fi
if [[ "$(realpath target/e3-factory)" != "$repo/target/e3-factory" ]]; then
  echo "E31_OUTPUT_SCOPE_REJECTED" >&2; exit 2
fi
image="python:3.12-slim-bookworm"
cid=""
inspection=""
cleanup() {
  if [[ -n "$cid" ]]; then docker rm -f "$cid" >/dev/null 2>&1 || true; fi
  if [[ -n "$inspection" ]]; then rm -f "$inspection"; fi
}
trap cleanup EXIT

# Image download is a CI harness setup step, NOT performed inside the worker.
# Pull with a reviewed workflow, then use this fixed local image.
docker image inspect "$image" >/dev/null
cid="$(docker create \
  --network none \
  --cpus 1 \
  --memory 256m --memory-swap 256m \
  --pids-limit 64 \
  --read-only --cap-drop ALL --security-opt no-new-privileges \
  --user "$(id -u):$(id -g)" \
  --tmpfs /tmp:rw,noexec,nosuid,size=16m \
  --mount "type=bind,src=$repo,dst=/workspace,readonly" \
  --mount "type=bind,src=$repo/target,dst=/workspace/target" \
  --workdir /workspace \
  -e PYTHONDONTWRITEBYTECODE=1 -e PYTHONUNBUFFERED=1 \
  "$image" python3 "/workspace/tools/enterprise/e3_factory/$entry" "${args[@]}")"
inspection="$(mktemp)"
docker inspect "$cid" > "$inspection"
python3 - "$inspection" "target/e3-factory/sandbox-$phase.json" "$phase" <<'PY'
import json, sys
from pathlib import Path
container = json.loads(Path(sys.argv[1]).read_text())[0]
host = container["HostConfig"]
mounts = {m["Destination"]: m for m in container["Mounts"]}
assert host["NetworkMode"] == "none"
assert host["ReadonlyRootfs"] is True
assert host["Memory"] == 268435456 and host["MemorySwap"] == 268435456
assert host["NanoCpus"] == 1000000000 and host["PidsLimit"] == 64
assert "ALL" in host["CapDrop"]
assert "no-new-privileges" in host["SecurityOpt"]
assert mounts["/workspace"]["RW"] is False
assert mounts["/workspace/target"]["RW"] is True
assert all(m["Destination"] in {"/workspace", "/workspace/target"} for m in container["Mounts"])
report = {
    "evidenceClass": "DOCKER_INSPECTED_DISPOSABLE_CI_WORKER",
    "phase": sys.argv[3], "network": "none", "readOnlyRootfs": True,
    "cpuLimit": 1, "memoryBytes": 268435456, "pidsLimit": 64,
    "noNewPrivileges": True, "allCapabilitiesDropped": True,
    "repositoryMount": "read-only", "targetMount": "read-write",
    "wallClockLimitSeconds": 45, "imageId": container["Image"],
    "ownerPcUsed": False, "productionDeployment": False,
    "ownerApprovalGranted": False
}
Path(sys.argv[2]).write_text(json.dumps(report, sort_keys=True, indent=2) + "\n")
print("E31_ENFORCED_DOCKER_POLICY", sys.argv[3])
PY
# The timeout is enforced outside the container. Cleanup forcibly kills leftovers.
timeout --signal=TERM --kill-after=3s 45s docker start -a "$cid"
