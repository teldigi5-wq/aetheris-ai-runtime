from __future__ import annotations

from urllib.parse import urlparse

BINANCE_FUTURES_TESTNET_REST_HOSTS = frozenset({"testnet.binancefuture.com"})
BINANCE_FUTURES_TESTNET_WS_HOSTS = frozenset({"fstream.binancefuture.com"})


def _validate_endpoint(
    raw_url: str,
    *,
    allowed_scheme: str,
    allowed_hosts: frozenset[str],
    label: str,
) -> str:
    value = (raw_url or "").strip().rstrip("/")
    parsed = urlparse(value)

    if parsed.scheme.lower() != allowed_scheme:
        raise ValueError(f"{label} must use {allowed_scheme}://")
    if parsed.username is not None or parsed.password is not None:
        raise ValueError(f"{label} must not contain URL credentials")
    if parsed.hostname is None or parsed.hostname.lower() not in allowed_hosts:
        raise ValueError(f"{label} host is not an approved Binance Futures testnet endpoint")
    if parsed.port not in (None, 443):
        raise ValueError(f"{label} must use the default TLS port")
    if parsed.path not in ("", "/") or parsed.params or parsed.query or parsed.fragment:
        raise ValueError(f"{label} must be an origin-only URL without path, query, or fragment")

    return value


def validate_binance_testnet_rest_base(raw_url: str) -> str:
    """Return a normalized REST base URL only for the Binance Futures testnet.

    The quant runtime has a separate live/public market-data base. Any client capable
    of signing account/order requests must stay on an explicitly approved testnet
    origin so operator configuration cannot silently turn testnet execution into
    live-money execution.
    """

    return _validate_endpoint(
        raw_url,
        allowed_scheme="https",
        allowed_hosts=BINANCE_FUTURES_TESTNET_REST_HOSTS,
        label="Binance testnet REST base",
    )


def validate_binance_testnet_ws_base(raw_url: str) -> str:
    return _validate_endpoint(
        raw_url,
        allowed_scheme="wss",
        allowed_hosts=BINANCE_FUTURES_TESTNET_WS_HOSTS,
        label="Binance testnet WebSocket base",
    )
