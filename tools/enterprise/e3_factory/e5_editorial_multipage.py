#!/usr/bin/env python3
"""E5 bounded three-page OFFLINE editorial preview from supplied human copy.

Compiles a reviewed structured layout bundle, then adds two deterministic
navigable HTML pages. No model calls, HTTP, remote images, forms, execution,
publication, or assertion that customer/source content is authenticated.
"""
from __future__ import annotations

import hashlib
import html
import io
import json
import os
import re
import sys
import zipfile
from pathlib import Path

import e5_creative_brief_gate as gate
import e5_layout_design_intent as intent
import e5_reference_preview as ref
import e5_structured_layout_preview as layout
import e5_untrusted_copy_preview as copy_schema

FOLDER = layout.FOLDER
CONTENT_FILE = FOLDER / "editorial-content.json"
OUTPUT = FOLDER / "editorial-three-page-review.zip"
MAX_CONTENT_BYTES = 4_096
ROOT_KEYS = {"version", "brandLabel", "project", "contact"}
PROJECT_KEYS = {"eyebrow", "title", "summary", "process", "result"}
CONTACT_KEYS = {"headline", "message", "email"}
MAILBOX = re.compile(r"[A-Za-z0-9][A-Za-z0-9._%+-]{0,63}@[A-Za-z0-9][A-Za-z0-9.-]{0,120}\.[A-Za-z]{2,24}\Z")
ALLOWED_PARENT = frozenset((
    "index.html", "styles.css", "scene.js", "e5-layout.css",
    "copy-proposal.json", "layout-proposal.json", "preview-manifest.json",
))
THEMES = {
    "warm-stone": ("#151310", "#efe8dc", "#e8b98b"),
    "charcoal": ("#111821", "#f0f5f6", "#d5bca0"),
    "paper": ("#f6f0e7", "#20242a", "#745038"),
}
EDITORIAL_CSS = """/* Original authored review-only editorial pages; no external assets. */
.e5-editorial-nav{display:flex;gap:1.3rem;flex-wrap:wrap;margin:.4rem 0}
.e5-editorial-nav a{font-size:.85rem;font-weight:600;text-underline-offset:.25em}
.e5-editorial-nav a:focus-visible{outline:3px solid currentColor;outline-offset:5px}
.e5-editorial-page{min-height:100vh;margin:0;font-family:system-ui,sans-serif;
 line-height:1.65;background:#151310;color:#efe8dc}
.e5-theme-paper{background:#f6f0e7;color:#20242a}
.e5-theme-charcoal{background:#111821;color:#f0f5f6}
.e5-editorial-page *{box-sizing:border-box}
.e5-editorial-skip{position:absolute;left:1rem;top:-6rem;padding:.6rem 1rem;
 background:#fff;color:#111;z-index:999}
.e5-editorial-skip:focus{top:.5rem}
.e5-editorial-header{max-width:1160px;margin:auto;padding:1.5rem 5%;
 display:flex;align-items:center;justify-content:space-between;gap:1rem;flex-wrap:wrap;
 border-bottom:1px solid currentColor}
.e5-editorial-brand{font-size:.85rem;font-weight:750;letter-spacing:.07em;text-decoration:none}
.e5-editorial-shell{max-width:1160px;margin:auto;padding:clamp(3rem,8vw,7rem) 5% 6rem}
.e5-editorial-kicker{font-size:.76rem;font-weight:750;letter-spacing:.16em;text-transform:uppercase}
.e5-editorial-page h1{font-size:clamp(2.8rem,7vw,6.7rem);line-height:1.04;
 letter-spacing:-.06em;max-width:13ch;margin:.6rem 0 2.5rem}
.e5-editorial-serif h1,.e5-editorial-serif h2{font-family:Georgia,serif;letter-spacing:-.04em}
.e5-editorial-page h2{font-size:clamp(1.35rem,2.7vw,2.4rem);line-height:1.18}
.e5-editorial-columns{display:grid;grid-template-columns:1fr 1fr;gap:clamp(2rem,6vw,7rem)}
.e5-editorial-page p{max-width:60ch}
.e5-editorial-page a{color:inherit}
.e5-editorial-page a:focus-visible{outline:3px solid currentColor;outline-offset:5px}
.e5-editorial-mail{display:inline-block;margin:2rem 0;font-size:clamp(1.1rem,2vw,1.75rem);
 overflow-wrap:anywhere;text-underline-offset:.2em}
.e5-editorial-footer{max-width:1160px;margin:0 auto;padding:2rem 5%;
 border-top:1px solid currentColor;font-size:.78rem}
.e5-editorial-note{font-size:.8rem;opacity:.8;margin-top:3rem}
@media(max-width:700px){
 .e5-editorial-columns{grid-template-columns:1fr}
 .e5-editorial-header{align-items:flex-start}
 .e5-editorial-nav{gap:.8rem}
}
@media(prefers-reduced-motion:reduce){
 .e5-editorial-page *{animation:none!important;transition:none!important;scroll-behavior:auto!important}
}
"""


