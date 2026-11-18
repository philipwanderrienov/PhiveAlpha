package com.coinmind.manifold.model;

import java.time.Instant;
import java.util.List;

public record PredictionSourceHealth(
        List<SourceStatus> sources,
        Instant checkedAt,
        java.util.Map<String, SourceStatus> symbols
) {
    public record SourceStatus(
            String source,
            String state,
            String detail,
            Instant lastCheckedAt,
            int lastSignalCount,
            String symbol
    ) {
    }
}
