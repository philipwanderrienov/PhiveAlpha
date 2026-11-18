package com.coinmind.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Separate credentials from OpenAI; never expose the generated record toString. */
@ConfigurationProperties("coinmind.ai.gemini")
public record GeminiProperties(
        @DefaultValue("false") boolean newsEnabled,
        @DefaultValue("gemini-3.5-flash-lite") String model,
        @DefaultValue("") String apiKey,
        @DefaultValue("15000") int timeoutMs,
        @DefaultValue("10") int maxCallsPerDay
) {
    @Override public String toString() { return "GeminiProperties[credentials redacted]"; }
}
