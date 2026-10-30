#!/usr/bin/env python3
"""Strict stdlib PNG pixel comparison for E6 software-WebGL screenshots.

Measures pixel differences under the exact same CI SwiftShader configuration.
This is not independent artistic review or a hardware GPU performance claim.
"""
from __future__ import annotations

import hashlib
import json
import struct
import sys
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
EVIDENCE = ROOT / "target" / "e6-studio" / "browser-visual-parity"
SCENES = ("desktop-front", "desktop-detail", "mobile-front")
MAX_PNG_BYTES = 7_000_000
MAX_PIXELS = 1_500_000


def png_rgb(raw: bytes) -> tuple[int, int, bytes]:
    if len(raw) > MAX_PNG_BYTES or not raw.startswith(b"\x89PNG\r\n\x1a\n"):
        raise ValueError("E6_VISUAL_INVALID_PNG_HEADER_OR_SIZE")
    offset, idat, w, h, channels = 8, bytearray(), 0, 0, 0
    types = []
    while offset < len(raw):
        if offset + 12 > len(raw):
            raise ValueError("E6_VISUAL_TRUNCATED_CHUNK")
        length = int.from_bytes(raw[offset:offset + 4], "big")
        chunk_type = raw[offset + 4:offset + 8]
        end = offset + 12 + length
        if end > len(raw):
            raise ValueError("E6_VISUAL_CHUNK_OUT_OF_BOUNDS")
        payload = raw[offset + 8:offset + 8 + length]
        checksum = int.from_bytes(raw[offset + 8 + length:end], "big")
        if zlib.crc32(chunk_type + payload) & 0xffffffff != checksum:
            raise ValueError("E6_VISUAL_PNG_CRC")
        types.append(chunk_type)
        if chunk_type == b"IHDR":
            if len(payload) != 13 or w:
                raise ValueError("E6_VISUAL_IHDR")
            w, h, depth, kind, compression, filtering, interlace = struct.unpack(">IIBBBBB", payload)
            channels = {2: 3, 6: 4}.get(kind, 0)
            if (not channels or depth != 8 or compression or filtering or interlace
                    or not 64 <= w <= 2000 or not 64 <= h <= 2000 or w * h > MAX_PIXELS):
                raise ValueError("E6_VISUAL_UNSUPPORTED_IMAGE_MODE")
        elif chunk_type == b"IDAT":
            idat.extend(payload)
            if len(idat) > MAX_PNG_BYTES:
                raise ValueError("E6_VISUAL_COMPRESSED_SIZE")
        elif chunk_type == b"IEND":
            if end != len(raw):
                raise ValueError("E6_VISUAL_TRAILING_BYTES")
            break
        elif chunk_type not in (b"gAMA", b"sRGB", b"cHRM", b"pHYs", b"iCCP", b"tEXt"):
            raise ValueError("E6_VISUAL_UNSUPPORTED_PNG_CHUNK")
        offset = end
    if not w or not h or b"IEND" not in types or len(idat) == 0:
        raise ValueError("E6_VISUAL_INCOMPLETE_PNG")
    try:
        data = zlib.decompress(bytes(idat))
    except zlib.error as error:
        raise ValueError("E6_VISUAL_PNG_INFLATE") from error
    stride = w * channels
    if len(data) != (stride + 1) * h:
        raise ValueError("E6_VISUAL_PNG_SCANLINE_SIZE")
    rgb = bytearray(w * h * 3)
    before = bytearray(stride)
    index = 0
    for row in range(h):
        filter_type = data[index]
        index += 1
        scan = bytearray(data[index:index + stride])
        index += stride
        if filter_type not in range(5):
            raise ValueError("E6_VISUAL_UNSUPPORTED_PNG_FILTER")
        for col in range(stride):
            left = scan[col - channels] if col >= channels else 0
            top = before[col]
            upper_left = before[col - channels] if col >= channels else 0
            if filter_type == 1:
                estimate = left
            elif filter_type == 2:
                estimate = top
            elif filter_type == 3:
                estimate = (left + top) // 2
            elif filter_type == 4:
                p = left + top - upper_left
                distances = (abs(p - left), abs(p - top), abs(p - upper_left))
                estimate = (left, top, upper_left)[distances.index(min(distances))]
            else:
                estimate = 0
            scan[col] = (scan[col] + estimate) & 0xff
        for col in range(w):
            source = col * channels
            target = (row * w + col) * 3
            rgb[target:target + 3] = scan[source:source + 3]
        before = scan
    return w, h, bytes(rgb)


