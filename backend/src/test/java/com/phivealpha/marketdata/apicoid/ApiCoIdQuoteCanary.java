package com.phivealpha.marketdata.apicoid;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public final class ApiCoIdQuoteCanary {
    private static final String SYMBOL = "BBCA";
    private static final String SCOPE = "GET https://use.api.co.id/idx/v2/chart/quote/BBCA";
    static final List<String> CI_MARKERS = List.of("CI", "CONTINUOUS_INTEGRATION",
            "GITHUB_ACTIONS", "GITLAB_CI", "JENKINS_URL", "BUILD_BUILDID", "TF_BUILD",
            "CIRCLECI", "BUILDKITE", "TEAMCITY_VERSION", "TRAVIS", "BITBUCKET_BUILD_NUMBER", "CODEBUILD_BUILD_ID");
    private static final List<String> QUOTE_FIELDS = List.of(
            "symbol", "name", "date", "last", "open", "high", "low", "volume");

    private ApiCoIdQuoteCanary() {}

    public static void main(String[] args) {
        System.exit(run(args, System.getenv()));
    }

    static int run(String[] args, Map<String, String> env) {
        if (CI_MARKERS.stream().anyMatch(env::containsKey)) {
            System.err.println("Canary blocked in CI.");
            return 2;
        }
        if (!Arrays.equals(args, new String[] {"--confirm-live"})) {
            System.err.println("Canary requires --confirm-live and a reviewed local approval.");
            return 2;
        }

        Report report;
        try {
            var clock = Clock.systemUTC();
            long maxAgeSeconds = checkApproval(env.get("PHIVEALPHA_CANARY_APPROVAL_PATH"), clock.instant());
            var access = ApiCoIdAccess.load(env.get("PHIVEALPHA_API_APPROVAL_PATH"),
                    env.get("PHIVEALPHA_API_QUOTA_LEDGER_PATH"), clock);
            access.check(ApiCoIdAccess.Dataset.QUOTE);
            var settings = settings(env.get("API_CO_ID_CALENDAR_PATH"), Duration.ofSeconds(maxAgeSeconds));
            var calendar = ApiCoIdConfiguration.readCalendar(settings.calendarPath());
            // Use a known logging backend with no appenders or wire logging for this standalone process.
            disableLogging();
            var client = ApiCoIdConfiguration.client(settings, env.get("API_CO_ID_KEY"));
            report = fetch(client, settings, clock, calendar, access);
        } catch (IOException | RuntimeException rejected) {
            System.err.println("Canary stopped. Check approval, calendar, credential configuration and local runtime.");
            return 2;
        }

        System.out.println(JsonMapper.builder().build().writeValueAsString(report));
        return switch (report.quality()) {
            case UNVERIFIED, STALE -> 0;
            case COMPLETE, INCOMPLETE, UNAVAILABLE -> 1;
        };
    }

    static void disableLogging() {
        var logging = (LoggerContext) LoggerFactory.getILoggerFactory();
        logging.reset();
        logging.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).setLevel(Level.OFF);
    }

    static long checkApproval(String location, Instant now) throws IOException {
        return checkApproval(location, now, SCOPE, false);
    }

    static long checkApproval(String location, Instant now, String scope, boolean freeOnly) throws IOException {
        if (location == null || !Path.of(location).isAbsolute() || !Files.isRegularFile(Path.of(location))) {
            throw new IllegalArgumentException("Missing local canary approval");
        }
        var approval = new Properties();
        try (var input = Files.newInputStream(Path.of(location))) {
            byte[] bytes = input.readNBytes(4097);
            if (bytes.length > 4096) {
                throw new IllegalArgumentException("Oversized canary approval");
            }
            approval.load(new java.io.ByteArrayInputStream(bytes));
        }
        if (!scope.equals(approval.getProperty("scope"))
                || (freeOnly && !"true".equals(approval.getProperty("free-confirmed")))
                || !"true".equals(approval.getProperty("entitlement-confirmed"))
                || !"true".equals(approval.getProperty("single-request-approved"))
                || !hasReference(approval, "entitlement-reference")
                || !hasReference(approval, "contract-reference")
                || !hasReference(approval, "operator-approval-reference")
                || !Instant.parse(approval.getProperty("expires-at", "")).isAfter(now)) {
            throw new IllegalArgumentException("Missing or expired scoped canary approval");
        }
        long maxAge = Long.parseLong(approval.getProperty("max-age-seconds", ""));
        if (maxAge < 1 || maxAge > Duration.ofDays(7).toSeconds()) {
            throw new IllegalArgumentException("Invalid approved freshness limit");
        }
        return maxAge;
    }

    private static boolean hasReference(Properties approval, String name) {
        return !approval.getProperty(name, "").isBlank();
    }

    static ApiCoIdSettings settings(String calendarPath, Duration maxAge) {
        return new ApiCoIdSettings(URI.create("https://use.api.co.id"), Duration.ofSeconds(8),
                Duration.ofSeconds(10), 1, Duration.ofSeconds(1), 1, 0, Duration.ofSeconds(1),
                1, Duration.ofMinutes(1), Duration.ofSeconds(1), 1, 1, 1,
                Map.of(ApiCoIdEndpoint.QUOTE, maxAge), calendarPath, null, null);
    }

    static Report fetch(WebClient.Builder client, ApiCoIdSettings settings, Clock clock,
                        VersionedIdxCalendar calendar) {
        return fetch(client, settings, clock, calendar, null);
    }

    private static Report fetch(WebClient.Builder client, ApiCoIdSettings settings, Clock clock,
                                VersionedIdxCalendar calendar, ApiCoIdAccess access) {
        var httpStatus = new AtomicInteger();
        var fields = new AtomicReference<Map<String, Boolean>>(Map.of());
        var transport = new ApiCoIdTransport(client.filter((request, next) -> next.exchange(request)
                .doOnNext(response -> httpStatus.set(response.statusCode().value()))).build(), settings, clock,
                json -> fields.set(fieldPresence(json)), access);
        var provider = new ApiCoIdHttpProvider(settings, transport, clock, calendar);
        long start = System.nanoTime();
        var snapshot = provider.marketQuote(SYMBOL).block();
        long elapsedMillis = Duration.ofNanos(System.nanoTime() - start).toMillis();
        var provenance = snapshot.provenance();
        var observedAt = clock.instant();
        int status = httpStatus.get();
        String parser = switch (snapshot.quality()) {
            case UNAVAILABLE -> "NOT_RUN";
            case COMPLETE, INCOMPLETE -> "REJECTED";
            case STALE, UNVERIFIED -> "ACCEPTED";
        };
        return new Report(provenance.dataset(), SYMBOL, status == 0 ? null : status, elapsedMillis,
                settings.requestBudget() - transport.remainingBudget(), parser, fields.get(),
                provenance.asOf(), provenance.fetchedAt(), observedAt,
                provenance.ageAnchor() == null ? null : Duration.between(provenance.ageAnchor(), observedAt).toSeconds(),
                snapshot.rows().isEmpty() ? null : snapshot.rows().getFirst().tradingDate(),
                provenance.provider(), "REVIEWED_LOCAL_MANIFEST", snapshot.quality(), snapshot.reason());
    }

    private static Map<String, Boolean> fieldPresence(JsonNode json) {
        var fields = new LinkedHashMap<String, Boolean>();
        var wrapper = json.path("data");
        var row = wrapper.path("data");
        fields.put("is_success", !ApiCoIdPayload.absent(json.path("is_success")));
        fields.put("meta.count", !ApiCoIdPayload.absent(wrapper.path("meta").path("count")));
        fields.put("meta.as_of", !ApiCoIdPayload.absent(wrapper.path("meta").path("as_of")));
        for (String field : QUOTE_FIELDS) {
            fields.put(field, !ApiCoIdPayload.absent(row.path(field)));
        }
        return Map.copyOf(fields);
    }

    record Report(String dataset, String symbol, Integer httpStatus, long elapsedMillis,
                  int requestCount, String parserStatus, Map<String, Boolean> fieldPresence,
                  Instant asOf, Instant fetchedAt, Instant observedAt, Long dataAgeSeconds,
                  LocalDate tradingDate, String provider, String calendarProvenance,
                  ApiCoIdData.Quality quality, String reason) {}
}
