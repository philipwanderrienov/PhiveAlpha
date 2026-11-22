package com.phivealpha.screening;

import static com.phivealpha.screening.HistoricalScreening.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.phivealpha.marketdata.apicoid.VersionedIdxCalendar;
import com.phivealpha.persistence.IdxDailyIngestionService;
import com.phivealpha.persistence.IdxRecords.AdjustmentStatus;
import com.phivealpha.persistence.IdxRecords.DailyPrice;
import com.phivealpha.research.HistoricalReplay.CorporateAction;
import com.phivealpha.research.HistoricalReplay.Frame;
import com.phivealpha.research.HistoricalReplay.Listing;
import com.phivealpha.research.HistoricalReplay.SessionBar;
import com.phivealpha.screening.news.NewsDrivenScoringService;
import com.phivealpha.screening.news.NewsScoring;
import com.phivealpha.screening.news.NewsScoringPolicy;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import tools.jackson.databind.json.JsonMapper;

class HistoricalScreeningServiceTest {
    private static final Instant CUTOFF = StockScreenerFixture.CUTOFF;
    private static final String MODEL = NewsDrivenScoringService.modelVersion();
    private static final List<String> SYMBOLS = List.of("TLKM", "ICBP", "BMRI", "BBRI", "BBCA", "ASII");
    private static final StockScreenerFixture.Data FIXTURE = StockScreenerFixture.data();
    private static final Duration WEEK = Duration.ofDays(7);

    private final LiquidityPolicy policy = new LiquidityPolicy(20, 20, 15, new BigDecimal("0.75"), 12,
            n(1_000_000_000), n(2_000_000_000), 100_000, WEEK, WEEK);
    private final LiquidUniverseService liquidity = new LiquidUniverseService(policy);
    private final HistoricalScreeningService service = service();
    private final List<SessionBar> evidence = liquidity.priceWindow(FIXTURE.context().calendar(), CUTOFF)
            .stream().flatMap(date -> Stream.concat(SYMBOLS.stream(), Stream.of("IHSG")).map(symbol ->
                    new SessionBar(symbol, date, FIXTURE.context().calendar().calendar().sessionClose(date).orElseThrow(),
                            "synthetic-session-v1", Listing.ACTIVE, CorporateAction.NONE, n(100), n(100)))).toList();

    @Test
    void validHistoricalFixtureRunsTheExistingScreenerWithDeterministicTies() {
        var report = service.screen(context(frame()));
        assertThat(report.status()).isEqualTo(Status.RANKED);
        assertThat(report.purpose()).isEqualTo("RESEARCH_ONLY");
        assertThat(report.cutoff()).isEqualTo(CUTOFF);
        assertThat(report.readiness().outcome()).isEqualTo(HistoricalDatasetReadiness.Outcome.READY);
        assertThat(report.readiness().lookbackSessions()).isEqualTo(20);
        assertThat(service.screen(new Context(request(frame(), evidence, 5), MODEL))).isEqualTo(report);
        assertThat(report.readiness().provenance().datasetVersion()).isEqualTo("dataset-v1");
        assertThat(report.readiness().provenance().calendarVersion()).isEqualTo(FIXTURE.context().calendar().calendar().version());
        var ranking = report.scoring().ranking();
        assertThat(ranking).extracting(row -> row.assessment().symbol())
                .containsExactly("ASII", "BBCA", "BBRI", "BMRI", "ICBP");
        assertThat(ranking).extracting(row -> row.assessment().overallScore()).containsOnly(ranking.getFirst().assessment().overallScore());
        assertThat(report.scoring().modelVersion()).isEqualTo(MODEL);
        assertThat(ranking.stream().flatMap(row -> row.assessment().factors().stream()))
                .allSatisfy(factor -> assertThat(factor.evidence().availableAt()).isBeforeOrEqualTo(CUTOFF));
        assertThat(report.decisions()).extracting(StockScreenerService.Decision::symbol).doesNotContain("SUSP");
    }

