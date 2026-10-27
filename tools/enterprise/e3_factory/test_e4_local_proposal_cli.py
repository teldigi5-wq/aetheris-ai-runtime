"""CLI operator boundaries: dry-run default, no model or cloud call in CI."""
import contextlib
import io
import json
import os
import subprocess
import sys
import unittest
from pathlib import Path
from unittest import mock

import e36_free_model_router as e36
import e4_local_proposal_cli as cli
import e4_local_proposal_session as local


def run_cli(*argv):
    out=io.StringIO()
    with contextlib.redirect_stdout(out):
        status=cli.cli(list(argv))
    return status,json.loads(out.getvalue())


class LocalCliTests(unittest.TestCase):
    def test_plan_only_without_network_or_environment(self):
        with (mock.patch.object(e36,"send",side_effect=AssertionError("NETWORK")),
              mock.patch.object(cli,"LocalProposalSession",
                  side_effect=AssertionError("MODEL"))):
            code,decision=run_cli("--model","qwen2.5-coder:1.5b","--task","scroll")
        self.assertEqual(code,0)
        self.assertEqual(decision["status"],"PLAN_ONLY_NO_INFERENCE")
        self.assertFalse(decision["installedModelVerified"])
        self.assertFalse(decision["localInferenceExecuted"])
        self.assertFalse(decision["cloudFallbackPermitted"])

    def test_bad_model_denied_without_model_request(self):
        with mock.patch.object(e36,"send",side_effect=AssertionError("NETWORK")):
            code,result=run_cli("--model","attacker.invalid:latest","--task","mobile")
        self.assertEqual(code,2)
        self.assertEqual(result["status"],"DENIED_UNRECOGNIZED_MODEL")

    def test_execution_needs_double_local_opt_in(self):
        with (mock.patch.dict(os.environ,{
                cli.LOCAL_EXPERIMENT_OPT_IN:"no",
                e36.OPT_LOCAL:"yes"},clear=False),
              mock.patch.object(cli,"LocalProposalSession",
                  side_effect=AssertionError("CONTACT"))):
            code,result=run_cli("--model","qwen2.5-coder:1.5b",
                                "--task","reduced","--run-local")
        self.assertEqual(code,2)
        self.assertEqual(result["status"],"DENIED_EXPLICIT_DOUBLE_LOCAL_OPT_IN_REQUIRED")

    def test_explicit_single_attempt_dispatch_is_never_an_owner_approval(self):
        payload={
            "evidenceClass":"E4_EXPLICIT_LOCAL_SINGLE_ATTEMPT_UNTRUSTED_PROPOSAL",
            "sessionAttemptsConsumed":1,"proposalApplied":False,
            "ownerAuthorizationIndependentlyVerified":False,
            "stage45Go":False,
        }
        with (mock.patch.dict(os.environ,{
                cli.LOCAL_EXPERIMENT_OPT_IN:"yes",
                e36.OPT_LOCAL:"yes"}),
              mock.patch.object(cli,"LocalProposalSession") as session):
            session.return_value.propose.return_value=payload
            code,result=run_cli("--model","qwen2.5-coder:1.5b",
                                "--task","scroll","--run-local")
            session.return_value.propose.assert_called_once_with(
                "qwen2.5-coder:1.5b","scroll")
        self.assertEqual(code,0)
        self.assertEqual(result["status"],
                         "UNTRUSTED_LOCAL_PROPOSAL_REQUIRES_INDEPENDENT_REVIEW")
        self.assertFalse(result["sourceTreeWritten"])
        self.assertFalse(result["cloudFallbackPermitted"])
        self.assertFalse(result["ownerApprovalGranted"])
        self.assertFalse(result["stage45Go"])

    def test_failed_provider_reply_never_leaks_exception_or_retries(self):
        with (mock.patch.dict(os.environ,{
                cli.LOCAL_EXPERIMENT_OPT_IN:"yes",
                e36.OPT_LOCAL:"yes"}),
              mock.patch.object(cli,"LocalProposalSession") as session):
            session.return_value.propose.side_effect=ValueError(
                "secret-user-machine-path MUST NOT LEAK")
            code,result=run_cli("--model","qwen2.5-coder:1.5b",
                                "--task","scroll","--run-local")
            session.return_value.propose.assert_called_once()
        self.assertEqual(code,2)
        self.assertNotIn("secret-user-machine-path",str(result))
        self.assertFalse(result["ownerApprovalGranted"])

    def test_real_cli_process_dry_run_no_server_needed(self):
        p=Path(cli.__file__)
        env=os.environ.copy()
        env.pop(cli.LOCAL_EXPERIMENT_OPT_IN,None)
        env.pop(e36.OPT_LOCAL,None)
        run=subprocess.run([sys.executable,str(p),"--model",
                            "qwen2.5-coder:1.5b","--task","scroll"],
                            env=env,capture_output=True,text=True,timeout=8)
        self.assertEqual(run.returncode,0,run.stderr)
        result=json.loads(run.stdout)
        self.assertEqual(result["status"],"PLAN_ONLY_NO_INFERENCE")
        self.assertFalse(result["localInferenceExecuted"])

if __name__=="__main__":
    unittest.main()
