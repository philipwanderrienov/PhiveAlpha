package com.phivealpha.research;

import static com.phivealpha.research.ForwardOutcomeEvaluation.*;
import static com.phivealpha.research.HistoricalReplayService.divide;

import com.phivealpha.persistence.IdxMarketCalendar;
import com.phivealpha.persistence.IdxRecords.AdjustmentStatus;
import com.phivealpha.research.HistoricalReplay.Arm;
import com.phivealpha.research.HistoricalReplay.Costs;
import com.phivealpha.research.HistoricalReplay.Decision;
import com.phivealpha.research.HistoricalReplay.Observation;
import com.phivealpha.research.HistoricalReplay.Partition;
import com.phivealpha.research.HistoricalReplay.Split;
import com.phivealpha.screening.HistoricalDatasetReadiness;
import com.phivealpha.screening.HistoricalScreening;
import com.phivealpha.screening.HistoricalScreeningService;
import com.phivealpha.screening.news.NewsScoring.RankedCandidate;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** Offline fixture evaluation of the exact Phase 1O-B shortlist using Phase 1G simulation. */
@Service
public final class ForwardOutcomeEvaluationService {
    private static final List<String> LIMITATIONS = List.of(
            "SYNTHETIC_FIXTURE_ONLY; NO_EMPIRICAL_PREDICTIVE_OR_PROFITABILITY_CLAIM",
            "PAIRED_COMPLETE_CASES_ONLY; COVERAGE_DOES_NOT_REMOVE_SELECTION_BIAS",
            "CANDIDATES_AT_ONE_CUTOFF_SHARE_MARKET_EXPOSURE; HOLDING_WINDOWS_CAN_OVERLAP",
            "DESCRIPTIVE_METRICS_ONLY; NO_INDEPENDENCE_OR_SIGNIFICANCE_ASSUMPTION",
            "IHSG_IS_GROSS_PRICE_ONLY; NO_DIVIDENDS_OR_BENCHMARK_EXECUTION_COSTS",
            "PATH_DRAWDOWN_IS_PER_POSITION; NO_PORTFOLIO_CASH_OR_EXECUTION_MODEL");

    private final HistoricalScreeningService screening;

    public ForwardOutcomeEvaluationService(HistoricalScreeningService screening) {
        this.screening = screening;
    }

    public Report evaluate(List<HistoricalScreening.Context> contexts, OutcomeData outcomes, Split split,
                           Costs costs, CoveragePolicy policy) {
        Objects.requireNonNull(outcomes);
        Objects.requireNonNull(split);
        Objects.requireNonNull(costs);
        Objects.requireNonNull(policy);
        var ordered = contexts.stream().sorted(Comparator.comparing(row -> row.dataset().cutoff())).toList();
        if (ordered.stream().map(row -> row.dataset().cutoff()).distinct().count() != ordered.size()) {
            return report(outcomes, split, costs, policy, List.of(Reason.DUPLICATE_CUTOFF), List.of(), List.of());
        }

        // Screening receives only the immutable decision contexts. Outcome rows are indexed afterwards.
        var screened = ordered.stream().map(screening::screen).toList();
        var provenance = outcomes.provenance();
        var issues = new ArrayList<Reason>();
        try {
            HistoricalReplayService.validateOutcomeCalendar(provenance.calendar(), provenance.freeze(), split);
        } catch (IllegalArgumentException invalid) {
            issues.add(Reason.INVALID_OUTCOME_CALENDAR_OR_SPLIT);
        }
        if (provenance.equityBasis() != AdjustmentStatus.UNADJUSTED
                || provenance.benchmarkBasis() != provenance.equityBasis()) {
            issues.add(Reason.INCOMPATIBLE_OUTCOME_PRICE_BASIS);
        }

        var index = issues.isEmpty() ? HistoricalReplayService.outcomeIndex(outcomes.bars(), provenance.freeze()) : null;
        var cutoffs = new ArrayList<CutoffResult>();
        var pairs = new ArrayList<Pair>();
        for (int i = 0; i < screened.size(); i++) {
            var result = screened.get(i);
            var cutoff = result.cutoff();
            var partition = HistoricalReplayService.partition(split, cutoff.atZone(IdxMarketCalendar.ZONE).toLocalDate());
            var exclusion = cutoffExclusion(result, partition, provenance.freeze());
            cutoffs.add(new CutoffResult(cutoff, partition, result, exclusion));
            if (index != null && exclusion == null) {
                var frame = ordered.get(i).dataset().frame();
                var vintage = result.readiness().provenance();
                var decision = new Decision(cutoff, partition, vintage.frameVersion(), vintage.frameAsOf(),
                        vintage.frameAvailableAt(), vintage.universeVersion(), vintage.universeSource(),
                        vintage.calendarVersion(), vintage.calendarSource(), vintage.dailyPriceVersion(),
                        result.liquidity().results(), result.scoring(), List.of(), null);
                var observations = new ArrayList<Observation>();
                HistoricalReplayService.evaluateOutcomes(decision, frame, HistoricalReplayService.window(split, partition),
                        provenance.calendar().calendar(), index, costs, provenance.freeze(), observations, true);
                pairs.addAll(pair(result.scoring().ranking(), observations));
            }
        }
        return report(outcomes, split, costs, policy, issues, cutoffs, pairs);
    }

