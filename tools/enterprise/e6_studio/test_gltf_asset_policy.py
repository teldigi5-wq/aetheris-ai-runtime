"""E6 deterministic glTF2 research fixture and browser trust-boundary regression."""
import hashlib
import json
import tempfile
import unittest
from pathlib import Path
from unittest import mock

import generate_reference_gltf as fixture

ROOT = Path(__file__).parent
ASSET_PAGE = ROOT / "gltf_lab" / "asset_page.html"
RENDERER = ROOT / "gltf_lab" / "gltf_scene.js"
BROWSER = ROOT / "gltf_browser_proof.mjs"
EXPECTED_SHA256 = "ace770e960909c2b788312dfd80e9d788ed89fd1eeb3f8008842b4e246af45a7"


class GltfAssetTests(unittest.TestCase):
    def test_reproducible_exact_gltf2_36_vertices_and_embedded_geometry(self):
        a = fixture.expected_bytes()
        b = fixture.expected_bytes()
        self.assertEqual(a,b)
        self.assertEqual(hashlib.sha256(a).hexdigest(),EXPECTED_SHA256)
        m=fixture.validate(a)
        self.assertEqual(m["vertexCount"],36)
        self.assertEqual(m["interleavedBytes"],864)
        self.assertTrue(m["localEmbeddedBuffer"])
        self.assertFalse(m["externalUris"])
        self.assertFalse(m["blenderExported"])
        self.assertFalse(m["modelAuthored"])
        self.assertFalse(m["stage45Go"])
        self.assertEqual(json.loads(a)["asset"]["version"],"2.0")

    def test_external_binary_texture_or_extension_is_never_accepted(self):
        src=json.loads(fixture.expected_bytes())
        changed=json.loads(json.dumps(src))
        changed["buffers"][0]["uri"]="https://untrusted.invalid/mesh.bin"
        with self.assertRaisesRegex(ValueError,"NOT_PINNED"):
            fixture.validate(fixture.canonical(changed))
        changed=json.loads(json.dumps(src))
        changed["images"]=[{"uri":"https://untrusted.invalid/a.png"}]
        with self.assertRaisesRegex(ValueError,"UNAPPROVED_SCHEMA"):
            fixture.validate(fixture.canonical(changed))
        changed=json.loads(json.dumps(src))
        changed["extensionsRequired"]=["KHR_draco_mesh_compression"]
        with self.assertRaisesRegex(ValueError,"UNAPPROVED_SCHEMA"):
            fixture.validate(fixture.canonical(changed))

    def test_geometry_mutations_duplicate_metadata_or_over_budget_fail(self):
        src=json.loads(fixture.expected_bytes())
        changed=json.loads(json.dumps(src))
        changed["bufferViews"][0]["byteStride"]=12
        with self.assertRaisesRegex(ValueError,"NOT_PINNED"):
            fixture.validate(fixture.canonical(changed))
        with self.assertRaises(ValueError):
            fixture.validate(b' '*(fixture.MAX_BYTES+1))
        with self.assertRaises(ValueError):
            fixture.validate(b"\xff")
        with self.assertRaises(ValueError):
            fixture.validate(b'{"asset":{"version":"2.0"}}')

    def test_generator_refuses_overwrite_and_has_no_arbitrary_output_paths(self):
        with tempfile.TemporaryDirectory() as tmp:
            folder=Path(tmp)
            target=folder/"reference_mesh.gltf"
            with (mock.patch.object(fixture,"FOLDER",folder),
                  mock.patch.object(fixture,"FILE",target),
                  mock.patch("sys.argv",["generate_reference_gltf.py","--generate"])):
                self.assertEqual(fixture.main(),0)
                self.assertEqual(target.stat().st_mode&0o777,0o600)
                original=target.read_bytes()
                with self.assertRaises(SystemExit):
                    fixture.main()
                self.assertEqual(target.read_bytes(),original)
            with (mock.patch.object(fixture,"FOLDER",folder),
                  mock.patch.object(fixture,"FILE",target),
                  mock.patch("sys.argv",["generate_reference_gltf.py","--verify"])):
                self.assertEqual(fixture.main(),0)
            target.write_text('{"suspicious":true}')
            with (mock.patch.object(fixture,"FOLDER",folder),
                  mock.patch.object(fixture,"FILE",target),
                  mock.patch("sys.argv",["generate_reference_gltf.py","--verify"])):
                with self.assertRaises(ValueError):
                    fixture.main()

    def test_new_browser_scene_requires_pinned_asset_before_webgl_draw(self):
        scene=RENDERER.read_text()
        for required in (
            "const expectedAssetSha256='"+EXPECTED_SHA256+"'",
            "loadTrustedGltf()", "crypto.subtle.digest('SHA-256',raw)",
            "response", "asset-invalid", "vetted-gltf2-embedded-buffer",
            "gl.bufferData(gl.ARRAY_BUFFER,vettedGeometry,gl.STATIC_DRAW)",
            "webglcontextlost", "prefers-reduced-motion: reduce",
            "setFallback('no-webgl')", "1200000/area", "pagehide"
        ):
            if required=="response":
                self.assertIn("await asset.arrayBuffer()",scene)
            else:
                self.assertIn(required,scene)
        self.assertNotIn("function cube()",scene)
        for banned in ("https://", "XMLHttpRequest(", "WebSocket(",
                       "eval(", "import(", "model.generate(", "subprocess"):
            self.assertNotIn(banned,scene)
        page=ASSET_PAGE.read_text()
        self.assertIn('src="./gltf_scene.js"',page)
        self.assertIn('href="../reference/studio.css"',page)
        self.assertIn("not autonomous AI-generated design",page.lower())

    def test_real_browser_proof_requires_asset_and_tamper_case(self):
        script=BROWSER.read_text()
        for marker in ("127.0.0.1:19077","E6_GLTF2_REAL_CHROMIUM_3_SHOTS",
                       "E6_GLTF2_REAL_CHROMIUM_TAMPER_FAIL_CLOSED_PASS",
                       "asset-invalid","SOFTWARE_EMULATED_NOT_OWNER_GPU",
                       "modelGeneratedGeometry:false", "stage45Go:false"):
            self.assertIn(marker,script)
        self.assertIn("server.kill('SIGTERM')",script)


if __name__=="__main__":
    unittest.main()
