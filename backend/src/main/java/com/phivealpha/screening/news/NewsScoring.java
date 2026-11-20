package com.phivealpha.screening.news;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class NewsScoring {
    private NewsScoring() {}

    public enum Factor { NEWS_CATALYST, PRICE_MOMENTUM, VOLUME_LIQUIDITY, FOREIGN_BROKER_FLOW, MARKET_REGIME_RISK }
    public enum Quality { AVAILABLE, UNAVAILABLE, STALE, INCOMPLETE, UNVERIFIED }
    public enum Status { SCOREABLE, INSUFFICIENT_DATA, UNVERIFIED }
    public enum Confidence { COMPLETE_FIXTURE, INSUFFICIENT_DATA, UNVERIFIED }
    public enum Reason {
        UNIVERSE_UNVERIFIED, SOURCE_MISSING, NOT_AVAILABLE_AT_CUTOFF, SOURCE_STALE, SOURCE_DECLARED_QUALITY,
        INVALID_PROVENANCE, INVALID_METRICS, INVALID_NEWS_EVENT, CONFLICTING_NEWS_EVENT
    }
    public enum Risk { MATERIAL_NEGATIVE_CATALYST, BEARISH_MARKET, ELEVATED_MARKET_RISK }

    public record Evidence(String source, String version, Instant asOf, Instant availableAt, Quality quality) {}

    /** Scoped snapshot for one factor. Absent input can be represented by a null snapshot. */
    public record Snapshot<T>(Evidence evidence, T value) {}

    /** Canonical event key, with fixture annotations scoped to the candidate's issuer. */
    public record Event(String eventId, String label, Instant publishedAt, Instant firstSeenAt, Instant availableAt,
                        BigDecimal materiality, BigDecimal relevance, BigDecimal novelty,
                        BigDecimal sentiment, BigDecimal anticipated) {}

    public record Momentum(BigDecimal latestClose, BigDecimal closeFiveSessionsEarlier,
                           BigDecimal closeTenSessionsEarlier) {}

    public record Flow(BigDecimal foreignNetIdr, BigDecimal brokerCohortNetIdr,
                       BigDecimal matchedTurnoverIdr) {}

    public record Regime(BigDecimal benchmarkReturnFiveSessionsPct, BigDecimal riskLevel) {}

    /** News, prices and flow are scoped by the caller to the map's canonical IDX symbol. */
    public record Inputs(Snapshot<List<Event>> news, Snapshot<Momentum> momentum, Snapshot<Flow> flow) {}

    public record FactorScore(Factor factor, BigDecimal weightPct, BigDecimal score, BigDecimal contribution,
                              Map<String, BigDecimal> rawMetrics, Evidence evidence, Quality quality,
                              String explanation, Reason reason) {
        public FactorScore {
            rawMetrics = Map.copyOf(rawMetrics);
        }
    }

    public record Catalyst(String eventId, String label, Instant publishedAt, Instant firstSeenAt,
                           Instant availableAt, BigDecimal impact) {}

    public record Assessment(String symbol, Status status, BigDecimal overallScore, List<FactorScore> factors,
                             Catalyst catalystSummary, List<Risk> riskNotes, Instant asOf,
                             Map<String, String> sourceVersions, Confidence confidence, Quality dataQuality, List<Reason> reasons) {
        public Assessment {
            factors = List.copyOf(factors);
            riskNotes = List.copyOf(riskNotes);
            sourceVersions = Map.copyOf(sourceVersions);
            reasons = List.copyOf(reasons);
        }
    }

    public record RankedCandidate(int rank, Assessment assessment) {}

    public record Report(String purpose, String modelVersion, Instant asOf, NewsScoringPolicy policy,
                         List<RankedCandidate> ranking, List<Assessment> assessments, List<Reason> reasons) {
        public Report {
            ranking = List.copyOf(ranking);
            assessments = List.copyOf(assessments);
            reasons = List.copyOf(reasons);
        }
    }
}
