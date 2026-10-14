package io.aetheris.orchestrator.execution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@EnabledOnOs(OS.LINUX)
class BoundedUnixStopTransportTest {
    @TempDir Path root;

    @Test
    void stalledPeerReturningPartialFrameCannotBlockBeyondDeadline() throws Exception {
        Path socket = root.resolve("partial.sock");
        try (ServerSocketChannel server = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
             var executor = Executors.newSingleThreadExecutor()) {
            server.bind(UnixDomainSocketAddress.of(socket));
            var peer = executor.submit(() -> {
                try (var accepted = server.accept()) {
                    // Complete controller request first, then maliciously
                    // write only one byte of the signed response and stall.
                    var frame = BoundedUnixStopTransport.read(
                            accepted, Duration.ofSeconds(2));
                    assertThat(frame.message().length).isGreaterThan(0);
                    accepted.write(ByteBuffer.wrap(new byte[]{0}));
                    Thread.sleep(1200);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            });
            var pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
            long now = Instant.now().getEpochSecond();
            var request = new SandboxWorkerStopWire.StopRequest(
                    UUID.randomUUID(), "workerTest", now - 1, now + 15, 1);
            var signed = SandboxWorkerStopWire.sign(
                    SandboxWorkerStopWire.requestBytes(request), pair.getPrivate());

            long started = System.nanoTime();
            assertThatThrownBy(() -> BoundedUnixStopTransport.exchange(
                    socket, signed, Duration.ofMillis(300)))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("DEADLINE_EXCEEDED");
            assertThat(Duration.ofNanos(System.nanoTime() - started).toMillis())
                    .isLessThan(2500);
            executor.shutdownNow();
            peer.cancel(true);
        }
    }

    @Test
    void invalidTimeoutNeverOpensSocketOrWaitsIndefinitely() throws Exception {
        var pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        long now = Instant.now().getEpochSecond();
        var request = new SandboxWorkerStopWire.StopRequest(
                UUID.randomUUID(), "workerTest", now - 1, now + 10, 2);
        var signed = SandboxWorkerStopWire.sign(
                SandboxWorkerStopWire.requestBytes(request), pair.getPrivate());
        assertThatThrownBy(() -> BoundedUnixStopTransport.exchange(
                root.resolve("none.sock"), signed, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BoundedUnixStopTransport.exchange(
                root.resolve("none.sock"), signed, Duration.ofSeconds(6)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
