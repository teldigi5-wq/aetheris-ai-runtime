package io.aetheris.orchestrator.stage20;

import io.aetheris.orchestrator.stage16.Stage16CommandTrustService;
import io.aetheris.orchestrator.stage18.*;
import io.aetheris.orchestrator.stage19.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.*;
import java.util.*;

@Service
public class Stage20HardwareHandoffService {
    private static final Duration AUTH_MAX_AGE = Duration.ofMinutes(5);
    private static final Duration STAGE19_ATTESTATION_MAX_AGE = Duration.ofMinutes(30);
    private static final Duration CHALLENGE_TTL = Duration.ofMinutes(2);
    private static final Set<String> RECEIPT_RESULTS = Set.of("SUCCESS", "HEALTHY", "FAILED", "REJECTED");

    private final Stage20ActivationAuthorizationRepository authorizations;
    private final Stage20DeviceChallengeRepository challenges;
    private final Stage20ActivationLeaseRepository leases;
    private final Stage20TargetReceiptRepository receipts;
    private final Stage20HandoffAuditRepository audit;
    private final Stage19ActivationService stage19;
    private final Stage18ProvisioningService stage18;
    private final Stage16CommandTrustService trust;
    private final SecureRandom random = new SecureRandom();

    public Stage20HardwareHandoffService(Stage20ActivationAuthorizationRepository authorizations,
                                         Stage20DeviceChallengeRepository challenges,
                                         Stage20ActivationLeaseRepository leases,
                                         Stage20TargetReceiptRepository receipts,
                                         Stage20HandoffAuditRepository audit,
                                         Stage19ActivationService stage19,
                                         Stage18ProvisioningService stage18,
                                         Stage16CommandTrustService trust) {
        this.authorizations = authorizations;
        this.challenges = challenges;
        this.leases = leases;
        this.receipts = receipts;
        this.audit = audit;
        this.stage19 = stage19;
        this.stage18 = stage18;
        this.trust = trust;
    }

