package com.phivealpha.persistence;

import static com.phivealpha.persistence.IdxRecords.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.phivealpha.PhiveAlphaApplication;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import reactor.test.StepVerifier;

@EnabledIfEnvironmentVariable(named = "PHIVEALPHA_IT_DATA_DIR", matches = "/tmp/phivealpha-it\\.[A-Za-z0-9]+/data")
@SpringBootTest(classes = PhiveAlphaApplication.class, useMainMethod = SpringBootTest.UseMainMethod.ALWAYS)
@ActiveProfiles("phivealpha-persistence")
class IdxPersistenceIT {
    private static final OffsetDateTime SEEN = OffsetDateTime.parse("2026-11-13T15:00:00+07:00");
    private static final LocalDate LATE = SEEN.toLocalDate();
    private static final LocalDate EARLY = LATE.minusDays(1);
    private static final IdxMarketCalendar CALENDAR = Set.of(EARLY, LATE)::contains;
    private static final BigDecimal PRICE = new BigDecimal("1234567890.1234567890123456789");

    @Autowired IdxPersistenceService service;
    @Autowired R2dbcEntityTemplate template;
    @Autowired ApplicationContext context;

    @DynamicPropertySource
    static void isolatedConnection(DynamicPropertyRegistry registry) {
        String url = System.getenv("PHIVEALPHA_R2DBC_URL");
        assertThat(url).matches("r2dbc:postgresql://127\\.0\\.0\\.1:[0-9]+/phivealpha_it");
        registry.add("phivealpha.persistence.url", () -> url);
        registry.add("phivealpha.persistence.username", () -> "phivealpha_it");
        registry.add("phivealpha.persistence.password", () -> System.getenv("PHIVEALPHA_R2DBC_PASSWORD"));
    }

    @BeforeEach
    void verifyClusterIdentity() {
        StepVerifier.create(template.getDatabaseClient().sql("SHOW data_directory")
                        .map((row, meta) -> row.get(0, String.class)).one())
                .expectNext(System.getenv("PHIVEALPHA_IT_DATA_DIR")).verifyComplete();
    }

    @Test
    void upsertsInstrumentOnlyWithNewerDataAndKeepsCoinMindOutOfContext() {
        assertThat(context.getBeanDefinitionNames()).doesNotContain("coinMindApplication");
        assertThat(context.getBeansOfType(ScheduledAnnotationBeanPostProcessor.class)).isEmpty();
        assertThat(context.getEnvironment().getProperty("coinmind.ai.enabled")).isNull();
        assertThat(service.upsertInstrument(instrument(" upst ", "Original", SEEN)).block()).isTrue();
        assertThat(service.upsertInstrument(instrument("UPST", "Updated", SEEN.plusMinutes(1))).block()).isTrue();
        assertThat(service.upsertInstrument(instrument("UPST", "Stale", SEEN)).block()).isFalse();
        assertThat(service.upsertInstrument(instrument("UPST", "Updated", SEEN.plusMinutes(1))).block()).isFalse();

        StepVerifier.create(template.getDatabaseClient().sql(
                        "SELECT name FROM phivealpha_idx.idx_instruments WHERE symbol = 'UPST'")
                .map((row, meta) -> row.get("name", String.class)).one())
                .expectNext("Updated").verifyComplete();
    }

