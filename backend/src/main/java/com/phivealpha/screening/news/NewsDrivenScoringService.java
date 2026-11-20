package com.phivealpha.screening.news;

import static com.phivealpha.screening.news.NewsScoring.*;
import static com.phivealpha.screening.news.NewsScoring.Factor.*;
import static com.phivealpha.screening.news.NewsScoring.Quality.*;
import static com.phivealpha.screening.news.NewsScoring.Reason.*;

import com.phivealpha.screening.LiquidUniverse;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;
import org.springframework.stereotype.Service;

@Service
public class NewsDrivenScoringService {
    private static final int MAX_CANDIDATES = 5;
    private static final String MODEL_VERSION = "fixture-news-v1";
    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal HUNDRED = decimal(100);
    private static final BigDecimal NEUTRAL = decimal(50);
    private static final BigDecimal NEGATIVE_IMPACT_THRESHOLD = new BigDecimal("-0.25");
    private static final BigDecimal ELEVATED_RISK_THRESHOLD = new BigDecimal("0.70");
    private final NewsScoringPolicy policy;

    public NewsDrivenScoringService(NewsScoringPolicy policy) {
        this.policy = Objects.requireNonNull(policy);
    }

    public Report score(LiquidUniverse.Evaluation universe, Map<String, Inputs> inputBySymbol,
                        Snapshot<Regime> market, Instant cutoff) {
        Objects.requireNonNull(cutoff);
        if (!cutoff.equals(universe.asOf()) || !universe.referenceIssues().isEmpty()
                || !visible(universe.universeAvailableAt(), cutoff) || !visible(universe.universeAsOf(), cutoff)
                || !visible(universe.calendarAvailableAt(), cutoff) || !present(universe.universeVersion())
                || !present(universe.calendarVersion()) || !present(universe.universeSource())
                || !present(universe.calendarSource())) {
            return new Report("RESEARCH_ONLY", MODEL_VERSION, cutoff, policy, List.of(), List.of(),
                    List.of(UNIVERSE_UNVERIFIED));
        }

        var regime = compute(MARKET_REGIME_RISK, market, cutoff, NewsDrivenScoringService::regime);
        var assessments = universe.results().stream().filter(result -> eligible(result, universe))
                .sorted(Comparator.comparing(LiquidUniverse.Result::symbol))
                .map(result -> assess(result, universe, inputBySymbol.get(result.symbol()), regime, cutoff))
                .toList();
        var selected = assessments.stream().filter(row -> row.status() == Status.SCOREABLE)
                .sorted(Comparator.comparing(Assessment::overallScore).reversed().thenComparing(Assessment::symbol))
                .limit(MAX_CANDIDATES).toList();
        var ranking = new ArrayList<RankedCandidate>();
        for (var assessment : selected) {
            ranking.add(new RankedCandidate(ranking.size() + 1, assessment));
        }
        return new Report("RESEARCH_ONLY", MODEL_VERSION, cutoff, policy, ranking, assessments, List.of());
    }

    private static boolean eligible(LiquidUniverse.Result result, LiquidUniverse.Evaluation universe) {
        var completeness = result.completeness();
        return result.eligibility() == LiquidUniverse.Eligibility.ELIGIBLE
                && result.dataQuality() == LiquidUniverse.DataQuality.COMPLETE
                && result.rejectionReasons().isEmpty() && result.liquidity() != null
                && completeness != null && completeness.evaluated() && completeness.expectedSessions() > 0
                && completeness.expectedSessions() == completeness.validCandles()
                && universe.asOf().equals(result.asOf()) && universe.universeVersion().equals(result.universeVersion())
                && universe.calendarVersion().equals(result.calendarVersion());
    }

