package com.phivealpha.trade;

import static org.assertj.core.api.Assertions.assertThat;

import com.phivealpha.persistence.IdxRecords.AdjustmentStatus;
import com.phivealpha.persistence.IdxRecords.DailyPrice;
import com.phivealpha.research.HistoricalReplay.CorporateAction;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;

class SwingTradeAdjustmentLineageTest {
    private static final Instant CUTOFF = Instant.parse("2026-11-24T04:00:00Z");
    private static final List<LocalDate> DATES = LocalDate.parse("2026-10-15")
            .datesUntil(LocalDate.parse("2026-11-24"))
            .filter(d -> d.getDayOfWeek().getValue() <= 5)
            .sorted(Comparator.reverseOrder()).limit(21).toList();

    private static BigDecimal n(String value) { return new BigDecimal(value); }

    private static DailyPrice candle(LocalDate date, AdjustmentStatus status, BigDecimal factor) {
        var at = date.atTime(9, 0).atOffset(ZoneOffset.UTC);
        return new DailyPrice("TEST", date, n("100").multiply(factor),
                n("110").multiply(factor), n("90").multiply(factor),
                n("105").multiply(factor), 1000, n("100000"), "SYNTHETIC",
                at, at.plusSeconds(1), status);
    }

    private static SwingTradeAdjustmentLineage.Factor factor(LocalDate date) {
        return new SwingTradeAdjustmentLineage.Factor("TEST", date, BigDecimal.ONE,
                CorporateAction.NONE, "SYNTHETIC_FACTOR", "v1",
                CUTOFF.minusSeconds(60), true);
    }

    private static SwingTradeAdjustmentLineage.Request request() {
        return new SwingTradeAdjustmentLineage.Request("TEST", CUTOFF, DATES,
                DATES.stream().map(d -> candle(d, AdjustmentStatus.UNADJUSTED, BigDecimal.ONE)).toList(),
                DATES.stream().map(d -> candle(d, AdjustmentStatus.ADJUSTED, BigDecimal.ONE)).toList(),
                DATES.stream().map(SwingTradeAdjustmentLineageTest::factor).toList());
    }

    private static SwingTradeAdjustmentLineage.Request adjusted(
            SwingTradeAdjustmentLineage.Request base,
            List<DailyPrice> prices, List<SwingTradeAdjustmentLineage.Factor> factors) {
        return new SwingTradeAdjustmentLineage.Request(base.symbol(), base.cutoff(),
                base.sessionsNewestFirst(), base.raw(), prices, factors);
    }

    private static void blocked(SwingTradeAdjustmentLineage.Request request,
                                SwingTradeAdjustmentLineage.Reason expected) {
        var result = SwingTradeAdjustmentLineage.audit(request);
        assertThat(result.status()).isEqualTo(SwingTradeAdjustmentLineage.Status.BLOCKED);
        assertThat(result.reason()).isEqualTo(expected);
        assertThat(result.verified()).isFalse();
    }

    @Test
    void exactTwentyOneSessionLineageIsResearchOnly() {
        var result = SwingTradeAdjustmentLineage.audit(request());
        assertThat(result.verified()).isTrue();
        assertThat(result.matchedSessions()).isEqualTo(21);
        assertThat(result.evidenceSource()).isEqualTo("SYNTHETIC_FACTOR");
        assertThat(result.purpose()).isEqualTo("RESEARCH_ONLY_NOT_TRADE_READY");
    }

    @Test
    void legitimateSyntheticSplitFactorMustMatchAllFourOhlcFields() {
        var original = request();
        var date = DATES.get(6);
        var prices = new ArrayList<>(original.adjusted());
        prices.set(6, candle(date, AdjustmentStatus.ADJUSTED, n("0.5")));
        var factors = new ArrayList<>(original.factors());
        factors.set(6, new SwingTradeAdjustmentLineage.Factor("TEST", date, n("0.5"),
                CorporateAction.PRESENT, "SYNTHETIC_FACTOR", "v1",
                CUTOFF.minusSeconds(60), true));
        var result = SwingTradeAdjustmentLineage.audit(adjusted(original, prices, factors));
        assertThat(result.verified()).isTrue();
        prices.set(6, candle(date, AdjustmentStatus.ADJUSTED, n("0.6")));
        blocked(adjusted(original, prices, factors), SwingTradeAdjustmentLineage.Reason.PRICE_MISMATCH);
    }