    @Transactional
    public Stage20ActivationAuthorizationEntity authorize(SignedAuthorizationRequest request) {
        if (request == null || request.stage19ProposalId() == null)
            throw new IllegalArgumentException("Stage 20 owner-signed activation authorization is required");
        Stage19ActivationProposalEntity proposal = stage19.get(request.stage19ProposalId());
        if (!"CANARY_VALIDATED_SIMULATION_ONLY".equals(proposal.getStatus()))
            throw new IllegalStateException("Stage 20 requires a Stage 19 validated simulation canary");
        if (proposal.getRefreshedAttestationSha256() == null || proposal.getAttestationRefreshedAt() == null
                || proposal.getAttestationRefreshedAt().isBefore(Instant.now().minus(STAGE19_ATTESTATION_MAX_AGE)))
            throw new IllegalStateException("Stage 20 requires a fresh Stage 19 attestation refresh");

        Stage18BootstrapManifestEntity manifest = stage18.latest(proposal.getTargetId());
        Stage18ProvisioningService.ReadinessAssessment readiness = stage18.readiness(proposal.getTargetId());
        Stage18ProvisioningService.EvidenceBundle bundle = stage18.bundle(proposal.getTargetId());
        if (!"READY_FOR_OWNER_ACTIVATION_REVIEW".equals(readiness.status()) || readiness.score() != 100)
            throw new IllegalStateException("Stage 18 readiness must remain complete before Stage 20 authorization");
        assertProposalContinuity(proposal, manifest, bundle);

        String ownerSigner = token(request.ownerSignerKeyId(), "ownerSignerKeyId", 80).toLowerCase(Locale.ROOT);
        String deviceSigner = token(request.deviceSignerKeyId(), "deviceSignerKeyId", 80).toLowerCase(Locale.ROOT);
        if (ownerSigner.equals(deviceSigner))
            throw new IllegalArgumentException("Owner authorization and device attestation must use separate trusted signers");
        trust.get(ownerSigner);
        trust.get(deviceSigner);

        Set<String> capabilities = normalizeCapabilities(request.capabilities());
        if (capabilities.isEmpty() || !proposal.getCanaryCapabilities().containsAll(capabilities))
            throw new IllegalArgumentException("Stage 20 capabilities must be a non-empty subset of the Stage 19 canary grant");
        int maxLeaseMinutes = request.maxLeaseMinutes();
        if (maxLeaseMinutes < 1 || maxLeaseMinutes > 10)
            throw new IllegalArgumentException("Stage 20 max lease must be between 1 and 10 minutes");

        Instant now = Instant.now();
        Instant issuedAt = Objects.requireNonNull(request.issuedAt(), "issuedAt is required");
        Instant expiresAt = Objects.requireNonNull(request.expiresAt(), "expiresAt is required");
        Instant maintenanceStart = Objects.requireNonNull(request.maintenanceStart(), "maintenanceStart is required");
        Instant maintenanceEnd = Objects.requireNonNull(request.maintenanceEnd(), "maintenanceEnd is required");
        if (issuedAt.isAfter(now.plusSeconds(30)) || issuedAt.isBefore(now.minus(AUTH_MAX_AGE)))
            throw new IllegalArgumentException("Stage 20 authorization issue time is outside the accepted freshness window");
        if (!expiresAt.isAfter(now) || expiresAt.isAfter(issuedAt.plus(Duration.ofMinutes(30))))
            throw new IllegalArgumentException("Stage 20 authorization expiry must be within 30 minutes of issue time");
        if (!maintenanceEnd.isAfter(maintenanceStart)
                || Duration.between(maintenanceStart, maintenanceEnd).compareTo(Duration.ofHours(1)) > 0)
            throw new IllegalArgumentException("Stage 20 maintenance window must be positive and at most one hour");
        if (expiresAt.isAfter(maintenanceEnd))
            throw new IllegalArgumentException("Stage 20 authorization cannot outlive its signed maintenance window");

        String canonical = authorizationCanonical(proposal.getId(), proposal.getProposalSha256(), proposal.getTargetId(),
                proposal.getAdapterId(), proposal.getManifestSha256(), bundle.bundleSha256(), proposal.getPackageSha256(),
                proposal.getDeviceCertificateSha256(), proposal.getRefreshedAttestationSha256(), ownerSigner, deviceSigner,
                capabilities, issuedAt, expiresAt, maintenanceStart, maintenanceEnd, maxLeaseMinutes);
        String authorizationSha = normalizeSha(request.authorizationSha256(), "authorizationSha256");
        if (!authorizationSha.equals(Stage18ProvisioningService.shaText(canonical)))
            throw new IllegalArgumentException("Stage 20 authorization SHA-256 does not match canonical content");
        String signature = normalizeBase64(request.signatureBase64(), "signatureBase64");
        if (!trust.verify(ownerSigner, canonical, signature))
            throw new IllegalArgumentException("Stage 20 owner authorization signature verification failed");
        if (authorizations.existsByAuthorizationSha256(authorizationSha))
            throw new IllegalStateException("This Stage 20 owner authorization already exists");

        Stage20ActivationAuthorizationEntity entity = authorizations.save(new Stage20ActivationAuthorizationEntity(
                UUID.randomUUID(), proposal.getId(), proposal.getTargetId(), proposal.getAdapterId(),
                proposal.getProposalSha256(), proposal.getRefreshedAttestationSha256(), bundle.bundleSha256(),
                proposal.getManifestSha256(), proposal.getPackageSha256(), proposal.getDeviceCertificateSha256(),
                ownerSigner, deviceSigner, capabilities, authorizationSha,
                Stage16CommandTrustService.sha256(Base64.getDecoder().decode(signature)), issuedAt, expiresAt,
                maintenanceStart, maintenanceEnd, maxLeaseMinutes));
        appendAudit(entity, "OWNER_AUTHORIZATION_VERIFIED", authorizationSha + "|" + bundle.bundleSha256());
        return entity;
    }

    @Transactional
    public ChallengeIssue issueChallenge(UUID authorizationId) {
        Stage20ActivationAuthorizationEntity auth = getAuthorization(authorizationId);
        assertAuthorizationUsable(auth);
        byte[] nonce = new byte[32];
        random.nextBytes(nonce);
        String nonceSha = Stage16CommandTrustService.sha256(nonce);
        Stage20DeviceChallengeEntity challenge = challenges.save(new Stage20DeviceChallengeEntity(
                UUID.randomUUID(), auth.getId(), auth.getTargetId(), nonceSha, Instant.now().plus(CHALLENGE_TTL)));
        appendAudit(auth, "HARDWARE_CHALLENGE_ISSUED", challenge.getId() + "|" + nonceSha);
        return new ChallengeIssue(challenge.getId(), auth.getId(), auth.getTargetId(),
                Base64.getEncoder().encodeToString(nonce), nonceSha, challenge.getExpiresAt(),
                "PENDING_DEVICE_ATTESTATION", true, false, false);
    }

