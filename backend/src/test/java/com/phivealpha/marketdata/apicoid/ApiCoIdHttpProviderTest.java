package com.phivealpha.marketdata.apicoid;

import static com.phivealpha.marketdata.apicoid.ApiCoIdData.Quality.*;
import static org.assertj.core.api.Assertions.*;

import com.phivealpha.persistence.IdxRecords.AdjustmentStatus;
import com.phivealpha.screening.WeeklyScreening;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(OutputCaptureExtension.class)
class ApiCoIdHttpProviderTest {
    private static final Instant NOW = Instant.parse("2026-11-13T09:31:00Z");
    private static final LocalDate EARLIER = LocalDate.parse("2026-11-11");
    private static final LocalDate HOLIDAY = LocalDate.parse("2026-11-12");
    private static final LocalDate SPECIAL = LocalDate.parse("2026-11-13");
    private static final String KEY = "unit-test-key-do-not-log";
    private static final String PRIVATE_BODY = "mock-sensitive-response-do-not-log";

    @Test
    void mapsEveryDatasetAndRecordsProvenanceWithoutSynthesizingWeeklyData() {
        var mock = new MockHttp();
        mock.add("instruments-first", "instruments-last", "quote", "daily", "news", "news",
                "corporate-actions", "foreign-flow", "broker-flow");
        var provider = mock.provider();

        var master = provider.instrumentMaster().block();
        assertThat(master.quality()).isEqualTo(UNVERIFIED);
        assertThat(master.rows()).extracting(ApiCoIdData.Instrument::symbol).containsExactly("BBCA", "BBRI");
        assertThat(master.provenance().universeVersion()).isEqualTo("qa-universe-v1");

        var quote = provider.marketQuote(" bbca ").block();
        assertThat(quote.rows()).singleElement().satisfies(row -> {
            assertThat(row.lastIdr()).isEqualByComparingTo("9800");
            assertThat(row.volume()).isEqualTo(12345678);
            assertThat(row.turnoverIdr()).isEqualByComparingTo("121000000000.25");
            assertThat(row.adjustment()).isEqualTo(AdjustmentStatus.UNKNOWN);
        });
        assertThat(quote.provenance().calendarVersion()).isEqualTo("qa-2026-11-v1");

        var daily = provider.dailyOhlcv("BBCA", EARLIER, SPECIAL).block();
        assertThat(daily.rows()).extracting(ApiCoIdData.DailyBar::tradingDate).containsExactly(EARLIER, SPECIAL);
        assertThat(daily.rows().getFirst().turnoverIdr()).isEqualByComparingTo("9789900.1234567890123456789");
        assertThat(daily.rows().getFirst().adjustment()).isEqualTo(AdjustmentStatus.ADJUSTED);

        var news = provider.marketNews().block().rows().getFirst();
        assertThat(news.publishedAt()).isEqualTo(Instant.parse("2026-11-13T03:04:00Z"));
        assertThat(news.observedAt()).isAfter(news.publishedAt());
        assertThat(news.symbols()).containsExactly("BBCA", "BBRI");
        assertThat(news.publisher()).isNull();
        assertThat(provider.symbolNews("bbca").block().rows()).hasSize(1);

        var action = provider.corporateActions("BBCA").block().rows().getFirst();
        assertThat(action.valueIdr()).isEqualByComparingTo("200.125");
        assertThat(action.exDate()).isAfter(SPECIAL);

        var foreign = provider.foreignFlow("BBCA", SPECIAL, SPECIAL).block().rows().getFirst();
        assertThat(foreign.netIdr()).isEqualByComparingTo("-100000.375");

        var broker = provider.brokerFlow("BBCA", SPECIAL).block().rows().getFirst();
        assertThat(broker.bucketAt()).isEqualTo(Instant.parse("2026-11-13T03:00:00Z"));
        assertThat(broker.positions().getFirst().netLots()).isEqualTo(-15);

        assertThat(mock.requests).allSatisfy(request -> {
            assertThat(request.headers().getFirst("x-api-co-id")).isEqualTo(KEY);
            assertThat(request.url().getHost()).isEqualTo("use.api.co.id");
            assertThat(request.url().toString()).doesNotContain(KEY);
        });
        assertThat(mock.requests.stream().map(request -> request.url().getPath()).toList()).containsExactly(
                "/idx/v2/instruments", "/idx/v2/instruments", "/idx/v2/chart/quote/BBCA",
                "/idx/v2/chart/daily/BBCA", "/idx/v2/news", "/idx/v2/news/BBCA",
                "/idx/v2/corporate-actions/BBCA", "/idx/v2/foreign-flow/BBCA", "/idx/v2/broker-flow/BBCA");
        assertThat(mock.requests.getFirst().url().getQuery()).contains("index=IHSG", "limit=2");
        assertThat(mock.requests.get(1).url().getRawQuery()).contains("cursor=page%2B%2F%3D%20%261");
    }

