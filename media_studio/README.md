# Aetheris Media Studio — local ComfyUI foundation

**State:** source implemented, CI/mocked tests only. No local ComfyUI installation, compatible checkpoint, physical GPU benchmark, working video model, authenticated desktop media endpoint, image editing, or upscaling has been verified. Physical target remains `BLOCKED_PENDING_HARDWARE`.

This is a zero-subscription, zero-paid-API **opt-in local CLI** with a fixed built-in text-to-image workflow derived from the [official ComfyUI API example](https://github.com/Comfy-Org/ComfyUI/blob/master/script_examples/basic_api_example.py). It does not serve its own HTTP port, call cloud models or download checkpoints.

## Safe offline-first use (only after actual owner-PC setup and validation)

1. Install ComfyUI from the official project and a compatible checkpoint you have independently reviewed and licensed.
2. Ensure ComfyUI listens **only on `127.0.0.1:8188`**; do not expose ComfyUI on your LAN/Internet.
3. Set an exact local checkpoint **basename** you installed; all checkpoints remain disallowed until allowlisted.
4. Enable the CLI using environment variables:

   ```powershell
   $env:AETHERIS_MEDIA_ENABLED = "true"
   $env:AETHERIS_COMFYUI_URL = "http://127.0.0.1:8188"
   $env:AETHERIS_MEDIA_ALLOWED_CHECKPOINTS = "YOUR_INSTALLED_SD15_CHECKPOINT.safetensors"
   python -m media_studio check
   python -m media_studio probe-local-gpu
   python -m media_studio submit-image --checkpoint YOUR_INSTALLED_SD15_CHECKPOINT.safetensors --prompt "a mountain at sunrise" --width 512 --height 512 --steps 20
   python -m media_studio outputs --id YOUR_RETURNED_PROMPT_ID
   python -m media_studio save-image --id YOUR_RETURNED_PROMPT_ID --to ./generated-image.png
   ```

The read-only GPU probe reports ComfyUI-observed memory in MiB, not independent GPU or owner-PC validation. The submit call reports `SUBMITTED_NOT_COMPLETED`. The history check is required; queued jobs do not count as generated images. `save-image` validates the returned filename, disallows subfolders, checks a bounded image signature/size, and never overwrites an existing file.

The baseline API workflow supports SD1.5-style standard checkpoints. Other model architectures (FLUX, SDXL refiners, high-resolution upscalers, Wan, LTX Video) need separately reviewed templates; no unsupported checkpoint is promised to work just because it is allowlisted.

## Security boundaries

- Fail-closed unless `AETHERIS_MEDIA_ENABLED=true` **and** exact checkpoint allowlist is set.
- Accepts only `http://127.0.0.1:<port>`; does not follow HTTP redirects or honor proxy variables.
- Client cannot supply arbitrary ComfyUI graph JSON, custom nodes, scripts, paths, or URLs.
- Fixed sampler `euler`, single image batch, max resolution 1024×1024, 35 steps and one attempted job when ComfyUI reports an idle queue. This is **best-effort** scheduling, not atomic interprocess GPU reservation.
- User prompt text stays within the local ComfyUI API call; output console only shows job IDs and result filenames. ComfyUI itself may persist metadata/prompts in its own history.
- No paid/cloud API fallback; no file or host execution from remote requests; no new unauthenticated controller or public port.
- Model files and third-party custom nodes remain an owner-managed supply-chain risk. Avoid installing arbitrary workflow packs/scripts; respect each checkpoint's license and content policy.
- No guarantee of sufficient VRAM, available GPU, speed, quality, or zero electricity/storage cost.

## Offline GPU-upgrade planning (works without owning a laptop)

This planning command uses **only numbers you enter**. It does not detect the currently installed GPU, contact ComfyUI or activate generation:

```powershell
python -m media_studio plan --workflow image --hypothetical-vram-mib 6144
python -m media_studio plan --workflow image --hypothetical-vram-mib 16384
python -m media_studio plan --workflow video --hypothetical-vram-mib 24576
```

Results group the *hypothetical* VRAM into coarse capacity bands, suggest conservative **image experiment sizes only**, and return `BLOCKED_PENDING_HARDWARE` regardless of the GPU size entered. Video always returns `PLANNED_NOT_WIRED`. None of these bands asserts that an arbitrary model fits or achieves target performance. A model's *actual* VRAM requirements depend on the exact checkpoint, quantization, offload configuration, resolution, duration and drivers.

The command never installs checkpoints, fetches telemetry, runs a model, makes network requests, or grants execution authority. Optional free-VRAM input is likewise a hypothetical estimate and **not** automatic detection.

## Hardware-adaptive video strategy

`media_studio/capabilities.json` declares video `PLANNED_NOT_WIRED`. After GPU replacement, separately validate a model-specific, preapproved API workflow (Wan or LTX), licensing, VRAM usage, max duration/resolution, video outputs, storage quotas, cancellation and deterministic owner permissions. The CLI's `video-plan` does not generate video.

Syntra Desktop remains **locally staged UI only**, with authenticated generation operations not wired. Do not bypass the platform gateway, validated memory ownership or desktop token storage to connect this CLI to a web UI. A separate PR must implement the authenticated media job contract with safe artifact ownership.

Run local validation: `python -m unittest discover -s media_studio/tests -p 'test_*.py' -v`.

This project does not certify physical hardware, Windows installation, GPU inference or production operation.
