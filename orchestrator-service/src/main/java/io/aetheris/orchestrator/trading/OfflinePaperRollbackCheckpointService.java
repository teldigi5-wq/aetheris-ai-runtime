package io.aetheris.orchestrator.trading;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/**
 * Stage46.32: read-only database rollback detection against a separately
 * PRESENTED, synthetic checkpoint fixture.
 *
 * CAUTION: This code does not independently establish that a checkpoint came
 * from outside the database, is immutable, or was signed by a trusted owner.
 * The current CI key is PUBLIC. No network, owner identity, restoration,
 * paper-account mutation or exchange authorization is implemented.
 */
@Service
public final class OfflinePaperRollbackCheckpointService {
    private static final byte[] DOMAIN =
            "AETHERIS_STAGE46_32_PUBLIC_CHECKPOINT_TEST\0"
                    .getBytes(StandardCharsets.US_ASCII);
    private static final Set<String> FIELDS = Set.of(
            "schema_version", "scope", "account_id", "highest_sequence",
            "highest_digest", "issued_ms", "expires_ms", "decision");
    private static final List<String> ORDER = List.of(
            "schema_version", "scope", "account_id", "highest_sequence",
            "highest_digest", "issued_ms", "expires_ms", "decision");
    private static final long MAX_NUMBER = 1_000_000_000_000_000L;
    private static final long MAX_TTL = 86_400_000L;
    private final OfflinePaperReplayAnchorRepository anchors;
    private final TransactionTemplate tx;

    public record Review(
            boolean checkpointAuthenticatedWithSuppliedTestKey,
            boolean databaseMatchesPresentedCheckpoint,
            String classification, long pinnedSequence,
            boolean independentTrustRootVerified,
            boolean accountStateImported,
            boolean pythonQuantTestnetAuthorized,
            boolean liveMoneyAuthorized,
            boolean productionAuthorized,
            String decision) {}

    public OfflinePaperRollbackCheckpointService(
            OfflinePaperReplayAnchorRepository anchors,
            PlatformTransactionManager manager) {
        this.anchors = anchors;
        this.tx = new TransactionTemplate(manager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    private static boolean bounded(JsonNode value, boolean positive) {
        return value != null && value.isIntegralNumber() && value.canConvertToLong() &&
                value.longValue() <= MAX_NUMBER &&
                (positive ? value.longValue() > 0 : value.longValue() >= 0);
    }

    private static void validate(JsonNode checkpoint) {
        if (checkpoint == null || !checkpoint.isObject() || checkpoint.size() != FIELDS.size()) {
            throw new IllegalArgumentException("BAD_CHECKPOINT_SCHEMA");
        }
        var names = checkpoint.fieldNames();
        while (names.hasNext()) {
            if (!FIELDS.contains(names.next())) {
                throw new IllegalArgumentException("UNKNOWN_CHECKPOINT_FIELD");
            }
        }
        if (!bounded(checkpoint.path("schema_version"), true) ||
                checkpoint.path("schema_version").longValue() != 1 ||
                !checkpoint.path("scope").isTextual() ||
                !"OFFLINE_SYNTHETIC_ROLLBACK_CHECKPOINT_ONLY".equals(
                        checkpoint.path("scope").textValue()) ||
                !checkpoint.path("account_id").isTextual() ||
                !"paper-default".equals(checkpoint.path("account_id").textValue()) ||
                !bounded(checkpoint.path("highest_sequence"), true) ||
                !checkpoint.path("highest_digest").isTextual() ||
                !checkpoint.path("highest_digest").textValue().matches("[0-9a-f]{64}") ||
                !bounded(checkpoint.path("issued_ms"), true) ||
                !bounded(checkpoint.path("expires_ms"), true) ||
                !checkpoint.path("decision").isTextual() ||
                !"NO_GO".equals(checkpoint.path("decision").textValue())) {
            throw new IllegalArgumentException("BAD_CHECKPOINT_VALUES");
        }
        long start = checkpoint.path("issued_ms").longValue();
        long end = checkpoint.path("expires_ms").longValue();
        if (end <= start || end - start > MAX_TTL) {
            throw new IllegalArgumentException("BAD_CHECKPOINT_WINDOW");
        }
    }

    public static byte[] canonical(JsonNode checkpoint) {
        validate(checkpoint);
        StringBuilder builder = new StringBuilder();
        for (String key : ORDER) {
            JsonNode node = checkpoint.path(key);
            builder.append(key).append('=')
                    .append(node.isTextual() ? node.textValue() : node.asText())
                    .append('\n');
        }
        return builder.toString().getBytes(StandardCharsets.US_ASCII);
    }

    public static String fixtureMac(JsonNode checkpoint, byte[] key) {
        if (key == null || key.length < 32) {
            throw new IllegalArgumentException("NO_SIGNER_KEY");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.clone(), "HmacSHA256"));
            mac.update(DOMAIN);
            return HexFormat.of().formatHex(mac.doFinal(canonical(checkpoint)));
        } catch (Exception error) {
            throw new IllegalStateException("UNAVAILABLE_TEST_MAC", error);
        }
    }

