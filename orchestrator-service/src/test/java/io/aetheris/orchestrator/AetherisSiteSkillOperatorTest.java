package io.aetheris.orchestrator;

import io.aetheris.orchestrator.operator.AutonomousWebTaskService;
import io.aetheris.orchestrator.operator.SiteSkillTypes.SiteSkillKind;
import io.aetheris.orchestrator.operator.SiteSkillTypes.WebTaskExecuteRequest;
import io.aetheris.orchestrator.operator.SiteSkillTypes.WebTaskRequest;
import io.aetheris.orchestrator.operator.SiteSkillTypes.WebTaskStatus;
import io.aetheris.orchestrator.policy.OperationMode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class AetherisSiteSkillOperatorTest {

    @Autowired AutonomousWebTaskService tasks;

    @Test
    void starterCatalogExistsButDoesNotPretendRealSitesAreValidated() {
        var skills = tasks.skills();

        assertThat(skills).extracting(skill -> skill.id()).containsExactlyInAnyOrder(
                "linkedin.profile.inspect",
                "linkedin.profile.update-about",
                "vercel.deployment.inspect",
                "vercel.deployment.trigger");
        assertThat(skills).allMatch(skill -> !skill.physicallyValidated());
    }

    @Test
    void readOnlySkillCompilesWithBoundedRetryButRemainsValidationBlocked() {
        var compilation = tasks.compile(request(
                "career-director",
                OperationMode.BALANCED,
                false,
                "linkedin.profile.inspect",
                Map.of("targetUrl", "https://www.linkedin.com/in/example/")));

        assertThat(compilation.status()).isEqualTo(WebTaskStatus.SKILL_VALIDATION_REQUIRED);
        assertThat(compilation.skill().kind()).isEqualTo(SiteSkillKind.OBSERVE);
        assertThat(compilation.maxAutomaticAttempts()).isEqualTo(2);
        assertThat(compilation.blindRetryAllowed()).isTrue();
        assertThat(compilation.browserWorkflow().allowedDomains()).containsExactly("linkedin.com");
    }

    @Test
    void mutationSkillRequiresApprovalAndNeverGetsBlindRetry() {
        var compilation = tasks.compile(request(
                "career-director",
                OperationMode.BALANCED,
                false,
                "linkedin.profile.update-about",
                Map.of("targetUrl", "https://www.linkedin.com/in/example/")));

        assertThat(compilation.status()).isEqualTo(WebTaskStatus.SKILL_VALIDATION_REQUIRED);
        assertThat(compilation.skill().kind()).isEqualTo(SiteSkillKind.MUTATE);
        assertThat(compilation.maxAutomaticAttempts()).isEqualTo(1);
        assertThat(compilation.blindRetryAllowed()).isFalse();
        assertThat(compilation.browserPlan().requiresApproval()).isTrue();
        assertThat(compilation.warnings()).anyMatch(value -> value.contains("never receive automatic blind retries"));
    }

    @Test
    void privateModeStillOutranksSiteSkillAndBlocksProtectedRemoteMutation() {
        var compilation = tasks.compile(request(
                "career-director",
                OperationMode.PRIVATE,
                true,
                "linkedin.profile.update-about",
                Map.of("targetUrl", "https://www.linkedin.com/in/example/")));

        assertThat(compilation.status()).isEqualTo(WebTaskStatus.BLOCKED);
        assertThat(compilation.browserPlan().blockedReasons()).isNotEmpty();
    }

    @Test
    void siteSkillCannotEscapeItsDomainAllowlist() {
        var compilation = tasks.compile(request(
                "career-director",
                OperationMode.BALANCED,
                false,
                "linkedin.profile.inspect",
                Map.of("targetUrl", "https://example.com/profile")));

        assertThat(compilation.status()).isEqualTo(WebTaskStatus.BLOCKED);
        assertThat(compilation.browserPlan().blockedReasons())
                .anyMatch(value -> value.contains("outside the workflow domain allowlist"));
    }

    @Test
    void executionRequiresEphemeralValueReferenceBeforeAnyMutationAttempt() {
        var response = tasks.execute(new WebTaskExecuteRequest(
                request(
                        "career-director",
                        OperationMode.BALANCED,
                        false,
                        "linkedin.profile.update-about",
                        Map.of("targetUrl", "https://www.linkedin.com/in/example/")),
                Map.of(),
                Map.of()));

        assertThat(response.status()).isEqualTo(WebTaskStatus.BLOCKED);
        assertThat(response.attempts()).isZero();
        assertThat(response.detail()).contains("linkedin.about");
    }

    @Test
    void suppliedValueRemainsUnableToExecuteUntilSkillPhysicalValidationExists() {
        var response = tasks.execute(new WebTaskExecuteRequest(
                request(
                        "career-director",
                        OperationMode.BALANCED,
                        false,
                        "linkedin.profile.update-about",
                        Map.of("targetUrl", "https://www.linkedin.com/in/example/")),
                Map.of("linkedin.about", "ephemeral test value"),
                Map.of()));

        assertThat(response.status()).isEqualTo(WebTaskStatus.SKILL_VALIDATION_REQUIRED);
        assertThat(response.attempts()).isZero();
        assertThat(response.browserExecution()).isNull();
    }

    @Test
    void unknownSiteSkillFailsClosed() {
        assertThatThrownBy(() -> tasks.compile(request(
                "career-director",
                OperationMode.BALANCED,
                false,
                "unknown.site.skill",
                Map.of("targetUrl", "https://example.com/"))))
                .isInstanceOf(java.util.NoSuchElementException.class)
                .hasMessageContaining("Unknown site skill");
    }

    private WebTaskRequest request(
            String agentId,
            OperationMode mode,
            boolean protectedData,
            String skillId,
            Map<String, String> parameters) {
        return new WebTaskRequest(null, agentId, mode, protectedData, skillId, parameters);
    }
}
