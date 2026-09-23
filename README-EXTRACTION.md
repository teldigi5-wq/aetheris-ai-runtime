# Aetheris AI Runtime Extraction Bundle

This archive is a deterministic, content-only transfer bundle generated from
`teldigi5-wq/aetheris-platform` at revision `16e1bbc2b357e36c2a484e0ff66bb81a329c1c99`.

Destination repository: `teldigi5-wq/aetheris-ai-runtime`  
Certified Step D source baseline: `7e08d8c5dca2215a406fade883b53ec299f4fb4e`  
Payload tree SHA-256: `5892e76c9332c3da18bef4fc92c662b040fa854c946c74e7bc91c1faae327d6f`

The payload is defined exclusively by `architecture/ai-runtime-extraction-manifest.json`:

- all tracked files under `move_roots`;
- the exact `transfer_workflows` files;
- the exact `copy_bootstrap` files.

The archive itself contains **no Git history**. Git history and provenance are preserved through the source/destination repository history and the certified cutover sequence rather than being embedded in this content-transfer archive.

## Historical deletion-gate note

At the time this bundle was generated, it did **not** authorize deletion of the platform source trees. Platform source removal was then blocked until the deletion gates tracked by Issue #140 were satisfied. That statement described the state **at bundle-generation time** and should not be read as the current repository state.

The subsequent certified sequence completed the runtime migration, platform-side adaptation/source removal, lineage reconciliation and stable-main promotion without restoring duplicate runtime source into the platform repository.

Current certified cutover references from that sequence are:

- AI runtime checkpoint: `teldigi5-wq/aetheris-ai-runtime@68af39a1115a7330020c18b6e2cb601e66b8f22f`;
- promoted platform stable `main`: `teldigi5-wq/aetheris-platform@6b73eaf30288e1e412b6d4cc1fa3a3ca82f9464b`.

The platform certification reference identifies the runtime-owned roots as:

- `orchestrator-service`;
- `aetheris-quant`;
- `aetheris-reasoning`;
- `workstation-agent`.

Those roots are now owned by this runtime repository. The platform retains integration contracts, certification references and evidence rather than a second copy of the runtime implementation.

## Truth boundary

This document records hosted content-transfer and repository-certification provenance only. It does **not** prove:

- physical owner-PC validation;
- production deployment or activation;
- registry publication;
- live-money execution.

Physical-machine status remains `BLOCKED_PENDING_HARDWARE`. CI/evidence gates must not be weakened or bypassed, and a future runtime checkpoint must be recertified before a platform certification reference is advanced.