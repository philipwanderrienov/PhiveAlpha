package com.phivealpha.trade;

import com.phivealpha.persistence.IdxRecords.AdjustmentStatus;
import com.phivealpha.persistence.IdxRecords.DailyPrice;
import com.phivealpha.research.HistoricalReplay.CorporateAction;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * Offline comparison of point-in-time raw and adjusted OHLC prices against a
 * separately supplied, independently reviewed corporate-action factor ledger.
 * This audit is deliberately not an authorization to emit trade levels.
 */
public final class SwingTradeAdjustmentLineage {
    private SwingTradeAdjustmentLineage() {}

    public enum Status { VERIFIED_OFFLINE_RESEARCH, BLOCKED }
    public enum Reason {
        INVALID_WINDOW, MISSING_CANDLE, DUPLICATE_CANDLE, MISSING_FACTOR,
        DUPLICATE_FACTOR, FUTURE_EVIDENCE, UNVERIFIED_EVIDENCE, MIXED_SOURCE,
        MISMATCHED_BASIS, INVALID_FACTOR, UNKNOWN_ACTION, PRICE_MISMATCH,
        INVALID_OHLC, INCORRECT_SYMBOL, FUTURE_PRICE
    }

    public record Factor(String symbol, LocalDate tradingDate, BigDecimal priceMultiplier,
                         CorporateAction action, String source, String version,
                         Instant availableAt, boolean independentlyVerified) {}

    public record Request(String symbol, Instant cutoff, List<LocalDate> sessionsNewestFirst,
                          List<DailyPrice> raw, List<DailyPrice> adjusted,
                          List<Factor> factors) {
        public Request {
            Objects.requireNonNull(symbol);
            Objects.requireNonNull(cutoff);
            sessionsNewestFirst = List.copyOf(sessionsNewestFirst);
            raw = List.copyOf(raw);
            adjusted = List.copyOf(adjusted);
            factors = List.copyOf(factors);
        }
    }

    public record Result(Status status, Reason reason, String symbol,
                         int matchedSessions, String evidenceSource,
                         String evidenceVersion, String purpose) {
        public boolean verified() { return status == Status.VERIFIED_OFFLINE_RESEARCH; }
    }

