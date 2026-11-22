package com.phivealpha.screening;

import com.phivealpha.research.HistoricalReplay.Frame;
import com.phivealpha.screening.HistoricalDatasetReadiness.Request;
import com.phivealpha.screening.news.NewsScoring;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public final class HistoricalScreening {
    private HistoricalScreening() {}

    public enum Status { RANKED, BLOCKED }
    public enum Reason {
        READINESS_REJECTED, PERSISTENCE_DISABLED, HISTORICAL_STORAGE_UNAVAILABLE,
        MODEL_VERSION_MISMATCH, FIRST_SEEN_UNVERIFIED, FACTOR_AFTER_FRAME, NO_SCOREABLE_CANDIDATES
    }

    public record Rejection(Reason reason, String symbol, NewsScoring.Factor factor) {}

    /** One explicitly selected historical vintage. Nested news lists are frozen on entry. */
    public record Context(Request dataset, String modelVersion) {
        public Context {
            Objects.requireNonNull(dataset);
            if (Objects.requireNonNull(modelVersion).isBlank()) {
                throw new IllegalArgumentException("Historical screening model version is required");
            }
            var frame = dataset.frame();
            if (frame != null) {
                var factors = frame.inputs().entrySet().stream().collect(Collectors.toMap(
                        entry -> entry.getKey(), entry -> freezeNews(entry.getValue())));
                var frozen = new Frame(frame.version(), frame.asOf(), frame.availableAt(), frame.universe(),
                        frame.calendar(), frame.dailyPriceVersion(), frame.dailyPrices(), factors, frame.market(),
                        frame.historicalNewsFirstSeenVerified(), frame.lotSizes());
                dataset = new Request(dataset.datasetId(), dataset.datasetVersion(), dataset.datasetSource(),
                        dataset.dailyPriceSource(), dataset.priceBasis(), dataset.sessionEvidenceSource(),
                        dataset.cutoff(), dataset.symbols(), frozen, dataset.sessionEvidence(), dataset.lookbackSessions());
            }
        }

        private static NewsScoring.Inputs freezeNews(NewsScoring.Inputs input) {
            var news = input.news();
            if (news == null || news.value() == null) return input;
            var events = Collections.unmodifiableList(new ArrayList<>(news.value()));
            return new NewsScoring.Inputs(new NewsScoring.Snapshot<>(news.evidence(), events),
                    input.momentum(), input.flow());
        }
    }

    public record Report(String purpose, Status status, Instant cutoff, String modelVersion,
                         HistoricalDatasetReadiness.Report readiness, LiquidUniverse.Evaluation liquidity,
                         NewsScoring.Report scoring,
                         List<StockScreenerService.Decision> decisions, List<Rejection> rejections) {
        public Report {
            decisions = List.copyOf(decisions);
            rejections = List.copyOf(rejections);
        }
    }
}
