package com.phivealpha.marketdata.apicoid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

class ApiCoIdQuoteCanaryTest {
    private static final String SECRET = "synthetic-private-key";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-13T09:31:00Z"), ZoneOffset.UTC);

    @TempDir Path tmp;

    @Test
    void guardsFailBeforeAnyLiveSetup() {
        assertThat(ApiCoIdQuoteCanary.run(new String[0], Map.of())).isEqualTo(2);
        assertThat(ApiCoIdQuoteCanary.run(new String[] {"--confirm-live"},
                Map.of("CI", "false"))).isEqualTo(2);
        assertThat(ApiCoIdQuoteCanary.run(new String[] {"--confirm-live"},
                Map.of("API_CO_ID_KEY", SECRET))).isEqualTo(2);
    }

    @Test
    void requiresUnexpiredApprovalForThisExactQuoteAndEntitlement() throws IOException {
        var file = tmp.resolve("approval.properties");
        var valid = """
                scope=GET https://use.api.co.id/idx/v2/chart/quote/BBCA
                entitlement-confirmed=true
                single-request-approved=true
                entitlement-reference=synthetic-vendor-ref
                contract-reference=synthetic-contract-ref
                operator-approval-reference=synthetic-approval-ref
                expires-at=2026-11-13T10:00:00Z
                max-age-seconds=120
                """;
        Files.writeString(file, valid);
        assertThat(ApiCoIdQuoteCanary.checkApproval(file.toString(), CLOCK.instant())).isEqualTo(120);
        assertThatThrownBy(() -> ApiCoIdQuoteCanary.checkApproval(file.toString(),
                Instant.parse("2026-11-13T10:00:00Z"))).isInstanceOf(IllegalArgumentException.class);
        for (var invalid : new String[] {valid.replace("BBCA", "BBRI"),
                valid.replace("entitlement-confirmed=true", "entitlement-confirmed=false"),
                valid.replace("contract-reference=synthetic-contract-ref", "contract-reference=")}) {
            Files.writeString(file, invalid);
            assertThatThrownBy(() -> ApiCoIdQuoteCanary.checkApproval(file.toString(), CLOCK.instant()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @ParameterizedTest
    @CsvSource({
        "200, valid, 120, UNVERIFIED, ACCEPTED",
        "200, valid, 30, STALE, ACCEPTED",
        "200, old-lag, 120, STALE, ACCEPTED",
        "200, missing, 120, INCOMPLETE, REJECTED",
        "200, invalid, 120, INCOMPLETE, REJECTED",
        "200, cursor, 120, INCOMPLETE, REJECTED",
        "302, invalid, 120, UNAVAILABLE, NOT_RUN",
        "403, invalid, 120, UNAVAILABLE, NOT_RUN",
        "429, invalid, 120, UNAVAILABLE, NOT_RUN",
        "503, invalid, 120, UNAVAILABLE, NOT_RUN"
    })
    void reportsSafeMetadataAndUsesOneAdmission(int status, String scenario, long freshness,
                                                String quality, String parser) throws IOException {
        var hits = new AtomicInteger();
        var body = switch (scenario) {
            case "missing" -> fixture("quote").replace("\"volume\": \"12345678\",", "");
            case "cursor" -> fixture("quote").replace("\"cursor\": null", "\"cursor\": \"private-cursor\"");
            case "invalid" -> SECRET;
            case "old-lag" -> fixture("quote").replace("\"stale_seconds\": \"5\"", "\"stale_seconds\": \"180\"");
            default -> fixture("quote");
        };
        var settings = ApiCoIdQuoteCanary.settings(null, Duration.ofSeconds(freshness));
        var client = ApiCoIdConfiguration.client(settings, SECRET).exchangeFunction(request -> {
            hits.incrementAndGet();
            assertThat(request.url().toString()).isEqualTo("https://use.api.co.id/idx/v2/chart/quote/BBCA");
            assertThat(request.headers().getFirst("x-api-co-id")).isEqualTo(SECRET);
            return Mono.just(ClientResponse.create(HttpStatusCode.valueOf(status)).body(body).build());
        });
        var report = ApiCoIdQuoteCanary.fetch(client, settings, CLOCK, calendar());
        assertThat(report.httpStatus()).isEqualTo(status);
        assertThat(report.requestCount()).isEqualTo(1);
        assertThat(hits.get()).isEqualTo(1);
        assertThat(report.quality().name()).isEqualTo(quality);
        assertThat(report.parserStatus()).isEqualTo(parser);
        if ("missing".equals(scenario)) {
            assertThat(report.fieldPresence()).containsEntry("volume", false).containsEntry("last", true);
        }
        if ("valid".equals(scenario) || "old-lag".equals(scenario)) {
            assertThat(report.asOf()).isEqualTo(Instant.parse("2026-11-13T09:30:00Z"));
            assertThat(report.dataAgeSeconds()).isEqualTo("valid".equals(scenario) ? 60L : 180L);
        }
        assertThat(JsonMapper.builder().build().writeValueAsString(report))
                .doesNotContain(SECRET, "private-cursor", "Bank Contoh A", "12345678", "Authorization");
    }

    @Test
    void realHttpConnectorDoesNotFollowRedirectOrRetry() throws IOException {
        HttpServer server;
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (SocketException denied) {
            assumeTrue(false, "Loopback unavailable: " + denied.getMessage());
            return;
        }
        try {
            var hits = new AtomicInteger();
            var response = new AtomicInteger(302);
            server.createContext("/", exchange -> {
                hits.incrementAndGet();
                exchange.getResponseHeaders().set("Location", "/redirect-target");
                if (response.get() == 0) {
                    exchange.close();
                    return;
                }
                exchange.sendResponseHeaders(response.get(), -1);
                exchange.close();
            });
            server.start();
            var settings = ApiCoIdQuoteCanary.settings(null, Duration.ofMinutes(2));
            for (int status : new int[] {302, 503, 0}) {
                hits.set(0);
                response.set(status);
                var client = ApiCoIdConfiguration.client(settings, SECRET).filter((request, next) ->
                        next.exchange(ClientRequest.from(request).url(URI.create("http://127.0.0.1:"
                                + server.getAddress().getPort() + request.url().getRawPath())).build()));
                var report = ApiCoIdQuoteCanary.fetch(client, settings, CLOCK, calendar());
                assertThat(report.httpStatus()).isEqualTo(status == 0 ? null : status);
                assertThat(report.quality()).isEqualTo(ApiCoIdData.Quality.UNAVAILABLE);
                assertThat(report.requestCount()).isEqualTo(1);
                assertThat(hits.get()).isEqualTo(1);
            }
        } finally {
            server.stop(0);
        }
    }

    private static VersionedIdxCalendar calendar() throws IOException {
        return new VersionedIdxCalendar(JsonMapper.builder().build().readTree(fixture("calendar")));
    }

    private static String fixture(String name) throws IOException {
        try (var input = ApiCoIdQuoteCanaryTest.class.getResourceAsStream("/phivealpha/api-co-id/" + name + ".json")) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