    @Transactional
    public Stage20DeviceChallengeEntity attest(UUID challengeId, DeviceAttestationRequest request) {
        Stage20DeviceChallengeEntity challenge = getChallenge(challengeId);
        Stage20ActivationAuthorizationEntity auth = getAuthorization(challenge.getAuthorizationId());
        assertAuthorizationUsable(auth);
        if (!"PENDING_DEVICE_ATTESTATION".equals(challenge.getStatus()) || challenge.getConsumedAt() != null)
            throw new IllegalStateException("Stage 20 hardware challenge is not pending");
        if (!challenge.getExpiresAt().isAfter(Instant.now()))
            throw new IllegalStateException("Stage 20 hardware challenge expired");
        if (request == null) throw new IllegalArgumentException("Stage 20 device attestation response is required");
        String adapterId = token(request.adapterId(), "adapterId", 80).toLowerCase(Locale.ROOT);
        String packageSha = normalizeSha(request.packageSha256(), "packageSha256");
        String certSha = normalizeSha(request.deviceCertificateSha256(), "deviceCertificateSha256");
        if (!adapterId.equals(auth.getAdapterId()) || !packageSha.equals(auth.getPackageSha256())
                || !certSha.equals(auth.getDeviceCertificateSha256()))
            throw new IllegalArgumentException("Stage 20 device attestation does not match the authorized adapter/package/certificate");
        Instant observedAt = request.observedAt() == null ? Instant.now() : request.observedAt();
        if (observedAt.isAfter(Instant.now().plusSeconds(30))
                || observedAt.isBefore(challenge.getIssuedAt().minusSeconds(30)))
            throw new IllegalArgumentException("Stage 20 device attestation timestamp is outside the challenge window");
        String canonical = deviceAttestationCanonical(challenge.getId(), auth.getId(), auth.getTargetId(),
                challenge.getNonceSha256(), adapterId, packageSha, certSha, observedAt);
        String signature = normalizeBase64(request.signatureBase64(), "signatureBase64");
        if (!trust.verify(auth.getDeviceSignerKeyId(), canonical, signature))
            throw new IllegalArgumentException("Stage 20 device attestation signature verification failed");
        String attestationSha = Stage18ProvisioningService.shaText(canonical + "|" + signature);
        challenge.verify(attestationSha);
        Stage20DeviceChallengeEntity saved = challenges.save(challenge);
        appendAudit(auth, "DEVICE_ATTESTATION_VERIFIED", challenge.getId() + "|" + attestationSha);
        return saved;
    }

    @Transactional
    public Stage20ActivationLeaseEntity startLease(UUID authorizationId, LeaseRequest request) {
        Stage20ActivationAuthorizationEntity auth = getAuthorization(authorizationId);
        assertAuthorizationUsable(auth);
        if (request == null || request.challengeId() == null)
            throw new IllegalArgumentException("Stage 20 lease requires a verified hardware challenge");
        Stage20DeviceChallengeEntity challenge = validatedUnconsumedChallenge(auth, request.challengeId());
        Set<String> capabilities = normalizeCapabilities(request.capabilities());
        if (capabilities.isEmpty() || !auth.getCapabilities().containsAll(capabilities))
            throw new IllegalArgumentException("Stage 20 lease capabilities cannot widen the owner-signed authorization");
        int minutes = boundedLeaseMinutes(request.leaseMinutes(), auth);
        Instant now = Instant.now();
        Instant expiresAt = min(now.plus(Duration.ofMinutes(minutes)), auth.getExpiresAt(), auth.getMaintenanceEnd());
        if (!expiresAt.isAfter(now.plusSeconds(30)))
            throw new IllegalStateException("Stage 20 lease would expire too soon");
        UUID leaseId = UUID.randomUUID();
        String leaseSha = Stage18ProvisioningService.shaText(leaseCanonical(leaseId, auth.getId(), auth.getTargetId(),
                capabilities, challenge.getAttestationSha256(), now, expiresAt));
        Stage20ActivationLeaseEntity lease = leases.save(new Stage20ActivationLeaseEntity(
                leaseId, auth.getId(), auth.getTargetId(), capabilities, leaseSha,
                challenge.getAttestationSha256(), expiresAt));
        challenge.consume();
        challenges.save(challenge);
        appendAudit(auth, "LEASE_STARTED", leaseSha + "|" + expiresAt.toEpochMilli());
        return lease;
    }

    @Transactional
    public Stage20ActivationLeaseEntity renewLease(UUID leaseId, LeaseRenewalRequest request) {
        Stage20ActivationLeaseEntity lease = getLease(leaseId);
        Stage20ActivationAuthorizationEntity auth = getAuthorization(lease.getAuthorizationId());
        assertAuthorizationUsable(auth);
        if (!("LEASE_ACTIVE_SIMULATION_ONLY".equals(lease.getStatus())
                || "LEASE_RENEWED_SIMULATION_ONLY".equals(lease.getStatus())))
            throw new IllegalStateException("Only an active Stage 20 lease can be renewed");
        if (!lease.getExpiresAt().isAfter(Instant.now()))
            throw new IllegalStateException("Stage 20 lease already expired");
        if (request == null || request.challengeId() == null)
            throw new IllegalArgumentException("Stage 20 lease renewal requires a fresh hardware challenge");
        Stage20DeviceChallengeEntity challenge = validatedUnconsumedChallenge(auth, request.challengeId());
        int minutes = boundedLeaseMinutes(request.leaseMinutes(), auth);
        Instant newExpiry = min(lease.getExpiresAt().plus(Duration.ofMinutes(minutes)),
                auth.getExpiresAt(), auth.getMaintenanceEnd());
        if (!newExpiry.isAfter(lease.getExpiresAt()))
            throw new IllegalStateException("Stage 20 lease cannot be extended beyond authorization/window bounds");
        String renewalSha = Stage18ProvisioningService.shaText(lease.getId() + "|" + lease.getLeaseSha256() + "|"
                + challenge.getAttestationSha256() + "|" + newExpiry.toEpochMilli() + "|"
                + (lease.getRenewalCount() + 1));
        lease.renew(newExpiry, challenge.getAttestationSha256(), renewalSha);
        challenge.consume();
        challenges.save(challenge);
        Stage20ActivationLeaseEntity saved = leases.save(lease);
        appendAudit(auth, "LEASE_RENEWED", renewalSha + "|" + newExpiry.toEpochMilli());
        return saved;
    }

