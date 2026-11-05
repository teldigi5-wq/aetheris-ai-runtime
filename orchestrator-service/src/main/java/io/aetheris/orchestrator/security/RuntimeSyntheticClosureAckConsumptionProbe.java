package io.aetheris.orchestrator.security;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/**
 * REVIEW ONLY: conditional, one-shot receipt/challenge consumption.
 *
 * Uses disposable PG16 lab fixtures. The caller MUST provide independently
 * pinned expectations: neither a valid signed ACK nor a local receipt
 * establishes global leadership or remote old-writer fencing.
 *
 * The consumption table must be durable and access-controlled in any real
 * design. The tests use TEMP fixtures and roll back all rows; they prove
 * SQL uniqueness and transactional semantics, NOT restart durability.
 * No routes, Spring beans or production connectors are enabled.
 */
public final class RuntimeSyntheticClosureAckConsumptionProbe {
    private static final String REVIEW_CONSUME = """
        WITH eligible AS MATERIALIZED (
            SELECT c.challenge_id, r.receipt_nonce,
                   r.node_identity, r.fence_term
              FROM b62_pending_closure_challenges c
              JOIN b62_closure_receipts r
                ON r.resource_key=c.resource_key
               AND r.node_identity=c.expected_node
               AND r.fence_term=c.expected_term
               AND r.receipt_nonce=c.receipt_nonce
              JOIN b62_writer_gate g ON g.resource_key=r.resource_key
             WHERE c.challenge_id=?
               AND c.resource_key='runtime'
               AND c.expected_node=?
               AND c.expected_term=?
               AND c.receipt_nonce=?
               AND c.expires_at>clock_timestamp()
               AND g.state='CLOSED'
               AND NOT pg_is_in_recovery()
               AND current_setting('transaction_isolation')='read committed'
             FOR SHARE OF c,r,g
        ),
        consumed AS (
            INSERT INTO b62_consumed_closure_acks
                (challenge_id,receipt_nonce,node_identity,fence_term)
            SELECT challenge_id,receipt_nonce,node_identity,fence_term
              FROM eligible
            ON CONFLICT DO NOTHING
            RETURNING challenge_id
        )
        SELECT EXISTS (SELECT 1 FROM consumed)
        """;

    /**
     * A true value is an uncommitted, local one-time consumption only.
     * The caller must commit, and MUST roll back on SQLException.
     * An ACK from another node does not attest its peers were fenced.
     */
    public boolean consumeReview(Connection connection,
            RuntimePinnedClosureAckVerifier.VerifiedAck ack,
            String expectedNode, long expectedTerm,
            UUID expectedReceipt, UUID expectedChallenge) throws SQLException {
        if (connection == null || ack == null
                || expectedNode == null || expectedReceipt == null
                || expectedChallenge == null || expectedTerm <= 0
                || !ack.matches(expectedNode, expectedTerm,
                    expectedReceipt, expectedChallenge)) {
            return false;
        }
        if (connection.getAutoCommit() || connection.isReadOnly()
                || connection.getTransactionIsolation()
                    != Connection.TRANSACTION_READ_COMMITTED) {
            return false;
        }
        try (PreparedStatement stmt = connection.prepareStatement(REVIEW_CONSUME)) {
            stmt.setQueryTimeout(2);
            stmt.setObject(1, expectedChallenge);
            stmt.setString(2, expectedNode);
            stmt.setLong(3, expectedTerm);
            stmt.setObject(4, expectedReceipt);
            try (ResultSet row = stmt.executeQuery()) {
                return row.next() && row.getBoolean(1) && ack.isFresh();
            }
        }
    }
}
