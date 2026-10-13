"""Search of extension research metadata never enables tools or network calls."""
import json
import unittest

from scripts.search_extension_catalog import search_sources


class OfflineSearchTest(unittest.TestCase):
    def test_all_projects_discoverable_without_activating_any(self):
        result = search_sources(limit=25)
        self.assertEqual(14, result["totalMatched"])
        self.assertFalse(result["networkUsed"])
        self.assertFalse(result["autoInstallation"])
        self.assertFalse(result["claimOfAvailability"])
        self.assertTrue(result["requiresOwnerApprovalAndIndependentReview"])
        self.assertEqual("BLOCKED_PENDING_HARDWARE", result["physicalPcStatus"])
        for source in result["results"]:
            self.assertFalse(source["installed"])
            self.assertFalse(source["executionEnabled"])
            self.assertEqual("REFERENCE_ONLY", source["status"])
            self.assertEqual("NOT_REVIEWED", source["licenseReview"])

    def test_category_filter_only_returns_sourced_candidates(self):
        result = search_sources(category="local-memory", limit=25)
        self.assertEqual(2, result["totalMatched"])
        self.assertEqual({"qdrant", "chroma"}, {x["id"] for x in result["results"]})

    def test_search_is_case_insensitive(self):
        x = search_sources(query="PLAYWRIGHT", limit=25)
        self.assertEqual(["playwright-mcp"], [s["id"] for s in x["results"]])

    def test_unknown_query_is_empty_not_network_discovery(self):
        x = search_sources(query="definitely-not-a-source", limit=25)
        self.assertEqual(0, x["totalMatched"])
        self.assertFalse(x["networkUsed"])

    def test_fair_code_is_not_relabelled_open_source(self):
        x = search_sources(query="n8n", limit=25)
        self.assertEqual("NOASSERTION", x["results"][0]["reportedLicense"])
        self.assertEqual("NOT_REVIEWED", x["results"][0]["licenseReview"])

    def test_invalid_limit_and_types_rejected(self):
        for limit in (0, 26, 10000, True, "3"):
            with self.subTest(limit=limit), self.assertRaises(ValueError):
                search_sources(limit=limit)
        with self.assertRaises(ValueError):
            search_sources(query="x" * 121)


if __name__ == "__main__":
    unittest.main()
