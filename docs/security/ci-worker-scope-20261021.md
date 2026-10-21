# CI-only Docker worker — immutable image ID and writable scope (2026-10-21)

## Existing boundary and scope
Aetheris already has a **CI-only** Docker worker in
`tools/enterprise/e3_factory/run_locked_worker.sh`. It ran with
`--network none`, read-only root, capabilities dropped, no-new-privileges,
CPU/memory/PID limits, fixed phase commands and automatic cleanup. This
slice **does not** create a production agent worker or change the live
orchestrator's terminal execution.

## Issues closed in the CI harness
- Before: the worker mounted `repo/target` as read-write, giving it access
  to unrelated generated build artifacts. Now the only writable bind mount is
  `target/e3-factory`; the checked-out source remains read-only.
- Before: the runner inspected image tag `python:3.12-slim-bookworm` and
  created the container by mutable tag. Now the already-present local tag
  is resolved to `sha256:<64 hex>` exactly once, and the container is
  created from that exact image ID.
- Reject attempting the worker as root and reject output symlinks before
  touching target directories. Add `--ipc none` and inspect Docker's
  actual mount, IPC, no-privilege and image ID properties.
- Keep image fetching a separate explicit **trusted CI setup** step;
  the worker never pulls images, reaches the network or receives model keys.
  A local image ID is immutable on that Docker daemon, but **not** a
  signed provenance guarantee for an image imported to a different daemon.

## Evidence and known limitations
Linux GitHub CI executes a frozen deterministic site generator in a real
non-root, resource-limited container, inspects Docker's actual config, and
verifies generated output SHA256 hashes. Static policy tests also run on
Ubuntu and Windows. No owner workstation, live site, code-writing agent,
privileged tool, production service, model output or autonomous execution
is connected to this proof.

Remaining: trusted pinned upstream image supply chain, secured Docker daemon,
absolute/mount-symlink races, comprehensive image attestation, process
isolation of all agent tools, filesystem quotas, executable allowlisting,
network DNS/egress guarantees and owner approvals. Do not describe this
harness as a security certification of a generic AI worker. Production
remains **NO_GO** and all owner-PC acceptance gates are still open.
