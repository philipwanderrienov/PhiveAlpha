package com.phivealpha.screening;

import java.math.BigDecimal;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("phivealpha.liquid-universe")
public record LiquidityPolicy(
        @DefaultValue("20") int lookbackSessions,
        @DefaultValue("20") int minListingSessions,
        @DefaultValue("15") int minActiveSessions,
        @DefaultValue("0.75") BigDecimal minActiveRatio,
        @DefaultValue("12") int minLiquidSessions,
        @DefaultValue("1000000000") BigDecimal minDailyTurnoverIdr,
        @DefaultValue("2000000000") BigDecimal minMedianTurnoverIdr,
        @DefaultValue("100000") long minDailyVolumeShares,
        @DefaultValue("7d") Duration maxPriceAge,
        @DefaultValue("7d") Duration maxReferenceAge) {

    public LiquidityPolicy {
        if (lookbackSessions < 1 || lookbackSessions > 252 || minListingSessions < 1 || minListingSessions > 252
                || minActiveSessions < 1 || minActiveSessions > lookbackSessions
                || minLiquidSessions < 1 || minLiquidSessions > lookbackSessions
                || minActiveRatio == null || minActiveRatio.signum() <= 0
                || minActiveRatio.compareTo(BigDecimal.ONE) > 0
                || minDailyTurnoverIdr == null || minDailyTurnoverIdr.signum() <= 0
                || minMedianTurnoverIdr == null || minMedianTurnoverIdr.signum() <= 0
                || minDailyVolumeShares < 1
                || maxPriceAge == null || maxPriceAge.isZero() || maxPriceAge.isNegative()
                || maxReferenceAge == null || maxReferenceAge.isZero() || maxReferenceAge.isNegative()) {
            throw new IllegalArgumentException("Invalid PhiveAlpha liquidity policy");
        }
    }
}
