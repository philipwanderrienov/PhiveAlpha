package com.coinmind.manifold;

import com.coinmind.manifold.api.ManifoldController;
import com.coinmind.manifold.service.ManifoldIntelligenceService;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;

class ManifoldMigrationTest {
    @Test void productionSourceHasNoRetiredPredictionContractsOrHosts() throws Exception {
        for (var root : java.util.List.of(java.nio.file.Path.of("src/main/java"), java.nio.file.Path.of("src/main/resources"), java.nio.file.Path.of("../frontend/src"))) {
            try (var paths = java.nio.file.Files.walk(root)) {
                for (var path : paths.filter(java.nio.file.Files::isRegularFile).toList()) {
                    var text = java.nio.file.Files.readString(path).toLowerCase(java.util.Locale.ROOT);
                    assertThat(text).as(path.toString()).doesNotContain("polymarket", "gamma-api.", "clob.", "kalshi", "finfeedapi");
                }
            }
        }
    }

    @Test void publicSearchRetainsNeutralContextAndCacheAndOldRoutesAreAbsent() throws Exception {
        var calls = new AtomicInteger();
        var service = new ManifoldIntelligenceService(new ObjectMapper(), "https://api.manifold.markets", 1000, 1000, 1000);
        var client = WebClient.builder().baseUrl("https://api.manifold.markets").exchangeFunction(request -> {
            calls.incrementAndGet();
            assertThat(request.url().getHost()).isEqualTo("api.manifold.markets");
            assertThat(request.url().getPath()).isEqualTo("/v0/search-markets");
            String body = "[{\"id\":\"test\",\"question\":\"Bitcoin above threshold?\",\"outcomeType\":\"BINARY\",\"probability\":0.9,\"closeTime\":"
                    + Instant.now().plusSeconds(86400).toEpochMilli() + ",\"volume24Hours\":10,\"totalLiquidity\":20}]";
            return Mono.just(ClientResponse.create(HttpStatus.OK).header("Content-Type", "application/json").body(body).build());
        }).build();
        var field = ManifoldIntelligenceService.class.getDeclaredField("manifoldClient");
        field.setAccessible(true); field.set(service, client);
        var http = WebTestClient.bindToController(new ManifoldController(service)).build();
        http.get().uri("/api/v1/manifold/BTC").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.source").isEqualTo("MANIFOLD")
                .jsonPath("$.bias").isEqualTo("NEUTRAL").jsonPath("$.marketCount").isEqualTo(1);
        http.get().uri("/api/v1/manifold/BTC").exchange().expectStatus().isOk();
        assertThat(calls).hasValue(1);
        assertThat(service.latestFresh("BTC").averageProbabilityChange()).isZero();
        http.get().uri("/api/v1/polymarket/BTC").exchange().expectStatus().isNotFound();
        http.post().uri("/api/v1/ai/polymarket-intelligence/BTC").exchange().expectStatus().isNotFound();
        http.get().uri("/api/v1/manifold/source-health").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.sources[0].source").isEqualTo("MANIFOLD");
    }
    @Test void independentSymbolHealthAndRecovery() throws Exception {
        var service = new ManifoldIntelligenceService(new ObjectMapper(), "https://api.manifold.markets", 1000, 1000, 1000);
        var failed = new java.util.concurrent.atomic.AtomicBoolean();
        var field = ManifoldIntelligenceService.class.getDeclaredField("manifoldClient"); field.setAccessible(true);
        field.set(service, WebClient.builder().baseUrl("https://api.manifold.markets").exchangeFunction(r -> {
            String query = r.url().getQuery();
            if (query.contains("solana") || failed.get()) return Mono.error(new IllegalStateException("offline"));
            String body = query.contains("bitcoin") ? "[{\"question\":\"Bitcoin?\",\"outcomeType\":\"BINARY\",\"probability\":0.5,\"closeTime\":" + Instant.now().plusSeconds(3600).toEpochMilli() + "}]" : "[]";
            return Mono.just(ClientResponse.create(HttpStatus.OK).body(body).build());
        }).build());
        service.refresh("BTC").block(); service.refresh("ETH").block(); service.refresh("SOL").block();
        var before = service.sourceHealth().symbols();
        assertThat(before.get("BTCUSDT").state()).isEqualTo("UP");
        assertThat(before.get("ETHUSDT").state()).isEqualTo("EMPTY");
        assertThat(before.get("SOLUSDT").state()).isEqualTo("UNAVAILABLE");
        service.refreshTrackedSymbols();
        assertThat(service.sourceHealth().symbols().get("BTCUSDT").state()).isEqualTo("UP");
        failed.set(true);
        assertThat(service.refresh("BTC").block().marketCount()).isZero();
        assertThat(service.latestFresh("BTC")).isNull();
        assertThat(service.sourceHealth().symbols().get("ETHUSDT").state()).isEqualTo("EMPTY");
    }
    @Test void transportFailureAndUnsupportedSymbolNeverInventMarkets() throws Exception {
        var service = new ManifoldIntelligenceService(new ObjectMapper(), "https://api.manifold.markets", 1000, 1000, 1000);
        var calls = new AtomicInteger();
        var field = ManifoldIntelligenceService.class.getDeclaredField("manifoldClient"); field.setAccessible(true);
        field.set(service, WebClient.builder().exchangeFunction(r -> { calls.incrementAndGet(); return Mono.error(new IllegalStateException("synthetic")); }).build());
        assertThat(service.refresh("BTC").block().source()).isEqualTo("NONE");
        assertThat(service.refresh("BTC").block().marketCount()).isZero();
        assertThat(service.refresh("UNKNOWN").block().marketCount()).isZero();
        assertThat(calls).hasValue(1);
    }
}
