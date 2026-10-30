#!/usr/bin/env python3
"""E5 bounded model-DIRECTED layout decisions, never model-authored executable code.

A candidate chooses from pre-reviewed layout/type/palette tokens only. The
compiler generates a visible alternative using trusted, static CSS rules.
No arbitrary CSS/JS/HTML, code generation, network, model execution or
deployment. Approval, design quality and source provenance remain unverified.
"""
from __future__ import annotations

import hashlib
import html
import io
import json
import sys
import zipfile
from pathlib import Path

import e5_creative_brief_gate as gate
import e5_reference_preview as reference
import e5_untrusted_copy_preview as copy

ROOT = reference.PROJECT
FOLDER = ROOT / "target" / "e5"
BRIEF = gate.BRIEF
COPY = gate.PROPOSAL
LAYOUT = FOLDER / "layout-proposal.json"
OUTPUT = FOLDER / "structured-layout-review.zip"
MAX_LAYOUT_BYTES = 500
# Reviewed E3.9H reference Git-blob fingerprints, not new model code.
PINNED_SCENE_BLOB = "f8ae004f8678ebe312ea5179e01dc85147319343"
PINNED_STYLES_BLOB = "7d06ac0a58fb97d94414771290deea055b64b67c"


def _git_blob_sha(data: bytes) -> str:
    return hashlib.sha1(b"blob " + str(len(data)).encode("ascii") + b"\x00" + data).hexdigest()
KEYS = {"version", "composition", "palette", "typography"}
OPTIONS = {
    "composition": frozenset(("split", "centered", "gallery")),
    "palette": frozenset(("warm-stone", "charcoal", "paper")),
    "typography": frozenset(("editorial-serif", "modern-sans")),
}

# This CSS is all human-authored, static, finite and visually differentiated.
# The only model/operator influence is selecting from strict enumerable tokens.
# All variants keep keyboard focus, text contrast and reduced-motion fallback.
TRUSTED_CSS = """/* E5 REVIEW-ONLY controlled composition CSS, not model-authored source. */
body[data-e5-palette="warm-stone"]{background:#151310;color:#efe8dc}
body[data-e5-palette="charcoal"]{background:#111821;color:#f0f5f6}
body[data-e5-palette="paper"]{background:#f6f0e7;color:#20242a}
body[data-e5-palette="charcoal"] .masthead,
body[data-e5-palette="charcoal"] footer{border-color:#647889}
body[data-e5-palette="paper"] .masthead,
body[data-e5-palette="paper"] footer{border-color:#9f8b75}
body[data-e5-palette="paper"] footer{color:#41494e}
body[data-e5-palette="charcoal"] .chapter{background:#172433}
body[data-e5-palette="paper"] .chapter{background:#e8ded0}
body[data-e5-palette="charcoal"] .intro p,
body[data-e5-palette="charcoal"] .outro p,
body[data-e5-palette="charcoal"] .chapter-copy>p:not(.eyebrow):not(.status){color:#d1e2e9}
body[data-e5-palette="paper"] .intro p,
body[data-e5-palette="paper"] .outro p,
body[data-e5-palette="paper"] .chapter-copy>p:not(.eyebrow):not(.status){color:#41494e}
body[data-e5-palette="paper"] .eyebrow,
body[data-e5-palette="paper"] .status{color:#65462d}
body[data-e5-palette="charcoal"] .eyebrow,
body[data-e5-palette="charcoal"] .status{color:#f0c798}
body[data-e5-palette="paper"] .viewport-caption{color:#ffffff}
body[data-e5-palette="paper"] .masthead a:focus-visible,
body[data-e5-palette="paper"] a:focus-visible{outline-color:#684726}
body[data-e5-typography="editorial-serif"] h1,
body[data-e5-typography="editorial-serif"] h2{font-family:Georgia,"Times New Roman",serif;letter-spacing:-.047em}
body[data-e5-typography="modern-sans"] h1,
body[data-e5-typography="modern-sans"] h2{font-family:Inter,system-ui,sans-serif;letter-spacing:-.07em}
body[data-e5-composition="split"] .intro{margin-left:0;margin-right:auto;max-width:1110px}
body[data-e5-composition="split"] .intro h1{max-width:12ch}
body[data-e5-composition="centered"] .intro{text-align:center;max-width:1000px}
body[data-e5-composition="centered"] .intro p{margin-left:auto;margin-right:auto}
body[data-e5-composition="centered"] .chapter-inner{grid-template-columns:1fr 1fr}
body[data-e5-composition="gallery"] .intro{max-width:1180px;margin-left:auto;margin-right:auto}
body[data-e5-composition="gallery"] .intro h1{max-width:15ch}
body[data-e5-composition="gallery"] .chapter-inner{grid-template-columns:minmax(0,1.35fr) minmax(0,.65fr)}
body[data-e5-composition="gallery"] .chapter-copy{order:2}
body[data-e5-composition="gallery"] .viewport{order:1}
body[data-e5-composition="gallery"] .viewport-caption{font-size:.68rem}
@media(max-width:800px){
 body[data-e5-composition] .chapter-inner{grid-template-columns:1fr}
 body[data-e5-composition="gallery"] .chapter-copy,
 body[data-e5-composition="gallery"] .viewport{order:initial}
 body[data-e5-composition] .intro h1{max-width:100%;overflow-wrap:anywhere}
}
@media(prefers-reduced-motion:reduce){
 body[data-e5-composition] *{scroll-behavior:auto!important}
}
"""