    @Transactional
    public Stage20ActivationAuthorizationEntity engageEmergencyStop(UUID authorizationId, ReasonRequest request) {
        Stage20ActivationAuthorizationEntity auth = getAuthorization(authorizationId);
        String reason = reason(request == null ? null : request.reason());
        String reasonSha = Stage18ProvisioningService.shaText(reason);
        auth.engageEmergencyStop(reasonSha);
        authorizations.save(auth);
        revokeActiveLeases(auth.getId(), reasonSha);
        appendAudit(auth, "EMERGENCY_STOP_ENGAGED", reasonSha);
        return auth;
    }

    @Transactional
    public Stage20ActivationAuthorizationEntity clearEmergencyStop(UUID authorizationId, ClearInterlockRequest request) {
        Stage20ActivationAuthorizationEntity auth = getAuthorization(authorizationId);
        if (!auth.isEmergencyStopEngaged() || !"EMERGENCY_STOPPED_SIMULATION".equals(auth.getStatus()))
            throw new IllegalStateException("Stage 20 emergency stop is not in a clearable state");
        if (request == null || !"OWNER".equalsIgnoreCase(request.approvedBy()))
            throw new IllegalArgumentException("Only OWNER can clear the Stage 20 activation interlock");
        if (!auth.getExpiresAt().isAfter(Instant.now()))
            throw new IllegalStateException("Stage 20 owner authorization expired and cannot be re-enabled");
        Instant observedAt = request.observedAt() == null ? Instant.now() : request.observedAt();
        if (observedAt.isAfter(Instant.now().plusSeconds(30))
                || observedAt.isBefore(Instant.now().minus(Duration.ofMinutes(2))))
            throw new IllegalArgumentException("Stage 20 interlock-clear authorization is stale");
        assertContinuity(auth);
        String canonical = clearInterlockCanonical(auth.getId(), auth.getAuthorizationSha256(), observedAt);
        String signature = normalizeBase64(request.signatureBase64(), "signatureBase64");
        if (!trust.verify(auth.getOwnerSignerKeyId(), canonical, signature))
            throw new IllegalArgumentException("Stage 20 owner interlock-clear signature verification failed");
        String clearSha = Stage18ProvisioningService.shaText(canonical + "|" + signature);
        auth.clearEmergencyStop(clearSha);
        Stage20ActivationAuthorizationEntity saved = authorizations.save(auth);
        appendAudit(saved, "EMERGENCY_STOP_CLEARED_BY_OWNER", clearSha);
        return saved;
    }

