package io.aetheris.orchestrator.syntracore;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class SyntraContextAssemblyService {
    private final SyntraRetrievalService retrieval;
    private final Clock clock;

    @Autowired
    public SyntraContextAssemblyService(SyntraRetrievalService retrieval) {
        this(retrieval, Clock.systemUTC());
    }

    SyntraContextAssemblyService(SyntraRetrievalService retrieval, Clock clock) {
        this.retrieval = Objects.requireNonNull(retrieval, "retrieval");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public ContextPack assemble(ContextAssemblyRequest request) {
        Objects.requireNonNull(request, "request");

        List<RetrievalEvidence> candidates = retrieval.retrieve(new RetrievalQuery(
                request.scope(),
                request.query(),
                request.retrievalLimit()));
        if (candidates == null) {
            throw new IllegalStateException("retrieval returned no candidate collection");
        }

        Map<UUID, RetrievalEvidence> uniqueByNode = new LinkedHashMap<>();
        for (RetrievalEvidence candidate : candidates) {
            validateCandidate(request.scope(), candidate);
            uniqueByNode.merge(candidate.nodeId(), candidate, this::preferEvidence);
        }

        List<RetrievalEvidence> ranked = new ArrayList<>(uniqueByNode.values());
        ranked.sort(Comparator
                .comparingDouble(RetrievalEvidence::score)
                .reversed()
                .thenComparing(RetrievalEvidence::updatedAt, Comparator.reverseOrder())
                .thenComparing(RetrievalEvidence::evidenceAddress));

        List<ContextEvidence> selected = new ArrayList<>();
        int remainingTokens = request.budget().maxContextTokens();
        for (RetrievalEvidence candidate : ranked) {
            if (selected.size() >= request.budget().maxEvidenceItems() || remainingTokens <= 0) {
                break;
            }
            int itemLimit = Math.min(request.budget().maxEvidenceTokens(), remainingTokens);
            ContextEvidence fitted = fitEvidence(candidate, itemLimit);
            if (fitted == null) {
                continue;
            }
            selected.add(fitted);
            remainingTokens -= fitted.estimatedTokens();
        }

        int estimatedTokens = selected.stream().mapToInt(ContextEvidence::estimatedTokens).sum();
        List<String> citations = selected.stream().map(ContextEvidence::citation).toList();
        ContextQualityAssessment quality = quality(candidates.size(), uniqueByNode.size(), selected);

        return new ContextPack(
                request.scope(),
                request.query(),
                selected,
                citations,
                estimatedTokens,
                quality,
                clock.instant());
    }

    private void validateCandidate(RetrievalScope requestedScope, RetrievalEvidence candidate) {
        if (candidate == null) {
            throw new IllegalStateException("retrieval returned null evidence");
        }
        if (!requestedScope.equals(candidate.scope())) {
            throw new IllegalStateException("context assembly rejected evidence outside requested scope");
        }
        String expectedAddress = "aetheris-memory://"
                + requestedScope.kind()
                + "/"
                + requestedScope.scopeId()
                + "/"
                + candidate.nodeId();
        if (!expectedAddress.equals(candidate.evidenceAddress())) {
            throw new IllegalStateException("context assembly rejected forged or inconsistent evidence address");
        }
    }

    private RetrievalEvidence preferEvidence(RetrievalEvidence current, RetrievalEvidence candidate) {
        if (!current.evidenceAddress().equals(candidate.evidenceAddress())
                || !current.memoryKey().equals(candidate.memoryKey())) {
            throw new IllegalStateException("duplicate node id carried inconsistent evidence identity");
        }
        int scoreComparison = Double.compare(candidate.score(), current.score());
        if (scoreComparison > 0) {
            return candidate;
        }
        if (scoreComparison < 0) {
            return current;
        }
        if (candidate.updatedAt().isAfter(current.updatedAt())) {
            return candidate;
        }
        return current;
    }

    private ContextEvidence fitEvidence(RetrievalEvidence evidence, int tokenLimit) {
        String excerpt = evidence.excerpt().trim();
        if (excerpt.isEmpty() || tokenLimit <= 0) {
            return null;
        }

        ContextEvidence full = new ContextEvidence(evidence, excerpt);
        if (full.estimatedTokens() <= tokenLimit) {
            return full;
        }

        int[] codePoints = excerpt.codePoints().toArray();
        int low = 1;
        int high = codePoints.length;
        ContextEvidence best = null;
        while (low <= high) {
            int middle = low + ((high - low) / 2);
            String prefix = new String(codePoints, 0, middle);
            if (middle < codePoints.length) {
                prefix += "…";
            }
            ContextEvidence candidate = new ContextEvidence(evidence, prefix);
            if (candidate.estimatedTokens() <= tokenLimit) {
                best = candidate;
                low = middle + 1;
            } else {
                high = middle - 1;
            }
        }
        return best;
    }

    private ContextQualityAssessment quality(
            int candidateCount,
            int uniqueCandidateCount,
            List<ContextEvidence> selected) {
        if (selected.isEmpty()) {
            return new ContextQualityAssessment(
                    candidateCount,
                    uniqueCandidateCount,
                    0,
                    candidateCount - uniqueCandidateCount,
                    uniqueCandidateCount,
                    0d,
                    0d,
                    0d);
        }

        double average = selected.stream()
                .mapToDouble(item -> item.source().score())
                .average()
                .orElse(0d);
        double minimum = selected.stream()
                .mapToDouble(item -> item.source().score())
                .min()
                .orElse(0d);
        double maximum = selected.stream()
                .mapToDouble(item -> item.source().score())
                .max()
                .orElse(0d);

        return new ContextQualityAssessment(
                candidateCount,
                uniqueCandidateCount,
                selected.size(),
                candidateCount - uniqueCandidateCount,
                uniqueCandidateCount - selected.size(),
                average,
                minimum,
                maximum);
    }
}
