package io.aetheris.orchestrator.model;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

@Service
public class LocalModelService {

    private final String baseUrl;
    private final String configuredModel;
    private final RestClient client;

    public LocalModelService(
            RestClient.Builder builder,
            @Value("${aetheris.local-model.base-url:http://localhost:11434}") String baseUrl,
            @Value("${aetheris.local-model.model:qwen2.5:3b}") String configuredModel) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.configuredModel = configuredModel;
        this.client = builder.baseUrl(this.baseUrl).build();
    }

    public LocalModelHealth health() {
        try {
            client.get().uri("/api/tags").retrieve().toBodilessEntity();
            return new LocalModelHealth(true, "ollama", baseUrl, configuredModel, "Local model endpoint is reachable");
        } catch (RestClientException exception) {
            return new LocalModelHealth(false, "ollama", baseUrl, configuredModel, safeMessage(exception));
        }
    }

    @SuppressWarnings("unchecked")
    public LocalGenerateResponse generate(LocalGenerateRequest request) {
        String model = request.model() == null || request.model().isBlank()
                ? configuredModel
                : request.model().trim();
        try {
            Map<String, Object> result = client.post()
                    .uri("/api/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "model", model,
                            "prompt", request.prompt(),
                            "stream", false))
                    .retrieve()
                    .body(Map.class);

            if (result == null || result.get("response") == null) {
                throw new IllegalStateException("Local model returned no response payload");
            }
            return new LocalGenerateResponse("ollama", model, String.valueOf(result.get("response")));
        } catch (RestClientException exception) {
            throw new IllegalStateException("Local model endpoint is unavailable: " + safeMessage(exception), exception);
        }
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }
}
