package com.phivealpha.research;

import static com.phivealpha.research.ForwardOutcomeEvaluation.*;
import static com.phivealpha.research.HistoricalReplay.CorporateAction;
import static com.phivealpha.research.HistoricalReplay.Listing;
import static com.phivealpha.research.HistoricalReplay.Partition;
import static com.phivealpha.research.HistoricalReplay.SessionBar;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.phivealpha.marketdata.apicoid.VersionedIdxCalendar;
import com.phivealpha.persistence.IdxDailyIngestionService;
import com.phivealpha.persistence.IdxRecords.AdjustmentStatus;
import com.phivealpha.persistence.IdxRecords.DailyPrice;
import com.phivealpha.screening.HistoricalDatasetReadiness;
import com.phivealpha.screening.HistoricalDatasetReadinessAudit;
import com.phivealpha.screening.HistoricalScreening;
import com.phivealpha.screening.HistoricalScreeningService;
import com.phivealpha.screening.LiquidUniverse;
import com.phivealpha.screening.LiquidUniverseService;
import com.phivealpha.screening.LiquidityPolicy;
import com.phivealpha.screening.StockScreenerService;
import com.phivealpha.screening.news.NewsDrivenScoringService;
import com.phivealpha.screening.news.NewsScoring;
import com.phivealpha.screening.news.NewsScoringPolicy;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import tools.jackson.databind.json.JsonMapper;

class ForwardOutcomeEvaluationTest {
    private static final LocalDate START = LocalDate.parse("2025-11-01");
    private static final LocalDate END = date("04-30");
    private static final LocalDate HOLIDAY = date("01-12");
    private static final LocalDate ENTRY = date("01-13");
    private static final LocalDate EXIT = date("01-14");
    private static final Instant CUTOFF = cutoff("01-09");
    private static final Instant FREEZE = Instant.parse("2026-05-01T00:00:00Z");
    private static final VersionedIdxCalendar CALENDAR = calendar("qa-v1", null);
    private static final List<String> EQUITIES = List.of("BBB", "AAA");
    private static final List<LocalDate> SESSIONS = START.datesUntil(END.plusDays(1))
            .filter(CALENDAR::isTradingSession).toList();
    private static final List<SessionBar> OUTCOMES = SESSIONS.stream()
            .flatMap(day -> Stream.of("AAA", "BBB", "IHSG").map(symbol -> bar(symbol, day))).toList();
    private static final HistoricalReplay.Split SPLIT = new HistoricalReplay.Split(
            new HistoricalReplay.Window(date("01-05"), date("02-02")),
            new HistoricalReplay.Window(date("02-18"), date("03-05")),
            new HistoricalReplay.Window(date("03-23"), END), 10);
    private static final HistoricalReplay.Costs COSTS = HistoricalReplay.Costs.syntheticDefaults();
    private static final CoveragePolicy COVERAGE = new CoveragePolicy(2, new BigDecimal("0.80"));
    private static final Duration WEEK = Duration.ofDays(7);
    private static final AdjustmentStatus RAW = AdjustmentStatus.UNADJUSTED;

    private final ForwardOutcomeEvaluationService service = service();

    @Test
    void evaluatesTwoFiveAndTenTradingSessionsWithExactRankingAndMatchedBenchmark() {
        var report = evaluate(List.of(context(CUTOFF)), OUTCOMES);
        assertThat(report.purpose()).isEqualTo("RESEARCH_ONLY_SYNTHETIC");
        assertThat(report.issues()).isEmpty();
        assertThat(report.observations()).hasSize(6);
        var first = report.observations().stream().filter(pair -> pair.rank() == 1).toList();
        assertThat(first).extracting(pair -> pair.candidate().symbol()).containsOnly("AAA");
        assertThat(first).extracting(pair -> pair.candidate().holdingSessions()).containsExactly(2, 5, 10);
        assertThat(first).extracting(pair -> pair.candidate().entryDate()).containsOnly(ENTRY);
        assertThat(first).extracting(pair -> pair.candidate().exitDate()).containsExactly(EXIT, date("01-19"), date("01-26"));
        assertThat(report.observations()).filteredOn(pair -> pair.rank() == 2)
                .extracting(pair -> pair.candidate().symbol()).containsOnly("BBB");
        for (var pair : first) {
            assertThat(pair.candidate().grossReturn()).isEqualByComparingTo("0.10");
            assertThat(pair.benchmark().grossReturn()).isEqualByComparingTo("0.02");
            assertThat(pair.grossExcessReturn()).isEqualByComparingTo("0.08");
            assertThat(pair.benchmark().entryDate()).isEqualTo(pair.candidate().entryDate());
            assertThat(pair.benchmark().exitDate()).isEqualTo(pair.candidate().exitDate());
        }
        assertThat(report.status()).isEqualTo(Status.NOT_EVALUABLE);
        assertThat(summary(report, Partition.TRAIN, 2).netExcessHitRate()).isNull();
    }

