package io.aetheris.orchestrator.syntracore;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

public record ContextEvidence(
        RetrievalEvidence source,
        String excerpt) {

    private static final String BEGIN_MARKER = "BEGIN_UNTRUSTED_EVIDENCE";
    private static final String END_MARKER = "END_UNTRUSTED_EVIDENCE";

    public ContextEvidence {
        source = Objects.requireNonNull(source, "source");
        if (excerpt == null || excerpt.isBlank()) {
            throw new IllegalArgumentException("context evidence excerpt must not be blank");
        }
        excerpt = excerpt.trim();
    }

    public String citation() {
        return source.evidenceAddress();
    }

    public String render() {
        String safeExcerpt = excerpt
                .replace(BEGIN_MARKER, "BEGIN_UNTRUSTED_EVIDENCE_TEXT")
                .replace(END_MARKER, "END_UNTRUSTED_EVIDENCE_TEXT");
        return "SOURCE: " + citation() + "\n"
                + "RETRIEVAL_SCORE: " + source.score() + "\n"
                + "RETRIEVAL_METHOD: " + source.retrievalMethod() + "\n"
                + "UPDATED_AT: " + source.updatedAt() + "\n"
                + BEGIN_MARKER + "\n"
                + safeExcerpt + "\n"
                + END_MARKER;
    }

    public int estimatedTokens() {
        return render().getBytes(StandardCharsets.UTF_8).length;
    }
}
