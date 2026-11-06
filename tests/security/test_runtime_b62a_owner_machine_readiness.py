"""No-side-effect S-B62-A owner-machine capability discovery contract tests."""
import contextlib
import io
import json
import sys
from pathlib import Path
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "scripts/security"))
import runtime_b62a_owner_machine_readiness as review


class OwnerReadiness(unittest.TestCase):
    def test_no_tools_installed_remains_no_go(self):
        output = review.readiness(lambda _: None)
        self.assertTrue(all(value is False for value in output["tools_present"].values()))
        self.assertEqual(output["stage"], "S-B62-A_IN_PROGRESS")
        self.assertEqual(output["release_decision"], "STAGE45_NO_GO")

    def test_all_tools_installed_does_not_claim_independent_fencing(self):
        output = review.readiness(lambda name: "/REDACTED/" + name)
        self.assertTrue(all(output["tools_present"].values()))
        for name in ("deployed_runtime_image_verified", "independent_host_domains_verified",
                     "independent_admin_domains_verified", "physical_old_primary_fence_verified",
                     "privileged_epoch_rollback_denied_verified",
                     "independent_security_review_approved"):
            self.assertIs(output[name], False, name)
        self.assertEqual(output["xerj_publication"], "HOLD")
        self.assertEqual(output["release_decision"], "STAGE45_NO_GO")

    def test_output_has_only_allowlisted_booleans_not_command_paths(self):
        secret = "/private/user/hostname/env/docker"
        output = review.readiness(lambda name: secret if name == "docker" else None)
        encoded = json.dumps(output)
        self.assertNotIn(secret, encoded)
        self.assertTrue(output["tools_present"]["docker"])
        self.assertFalse(output["tools_present"]["psql"])
        self.assertEqual(set(output["tools_present"]), set(review.TOOLS))

    def test_default_cli_returns_one_even_with_all_commands_present(self):
        with patch.object(review.shutil, "which", return_value="/fake/path"), \
                patch("sys.argv", ["readiness"]), \
                contextlib.redirect_stdout(io.StringIO()) as stream:
            status = review.main()
        payload = json.loads(stream.getvalue())
        self.assertEqual(status, 1)
        self.assertEqual(payload["release_decision"], "STAGE45_NO_GO")
        self.assertFalse(payload["independent_security_review_approved"])
        self.assertNotIn("/fake/path", stream.getvalue())

    def test_extra_arguments_fail_closed_without_echo(self):
        with patch("sys.argv", ["readiness", "SECRET-NEVER-ECHO"]), \
                contextlib.redirect_stdout(io.StringIO()) as stream:
            status = review.main()
        self.assertEqual(status, 2)
        self.assertNotIn("SECRET-NEVER-ECHO", stream.getvalue())
        self.assertEqual(json.loads(stream.getvalue())["release_decision"], "STAGE45_NO_GO")

    def test_script_imports_no_network_or_command_execution_library(self):
        text = (Path(__file__).resolve().parents[2] /
                "scripts/security/runtime_b62a_owner_machine_readiness.py").read_text(
                    encoding="utf-8")
        for bad in ("import subprocess", "from subprocess", "import socket",
                    "from socket", "os.system(", "os.popen(", "eval(", "exec("):
            self.assertNotIn(bad, text, bad)


if __name__ == "__main__":
    unittest.main()
