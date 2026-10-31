# Aetheris E6 Task 7 — Owner workstation preflight

**31 October 2026 · Stacked E6 research only · no hardware results · Stage45 NO_GO**

## Why this exists

The actual Chrome/Blender/device acceptance benchmark in PR #244 has a long preparation path. A read-only, no-dependency check makes those prerequisites explicit before asking the owner to build a Blender scene or launch real Chrome. It does not run Chrome, Blender, an HTTP server, Playwright, Python, models or `npm install`, does not submit anything to GitHub, and does not collect/measure a GPU.

## Quick start — existing development checkout

Verify the local GitHub account is the correct one (`teldigi5-wq`) and the repository is **`aetheris-ai-runtime`**, not `aetheris-platform` or BoundaryLab. Run from the repository root in a terminal:

```bash
git remote -v
git fetch origin
git switch feat/e6-owner-device-preflight-20261031
git status --short
node tools/enterprise/e6_studio/next-studio/owner_device_preflight.mjs --json
```

The command prints a JSON report with individual blockers, and returns exit code **2** if anything required for a local attempt is missing. An exit code of **0** means only that local prerequisites and the built preview have been detected; it does **not** mean GPU, VRAM, thermals, mobile devices, visual design or Stage45 were verified.

## Blockers and targeted fixes

| Blocker | Safe preparation action |
| --- | --- |
| `E6_WRONG_REPOSITORY_OR_ORIGIN` / `E6_GIT_HEAD_UNVERIFIED` | Check `git remote -v`, `git rev-parse HEAD` and correct repository; do not rewrite history |
| `E6_TRACKED_SOURCE_DIRTY` | Inspect changes with `git diff`; preserve work, do not discard/stash unreviewed changes blindly |
| `E6_NODE22_REQUIRED` | Select Node.js 22 via nvm or a trusted installed Node manager; Node 24 has not been accepted by this policy |
| `E6_BLENDER_NOT_FOUND` | Install/pin the known Blender 4.0.2 and add its command to PATH. Discovery is not version certification |
| `E6_PYTHON_NOT_FOUND` | Install Python 3 and ensure `python3`/`python`/`py` launcher is accessible. Discovery alone does not prove version or packages |
| `E6_LOCAL_CHROME_NOT_FOUND` | Install **Google Chrome**, not merely generic Chromium; keep an actual visible browser available on the owner workstation |
| `E6_PLAYWRIGHT_NOT_INSTALLED` | With access to the existing Node 22 toolchain, install the pinned test-only Playwright 1.55.0 in `tools/enterprise/e6_studio`; do not add it to the production Next app lock |
| `E6_STAGED_BLENDER_PROVENANCE_MISSING`, `E6_PREVIEW_GLB_UNBUILT_OR_STALE` | Follow the [Task 7 build guide](E6_OWNER_GPU_BENCHMARK_20261031.md) to regenerate and strictly verify the original Blender `.blend` and GLB before staging the preview. Do not alter the checksum or supply fake files |
| `E6_NEXT_PREVIEW_STATIC_EXPORT_MISSING` | Build the isolated `NEXT_PUBLIC_E6_BLENDER_PREVIEW=1` static research export as documented; default `/` still uses the old fixture |
| `E6_CI_CANNOT_BE_OWNER_HARDWARE` | Run only on a locally controlled device. Do not weaken this guard or substitute GitHub Actions/SwiftShader |

## Windows and WSL2

On native Windows PowerShell, the command is the same `node .../owner_device_preflight.mjs --json` when Node is in PATH. The CLI checks known installed Chrome locations without exposing absolute paths or personal identifiers. Chrome, Blender and Python path detection only establishes *presence*; it does not execute/version/attest the binaries.

On WSL2, the report flags `E6_WSL_GRAPHICS_TRANSLATION_REQUIRES_SEPARATE_PHYSICAL_GPU_VERIFICATION` as a warning. A WSL graphics renderer may be virtualized or translated, and a WSL environment will not necessarily discover native Windows Chrome. You may use WSL to prepare the Blender assets, but capture physical-device performance using a genuine visible browser/OS setup. Do not treat an emulated or cloud-browser result as a physical GPU run.

## Security, provenance and truthful status

Read-only preflight inspects the git origin, tracked working tree, HEAD format, available commands, Node major version, locally installed Playwright, staging manifest kind and explicit `stage45Go:false`, SHA-256 match against the local Blender preview GLB, and presence of static HTML export. The GLB is bounded to 5 MiB before reading; the manifest is bounded to 256 KiB. It only reports summarized booleans and the source HEAD, not paths, serials, environment secrets, exact GPU name, or raw hardware results.

Passing the preflight allows the owner to *attempt* the actual [benchmark command and methodology](E6_OWNER_GPU_BENCHMARK_20261031.md). The task still requires three measured local scroll runs, physical GPU frame-time profiling, memory/VRAM/temperature/power and mobile acceptance, then independent human review. Evidence collected from a machine must be verified separately. **E6 Task 7 incomplete, E6 Task 8 incomplete, Stage45 NO_GO.**
