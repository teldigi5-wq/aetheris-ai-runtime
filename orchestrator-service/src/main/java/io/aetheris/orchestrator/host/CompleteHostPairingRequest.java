package io.aetheris.orchestrator.host;
public record CompleteHostPairingRequest(String nonce, String publicKeyPem, String signatureBase64) {}
