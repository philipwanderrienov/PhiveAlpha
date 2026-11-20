package com.phivealpha.screening;

import com.phivealpha.marketdata.apicoid.VersionedIdxCalendar;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class LiquidUniverse {
    private LiquidUniverse() {}

    public enum Eligibility { ELIGIBLE, INELIGIBLE, UNVERIFIED }
    public enum DataQuality { COMPLETE, INCOMPLETE, STALE, UNVERIFIED, NOT_EVALUATED }
    public enum SecurityType { COMMON_STOCK, OTHER, UNKNOWN }
    public enum ListingStatus { ACTIVE, SUSPENDED, DELISTED, UNKNOWN }
    public enum CorporateActionStatus { CLEAR, AMBIGUOUS, UNKNOWN }
    public enum Reason {
        REFERENCE_NOT_AVAILABLE_AT_CUTOFF, STALE_UNIVERSE_REFERENCE, DUPLICATE_INSTRUMENT,
        SECURITY_TYPE_UNVERIFIED, NOT_COMMON_STOCK, LISTING_STATUS_UNVERIFIED, SUSPENDED, DELISTED,
        IPO_TOO_RECENT, LISTING_DATE_UNVERIFIED, CORPORATE_ACTION_UNVERIFIED,
        CALENDAR_NOT_AVAILABLE_AT_CUTOFF, CALENDAR_OUT_OF_COVERAGE,
        INVALID_CANDLE, NON_TRADING_CANDLE, DUPLICATE_CANDLE, MISSING_CANDLE,
        STALE_PRICE, INVALID_PRICE_AVAILABILITY, ADJUSTMENT_BASIS_UNVERIFIED, MIXED_PRICE_SOURCE,
        ACTIVE_SESSIONS_BELOW_MINIMUM, ACTIVE_RATIO_BELOW_MINIMUM,
        LIQUID_SESSIONS_BELOW_MINIMUM, MEDIAN_TURNOVER_BELOW_MINIMUM
    }

    /** Point-in-time exchange listing and corporate action review for a symbol. */
    public record Member(String symbol, SecurityType securityType, ListingStatus listingStatus,
                         LocalDate listedOn, CorporateActionStatus corporateActionStatus) {
        public Member {
            symbol = normalizeSymbol(symbol);
            Objects.requireNonNull(securityType);
            Objects.requireNonNull(listingStatus);
            Objects.requireNonNull(corporateActionStatus);
        }
    }

    public record Reference(String version, Instant asOf, Instant availableAt, String source, List<Member> members) {
        public Reference {
            if (version == null || version.isBlank() || source == null || source.isBlank()
                    || asOf == null || availableAt == null || availableAt.isBefore(asOf)) {
                throw new IllegalArgumentException("Invalid universe reference provenance");
            }
            members = List.copyOf(members);
        }
    }

    public record CalendarReference(VersionedIdxCalendar calendar, Instant availableAt) {
        public CalendarReference {
            Objects.requireNonNull(calendar);
            Objects.requireNonNull(availableAt);
        }
    }

    public record Issue(Reason reason, LocalDate tradingDate) {}

    public record LiquidityMetrics(BigDecimal latestDailyTurnoverIdr, BigDecimal medianDailyTurnoverIdr,
                                   long latestDailyVolumeShares, int activeSessions, BigDecimal activeRatio,
                                   int liquidSessions, String priceSource, Instant latestSessionClose,
                                   Instant pricesAvailableAt) {}

    public record Completeness(int expectedSessions, int validCandles, boolean evaluated) {}

    public record Result(String symbol, Eligibility eligibility, LiquidityMetrics liquidity,
                         Completeness completeness, Instant asOf, String universeVersion, String calendarVersion,
                         List<Issue> rejectionReasons, DataQuality dataQuality) {
        public Result {
            rejectionReasons = List.copyOf(rejectionReasons);
        }
    }

    public record Evaluation(Instant asOf, String universeVersion, Instant universeAsOf, Instant universeAvailableAt,
                             String universeSource, String calendarVersion, String calendarSource,
                             Instant calendarAvailableAt, LiquidityPolicy policy, List<Issue> referenceIssues,
                             List<Result> results) {
        public Evaluation {
            referenceIssues = List.copyOf(referenceIssues);
            results = List.copyOf(results);
        }
    }

    static String normalizeSymbol(String value) {
        String normalized = Objects.requireNonNull(value).strip().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z0-9]{1,16}")) {
            throw new IllegalArgumentException("Invalid IDX symbol");
        }
        return normalized;
    }
}