    /**
     * Only check a DB anchor against a separately supplied offline receipt.
     * No state writes. A valid public fixture signature is NOT owner identity.
     */
    public Review compare(JsonNode receipt, String suppliedTag, byte[] testKey, long nowMs) {
        try {
            byte[] body = canonical(receipt);
            if (body.length > 1024 ||
                    testKey == null || testKey.length < 32 ||
                    suppliedTag == null || !suppliedTag.matches("[0-9a-f]{64}") ||
                    nowMs < 0 || nowMs > MAX_NUMBER ||
                    nowMs < receipt.path("issued_ms").longValue() ||
                    nowMs > receipt.path("expires_ms").longValue()) {
                return refuse("INVALID_OR_EXPIRED_CHECKPOINT", false, 0);
            }
            byte[] actualTag = HexFormat.of().parseHex(suppliedTag);
            byte[] expectedTag = HexFormat.of().parseHex(fixtureMac(receipt, testKey));
            if (!MessageDigest.isEqual(actualTag, expectedTag)) {
                return refuse("CHECKPOINT_TAG_MISMATCH", false, 0);
            }
            long pinnedSequence = receipt.path("highest_sequence").longValue();
            String pinnedDigest = receipt.path("highest_digest").textValue();
            Review result = tx.execute(status -> {
                var anchor = anchors.locked("paper-default");
                if (anchor.isEmpty()) {
                    return refuse("DB_ANCHOR_MISSING_OR_RESTORED", true, pinnedSequence);
                }
                OfflinePaperReplayAnchorEntity row = anchor.get();
                if (row.invalid()) {
                    return refuse("DB_ANCHOR_CORRUPTED", true, pinnedSequence);
                }
                if (row.getLastAcceptedSequence() < pinnedSequence) {
                    return refuse("STALE_DB_RESTORE_DETECTED", true, pinnedSequence);
                }
                if (row.getLastAcceptedSequence() > pinnedSequence) {
                    // Cannot prove ancestry from only the last SHA digest.
                    return refuse("UNANCHORED_DB_ADVANCE", true, pinnedSequence);
                }
                if (!row.getLastAcceptedDigest().equals(pinnedDigest)) {
                    return refuse("EQUAL_SEQUENCE_DIFFERENT_DIGEST", true, pinnedSequence);
                }
                return new Review(true, true, "FIXTURE_CHECKPOINT_MATCH",
                        pinnedSequence, false, false, false, false, false, "NO_GO");
            });
            return result == null ? refuse("MISSING_DB_TRANSACTION_RESULT", true, pinnedSequence)
                    : result;
        } catch (Exception error) {
            return refuse("ROLLBACK_REVIEW_FAILED_CLOSED", false, 0);
        }
    }

    private static Review refuse(String reason, boolean fixtureTagValid, long pinnedSequence) {
        return new Review(fixtureTagValid, false, reason,
                pinnedSequence, false, false, false, false, false, "NO_GO");
    }
}
