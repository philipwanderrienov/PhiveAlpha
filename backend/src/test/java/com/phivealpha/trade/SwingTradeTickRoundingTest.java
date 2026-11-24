package com.phivealpha.trade;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class SwingTradeTickRoundingTest {
    private static final Instant CUTOFF = Instant.parse("2026-11-24T04:00:00Z");

    private static BigDecimal n(String amount) { return new BigDecimal(amount); }

    private static SwingTradeMarketRules.Reference rules() {
        // Artificial historical test reference; not claimed to be IDX price bands.
        return new SwingTradeMarketRules.Reference("TEST", "FIXTURE", "v1", CUTOFF.minusSeconds(3600),
                LocalDate.parse("2026-11-01"), null, true, 100, List.of(
                new SwingTradeMarketRules.TickBand(n("1"), n("200"), n("1")),
                new SwingTradeMarketRules.TickBand(n("200"), null, n("5"))));
    }

    private static SwingTradeTickRounding.Result round(String amount, SwingTradeTickRounding.Direction direction) {
        return SwingTradeTickRounding.round(rules(), "TEST", CUTOFF, n(amount), direction);
    }

    @Test
    void roundsBelowBoundaryUsingCorrectTick() {
        assertThat(round("192.3", SwingTradeTickRounding.Direction.UP).priceIdr())
                .isEqualByComparingTo("193");
        assertThat(round("192.3", SwingTradeTickRounding.Direction.DOWN).priceIdr())
                .isEqualByComparingTo("192");
    }

    @Test
    void roundsAcrossTickBoundaryWithoutApplyingStaleTick() {
        assertThat(round("199.8", SwingTradeTickRounding.Direction.UP).priceIdr())
                .isEqualByComparingTo("200");
        assertThat(round("199.8", SwingTradeTickRounding.Direction.DOWN).priceIdr())
                .isEqualByComparingTo("199");
        assertThat(round("201", SwingTradeTickRounding.Direction.UP).priceIdr())
                .isEqualByComparingTo("205");
        assertThat(round("201", SwingTradeTickRounding.Direction.DOWN).priceIdr())
                .isEqualByComparingTo("200");
    }

    @Test
    void exactTickIsUnchangedInBothDirections() {
        assertThat(round("205", SwingTradeTickRounding.Direction.UP).priceIdr())
                .isEqualByComparingTo("205");
        assertThat(round("205", SwingTradeTickRounding.Direction.DOWN).priceIdr())
                .isEqualByComparingTo("205");
    }

    @Test
    void futureReferenceBlocksRoundingAndDoesNotReturnPrice() {
        var current = rules();
        var future = new SwingTradeMarketRules.Reference(current.symbol(), current.source(),
                current.version(), CUTOFF.plusSeconds(1), current.effectiveFrom(),
                current.effectiveUntil(), true, current.boardLotShares(), current.bands());
        var result = SwingTradeTickRounding.round(future, "TEST", CUTOFF,
                n("201"), SwingTradeTickRounding.Direction.UP);
        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).isEqualTo(SwingTradeMarketRules.Reason.REFERENCE_AFTER_CUTOFF);
        assertThat(result.priceIdr()).isNull();
    }

    @Test
    void unverifiedReferenceAndWrongSymbolBlockRounding() {
        var current = rules();
        var unverified = new SwingTradeMarketRules.Reference(current.symbol(), current.source(),
                current.version(), current.availableAt(), current.effectiveFrom(),
                current.effectiveUntil(), false, current.boardLotShares(), current.bands());
        assertThat(SwingTradeTickRounding.round(unverified, "TEST", CUTOFF,
                n("201"), SwingTradeTickRounding.Direction.UP).valid()).isFalse();
        assertThat(SwingTradeTickRounding.round(current, "OTHER", CUTOFF,
                n("201"), SwingTradeTickRounding.Direction.UP).reason())
                .isEqualTo(SwingTradeMarketRules.Reason.SYMBOL_MISMATCH);
    }

    @Test
    void missingRulesAndOutOfRangePricesNeverProduceLevels() {
        assertThat(SwingTradeTickRounding.round(null, "TEST", CUTOFF, n("201"),
                SwingTradeTickRounding.Direction.UP).priceIdr()).isNull();
        var result = round("0", SwingTradeTickRounding.Direction.DOWN);
        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).isEqualTo(SwingTradeMarketRules.Reason.PRICE_OUTSIDE_TICK_BANDS);
        assertThat(result.priceIdr()).isNull();
    }
}
