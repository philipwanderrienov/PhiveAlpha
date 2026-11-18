package com.coinmind.ai.status;

import java.time.Instant;

public record AiEngineStatus(
        String state,
        String provider,
        String model,
        boolean enabled,
        boolean apiKeyConfigured,
        String message,
        Integer lastHttpStatus,
        Instant lastCheckedAt,
        java.util.Map<String, Object> recovery
) {
    public AiEngineStatus(String state, String provider, String model, boolean enabled, boolean apiKeyConfigured,
                          String message, Integer lastHttpStatus, Instant lastCheckedAt) {
        this(state, provider, model, enabled, apiKeyConfigured, message, lastHttpStatus, lastCheckedAt, java.util.Map.of());
    }
}
