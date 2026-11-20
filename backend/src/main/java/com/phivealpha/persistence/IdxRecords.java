package com.phivealpha.persistence;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public final class IdxRecords {
    private IdxRecords() {}

    public enum AdjustmentStatus { UNKNOWN, UNADJUSTED, ADJUSTED }
    public enum ValidationStatus { PENDING, VALIDATED, REJECTED }
    public enum IngestionStatus { SUCCESS, PARTIAL, FAILED }

    public record Instrument(String symbol, String name, String sector, String instrumentType,
                             String listingStatus, String currency, int lotSize, OffsetDateTime updatedAt) {}

    public record DailyPrice(String symbol, LocalDate tradingDate, BigDecimal open, BigDecimal high,
                             BigDecimal low, BigDecimal close, long volume, BigDecimal turnover,
                             String source, OffsetDateTime observedAt, OffsetDateTime ingestedAt,
                             AdjustmentStatus adjustmentStatus) {}

    public record News(String articleId, String title, String source, String sourceUrl,
                       OffsetDateTime publishedAt, OffsetDateTime firstSeenAt, OffsetDateTime ingestedAt,
                       OffsetDateTime availableAt, String contentHash, String provenance,
                       ValidationStatus validationStatus) {}

    public record Ingestion(String provider, String dataset, String scope, OffsetDateTime fetchedAt,
                            IngestionStatus status, String errorCategory, long recordCount,
                            long rejectedCount, int requestsUsed, int requestBudget, String budgetWindow) {}
}
