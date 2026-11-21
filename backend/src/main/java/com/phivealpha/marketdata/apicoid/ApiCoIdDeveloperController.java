package com.phivealpha.marketdata.apicoid;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@Profile("phivealpha-developer")
class ApiCoIdDeveloperController {
    private final ApiCoIdAccess access;
    private final ApiCoIdDiagnostics diagnostics;
    private final Environment environment;
    private final String token;

    ApiCoIdDeveloperController(ApiCoIdAccess access, ApiCoIdDiagnostics diagnostics, Environment environment) {
        var token = System.getenv("PHIVEALPHA_DEVELOPER_TOKEN");
        this.access = access;
        this.diagnostics = diagnostics;
        this.environment = environment;
        this.token = token != null && token.matches("[A-Za-z0-9_-]{24,128}") ? token : null;
    }

    @GetMapping("/internal/developer/api-co-id")
    Mono<Report> status(ServerWebExchange exchange) {
        var remote = exchange.getRequest().getRemoteAddress();
        var supplied = exchange.getRequest().getHeaders().getFirst("X-PhiveAlpha-Developer-Token");
        if (!authorized(token, supplied, remote)) {
            return Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND));
        }
        exchange.getResponse().getHeaders().setCacheControl("no-store");
        return Mono.fromCallable(this::report).subscribeOn(Schedulers.boundedElastic());
    }

    static boolean authorized(String token, String supplied, java.net.InetSocketAddress remote) {
        return token != null && remote != null && remote.getAddress() != null && remote.getAddress().isLoopbackAddress()
                && supplied != null && MessageDigest.isEqual(token.getBytes(StandardCharsets.US_ASCII),
                        supplied.getBytes(StandardCharsets.UTF_8));
    }

    Report report() {
        var now = Instant.now();
        var products = new EnumMap<ApiCoIdAccess.Product, ProductStatus>(ApiCoIdAccess.Product.class);
        for (var product : ApiCoIdAccess.Product.values()) {
            ApiCoIdQuotaLedger.Usage usage = null;
            String ledgerStatus = "WINDOW_UNVERIFIED";
            try {
                usage = access.usage(product);
                if (usage != null) {
                    ledgerStatus = "AVAILABLE";
                }
            } catch (ApiCoIdFailure unavailable) {
                ledgerStatus = "UNAVAILABLE";
            }
            var mode = mode(product);
            var datasets = new EnumMap<ApiCoIdAccess.Dataset, DatasetStatus>(ApiCoIdAccess.Dataset.class);
            for (var dataset : ApiCoIdAccess.Dataset.values()) {
                if (dataset.product != product) {
                    continue;
                }
                boolean inScope = product != ApiCoIdAccess.Product.IDX
                        || ApiCoIdRuntimeConfiguration.FREE_IDX.contains(dataset);
                var observation = diagnostics.observation(dataset);
                var age = observation == null || observation.ageAnchor() == null ? null
                        : Math.max(0, Duration.between(observation.ageAnchor(), now).toSeconds());
                var attempts = usage == null ? null : usage.datasets().getOrDefault(dataset,
                        new ApiCoIdQuotaLedger.DatasetUsage(0, 0, 0, null));
                datasets.put(dataset, new DatasetStatus(access.freeEntitled(dataset) ? "CONFIRMED" : "UNVERIFIED",
                        inScope, observation, age, attempts));
            }
            var vendor = access.vendorQuota(product);
            products.put(product, new ProductStatus(mode, ledgerStatus, access.configuredLimit(product),
                    vendor == null ? "UNVERIFIED" : "CONFIRMED_OBSERVATION", vendor, usage == null ? null : usage.used(), usage == null ? null : usage.estimatedRemaining(),
                    usage == null ? null : usage.end(), Map.copyOf(datasets)));
        }
        return new Report(now, Map.copyOf(products));
    }

    private String mode(ApiCoIdAccess.Product product) {
        return switch (product) {
            case IDX -> environment.matchesProfiles("phivealpha-api-co-id") ? "LIVE_OPT_IN" : "FIXTURE";
            case FX -> environment.matchesProfiles("phivealpha-api-co-id-fx") ? "LIVE_OPT_IN" : "DISABLED";
            case HOLIDAY -> environment.matchesProfiles("phivealpha-api-co-id-holiday") ? "LIVE_OPT_IN" : "DISABLED";
        };
    }

    record DatasetStatus(String entitlement, boolean inRuntimeScope, ApiCoIdDiagnostics.Observation data,
                         Long dataAgeSeconds,
                         ApiCoIdQuotaLedger.DatasetUsage attempts) {}
    record ProductStatus(String mode, String ledgerStatus, int internalLimit, String vendorQuotaStatus,
                         ApiCoIdAccess.VendorQuota vendorQuota, Integer internalUsed, Integer estimatedInternalRemaining, Instant windowEnd,
                         Map<ApiCoIdAccess.Dataset, DatasetStatus> datasets) {}
    record Report(Instant observedAt, Map<ApiCoIdAccess.Product, ProductStatus> products) {}
}