    private Assessment assess(LiquidUniverse.Result result, LiquidUniverse.Evaluation universe, Inputs input,
                              Scored regime, Instant cutoff) {
        var news = compute(NEWS_CATALYST, input == null ? null : input.news(), cutoff,
                events -> news(events, input.news().evidence().asOf(), cutoff));
        var momentum = compute(PRICE_MOMENTUM, input == null ? null : input.momentum(), cutoff,
                NewsDrivenScoringService::momentum);
        var metrics = result.liquidity();
        var liquidityEvidence = new Evidence(metrics.priceSource(),
                universe.universeVersion() + "/" + universe.calendarVersion(),
                metrics.latestSessionClose(), metrics.pricesAvailableAt(), AVAILABLE);
        var liquidity = compute(VOLUME_LIQUIDITY, new Snapshot<>(liquidityEvidence, metrics), cutoff,
                values -> liquidity(values, result.completeness().expectedSessions(),
                        universe.policy().minMedianTurnoverIdr()));
        var flow = compute(FOREIGN_BROKER_FLOW, input == null ? null : input.flow(), cutoff,
                NewsDrivenScoringService::flow);

        var factors = List.of(news.factor(), momentum.factor(), liquidity.factor(), flow.factor(), regime.factor());
        var reasons = factors.stream().map(FactorScore::reason).filter(Objects::nonNull).distinct().toList();
        var quality = factors.stream().map(FactorScore::quality)
                .max(Comparator.comparingInt(NewsDrivenScoringService::qualitySeverity)).orElseThrow();
        var status = switch (quality) {
            case AVAILABLE -> Status.SCOREABLE;
            case UNVERIFIED -> Status.UNVERIFIED;
            case UNAVAILABLE, INCOMPLETE, STALE -> Status.INSUFFICIENT_DATA;
        };
        var overall = status == Status.SCOREABLE
                ? display(factors.stream().map(FactorScore::contribution).reduce(ZERO, BigDecimal::add)) : null;
        var confidence = switch (status) {
            case SCOREABLE -> Confidence.COMPLETE_FIXTURE;
            case INSUFFICIENT_DATA -> Confidence.INSUFFICIENT_DATA;
            case UNVERIFIED -> Confidence.UNVERIFIED;
        };
        var risks = new ArrayList<Risk>();
        if (news.factor().quality() == AVAILABLE
                && news.factor().rawMetrics().get("materialNegativeEvents").signum() > 0) {
            risks.add(Risk.MATERIAL_NEGATIVE_CATALYST);
        }
        var regimeMetrics = regime.factor().rawMetrics();
        if (regime.factor().quality() == AVAILABLE) {
            if (regimeMetrics.get("benchmarkReturnFiveSessionsPct").signum() < 0) {
                risks.add(Risk.BEARISH_MARKET);
            }
            if (regimeMetrics.get("riskLevel").compareTo(ELEVATED_RISK_THRESHOLD) >= 0) {
                risks.add(Risk.ELEVATED_MARKET_RISK);
            }
        }
        var versions = new LinkedHashMap<String, String>();
        versions.put("universe", universe.universeVersion());
        versions.put("calendar", universe.calendarVersion());
        for (var factor : factors) {
            if (factor.evidence() != null) {
                versions.put(factor.factor().name(), factor.evidence().version());
            }
        }
        return new Assessment(result.symbol(), status, overall, factors, news.catalyst(),
                risks, cutoff, versions, confidence, quality, reasons);
    }

    private <T> Scored compute(Factor factor, Snapshot<T> snapshot, Instant cutoff,
                               Function<T, Calculation> calculation) {
        if (snapshot == null) {
            return blocked(factor, null, UNAVAILABLE, SOURCE_MISSING);
        }
        var evidence = snapshot.evidence();
        if (evidence != null && (after(evidence.asOf(), cutoff) || after(evidence.availableAt(), cutoff))) {
            return blocked(factor, null, UNAVAILABLE, NOT_AVAILABLE_AT_CUTOFF);
        }
        if (evidence == null || !present(evidence.source()) || !present(evidence.version())
                || evidence.quality() == null || evidence.asOf() == null || evidence.availableAt() == null
                || evidence.availableAt().isBefore(evidence.asOf())) {
            return blocked(factor, null, UNVERIFIED, INVALID_PROVENANCE);
        }
        if (evidence.quality() != AVAILABLE) {
            return blocked(factor, evidence, evidence.quality(), SOURCE_DECLARED_QUALITY);
        }
        if (Duration.between(evidence.asOf(), cutoff).compareTo(policy.maxSnapshotAge()) > 0) {
            return blocked(factor, evidence, STALE, SOURCE_STALE);
        }
        if (snapshot.value() == null) {
            return blocked(factor, evidence, INCOMPLETE, INVALID_METRICS);
        }
        var result = calculation.apply(snapshot.value());
        if (result.reason() != null) {
            var quality = result.reason() == CONFLICTING_NEWS_EVENT ? UNVERIFIED : INCOMPLETE;
            return blocked(factor, evidence, quality, result.reason());
        }

        var weight = weight(factor);
        var score = display(clamp(result.score(), ZERO, HUNDRED));
        return new Scored(new FactorScore(factor, weight, score, score.multiply(weight).movePointLeft(2),
                result.raw(), evidence, AVAILABLE, result.explanation(), null), result.catalyst());
    }

