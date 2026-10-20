#!/usr/bin/env python3
"""E3.9F read-only, typed, non-oracle CSS/Chromium context for the proposer.

Never determines the required patch, imports the scorer, or grants actions.
Rejects browser/CSS strings that could carry instructions. The independent
E39E source hash and network-none policy still decide all patch acceptance.
"""
from __future__ import annotations
import math
import re

RULE = re.compile(r"\.skip-link:focus\{([^{}]{1,160})\}\Z", re.ASCII)
PROPERTY = frozenset(("top", "left", "opacity", "visibility", "transform"))
SCALAR = re.compile(r"-?[0-9]{1,4}px|(?:0|1)(?:\.0{1,2})?|0\.[0-9]{1,2}", re.ASCII)
TRANSFORM = re.compile(r"translate[XY]\(-?[0-9]{1,4}px\)|none", re.ASCII)
COMPUTED = {
    "visibility": frozenset(("visible", "hidden", "collapse")),
    "display": frozenset(("block", "inline", "none", "inline-block", "flex")),
    "opacity": frozenset(("0", "0.0", "1", "1.0", "0.95")),
}
BOUNDS = ("top", "bottom", "left", "right", "width", "height",
          "viewportW", "viewportH")


def focused_declarations(css_rule: str) -> dict[str, str]:
    if not isinstance(css_rule, str):
        raise ValueError("E39F_UNTRUSTED_CSS_TYPE")
    match = RULE.fullmatch(css_rule)
    if match is None:
        raise ValueError("E39F_FOCUS_RULE_ONLY")
    declarations = {}
    for token in match.group(1).split(";"):
        parts = token.split(":")
        if len(parts) != 2:
            raise ValueError("E39F_CSS_DECLARATION_INVALID")
        key, value = parts
        if key not in PROPERTY or key in declarations or not value:
            raise ValueError("E39F_CSS_PROPERTY_INVALID_OR_DUPLICATE")
        if key == "transform":
            valid = TRANSFORM.fullmatch(value) is not None
        elif key == "visibility":
            valid = value in ("visible", "hidden")
        else:
            valid = SCALAR.fullmatch(value) is not None
        if not valid:
            raise ValueError("E39F_CSS_UNTRUSTED_VALUE")
        declarations[key] = value
    return declarations


def number(value):
    if type(value) not in (float, int) or not math.isfinite(value) or abs(value) > 10000:
        raise ValueError("E39F_UNTRUSTED_BROWSER_GEOMETRY")
    return value


def typed_context(css_rule: str, observations: dict) -> dict:
    if not isinstance(observations, dict) or set(observations) != {"mobile", "desktop"}:
        raise ValueError("E39F_EXACT_TWO_VIEWPORTS_REQUIRED")
    viewports = {}
    for viewport in ("mobile", "desktop"):
        obs = observations[viewport]
        if not isinstance(obs, dict) or type(obs.get("focusReached")) is not bool:
            raise ValueError("E39F_UNTRUSTED_BROWSER_OBSERVATION")
        bounds = obs.get("geometry")
        computed = obs.get("computed")
        if not isinstance(bounds, dict) or not isinstance(computed, dict):
            raise ValueError("E39F_MISSING_TYPED_BROWSER_DATA")
        safe_bounds = {key: number(bounds.get(key)) for key in BOUNDS}
        if safe_bounds["viewportW"] != (390 if viewport == "mobile" else 1280):
            raise ValueError("E39F_BROWSER_VIEWPORT_UNBOUND")
        if safe_bounds["viewportH"] < 300 or safe_bounds["viewportH"] > 2000:
            raise ValueError("E39F_BROWSER_VIEWPORT_HEIGHT_INVALID")
        safe_computed = {}
        for key, allowed in COMPUTED.items():
            value = computed.get(key)
            if type(value) is not str or value not in allowed:
                raise ValueError("E39F_UNTRUSTED_COMPUTED_STYLE")
            safe_computed[key] = value
        viewports[viewport] = {
            "focusReached": obs["focusReached"],
            "boundsPx": safe_bounds,
            "computed": safe_computed,
        }
    return {
        "focusSelector": ".skip-link:focus",
        "declarations": focused_declarations(css_rule),
        "observed": viewports,
    }
