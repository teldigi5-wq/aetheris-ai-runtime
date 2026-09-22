package io.aetheris.orchestrator.trading;import java.time.Duration;public interface MarketDataAdapter {String id();boolean available();MarketDataSnapshot latest(String symbol,Duration maxAge);}
