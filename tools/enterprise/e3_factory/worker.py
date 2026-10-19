#!/usr/bin/env python3
"""E3.0: deterministic CI-only website builder. No model, shell, network or host actions."""
from __future__ import annotations

import hashlib
import html
import json
from pathlib import Path
import re

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[2]
TARGET = REPO / "target" / "e3-factory" / "site"
REQUIRED = {"schema_version", "project_id", "title", "eyebrow", "description", "features"}
OUTPUTS = {"index.html", "styles.css", "main.js", "manifest.json", "SHA256SUMS"}


def validate_brief(value: object) -> dict:
    if not isinstance(value, dict) or set(value) != REQUIRED:
        raise ValueError("E3_BRIEF_SCHEMA_INVALID")
    if type(value["schema_version"]) is not int or value["schema_version"] != 1:
        raise ValueError("E3_SCHEMA_VERSION_INVALID")
    if not isinstance(value["project_id"], str) or not re.fullmatch(
        r"[a-z0-9]+(?:-[a-z0-9]+)*", value["project_id"]
    ) or len(value["project_id"]) > 48:
        raise ValueError("E3_PROJECT_ID_INVALID")
    for key, limit in (("title", 70), ("eyebrow", 50), ("description", 240)):
        text = value[key]
        if not isinstance(text, str) or not 2 <= len(text.strip()) <= limit:
            raise ValueError("E3_BRIEF_FIELD_INVALID:" + key)
        if any(ord(ch) < 32 and ch not in "\t\n" for ch in text):
            raise ValueError("E3_BRIEF_CONTROL_CHARACTER")
    features = value["features"]
    if (not isinstance(features, list) or len(features) != 3
            or any(not isinstance(x, str) or not 4 <= len(x) <= 90 for x in features)):
        raise ValueError("E3_BRIEF_FEATURES_INVALID")
    return value


def render_html(spec: dict, template: str) -> str:
    values = {
        "TITLE": html.escape(spec["title"], quote=True),
        "EYEBROW": html.escape(spec["eyebrow"], quote=True),
        "DESCRIPTION": html.escape(spec["description"], quote=True),
        "PROJECT_ID": html.escape(spec["project_id"], quote=True),
        "FEATURE_1": html.escape(spec["features"][0], quote=True),
        "FEATURE_2": html.escape(spec["features"][1], quote=True),
        "FEATURE_3": html.escape(spec["features"][2], quote=True),
    }
    for key, escaped in values.items():
        template = template.replace("[[" + key + "]]", escaped)
    if re.search(r"\[\[[A-Z_0-9]+\]\]", template):
        raise ValueError("E3_UNRESOLVED_TEMPLATE_TOKEN")
    return template


def _write_bytes(path: Path, data: bytes) -> None:
    with path.open("xb") as stream:
        stream.write(data)


def build() -> Path:
    """Only the fixed repo-local CI target can be written; symlinks are refused."""
    spec = validate_brief(json.loads((HERE / "frozen-brief.json").read_text("utf-8")))
    resolved_repo = REPO.resolve(strict=True)
    target_parent = REPO / "target"
    if target_parent.is_symlink():
        raise ValueError("E3_OUTPUT_SYMLINK_DISALLOWED")
    if target_parent.exists() and target_parent.resolve() != resolved_repo / "target":
        raise ValueError("E3_OUTPUT_ESCAPE")
    subdir = target_parent / "e3-factory"
    if subdir.is_symlink() or TARGET.is_symlink() or TARGET.exists():
        raise ValueError("E3_OUTPUT_ALREADY_EXISTS_OR_LINK")
    subdir.mkdir(parents=True, exist_ok=True)
    if subdir.resolve() != resolved_repo / "target" / "e3-factory":
        raise ValueError("E3_OUTPUT_ESCAPE")
    TARGET.mkdir(mode=0o700)
    assets = {
        "index.html": render_html(spec, (HERE / "template.html").read_text("utf-8")),
        "styles.css": (HERE / "styles.css").read_text("utf-8"),
        "main.js": (HERE / "main.js").read_text("utf-8"),
    }
    sources = {}
    for name, value in assets.items():
        encoded = value.encode("utf-8")
        _write_bytes(TARGET / name, encoded)
        sources[name] = hashlib.sha256(encoded).hexdigest()
    manifest = {
        "schemaVersion": 1,
        "projectId": spec["project_id"],
        "evidenceState": "CI_ONLY_WEBSITE_SOURCE_GENERATED_NOT_DEPLOYED",
        "workerExecuted": True,
        "webBrowserQAVerifiedByWorker": False,
        "blenderRenderedFramesPresent": False,
        "threeJsOrWebglScenePresent": False,
        "realOwnerApprovalGranted": False,
        "productionPublished": False,
        "generatedFilesSha256": sources,
    }
    manifest_bytes = (json.dumps(manifest, indent=2, sort_keys=True) + "\n").encode("utf-8")
    _write_bytes(TARGET / "manifest.json", manifest_bytes)
    sources["manifest.json"] = hashlib.sha256(manifest_bytes).hexdigest()
    checksum_bytes = "".join(
        f"{digest}  {name}\n" for name, digest in sorted(sources.items())
    ).encode("ascii")
    _write_bytes(TARGET / "SHA256SUMS", checksum_bytes)
    if set(path.name for path in TARGET.iterdir()) != OUTPUTS:
        raise ValueError("E3_UNEXPECTED_OUTPUT")
    return TARGET


if __name__ == "__main__":
    print("E3_CI_ONLY_SITE_GENERATED:", build().relative_to(REPO))
