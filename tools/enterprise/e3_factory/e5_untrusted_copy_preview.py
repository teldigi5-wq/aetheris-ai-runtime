#!/usr/bin/env python3
"""E5 bounded, untrusted copy-proposal review artifact (never deployment).

Accept exactly a local inert JSON proposal under target/e5. The ONLY
changes to the trusted E3.9H visual reference are three escaped text nodes.
No model is invoked, no external resources are loaded and no copy is approved.
"""
from __future__ import annotations

import hashlib
import html
import io
import json
import sys
import unicodedata
import zipfile
from pathlib import Path

import e5_reference_preview as reference

ROOT = reference.PROJECT
INPUT = ROOT / "target" / "e5" / "copy-proposal.json"
OUTPUT = ROOT / "target" / "e5" / "untrusted-copy-preview.zip"
MAX_INPUT = 1400
KEYS = {"version", "brandLabel", "headlineLines"}
ANCHORS = (
    '<span class="brand">AETHERIS / RESEARCH</span>',
    '<h1 id="intro-title">Form, light<br>and movement.</h1>',
)


def _pairs_unique(pairs: list[tuple[str, object]]) -> dict:
    data: dict = {}
    for key, value in pairs:
        if key in data:
            raise ValueError("E5_DUPLICATE_PROPOSAL_FIELD")
        data[key] = value
    return data


def _safe_text(value: object, lower: int, upper: int) -> str:
    if not isinstance(value, str) or not lower <= len(value) <= upper:
        raise ValueError("E5_UNBOUNDED_TEXT")
    if value != value.strip() or "  " in value or not value.isprintable():
        raise ValueError("E5_NONCANONICAL_TEXT")
    if value != unicodedata.normalize("NFC", value):
        raise ValueError("E5_UNNORMALIZED_UNICODE")
    if not any(ch.isalpha() for ch in value):
        raise ValueError("E5_TEXT_NEEDS_LETTERS")
    if any(unicodedata.category(ch) in ("Cf", "Cs", "Co", "Cn") for ch in value):
        raise ValueError("E5_UNTRUSTED_UNICODE_CONTROL")
    if any(ch in '<>&\\/' for ch in value):
        raise ValueError("E5_DISALLOWED_MARKUP_OR_URL")
    return value


def read_proposal(raw: bytes) -> dict:
    if not raw or len(raw) > MAX_INPUT:
        raise ValueError("E5_PROPOSAL_SIZE_INVALID")
    try:
        parsed = json.loads(raw.decode("utf-8", errors="strict"),
                            object_pairs_hook=_pairs_unique)
    except (UnicodeError, json.JSONDecodeError) as error:
        raise ValueError("E5_PROPOSAL_JSON_INVALID") from error
    if not isinstance(parsed, dict) or set(parsed) != KEYS or type(parsed["version"]) is not int or parsed["version"] != 1:
        raise ValueError("E5_PROPOSAL_SCHEMA_MISMATCH")
    brand = _safe_text(parsed["brandLabel"], 3, 38)
    lines = parsed["headlineLines"]
    if not isinstance(lines, list) or len(lines) != 2:
        raise ValueError("E5_PROPOSAL_HEADLINE_FORMAT")
    one, two = (_safe_text(line, 3, 26) for line in lines)
    return {"version": 1, "brandLabel": brand, "headlineLines": [one, two]}


