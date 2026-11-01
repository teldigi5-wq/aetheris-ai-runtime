#!/usr/bin/env python3
"""E6 W3-A: admit a PREEXISTING, locally verified Blender research GLB into a
separate W2-C preview. No user-selected paths, asset downloads, web/network,
production publication, arbitrary code, overwrite or model-generated claims.

W3-A establishes source/content integrity, not ownership by cryptographic proof:
the source script is reviewed in Git and Blender's emitted bytes are bound to it.
Human design approval, actual model origin, rights, GPU and release remain gated.
"""
from __future__ import annotations
import hashlib
import html
import json
import shutil
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
BLENDER = HERE / "blender"
sys.path.insert(0, str(BLENDER))
from validate_blender_glb import inspect_glb  # noqa: E402
from verify_detailed_module import inspect_design  # noqa: E402

ASSET_PROOF = ROOT / "target/e6-studio/blender-proof"
W2C_PREVIEW = HERE / "target/e6-w2c-arcloom"
W3_PREVIEW = HERE / "target/e6-w3a-arcloom"
MAX_SITE_BYTES = 2_000_000
CANDIDATE = "aetheris-command-module-no-uv.glb"
ORIGINAL = "aetheris-command-module.glb"
BLEND = "aetheris-command-module.blend"
SCRIPT = "author_command_module.py"
METADATA = "aetheris-command-module.manifest.json"
DETAILS = "enhanced-module-detail.json"
COMPARISON = "asset-size-comparison.json"


def fail(code: str):
    raise ValueError("E6_W3A_" + code)


