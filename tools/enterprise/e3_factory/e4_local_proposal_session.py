#!/usr/bin/env python3
"""E4 opt-in, single-attempt LOCAL Ollama proposal session.

A callable development feature, not an agent/owner auth mechanism. Never
permits cloud inference, model pull, source edits, deployment or paid fallback.
Only the bounded E3.6 local advisory path can be invoked.
"""
from __future__ import annotations
import os
import threading
import time
from typing import Any

import e36_free_model_router as e36
import e4_free_route_admission as e4

LOCAL_EXPERIMENT_OPT_IN = "AETHERIS_E4_EXPLICIT_LOCAL_SESSION_OPT_IN"


class LocalProposalSession:
    """Maximum ONE dispatch attempt per instance, even on error/replay.

    Process-local bookkeeping is not a durable quota or remote caller
    authentication. The actual Ollama installed-model probe is repeated by
    the E3.6 delegate before inference, with no cloud fallback.
    """

    def __init__(self) -> None:
        self._lock = threading.Lock()
        self._attempted = False

    def propose(self, model_id: str, task: str) -> dict[str, Any]:
        if type(model_id) is not str or type(task) is not str:
            raise ValueError("E4_LOCAL_INVALID_INPUT")
        # Never contact localhost on mere module import or during selection.
        if os.environ.get(LOCAL_EXPERIMENT_OPT_IN) != "yes" or os.environ.get(e36.OPT_LOCAL) != "yes":
            raise ValueError("E4_LOCAL_EXPLICIT_DOUBLE_OPT_IN_REQUIRED")
        e36.checked_choice("ollama", model_id)
        if task not in e4.ALLOWED_TASKS or task not in e36.CATEGORIES:
            raise ValueError("E4_LOCAL_UNAPPROVED_TASK")
        high = model_id in e36.HIGH_RESOURCE_LOCAL_CANDIDATES
        if high and os.environ.get(e36.OPT_HIGH_RESOURCE) != "yes":
            raise ValueError("E4_LOCAL_HIGH_RESOURCE_SECOND_OPT_IN_REQUIRED")
        with self._lock:
            if self._attempted:
                raise ValueError("E4_LOCAL_SESSION_BUDGET_EXHAUSTED")
            self._attempted = True  # Reserve BEFORE network or model attempt.
        tags, _ = e36.send("ollama", "GET", "/api/tags")
        models = tags.get("models")
        if (type(models) is not list or len(models) > 64 or
            any(type(item) is not dict or type(item.get("name")) is not str
                for item in models)):
            raise ValueError("E4_LOCAL_MODEL_INVENTORY_UNTRUSTED")
        found = [item["name"] for item in models]
        plan = e4.advisory(
            {
                "task": task, "provider": "ollama", "modelId": model_id,
                "externalInferenceConsent": False,
                "sourceDisclosureConsent": False,
                "highResourceConsent": high,
                "maxCostUsd": "0",
            },
            {
                "installedLocalModelIds": found,
                "cloudModelDetail": None,
                "catalogFetchedUnixSeconds": None,
            },
            int(time.time()),
        )
        if plan["decision"] != "CANDIDATE_FOR_SEPARATE_RUNTIME_VERIFICATION":
            raise ValueError("E4_LOCAL_ROUTE_DENIED_" + str(plan["reason"]))
        # This existing delegate rechecks true installation and only talks to
        # localhost:11434. No developer/customer source is sent to cloud.
        result = e36.request_advice("ollama", model_id, task)
        if (type(result) is not dict
            or result.get("provider") != "ollama"
            or result.get("requestedModel") != model_id
            or result.get("case") != task
            or result.get("responseWasExecuted") is not False
            or result.get("gitHubWrites") is not False
            or result.get("productionPublished") is not False
            or result.get("ownerApprovalGranted") is not False
            or result.get("externalSourceDisclosure") is not False):
            raise ValueError("E4_LOCAL_UNTRUSTED_PROVIDER_RESULT_DENIED")
        return {
            "evidenceClass": "E4_EXPLICIT_LOCAL_SINGLE_ATTEMPT_UNTRUSTED_PROPOSAL",
            "modelId": model_id,
            "task": task,
            "sessionAttemptBudget": 1,
            "sessionAttemptsConsumed": 1,
            "localOllamaDelegationPerformed": True,
            "remoteProviderFallbackPermitted": False,
            "modelDownloadsPermitted": False,
            "proposalApplied": False,
            "arbitraryToolsPermitted": False,
            "ownerAuthorizationIndependentlyVerified": False,
            "releaseCertified": False,
            "stage45Go": False,
            "untrustedProposal": result,
        }
