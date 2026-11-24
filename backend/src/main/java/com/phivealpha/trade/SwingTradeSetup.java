package com.phivealpha.trade;

import com.phivealpha.persistence.IdxRecords.AdjustmentStatus;
import com.phivealpha.persistence.IdxRecords.DailyPrice;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * Offline, conditional, long-only swing research proposal.
 * Never an order or live buy/sell signal. Policy is experimental and unvalidated on real IDX data.
 */
public final class SwingTradeSetup {
    private SwingTradeSetup() {}

    public enum Status { PROPOSED_RESEARCH_ONLY, NO_SETUP, UNAVAILABLE }
    public enum Reason {
        UNVERIFIED_REFERENCE, NOT_ELIGIBLE, MISSING_SESSION, INVALID_CANDLE,
        DUPLICATE_SESSION, MIXED_SOURCE, FUTURE_OBSERVATION, UNADJUSTED_HISTORY,
        VOLATILITY_UNAVAILABLE, INVALID_LEVELS, PRICE_ALREADY_ABOVE_TRIGGER
    }

    public record Request(String symbol, Instant cutoff, List<LocalDate> sessionsNewestFirst,
                          List<DailyPrice> prices, boolean verifiedReference, boolean eligible,
                          BigDecimal tickSizeIdr, int lotSize) {
        public Request {
            Objects.requireNonNull(symbol);
            Objects.requireNonNull(cutoff);
            sessionsNewestFirst = List.copyOf(sessionsNewestFirst);
            prices = List.copyOf(prices);
        }
    }

    public record Proposal(String symbol, Status status, Reason reason, String policyVersion,
                           LocalDate dataAsOf, String source, BigDecimal entryTriggerIdr,
                           BigDecimal entryZoneLowIdr, BigDecimal entryZoneHighIdr,
                           BigDecimal stopLossIdr, BigDecimal takeProfitIdr,
                           BigDecimal riskReward, int horizonMinSessions, int horizonMaxSessions,
                           String purpose) {
        public boolean hasLevels() { return status == Status.PROPOSED_RESEARCH_ONLY; }
    }

    private static final String VERSION = "swing-research-experimental-v1";
    private static final String PURPOSE = "RESEARCH_ONLY_SYNTHETIC_OR_VERIFIED_OFFLINE";
    private static final int REQUIRED = 21;
    private static final BigDecimal TWO = BigDecimal.valueOf(2);
    private static final BigDecimal MIN_ATR = new BigDecimal("0.000001");

