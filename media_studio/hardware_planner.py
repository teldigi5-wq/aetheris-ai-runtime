"""Deterministic, offline VRAM planning; never a hardware or model certification.

The planner accepts *hypothetical* GPU capacity only. It does not probe devices,
install models, send requests, or approve execution.
"""
from __future__ import annotations

from dataclasses import dataclass
from typing import Literal

Workflow = Literal["image", "video"]


@dataclass(frozen=True)
class PlanningScenario:
    workflow: Workflow = "image"
    hypothetical_total_vram_mib: int | None = None
    hypothetical_free_vram_mib: int | None = None

    def validate(self) -> None:
        if self.workflow not in ("image", "video"):
            raise ValueError("Workflow must be exactly 'image' or 'video'")
        total, free = self.hypothetical_total_vram_mib, self.hypothetical_free_vram_mib
        if total is None:
            if free is not None:
                raise ValueError("Total VRAM is required if free VRAM is supplied")
        elif type(total) is not int or not 512 <= total <= 1_048_576:
            raise ValueError("Hypothetical total VRAM must be 512–1048576 MiB")
        if free is not None and (type(free) is not int or not 0 <= free <= total):
            raise ValueError("Hypothetical free VRAM must be between 0 and total VRAM")


def plan_scenario(scenario: PlanningScenario) -> dict[str, object]:
    """Build a rough capacity *discussion*, never an executable capability."""
    scenario.validate()
    total = scenario.hypothetical_total_vram_mib
    free = scenario.hypothetical_free_vram_mib

    if total is None:
        band, image_preset = "UNKNOWN", "512x512_CANDIDATE_ONLY"
    elif total < 8192:
        band, image_preset = "LOW_VRAM", "512x512_CANDIDATE_ONLY"
    elif total < 16384:
        band, image_preset = "MID_VRAM", "512x512_OR_768x512_CANDIDATE_ONLY"
    elif total < 24576:
        band, image_preset = "HIGH_VRAM", "UP_TO_1024x1024_CANDIDATE_ONLY"
    else:
        band, image_preset = "EXTENDED_VRAM", "UP_TO_1024x1024_CANDIDATE_ONLY"

    warnings = [
        "HYPOTHETICAL_INPUT_NOT_HARDWARE_DETECTION",
        "MODEL_MEMORY_REQUIREMENTS_NOT_VERIFIED",
        "NO_INSTALL_OR_GENERATION_AUTHORIZED",
        "OWNER_PC_CERTIFICATION_REQUIRED",
    ]
    if total is None:
        warnings.append("UNKNOWN_GPU_CAPACITY")
    elif free is None:
        warnings.append("FREE_VRAM_NOT_MEASURED")
    elif free < total // 4:
        warnings.append("LOW_FREE_VRAM_IN_HYPOTHETICAL_SCENARIO")
    if scenario.workflow == "video":
        warnings.append("VIDEO_BACKEND_NOT_IMPLEMENTED")

    return {
        "schemaVersion": 1,
        "scenarioSource": "USER_SUPPLIED_HYPOTHETICAL",
        "requestedWorkflow": scenario.workflow,
        "hypotheticalTotalVramMiB": total,
        "hypotheticalFreeVramMiB": free,
        "capacityBandHeuristic": band,
        "imageResolutionPlanningSuggestion": image_preset,
        "videoWorkflowState": "PLANNED_NOT_WIRED",
        "imageWorkflowState": "SOURCE_IMPLEMENTED_NOT_HARDWARE_VALIDATED",
        "physicalPcStatus": "BLOCKED_PENDING_HARDWARE",
        "modelCompatibilityConfirmed": False,
        "modelLicenseApproved": False,
        "physicalHardwareValidated": False,
        "generationPermissionGranted": False,
        "cloudFallbackAvailable": False,
        "paidProviderEnabled": False,
        "warnings": warnings,
    }
