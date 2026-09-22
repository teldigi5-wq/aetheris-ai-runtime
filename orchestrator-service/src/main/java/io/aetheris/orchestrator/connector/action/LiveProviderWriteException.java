package io.aetheris.orchestrator.connector.action;

/**
 * Represents a real provider HTTP rejection after Aetheris has passed its own
 * approval, policy, credential, and action-state gates.
 *
 * <p>The provider response body is deliberately discarded. Only the numeric
 * upstream status and a narrowly allow-listed GitHub permission hint may be
 * retained for sanitized diagnostics.</p>
 */
public class LiveProviderWriteException extends RuntimeException {

    private final int providerStatus;
    private final String acceptedPermissions;

    public LiveProviderWriteException(int providerStatus) {
        this(providerStatus, null);
    }

    public LiveProviderWriteException(
            int providerStatus,
            String acceptedPermissions
    ) {
        super(buildSafeMessage(providerStatus, acceptedPermissions));
        this.providerStatus = providerStatus;
        this.acceptedPermissions = acceptedPermissions;
    }

    public int getProviderStatus() {
        return providerStatus;
    }

    public String getAcceptedPermissions() {
        return acceptedPermissions;
    }

    private static String buildSafeMessage(
            int providerStatus,
            String acceptedPermissions
    ) {

        StringBuilder message =
                new StringBuilder(
                        "Live provider write failed with HTTP "
                ).append(providerStatus);

        if (acceptedPermissions != null
                && !acceptedPermissions.isBlank()) {
            message.append(
                    "; accepted GitHub permissions="
            ).append(acceptedPermissions);
        }

        return message.toString();
    }
}
