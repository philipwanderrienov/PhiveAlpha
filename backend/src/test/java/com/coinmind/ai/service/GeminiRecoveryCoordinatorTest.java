package com.coinmind.ai.service;

import com.coinmind.ai.provider.GeminiAiProvider;
import com.coinmind.ai.model.AiNewsIntelligence;
import com.coinmind.news.service.NewsService;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import java.util.List;
import java.time.Instant;
import static org.mockito.Mockito.*;

class GeminiRecoveryCoordinatorTest {
    @Test void scheduledRecoveryUsesExistingNewsRequestAndStopsAfterProbe() {
        var provider = mock(GeminiAiProvider.class);
        var news = mock(NewsService.class);
        when(provider.recoveryDue()).thenReturn(true, false);
        when(news.recentForSymbol(eq("BTCUSDT"), eq(5), any())).thenReturn(List.of());
        when(provider.analyzeNews("BTCUSDT", List.of())).thenReturn(Mono.just(new AiNewsIntelligence(
            "BTCUSDT", "UNAVAILABLE", 0, 0, "NONE", "No fresh work", List.of(), List.of(), List.of(), "gemini", Instant.EPOCH)));
        new AiNewsIntelligenceService(news, provider).recover();
        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(10)).untilAsserted(() ->
                verify(news).recentForSymbol(eq("BTCUSDT"), eq(5), any()));
        verify(provider, never()).analyzeNews(anyString(), anyList());
        verify(news).recentForSymbol(eq("BTCUSDT"), eq(5), any());
        verifyNoMoreInteractions(news);
    }
    @Test void noScheduleProbeBeforeCooldown() {
        var provider = mock(GeminiAiProvider.class); var news = mock(NewsService.class);
        when(provider.recoveryDue()).thenReturn(false);
        new AiNewsIntelligenceService(news, provider).recover();
        verifyNoInteractions(news);
    }

    @Test void blockedNewsReadDoesNotBlockSchedulerOrStartAnotherPass() throws Exception {
        var provider = mock(GeminiAiProvider.class);
        var news = mock(NewsService.class);
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        when(provider.recoveryDue()).thenReturn(true);
        when(news.recentForSymbol(eq("BTCUSDT"), eq(5), any())).thenAnswer(invocation -> {
            entered.countDown();
            if (!release.await(20, java.util.concurrent.TimeUnit.SECONDS)) throw new AssertionError("worker not released");
            return List.of();
        });
        when(provider.analyzeNews(anyString(), anyList())).thenReturn(Mono.empty());
        var service = new AiNewsIntelligenceService(news, provider);
        try {
            org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(java.time.Duration.ofSeconds(1), service::recover);
            org.assertj.core.api.Assertions.assertThat(entered.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            service.recover();
            verify(provider, never()).analyzeNews(anyString(), anyList());
        } finally { release.countDown(); }
        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(10)).untilAsserted(() ->
                verify(news).recentForSymbol(eq("SOLUSDT"), eq(5), any()));
        verify(provider, never()).analyzeNews(anyString(), anyList());
        verify(news, times(1)).recentForSymbol(eq("BTCUSDT"), eq(5), any());
    }
}