    @Transactional
    public Stage20TargetReceiptEntity recordReceipt(UUID leaseId, TargetReceiptRequest request) {
        Stage20ActivationLeaseEntity lease = getLease(leaseId);
        Stage20ActivationAuthorizationEntity auth = getAuthorization(lease.getAuthorizationId());
        if (!("LEASE_ACTIVE_SIMULATION_ONLY".equals(lease.getStatus())
                || "LEASE_RENEWED_SIMULATION_ONLY".equals(lease.getStatus())))
            throw new IllegalStateException("Stage 20 target receipt requires an active simulation lease");
        if (request == null) throw new IllegalArgumentException("Stage 20 target receipt is required");
        if (request.targetMutated() || request.externalActionAttempted())
            throw new IllegalArgumentException("Stage 20 repository mode refuses receipts that claim target mutation or external action");
        Instant observedAt = request.observedAt() == null ? Instant.now() : request.observedAt();
        if (observedAt.isAfter(Instant.now().plusSeconds(30))
                || observedAt.isBefore(lease.getStartedAt().minusSeconds(30))
                || observedAt.isAfter(lease.getExpiresAt().plusSeconds(30)))
            throw new IllegalArgumentException("Stage 20 target receipt timestamp is outside the lease window");
        String packageSha = normalizeSha(request.packageSha256(), "packageSha256");
        String certSha = normalizeSha(request.deviceCertificateSha256(), "deviceCertificateSha256");
        String attestationSha = normalizeSha(request.targetAttestationSha256(), "targetAttestationSha256");
        String result = token(request.resultCode(), "resultCode", 48).toUpperCase(Locale.ROOT);
        if (!RECEIPT_RESULTS.contains(result))
            throw new IllegalArgumentException("Unsupported Stage 20 receipt resultCode");
        String canonical = targetReceiptCanonical(lease.getId(), auth.getId(), auth.getTargetId(), lease.getLeaseSha256(),
                packageSha, certSha, attestationSha, result, request.measuredOnTarget(), observedAt);
        String signature = normalizeBase64(request.signatureBase64(), "signatureBase64");
        if (!trust.verify(auth.getDeviceSignerKeyId(), canonical, signature))
            throw new IllegalArgumentException("Stage 20 target receipt signature verification failed");
        String receiptSha = Stage18ProvisioningService.shaText(canonical + "|" + signature);
        boolean drift = !packageSha.equals(auth.getPackageSha256())
                || !certSha.equals(auth.getDeviceCertificateSha256())
                || !attestationSha.equals(lease.getLastAttestationSha256());
        String status;
        if (drift) {
            String driftSha = Stage18ProvisioningService.shaText("ATTESTATION_DRIFT|" + receiptSha);
            auth.revokeForDrift(driftSha);
            authorizations.save(auth);
            revokeActiveLeases(auth.getId(), driftSha);
            status = "REJECTED_ATTESTATION_DRIFT_SIMULATION";
            appendAudit(auth, "ATTESTATION_DRIFT_AUTO_REVOKE", driftSha);
        } else {
            status = request.measuredOnTarget()
                    ? "VERIFIED_TARGET_REPORTED_CONTRACT_ONLY" : "VERIFIED_SIMULATION_ONLY";
            appendAudit(auth, "TARGET_RECEIPT_VERIFIED", receiptSha + "|" + status);
        }
        return receipts.save(new Stage20TargetReceiptEntity(UUID.randomUUID(), auth.getId(), lease.getId(),
                auth.getTargetId(), lease.getLeaseSha256(), packageSha, certSha, attestationSha, result,
                receiptSha, Stage16CommandTrustService.sha256(Base64.getDecoder().decode(signature)), status,
                request.measuredOnTarget(), observedAt));
    }

    public PostActivationEvidenceBundle postActivationBundle(UUID authorizationId) {
        Stage20ActivationAuthorizationEntity auth = getAuthorization(authorizationId);
        Stage18ProvisioningService.EvidenceBundle stage18Bundle = stage18.bundle(auth.getTargetId());
        List<Stage20DeviceChallengeEntity> cs = challenges.findTop50ByAuthorizationIdOrderByIssuedAtDesc(auth.getId());
        List<Stage20ActivationLeaseEntity> ls = leases.findTop50ByAuthorizationIdOrderByStartedAtDesc(auth.getId());
        List<Stage20TargetReceiptEntity> rs = receipts.findTop100ByAuthorizationIdOrderByObservedAtDesc(auth.getId());
        StringBuilder canonical = new StringBuilder(auth.getAuthorizationSha256())
                .append('|').append(stage18Bundle.bundleSha256()).append('|').append(auth.getStatus());
        cs.stream().sorted(Comparator.comparing(Stage20DeviceChallengeEntity::getIssuedAt))
                .forEach(c -> canonical.append('|').append(c.getNonceSha256()).append(':')
                        .append(c.getStatus()).append(':').append(Objects.toString(c.getAttestationSha256(), "")));
        ls.stream().sorted(Comparator.comparing(Stage20ActivationLeaseEntity::getStartedAt))
                .forEach(l -> canonical.append('|').append(l.getLeaseSha256()).append(':')
                        .append(l.getStatus()).append(':').append(l.getExpiresAt().toEpochMilli()));
        rs.stream().sorted(Comparator.comparing(Stage20TargetReceiptEntity::getObservedAt))
                .forEach(r -> canonical.append('|').append(r.getReceiptSha256()).append(':').append(r.getStatus()));
        boolean verifiedReceipt = rs.stream().anyMatch(r -> r.getStatus().startsWith("VERIFIED_"));
        String status = auth.getStatus().startsWith("REVOKED_") || auth.isEmergencyStopEngaged()
                ? "HANDOFF_REVOKED_SIMULATION"
                : verifiedReceipt ? "POST_ACTIVATION_EVIDENCE_SIMULATION_ONLY" : "HANDOFF_EVIDENCE_INCOMPLETE";
        return new PostActivationEvidenceBundle(auth.getId(), auth.getTargetId(), status,
                cs.size(), ls.size(), rs.size(), Stage18ProvisioningService.shaText(canonical.toString()),
                false, false, false);
    }

