package io.aetheris.orchestrator.connector;

import io.aetheris.orchestrator.executive.*;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class ConnectorIntegrationService {
    private final ConnectorConnectionRepository connections;
    private final ConnectorEventReceiptRepository receipts;
    private final ExecutiveAgentService executive;

    public ConnectorIntegrationService(ConnectorConnectionRepository connections,
                                       ConnectorEventReceiptRepository receipts,
                                       ExecutiveAgentService executive) {
        this.connections = connections;
        this.receipts = receipts;
        this.executive = executive;
    }

    @Transactional
    public ConnectorConnectionView register(RegisterConnectorRequest request) {
        if (request == null) throw new IllegalArgumentException("Connector registration is required");
        String ownerId = text(request.ownerId(), "");
        if (ownerId.isBlank()) throw new IllegalArgumentException("ownerId is required");
        if (request.provider() == null) throw new IllegalArgumentException("provider is required");
        String externalAccountRef = text(request.externalAccountRef(), "");
        if (externalAccountRef.isBlank()) throw new IllegalArgumentException("externalAccountRef is required");
        if (externalAccountRef.length() > 240) throw new IllegalArgumentException("externalAccountRef is too long");
        Set<ConnectorCapability> capabilities = request.capabilities() == null
                ? Set.of() : Set.copyOf(request.capabilities());
        if (capabilities.isEmpty()) throw new IllegalArgumentException("At least one connector capability is required");

        var existing = connections.findByProviderAndExternalAccountRef(request.provider(), externalAccountRef);
        if (existing.isPresent()) {
            if (!existing.get().getOwnerId().equals(ownerId)) {
                throw new IllegalStateException("Connector account is already registered to another owner");
            }
            return view(existing.get());
        }

        String displayName = text(request.displayName(), request.provider().name() + " connector");
        ConnectorConnectionEntity saved = connections.save(new ConnectorConnectionEntity(
                UUID.randomUUID(), ownerId, request.provider(), externalAccountRef, displayName, capabilities));
        return view(saved);
    }

    @Transactional
    public ConnectorConnectionView updateStatus(UUID id, UpdateConnectorStatusRequest request) {
        if (request == null || request.status() == null) {
            throw new IllegalArgumentException("Connector status is required");
        }
        ConnectorConnectionEntity connection = connection(id);
        connection.updateStatus(request.status());
        return view(connections.save(connection));
    }

    public List<ConnectorConnectionView> recentConnections() {
        return connections.findTop100ByOrderByUpdatedAtDesc().stream().map(this::view).toList();
    }

    public List<ConnectorEventReceiptEntity> recentReceipts() {
        return receipts.findTop100ByOrderByReceivedAtDesc();
    }

    @Transactional
    public ConnectorIngestResult ingest(ConnectorInboundEvent event) {
        if (event == null) throw new IllegalArgumentException("Connector event is required");
        if (event.connectionId() == null) throw new IllegalArgumentException("connectionId is required");
        String externalEventId = text(event.externalEventId(), "");
        if (externalEventId.isBlank()) throw new IllegalArgumentException("externalEventId is required");
        if (externalEventId.length() > 240) throw new IllegalArgumentException("externalEventId is too long");
        if (event.type() == null) throw new IllegalArgumentException("Connector event type is required");

        ConnectorEventReceiptEntity duplicate = receipts
                .findByConnectionIdAndExternalEventId(event.connectionId(), externalEventId)
                .orElse(null);
        if (duplicate != null) return result(duplicate, true);

        ConnectorConnectionEntity connection = connection(event.connectionId());
        if (connection.getStatus() != ConnectorStatus.ENABLED) {
            throw new IllegalStateException("Connector must be ENABLED before ingesting events");
        }

        ConnectorCapability required = requiredCapability(event.type());
        if (!connection.supports(required)) {
            throw new IllegalStateException("Connector lacks required capability: " + required);
        }

        boolean messageSignal = switch (event.type()) {
            case EMAIL, DM, FAQ, PRODUCT_INTEREST -> true;
            default -> false;
        };
        boolean effectiveTrustedLowRisk = messageSignal && event.trustedLowRisk() && event.deliveryVerified();
        String source = connection.getProvider().name().toLowerCase() + ":" + connection.getExternalAccountRef();
        String subject = text(event.subject(), event.type().name());
        String detail = text(event.detail(), subject);

        OvernightSignal signal = new OvernightSignal(
                source,
                event.type(),
                subject,
                detail,
                effectiveTrustedLowRisk,
                Math.max(0, event.signups()),
                text(event.verificationStatus(), ""),
                Math.max(0, event.verificationRuns())
        );

        ExecutiveOvernightBriefing briefing = executive.run(new ExecutiveOvernightRunRequest(
                connection.getOwnerId(), null, List.of(signal)));
        if (briefing.actions() == null || briefing.actions().size() != 1) {
            throw new IllegalStateException("Executive agent did not return exactly one action for connector event");
        }
        ExecutiveAction action = briefing.actions().getFirst();
        ConnectorEventReceiptEntity saved = receipts.save(new ConnectorEventReceiptEntity(
                UUID.randomUUID(),
                connection.getId(),
                externalEventId,
                connection.getProvider(),
                source,
                event.type(),
                Instant.now(),
                event.occurredAt(),
                event.deliveryVerified(),
                event.trustedLowRisk(),
                effectiveTrustedLowRisk,
                briefing.runId(),
                action.disposition(),
                action.taskId(),
                action.approvalId(),
                action.summary()
        ));
        return result(saved, false);
    }

    private ConnectorConnectionEntity connection(UUID id) {
        if (id == null) throw new IllegalArgumentException("Connector id is required");
        return connections.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown connector: " + id));
    }

    private ConnectorConnectionView view(ConnectorConnectionEntity entity) {
        return new ConnectorConnectionView(
                entity.getId(), entity.getOwnerId(), entity.getProvider(), entity.getExternalAccountRef(),
                entity.getDisplayName(), entity.getStatus(), Set.copyOf(entity.capabilities()),
                entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private ConnectorIngestResult result(ConnectorEventReceiptEntity entity, boolean duplicate) {
        return new ConnectorIngestResult(
                entity.getId(), duplicate, entity.getProvider(), entity.getExternalEventId(), entity.getSource(),
                entity.getExecutiveRunId(), entity.getDisposition(), entity.getTaskId(), entity.getApprovalId(),
                entity.isTrustedLowRiskEffective(), entity.getSummary());
    }

    private static ConnectorCapability requiredCapability(ExecutiveSignalType type) {
        return switch (type) {
            case EMAIL, DM, FAQ, PRODUCT_INTEREST -> ConnectorCapability.INGEST_MESSAGES;
            case SIGNUP -> ConnectorCapability.INGEST_ANALYTICS;
            case CODE_UPDATE -> ConnectorCapability.INGEST_CODE_UPDATES;
            case CALENDAR_EVENT -> ConnectorCapability.READ_CALENDAR;
            case BILLING -> ConnectorCapability.INGEST_BILLING;
            case DEPLOYMENT -> ConnectorCapability.INGEST_DEPLOYMENTS;
        };
    }

    private static String text(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
