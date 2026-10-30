# Enterprise E6 — R3F GLB browser visual-parity research

**30 October 2026 | OPEN DRAFT | NO PROD / NO MAIN MERGE / STAGE45 NO_GO**

## Exact predecessor

Base: E6 asset optimization PR #238, verified head `7f2db1042c73406b392e727258844896cbd64fa0`. Prior actual Blender CI saved 13,116 bytes (23.27%) by removing unused UV export; seven meshes, four PBR materials, 1512 vertices, 756 identical ordered triangle POSITION/NORMAL tuples. This branch extends the research evidence; it does **not** modify the Next.js R3F page, install a permanent rendering dependency, or substitute the user-facing GLB.

## Test design

- Both original and no-UV candidate GLBs are regenerated inside the same Ubuntu CI runner from the same original Blender .blend source and independently hashed. Previous source and structural policy tests must pass. The tracked npm v3 lock is validated; Next.js is built unchanged using `npm ci --ignore-scripts`.
- Fixed-version Playwright Chromium launches the real Next.js static export on 127.0.0.1 under SwiftShader software-WebGL. The screenshot harness intercepts **only** the existing `/research-panel.glb` request and supplies one of the two SHA-verified locally authored GLBs. No deployment or application file replacement occurs.
- Each scene captures two independent original renders to measure repeatability and one optimized render: desktop-front (1280×800), desktop-detail after a GSAP-driven scroll (1280×800), and mobile-front (390×844). Camera/scene state, loaded WebGL status, original asset replacement and absence of external requests/browser errors are recorded.
- An independent standard-library PNG decoder validates IHDR/mode, dimensions, CRCs, decompression, scanline filters and size limits. The comparison rejects flat/absent scenes, unstable baseline controls, changed dimensions, corrupt screenshots, pixel drift above bounds, and missing/hash-mismatched screenshot records. Human visual review is **not** replaced by this pixel comparison.
- The software-rendered result and actual screenshots are included in a short-lived GitHub Actions artifact. Report flags remain explicit: `noOwnerGpuCertification=true`, `noIndependentDesignAcceptance=true`, `ownerApproved=false`, `stage45Go=false`.

## Interpretation and remaining acceptance

An exact-head successful CI run establishes that the two actual Blender exports render with bounded screenshot-level similarity in the **same SwiftShader browser setup**. It is not representative of RTX 4050/other hardware FPS, VRAM, thermals, animation smoothness, actual customer network transfer, color-calibrated visual quality, mobile GPU coverage, UI WCAG 2.2 AA certification or release legality.

Before adopting the no-UV GLB in the actual R3F app, require a human visual review of screenshots and all cameras, real device/browser profiling, quality acceptance, asset and dependency-license review, reduced-motion accessibility verification, recovery on WebGL context loss and owner approval. No autonomous company operations or production deployment are authorized. Stage45 remains 0/9 independently certified and NO_GO.
