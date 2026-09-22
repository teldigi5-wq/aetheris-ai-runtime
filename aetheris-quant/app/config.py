from pydantic import BaseModel
from dotenv import load_dotenv
import os

load_dotenv()


def b(name, default="false"):
    return os.getenv(name, default).strip().lower() == "true"


class Settings(BaseModel):
    mode: str = os.getenv("AETHERIS_MODE", "PAPER").strip().upper()
    starting_balance: float = float(os.getenv("STARTING_BALANCE", "10000"))
    risk_per_trade: float = float(os.getenv("RISK_PER_TRADE", "0.005"))
    max_daily_loss: float = float(os.getenv("MAX_DAILY_LOSS", "0.02"))
    max_open_positions: int = int(os.getenv("MAX_OPEN_POSITIONS", "3"))
    max_leverage: int = int(os.getenv("MAX_LEVERAGE", "3"))
    min_setup_score: float = float(os.getenv("MIN_SETUP_SCORE", "72"))

    binance_fapi_base: str = os.getenv("BINANCE_FAPI_BASE", "https://fapi.binance.com").strip()
    binance_testnet_base: str = os.getenv("BINANCE_TESTNET_BASE", "https://testnet.binancefuture.com").strip()
    binance_testnet_ws_base: str = os.getenv("BINANCE_TESTNET_WS_BASE", "wss://fstream.binancefuture.com").strip().rstrip("/")
    binance_testnet_api_key: str = os.getenv("BINANCE_TESTNET_API_KEY", "").strip()
    binance_testnet_api_secret: str = os.getenv("BINANCE_TESTNET_API_SECRET", "").strip()
    enable_testnet_execution: bool = b("ENABLE_TESTNET_EXECUTION")
    binance_recv_window: int = int(os.getenv("BINANCE_RECV_WINDOW", "10000"))
    binance_http_retries: int = int(os.getenv("BINANCE_HTTP_RETRIES", "3"))
    binance_retry_base_seconds: float = float(os.getenv("BINANCE_RETRY_BASE_SECONDS", "0.5"))

    shadow_scan_seconds: int = int(os.getenv("SHADOW_SCAN_SECONDS", "300"))
    max_correlated_exposure: float = float(os.getenv("MAX_CORRELATED_EXPOSURE", "0.82"))

    # v1.0 autonomous TESTNET lifecycle
    enable_autonomous_testnet: bool = b("ENABLE_AUTONOMOUS_TESTNET")
    auto_start_autonomous: bool = b("AUTO_START_AUTONOMOUS", "false")
    auto_scan_seconds: int = int(os.getenv("AUTO_SCAN_SECONDS", "120"))
    auto_scan_markets: int = int(os.getenv("AUTO_SCAN_MARKETS", "12"))
    auto_min_score: float = float(os.getenv("AUTO_MIN_SCORE", "82"))
    auto_min_mtf_confidence: float = float(os.getenv("AUTO_MIN_MTF_CONFIDENCE", "72"))
    auto_max_spread_pct: float = float(os.getenv("AUTO_MAX_SPREAD_PCT", "0.08"))
    auto_risk_pct: float = float(os.getenv("AUTO_RISK_PCT", "0.0025"))
    auto_max_notional_pct: float = float(os.getenv("AUTO_MAX_NOTIONAL_PCT", "0.05"))
    auto_leverage: int = int(os.getenv("AUTO_LEVERAGE", "1"))
    auto_max_positions: int = int(os.getenv("AUTO_MAX_POSITIONS", "1"))
    auto_entry_cooldown_seconds: int = int(os.getenv("AUTO_ENTRY_COOLDOWN_SECONDS", "900"))
    auto_max_consecutive_errors: int = int(os.getenv("AUTO_MAX_CONSECUTIVE_ERRORS", "3"))

    enable_execution_stream: bool = b("ENABLE_EXECUTION_STREAM", "true")
    execution_reconcile_seconds: int = int(os.getenv("EXECUTION_RECONCILE_SECONDS", "30"))
    execution_keepalive_seconds: int = int(os.getenv("EXECUTION_KEEPALIVE_SECONDS", "2700"))

    # v0.9 active position manager (TESTNET only)
    enable_position_manager: bool = b("ENABLE_POSITION_MANAGER", "true")
    manager_cycle_seconds: int = int(os.getenv("MANAGER_CYCLE_SECONDS", "5"))
    manager_fallback_stop_pct: float = float(os.getenv("MANAGER_FALLBACK_STOP_PCT", "0.006"))
    manager_tp1_r: float = float(os.getenv("MANAGER_TP1_R", "1.0"))
    manager_tp2_r: float = float(os.getenv("MANAGER_TP2_R", "2.0"))
    manager_trail_start_r: float = float(os.getenv("MANAGER_TRAIL_START_R", "1.5"))
    manager_trail_distance_r: float = float(os.getenv("MANAGER_TRAIL_DISTANCE_R", "0.75"))
    manager_tp1_fraction: float = float(os.getenv("MANAGER_TP1_FRACTION", "0.40"))
    manager_tp2_fraction: float = float(os.getenv("MANAGER_TP2_FRACTION", "0.30"))
    manager_reversal_exit: bool = b("MANAGER_REVERSAL_EXIT", "true")
    manager_reversal_min_score: float = float(os.getenv("MANAGER_REVERSAL_MIN_SCORE", "82"))

    # v0.9.1 TESTNET validation mode. Never use these thresholds for production decisions.
    manager_validation_mode: bool = b("MANAGER_VALIDATION_MODE", "false")
    manager_validation_stop_pct: float = float(os.getenv("MANAGER_VALIDATION_STOP_PCT", "0.001"))
    manager_validation_tp1_r: float = float(os.getenv("MANAGER_VALIDATION_TP1_R", "0.05"))
    manager_validation_trail_start_r: float = float(os.getenv("MANAGER_VALIDATION_TRAIL_START_R", "0.08"))
    manager_validation_tp2_r: float = float(os.getenv("MANAGER_VALIDATION_TP2_R", "0.10"))
    manager_validation_trail_distance_r: float = float(os.getenv("MANAGER_VALIDATION_TRAIL_DISTANCE_R", "0.03"))


settings = Settings()
