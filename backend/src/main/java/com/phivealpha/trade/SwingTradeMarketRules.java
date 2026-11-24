package com.phivealpha.trade;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Validates exchange-provided, point-in-time price step / board-lot references.
 * Does not contain hard-coded IDX tick bands: an independently verified historical
 * source must supply the reference, with its publication and validity interval.
 */
public final class SwingTradeMarketRules {
    private SwingTradeMarketRules() {}

    private static final ZoneId JAKARTA = ZoneId.of("Asia/Jakarta");

    public enum Reason {
        REFERENCE_MISSING, REFERENCE_UNVERIFIED, REFERENCE_AFTER_CUTOFF,
        OUTSIDE_EFFECTIVE_DATES, SYMBOL_MISMATCH, LOT_SIZE_UNVERIFIED,
        INVALID_TICK_BANDS, PRICE_OUTSIDE_TICK_BANDS, PRICE_NOT_ON_TICK
    }

    public record TickBand(BigDecimal fromInclusiveIdr, BigDecimal toExclusiveIdr,
                           BigDecimal tickSizeIdr) {}

    public record Reference(String symbol, String source, String version,
                            Instant availableAt, LocalDate effectiveFrom, LocalDate effectiveUntil,
                            boolean independentlyVerified, int boardLotShares,
                            List<TickBand> bands) {
        public Reference {
            bands = bands == null ? List.of() : List.copyOf(bands);
        }
    }

    public record Assessment(boolean valid, Reason reason, String source, String version,
                             int boardLotShares, LocalDate effectiveDate) {}

    public static Assessment validate(Reference ref, String symbol, Instant cutoff,
                                      BigDecimal entry, BigDecimal stop, BigDecimal target) {
        Objects.requireNonNull(cutoff);
        Objects.requireNonNull(symbol);
        if (ref == null) return rejected(Reason.REFERENCE_MISSING, null, cutoff);
        if (!ref.independentlyVerified() || blank(ref.source()) || blank(ref.version())
                || ref.effectiveFrom() == null || ref.availableAt() == null) {
            return rejected(Reason.REFERENCE_UNVERIFIED, ref, cutoff);
        }
        if (ref.availableAt().isAfter(cutoff)) {
            return rejected(Reason.REFERENCE_AFTER_CUTOFF, ref, cutoff);
        }
        var date = cutoff.atZone(JAKARTA).toLocalDate();
        if (date.isBefore(ref.effectiveFrom())
                || (ref.effectiveUntil() != null && date.isAfter(ref.effectiveUntil()))) {
            return rejected(Reason.OUTSIDE_EFFECTIVE_DATES, ref, cutoff);
        }
        if (!symbol.equals(ref.symbol())) return rejected(Reason.SYMBOL_MISMATCH, ref, cutoff);
        if (ref.boardLotShares() <= 0) return rejected(Reason.LOT_SIZE_UNVERIFIED, ref, cutoff);
        if (!validBands(ref.bands())) return rejected(Reason.INVALID_TICK_BANDS, ref, cutoff);
        for (var price : new BigDecimal[] { entry, stop, target }) {
            if (price == null || price.signum() <= 0) {
                return rejected(Reason.PRICE_OUTSIDE_TICK_BANDS, ref, cutoff);
            }
            var band = ref.bands().stream().filter(b -> price.compareTo(b.fromInclusiveIdr()) >= 0
                    && (b.toExclusiveIdr() == null || price.compareTo(b.toExclusiveIdr()) < 0))
                    .findFirst().orElse(null);
            if (band == null) return rejected(Reason.PRICE_OUTSIDE_TICK_BANDS, ref, cutoff);
            if (price.remainder(band.tickSizeIdr()).signum() != 0) {
                return rejected(Reason.PRICE_NOT_ON_TICK, ref, cutoff);
            }
        }
        return new Assessment(true, null, ref.source(), ref.version(), ref.boardLotShares(), date);
    }

    private static boolean validBands(List<TickBand> bands) {
        if (bands.isEmpty()) return false;
        var ordered = bands.stream().sorted(Comparator.comparing(TickBand::fromInclusiveIdr,
                Comparator.nullsFirst(Comparator.naturalOrder()))).toList();
        BigDecimal nextFrom = null;
        for (int i = 0; i < ordered.size(); i++) {
            var b = ordered.get(i);
            if (b.fromInclusiveIdr() == null || b.fromInclusiveIdr().signum() < 0
                    || b.tickSizeIdr() == null || b.tickSizeIdr().signum() <= 0
                    || (b.toExclusiveIdr() != null
                            && b.toExclusiveIdr().compareTo(b.fromInclusiveIdr()) <= 0)) return false;
            if (nextFrom != null && b.fromInclusiveIdr().compareTo(nextFrom) < 0) return false;
            if (i > 0 && ordered.get(i - 1).toExclusiveIdr() == null) return false;
            nextFrom = b.toExclusiveIdr();
        }
        return true;
    }

    private static boolean blank(String text) { return text == null || text.isBlank(); }

    private static Assessment rejected(Reason reason, Reference ref, Instant cutoff) {
        return new Assessment(false, reason, ref == null ? null : ref.source(),
                ref == null ? null : ref.version(), 0,
                cutoff.atZone(JAKARTA).toLocalDate());
    }
}
