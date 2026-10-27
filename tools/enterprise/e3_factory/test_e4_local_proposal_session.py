"""E4 actual opt-in Ollama advisory dispatch wrapper: fully mocked CI."""
import os
import unittest
from unittest import mock

import e36_free_model_router as e36
import e4_local_proposal_session as e4


def signed_off():
    return {
        e4.LOCAL_EXPERIMENT_OPT_IN: "yes",
        e36.OPT_LOCAL: "yes",
        e36.OPT_HIGH_RESOURCE: "no",
    }


def proposed(model="qwen2.5-coder:1.5b",task="scroll"):
    return {
        "provider":"ollama","requestedModel":model,"case":task,
        "responseWasExecuted":False,"gitHubWrites":False,
        "productionPublished":False,"ownerApprovalGranted":False,
        "externalSourceDisclosure":False,
        "responseClass":"ABSTAIN",
        "providerResponseRaw":'{"operation":"abstain"}',
    }


class E4LocalProposal(unittest.TestCase):
    def test_missing_opt_in_never_contacts_even_local_provider(self):
        for change in (
            {e4.LOCAL_EXPERIMENT_OPT_IN:"no",e36.OPT_LOCAL:"yes"},
            {e4.LOCAL_EXPERIMENT_OPT_IN:"yes",e36.OPT_LOCAL:"no"},
        ):
            with mock.patch.dict(os.environ,change),mock.patch.object(e36,"send") as send:
                with self.assertRaisesRegex(ValueError,"DOUBLE_OPT_IN"):
                    e4.LocalProposalSession().propose("qwen2.5-coder:1.5b","scroll")
                send.assert_not_called()

    def test_real_delegate_wiring_called_once_local_only(self):
        with (mock.patch.dict(os.environ,signed_off()),
              mock.patch.object(e36,"send",return_value=(
                  {"models":[{"name":"qwen2.5-coder:1.5b"}]},2)) as probe,
              mock.patch.object(e36,"request_advice",return_value=proposed()) as delegate):
            session=e4.LocalProposalSession()
            result=session.propose("qwen2.5-coder:1.5b","scroll")
            self.assertTrue(result["localOllamaDelegationPerformed"])
            self.assertFalse(result["proposalApplied"])
            self.assertFalse(result["ownerAuthorizationIndependentlyVerified"])
            self.assertFalse(result["releaseCertified"])
            self.assertFalse(result["stage45Go"])
            self.assertEqual(result["sessionAttemptsConsumed"],1)
            self.assertEqual(result["untrustedProposal"]["responseClass"],"ABSTAIN")
            probe.assert_called_once_with("ollama","GET","/api/tags")
            delegate.assert_called_once_with("ollama","qwen2.5-coder:1.5b","scroll")
            with self.assertRaisesRegex(ValueError,"BUDGET_EXHAUSTED"):
                session.propose("qwen2.5-coder:1.5b","scroll")
            self.assertEqual(delegate.call_count,1)

    def test_uninstalled_candidate_aborts_before_model_dispatch(self):
        with (mock.patch.dict(os.environ,signed_off()),
              mock.patch.object(e36,"send",return_value=({"models":[]},1)),
              mock.patch.object(e36,"request_advice") as delegate):
            session=e4.LocalProposalSession()
            with self.assertRaisesRegex(ValueError,"LOCAL_ROUTE_DENIED"):
                session.propose("qwen2.5-coder:1.5b","scroll")
            with self.assertRaisesRegex(ValueError,"BUDGET_EXHAUSTED"):
                session.propose("qwen2.5-coder:1.5b","scroll")
            delegate.assert_not_called()

    def test_high_resource_and_unknown_ids_refused_before_network(self):
        with (mock.patch.dict(os.environ,signed_off()),
              mock.patch.object(e36,"send") as send):
            for model in ("../secrets","artemis","openrouter/free"):
                with self.subTest(model=model),self.assertRaises(ValueError):
                    e4.LocalProposalSession().propose(model,"scroll")
            with self.assertRaisesRegex(ValueError,"HIGH_RESOURCE_SECOND_OPT_IN"):
                e4.LocalProposalSession().propose("qwen3-coder:30b","scroll")
            with self.assertRaisesRegex(ValueError,"UNAPPROVED_TASK"):
                e4.LocalProposalSession().propose("qwen2.5-coder:1.5b","deploy")
            send.assert_not_called()

    def test_untrusted_inventory_rejected_without_inference(self):
        with (mock.patch.dict(os.environ,signed_off()),
              mock.patch.object(e36,"send",return_value=({"models":[
                  {"name":42}]},1)),
              mock.patch.object(e36,"request_advice") as delegate):
            with self.assertRaisesRegex(ValueError,"INVENTORY_UNTRUSTED"):
                e4.LocalProposalSession().propose("qwen2.5-coder:1.5b","scroll")
            delegate.assert_not_called()

    def test_provider_result_cannot_claim_source_edit_or_release(self):
        forged={**proposed(),"gitHubWrites":True,"ownerApprovalGranted":True}
        with (mock.patch.dict(os.environ,signed_off()),
              mock.patch.object(e36,"send",return_value=(
                  {"models":[{"name":"qwen2.5-coder:1.5b"}]},1)),
              mock.patch.object(e36,"request_advice",return_value=forged)):
            with self.assertRaisesRegex(ValueError,"UNTRUSTED_PROVIDER_RESULT"):
                e4.LocalProposalSession().propose("qwen2.5-coder:1.5b","scroll")

    def test_provider_failure_still_consumes_one_request_attempt(self):
        with (mock.patch.dict(os.environ,signed_off()),
              mock.patch.object(e36,"send",side_effect=ValueError("NO_OLLAMA"))):
            s=e4.LocalProposalSession()
            with self.assertRaisesRegex(ValueError,"NO_OLLAMA"):
                s.propose("qwen2.5-coder:1.5b","scroll")
            with self.assertRaisesRegex(ValueError,"BUDGET_EXHAUSTED"):
                s.propose("qwen2.5-coder:1.5b","scroll")

if __name__=="__main__":
    unittest.main()
