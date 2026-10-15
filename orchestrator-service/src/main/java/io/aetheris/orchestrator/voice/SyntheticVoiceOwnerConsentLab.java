package io.aetheris.orchestrator.voice;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * Stage 45.5.18 — NON-EXECUTING synthetic transcript/owner-consent lab.
 *
 * Not a Spring component, API endpoint, microphone adapter, desktop tool,
 * Windows agent, OS dispatcher, or model-powered speech/wake detector.
 * No execution method exists. Every approved action is STILL a simulation.
 *
 * Owner identity and Ed25519 verification key are provided by a separate,
 * trusted caller. A voice transcript NEVER creates an approval or trust key.
 * Approval replay and wake state are intentionally process-local: NOT a
 * durable production authority or authenticated microphone speaker identity.
 */
public final class SyntheticVoiceOwnerConsentLab {
    private static final String DOMAIN = "AETHERIS45VOICEOWNERAPPROVAL1";
    private static final long SESSION_SECONDS = 300;
    private static final long REQUEST_SECONDS = 30;
    private static final long MAX_APPROVAL_SECONDS = 20;

    public enum Intent {
        SYSTEM_STATUS_PREVIEW, EDITOR_OPEN_PREVIEW, NOTES_OPEN_PREVIEW
    }
    public enum Status {
        WAKE_ACKNOWLEDGED, AWAITING_TRUSTED_OWNER_APPROVAL,
        SIMULATED_ONLY, EMERGENCY_STOP_LATCHED, DENIED
    }
    public record Result(Status status, UUID requestId, String detail) {
        public Result {
            Objects.requireNonNull(status);
            detail = Objects.requireNonNull(detail);
        }
    }

    /** Signed only by the independent synthetic owner UI test fixture.
     * No voice or chat field may produce this claim.
     */
    public record OwnerPermit(UUID tokenId, UUID sessionId, UUID requestId,
                              String ownerId, Intent intent,
                              long issuedAt, long expiresAt, String signatureHex) {
        public OwnerPermit {
            Objects.requireNonNull(tokenId);
            Objects.requireNonNull(sessionId);
            Objects.requireNonNull(requestId);
            validateOwner(ownerId);
            Objects.requireNonNull(intent);
            if (issuedAt <= 0 || expiresAt <= issuedAt ||
                    expiresAt - issuedAt > MAX_APPROVAL_SECONDS ||
                    signatureHex == null ||
                    !signatureHex.matches("[0-9a-f]{128}"))
                throw new IllegalArgumentException("Invalid bounded owner permit");
        }
    }

    private static final class Session {
        private final String owner;
        private final Instant expires;
        private boolean awake;
        private Pending pending;
        private Session(String owner, Instant expires) {
            this.owner=owner;
            this.expires=expires;
        }
    }
    private record Pending(UUID id, Intent intent, Instant expires) {}

    private final PublicKey trustedOwnerApprovalKey;
    private final BooleanSupplier independentEmergencyStop;
    private final Clock clock;
    private final Map<UUID,Session> sessions=new HashMap<>();
    private final Set<UUID> consumedPermits=new HashSet<>();
    private boolean localStopLatched;

    public SyntheticVoiceOwnerConsentLab(PublicKey trustedOwnerApprovalKey,
                                         BooleanSupplier independentEmergencyStop,
                                         Clock clock) {
        this.trustedOwnerApprovalKey=Objects.requireNonNull(trustedOwnerApprovalKey);
        this.independentEmergencyStop=Objects.requireNonNull(independentEmergencyStop);
        this.clock=Objects.requireNonNull(clock);
        if (!"EdDSA".equalsIgnoreCase(trustedOwnerApprovalKey.getAlgorithm()) &&
                !"Ed25519".equalsIgnoreCase(trustedOwnerApprovalKey.getAlgorithm()))
            throw new IllegalArgumentException("Synthetic owner signature must be Ed25519");
    }

    /** Session must be created by a trusted caller, never the microphone. */
    public synchronized UUID openTrustedSession(String ownerId) {
        validateOwner(ownerId);
        if (stopActive()) throw new SecurityException("VOICE_STOP_ACTIVE");
        UUID id=UUID.randomUUID();
        sessions.put(id,new Session(ownerId,clock.instant().plusSeconds(SESSION_SECONDS)));
        return id;
    }