    @Test
    void reusesReplayCostsAndReportsNegativeReturnAndPathDrawdown() {
        var falling = replace(OUTCOMES, "AAA", EXIT, row -> replace(row, n(80), row.listing(), row.action()));
        var pair = evaluate(List.of(context(CUTOFF)), falling).observations().getFirst();
        var costAdjusted = new BigDecimal("80").multiply(new BigDecimal("0.998")).multiply(new BigDecimal("0.9975"))
                .divide(new BigDecimal("100").multiply(new BigDecimal("1.002")).multiply(new BigDecimal("1.0015")),
                        MathContext.DECIMAL128).subtract(BigDecimal.ONE);
        assertThat(pair.candidate().grossReturn()).isEqualByComparingTo("-0.20");
        assertThat(pair.candidate().netReturn()).isEqualByComparingTo(costAdjusted);
        assertThat(pair.netExcessReturn()).isEqualByComparingTo(costAdjusted.subtract(new BigDecimal("0.02")));
        assertThat(pair.candidate().maxPathDrawdown()).isCloseTo(
                BigDecimal.ONE.subtract(n(80).divide(n(110), MathContext.DECIMAL128)),
                within(new BigDecimal("1e-30")));
    }

    @Test
    void missingEntryExitAndHoldingPricesRemainNullWithPerHorizonReasons() {
        var withoutExit = without(OUTCOMES, "AAA", EXIT);
        var report = evaluate(List.of(context(CUTOFF)), withoutExit);
        assertThat(report.observations().get(0).candidate().exclusion()).isEqualTo(HistoricalReplay.Reason.MISSING_EXIT);
        assertThat(report.observations().get(2).candidate().exclusion()).isEqualTo(HistoricalReplay.Reason.MISSING_HOLDING_PRICE);
        assertThat(report.observations().getFirst().candidate().grossReturn()).isNull();
        assertThat(report.observations().getFirst().grossExcessReturn()).isNull();
        assertThat(summary(report, Partition.TRAIN, 2).rejected()).isEqualTo(1);

        var absentEntry = evaluate(List.of(context(CUTOFF)), without(OUTCOMES, "AAA", ENTRY)).observations().getFirst();
        assertThat(absentEntry.candidate().exclusion()).isEqualTo(HistoricalReplay.Reason.MISSING_ENTRY);
        assertThat(absentEntry.candidate().entered()).isFalse();
    }

    @Test
    void missingBenchmarkBlocksPairWithoutReplacingCandidateReturn() {
        var report = evaluate(List.of(context(CUTOFF)), without(OUTCOMES, "IHSG", EXIT));
        var pair = report.observations().getFirst();
        assertThat(pair.benchmark().exclusion()).isEqualTo(HistoricalReplay.Reason.MISSING_EXIT);
        assertThat(pair.candidate().grossReturn()).isEqualByComparingTo("0.1");
        assertThat(pair.netExcessReturn()).isNull();
        var summary = summary(report, Partition.TRAIN, 2);
        assertThat(summary.valid()).isZero();
        assertThat(summary.rejected()).isEqualTo(2);
        assertThat(summary.meanBenchmarkGross()).isNull();
    }

    @Test
    void suspensionAndCorporateActionBlockOnlyAffectedPaths() {
        var suspended = replace(OUTCOMES, "AAA", ENTRY, row -> replace(row, row.close(), Listing.SUSPENDED, row.action()));
        var uncertain = replace(suspended, "BBB", date("01-16"), row ->
                replace(row, row.close(), row.listing(), CorporateAction.UNKNOWN));
        var pairs = evaluate(List.of(context(CUTOFF)), uncertain).observations();
        assertThat(pairs.get(0).candidate().exclusion()).isEqualTo(HistoricalReplay.Reason.SUSPENDED);
        assertThat(pairs.get(1).candidate().exclusion()).isNull();
        assertThat(pairs.get(3).candidate().exclusion()).isEqualTo(HistoricalReplay.Reason.CORPORATE_ACTION);
    }

