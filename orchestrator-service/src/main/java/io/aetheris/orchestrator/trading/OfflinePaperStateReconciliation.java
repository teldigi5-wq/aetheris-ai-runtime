package io.aetheris.orchestrator.trading;

import com.fasterxml.jackson.databind.JsonNode;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Stage46.30: INERT, offline, strictly synthetic reconciliation envelope.
 *
 * NO @Service, HTTP endpoint, broker, Java account write, owner authentication
 * or testnet order bridge. Runtime identity, durable anti-replay persistence,
 * real key distribution and cross-process transport remain NOT implemented.
 */
public final class OfflinePaperStateReconciliation {
    private static final byte[] DOMAIN =
            "AETHERIS_STAGE46_30_INERT_PAPER_MAC\0".getBytes(StandardCharsets.US_ASCII);
    private static final String PREFIX = "AETHERIS_STAGE46_30_PAPER_STATE_V1\n";
    private static final String ZERO_DIGEST = "0".repeat(64);
    private static final Pattern HEX = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9-]{0,39}");
    private static final Pattern SYMBOL = Pattern.compile("[A-Z0-9]{3,20}");
    private static final Set<String> FIELDS = Set.of(
            "schema_version", "origin", "account_id", "currency", "sequence",
            "issued_ms", "expires_ms", "previous_digest", "cash_minor",
            "equity_minor", "daily_realized_minor", "halted", "positions");
    private static final Set<String> POSITION_FIELDS = Set.of(
            "id", "symbol", "side", "entry_minor", "stop_minor",
            "quantity_microunits", "notional_minor");
    private static final List<String> ORDERED_FIELDS = List.of(
            "schema_version", "origin", "account_id", "currency", "sequence",
            "issued_ms", "expires_ms", "previous_digest", "cash_minor",
            "equity_minor", "daily_realized_minor", "halted");

    private OfflinePaperStateReconciliation() {}

    public record Review(boolean accepted, String reason, long sequence,
                         boolean testOnly, boolean stateApplied,
                         boolean pythonQuantTestnetAuthorized, boolean liveMoneyAuthorized,
                         boolean productionAuthorized, String decision) {}

    private static boolean fields(JsonNode node, Set<String> names) {
        if (node == null || !node.isObject() || node.size() != names.size()) return false;
        var it = node.fieldNames();
        while (it.hasNext()) if (!names.contains(it.next())) return false;
        return true;
    }

    private static boolean integer(JsonNode value, boolean positive) {
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()) return false;
        long n = value.longValue();
        return (positive ? n > 0 : n >= 0) && n <= 1_000_000_000_000_000L;
    }

    private static long num(JsonNode node, String key) { return node.path(key).longValue(); }
    private static boolean exactly(JsonNode node, String key, String expected) {
        return node.path(key).isTextual() && expected.equals(node.path(key).textValue());
    }

    public static byte[] canonical(JsonNode state) {
        if (!fields(state, FIELDS)
                || !integer(state.path("schema_version"), true)
                || num(state, "schema_version") != 1
                || !exactly(state, "origin", "JAVA_PAPER_SYNTHETIC_ONLY")
                || !exactly(state, "account_id", "paper-default")
                || !exactly(state, "currency", "USD")
                || !integer(state.path("sequence"), true)
                || !integer(state.path("issued_ms"), true)
                || !integer(state.path("expires_ms"), true)
                || !state.path("previous_digest").isTextual()
                || !HEX.matcher(state.path("previous_digest").textValue()).matches()
                || !integer(state.path("cash_minor"), true)
                || !integer(state.path("equity_minor"), true)
                || !state.path("daily_realized_minor").isIntegralNumber()
                || !state.path("daily_realized_minor").canConvertToLong()
                || state.path("daily_realized_minor").longValue() < -1_000_000_000_000_000L
                || state.path("daily_realized_minor").longValue() > 1_000_000_000_000_000L
                || !state.path("halted").isBoolean()
                || !state.path("positions").isArray()
                || state.path("positions").size() > 16) {
            throw new IllegalArgumentException("BAD_STATE_SCHEMA_OR_VALUES");
        }
        long issued = num(state, "issued_ms"), expires = num(state, "expires_ms");
        if (expires <= issued || expires - issued > 120_000) {
            throw new IllegalArgumentException("BAD_VALIDITY_WINDOW");
        }
        var parts = new ArrayList<String>();
        parts.add(PREFIX);
        for (String name : ORDERED_FIELDS) {
            JsonNode value = state.path(name);
            String field = value.isTextual() ? value.textValue()
                    : value.isBoolean() ? Boolean.toString(value.booleanValue()) : value.asText();
            parts.add(name + "=" + field + "\n");
        }
        JsonNode positions = state.path("positions");
        parts.add("positions=" + positions.size() + "\n");
        String previous = "";
        for (JsonNode p : positions) {
            if (!fields(p, POSITION_FIELDS)) throw new IllegalArgumentException("BAD_POSITION_SCHEMA");
            JsonNode ident = p.path("id"), symbol = p.path("symbol"), side = p.path("side");
            if (!ident.isTextual() || !ID.matcher(ident.textValue()).matches()
                    || ident.textValue().compareTo(previous) <= 0
                    || !symbol.isTextual() || !SYMBOL.matcher(symbol.textValue()).matches()
                    || !side.isTextual() ||
                    !(side.textValue().equals("LONG") || side.textValue().equals("SHORT"))) {
                throw new IllegalArgumentException("BAD_POSITION_IDENTITY");
            }
            previous = ident.textValue();
            for (String k : List.of("entry_minor", "stop_minor", "quantity_microunits", "notional_minor")) {
                if (!integer(p.path(k), true)) throw new IllegalArgumentException("BAD_POSITION_NUMBER");
            }
            long entry = num(p, "entry_minor"), stop = num(p, "stop_minor");
            long qty = num(p, "quantity_microunits"), notional = num(p, "notional_minor");
            if (side.textValue().equals("LONG") && stop >= entry
                    || side.textValue().equals("SHORT") && stop <= entry
                    || !BigInteger.valueOf(entry).multiply(BigInteger.valueOf(qty))
                    .equals(BigInteger.valueOf(notional).multiply(BigInteger.valueOf(1_000_000)))) {
                throw new IllegalArgumentException("BAD_STOP_OR_NOTIONAL");
            }
            parts.add("position=" + previous + "|" + symbol.textValue() + "|" +
                    side.textValue() + "|" + entry + "|" + stop + "|" + qty + "|" + notional + "\n");
        }
        byte[] body = String.join("", parts).getBytes(StandardCharsets.US_ASCII);
        if (body.length > 4096) throw new IllegalArgumentException("TOO_MANY_STATE_BYTES");
        return body;
    }

    public static String digest(JsonNode state) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical(state)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA256_UNAVAILABLE", ex);
        }
    }

    public static String mac(JsonNode state, byte[] key) {
        if (key == null || key.length < 32) throw new IllegalArgumentException("MISSING_KEY");
        try {
            Mac h = Mac.getInstance("HmacSHA256");
            h.init(new SecretKeySpec(key.clone(), "HmacSHA256"));
            h.update(DOMAIN);
            return HexFormat.of().formatHex(h.doFinal(canonical(state)));
        } catch (Exception ex) {
            throw new IllegalStateException("HMAC_UNAVAILABLE", ex);
        }
    }

    public static final class OfflineVerifier {
        private final byte[] key;
        private long sequence;
        private String lastDigest = ZERO_DIGEST;

        public OfflineVerifier(byte[] key) {
            if (key == null || key.length < 32) throw new IllegalArgumentException("MISSING_KEY");
            this.key = key.clone();
        }

        public synchronized Review inspect(JsonNode snapshot, String signedTag, long nowMs) {
            try {
                byte[] body = canonical(snapshot);
                if (nowMs < 0 || nowMs > 1_000_000_000_000_000L
                        || nowMs < num(snapshot, "issued_ms")
                        || nowMs > num(snapshot, "expires_ms")) {
                    throw new IllegalArgumentException("STALE_OR_FUTURE");
                }
                if (signedTag == null || !HEX.matcher(signedTag).matches()) {
                    throw new IllegalArgumentException("INVALID_MAC_FORMAT");
                }
                byte[] supplied = HexFormat.of().parseHex(signedTag);
                byte[] expected = HexFormat.of().parseHex(mac(snapshot, key));
                if (!MessageDigest.isEqual(supplied, expected)) {
                    throw new IllegalArgumentException("UNAUTHENTICATED");
                }
                if (num(snapshot, "sequence") != sequence + 1) {
                    throw new IllegalArgumentException("REPLAY_OR_GAP");
                }
                if (!snapshot.path("previous_digest").textValue().equals(lastDigest)) {
                    throw new IllegalArgumentException("CHAIN_DISCONTINUITY");
                }
                lastDigest = HexFormat.of().formatHex(
                        MessageDigest.getInstance("SHA-256").digest(body));
                sequence++;
                return review(true, "SYNTHETIC_STATE_ONLY");
            } catch (Exception ex) {
                return review(false, "FAILED_CLOSED_SYNTHETIC_STATE");
            }
        }

        private Review review(boolean accepted, String reason) {
            return new Review(accepted, reason, sequence, true,
                    false, false, false, false, "NO_GO");
        }
    }
}
