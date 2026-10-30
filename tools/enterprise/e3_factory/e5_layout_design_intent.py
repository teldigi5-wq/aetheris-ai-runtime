#!/usr/bin/env python3
"""E5 abstract, non-sensitive layout preferences only; not a creative quality judge.

Three exact, versioned categorical preferences are safe to send to a local
model only after explicit opt-in. Their rubric is a transparent, human-made
baseline for testing alignment, NOT proof of original design or artistic merit.
"""
from __future__ import annotations

import hashlib
import json

import e5_creative_brief_gate as gate
import e5_structured_layout_preview as layout

MAX_INTENT_BYTES = 350
KEYS = {"version", "layoutGoal", "contrastPreference", "typographicVoice"}
CHOICES = {
    "layoutGoal": frozenset(("immersive", "informational", "balanced")),
    "contrastPreference": frozenset(("light", "dark")),
    "typographicVoice": frozenset(("editorial", "contemporary")),
}

# Explainable authored heuristics, not an AI aesthetics assessment.
PREFERENCES = {
    "layoutGoal": {
        "immersive": "gallery", "informational": "centered",
        "balanced": "split",
    },
    "contrastPreference": {"light": "paper", "dark": "charcoal"},
    "typographicVoice": {"editorial": "editorial-serif",
                          "contemporary": "modern-sans"},
}
FIELDS = {"layoutGoal": "composition", "contrastPreference": "palette",
          "typographicVoice": "typography"}


def canonical(value: object) -> bytes:
    return (json.dumps(value, sort_keys=True, ensure_ascii=False,
                       separators=(",", ":")) + "\n").encode("utf-8")


def read_intent(raw: bytes) -> dict:
    if not raw or len(raw) > MAX_INTENT_BYTES:
        raise ValueError("E5_INTENT_SIZE")
    try:
        value = json.loads(raw.decode("utf-8", errors="strict"),
                           object_pairs_hook=gate._unique)
    except (UnicodeError, ValueError) as exc:
        raise ValueError("E5_INTENT_JSON_REJECTED") from exc
    if type(value) is not dict or set(value) != KEYS:
        raise ValueError("E5_INTENT_SCHEMA")
    if type(value["version"]) is not int or value["version"] != 1:
        raise ValueError("E5_INTENT_VERSION")
    for key, possible in CHOICES.items():
        if type(value[key]) is not str or value[key] not in possible:
            raise ValueError("E5_INTENT_ENUM_REJECTED")
    return {"version": 1, "layoutGoal": value["layoutGoal"],
            "contrastPreference": value["contrastPreference"],
            "typographicVoice": value["typographicVoice"]}


def score(intent: dict, decision: dict) -> dict:
    requested = read_intent(canonical(intent))
    chosen = layout.read_layout(canonical(decision))
    matches = {key: chosen[FIELDS[key]] == PREFERENCES[key][requested[key]]
               for key in FIELDS}
    return {
        "rubric": "STATIC_DESIGN_PREFERENCE_ALIGNMENT_NOT_CREATIVE_QUALITY",
        "matched": sum(matches.values()),
        "total": 3,
        "matches": matches,
        "humanAuthoredRubric": True,
        "aestheticQualityCertified": False,
        "accessibilityCertified": False,
    }


def sha256(intent: dict) -> str:
    return hashlib.sha256(canonical(read_intent(canonical(intent)))).hexdigest()
