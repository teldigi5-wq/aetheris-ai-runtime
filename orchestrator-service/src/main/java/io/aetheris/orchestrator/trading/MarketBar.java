package io.aetheris.orchestrator.trading;import java.time.Instant;public record MarketBar(Instant time,double open,double high,double low,double close,double volume){}
