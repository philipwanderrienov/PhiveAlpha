package com.phivealpha.persistence;

import static com.phivealpha.persistence.IdxRecords.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class IdxPersistenceValidationTest {
    private static final OffsetDateTime OBSERVED = OffsetDateTime.parse("2026-11-13T15:00:00+07:00");
    private final IdxPersistenceService service = new IdxPersistenceService(null, null);

    @Test
    void rejectsWeekdayOutsideConfirmedExchangeCalendar() {
        var date = LocalDate.of(2026, 11, 13);
        var price = new DailyPrice("BBCA", date, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ONE, 1, BigDecimal.ONE, "fixture", OBSERVED, OBSERVED, AdjustmentStatus.UNKNOWN);
        StepVerifier.create(service.saveDailyPrice(price, candidate -> false))
                .expectErrorMessage("Date is outside confirmed IDX trading sessions").verify();
    }

    @Test
    void rejectsNewsThatWouldBeAvailableBeforeIngestion() {
        var article = new News("fixture:1", "Title", "Fixture", "https://example.invalid/1", OBSERVED,
                OBSERVED, OBSERVED.plusMinutes(1), OBSERVED, "a".repeat(64), "synthetic",
                ValidationStatus.VALIDATED);
        StepVerifier.create(service.saveNews(article, List.of("BBCA")))
                .expectErrorMessage("IDX timestamps are out of order").verify();
    }

    @Test
    void rejectsIngestionWithMoreRejectedRowsThanFetched() {
        var ingestion = new Ingestion("fixture", "news", "BBCA", OBSERVED,
                IngestionStatus.PARTIAL, "INVALID_ROW", 1, 2, 1, 10, "day");
        StepVerifier.create(service.recordIngestion(ingestion))
                .expectErrorMessage("Invalid ingestion metadata").verify();
    }

    @Test
    void requiresAnEvaluationCutoffForHistoricalReads() {
        var date = OBSERVED.toLocalDate();
        StepVerifier.create(service.historicalPrices("BBCA", AdjustmentStatus.UNKNOWN, date, date, null))
                .expectErrorSatisfies(error -> assertThat(error).isInstanceOf(IllegalArgumentException.class))
                .verify();
    }
}