def read_layout(raw: bytes) -> dict:
    if not raw or len(raw) > MAX_LAYOUT_BYTES:
        raise ValueError("E5_LAYOUT_BUDGET")
    try:
        candidate = json.loads(raw.decode("utf-8", errors="strict"),
                               object_pairs_hook=gate._unique)
    except (UnicodeError, ValueError) as error:
        raise ValueError("E5_LAYOUT_JSON_INVALID") from error
    if type(candidate) is not dict or set(candidate) != KEYS:
        raise ValueError("E5_LAYOUT_SCHEMA")
    if type(candidate["version"]) is not int or candidate["version"] != 1:
        raise ValueError("E5_LAYOUT_VERSION")
    for token, acceptable in OPTIONS.items():
        if type(candidate[token]) is not str or candidate[token] not in acceptable:
            raise ValueError("E5_LAYOUT_TOKEN_REJECTED")
    return {key: candidate[key] for key in
            ("version", "composition", "palette", "typography")}


def canonical(data: object) -> bytes:
    return (json.dumps(data, sort_keys=True, ensure_ascii=False,
                       separators=(",", ":")) + "\n").encode("utf-8")


def build(brief: dict, proposal: dict, layout: dict) -> tuple[bytes, dict]:
    decision = read_layout(canonical(layout))
    # Rebuild, not just trust, E5's independent brief/copy acceptance.
    parent_bytes, parent_evidence = gate.build(brief, proposal)
    with zipfile.ZipFile(io.BytesIO(parent_bytes)) as parent:
        names = set(parent.namelist())
        if names != set(reference.FILES) | {"copy-proposal.json", "preview-manifest.json"}:
            raise ValueError("E5_LAYOUT_UNEXPECTED_PARENT_ASSETS")
        source = {name: parent.read(name) for name in names}
    page = source["index.html"].decode("utf-8")
    if page.count("<body>") != 1 or page.count(
            '<link rel="stylesheet" href="./styles.css">') != 1:
        raise ValueError("E5_LAYOUT_REFERENCE_ANCHOR_CHANGED")
    attributes = " ".join(
        'data-e5-' + token + '="' + html.escape(decision[token], quote=True) + '"'
        for token in ("composition", "palette", "typography"))
    page = page.replace("<body>", "<body " + attributes + ">")
    page = page.replace(
        '<link rel="stylesheet" href="./styles.css">',
        '<link rel="stylesheet" href="./styles.css">\n'
        '  <link rel="stylesheet" href="./e5-layout.css">')
    parser = reference.AssetParser()
    parser.feed(page)
    if parser.errors or set(parser.assets) != {
            "./styles.css", "./e5-layout.css", "./scene.js"
        } or len(parser.assets) != 3:
        raise ValueError("E5_LAYOUT_UNEXPECTED_ACTIVE_ASSET")
    if _git_blob_sha(source["scene.js"]) != PINNED_SCENE_BLOB:
        raise ValueError("E5_LAYOUT_SCRIPT_CHANGED")
    if _git_blob_sha(source["styles.css"]) != PINNED_STYLES_BLOB:
        raise ValueError("E5_LAYOUT_BASE_STYLE_CHANGED")

    assets = {
        **source,
        "index.html": page.encode("utf-8"),
        "e5-layout.css": TRUSTED_CSS.encode("utf-8"),
        "layout-proposal.json": canonical(decision),
    }
    evidence = {
        "formatVersion": 1,
        "artifactClass": "E5_UNTRUSTED_STRUCTURED_LAYOUT_REVIEW_ONLY",
        "layoutDecision": decision,
        "parentBriefEvidenceSha256": hashlib.sha256(parent_evidence).hexdigest(),
        "parentPreviewSha256": hashlib.sha256(parent_bytes).hexdigest(),
        "sourceCodeProvenance": "TRUSTED_HUMAN_CODED_TEMPLATE_PLUS_HUMAN_AUTHORED_FINITE_CSS",
        "layoutDecisionProvenance": "UNVERIFIED_OPERATOR_OR_MODEL_PROPOSAL",
        "siteCodeModelAuthored": False,
        "independentModelRunVerified": False,
        "independentAestheticReviewPassed": False,
        "ownerApproved": False,
        "deploymentAuthorized": False,
        "productionReady": False,
        "runtimeReleaseStatus": "NO_GO",
        "files": [
            {"path": name, "sha256": hashlib.sha256(raw).hexdigest(),
             "bytes": len(raw)}
            for name, raw in sorted(assets.items()) if name != "preview-manifest.json"
        ],
    }
    assets["preview-manifest.json"] = canonical(evidence)
    output = io.BytesIO()
    with zipfile.ZipFile(output, "w") as archive:
        for name, raw in sorted(assets.items()):
            info = zipfile.ZipInfo(name, reference.PINNED_TIME)
            info.external_attr = 0o100644 << 16
            info.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(info, raw, compress_type=zipfile.ZIP_DEFLATED,
                             compresslevel=9)
    return output.getvalue(), evidence