    public Stage20ActivationAuthorizationEntity getAuthorization(UUID id) {
        if (id == null) throw new IllegalArgumentException("authorizationId is required");
        return authorizations.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Unknown Stage 20 authorization"));
    }
    public Stage20DeviceChallengeEntity getChallenge(UUID id) {
        if (id == null) throw new IllegalArgumentException("challengeId is required");
        return challenges.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Unknown Stage 20 hardware challenge"));
    }
    public Stage20ActivationLeaseEntity getLease(UUID id) {
        if (id == null) throw new IllegalArgumentException("leaseId is required");
        return leases.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Unknown Stage 20 activation lease"));
    }
    public List<Stage20ActivationAuthorizationEntity> authorizations() { return authorizations.findTop100ByOrderByCreatedAtDesc(); }
    public List<Stage20DeviceChallengeEntity> challenges() { return challenges.findTop100ByOrderByIssuedAtDesc(); }
    public List<Stage20ActivationLeaseEntity> leases() { return leases.findTop100ByOrderByStartedAtDesc(); }
    public List<Stage20TargetReceiptEntity> receipts() { return receipts.findTop200ByOrderByObservedAtDesc(); }
    public List<Stage20HandoffAuditEntity> audit() { return audit.findTop200ByOrderByObservedAtDesc(); }
    public List<Stage20HandoffAuditEntity> audit(UUID authorizationId) {
        return audit.findTop100ByAuthorizationIdOrderByObservedAtDesc(authorizationId);
    }

    private Stage20DeviceChallengeEntity validatedUnconsumedChallenge(Stage20ActivationAuthorizationEntity auth,
                                                                       UUID challengeId) {
        Stage20DeviceChallengeEntity challenge = getChallenge(challengeId);
        if (!challenge.getAuthorizationId().equals(auth.getId()) || !challenge.getTargetId().equals(auth.getTargetId()))
            throw new IllegalArgumentException("Stage 20 hardware challenge belongs to a different authorization/target");
        if (challenge.getConsumedAt() != null)
            throw new IllegalStateException("Stage 20 hardware challenge was already consumed");
        if (!"DEVICE_ATTESTATION_VERIFIED_SIMULATION_ONLY".equals(challenge.getStatus())
                || challenge.getVerifiedAt() == null)
            throw new IllegalStateException("Stage 20 lease requires a verified device attestation");
        if (!challenge.getExpiresAt().isAfter(Instant.now()))
            throw new IllegalStateException("Stage 20 hardware challenge expired");
        return challenge;
    }

    private void assertAuthorizationUsable(Stage20ActivationAuthorizationEntity auth) {
        if (!"AUTHORIZED_SIMULATION_ONLY".equals(auth.getStatus()))
            throw new IllegalStateException("Stage 20 authorization is not active for hardware handoff");
        if (auth.isEmergencyStopEngaged())
            throw new IllegalStateException("Stage 20 emergency stop interlock is engaged");
        Instant now = Instant.now();
        if (!auth.getExpiresAt().isAfter(now))
            throw new IllegalStateException("Stage 20 owner authorization expired");
        if (now.isBefore(auth.getMaintenanceStart()) || !now.isBefore(auth.getMaintenanceEnd()))
            throw new IllegalStateException("Stage 20 operation is outside the owner-signed maintenance window");
        assertContinuity(auth);
    }

    private void assertContinuity(Stage20ActivationAuthorizationEntity auth) {
        Stage19ActivationProposalEntity proposal = stage19.get(auth.getStage19ProposalId());
        Stage18BootstrapManifestEntity manifest = stage18.latest(auth.getTargetId());
        Stage18ProvisioningService.ReadinessAssessment readiness = stage18.readiness(auth.getTargetId());
        Stage18ProvisioningService.EvidenceBundle bundle = stage18.bundle(auth.getTargetId());
        if (!"CANARY_VALIDATED_SIMULATION_ONLY".equals(proposal.getStatus())
                || !proposal.getProposalSha256().equals(auth.getStage19ProposalSha256())
                || !Objects.equals(proposal.getRefreshedAttestationSha256(), auth.getStage19AttestationSha256()))
            throw new IllegalStateException("Stage 20 Stage 19 canary/attestation continuity changed");
        if (!manifest.getAdapterId().equals(auth.getAdapterId())
                || !manifest.getManifestSha256().equals(auth.getManifestSha256())
                || !manifest.getPackageSha256().equals(auth.getPackageSha256())
                || !manifest.getDeviceCertificateSha256().equals(auth.getDeviceCertificateSha256()))
            throw new IllegalStateException("Stage 20 target/package/certificate/adapter continuity changed");
        if (!bundle.bundleSha256().equals(auth.getStage18BundleSha256())
                || !"READY_FOR_OWNER_ACTIVATION_REVIEW".equals(readiness.status()) || readiness.score() != 100)
            throw new IllegalStateException("Stage 20 Stage 18 readiness/evidence continuity changed");
    }

