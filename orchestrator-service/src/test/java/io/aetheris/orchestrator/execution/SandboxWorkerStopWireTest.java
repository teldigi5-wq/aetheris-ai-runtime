package io.aetheris.orchestrator.execution;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SandboxWorkerStopWireTest {
    private static SandboxWorkerStopWire.StopRequest request() {
        long now = Instant.now().getEpochSecond();
        return new SandboxWorkerStopWire.StopRequest(
                UUID.randomUUID(), "workerTest", now, now + 20, 5);
    }

    @Test
    void canonicalEd25519SignatureOnlyAuthenticatesExactBoundPayload() throws Exception {
        var keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        var other = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        var original = SandboxWorkerStopWire.requestBytes(request());
        var frame = SandboxWorkerStopWire.sign(original, keys.getPrivate());
        assertThat(SandboxWorkerStopWire.verified(frame, keys.getPublic())).isTrue();
        assertThat(SandboxWorkerStopWire.verified(frame, other.getPublic())).isFalse();

        byte[] forged = original.clone();
        forged[forged.length - 1] ^= 1;
        var modified = new SandboxWorkerStopWire.SignedFrame(forged, frame.signature());
        assertThat(SandboxWorkerStopWire.verified(modified, keys.getPublic())).isFalse();
        assertThat(SandboxWorkerStopWire.parseRequest(original).workerId()).isEqualTo("workerTest");

        byte[] leaked = frame.signature();
        leaked[0] ^= 1;
        assertThat(SandboxWorkerStopWire.verified(frame, keys.getPublic())).isTrue();
    }

    @Test
    void receiptDomainAndNonceAreSeparateFromRequest() throws Exception {
        var req = request();
        var receipt = new SandboxWorkerStopWire.StopReceipt(
                req.nonce(), req.workerId(), req.epoch(), SandboxWorkerStopWire.STOP_STATUS);
        byte[] signed = SandboxWorkerStopWire.receiptBytes(receipt);
        assertThat(SandboxWorkerStopWire.parseReceipt(signed)).isEqualTo(receipt);
        assertThatThrownBy(() -> SandboxWorkerStopWire.parseRequest(signed))
                .isInstanceOf(java.io.IOException.class).hasMessageContaining("domain");
        assertThatThrownBy(() -> SandboxWorkerStopWire.parseReceipt(
                SandboxWorkerStopWire.requestBytes(req)))
                .isInstanceOf(java.io.IOException.class).hasMessageContaining("domain");
    }

    @Test
    void lengthLimitsAndExtraBytesFailClosed() throws Exception {
        var r = request();
        assertThatThrownBy(() -> new SandboxWorkerStopWire.StopRequest(
                r.nonce(), "wrong/worker", r.issuedAt(), r.expiresAt(), r.epoch()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SandboxWorkerStopWire.StopRequest(
                r.nonce(), r.workerId(), r.issuedAt(), r.issuedAt() + 31, r.epoch()))
                .isInstanceOf(IllegalArgumentException.class);

        var bytes = SandboxWorkerStopWire.requestBytes(r);
        byte[] trailing = java.util.Arrays.copyOf(bytes, bytes.length + 1);
        assertThatThrownBy(() -> SandboxWorkerStopWire.parseRequest(trailing))
                .isInstanceOf(java.io.IOException.class).hasMessageContaining("Non-canonical");

        var output = new ByteArrayOutputStream();
        var writer = new DataOutputStream(output);
        writer.writeInt(1024);
        writer.flush();
        assertThatThrownBy(() -> SandboxWorkerStopWire.readFrame(
                new DataInputStream(new ByteArrayInputStream(output.toByteArray()))))
                .isInstanceOf(java.io.IOException.class).hasMessageContaining("too large");
    }

    @Test
    void frameRoundtripRequiresExactly64SignatureBytes() throws Exception {
        var keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        var request = request();
        var frame = SandboxWorkerStopWire.sign(
                SandboxWorkerStopWire.requestBytes(request), keys.getPrivate());
        var out = new ByteArrayOutputStream();
        SandboxWorkerStopWire.writeFrame(new DataOutputStream(out), frame);
        var read = SandboxWorkerStopWire.readFrame(
                new DataInputStream(new ByteArrayInputStream(out.toByteArray())));
        assertThat(SandboxWorkerStopWire.verified(read, keys.getPublic())).isTrue();
        assertThat(SandboxWorkerStopWire.parseRequest(read.message())).isEqualTo(request);
    }
}
