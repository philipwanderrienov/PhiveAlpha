package com.coinmind.ai.service;

import com.coinmind.ai.model.AiNewsIntelligence;
import com.coinmind.ai.provider.AiProvider;
import com.coinmind.ai.provider.GeminiAiProvider;
import com.coinmind.news.model.NewsArticle;
import com.coinmind.news.service.NewsService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AiNewsIntelligenceService {

    private static final Duration FRESHNESS = Duration.ofHours(2);

    private final NewsService newsService;
    private final AiProvider aiProvider;
    private final Map<String, AiNewsIntelligence> latestBySymbol = new ConcurrentHashMap<>();

    public AiNewsIntelligenceService(
            NewsService newsService,
            AiProvider aiProvider
    ) {
        this.newsService = newsService;
        this.aiProvider = aiProvider;
    }

    private final java.util.concurrent.atomic.AtomicBoolean recovering = new java.util.concurrent.atomic.AtomicBoolean();

    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 30000)
    public void recover() {
        if (!(aiProvider instanceof GeminiAiProvider g) || !g.recoveryDue() || !recovering.compareAndSet(false, true)) return;
        // One bounded pass through existing RSS work; provider validates freshness and admission.
        reactor.core.publisher.Flux.fromIterable(List.of("BTCUSDT", "ETHUSDT", "SOLUSDT"))
                .concatMap(this::analyze).takeUntil(r -> !g.recoveryDue())
                // RSS snapshot/admission includes synchronized reads and durable file I/O.
                // Do not execute that work on Spring's shared scheduling thread.
                .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
                .doFinally(signal -> recovering.set(false)).subscribe(r -> { }, e -> { });
    }

    public Mono<AiNewsIntelligence> analyze(String symbol) {
        List<NewsArticle> articles = aiProvider instanceof GeminiAiProvider gemini
                ? newsService.recentForSymbol(symbol, 5, gemini.headlineEligibility())
                : newsService.recentForSymbol(symbol, 5);

        if (articles.isEmpty() && aiProvider instanceof GeminiAiProvider) {
            String reason = "No valid fresh live headlines";
            return Mono.just(new AiNewsIntelligence(symbol.toUpperCase(), "UNAVAILABLE", 0, 0,
                    "NONE", reason, List.of(), List.of(reason), List.of(), aiProvider.name(), Instant.now()));
        }

        if (articles.isEmpty() && !(aiProvider instanceof GeminiAiProvider)) {
            return Mono.just(new AiNewsIntelligence(
                    symbol.toUpperCase(),
                    "NEUTRAL",
                    0,
                    0,
                    "NONE",
                    "Belum ada berita relevan yang cukup untuk dianalisis.",
                    List.of(),
                    List.of(),
                    List.of(),
                    aiProvider.name(),
                    java.time.Instant.now()
            ));
        }

        String normalized = symbol.toUpperCase();

        return aiProvider.analyzeNews(normalized, articles)
                .doOnNext(result -> latestBySymbol.put(normalized, result));
    }

    public AiNewsIntelligence latestFresh(String symbol) {
        // Phase 1 is classification/display only. No Gemini contribution to trading decisions.
        // The provider validates its own identity/content cache on every explicit news request.
        if (aiProvider instanceof GeminiAiProvider) return null;

        AiNewsIntelligence intelligence = latestBySymbol.get(symbol.toUpperCase());

        if (intelligence == null || intelligence.analyzedAt() == null) {
            return null;
        }

        return intelligence.analyzedAt()
                .plus(FRESHNESS)
                .isAfter(Instant.now())
                ? intelligence
                : null;
    }
}
