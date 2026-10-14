#!/usr/bin/env bash
set -euo pipefail

# Synthetic CI-only lifecycle proof. This script is NOT an agent-invokable
# runner or deployable service and never accepts image/command/host paths.
if [[ "${GITHUB_ACTIONS:-}" != "true" ||
      "${RUNNER_OS:-}" != "Linux" ||
      "${AETHERIS_STAGE45_STOP_CI:-}" != "true" ]]; then
  echo "BLOCKED: disposable Linux GitHub Actions lab only" >&2
  exit 1
fi

image="ghcr.io/containerd/busybox@sha256:907ca53d7e2947e849b839b1cd258c98fd3916c60f2e6e70c30edbf741ab6754"
root="$(mktemp -d)"
name="aetheris-stage45-stop-$RANDOM-$RANDOM"
cid=""
cleanup() {
  if [[ -n "$cid" ]]; then
    docker rm --force "$cid" >/dev/null 2>&1 || true
  fi
  rm -rf "$root"
}
trap cleanup EXIT
chmod 755 "$root"
mkdir -m 755 "$root/work"
cat > "$root/work/worker.sh" <<'WORKER'
#!/bin/sh
set -eu
# Three generations of processes to exercise PID-namespace shutdown.
sleep 300 &
sh -c 'sleep 300' &
sh -c 'sh -c "sleep 300"' &
echo "SYNTHETIC_WORKER_READY"
wait
WORKER
chmod 644 "$root/work/worker.sh"

# We never pass the host's secret to Docker.
export AETHERIS_TEST_HOST_SECRET="SYNTHETIC_HOST_SECRET_SHOULD_NOT_LEAK"
docker pull "$image" >/dev/null
cid="$(docker run -d --pull=never --name "$name" \
  --network=none --read-only --cap-drop=ALL \
  --security-opt=no-new-privileges:true \
  --pids-limit=32 --memory=128m --cpus=0.50 --ipc=none \
  --user=65534:65534 \
  --tmpfs=/tmp:rw,nosuid,nodev,noexec,size=16m \
  --mount="type=bind,source=$root/work,target=/workspace,readonly" \
  --workdir=/workspace "$image" /bin/sh /workspace/worker.sh)"

# Ensure the fixture actually launched a running process tree, instead
# of counting an early crash as successful cancellation.
ready=0
for _ in $(seq 1 30); do
  if docker logs "$cid" 2>/dev/null | grep -q '^SYNTHETIC_WORKER_READY$'; then
    ready=1
    break
  fi
  sleep 0.2
done
if [[ "$ready" != 1 ]]; then
  echo "FAIL: synthetic worker did not become ready" >&2
  exit 1
fi

# BusyBox applets may show "busybox" (not "sleep") in ps comm.
# Count distinct live PID rows rather than infer process identity from
# an executable label, and require PID 1 plus at least two descendants.
descendants_observed=0
for _ in $(seq 1 30); do
  before="$(docker top "$cid" -eo pid,ppid,comm)"
  process_count="$(printf '%s\n' "$before" | awk 'NR>1 && NF>0 {n++} END {print n+0}')"
  if [[ "$process_count" -ge 3 ]]; then
    descendants_observed=1
    break
  fi
  sleep 0.2
done
if [[ "$descendants_observed" != 1 ]]; then
  echo "FAIL: expected synthetic parent and descendant PID rows" >&2
  exit 1
fi

# Emergency STOP kills the entire container PID namespace instead of
# merely marking an application task cancelled. This does NOT yet wire
# TaskControlService to a production sandbox worker.
docker kill --signal=KILL "$cid" >/dev/null
stopped=0
for _ in $(seq 1 25); do
  running="$(docker inspect --format '{{.State.Running}}' "$cid")"
  pid="$(docker inspect --format '{{.State.Pid}}' "$cid")"
  if [[ "$running" == "false" && "$pid" == "0" ]]; then
    stopped=1
    break
  fi
  sleep 0.2
done
if [[ "$stopped" != 1 ]]; then
  echo "FAIL: container STOP did not terminate the runtime PID namespace" >&2
  exit 1
fi
if docker top "$cid" >/dev/null 2>&1; then
  echo "FAIL: stopped workload still exposes a live process listing" >&2
  exit 1
fi
if docker ps -q --no-trunc | grep -Fxq "$cid"; then
  echo "FAIL: workload remained active after STOP" >&2
  exit 1
fi
if [[ -e "$root/work/worker-output" ]]; then
  echo "FAIL: container unexpectedly modified the read-only host fixture" >&2
  exit 1
fi
echo "PASS: synthetic OCI process tree observed, STOP killed container PID namespace, read-only fixture preserved"
echo "NOTE: independent lab check; no real TaskControlService/worker transport connection"