def build(proposal: dict) -> tuple[bytes, dict]:
    # Do not take arbitrary source paths or model-authored code. Re-run the
    # reviewed reference validator before constructing any output.
    vetted = read_proposal(json.dumps(proposal, ensure_ascii=False).encode("utf-8"))
    source = reference.validate_source(reference.REFERENCE)
    page = source["index.html"].decode("utf-8")
    for anchor in ANCHORS:
        if page.count(anchor) != 1:
            raise ValueError("E5_REFERENCE_ANCHOR_DRIFT")
    brand = html.escape(vetted["brandLabel"], quote=True)
    lines = [html.escape(line, quote=True) for line in vetted["headlineLines"]]
    page = page.replace(ANCHORS[0],
        '<span class="brand">' + brand + ' / UNAPPROVED DRAFT</span>')
    page = page.replace(ANCHORS[1],
        '<h1 id="intro-title">' + lines[0] + '<br>' + lines[1] + '</h1>')
    revised = {**source, "index.html": page.encode("utf-8")}
    if revised["scene.js"] != source["scene.js"] or revised["styles.css"] != source["styles.css"]:
        raise ValueError("E5_ACTIVE_CODE_CHANGED")
    parser = reference.AssetParser()
    parser.feed(page)
    if parser.errors or set(parser.assets) != {"./scene.js", "./styles.css"}:
        raise ValueError("E5_UNSAFE_REVISED_HTML")
    canonical = (json.dumps(vetted, sort_keys=True, ensure_ascii=False,
                            separators=(",", ":")) + "\n").encode("utf-8")
    assets = {**revised, "copy-proposal.json": canonical}
    manifest = {
        "formatVersion": 1,
        "artifactClass": "E5_UNTRUSTED_COPY_REVIEW_ONLY",
        "siteCodeProvenance": "E3.9H_HUMAN_CODED_BROWSER_REFERENCE",
        "copyProvenance": "UNVERIFIED_OPERATOR_SUPPLIED_OR_MODEL_PROPOSED",
        "modelAuthored": None,
        "ownerApproved": False,
        "deploymentAuthorized": False,
        "productionReady": False,
        "externalNetworkRequestsRequired": False,
        "runtimeReleaseStatus": "NO_GO",
        "files": [
            {"path": name, "bytes": len(data),
             "sha256": hashlib.sha256(data).hexdigest()}
            for name, data in sorted(assets.items())
        ],
    }
    assets["preview-manifest.json"] = (
        json.dumps(manifest, sort_keys=True, separators=(",", ":")) + "\n"
    ).encode("utf-8")
    output = io.BytesIO()
    with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED,
                         compresslevel=9) as archive:
        for name, data in sorted(assets.items()):
            item = zipfile.ZipInfo(name, reference.PINNED_TIME)
            item.compress_type = zipfile.ZIP_DEFLATED
            item.external_attr = 0o100644 << 16
            archive.writestr(item, data, compress_type=zipfile.ZIP_DEFLATED,
                             compresslevel=9)
    return output.getvalue(), manifest


def main() -> int:
    if len(sys.argv) != 1:
        print("E5_NO_ARBITRARY_SOURCE_OR_DEPLOYMENT_ARGUMENTS", file=sys.stderr)
        return 2
    try:
        if not INPUT.is_file() or INPUT.is_symlink() or INPUT.stat().st_size > MAX_INPUT:
            raise ValueError("E5_LOCAL_PROPOSAL_MISSING_OR_UNSAFE")
        proposal = read_proposal(INPUT.read_bytes())
        archive, manifest = build(proposal)
        if OUTPUT.parent.is_symlink() or OUTPUT.is_symlink():
            raise ValueError("E5_OUTPUT_SYMLINK")
        OUTPUT.parent.mkdir(parents=True, exist_ok=True)
        if OUTPUT.exists() and not OUTPUT.is_file():
            raise ValueError("E5_OUTPUT_NOT_REGULAR_FILE")
        OUTPUT.write_bytes(archive)
        print(json.dumps({
            "status": "LOCAL_UNTRUSTED_COPY_REVIEW_NOT_DEPLOYED",
            "artifact": str(OUTPUT.relative_to(ROOT)),
            "sha256": hashlib.sha256(archive).hexdigest(),
            "artifactClass": manifest["artifactClass"],
            "copyProvenance": manifest["copyProvenance"],
            "productionReady": False,
            "release": "NO_GO",
        }, sort_keys=True))
        return 0
    except (OSError, ValueError) as error:
        print("E5_CANDIDATE_REFUSED_" + str(error), file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
