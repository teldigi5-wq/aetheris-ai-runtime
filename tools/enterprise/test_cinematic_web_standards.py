"""Regression for owner-defined cinematic website advisory quality and zero privilege.

Tests guidance consistency with the existing governed reference catalog;
does NOT imply a generated site, actual GPU measurement or owner approval.
"""
from __future__ import annotations

import json
import unittest
from pathlib import Path

import e39g2_scoped_skill_advice as reference
import e39g3_validate_java_skill_manifest as manifest
import skills_registry

ROOT = Path(__file__).resolve().parents[2]
SKILLS = ROOT / "skills" / "native"
COMPANY = ROOT / "orchestrator-service/src/main/resources/enterprise/organization-blueprint-v1.json"
DOCS = ROOT / "docs/runtime/syntra-aetheris-cinematic-website-standards-20261025.md"
POLICY = ROOT / "orchestrator-service/src/main/resources/enterprise/skill-advice-bindings-v1.json"

REQUIRED = {
    "aetheris-web-experience": (
        "Brand-led", "GSAP", "ScrollTrigger", "Three.js", "Blender",
        "Lenis", "prefers-reduced-motion", "wireframes", "customer",
        "keyboard", "1920",
    ),
    "aetheris-web-accessibility": (
        "WCAG 2.2 AA", "320", "1920", "prefers-reduced-motion",
        "keyboard", "Playwright", "WebGL", "native",
    ),
    "aetheris-web-performance": (
        "60 fps", "LCP <= 2.5s", "INP <= 200ms", "CLS <= 0.1",
        "GSAP", "ScrollTrigger", "WebGL", "Lenis", "DPR",
    ),
    "aetheris-security-audit": (
        "authorization", "CSP", "XSS", "SSRF", "OWASP",
        "asset", "secrets", "approval",
    ),
}


class CinematicWebGuidanceTests(unittest.TestCase):
    def test_existing_governed_skill_registry_still_valid_and_nonexecuting(self):
        entries = skills_registry.load_registry()
        self.assertEqual(len(entries), 14)
        self.assertEqual(sum(x["trust"] == "third-party-pinned-reference" for x in entries), 6)
        self.assertEqual(sum(x["trust"] == "first-party-advisory" for x in entries), 8)
        self.assertTrue(all(x["autoActivate"] is False and x["capabilities"] == [] for x in entries))
        self.assertTrue(set(REQUIRED).issubset({x["name"] for x in entries}))

    def test_first_party_guidance_is_reviewable_and_scope_limited(self):
        for name, required in REQUIRED.items():
            with self.subTest(name=name):
                path = SKILLS / name / "SKILL.md"
                self.assertFalse(path.is_symlink())
                text = path.read_text(encoding="utf-8")
                self.assertLess(len(text.encode("utf-8")), 12000)
                self.assertIn("name: " + name, text)
                for phrase in required:
                    self.assertIn(phrase, text)
                self.assertTrue(any(word in text.lower() for word in ("read-only", "reference only")))
                self.assertIn("approval", text.lower())

    def test_existing_roles_and_cross_language_manifest_are_unchanged(self):
        roles = reference.frozen_roles()
        self.assertEqual(len(roles), 26)
        validated = manifest.validate()
        self.assertEqual(validated["roles"], 7)
        self.assertEqual(validated["skills"], 14)
        obj = json.loads(POLICY.read_text(), object_pairs_hook=skills_registry.no_duplicate_keys)
        self.assertIs(obj["enabledByDefault"], False)
        expected = {
            "frontend-engineer": {"aetheris-web-experience", "aetheris-web-accessibility", "aetheris-web-performance"},
            "qa-engineer": {"aetheris-web-accessibility"},
            "software-architect": {"aetheris-web-experience"},
            "appsec-officer": {"aetheris-security-audit"},
        }
        for role, skills in expected.items():
            self.assertTrue(skills.issubset(obj["roles"][role]), role)

    def test_reference_selector_is_default_denied_and_explicitly_advisory(self):
        with self.assertRaisesRegex(ValueError, "E39G2_DEFAULT_OFF"):
            reference.advisory_reference("frontend-engineer", "aetheris-web-experience",
                                          "Review a brand-led cinematic website")
        result = reference.advisory_reference(
            "frontend-engineer", "aetheris-web-experience",
            "Review a brand-led cinematic website",
            explicit_request=True, enabled=True,
        )
        self.assertTrue(result["readOnly"])
        self.assertFalse(result["automaticActivation"])
        self.assertFalse(result["approvalGranted"])
        self.assertFalse(result["stage45Go"])
        self.assertEqual(result["modelCalls"], 0)
        self.assertEqual(result["toolCalls"], 0)
        self.assertEqual(result["skillId"], "aetheris-web-experience")

    def test_company_blueprint_stays_nonexecuting(self):
        blueprint = json.loads(COMPANY.read_text(encoding="utf-8"), object_pairs_hook=skills_registry.no_duplicate_keys)
        self.assertEqual(blueprint["targetCompanyCount"], 13)
        self.assertEqual(blueprint["targetDivisionCount"], 100)
        self.assertEqual(len(blueprint["companies"]), 13)
        self.assertEqual(sum(len(c["divisions"]) for c in blueprint["companies"]), 100)
        self.assertEqual(blueprint["evidenceClass"], "PROPOSED_TAXONOMY_NON_EXECUTABLE")

    def test_owner_requirements_recorded_without_unearned_certification(self):
        text = DOCS.read_text(encoding="utf-8")
        for marker in ("Explain Smooth Website Technology", "Aetheris Creative",
                       "Aetheris Immersive", "60 fps", "Web Vitals", "WCAG 2.2 AA",
                       "preview-first", "owner", "NO_GO", "no agent registration"):
            self.assertIn(marker.casefold(), text.casefold())
        self.assertIn("NOT an automatic website builder", text)


if __name__ == "__main__":
    unittest.main()
