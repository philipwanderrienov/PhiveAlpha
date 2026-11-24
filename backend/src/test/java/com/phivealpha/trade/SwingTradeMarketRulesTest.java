package com.phivealpha.trade;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class SwingTradeMarketRulesTest {
    private static final Instant CUTOFF = Instant.parse("2026-11-24T04:00:00Z");

    // Artificial bands, NOT asserted to be IDX exchange rules.
    private static SwingTradeMarketRules.Reference fixture() {
        return new SwingTradeMarketRules.Reference("TEST", "EXPERIMENTAL_VERIFIED_FIXTURE",
                "test-v1", CUTOFF.minusSeconds(3600), LocalDate.parse("2026-11-01"),
                null, true, 100, List.of(
                    new SwingTradeMarketRules.TickBand(n("1"), n("200"), n("1")),
                    new SwingTradeMarketRules.TickBand(n("200"), null, n("5"))));
    }

    private static BigDecimal n(String value) { return new BigDecimal(value); }

    private static SwingTradeMarketRules.Assessment check(SwingTradeMarketRules.Reference reference,
                                                          String entry, String stop, String target) {
        return SwingTradeMarketRules.validate(reference, "TEST", CUTOFF,
                n(entry), n(stop), n(target));
    }

    private static void rejected(SwingTradeMarketRules.Assessment result,
                                 SwingTradeMarketRules.Reason reason) {
        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).isEqualTo(reason);
        assertThat(result.boardLotShares()).isZero();
    }

    @Test
    void validatesFixturePricesAcrossDifferentTickBands() {
        var result = check(fixture(), "205", "190", "230");
        assertThat(result.valid()).isTrue();
        assertThat(result.reason()).isNull();
        assertThat(result.version()).isEqualTo("test-v1");
        assertThat(result.boardLotShares()).isEqualTo(100);
    }

    @Test
    void rejectsTickMismatchInHigherPriceBand() {
        rejected(check(fixture(), "201", "190", "230"),
                SwingTradeMarketRules.Reason.PRICE_NOT_ON_TICK);
    }

    @Test
    void refusesFutureOrUnverifiedReference() {
        var f = fixture();
        var future = new SwingTradeMarketRules.Reference(f.symbol(), f.source(), f.version(),
                CUTOFF.plusSeconds(1), f.effectiveFrom(), f.effectiveUntil(),
                true, f.boardLotShares(), f.bands());
        rejected(check(future, "205", "190", "230"),
                SwingTradeMarketRules.Reason.REFERENCE_AFTER_CUTOFF);
        var unverified = new SwingTradeMarketRules.Reference(f.symbol(), f.source(), f.version(),
                f.availableAt(), f.effectiveFrom(), f.effectiveUntil(), false,
                f.boardLotShares(), f.bands());
        rejected(check(unverified, "205", "190", "230"),
                SwingTradeMarketRules.Reason.REFERENCE_UNVERIFIED);
    }

    @Test
    void refusesWrongEffectiveDateAndWrongSymbol() {
        var f = fixture();
        var expired = new SwingTradeMarketRules.Reference(f.symbol(), f.source(), f.version(),
                f.availableAt(), f.effectiveFrom(), LocalDate.parse("2026-11-23"), true,
                f.boardLotShares(), f.bands());
        rejected(check(expired, "205", "190", "230"),
                SwingTradeMarketRules.Reason.OUTSIDE_EFFECTIVE_DATES);
        rejected(SwingTradeMarketRules.validate(f, "OTHER", CUTOFF,
                n("205"), n("190"), n("230")), SwingTradeMarketRules.Reason.SYMBOL_MISMATCH);
    }

    @Test
    void refusesOverlappingOrEmptyTickBands() {
        var f = fixture();
        var overlapping = new SwingTradeMarketRules.Reference(f.symbol(), f.source(), f.version(),
                f.availableAt(), f.effectiveFrom(), f.effectiveUntil(), true,
                100, List.of(new SwingTradeMarketRules.TickBand(n("1"), n("205"), n("1")),
                new SwingTradeMarketRules.TickBand(n("200"), null, n("5"))));
        rejected(check(overlapping, "205", "190", "230"),
                SwingTradeMarketRules.Reason.INVALID_TICK_BANDS);
        var empty = new SwingTradeMarketRules.Reference(f.symbol(), f.source(), f.version(),
                f.availableAt(), f.effectiveFrom(), f.effectiveUntil(), true, 100, List.of());
        rejected(check(empty, "205", "190", "230"),
                SwingTradeMarketRules.Reason.INVALID_TICK_BANDS);
    }

    @Test
    void refusesUnknownBandAndInvalidLot() {
        var f = fixture();
        rejected(check(f, "205", "0", "230"),
                SwingTradeMarketRules.Reason.PRICE_OUTSIDE_TICK_BANDS);
        var invalidLot = new SwingTradeMarketRules.Reference(f.symbol(), f.source(), f.version(),
                f.availableAt(), f.effectiveFrom(), f.effectiveUntil(), true, 0, f.bands());
        rejected(check(invalidLot, "205", "190", "230"),
                SwingTradeMarketRules.Reason.LOT_SIZE_UNVERIFIED);
    }

    @Test
    void missingReferenceAlwaysFailsClosed() {
        rejected(check(null, "205", "190", "230"),
                SwingTradeMarketRules.Reason.REFERENCE_MISSING);
    }
}
