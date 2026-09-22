package io.aetheris.orchestrator.stage13;

import io.aetheris.orchestrator.approval.ApprovalService;
import io.aetheris.orchestrator.approval.ApprovalStatus;
import io.aetheris.orchestrator.stage12.Stage12ExternalIntegrationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
public class Stage13TestnetExecutionJournalService {
    public static final String APPROVAL_ACTION = "STAGE13_TESTNET_ORDER";

    private final Stage13TestnetJournalRepository repository;
    private final Stage12ExternalIntegrationService external;
    private final ApprovalService approvals;

    public Stage13TestnetExecutionJournalService(Stage13TestnetJournalRepository repository,
                                                 Stage12ExternalIntegrationService external,
                                                 ApprovalService approvals) {
        this.repository = repository;
        this.external = external;
        this.approvals = approvals;
    }

    @Transactional
    public Stage13TestnetJournalEntity createIntent(TestnetIntentRequest request) {
        if (request == null || request.taskId() == null || request.approvalId() == null) {
            throw new IllegalArgumentException("taskId and approvalId are required");
        }
        String providerId = token(request.providerId(), "providerId", 80).toLowerCase(Locale.ROOT);
        var readiness = external.exchangeTestnet(providerId);
        if (!readiness.blockers().isEmpty()) throw new IllegalStateException("Exchange testnet provider is not ready: " + String.join("; ", readiness.blockers()));
        if (readiness.liveMoneyEnabled() || readiness.withdrawalOrTransferEnabled()) {
            throw new IllegalStateException("Stage 13 refuses exchange readiness that enables live money or transfers");
        }
        var approval = approvals.getRequired(request.approvalId());
        if (approval.getStatus() != ApprovalStatus.APPROVED) throw new IllegalStateException("Owner approval is not approved");
        if (!APPROVAL_ACTION.equals(approval.getActionType())) throw new IllegalArgumentException("Approval action type must be " + APPROVAL_ACTION);
        if (!request.taskId().equals(approval.getTaskId())) throw new IllegalArgumentException("Approval task does not match testnet intent task");

        String clientOrderId = token(request.clientOrderId(), "clientOrderId", 100);
        if (repository.findByClientOrderId(clientOrderId).isPresent()) throw new IllegalStateException("Duplicate testnet clientOrderId");
        String symbol = token(request.symbol(), "symbol", 32).toUpperCase(Locale.ROOT);
        String side = token(request.side(), "side", 8).toUpperCase(Locale.ROOT);
        if (!Set.of("BUY", "SELL").contains(side)) throw new IllegalArgumentException("side must be BUY or SELL");
        String orderType = token(request.orderType(), "orderType", 24).toUpperCase(Locale.ROOT);
        if (!Set.of("MARKET", "LIMIT", "STOP", "STOP_LIMIT").contains(orderType)) throw new IllegalArgumentException("unsupported testnet orderType");
        if (request.quantity() == null || request.quantity().signum() <= 0) throw new IllegalArgumentException("quantity must be positive");
        String risk = sha256(request.riskDecisionSha256(), "riskDecisionSha256");
        return repository.save(new Stage13TestnetJournalEntity(UUID.randomUUID(), request.taskId(), providerId,
                clientOrderId, symbol, side, orderType, request.quantity(), risk));
    }

    @Transactional
    public Stage13TestnetJournalEntity recordProviderEvidence(UUID id, TestnetProviderEvidence evidence) {
        Stage13TestnetJournalEntity journal = repository.findById(id).orElseThrow(() -> new NoSuchElementException("Unknown Stage 13 testnet journal entry"));
        if (evidence == null || !evidence.providerMeasured()) throw new IllegalArgumentException("Provider-measured testnet evidence is required");
        String status = token(evidence.status(), "status", 24).toUpperCase(Locale.ROOT);
        if (!Set.of("ACCEPTED", "PARTIALLY_FILLED", "FILLED", "REJECTED", "CANCELLED").contains(status)) {
            throw new IllegalArgumentException("Unsupported testnet provider status");
        }
        String attestation = sha256(evidence.attestationSha256(), "attestationSha256");
        journal.recordProviderEvidence(status, attestation);
        return repository.save(journal);
    }

    public List<Stage13TestnetJournalEntity> recent() { return repository.findTop100ByOrderByUpdatedAtDesc(); }

    private String token(String value, String label, int max) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required");
        String v = value.trim();
        if (v.length() > max || !v.matches("[A-Za-z0-9._:-]{1," + max + "}")) throw new IllegalArgumentException(label + " is invalid");
        return v;
    }

    private String sha256(String value, String label) {
        if (value == null || !value.trim().toLowerCase(Locale.ROOT).matches("[a-f0-9]{64}")) {
            throw new IllegalArgumentException(label + " must be a SHA-256 value");
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    public record TestnetIntentRequest(UUID taskId, UUID approvalId, String providerId, String clientOrderId,
                                       String symbol, String side, String orderType, BigDecimal quantity,
                                       String riskDecisionSha256) {}
    public record TestnetProviderEvidence(String status, boolean providerMeasured, String attestationSha256) {}
}
