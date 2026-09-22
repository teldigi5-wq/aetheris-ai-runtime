package io.aetheris.workstation;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

public final class WindowsDpapiSigningKeyProvider {
    private static final String SCRIPT = "$b=[Convert]::FromBase64String([Console]::In.ReadToEnd().Trim());$p=[Security.Cryptography.ProtectedData]::Unprotect($b,$null,[Security.Cryptography.DataProtectionScope]::CurrentUser);[Console]::Out.Write([Convert]::ToBase64String($p))";

    public byte[] load(Path encryptedFile) throws Exception {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows")) {
            throw new IllegalStateException("AetherisHostAgent production key loading requires Windows DPAPI");
        }
        byte[] cipher = null;
        try {
            cipher = Base64.getDecoder().decode(Files.readString(encryptedFile, StandardCharsets.US_ASCII).trim());
            Process process = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", SCRIPT)
                    .redirectErrorStream(true).start();
            try (OutputStream out = process.getOutputStream()) {
                out.write(Base64.getEncoder().encode(cipher));
            }
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException("DPAPI key helper timed out");
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (process.exitValue() != 0) throw new IllegalStateException("DPAPI key helper failed");
            byte[] key = Base64.getDecoder().decode(output);
            if (key.length < 32) {
                Arrays.fill(key, (byte) 0);
                throw new IllegalStateException("Host signing key must be at least 32 bytes");
            }
            return key;
        } finally {
            if (cipher != null) Arrays.fill(cipher, (byte) 0);
        }
    }
}
