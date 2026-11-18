package com.coinmind.ai.provider;

import com.coinmind.ai.config.GeminiProperties;
import com.coinmind.ai.service.AiNewsIntelligenceService;
import com.coinmind.news.model.NewsArticle;
import com.coinmind.news.service.NewsService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.*;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;

class GeminiHeadlineSelectionTest {
    final Instant now = Instant.parse("2026-11-12T12:00:00Z");
    final AtomicInteger calls = new AtomicInteger();
    final Map<String, NewsArticle> stored = new LinkedHashMap<>();
    final NewsService news = new NewsService(null, null, null);
    final GeminiAiProvider provider = new GeminiAiProvider(true,
            new GeminiProperties(true, "gemini-test", "test", 1000, 10),
            WebClient.builder().exchangeFunction(req -> {
                calls.incrementAndGet();
                String content = new tools.jackson.databind.json.JsonMapper().writeValueAsString(Map.of("items", List.of(Map.of(
                        "newsId", "fresh", "sentiment", "BULLISH", "relevance", .9,
                        "impact", "HIGH", "rationale", "Headline classification"))));
                String body = new tools.jackson.databind.json.JsonMapper().writeValueAsString(Map.of("candidates", List.of(Map.of(
                        "finishReason", "STOP", "content", Map.of("parts", List.of(Map.of("text", content)))))));
                return Mono.just(ClientResponse.create(HttpStatus.OK).body(body).build());
            }).build(), Clock.fixed(now, ZoneOffset.UTC));

    GeminiHeadlineSelectionTest() { ReflectionTestUtils.setField(news, "articles", stored); }
    NewsArticle article(String id, Instant time, String title, String source, double relevance) {
        return new NewsArticle(id, title, source, "", "", time, List.of("BTC"), BigDecimal.ZERO, BigDecimal.valueOf(relevance));
    }
    void add(String key, String id, Instant time, double score) {
        stored.put(key, article(id, time, "Bitcoin headline", "publisher", score));
    }
    List<NewsArticle> selected() { return news.recentForSymbol("BTCUSDT", 5, provider.headlineEligibility()); }

    @Test void staleTopFiveDoNotHideFreshArticleAndFreshArticleReachesRealProviderWithMockHttp() {
        for (int i=0;i<5;i++) add("old"+i, "old"+i, now.minusSeconds(86401), 1);
        add("fresh", "fresh", now.minusSeconds(60), .8);
        assertThat(selected()).extracting(NewsArticle::id).containsExactly("fresh");
        assertThat(new AiNewsIntelligenceService(news, provider).analyze("BTCUSDT").block().direction()).isEqualTo("BULLISH");
        assertThat(calls).hasValue(1);
    }
    @Test void invalidArticlesAreDiscardedIndividuallyIncludingTimeBoundaries() {
        stored.put("null", null);
        for (NewsArticle a : List.of(
                article(null, now, "title", "source", 1), article(" ", now, "title", "source", 1),
                article("x".repeat(201), now, "title", "source", 1),
                article("title-null", now, null, "source", 1), article("title-blank", now, " ", "source", 1),
                article("title-long", now, "x".repeat(401), "source", 1),
                article("source-null", now, "title", null, 1), article("source-long", now, "title", "x".repeat(101), 1),
                article("time-null", null, "title", "source", 1))) stored.put(UUID.randomUUID().toString(), a);
        add("future", "future", now.plusNanos(1), 1);
        add("expired", "expired", now.minusSeconds(86400).minusNanos(1), 1);
        add("boundary", "boundary", now.minusSeconds(86400), .8);
        add("now", "now", now, .8);
        assertThat(selected()).extracting(NewsArticle::id).containsExactly("now", "boundary");
    }
    @Test void duplicateIdsKeepHighestRankedAndLimitAppliesAfterDeduplication() {
        add("duplicate-low", "duplicate", now, .1);
        add("duplicate-high", "duplicate", now.minusSeconds(1), 1);
        for(int i=0;i<7;i++) add("fresh"+i, "fresh"+i, now.minusSeconds(i), .9);
        assertThat(selected()).extracting(NewsArticle::id).containsExactly("duplicate", "fresh0", "fresh1", "fresh2", "fresh3");
    }
    @Test void allExpiredReturnsUnavailableWithoutHttp() {
        add("old", "old", now.minusSeconds(86401), 1);
        assertThat(new AiNewsIntelligenceService(news, provider).analyze("BTCUSDT").block().direction()).isEqualTo("UNAVAILABLE");
        assertThat(calls).hasValue(0);
    }
    @Test void providerDefensiveGuardRejectsInvalidBatchesWithoutHttp() {
        var fresh = article("fresh", now, "Bitcoin headline", "publisher", .9);
        var expired = article("expired", now.minusSeconds(86401), "Bitcoin headline", "publisher", 1);
        var oversized = new ArrayList<NewsArticle>();
        for (int i = 0; i < 6; i++) {
            oversized.add(article("fresh" + i, now, "Bitcoin headline", "publisher", .9));
        }
        for (List<NewsArticle> batch : List.of(List.<NewsArticle>of(), List.of(fresh, expired),
                List.of(fresh, fresh), oversized)) {
            assertThat(provider.analyzeNews("BTCUSDT", batch).block().direction()).isEqualTo("UNAVAILABLE");
        }
        assertThat(calls).hasValue(0);
    }

    @Test void exactFreshnessBoundaryReachesProviderUsingTheSelectionClock() {
        add("fresh", "fresh", now.minus(Duration.ofHours(24)), .9);
        assertThat(new AiNewsIntelligenceService(news, provider).analyze("BTCUSDT").block().direction())
                .isEqualTo("BULLISH");
        assertThat(calls).hasValue(1);
    }

    @Test void emptyInputDoesNotInvokeAiProvider() {
        var spy = org.mockito.Mockito.spy(provider);
        assertThat(new AiNewsIntelligenceService(news, spy).analyze("BTCUSDT").block().direction()).isEqualTo("UNAVAILABLE");
        org.mockito.Mockito.verify(spy, org.mockito.Mockito.never()).analyzeNews(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        assertThat(calls).hasValue(0);
    }
}
