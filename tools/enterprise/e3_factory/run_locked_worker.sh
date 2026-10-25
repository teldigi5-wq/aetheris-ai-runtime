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
  e39g12b4k-apply)
    [[ $# -eq 2 ]] || exit 2
    case "$2" in focus|motion) ;; *) echo "E39G12B4K_UNREVIEWED_MODEL_CASE" >&2; exit 2;; esac
    entry="e39g12b4k_model_patch_worker.py"
    args=("apply" "$2")
    ;;
  e39g12b4j-apply)
    [[ $# -eq 2 ]] || exit 2
    case "$2" in focus|motion) ;; *) echo "E39G12B4J_UNREVIEWED_CONTROL" >&2; exit 2;; esac
    entry="e39g12b4j_typed_control_worker.py"
    args=("apply" "$2")
    ;;
  e39g12b4a-apply)
    [[ $# -eq 2 ]] || exit 2
    case "$2" in
      studynest--keyboard-focus-visibility|studynest--responsive-mobile-overflow|cityline--reduced-motion|cityline--safe-untrusted-text) ;;
      *) echo "E39G12B4A_UNREVIEWED_SITE_FAULT" >&2; exit 2;;
    esac
    entry="e39g12b4a_policy_worker.py"
    args=("apply" "$2")
    ;;
  e39g12b3-apply)
    [[ $# -eq 2 ]] || exit 2
    case "$2" in
      studynest--keyboard-focus-visibility|studynest--responsive-mobile-overflow|cityline--reduced-motion|cityline--safe-untrusted-text) ;;
      *) echo "E39G12B3_UNREVIEWED_SITE_FAULT" >&2; exit 2;;
    esac
    entry="e39g12b3_policy_worker.py"
    args=("apply" "$2")
    ;;
  e39g12b-seed)
    [[ $# -eq 1 ]] || exit 2
    entry="e39g12b_two_site_corpus.py"
    args=("seed")
    ;;
  e39g12a-apply|e39g12a-reset|e39g12a-done)
    [[ $# -eq 2 ]] || exit 2
    case "$2" in
      qwen25-coder-15b--mobile-940--original|qwen25-coder-15b--mobile-940--targeted|qwen25-coder-15b--reduced-boolean--original|qwen25-coder-15b--reduced-boolean--targeted|qwen25-coder-15b--scroll-24--original|qwen25-coder-15b--scroll-24--targeted) ;;
      *) echo "E39G12A_UNREVIEWED_CODING_MODEL_CASE_ARM" >&2; exit 2;;
    esac
    entry="e39g12a_coder_gate.py"
    args=("${phase#e39g12a-}" "$2")
    ;;
  e39g11-apply|e39g11-reset|e39g11-done)
    [[ $# -eq 2 ]] || exit 2
    case "$2" in
      granite4-3b--mobile-940--targeted|granite4-3b--mobile-940--semantic|granite4-3b--reduced-boolean--targeted|granite4-3b--reduced-boolean--semantic|granite4-3b--scroll-24--targeted|granite4-3b--scroll-24--semantic|hermes3-3b--mobile-940--targeted|hermes3-3b--mobile-940--semantic|hermes3-3b--reduced-boolean--targeted|hermes3-3b--reduced-boolean--semantic|hermes3-3b--scroll-24--targeted|hermes3-3b--scroll-24--semantic) ;;
      *) echo "E39G11_TOKEN_UNREVIEWED" >&2; exit 2;;
    esac
    entry="e39g11_diagnosis_gate.py"
    args=("${phase#e39g11-}" "$2")
    ;;
  e39g10-apply|e39g10-reset|e39g10-done)
    [[ $# -eq 2 ]] || exit 2
    case "$2" in
      granite4-3b--mobile-940--original|granite4-3b--mobile-940--targeted|granite4-3b--reduced-boolean--original|granite4-3b--reduced-boolean--targeted|granite4-3b--scroll-24--original|granite4-3b--scroll-24--targeted|hermes3-3b--mobile-940--original|hermes3-3b--mobile-940--targeted|hermes3-3b--reduced-boolean--original|hermes3-3b--reduced-boolean--targeted|hermes3-3b--scroll-24--original|hermes3-3b--scroll-24--targeted) ;;
      *) echo "E39G10_TOKEN_NOT_APPROVED" >&2; exit 2;;
    esac
    entry="e39g10_targeted_gate.py"
    args=("${phase#e39g10-}" "$2")
    ;;
  e39g9-seed)
    [[ $# -eq 2 ]] || exit 2
    case "$2" in mobile-940|reduced-boolean|scroll-24) ;; *) echo "E39G9_SEED_CASE_DENIED" >&2; exit 2;; esac
    entry="e39g9_multidomain_worker.py"
    args=("seed" "$2")
    ;;
  e39g9-apply|e39g9-reset|e39g9-done)
    [[ $# -eq 2 ]] || exit 2
    case "$2" in
      granite4-3b--mobile-940--baseline|granite4-3b--mobile-940--skill-context|granite4-3b--reduced-boolean--baseline|granite4-3b--reduced-boolean--skill-context|granite4-3b--scroll-24--baseline|granite4-3b--scroll-24--skill-context|hermes3-3b--mobile-940--baseline|hermes3-3b--mobile-940--skill-context|hermes3-3b--reduced-boolean--baseline|hermes3-3b--reduced-boolean--skill-context|hermes3-3b--scroll-24--baseline|hermes3-3b--scroll-24--skill-context) ;;
      *) echo "E39G9_MODEL_CASE_ARM_NOT_APPROVED" >&2; exit 2;;
    esac
    entry="e39g9_multidomain_worker.py"
    args=("${phase#e39g9-}" "$2")
    ;;
  e39g8-apply|e39g8-cleanup)
    [[ $# -eq 2 ]] || exit 2
    case "$2" in
      granite4-3b--top-120--baseline|granite4-3b--top-120--skill-context|granite4-3b--opacity-zero--baseline|granite4-3b--opacity-zero--skill-context|hermes3-3b--top-120--baseline|hermes3-3b--top-120--skill-context|hermes3-3b--opacity-zero--baseline|hermes3-3b--opacity-zero--skill-context) ;;
      *) echo "E39G8_UNAPPROVED_MODEL_CASE_ARM" >&2; exit 2;;
    esac
    entry="e39g8_policy_worker.py"
    args=("${phase#e39g8-}" "$2")
    ;;
  e39f-restore)
    [[ $# -eq 2 ]] || exit 2
    case "$2" in
      challenge-0[1-8]) ;;
      *) echo "E39F_UNSUPPORTED_RUNTIME_CHALLENGE" >&2; exit 2;;
    esac
    entry="e39f_runtime_sealed_challenge.py"
    args=("restore" "$2")
    ;;
  *) echo "E31_UNKNOWN_WORKER_PHASE" >&2; exit 2;;
