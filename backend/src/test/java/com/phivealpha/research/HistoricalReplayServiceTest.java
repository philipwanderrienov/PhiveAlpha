package com.phivealpha.research;

import static com.phivealpha.research.HistoricalReplay.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.phivealpha.marketdata.apicoid.VersionedIdxCalendar;
import com.phivealpha.persistence.IdxMarketCalendar;
import com.phivealpha.persistence.IdxRecords.AdjustmentStatus;
import com.phivealpha.persistence.IdxRecords.DailyPrice;
import com.phivealpha.screening.LiquidUniverse;
import com.phivealpha.screening.LiquidUniverseService;
import com.phivealpha.screening.LiquidityPolicy;
import com.phivealpha.screening.news.NewsScoring;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class HistoricalReplayServiceTest {
    private static final Instant CUTOFF = Instant.parse("2026-01-09T10:00:00Z");
    private static final Instant FREEZE = Instant.parse("2026-05-01T00:00:00Z");
    private static final LocalDate ENTRY = date(1, 13);
    private static final LocalDate EXIT = date(1, 14);
    private static final VersionedIdxCalendar CALENDAR = calendar();
    private static final Split SPLIT = new Split(new Window(date(1, 5), date(2, 2)),
            new Window(date(2, 18), date(3, 5)), new Window(date(3, 23), date(4, 30)), 10);
    private static final Costs COSTS = new Costs(n(100_000), n(15), n(25), n(20), n(10), new BigDecimal("0.10"));
    private static final LiquidityPolicy LIQUIDITY = new LiquidityPolicy(2, 2, 2, BigDecimal.ONE,
            2, n(1_000), n(1_000), 100, Duration.ofDays(7), Duration.ofDays(7));
    private final HistoricalReplayService service = new HistoricalReplayService(new LiquidUniverseService(LIQUIDITY));

    @Test
    void picksHistoricallyListedInstrumentAndEntersAtNextSessionOpenAfterHoliday() {
        var older = frame("historical", CUTOFF, CUTOFF, true, event(CUTOFF.minusSeconds(1), true));
        var embargoCutoff = Instant.parse("2026-02-06T10:00:00Z");
        var later = frame("future-master", embargoCutoff, embargoCutoff, true, null);
        var report = service.replay(dataset(List.of(later, older), bars()), SPLIT, COSTS, List.of(embargoCutoff, CUTOFF));

        var decision = report.decisions().getFirst();
        assertThat(decision.cutoff()).isEqualTo(CUTOFF);
        assertThat(decision.universeVersion()).isEqualTo("historical-universe");
        assertThat(decision.scoring().ranking()).extracting(item -> item.assessment().symbol()).containsExactly("OLD");
        assertThat(decision.universe()).filteredOn(row -> row.symbol().equals("IPO") || row.symbol().equals("SUSP"))
                .extracting(LiquidUniverse.Result::eligibility).containsOnly(LiquidUniverse.Eligibility.INELIGIBLE);
        assertThat(report.excludedCutoffs()).containsEntry(Reason.OUTSIDE_PARTITIONS, 1L);
        var scored = report.observations().stream().filter(row -> row.arm() == Arm.NEWS_SCORING).toList();
        assertThat(scored).extracting(Observation::entryDate).containsOnly(ENTRY);
        assertThat(scored).extracting(Observation::exitDate).containsExactly(EXIT, date(1, 19), date(1, 26));
        var two = scored.getFirst();
        assertThat(two.shares()).isEqualByComparingTo("400");
        assertThat(two.grossReturn()).isEqualByComparingTo("0.1");
        var net = new BigDecimal("110").multiply(new BigDecimal("0.998")).multiply(new BigDecimal("0.9975"))
                .divide(new BigDecimal("100").multiply(new BigDecimal("1.002")).multiply(new BigDecimal("1.0015")),
                        java.math.MathContext.DECIMAL128).subtract(BigDecimal.ONE);
        assertThat(two.netReturn()).isEqualByComparingTo(net);
        assertThat(two.maxPathDrawdown()).isGreaterThan(new BigDecimal("0.10"));
        var benchmark = metrics(report, Arm.SYNTHETIC_IHSG, 2);
        assertThat(benchmark.meanGrossReturn()).isEqualByComparingTo("0.02");
        assertThat(benchmark.meanNetReturn()).isNull();
        assertThat(metrics(report, Arm.LIQUIDITY_BASELINE, 2).meanNetReturn()).isEqualByComparingTo(net);
        var scoredMetrics = metrics(report, Arm.NEWS_SCORING, 2);
        assertThat(scoredMetrics.coverage()).isEqualByComparingTo("1");
        assertThat(scoredMetrics.winRate()).isEqualByComparingTo("1");
        assertThat(scoredMetrics.averageGain()).isEqualByComparingTo(net);
        assertThat(scoredMetrics.averageLoss()).isNull();
    }

    @Test
    void lateMasterNewsAndOutcomeRevisionsDoNotLeakIntoEarlierCutoffOrFrozenResults() {
        var baseline = frame("historical", CUTOFF, CUTOFF, true, null);
        var delayed = frame("historical", CUTOFF, CUTOFF, true, event(CUTOFF.plusSeconds(1), true));
        var past = delayed.dailyPrices().getFirst();
        var daily = new ArrayList<>(delayed.dailyPrices());
        daily.add(new DailyPrice(past.symbol(), past.tradingDate(), past.open(), past.high(), past.low(),
                past.close(), past.volume(), n(1), past.source(), past.observedAt(),
                CUTOFF.plusSeconds(1).atOffset(ZoneOffset.UTC), past.adjustmentStatus()));
        delayed = new Frame(delayed.version(), delayed.asOf(), delayed.availableAt(), delayed.universe(),
                delayed.calendar(), delayed.dailyPriceVersion(), daily, delayed.inputs(), delayed.market(),
                delayed.historicalNewsFirstSeenVerified(), delayed.lotSizes());
        var lateVintage = frame("late-vintage", CUTOFF, CUTOFF.plusSeconds(1), true, null);
        var outcomes = new ArrayList<>(bars());
        outcomes.add(new SessionBar("OLD", EXIT, FREEZE.plusSeconds(1), "too-late",
                Listing.ACTIVE, CorporateAction.NONE, n(10), n(100_000)));
        Collections.reverse(outcomes);
        var expected = run(List.of(baseline), bars(), COSTS);
        assertThat(run(List.of(lateVintage, delayed), outcomes, COSTS)).isEqualTo(expected);
        assertThat(expected.status()).isEqualTo(Status.EVALUATED);
        assertThat(expected.scoringPolicy().newsWeight()).isEqualByComparingTo("30");
    }

    @Test
    void missingFirstSeenStopsReplayEvenWithKnownPublicationAndAvailability() {
        var report = run(List.of(frame("legacy-news", CUTOFF, CUTOFF, true, event(CUTOFF, false))), bars(), COSTS);
        assertThat(report.status()).isEqualTo(Status.NOT_EVALUABLE);
        assertThat(report.excludedCutoffs()).containsExactlyEntriesOf(Map.of(Reason.FIRST_SEEN_UNVERIFIED, 1L));
        assertThat(report.observations()).isEmpty();
        var uncertified = run(List.of(frame("uncertified", CUTOFF, CUTOFF, false, null)), bars(), COSTS);
        assertThat(uncertified.status()).isEqualTo(Status.NOT_EVALUABLE);
        assertThat(uncertified.decisions().getFirst().reason()).isEqualTo(Reason.FIRST_SEEN_UNVERIFIED);
    }

    @Test
    void missingExitAndCorporateActionRemainCountedAsUnresolved() {
        var frame = frame("historical", CUTOFF, CUTOFF, true, null);
        var missing = bars().stream().filter(row -> !(row.symbol().equals("OLD") && row.date().equals(EXIT))).toList();
        var report = run(List.of(frame), missing, COSTS);
        assertThat(report.status()).isEqualTo(Status.NOT_EVALUABLE);
        assertThat(trade(report, 2).exclusion()).isEqualTo(Reason.MISSING_EXIT);
        var metrics = metrics(report, Arm.NEWS_SCORING, 2);
        assertThat(metrics.entered()).isEqualTo(1);
        assertThat(metrics.unresolved()).isEqualTo(1);
        assertThat(metrics.excluded()).isEqualTo(1);
        assertThat(metrics.coverage()).isEqualByComparingTo("0");
        assertThat(metrics.meanNetReturn()).isNull();

        var earlyFreeze = new Dataset("early-freeze", "Synthetic QA only", List.of(frame),
                new LiquidUniverse.CalendarReference(CALENDAR, Instant.EPOCH), "early-outcomes",
                CALENDAR.sessionClose(ENTRY).orElseThrow().plusSeconds(120), bars());
        var immature = service.replay(earlyFreeze, SPLIT, COSTS, List.of(CUTOFF));
        assertThat(trade(immature, 2).entered()).isTrue();
        assertThat(trade(immature, 2).exclusion()).isEqualTo(Reason.OUTCOME_NOT_MATURE);

        var action = modifyExit(row -> new SessionBar(row.symbol(), row.date(), row.availableAt(), row.version(),
                Listing.ACTIVE, CorporateAction.PRESENT, row.open(), row.close()));
        assertThat(trade(run(List.of(frame), action, COSTS), 2).exclusion()).isEqualTo(Reason.CORPORATE_ACTION);
        for (var listing : List.of(Listing.SUSPENDED, Listing.DELISTED)) {
            var changed = modifyExit(row -> new SessionBar(row.symbol(), row.date(), row.availableAt(), row.version(),
                    listing, CorporateAction.NONE, row.open(), row.close()));
            var trade = trade(run(List.of(frame), changed, COSTS), 2);
            assertThat(trade.entered()).isTrue();
            assertThat(trade.exclusion().name()).isEqualTo(listing.name());
            assertThat(trade.netReturn()).isNull();
        }
    }

    @Test
    void lotCapacityAndExitLossAreExplicit() {
        var frame = frame("historical", CUTOFF, CUTOFF, true, null);
        var minimum = new Costs(n(9_999), n(15), n(25), n(20), n(10), new BigDecimal("0.10"));
        var report = run(List.of(frame), bars(), minimum);
        assertThat(trade(report, 2).exclusion()).isEqualTo(Reason.INSUFFICIENT_CAPACITY);
        assertThat(trade(report, 2).entered()).isFalse();

        var lossBars = modifyExit(row -> new SessionBar(row.symbol(), row.date(), row.availableAt(), row.version(),
                Listing.ACTIVE, CorporateAction.NONE, row.open(), n(80)));
        var loss = metrics(run(List.of(frame), lossBars, COSTS), Arm.NEWS_SCORING, 2);
        assertThat(loss.winRate()).isEqualByComparingTo("0");
        assertThat(loss.averageLoss()).isNegative();
        assertThat(loss.averageGain()).isNull();
    }

    @Test
    void enforcesChronologicalEmbargoAndPurgesLabelsCrossingPartitionEnd() {
        var frame = frame("historical", CUTOFF, CUTOFF, true, null);
        var smallGap = new Split(SPLIT.training(), new Window(date(2, 3), date(3, 5)), SPLIT.outOfSample(), 10);
        assertThatThrownBy(() -> service.replay(dataset(List.of(frame), bars()), smallGap, COSTS, List.of(CUTOFF)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("embargo");

        var shortTraining = new Split(new Window(date(1, 5), EXIT), SPLIT.validation(), SPLIT.outOfSample(), 10);
        var report = service.replay(dataset(List.of(frame), bars()), shortTraining, COSTS, List.of(CUTOFF));
        assertThat(trade(report, 2).exclusion()).isNull();
        assertThat(trade(report, 5).exclusion()).isEqualTo(Reason.EXIT_BEYOND_PARTITION);
        assertThat(trade(report, 10).exclusion()).isEqualTo(Reason.EXIT_BEYOND_PARTITION);
        assertThat(report.status()).isEqualTo(Status.PARTIAL);
    }

    @Test
    void outOfSampleHasSeparateMetricsAndUsesFrozenWeights() {
        var cutoff = Instant.parse("2026-03-23T10:00:00Z");
        var frame = frame("out-of-sample", cutoff, cutoff, true, null);
        var entry = date(3, 24);
        var synthetic = new ArrayList<SessionBar>();
        for (var session : entry.datesUntil(date(4, 8)).filter(CALENDAR::isTradingSession).toList()) {
            var available = CALENDAR.sessionClose(session).orElseThrow();
            for (var symbol : List.of("OLD", "IHSG")) {
                synthetic.add(new SessionBar(symbol, session, available, "qa-" + session,
                        Listing.ACTIVE, CorporateAction.NONE, n(100), n(110)));
            }
        }
        var report = service.replay(dataset(List.of(frame), synthetic), SPLIT, COSTS, List.of(cutoff));
        assertThat(report.status()).isEqualTo(Status.EVALUATED);
        assertThat(report.decisions().getFirst().partition()).isEqualTo(Partition.OUT_OF_SAMPLE);
        assertThat(report.decisions().getFirst().scoring().policy()).isEqualTo(report.scoringPolicy());
        assertThat(report.metrics().stream().filter(row -> row.partition() != Partition.OUT_OF_SAMPLE))
                .allMatch(row -> row.observations() == 0 && row.meanNetReturn() == null);
    }

    private Report run(List<Frame> frames, List<SessionBar> outcomes, Costs costs) {
        return service.replay(dataset(frames, outcomes), SPLIT, costs, List.of(CUTOFF));
    }

    private static Dataset dataset(List<Frame> frames, List<SessionBar> rows) {
        return new Dataset("synthetic-dataset-v1", "Synthetic QA only", frames,
                new LiquidUniverse.CalendarReference(CALENDAR, Instant.EPOCH), "synthetic-outcomes-v1", FREEZE, rows);
    }

    private static Frame frame(String version, Instant asOf, Instant availableAt, boolean verified, NewsScoring.Event event) {
        var members = List.of(
                member("OLD", version.equals("future-master") ? LiquidUniverse.ListingStatus.DELISTED
                        : LiquidUniverse.ListingStatus.ACTIVE, date(1, 2)),
                member("IPO", LiquidUniverse.ListingStatus.ACTIVE, date(1, 9)),
                member("SUSP", LiquidUniverse.ListingStatus.SUSPENDED, date(1, 2)));
        var reference = new LiquidUniverse.Reference(version + "-universe", asOf, availableAt, "Synthetic master", members);
        var evidence = new NewsScoring.Evidence("Synthetic factor", version + "-factors", asOf, availableAt,
                NewsScoring.Quality.AVAILABLE);
        var inputs = new NewsScoring.Inputs(new NewsScoring.Snapshot<>(evidence, event == null ? List.of() : List.of(event)),
                new NewsScoring.Snapshot<>(evidence, new NewsScoring.Momentum(n(100), n(100), n(100))),
                new NewsScoring.Snapshot<>(evidence, new NewsScoring.Flow(n(0), n(0), n(1_000))));
        var asOfDate = asOf.atZone(IdxMarketCalendar.ZONE).toLocalDate();
        var closedDates = asOfDate.minusDays(7).datesUntil(asOfDate.plusDays(1))
                .filter(day -> CALENDAR.isClosedBy(day, asOf)).toList();
        var daily = closedDates.reversed().stream().limit(2).map(day -> {
            var close = CALENDAR.sessionClose(day).orElseThrow().atOffset(ZoneOffset.UTC);
            return new DailyPrice("OLD", day, n(90), n(100), n(80), n(90), 10_000, n(500_000),
                    "Synthetic EOD", close, close.plusSeconds(1), AdjustmentStatus.UNADJUSTED);
        }).toList();
        return new Frame(version, asOf, availableAt, reference, new LiquidUniverse.CalendarReference(CALENDAR, Instant.EPOCH),
                version + "-prices", daily, Map.of("OLD", inputs),
                new NewsScoring.Snapshot<>(evidence, new NewsScoring.Regime(n(0), n(0))), verified, Map.of("OLD", 100));
    }

    private static NewsScoring.Event event(Instant availableAt, boolean verified) {
        return new NewsScoring.Event("synthetic-event", "Synthetic announcement", CUTOFF.minusSeconds(120),
                verified ? CUTOFF.minusSeconds(60) : null, availableAt, n(1), n(1), n(1), n(1), n(0));
    }

    private static LiquidUniverse.Member member(String name, LiquidUniverse.ListingStatus status, LocalDate listedOn) {
        return new LiquidUniverse.Member(name, LiquidUniverse.SecurityType.COMMON_STOCK, status, listedOn,
                LiquidUniverse.CorporateActionStatus.CLEAR);
    }

    private static List<SessionBar> bars() {
        var rows = new ArrayList<SessionBar>();
        for (var day : ENTRY.datesUntil(date(1, 27)).filter(CALENDAR::isTradingSession).toList()) {
            var available = CALENDAR.sessionClose(day).orElseThrow().plusSeconds(60);
            rows.add(new SessionBar("OLD", day, available, "old-" + day,
                    Listing.ACTIVE, CorporateAction.NONE, n(100), n(day.equals(ENTRY) ? 90 : 110)));
            rows.add(new SessionBar("IHSG", day, available, "synthetic-ihsg-" + day,
                    Listing.ACTIVE, CorporateAction.NONE, n(1_000), n(1_020)));
        }
        return rows;
    }

    private static List<SessionBar> modifyExit(UnaryOperator<SessionBar> operation) {
        return bars().stream().map(row -> row.symbol().equals("OLD") && row.date().equals(EXIT)
                ? operation.apply(row) : row).toList();
    }

    private static Observation trade(Report report, int horizon) {
        return report.observations().stream().filter(row -> row.arm() == Arm.NEWS_SCORING
                && row.holdingSessions() == horizon).findFirst().orElseThrow();
    }

    private static Metrics metrics(Report report, Arm arm, int horizon) {
        return report.metrics().stream().filter(row -> row.partition() == Partition.TRAIN && row.arm() == arm
                && row.holdingSessions() == horizon).findFirst().orElseThrow();
    }

    private static VersionedIdxCalendar calendar() {
        var mapper = JsonMapper.builder().build();
        var node = mapper.createObjectNode().put("source", "Synthetic BEI calendar fixture").put("version", "qa-calendar-v1");
        var days = node.putArray("days");
        for (var day : date(1, 1).datesUntil(date(5, 1)).toList()) {
            boolean holiday = day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY
                    || day.equals(date(1, 12));
            var sessions = days.addObject().put("date", day.toString())
                    .put("kind", holiday ? "HOLIDAY" : day.equals(ENTRY) ? "SPECIAL" : "REGULAR").putArray("sessions");
            if (!holiday) {
                sessions.addObject().put("open", day.equals(ENTRY) ? "10:00" : "09:00").put("close", "16:00");
            }
        }
        return new VersionedIdxCalendar(node);
    }

    private static LocalDate date(int month, int day) {
        return LocalDate.of(2026, month, day);
    }

    private static BigDecimal n(long number) {
        return BigDecimal.valueOf(number);
    }
}
