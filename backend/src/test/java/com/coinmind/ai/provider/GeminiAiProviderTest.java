package com.coinmind.ai.provider;

import com.coinmind.ai.config.GeminiProperties;
import com.coinmind.ai.model.AiNewsIntelligence;
import com.coinmind.news.model.NewsArticle;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.*;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;

class GeminiAiProviderTest {
    private final MutableClock clock = new MutableClock();
    private final AtomicInteger calls = new AtomicInteger();
    private ClientRequest captured;
    private final JsonMapper json = new JsonMapper();
    private NewsArticle article(String title) {
        return new NewsArticle("a", title, "publisher", "https://example.com/a", "untrusted body",
                clock.instant().minusSeconds(60), List.of("BTC"), BigDecimal.ZERO, BigDecimal.ONE);
    }
    private String item(String id, String sentiment, String relevance) {
        return "{\"newsId\":\"" + id + "\",\"sentiment\":\"" + sentiment + "\",\"relevance\":" + relevance
                + ",\"impact\":\"HIGH\",\"rationale\":\"Public headline classification\"}";
    }
    private String envelope(String content) {
        return json.writeValueAsString(Map.of("candidates", List.of(Map.of("finishReason", "STOP", "content",
                Map.of("parts", List.of(Map.of("text", content)))))));
    }
    private GeminiAiProvider provider(String body, int status) {
        return provider(new GeminiProperties(true, "gemini-3.5-flash-lite", "test-placeholder", 100, 10),
                Mono.just(ClientResponse.create(HttpStatus.valueOf(status)).header("Content-Type", MediaType.APPLICATION_JSON_VALUE).body(body).build()));
    }
    private GeminiAiProvider provider(GeminiProperties props, Mono<ClientResponse> response) {
        WebClient client = WebClient.builder().baseUrl("https://generativelanguage.googleapis.com")
                .codecs(c -> c.defaultCodecs().maxInMemorySize(65536))
                .exchangeFunction(request -> { calls.incrementAndGet(); captured = request; return response; }).build();
        return new GeminiAiProvider(true, props, client, clock);
    }
    private AiNewsIntelligence run(GeminiAiProvider p) { return p.analyzeNews("BTCUSDT", List.of(article("Bitcoin headline"))).block(); }
    private String success() { return envelope("{\"items\":[" + item("a", "BULLISH", "0.9") + "]}"); }
    @Test void retryAfterHeaderControlsRecoveryEligibility() {
        for (String header : List.of("7200", java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME.format(clock.instant().plusSeconds(7200).atZone(ZoneOffset.UTC)))) {
            var p = provider(new GeminiProperties(true, "gemini-test", "test", 1000, 10),
                    Mono.just(ClientResponse.create(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", header).body("discard").build()));
            run(p);
            assertThat(p.recoveryStatus().get("nextEligibleRecoveryTime")).isEqualTo(clock.instant().plusSeconds(7200));
        }
    }
    @Test void recoveryRequiresEligibleWorkAndValidatedResponse() {
        var responseNumber = new AtomicInteger();
        WebClient client = WebClient.builder().baseUrl("https://example.invalid")
            .exchangeFunction(req -> {
                calls.incrementAndGet();
                int n = responseNumber.getAndIncrement();
                return Mono.just(ClientResponse.create(n == 0 ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.OK)
                    .body(n == 1 ? "{}" : success()).build());
            }).build();
        var p = new GeminiAiProvider(true, new GeminiProperties(true, "gemini-test", "test", 1000, 10), client, clock);
        assertThat(run(p).direction()).isEqualTo("UNAVAILABLE");
        assertThat(p.recoveryStatus().get("state")).isEqualTo("OPEN");
        clock.now = clock.now.plusSeconds(300);
        assertThat(p.recoveryDue()).isTrue();
        p.analyzeNews("BTCUSDT", List.of()).block();
        assertThat(calls).hasValue(1);
        assertThat(run(p).direction()).isEqualTo("UNAVAILABLE");
        assertThat(p.recoveryStatus().get("state")).isEqualTo("OPEN");
        clock.now = ((Instant)p.recoveryStatus().get("nextEligibleRecoveryTime"));
        assertThat(run(p).direction()).isEqualTo("BULLISH");
        assertThat(p.recoveryStatus().get("state")).isEqualTo("CLOSED");
        assertThat(calls).hasValue(3);
    }
    @Test void scheduledCoordinatorRecoversOnlyWithFreshNews() {
        WebClient client = WebClient.builder().baseUrl("https://example.invalid")
                .exchangeFunction(req -> Mono.just(ClientResponse.create(
                        calls.getAndIncrement() == 0 ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.OK)
                        .body(success()).build())).build();
        var p = new GeminiAiProvider(true,
                new GeminiProperties(true, "gemini-test", "test", 1000, 10), client, clock);
        run(p);
        var news = org.mockito.Mockito.mock(com.coinmind.news.service.NewsService.class);
        var coordinator = new com.coinmind.ai.service.AiNewsIntelligenceService(news, p);
        coordinator.recover();
        org.mockito.Mockito.verifyNoInteractions(news);
        clock.advance(300);
        coordinator.recover(); // Empty RSS result must not consume a recovery attempt.
        org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                org.mockito.Mockito.verify(news).recentForSymbol(org.mockito.ArgumentMatchers.eq("SOLUSDT"), org.mockito.ArgumentMatchers.eq(5), org.mockito.ArgumentMatchers.any()));
        assertThat(calls).hasValue(1);
        assertThat(p.recoveryDue()).isTrue();
        org.mockito.Mockito.when(news.recentForSymbol(org.mockito.ArgumentMatchers.eq("BTCUSDT"), org.mockito.ArgumentMatchers.eq(5), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(article("Fresh Bitcoin headline")));
        org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
            coordinator.recover();
            assertThat(p.recoveryStatus().get("state")).isEqualTo("CLOSED");
        });
        assertThat(calls).hasValue(2);
        assertThat(p.recoveryStatus().get("dataFreshness")).isEqualTo("FRESH");
        coordinator.recover();
        assertThat(calls).hasValue(2);
    }
    @Test void successAndDeduplication() {
        var p = provider(success(), 200);
        Instant started = clock.instant();
        var first = run(p);
        assertThat(first.direction()).isEqualTo("BULLISH");
        assertThat(first.confidence()).isZero();
        assertThat(first.importance()).isEqualTo(75);
        assertThat(first.articleImpacts().getFirst().articleId()).isEqualTo("a");
        assertThat(first.analyzedAt()).isAfterOrEqualTo(started);
        assertThat(run(p)).isEqualTo(first);
        assertThat(calls).hasValue(1);
        assertThat(captured.url().toString()).isEqualTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent");
        assertThat(captured.url().getQuery()).isNull();
        assertThat(captured.headers().containsHeader("x-goog-api-key")).isTrue();
    }
    @Test void rejectsInvalidResponses() {
        for (String content : List.of("not JSON", "{}", "{\"items\":[]}",
                "{\"items\":[" + item("a", "BUY", "0.8") + "]}",
                "{\"items\":[" + item("a", "BULLISH", "1.1") + "]}",
                "{\"items\":[" + item("extra", "BULLISH", "0.8") + "]}",
                "{\"items\":[" + item("a", "BULLISH", "0.8") + "," + item("a", "BULLISH", "0.8") + "]}",
                "{\"items\":[" + item("a", "BULLISH", "0.8").replace("HIGH", "EXTREME") + "]}",
                "{\"items\":[" + item("a", "BULLISH", "0.8").replace("\"relevance\":0.8", "\"relevance\":0.8,\"relevance\":0.7") + "]}")) {
            assertThat(run(provider(envelope(content), 200)).direction()).isEqualTo("UNAVAILABLE");
        }
        assertThat(run(provider("not JSON", 200)).direction()).isEqualTo("UNAVAILABLE");
    }
    @Test void rejectsDuplicateIdentityWithCorrectCount() {
        var p = provider(envelope("{\"items\":[" + item("a", "NEUTRAL", "0.5") + "," + item("a", "NEUTRAL", "0.5") + "]}"), 200);
        var a = article("Bitcoin headline");
        var b = new NewsArticle("b", a.title(), a.source(), a.url(), a.summary(), a.publishedAt(), a.symbols(), a.sentimentScore(), a.relevanceScore());
        assertThat(p.analyzeNews("BTCUSDT", List.of(a, b)).block().direction()).isEqualTo("UNAVAILABLE");
    }
    @Test void missingKeyAndDisabledOptInNeverCallHttp() {
        for (GeminiProperties props : List.of(new GeminiProperties(true, "gemini-3.5-flash-lite", "", 100, 10),
                new GeminiProperties(false, "gemini-3.5-flash-lite", "test-placeholder", 100, 10))) {
            assertThat(run(provider(props, Mono.never())).direction()).isEqualTo("UNAVAILABLE");
        }
        assertThat(calls).hasValue(0);
    }
    @Test void httpFailuresHaveNoRetryAndNoSensitiveBody() {
        for (int status : List.of(400, 401, 403, 404, 429, 500, 503)) {
            calls.set(0);
            var p = provider("sensitive provider error", status);
            var result = run(p);
            assertThat(result.direction()).isEqualTo("UNAVAILABLE");
            assertThat(result.summary()).contains(Integer.toString(status)).doesNotContain("sensitive");
            run(p);
            assertThat(calls).hasValue(1);
        }
    }
    @Test void timeoutIsUnavailableWithoutRetry() {
        var p = provider(new GeminiProperties(true, "gemini-3.5-flash-lite", "test-placeholder", 100, 10), Mono.never());
        StepVerifier.create(p.analyzeNews("BTCUSDT", List.of(article("Bitcoin headline"))))
                .assertNext(r -> assertThat(r.direction()).isEqualTo("UNAVAILABLE")).verifyComplete();
        assertThat(calls).hasValue(1);
    }
    @Test void changedHeadlineNeverUsesOldIntelligenceEvenWhenRateLimited() {
        var p = provider(success(), 200);
        var a = article("Bitcoin headline");
        assertThat(p.analyzeNews("BTCUSDT", List.of(a)).block().direction()).isEqualTo("BULLISH");
        assertThat(p.analyzeNews("BTCUSDT", List.of(article("Changed headline"))).block().direction()).isEqualTo("UNAVAILABLE");
        assertThat(calls).hasValue(1);
        clock.advance(61);
        assertThat(p.analyzeNews("BTCUSDT", List.of(article("Changed headline"))).block().articleImpacts().getFirst().title()).isEqualTo("Changed headline");
        assertThat(calls).hasValue(2);
    }
    @Test void futureAndOldHeadlinesAreRejected() {
        var p = provider(success(), 200);
        var a = article("Bitcoin headline");
        for (Instant published : List.of(clock.instant().plusSeconds(1), clock.instant().minus(Duration.ofHours(25)))) {
            var invalid = new NewsArticle(a.id(), a.title(), a.source(), a.url(), a.summary(), published, a.symbols(), a.sentimentScore(), a.relevanceScore());
            assertThat(p.analyzeNews("BTCUSDT", List.of(invalid)).block().direction()).isEqualTo("UNAVAILABLE");
        }
        assertThat(calls).hasValue(0);
    }
    @Test void expiredCacheAndDailyLimit() {
        var props = new GeminiProperties(true, "gemini-3.5-flash-lite", "test-placeholder", 100, 1);
        var p = provider(props, Mono.just(ClientResponse.create(HttpStatus.OK).body(success()).build()));
        var a = List.of(article("Bitcoin headline"));
        assertThat(p.analyzeNews("BTCUSDT", a).block().direction()).isEqualTo("BULLISH");
        clock.advance(901);
        assertThat(p.analyzeNews("BTCUSDT", a).block().direction()).isEqualTo("UNAVAILABLE");
        assertThat(calls).hasValue(1);
    }
    @Test void concurrentSubscribersShareOneRequest() {
        var sink = reactor.core.publisher.Sinks.<ClientResponse>one();
        var p = provider(new GeminiProperties(true, "gemini-3.5-flash-lite", "test-placeholder", 15000, 10), sink.asMono());
        var a = List.of(article("Bitcoin headline"));
        var results = Mono.zip(p.analyzeNews("BTCUSDT", a), p.analyzeNews("BTCUSDT", a)).toFuture();
        assertThat(calls).hasValue(1);
        sink.tryEmitValue(ClientResponse.create(HttpStatus.OK).body(success()).build());
        assertThat(results.join().getT1()).isEqualTo(results.join().getT2());
    }
    @Test void oversizedResponseIsRejected() {
        assertThat(run(provider("x".repeat(70000), 200)).direction()).isEqualTo("UNAVAILABLE");
    }
    @Test void masterSwitchBlocksHttpAndUnsupportedMethodsDoNotCallHttp() {
        var p = new GeminiAiProvider(false, new GeminiProperties(true, "gemini-3.5-flash-lite", "test-placeholder", 100, 10),
                WebClient.builder().exchangeFunction(r -> { calls.incrementAndGet(); return Mono.never(); }).build(), clock);
        assertThat(run(p).direction()).isEqualTo("UNAVAILABLE");
        var context = org.mockito.Mockito.mock(com.coinmind.ai.model.MarketContext.class);
        var setup = org.mockito.Mockito.mock(com.coinmind.trade.model.TradeSetup.class);
        assertThat(p.analyze(context, "MANUAL").block().marketBias()).isEqualTo("UNAVAILABLE");
        assertThat(p.reviewDecision(setup, context).block().confidence()).isZero();
        assertThat(calls).hasValue(0);
    }
    @Test void realisticFiveArticleResponseAndSerializedRequest() {
        var articles = new ArrayList<NewsArticle>();
        var items = new ArrayList<String>();
        for (int i = 0; i < 5; i++) {
            var a = article("Public Bitcoin headline " + i);
            articles.add(new NewsArticle("id-" + i, a.title(), a.source(), a.url(), a.summary(),
                    a.publishedAt(), a.symbols(), a.sentimentScore(), a.relevanceScore()));
            items.add(item("id-" + i, "NEUTRAL", "0.75"));
        }
        String body = json.writeValueAsString(Map.of(
                "candidates", List.of(Map.of("content", Map.of("role", "model", "parts", List.of(
                        Map.of("text", "{\"items\":[" + String.join(",", items) + "]}"))),
                        "finishReason", "STOP", "index", 0)),
                "usageMetadata", Map.of("promptTokenCount", 400, "candidatesTokenCount", 300),
                "modelVersion", "gemini-3.5-flash-lite", "responseId", "provider-response-id"));
        var result = provider(body, 200).analyzeNews("BTCUSDT", articles).block();
        assertThat(result.direction()).isEqualTo("NEUTRAL");
        assertThat(result.articleImpacts()).hasSize(5);
        assertThat(result.confidence()).isZero();

        var output = new org.springframework.mock.http.client.reactive.MockClientHttpRequest(
                org.springframework.http.HttpMethod.POST, captured.url());
        captured.writeTo(output, ExchangeStrategies.withDefaults()).block();
        var request = json.readTree(output.getBodyAsString().block());
        assertThat(request.size()).isEqualTo(3);
        assertThat(request.path("systemInstruction").path("parts").get(0).path("text").asText())
                .contains("untrusted DATA");
        assertThat(request.path("generationConfig").path("responseMimeType").asText()).isEqualTo("application/json");
        assertThat(request.path("generationConfig").path("maxOutputTokens").asInt()).isEqualTo(1600);
        var data = json.readTree(request.path("contents").get(0).path("parts").get(0).path("text").asText());
        assertThat(data.path("articles").size()).isEqualTo(5);
        assertThat(data.path("articles").get(0).size()).isEqualTo(4);
        assertThat(output.getBodyAsString().block()).doesNotContain("untrusted body", "https://example.com", "test-placeholder");
    }

    @Test void failureCategoriesAndLogsNeverExposeSensitiveData() {
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(GeminiAiProvider.class);
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            assertThat(run(provider("secret response and headline", 400)).summary()).contains("HTTP_BAD_REQUEST", "HTTP_STATUS_400");
            assertThat(run(provider("secret invalid json", 200)).summary()).contains("INVALID_JSON", "HTTP_STATUS_200");
            assertThat(run(provider(envelope("{}"), 200)).summary()).contains("VALIDATION_FAILED");
            assertThat(run(provider("", 200)).summary()).contains("EMPTY_RESPONSE");
            var transport = new WebClientRequestException(new java.io.IOException("secret transport"),
                    org.springframework.http.HttpMethod.POST, java.net.URI.create("https://secret.example/?key=secret"),
                    new org.springframework.http.HttpHeaders());
            var props = new GeminiProperties(true, "gemini-3.5-flash-lite", "test-placeholder", 100, 1);
            assertThat(run(provider(props, Mono.error(transport))).summary()).contains("TRANSPORT_ERROR");
            assertThat(run(provider(props, Mono.never())).summary()).contains("TIMEOUT");
            assertThat(run(provider(props, Mono.error(new WebClientRequestException(
                    new io.netty.channel.ConnectTimeoutException("secret timeout"), transport.getMethod(),
                    transport.getUri(), transport.getHeaders())))).summary()).contains("TIMEOUT");
            assertThat(appender.list).hasSize(7);
            for (var event : appender.list) {
                assertThat(event.getThrowableProxy()).isNull();
                assertThat(event.getFormattedMessage()).matches(
                        "category=[A-Z_]+ httpStatus=(NONE|[0-9]{3}) model=gemini-3.5-flash-lite articleCount=1 correlationId=[a-f0-9-]{36}")
                        .doesNotContain("secret", "test-placeholder", "Bitcoin headline", "https://");
            }
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test void httpStatusSurvivesErrorBodyDrainFailure() {
        var response = ClientResponse.create(HttpStatus.BAD_REQUEST)
                .body(reactor.core.publisher.Flux.error(new java.io.IOException("secret body failure"))).build();
        // Reproduce why the old releaseBody().thenReturn(status) path lost the status.
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> response.releaseBody().block())
                .hasRootCauseInstanceOf(java.io.IOException.class);
        var p = provider(new GeminiProperties(true, "gemini-3.5-flash-lite", "test-placeholder", 100, 1),
                Mono.just(response));
        assertThat(run(p).summary()).contains("HTTP_BAD_REQUEST", "HTTP_STATUS_400").doesNotContain("secret");
        run(p);
        assertThat(calls).hasValue(1);
    }

    @Test void incompleteOrNonconformingCandidatesRemainRejected() {
        for (String body : List.of(
                success().replace("STOP", "MAX_TOKENS"),
                success().replace("STOP", "SAFETY"),
                "{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}",
                "{\"candidates\":[]}",
                envelope("{\"items\":[" + item("a", "NEUTRAL", "0.5").replace(
                        "Public headline classification", "") + "]}"),
                envelope("{\"items\":[" + item("a", "NEUTRAL", "0.5") + "],\"extra\":true}"))) {
            assertThat(run(provider(body, 200)).summary()).contains("VALIDATION_FAILED");
        }
        assertThat(run(provider(success() + "{}", 200)).summary()).contains("INVALID_JSON");
    }

    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-11-05T12:00:00Z");
        void advance(long seconds) { now = now.plusSeconds(seconds); }
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
    }
}
