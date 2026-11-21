package com.phivealpha.marketdata.apicoid;

import static com.phivealpha.marketdata.apicoid.ApiCoIdAccess.Dataset.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import tools.jackson.databind.json.JsonMapper;

class ApiCoIdFreeIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-11-13T09:31:00Z");
    private static final long FX_TIMESTAMP = NOW.minusSeconds(30).toEpochMilli();
    private static final String FX = """
            {"is_success":true,"data":{"pair":"USDIDR","rate":16711.1234567890123456789,"updated_at":%d}}
            """.formatted(FX_TIMESTAMP);
    private static final String HOLIDAYS = """
            {"is_success":true,"data":[
                {"id":"national","date":"2026-11-13","name":"Synthetic holiday","type":"National Holiday","is_national_holiday":true},
                {"id":"regional","date":"2026-11-13","name":"Synthetic local holiday","type":"Observance","is_national_holiday":false}
            ],"paging":{"page":1,"size":100,"total_item":2,"total_page":1}}
            """;
    private static final String SECRET = "synthetic-mock-key-private";
    @TempDir Path tmp;

    @Test
    void exchangeRequiresEvidenceAndReassessesFreshnessOnCacheHit() throws Exception {
        var clock = new MutableClock();
        var access = access(clock, journal(), 5, Set.of(USD_IDR), Set.of(USD_IDR));
        var hits = new AtomicInteger();
        var adapter = fx(clock, access, hits, () -> answer(200, FX), 0);
        var snapshot = adapter.usdIdr().block();
        assertThat(snapshot.quality()).isEqualTo(MacroData.Quality.VERIFIED);
        assertThat(snapshot.purpose()).isEqualTo("RESEARCH_ONLY");
        assertThat(snapshot.data().base()).isEqualTo("USD");
        assertThat(snapshot.data().quote()).isEqualTo("IDR");
        assertThat(snapshot.data().rate()).isEqualByComparingTo("16711.1234567890123456789");
        assertThat(snapshot.provenance().asOf()).isEqualTo(NOW.minusSeconds(30));
        assertThat(snapshot.provenance().fetchedAt()).isEqualTo(NOW);
        clock.advance(Duration.ofMinutes(2));
        assertThat(adapter.usdIdr().block().quality()).isEqualTo(MacroData.Quality.STALE);
        assertThat(hits.get()).isEqualTo(1);
        clock.advance(Duration.ofMinutes(31));
        assertThat(adapter.usdIdr().block().quality()).isEqualTo(MacroData.Quality.STALE);
        assertThat(hits.get()).isEqualTo(2);
        assertThat(access.usage(ApiCoIdAccess.Product.FX).used()).isEqualTo(2);
    }

    @Test
    void duplicateInFlightRequestsShareOneAttemptAndUnreviewedDataIsUnverified() throws Exception {
        var clock = new MutableClock();
        var access = access(clock, journal(), 5, Set.of(USD_IDR), Set.of());
        var hits = new AtomicInteger();
        var response = Sinks.<ClientResponse>one();
        var adapter = fx(clock, access, hits, response::asMono, 0);
        var results = Mono.zip(adapter.usdIdr(), adapter.usdIdr()).toFuture();
        assertThat(response.tryEmitValue(clientResponse(200, FX))).isEqualTo(Sinks.EmitResult.OK);
        var pair = results.get(5, java.util.concurrent.TimeUnit.SECONDS);
        assertThat(pair.getT1()).isEqualTo(pair.getT2());
        assertThat(pair.getT1().quality()).isEqualTo(MacroData.Quality.UNVERIFIED);
        assertThat(hits.get()).isEqualTo(1);
    }

    @Test
    void persistentQuotaSurvivesProviderRecreationAndDoesNotBorrowOtherProductBudget() throws Exception {
        var clock = new MutableClock();
        var path = journal();
        var datasets = Set.of(USD_IDR, NATIONAL_HOLIDAYS, DAILY);
        var first = access(clock, path, 1, datasets, Set.of());
        var hits = new AtomicInteger();
        assertThat(fx(clock, first, hits, () -> answer(200, FX), 0).usdIdr().block().data()).isNotNull();
        var restarted = access(clock, path, 1, datasets, Set.of());
        var denial = fx(clock, restarted, hits, () -> answer(200, FX), 0).usdIdr().block();
        assertThat(denial.reason()).isEqualTo("PERSISTENT_BUDGET_EXHAUSTED");
        assertThat(hits.get()).isEqualTo(1);
        var holiday = new IndonesiaHolidayCalendarProvider(transport(clock, restarted, new AtomicInteger(),
                () -> answer(200, HOLIDAYS), 0), restarted, clock, Duration.ofDays(30),
                Duration.ofDays(30), new ApiCoIdDiagnostics());
        assertThat(holiday.holidays(2026).block().data()).isNotNull();
        assertThat(restarted.usage(ApiCoIdAccess.Product.HOLIDAY).used()).isEqualTo(1);
        assertThat(restarted.usage(ApiCoIdAccess.Product.IDX).used()).isZero();
        assertThat(Files.readString(path)).doesNotContain(SECRET, "Synthetic holiday", "16711", "https:");
    }

    @Test
    void missingOrCorruptLedgerAndUnverifiedWindowBlockBeforeHttp() throws Exception {
        var clock = new MutableClock();
        var empty = tmp.resolve("missing");
        var hits = new AtomicInteger();
        var access = access(clock, empty, 1, Set.of(USD_IDR), Set.of());
        assertThat(fx(clock, access, hits, () -> answer(200, FX), 0).usdIdr().block().reason())
                .isEqualTo("QUOTA_LEDGER_UNAVAILABLE");
        Files.writeString(empty, "PHIVEALPHA-QUOTA-V1\npartial");
        assertThat(fx(clock, access, hits, () -> answer(200, FX), 0).usdIdr().block().reason())
                .isEqualTo("QUOTA_LEDGER_UNAVAILABLE");
        var noWindow = new ApiCoIdAccess(Map.of(), Map.of(USD_IDR, grant(false)),
                new ApiCoIdQuotaLedger(empty), clock);
        assertThat(fx(clock, noWindow, hits, () -> answer(200, FX), 0).usdIdr().block().reason())
                .isEqualTo("QUOTA_WINDOW_UNAVAILABLE");
        assertThat(hits.get()).isZero();
    }

    @Test
    void conflictingWindowIsBlockedAndOnlyNonoverlappingVerifiedWindowResets() throws Exception {
        var clock = new MutableClock();
        var path = journal();
        var first = access(clock, path, 2, Set.of(USD_IDR), Set.of());
        first.reserve(USD_IDR);
        var overlapping = new ApiCoIdAccess(Map.of(ApiCoIdAccess.Product.FX,
                new ApiCoIdAccess.Window(NOW, NOW.plusSeconds(4000), 2)),
                Map.of(USD_IDR, grant(false)), new ApiCoIdQuotaLedger(path), clock);
        assertThatThrownBy(() -> overlapping.reserve(USD_IDR)).hasMessage("QUOTA_WINDOW_CONFLICT");
        assertThat(first.usage(ApiCoIdAccess.Product.FX).datasets().get(USD_IDR).pending()).isEqualTo(1);
        clock.advance(Duration.ofHours(1));
        assertThatThrownBy(() -> first.reserve(USD_IDR)).hasMessage("QUOTA_WINDOW_UNAVAILABLE");
        var next = new ApiCoIdAccess(Map.of(ApiCoIdAccess.Product.FX,
                new ApiCoIdAccess.Window(NOW.plusSeconds(3600), NOW.plusSeconds(7200), 2)),
                Map.of(USD_IDR, grant(false)), new ApiCoIdQuotaLedger(path), clock);
        assertThat(next.reserve(USD_IDR)).isNotNull();
        assertThat(next.usage(ApiCoIdAccess.Product.FX).used()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 429, 500})
    void statusIsSafeAndFailuresConsumeBudget(int status) throws Exception {
        var clock = new MutableClock();
        var path = journal();
        var access = access(clock, path, 3, Set.of(USD_IDR), Set.of());
        var hits = new AtomicInteger();
        var adapter = fx(clock, access, hits, () -> answer(status, SECRET), 0);
        var snapshot = adapter.usdIdr().block();
        assertThat(snapshot.quality()).isEqualTo(MacroData.Quality.UNAVAILABLE);
        assertThat(snapshot.data()).isNull();
        assertThat(access.usage(ApiCoIdAccess.Product.FX).datasets().get(USD_IDR).errors()).isEqualTo(1);
        if (status != 500) {
            var restarted = access(clock, path, 3, Set.of(USD_IDR), Set.of());
            assertThat(fx(clock, restarted, hits, () -> answer(200, FX), 0).usdIdr().block().reason())
                    .isEqualTo(status == 429 ? "PERSISTENT_RATE_LIMIT" : "PERSISTENT_ENTITLEMENT_BLOCK");
        }
        assertThat(hits.get()).isEqualTo(1);
    }

    @Test
    void transientRetryUsesSeparateDurableAdmissionAndTimeoutCounts() throws Exception {
        var clock = new MutableClock();
        var access = access(clock, journal(), 5, Set.of(USD_IDR), Set.of());
        var hits = new AtomicInteger();
        var retry = fx(clock, access, hits, () -> {
            clock.advance(Duration.ofSeconds(1));
            return hits.get() == 1 ? answer(500, SECRET) : answer(200, FX);
        }, 1);
        assertThat(retry.usdIdr().block().data()).isNotNull();
        assertThat(hits.get()).isEqualTo(2);
        assertThat(access.usage(ApiCoIdAccess.Product.FX).used()).isEqualTo(2);
        var timeout = new ExchangeRateProvider(transport(clock, access, hits, Mono::never, 0,
                Duration.ofMillis(100)), access, clock, Duration.ofMinutes(1), Duration.ofMinutes(1),
                new ApiCoIdDiagnostics());
        assertThat(timeout.usdIdr().block().reason()).isEqualTo("TIMEOUT");
        assertThat(access.usage(ApiCoIdAccess.Product.FX).used()).isEqualTo(3);
    }

    @Test
    void validatesExchangePairFieldsTimeUnitsAndFutureTime() {
        var clock = new MutableClock();
        for (String fixture : List.of(FX.replace("USDIDR", "IDRUSD"), FX.replace("rate", "missing"),
                FX.replace(Long.toString(FX_TIMESTAMP), "\"2026-11-13T09:30:00+07:00\""),
                FX.replace(Long.toString(FX_TIMESTAMP), Long.toString(FX_TIMESTAMP / 1000)),
                FX.replace(Long.toString(FX_TIMESTAMP), Long.toString(NOW.plusSeconds(5).toEpochMilli())),
                FX.replace("16711.1234567890123456789", "-1"))) {
            var adapter = fx(clock, null, new AtomicInteger(), () -> answer(200, fixture), 0);
            assertThat(adapter.usdIdr().block().quality()).isEqualTo(MacroData.Quality.INCOMPLETE);
        }
    }

    @Test
    void holidayCacheValidatesYearPagingAndDoesNotChangeBeiCalendar() throws Exception {
        var clock = new MutableClock();
        var access = access(clock, journal(), 5, Set.of(NATIONAL_HOLIDAYS), Set.of());
        var hits = new AtomicInteger();
        var diagnostics = new ApiCoIdDiagnostics();
        var adapter = new IndonesiaHolidayCalendarProvider(transport(clock, access, hits,
                () -> answer(200, HOLIDAYS), 0), access, clock, Duration.ofDays(30), Duration.ofDays(30), diagnostics);
        var calendar = adapter.holidays(2026).block();
        assertThat(calendar.quality()).isEqualTo(MacroData.Quality.UNVERIFIED);
        assertThat(calendar.provenance().asOf()).isNull();
        assertThat(calendar.provenance().version()).hasSize(64);
        assertThat(calendar.data().holidays()).extracting(MacroData.Holiday::id).containsExactly("national");
        assertThat(adapter.holidays(2026).block()).isEqualTo(calendar);
        assertThat(adapter.cachedVersion(2026, calendar.provenance().version()).block()).isEqualTo(calendar);
        assertThat(adapter.cachedVersion(2026, "0".repeat(64)).block().reason())
                .isEqualTo("CACHED_VERSION_UNAVAILABLE");
        assertThat(hits.get()).isEqualTo(1);
        assertThat(readCalendar().isTradingSession(LocalDate.parse("2026-11-13"))).isTrue();
        assertThatThrownBy(() -> adapter.holidays(1900).block()).isInstanceOf(IllegalArgumentException.class);
        clock.advance(Duration.ofSeconds(1));
        assertThat(adapter.holidays(2025).block().quality()).isEqualTo(MacroData.Quality.INCOMPLETE);
        assertThat(hits.get()).isEqualTo(2);

        for (var fixture : List.of(HOLIDAYS.replace("total_page\":1", "total_page\":2"),
                HOLIDAYS.replace("2026-11-13", "2026-11-31"), HOLIDAYS.replace("regional", "national"))) {
            var malformed = new IndonesiaHolidayCalendarProvider(transport(clock, null, new AtomicInteger(),
                    () -> answer(200, fixture), 0), null, clock, Duration.ofDays(30), Duration.ofDays(30), diagnostics);
            assertThat(malformed.holidays(2026).block().quality()).isEqualTo(MacroData.Quality.INCOMPLETE);
        }
    }

    @Test
    void dailyCanaryRequiresScopedFreeApprovalAndCannotFollowCursor() throws Exception {
        var date = LocalDate.parse("2026-11-13");
        assertThat(ApiCoIdDailyCanary.run(new String[0], Map.of())).isEqualTo(2);
        assertThat(ApiCoIdDailyCanary.run(new String[] {"--confirm-live"}, Map.of("CI", "false"))).isEqualTo(2);
        assertThat(ApiCoIdDailyCanary.run(new String[] {"--confirm-live"},
                Map.of("PHIVEALPHA_DAILY_CANARY_DATE", date.toString()))).isEqualTo(2);
        var approval = tmp.resolve("approval.properties");
        var content = """
                scope=%s
                entitlement-confirmed=true
                single-request-approved=true
                free-confirmed=true
                entitlement-reference=fixture
                contract-reference=fixture
                operator-approval-reference=fixture
                expires-at=%s
                max-age-seconds=120
                """.formatted(ApiCoIdDailyCanary.scope(date), NOW.plusSeconds(120));
        Files.writeString(approval, content);
        assertThat(ApiCoIdQuoteCanary.checkApproval(approval.toString(), NOW, ApiCoIdDailyCanary.scope(date), true))
                .isEqualTo(120L);
        assertThatThrownBy(() -> ApiCoIdQuoteCanary.checkApproval(approval.toString(), NOW.plusSeconds(120),
                ApiCoIdDailyCanary.scope(date), true)).hasMessageContaining("expired");
        Files.writeString(approval, content.replace("free-confirmed=true", "free-confirmed=false"));
        assertThatThrownBy(() -> ApiCoIdQuoteCanary.checkApproval(approval.toString(), NOW,
                ApiCoIdDailyCanary.scope(date), true)).hasMessageContaining("scoped");

        var clock = new MutableClock();
        var access = access(clock, journal(), 2, Set.of(DAILY), Set.of());
        var hits = new AtomicInteger();
        for (String cursor : List.of("null", "\"page-2\"")) {
            var settings = ApiCoIdDailyCanary.settings(null, Duration.ofMinutes(2));
            var client = WebClient.builder().exchangeFunction(request -> {
                assertThat(request.url()).isEqualTo(URI.create(ApiCoIdDailyCanary.scope(date).substring(4)));
                hits.incrementAndGet();
                return answer(200, """
                    {"is_success":true,"data":{"data":[{"date":"2026-11-13","open":100,"high":110,
                    "low":95,"close":101,"volume":1000}],
                    "meta":{"count":1,"as_of":"2026-11-13T09:30:00Z","cursor":%s}}}
                    """.formatted(cursor));
            });
            var report = ApiCoIdDailyCanary.fetch(client, settings, clock, readCalendar(), date, access);
            assertThat(report.requestCount()).isEqualTo(1);
            assertThat(report.quality()).isEqualTo("null".equals(cursor)
                    ? ApiCoIdData.Quality.UNVERIFIED : ApiCoIdData.Quality.INCOMPLETE);
        }
        assertThat(hits.get()).isEqualTo(2);
        assertThat(access.usage(ApiCoIdAccess.Product.IDX).used()).isEqualTo(2);
    }

    @Test
    void developerReportSeparatesVendorQuotaAndFixtureAndAuditRejectsProductionTargets() throws Exception {
        var clock = new MutableClock();
        var access = access(clock, journal(), 6, Set.of(USD_IDR), Set.of());
        access.reserve(USD_IDR);
        var controller = new ApiCoIdDeveloperController(access, new ApiCoIdDiagnostics(),
                new org.springframework.mock.env.MockEnvironment());
        var local = new java.net.InetSocketAddress("127.0.0.1", 12345);
        assertThat(ApiCoIdDeveloperController.authorized(SECRET, SECRET, local)).isTrue();
        assertThat(ApiCoIdDeveloperController.authorized(SECRET, null, local)).isFalse();
        assertThat(ApiCoIdDeveloperController.authorized(SECRET, SECRET,
                new java.net.InetSocketAddress("192.0.2.1", 12345))).isFalse();
        var report = controller.report();
        assertThat(report.products().get(ApiCoIdAccess.Product.IDX).mode()).isEqualTo("FIXTURE");
        var fx = report.products().get(ApiCoIdAccess.Product.FX);
        assertThat(fx.vendorQuotaStatus()).isEqualTo("UNVERIFIED");
        assertThat(fx.estimatedInternalRemaining()).isEqualTo(5);
        assertThat(fx.datasets().get(USD_IDR).attempts().pending()).isEqualTo(1);
        assertThat(JsonMapper.builder().build().writeValueAsString(report))
                .doesNotContain(SECRET, "BUY", "SETUP READY", tmp.toString());
        assertThatThrownBy(() -> ApiCoIdAuditWriter.requireDevelopment(
                "r2dbc:postgresql://127.0.0.1:5432/coinmind", "coinmind")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ApiCoIdAuditWriter.requireDevelopment(
                "r2dbc:postgresql://production.example:5432/phivealpha_dev_sit", "phivealpha_dev_user"))
                .isInstanceOf(IllegalArgumentException.class);
        ApiCoIdAuditWriter.requireDevelopment("r2dbc:postgresql://127.0.0.1:15432/phivealpha_dev_sit",
                "phivealpha_dev_user");
    }

    @Test
    void reviewedVendorObservationDoesNotReplaceInternalBudgetAndFreeGrantIsExplicit() throws Exception {
        var clock = new MutableClock();
        var journal = journal();
        var approval = tmp.resolve("reviewed.properties");
        Files.writeString(approval, """
                FX.start=2026-11-13T00:00:00Z
                FX.end=2026-12-13T00:00:00Z
                FX.reset-reference=synthetic-reset-evidence
                FX.vendor-quota-reference=synthetic-account-evidence
                FX.vendor-limit=100
                FX.vendor-remaining=4
                FX.vendor-observed-at=2026-11-13T09:30:00Z
                USD_IDR.entitlement-reference=synthetic-entitlement
                USD_IDR.expires=2026-11-13T09:32:00Z
                """);
        var access = ApiCoIdAccess.load(approval.toString(), journal.toString(), clock);
        assertThat(access.entitled(USD_IDR)).isTrue();
        assertThat(access.restrictedToFree(Set.of(USD_IDR)).freeEntitled(USD_IDR)).isFalse();
        var controller = new ApiCoIdDeveloperController(access, new ApiCoIdDiagnostics(),
                new org.springframework.mock.env.MockEnvironment());
        var fx = controller.report().products().get(ApiCoIdAccess.Product.FX);
        assertThat(fx.internalLimit()).isEqualTo(60);
        assertThat(fx.estimatedInternalRemaining()).isEqualTo(60);
        assertThat(fx.vendorQuotaStatus()).isEqualTo("CONFIRMED_OBSERVATION");
        assertThat(fx.vendorQuota().remainingAtObservation()).isEqualTo(4);
        Files.writeString(approval, Files.readString(approval) + "USD_IDR.free-confirmed=true\n");
        var free = ApiCoIdAccess.load(approval.toString(), journal.toString(), clock).restrictedToFree(Set.of(USD_IDR));
        assertThat(free.freeEntitled(USD_IDR)).isTrue();
        clock.advance(Duration.ofMinutes(1));
        assertThat(free.freeEntitled(USD_IDR)).isFalse();
    }

    @Test
    void durableReservationSurvivesSeparateJvmProcesses() throws Exception {
        var path = journal();
        assertThat(runQuotaProcess(path)).isEqualTo("RESERVED");
        assertThat(runQuotaProcess(path)).isEqualTo("PERSISTENT_BUDGET_EXHAUSTED");
    }

    private static String runQuotaProcess(Path path) throws Exception {
        var process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("surefire.test.class.path"), ApiCoIdFreeIntegrationTest.class.getName(),
                path.toString()).start();
        try {
            assertThat(process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThat(process.exitValue()).isZero();
            return new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).strip();
        } finally {
            process.destroyForcibly();
        }
    }

    public static void main(String[] args) {
        var access = access(Clock.fixed(NOW, ZoneOffset.UTC), Path.of(args[0]), 1, Set.of(USD_IDR), Set.of());
        try {
            access.reserve(USD_IDR);
            System.out.println("RESERVED");
        } catch (ApiCoIdFailure failure) {
            System.out.println(failure.reason);
        }
    }

    private Path journal() throws Exception {
        return Files.writeString(Files.createTempFile(tmp, "quota", ".journal"), "PHIVEALPHA-QUOTA-V1\n");
    }

    private static ApiCoIdAccess access(Clock clock, Path journal, int limit, Set<ApiCoIdAccess.Dataset> datasets,
                                       Set<ApiCoIdAccess.Dataset> verified) {
        var window = new ApiCoIdAccess.Window(NOW.minusSeconds(60), NOW.plusSeconds(3600), limit);
        var grants = new java.util.EnumMap<ApiCoIdAccess.Dataset, ApiCoIdAccess.Grant>(ApiCoIdAccess.Dataset.class);
        datasets.forEach(dataset -> grants.put(dataset, grant(verified.contains(dataset))));
        return new ApiCoIdAccess(Map.of(ApiCoIdAccess.Product.FX, window, ApiCoIdAccess.Product.IDX, window,
                ApiCoIdAccess.Product.HOLIDAY, window), grants, new ApiCoIdQuotaLedger(journal), clock);
    }

    private static ApiCoIdAccess.Grant grant(boolean verified) {
        return new ApiCoIdAccess.Grant(NOW.plus(Duration.ofDays(2)), verified, true, false);
    }

    private static ApiCoIdTransport transport(Clock clock, ApiCoIdAccess access, AtomicInteger hits,
                                              Supplier<Mono<ClientResponse>> responses, int retries) {
        return transport(clock, access, hits, responses, retries, Duration.ofSeconds(3));
    }

    private static ApiCoIdTransport transport(Clock clock, ApiCoIdAccess access, AtomicInteger hits,
                                              Supplier<Mono<ClientResponse>> responses, int retries, Duration timeout) {
        var settings = ApiCoIdRuntimeConfiguration.macroSettings(retries, timeout, 30);
        var client = ApiCoIdConfiguration.client(settings, SECRET).exchangeFunction(request -> {
            assertThat(request.url().getHost()).isEqualTo("use.api.co.id");
            assertThat(request.headers().getFirst("x-api-co-id")).isEqualTo(SECRET);
            hits.incrementAndGet();
            return responses.get();
        });
        return new ApiCoIdTransport(client.build(), settings, clock, ignored -> {}, access);
    }

    private static ExchangeRateProvider fx(Clock clock, ApiCoIdAccess access, AtomicInteger hits,
                                           Supplier<Mono<ClientResponse>> responses, int retries) {
        return new ExchangeRateProvider(transport(clock, access, hits, responses, retries), access, clock,
                Duration.ofMinutes(30), Duration.ofMinutes(1), new ApiCoIdDiagnostics());
    }

    private static VersionedIdxCalendar readCalendar() throws Exception {
        return ApiCoIdConfiguration.readCalendar(Path.of(ApiCoIdFreeIntegrationTest.class
                .getResource("/phivealpha/api-co-id/calendar.json").toURI()).toString());
    }

    private static Mono<ClientResponse> answer(int code, String body) {
        return Mono.just(clientResponse(code, body));
    }

    private static ClientResponse clientResponse(int code, String body) {
        return ClientResponse.create(HttpStatusCode.valueOf(code)).body(body).build();
    }

    private static final class MutableClock extends Clock {
        private volatile Instant now = NOW;
        void advance(Duration duration) { now = now.plus(duration); }
        @Override public Instant instant() { return now; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(now, zone); }
    }
}