    @Test
    void repeatedEvaluationAndPriceReorderingProduceTheSameReport() {
        var context = context(frame());
        var expected = service.screen(context);
        assertThat(service.screen(context)).isEqualTo(expected);
        assertThat(service.screen(context(withPrices(frame(), frame().dailyPrices().reversed())))).isEqualTo(expected);
    }

    @Test
    void latePriceIngestionCannotReplaceAnEarlierVisibleCandle() {
        var rows = new ArrayList<>(frame().dailyPrices());
        var original = rows.getLast();
        rows.add(new DailyPrice(original.symbol(), original.tradingDate(), n(500), n(500), n(500), n(500),
                original.volume(), original.turnover(), original.source(), original.observedAt(),
                original.ingestedAt().plusDays(1), original.adjustmentStatus()));
        assertThat(service.screen(context(withPrices(frame(), rows)))).isEqualTo(service.screen(context(frame())));
        rows.remove(original);
        var blocked = service.screen(context(withPrices(frame(), rows)));
        assertReadinessBlocked(blocked);
        assertThat(blocked.readiness().equities().stream().flatMap(row -> row.issues().stream()))
                .anyMatch(issue -> issue.reason() == HistoricalDatasetReadiness.Reason.MISSING_CANDLE);
    }

    @Test
    void laterNewsAndEventRevisionsCannotChangeAnEarlierResult() {
        var frame = frame();
        var old = frame.inputs().get("ASII");
        var visible = event("revision", CUTOFF.minusSeconds(200), CUTOFF.minusSeconds(190), CUTOFF.minusSeconds(180));
        var baseline = withInput(frame, "ASII",
                new NewsScoring.Inputs(new NewsScoring.Snapshot<>(old.news().evidence(), List.of(visible)), null, old.flow()));
        var expected = service.screen(context(baseline));
        assertThat(expected.scoring().ranking().getFirst().assessment().catalystSummary().eventId()).isEqualTo("revision");

        var news = List.of(visible,
                event("release", CUTOFF.plusSeconds(1), CUTOFF.plusSeconds(2), CUTOFF.plusSeconds(3)),
                event("revision", CUTOFF.minusSeconds(200), CUTOFF.minusSeconds(190), CUTOFF.plusSeconds(1)),
                event("late-first-seen", CUTOFF.minusSeconds(4), CUTOFF.plusSeconds(1), CUTOFF.plusSeconds(2)));
        var updated = withInput(frame, "ASII",
                new NewsScoring.Inputs(new NewsScoring.Snapshot<>(old.news().evidence(), news), null, old.flow()));
        assertThat(service.screen(context(updated))).isEqualTo(expected);
    }

    @Test
    void aFutureUniverseCannotSupplyMembershipForTheEarlierCutoff() {
        var frame = frame();
        var future = new LiquidUniverse.Reference("later-universe", CUTOFF.plusSeconds(1), CUTOFF.plusSeconds(1),
                frame.universe().source(), frame.universe().members().stream().filter(row -> !row.symbol().equals("ASII")).toList());
        var changed = new Frame(frame.version(), frame.asOf(), frame.availableAt(), future, frame.calendar(),
                frame.dailyPriceVersion(), frame.dailyPrices(), frame.inputs(), frame.market(), true, Map.of());
        var report = service.screen(context(changed));
        assertReadinessBlocked(report);
        assertThat(report.readiness().issues()).extracting(HistoricalDatasetReadiness.Issue::reason)
                .contains(HistoricalDatasetReadiness.Reason.UNIVERSE_AFTER_FRAME);
        assertThat(service.screen(context(frame)).scoring().ranking().getFirst().assessment().symbol()).isEqualTo("ASII");
    }