    private void assertProposalContinuity(Stage19ActivationProposalEntity proposal,
                                          Stage18BootstrapManifestEntity manifest,
                                          Stage18ProvisioningService.EvidenceBundle bundle) {
        if (!proposal.getAdapterId().equals(manifest.getAdapterId())
                || !proposal.getManifestSha256().equals(manifest.getManifestSha256())
                || !proposal.getPackageSha256().equals(manifest.getPackageSha256())
                || !proposal.getDeviceCertificateSha256().equals(manifest.getDeviceCertificateSha256())
                || !proposal.getEvidenceBundleSha256().equals(bundle.bundleSha256()))
            throw new IllegalStateException("Stage 20 exact Stage 19/18 target continuity check failed");
    }

    private int boundedLeaseMinutes(int minutes, Stage20ActivationAuthorizationEntity auth) {
        if (minutes < 1 || minutes > auth.getMaxLeaseMinutes())
            throw new IllegalArgumentException("Stage 20 lease duration exceeds the owner-signed maximum");
        return minutes;
    }
    private void revokeActiveLeases(UUID authorizationId, String reasonSha) {
        for (Stage20ActivationLeaseEntity lease : leases.findTop50ByAuthorizationIdOrderByStartedAtDesc(authorizationId)) {
            if ("LEASE_ACTIVE_SIMULATION_ONLY".equals(lease.getStatus())
                    || "LEASE_RENEWED_SIMULATION_ONLY".equals(lease.getStatus())) {
                lease.revoke(reasonSha);
                leases.save(lease);
            }
        }
    }
    private void appendAudit(Stage20ActivationAuthorizationEntity auth, String event, String detail) {
        audit.save(new Stage20HandoffAuditEntity(UUID.randomUUID(), auth.getId(), auth.getTargetId(), event,
                auth.getStatus(), Stage18ProvisioningService.shaText(detail)));
    }
    private Set<String> normalizeCapabilities(Set<String> values) {
        if (values == null) return Set.of();
        TreeSet<String> out = new TreeSet<>();
        for (String value : values)
            out.add(token(value, "capability", 48).toUpperCase(Locale.ROOT));
        return Set.copyOf(out);
    }
    private String normalizeSha(String value, String label) {
        if (value == null || !value.trim().toLowerCase(Locale.ROOT).matches("[a-f0-9]{64}"))
            throw new IllegalArgumentException(label + " must be a SHA-256 value");
        return value.trim().toLowerCase(Locale.ROOT);
    }
    private String normalizeBase64(String value, String label) {
        if (value == null || value.isBlank() || value.length() > 1024)
            throw new IllegalArgumentException(label + " is invalid");
        try {
            return Base64.getEncoder().encodeToString(Base64.getDecoder().decode(value.trim()));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(label + " must be valid Base64");
        }
    }
    private String token(String value, String label, int max) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required");
        String v = value.trim();
        if (v.length() > max || !v.matches("[A-Za-z0-9._:/-]{1," + max + "}"))
            throw new IllegalArgumentException(label + " is invalid");
        return v;
    }
    private String reason(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("reason is required");
        String v = value.trim();
        if (v.length() > 300) throw new IllegalArgumentException("reason is too long");
        String lower = v.toLowerCase(Locale.ROOT);
        if (lower.contains("password=") || lower.contains("api_key=")
                || lower.contains("authorization:") || lower.contains("bearer "))
            throw new IllegalArgumentException("reason appears to contain secret material");
        return v;
    }
    private Instant min(Instant... values) {
        return Arrays.stream(values).min(Comparator.naturalOrder()).orElseThrow();
    }

    public static String authorizationCanonical(UUID stage19ProposalId, String stage19ProposalSha256, String targetId,
                                                String adapterId, String manifestSha256, String stage18BundleSha256,
                                                String packageSha256, String deviceCertificateSha256,
                                                String stage19AttestationSha256, String ownerSignerKeyId,
                                                String deviceSignerKeyId, Set<String> capabilities, Instant issuedAt,
                                                Instant expiresAt, Instant maintenanceStart, Instant maintenanceEnd,
                                                int maxLeaseMinutes) {
        return stage19ProposalId + "|" + stage19ProposalSha256.toLowerCase(Locale.ROOT) + "|"
                + targetId.toLowerCase(Locale.ROOT) + "|" + adapterId.toLowerCase(Locale.ROOT) + "|"
                + manifestSha256.toLowerCase(Locale.ROOT) + "|" + stage18BundleSha256.toLowerCase(Locale.ROOT) + "|"
                + packageSha256.toLowerCase(Locale.ROOT) + "|" + deviceCertificateSha256.toLowerCase(Locale.ROOT) + "|"
                + stage19AttestationSha256.toLowerCase(Locale.ROOT) + "|" + ownerSignerKeyId.toLowerCase(Locale.ROOT) + "|"
                + deviceSignerKeyId.toLowerCase(Locale.ROOT) + "|" + String.join(",", new TreeSet<>(capabilities)) + "|"
                + issuedAt.toEpochMilli() + "|" + expiresAt.toEpochMilli() + "|" + maintenanceStart.toEpochMilli() + "|"
                + maintenanceEnd.toEpochMilli() + "|" + maxLeaseMinutes;
    }
    public static String deviceAttestationCanonical(UUID challengeId, UUID authorizationId, String targetId,
                                                     String nonceSha256, String adapterId, String packageSha256,
                                                     String deviceCertificateSha256, Instant observedAt) {
        return challengeId + "|" + authorizationId + "|" + targetId.toLowerCase(Locale.ROOT) + "|"
                + nonceSha256.toLowerCase(Locale.ROOT) + "|" + adapterId.toLowerCase(Locale.ROOT) + "|"
                + packageSha256.toLowerCase(Locale.ROOT) + "|" + deviceCertificateSha256.toLowerCase(Locale.ROOT)
                + "|" + observedAt.toEpochMilli();
    }
    public static String leaseCanonical(UUID leaseId, UUID authorizationId, String targetId, Set<String> capabilities,
                                        String attestationSha256, Instant startedAt, Instant expiresAt) {
        return leaseId + "|" + authorizationId + "|" + targetId.toLowerCase(Locale.ROOT) + "|"
                + String.join(",", new TreeSet<>(capabilities)) + "|" + attestationSha256.toLowerCase(Locale.ROOT)
                + "|" + startedAt.toEpochMilli() + "|" + expiresAt.toEpochMilli();
    }
    public static String clearInterlockCanonical(UUID authorizationId, String authorizationSha256, Instant observedAt) {
        return "CLEAR|" + authorizationId + "|" + authorizationSha256.toLowerCase(Locale.ROOT)
                + "|" + observedAt.toEpochMilli();
    }
    public static String targetReceiptCanonical(UUID leaseId, UUID authorizationId, String targetId, String leaseSha256,
                                                String packageSha256, String deviceCertificateSha256,
                                                String targetAttestationSha256, String resultCode,
                                                boolean measuredOnTarget, Instant observedAt) {
        return leaseId + "|" + authorizationId + "|" + targetId.toLowerCase(Locale.ROOT) + "|"
                + leaseSha256.toLowerCase(Locale.ROOT) + "|" + packageSha256.toLowerCase(Locale.ROOT) + "|"
                + deviceCertificateSha256.toLowerCase(Locale.ROOT) + "|"
                + targetAttestationSha256.toLowerCase(Locale.ROOT) + "|"
                + resultCode.toUpperCase(Locale.ROOT) + "|" + measuredOnTarget + "|" + observedAt.toEpochMilli();
    }

    public record SignedAuthorizationRequest(UUID stage19ProposalId, String ownerSignerKeyId,
                                             String deviceSignerKeyId, Set<String> capabilities,
                                             Instant issuedAt, Instant expiresAt, Instant maintenanceStart,
                                             Instant maintenanceEnd, int maxLeaseMinutes,
                                             String authorizationSha256, String signatureBase64) {}
    public record ChallengeIssue(UUID challengeId, UUID authorizationId, String targetId, String nonceBase64,
                                 String nonceSha256, Instant expiresAt, String status, boolean simulationOnly,
                                 boolean productionActivationAllowed, boolean externalActionAttempted) {}
    public record DeviceAttestationRequest(String adapterId, String packageSha256,
                                           String deviceCertificateSha256, Instant observedAt,
                                           String signatureBase64) {}
    public record LeaseRequest(UUID challengeId, Set<String> capabilities, int leaseMinutes) {}
    public record LeaseRenewalRequest(UUID challengeId, int leaseMinutes) {}
    public record ReasonRequest(String reason) {}
    public record ClearInterlockRequest(String approvedBy, Instant observedAt, String signatureBase64) {}
    public record TargetReceiptRequest(String packageSha256, String deviceCertificateSha256,
                                       String targetAttestationSha256, String resultCode,
                                       boolean measuredOnTarget, boolean targetMutated,
                                       boolean externalActionAttempted, Instant observedAt,
                                       String signatureBase64) {}
    public record PostActivationEvidenceBundle(UUID authorizationId, String targetId, String status,
                                               int challengeCount, int leaseCount, int receiptCount,
                                               String bundleSha256, boolean productionActivationAllowed,
                                               boolean targetMutated, boolean externalActionAttempted) {}
}
