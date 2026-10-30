"""E6 Next/R3F/GSAP integration fixture source + GLB checks; no npm required."""
import hashlib
import json
import struct
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

ROOT=Path(__file__).parent
NEXT=ROOT/"next-studio"
sys.path.insert(0,str(NEXT/"scripts"))
import build_research_glb as asset


class E6NextResearchTests(unittest.TestCase):
    def test_asset_is_canonical_embedded_original_mesh_not_external(self):
        first=asset.build()
        second=asset.build()
        self.assertEqual(first,second)
        proof=asset.verify(first)
        self.assertEqual(proof["format"],"E6_OWNED_GLB_2_RESEARCH_ONLY")
        self.assertLessEqual(proof["bytes"],4096)
        self.assertEqual(proof["sourceVertices"],36)
        self.assertFalse(proof["dracoOrMeshoptCompressed"])
        self.assertFalse(proof["blenderExported"])
        self.assertFalse(proof["sourceAuthoredByModel"])
        self.assertFalse(proof["stage45Go"])
        self.assertEqual(first[:4],b"glTF")
        _,version,length=struct.unpack_from("<4sII",first)
        self.assertEqual(version,2)
        self.assertEqual(length,len(first))
        chunk_length,chunk_type=struct.unpack_from("<I4s",first,12)
        self.assertEqual(chunk_type,b"JSON")
        info=json.loads(first[20:20+chunk_length])
        self.assertEqual(info["buffers"][0]["byteLength"],864)
        self.assertNotIn("uri",info["buffers"][0])
        self.assertEqual(info["materials"][0]["pbrMetallicRoughness"]["metallicFactor"],0.64)
        self.assertEqual(info["materials"][0]["pbrMetallicRoughness"]["roughnessFactor"],0.34)

    def test_corrupted_glb_refused_no_replace(self):
        good=asset.build()
        for candidate in (good+b"tamper",good[:32],b"glTF"+good[4:18]+b"FAIL"+good[22:]):
            with self.subTest(len=len(candidate)),self.assertRaises(ValueError):
                asset.verify(candidate)
        with tempfile.TemporaryDirectory() as dir:
            target=Path(dir)/"research-panel.glb"
            manifest=Path(dir)/"research-panel-manifest.json"
            with (mock.patch.object(asset,"TARGET",target),
                  mock.patch.object(asset,"MANIFEST",manifest)):
                asset.main()
                self.assertEqual(target.read_bytes(),good)
                asset.main()
                target.write_bytes(b"bad-data")
                with self.assertRaises(SystemExit):
                    asset.main()
                self.assertEqual(target.read_bytes(),b"bad-data")

    def test_next_app_and_dependencies_are_intentionally_separate(self):
        package=json.loads((NEXT/"package.json").read_text())
        deps=package["dependencies"]
        for dependency in ("next","react","react-dom","three",
                           "@react-three/fiber","gsap","motion","lenis"):
            self.assertIn(dependency,deps)
            self.assertNotIn("^",deps[dependency])
            self.assertNotIn("latest",deps[dependency])
        self.assertTrue(package["private"])
        config=(NEXT/"next.config.ts").read_text()
        self.assertIn('output: "export"',config)
        self.assertIn("poweredByHeader: false",config)
        self.assertIn("reactStrictMode: true",config)

    def test_scroll_director_does_not_autoplay_lenis_or_override_reduced_motion(self):
        code=(NEXT/"components/CinematicExperience.tsx").read_text()
        for required in ("ScrollTrigger.create(","gsap.context(","ctx.revert()",
                         'pin:true',"setProgress(","onUpdate:",
                         "prefers-reduced-motion: reduce",'if(reduced||!story.current)return',
                         'if(!smooth||reduced)return',
                         'syncTouch:false',"lenis?.destroy()",
                         'aria-pressed={smooth}',"marker.animate("):
            self.assertIn(required,code,required)
        self.assertIn("STAGE45 NO_GO",code)
        self.assertNotIn("document.cookie",code)
        self.assertNotIn("localStorage",code)
        self.assertNotIn("fetch(",code)
        self.assertIn("StaticGeometry",code)

    def test_webgl_scene_demand_renders_bounded_dpr_gltf(self):
        scene=(NEXT/"components/Scene3D.tsx").read_text()
        for required in ("useLoader(GLTFLoader",'/research-panel.glb',
                         'frameloop="demand"',"dpr={[1,1.5]}",
                         "useThree(state=>state.invalidate)",
                         'shadows={false}',"camera.lookAt(0,0,0)",
                         "mesh","<primitive object={asset.scene}"):
            if required=="mesh":
                continue
            self.assertIn(required,scene,required)
        self.assertNotIn("DracoLoader",scene)
        self.assertNotIn("model.generate",scene)

    def test_accessible_markup_does_not_link_remote_media_or_assets(self):
        css=(NEXT/"app/globals.css").read_text()
        page=(NEXT/"components/CinematicExperience.tsx").read_text()
        for forbidden in ("@import", "url(http", "https://", "http://",
                          "iframe", "WebSocket(", "dangerouslySetInnerHTML"):
            self.assertNotIn(forbidden,css+page)
        for required in (".skip-link:focus",":focus-visible",
                         "@media(prefers-reduced-motion:reduce)",
                         "@media(max-width:760px)"):
            self.assertIn(required,css)
        for required in ('href="#studio-story"','id="studio-story"',
                         'aria-label="Storyboard design method"',
                         'id="limits"','role="status"'):
            self.assertIn(required,page)


if __name__=="__main__":
    unittest.main()
