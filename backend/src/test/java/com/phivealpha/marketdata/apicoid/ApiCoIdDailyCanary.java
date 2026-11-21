package com.phivealpha.marketdata.apicoid;

import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.json.JsonMapper;

public final class ApiCoIdDailyCanary {
    private ApiCoIdDailyCanary() {}

    public static void main(String[] args) {
        System.exit(run(args, System.getenv()));
    }

    static int run(String[] args, Map<String, String> env) {
        if (ApiCoIdQuoteCanary.CI_MARKERS.stream().anyMatch(env::containsKey)
                || !Arrays.equals(args, new String[] {"--confirm-live"})) {
            System.err.println("Daily canary requires --confirm-live outside CI.");
            return 2;
        }
        Report result;
        try {
            var date = LocalDate.parse(env.get("PHIVEALPHA_DAILY_CANARY_DATE"));
            var clock = Clock.systemUTC();
            var maxAge = ApiCoIdQuoteCanary.checkApproval(env.get("PHIVEALPHA_DAILY_CANARY_APPROVAL_PATH"),
                    clock.instant(), scope(date), true);
            var access = ApiCoIdAccess.load(env.get("PHIVEALPHA_API_APPROVAL_PATH"),
                    env.get("PHIVEALPHA_API_QUOTA_LEDGER_PATH"), clock)
                    .restrictedToFree(java.util.Set.of(ApiCoIdAccess.Dataset.DAILY));
            access.check(ApiCoIdAccess.Dataset.DAILY);
            var settings = settings(env.get("API_CO_ID_CALENDAR_PATH"), Duration.ofSeconds(maxAge));
            var calendar = ApiCoIdConfiguration.readCalendar(settings.calendarPath());
            if (!calendar.isClosedBy(date, clock.instant())) {
                return 2;
            }
            ApiCoIdQuoteCanary.disableLogging();
            result = fetch(ApiCoIdConfiguration.client(settings, env.get("API_CO_ID_KEY")),
                    settings, clock, calendar, date, access);
        } catch (IOException | RuntimeException rejected) {
            System.err.println("Daily canary stopped. Check approval, entitlement, ledger, calendar and credential setup.");
            return 2;
        }
        System.out.println(JsonMapper.builder().build().writeValueAsString(result));
        return result.rows() == 1 ? 0 : 1;
    }

    static String scope(LocalDate date) {
        return "GET https://use.api.co.id/idx/v2/chart/daily/BBCA?timeframe=1d&order=asc&from=" + date + "&to=" + date;
    }

    static ApiCoIdSettings settings(String calendar, Duration maxAge) {
        return new ApiCoIdSettings(URI.create(ApiCoIdMacroCache.ORIGIN), Duration.ofSeconds(8),
                Duration.ofSeconds(12), 1, Duration.ofSeconds(1), 1, 0, Duration.ofSeconds(1),
                1, Duration.ofMinutes(1), Duration.ofSeconds(1), 1, 1, 1,
                Map.of(ApiCoIdEndpoint.DAILY, maxAge), calendar, null, null);
    }

    static Report fetch(WebClient.Builder client, ApiCoIdSettings settings, Clock clock,
                        VersionedIdxCalendar calendar, LocalDate date, ApiCoIdAccess access) {
        var status = new AtomicInteger();
        var transport = new ApiCoIdTransport(client.filter((request, next) -> next.exchange(request)
                .doOnNext(response -> status.set(response.statusCode().value()))).build(),
                settings, clock, ignored -> {}, access);
        var provider = new ApiCoIdHttpProvider(settings, transport, clock, calendar);
        long start = System.nanoTime();
        var result = provider.dailyOhlcv("BBCA", date, date).block();
        var source = result.provenance();
        return new Report("DAILY", "BBCA", date, status.get() == 0 ? null : status.get(),
                Duration.ofNanos(System.nanoTime() - start).toMillis(),
                settings.requestBudget() - transport.remainingBudget(), result.rows().size(),
                source.asOf(), source.fetchedAt(), result.quality(), result.reason());
    }

    record Report(String dataset, String symbol, LocalDate date, Integer httpStatus, long elapsedMillis,
                  int requestCount, int rows, java.time.Instant asOf, java.time.Instant fetchedAt,
                  ApiCoIdData.Quality quality, String reason) {}
}
