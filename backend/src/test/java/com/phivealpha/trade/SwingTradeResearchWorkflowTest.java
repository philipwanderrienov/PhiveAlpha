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

class SwingTradeResearchWorkflowTest {
    private static final String SYMBOL = "TEST";
    private static final LocalDate DAY = LocalDate.parse("2026-11-20");
    private static final Instant CUTOFF = Instant.parse("2026-11-20T10:00:00Z");
    private static final Instant CLOSE = Instant.parse("2026-11-20T09:00:00Z");

    private final SwingTradeResearchWorkflow workflow = new SwingTradeResearchWorkflow(
            new SwingTradeReadinessGate(new HistoricalDatasetReadinessAudit(
                    new LiquidityPolicy(1, 1, 1, BigDecimal.ONE, 1, BigDecimal.ONE,
                            BigDecimal.ONE, 1, Duration.ofDays(7), Duration.ofDays(7)))));

    @Test
    void missingFrameWithMissingRulesStillReturnsNoLevels() {
        var outcome = workflow.assess(emptyRequest(), SYMBOL, null);
        assertThat(outcome.status()).isEqualTo(SwingTradeResearchWorkflow.Status.BLOCKED);
        assertThat(outcome.reason()).isEqualTo(
                SwingTradeResearchWorkflow.Reason.HISTORICAL_INPUT_NOT_READY);
        assertThat(outcome.detail()).isEqualTo("FRAME_NOT_AVAILABLE_AT_CUTOFF");
        assertNoLevels(outcome);
    }

    @Test
    void completeUnadjustedHistoryStillBlocksDespiteSyntheticVerifiedRules() {
        var outcome = workflow.assess(completeRequest(), SYMBOL, fixtureRules());
        assertThat(outcome.status()).isEqualTo(SwingTradeResearchWorkflow.Status.BLOCKED);
        assertThat(outcome.detail()).isEqualTo("ADJUSTED_PRICE_AUDIT_UNAVAILABLE");
        assertThat(outcome.historicalOutcome()).isEqualTo(HistoricalDatasetReadiness.Outcome.READY);
        assertNoLevels(outcome);
    }

    @Test
    void missingSymbolIsNotPromotedToTradeSetup() {
        var outcome = workflow.assess(completeRequest(), "OTHER", fixtureRules());
        assertThat(outcome.status()).isEqualTo(SwingTradeResearchWorkflow.Status.BLOCKED);
        assertThat(outcome.detail()).isEqualTo("SYMBOL_NOT_AUDITED");
        assertNoLevels(outcome);
    }

    @Test
    void futureFrameNeverLeaksAPriceLevel() {
        var request = completeRequest();
        var frame = request.frame();
        var future = new HistoricalReplay.Frame(frame.version(), frame.asOf(),
                CUTOFF.plusSeconds(1), frame.universe(), frame.calendar(),
                frame.dailyPriceVersion(), frame.dailyPrices(), frame.inputs(),
                frame.market(), frame.historicalNewsFirstSeenVerified(), frame.lotSizes());
        var altered = new HistoricalDatasetReadiness.Request(request.datasetId(),
                request.datasetVersion(), request.datasetSource(), request.dailyPriceSource(),
                request.priceBasis(), request.sessionEvidenceSource(), request.cutoff(),
                request.symbols(), future, request.sessionEvidence(), request.lookbackSessions());
        var result = workflow.assess(altered, SYMBOL, fixtureRules());
        assertThat(result.detail()).isEqualTo("FRAME_NOT_AVAILABLE_AT_CUTOFF");
        assertNoLevels(result);
    }

    private static void assertNoLevels(SwingTradeResearchWorkflow.Snapshot result) {
        assertThat(result.hasLevels()).isFalse();
        assertThat(result.entryTriggerIdr()).isNull();
        assertThat(result.stopLossIdr()).isNull();
        assertThat(result.takeProfitIdr()).isNull();
        assertThat(result.riskReward()).isNull();
        assertThat(result.purpose()).isEqualTo("RESEARCH_ONLY");
    }

    private static HistoricalDatasetReadiness.Request emptyRequest() {
        return new HistoricalDatasetReadiness.Request("qa", "v1", "fixture", "synthetic-source",
                AdjustmentStatus.UNADJUSTED, "session-evidence", CUTOFF,
                List.of(SYMBOL), null, List.of(), 1);
    }

    private static SwingTradeMarketRules.Reference fixtureRules() {
        return new SwingTradeMarketRules.Reference(SYMBOL, "SYNTHETIC_RULE", "v1",
                CUTOFF.minusSeconds(60), DAY.minusDays(1), null, true, 100,
                List.of(new SwingTradeMarketRules.TickBand(BigDecimal.ONE,
                        null, BigDecimal.ONE)));
    }

    private static HistoricalDatasetReadiness.Request completeRequest() {
        var manifest = JsonMapper.builder().build().createObjectNode();
        manifest.put("source", "synthetic-calendar").put("version", "v1");
        manifest.putArray("days").addObject().put("date", DAY.toString())
                .put("kind", "REGULAR").putArray("sessions")
                .addObject().put("open", "09:00").put("close", "16:00");
        var calendar = new LiquidUniverse.CalendarReference(new VersionedIdxCalendar(manifest), Instant.EPOCH);
        var universe = new LiquidUniverse.Reference("v1", CUTOFF.minusSeconds(60),
                CUTOFF.minusSeconds(59), "synthetic-universe",
                List.of(new LiquidUniverse.Member(SYMBOL, LiquidUniverse.SecurityType.COMMON_STOCK,
                        LiquidUniverse.ListingStatus.ACTIVE, DAY.minusYears(2),
                        LiquidUniverse.CorporateActionStatus.CLEAR)));
        var observation = CLOSE.atOffset(ZoneOffset.UTC);
        var price = new DailyPrice(SYMBOL, DAY, BigDecimal.TEN, BigDecimal.TEN,
                BigDecimal.TEN, BigDecimal.TEN, 100, BigDecimal.valueOf(1000),
                "synthetic-source", observation, observation.plusSeconds(1),
                AdjustmentStatus.UNADJUSTED);
        var frame = new HistoricalReplay.Frame("v1", CUTOFF.minusSeconds(60),
                CUTOFF.minusSeconds(59), universe, calendar, "v1", List.of(price),
                Map.of(), null, false, Map.of(SYMBOL, 100));
        var evidence = List.of(
                new HistoricalReplay.SessionBar(SYMBOL, DAY, CLOSE, "v1",
                        HistoricalReplay.Listing.ACTIVE, HistoricalReplay.CorporateAction.NONE,
                        BigDecimal.TEN, BigDecimal.TEN),
                new HistoricalReplay.SessionBar("IHSG", DAY, CLOSE, "v1",
                        HistoricalReplay.Listing.ACTIVE, HistoricalReplay.CorporateAction.NONE,
                        BigDecimal.TEN, BigDecimal.TEN));
        return new HistoricalDatasetReadiness.Request("qa", "v1", "fixture",
                "synthetic-source", AdjustmentStatus.UNADJUSTED, "session-evidence",
                CUTOFF, List.of(SYMBOL), frame, evidence, 1);
    }
}