def _canonical(value: object) -> bytes:
    return (json.dumps(value, ensure_ascii=False, sort_keys=True,
                       separators=(",", ":")) + "\n").encode("utf-8")


def _text(value: object, lower: int, upper: int) -> str:
    if (type(value) is not str or not lower <= len(value) <= upper
            or value != value.strip() or not value.strip()
            or any(ord(c) < 32 or 0x7f <= ord(c) <= 0x9f
                   or ord(c) in (0x202a, 0x202b, 0x202d, 0x202e, 0x2066, 0x2067, 0x2068, 0x2069)
                   for c in value)
            or "://" in value or "javascript:" in value.casefold()):
        raise ValueError("E5_EDITORIAL_UNSAFE_TEXT")
    return value


def read_content(raw: bytes) -> dict:
    if not raw or len(raw) > MAX_CONTENT_BYTES:
        raise ValueError("E5_EDITORIAL_CONTENT_BUDGET")
    try:
        item = json.loads(raw.decode("utf-8", errors="strict"),
                          object_pairs_hook=gate._unique)
    except (UnicodeError, ValueError) as exc:
        raise ValueError("E5_EDITORIAL_JSON_INVALID") from exc
    if type(item) is not dict or set(item) != ROOT_KEYS or type(item["version"]) is not int or item["version"] != 1:
        raise ValueError("E5_EDITORIAL_SCHEMA")
    if type(item["project"]) is not dict or set(item["project"]) != PROJECT_KEYS:
        raise ValueError("E5_EDITORIAL_PROJECT_SCHEMA")
    if type(item["contact"]) is not dict or set(item["contact"]) != CONTACT_KEYS:
        raise ValueError("E5_EDITORIAL_CONTACT_SCHEMA")
    brand = _text(item["brandLabel"], 3, 38)
    project = {
        "eyebrow": _text(item["project"]["eyebrow"], 4, 45),
        "title": _text(item["project"]["title"], 6, 85),
        "summary": _text(item["project"]["summary"], 30, 420),
        "process": _text(item["project"]["process"], 30, 500),
        "result": _text(item["project"]["result"], 30, 500),
    }
    contact = {
        "headline": _text(item["contact"]["headline"], 8, 90),
        "message": _text(item["contact"]["message"], 18, 450),
        "email": _text(item["contact"]["email"], 6, 160),
    }
    if not MAILBOX.fullmatch(contact["email"]) or ".." in contact["email"]:
        raise ValueError("E5_EDITORIAL_MAILBOX_INVALID")
    return {"version": 1, "brandLabel": brand, "project": project,
            "contact": contact}


def _escape(text: str) -> str:
    return html.escape(text, quote=True)


def _header(brand: str, current: str) -> str:
    links = (("Home", "index.html"), ("Project", "work.html"),
             ("Contact", "contact.html"))
    nav = " ".join(
        '<a href="./' + target + '"' +
        (' aria-current="page"' if target == current else "") +
        '>' + name + '</a>' for name, target in links)
    return (
        '<header class="e5-editorial-header">'
        '<a class="e5-editorial-brand" href="./index.html">' + _escape(brand) + '</a>'
        '<nav class="e5-editorial-nav" aria-label="Editorial pages">' +
        nav + '</nav></header>'
    )


