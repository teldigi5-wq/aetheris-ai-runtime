import sys
import types
import unittest
from pathlib import Path

QUANT_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(QUANT_ROOT))

# The constructor under test does not perform HTTP. Keep this unit test dependency-free
# so the required runtime CI job can prove the execution boundary without network/package
# installation. Production still imports the real httpx dependency from the locked env.
if "httpx" not in sys.modules:
    httpx_stub = types.ModuleType("httpx")
    httpx_stub.RequestError = RuntimeError
    httpx_stub.AsyncClient = object
    sys.modules["httpx"] = httpx_stub

from app.binance_testnet import BinanceTestnetClient  # noqa: E402
from app.execution_boundary import (  # noqa: E402
    validate_binance_testnet_rest_base,
    validate_binance_testnet_ws_base,
)


class BinanceTestnetExecutionBoundaryTests(unittest.TestCase):
    def test_default_futures_testnet_rest_origin_is_allowed(self):
        self.assertEqual(
            validate_binance_testnet_rest_base("https://testnet.binancefuture.com/"),
            "https://testnet.binancefuture.com",
        )

    def test_live_binance_futures_rest_origin_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "approved Binance Futures testnet"):
            validate_binance_testnet_rest_base("https://fapi.binance.com")

    def test_signed_client_rejects_live_origin_before_any_request(self):
        with self.assertRaisesRegex(ValueError, "approved Binance Futures testnet"):
            BinanceTestnetClient("live-key", "live-secret", "https://fapi.binance.com", enabled=True)

    def test_signed_client_accepts_only_approved_testnet_origin(self):
        client = BinanceTestnetClient(
            "test-key",
            "test-secret",
            "https://testnet.binancefuture.com/",
            enabled=True,
        )
        self.assertEqual(client.base_url, "https://testnet.binancefuture.com")

    def test_testnet_lookalike_domain_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "approved Binance Futures testnet"):
            validate_binance_testnet_rest_base("https://testnet.binancefuture.com.attacker.example")

    def test_http_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "https://"):
            validate_binance_testnet_rest_base("http://testnet.binancefuture.com")

    def test_origin_with_credentials_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "must not contain URL credentials"):
            validate_binance_testnet_rest_base("https://user:pass@testnet.binancefuture.com")

    def test_origin_with_path_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "origin-only"):
            validate_binance_testnet_rest_base("https://testnet.binancefuture.com/fapi")

    def test_default_testnet_websocket_origin_is_allowed(self):
        self.assertEqual(
            validate_binance_testnet_ws_base("wss://fstream.binancefuture.com/"),
            "wss://fstream.binancefuture.com",
        )

    def test_live_or_unapproved_websocket_origin_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "approved Binance Futures testnet"):
            validate_binance_testnet_ws_base("wss://fstream.binance.com")


if __name__ == "__main__":
    unittest.main()