def main() -> int:
    if len(sys.argv) != 1:
        print("E5_LAYOUT_NO_CUSTOM_PATHS_OR_DEPLOY_ARGUMENTS", file=sys.stderr)
        return 2
    try:
        if not FOLDER.is_dir() or FOLDER.is_symlink():
            raise ValueError("E5_LAYOUT_DIRECTORY_UNSAFE")
        brief = gate.read_brief(gate._read_regular(BRIEF, gate.MAX_BRIEF_BYTES))
        proposal = copy.read_proposal(gate._read_regular(COPY, copy.MAX_INPUT))
        layout = read_layout(gate._read_regular(LAYOUT, MAX_LAYOUT_BYTES))
        if OUTPUT.exists() or OUTPUT.is_symlink():
            raise ValueError("E5_LAYOUT_EVIDENCE_ALREADY_EXISTS")
        archive, evidence = build(brief, proposal, layout)
        import os
        flags = os.O_WRONLY | os.O_CREAT | os.O_EXCL | getattr(os, "O_NOFOLLOW", 0)
        fd = os.open(OUTPUT, flags, 0o600)
        with os.fdopen(fd, "wb") as handle:
            handle.write(archive)
        print(json.dumps({
            "status": "INERT_STRUCTURED_LAYOUT_REVIEW_ONLY",
            "sha256": hashlib.sha256(archive).hexdigest(),
            "siteCodeModelAuthored": False,
            "modelRunVerified": False,
            "ownerApproved": False,
            "release": "NO_GO",
        }, sort_keys=True))
        return 0
    except (OSError, ValueError):
        print("E5_STRUCTURED_LAYOUT_REFUSED", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
