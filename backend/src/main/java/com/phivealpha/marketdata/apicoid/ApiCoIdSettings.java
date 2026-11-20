package com.phivealpha.marketdata.apicoid;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("phivealpha.api-co-id")
record ApiCoIdSettings(URI baseUrl, Duration timeout, Duration operationTimeout, int concurrency,
                       Duration minRequestInterval, int requestBudget, int maxRetries, Duration retryDelay,
                       int breakerThreshold, Duration breakerCooldown, Duration cacheTtl, int cacheEntries,
                       int maxPages, int maxRows, Map<ApiCoIdEndpoint, Duration> maxAge,
                       String calendarPath, Set<String> expectedUniverse, String universeVersion) {
    ApiCoIdSettings {
        if (baseUrl == null || !"https".equals(baseUrl.getScheme())
                || !"use.api.co.id".equalsIgnoreCase(baseUrl.getHost())
                || (baseUrl.getPort() != -1 && baseUrl.getPort() != 443)
                || baseUrl.getRawUserInfo() != null || baseUrl.getRawQuery() != null
                || baseUrl.getRawFragment() != null || !Set.of("", "/").contains(baseUrl.getRawPath())) {
            throw new IllegalArgumentException("Configure the official HTTPS API.co.id origin");
        }
        if (!positive(timeout) || !positive(operationTimeout) || !positive(minRequestInterval)
                || !positive(retryDelay) || !positive(breakerCooldown) || !positive(cacheTtl)
                || concurrency < 1 || concurrency > 32 || requestBudget < 1 || maxRetries < 0 || maxRetries > 2
                || breakerThreshold < 1 || cacheEntries < 1 || cacheEntries > 64
                || maxPages < 1 || maxPages > 100 || maxRows < 1 || maxRows > 20_000) {
            throw new IllegalArgumentException("Invalid API.co.id request safety limits");
        }
        maxAge = maxAge == null ? Map.of() : Map.copyOf(maxAge);
        if (maxAge.values().stream().anyMatch(age -> !positive(age))) {
            throw new IllegalArgumentException("Invalid API.co.id freshness limit");
        }
        expectedUniverse = expectedUniverse == null ? Set.of() : expectedUniverse.stream()
                .map(ApiCoIdPayload::symbol).collect(Collectors.toUnmodifiableSet());
        if (!expectedUniverse.isEmpty() && (universeVersion == null || universeVersion.isBlank()
                || expectedUniverse.size() > maxRows)) {
            throw new IllegalArgumentException("Configure a version and adequate row budget for the approved universe");
        }
    }

    private static boolean positive(Duration duration) {
        return duration != null && !duration.isNegative() && !duration.isZero()
                && duration.compareTo(Duration.ofDays(7)) <= 0;
    }
}