    private Calculation news(List<Event> events, Instant sourceAsOf, Instant cutoff) {
        var visibleEvents = new ArrayList<Event>();
        for (var event : events) {
            if (event != null && (after(event.publishedAt(), cutoff) || after(event.firstSeenAt(), cutoff)
                    || after(event.availableAt(), cutoff))) {
                continue;
            }
            if (!valid(event) || event.availableAt().isAfter(sourceAsOf)) {
                return invalid(INVALID_NEWS_EVENT);
            }
            visibleEvents.add(event);
        }

        var byEvent = new TreeMap<String, Event>();
        for (var event : visibleEvents) {
            var existing = byEvent.putIfAbsent(event.eventId(), event);
            if (existing != null && !sameEvent(existing, event)) {
                return invalid(CONFLICTING_NEWS_EVENT);
            }
            if (existing != null && EVENT_TIME.compare(event, existing) < 0) {
                byEvent.put(event.eventId(), event);
            }
        }

        var recent = byEvent.values().stream()
                .filter(event -> Duration.between(event.publishedAt(), cutoff).compareTo(policy.newsLookback()) <= 0)
                .toList();
        var selected = recent.stream().filter(event -> impact(event).signum() != 0)
                .sorted(Comparator.<Event, BigDecimal>comparing(event -> impact(event).abs()).reversed()
                        .thenComparing(Event::eventId)).findFirst();
        var raw = new LinkedHashMap<String, BigDecimal>();
        raw.put("uniqueRecentEvents", decimal(recent.size()));
        raw.put("zeroMaterialityEvents", decimal(recent.stream().filter(event -> event.materiality().signum() == 0).count()));
        raw.put("fullyAnticipatedEvents", decimal(recent.stream()
                .filter(event -> event.anticipated().compareTo(ONE) == 0).count()));
        raw.put("materialNegativeEvents", decimal(recent.stream()
                .filter(event -> impact(event).compareTo(NEGATIVE_IMPACT_THRESHOLD) <= 0).count()));
        if (selected.isEmpty()) {
            raw.put("impact", ZERO);
            return new Calculation(NEUTRAL, raw, "Verified recent coverage has no event with nonzero impact.",
                    null, null);
        }

        var event = selected.orElseThrow();
        var impact = impact(event);
        raw.put("materiality", event.materiality().stripTrailingZeros());
        raw.put("relevance", event.relevance().stripTrailingZeros());
        raw.put("novelty", event.novelty().stripTrailingZeros());
        raw.put("sentiment", event.sentiment().stripTrailingZeros());
        raw.put("anticipated", event.anticipated().stripTrailingZeros());
        raw.put("impact", impact);
        var catalyst = new Catalyst(event.eventId(), event.label(), event.publishedAt(), event.firstSeenAt(),
                event.availableAt(), impact);
        return new Calculation(NEUTRAL.add(NEUTRAL.multiply(impact)), raw,
                "Strongest absolute impact from materiality, relevance, novelty, sentiment and surprise.",
                catalyst, null);
    }

    private static final Comparator<Event> EVENT_TIME = Comparator.comparing(Event::availableAt)
            .thenComparing(Event::firstSeenAt).thenComparing(Event::publishedAt).thenComparing(Event::label);