def diff_pixels(a: tuple, b: tuple) -> dict:
    if a[:2] != b[:2]:
        raise ValueError("E6_VISUAL_DIFFERENT_SCREENSHOT_DIMENSIONS")
    first, second = a[2], b[2]
    pixels = len(first) // 3
    bad, sum_diff, max_diff = 0, 0, 0
    for i in range(0, len(first), 3):
        differences = [abs(first[i + c] - second[i + c]) for c in range(3)]
        if max(differences) > 8:
            bad += 1
        sum_diff += sum(differences)
        max_diff = max(max_diff, *differences)
    return {
        "pixelCount": pixels,
        "pixelsBeyond8ChannelError": bad,
        "fractionBeyond8": round(bad / pixels, 6),
        "meanChannelAbsoluteError": round(sum_diff / (pixels * 3), 6),
        "maxChannelError": max_diff,
    }


def compare_triplet(original: bytes, repeat: bytes, candidate: bytes) -> dict:
    decoded = [png_rgb(x) for x in (original, repeat, candidate)]
    w, h, rgb = decoded[0]
    unique = set(rgb[index:index+3] for index in range(0, len(rgb), max(3, len(rgb) // 1024 // 3 * 3)))
    luminances = [sum(c) // 3 for c in unique]
    if len(unique) < 12 or max(luminances) - min(luminances) < 20:
        raise ValueError("E6_VISUAL_MISSING_NONFLAT_SCENE")
    stable = diff_pixels(decoded[0], decoded[1])
    variation = diff_pixels(decoded[0], decoded[2])
    if stable["fractionBeyond8"] > 0.01:
        raise ValueError("E6_VISUAL_BASELINE_NOT_STABLE")
    if variation["fractionBeyond8"] > max(0.01, stable["fractionBeyond8"] + 0.005):
        raise ValueError("E6_VISUAL_OPTIMIZED_PIXEL_PARITY_FAILED")
    if variation["meanChannelAbsoluteError"] > max(0.7, stable["meanChannelAbsoluteError"] + 0.3):
        raise ValueError("E6_VISUAL_OPTIMIZED_MEAN_ERROR")
    return {
        "width": w,
        "height": h,
        "sampledUniqueColors": len(unique),
        "baselineRepeat": stable,
        "optimizedVsOriginal": variation,
        "softwareRenderedPixelParityPassed": True,
        "humanVisualReviewPassed": False,
        "physicalGpuMeasured": False,
    }


def main() -> None:
    if len(sys.argv) != 1:
        raise SystemExit("No arbitrary user-supplied paths accepted")
    manifest_path = EVIDENCE / "capture-report.json"
    report_path = EVIDENCE / "pixel-diff-report.json"
    if report_path.exists() or report_path.is_symlink():
        raise SystemExit("E6_VISUAL_REPORT_NO_OVERWRITE")
    capture = json.loads(manifest_path.read_text(encoding="utf-8"))
    if (capture.get("kind") != "E6_TWO_BLENDER_GLB_CHROMIUM_SCREENSHOT_INPUT"
            or capture.get("stage45Go") is not False
            or capture.get("controlShaValidated") is not True
            or capture.get("failures") != []
            or len(capture.get("captures", [])) != 9):
        raise ValueError("E6_VISUAL_UNVERIFIED_CAPTURE_SOURCE")
    saved = {(c["scene"], c["variant"]): c for c in capture["captures"]}
    result = {
        "kind": "E6_SOFTWARE_CHROMIUM_TWO_GLB_PIXEL_PARITY",
        "renderer": "CHROMIUM_SWIFTSHADER_SOFTWARE",
        "noOwnerGpuCertification": True,
        "noIndependentDesignAcceptance": True,
        "noWebsiteAssetChange": True,
        "noDeployment": True,
        "ownerApproved": False,
        "stage45Go": False,
        "cases": [],
    }
    for scene in SCENES:
        screenshots = []
        for variant in ("original", "repeat", "candidate"):
            c = saved[(scene, variant)]
            filename = f"{scene}-{variant}.png"
            if c["screenshot"] != filename or c["networkRequestsOutside"] or c["errors"]:
                raise ValueError("E6_VISUAL_CAPTURE_IDENTITY_OR_BROWSER_ERROR")
            image = EVIDENCE / filename
            if image.is_symlink():
                raise ValueError("E6_VISUAL_SYMLINK_DENIED")
            raw = image.read_bytes()
            if hashlib.sha256(raw).hexdigest() != c["screenshotSha256"]:
                raise ValueError("E6_VISUAL_SCREENSHOT_TAMPERED")
            screenshots.append(raw)
        score = compare_triplet(*screenshots)
        result["cases"].append({"scene": scene, **score})
    report_path.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print("E6_SWIFTSHADER_PIXEL_PARITY_ONLY_PASS " + json.dumps(result, sort_keys=True))


if __name__ == "__main__":
    main()
