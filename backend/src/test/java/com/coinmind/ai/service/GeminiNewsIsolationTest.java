package com.coinmind.ai.service;

import com.coinmind.ai.model.AiNewsIntelligence;
import com.coinmind.ai.provider.GeminiAiProvider;
import com.coinmind.news.service.NewsService;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class GeminiNewsIsolationTest {
    @Test void classificationIsReturnedButNeverExposedAsTradingModifier() {
        var news = mock(NewsService.class);
        var gemini = mock(GeminiAiProvider.class);
        var result = new AiNewsIntelligence("BTCUSDT", "BULLISH", 75, 0, "NONE", "headline only", List.of(), List.of(), List.of(), "gemini-3.5-flash-lite", Instant.now());
        var article = new com.coinmind.news.model.NewsArticle("fresh", "Bitcoin headline", "publisher", "", "",
                Instant.now(), List.of("BTC"), java.math.BigDecimal.ZERO, java.math.BigDecimal.ONE);
        when(news.recentForSymbol(eq("BTCUSDT"), eq(5), any())).thenReturn(List.of(article));
        when(gemini.analyzeNews("BTCUSDT", List.of(article))).thenReturn(Mono.just(result));
        var service = new AiNewsIntelligenceService(news, gemini);
        assertThat(service.analyze("BTCUSDT").block()).isEqualTo(result);
        assertThat(service.latestFresh("BTCUSDT")).isNull();
    }
}