    private static Reason cutoffExclusion(HistoricalScreening.Report screening, Partition partition, Instant freeze) {
        if (partition == null) return Reason.OUTSIDE_PARTITIONS;
        if (screening.cutoff().isAfter(freeze)) return Reason.CUTOFF_AFTER_OUTCOME_FREEZE;
        if (screening.status() != HistoricalScreening.Status.RANKED
                || screening.readiness().outcome() != HistoricalDatasetReadiness.Outcome.READY) {
            return Reason.SCREENING_BLOCKED;
        }
        return null;
    }

    private static List<Pair> pair(List<RankedCandidate> ranking, List<Observation> observations) {
        var ranks = ranking.stream().collect(Collectors.toMap(row -> row.assessment().symbol(), RankedCandidate::rank));
        var benchmarks = observations.stream().filter(row -> row.arm() == Arm.SYNTHETIC_IHSG)
                .collect(Collectors.toMap(Observation::holdingSessions, Function.identity()));
        return observations.stream().filter(row -> row.arm() == Arm.NEWS_SCORING).map(candidate -> {
            var benchmark = benchmarks.get(candidate.holdingSessions());
            boolean matched = candidate.exclusion() == null && benchmark.exclusion() == null;
            return new Pair(ranks.get(candidate.symbol()), candidate, benchmark,
                    matched ? candidate.grossReturn().subtract(benchmark.grossReturn()) : null,
                    matched ? candidate.netReturn().subtract(benchmark.grossReturn()) : null);
        }).toList();
    }

    private static Report report(OutcomeData outcomes, Split split, Costs costs, CoveragePolicy policy,
                                 List<Reason> issues, List<CutoffResult> cutoffs, List<Pair> pairs) {
        var summaries = new ArrayList<Summary>();
        for (var partition : Partition.values()) {
            for (int horizon : HistoricalReplayService.HORIZONS) {
                var rows = pairs.stream().filter(row -> row.candidate().partition() == partition
                        && row.candidate().holdingSessions() == horizon).toList();
                summaries.add(summarize(partition, horizon, rows, policy));
            }
        }
        var status = Status.NOT_EVALUABLE;
        if (summaries.stream().anyMatch(Summary::sufficientCoverage)) {
            boolean partial = summaries.stream().anyMatch(row -> row.rejected() > 0 || !row.sufficientCoverage())
                    || cutoffs.stream().anyMatch(row -> row.exclusion() != null);
            status = partial ? Status.PARTIAL : Status.EVALUATED;
        }
        return new Report("RESEARCH_ONLY_SYNTHETIC", status, outcomes.provenance(), split, costs, policy,
                issues, cutoffs, pairs, summaries, LIMITATIONS);
    }

    private static Summary summarize(Partition partition, int horizon, List<Pair> rows, CoveragePolicy policy) {
        var matched = rows.stream().filter(row -> row.grossExcessReturn() != null).toList();
        var windows = new TreeMap<Instant, Observation>();
        matched.forEach(row -> windows.putIfAbsent(row.candidate().cutoff(), row.candidate()));
        var coverage = rows.isEmpty() ? null : divide(BigDecimal.valueOf(matched.size()), BigDecimal.valueOf(rows.size()));
        boolean sufficient = windows.size() >= policy.minimumMatchedCutoffs()
                && coverage != null && coverage.compareTo(policy.minimumPairedCoverage()) >= 0;
        int overlaps = overlappingCutoffs(windows.values().stream().toList());
        return new Summary(partition, horizon, rows.size(), matched.size(), rows.size() - matched.size(),
                windows.size(), overlaps, coverage, sufficient,
                sufficient ? mean(matched, row -> row.candidate().grossReturn()) : null,
                sufficient ? mean(matched, row -> row.candidate().netReturn()) : null,
                sufficient ? mean(matched, row -> row.benchmark().grossReturn()) : null,
                sufficient ? mean(matched, Pair::grossExcessReturn) : null,
                sufficient ? mean(matched, Pair::netExcessReturn) : null,
                sufficient ? divide(BigDecimal.valueOf(matched.stream().filter(row -> row.netExcessReturn().signum() > 0).count()),
                        BigDecimal.valueOf(matched.size())) : null,
                sufficient ? matched.stream().map(row -> row.candidate().maxPathDrawdown())
                        .max(Comparator.naturalOrder()).orElseThrow() : null);
    }

    private static int overlappingCutoffs(List<Observation> windows) {
        LocalDate latestExit = null;
        int overlaps = 0;
        for (var window : windows) {
            if (latestExit != null && !window.entryDate().isAfter(latestExit)) overlaps++;
            if (latestExit == null || window.exitDate().isAfter(latestExit)) latestExit = window.exitDate();
        }
        return overlaps;
    }

    private static BigDecimal mean(List<Pair> rows, Function<Pair, BigDecimal> field) {
        return divide(rows.stream().map(field).reduce(BigDecimal.ZERO, BigDecimal::add), BigDecimal.valueOf(rows.size()));
    }
}
