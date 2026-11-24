package com.phivealpha.trade;

import static org.assertj.core.api.Assertions.assertThat;

import com.phivealpha.marketdata.apicoid.VersionedIdxCalendar;
import com.phivealpha.persistence.IdxRecords.AdjustmentStatus;
import com.phivealpha.persistence.IdxRecords.DailyPrice;
import com.phivealpha.research.HistoricalReplay;
import com.phivealpha.screening.HistoricalDatasetReadiness;
import com.phivealpha.screening.HistoricalDatasetReadinessAudit;
import com.phivealpha.screening.LiquidUniverse;
import com.phivealpha.screening.LiquidityPolicy;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class SwingTradeReadinessGateTest {
    private static final String SYMBOL = "SYN";
    private static final LocalDate SESSION = LocalDate.parse("2026-11-20");
    private static final Instant CUTOFF = Instant.parse("2026-11-20T10:00:00Z");
    private static final Instant CLOSE = Instant.parse("2026-11-20T09:00:00Z");
    private static final String SOURCE = "synthetic-verified-daily";
    private final SwingTradeReadinessGate gate = new SwingTradeReadinessGate(
            new HistoricalDatasetReadinessAudit(new LiquidityPolicy(
                    1, 1, 1, BigDecimal.ONE, 1, BigDecimal.ONE, BigDecimal.ONE,
                    1, Duration.ofDays(7), Duration.ofDays(7))));

    @Test
    void missingFrameBlocksWithoutAnyTradeSetup() {
        var request = new HistoricalDatasetReadiness.Request("qa", "v1", "fixture", SOURCE,
                AdjustmentStatus.UNADJUSTED, "session-evidence", CUTOFF, List.of(SYMBOL),
                null, List.of(), 1);
        var result = gate.check(request, SYMBOL, true, true);
        assertThat(result.mayCalculate()).isFalse();
        assertThat(result.reason()).isEqualTo(SwingTradeReadinessGate.Reason.FRAME_NOT_AVAILABLE_AT_CUTOFF);
    }

    @Test
    void futureFrameBlocksBeforeAnyHistoricalDataIsUsed() {
        var request = request(AdjustmentStatus.UNADJUSTED, CUTOFF.plusSeconds(10), true);
        var result = gate.check(request, SYMBOL, true, true);
        assertThat(result.mayCalculate()).isFalse();
        assertThat(result.reason()).isEqualTo(SwingTradeReadinessGate.Reason.FRAME_NOT_AVAILABLE_AT_CUTOFF);
    }

    @Test
    void evenAuditedUnadjustedHistoryCannotFeedAdjustedOnlyTradeEngine() {
        var request = request(AdjustmentStatus.UNADJUSTED, CUTOFF, true);
        var audit = new HistoricalDatasetReadinessAudit(new LiquidityPolicy(
                1, 1, 1, BigDecimal.ONE, 1, BigDecimal.ONE, BigDecimal.ONE,
                1, Duration.ofDays(7), Duration.ofDays(7)));
        assertThat(audit.audit(request).outcome()).isEqualTo(HistoricalDatasetReadiness.Outcome.READY);
        var result = gate.check(request, SYMBOL, true, true);
        assertThat(result.status()).isEqualTo(SwingTradeReadinessGate.Status.BLOCKED);
        assertThat(result.reason()).isEqualTo(SwingTradeReadinessGate.Reason.ADJUSTED_PRICE_AUDIT_UNAVAILABLE);
        assertThat(result.mayCalculate()).isFalse();
    }

    @Test
    void adjustedHistoryIsNotPromotedPastUnsupportedAudit() {
        var request = request(AdjustmentStatus.ADJUSTED, CUTOFF, true);
        var result = gate.check(request, SYMBOL, true, true);
        assertThat(result.status()).isEqualTo(SwingTradeReadinessGate.Status.BLOCKED);
        assertThat(result.reason()).isEqualTo(SwingTradeReadinessGate.Reason.HISTORICAL_READINESS_NOT_READY);
        assertThat(result.mayCalculate()).isFalse();
    }

    @Test
    void missingCandleAndUnscopedSymbolFailClosed() {
        var request = request(AdjustmentStatus.UNADJUSTED, CUTOFF, false);
        var missing = gate.check(request, SYMBOL, true, true);
        assertThat(missing.reason()).isEqualTo(SwingTradeReadinessGate.Reason.HISTORICAL_READINESS_NOT_READY);
        var unscoped = gate.check(request, "OTHER", true, true);
        assertThat(unscoped.reason()).isEqualTo(SwingTradeReadinessGate.Reason.SYMBOL_NOT_AUDITED);
    }

    private static HistoricalDatasetReadiness.Request request(AdjustmentStatus basis,
            Instant frameAvailable, boolean includePrice) {
        var manifest = JsonMapper.builder().build().createObjectNode();
        manifest.put("source", "fixture-calendar").put("version", "v1");
        var day = manifest.putArray("days").addObject().put("date", SESSION.toString())
                .put("kind", "REGULAR");
        day.putArray("sessions").addObject().put("open", "09:00").put("close", "16:00");
        var calendar = new LiquidUniverse.CalendarReference(new VersionedIdxCalendar(manifest), Instant.EPOCH);
        var universe = new LiquidUniverse.Reference("v1", CUTOFF.minusSeconds(60),
                CUTOFF.minusSeconds(59), "fixture-universe",
                List.of(new LiquidUniverse.Member(SYMBOL, LiquidUniverse.SecurityType.COMMON_STOCK,
                        LiquidUniverse.ListingStatus.ACTIVE, SESSION.minusYears(2),
                        LiquidUniverse.CorporateActionStatus.CLEAR)));
        var observed = CLOSE.atOffset(ZoneOffset.UTC);
        var price = new DailyPrice(SYMBOL, SESSION, BigDecimal.TEN, BigDecimal.TEN,
                BigDecimal.TEN, BigDecimal.TEN, 100, BigDecimal.valueOf(1000),
                SOURCE, observed, observed.plusSeconds(1), basis);
        var frame = new HistoricalReplay.Frame("v1", CUTOFF.minusSeconds(60), frameAvailable,
                universe, calendar, "v1", includePrice ? List.of(price) : List.of(),
                Map.of(), null, false, Map.of(SYMBOL, 100));
        var sessions = List.of(
                new HistoricalReplay.SessionBar(SYMBOL, SESSION, CLOSE, "v1",
                        HistoricalReplay.Listing.ACTIVE, HistoricalReplay.CorporateAction.NONE,
                        BigDecimal.TEN, BigDecimal.TEN),
                new HistoricalReplay.SessionBar("IHSG", SESSION, CLOSE, "v1",
                        HistoricalReplay.Listing.ACTIVE, HistoricalReplay.CorporateAction.NONE,
                        BigDecimal.TEN, BigDecimal.TEN));
        return new HistoricalDatasetReadiness.Request("qa", "v1", "fixture", SOURCE, basis,
                "session-evidence", CUTOFF, List.of(SYMBOL), frame, sessions, 1);
    }
}
