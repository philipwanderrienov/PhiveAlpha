package com.phivealpha.screening;

import com.phivealpha.persistence.IdxDailyIngestionService;
import com.phivealpha.persistence.IdxPersistenceService;
import com.phivealpha.persistence.IdxRecords.DailyPrice;
import com.phivealpha.screening.LiquidUniverse.CalendarReference;
import com.phivealpha.screening.LiquidUniverse.Eligibility;
import com.phivealpha.screening.LiquidUniverse.Evaluation;
import com.phivealpha.screening.LiquidUniverse.Issue;
import com.phivealpha.screening.LiquidUniverse.Member;
import com.phivealpha.screening.LiquidUniverse.Reason;
import com.phivealpha.screening.LiquidUniverse.Reference;
import com.phivealpha.screening.news.NewsDrivenScoringService;
import com.phivealpha.screening.news.NewsScoring;
import com.phivealpha.screening.news.NewsScoring.Event;
import com.phivealpha.screening.news.NewsScoring.Flow;
import com.phivealpha.screening.news.NewsScoring.Regime;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class StockScreenerService {
    private static final int MOMENTUM_SESSIONS = 11;

    public enum Mode { FIXTURE, DEVELOPMENT_PERSISTENCE }
    public enum Quality { COMPLETE, INCOMPLETE, STALE, UNVERIFIED, UNAVAILABLE }
    public enum Failure { PERSISTENCE_DISABLED, HISTORICAL_STORAGE_UNAVAILABLE }

    public record Factors(NewsScoring.Snapshot<List<Event>> news, NewsScoring.Snapshot<Flow> flow) {}
    public record Context(Reference universe, CalendarReference calendar, Map<String, Factors> factors,
                          NewsScoring.Snapshot<Regime> market) {
        public Context {
            factors = Map.copyOf(factors);
        }
    }

    public record Decision(String symbol, Eligibility status, LiquidUniverse.Result liquidity,
                           NewsScoring.Assessment assessment, Issue momentumIssue) {}

    private record MomentumInput(NewsScoring.Snapshot<NewsScoring.Momentum> snapshot, Issue issue) {}

    public record ScreeningSnapshot(Mode mode, Instant generatedAt, Instant dataAsOf, Instant cutoff,
                                    Quality quality, Failure failure, Evaluation liquidity,
                                    NewsScoring.Report scoring, List<Decision> decisions) {
        public ScreeningSnapshot {
            decisions = List.copyOf(decisions);
        }
    }

    private final LiquidUniverseService liquidity;
    private final NewsDrivenScoringService scoring;
    private final ObjectProvider<IdxDailyIngestionService> daily;

    public StockScreenerService(LiquidUniverseService liquidity, NewsDrivenScoringService scoring,
                                ObjectProvider<IdxDailyIngestionService> daily) {
        this.liquidity = liquidity;
        this.scoring = scoring;
        this.daily = daily;
    }

    public Mono<ScreeningSnapshot> screenPersisted(Context context, Instant cutoff) {
        return Mono.defer(() -> {
            var source = daily.getIfAvailable();
            if (source == null) {
                return Mono.just(unavailable(cutoff, Failure.PERSISTENCE_DISABLED));
            }
            var momentumDates = liquidity.priceWindow(context.calendar(), cutoff, MOMENTUM_SESSIONS);
            var window = momentumDates.size() > liquidity.policy().lookbackSessions()
                    ? momentumDates : liquidity.priceWindow(context.calendar(), cutoff);
            return source.screeningHistory(context.universe(), context.calendar(), window, cutoff).collectList()
                    .map(prices -> screen(Mode.DEVELOPMENT_PERSISTENCE, context, prices, cutoff, momentumDates))
                    .onErrorResume(IdxPersistenceService.PersistenceException.class,
                            error -> Mono.just(unavailable(cutoff, Failure.HISTORICAL_STORAGE_UNAVAILABLE)));
        });
    }

    public ScreeningSnapshot screenFixture(Context context, List<DailyPrice> prices, Instant cutoff) {
        return screen(Mode.FIXTURE, context, prices, cutoff,
                liquidity.priceWindow(context.calendar(), cutoff, MOMENTUM_SESSIONS));
    }

    private ScreeningSnapshot screen(Mode mode, Context context, List<DailyPrice> prices, Instant cutoff,
                                     List<LocalDate> momentumDates) {
        var result = evaluate(context, prices, cutoff, momentumDates);
        return new ScreeningSnapshot(mode, Instant.now(), result.dataAsOf(), cutoff, result.quality(),
                null, result.liquidity(), result.scoring(), result.decisions());
    }

    record EvaluationAtCutoff(Instant dataAsOf, Quality quality, Evaluation liquidity,
                              NewsScoring.Report scoring, List<Decision> decisions) {
        EvaluationAtCutoff {
            decisions = List.copyOf(decisions);
        }
    }

    int historicalMinimumSessions() {
        return Math.max(MOMENTUM_SESSIONS, Math.max(liquidity.policy().lookbackSessions(),
                liquidity.policy().minListingSessions()));
    }

    EvaluationAtCutoff evaluate(Context context, List<DailyPrice> prices, Instant cutoff) {
        return evaluate(context, prices, cutoff, liquidity.priceWindow(context.calendar(), cutoff, MOMENTUM_SESSIONS));
    }

    private EvaluationAtCutoff evaluate(Context context, List<DailyPrice> prices, Instant cutoff,
                                        List<LocalDate> momentumDates) {
        var visible = prices.stream().filter(price -> visibleAt(price, cutoff)).toList();
        var evaluation = liquidity.evaluate(context.universe(), context.calendar(), visible, cutoff);
        var inputs = new HashMap<String, NewsScoring.Inputs>();
        var momentumIssues = new HashMap<String, Issue>();
        if (evaluation.referenceIssues().isEmpty()) {
            var members = new HashMap<String, Member>();
            context.universe().members().forEach(member -> members.put(member.symbol(), member));
            var bySymbol = visible.stream().collect(Collectors.groupingBy(
                    price -> LiquidUniverse.normalizeSymbol(price.symbol())));
            for (var result : evaluation.results()) {
                if (result.eligibility() != Eligibility.ELIGIBLE) {
                    continue;
                }
                var factors = context.factors().get(result.symbol());
                var momentum = momentum(context, members.get(result.symbol()),
                        bySymbol.getOrDefault(result.symbol(), List.of()), momentumDates);
                if (momentum.issue() != null) {
                    momentumIssues.put(result.symbol(), momentum.issue());
                }
                inputs.put(result.symbol(), new NewsScoring.Inputs(factors == null ? null : factors.news(),
                        momentum.snapshot(), factors == null ? null : factors.flow()));
            }
        }
        var report = scoring.score(evaluation, inputs, context.market(), cutoff);
        var assessments = report.assessments().stream().collect(Collectors.toMap(
                NewsScoring.Assessment::symbol, Function.identity()));
        var decisions = evaluation.results().stream().map(row -> {
            var assessment = assessments.get(row.symbol());
            var status = row.eligibility() == Eligibility.ELIGIBLE
                    && (assessment == null || assessment.status() != NewsScoring.Status.SCOREABLE)
                    ? Eligibility.UNVERIFIED : row.eligibility();
            return new Decision(row.symbol(), status, row, assessment, momentumIssues.get(row.symbol()));
        }).toList();
        var asOf = evaluation.results().stream().map(LiquidUniverse.Result::liquidity)
                .filter(Objects::nonNull).map(LiquidUniverse.LiquidityMetrics::latestSessionClose)
                .max(Comparator.naturalOrder()).orElse(null);
        return new EvaluationAtCutoff(asOf, quality(evaluation, decisions), evaluation, report, decisions);
    }

    static boolean visibleAt(DailyPrice price, Instant cutoff) {
        return (price.observedAt() == null || !price.observedAt().toInstant().isAfter(cutoff))
                && (price.ingestedAt() == null || !price.ingestedAt().toInstant().isAfter(cutoff));
    }

    private static MomentumInput momentum(Context context, Member member,
            List<DailyPrice> prices, List<LocalDate> dates) {
        if (dates.size() != MOMENTUM_SESSIONS) {
            return new MomentumInput(null, new Issue(Reason.CALENDAR_OUT_OF_COVERAGE, null));
        }
        var calendar = context.calendar().calendar();
        var rowsByDate = prices.stream().filter(price -> price.tradingDate() != null && dates.contains(price.tradingDate()))
                .collect(Collectors.groupingBy(DailyPrice::tradingDate));
        var sources = new HashSet<String>();
        var byDate = new HashMap<LocalDate, DailyPrice>();
        for (var date : dates) {
            var rows = rowsByDate.getOrDefault(date, List.of());
            if (rows.isEmpty()) {
                return new MomentumInput(null, new Issue(Reason.MISSING_CANDLE, date));
            }
            if (rows.size() != 1) {
                return new MomentumInput(null, new Issue(Reason.DUPLICATE_CANDLE, date));
            }
            var price = rows.getFirst();
            var issue = LiquidUniverseService.candleIssue(price, member.listedOn(),
                    calendar.sessionClose(date).orElseThrow());
            if (issue != null) {
                return new MomentumInput(null, new Issue(issue, date));
            }
            byDate.put(date, price);
            sources.add(price.source());
        }
        if (sources.size() != 1) {
            return new MomentumInput(null, new Issue(Reason.MIXED_PRICE_SOURCE, null));
        }
        var latest = byDate.get(dates.getFirst());
        var available = byDate.values().stream().map(price -> price.ingestedAt().toInstant())
                .max(Comparator.naturalOrder()).orElseThrow();
        var evidence = new NewsScoring.Evidence(latest.source(),
                context.universe().version() + "/" + calendar.version(),
                calendar.sessionClose(latest.tradingDate()).orElseThrow(), available, NewsScoring.Quality.AVAILABLE);
        var momentum = new NewsScoring.Momentum(latest.close(), byDate.get(dates.get(5)).close(),
                byDate.get(dates.get(10)).close());
        return new MomentumInput(new NewsScoring.Snapshot<>(evidence, momentum), null);
    }

    private static Quality quality(Evaluation evaluation, List<Decision> decisions) {
        if (!evaluation.referenceIssues().isEmpty()) {
            return evaluation.referenceIssues().stream().anyMatch(issue ->
                    issue.reason() == LiquidUniverse.Reason.STALE_UNIVERSE_REFERENCE) ? Quality.STALE : Quality.UNVERIFIED;
        }
        return decisions.stream().map(StockScreenerService::decisionQuality)
                .max(Comparator.naturalOrder()).orElse(Quality.COMPLETE);
    }

    private static Quality decisionQuality(Decision decision) {
        if (decision.assessment() != null) {
            return switch (decision.assessment().dataQuality()) {
                case AVAILABLE -> Quality.COMPLETE;
                case INCOMPLETE, UNAVAILABLE -> Quality.INCOMPLETE;
                case STALE -> Quality.STALE;
                case UNVERIFIED -> Quality.UNVERIFIED;
            };
        }
        return switch (decision.liquidity().dataQuality()) {
            case COMPLETE, NOT_EVALUATED -> Quality.COMPLETE;
            case INCOMPLETE -> Quality.INCOMPLETE;
            case STALE -> Quality.STALE;
            case UNVERIFIED -> Quality.UNVERIFIED;
        };
    }

    private static ScreeningSnapshot unavailable(Instant cutoff, Failure failure) {
        return new ScreeningSnapshot(Mode.DEVELOPMENT_PERSISTENCE, Instant.now(), null, cutoff,
                Quality.UNAVAILABLE, failure, null, null, List.of());
    }
}
