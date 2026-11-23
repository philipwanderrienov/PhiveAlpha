package com.phivealpha.research;

import static com.phivealpha.research.HistoricalReplay.*;
import static com.phivealpha.research.HistoricalReplay.Reason.*;

import com.phivealpha.marketdata.apicoid.VersionedIdxCalendar;
import com.phivealpha.persistence.IdxMarketCalendar;
import com.phivealpha.screening.LiquidCandidateRanking;
import com.phivealpha.screening.LiquidUniverse;
import com.phivealpha.screening.LiquidUniverseService;
import com.phivealpha.screening.news.NewsDrivenScoringService;
import com.phivealpha.screening.news.NewsScoring;
import com.phivealpha.screening.news.NewsScoringPolicy;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class HistoricalReplayService {
    static final List<Integer> HORIZONS = List.of(2, 5, 10);
    private static final String STRATEGY_VERSION = "synthetic-replay-v1/fixture-news-v1";
    private static final String BENCHMARK_SYMBOL = "IHSG";
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final NewsScoringPolicy FROZEN_SCORING = new NewsScoringPolicy(
            n(30), n(25), n(20), n(15), n(10), Duration.ofDays(7), Duration.ofDays(7));
    private static final List<String> LIMITATIONS = List.of(
            "SYNTHETIC_ONLY",
            "SETTLED_ONLY_RETURNS; UNRESOLVED_POSITIONS_HAVE_NO_VALUATION",
            "INDEPENDENT_POSITIONS; PATH_DRAWDOWN_IS_NOT_PORTFOLIO_DRAWDOWN",
            "IHSG_IS_SYNTHETIC_AND_GROSS_ONLY",
            "NO_ORDER_BOOK, CASH_NETTING OR CORPORATE_ACTION ADJUSTMENTS",
            "PRIOR_MEDIAN_TURNOVER_IS_A_CAPACITY_PROXY");
    private final LiquidUniverseService universeService;
    private final NewsDrivenScoringService scorer = new NewsDrivenScoringService(FROZEN_SCORING);

    public HistoricalReplayService(LiquidUniverseService universeService) {
        this.universeService = universeService;
    }

    public Report replay(Dataset dataset, Split split, Costs costs, List<Instant> cutoffs) {
        var calendar = dataset.outcomeCalendar().calendar();
        validate(dataset, split, cutoffs);
        var outcomes = outcomeIndex(dataset.outcomes(), dataset.outcomeFreeze());
        var frames = dataset.frames().stream().sorted(Comparator.comparing(Frame::asOf)
                .thenComparing(Frame::availableAt).reversed()).toList();
        var decisions = new ArrayList<Decision>();
        var observations = new ArrayList<Observation>();
        for (var cutoff : cutoffs.stream().sorted().toList()) {
            var partition = partition(split, cutoff.atZone(IdxMarketCalendar.ZONE).toLocalDate());
            if (partition == null) {
                decisions.add(blocked(cutoff, null, OUTSIDE_PARTITIONS));
                continue;
            }
            var frame = frames.stream().filter(vintage -> !vintage.asOf().isAfter(cutoff)
                    && !vintage.availableAt().isAfter(cutoff)).findFirst().orElse(null);
            if (frame == null) {
                decisions.add(blocked(cutoff, partition, NO_POINT_IN_TIME_FRAME));
                continue;
            }
            var universe = universeService.evaluate(frame.universe(), frame.calendar(), frame.dailyPrices(), cutoff);
            var decision = decide(frame, universe, partition, cutoff);
            decisions.add(decision);
            if (decision.reason() == null) {
                evaluateOutcomes(decision, frame, window(split, partition), calendar, outcomes, costs,
                        dataset.outcomeFreeze(), observations, false);
            }
        }
        boolean settled = observations.stream().anyMatch(row ->
                row.arm() == Arm.NEWS_SCORING && row.exclusion() == null);
        boolean incomplete = decisions.stream().anyMatch(row -> row.reason() != null)
                || observations.stream().anyMatch(row -> row.exclusion() != null);
        var status = !settled ? Status.NOT_EVALUABLE : incomplete ? Status.PARTIAL : Status.EVALUATED;
        return new Report("RESEARCH_ONLY_SYNTHETIC", status, STRATEGY_VERSION, dataset.version(), dataset.source(),
                dataset.outcomeVersion(), calendar.version(), calendar.source(), dataset.outcomeCalendar().availableAt(),
                dataset.outcomeFreeze(), split,
                costs, universeService.policy(), FROZEN_SCORING, decisions, observations, summarize(observations),
                counts(decisions.stream().map(Decision::reason).toList()), LIMITATIONS);
    }

    private static void validate(Dataset dataset, Split split, List<Instant> cutoffs) {
        validateOutcomeCalendar(dataset.outcomeCalendar(), dataset.outcomeFreeze(), split);
        if (cutoffs.stream().distinct().count() != cutoffs.size()
                || cutoffs.stream().anyMatch(cutoff -> cutoff.isAfter(dataset.outcomeFreeze()))
                || dataset.frames().stream().map(frame -> List.of(frame.asOf(), frame.availableAt())).distinct().count()
                        != dataset.frames().size()) {
            throw new IllegalArgumentException("Ambiguous vintage, repeated cutoff, or cutoff after outcome freeze");
        }
    }

    static void validateOutcomeCalendar(LiquidUniverse.CalendarReference reference, Instant freeze, Split split) {
        var calendar = reference.calendar();
        if (!calendar.covers(split.training().first(), split.outOfSample().last())
                || reference.availableAt().isAfter(freeze)) {
            throw new IllegalArgumentException("Outcome calendar coverage or availability is insufficient");
        }
        if (gap(calendar, split.training(), split.validation()) < split.embargoSessions()
                || gap(calendar, split.validation(), split.outOfSample()) < split.embargoSessions()) {
            throw new IllegalArgumentException("Replay partitions overlap or have insufficient session embargo");
        }
    }

    private static long gap(VersionedIdxCalendar calendar, Window older, Window newer) {
        return older.last().plusDays(1).datesUntil(newer.first()).filter(calendar::isTradingSession).count();
    }

    private Decision decide(Frame frame, LiquidUniverse.Evaluation universe, Partition partition, Instant cutoff) {
        Reason reason = null;
        NewsScoring.Report scores = null;
        List<String> baseline = List.of();
        if (!universe.referenceIssues().isEmpty()) {
            reason = UNIVERSE_UNVERIFIED;
        } else if (!frame.historicalNewsFirstSeenVerified() || missingFirstSeen(frame, universe, cutoff)) {
            reason = FIRST_SEEN_UNVERIFIED;
        } else {
            scores = scorer.score(universe, frame.inputs(), frame.market(), cutoff);
            if (scores.ranking().isEmpty()) {
                reason = NO_SCOREABLE_CANDIDATES;
            } else {
                int count = scores.ranking().size();
                baseline = LiquidCandidateRanking.rank(universe).stream().limit(count)
                        .map(LiquidUniverse.Result::symbol).toList();
            }
        }
        return new Decision(cutoff, partition, frame.version(), frame.asOf(), frame.availableAt(),
                universe.universeVersion(), universe.universeSource(), universe.calendarVersion(), universe.calendarSource(),
                frame.dailyPriceVersion(), universe.results(), scores, baseline, reason);
    }

    private static boolean missingFirstSeen(Frame frame, LiquidUniverse.Evaluation universe, Instant cutoff) {
        return LiquidCandidateRanking.rank(universe).stream().map(row -> frame.inputs().get(row.symbol()))
                .filter(input -> input != null && input.news() != null && input.news().value() != null
                        && input.news().evidence() != null && !after(input.news().evidence().asOf(), cutoff)
                        && !after(input.news().evidence().availableAt(), cutoff))
                .flatMap(input -> input.news().value().stream())
                .anyMatch(event -> event == null || (!after(event.publishedAt(), cutoff)
                        && !after(event.firstSeenAt(), cutoff) && !after(event.availableAt(), cutoff)
                        && (event.firstSeenAt() == null || event.availableAt() == null)));
    }

    private static Decision blocked(Instant cutoff, Partition partition, Reason reason) {
        return new Decision(cutoff, partition, null, null, null, null, null, null,
                null, null, List.of(), null, List.of(), reason);
    }

    private static boolean after(Instant time, Instant cutoff) {
        return time != null && time.isAfter(cutoff);
    }

    static void evaluateOutcomes(Decision decision, Frame frame, Window window, VersionedIdxCalendar realized,
                                         Map<BarKey, List<SessionBar>> outcomes, Costs costs, Instant freeze,
                                         List<Observation> observations, boolean requireMatchedCalendar) {
        var cutoff = decision.cutoff();
        var plannedCalendar = frame.calendar().calendar();
        var planned = firstOpen(plannedCalendar, cutoff, window.last());
        var actual = firstOpen(realized, cutoff, window.last());
        var candidates = decision.scoring().ranking().stream().map(row -> row.assessment().symbol()).toList();
        var liquidity = decision.universe().stream().filter(row -> row.liquidity() != null)
                .collect(Collectors.toMap(LiquidUniverse.Result::symbol, LiquidUniverse.Result::liquidity));
        var arms = Map.of(Arm.NEWS_SCORING, candidates, Arm.LIQUIDITY_BASELINE, decision.baselineSymbols(),
                Arm.SYNTHETIC_IHSG, List.of(BENCHMARK_SYMBOL));
        for (int horizon : HORIZONS) {
            var path = path(planned, actual, realized, window.last(), horizon);
            if (requireMatchedCalendar && path.reason() == null) {
                path = verifyHoldingCalendar(path, plannedCalendar, realized);
            }
            for (var arm : Arm.values()) {
                for (var symbol : arms.get(arm)) {
                    var trade = new Trade(decision, arm, symbol, horizon, path);
                    observations.add(simulate(trade, frame, realized, outcomes, costs, liquidity.get(symbol), freeze));
                }
            }
        }
    }

    private static Open firstOpen(VersionedIdxCalendar calendar, Instant cutoff, LocalDate last) {
        var first = cutoff.atZone(IdxMarketCalendar.ZONE).toLocalDate();
        for (var date = first; !date.isAfter(last); date = date.plusDays(1)) {
            if (!calendar.covers(date, date)) {
                return new Open(date, null, CALENDAR_COVERAGE);
            }
            var open = calendar.sessionOpen(date);
            if (open.isPresent() && open.get().isAfter(cutoff)) {
                return new Open(date, open.get(), null);
            }
        }
        return new Open(null, null, EXIT_BEYOND_PARTITION);
    }

    private static Path path(Open planned, Open actual, VersionedIdxCalendar calendar, LocalDate last,
                             int holding) {
        if (planned.reason() != null) {
            return new Path(null, null, List.of(), planned.date(), planned.reason());
        }
        if (!planned.equals(actual)) {
            return new Path(planned.date(), null, List.of(), planned.date(), ENTRY_CALENDAR_CHANGED);
        }
        var sessions = planned.date().datesUntil(last.plusDays(1)).filter(calendar::isTradingSession)
                .limit(holding).toList();
        if (sessions.size() != holding) {
            return new Path(planned.date(), null, List.of(), last, EXIT_BEYOND_PARTITION);
        }
        var exit = sessions.getLast();
        return new Path(planned.date(), exit, sessions, null, null);
    }

    private static Path verifyHoldingCalendar(Path path, VersionedIdxCalendar planned, VersionedIdxCalendar realized) {
        for (var date : path.entry().datesUntil(path.exit().plusDays(1)).toList()) {
            if (!planned.covers(date, date)) {
                return new Path(path.entry(), path.exit(), path.sessions(), date, CALENDAR_COVERAGE);
            }
            if (!planned.sessionOpen(date).equals(realized.sessionOpen(date))
                    || !planned.sessionClose(date).equals(realized.sessionClose(date))) {
                return new Path(path.entry(), path.exit(), path.sessions(), date, HOLDING_CALENDAR_CHANGED);
            }
        }
        return path;
    }

    private static Observation simulate(Trade trade, Frame frame, VersionedIdxCalendar calendar,
                                        Map<BarKey, List<SessionBar>> outcomes, Costs costs,
                                        LiquidUniverse.LiquidityMetrics liquidity, Instant freeze) {
        var path = trade.path();
        var versions = new ArrayList<String>();
        if (path.reason() != null) {
            return observation(trade, false, null, null, null, null, versions, path.failedAt(), path.reason());
        }

        var entry = bar(trade.symbol(), path.entry(), calendar, outcomes, freeze, MISSING_ENTRY);
        if (entry.row() != null) {
            versions.add(entry.row().version());
        }
        if (entry.reason() != null) {
            return observation(trade, false, null, null, null, null, versions, path.entry(), entry.reason());
        }
        var entryPrice = entry.row().open();
        if (!positive(entryPrice)) {
            return observation(trade, false, null, null, null, null, versions, path.entry(), INVALID_PRICE);
        }
        boolean benchmark = trade.arm() == Arm.SYNTHETIC_IHSG;
        var buy = benchmark ? entryPrice : entryPrice.multiply(ONE.add(friction(costs))).multiply(ONE.add(bps(costs.buyFeeBps())));
        BigDecimal shares = null;
        if (!benchmark) {
            Integer lotSize = frame.lotSizes().get(trade.symbol());
            if (lotSize == null || lotSize <= 0) {
                return observation(trade, false, null, null, null, null, versions, path.entry(), LOT_SIZE_UNVERIFIED);
            }
            var lot = n(lotSize);
            var cap = liquidity.medianDailyTurnoverIdr().multiply(costs.maxMedianParticipation());
            var entryFill = entryPrice.multiply(ONE.add(friction(costs)));
            var lots = costs.capitalIdr().divide(buy.multiply(lot), 0, RoundingMode.FLOOR)
                    .min(cap.divide(entryFill.multiply(lot), 0, RoundingMode.FLOOR));
            shares = lots.multiply(lot);
            if (shares.signum() == 0) {
                return observation(trade, false, null, null, null, null, versions, path.entry(), INSUFFICIENT_CAPACITY);
            }
        }

        var peak = ONE;
        var drawdown = ZERO;
        BigDecimal net = null;
        BigDecimal gross = null;
        for (var day : path.sessions()) {
            var mark = day.equals(path.entry()) ? entry : bar(trade.symbol(), day, calendar, outcomes, freeze,
                    day.equals(path.exit()) ? MISSING_EXIT : MISSING_HOLDING_PRICE);
            if (!day.equals(path.entry()) && mark.row() != null) {
                versions.add(mark.row().version());
            }
            if (mark.reason() != null) {
                return observation(trade, true, shares, null, null, null, versions, day, mark.reason());
            }
            var close = mark.row().close();
            if (!positive(close)) {
                Reason reason = close == null ? (day.equals(path.exit()) ? MISSING_EXIT : MISSING_HOLDING_PRICE)
                        : INVALID_PRICE;
                return observation(trade, true, shares, null, null, null, versions, day, reason);
            }
            var sell = benchmark ? close : close.multiply(ONE.subtract(friction(costs)))
                    .multiply(ONE.subtract(bps(costs.sellFeeBps())));
            var valueRatio = divide(sell, buy);
            peak = peak.max(valueRatio);
            drawdown = drawdown.max(ONE.subtract(divide(valueRatio, peak)));
            gross = divide(close, entryPrice).subtract(ONE);
            net = valueRatio.subtract(ONE);
        }
        return observation(trade, true, shares, gross, benchmark ? null : net, drawdown, versions, null, null);
    }

    private static Bar bar(String symbol, LocalDate date, VersionedIdxCalendar calendar,
                           Map<BarKey, List<SessionBar>> outcomes, Instant freeze, Reason ifMissing) {
        var sessionClose = calendar.sessionClose(date).orElseThrow();
        if (sessionClose.isAfter(freeze)) {
            return new Bar(null, OUTCOME_NOT_MATURE);
        }
        var rows = outcomes.getOrDefault(new BarKey(symbol, date), List.of());
        if (rows.isEmpty()) {
            return new Bar(null, ifMissing);
        }
        var row = rows.getFirst();
        if (row.availableAt().isAfter(freeze) || row.availableAt().isBefore(sessionClose)
                || (rows.size() > 1 && rows.get(1).availableAt().equals(row.availableAt()))) {
            return new Bar(null, OUTCOME_PROVENANCE_UNVERIFIED);
        }
        if (row.listing() != Listing.ACTIVE) {
            return new Bar(row, switch (row.listing()) {
                case SUSPENDED -> SUSPENDED;
                case DELISTED -> DELISTED;
                default -> LISTING_UNVERIFIED;
            });
        }
        return row.action() == CorporateAction.NONE ? new Bar(row, null) : new Bar(row, CORPORATE_ACTION);
    }

    static Map<BarKey, List<SessionBar>> outcomeIndex(List<SessionBar> outcomes, Instant freeze) {
        var rows = new HashMap<BarKey, List<SessionBar>>();
        for (var row : outcomes) {
            if (!row.availableAt().isAfter(freeze)) {
                rows.computeIfAbsent(new BarKey(row.symbol(), row.date()), ignored -> new ArrayList<>()).add(row);
            }
        }
        rows.values().forEach(values -> values.sort(Comparator.comparing(SessionBar::availableAt).reversed()));
        return rows;
    }

    private static Observation observation(Trade trade, boolean entered, BigDecimal shares, BigDecimal gross,
                                           BigDecimal net, BigDecimal drawdown, List<String> versions,
                                           LocalDate failureDay, Reason reason) {
        return new Observation(trade.decision().cutoff(), trade.decision().partition(), trade.arm(), trade.symbol(),
                trade.horizon(), trade.path().entry(), trade.path().exit(), entered, shares, gross, net, drawdown,
                versions, failureDay, reason);
    }

    private static List<Metrics> summarize(List<Observation> observations) {
        var grouped = observations.stream().collect(Collectors.groupingBy(row ->
                new MetricKey(row.partition(), row.arm(), row.holdingSessions())));
        var metrics = new ArrayList<Metrics>();
        for (var partition : Partition.values()) {
            for (var arm : Arm.values()) {
                for (int horizon : HORIZONS) {
                    var key = new MetricKey(partition, arm, horizon);
                    metrics.add(metrics(key, grouped.getOrDefault(key, List.of())));
                }
            }
        }
        return metrics;
    }

    private static Metrics metrics(MetricKey key, List<Observation> rows) {
        var settled = rows.stream().filter(row -> row.exclusion() == null).toList();
        var entered = (int) rows.stream().filter(Observation::entered).count();
        var gross = settled.stream().map(Observation::grossReturn).toList();
        var net = settled.stream().map(Observation::netReturn).toList();
        var evaluated = key.arm() == Arm.SYNTHETIC_IHSG ? gross : net;
        var wins = evaluated.stream().filter(value -> value.signum() > 0).toList();
        var losses = evaluated.stream().filter(value -> value.signum() < 0).toList();
        return new Metrics(key.partition(), key.arm(), key.horizon(), rows.size(), entered, settled.size(),
                entered - settled.size(), rows.size() - settled.size(), ratio(settled.size(), rows.size()),
                mean(gross), key.arm() == Arm.SYNTHETIC_IHSG ? null : mean(net),
                ratio(wins.size(), evaluated.size()), mean(wins), mean(losses),
                settled.stream().map(Observation::maxPathDrawdown).max(Comparator.naturalOrder()).orElse(null),
                counts(rows.stream().map(Observation::exclusion).toList()));
    }

    private static Map<Reason, Long> counts(List<Reason> reasons) {
        return reasons.stream().filter(reason -> reason != null).collect(Collectors.groupingBy(
                Function.identity(), TreeMap::new, Collectors.counting()));
    }

    private static BigDecimal ratio(int numerator, int denominator) {
        return denominator == 0 ? null : divide(n(numerator), n(denominator));
    }

    private static BigDecimal mean(List<BigDecimal> values) {
        return values.isEmpty() ? null : divide(values.stream().reduce(ZERO, BigDecimal::add), n(values.size()));
    }

    static Partition partition(Split split, LocalDate date) {
        for (var partition : Partition.values()) {
            if (window(split, partition).includes(date)) {
                return partition;
            }
        }
        return null;
    }

    static Window window(Split split, Partition partition) {
        return switch (partition) {
            case TRAIN -> split.training();
            case VALIDATION -> split.validation();
            case OUT_OF_SAMPLE -> split.outOfSample();
        };
    }

    private static BigDecimal friction(Costs costs) {
        return bps(costs.fullSpreadBps().divide(BigDecimal.TWO).add(costs.slippagePerSideBps()));
    }

    private static BigDecimal bps(BigDecimal value) {
        return value.movePointLeft(4);
    }

    private static boolean positive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    static BigDecimal divide(BigDecimal numerator, BigDecimal denominator) {
        return numerator.divide(denominator, MathContext.DECIMAL128);
    }

    private static BigDecimal n(long value) {
        return BigDecimal.valueOf(value);
    }

    record BarKey(String symbol, LocalDate date) {}
    private record MetricKey(Partition partition, Arm arm, int horizon) {}
    private record Open(LocalDate date, Instant instant, Reason reason) {}
    private record Path(LocalDate entry, LocalDate exit, List<LocalDate> sessions, LocalDate failedAt, Reason reason) {}
    private record Trade(Decision decision, Arm arm, String symbol, int horizon, Path path) {}
    private record Bar(SessionBar row, Reason reason) {}
}
