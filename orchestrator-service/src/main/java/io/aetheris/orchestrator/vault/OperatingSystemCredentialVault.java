package io.aetheris.orchestrator.vault;
/** Production-facing contract for an OS-backed vault such as Windows Credential Manager/DPAPI. No OS implementation is enabled until the target workstation is available and tested. */
public interface OperatingSystemCredentialVault extends CredentialVault {
    String backendName();
    boolean hardwareBacked();
}