def _page(brand: str, palette: str, typography: str,
          title: str, main: str, current: str) -> bytes:
    kind = "e5-editorial-serif" if typography == "editorial-serif" else ""
    markup = (
        '<!doctype html>\n<html lang="en"><head><meta charset="utf-8">'
        '<meta name="viewport" content="width=device-width, initial-scale=1">'
        '<meta name="robots" content="noindex,nofollow">'
        '<title>' + _escape(title) + ' — ' + _escape(brand) + '</title>'
        '<link rel="stylesheet" href="./editorial.css"></head>'
        '<body class="e5-editorial-page e5-theme-' + palette + ' ' + kind + '">'
        '<a class="e5-editorial-skip" href="#main">Skip to main content</a>'
        + _header(brand, current) +
        '<main id="main" class="e5-editorial-shell">' + main + '</main>'
        '<footer class="e5-editorial-footer">INDEPENDENT HUMAN CONTENT REVIEW / NOT PUBLISHED</footer>'
        '</body></html>\n'
    )
    return markup.encode("utf-8")


def _zip(files: dict[str, bytes]) -> bytes:
    out = io.BytesIO()
    with zipfile.ZipFile(out, "w") as archive:
        for name, data in sorted(files.items()):
            info = zipfile.ZipInfo(name, ref.PINNED_TIME)
            info.external_attr = 0o100644 << 16
            info.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(info, data, compress_type=zipfile.ZIP_DEFLATED,
                             compresslevel=9)
    return out.getvalue()


def build(brief: dict, proposal: dict, choice: dict,
          content: dict) -> tuple[bytes, dict]:
    reviewed = read_content(_canonical(content))
    b = gate.read_brief(_canonical(brief))
    decision = layout.read_layout(_canonical(choice))
    if reviewed["brandLabel"] != b["brandLabel"]:
        raise ValueError("E5_EDITORIAL_BRAND_MISMATCH")
    parent_bytes, parent_evidence = layout.build(b, proposal, decision)
    with zipfile.ZipFile(io.BytesIO(parent_bytes)) as base:
        if set(base.namelist()) != ALLOWED_PARENT or base.testzip() is not None:
            raise ValueError("E5_EDITORIAL_UNEXPECTED_PARENT")
        assets = {name: base.read(name) for name in ALLOWED_PARENT}
    html_home = assets["index.html"].decode("utf-8")
    if html_home.count("</header>") != 1 or html_home.count("</head>") != 1:
        raise ValueError("E5_EDITORIAL_HOME_ANCHOR_CHANGED")
    home_nav = (
        '<nav class="e5-editorial-nav" aria-label="Editorial pages">'
        '<a href="./work.html">Project</a>'
        '<a href="./contact.html">Contact</a></nav>'
    )
    html_home = html_home.replace("</header>", home_nav + "</header>")
    html_home = html_home.replace(
        "</head>", '<meta name="robots" content="noindex,nofollow">'
        '<link rel="stylesheet" href="./editorial.css"></head>')
    assets["index.html"] = html_home.encode("utf-8")
    project = reviewed["project"]
    work_content = (
        '<p class="e5-editorial-kicker">' + _escape(project["eyebrow"]) + '</p>'
        '<h1>' + _escape(project["title"]) + '</h1>'
        '<p>' + _escape(project["summary"]) + '</p>'
        '<div class="e5-editorial-columns">'
        '<section aria-labelledby="work-process"><h2 id="work-process">Approach</h2>'
        '<p>' + _escape(project["process"]) + '</p></section>'
        '<section aria-labelledby="work-result"><h2 id="work-result">Outcome</h2>'
        '<p>' + _escape(project["result"]) + '</p></section></div>'
        '<p class="e5-editorial-note">Content is supplied for review; claims '
        'have not been independently verified.</p>'
    )
    contact = reviewed["contact"]
    contact_content = (
        '<p class="e5-editorial-kicker">Editorial enquiry</p>'
        '<h1>' + _escape(contact["headline"]) + '</h1>'
        '<p>' + _escape(contact["message"]) + '</p>'
        '<a class="e5-editorial-mail" href="mailto:' + _escape(contact["email"]) + '">'
        + _escape(contact["email"]) + '</a>'
        '<p class="e5-editorial-note">Email link only; no form data collection, '
        'analytics, or server-side submission.</p>'
    )
    assets["work.html"] = _page(reviewed["brandLabel"], decision["palette"],
                               decision["typography"], project["title"],
                               work_content, "work.html")
    assets["contact.html"] = _page(reviewed["brandLabel"], decision["palette"],
                                  decision["typography"], contact["headline"],
                                  contact_content, "contact.html")
    assets["editorial.css"] = EDITORIAL_CSS.encode("utf-8")

    parser = ref.AssetParser()
    parser.feed(html_home)
    if parser.errors or set(parser.assets) != {
            "./styles.css", "./scene.js", "./e5-layout.css", "./editorial.css"}:
        raise ValueError("E5_EDITORIAL_HOME_RESOURCE_UNSAFE")
    if layout._git_blob_sha(assets["scene.js"]) != layout.PINNED_SCENE_BLOB:
        raise ValueError("E5_EDITORIAL_REFERENCE_SCRIPT_CHANGED")
    if layout._git_blob_sha(assets["styles.css"]) != layout.PINNED_STYLES_BLOB:
        raise ValueError("E5_EDITORIAL_REFERENCE_STYLES_CHANGED")
    if sum(len(data) for data in assets.values()) > 90_000:
        raise ValueError("E5_EDITORIAL_OUTPUT_TOO_LARGE")

    manifest = {
        "formatVersion": 1,
        "artifactClass": "E5_THREE_PAGE_HUMAN_CONTENT_EDITORIAL_REVIEW",
        "parentPreviewSha256": hashlib.sha256(parent_bytes).hexdigest(),
        "parentManifestSha256": hashlib.sha256(assets["preview-manifest.json"]).hexdigest(),
        "editorialContentSha256": hashlib.sha256(_canonical(reviewed)).hexdigest(),
        "pagePaths": ["index.html", "work.html", "contact.html"],
        "inputAuthenticityVerified": False,
        "siteCodeModelAuthored": False,
        "humanEditorialClaimsReviewed": False,
        "ownerApproved": False,
        "externalNetworkRequestsRequired": False,
        "deploymentAuthorized": False,
        "productionReady": False,
        "stage45Go": False,
        "files": [
            {"path": name, "bytes": len(data),
             "sha256": hashlib.sha256(data).hexdigest()}
            for name, data in sorted(assets.items())
            if name != "preview-manifest.json"
        ],
    }
    assets["preview-manifest.json"] = _canonical(manifest)
    return _zip(assets), manifest


