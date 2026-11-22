package com.phivealpha.screening;

import static com.phivealpha.screening.HistoricalDatasetReadiness.*;
import static com.phivealpha.screening.HistoricalDatasetReadiness.Reason.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.phivealpha.marketdata.apicoid.VersionedIdxCalendar;
import com.phivealpha.persistence.IdxRecords.AdjustmentStatus;
import com.phivealpha.persistence.IdxRecords.DailyPrice;
import com.phivealpha.research.HistoricalReplay.CorporateAction;
import com.phivealpha.research.HistoricalReplay.Frame;
import com.phivealpha.research.HistoricalReplay.Listing;
import com.phivealpha.research.HistoricalReplay.SessionBar;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class HistoricalDatasetReadinessAuditTest {
    private static final String STOCK = "SYN";
    private static final String PRICE_SOURCE = "synthetic-eod-v1";
    private static final LocalDate END = LocalDate.parse("2026-11-20");
    private static final LocalDate HOLIDAY = END.minusDays(3);
    private static final LocalDate SPECIAL = END.minusDays(2);
    private static final Instant CUTOFF = Instant.parse("2026-11-20T10:00:00Z");
    private static final VersionedIdxCalendar CALENDAR = calendar(null);
    private static final List<LocalDate> DATES = END.minusDays(400).datesUntil(END.plusDays(1))
            .filter(CALENDAR::isTradingSession).toList().reversed().stream().limit(250).toList().reversed();
    private static final List<DailyPrice> PRICES = DATES.stream().map(HistoricalDatasetReadinessAuditTest::price).toList();
    private static final List<SessionBar> EVIDENCE = DATES.stream()
            .flatMap(day -> Stream.of(evidence(STOCK, day), evidence(BENCHMARK, day))).toList();
    private static final LocalDate TARGET = DATES.get(20);

    private final HistoricalDatasetReadinessAudit audit = new HistoricalDatasetReadinessAudit(
            new LiquidityPolicy(20, 20, 15, new BigDecimal("0.75"), 12,
                    BigDecimal.ONE, BigDecimal.ONE, 1, Duration.ofDays(7), Duration.ofDays(7)));

    @Test
    void complete250SessionSnapshotIsReadyAtTheExplicitCutoff() {
        var report = run(PRICES, EVIDENCE);
        assertThat(report.outcome()).isEqualTo(Outcome.READY);
        assertThat(report.expectedSessions()).containsExactlyElementsOf(DATES);
        assertThat(report.lookbackSessions()).isEqualTo(250);
        assertThat(report.cutoff()).isEqualTo(CUTOFF);
        assertThat(report.provenance().dailyPriceSource()).isEqualTo(PRICE_SOURCE);
        assertThat(report.provenance().universeVersion()).isEqualTo("master-v1");
        assertThat(report.provenance().calendarVersion()).isEqualTo("calendar-v1");
        for (var series : List.of(report.equities().getFirst(), report.benchmark())) {
            assertThat(series.observedSessions()).isEqualTo(250);
            assertThat(series.validCandles()).isEqualTo(250);
            assertThat(series.coverage()).isEqualTo(Coverage.COMPLETE);
            assertThat(series.freshness()).isEqualTo(Freshness.CURRENT);
            assertThat(series.issues()).isEmpty();
        }
    }

    @Test
    void oneMissingSessionDoesNotShrinkOrShiftTheWindow() {
        var report = run(PRICES.stream().filter(row -> !row.tradingDate().equals(TARGET)).toList(), EVIDENCE);
        assertIssue(report, Outcome.INCOMPLETE, MISSING_CANDLE, TARGET);
        assertThat(report.expectedSessions()).hasSize(250);
        assertThat(report.equities().getFirst().observedSessions()).isEqualTo(249);
    }

    @Test
    void duplicateInvalidatesTheEntireSession() {
        var rows = new ArrayList<>(PRICES);
        rows.add(price(TARGET));
        var report = run(rows, EVIDENCE);
        assertIssue(report, Outcome.INCOMPLETE, DUPLICATE_CANDLE, TARGET);
        assertThat(report.equities().getFirst().observedSessions()).isEqualTo(250);
        assertThat(report.equities().getFirst().validCandles()).isEqualTo(249);
    }

    @Test
    void rejectsInvalidOhlcAndNegativeVolumeIndependently() {
        for (var invalid : List.<UnaryOperator<DailyPrice>>of(
                row -> edit(row, BigDecimal.ONE, row.volume(), row.turnover(), row.source(), row.adjustmentStatus(), 0),
                row -> edit(row, row.high(), -1, row.turnover(), row.source(), row.adjustmentStatus(), 0))) {
            assertIssue(run(replace(invalid), EVIDENCE), Outcome.INCOMPLETE, INVALID_OHLCV, TARGET);
        }
    }

    @Test
    void nullTurnoverIsReportedWithoutImputingIt() {
        var report = run(replace(row -> edit(row, row.high(), row.volume(), null,
                row.source(), row.adjustmentStatus(), 0)), EVIDENCE);
        assertIssue(report, Outcome.INCOMPLETE, TURNOVER_UNAVAILABLE, TARGET);
        assertThat(report.equities().getFirst().validCandles()).isEqualTo(249);
    }

    @Test
    void distinguishesShortHistoryFromAnUnavailableSeries() {
        var shortHistory = run(PRICES.subList(220, 250), EVIDENCE);
        assertThat(shortHistory.outcome()).isEqualTo(Outcome.INCOMPLETE);
        assertThat(shortHistory.equities().getFirst().validCandles()).isEqualTo(30);
        var empty = run(List.of(), EVIDENCE);
        assertThat(empty.outcome()).isEqualTo(Outcome.UNAVAILABLE);
        assertThat(empty.equities().getFirst().coverage()).isEqualTo(Coverage.NONE);
        assertThat(empty.equities().getFirst().freshness()).isEqualTo(Freshness.NOT_ASSESSED);
    }

    @Test
    void verifiesExactSourceAndUnadjustedBasis() {
        var report = run(replace(row -> edit(row, row.high(), row.volume(), row.turnover(),
                "another-source", AdjustmentStatus.ADJUSTED, 0)), EVIDENCE);
        assertIssue(report, Outcome.UNVERIFIED, PRICE_SOURCE_MISMATCH, TARGET);
        assertIssue(report, Outcome.UNVERIFIED, PRICE_BASIS_MISMATCH, TARGET);
    }

    @Test
    void rejectsFutureObservationAndLaterIngestionOfAnOlderCandle() {
        var future = run(replace(row -> edit(row, row.high(), row.volume(), row.turnover(),
                row.source(), row.adjustmentStatus(), 1)), EVIDENCE);
        assertIssue(future, Outcome.UNVERIFIED, OBSERVATION_AFTER_CUTOFF, TARGET);
        assertIssue(future, Outcome.UNVERIFIED, INGESTION_AFTER_CUTOFF, TARGET);
        var late = run(replace(row -> edit(row, row.high(), row.volume(), row.turnover(),
                row.source(), row.adjustmentStatus(), 2)), EVIDENCE);
        assertIssue(late, Outcome.UNVERIFIED, INGESTION_AFTER_CUTOFF, TARGET);
    }

    @Test
    void calendarGapFailsClosedEvenWhenPricesContainThatDate() {
        var frame = frame(calendar(TARGET), PRICES, CUTOFF, LiquidUniverse.CorporateActionStatus.CLEAR);
        var report = audit.audit(request(frame, EVIDENCE));
        assertThat(report.outcome()).isEqualTo(Outcome.UNAVAILABLE);
        assertThat(report.issues()).contains(new Issue(CALENDAR_OUT_OF_COVERAGE, TARGET));
        assertThat(report.equities()).isEmpty();
        assertThat(report.expectedSessions()).doesNotContain(TARGET);
    }

    @Test
    void corporateActionUncertaintyBlocksBothMemberAndSessionEvidence() {
        var frame = frame(CALENDAR, PRICES, CUTOFF, LiquidUniverse.CorporateActionStatus.AMBIGUOUS);
        var report = audit.audit(request(frame, EVIDENCE));
        assertIssue(report, Outcome.UNVERIFIED, INELIGIBLE_HISTORICAL_MEMBER, null);
        assertThat(report.equities().getFirst().eligibilityIssue())
                .isEqualTo(LiquidUniverse.Reason.CORPORATE_ACTION_UNVERIFIED);
        var unknown = EVIDENCE.stream().map(row -> row.symbol().equals(STOCK) && row.date().equals(TARGET)
                ? new SessionBar(STOCK, TARGET, row.availableAt(), row.version(), Listing.ACTIVE,
                        CorporateAction.UNKNOWN, null, null) : row).toList();
        assertIssue(run(PRICES, unknown), Outcome.UNVERIFIED, SESSION_CORPORATE_ACTION, TARGET);
    }

    @Test
    void missingBenchmarkIsUnavailableAndBlocksOverallReadiness() {
        var report = run(PRICES, EVIDENCE.stream().filter(row -> row.symbol().equals(STOCK)).toList());
        assertThat(report.outcome()).isEqualTo(Outcome.INCOMPLETE);
        assertThat(report.benchmark().outcome()).isEqualTo(Outcome.UNAVAILABLE);
        assertThat(report.benchmark().issues()).contains(new Issue(BENCHMARK_UNAVAILABLE, null));
    }

    @Test
    void calendarHandlesHolidaysSpecialAndNotYetClosedSessions() {
        var complete = run(PRICES, EVIDENCE);
        assertThat(complete.expectedSessions()).contains(SPECIAL).doesNotContain(HOLIDAY);
        var cutoff = CALENDAR.sessionClose(SPECIAL).orElseThrow().minusSeconds(1);
        var prior = DATES.stream().filter(day -> day.isBefore(SPECIAL)).toList().getLast();
        var frame = frame(CALENDAR, List.of(price(prior)), cutoff, LiquidUniverse.CorporateActionStatus.CLEAR);
        var sample = List.of(evidence(STOCK, prior), evidence(BENCHMARK, prior));
        var report = audit.audit(new Request("qa", "v1", "fixture", PRICE_SOURCE, AdjustmentStatus.UNADJUSTED,
                "fixture session evidence", cutoff, List.of(STOCK), frame, sample, 1));
        assertThat(report.outcome()).isEqualTo(Outcome.READY);
        assertThat(report.expectedSessions()).containsExactly(prior);
    }

    @Test
    void repeatedAndReorderedAuditsProduceTheSameReport() {
        var prices = replace(row -> edit(row, row.high(), row.volume(), null, row.source(), row.adjustmentStatus(), 0));
        var report = run(prices, EVIDENCE);
        assertThat(run(prices, EVIDENCE)).isEqualTo(report);
        assertThat(run(prices.reversed(), EVIDENCE.reversed())).isEqualTo(report);
    }

    @Test
    void refusesAFutureFrameAndDoesNotAssessItsMembership() {
        var frame = frame(CALENDAR, PRICES, CUTOFF.plusSeconds(1), LiquidUniverse.CorporateActionStatus.CLEAR);
        var report = audit.audit(request(frame, EVIDENCE));
        assertThat(report.outcome()).isEqualTo(Outcome.UNVERIFIED);
        assertThat(report.issues()).containsExactly(new Issue(FRAME_AFTER_CUTOFF, null));
        assertThat(report.equities()).isEmpty();
    }

    @Test
    void suspendedSessionStaysInCoverageAndBlocksReadiness() {
        var suspended = EVIDENCE.stream().map(row -> row.symbol().equals(STOCK) && row.date().equals(TARGET)
                ? new SessionBar(STOCK, TARGET, row.availableAt(), row.version(), Listing.SUSPENDED,
                        CorporateAction.NONE, null, null) : row).toList();
        var report = run(PRICES, suspended);
        assertIssue(report, Outcome.INCOMPLETE, SESSION_SUSPENDED, TARGET);
        assertThat(report.expectedSessions()).contains(TARGET);
    }

    @Test
    void latestMissingCandleMakesTheSeriesStale() {
        var report = run(PRICES.subList(0, 249), EVIDENCE);
        assertThat(report.equities().getFirst().freshness()).isEqualTo(Freshness.STALE);
        assertIssue(report, Outcome.INCOMPLETE, MISSING_CANDLE, END);
    }

    @Test
    void futureBenchmarkRevisionCannotReplaceVisibleBenchmarkEvidence() {
        var rows = new ArrayList<>(EVIDENCE);
        rows.add(new SessionBar(BENCHMARK, TARGET, CUTOFF.plusSeconds(1), "future-revision",
                Listing.ACTIVE, CorporateAction.NONE, BigDecimal.TEN, BigDecimal.TEN));
        var report = run(PRICES, rows);
        assertThat(report.outcome()).isEqualTo(Outcome.UNVERIFIED);
        assertThat(report.benchmark().validCandles()).isEqualTo(249);
        assertThat(report.benchmark().issues()).contains(new Issue(DUPLICATE_SESSION_EVIDENCE, TARGET),
                new Issue(SESSION_EVIDENCE_AFTER_CUTOFF, TARGET));
    }

    @Test
    void aCurrentSymbolOutsideTheHistoricalUniverseIsUnverified() {
        var frame = frame(CALENDAR, PRICES, CUTOFF, LiquidUniverse.CorporateActionStatus.CLEAR);
        var report = audit.audit(new Request("qa", "v1", "fixture", PRICE_SOURCE, AdjustmentStatus.UNADJUSTED,
                "fixture session evidence", CUTOFF, List.of("NEW"), frame, EVIDENCE));
        assertIssue(report, Outcome.UNVERIFIED, MISSING_HISTORICAL_MEMBER, null);
    }

    private Report run(List<DailyPrice> prices, List<SessionBar> evidence) {
        return audit.audit(request(frame(CALENDAR, prices, CUTOFF, LiquidUniverse.CorporateActionStatus.CLEAR), evidence));
    }

    private static Request request(Frame frame, List<SessionBar> evidence) {
        return new Request("qa", "v1", "fixture", PRICE_SOURCE, AdjustmentStatus.UNADJUSTED,
                "fixture session evidence", CUTOFF, List.of(STOCK), frame, evidence);
    }

    private static Frame frame(VersionedIdxCalendar calendar, List<DailyPrice> prices, Instant available,
                               LiquidUniverse.CorporateActionStatus action) {
        var asOf = available.minusSeconds(1);
        var member = new LiquidUniverse.Member(STOCK, LiquidUniverse.SecurityType.COMMON_STOCK,
                LiquidUniverse.ListingStatus.ACTIVE, END.minusYears(3), action);
        var universe = new LiquidUniverse.Reference("master-v1", asOf, available, "fixture master", List.of(member));
        return new Frame("frame-v1", asOf, available, universe,
                new LiquidUniverse.CalendarReference(calendar, Instant.EPOCH), "prices-v1", prices,
                Map.of(), null, false, Map.of());
    }

    private static void assertIssue(Report report, Outcome outcome, Reason reason, LocalDate date) {
        assertThat(report.outcome()).isEqualTo(outcome);
        assertThat(report.equities().getFirst().issues()).contains(new Issue(reason, date));
    }

    private static List<DailyPrice> replace(UnaryOperator<DailyPrice> replacement) {
        return PRICES.stream().map(row -> row.tradingDate().equals(TARGET) ? replacement.apply(row) : row).toList();
    }

    private static DailyPrice edit(DailyPrice row, BigDecimal high, long volume, BigDecimal turnover, String source,
                                   AdjustmentStatus basis, int timing) {
        var future = CUTOFF.plusSeconds(5).atOffset(ZoneOffset.UTC);
        return new DailyPrice(row.symbol(), row.tradingDate(), row.open(), high, row.low(), row.close(), volume,
                turnover, source, timing == 1 ? future : row.observedAt(), timing != 0 ? future : row.ingestedAt(), basis);
    }

    private static DailyPrice price(LocalDate date) {
        var observed = CALENDAR.sessionClose(date).orElseThrow().atOffset(ZoneOffset.UTC);
        return new DailyPrice(STOCK, date, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN,
                100, BigDecimal.valueOf(1_000), PRICE_SOURCE, observed, observed.plusSeconds(1), AdjustmentStatus.UNADJUSTED);
    }

    private static SessionBar evidence(String symbol, LocalDate date) {
        return new SessionBar(symbol, date, CALENDAR.sessionClose(date).orElseThrow(), "session-v1",
                Listing.ACTIVE, CorporateAction.NONE, BigDecimal.TEN, BigDecimal.TEN);
    }

    private static VersionedIdxCalendar calendar(LocalDate missing) {
        var manifest = JsonMapper.builder().build().createObjectNode();
        manifest.put("source", "fixture calendar").put("version", "calendar-v1");
        var days = manifest.putArray("days");
        for (var day : END.minusDays(400).datesUntil(END.plusDays(1)).toList()) {
            if (day.equals(missing)) continue;
            boolean closed = day.equals(HOLIDAY) || day.getDayOfWeek() == DayOfWeek.SATURDAY
                    || day.getDayOfWeek() == DayOfWeek.SUNDAY;
            var entry = days.addObject().put("date", day.toString())
                    .put("kind", closed ? "HOLIDAY" : day.equals(SPECIAL) ? "SPECIAL" : "REGULAR");
            var sessions = entry.putArray("sessions");
            if (!closed) sessions.addObject().put("open", "09:00").put("close", day.equals(SPECIAL) ? "11:30" : "16:00");
        }
        return new VersionedIdxCalendar(manifest);
    }
}