    private static boolean valid(Event event) {
        return event != null && present(event.eventId()) && present(event.label())
                && event.publishedAt() != null && event.firstSeenAt() != null && event.availableAt() != null
                && !event.firstSeenAt().isBefore(event.publishedAt())
                && !event.availableAt().isBefore(event.firstSeenAt())
                && fraction(event.materiality()) && fraction(event.relevance()) && fraction(event.novelty())
                && event.sentiment() != null && event.sentiment().abs().compareTo(ONE) <= 0
                && fraction(event.anticipated());
    }

    private static boolean sameEvent(Event first, Event other) {
        return first.materiality().compareTo(other.materiality()) == 0
                && first.relevance().compareTo(other.relevance()) == 0
                && first.novelty().compareTo(other.novelty()) == 0
                && first.sentiment().compareTo(other.sentiment()) == 0
                && first.anticipated().compareTo(other.anticipated()) == 0;
    }

    private static BigDecimal impact(Event event) {
        return event.sentiment().multiply(event.materiality()).multiply(event.relevance())
                .multiply(event.novelty()).multiply(ONE.subtract(event.anticipated())).stripTrailingZeros();
    }

    private static Calculation momentum(Momentum value) {
        if (!positive(value.latestClose()) || !positive(value.closeFiveSessionsEarlier())
                || !positive(value.closeTenSessionsEarlier())) {
            return invalid(INVALID_METRICS);
        }
        var five = pctReturn(value.latestClose(), value.closeFiveSessionsEarlier());
        var ten = pctReturn(value.latestClose(), value.closeTenSessionsEarlier());
        return calculated(NEUTRAL.add(five.multiply(decimal(5))).add(ten.multiply(decimal(2))),
                Map.of("latestClose", value.latestClose(), "closeFiveSessionsEarlier", value.closeFiveSessionsEarlier(),
                        "closeTenSessionsEarlier", value.closeTenSessionsEarlier(), "returnFiveSessionsPct", five,
                        "returnTenSessionsPct", ten), "Five- and ten-session price returns move the neutral score.");
    }

    private static Calculation liquidity(LiquidUniverse.LiquidityMetrics values, int expected, BigDecimal minimum) {
        if (values.medianDailyTurnoverIdr() == null || values.medianDailyTurnoverIdr().signum() < 0
                || values.latestDailyTurnoverIdr() == null || values.latestDailyTurnoverIdr().signum() < 0
                || values.latestDailyVolumeShares() < 0 || values.activeSessions() < 0
                || values.activeSessions() > expected || values.liquidSessions() < 0
                || values.liquidSessions() > values.activeSessions() || !positive(minimum)) {
            return invalid(INVALID_METRICS);
        }
        var active = divide(decimal(values.activeSessions()), decimal(expected));
        var liquid = divide(decimal(values.liquidSessions()), decimal(expected));
        var medianStrength = divide(values.medianDailyTurnoverIdr(), minimum.multiply(BigDecimal.TWO)).min(ONE);
        var score = medianStrength.multiply(decimal(40)).add(active.multiply(decimal(30)))
                .add(liquid.multiply(decimal(30)));
        return calculated(score, Map.of("medianDailyTurnoverIdr", values.medianDailyTurnoverIdr(),
                "latestDailyTurnoverIdr", values.latestDailyTurnoverIdr(),
                "latestDailyVolumeShares", decimal(values.latestDailyVolumeShares()),
                "activeSessions", decimal(values.activeSessions()), "liquidSessions", decimal(values.liquidSessions()),
                "expectedSessions", decimal(expected), "minimumMedianTurnoverIdr", minimum),
                "Median turnover, active-session ratio and liquid-session ratio from the eligible universe.");
    }

