# Enterprise E6 — original procedural Blender source and GLB research proof

**30 October 2026 — DRAFT, UNMERGED, research-only. Stage45 NO_GO.**

## Scope and purpose

The current E6 R3F website loads a deterministic generated GLB fixture, not an authentic Blender project. This incremental source pipeline uses Blender 4.0.x to author and save an original, small Aetheris command-module scene and export a self-contained glTF 2.0 binary. The object depicts a machined physical module with a metallic chassis, dark governed-runtime inset, three status rails and connectors. It is an original procedural scene, **not** a human-sculpted photorealistic production hero or AI-generated creative benchmark.

## Implementation

- `tools/enterprise/e6_studio/next-studio/blender/author_command_module.py` is reviewable Blender Python source; it does not download third-party meshes, photos, textures or materials.
- Ubuntu 24.04 runner installs Blender 4.0.2+dfsg-1ubuntu8 from the signed Ubuntu apt repository. This creates no application production dependency. The exact apt package version is selected explicitly; dependencies are still Ubuntu repository-managed.
- Blender saves `.blend` source and exports `aetheris-command-module.glb` using its real glTF exporter; both remain CI artifacts under `target/e6-studio/blender-proof/`.
- `validate_blender_glb.py` enforces GLB 2.0 structural checks, bounded size and vertices, embedded binary buffer, finite mesh/material budgets, PBR materials and no external asset references, extensions, images, cameras or animations. Its negative tests reject altered headers, truncated files, external buffers and unreviewed extensions.
- Manifest pins GLB, `.blend` and authoring script by SHA-256 and records actual Blender version. Manifest flags remain `assetApproved=false`, `ownerApproved=false`, `licensingCertified=false`, `productionReady=false`, `stage45Go=false`.
- This does not overwrite or change `public/research-panel.glb` or the existing Next.js scene and does not touch independent S0 security or protected main.

## Tests / release limitations

New offline negative source/GLB tests must pass first. An independent actual Blender CLI CI job is required to produce real `.blend`/GLB evidence and verify it. Until that job completes, files are only implementation proposals, not proven exports. No physical GPU, texture/UV quality, visual-design acceptance, accessibility, performance, licensing legal review, or device validation has been established.

Reference API: https://docs.blender.org/api/4.0/bpy.ops.export_scene.html . Ubuntu signed package reference: https://packages.ubuntu.com/noble/amd64/blender .
Stage45 0/9 independently certified, NO_GO. No merge or deployment.
