package com.phivealpha.screening;

import static com.phivealpha.screening.HistoricalScreening.*;

import com.phivealpha.persistence.IdxDailyIngestionService;
import com.phivealpha.persistence.IdxPersistenceService;
import com.phivealpha.persistence.IdxRecords.DailyPrice;
import com.phivealpha.research.HistoricalReplay.Frame;
import com.phivealpha.screening.HistoricalDatasetReadiness.Request;
import com.phivealpha.screening.news.NewsDrivenScoringService;
import com.phivealpha.screening.news.NewsScoring;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/** Opt-in historical screening. The same materialized price view is audited and screened. */
@Service
public final class HistoricalScreeningService {
    private static final String PURPOSE = "RESEARCH_ONLY";
    private final HistoricalDatasetReadinessAudit readiness;
    private final StockScreenerService screener;
    private final LiquidUniverseService liquidity;
    private final ObjectProvider<IdxDailyIngestionService> daily;

    public HistoricalScreeningService(HistoricalDatasetReadinessAudit readiness, StockScreenerService screener,
                                      LiquidUniverseService liquidity, ObjectProvider<IdxDailyIngestionService> daily) {
        this.readiness = readiness;
        this.screener = screener;
        this.liquidity = liquidity;
        this.daily = daily;
    }

    public Report screen(Context context) {
        var frame = context.dataset().frame();
        return evaluate(context, frame == null ? List.of() : frame.dailyPrices());
    }

    public Mono<Report> screenPersisted(Context context) {
        return Mono.defer(() -> {
            var request = context.dataset();
            var frame = request.frame();
            if (!HistoricalDatasetReadinessAudit.vintageIssues(request).isEmpty()) {
                return Mono.just(evaluate(context, List.of()));
            }
            var window = liquidity.priceWindow(frame.calendar(), request.cutoff(), lookback(request));
            if (window.isEmpty()) return Mono.just(evaluate(context, List.of()));
            var storage = daily.getIfAvailable();
            if (storage == null) return Mono.just(storageUnavailable(context, Reason.PERSISTENCE_DISABLED));

            return storage.historicalReadinessHistory(request.symbols(), request.priceBasis(),
                            window.getLast(), window.getFirst(), request.cutoff())
                    .collectList().map(prices -> evaluate(context, prices))
                    .onErrorResume(IdxPersistenceService.PersistenceException.class,
                            error -> Mono.just(storageUnavailable(context, Reason.HISTORICAL_STORAGE_UNAVAILABLE)));
        });
    }

    private Report evaluate(Context context, List<DailyPrice> prices) {
        var request = context.dataset();
        var cutoff = request.cutoff();
        var frame = request.frame();
        var visiblePrices = prices.stream().filter(price -> StockScreenerService.visibleAt(price, cutoff)).toList();
        var view = frame == null ? null : new Frame(frame.version(), frame.asOf(), frame.availableAt(),
                frame.universe(), frame.calendar(), frame.dailyPriceVersion(), visiblePrices, frame.inputs(),
                frame.market(), frame.historicalNewsFirstSeenVerified(), frame.lotSizes());
        var audit = readiness.audit(new Request(request.datasetId(), request.datasetVersion(), request.datasetSource(),
                request.dailyPriceSource(), request.priceBasis(), request.sessionEvidenceSource(), cutoff,
                request.symbols(), view, request.sessionEvidence(), lookback(request)));
        if (audit.outcome() != HistoricalDatasetReadiness.Outcome.READY) {
            return blocked(context, audit, Reason.READINESS_REJECTED);
        }
        if (!NewsDrivenScoringService.modelVersion().equals(context.modelVersion())) {
            return blocked(context, audit, Reason.MODEL_VERSION_MISMATCH);
        }
        if (!frame.historicalNewsFirstSeenVerified()) return blocked(context, audit, Reason.FIRST_SEEN_UNVERIFIED);

        var scoped = new HashSet<>(request.symbols());
        var universe = frame.universe();
        var historicalUniverse = new LiquidUniverse.Reference(universe.version(), universe.asOf(),
                universe.availableAt(), universe.source(),
                universe.members().stream().filter(member -> scoped.contains(member.symbol())).toList());
        var factors = new HashMap<String, StockScreenerService.Factors>();
        var rejections = new ArrayList<Rejection>();
        for (var symbol : request.symbols()) {
            var input = frame.inputs().get(symbol);
            if (input != null) {
                factors.put(symbol, new StockScreenerService.Factors(
                        assembled(input.news(), frame.availableAt(), symbol, NewsScoring.Factor.NEWS_CATALYST, rejections),
                        assembled(input.flow(), frame.availableAt(), symbol, NewsScoring.Factor.FOREIGN_BROKER_FLOW, rejections)));
            }
        }
        var market = assembled(frame.market(), frame.availableAt(), null, NewsScoring.Factor.MARKET_REGIME_RISK, rejections);
        var auditedDates = new HashSet<>(audit.expectedSessions());
        var auditedPrices = visiblePrices.stream().filter(price -> auditedDates.contains(price.tradingDate())
                && scoped.contains(LiquidUniverse.normalizeSymbol(price.symbol()))).toList();
        var result = screener.evaluate(new StockScreenerService.Context(historicalUniverse, frame.calendar(), factors, market),
                auditedPrices, cutoff);
        var status = result.scoring().ranking().isEmpty() ? Status.BLOCKED : Status.RANKED;
        if (status == Status.BLOCKED) rejections.add(new Rejection(Reason.NO_SCOREABLE_CANDIDATES, null, null));
        return new Report(PURPOSE, status, cutoff, context.modelVersion(), audit,
                result.liquidity(), result.scoring(), result.decisions(), rejections);
    }

    private static <T> NewsScoring.Snapshot<T> assembled(NewsScoring.Snapshot<T> snapshot, Instant frameAvailableAt,
            String symbol, NewsScoring.Factor factor, List<Rejection> rejections) {
        if (snapshot != null && snapshot.evidence() != null) {
            var evidence = snapshot.evidence();
            if (after(evidence.asOf(), frameAvailableAt) || after(evidence.availableAt(), frameAvailableAt)) {
                rejections.add(new Rejection(Reason.FACTOR_AFTER_FRAME, symbol, factor));
                return null;
            }
        }
        return snapshot;
    }

    private static boolean after(Instant time, Instant boundary) {
        return time != null && time.isAfter(boundary);
    }

    private int lookback(Request request) {
        return Math.max(request.lookbackSessions(), screener.historicalMinimumSessions());
    }

    private Report storageUnavailable(Context context, Reason reason) {
        var request = context.dataset();
        var auditRequest = new Request(request.datasetId(), request.datasetVersion(), request.datasetSource(),
                request.dailyPriceSource(), request.priceBasis(), request.sessionEvidenceSource(), request.cutoff(),
                request.symbols(), request.frame(), request.sessionEvidence(), lookback(request));
        return blocked(context, HistoricalDatasetReadinessAudit.storageUnavailable(auditRequest), reason);
    }

    private static Report blocked(Context context, HistoricalDatasetReadiness.Report audit, Reason reason) {
        return new Report(PURPOSE, Status.BLOCKED, context.dataset().cutoff(), context.modelVersion(), audit,
                null, null, List.of(), List.of(new Rejection(reason, null, null)));
    }
}
