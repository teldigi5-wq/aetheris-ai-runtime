package io.aetheris.orchestrator.execution;

import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Set;

/** Disposable Linux CI fixture: STOP witness stored independently of
 * PostgreSQL. Anchor digest is held separately in the test controller.
 * It is NOT a real external immutable audit or hardware trust root.
 */
final class SyntheticStopAuditFileLab {
    private static final String NAME = "signed-stop.audit";
    private SyntheticStopAuditFileLab() {}

    record Anchored(Path recordPath, String pinnedDigest) {}

    static Anchored appendOnce(Path directory,
            SyntheticStopAuditWitnessLab.Witness witness) throws Exception {
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(directory))
            throw new SecurityException("CI witness directory not trusted");
        Path file = directory.resolve(NAME);
        byte[] content = SyntheticStopAuditWitnessLab.canonicalText(witness)
                .getBytes(StandardCharsets.US_ASCII);
        try (var channel = FileChannel.open(file,
                Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE,
                        StandardOpenOption.SYNC),
                PosixFilePermissions.asFileAttribute(
                        PosixFilePermissions.fromString("rw-------")))) {
            ByteBuffer data = ByteBuffer.wrap(content);
            while (data.hasRemaining()) channel.write(data);
            channel.force(true);
        }
        try (var parent = FileChannel.open(directory, StandardOpenOption.READ)) {
            parent.force(true);
        }
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
        return new Anchored(file, HexFormat.of().formatHex(digest));
    }

    static SyntheticStopAuditWitnessLab.Witness readAnchored(Anchored anchor)
            throws Exception {
        if (anchor == null || anchor.pinnedDigest() == null
                || !anchor.pinnedDigest().matches("[0-9a-f]{64}"))
            throw new SecurityException("Missing independent audit anchor");
        Path file = anchor.recordPath();
        if (Files.isSymbolicLink(file)
                || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                || Files.size(file) > 1024
                || Files.getPosixFilePermissions(file).stream().anyMatch(p ->
                    p == java.nio.file.attribute.PosixFilePermission.GROUP_WRITE
                    || p == java.nio.file.attribute.PosixFilePermission.OTHERS_WRITE
                    || p == java.nio.file.attribute.PosixFilePermission.OTHERS_READ))
            throw new SecurityException("Untrusted audit record file");
        byte[] bytes = Files.readAllBytes(file);
        byte[] actual = MessageDigest.getInstance("SHA-256").digest(bytes);
        if (!MessageDigest.isEqual(actual, HexFormat.of().parseHex(anchor.pinnedDigest())))
            throw new SecurityException("Synthetic STOP audit record changed or truncated");
        return SyntheticStopAuditWitnessLab.parseCanonicalText(
                new String(bytes, StandardCharsets.US_ASCII));
    }
}
