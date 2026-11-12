"""Hardware-planning policy tests that run without a GPU, network, or models."""
import unittest

from media_studio.hardware_planner import PlanningScenario, plan_scenario


class HardwarePlannerTests(unittest.TestCase):
    def test_no_device_is_detected_or_certified(self):
        plan = plan_scenario(PlanningScenario())
        self.assertEqual("UNKNOWN", plan["capacityBandHeuristic"])
        self.assertEqual("BLOCKED_PENDING_HARDWARE", plan["physicalPcStatus"])
        self.assertFalse(plan["physicalHardwareValidated"])
        self.assertFalse(plan["generationPermissionGranted"])
        self.assertFalse(plan["modelCompatibilityConfirmed"])
        self.assertFalse(plan["modelLicenseApproved"])
        self.assertIsNone(plan["hypotheticalTotalVramMiB"])

    def test_capacity_tiers_are_heuristics_only(self):
        scenarios = (
            (6144, "LOW_VRAM"),
            (8192, "MID_VRAM"),
            (12288, "MID_VRAM"),
            (16384, "HIGH_VRAM"),
            (24576, "EXTENDED_VRAM"),
        )
        for vram, expected in scenarios:
            with self.subTest(vram=vram):
                plan = plan_scenario(PlanningScenario(hypothetical_total_vram_mib=vram))
                self.assertEqual(expected, plan["capacityBandHeuristic"])
                self.assertEqual("USER_SUPPLIED_HYPOTHETICAL", plan["scenarioSource"])
                self.assertFalse(plan["generationPermissionGranted"])
                self.assertIn("MODEL_MEMORY_REQUIREMENTS_NOT_VERIFIED", plan["warnings"])

    def test_video_never_becomes_implemented_just_from_vram(self):
        for gib in (6, 12, 16, 24, 48):
            with self.subTest(gib=gib):
                plan = plan_scenario(PlanningScenario("video", gib * 1024))
                self.assertEqual("PLANNED_NOT_WIRED", plan["videoWorkflowState"])
                self.assertIn("VIDEO_BACKEND_NOT_IMPLEMENTED", plan["warnings"])
                self.assertFalse(plan["generationPermissionGranted"])

    def test_telemetry_not_inferred_from_total_vram(self):
        plan = plan_scenario(PlanningScenario("image", 16384))
        self.assertIsNone(plan["hypotheticalFreeVramMiB"])
        self.assertIn("FREE_VRAM_NOT_MEASURED", plan["warnings"])

    def test_user_supplied_free_vram_only_a_scenario(self):
        plan = plan_scenario(PlanningScenario("image", 6144, 1000))
        self.assertIn("LOW_FREE_VRAM_IN_HYPOTHETICAL_SCENARIO", plan["warnings"])
        self.assertFalse(plan["physicalHardwareValidated"])

    def test_invalid_models_and_impossible_inputs_fail_closed(self):
        bad = (
            PlanningScenario("audio"),
            PlanningScenario("video", -1),
            PlanningScenario("image", 1024, 1100),
            PlanningScenario("image", None, 100),
            PlanningScenario("image", True),
            PlanningScenario("image", 2048, False),
            PlanningScenario("image", 20_000_000),
            PlanningScenario("image", 0),
        )
        for scenario in bad:
            with self.subTest(scenario=scenario), self.assertRaises(ValueError):
                plan_scenario(scenario)

    def test_output_contract_is_deterministic(self):
        scenario = PlanningScenario("video", 24576, 18000)
        self.assertEqual(plan_scenario(scenario), plan_scenario(scenario))
        self.assertFalse(plan_scenario(scenario)["cloudFallbackAvailable"])
        self.assertFalse(plan_scenario(scenario)["paidProviderEnabled"])


if __name__ == "__main__":
    unittest.main()
