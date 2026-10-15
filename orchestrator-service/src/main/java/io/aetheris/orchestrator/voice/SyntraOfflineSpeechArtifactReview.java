package io.aetheris.orchestrator.voice;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Stage 46.6: read-only model/voice artifact inventory for manual review.
 * This class never executes engines, loads weights, opens audio devices,
 * downloads files, grants owner consent, invokes tools or changes a file.
 *
 * SHA-256 must be supplied independently from a source the owner trusts.
 * Digest equality proves only file consistency, NOT software publisher
 * identity, a valid model license, hardware compatibility, or sandboxing.
 */
public final class SyntraOfflineSpeechArtifactReview {
    private static final long MAX_ARTIFACT_BYTES=4L*1024*1024*1024;
    private static final int BUFFER_SIZE=65536;

    public enum Role { WAKE, STT, TTS }
    public record Artifact(Role role,String engineId,String relativeName,
                           long declaredBytes,String manifestSha256) {}
    public record ReviewProfile(boolean offlineRequired,boolean micEnabled,
                                boolean speakerEnabled,boolean autostartEnabled,
                                boolean networkEnabled,List<Artifact> artifacts) {}
    public record Report(String verdict,boolean artifactDigestsMatch,
                         boolean microphoneAuthorized,boolean speakerAuthorized,
                         boolean modelExecutionAuthorized,boolean licenseCertified,
                         boolean deviceCertified,int artifactCount,String reason) {}

    private SyntraOfflineSpeechArtifactReview() {}

    /**
     * Check only three local model/voice files in a user-specified bundle.
     * This API has no auto-discovery or silent fallback. A missing or corrupt
     * role fails the entire review. No file contents are returned.
     */
    public static Report review(Path bundle,ReviewProfile profile,
                                Map<Role,String> independentlyTrustedHashes) {
        if (bundle==null || profile==null || independentlyTrustedHashes==null)
            return deny("REVIEW_INPUT_MISSING");
        if (!profile.offlineRequired() || profile.micEnabled()
                || profile.speakerEnabled() || profile.autostartEnabled()
                || profile.networkEnabled())
            return deny("REVIEW_PROFILE_ACTIVATION_DENIED");
        if (profile.artifacts()==null || profile.artifacts().size()!=3
                || independentlyTrustedHashes.size()!=3)
            return deny("REVIEW_EXACT_THREE_ROLES_REQUIRED");
        Path root=bundle.toAbsolutePath().normalize();
        try {
            if (symlinkInAncestors(root) ||
                    !Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS))
                return deny("REVIEW_BUNDLE_NOT_REGULAR_DIRECTORY");
            Set<Role> seenRoles=new HashSet<>();
            Set<String> seenNames=new HashSet<>();
            for (Artifact a:profile.artifacts()) {
                if (a==null || a.role()==null ||
                        !seenRoles.add(a.role()) ||
                        !validId(a.engineId()) ||
                        !validArtifactName(a.relativeName()) ||
                        !seenNames.add(a.relativeName()) ||
                        a.declaredBytes()<=0 || a.declaredBytes()>MAX_ARTIFACT_BYTES ||
                        !validHash(a.manifestSha256()))
                    return deny("REVIEW_ARTIFACT_MANIFEST_INVALID");
                String external=independentlyTrustedHashes.get(a.role());
                if (!validHash(external) || !a.manifestSha256().equals(external))
                    return deny("REVIEW_INDEPENDENT_DIGEST_MISMATCH");
                // Only flat model/voice data files, not binaries or folders.
                Path file=root.resolve(a.relativeName()).normalize();
                if (!root.equals(file.getParent()) ||
                        symlinkInAncestors(file) ||
                        !Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))
                    return deny("REVIEW_ARTIFACT_PATH_DENIED");
                long before=Files.size(file);
                if (before!=a.declaredBytes())
                    return deny("REVIEW_ARTIFACT_SIZE_MISMATCH");
                String calculated=sha256(file,a.declaredBytes());
                long after=Files.size(file);
                if (after!=before || !calculated.equals(external))
                    return deny("REVIEW_ARTIFACT_CONTENT_MISMATCH");
            }
            if (seenRoles.size()!=Role.values().length)
                return deny("REVIEW_MISSING_SPEECH_ROLE");
            return new Report("ARTIFACT_HASH_REVIEW_ONLY",true,false,false,
                    false,false,false,seenRoles.size(),
                    "INDEPENDENT_LICENSE_DEVICE_AND_OWNER_APPROVAL_REQUIRED");
        } catch (IOException | SecurityException | NoSuchAlgorithmException error) {
            // No local user path, filename, file content, or error details
            // are displayed to the caller.
            return deny("REVIEW_ARTIFACT_UNREADABLE");
        }
    }

    private static String sha256(Path file,long expectedBytes)
            throws IOException,NoSuchAlgorithmException {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        byte[] buffer=new byte[BUFFER_SIZE];
        long count=0;
        try(InputStream stream=Files.newInputStream(file,LinkOption.NOFOLLOW_LINKS)) {
            int read;
            while((read=stream.read(buffer))!=-1) {
                count+=read;
                if (count>expectedBytes || count>MAX_ARTIFACT_BYTES)
                    throw new IOException("Bound exceeded");
                digest.update(buffer,0,read);
            }
        } finally {
            java.util.Arrays.fill(buffer,(byte)0);
        }
        if (count!=expectedBytes) throw new IOException("Content length changed");
        return java.util.HexFormat.of().formatHex(digest.digest());
    }

    private static boolean symlinkInAncestors(Path p) {
        for(Path path=p;path!=null;path=path.getParent())
            if (Files.isSymbolicLink(path)) return true;
        return false;
    }

    private static boolean validId(String value) {
        return value!=null && value.matches("[a-z][a-z0-9_-]{1,47}");
    }

    private static boolean validArtifactName(String value) {
        if (value==null || value.length()>100 ||
                !value.matches("[A-Za-z0-9][A-Za-z0-9_.-]{1,99}") ||
                value.contains(".."))
            return false;
        String name=value.toLowerCase(Locale.ROOT);
        // Data-only review. It is deliberately not an executable loader.
        return name.endsWith(".onnx") || name.endsWith(".bin") ||
                name.endsWith(".tflite") || name.endsWith(".json");
    }

    private static boolean validHash(String value) {
        return value!=null && value.matches("[0-9a-f]{64}");
    }

    private static Report deny(String reason) {
        return new Report("DENIED",false,false,false,false,false,false,0,reason);
    }
}
