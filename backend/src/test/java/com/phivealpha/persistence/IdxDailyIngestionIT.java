package com.phivealpha.persistence;

import static com.phivealpha.persistence.IdxDailyIngestionService.WriteStatus.*;
import static com.phivealpha.persistence.IdxDailyIngestionTest.*;
import static com.phivealpha.persistence.IdxRecords.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.phivealpha.PhiveAlphaApplication;
import com.phivealpha.marketdata.apicoid.ApiCoIdData.Quality;
import com.phivealpha.screening.LiquidUniverse.DataQuality;
import com.phivealpha.screening.LiquidUniverse.Eligibility;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@EnabledIfEnvironmentVariable(named = "PHIVEALPHA_IT_DATA_DIR", matches = "/tmp/phivealpha-it\\.[A-Za-z0-9]+/data")
@SpringBootTest(classes = PhiveAlphaApplication.class, useMainMethod = SpringBootTest.UseMainMethod.ALWAYS)
@ActiveProfiles({"phivealpha-persistence", "phivealpha-daily-ingestion"})
class IdxDailyIngestionIT {
    private static final String DATABASE = "phivealpha_dev_daily_it";
    private static final Duration WAIT = Duration.ofSeconds(15);

    @Autowired IdxPersistenceService storage;
    @Autowired R2dbcEntityTemplate template;
    @Autowired IdxDailyIngestionService guardedService;

    @DynamicPropertySource
    static void isolatedConnection(DynamicPropertyRegistry registry) {
        String url = System.getenv("PHIVEALPHA_R2DBC_URL");
        assertThat(url).matches("r2dbc:postgresql://127\\.0\\.0\\.1:[0-9]+/" + DATABASE);
        assertThat(System.getenv("PHIVEALPHA_R2DBC_USERNAME")).isEqualTo(DATABASE);
        registry.add("phivealpha.persistence.url", () -> url);
        registry.add("phivealpha.persistence.username", () -> DATABASE);
        registry.add("phivealpha.persistence.password", () -> System.getenv("PHIVEALPHA_R2DBC_PASSWORD"));
    }

    @Test
    void retriesPartialSqlFailureWithoutDuplicatesAndScreensReopenedHistoricalData() {
        assertThat(guardedService).isNotNull();
        var identity = Objects.requireNonNull(template.getDatabaseClient().sql("""
                SELECT current_setting('data_directory'), current_database(), current_user,
                       current_setting('server_version_num')
                """).map((row, metadata) -> List.of(row.get(0, String.class), row.get(1, String.class),
                        row.get(2, String.class), row.get(3, String.class))).one().block(WAIT));
        assertThat(identity.subList(0, 3)).containsExactly(System.getenv("PHIVEALPHA_IT_DATA_DIR"), DATABASE, DATABASE);
        assertThat(identity.getLast()).matches("17[0-9]{4}");
        assertThat(storage.upsertInstrument(new Instrument("BBCA", "Fixture Bank", "Finance", "STOCK",
                "LISTED", "IDR", 100, NOW.atOffset(ZoneOffset.UTC).withNano(0))).block(WAIT)).isTrue();

        sql("ALTER TABLE phivealpha_idx.idx_daily_prices ADD CONSTRAINT sit_daily_failure CHECK (volume <> 450000)");
        var pipeline = service(storage, NOW);
        var partial = ingest(pipeline);
        assertThat(partial.quality()).isEqualTo(Quality.INCOMPLETE);
        assertThat(partial.reason()).isEqualTo("STORAGE_FAILURE");
        assertThat(partial.auditRecorded()).isTrue();
        assertThat(partial.candles()).extracting(IdxDailyIngestionService.CandleResult::status)
                .containsExactly(INSERTED, FAILED);
        assertThat(pipeline.evaluate(universe(), FEED.calendar(), STORED).block(WAIT).results().getFirst().dataQuality())
                .isNotEqualTo(DataQuality.COMPLETE);

        sql("ALTER TABLE phivealpha_idx.idx_daily_prices DROP CONSTRAINT sit_daily_failure");
        var reopened = service(storage, NOW.plusSeconds(1));
        var retry = ingest(reopened);
        assertThat(retry.quality()).isEqualTo(Quality.COMPLETE);
        assertThat(retry.candles()).extracting(IdxDailyIngestionService.CandleResult::status)
                .containsExactly(ALREADY_EXISTS, INSERTED);
        var cutoff = STORED.plusSeconds(1);
        assertThat(ingest(reopened).candles()).extracting(IdxDailyIngestionService.CandleResult::status)
                .containsExactly(ALREADY_EXISTS, ALREADY_EXISTS);

        assertThat(storage.historicalPrices("BBCA", AdjustmentStatus.UNADJUSTED, FIRST, LAST,
                STORED.atOffset(ZoneOffset.UTC)).collectList().block(WAIT)).singleElement()
                .satisfies(row -> assertThat(row.tradingDate()).isEqualTo(FIRST));
        assertThat(storage.historicalPrices("BBCA", AdjustmentStatus.UNADJUSTED, FIRST, LAST,
                cutoff.atOffset(ZoneOffset.UTC)).collectList().block(WAIT))
                .extracting(DailyPrice::tradingDate).containsExactly(FIRST, LAST);
        assertThat(reopened.evaluate(universe(), FEED.calendar(), cutoff).block(WAIT).results().getFirst().eligibility())
                .isEqualTo(Eligibility.ELIGIBLE);

        var statuses = template.getDatabaseClient().sql("""
                SELECT status FROM phivealpha_idx.idx_data_ingestion
                WHERE provider = 'fixture-phivealpha' AND dataset = 'DAILY' AND requests_used = 0
                ORDER BY ingestion_id
                """).map((row, metadata) -> row.get(0, String.class)).all().collectList().block(WAIT);
        assertThat(statuses).containsExactly("PARTIAL", "SUCCESS", "SUCCESS");
    }

    private IdxDailyIngestionService.Summary ingest(IdxDailyIngestionService service) {
        return service.ingest(FEED, "BBCA", FIRST, LAST, FEED.calendar(), FEED.universeVersion()).block(WAIT);
    }

    private void sql(String statement) {
        template.getDatabaseClient().sql(statement).then().block(WAIT);
    }
}
