#!/usr/bin/env bash
# E3.1 fixed-command disposable Docker runner. Never for owner's PC.
set -euo pipefail
if [[ $# -lt 1 || $# -gt 2 ]]; then echo "E31_PHASE_REQUIRED" >&2; exit 2; fi
phase="$1"
case "$phase" in
  build) [[ $# -eq 1 ]] || exit 2; entry="worker.py"; args=();;
  seed|repair) [[ $# -eq 1 ]] || exit 2; entry="fault_repair.py"; args=("$phase");;
  e32-seed|e32-repair)
    [[ $# -eq 2 ]] || exit 2
    case "$2" in scroll|mobile|reduced) ;; *) echo "E32_UNSUPPORTED_CASE" >&2; exit 2;; esac
    entry="multi_fault.py"
    action="${phase#e32-}"
    args=("$action" "$2")
    ;;
  e33-propose|e33-apply)
    [[ $# -eq 2 ]] || exit 2
    case "$2" in scroll|mobile|reduced) ;; *) echo "E33_UNSUPPORTED_CATEGORY" >&2; exit 2;; esac
    entry="heuristic_repair.py"
    action="${phase#e33-}"
    args=("$action" "$2")
    ;;
  e34-prepare)
    [[ $# -eq 2 ]] || exit 2
    case "$2" in scroll|mobile|reduced) ;; *) echo "E34_UNSUPPORTED_CATEGORY" >&2; exit 2;; esac
    entry="e34_model_adviser.py"
    args=("prepare" "$2")
    ;;
  e39-apply)
    [[ $# -eq 1 ]] || exit 2
    entry="e39_keyboard_patch_gate.py"
    args=("apply")
    ;;
  e39b-apply)
    [[ $# -eq 1 ]] || exit 2
    entry="e39b_model_patch_gate.py"
    args=("apply")
    ;;
  e39c-apply)
    [[ $# -eq 1 ]] || exit 2
    entry="e39c_selector_policy.py"
    args=("apply")
    ;;
  e39c2-apply)
    [[ $# -eq 1 ]] || exit 2
    entry="e39c2_model_policy.py"
    args=("apply")
    ;;
  e39d-restore)
    [[ $# -eq 2 ]] || exit 2
    case "$2" in
      top-40|top-120|left-120|left-240|opacity-zero|visibility-hidden|transform-up|transform-left)
        ;;
      *) echo "E39D_UNKNOWN_TEST_CASE" >&2; exit 2;;
    esac
    entry="e39d_heldout_focus_evaluator.py"
    args=("restore" "$2")
    ;;
  e39e-apply|e39e-cleanup)
    [[ $# -eq 2 ]] || exit 2
    case "$2" in
      top-40|top-120|left-120|left-240|opacity-zero|visibility-hidden|transform-up|transform-left)
        ;;
      *) echo "E39E_UNKNOWN_MODEL_BENCHMARK_CASE" >&2; exit 2;;
    esac
    entry="e39e_model_policy.py"
    args=("${phase#e39e-}" "$2")
    ;;
  *) echo "E31_UNKNOWN_WORKER_PHASE" >&2; exit 2;;
esac
evidence_id="$phase"
if [[ "$phase" == e32-* || "$phase" == e33-* || "$phase" == e34-* || "$phase" == e39d-* || "$phase" == e39e-* ]]; then evidence_id="$phase-$2"; fi

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
python3 - "$inspection" "target/e3-factory/sandbox-$evidence_id.json" "$evidence_id" <<'PY'
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