    public static Proposal evaluate(Request request) {
        if (!request.verifiedReference() || request.tickSizeIdr() == null
                || request.tickSizeIdr().signum() <= 0 || request.lotSize() <= 0) {
            return blocked(request, Reason.UNVERIFIED_REFERENCE);
        }
        if (!request.eligible()) return blocked(request, Reason.NOT_ELIGIBLE);
        var dates = request.sessionsNewestFirst();
        if (dates.size() != REQUIRED || new HashSet<>(dates).size() != REQUIRED
                || dates.stream().anyMatch(d -> d == null || d.isAfter(request.cutoff().atZone(java.time.ZoneOffset.UTC).toLocalDate()))) {
            return blocked(request, Reason.MISSING_SESSION);
        }
        for (int i = 1; i < dates.size(); i++) {
            if (!dates.get(i).isBefore(dates.get(i - 1))) return blocked(request, Reason.MISSING_SESSION);
        }
        var byDate = new java.util.HashMap<LocalDate, DailyPrice>();
        String source = null;
        for (var bar : request.prices()) {
            if (!request.symbol().equals(bar.symbol()) || !dates.contains(bar.tradingDate())) continue;
            if (byDate.putIfAbsent(bar.tradingDate(), bar) != null) return blocked(request, Reason.DUPLICATE_SESSION);
            if (bar.source() == null || bar.source().isBlank()) return blocked(request, Reason.MIXED_SOURCE);
            if (source == null) source = bar.source();
            else if (!source.equals(bar.source())) return blocked(request, Reason.MIXED_SOURCE);
            if (bar.observedAt() == null || bar.ingestedAt() == null
                    || bar.observedAt().toInstant().isAfter(request.cutoff())
                    || bar.ingestedAt().toInstant().isAfter(request.cutoff())) {
                return blocked(request, Reason.FUTURE_OBSERVATION);
            }
            if (bar.adjustmentStatus() != AdjustmentStatus.ADJUSTED) {
                return blocked(request, Reason.UNADJUSTED_HISTORY);
            }
            if (!validBar(bar)) return blocked(request, Reason.INVALID_CANDLE);
        }
        if (byDate.size() != REQUIRED) return blocked(request, Reason.MISSING_SESSION);
        var ordered = dates.stream().map(byDate::get).toList();
        BigDecimal sum = BigDecimal.ZERO;
        // ATR(14) uses 14 true ranges, each including the preceding trading session close.
        for (int i = 0; i < 14; i++) {
            var bar = ordered.get(i);
            var prev = ordered.get(i + 1);
            var range = bar.high().subtract(bar.low())
                    .max(bar.high().subtract(prev.close()).abs())
                    .max(bar.low().subtract(prev.close()).abs());
            sum = sum.add(range);
        }
        var atr = sum.divide(BigDecimal.valueOf(14), 10, RoundingMode.HALF_UP);
        if (atr.compareTo(MIN_ATR) < 0) return blocked(request, Reason.VOLATILITY_UNAVAILABLE);

        // Breakout trigger from 20 completed sessions preceding the latest observed session.
        var prior = ordered.subList(1, REQUIRED);
        var resistance = prior.stream().map(DailyPrice::high).max(BigDecimal::compareTo).orElseThrow();
        var support = prior.subList(0, 10).stream().map(DailyPrice::low)
                .min(BigDecimal::compareTo).orElseThrow();
        var tick = request.tickSizeIdr();
        var trigger = roundUp(resistance.add(tick), tick);
        if (ordered.getFirst().close().compareTo(trigger) >= 0) {
            return blocked(request, Reason.PRICE_ALREADY_ABOVE_TRIGGER);
        }
        var stop = roundDown(support.min(trigger.subtract(atr.multiply(TWO))), tick);
        var risk = trigger.subtract(stop);
        if (stop.signum() <= 0 || risk.signum() <= 0) return blocked(request, Reason.INVALID_LEVELS);
        var target = roundUp(trigger.add(risk.multiply(TWO)), tick);
        var rr = target.subtract(trigger).divide(risk, 4, RoundingMode.HALF_UP);
        if (target.compareTo(trigger) <= 0 || rr.compareTo(TWO) < 0) {
            return blocked(request, Reason.INVALID_LEVELS);
        }
        // Zone is a watch condition, not a fill promise; levels are research-only.
        return new Proposal(request.symbol(), Status.PROPOSED_RESEARCH_ONLY, null, VERSION,
                dates.getFirst(), source, trigger, trigger, trigger, stop, target,
                rr, 2, 10, PURPOSE);
    }

    private static boolean validBar(DailyPrice p) {
        return p.open() != null && p.high() != null && p.low() != null && p.close() != null
                && p.volume() > 0 && p.open().signum() > 0 && p.low().signum() > 0
                && p.high().compareTo(p.low()) >= 0
                && p.high().compareTo(p.open()) >= 0 && p.high().compareTo(p.close()) >= 0
                && p.low().compareTo(p.open()) <= 0 && p.low().compareTo(p.close()) <= 0;
    }

    private static BigDecimal roundUp(BigDecimal value, BigDecimal tick) {
        return value.divide(tick, 0, RoundingMode.CEILING).multiply(tick);
    }

    private static BigDecimal roundDown(BigDecimal value, BigDecimal tick) {
        return value.divide(tick, 0, RoundingMode.FLOOR).multiply(tick);
    }

    private static Proposal blocked(Request request, Reason reason) {
        return new Proposal(request.symbol(), Status.UNAVAILABLE, reason, VERSION, null,
                null, null, null, null, null, null, null, 2, 10, PURPOSE);
    }
}
