package com.phivealpha.screening;

import com.phivealpha.persistence.IdxRecords.AdjustmentStatus;
import com.phivealpha.research.HistoricalReplay.Frame;
import com.phivealpha.research.HistoricalReplay.SessionBar;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public final class HistoricalDatasetReadiness {
    private HistoricalDatasetReadiness() {}

    public static final int DEFAULT_LOOKBACK_SESSIONS = 250;
    public static final String BENCHMARK = "IHSG";

    public enum Outcome { READY, INCOMPLETE, UNVERIFIED, UNAVAILABLE }
    public enum Coverage { COMPLETE, PARTIAL, NONE }
    public enum Freshness { CURRENT, STALE, NOT_ASSESSED }
    public enum Reason {
        FRAME_UNAVAILABLE, FRAME_AFTER_CUTOFF, UNIVERSE_AFTER_FRAME, CALENDAR_AFTER_FRAME,
        CALENDAR_OUT_OF_COVERAGE, STALE_UNIVERSE, UNSUPPORTED_PRICE_BASIS,
        MISSING_HISTORICAL_MEMBER, DUPLICATE_HISTORICAL_MEMBER, INELIGIBLE_HISTORICAL_MEMBER,
        MISSING_CANDLE, DUPLICATE_CANDLE, INVALID_CANDLE_DATE, INVALID_CANDLE_SYMBOL, CANDLE_BEFORE_LISTING, NON_TRADING_CANDLE, SESSION_NOT_CLOSED,
        INVALID_OHLCV, TURNOVER_UNAVAILABLE, OBSERVATION_AFTER_CUTOFF, INGESTION_AFTER_CUTOFF,
        INVALID_PRICE_AVAILABILITY, PRICE_AFTER_FRAME, PRICE_SOURCE_MISMATCH, PRICE_BASIS_MISMATCH, STALE_PRICE,
        SESSION_EVIDENCE_MISSING, DUPLICATE_SESSION_EVIDENCE,
        SESSION_EVIDENCE_AFTER_CUTOFF, SESSION_EVIDENCE_BEFORE_CLOSE, SESSION_EVIDENCE_OUTSIDE_WINDOW,
        SESSION_SUSPENDED, SESSION_DELISTED, SESSION_LISTING_UNVERIFIED, SESSION_CORPORATE_ACTION,
        BENCHMARK_UNAVAILABLE, BENCHMARK_SESSION_MISSING, INVALID_BENCHMARK_PRICE
    }

    public record Issue(Reason reason, LocalDate tradingDate) {}

    /** Caller selects a historical frame, scope and immutable session evidence for the cutoff under review. */
    public record Request(String datasetId, String datasetVersion, String datasetSource,
                          String dailyPriceSource, AdjustmentStatus priceBasis, String sessionEvidenceSource,
                          Instant cutoff, List<String> symbols, Frame frame, List<SessionBar> sessionEvidence,
                          int lookbackSessions) {
        public Request {
            for (var text : List.of(datasetId, datasetVersion, datasetSource, dailyPriceSource, sessionEvidenceSource)) {
                if (text.isBlank()) {
                    throw new IllegalArgumentException("Dataset provenance must be nonblank");
                }
            }
            Objects.requireNonNull(priceBasis);
            Objects.requireNonNull(cutoff);
            symbols = symbols.stream().map(LiquidUniverse::normalizeSymbol).distinct().sorted().toList();
            sessionEvidence = List.copyOf(sessionEvidence);
            if (symbols.isEmpty() || symbols.contains(BENCHMARK) || lookbackSessions < 1) {
                throw new IllegalArgumentException("Specify equity symbols and a positive lookback");
            }
        }

        public Request(String datasetId, String datasetVersion, String datasetSource,
                       String dailyPriceSource, AdjustmentStatus priceBasis, String sessionEvidenceSource,
                       Instant cutoff, List<String> symbols, Frame frame, List<SessionBar> sessionEvidence) {
            this(datasetId, datasetVersion, datasetSource, dailyPriceSource, priceBasis, sessionEvidenceSource,
                    cutoff, symbols, frame, sessionEvidence, DEFAULT_LOOKBACK_SESSIONS);
        }
    }

    public record Provenance(String datasetId, String datasetVersion, String datasetSource,
                             String frameVersion, Instant frameAsOf, Instant frameAvailableAt,
                             String dailyPriceVersion, String dailyPriceSource, AdjustmentStatus priceBasis,
                             String universeVersion, String universeSource, Instant universeAsOf,
                             Instant universeAvailableAt, String calendarVersion, String calendarSource,
                             Instant calendarAvailableAt, String sessionEvidenceSource) {}

    public record Series(String symbol, Outcome outcome, int observedSessions, int validCandles,
                         Coverage coverage, Freshness freshness, LiquidUniverse.Reason eligibilityIssue,
                         List<String> sessionEvidenceVersions, List<Issue> issues) {
        public Series {
            sessionEvidenceVersions = List.copyOf(sessionEvidenceVersions);
            issues = List.copyOf(issues);
        }
    }

    public record Report(Outcome outcome, Provenance provenance, Instant cutoff, int lookbackSessions,
                         List<LocalDate> expectedSessions, List<String> requestedSymbols,
                         List<Series> equities, Series benchmark, List<Issue> issues) {
        public Report {
            expectedSessions = List.copyOf(expectedSessions);
            requestedSymbols = List.copyOf(requestedSymbols);
            equities = List.copyOf(equities);
            issues = List.copyOf(issues);
        }
    }
}