    @Test
    void futureOrUnreviewedFactorIsAlwaysRejected() {
        var original = request();
        var factors = new ArrayList<>(original.factors());
        var old = factors.getFirst();
        factors.set(0, new SwingTradeAdjustmentLineage.Factor(old.symbol(), old.tradingDate(),
                old.priceMultiplier(), old.action(), old.source(), old.version(),
                CUTOFF.plusSeconds(1), true));
        blocked(adjusted(original, original.adjusted(), factors),
                SwingTradeAdjustmentLineage.Reason.FUTURE_EVIDENCE);
        factors.set(0, new SwingTradeAdjustmentLineage.Factor(old.symbol(), old.tradingDate(),
                old.priceMultiplier(), old.action(), old.source(), old.version(),
                CUTOFF.minusSeconds(1), false));
        blocked(adjusted(original, original.adjusted(), factors),
                SwingTradeAdjustmentLineage.Reason.UNVERIFIED_EVIDENCE);
    }

    @Test
    void missingAndDuplicatePriceSessionFailClosed() {
        var original = request();
        var missing = original.adjusted().subList(1, 21);
        blocked(adjusted(original, missing, original.factors()),
                SwingTradeAdjustmentLineage.Reason.MISSING_CANDLE);
        var duplicate = new ArrayList<>(original.adjusted());
        duplicate.add(duplicate.getFirst());
        blocked(adjusted(original, duplicate, original.factors()),
                SwingTradeAdjustmentLineage.Reason.DUPLICATE_CANDLE);
    }

    @Test
    void unknownActionAndUnexplainedNonUnitFactorAreRejected() {
        var original = request();
        var factors = new ArrayList<>(original.factors());
        var old = factors.getFirst();
        factors.set(0, new SwingTradeAdjustmentLineage.Factor(old.symbol(), old.tradingDate(),
                BigDecimal.ONE, CorporateAction.UNKNOWN, old.source(), old.version(),
                old.availableAt(), true));
        blocked(adjusted(original, original.adjusted(), factors),
                SwingTradeAdjustmentLineage.Reason.UNKNOWN_ACTION);
        factors.set(0, new SwingTradeAdjustmentLineage.Factor(old.symbol(), old.tradingDate(),
                n("0.5"), CorporateAction.NONE, old.source(), old.version(),
                old.availableAt(), true));
        blocked(adjusted(original, original.adjusted(), factors),
                SwingTradeAdjustmentLineage.Reason.INVALID_FACTOR);
    }

    @Test
    void futureAdjustedPriceAndMismatchedBasisAreRejected() {
        var original = request();
        var prices = new ArrayList<>(original.adjusted());
        var old = prices.getFirst();
        prices.set(0, new DailyPrice(old.symbol(), old.tradingDate(), old.open(),
                old.high(), old.low(), old.close(), old.volume(), old.turnover(),
                old.source(), old.observedAt(), CUTOFF.plusSeconds(1).atOffset(ZoneOffset.UTC),
                old.adjustmentStatus()));
        blocked(adjusted(original, prices, original.factors()),
                SwingTradeAdjustmentLineage.Reason.FUTURE_PRICE);
        prices.set(0, candle(DATES.getFirst(), AdjustmentStatus.UNADJUSTED, BigDecimal.ONE));
        blocked(adjusted(original, prices, original.factors()),
                SwingTradeAdjustmentLineage.Reason.MISMATCHED_BASIS);
    }

    @Test
    void missingAndDuplicateFactorOrMixedProvenanceIsRejected() {
        var original = request();
        blocked(adjusted(original, original.adjusted(), original.factors().subList(1, 21)),
                SwingTradeAdjustmentLineage.Reason.MISSING_FACTOR);
        var factors = new ArrayList<>(original.factors());
        factors.add(factors.getFirst());
        blocked(adjusted(original, original.adjusted(), factors),
                SwingTradeAdjustmentLineage.Reason.DUPLICATE_FACTOR);
        factors.removeLast();
        var old = factors.get(1);
        factors.set(1, new SwingTradeAdjustmentLineage.Factor(old.symbol(), old.tradingDate(),
                old.priceMultiplier(), old.action(), "OTHER_FACTOR", old.version(),
                old.availableAt(), true));
        blocked(adjusted(original, original.adjusted(), factors),
                SwingTradeAdjustmentLineage.Reason.MIXED_SOURCE);
    }
}
