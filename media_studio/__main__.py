"""Use: python -m media_studio {check,submit-image,outputs,save-image,video-plan}."""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

from .comfy_client import ComfyMediaClient, ImageJob, MediaSettings, MediaDisabled, MediaTransportError, PROMPT_ID


def parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(description="Local ComfyUI image client; cloud and paid APIs are forbidden")
    subs = p.add_subparsers(dest="command", required=True)
    subs.add_parser("check", help="Inspect configuration without performing network requests")
    img = subs.add_parser("submit-image", help="Submit one allowlisted local checkpoint graph")
    img.add_argument("--checkpoint", required=True, help="Exact installed checkpoint basename")
    img.add_argument("--prompt", required=True)
    img.add_argument("--negative", default="")
    img.add_argument("--width", type=int, default=512)
    img.add_argument("--height", type=int, default=512)
    img.add_argument("--steps", type=int, default=20)
    img.add_argument("--seed", type=int, default=0)
    img.add_argument("--cfg", type=float, default=7.0)
    output = subs.add_parser("outputs", help="Read ComfyUI's output history, not a success claim while queued")
    output.add_argument("--id", required=True)
    download = subs.add_parser("save-image", help="Save the first image from verified ComfyUI job history")
    download.add_argument("--id", required=True)
    download.add_argument("--to", type=Path, required=True)
    subs.add_parser("video-plan", help="Explain gated future video engine readiness")
    return p


def main(argv: list[str] | None = None) -> int:
    options = parser().parse_args(argv)
    cfg = MediaSettings.from_environment()
    try:
        cfg.validate()
        if options.command == "check":
            print(json.dumps({
                "backend": "ComfyUI local loopback only",
                "enabled": cfg.enabled,
                "endpoint": cfg.endpoint,
                "allowlistedCheckpointCount": len(cfg.allowed_checkpoints),
                "imageEngine": "IMPLEMENTED_NOT_HARDWARE_VALIDATED",
                "videoEngine": "PLANNED_NOT_WIRED",
                "desktopIntegration": "NOT_WIRED",
                "physicalPcStatus": "BLOCKED_PENDING_HARDWARE",
                "cloudFallback": False,
                "paidProviderCalls": False,
            }, indent=2))
            return 0
        if options.command == "video-plan":
            print("Video backend NOT WIRED. Requires a reviewed, model-specific API workflow, VRAM validation, bounded artifact handling and owner policy.")
            return 0
        # Never call ComfyUI when the local-generation feature flag is not true.
        if not cfg.enabled:
            raise MediaDisabled("Media execution is disabled by default")
        client = ComfyMediaClient(cfg)
        if options.command == "submit-image":
            job = ImageJob(
                options.prompt, options.checkpoint, options.negative, options.width,
                options.height, options.steps, options.seed, options.cfg)
            # No prompt/model identifier is echoed to stdout beyond the opaque job id.
            print(json.dumps({"state": "SUBMITTED_NOT_COMPLETED", "prompt_id": client.queue_image(job)}))
        elif options.command == "outputs":
            if not PROMPT_ID.fullmatch(options.id):
                raise ValueError("Invalid prompt id")
            images = client.image_outputs(options.id)
            print(json.dumps({"state": "OUTPUT_OBSERVED" if images else "NOT_YET_OBSERVED", "images": images}))
        elif options.command == "save-image":
            if not PROMPT_ID.fullmatch(options.id):
                raise ValueError("Invalid prompt id")
            images = client.image_outputs(options.id)
            if not images:
                raise MediaDisabled("No verified generated image exists for that job")
            result = client.download_image(images[0]["filename"], options.to)
            print(json.dumps({"state": "IMAGE_WRITTEN", "path": str(result)}))
        else:
            raise ValueError("Unsupported command")
        return 0
    except (MediaDisabled, MediaTransportError, ValueError, OSError) as exc:
        print("Media Studio refused operation: " + str(exc), file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