    @Test
    void laterHoldingCalendarRevisionOnlyInvalidatesAffectedHorizons() {
        var revision = new LiquidUniverse.CalendarReference(calendar("revision", date("01-16")), CUTOFF.plusSeconds(1));
        var data = data(OUTCOMES, RAW, revision);
        var report = service.evaluate(List.of(context(CUTOFF)), data, SPLIT, COSTS, COVERAGE);
        assertThat(report.observations().get(0).candidate().exclusion()).isNull();
        assertThat(report.observations().get(2).candidate().exclusion()).isEqualTo(HistoricalReplay.Reason.HOLDING_CALENDAR_CHANGED);
        assertThat(report.observations().get(2).candidate().failureDate()).isEqualTo(date("01-16"));
    }

    @Test
    void overlappingCutoffsAndChronologicalPartitionsStaySeparate() {
        var contexts = List.of(context(cutoff("03-25")), context(cutoff("01-13")), context(cutoff("03-24")),
                context(CUTOFF), context(cutoff("02-06")));
        var report = evaluate(contexts, OUTCOMES);
        assertThat(report.cutoffs()).extracting(CutoffResult::partition)
                .containsExactly(Partition.TRAIN, Partition.TRAIN, null, Partition.OUT_OF_SAMPLE, Partition.OUT_OF_SAMPLE);
        assertThat(report.cutoffs().get(2).exclusion()).isEqualTo(Reason.OUTSIDE_PARTITIONS);
        assertThat(report.status()).isEqualTo(Status.PARTIAL);
        for (var partition : List.of(Partition.TRAIN, Partition.OUT_OF_SAMPLE)) {
            var summary = summary(report, partition, 2);
            assertThat(summary.valid()).isEqualTo(4);
            assertThat(summary.matchedCutoffs()).isEqualTo(2);
            assertThat(summary.overlappingCutoffs()).isEqualTo(1);
            assertThat(summary.netExcessHitRate()).isEqualByComparingTo("1");
            assertThat(summary.meanGrossExcess()).isEqualByComparingTo("0.08");
        }
        assertThat(summary(report, Partition.VALIDATION, 2).meanGrossExcess()).isNull();
        assertThat(evaluate(contexts.reversed(), OUTCOMES.reversed())).isEqualTo(report);
    }

    @Test
    void futureOutcomesChangeReturnsButNeverTheFrozenScreeningDecision() {
        var contexts = List.of(context(CUTOFF));
        var expected = evaluate(contexts, OUTCOMES);
        var changed = evaluate(contexts, replace(OUTCOMES, "AAA", EXIT, row ->
                replace(row, n(200), row.listing(), row.action())));
        assertThat(changed.cutoffs()).isEqualTo(expected.cutoffs());
        assertThat(changed.observations().getFirst().grossExcessReturn()).isNotEqualTo(expected.observations().getFirst().grossExcessReturn());
        var revised = new ArrayList<>(OUTCOMES);
        revised.add(new SessionBar("AAA", EXIT, FREEZE.plusSeconds(1), "too-late", Listing.ACTIVE,
                CorporateAction.NONE, n(100), n(1000)));
        assertThat(evaluate(contexts, revised)).isEqualTo(expected);
    }

    @Test
    void readinessAndIncompatibleBasisBlockWithExplicitReasons() {
        var valid = context(CUTOFF);
        var request = valid.dataset();
        var frame = request.frame();
        var empty = new HistoricalReplay.Frame(frame.version(), frame.asOf(), frame.availableAt(), frame.universe(),
                frame.calendar(), frame.dailyPriceVersion(), List.of(), frame.inputs(), frame.market(), true, frame.lotSizes());
        var blocked = evaluate(List.of(context(CUTOFF, empty, request.sessionEvidence())), OUTCOMES);
        assertThat(blocked.cutoffs().getFirst().exclusion()).isEqualTo(Reason.SCREENING_BLOCKED);
        assertThat(blocked.observations()).isEmpty();
        var badBasis = service.evaluate(List.of(valid), data(OUTCOMES, AdjustmentStatus.ADJUSTED,
                new LiquidUniverse.CalendarReference(CALENDAR, Instant.EPOCH)), SPLIT, COSTS, COVERAGE);
        assertThat(badBasis.issues()).containsExactly(Reason.INCOMPATIBLE_OUTCOME_PRICE_BASIS);
        assertThat(badBasis.observations()).isEmpty();
        assertThat(badBasis.status()).isEqualTo(Status.NOT_EVALUABLE);
    }

