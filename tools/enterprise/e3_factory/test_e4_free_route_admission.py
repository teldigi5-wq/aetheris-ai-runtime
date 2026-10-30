"""E4: no paid fallback, unverified-model activation or forged owner approval."""
import copy
import unittest
from unittest import mock

import e4_free_route_admission as e4
import e36_free_model_router as old


def request(**changes):
    default={
        "task":"scroll","provider":"ollama","modelId":"qwen2.5-coder:1.5b",
        "externalInferenceConsent":False,"sourceDisclosureConsent":False,
        "highResourceConsent":False,"maxCostUsd":"0",
    }
    default.update(changes)
    return default


def observed(**changes):
    default={"installedLocalModelIds":["qwen2.5-coder:1.5b"],
             "cloudModelDetail":None,"catalogFetchedUnixSeconds":None}
    default.update(changes)
    return default


class E4RouteAdmission(unittest.TestCase):
    def test_local_plan_is_advisory_only_not_an_execution(self):
        d=e4.advisory(request(),observed(),2000)
        self.assertEqual(d["decision"],"CANDIDATE_FOR_SEPARATE_RUNTIME_VERIFICATION")
        self.assertFalse(d["inferenceExecuted"])
        self.assertFalse(d["modelDownloaded"])
        self.assertFalse(d["ownerApprovalVerified"])
        self.assertEqual(d["networkRequestsPerformed"],0)
        self.assertFalse(d["highResourceFitMeasured"])

    def test_unknown_missing_paid_and_high_resource_models_refused(self):
        for model in ("artemis","../../../env","qwen2.5:72b"):
            self.assertEqual(e4.advisory(request(modelId=model),observed(),2000)["decision"],
                             "DENIED_NO_FALLBACK")
        self.assertEqual(e4.advisory(request(),observed(installedLocalModelIds=[]),2000)["reason"],
                         "LOCAL_MODEL_NOT_INSTALLED_IN_CLAIMED_SNAPSHOT")
        heavy="qwen3-coder:30b"
        d=e4.advisory(request(modelId=heavy),
            observed(installedLocalModelIds=[heavy]),2000)
        self.assertEqual(d["reason"],"HIGH_RESOURCE_SECOND_OPT_IN_REQUIRED")
        good=e4.advisory(request(modelId=heavy,highResourceConsent=True),
            observed(installedLocalModelIds=[heavy]),2000)
        self.assertEqual(good["decision"],"CANDIDATE_FOR_SEPARATE_RUNTIME_VERIFICATION")
        self.assertFalse(good["highResourceFitMeasured"])

    def test_remote_always_requires_both_declarations_and_fresh_zero_price_claim(self):
        model=old.CLOUD_CANDIDATES[1]
        price={"data":{"id":model,"pricing":{
            "prompt":"0","completion":"0","request":"0.000"}}}
        q=request(provider="openrouter",modelId=model,
                  externalInferenceConsent=True,sourceDisclosureConsent=True)
        e=observed(installedLocalModelIds=[],
                   cloudModelDetail=price,catalogFetchedUnixSeconds=1930)
        d=e4.advisory(q,e,2000)
        self.assertEqual(d["decision"],"CANDIDATE_REQUIRES_INDEPENDENT_LIVE_ZERO_PRICE_CHECK")
        self.assertFalse(d["liveProviderProbePerformed"])
        self.assertFalse(d["ownerApprovalVerified"])
        for k in ("externalInferenceConsent","sourceDisclosureConsent"):
            altered=dict(q)
            altered[k]=False
            self.assertEqual(e4.advisory(altered,e,2000)["decision"],"DENIED_NO_FALLBACK")
        self.assertEqual(e4.advisory(q,{**e,"catalogFetchedUnixSeconds":1800},2000)["reason"],
                         "CLOUD_CATALOG_MISSING_OR_STALE")
        self.assertEqual(e4.advisory(q,{**e,"catalogFetchedUnixSeconds":2100},2000)["reason"],
                         "CLOUD_CATALOG_MISSING_OR_STALE")
        expensive=copy.deepcopy(e)
        expensive["cloudModelDetail"]["data"]["pricing"]["request"]="0.001"
        self.assertEqual(e4.advisory(q,expensive,2000)["reason"],
                         "CLOUD_PRICE_NOT_PROVEN_ZERO")

    def test_bad_types_and_extra_privileges_are_denied(self):
        for value in (True,1.3,"NaN","0.01","-1",{},None):
            self.assertEqual(e4.advisory(request(maxCostUsd=value),observed(),2000)["decision"],
                             "DENIED_NO_FALLBACK")
        for variant in (
            {**request(),"tools":["exec"]},
            {**request(),"task":"production-deploy"},
            {**request(),"externalInferenceConsent":"yes"},
            {**request(),"provider":"https://evil.invalid"},
        ):
            self.assertEqual(e4.advisory(variant,observed(),2000)["decision"],
                             "DENIED_NO_FALLBACK")
        self.assertEqual(e4.advisory(request(),{**observed(),"approval":True},2000)["reason"],
                         "UNTRUSTED_OR_INCOMPLETE_SCHEMA")
        self.assertEqual(e4.advisory(request(),observed(),True)["reason"],
                         "UNTRUSTED_OR_INCOMPLETE_SCHEMA")

    def test_website_copy_is_a_separate_local_only_task_not_remotely_admitted(self):
        local = e4.advisory(request(task="website-copy"), observed(), 2000)
        self.assertEqual(local["decision"], "CANDIDATE_FOR_SEPARATE_RUNTIME_VERIFICATION")
        self.assertFalse(local["inferenceExecuted"])
        self.assertFalse(local["ownerApprovalVerified"])
        remote = e4.advisory(request(task="website-copy",provider="openrouter",
            modelId=old.CLOUD_CANDIDATES[1],
            externalInferenceConsent=True,sourceDisclosureConsent=True),
            observed(installedLocalModelIds=[],
                cloudModelDetail={"data":{"id":old.CLOUD_CANDIDATES[1],
                     "pricing":{"prompt":"0","completion":"0","request":"0"}}},
                catalogFetchedUnixSeconds=1999), 2000)
        self.assertEqual(remote["decision"], "DENIED_NO_FALLBACK")
        self.assertEqual(remote["reason"], "WEBSITE_COPY_LOCAL_ONLY")

    def test_website_layout_is_separately_local_only_even_when_cloud_claims_zero_price(self):
        local = e4.advisory(request(task="website-layout"), observed(), 2000)
        self.assertEqual(local["decision"], "CANDIDATE_FOR_SEPARATE_RUNTIME_VERIFICATION")
        self.assertFalse(local["inferenceExecuted"])
        remote = e4.advisory(request(task="website-layout",
            provider="openrouter", modelId=old.CLOUD_CANDIDATES[1],
            externalInferenceConsent=True, sourceDisclosureConsent=True),
            observed(installedLocalModelIds=[],
                cloudModelDetail={"data":{"id":old.CLOUD_CANDIDATES[1],
                    "pricing":{"prompt":"0","completion":"0","request":"0"}}},
                catalogFetchedUnixSeconds=1999), 2000)
        self.assertEqual(remote["decision"], "DENIED_NO_FALLBACK")
        self.assertEqual(remote["reason"], "WEBSITE_LAYOUT_LOCAL_ONLY")

    def test_admission_is_pure_no_network_or_local_provider_execution(self):
        with mock.patch.object(old,"send",side_effect=AssertionError("NETWORK_CALL")) as fn:
            a=e4.advisory(request(),observed(),2000)
            self.assertFalse(a["inferenceExecuted"])
            self.assertEqual(fn.call_count,0)
            self.assertFalse(a["stage45Go"])

if __name__=="__main__":
    unittest.main()