    @Test
    void persistsExactNumericValuesIdempotentlyAndReadsOrderedAsOfHistory() {
        service.upsertInstrument(instrument("PRCE", "Price", SEEN)).block();
        assertThat(service.saveDailyPrice(price(LATE, AdjustmentStatus.UNKNOWN), CALENDAR).block()).isTrue();
        assertThat(service.saveDailyPrice(price(LATE, AdjustmentStatus.UNKNOWN), CALENDAR).block()).isFalse();
        assertThat(service.saveDailyPrice(price(LATE, AdjustmentStatus.UNADJUSTED), CALENDAR).block()).isTrue();
        assertThat(service.saveDailyPrice(price(EARLY, AdjustmentStatus.UNKNOWN), CALENDAR).block()).isTrue();

        var rows = service.historicalPrices("prce", AdjustmentStatus.UNKNOWN, EARLY, LATE, SEEN)
                .collectList().block();
        assertThat(rows).extracting(DailyPrice::tradingDate).containsExactly(EARLY, LATE);
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.close()).isEqualByComparingTo(PRICE);
            assertThat(row.turnover()).isEqualByComparingTo(PRICE);
            assertThat(row.adjustmentStatus()).isEqualTo(AdjustmentStatus.UNKNOWN);
        });
        StepVerifier.create(service.historicalPrices(
                        "prce", AdjustmentStatus.UNKNOWN, EARLY, LATE, SEEN.minusSeconds(1)))
                .expectNextMatches(row -> row.tradingDate().equals(EARLY)).verifyComplete();
        StepVerifier.create(template.getDatabaseClient().sql("""
                INSERT INTO phivealpha_idx.idx_daily_prices
                SELECT * FROM phivealpha_idx.idx_daily_prices
                WHERE symbol = 'PRCE' AND adjustment_status = 'UNKNOWN'
                """).fetch().rowsUpdated()).expectError(DataIntegrityViolationException.class).verify();
    }

    @Test
    void deduplicatesNewsAndAppliesArticleAndIssuerAvailabilityCutoffs() {
        service.upsertInstrument(instrument("NWSA", "Issuer A", SEEN)).block();
        service.upsertInstrument(instrument("NWSB", "Issuer B", SEEN)).block();
        var original = news("wire:1", "a", SEEN, ValidationStatus.VALIDATED);
        var duplicate = news("wire:2", "a", SEEN.plusHours(1), ValidationStatus.VALIDATED);
        assertThat(service.saveNews(original, List.of("NWSA", "nwsa")).block()).isEqualTo("wire:1");
        assertThat(service.saveNews(duplicate, List.of("NWSB")).block()).isEqualTo("wire:1");
        assertThat(service.saveNews(duplicate, List.of("NWSB")).block()).isEqualTo("wire:1");
        service.saveNews(news("wire:pending", "b", SEEN, ValidationStatus.PENDING), List.of("NWSA")).block();

        StepVerifier.create(service.newsAvailableAt("NWSA", SEEN.minusNanos(1), 10)).verifyComplete();
        StepVerifier.create(service.newsAvailableAt("NWSA", SEEN, 10))
                .expectNextMatches(row -> row.articleId().equals("wire:1")).verifyComplete();
        StepVerifier.create(service.newsAvailableAt("NWSB", SEEN, 10)).verifyComplete();
        StepVerifier.create(service.newsAvailableAt("NWSB", SEEN.plusHours(1), 10))
                .expectNextMatches(row -> row.articleId().equals("wire:1")).verifyComplete();
        StepVerifier.create(service.saveNews(news("wire:2", "c", SEEN, ValidationStatus.VALIDATED), List.of()))
                .expectError(IllegalArgumentException.class).verify();
    }

    @Test
    void rollsBackArticleWhenLinkFailsAndCanRecordFailedIngestion() {
        var article = news("wire:rollback", "d", SEEN, ValidationStatus.VALIDATED);
        StepVerifier.create(service.saveNews(article, List.of("MISSING")))
                .expectError(IdxPersistenceService.PersistenceException.class).verify();
        StepVerifier.create(template.getDatabaseClient().sql(
                        "SELECT article_id FROM phivealpha_idx.idx_news WHERE article_id = 'wire:rollback'")
                .map((row, meta) -> row.get(0, String.class)).one()).verifyComplete();
        var ingestion = new Ingestion("fixture", "news", "MISSING", SEEN, IngestionStatus.FAILED,
                "INVALID_SYMBOL", 1, 1, 1, 10, "day");
        assertThat(service.recordIngestion(ingestion).block()).isPositive();
    }

    private static Instrument instrument(String symbol, String name, OffsetDateTime updatedAt) {
        return new Instrument(symbol, name, "Finance", "STOCK", "LISTED", "IDR", 100, updatedAt);
    }

    private static DailyPrice price(LocalDate date, AdjustmentStatus status) {
        var observed = date.atTime(SEEN.toLocalTime()).atOffset(SEEN.getOffset());
        return new DailyPrice("PRCE", date, PRICE, PRICE, PRICE, PRICE, 100, PRICE,
                "fixture", observed, observed, status);
    }

    private static News news(String id, String hash, OffsetDateTime available, ValidationStatus status) {
        return new News(id, "Fixture headline", "fixture", "https://example.invalid/headline",
                SEEN.minusMinutes(5), SEEN.minusMinutes(4), available, available, hash.repeat(64),
                "synthetic", status);
    }
}