    /**
     * Accepts text only. It is NOT transcription or wake-word recognition.
     * STOP is processed without waking, using a fixed exact phrase set.
     * Other phrases must be independently whitelisted; arbitrary prompts,
     * URLs, executable names and scripts cannot become an action.
     */
    public synchronized Result receiveSyntheticTranscript(UUID sessionId,String rawText) {
        if (sessionId==null || !sessions.containsKey(sessionId))
            return deny("Unknown owner-bound synthetic session");
        String text=normalize(rawText);
        if (isStop(text)) {
            localStopLatched=true;
            sessions.clear();
            return new Result(Status.EMERGENCY_STOP_LATCHED,null,
                    "Local synthetic STOP latched; no host action was ever started");
        }
        if (stopActive())
            return new Result(Status.EMERGENCY_STOP_LATCHED,null,
                    "STOP signal active; no simulated action permitted");
        Session session=sessions.get(sessionId);
        if (!clock.instant().isBefore(session.expires)) {
            sessions.remove(sessionId);
            return deny("Synthetic voice session expired");
        }
        if (isWake(text)) {
            session.awake=true;
            session.pending=null;
            return new Result(Status.WAKE_ACKNOWLEDGED,null,
                    "Text wake phrase acknowledged; microphone and speaker NOT verified");
        }
        if (!session.awake) return deny("Explicit text wake phrase required");
        Intent intent=switch(text) {
            case "show system status" -> Intent.SYSTEM_STATUS_PREVIEW;
            case "open editor" -> Intent.EDITOR_OPEN_PREVIEW;
            case "open project notes" -> Intent.NOTES_OPEN_PREVIEW;
            default -> null;
        };
        if (intent==null) return deny("No permitted synthetic command matched");
        UUID request=UUID.randomUUID();
        session.pending=new Pending(request,intent,clock.instant().plusSeconds(REQUEST_SECONDS));
        return new Result(Status.AWAITING_TRUSTED_OWNER_APPROVAL,request,
                "Owner approval required; this is NOT permission to perform a desktop action");
    }

    /**
     * Verifies independent UI-signed owner approval. A positive verdict
     * ONLY marks a simulated preview; no OS process, app, file or browser is
     * ever launched. This method is deliberately not connected to tools.
     */
    public synchronized Result simulateWithOwnerPermit(OwnerPermit permit) {
        if (stopActive())
            return new Result(Status.EMERGENCY_STOP_LATCHED,null,"STOP blocks approval");
        if (permit==null || !verifySignature(permit))
            return deny("Independent trusted owner signature required");
        Session session=sessions.get(permit.sessionId());
        if (session==null || !session.awake ||
                !clock.instant().isBefore(session.expires) ||
                session.pending==null)
            return deny("No awake session and outstanding request");
        Pending pending=session.pending;
        long now=clock.instant().getEpochSecond();
        if (!clock.instant().isBefore(pending.expires) ||
                !permit.requestId().equals(pending.id) ||
                !permit.ownerId().equals(session.owner) ||
                permit.intent()!=pending.intent ||
                permit.issuedAt()>now ||
                permit.issuedAt()<now-5 ||
                permit.expiresAt()<=now ||
                consumedPermits.contains(permit.tokenId()))
            return deny("Approval expired, replayed, or crossed scope");
        consumedPermits.add(permit.tokenId());
        session.pending=null;
        return new Result(Status.SIMULATED_ONLY,pending.id,
                "Approved synthetic preview only; no PC, file or application was touched");
    }

    public synchronized boolean isSyntheticStopLatched() {
        return localStopLatched;
    }

    /** Canonical data to be signed by an independent synthetic owner test
     * fixture; excludes signature and uses a fixed domain and exact fields. */
    public static byte[] canonicalPermit(OwnerPermit p) {
        Objects.requireNonNull(p);
        return (DOMAIN+"|"+p.tokenId()+"|"+p.sessionId()+"|"+
                p.requestId()+"|"+p.ownerId()+"|"+p.intent().name()+"|"+
                p.issuedAt()+"|"+p.expiresAt()+"\n")
                .getBytes(StandardCharsets.US_ASCII);
    }

    private boolean verifySignature(OwnerPermit permit) {
        try {
            Signature verifier=Signature.getInstance("Ed25519");
            verifier.initVerify(trustedOwnerApprovalKey);
            verifier.update(canonicalPermit(permit));
            return verifier.verify(java.util.HexFormat.of().parseHex(permit.signatureHex()));
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            return false;
        }
    }

    private boolean stopActive() {
        if (localStopLatched) return true;
        // A later release of a volatile global STOP must NOT resurrect
        // a pending, previously owner-signed request from this session.
        // Every entry point calling this method is synchronized.
        try {
            if (!independentEmergencyStop.getAsBoolean()) return false;
        } catch (RuntimeException unavailable) {
            // Unknown supervisor status is an irreversible local STOP.
        }
        localStopLatched=true;
        sessions.clear();
        return true;
    }

    private static String normalize(String input) {
        if (input==null || input.length()>100 ||
                !input.matches("[A-Za-z ]{1,100}"))
            return "";
        return input.toLowerCase(Locale.ROOT).trim().replaceAll(" +"," ");
    }
    private static boolean isStop(String phrase) {
        return switch(phrase) {
            case "stop", "stop syntra", "stop sintra", "syntra stop",
                 "sintra stop", "emergency stop", "stop everything" -> true;
            default -> false;
        };
    }
    private static boolean isWake(String phrase) {
        return switch(phrase) {
            case "syntra","sintra","hi syntra","hi sintra",
                 "hey syntra","hey sintra" -> true;
            default -> false;
        };
    }
    private static void validateOwner(String owner) {
        if(owner==null || !owner.matches("[A-Za-z0-9_-]{1,64}"))
            throw new IllegalArgumentException("Untrusted owner identity");
    }
    private static Result deny(String detail) {
        return new Result(Status.DENIED,null,detail);
    }
}
