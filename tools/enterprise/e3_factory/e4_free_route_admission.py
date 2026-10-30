#!/usr/bin/env python3
"""E4 opt-in zero-cost ROUTE ADMISSION advisory — never executes models.

Consumes untrusted declarative observations; cannot authorize model downloads,
cloud inference, code disclosure, local tool execution, or owner permissions.
Actual live evidence and owner authentication MUST be obtained separately.
"""
from __future__ import annotations
from decimal import Decimal, InvalidOperation
from typing import Any

import e36_free_model_router as baseline

ALLOWED_TASKS=frozenset(("scroll","mobile","reduced","website-copy"))
REQ_KEYS=frozenset(("task","provider","modelId","externalInferenceConsent",
                   "sourceDisclosureConsent","highResourceConsent","maxCostUsd"))
EVIDENCE_KEYS=frozenset(("installedLocalModelIds","cloudModelDetail",
                        "catalogFetchedUnixSeconds"))
MAX_CATALOG_AGE_SEC=180


def deny(reason:str)->dict[str,Any]:
    return {
        "decision":"DENIED_NO_FALLBACK",
        "reason":reason,
        "routeAdvisoryOnly":True,
        "inferenceExecuted":False,
        "externalCodeTransferred":False,
        "networkRequestsPerformed":0,
        "modelDownloaded":False,
        "ownerApprovalVerified":False,
        "productionEnabled":False,
        "stage45Go":False,
    }


def advisory(request: object, evidence: object, now_unix_seconds: int)->dict[str,Any]:
    if (type(request) is not dict or set(request)!=REQ_KEYS
        or type(evidence) is not dict or set(evidence)!=EVIDENCE_KEYS
        or type(now_unix_seconds) is not int or now_unix_seconds<=0):
        return deny("UNTRUSTED_OR_INCOMPLETE_SCHEMA")
    if type(request["task"]) is not str or request["task"] not in ALLOWED_TASKS:
        return deny("UNSUPPORTED_TASK")
    if (type(request["provider"]) is not str
        or type(request["modelId"]) is not str):
        return deny("INVALID_MODEL_SELECTOR")
    if any(type(request[k]) is not bool for k in (
        "externalInferenceConsent","sourceDisclosureConsent","highResourceConsent")):
        return deny("INVALID_CONSENT_DECLARATION")
    if type(request["maxCostUsd"]) not in (int,str):
        return deny("INVALID_COST_BOUND")
    try:
        max_cost=Decimal(str(request["maxCostUsd"]))
        if not max_cost.is_finite() or max_cost != 0:
            return deny("ZERO_MANDATORY_COST_ONLY")
    except (InvalidOperation,ValueError):
        return deny("INVALID_COST_BOUND")
    provider=request["provider"]
    model=request["modelId"]
    try:
        baseline.checked_choice(provider,model)
    except ValueError:
        return deny("UNRECOGNIZED_OR_PAID_ROUTE")
    installed=evidence["installedLocalModelIds"]
    if (type(installed) is not list or len(installed)>64
        or any(type(m) is not str or len(m)>120 for m in installed)
        or len(installed)!=len(set(installed))):
        return deny("UNTRUSTED_INSTALLED_MODEL_LIST")
    if provider=="ollama":
        if request["externalInferenceConsent"] or request["sourceDisclosureConsent"]:
            # Explicit local path cannot silently turn cloud/disclosure on.
            return deny("LOCAL_ROUTE_CONSENT_SCOPE_CONFLICT")
        if model not in installed:
            return deny("LOCAL_MODEL_NOT_INSTALLED_IN_CLAIMED_SNAPSHOT")
        if (model in baseline.HIGH_RESOURCE_LOCAL_CANDIDATES
            and not request["highResourceConsent"]):
            return deny("HIGH_RESOURCE_SECOND_OPT_IN_REQUIRED")
        if evidence["cloudModelDetail"] is not None or evidence["catalogFetchedUnixSeconds"] is not None:
            return deny("LOCAL_ROUTE_EXTRA_REMOTE_EVIDENCE")
        return {
            **deny("RUNTIME_VERIFICATION_AND_OWNER_AUTH_REQUIRED"),
            "decision":"CANDIDATE_FOR_SEPARATE_RUNTIME_VERIFICATION",
            "provider":"ollama","modelId":model,
            "installedOnlyCallerClaim":True,
            "highResourceFitMeasured":False,
        }

    if request["task"]=="website-copy":
        # Copy-trial capability is deliberately restricted to installed local
        # models. Remote pricing/consent advisory is NOT copy-disclosure approval.
        return deny("WEBSITE_COPY_LOCAL_ONLY")
    if provider!="openrouter":
        return deny("UNKNOWN_PROVIDER")
    if not request["externalInferenceConsent"] or not request["sourceDisclosureConsent"]:
        return deny("EXPLICIT_REMOTE_AND_SOURCE_DISCLOSURE_DECLARATIONS_REQUIRED")
    if request["highResourceConsent"]:
        return deny("UNRELATED_LOCAL_RESOURCE_CONSENT_IN_REMOTE_ROUTE")
    fetched=evidence["catalogFetchedUnixSeconds"]
    if (type(fetched) is not int
        or not 0<=now_unix_seconds-fetched<=MAX_CATALOG_AGE_SEC):
        return deny("CLOUD_CATALOG_MISSING_OR_STALE")
    catalog=evidence["cloudModelDetail"]
    if type(catalog) is not dict:
        return deny("CLOUD_CATALOG_UNTRUSTED")
    try:
        baseline.validated_free_entry(catalog,model)
    except (ValueError,TypeError,KeyError):
        return deny("CLOUD_PRICE_NOT_PROVEN_ZERO")
    return {
        **deny("INDEPENDENT_LIVE_CATALOG_AND_OWNER_AUTH_REQUIRED"),
        "decision":"CANDIDATE_REQUIRES_INDEPENDENT_LIVE_ZERO_PRICE_CHECK",
        "provider":"openrouter","modelId":model,
        "callerSuppliedCatalogPriceClaimZero":True,
        "liveProviderProbePerformed":False,
        "consentDeclarationsAreNotAuthenticatedOwnerApproval":True,
    }