    @Test
    void aLaterCalendarRevisionCannotReplaceTheHistoricalVersion() {
        var frame = frame();
        var manifest = JsonMapper.builder().build().readTree("""
                {"version":"later-calendar","source":"synthetic","days":[
                  {"date":"2026-11-13","kind":"HOLIDAY","sessions":[]}
                ]}
                """);
        var future = new LiquidUniverse.CalendarReference(new VersionedIdxCalendar(manifest), CUTOFF.plusSeconds(1));
        var changed = new Frame(frame.version(), frame.asOf(), frame.availableAt(), frame.universe(), future,
                frame.dailyPriceVersion(), frame.dailyPrices(), frame.inputs(), frame.market(), true, Map.of());
        var report = service.screen(context(changed));
        assertReadinessBlocked(report);
        assertThat(report.readiness().provenance().calendarVersion()).isEqualTo("later-calendar");
        assertThat(report.readiness().issues()).extracting(HistoricalDatasetReadiness.Issue::reason)
                .containsExactly(HistoricalDatasetReadiness.Reason.CALENDAR_AFTER_FRAME);
    }

    @Test
    void missingStaleAndFutureFactorsCannotScoreAffectedCandidates() {
        var frame = frame();
        var asii = frame.inputs().get("ASII");
        var bbca = frame.inputs().get("BBCA");
        var bbri = frame.inputs().get("BBRI");
        var expired = evidenceAt(CUTOFF.minus(WEEK).minusSeconds(1));
        var future = evidenceAt(CUTOFF.plusSeconds(1));
        var changed = withInput(frame, "ASII", new NewsScoring.Inputs(asii.news(), null, null));
        changed = withInput(changed, "BBCA", new NewsScoring.Inputs(bbca.news(), null,
                new NewsScoring.Snapshot<>(expired, bbca.flow().value())));
        changed = withInput(changed, "BBRI", new NewsScoring.Inputs(bbri.news(), null,
                new NewsScoring.Snapshot<>(future, bbri.flow().value())));
        var report = service.screen(context(changed));
        assertThat(report.scoring().ranking()).extracting(row -> row.assessment().symbol()).containsExactly("BMRI", "ICBP", "TLKM");
        assertThat(report.rejections()).containsExactly(new Rejection(Reason.FACTOR_AFTER_FRAME, "BBRI", NewsScoring.Factor.FOREIGN_BROKER_FLOW));
        assertThat(report.scoring().assessments().get(0).reasons()).contains(NewsScoring.Reason.SOURCE_MISSING);
        assertThat(report.scoring().assessments().get(1).reasons()).contains(NewsScoring.Reason.SOURCE_STALE);
    }

    @Test
    void insufficientHistoryAndMissingBenchmarkBlockBeforeScoring() {
        var frame = frame();
        assertReadinessBlocked(service.screen(context(withPrices(frame, frame.dailyPrices().subList(0, 1)))));
        var request = context(frame).dataset();
        var noBenchmark = evidence.stream().filter(row -> !row.symbol().equals("IHSG")).toList();
        var blocked = service.screen(new Context(request(frame, noBenchmark, request.lookbackSessions()), MODEL));
        assertReadinessBlocked(blocked);
        assertThat(blocked.readiness().benchmark().outcome()).isEqualTo(HistoricalDatasetReadiness.Outcome.UNAVAILABLE);
    }

    @Test
    void unavailablePersistenceNeverUsesTheMaterializedFrameAsFallback() {
        var context = context(frame());
        assertThat(service.screen(context).status()).isEqualTo(Status.RANKED);
        var unavailable = service.screenPersisted(context).block(Duration.ofSeconds(5));
        assertThat(unavailable.status()).isEqualTo(Status.BLOCKED);
        assertThat(unavailable.readiness().outcome()).isEqualTo(HistoricalDatasetReadiness.Outcome.UNAVAILABLE);
        assertThat(unavailable.rejections()).containsExactly(new Rejection(Reason.PERSISTENCE_DISABLED, null, null));
        assertThat(unavailable.scoring()).isNull();
        assertThat(unavailable.decisions()).isEmpty();
    }

