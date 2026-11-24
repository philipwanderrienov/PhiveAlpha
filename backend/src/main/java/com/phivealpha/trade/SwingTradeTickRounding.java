package com.phivealpha.trade;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Comparator;
import java.util.Objects;

/**
 * Independent per-level rounding from effective-dated and verified tick bands.
 * Artificial fixtures are permitted for testing only; no official IDX bands are embedded.
 */
public final class SwingTradeTickRounding {
    private SwingTradeTickRounding() {}

    public enum Direction { UP, DOWN }

    public record Result(boolean valid, SwingTradeMarketRules.Reason reason,
                         BigDecimal priceIdr, String ruleVersion) {}

    public static Result round(SwingTradeMarketRules.Reference reference, String symbol,
                               Instant cutoff, BigDecimal desiredPriceIdr, Direction direction) {
        Objects.requireNonNull(direction);
        var preflight = SwingTradeMarketRules.validateReference(reference, symbol, cutoff);
        if (!preflight.valid()) return blocked(preflight.reason(), reference);
        if (desiredPriceIdr == null || desiredPriceIdr.signum() <= 0) {
            return blocked(SwingTradeMarketRules.Reason.PRICE_OUTSIDE_TICK_BANDS, reference);
        }

        // Scan *all* bands: a rounded price may cross an exchange tick threshold.
        BigDecimal selected = null;
        var bands = reference.bands().stream()
                .sorted(Comparator.comparing(SwingTradeMarketRules.TickBand::fromInclusiveIdr)).toList();
        for (var band : bands) {
            var tick = band.tickSizeIdr();
            var candidate = desiredPriceIdr.divide(tick, 0,
                    direction == Direction.UP ? RoundingMode.CEILING : RoundingMode.FLOOR).multiply(tick);
            if (candidate.compareTo(band.fromInclusiveIdr()) < 0) {
                if (direction == Direction.DOWN) continue;
                candidate = band.fromInclusiveIdr().divide(tick, 0, RoundingMode.CEILING).multiply(tick);
            }
            if (band.toExclusiveIdr() != null && candidate.compareTo(band.toExclusiveIdr()) >= 0) {
                if (direction == Direction.UP) continue;
                candidate = band.toExclusiveIdr().divide(tick, 0, RoundingMode.CEILING)
                        .subtract(BigDecimal.ONE).multiply(tick);
                if (candidate.compareTo(band.fromInclusiveIdr()) < 0) continue;
            }
            if ((direction == Direction.UP && candidate.compareTo(desiredPriceIdr) < 0)
                    || (direction == Direction.DOWN && candidate.compareTo(desiredPriceIdr) > 0)) continue;
            if (selected == null || (direction == Direction.UP && candidate.compareTo(selected) < 0)
                    || (direction == Direction.DOWN && candidate.compareTo(selected) > 0)) selected = candidate;
        }
        if (selected == null || selected.signum() <= 0) {
            return blocked(SwingTradeMarketRules.Reason.PRICE_OUTSIDE_TICK_BANDS, reference);
        }
        return new Result(true, null, selected, reference.version());
    }

    private static Result blocked(SwingTradeMarketRules.Reason reason,
                                  SwingTradeMarketRules.Reference reference) {
        return new Result(false, reason, null, reference == null ? null : reference.version());
    }
}
