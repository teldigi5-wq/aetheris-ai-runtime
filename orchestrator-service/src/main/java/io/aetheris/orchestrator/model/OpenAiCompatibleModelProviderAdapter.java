package io.aetheris.orchestrator.model;

import io.aetheris.orchestrator.agent.ModelClass;
import io.aetheris.orchestrator.vault.ScopedCredentialAccessService;
import io.aetheris.orchestrator.vault.SecretAccessRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.aetheris.orchestrator.vault.SecretAccessRequest.Caller.CLOUD_MODEL_PROVIDER;
import static io.aetheris.orchestrator.vault.SecretAccessRequest.Purpose.MODEL_INFERENCE;

@Service
@ConditionalOnProperty(prefix = "aetheris.cloud-model", name = "enabled", havingValue = "true")
public class OpenAiCompatibleModelProviderAdapter implements ModelProviderAdapter {

    private final ScopedCredentialAccessService secretAccess;
    private final String providerId;
    private final String baseUrl;
    private final String configuredModel;
    private final String credentialAlias;
    private final boolean zeroCost;
    private final long dailyQuotaUnits;
    private final BigDecimal costPerThousandUnitsUsd;
    private final BigDecimal dailyBudgetUsd;

    public OpenAiCompatibleModelProviderAdapter(
            ScopedCredentialAccessService secretAccess,
            @Value("${aetheris.cloud-model.provider-id:openai-compatible}") String providerId,
            @Value("${aetheris.cloud-model.base-url:}") String baseUrl,
            @Value("${aetheris.cloud-model.model:}") String configuredModel,
            @Value("${aetheris.cloud-model.credential-alias:cloud-model-api-key}") String credentialAlias,
            @Value("${aetheris.cloud-model.zero-cost:false}") boolean zeroCost,
            @Value("${aetheris.cloud-model.daily-quota-units:0}") long dailyQuotaUnits,
            @Value("${aetheris.cloud-model.cost-per-thousand-units-usd:0}") BigDecimal costPerThousandUnitsUsd,
            @Value("${aetheris.cloud-model.daily-budget-usd:0}") BigDecimal dailyBudgetUsd) {
        this.secretAccess = secretAccess;
        this.providerId = providerId.trim();
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
        this.configuredModel = configuredModel == null ? "" : configuredModel.trim();
        this.credentialAlias = credentialAlias.trim();
        this.zeroCost = zeroCost;
        this.dailyQuotaUnits = dailyQuotaUnits <= 0 ? Long.MAX_VALUE : dailyQuotaUnits;
        this.costPerThousandUnitsUsd = costPerThousandUnitsUsd == null ? BigDecimal.ZERO : costPerThousandUnitsUsd.max(BigDecimal.ZERO);
        this.dailyBudgetUsd = dailyBudgetUsd == null ? BigDecimal.ZERO : dailyBudgetUsd.max(BigDecimal.ZERO);
    }

    @Override public String id() { return providerId; }
    @Override public boolean local() { return false; }
    @Override public boolean zeroCost() { return zeroCost; }
    @Override public Set<ModelClass> supportedClasses() { return Set.copyOf(EnumSet.allOf(ModelClass.class)); }
    @Override public long dailyQuotaUnits() { return dailyQuotaUnits; }
    @Override public BigDecimal costPerThousandUnitsUsd() { return costPerThousandUnitsUsd; }
    @Override public BigDecimal dailyBudgetUsd() { return dailyBudgetUsd; }

    @Override
    public ModelProviderSnapshot snapshot() {
        boolean secureEndpoint = baseUrl.startsWith("https://");
        boolean credentialAvailable = secretAccess.available(new SecretAccessRequest(CLOUD_MODEL_PROVIDER, MODEL_INFERENCE, credentialAlias));
        boolean available = secureEndpoint && !configuredModel.isBlank() && credentialAvailable;
        String detail = available
                ? "Cloud provider is configured; runtime invocation health is checked on use"
                : "Cloud provider unavailable: HTTPS endpoint, model and credential alias are required";
        return new ModelProviderSnapshot(providerId, available, false, zeroCost, configuredModel, detail);
    }

    @Override
    @SuppressWarnings("unchecked")
    public LocalGenerateResponse generate(LocalGenerateRequest request) {
        if (!snapshot().available()) throw new IllegalStateException("Cloud provider is not fully configured");
        char[] secret = secretAccess.resolve(new SecretAccessRequest(CLOUD_MODEL_PROVIDER, MODEL_INFERENCE, credentialAlias))
                .orElseThrow(() -> new IllegalStateException("Cloud-model credential is unavailable"));
        try {
            String model = request.model() == null || request.model().isBlank() ? configuredModel : request.model().trim();
            Map<String, Object> body = RestClient.create(baseUrl).post()
                    .uri("/v1/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + new String(secret))
                    .body(Map.of(
                            "model", model,
                            "messages", List.of(Map.of("role", "user", "content", request.prompt())),
                            "stream", false))
                    .retrieve()
                    .body(Map.class);
            if (body == null || !(body.get("choices") instanceof List<?> choices) || choices.isEmpty()) {
                throw new IllegalStateException("Cloud provider returned no choices");
            }
            Object first = choices.getFirst();
            if (!(first instanceof Map<?, ?> choice) || !(choice.get("message") instanceof Map<?, ?> message)
                    || message.get("content") == null) {
                throw new IllegalStateException("Cloud provider returned an invalid chat-completions payload");
            }
            return new LocalGenerateResponse(providerId, model, String.valueOf(message.get("content")));
        } finally {
            Arrays.fill(secret, '\0');
        }
    }
}