def digest(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def read_regular(folder: Path, filename: str, *, limit=MAX_SITE_BYTES) -> bytes:
    path = folder / filename
    if path.is_symlink() or not path.is_file():
        fail("UNTRUSTED_OR_MISSING_FILE")
    if path.stat().st_size > limit:
        fail("FILE_BUDGET")
    raw = path.read_bytes()
    if not raw or len(raw) > limit:
        fail("FILE_EMPTY_OR_TOO_LARGE")
    return raw


def read_evidence(folder: Path, name: str) -> dict:
    data = json.loads(read_regular(folder, name, limit=50_000))
    if not isinstance(data, dict):
        fail("INVALID_EVIDENCE")
    return data


def validate_evidence(source: bytes, blend: bytes, original: bytes, candidate: bytes,
                      manifest: dict, comparison: dict, detail: dict) -> dict:
    """Only a fully bound first-party research proof may be STAGED, never approved."""
    if not source or not blend or not original or not candidate:
        fail("EMPTY_PROOF")
    raw = inspect_glb(original)
    optimized = inspect_glb(candidate)
    before, after = inspect_design(original), inspect_design(candidate)
    if any(v is not False for v in (
        manifest.get("assetApproved"), manifest.get("ownerApproved"),
        manifest.get("licensingCertified"), manifest.get("productionReady"),
        manifest.get("stage45Go"), comparison.get("stage45Go"),
        detail.get("visualApproval"), detail.get("physicalGpuMeasured"),
        detail.get("ownerApproved"), detail.get("stage45Go")
    )):
        fail("PREMATURE_AUTHORITY")
    if (manifest.get("authoringScriptSha256") != digest(source)
        or manifest.get("blenderSourceSha256") != digest(blend)
        or manifest.get("sha256") != digest(original)
        or comparison.get("originalSha256") != digest(original)
        or comparison.get("candidateSha256") != digest(candidate)
        or detail.get("sha256") != digest(original)
        or detail.get("optimizedSha256") != digest(candidate)
        or detail.get("provenanceVerified") is not True
        or detail.get("equalTrianglePositionsNormals") is not True
        or comparison.get("trianglePositionNormalDataEqual") is not True
        or comparison.get("geometryAndMaterialMetadataEqual") is not True
        or comparison.get("candidateUVAttributeSets") != 0):
        fail("HASH_OR_PARITY_MISMATCH")
    if (raw["sha256"] != digest(original)
        or optimized["sha256"] != digest(candidate)
        or before["meshCount"] != 12 or after["meshCount"] != 12
        or before["materialCount"] != 6 or after["materialCount"] != 6
        or before["triangleCount"] != after["triangleCount"]
        or before["materialNames"] != after["materialNames"]
        or before["names"] != after["names"]
        or detail.get("meshCount") != 12
        or detail.get("materialCount") != 6
        or detail.get("triangleCount") != before["triangleCount"]
        or detail.get("optimizedBytes") != len(candidate)
        or detail.get("rawBytes") != len(original)
        or detail.get("rawBytesSavedByOmittingUnusedUV") != len(original) - len(candidate)
        or len(candidate) >= len(original)):
        fail("MODEL_OR_OPTIMIZATION_INVALID")
    return {
        "kind": "E6_W3A_VERIFIED_LOCAL_BLENDER_PROVENANCE_V1",
        "asset": "/assets/aetheris-command-module-no-uv.glb",
        "sourceScriptSha256": digest(source), "blendSha256": digest(blend),
        "originalGlbSha256": digest(original), "stagedGlbSha256": digest(candidate),
        "originalBytes": len(original), "stagedBytes": len(candidate),
        "meshCount": before["meshCount"], "materialCount": before["materialCount"],
        "triangleCount": before["triangleCount"],
        "geometryAndMaterialsIndependentValidation": True,
        "ownerAuthoredSourceClaim": "AUTHORING_SCRIPT_IS_FIRST_PARTY_REPO_SOURCE_REVIEW_REQUIRED",
        "renderedInW2Pages": False, "genuineModelAuthorshipProven": False,
        "licenseIndependentlyCertified": False, "humanVisualApproval": False,
        "physicalGpuMeasured": False, "ownerApproved": False,
        "deploymentAuthorized": False, "stage45Go": False, "releaseGo": False
    }


def ensure_original_files(folder: Path):
    if folder.is_symlink() or not folder.is_dir():
        fail("UNTRUSTED_PREVIEW_DIRECTORY")
    for path in folder.rglob("*"):
        if path.is_symlink() or (not path.is_file() and not path.is_dir()):
            fail("UNTRUSTED_PREVIEW_ENTRY")
        if path.is_file() and path.stat().st_size > MAX_SITE_BYTES:
            fail("PREVIEW_FILE_BUDGET")


def asset_lab_page(proof: dict) -> str:
    hashtext = html.escape(proof["stagedGlbSha256"])
    return """<!doctype html>
<html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="robots" content="noindex,nofollow">
<meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'self'; base-uri 'none'; form-action 'none'">
<title>Asset provenance laboratory — Aetheris E6</title>
<link rel="stylesheet" href="/assets/studio.css"><link rel="stylesheet" href="/assets/asset-lab.css"></head>
<body><a class="skip" href="#main">Skip to content</a>
<header class="site-header"><a href="/">Arcloom preview</a><nav aria-label="Asset navigation"><a href="/studies/">Concept index</a></nav></header>
<main id="main" class="section"><p class="eyebrow">AETHERIS / ASSET RESEARCH</p>
<h1>Provenance before presentation.</h1>
<p class="lead">This original Aetheris command-module GLB is a separate research proof,
not a model of any Arcloom product. Its Blender source and optimized geometry
were checked against source- and content-addressed evidence. No GPU scene is
rendered on this page; cinematic integration belongs to W4.</p>
<section aria-labelledby="asset-status"><h2 id="asset-status">Research-only verification</h2>
<p class="asset-digest-line">Optimized GLB SHA-256: <code class="asset-digest">""" + hashtext + """</code></p>
<p>The asset contains 12 meshes and 6 authored PBR materials.
This check does not certify third-party rights, brand quality, device performance,
model authorship or release approval.</p>
<p><a download href="/assets/aetheris-command-module-no-uv.glb">Download validated local research GLB</a></p>
<p><a download href="/assets/w3a-provenance.json">Read asset provenance evidence</a></p></section>
<a href="/studies/">Return to case studies</a></main></body></html>
"""


def stage() -> dict:
    if W3_PREVIEW.exists() or W3_PREVIEW.is_symlink():
        fail("NO_OVERWRITE")
    ensure_original_files(W2C_PREVIEW)
    if W2C_PREVIEW.resolve() == W3_PREVIEW.resolve():
        fail("PREVIEW_ISOLATION")
    if (ASSET_PROOF.is_symlink() or not ASSET_PROOF.is_dir()
        or BLENDER.is_symlink() or not BLENDER.is_dir()):
        fail("ASSET_SOURCE_DIRECTORY")
    source = read_regular(BLENDER, SCRIPT, limit=50_000)
    blend = read_regular(ASSET_PROOF, BLEND, limit=5_000_000)
    original = read_regular(ASSET_PROOF, ORIGINAL)
    candidate = read_regular(ASSET_PROOF, CANDIDATE)
    manifest = read_evidence(ASSET_PROOF, METADATA)
    comparison = read_evidence(ASSET_PROOF, COMPARISON)
    detail = read_evidence(ASSET_PROOF, DETAILS)
    proof = validate_evidence(source, blend, original, candidate, manifest, comparison, detail)
    base_manifest = read_evidence(W2C_PREVIEW, "build-evidence.json")
    if (base_manifest.get("kind") != "E6_W2C_NARRATIVE_PAGES_V1"
        or base_manifest.get("releaseGo") is not False
        or base_manifest.get("stage45Go") is not False):
        fail("INVALID_W2C_PREVIEW_BASE")
    site = W2C_PREVIEW / "studies/index.html"
    page = read_regular(site.parent, site.name).decode("utf-8")
    if page.count("</main>") != 1:
        fail("SOURCE_PAGE_NOT_COMPOSABLE")
    # New fixed directory is exclusive; source and previous preview never mutated.
    shutil.copytree(W2C_PREVIEW, W3_PREVIEW, symlinks=False)
    assets = W3_PREVIEW / "assets"
    (assets / CANDIDATE).write_bytes(candidate)
    (assets / "asset-lab.css").write_text(
        ".asset-digest-line{min-width:0;max-width:100%}"
        ".asset-digest{overflow-wrap:anywhere;word-break:break-word;white-space:normal}"
        "@media(max-width:480px){#main{min-width:0}#main>*{max-width:100%}}\n",
        encoding="utf-8")
    (assets / "w3a-provenance.json").write_text(
        json.dumps(proof, sort_keys=True, indent=2) + "\n", encoding="utf-8")
    lab = W3_PREVIEW / "asset-lab"
    lab.mkdir()
    (lab / "index.html").write_text(asset_lab_page(proof), encoding="utf-8")
    link = ('<p><a href="/asset-lab/">Inspect independently validated Blender '
            'research asset (not an Arcloom concept) ↗</a></p>')
    (W3_PREVIEW / "studies/index.html").write_text(
        page.replace("</main>", link + "</main>"), encoding="utf-8")
    proof["previewRoot"] = "target/e6-w3a-arcloom"
    proof["w2cOriginalUntouched"] = True
    return proof


if __name__ == "__main__":
    if len(sys.argv) != 1:
        fail("NO_USER_SUPPLIED_ARGUMENTS")
    result = stage()
    print("E6_W3A_ASSET_STAGED_WITH_VALIDATED_PROVENANCE "
          + json.dumps(result, sort_keys=True))
