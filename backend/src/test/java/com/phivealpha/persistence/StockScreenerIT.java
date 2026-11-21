package com.phivealpha.persistence;

import static com.phivealpha.screening.StockScreenerFixture.CUTOFF;
import static org.assertj.core.api.Assertions.assertThat;

import com.phivealpha.PhiveAlphaApplication;
import com.phivealpha.marketdata.apicoid.ApiCoIdData.*;
import com.phivealpha.persistence.IdxRecords.DailyPrice;
import com.phivealpha.persistence.IdxRecords.Instrument;
import com.phivealpha.screening.LiquidUniverse.*;
import com.phivealpha.screening.LiquidUniverseService;
import com.phivealpha.screening.StockScreenerFixture;
import com.phivealpha.screening.StockScreenerService;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@EnabledIfEnvironmentVariable(named = "PHIVEALPHA_IT_DATA_DIR", matches = "/tmp/phivealpha-it\\.[A-Za-z0-9]+/data")
@SpringBootTest(classes = PhiveAlphaApplication.class, useMainMethod = SpringBootTest.UseMainMethod.ALWAYS,
        properties = "phivealpha.stock-screener.persisted=true")
@ActiveProfiles({"phivealpha-persistence", "phivealpha-daily-ingestion"})
class StockScreenerIT {
    private static final String DATABASE = "phivealpha_dev_screener_it";
    private static final Duration WAIT = Duration.ofSeconds(60);

    @Autowired IdxPersistenceService storage;
    @Autowired R2dbcEntityTemplate template;
    @Autowired LiquidUniverseService liquidity;
    @Autowired StockScreenerService screener;
    @Autowired ApplicationContext application;

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
    void ingestsAndRanksPersistedCandlesAtCutoffAndFailsClosedOnStorageError() {
        var identity = Objects.requireNonNull(template.getDatabaseClient().sql("""
                SELECT current_setting('data_directory'), current_database(), current_user,
                       current_setting('server_version_num')
                """).map((row, metadata) -> List.of(row.get(0, String.class), row.get(1, String.class),
                        row.get(2, String.class), row.get(3, String.class))).one().block(WAIT));
        assertThat(identity.subList(0, 3)).containsExactly(System.getenv("PHIVEALPHA_IT_DATA_DIR"), DATABASE, DATABASE);
        assertThat(identity.getLast()).matches("17[0-9]{4}");

        var fixture = StockScreenerFixture.data();
        var context = fixture.context();
        var timestamp = CUTOFF.minusSeconds(10);
        var writer = new IdxDailyIngestionService(storage, liquidity, Clock.fixed(timestamp, ZoneOffset.UTC));
        for (String symbol : List.of("BBCA", "ASII")) {
            assertThat(storage.upsertInstrument(new Instrument(symbol, symbol, "Fixture", "STOCK", "LISTED",
                    "IDR", 100, timestamp.atOffset(ZoneOffset.UTC))).block(WAIT)).isTrue();
            var prices = fixture.prices().stream().filter(row -> row.symbol().equals(symbol)).toList();
            var bars = prices.stream().map(StockScreenerIT::dailyBar).toList();
            var calendar = context.calendar().calendar();
            var observed = CUTOFF.minusSeconds(60);
            var evidence = new Provenance(IdxDailyIngestionService.FIXTURE, "DAILY", observed, observed,
                    calendar.source(), calendar.version(), context.universe().version(), observed);
            var snapshot = new Snapshot<>(Quality.COMPLETE, "SYNTHETIC_FIXTURE", bars, evidence);
            assertThat(writer.ingest((s, from, to) -> Mono.just(snapshot), symbol,
                    bars.getFirst().tradingDate(), bars.getLast().tradingDate(),
                    context.calendar(), context.universe().version()).block(WAIT).quality()).isEqualTo(Quality.COMPLETE);
        }

        // Reference is already visible. Every candle is still hidden at this earlier cutoff.
        var before = screener.screenPersisted(context, timestamp.minusSeconds(1)).block(WAIT);
        assertThat(before.scoring().ranking()).isEmpty();

        var after = screener.screenPersisted(context, CUTOFF).block(WAIT);
        assertThat(after.mode()).isEqualTo(StockScreenerService.Mode.DEVELOPMENT_PERSISTENCE);
        assertThat(after.scoring().ranking()).extracting(row -> row.assessment().symbol()).containsExactly("ASII", "BBCA");
        assertThat(screener.screenPersisted(context, CUTOFF).block(WAIT).scoring()).isEqualTo(after.scoring());
        assertThat(after.decisions()).filteredOn(row -> row.symbol().equals("BBRI")).singleElement()
                .satisfies(row -> assertThat(row.liquidity().rejectionReasons()).extracting(Issue::reason)
                        .contains(Reason.MISSING_CANDLE));
        assertThat(after.decisions()).filteredOn(row -> row.symbol().equals("SUSP")).singleElement()
                .satisfies(row -> assertThat(row.status()).isEqualTo(Eligibility.INELIGIBLE));

        var client = WebTestClient.bindToApplicationContext(application).configureClient().responseTimeout(WAIT).build();
        client.get().uri("/api/idx/v1/stock-screener").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.mode").isEqualTo("DEVELOPMENT_PERSISTENCE")
                .jsonPath("$.scoring.ranking.length()").isEqualTo(2);

        template.getDatabaseClient().sql("""
                ALTER TABLE phivealpha_idx.idx_daily_prices RENAME TO sit_unavailable_prices
                """).then().block(WAIT);
        var unavailable = screener.screenPersisted(context, CUTOFF).block(WAIT);
        assertThat(unavailable.quality()).isEqualTo(StockScreenerService.Quality.UNAVAILABLE);
        assertThat(unavailable.failure()).isEqualTo(StockScreenerService.Failure.HISTORICAL_STORAGE_UNAVAILABLE);
        assertThat(unavailable.scoring()).isNull();
    }

    private static DailyBar dailyBar(DailyPrice price) {
        return new DailyBar(price.symbol(), price.tradingDate(), price.open(), price.high(), price.low(),
                price.close(), price.volume(), price.turnover(), price.adjustmentStatus());
    }
}
