"""No browser/network required: synthetic PNG corruption and pixel parity cases."""
import json
import struct
import sys
import unittest
import zlib
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent / "next-studio" / "scripts"))
from compare_visual_png import png_rgb, diff_pixels, compare_triplet


def img(width=96, height=72, color_change=None):
    lines = []
    for y in range(height):
        pixels = []
        for x in range(width):
            color = ((x * 5) % 256, (y * 4) % 256, (x + y) % 256)
            if color_change and color_change(x, y):
                color = (255, 0, 0)
            pixels.extend(color)
        lines.append(b"\x00" + bytes(pixels))
    raw = zlib.compress(b"".join(lines), 9)
    def chunk(name, data):
        return (struct.pack(">I", len(data)) + name + data
                + struct.pack(">I", zlib.crc32(name + data) & 0xffffffff))
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR",
            struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))
            + chunk(b"IDAT", raw) + chunk(b"IEND", b""))


class VisualParityPolicyTests(unittest.TestCase):
    def test_identical_variants_pass_not_gpu_certification(self):
        source = img()
        out = compare_triplet(source, source, source)
        self.assertEqual(out["optimizedVsOriginal"]["fractionBeyond8"], 0)
        self.assertTrue(out["softwareRenderedPixelParityPassed"])
        self.assertFalse(out["humanVisualReviewPassed"])
        self.assertFalse(out["physicalGpuMeasured"])

    def test_large_appearance_change_refused(self):
        source = img()
        changed = img(color_change=lambda x, y: x < 12)
        with self.assertRaisesRegex(ValueError, "PIXEL_PARITY_FAILED"):
            compare_triplet(source, source, changed)

    def test_unstable_control_refused(self):
        source = img()
        unstable = img(color_change=lambda x, y: x < 12)
        with self.assertRaisesRegex(ValueError, "BASELINE_NOT_STABLE"):
            compare_triplet(source, unstable, source)

    def test_tampered_png_crc_refused(self):
        data = bytearray(img())
        data[18] ^= 1
        with self.assertRaisesRegex(ValueError, "PNG_CRC"):
            png_rgb(bytes(data))

    def test_truncated_png_refused(self):
        with self.assertRaises(ValueError):
            png_rgb(img()[:40])

    def test_size_mismatch_refused(self):
        with self.assertRaisesRegex(ValueError, "DIFFERENT_SCREENSHOT_DIMENSIONS"):
            diff_pixels(png_rgb(img()), png_rgb(img(width=100)))


if __name__ == "__main__":
    unittest.main()