    public static Result audit(Request request) {
        var dates = request.sessionsNewestFirst();
        if (dates.size() != 21 || new HashSet<>(dates).size() != 21) {
            return blocked(request, Reason.INVALID_WINDOW, 0);
        }
        for (int i = 1; i < dates.size(); i++) {
            if (!dates.get(i).isBefore(dates.get(i - 1))) {
                return blocked(request, Reason.INVALID_WINDOW, 0);
            }
        }
        var raw = new HashMap<LocalDate, DailyPrice>();
        var adjusted = new HashMap<LocalDate, DailyPrice>();
        for (var bar : request.raw()) {
            if (!dates.contains(bar.tradingDate()) || !request.symbol().equals(bar.symbol())) {
                return blocked(request, Reason.INCORRECT_SYMBOL, 0);
            }
            if (raw.putIfAbsent(bar.tradingDate(), bar) != null) return blocked(request, Reason.DUPLICATE_CANDLE, 0);
        }
        for (var bar : request.adjusted()) {
            if (!dates.contains(bar.tradingDate()) || !request.symbol().equals(bar.symbol())) {
                return blocked(request, Reason.INCORRECT_SYMBOL, 0);
            }
            if (adjusted.putIfAbsent(bar.tradingDate(), bar) != null) {
                return blocked(request, Reason.DUPLICATE_CANDLE, 0);
            }
        }
        if (raw.size() != 21 || adjusted.size() != 21) return blocked(request, Reason.MISSING_CANDLE, 0);

        var factorMap = new HashMap<LocalDate, Factor>();
        for (var factor : request.factors()) {
            if (!request.symbol().equals(factor.symbol()) || !dates.contains(factor.tradingDate())) {
                return blocked(request, Reason.INCORRECT_SYMBOL, 0);
            }
            if (factorMap.putIfAbsent(factor.tradingDate(), factor) != null) {
                return blocked(request, Reason.DUPLICATE_FACTOR, 0);
            }
        }
        if (factorMap.size() != 21) return blocked(request, Reason.MISSING_FACTOR, 0);

        String factorSource = null;
        String factorVersion = null;
        int matched = 0;
        for (var date : dates) {
            var original = raw.get(date);
            var derived = adjusted.get(date);
            var factor = factorMap.get(date);
            if (!validPrice(original) || !validPrice(derived)) return blocked(request, Reason.INVALID_OHLC, matched);
            if (original.adjustmentStatus() != AdjustmentStatus.UNADJUSTED
                    || derived.adjustmentStatus() != AdjustmentStatus.ADJUSTED) {
                return blocked(request, Reason.MISMATCHED_BASIS, matched);
            }
            if (original.observedAt() == null || original.ingestedAt() == null
                    || derived.observedAt() == null || derived.ingestedAt() == null
                    || original.observedAt().toInstant().isAfter(request.cutoff())
                    || original.ingestedAt().toInstant().isAfter(request.cutoff())
                    || derived.observedAt().toInstant().isAfter(request.cutoff())
                    || derived.ingestedAt().toInstant().isAfter(request.cutoff())) {
                return blocked(request, Reason.FUTURE_PRICE, matched);
            }
            if (factor.availableAt() == null || factor.availableAt().isAfter(request.cutoff())) {
                return blocked(request, Reason.FUTURE_EVIDENCE, matched);
            }
            if (!factor.independentlyVerified() || blank(factor.source()) || blank(factor.version())) {
                return blocked(request, Reason.UNVERIFIED_EVIDENCE, matched);
            }
            if (factorSource == null) {
                factorSource = factor.source();
                factorVersion = factor.version();
            } else if (!factorSource.equals(factor.source()) || !factorVersion.equals(factor.version())) {
                return blocked(request, Reason.MIXED_SOURCE, matched);
            }
            if (factor.priceMultiplier() == null || factor.priceMultiplier().signum() <= 0) {
                return blocked(request, Reason.INVALID_FACTOR, matched);
            }
            if (factor.action() == null || factor.action() == CorporateAction.UNKNOWN) {
                return blocked(request, Reason.UNKNOWN_ACTION, matched);
            }
            if (factor.action() == CorporateAction.NONE
                    && factor.priceMultiplier().compareTo(BigDecimal.ONE) != 0) {
                return blocked(request, Reason.INVALID_FACTOR, matched);
            }
            for (var pair : new BigDecimal[][] {
                    {original.open(), derived.open()}, {original.high(), derived.high()},
                    {original.low(), derived.low()}, {original.close(), derived.close()} }) {
                // Exact arithmetic is intentional: normalization/precision policy must
                // be established and tested before rounded provider prices can pass.
                if (pair[0].multiply(factor.priceMultiplier()).compareTo(pair[1]) != 0) {
                    return blocked(request, Reason.PRICE_MISMATCH, matched);
                }
            }
            matched++;
        }
        return new Result(Status.VERIFIED_OFFLINE_RESEARCH, null, request.symbol(),
                matched, factorSource, factorVersion, "RESEARCH_ONLY_NOT_TRADE_READY");
    }

    private static boolean validPrice(DailyPrice bar) {
        return bar.open() != null && bar.high() != null && bar.low() != null
                && bar.close() != null && bar.open().signum() > 0 && bar.low().signum() > 0
                && bar.high().compareTo(bar.open()) >= 0
                && bar.high().compareTo(bar.close()) >= 0
                && bar.low().compareTo(bar.open()) <= 0
                && bar.low().compareTo(bar.close()) <= 0;
    }

    private static boolean blank(String text) { return text == null || text.isBlank(); }

    private static Result blocked(Request request, Reason reason, int matched) {
        return new Result(Status.BLOCKED, reason, request.symbol(), matched,
                null, null, "RESEARCH_ONLY_NOT_TRADE_READY");
    }
}