    private static Calculation flow(Flow value) {
        var turnover = value.matchedTurnoverIdr();
        if (!positive(turnover) || value.foreignNetIdr() == null || value.brokerCohortNetIdr() == null
                || value.foreignNetIdr().abs().compareTo(turnover) > 0
                || value.brokerCohortNetIdr().abs().compareTo(turnover) > 0) {
            return invalid(INVALID_METRICS);
        }
        var foreign = divide(value.foreignNetIdr(), turnover);
        var broker = divide(value.brokerCohortNetIdr(), turnover);
        var score = NEUTRAL.add(clamp(foreign.movePointRight(1), ONE.negate(), ONE).multiply(decimal(25)))
                .add(clamp(broker.movePointRight(1), ONE.negate(), ONE).multiply(decimal(25)));
        return calculated(score, Map.of("foreignNetIdr", value.foreignNetIdr(),
                "brokerCohortNetIdr", value.brokerCohortNetIdr(), "matchedTurnoverIdr", turnover,
                "foreignNetRatio", foreign, "brokerNetRatio", broker),
                "Each net-flow ratio moves the neutral score with saturation at 10% of turnover.");
    }

    private static Calculation regime(Regime value) {
        var benchmark = value.benchmarkReturnFiveSessionsPct();
        if (benchmark == null || benchmark.compareTo(decimal(-100)) < 0 || !fraction(value.riskLevel())) {
            return invalid(INVALID_METRICS);
        }
        return calculated(NEUTRAL.add(benchmark.multiply(decimal(10))).subtract(value.riskLevel().multiply(decimal(30))),
                Map.of("benchmarkReturnFiveSessionsPct", benchmark, "riskLevel", value.riskLevel()),
                "Benchmark return raises or lowers the score, and the risk assessment subtracts a penalty.");
    }

    private static int qualitySeverity(Quality quality) {
        return switch (quality) {
            case AVAILABLE -> 0;
            case UNAVAILABLE -> 1;
            case STALE -> 2;
            case INCOMPLETE -> 3;
            case UNVERIFIED -> 4;
        };
    }

    private BigDecimal weight(Factor factor) {
        return switch (factor) {
            case NEWS_CATALYST -> policy.newsWeight();
            case PRICE_MOMENTUM -> policy.momentumWeight();
            case VOLUME_LIQUIDITY -> policy.liquidityWeight();
            case FOREIGN_BROKER_FLOW -> policy.flowWeight();
            case MARKET_REGIME_RISK -> policy.regimeWeight();
        };
    }

    private Scored blocked(Factor factor, Evidence evidence, Quality quality, Reason reason) {
        return new Scored(new FactorScore(factor, weight(factor), null, null, Map.of(), evidence, quality,
                "Factor excluded from scoring because its evidence did not pass validation.", reason), null);
    }

    private static Calculation invalid(Reason reason) {
        return new Calculation(null, Map.of(), null, null, reason);
    }

    private static Calculation calculated(BigDecimal score, Map<String, BigDecimal> raw, String explanation) {
        return new Calculation(score, raw, explanation, null, null);
    }

    private static boolean visible(Instant value, Instant cutoff) {
        return value != null && !value.isAfter(cutoff);
    }

    private static boolean after(Instant value, Instant cutoff) {
        return value != null && value.isAfter(cutoff);
    }

    private static boolean present(String text) {
        return text != null && !text.isBlank();
    }

    private static boolean positive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    private static boolean fraction(BigDecimal value) {
        return value != null && value.signum() >= 0 && value.compareTo(ONE) <= 0;
    }

    private static BigDecimal pctReturn(BigDecimal latest, BigDecimal prior) {
        return divide(latest.subtract(prior), prior).movePointRight(2);
    }

    private static BigDecimal divide(BigDecimal numerator, BigDecimal denominator) {
        return numerator.divide(denominator, MathContext.DECIMAL128);
    }

    private static BigDecimal clamp(BigDecimal value, BigDecimal lower, BigDecimal upper) {
        return value.max(lower).min(upper);
    }

    private static BigDecimal display(BigDecimal value) {
        return value.setScale(6, RoundingMode.HALF_UP);
    }

    private static BigDecimal decimal(long value) {
        return BigDecimal.valueOf(value);
    }

    private record Calculation(BigDecimal score, Map<String, BigDecimal> raw, String explanation,
                               Catalyst catalyst, Reason reason) {}

    private record Scored(FactorScore factor, Catalyst catalyst) {}
}
