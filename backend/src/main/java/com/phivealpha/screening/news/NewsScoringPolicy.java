package com.phivealpha.screening.news;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.stream.Stream;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("phivealpha.news-scoring")
public record NewsScoringPolicy(
        @DefaultValue("30") BigDecimal newsWeight,
        @DefaultValue("25") BigDecimal momentumWeight,
        @DefaultValue("20") BigDecimal liquidityWeight,
        @DefaultValue("15") BigDecimal flowWeight,
        @DefaultValue("10") BigDecimal regimeWeight,
        @DefaultValue("7d") Duration maxSnapshotAge,
        @DefaultValue("7d") Duration newsLookback) {

    public NewsScoringPolicy {
        var weights = Stream.of(newsWeight, momentumWeight, liquidityWeight, flowWeight, regimeWeight).toList();
        if (weights.stream().anyMatch(weight -> weight == null || weight.signum() <= 0)
                || weights.stream().reduce(BigDecimal.ZERO, BigDecimal::add).compareTo(BigDecimal.valueOf(100)) != 0
                || maxSnapshotAge == null || maxSnapshotAge.isNegative() || maxSnapshotAge.isZero()
                || newsLookback == null || newsLookback.isNegative() || newsLookback.isZero()) {
            throw new IllegalArgumentException("Invalid research scoring policy");
        }
    }
}
