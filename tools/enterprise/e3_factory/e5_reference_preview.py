#!/usr/bin/env python3
"""E5 first offline website-factory output: reproducible reference preview.

Packages ONLY the trusted existing E3.9H human-coded reference. It cannot
package model output, deploy a site, execute JavaScript or authorize publication.
"""
from __future__ import annotations

import hashlib
import io
import json
import re
import sys
import zipfile
from html.parser import HTMLParser
from pathlib import Path

HERE = Path(__file__).resolve().parent
REFERENCE = HERE / "e39h" / "reference"
PROJECT = HERE.parents[2]
DESTINATION = PROJECT / "target" / "e5" / "reference-preview.zip"
FILES = ("index.html", "scene.js", "styles.css")
MAX_FILE_BYTES = 20_000
MAX_TOTAL_BYTES = 60_000
PINNED_TIME = (2024, 1, 1, 0, 0, 0)


class AssetParser(HTMLParser):
    def __init__(self) -> None:
        super().__init__()
        self.assets: list[str] = []
        self.errors: list[str] = []
        self.ids: set[str] = set()

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        d = dict(attrs)
        if tag in ("script", "link"):
            asset = d.get("src" if tag == "script" else "href")
            if asset:
                self.assets.append(asset)
        for name, value in attrs:
            if name.startswith("on") or name.lower() == "srcdoc":
                self.errors.append("inline_event_or_srcdoc")
            if name == "id":
                if value in self.ids:
                    self.errors.append("duplicate_id")
                if value is not None:
                    self.ids.add(value)
            if name in ("href", "src") and value is not None:
                if value.startswith(("https:", "http:", "//", "javascript:", "data:", "file:", "/")):
                    self.errors.append("external_or_unsafe_asset")


def validate_source(directory: Path) -> dict[str, bytes]:
    if directory.is_symlink() or not directory.is_dir():
        raise ValueError("E5_INVALID_SOURCE_DIRECTORY")
    entries = tuple(sorted(p.name for p in directory.iterdir()))
    if entries != tuple(sorted(FILES)):
        raise ValueError("E5_UNAPPROVED_ASSET_SET")
    files: dict[str, bytes] = {}
    for name in FILES:
        file = directory / name
        if file.is_symlink() or not file.is_file() or file.stat().st_size > MAX_FILE_BYTES:
            raise ValueError("E5_SYMLINK_OR_OVERSIZED_ASSET")
        data = file.read_bytes()
        if len(data) > MAX_FILE_BYTES:
            raise ValueError("E5_UNSAFE_ASSET_SIZE")
        try:
            data.decode("utf-8", errors="strict")
        except UnicodeError as exc:
            raise ValueError("E5_BINARY_OR_INVALID_UTF8_ASSET") from exc
        files[name] = data
    if sum(len(value) for value in files.values()) > MAX_TOTAL_BYTES:
        raise ValueError("E5_TOTAL_SOURCE_BUDGET_EXCEEDED")

    html = AssetParser()
    html.feed(files["index.html"].decode("utf-8"))
    if html.errors or set(html.assets) != {"./scene.js", "./styles.css"}:
        raise ValueError("E5_UNAPPROVED_DOCUMENT_REFERENCES")
    if len(html.assets) != 2:
        raise ValueError("E5_DUPLICATED_ACTIVE_ASSETS")
    if not {"main", "chapter", "static-fallback", "study-canvas"}.issubset(html.ids):
        raise ValueError("E5_REQUIRED_ACCESSIBLE_CONTENT_MISSING")
    javascript = files["scene.js"].decode("utf-8")
    css = files["styles.css"].decode("utf-8")
    if (re.search(r"\b(?:fetch\s*\(|XMLHttpRequest|WebSocket|EventSource|sendBeacon|import\s*\()", javascript)
        or re.search(r"@import\b|url\s*\(\s*['\"]?(?:https?:|//|data:)", css, flags=re.I)):
        raise ValueError("E5_UNAPPROVED_REMOTE_RESOURCE")
    if "prefers-reduced-motion" not in css or "reduced-static" not in javascript:
        raise ValueError("E5_MISSING_REDUCED_MOTION_FALLBACK")
    return files


def pack_preview(directory: Path) -> tuple[bytes, dict]:
    files = validate_source(directory)
    manifest = {
        "formatVersion": 1,
        "artifactClass": "E5_TRUSTED_REFERENCE_STATIC_PREVIEW",
        "sourceProvenance": "E3.9H_HUMAN_CODED_BROWSER_REFERENCE",
        "modelAuthored": False,
        "ownerApproved": False,
        "deploymentAuthorized": False,
        "productionReady": False,
        "externalNetworkRequestsRequired": False,
        "runtimeReleaseStatus": "NO_GO",
        "files": [
            {
                "path": name,
                "bytes": len(files[name]),
                "sha256": hashlib.sha256(files[name]).hexdigest(),
            }
            for name in sorted(files)
        ],
    }
    data = dict(files)
    data["preview-manifest.json"] = (
        json.dumps(manifest, sort_keys=True, separators=(",", ":"), ensure_ascii=True) + "\n"
    ).encode("utf-8")
    stream = io.BytesIO()
    with zipfile.ZipFile(stream, "w", compression=zipfile.ZIP_DEFLATED,
                         compresslevel=9, strict_timestamps=True) as archive:
        for name in sorted(data):
            info = zipfile.ZipInfo(name, PINNED_TIME)
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            archive.writestr(info, data[name], compress_type=zipfile.ZIP_DEFLATED,
                             compresslevel=9)
    return stream.getvalue(), manifest


def main() -> int:
    # The CLI intentionally does not take a caller-controlled source path.
    # Only writes the reproducible result into the repo's inert target folder.
    if len(sys.argv) != 1:
        print("E5_NO_CUSTOM_SOURCE_OR_DEPLOYMENT_ARGUMENTS", file=sys.stderr)
        return 2
    try:
        archive, manifest = pack_preview(REFERENCE)
        DESTINATION.parent.mkdir(parents=True, exist_ok=True)
        if DESTINATION.is_symlink() or DESTINATION.parent.is_symlink():
            raise ValueError("E5_UNSAFE_OUTPUT_SYMLINK")
        DESTINATION.write_bytes(archive)
        print(json.dumps({
            "status": "LOCAL_REFERENCE_PREVIEW_BUILT_NOT_DEPLOYED",
            "path": str(DESTINATION.relative_to(PROJECT)),
            "archiveSha256": hashlib.sha256(archive).hexdigest(),
            "artifactClass": manifest["artifactClass"],
            "modelAuthored": False,
            "productionReady": False,
            "release": "NO_GO",
        }, sort_keys=True))
        return 0
    except (OSError, ValueError) as exc:
        print("E5_PREVIEW_REJECTED_" + str(exc), file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
