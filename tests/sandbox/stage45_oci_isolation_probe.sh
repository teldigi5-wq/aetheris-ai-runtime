#!/usr/bin/env bash
set -euo pipefail

# ONLY tests synthetic adversarial code on disposable GitHub-hosted runners.
# Not an agent tool or operational executor. Environment flags are NOT a
# cryptographic trust boundary; never connect this to a deployed service.
if [[ "${GITHUB_ACTIONS:-}" != "true" ||
      "${RUNNER_OS:-}" != "Linux" ||
      "${AETHERIS_STAGE45_SYNTHETIC_CI:-}" != "true" ]]; then
  echo "BLOCKED: standalone synthetic GitHub Actions isolation probe only" >&2
  exit 1
fi

image="ghcr.io/containerd/busybox@sha256:907ca53d7e2947e849b839b1cd258c98fd3916c60f2e6e70c30edbf741ab6754"
temp_dir="$(mktemp -d)"
trap 'rm -rf "$temp_dir"' EXIT
chmod 755 "$temp_dir"
mkdir -m 755 "$temp_dir/work"
printf 'SYNTHETIC_READ_ONLY_WORKSPACE\n' > "$temp_dir/work/fixture.txt"
chmod 644 "$temp_dir/work/fixture.txt"
cat > "$temp_dir/work/probe.sh" <<'PROBE'
#!/bin/sh
set -eu
test "$(cat /workspace/fixture.txt)" = "SYNTHETIC_READ_ONLY_WORKSPACE"
test ! -e /var/run/docker.sock
test ! -e /run/docker.sock
test ! -e /workspace/host-secret.txt
test -z "${AETHERIS_HOST_SECRET:-}"
# A successful write here is a critical failure.
if (printf 'UNAUTHORIZED' > /workspace/should-not-exist 2>/dev/null); then
  echo "FAIL: readonly workspace was writable"
  exit 1
fi
test ! -e /workspace/should-not-exist
# No non-loopback routing in --network none (no usable egress routes).
test "$(awk 'END {print NR}' /proc/net/route)" -eq 1
# Operating system enforces no-new-privileges and removes all capabilities.
grep -Eq '^NoNewPrivs:[[:space:]]*1([[:space:]]*)$' /proc/self/status
grep -Eq '^CapEff:[[:space:]]*0+([[:space:]]*)$' /proc/self/status
printf 'EXPECTED_TMPFS_WRITE\n' > /tmp/safe.txt
test "$(cat /tmp/safe.txt)" = "EXPECTED_TMPFS_WRITE"
printf '#!/bin/sh\nexit 0\n' > /tmp/executable
chmod 755 /tmp/executable
if (/tmp/executable >/dev/null 2>&1); then
  echo "FAIL: noexec tmpfs was executable"
  exit 1
fi
echo "PASS: isolated container observed denied writes, no egress route, no inherited host secret, and no extra capabilities"
PROBE
chmod 644 "$temp_dir/work/probe.sh"

# This pulls a pinned OCI digest on the CI host. The container cannot access
# the network. No Docker socket, credentials or unrestricted host directories
# are mounted into the workload.
docker pull "$image" >/dev/null
export AETHERIS_HOST_SECRET="SYNTHETIC_SECRET_THAT_MUST_NOT_ENTER_CONTAINER"
docker run --rm --pull=never \
  --network=none --read-only --cap-drop=ALL \
  --security-opt=no-new-privileges:true \
  --pids-limit=32 --memory=128m --cpus=0.50 --ipc=none \
  --user=65534:65534 \
  --tmpfs=/tmp:rw,nosuid,nodev,noexec,size=16m \
  --mount="type=bind,source=$temp_dir/work,target=/workspace,readonly" \
  --workdir=/workspace "$image" /bin/sh /workspace/probe.sh
test ! -e "$temp_dir/work/should-not-exist"
test "$(cat "$temp_dir/work/fixture.txt")" = "SYNTHETIC_READ_ONLY_WORKSPACE"
echo "PASS: synthetic host fixture remains unchanged"
