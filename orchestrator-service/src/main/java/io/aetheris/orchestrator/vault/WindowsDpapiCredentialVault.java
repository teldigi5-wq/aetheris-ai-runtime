package io.aetheris.orchestrator.vault;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
@ConditionalOnProperty(name = "aetheris.vault.windows-dpapi.enabled", havingValue = "true")
public class WindowsDpapiCredentialVault implements OperatingSystemCredentialVault {
    private static final String PROTECT_SCRIPT = "$b=[Convert]::FromBase64String([Console]::In.ReadToEnd().Trim());$c=[Security.Cryptography.ProtectedData]::Protect($b,$null,[Security.Cryptography.DataProtectionScope]::CurrentUser);[Console]::Out.Write([Convert]::ToBase64String($c))";
    private static final String UNPROTECT_SCRIPT = "$b=[Convert]::FromBase64String([Console]::In.ReadToEnd().Trim());$p=[Security.Cryptography.ProtectedData]::Unprotect($b,$null,[Security.Cryptography.DataProtectionScope]::CurrentUser);[Console]::Out.Write([Convert]::ToBase64String($p))";

    private final Path directory;
    private volatile Boolean verified;

    public WindowsDpapiCredentialVault(@Value("${aetheris.vault.windows-dpapi.directory:${user.home}/.aetheris/vault}") String directory) {
        this.directory = Path.of(directory).toAbsolutePath().normalize();
    }

    @Override
    public Optional<char[]> resolve(String alias) {
        Path path = pathFor(alias);
        if (!Files.isRegularFile(path)) return Optional.empty();
        byte[] cipher = null;
        byte[] plain = null;
        try {
            cipher = Base64.getDecoder().decode(Files.readString(path, StandardCharsets.US_ASCII).trim());
            plain = runDpapi(UNPROTECT_SCRIPT, cipher);
            CharBuffer chars = StandardCharsets.UTF_8.decode(ByteBuffer.wrap(plain));
            char[] out = new char[chars.remaining()];
            chars.get(out);
            return Optional.of(out);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to resolve credential from Windows DPAPI vault", e);
        } finally {
            wipe(cipher);
            wipe(plain);
        }
    }

    public void store(String alias, char[] secret) {
        requireWindows();
        if (secret == null || secret.length == 0) throw new IllegalArgumentException("Secret value is required");
        ByteBuffer encoded = StandardCharsets.UTF_8.encode(CharBuffer.wrap(secret));
        byte[] plain = new byte[encoded.remaining()];
        encoded.get(plain);
        byte[] cipher = null;
        try {
            Files.createDirectories(directory);
            cipher = runDpapi(PROTECT_SCRIPT, plain);
            Files.writeString(pathFor(alias), Base64.getEncoder().encodeToString(cipher), StandardCharsets.US_ASCII,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to store credential in Windows DPAPI vault", e);
        } finally {
            wipe(plain);
            wipe(cipher);
        }
    }

    public boolean delete(String alias) {
        try {
            return Files.deleteIfExists(pathFor(alias));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to delete Windows DPAPI credential", e);
        }
    }

    @Override
    public CredentialDescriptor describe(String alias) {
        String normalized = requireAlias(alias);
        return new CredentialDescriptor(normalized, backendName(), Files.isRegularFile(pathFor(normalized)));
    }

    @Override
    public String backendName() { return "windows-dpapi-current-user"; }

    @Override
    public boolean hardwareBacked() {
        Boolean cached = verified;
        if (cached != null) return cached;
        synchronized (this) {
            if (verified == null) verified = selfTest();
            return verified;
        }
    }

    public DpapiProbe probe() {
        boolean ok = hardwareBacked();
        return new DpapiProbe(ok ? "VERIFIED_ON_THIS_WINDOWS_USER" : "NOT_VERIFIED", backendName(), ok,
                ok ? "DPAPI CurrentUser protect/unprotect self-test succeeded without exposing the probe secret"
                        : "DPAPI adapter is configured but the CurrentUser protect/unprotect self-test did not verify");
    }

    private boolean selfTest() {
        if (!isWindows()) return false;
        byte[] input = new byte[32];
        byte[] cipher = null;
        byte[] plain = null;
        new SecureRandom().nextBytes(input);
        try {
            cipher = runDpapi(PROTECT_SCRIPT, input);
            plain = runDpapi(UNPROTECT_SCRIPT, cipher);
            return MessageDigest.isEqual(input, plain);
        } catch (Exception ignored) {
            return false;
        } finally {
            wipe(input);
            wipe(cipher);
            wipe(plain);
        }
    }

    private byte[] runDpapi(String script, byte[] input) throws Exception {
        requireWindows();
        Process process = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", script)
                .redirectErrorStream(true).start();
        try (OutputStream out = process.getOutputStream()) {
            out.write(Base64.getEncoder().encode(input));
        }
        if (!process.waitFor(10, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("Windows DPAPI helper timed out");
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        if (process.exitValue() != 0) throw new IllegalStateException("Windows DPAPI helper failed");
        try {
            return Base64.getDecoder().decode(output);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Windows DPAPI helper returned invalid output", e);
        }
    }

    private Path pathFor(String alias) {
        String normalized = requireAlias(alias);
        try {
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(normalized.getBytes(StandardCharsets.UTF_8)));
            return directory.resolve(digest + ".dpapi").normalize();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to derive DPAPI vault entry path", e);
        }
    }

    private String requireAlias(String alias) {
        if (alias == null || alias.isBlank()) throw new IllegalArgumentException("Credential alias is required");
        String normalized = alias.trim();
        if (normalized.length() > 120 || !normalized.matches("[A-Za-z0-9._:-]{1,120}")) {
            throw new IllegalArgumentException("Credential alias is invalid");
        }
        return normalized;
    }

    private void requireWindows() {
        if (!isWindows()) throw new IllegalStateException("Windows DPAPI vault can only run on Windows");
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows");
    }

    private void wipe(byte[] value) { if (value != null) Arrays.fill(value, (byte) 0); }

    public record DpapiProbe(String status, String backend, boolean verified, String detail) {}
}
