package com.phivealpha.marketdata.apicoid;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class ApiCoIdDiagnostics {
    record Observation(String quality, String provider, Instant asOf, Instant fetchedAt, Instant ageAnchor,
                       String dataVersion, Instant lastSuccessfulIngestion) {}

    private final Map<ApiCoIdAccess.Dataset, Observation> recent = new ConcurrentHashMap<>();

    void record(ApiCoIdAccess.Dataset dataset, String quality, Instant asOf, Instant fetchedAt, Instant ageAnchor,
                String version, boolean accepted) {
        recent.compute(dataset, (key, previous) -> new Observation(quality, "API.co.id", asOf, fetchedAt, ageAnchor,
                version, accepted ? fetchedAt : previous == null ? null : previous.lastSuccessfulIngestion()));
    }

    Observation observation(ApiCoIdAccess.Dataset dataset) {
        return recent.get(dataset);
    }
}
