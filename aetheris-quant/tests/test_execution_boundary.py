import sys
import unittest
from pathlib import Path

QUANT_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(QUANT_ROOT))

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
