# Enterprise E6 — measured GLB asset-size optimization baseline

**30 October 2026 · DRAFT research experiment · NO production merge, deploy or asset substitution.**

## Source of truth

Stacked on Blender source PR #237 (head 30221ad9df3fc28b9c547dd04d2d86ce399067dc), whose exact-head Ubuntu CI actually exported `.blend` and GLB and validated both with SHAs. Parent source artifact includes seven meshes, four owned PBR materials, 1,512 exported POSITION vertices, 756 triangles, and seven UV attribute sets, but **no images/textures**. The GLB is not currently used as the R3F application's active model.

## Optimizer scope and experiment

The only tested variation is `export_texcoords=False` from Blender's official glTF exporter. We generate two separate GLBs from the **same locally generated original `.blend`** within the same CI run: baseline with UV attributes and no-UV candidate. No mesh reduction, no Draco/Meshopt/KTX2 dependency, no material changes or untrusted inputs are introduced. Baseline source/provenance is checked before candidate export. Neither GLB overwrites the other or the existing `research-panel.glb`.

Offline `compare_glb_variants.py` parses GLB JSON/binary structures and fails unless both have identical primitive POSITION/normal accessor counts, same per-primitive triangle/index metadata and position min/max, identical PBR material dictionaries and scene-node metadata, identical overall triangle count, original UV sets present and candidate sets absent. Both are separately validated against the existing E6 GLB structural budget and no-external-asset policy. The strengthened comparison decodes the binary POSITION/NORMAL/index data and requires all triangles in the same order with **exactly matching positions and normals**; an altered payload is tested as a negative control. This is stronger geometry evidence, **not image-level visual equality.**

Report `target/e6-studio/blender-proof/asset-size-comparison.json` contains SHA-256, exact uncompressed and deterministic gzip-level-9 byte counts, vertex/triangle/mesh/material counts, original/candidate UV counts, and size savings. The gzip figures are hypothetical content-encoding sizes produced offline. We **do not** claim a CDN uses gzip for GLB or infer internet throughput, frame time, memory, power, thermals, WebGL stability, texture detail or subjective aesthetic quality from size alone.

## Acceptance and no-go

Full exact-head CI must regenerate both variants in real Blender, run offline adversarial tests, verify byte-size savings while preserving metadata, and retain all assets in a short-lived GitHub Actions artifact. Success proves a measurable research size reduction, not release readiness. Before adoption: image-diff screenshots in real browsers from identical cameras, visual QA, hardware memory and GPU timings, no external loading, reduced-motion fallback, owner approval, distribution/license review and Stage45 independent certification.

**Stage45: NO_GO. Owner device: not benchmarked. E6 Cinematic Studio: incomplete.**

## Verified size baseline and independent hardening

The initial exact-head run #41067798032 (SHA `7ccb4c050b8247b66aedbfab6c42d82a7b88271a`) passed the metadata comparison. It generated 56,356-byte original and 43,240-byte no-UV GLBs: **13,116 raw bytes / 23.27% less**. The deterministic gzip-9 sizes were 6,955 and 4,736 bytes. Separate inspection of those actual output binaries independently matched 756/756 decoded ordered position/normal triangles. The new strict binary-payload equality regression must pass its subsequent exact-head CI before being accepted as an enforced gate. No visual, transfer, GPU, frame-time or release certification is claimed.
