from __future__ import annotations

from datetime import datetime, timezone
from pathlib import Path
import tempfile
import unittest

from aetheris_reasoning import (
    ActionRoute,
    ChangeImpactAnalyzer,
    Claim,
    ConfidenceEngine,
    ContradictionDetector,
    DecisionLedger,
    DecisionRecord,
    MetaReasoningEngine,
    PolicyCompiler,
    RiskLevel,
    SimulationGate,
    SourceKind,
    SourceTrustEngine,
    TaskContext,
    TaskDependencyGraph,
    TemporalIntelligence,
    VerifierCritic,
)


class ReasoningStage30Tests(unittest.TestCase):
    def test_high_risk_side_effect_routes_to_simulation(self) -> None:
        decision = MetaReasoningEngine().route(
            TaskContext(
                task_id="deploy-1",
                description="Change production deployment",
                risk=RiskLevel.HIGH,
                uncertainty=0.2,
                has_side_effects=True,
                complexity=6,
            )
        )
        self.assertEqual(ActionRoute.SIMULATION, decision.route)
        self.assertTrue(decision.verification_required)

    def test_critical_task_requires_owner_approval(self) -> None:
        decision = MetaReasoningEngine().route(
            TaskContext(
                task_id="critical-1",
                description="Irreversible privileged action",
                risk=RiskLevel.CRITICAL,
                has_side_effects=True,
                reversible=False,
            )
        )
        self.assertEqual(ActionRoute.OWNER_APPROVAL, decision.route)

    def test_policy_compiler_detects_conflict(self) -> None:
        compiler = PolicyCompiler()
        blocked = compiler.compile("never push to github").rule
        self.assertIsNotNone(blocked)
        result = compiler.compile("always push to github", existing=(blocked,))
        self.assertEqual((blocked.rule_id,), result.conflicts)

    def test_dependency_graph_orders_tasks_and_detects_cycle(self) -> None:
        graph = TaskDependencyGraph()
        graph.add_task("test", ("build",))
        graph.add_task("build", ("lint",))
        graph.add_task("lint")
        self.assertEqual(["lint", "build", "test"], graph.topological_order())

        cyclic = TaskDependencyGraph()
        cyclic.add_task("a", ("b",))
        cyclic.add_task("b", ("a",))
        with self.assertRaises(ValueError):
            cyclic.topological_order()

    def test_contradictions_are_surfaced(self) -> None:
        detector = ContradictionDetector()
        contradictions = detector.detect(
            [
                Claim("service", "status", "healthy", "monitor-a"),
                Claim("service", "status", "down", "monitor-b"),
            ]
        )
        self.assertEqual(1, len(contradictions))

    def test_source_trust_requires_cross_check_for_model_inference(self) -> None:
        result = SourceTrustEngine().assess(SourceKind.MODEL_INFERENCE)
        self.assertTrue(result.requires_cross_check)
        self.assertLess(result.trust, 0.75)

    def test_temporal_validity(self) -> None:
        claim = Claim(
            "provider",
            "free-tier",
            "available",
            "official",
            valid_from="2026-01-01T00:00:00Z",
            valid_until="2026-12-31T23:59:59Z",
        )
        moment = datetime(2026, 9, 14, tzinfo=timezone.utc)
        self.assertTrue(TemporalIntelligence.is_valid_at(claim, moment))

    def test_change_impact_marks_workflow_changes_for_review_and_rollback(self) -> None:
        report = ChangeImpactAnalyzer().analyze(
            [".github/workflows/ci.yml", "aetheris-reasoning/aetheris_reasoning/router.py"]
        )
        self.assertIn("reasoning-tests", report.required_checks)
        self.assertIn("workflow-review", report.required_checks)
        self.assertTrue(report.rollback_required)

    def test_verifier_requires_recovery_for_irreversible_action(self) -> None:
        task = TaskContext(
            task_id="dangerous-change",
            description="irreversible change",
            risk=RiskLevel.HIGH,
            has_side_effects=True,
            reversible=False,
        )
        findings = VerifierCritic().review(task, ["apply change", "verify result"])
        codes = {finding.code for finding in findings}
        self.assertIn("MISSING_RECOVERY", codes)

    def test_simulation_gate(self) -> None:
        result = SimulationGate().evaluate(
            TaskContext(
                task_id="deploy",
                description="high risk deploy",
                risk=RiskLevel.HIGH,
                has_side_effects=True,
            )
        )
        self.assertTrue(result.required)

    def test_decision_ledger_hash_chain_detects_tampering(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "ledger.jsonl"
            ledger = DecisionLedger(path)
            ledger.append(
                DecisionRecord(
                    decision_id="d-1",
                    task_id="t-1",
                    decision="simulate first",
                    route=ActionRoute.SIMULATION,
                    confidence=0.73,
                )
            )
            ledger.append(
                DecisionRecord(
                    decision_id="d-2",
                    task_id="t-1",
                    decision="request approval",
                    route=ActionRoute.OWNER_APPROVAL,
                    confidence=0.81,
                )
            )
            self.assertTrue(ledger.verify())

            text = path.read_text(encoding="utf-8")
            path.write_text(text.replace("simulate first", "skip simulation", 1), encoding="utf-8")
            self.assertFalse(ledger.verify())


if __name__ == "__main__":
    unittest.main()