    @ParameterizedTest
    @CsvSource({"401,UNAUTHORIZED", "402,PAYMENT_REQUIRED", "403,FORBIDDEN"})
    void credentialAndEntitlementErrorsLatchWithoutRetriesOrSensitiveLogs(
            int status, String reason, CapturedOutput output) {
        var mock = new MockHttp();
        mock.retries = 2;
        mock.responses.add(response(status, PRIVATE_BODY + KEY));
        var provider = mock.provider();
        assertThat(provider.marketQuote("BBCA").block().reason()).isEqualTo(reason);
        assertThat(provider.marketNews().block().reason()).isEqualTo("CREDENTIALS_OR_ENTITLEMENT_BLOCKED");
        assertThat(mock.requests).hasSize(1);
        assertThat(output.getAll()).doesNotContain(PRIVATE_BODY, KEY);
    }

    @Test
    void rateLimitsLocallyAndHonorsUpstreamRetryAfterAcrossDatasets() {
        var mock = new MockHttp();
        mock.retries = 2;
        mock.autoAdvance = false;
        mock.add("quote");
        mock.responses.add(Mono.just(ClientResponse.create(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, "120").body(PRIVATE_BODY).build()));
        mock.add("news");
        var provider = mock.provider();
        provider.marketQuote("BBCA").block();
        assertThat(provider.marketNews().block().reason()).isEqualTo("LOCAL_RATE_LIMIT");
        mock.clock.advance(Duration.ofMillis(2));
        assertThat(provider.marketNews().block().reason()).isEqualTo("UPSTREAM_RATE_LIMIT");
        mock.clock.advance(Duration.ofSeconds(119));
        assertThat(provider.marketNews().block().quality()).isEqualTo(UNAVAILABLE);
        assertThat(mock.requests).hasSize(2);
        mock.clock.advance(Duration.ofSeconds(1));
        assertThat(provider.marketNews().block().rows()).hasSize(1);
        assertThat(mock.requests).hasSize(3);
    }

    @Test
    void timeoutsAreClassifiedAndConsumeOneAttempt() {
        var mock = new MockHttp();
        mock.responses.add(Mono.never());
        var provider = mock.provider();
        StepVerifier.withVirtualTime(() -> provider.marketQuote("BBCA"))
                .expectSubscription().thenAwait(Duration.ofSeconds(1))
                .assertNext(snapshot -> {
                    assertThat(snapshot.quality()).isEqualTo(UNAVAILABLE);
                    assertThat(snapshot.reason()).isEqualTo("TIMEOUT");
                }).verifyComplete();
        assertThat(mock.requests).hasSize(1);
        assertThat(provider.remainingRequestBudget()).isEqualTo(19);
    }

    @Test
    void transientRetryConsumesBudgetAndStopsAtConfiguredLimit() {
        var mock = new MockHttp();
        mock.retries = 1;
        mock.threshold = 3;
        mock.responses.add(response(502, PRIVATE_BODY));
        mock.responses.add(response(503, PRIVATE_BODY));
        var provider = mock.provider();
        assertThat(provider.marketQuote("BBCA").block().reason()).isEqualTo("UPSTREAM_TRANSIENT");
        assertThat(mock.requests).hasSize(2);
        assertThat(provider.remainingRequestBudget()).isEqualTo(18);
    }

    @Test
    void circuitOpensAndAllowsOneProbeAfterCooldown() {
        var mock = new MockHttp();
        mock.threshold = 1;
        mock.responses.add(response(503, PRIVATE_BODY));
        var probe = Sinks.<ClientResponse>one();
        mock.responses.add(probe.asMono());
        mock.add("news");
        var provider = mock.provider();
        assertThat(provider.marketQuote("BBCA").block().reason()).isEqualTo("UPSTREAM_TRANSIENT");
        assertThat(provider.marketNews().block().reason()).isEqualTo("CIRCUIT_OPEN");
        mock.clock.advance(Duration.ofSeconds(60));
        var running = provider.marketQuote("BBCA").toFuture();
        assertThat(provider.marketNews().block().reason()).isEqualTo("CIRCUIT_OPEN");
        probe.tryEmitValue(httpResponse(200, fixture("quote")));
        assertThat(running.join().rows()).hasSize(1);
        assertThat(provider.marketNews().block().rows()).hasSize(1);
        assertThat(mock.requests).hasSize(3);
    }

