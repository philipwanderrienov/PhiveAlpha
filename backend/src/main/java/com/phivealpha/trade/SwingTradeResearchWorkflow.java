package com.phivealpha.trade;

import com.phivealpha.screening.HistoricalDatasetReadiness;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Safe integration boundary for offline research.
 * No numeric levels are exposed until historical adjusted-price verification,
 * corporate-action safety, execution costs and variable-tick calculation exist.
 */
@Service
public final class SwingTradeResearchWorkflow {
    public enum Status { BLOCKED, NOT_YET_VALIDATED }
    public enum Reason {
        HISTORICAL_INPUT_NOT_READY, EXCHANGE_REFERENCE_NOT_READY,
        ADJUSTED_PRICE_AND_EXECUTION_POLICY_PENDING
    }

    public record Snapshot(String symbol, Instant cutoff, Status status, Reason reason,
                           String detail, HistoricalDatasetReadiness.Outcome historicalOutcome,
                           String ruleVersion, BigDecimal entryTriggerIdr,
                           BigDecimal stopLossIdr, BigDecimal takeProfitIdr,
                           BigDecimal riskReward, String purpose) {
        public boolean hasLevels() {
            return entryTriggerIdr != null || stopLossIdr != null
                    || takeProfitIdr != null || riskReward != null;
        }
    }

    private final SwingTradeReadinessGate readinessGate;

    public SwingTradeResearchWorkflow(SwingTradeReadinessGate readinessGate) {
        this.readinessGate = Objects.requireNonNull(readinessGate);
    }

    public Snapshot assess(HistoricalDatasetReadiness.Request historical,
                           String symbol, SwingTradeMarketRules.Reference rules) {
        Objects.requireNonNull(historical);
        Objects.requireNonNull(symbol);
        var cutoff = historical.cutoff();
        var gate = readinessGate.check(historical, symbol, rules != null, rules != null);
        if (!gate.mayCalculate()) {
            return blocked(symbol, cutoff, Reason.HISTORICAL_INPUT_NOT_READY,
                    gate.reason().name(), gate.datasetOutcome(), null);
        }

        var reference = SwingTradeMarketRules.validateReference(rules, symbol, cutoff);
        if (!reference.valid()) {
            return blocked(symbol, cutoff, Reason.EXCHANGE_REFERENCE_NOT_READY,
                    reference.reason().name(), gate.datasetOutcome(), reference.version());
        }

        var frame = historical.frame();
        if (frame.lotSizes().get(symbol) == null ||
                frame.lotSizes().get(symbol).intValue() != reference.boardLotShares()) {
            return blocked(symbol, cutoff, Reason.EXCHANGE_REFERENCE_NOT_READY,
                    SwingTradeMarketRules.Reason.LOT_SIZE_UNVERIFIED.name(),
                    gate.datasetOutcome(), reference.version());
        }
        // Deliberately no call to SwingTradeSetup until adjusted-price audit and
        // tick-aware per-level computation are both implemented and verified.
        return new Snapshot(symbol, cutoff, Status.NOT_YET_VALIDATED,
                Reason.ADJUSTED_PRICE_AND_EXECUTION_POLICY_PENDING,
                "No validated executable trade setup is available",
                gate.datasetOutcome(), reference.version(), null, null, null, null, "RESEARCH_ONLY");
    }

    private static Snapshot blocked(String symbol, Instant cutoff, Reason reason, String detail,
                                    HistoricalDatasetReadiness.Outcome outcome, String ruleVersion) {
        return new Snapshot(symbol, cutoff, Status.BLOCKED, reason, detail, outcome,
                ruleVersion, null, null, null, null, "RESEARCH_ONLY");
    }
}