    @Test
    void partitionEndPurgesLongerLabelsAndLowPairedCoverageSuppressesAggregates() {
        var nearBoundary = evaluate(List.of(context(cutoff("01-29"))), OUTCOMES);
        assertThat(nearBoundary.observations().getFirst().candidate().exclusion()).isNull();
        assertThat(nearBoundary.observations().get(2).candidate().exclusion()).isEqualTo(HistoricalReplay.Reason.EXIT_BEYOND_PARTITION);
        var lowCoverage = evaluate(List.of(context(CUTOFF), context(cutoff("01-13"))), without(OUTCOMES, "AAA", ENTRY));
        var summary = summary(lowCoverage, Partition.TRAIN, 2);
        assertThat(summary.matchedCutoffs()).isEqualTo(2);
        assertThat(summary.pairedCoverage()).isEqualByComparingTo("0.75");
        assertThat(summary.sufficientCoverage()).isFalse();
        assertThat(summary.worstPathDrawdown()).isNull();
    }

    @Test
    void matchesLegacyReplayObservationsWhenBothWorkflowsSelectTheSameCandidates() {
        var context = context(CUTOFF);
        var original = context.dataset().frame();
        var inputs = original.inputs().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> {
            var factors = entry.getValue();
            var momentum = new NewsScoring.Snapshot<>(factors.news().evidence(), new NewsScoring.Momentum(n(100), n(100), n(100)));
            return new NewsScoring.Inputs(factors.news(), momentum, factors.flow());
        }));
        var frame = new HistoricalReplay.Frame(original.version(), original.asOf(), original.availableAt(), original.universe(),
                original.calendar(), original.dailyPriceVersion(), original.dailyPrices(), inputs, original.market(), true,
                original.lotSizes());
        var dataset = new HistoricalReplay.Dataset("qa", "fixture", List.of(frame),
                new LiquidUniverse.CalendarReference(CALENDAR, Instant.EPOCH), "fixture-outcome-v1", FREEZE, OUTCOMES);
        var legacy = new HistoricalReplayService(new LiquidUniverseService(liquidityPolicy()))
                .replay(dataset, SPLIT, COSTS, List.of(CUTOFF));
        var forward = evaluate(List.of(context), OUTCOMES);
        assertThat(forward.observations().stream().map(Pair::candidate))
                .containsExactlyElementsOf(legacy.observations().stream()
                        .filter(row -> row.arm() == HistoricalReplay.Arm.NEWS_SCORING).toList());
    }

    private Report evaluate(List<HistoricalScreening.Context> contexts, List<SessionBar> bars) {
        return service.evaluate(contexts, data(bars, RAW, new LiquidUniverse.CalendarReference(CALENDAR, Instant.EPOCH)),
                SPLIT, COSTS, COVERAGE);
    }

    private static OutcomeData data(List<SessionBar> bars, AdjustmentStatus basis, LiquidUniverse.CalendarReference calendar) {
        return new OutcomeData(new OutcomeProvenance("fixture-outcome-v1", "synthetic forward data", calendar, FREEZE,
                basis, RAW), bars);
    }

    private static HistoricalScreening.Context context(Instant cutoff) {
        var sessions = SESSIONS.stream().filter(day -> CALENDAR.isClosedBy(day, cutoff)).toList().reversed()
                .stream().limit(11).toList();
        var prices = sessions.stream().flatMap(day -> EQUITIES.stream().map(symbol -> {
            var close = CALENDAR.sessionClose(day).orElseThrow().atOffset(ZoneOffset.UTC);
            return new DailyPrice(symbol, day, n(100), n(100), n(100), n(100), 100_000, n(10_000_000),
                    "qa-prices", close, close.plusSeconds(1), RAW);
        })).toList();
        var evidence = sessions.stream().flatMap(day -> Stream.of("AAA", "BBB", "IHSG").map(symbol -> bar(symbol, day))).toList();
        var asOf = cutoff.minusSeconds(30);
        var members = EQUITIES.stream().map(symbol -> new LiquidUniverse.Member(symbol, LiquidUniverse.SecurityType.COMMON_STOCK,
                LiquidUniverse.ListingStatus.ACTIVE, START.minusYears(2), LiquidUniverse.CorporateActionStatus.CLEAR)).toList();
        var reference = new LiquidUniverse.Reference("qa-universe", asOf, asOf, "synthetic universe", members);
        var factor = new NewsScoring.Evidence("synthetic factors", "qa-factors", asOf, asOf, NewsScoring.Quality.AVAILABLE);
        var inputs = EQUITIES.stream().collect(Collectors.toMap(symbol -> symbol, symbol -> new NewsScoring.Inputs(
                new NewsScoring.Snapshot<>(factor, List.of()), null,
                new NewsScoring.Snapshot<>(factor, new NewsScoring.Flow(n(0), n(0), n(1_000_000))))));
        var frame = new HistoricalReplay.Frame("qa-frame", asOf, cutoff, reference,
                new LiquidUniverse.CalendarReference(CALENDAR, Instant.EPOCH), "qa-price-version", prices, inputs,
                new NewsScoring.Snapshot<>(factor, new NewsScoring.Regime(n(0), n(0))), true, Map.of("AAA", 100, "BBB", 100));
        return context(cutoff, frame, evidence);
    }

    private static HistoricalScreening.Context context(Instant cutoff, HistoricalReplay.Frame frame, List<SessionBar> evidence) {
        var request = new HistoricalDatasetReadiness.Request("qa", "v1", "synthetic", "qa-prices", RAW,
                "synthetic readiness evidence", cutoff, EQUITIES, frame, evidence, 11);
        return new HistoricalScreening.Context(request, NewsDrivenScoringService.modelVersion());
    }

    private static ForwardOutcomeEvaluationService service() {
        var policy = liquidityPolicy();
        var liquid = new LiquidUniverseService(policy);
        var storage = new DefaultListableBeanFactory().getBeanProvider(IdxDailyIngestionService.class);
        var scorer = new NewsDrivenScoringService(new NewsScoringPolicy(n(30), n(25), n(20), n(15), n(10), WEEK, WEEK));
        var screening = new HistoricalScreeningService(new HistoricalDatasetReadinessAudit(policy),
                new StockScreenerService(liquid, scorer, storage), liquid, storage);
        return new ForwardOutcomeEvaluationService(screening);
    }

    private static LiquidityPolicy liquidityPolicy() {
        return new LiquidityPolicy(2, 2, 2, BigDecimal.ONE, 2, n(1_000), n(1_000), 100, WEEK, WEEK);
    }

    private static List<SessionBar> without(List<SessionBar> rows, String symbol, LocalDate date) {
        return rows.stream().filter(row -> !(row.symbol().equals(symbol) && row.date().equals(date))).toList();
    }

    private static List<SessionBar> replace(List<SessionBar> rows, String symbol, LocalDate date, UnaryOperator<SessionBar> edit) {
        return rows.stream().map(row -> row.symbol().equals(symbol) && row.date().equals(date) ? edit.apply(row) : row).toList();
    }

    private static SessionBar replace(SessionBar row, BigDecimal close, Listing listing, CorporateAction action) {
        return new SessionBar(row.symbol(), row.date(), row.availableAt(), row.version(), listing, action, row.open(), close);
    }

    private static SessionBar bar(String symbol, LocalDate date) {
        return new SessionBar(symbol, date, CALENDAR.sessionClose(date).orElseThrow(), "qa-" + symbol + "-" + date,
                Listing.ACTIVE, CorporateAction.NONE, n(100), n(symbol.equals("IHSG") ? 102 : 110));
    }

    private static Summary summary(Report report, Partition partition, int horizon) {
        return report.summaries().stream().filter(row -> row.partition() == partition && row.holdingSessions() == horizon)
                .findFirst().orElseThrow();
    }

    private static VersionedIdxCalendar calendar(String version, LocalDate extraHoliday) {
        var manifest = JsonMapper.builder().build().createObjectNode().put("source", "synthetic calendar").put("version", version);
        var days = manifest.putArray("days");
        for (var date : START.datesUntil(END.plusDays(1)).toList()) {
            boolean closed = date.equals(HOLIDAY) || date.equals(extraHoliday)
                    || date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;
            var day = days.addObject().put("date", date.toString())
                    .put("kind", closed ? "HOLIDAY" : date.equals(ENTRY) ? "SPECIAL" : "REGULAR");
            var sessions = day.putArray("sessions");
            if (!closed) sessions.addObject().put("open", "09:00").put("close", date.equals(ENTRY) ? "11:30" : "16:00");
        }
        return new VersionedIdxCalendar(manifest);
    }

    private static LocalDate date(String monthDay) { return LocalDate.parse("2026-" + monthDay); }
    private static Instant cutoff(String monthDay) { return Instant.parse("2026-" + monthDay + "T10:00:00Z"); }
    private static BigDecimal n(long value) { return BigDecimal.valueOf(value); }
}