esac
evidence_id="$phase"
if [[ "$phase" == e32-* || "$phase" == e33-* || "$phase" == e34-* || "$phase" == e39d-* || "$phase" == e39e-* || "$phase" == e39f-* || "$phase" == e39g8-* || "$phase" == e39g9-* || "$phase" == e39g10-* || "$phase" == e39g11-* || "$phase" == e39g12a-* || "$phase" == e39g12b3-* || "$phase" == e39g12b4a-* || "$phase" == e39g12b4j-* || "$phase" == e39g12b4k-* ]]; then evidence_id="$phase-$2"; fi

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
  --mount "type=bind,src=$repo/target/e3-factory,dst=/workspace/target/e3-factory" \
  --workdir /workspace \
  -e PYTHONDONTWRITEBYTECODE=1 -e PYTHONUNBUFFERED=1 \
  "$image" python3 "/workspace/tools/enterprise/e3_factory/$entry" "${args[@]}")"
inspection="$(mktemp)"
docker inspect "$cid" > "$inspection"
python3 - "$inspection" "target/e3-factory/sandbox-$evidence_id.json" "$evidence_id" <<'PY'
import json, sys
from pathlib import Path
container = json.loads(Path(sys.argv[1]).read_text())[0]
from tools.security.locked_worker_inspection import validate_inspection
validate_inspection(container)
report = {
    "evidenceClass": "DOCKER_INSPECTED_DISPOSABLE_CI_WORKER",
    "phase": sys.argv[3], "network": "none", "readOnlyRootfs": True,
    "cpuLimit": 1, "memoryBytes": 268435456, "pidsLimit": 64,
    "noNewPrivileges": True, "allCapabilitiesDropped": True,
    "repositoryMount": "read-only", "targetMount": "e3-factory-only-read-write",
    "wallClockLimitSeconds": 45, "imageId": container["Image"],
    "ownerPcUsed": False, "productionDeployment": False,
    "ownerApprovalGranted": False
}
Path(sys.argv[2]).write_text(json.dumps(report, sort_keys=True, indent=2) + "\n")
print("E31_ENFORCED_DOCKER_POLICY", sys.argv[3])
PY
# The timeout is enforced outside the container. Cleanup forcibly kills leftovers.
timeout --signal=TERM --kill-after=3s 45s docker start -a "$cid"
