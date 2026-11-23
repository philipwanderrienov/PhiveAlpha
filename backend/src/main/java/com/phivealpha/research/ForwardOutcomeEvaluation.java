package com.phivealpha.research;

import com.phivealpha.persistence.IdxRecords.AdjustmentStatus;
import com.phivealpha.research.HistoricalReplay.Costs;
import com.phivealpha.research.HistoricalReplay.Observation;
import com.phivealpha.research.HistoricalReplay.Partition;
import com.phivealpha.research.HistoricalReplay.SessionBar;
import com.phivealpha.research.HistoricalReplay.Split;
import com.phivealpha.screening.HistoricalScreening;
import com.phivealpha.screening.LiquidUniverse.CalendarReference;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class ForwardOutcomeEvaluation {
    private ForwardOutcomeEvaluation() {}

    public enum Status { EVALUATED, PARTIAL, NOT_EVALUABLE }
    public enum Reason {
        DUPLICATE_CUTOFF, INVALID_OUTCOME_CALENDAR_OR_SPLIT, INCOMPATIBLE_OUTCOME_PRICE_BASIS,
        OUTSIDE_PARTITIONS, CUTOFF_AFTER_OUTCOME_FREEZE, SCREENING_BLOCKED
    }

    /** The caller attests the basis of each as-traded series in this frozen bundle. */
    public record OutcomeProvenance(String version, String source, CalendarReference calendar, Instant freeze,
                                    AdjustmentStatus equityBasis, AdjustmentStatus benchmarkBasis) {
        public OutcomeProvenance {
            if (Objects.requireNonNull(version).isBlank() || Objects.requireNonNull(source).isBlank()) {
                throw new IllegalArgumentException("Outcome source and version are required");
            }
            Objects.requireNonNull(calendar);
            Objects.requireNonNull(freeze);
            Objects.requireNonNull(equityBasis);
            Objects.requireNonNull(benchmarkBasis);
        }
    }

    public record OutcomeData(OutcomeProvenance provenance, List<SessionBar> bars) {
        public OutcomeData {
            Objects.requireNonNull(provenance);
            bars = List.copyOf(bars);
        }
    }

    /** Descriptive reporting thresholds, fixed by the caller before evaluation. */
    public record CoveragePolicy(int minimumMatchedCutoffs, BigDecimal minimumPairedCoverage) {
        public CoveragePolicy {
            if (minimumMatchedCutoffs < 2 || Objects.requireNonNull(minimumPairedCoverage).signum() <= 0
                    || minimumPairedCoverage.compareTo(BigDecimal.ONE) > 0) {
                throw new IllegalArgumentException("Invalid forward evaluation coverage policy");
            }
        }

        public static CoveragePolicy defaults() {
            return new CoveragePolicy(10, new BigDecimal("0.80"));
        }
    }

    public record CutoffResult(Instant cutoff, Partition partition, HistoricalScreening.Report screening,
                               Reason exclusion) {}

    /** Candidate and IHSG return independently expose their own coverage failures. */
    public record Pair(int rank, Observation candidate, Observation benchmark,
                       BigDecimal grossExcessReturn, BigDecimal netExcessReturn) {}

    public record Summary(Partition partition, int holdingSessions, int attempted, int valid, int rejected,
                          int matchedCutoffs, int overlappingCutoffs, BigDecimal pairedCoverage,
                          boolean sufficientCoverage, BigDecimal meanForwardGross, BigDecimal meanForwardNet,
                          BigDecimal meanBenchmarkGross, BigDecimal meanGrossExcess, BigDecimal meanNetExcess,
                          BigDecimal netExcessHitRate, BigDecimal worstPathDrawdown) {}

    public record Report(String purpose, Status status, OutcomeProvenance outcomeProvenance,
                         Split split, Costs costs, CoveragePolicy coveragePolicy, List<Reason> issues,
                         List<CutoffResult> cutoffs, List<Pair> observations, List<Summary> summaries,
                         List<String> limitations) {
        public Report {
            issues = List.copyOf(issues);
            cutoffs = List.copyOf(cutoffs);
            observations = List.copyOf(observations);
            summaries = List.copyOf(summaries);
            limitations = List.copyOf(limitations);
        }
    }
}
