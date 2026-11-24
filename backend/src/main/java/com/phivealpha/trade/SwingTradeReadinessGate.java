package com.phivealpha.trade;

import com.phivealpha.persistence.IdxRecords.AdjustmentStatus;
import com.phivealpha.screening.HistoricalDatasetReadiness;
import com.phivealpha.screening.HistoricalDatasetReadinessAudit;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Transition guard between audited historical inputs and experimental swing calculations.
 *
 * HistoricalDatasetReadinessAudit currently supports only UNADJUSTED prices, whereas
 * SwingTradeSetup requires ADJUSTED prices. Until an adjusted-history audit with
 * corporate-action evidence exists, no numeric trade levels can be published.
 */
@Service
public final class SwingTradeReadinessGate {
    public enum Status { BLOCKED, READY_FOR_OFFLINE_RESEARCH }
    public enum Reason {
        HISTORICAL_READINESS_NOT_READY, SYMBOL_NOT_AUDITED,
        ADJUSTED_PRICE_AUDIT_UNAVAILABLE, UNVERIFIED_TICK_OR_LOT,
        FRAME_NOT_AVAILABLE_AT_CUTOFF
    }

    public record Result(Status status, Reason reason, String symbol,
                         HistoricalDatasetReadiness.Outcome datasetOutcome,
                         String purpose) {
        public boolean mayCalculate() { return status == Status.READY_FOR_OFFLINE_RESEARCH; }
    }

    private final HistoricalDatasetReadinessAudit audit;

    public SwingTradeReadinessGate(HistoricalDatasetReadinessAudit audit) {
        this.audit = Objects.requireNonNull(audit);
    }

    public Result check(HistoricalDatasetReadiness.Request request, String symbol,
                        boolean tickVerified, boolean lotVerified) {
        Objects.requireNonNull(request);
        Objects.requireNonNull(symbol);
        var frame = request.frame();
        if (frame == null || frame.availableAt().isAfter(request.cutoff())
                || frame.calendar().availableAt().isAfter(request.cutoff())
                || frame.universe().availableAt().isAfter(request.cutoff())) {
            return blocked(symbol, HistoricalDatasetReadiness.Outcome.UNVERIFIED,
                    Reason.FRAME_NOT_AVAILABLE_AT_CUTOFF);
        }
        var report = audit.audit(request);
        if (!request.symbols().contains(symbol)) {
            return blocked(symbol, report.outcome(), Reason.SYMBOL_NOT_AUDITED);
        }
        if (report.outcome() != HistoricalDatasetReadiness.Outcome.READY) {
            return blocked(symbol, report.outcome(), Reason.HISTORICAL_READINESS_NOT_READY);
        }
        if (report.equities().stream().noneMatch(row -> row.symbol().equals(symbol)
                && row.outcome() == HistoricalDatasetReadiness.Outcome.READY
                && row.eligibilityIssue() == null)) {
            return blocked(symbol, report.outcome(), Reason.SYMBOL_NOT_AUDITED);
        }
        // Do not downgrade adjusted/unadjusted without a separate, proven corporate-action audit.
        if (request.priceBasis() != AdjustmentStatus.ADJUSTED) {
            return blocked(symbol, report.outcome(), Reason.ADJUSTED_PRICE_AUDIT_UNAVAILABLE);
        }
        if (!tickVerified || !lotVerified || !frame.lotSizes().containsKey(symbol)
                || frame.lotSizes().get(symbol) == null || frame.lotSizes().get(symbol) <= 0) {
            return blocked(symbol, report.outcome(), Reason.UNVERIFIED_TICK_OR_LOT);
        }
        return new Result(Status.READY_FOR_OFFLINE_RESEARCH, null, symbol,
                report.outcome(), "RESEARCH_ONLY");
    }

    private static Result blocked(String symbol, HistoricalDatasetReadiness.Outcome outcome, Reason reason) {
        return new Result(Status.BLOCKED, reason, symbol, outcome, "RESEARCH_ONLY");
    }
}