    @Test
    void otherHttpAndEnvelopeErrorsAreNotRetried() {
        var mock = new MockHttp();
        mock.retries = 2;
        mock.responses.add(response(501, PRIVATE_BODY));
        mock.responses.add(response(200, "{\"is_success\":false,\"message\":\"" + PRIVATE_BODY + "\"}"));
        mock.responses.add(response(302, PRIVATE_BODY));
        mock.responses.add(response(200, "bad-json"));
        var provider = mock.provider();
        assertThat(provider.marketQuote("BBCA").block().reason()).isEqualTo("UPSTREAM_ERROR");
        assertThat(provider.marketQuote("BBCA").block().quality()).isEqualTo(INCOMPLETE);
        assertThat(provider.marketQuote("BBCA").block().reason()).isEqualTo("HTTP_REJECTED");
        assertThat(provider.marketQuote("BBCA").block().reason()).isEqualTo("INVALID_JSON");
        assertThat(mock.requests).hasSize(4);
    }

    @Test
    void rejectsDuplicatePageEvenWhenCursorChanges() {
        var mock = new MockHttp();
        mock.add("instruments-first");
        mock.responses.add(response(200, fixture("instruments-first").replace("page+/= &1", "new-cursor")));
        var result = mock.provider().instrumentMaster().block();
        assertThat(result.reason()).isEqualTo("DUPLICATE_ROW_OR_PAGE");
        assertThat(result.rows()).isEmpty();
        assertThat(mock.requests).hasSize(2);
    }

    @Test
    void rejectsRepeatedCursorEvenWhenRowsDiffer() {
        var mock = new MockHttp();
        mock.add("instruments-first");
        mock.responses.add(response(200, fixture("instruments-last").replace("\"cursor\": null",
                "\"cursor\": \"page+/= &1\"")));
        var result = mock.provider().instrumentMaster().block();
        assertThat(result.reason()).isEqualTo("PAGINATION_LOOP");
        assertThat(result.rows()).isEmpty();
        assertThat(mock.requests).hasSize(2);
    }

    @Test
    void rejectsTruncatedUniverseAndStopsAtHardPageCeiling() {
        var mock = new MockHttp();
        mock.maxPages = 1;
        mock.add("instruments-first", "instruments-last");
        var provider = mock.provider();
        assertThat(provider.instrumentMaster().block().reason()).isEqualTo("PAGINATION_LIMIT");
        var truncated = provider.instrumentMaster().block();
        assertThat(truncated.quality()).isEqualTo(INCOMPLETE);
        assertThat(truncated.reason()).isEqualTo("UNIVERSE_MISMATCH");
        assertThat(truncated.rows()).isEmpty();
    }

    @Test
    void hasNoPartialUniverseWhenBudgetIsExhaustedAfterFirstPage() {
        var mock = new MockHttp();
        mock.budget = 1;
        mock.add("instruments-first");
        var provider = mock.provider();
        var result = provider.instrumentMaster().block();
        assertThat(result.quality()).isEqualTo(UNAVAILABLE);
        assertThat(result.reason()).isEqualTo("REQUEST_BUDGET_EXHAUSTED");
        assertThat(result.rows()).isEmpty();
        assertThat(provider.remainingRequestBudget()).isZero();
        assertThat(mock.requests).hasSize(1);
    }

    @Test
    void coalescesCacheMissesStopsConcurrentRequestsAndRechecksFreshnessOnHits() {
        var mock = new MockHttp();
        var reply = Sinks.<ClientResponse>one();
        mock.responses.add(reply.asMono());
        mock.add("quote");
        var provider = mock.provider();
        var one = provider.marketQuote("BBCA").toFuture();
        var two = provider.marketQuote("BBCA").toFuture();
        assertThat(provider.marketNews().block().reason()).isEqualTo("CONCURRENCY_LIMIT");
        reply.tryEmitValue(httpResponse(200, fixture("quote")));
        assertThat(one.join()).isEqualTo(two.join());
        mock.clock.advance(Duration.ofMinutes(3));
        assertThat(provider.marketQuote("BBCA").block().quality()).isEqualTo(STALE);
        assertThat(mock.requests).hasSize(1);
        mock.clock.advance(Duration.ofMinutes(8));
        assertThat(provider.marketQuote("BBCA").block().quality()).isEqualTo(STALE);
        assertThat(mock.requests).hasSize(2);
    }

