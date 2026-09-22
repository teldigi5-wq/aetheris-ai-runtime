package io.aetheris.workstation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

public final class HostAgentServer implements AutoCloseable {
    private static final int MAX_BODY_BYTES = 64 * 1024;
    private final AgentConfig config;
    private final EnvelopeVerifier verifier;
    private final LeastPrivilegeDispatcher dispatcher;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpServer server;
    private final ExecutorService executor;

    public HostAgentServer(AgentConfig config, EnvelopeVerifier verifier, LeastPrivilegeDispatcher dispatcher) throws IOException {
        this.config = Objects.requireNonNull(config);
        this.verifier = Objects.requireNonNull(verifier);
        this.dispatcher = Objects.requireNonNull(dispatcher);
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", config.port()), 32);
        this.executor = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "aetheris-host-agent");
            t.setDaemon(true);
            return t;
        });
        server.setExecutor(executor);
        server.createContext("/health", this::health);
        server.createContext("/v1/command", this::command);
    }

    public void start() { server.start(); }

    private void health(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) { send(exchange, 405, Map.of("error", "method_not_allowed")); return; }
        send(exchange, 200, Map.of(
                "status", "UP",
                "service", "AetherisHostAgent",
                "loopbackOnly", true,
                "hostId", config.hostId().toString(),
                "capabilities", List.of("system.telemetry", "process.status", "app.launch", "workspace.open"),
                "shellCapability", false));
    }

    private void command(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) { send(exchange, 405, Map.of("error", "method_not_allowed")); return; }
        try {
            byte[] body = readLimited(exchange.getRequestBody());
            HostCommandEnvelope envelope = parse(body);
            verifier.verifyAndClaim(envelope);
            Map<String, Object> result = dispatcher.execute(envelope);
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", "ACKNOWLEDGED");
            response.put("commandId", envelope.commandId().toString());
            response.put("capability", envelope.capability());
            response.put("result", result);
            send(exchange, 200, response);
        } catch (SecurityException e) {
            send(exchange, 403, Map.of("error", "command_rejected", "detail", safeMessage(e)));
        } catch (IllegalArgumentException e) {
            send(exchange, 400, Map.of("error", "invalid_command", "detail", safeMessage(e)));
        } catch (Exception e) {
            send(exchange, 500, Map.of("error", "host_action_failed", "detail", safeMessage(e)));
        }
    }

    private HostCommandEnvelope parse(byte[] body) {
        try {
            JsonNode root = mapper.readTree(body);
            if (root == null || !root.isObject()) throw new IllegalArgumentException("Command body must be a JSON object");
            Map<String, Object> args = root.has("arguments") && !root.get("arguments").isNull()
                    ? mapper.convertValue(root.get("arguments"), new TypeReference<Map<String, Object>>() {})
                    : Map.of();
            return new HostCommandEnvelope(
                    UUID.fromString(text(root, "commandId")),
                    UUID.fromString(text(root, "hostId")),
                    text(root, "capability"),
                    text(root, "action"),
                    args,
                    Instant.parse(text(root, "issuedAt")),
                    Instant.parse(text(root, "expiresAt")),
                    text(root, "signature"),
                    text(root, "mode"));
        } catch (IllegalArgumentException e) { throw e; }
        catch (Exception e) { throw new IllegalArgumentException("Unable to parse command envelope", e); }
    }

    private String text(JsonNode root, String field) {
        JsonNode value = root.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.asText().trim();
    }

    private byte[] readLimited(InputStream input) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int total = 0;
        for (int read; (read = input.read(buffer)) != -1;) {
            total += read;
            if (total > MAX_BODY_BYTES) throw new IllegalArgumentException("Command body exceeds 64 KiB limit");
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private void send(HttpExchange exchange, int status, Object value) throws IOException {
        byte[] bytes = mapper.writeValueAsBytes(value);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) { out.write(bytes); }
    }

    private String safeMessage(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) return e.getClass().getSimpleName();
        return message.length() <= 300 ? message : message.substring(0, 300);
    }

    @Override
    public void close() {
        server.stop(1);
        executor.shutdownNow();
        verifier.close();
    }
}
