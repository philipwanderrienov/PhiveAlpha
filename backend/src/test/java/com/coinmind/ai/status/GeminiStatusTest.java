package com.coinmind.ai.status;

import com.coinmind.ai.config.AiProperties;
import com.coinmind.ai.config.GeminiProperties;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class GeminiStatusTest {
    @Test void runtimeStatusExposesOnlySafeRecoveryMetadata() throws Exception {
        var ai = mock(AiProperties.class); when(ai.enabled()).thenReturn(true); when(ai.provider()).thenReturn("gemini");
        var service = new AiStatusService(ai, new GeminiProperties(true, "gemini-test", "secret-never-returned", 1000, 10));
        var geminiProvider = mock(com.coinmind.ai.provider.GeminiAiProvider.class);
        @SuppressWarnings("unchecked")
        org.springframework.beans.factory.ObjectProvider<com.coinmind.ai.provider.GeminiAiProvider> lazy = mock(org.springframework.beans.factory.ObjectProvider.class);
        when(lazy.getIfAvailable()).thenReturn(geminiProvider);
        var field = AiStatusService.class.getDeclaredField("provider"); field.setAccessible(true); field.set(service, lazy);
        for (String circuit : java.util.List.of("CLOSED", "OPEN", "HALF_OPEN", "AUTH_ERROR")) {
            when(geminiProvider.recoveryStatus()).thenReturn(java.util.Map.of("state", circuit, "reasonCode", "TEST", "dataFreshness", "FRESH"));
            String expected = circuit.equals("CLOSED") ? "READY" : circuit.equals("AUTH_ERROR") ? "AUTH_ERROR" : "ERROR";
            assertThat(service.current().state()).isEqualTo(expected);
            assertThat(service.current().toString()).doesNotContain("secret-never-returned");
        }
    }
    @Test void geminiStatusUsesOnlyGeminiCredentialAndModel() {
        var ai = mock(AiProperties.class);
        when(ai.enabled()).thenReturn(true);
        when(ai.provider()).thenReturn("gemini");
        var configured = new AiStatusService(ai, new GeminiProperties(true, "gemini-3.5-flash-lite", "test-placeholder", 100, 10)).current();
        assertThat(configured.state()).isEqualTo("CONFIGURED_UNVERIFIED");
        assertThat(configured.model()).isEqualTo("gemini-3.5-flash-lite");
        verify(ai, never()).apiKey();
        assertThat(new AiStatusService(ai, new GeminiProperties(true, "gemini-3.5-flash-lite", "", 100, 10)).current().state()).isEqualTo("MISSING_API_KEY");
        assertThat(new AiStatusService(ai, new GeminiProperties(false, "gemini-3.5-flash-lite", "test-placeholder", 100, 10)).current().state()).isEqualTo("DISABLED");
    }
}
