package io.aetheris.orchestrator.syntracore;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AdaptationSecretScrubber {
    private static final String REDACTED = "[REDACTED_SECRET]";
    private static final Pattern PRIVATE_KEY = Pattern.compile(
            "-----BEGIN(?: [A-Z0-9]+)? PRIVATE KEY-----",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern BEARER_TOKEN = Pattern.compile(
            "(?i)\\bBearer\\s+[A-Za-z0-9._~+/=-]{12,}");
    private static final Pattern GITHUB_TOKEN = Pattern.compile(
            "\\bgh(?:p|o|u|s|r)_[A-Za-z0-9]{20,}\\b");
    private static final Pattern AWS_ACCESS_KEY = Pattern.compile(
            "\\b(?:AKIA|ASIA)[A-Z0-9]{16}\\b");
    private static final Pattern LABELED_SECRET = Pattern.compile(
            "(?i)\\b(api[_-]?key|access[_-]?token|refresh[_-]?token|password|passwd|secret)\\s*[:=]\\s*([^\\s,;]+)");

    public ScrubResult scrub(String content) {
        Objects.requireNonNull(content, "content");
        if (PRIVATE_KEY.matcher(content).find()) {
            throw new IllegalArgumentException("private key material is not eligible for adaptation data");
        }

        Replacement bearer = replace(content, BEARER_TOKEN, REDACTED);
        Replacement github = replace(bearer.content(), GITHUB_TOKEN, REDACTED);
        Replacement aws = replace(github.content(), AWS_ACCESS_KEY, REDACTED);
        Replacement labeled = replaceLabeled(aws.content());
        return new ScrubResult(
                labeled.content(),
                bearer.count() + github.count() + aws.count() + labeled.count());
    }

    private Replacement replace(String input, Pattern pattern, String replacement) {
        Matcher matcher = pattern.matcher(input);
        StringBuffer output = new StringBuffer();
        int count = 0;
        while (matcher.find()) {
            count++;
            matcher.appendReplacement(output, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(output);
        return new Replacement(output.toString(), count);
    }

    private Replacement replaceLabeled(String input) {
        Matcher matcher = LABELED_SECRET.matcher(input);
        StringBuffer output = new StringBuffer();
        int count = 0;
        while (matcher.find()) {
            count++;
            String replacement = matcher.group(1) + "=" + REDACTED;
            matcher.appendReplacement(output, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(output);
        return new Replacement(output.toString(), count);
    }

    public record ScrubResult(String content, int redactionCount) {
        public ScrubResult {
            content = Objects.requireNonNull(content, "content");
            if (redactionCount < 0) {
                throw new IllegalArgumentException("redactionCount must not be negative");
            }
        }
    }

    private record Replacement(String content, int count) {}
}