def main() -> int:
    if len(sys.argv) != 1:
        print("E5_EDITORIAL_NO_CUSTOM_PATH_ARGS", file=sys.stderr)
        return 2
    try:
        if FOLDER.is_symlink() or not FOLDER.is_dir():
            raise ValueError("E5_EDITORIAL_OUTPUT_DIRECTORY_UNSAFE")
        brief = gate.read_brief(gate._read_regular(gate.BRIEF, gate.MAX_BRIEF_BYTES))
        proposal = copy_schema.read_proposal(
            gate._read_regular(gate.PROPOSAL, copy_schema.MAX_INPUT))
        choice = layout.read_layout(
            gate._read_regular(layout.LAYOUT, layout.MAX_LAYOUT_BYTES))
        content = read_content(gate._read_regular(CONTENT_FILE, MAX_CONTENT_BYTES))
        bundle, manifest = build(brief, proposal, choice, content)
        # Refuse overwrite, symlink following, and arbitrary user paths.
        fd = os.open(OUTPUT, os.O_WRONLY | os.O_CREAT | os.O_EXCL |
                     getattr(os, "O_NOFOLLOW", 0), 0o600)
        with os.fdopen(fd, "wb") as out:
            out.write(bundle)
            out.flush()
            os.fsync(out.fileno())
        print(json.dumps({
            "status": "E5_THREE_PAGE_EDITORIAL_REVIEW_NOT_PUBLISHED",
            "archiveSha256": hashlib.sha256(bundle).hexdigest(),
            "pageCount": len(manifest["pagePaths"]),
            "modelInferencePerformed": False,
            "ownerApproved": False,
            "stage45Go": False,
        }, sort_keys=True))
        return 0
    except (OSError, ValueError):
        print("E5_THREE_PAGE_EDITORIAL_REVIEW_DENIED", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
