package io.aetheris.orchestrator.voice;

import io.aetheris.orchestrator.voice.SyntraOfflineSpeechArtifactReview.Artifact;
import io.aetheris.orchestrator.voice.SyntraOfflineSpeechArtifactReview.ReviewProfile;
import io.aetheris.orchestrator.voice.SyntraOfflineSpeechArtifactReview.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SyntraOfflineSpeechArtifactReviewTest {
    @TempDir Path dir;
    private static final String WAKE="synthetic-wake.onnx";
    private static final String STT="synthetic-stt.bin";
    private static final String TTS="synthetic-tts.onnx";

    record Fixture(ReviewProfile profile,Map<Role,String> trusted) {}

    private String digest(byte[] content) throws Exception {
        return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }

    private Fixture fixture() throws Exception {
        var artifacts=new ArrayList<Artifact>();
        var trusted=new EnumMap<Role,String>(Role.class);
        for(var role:Role.values()) {
            String name=switch(role) {
                case WAKE -> WAKE;
                case STT -> STT;
                case TTS -> TTS;
            };
            byte[] bytes=("CI_SYNTHETIC_DATA_ONLY_FOR_"+role).getBytes(StandardCharsets.UTF_8);
            Files.write(dir.resolve(name),bytes);
            String hash=digest(bytes);
            artifacts.add(new Artifact(role,"ci_speech_"+role.name().toLowerCase(),name,bytes.length,hash));
            trusted.put(role,hash);
        }
        return new Fixture(new ReviewProfile(true,false,false,false,false,List.copyOf(artifacts)),
                Map.copyOf(trusted));
    }

    @Test void independentTrustedHashesPermitFileReviewButNeverAuthorizeExecution() throws Exception {
        Fixture fx=fixture();
        var result=SyntraOfflineSpeechArtifactReview.review(dir,fx.profile(),fx.trusted());
        assertThat(result.verdict()).isEqualTo("ARTIFACT_HASH_REVIEW_ONLY");
        assertThat(result.artifactDigestsMatch()).isTrue();
        assertThat(result.artifactCount()).isEqualTo(3);
        assertThat(result.microphoneAuthorized()).isFalse();
        assertThat(result.speakerAuthorized()).isFalse();
        assertThat(result.modelExecutionAuthorized()).isFalse();
        assertThat(result.deviceCertified()).isFalse();
        assertThat(result.licenseCertified()).isFalse();
    }

    @Test void unknownOrSelfAssertedDigestCannotCountAsIndependentTrust() throws Exception {
        Fixture fx=fixture();
        var insufficient=new EnumMap<Role,String>(Role.class);
        insufficient.putAll(fx.trusted());
        insufficient.put(Role.WAKE,"0".repeat(64));
        assertThat(SyntraOfflineSpeechArtifactReview.review(dir,fx.profile(),insufficient).reason())
                .isEqualTo("REVIEW_INDEPENDENT_DIGEST_MISMATCH");
        insufficient.remove(Role.WAKE);
        assertThat(SyntraOfflineSpeechArtifactReview.review(dir,fx.profile(),insufficient).reason())
                .isEqualTo("REVIEW_EXACT_THREE_ROLES_REQUIRED");
    }

    @Test void tamperedArtifactIsDeniedWithoutReadingOtherDeviceState() throws Exception {
        Fixture fx=fixture();
        Files.writeString(dir.resolve(STT),"tampered unrelated payload");
        var r=SyntraOfflineSpeechArtifactReview.review(dir,fx.profile(),fx.trusted());
        assertThat(r.verdict()).isEqualTo("DENIED");
        assertThat(r.reason()).matches("REVIEW_ARTIFACT_(SIZE|CONTENT)_MISMATCH");
        assertThat(r.modelExecutionAuthorized()).isFalse();
    }

    @Test void pathTraversalAndExecutableExtensionsAreDenied() throws Exception {
        Fixture fx=fixture();
        for(String unsafe:new String[]{"../data.bin","nested/model.onnx","C:\\windows\\x.bin",
                "/tmp/mic.bin","evil.ps1","model.exe","synthetic..onnx","\\u0000.wav"}) {
            var copy=new ArrayList<>(fx.profile().artifacts());
            Artifact a=copy.get(0);
            copy.set(0,new Artifact(a.role(),a.engineId(),unsafe,a.declaredBytes(),a.manifestSha256()));
            var p=new ReviewProfile(true,false,false,false,false,copy);
            var report=SyntraOfflineSpeechArtifactReview.review(dir,p,fx.trusted());
            assertThat(report.verdict()).as("unsafe name").isEqualTo("DENIED");
            assertThat(report.modelExecutionAuthorized()).isFalse();
        }
    }

    @Test void symlinkedArtifactsAndParentDirectoriesAreRejected() throws Exception {
        Fixture fx=fixture();
        Path external=Files.createTempDirectory("syntra-speech-foreign");
        try {
            Files.delete(dir.resolve(WAKE));
            Files.write(external.resolve(WAKE),"foreign".getBytes(StandardCharsets.UTF_8));
            Files.createSymbolicLink(dir.resolve(WAKE),external.resolve(WAKE));
            assertThat(SyntraOfflineSpeechArtifactReview.review(dir,fx.profile(),fx.trusted()).reason())
                    .isEqualTo("REVIEW_ARTIFACT_PATH_DENIED");
            Path shortcut=dir.getParent().resolve("speech-link-"+java.util.UUID.randomUUID());
            try {
                Files.createSymbolicLink(shortcut,dir);
                assertThat(SyntraOfflineSpeechArtifactReview.review(shortcut,fx.profile(),fx.trusted()).reason())
                        .isEqualTo("REVIEW_BUNDLE_NOT_REGULAR_DIRECTORY");
            } finally {
                Files.deleteIfExists(shortcut);
            }
        } finally {
            Files.deleteIfExists(external.resolve(WAKE));
            Files.delete(external);
        }
    }

    @Test void noNetworkMicSpeakerOrStartupCanBeEnabledThroughProfile() throws Exception {
        Fixture fx=fixture();
        for(int i=0;i<5;i++) {
            var p=new ReviewProfile(i!=0,i==1,i==2,i==3,i==4,fx.profile().artifacts());
            assertThat(SyntraOfflineSpeechArtifactReview.review(dir,p,fx.trusted()).reason())
                    .isEqualTo("REVIEW_PROFILE_ACTIVATION_DENIED");
        }
    }

    @Test void duplicateRolesAndMissingModelFileAlwaysFailClosed() throws Exception {
        Fixture fx=fixture();
        var duplicate=new ArrayList<>(fx.profile().artifacts());
        duplicate.set(1,duplicate.get(0));
        assertThat(SyntraOfflineSpeechArtifactReview.review(dir,
                new ReviewProfile(true,false,false,false,false,duplicate),fx.trusted()).reason())
                .isEqualTo("REVIEW_ARTIFACT_MANIFEST_INVALID");
        Files.delete(dir.resolve(TTS));
        assertThat(SyntraOfflineSpeechArtifactReview.review(dir,fx.profile(),fx.trusted()).reason())
                .isEqualTo("REVIEW_ARTIFACT_PATH_DENIED");
    }

    @Test void missingReviewInputsNeverCreateApproval() {
        var r=SyntraOfflineSpeechArtifactReview.review(dir,null,Map.of());
        assertThat(r.verdict()).isEqualTo("DENIED");
        assertThat(r.microphoneAuthorized()).isFalse();
        assertThat(r.modelExecutionAuthorized()).isFalse();
    }
}
