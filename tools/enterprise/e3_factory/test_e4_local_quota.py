"""Real cross-process E4 attempt ledger tests. No local model calls."""
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest import mock

import e4_local_quota as quota


class LocalDailyQuotaTests(unittest.TestCase):
    def setUp(self):
        self._temp = tempfile.TemporaryDirectory()
        self.addCleanup(self._temp.cleanup)
        self.root = Path(self._temp.name)
        self.root.chmod(0o700)
        self._environment = mock.patch.dict(os.environ, {quota.ROOT_ENV: str(self.root)})
        self._environment.start()
        self.addCleanup(self._environment.stop)

    def test_three_real_disk_reservations_then_denied_no_reset(self):
        self.assertEqual(
            [quota.reserve_local_attempt() for _ in range(quota.MAX_DAILY_ATTEMPTS)],
            list(range(1, quota.MAX_DAILY_ATTEMPTS + 1)))
        with self.assertRaisesRegex(quota.QuotaExhausted, "EXHAUSTED"):
            quota.reserve_local_attempt()
        content = json.loads((self.root / quota.STATE_NAME).read_text())
        self.assertEqual(content["used"], quota.MAX_DAILY_ATTEMPTS)
        self.assertEqual(set(content), {"schema", "day", "used"})
        self.assertEqual(list(self.root.glob("*.json")), [self.root / quota.STATE_NAME])

    def test_clock_rollback_denies_and_next_day_resets(self):
        with mock.patch.object(quota, "_utc_day", side_effect=[
                "2026-11-08", "2026-11-09", "2026-11-08"]):
            self.assertEqual(quota.reserve_local_attempt(), 1)
            self.assertEqual(quota.reserve_local_attempt(), 1)
            with self.assertRaisesRegex(quota.QuotaUnavailable, "CLOCK_ROLLBACK"):
                quota.reserve_local_attempt()
        self.assertEqual(json.loads(
            (self.root / quota.STATE_NAME).read_text())["day"], "2026-11-09")

    def test_corrupt_existing_quota_file_is_denied_not_recreated(self):
        ledger = self.root / quota.STATE_NAME
        ledger.write_text('{"schema":1,"day":"2026-11-08","used":true}')
        ledger.chmod(0o600)
        with self.assertRaises(quota.QuotaUnavailable):
            quota.reserve_local_attempt()
        self.assertIn('"used":true', ledger.read_text())

    def test_missing_or_relative_operator_directory_never_reserves(self):
        for value in ("", "relative/path", "/not-a-real-e4-directory-20261108"):
            with self.subTest(value=value), mock.patch.dict(
                    os.environ, {quota.ROOT_ENV: value}):
                with self.assertRaises(quota.QuotaUnavailable):
                    quota.reserve_local_attempt()

    def test_untrusted_link_to_budget_is_rejected(self):
        alternate = self.root / "external-ledger"
        alternate.write_text('{"schema":1,"day":"2026-11-08","used":1}')
        link = self.root / quota.STATE_NAME
        try:
            link.symlink_to(alternate)
        except (OSError, NotImplementedError):
            self.skipTest("OS policy forbids symlink creation")
        with self.assertRaises(quota.QuotaUnavailable):
            quota.reserve_local_attempt()

    @unittest.skipUnless(os.name == "posix", "Unix permissions only")
    def test_group_writable_directory_refused(self):
        self.root.chmod(0o770)
        with self.assertRaisesRegex(quota.QuotaUnavailable, "NOT_PRIVATE"):
            quota.reserve_local_attempt()

    def test_cross_process_concurrent_reservations_never_exceed_three(self):
        runner = (
            "import e4_local_quota as q,sys; "
            "\ntry:\n q.reserve_local_attempt();print('RESERVED')"
            "\nexcept (q.QuotaExhausted,q.QuotaUnavailable): sys.exit(3)"
        )
        env = dict(os.environ)
        cwd = str(Path(quota.__file__).resolve().parent)
        spawned = [
            subprocess.Popen([sys.executable, "-c", runner], cwd=cwd, env=env,
                             stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
            for _ in range(7)
        ]
        results = [p.communicate(timeout=20) for p in spawned]
        codes = [p.returncode for p in spawned]
        self.assertEqual(codes.count(0), quota.MAX_DAILY_ATTEMPTS,
                         (codes, results))
        self.assertEqual(codes.count(3), len(spawned)-quota.MAX_DAILY_ATTEMPTS,
                         (codes, results))
        self.assertEqual(json.loads(
            (self.root / quota.STATE_NAME).read_text())["used"],
            quota.MAX_DAILY_ATTEMPTS)


if __name__ == "__main__":
    unittest.main()
