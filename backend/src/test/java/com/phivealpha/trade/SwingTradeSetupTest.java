package com.phivealpha.trade;

import static org.assertj.core.api.Assertions.assertThat;

import com.phivealpha.persistence.IdxRecords.AdjustmentStatus;
import com.phivealpha.persistence.IdxRecords.DailyPrice;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;

class SwingTradeSetupTest {
    private static final Instant CUTOFF = Instant.parse("2026-07-01T00:00:00Z");
    private static final LocalDate LATEST = LocalDate.parse("2026-06-30");
    private static final List<LocalDate> DATES = LATEST.minusDays(40).datesUntil(LATEST.plusDays(1))
            .filter(day -> day.getDayOfWeek().getValue() <= 5)
            .sorted(java.util.Comparator.reverseOrder()).limit(21).toList();

    private static DailyPrice bar(LocalDate day) {
        var moment = day.atTime(9, 0).atOffset(ZoneOffset.UTC);
        return new DailyPrice("TEST", day, n("100"), n("110"), n("90"), n("100"),
                100_000, n("10000000"), "TEST_VERIFIED_OFFLINE", moment, moment,
                AdjustmentStatus.ADJUSTED);
    }

    private static SwingTradeSetup.Request request(List<DailyPrice> candles) {
        return new SwingTradeSetup.Request("TEST", CUTOFF, DATES, candles,
                true, true, n("1"), 100);
    }

    private static List<DailyPrice> complete() {
        return DATES.stream().map(SwingTradeSetupTest::bar).toList();
    }

    private static BigDecimal n(String value) { return new BigDecimal(value); }

    private static List<DailyPrice> alter(List<DailyPrice> source, UnaryOperator<DailyPrice> transform) {
        var result = new ArrayList<>(source);
        result.set(0, transform.apply(result.getFirst()));
        return result;
    }

    @Test
    void emitsOnlyConditionalResearchLevelsWithTwoToOneRewardRisk() {
        var result = SwingTradeSetup.evaluate(request(complete()));
        assertThat(result.status()).isEqualTo(SwingTradeSetup.Status.PROPOSED_RESEARCH_ONLY);
        assertThat(result.reason()).isNull();
        assertThat(result.hasLevels()).isTrue();
        assertThat(result.entryTriggerIdr()).isEqualByComparingTo("111");
        assertThat(result.stopLossIdr()).isEqualByComparingTo("71");
        assertThat(result.takeProfitIdr()).isEqualByComparingTo("191");
        assertThat(result.riskReward()).isEqualByComparingTo("2");
        assertThat(result.horizonMinSessions()).isEqualTo(2);
        assertThat(result.horizonMaxSessions()).isEqualTo(10);
        assertThat(result.purpose()).contains("RESEARCH_ONLY");
    }

    @Test
    void missingSessionFailsClosedWithoutPriceLevels() {
        var remaining = complete().subList(1, 21);
        var result = SwingTradeSetup.evaluate(request(remaining));
        assertBlocked(result, SwingTradeSetup.Reason.MISSING_SESSION);
    }

    @Test
    void duplicateSessionFailsClosed() {
        var bars = new ArrayList<>(complete());
        bars.add(bars.getFirst());
        assertBlocked(SwingTradeSetup.evaluate(request(bars)), SwingTradeSetup.Reason.DUPLICATE_SESSION);
    }

    @Test
    void refusesUnadjustedHistoryAndFutureObservations() {
        var bars = alter(complete(), bar -> new DailyPrice(bar.symbol(), bar.tradingDate(), bar.open(),
                bar.high(), bar.low(), bar.close(), bar.volume(), bar.turnover(), bar.source(),
                bar.observedAt(), bar.ingestedAt(), AdjustmentStatus.UNKNOWN));
        assertBlocked(SwingTradeSetup.evaluate(request(bars)), SwingTradeSetup.Reason.UNADJUSTED_HISTORY);
        var future = alter(complete(), bar -> new DailyPrice(bar.symbol(), bar.tradingDate(), bar.open(),
                bar.high(), bar.low(), bar.close(), bar.volume(), bar.turnover(), bar.source(),
                bar.observedAt(), OffsetDateTime.parse("2026-07-02T00:00:00Z"), AdjustmentStatus.ADJUSTED));
        assertBlocked(SwingTradeSetup.evaluate(request(future)), SwingTradeSetup.Reason.FUTURE_OBSERVATION);
    }

    @Test
    void refusesInvalidOhlcvAndMixedSources() {
        var malformed = alter(complete(), bar -> new DailyPrice(bar.symbol(), bar.tradingDate(), bar.open(),
                n("95"), bar.low(), bar.close(), bar.volume(), bar.turnover(), bar.source(),
                bar.observedAt(), bar.ingestedAt(), bar.adjustmentStatus()));
        assertBlocked(SwingTradeSetup.evaluate(request(malformed)), SwingTradeSetup.Reason.INVALID_CANDLE);
        var mixed = alter(complete(), bar -> new DailyPrice(bar.symbol(), bar.tradingDate(), bar.open(),
                bar.high(), bar.low(), bar.close(), bar.volume(), bar.turnover(), "OTHER",
                bar.observedAt(), bar.ingestedAt(), bar.adjustmentStatus()));
        assertBlocked(SwingTradeSetup.evaluate(request(mixed)), SwingTradeSetup.Reason.MIXED_SOURCE);
    }

    @Test
    void refusesUnverifiedReferenceAndIneligibleCandidate() {
        var current = request(complete());
        var unverified = new SwingTradeSetup.Request(current.symbol(), current.cutoff(),
                current.sessionsNewestFirst(), current.prices(), false, true, n("1"), 100);
        assertBlocked(SwingTradeSetup.evaluate(unverified), SwingTradeSetup.Reason.UNVERIFIED_REFERENCE);
        var ineligible = new SwingTradeSetup.Request(current.symbol(), current.cutoff(),
                current.sessionsNewestFirst(), current.prices(), true, false, n("1"), 100);
        assertBlocked(SwingTradeSetup.evaluate(ineligible), SwingTradeSetup.Reason.NOT_ELIGIBLE);
    }

    @Test
    void doesNotTreatCompletedBreakoutAsPendingEntry() {
        var rally = alter(complete(), bar -> new DailyPrice(bar.symbol(), bar.tradingDate(), n("110"),
                n("118"), n("108"), n("115"), bar.volume(), bar.turnover(), bar.source(),
                bar.observedAt(), bar.ingestedAt(), bar.adjustmentStatus()));
        assertBlocked(SwingTradeSetup.evaluate(request(rally)),
                SwingTradeSetup.Reason.PRICE_ALREADY_ABOVE_TRIGGER);
    }

    private static void assertBlocked(SwingTradeSetup.Proposal proposal, SwingTradeSetup.Reason reason) {
        assertThat(proposal.status()).isEqualTo(SwingTradeSetup.Status.UNAVAILABLE);
        assertThat(proposal.reason()).isEqualTo(reason);
        assertThat(proposal.hasLevels()).isFalse();
        assertThat(proposal.entryTriggerIdr()).isNull();
        assertThat(proposal.stopLossIdr()).isNull();
        assertThat(proposal.takeProfitIdr()).isNull();
        assertThat(proposal.riskReward()).isNull();
    }
}
