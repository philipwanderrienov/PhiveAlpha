package com.coinmind.ai.status;

import com.coinmind.ai.config.AiProperties;
import com.coinmind.ai.config.GeminiProperties;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class AiStatusService {

    private final AiProperties properties;
    private final GeminiProperties gemini;

    private volatile String runtimeState = null;
    private volatile String runtimeMessage = null;
    private volatile Integer lastHttpStatus = null;
    private volatile Instant lastCheckedAt = null;

    public AiStatusService(AiProperties properties, GeminiProperties gemini) {
        this.properties = properties;
        this.gemini = gemini;
    }

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.beans.factory.ObjectProvider<com.coinmind.ai.provider.GeminiAiProvider> provider;

    public AiEngineStatus current() {
        if ("gemini".equalsIgnoreCase(properties.provider())) {
            boolean keyConfigured = gemini.apiKey() != null && !gemini.apiKey().isBlank();
            String state = !properties.enabled() || !gemini.newsEnabled() ? "DISABLED"
                    : !keyConfigured ? "MISSING_API_KEY" : "CONFIGURED_UNVERIFIED";
            java.util.Map<String,Object> recovery = provider != null && provider.getIfAvailable() != null
                    ? provider.getIfAvailable().recoveryStatus() : java.util.Map.of();
            if (state.equals("CONFIGURED_UNVERIFIED")) {
                String circuit = (String) recovery.get("state");
                state = "AUTH_ERROR".equals(circuit) ? "AUTH_ERROR"
                        : "DAILY_QUOTA".equals(recovery.get("reasonCode")) ? "RATE_LIMITED"
                        : "OPEN".equals(circuit) || "HALF_OPEN".equals(circuit) ? "ERROR"
                        : "FRESH".equals(recovery.get("dataFreshness")) ? "READY" : "CONFIGURED_UNVERIFIED";
            }
            return new AiEngineStatus(state, properties.provider(), gemini.model(),
                    properties.enabled() && gemini.newsEnabled(), keyConfigured,
                    "Analisis deterministik tetap berjalan. Gemini hanya mendukung klasifikasi berita.", null, null, recovery);
        }
        boolean keyConfigured = properties.apiKey() != null
                && !properties.apiKey().isBlank();

        if (!properties.enabled()) {
            return status(
                    "DISABLED",
                    keyConfigured,
                    "AI engine is disabled."
            );
        }

        if ("mock".equalsIgnoreCase(properties.provider())) {
            return status(
                    "MOCK",
                    false,
                    "Mock AI mode is active. No paid API calls will be made."
            );
        }

        if (!keyConfigured) {
            return status(
                    "MISSING_API_KEY",
                    false,
                    "OpenAI API key is not configured."
            );
        }

        if (runtimeState != null) {
            return new AiEngineStatus(
                    runtimeState,
                    properties.provider(),
                    properties.model(),
                    true,
                    true,
                    runtimeMessage,
                    lastHttpStatus,
                    lastCheckedAt
            );
        }

        return status(
                "CONFIGURED_UNVERIFIED",
                true,
                "API key is configured, but the AI provider has not been verified since startup."
        );
    }

    public void markReady() {
        runtimeState = "READY";
        runtimeMessage = "AI engine is configured and responding.";
        lastHttpStatus = 200;
        lastCheckedAt = Instant.now();
    }

    public void markAuthError(int status, String message) {
        runtimeState = "AUTH_ERROR";
        runtimeMessage = message;
        lastHttpStatus = status;
        lastCheckedAt = Instant.now();
    }

    public void markRateLimited(int status, String message) {
        runtimeState = "RATE_LIMITED";
        runtimeMessage = message;
        lastHttpStatus = status;
        lastCheckedAt = Instant.now();
    }

    public void markError(Integer status, String message) {
        runtimeState = "ERROR";
        runtimeMessage = message;
        lastHttpStatus = status;
        lastCheckedAt = Instant.now();
    }

    private String displayModel() {
        return "mock".equalsIgnoreCase(properties.provider())
                ? "mock-signal-engine"
                : properties.model();
    }

    private AiEngineStatus status(
            String state,
            boolean keyConfigured,
            String message
    ) {
        return new AiEngineStatus(
                state,
                properties.provider(),
                displayModel(),
                properties.enabled(),
                keyConfigured,
                message,
                lastHttpStatus,
                lastCheckedAt
        );
    }
}