    @Test
    void freezesCallerNewsListAndRequiresFirstSeenAttestationAndModelVersion() {
        var frame = frame();
        var mutable = new ArrayList<NewsScoring.Event>();
        var old = frame.inputs().get("ASII");
        var changed = withInput(frame, "ASII",
                new NewsScoring.Inputs(new NewsScoring.Snapshot<>(old.news().evidence(), mutable), null, old.flow()));
        var context = context(changed);
        mutable.add(event("visible-before-freeze", CUTOFF.minusSeconds(200), CUTOFF.minusSeconds(190), CUTOFF.minusSeconds(180)));
        assertThat(service.screen(context)).isEqualTo(service.screen(context(frame)));
        var unattested = new Frame(frame.version(), frame.asOf(), frame.availableAt(), frame.universe(), frame.calendar(),
                frame.dailyPriceVersion(), frame.dailyPrices(), frame.inputs(), frame.market(), false, Map.of());
        assertThat(service.screen(context(unattested)).rejections())
                .containsExactly(new Rejection(Reason.FIRST_SEEN_UNVERIFIED, null, null));
        assertThat(service.screen(new Context(context.dataset(), "another-model")).rejections())
                .containsExactly(new Rejection(Reason.MODEL_VERSION_MISMATCH, null, null));
    }

    private HistoricalScreeningService service() {
        var provider = new DefaultListableBeanFactory().getBeanProvider(IdxDailyIngestionService.class);
        var scorer = new NewsDrivenScoringService(new NewsScoringPolicy(n(30), n(25), n(20), n(15), n(10), WEEK, WEEK));
        return new HistoricalScreeningService(new HistoricalDatasetReadinessAudit(policy),
                new StockScreenerService(liquidity, scorer, provider), liquidity, provider);
    }

    private static Frame frame() {
        var context = FIXTURE.context();
        var factors = context.factors().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                entry -> new NewsScoring.Inputs(entry.getValue().news(), null, entry.getValue().flow())));
        return new Frame("historical-frame-v1", CUTOFF, CUTOFF, context.universe(), context.calendar(),
                "price-v1", FIXTURE.prices(), factors, context.market(), true, Map.of());
    }

    private Context context(Frame frame) {
        return new Context(request(frame, evidence, 20), MODEL);
    }

    private static HistoricalDatasetReadiness.Request request(Frame frame, List<SessionBar> evidence, int lookback) {
        return new HistoricalDatasetReadiness.Request("synthetic-dataset", "dataset-v1", "synthetic",
                FIXTURE.prices().getFirst().source(), AdjustmentStatus.UNADJUSTED, "synthetic-session-feed",
                CUTOFF, SYMBOLS, frame, evidence, lookback);
    }

    private static Frame withPrices(Frame frame, List<DailyPrice> prices) {
        return new Frame(frame.version(), frame.asOf(), frame.availableAt(), frame.universe(), frame.calendar(),
                frame.dailyPriceVersion(), prices, frame.inputs(), frame.market(), true, Map.of());
    }

    private static Frame withInput(Frame frame, String symbol, NewsScoring.Inputs input) {
        var factors = new HashMap<>(frame.inputs());
        factors.put(symbol, input);
        return new Frame(frame.version(), frame.asOf(), frame.availableAt(), frame.universe(), frame.calendar(),
                frame.dailyPriceVersion(), frame.dailyPrices(), factors, frame.market(), true, Map.of());
    }

    private static NewsScoring.Event event(String id, Instant published, Instant firstSeen, Instant available) {
        return new NewsScoring.Event(id, id, published, firstSeen, available,
                n(1), n(1), n(1), n(1), n(0));
    }

    private static NewsScoring.Evidence evidenceAt(Instant instant) {
        return new NewsScoring.Evidence("synthetic-factor-revision", "revision-v2", instant, instant,
                NewsScoring.Quality.AVAILABLE);
    }

    private static BigDecimal n(long value) {
        return BigDecimal.valueOf(value);
    }

    private static void assertReadinessBlocked(Report report) {
        assertThat(report.status()).isEqualTo(Status.BLOCKED);
        assertThat(report.rejections()).containsExactly(new Rejection(Reason.READINESS_REJECTED, null, null));
        assertThat(report.scoring()).isNull();
        assertThat(report.decisions()).isEmpty();
    }
}
