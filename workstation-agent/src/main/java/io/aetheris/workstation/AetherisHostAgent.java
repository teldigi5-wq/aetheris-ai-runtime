package io.aetheris.workstation;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;

public final class AetherisHostAgent {
    private AetherisHostAgent() {}

    public static void main(String[] args) throws Exception {
        requireWindows();
        Path configPath = configPath(args);
        AgentConfig config = AgentConfig.load(configPath);
        Files.createDirectories(config.stateDirectory());

        byte[] key = new WindowsDpapiSigningKeyProvider().load(config.signingKeyFile());
        ReplayCache replay = new ReplayCache(config.stateDirectory().resolve("replay-cache.txt"));
        EnvelopeVerifier verifier = new EnvelopeVerifier(config.hostId(), key, replay);
        Arrays.fill(key, (byte) 0);
        LeastPrivilegeDispatcher dispatcher = new LeastPrivilegeDispatcher(config);
        HostAgentServer server = new HostAgentServer(config, verifier, dispatcher);

        Path pidFile = config.stateDirectory().resolve("agent.pid");
        Files.writeString(pidFile, Long.toString(ProcessHandle.current().pid()), StandardCharsets.US_ASCII,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        CountDownLatch stop = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { server.close(); } catch (Exception ignored) {}
            try { Files.deleteIfExists(pidFile); } catch (Exception ignored) {}
            stop.countDown();
        }, "aetheris-host-agent-shutdown"));

        server.start();
        System.out.println("AetherisHostAgent listening on loopback port " + config.port() + " with shellCapability=false");
        stop.await();
    }

    private static Path configPath(String[] args) {
        if (args != null && args.length == 2 && "--config".equals(args[0])) return Path.of(args[1]);
        if (args != null && args.length != 0) throw new IllegalArgumentException("Usage: java -jar aetheris-host-agent.jar --config <agent.properties>");
        String env = System.getenv("AETHERIS_HOST_CONFIG");
        if (env == null || env.isBlank()) throw new IllegalArgumentException("AETHERIS_HOST_CONFIG or --config is required");
        return Path.of(env.trim());
    }

    private static void requireWindows() {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows")) {
            throw new IllegalStateException("AetherisHostAgent production runtime is Windows-only");
        }
    }
}
