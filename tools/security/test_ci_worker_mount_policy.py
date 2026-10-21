"""Cross-platform source contract for the fixed CI-only Docker worker.

This is a negative/static policy test. The Linux CI job also performs a real
Docker run and inspects the actual resulting container.
"""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]
RUNNER = ROOT / "tools" / "enterprise" / "e3_factory" / "run_locked_worker.sh"


class CIWorkerMountPolicyTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.code = RUNNER.read_text(encoding="utf-8")

    def test_immutably_bound_existing_image(self):
        text = self.code
        self.assertIn("docker image inspect --format '{{.Id}}'", text)
        self.assertIn('"$image_id" python3', text)
        self.assertNotIn('"$image" python3', text)
        self.assertNotIn("docker pull", text)

    def test_mounts_scope_writes_to_own_output(self):
        code = self.code
        self.assertIn('src=$repo,dst=/workspace,readonly', code)
        self.assertIn('src=$repo/target/e3-factory,dst=/workspace/target/e3-factory', code)
        self.assertNotIn('src=$repo/target,dst=/workspace/target"', code)
        self.assertIn('mounts["/workspace/target/e3-factory"]["RW"] is True', code)
        self.assertIn('all(m["Destination"] in {"/workspace", "/workspace/target/e3-factory"}', code)

    def test_never_executes_root_or_networked_container(self):
        code = self.code
        for control in ('--network none', '--ipc none', '--cpus 1',
                        '--memory 256m --memory-swap 256m', '--pids-limit 64',
                        '--read-only --cap-drop ALL --security-opt no-new-privileges',
                        'E31_NONROOT_WORKER_REQUIRED', 'E31_WORKSPACE_SYMLINK_REJECTED',
                        '--tmpfs /tmp:rw,noexec,nosuid,size=16m',
                        'timeout --signal=TERM --kill-after=3s 45s'):
            self.assertIn(control, code)
        self.assertIn('host["Privileged"] is False', code)
        self.assertNotIn('--privileged', code)
        self.assertNotIn('docker.sock', code)
        self.assertNotIn('--network host', code)


if __name__ == "__main__":
    unittest.main()
