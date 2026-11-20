package com.phivealpha.screening.news;

import static com.phivealpha.screening.news.NewsScoring.*;
import static com.phivealpha.screening.news.NewsScoring.Factor.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.phivealpha.marketdata.apicoid.VersionedIdxCalendar;
import com.phivealpha.persistence.IdxRecords.AdjustmentStatus;
import com.phivealpha.persistence.IdxRecords.DailyPrice;
import com.phivealpha.screening.LiquidUniverse.CalendarReference;
import com.phivealpha.screening.LiquidUniverse.CorporateActionStatus;
import com.phivealpha.screening.LiquidUniverse.Evaluation;
import com.phivealpha.screening.LiquidUniverse.ListingStatus;
import com.phivealpha.screening.LiquidUniverse.Member;
import com.phivealpha.screening.LiquidUniverse.Reference;
import com.phivealpha.screening.LiquidUniverse.SecurityType;
import com.phivealpha.screening.LiquidUniverseService;
import com.phivealpha.screening.LiquidityPolicy;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class NewsDrivenScoringServiceTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Instant CUTOFF = Instant.parse("2026-11-13T05:00:00Z");
    private static final Instant AS_OF = CUTOFF.minusSeconds(60);
    private static final Duration WEEK = Duration.ofDays(7);
    private static final Evidence EVIDENCE = new Evidence("synthetic source", "fixture-v1",
            AS_OF, CUTOFF, Quality.AVAILABLE);
    private static final NewsScoringPolicy POLICY =
            new NewsScoringPolicy(n(30), n(25), n(20), n(15), n(10), WEEK, WEEK);
    private final NewsDrivenScoringService service = new NewsDrivenScoringService(POLICY);

    @Test
    void separatesNewsSentimentFromMaterialSurpriseAndDeduplicatesCanonicalEvents() throws IOException {
        var immaterial = event("immaterial");
        var negative = event("negative");
        var duplicated = event("duplicate");
        var anticipated = event("anticipated");
        var evaluation = universe(List.of("GOOD", "NEG"), "THIN");
        var good = inputs(List.of(immaterial, anticipated));
        var first = service.score(evaluation,
                Map.of("NEG", inputs(List.of(negative)), "GOOD", good, "THIN", inputs(List.of())), market(), CUTOFF);
        var replay = service.score(evaluation,
                Map.of("NEG", inputs(List.of(duplicated, negative)), "GOOD", good), market(), CUTOFF);
        assertThat(replay).isEqualTo(first);
        assertThat(first.ranking()).extracting(item -> item.assessment().symbol()).containsExactly("GOOD", "NEG");
        var neutral = first.ranking().getFirst().assessment();
        assertThat(factor(neutral, NEWS_CATALYST).score()).isEqualByComparingTo("50");
        assertThat(factor(neutral, NEWS_CATALYST).rawMetrics()).containsEntry("uniqueRecentEvents", n(2))
                .containsEntry("zeroMaterialityEvents", n(1)).containsEntry("fullyAnticipatedEvents", n(1));
        assertThat(neutral.catalystSummary()).isNull();
        assertThat(neutral.overallScore()).isEqualByComparingTo("61.5");
        var harmful = first.ranking().getLast().assessment();
        assertThat(harmful.catalystSummary().eventId()).isEqualTo("plant-closure");
        assertThat(harmful.riskNotes()).containsExactly(Risk.MATERIAL_NEGATIVE_CATALYST);
        assertThat(factor(harmful, NEWS_CATALYST).score()).isEqualByComparingTo("10");
        assertThat(factor(harmful, PRICE_MOMENTUM).rawMetrics().get("returnFiveSessionsPct"))
                .isEqualByComparingTo("2");
        assertThat(factor(harmful, VOLUME_LIQUIDITY).evidence().source()).isEqualTo("synthetic EOD");
        assertThat(harmful.overallScore()).isEqualByComparingTo("49.5");
        assertThat(first.assessments()).extracting(Assessment::symbol).containsExactly("GOOD", "NEG");

        var old = new Event(negative.eventId(), negative.label(), negative.publishedAt().minus(WEEK),
                negative.firstSeenAt().minus(WEEK), negative.availableAt().minus(WEEK), negative.materiality(),
                negative.relevance(), negative.novelty(), negative.sentiment(), negative.anticipated());
        var repost = service.score(evaluation, Map.of("NEG", inputs(List.of(duplicated, old))), market(), CUTOFF);
        var news = factor(repost.ranking().getFirst().assessment(), NEWS_CATALYST);
        assertThat(news.score()).isEqualByComparingTo("50");
        assertThat(news.rawMetrics()).containsEntry("uniqueRecentEvents", n(0));
    }

    @Test
    void ignoresEventsUnavailableAtCutoffAndRejectsFutureFactorSnapshots() throws IOException {
        var universe = universe(List.of("TEST"), "THIN");
        var events = List.of(event("negative"));
        var baseline = service.score(universe, Map.of("TEST", inputs(events)), market(), CUTOFF);
        var later = service.score(universe, Map.of("TEST", inputs(List.of(events.getFirst(), event("late")))),
                market(), CUTOFF);
        assertThat(later).isEqualTo(baseline);
        var input = inputs(events);
        var futureMomentum = new Snapshot<>(new Evidence("future", "future-v2", AS_OF,
                CUTOFF.plusSeconds(1), Quality.AVAILABLE), input.momentum().value());
        var blocked = service.score(universe, Map.of("TEST", new Inputs(input.news(), futureMomentum, input.flow())),
                market(), CUTOFF);
        var momentum = factor(blocked.assessments().getFirst(), PRICE_MOMENTUM);
        assertThat(momentum.quality()).isEqualTo(Quality.UNAVAILABLE);
        assertThat(momentum.reason()).isEqualTo(Reason.NOT_AVAILABLE_AT_CUTOFF);
        assertThat(momentum.evidence()).isNull();
        assertThat(blocked.ranking()).isEmpty();
        assertThat(service.score(universe, Map.of(), market(), CUTOFF.minusSeconds(1)).reasons())
                .containsExactly(Reason.UNIVERSE_UNVERIFIED);
    }

    @Test
    void missingFlowAndIncompleteMetricsNeverReceiveAnOverallScore() throws IOException {
        var universe = universe(List.of("MISS", "BAD"), "THIN");
        var valid = inputs(List.of());
        var invalidFlow = new Snapshot<>(EVIDENCE, new Flow(null, n(5), n(1000)));
        var report = service.score(universe, Map.of(
                "MISS", new Inputs(valid.news(), valid.momentum(), null),
                "BAD", new Inputs(valid.news(), valid.momentum(), invalidFlow)), market(), CUTOFF);
        assertThat(report.ranking()).isEmpty();
        for (var row : report.assessments()) {
            assertThat(row.status()).isEqualTo(Status.INSUFFICIENT_DATA);
            assertThat(row.overallScore()).isNull();
            var flow = factor(row, FOREIGN_BROKER_FLOW);
            assertThat(flow.quality()).isEqualTo(row.symbol().equals("BAD") ? Quality.INCOMPLETE : Quality.UNAVAILABLE);
            assertThat(flow.score()).isNull();
            assertThat(flow.contribution()).isNull();
        }
    }

    @Test
    void bearishMarketReducesScoreAndProvidesRiskNotes() throws IOException {
        var universe = universe(List.of("TEST"), "THIN");
        var input = Map.of("TEST", inputs(List.of()));
        var neutral = service.score(universe, input, market(), CUTOFF).ranking().getFirst().assessment();
        var bearish = new Snapshot<>(EVIDENCE, new Regime(n(-3), new BigDecimal("0.8")));
        var result = service.score(universe, input, bearish, CUTOFF).ranking().getFirst().assessment();
        assertThat(factor(result, MARKET_REGIME_RISK).score()).isEqualByComparingTo("0");
        assertThat(result.overallScore()).isLessThan(neutral.overallScore());
        assertThat(result.riskNotes()).containsExactly(Risk.BEARISH_MARKET, Risk.ELEVATED_MARKET_RISK);
    }

    @Test
    void stableTiesAreCappedAtFiveAndReportContainsNoTradingSignal() throws IOException {
        var names = new ArrayList<>(List.of("FFFF", "EEEE", "DDDD", "CCCC", "BBBB", "AAAA"));
        var inputs = new java.util.TreeMap<String, Inputs>();
        for (var name : names) {
            inputs.put(name, inputs(List.of()));
        }
        var report = service.score(universe(names, "THIN"), inputs, market(), CUTOFF);
        Collections.reverse(names);
        var replay = service.score(universe(names, "THIN"), inputs.descendingMap(), market(), CUTOFF);
        assertThat(replay).isEqualTo(report);
        assertThat(report.purpose()).isEqualTo("RESEARCH_ONLY");
        assertThat(report.assessments()).hasSize(6);
        assertThat(report.ranking()).extracting(item -> item.assessment().symbol())
                .containsExactly("AAAA", "BBBB", "CCCC", "DDDD", "EEEE");
        assertThat(report.ranking()).extracting(RankedCandidate::rank).containsExactly(1, 2, 3, 4, 5);
        assertThat(JSON.writeValueAsString(report))
                .doesNotContain("BUY", "SETUP READY", "\"signal\"", "\"order\"", "\"execution\"");
    }

    @Test
    void allStaleOrUnverifiedFactorsFailClosed() throws IOException {
        var restrictive = new NewsDrivenScoringService(new NewsScoringPolicy(n(30), n(25), n(20), n(15), n(10),
                Duration.ofSeconds(30), WEEK));
        var universe = universe(List.of("TEST"), "THIN");
        var stale = restrictive.score(universe, Map.of("TEST", inputs(List.of())), market(), CUTOFF);
        assertThat(stale.ranking()).isEmpty();
        var assessment = stale.assessments().getFirst();
        assertThat(assessment.status()).isEqualTo(Status.INSUFFICIENT_DATA);
        assertThat(assessment.factors()).extracting(FactorScore::quality).containsOnly(Quality.STALE);

        var unverified = new Evidence("unverified fixture", "fixture-v2", AS_OF, CUTOFF, Quality.UNVERIFIED);
        var input = new Inputs(new Snapshot<>(unverified, List.of()), new Snapshot<>(unverified, null),
                new Snapshot<>(unverified, null));
        var report = restrictive.score(universe, Map.of("TEST", input), new Snapshot<>(unverified, null), CUTOFF);
        assertThat(report.ranking()).isEmpty();
        assertThat(report.assessments().getFirst().status()).isEqualTo(Status.UNVERIFIED);
        assertThat(report.assessments().getFirst().factors()).extracting(FactorScore::quality)
                .containsOnly(Quality.UNVERIFIED, Quality.STALE);
    }

    @Test
    void conflictingEventAnnotationsFailClosedAndPolicyWeightsAreValidated() throws IOException {
        var negative = event("negative");
        var conflict = new Event(negative.eventId(), negative.label(), negative.publishedAt(), negative.firstSeenAt(),
                negative.availableAt(), negative.materiality(), negative.relevance(), negative.novelty(),
                n(1), negative.anticipated());
        var universe = universe(List.of("TEST"), "THIN");
        var report = service.score(universe, Map.of("TEST", inputs(List.of(negative, conflict))), market(), CUTOFF);
        assertThat(report.ranking()).isEmpty();
        assertThat(factor(report.assessments().getFirst(), NEWS_CATALYST).reason())
                .isEqualTo(Reason.CONFLICTING_NEWS_EVENT);
        assertThatThrownBy(() -> new NewsScoringPolicy(n(25), n(25), n(20), n(15), n(10), WEEK, WEEK))
                .isInstanceOf(IllegalArgumentException.class);
        var modified = new NewsDrivenScoringService(new NewsScoringPolicy(n(40), n(15), n(20), n(15), n(10), WEEK, WEEK));
        var result = modified.score(universe, Map.of("TEST", inputs(List.of())), market(), CUTOFF);
        assertThat(factor(result.assessments().getFirst(), NEWS_CATALYST).weightPct()).isEqualByComparingTo("40");
    }

    private static Evaluation universe(List<String> eligible, String thin) throws IOException {
        var policy = new LiquidityPolicy(4, 4, 3, new BigDecimal("0.75"), 3, n(1000), n(2000), 50,
                WEEK, WEEK);
        var calendar = new VersionedIdxCalendar(fixture("/phivealpha/liquid-universe/calendar.json"));
        var members = new ArrayList<Member>();
        var prices = new ArrayList<DailyPrice>();
        var names = new ArrayList<>(eligible);
        names.add(thin);
        for (var symbol : names) {
            members.add(new Member(symbol, SecurityType.COMMON_STOCK, ListingStatus.ACTIVE,
                    LocalDate.parse("2010-01-04"), CorporateActionStatus.CLEAR));
            for (int day : new int[] {9, 10, 11, 13}) {
                var date = LocalDate.of(2026, 11, day);
                var observed = calendar.sessionClose(date).orElseThrow().atOffset(ZoneOffset.UTC);
                boolean liquid = eligible.contains(symbol);
                prices.add(new DailyPrice(symbol, date, n(10), n(50), n(10), n(40), liquid ? 100 : 5,
                        n(liquid ? 3000 : 100), "synthetic EOD", observed, observed.plusSeconds(1),
                        AdjustmentStatus.UNADJUSTED));
            }
        }
        var reference = new Reference("synthetic-universe-v1", AS_OF, CUTOFF, "synthetic listing master", members);
        return new LiquidUniverseService(policy).evaluate(reference, new CalendarReference(calendar, Instant.EPOCH),
                prices, CUTOFF);
    }

    private static Inputs inputs(List<Event> events) {
        return new Inputs(new Snapshot<>(EVIDENCE, events),
                new Snapshot<>(EVIDENCE, new Momentum(n(102), n(100), n(100))),
                new Snapshot<>(EVIDENCE, new Flow(n(50), n(-50), n(1000))));
    }

    private static Snapshot<Regime> market() {
        return new Snapshot<>(EVIDENCE, new Regime(n(0), n(0)));
    }

    private static FactorScore factor(Assessment row, Factor factor) {
        return row.factors().stream().filter(item -> item.factor() == factor).findFirst().orElseThrow();
    }

    private static Event event(String key) throws IOException {
        return JSON.treeToValue(fixture("/phivealpha/news-scoring/events.json").path(key), Event.class);
    }

    private static JsonNode fixture(String path) throws IOException {
        try (var input = NewsDrivenScoringServiceTest.class.getResourceAsStream(path)) {
            return JSON.readTree(input);
        }
    }

    private static BigDecimal n(long number) {
        return BigDecimal.valueOf(number);
    }
}