    @Test
    void calendarRejectsHolidaysUnknownDatesAndAfterHoursBrokerBuckets() {
        var mock = new MockHttp();
        mock.responses.add(response(200, fixture("broker-flow").replace("\"10:00\"", "\"13:00\"")));
        var provider = mock.provider();
        assertThat(provider.brokerFlow("BBCA", HOLIDAY).block().quality()).isEqualTo(UNVERIFIED);
        assertThat(provider.dailyOhlcv("BBCA", EARLIER.minusDays(1), SPECIAL).block().quality())
                .isEqualTo(UNVERIFIED);
        assertThat(mock.requests).isEmpty();
        assertThat(provider.brokerFlow("BBCA", SPECIAL).block().reason()).isEqualTo("OUTSIDE_TRADING_SESSION");
        assertThat(mock.requests).hasSize(1);
    }

    @Test
    void suppressesLegacyRoutesAndRankingWithoutSpendingBudget() {
        var mock = new MockHttp();
        var provider = mock.provider();
        StepVerifier.create(new WeeklyScreening(provider).topFive())
                .expectErrorSatisfies(error -> assertThat(error).isInstanceOfSatisfying(
                        ResponseStatusException.class, response ->
                            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)))
                .verify();
        StepVerifier.create(provider.quotes()).expectError(ResponseStatusException.class).verify();
        StepVerifier.create(provider.quote("BBCA")).expectError(ResponseStatusException.class).verify();
        StepVerifier.create(provider.headlines()).expectError(ResponseStatusException.class).verify();
        assertThat(mock.requests).isEmpty();
        assertThat(provider.remainingRequestBudget()).isEqualTo(20);
    }

    @Test
    void rejectsIncompleteQuoteAndLookAheadNews() {
        var mock = new MockHttp();
        mock.responses.add(response(200, fixture("quote").replace("\"open\": \"9790\"", "\"open\": null")));
        mock.responses.add(response(200, fixture("news").replace("2026-11-13T10:04:00+07:00", "2026-11-14T10:04:00+07:00")));
        var provider = mock.provider();
        assertThat(provider.marketQuote("BBCA").block().quality()).isEqualTo(INCOMPLETE);
        assertThat(provider.marketNews().block().reason()).isEqualTo("FUTURE_PUBLICATION");
    }

    private static Mono<ClientResponse> response(int status, String body) {
        return Mono.just(httpResponse(status, body));
    }

    private static ClientResponse httpResponse(int status, String body) {
        return ClientResponse.create(HttpStatus.valueOf(status))
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).body(body).build();
    }

    private static String fixture(String name) {
        try (var in = ApiCoIdHttpProviderTest.class.getResourceAsStream("/phivealpha/api-co-id/" + name + ".json")) {
            assertThat(in).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> time = new AtomicReference<>(NOW);

        void advance(Duration duration) {
            time.updateAndGet(now -> now.plus(duration));
        }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant(), zone); }
        @Override public Instant instant() { return time.get(); }
    }

    private static final class MockHttp implements ExchangeFunction {
        final MutableClock clock = new MutableClock();
        final List<ClientRequest> requests = new CopyOnWriteArrayList<>();
        final Queue<Mono<ClientResponse>> responses = new ArrayDeque<>();
        boolean autoAdvance = true;
        int threshold = 2;
        int retries;
        int budget = 20;
        int maxPages = 4;

        void add(String... names) {
            for (String name : names) {
                responses.add(response(200, fixture(name)));
            }
        }

        @Override
        public Mono<ClientResponse> exchange(ClientRequest request) {
            requests.add(request);
            if (autoAdvance) {
                clock.advance(Duration.ofMillis(2));
            }
            var reply = responses.poll();
            if (reply == null) {
                throw new AssertionError("Unexpected request to mock");
            }
            return reply;
        }

        ApiCoIdHttpProvider provider() {
            var settings = new ApiCoIdSettings(URI.create("https://use.api.co.id"), Duration.ofSeconds(1),
                    Duration.ofSeconds(5), 1, Duration.ofMillis(2), budget, retries, Duration.ofMillis(2),
                    threshold, Duration.ofSeconds(60), Duration.ofMinutes(10), 8, maxPages, 100,
                    Map.of(ApiCoIdEndpoint.QUOTE, Duration.ofMinutes(2)), null, Set.of("BBCA", "BBRI"),
                    "qa-universe-v1");
            var client = WebClient.builder().exchangeFunction(this).defaultHeader("x-api-co-id", KEY).build();
            var calendar = new VersionedIdxCalendar(JsonMapper.builder().build().readTree(fixture("calendar")));
            return new ApiCoIdHttpProvider(settings, new ApiCoIdTransport(client, settings, clock), clock, calendar);
        }
    }
}
