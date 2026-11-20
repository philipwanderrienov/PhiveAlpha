package com.phivealpha.screening;

import static com.phivealpha.screening.LiquidUniverse.*;
import static com.phivealpha.screening.LiquidUniverse.CorporateActionStatus.CLEAR;
import static com.phivealpha.screening.LiquidUniverse.Eligibility.*;
import static com.phivealpha.screening.LiquidUniverse.ListingStatus.ACTIVE;
import static com.phivealpha.screening.LiquidUniverse.Reason.*;
import static com.phivealpha.screening.LiquidUniverse.SecurityType.COMMON_STOCK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.phivealpha.marketdata.apicoid.VersionedIdxCalendar;
import com.phivealpha.persistence.IdxRecords.AdjustmentStatus;
import com.phivealpha.persistence.IdxRecords.DailyPrice;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class LiquidUniverseServiceTest {
    private static final List<LocalDate> DAYS = List.of(date(9), date(10), date(11), date(13));
    private static final Instant CUTOFF = Instant.parse("2026-11-13T05:00:00Z");
    private static final Instant REFERENCE_TIME = Instant.parse("2026-11-13T04:30:00Z");
    private static final LocalDate OLD_LISTING = LocalDate.parse("2010-01-04");
    private static final CalendarReference CALENDAR = new CalendarReference(readCalendar(), Instant.EPOCH);
    private static final LiquidityPolicy POLICY = policy(4, Duration.ofDays(3));
    private final LiquidUniverseService service = new LiquidUniverseService(POLICY);

    @Test
    void classifiesLiquidAndIlliquidStocksWithoutPaddingRanking() {
        var members = List.of(member("HIGH"), member("TIEB"), member("TIEA"), member("LOW"), member("VOL"));
        var prices = new ArrayList<DailyPrice>();
        prices.addAll(bars("HIGH", 1001, 2000, 3001, 4000));
        prices.addAll(bars("TIEB", 1000, 1000, 3000, 3000));
        prices.addAll(bars("TIEA", 1000, 1000, 3000, 3000));
        prices.addAll(bars("LOW", 0, 0, 0, 100));
        prices.addAll(DAYS.stream().map(day -> bar("VOL", day, 2000, 5)).toList());

        var output = evaluate(members, prices);
        var high = result(output, "HIGH");
        assertThat(high.eligibility()).isEqualTo(ELIGIBLE);
        assertThat(high.dataQuality()).isEqualTo(DataQuality.COMPLETE);
        assertThat(high.liquidity().latestDailyTurnoverIdr()).isEqualByComparingTo("4000");
        assertThat(high.liquidity().medianDailyTurnoverIdr()).isEqualByComparingTo("2500.5");
        assertThat(high.liquidity().latestDailyVolumeShares()).isEqualTo(100);
        assertThat(high.liquidity().activeSessions()).isEqualTo(4);
        assertThat(high.liquidity().activeRatio()).isEqualByComparingTo("1");
        assertThat(high.liquidity().liquidSessions()).isEqualTo(4);
        assertThat(high.completeness()).isEqualTo(new Completeness(4, 4, true));
        assertThat(high.asOf()).isEqualTo(CUTOFF);
        assertThat(high.universeVersion()).isEqualTo("qa-master-v1");
        assertThat(high.calendarVersion()).isEqualTo("liquid-universe-calendar-v1");
        assertThat(output.universeAsOf()).isEqualTo(REFERENCE_TIME);
        assertThat(output.universeSource()).isEqualTo("Synthetic IDX master");

        var low = result(output, "LOW");
        assertThat(low.eligibility()).isEqualTo(INELIGIBLE);
        assertThat(low.liquidity().activeSessions()).isEqualTo(1);
        assertThat(low.liquidity().activeRatio()).isEqualByComparingTo("0.25");
        assertThat(low.liquidity().medianDailyTurnoverIdr()).isEqualByComparingTo("0");
        assertThat(low.rejectionReasons()).extracting(Issue::reason).containsExactly(
                ACTIVE_SESSIONS_BELOW_MINIMUM, ACTIVE_RATIO_BELOW_MINIMUM,
                LIQUID_SESSIONS_BELOW_MINIMUM, MEDIAN_TURNOVER_BELOW_MINIMUM);
        assertThat(result(output, "VOL").rejectionReasons()).extracting(Issue::reason)
                .containsExactly(LIQUID_SESSIONS_BELOW_MINIMUM);

        assertThat(LiquidCandidateRanking.rank(output)).extracting(Result::symbol)
                .containsExactly("HIGH", "TIEA", "TIEB");
        Collections.reverse(prices);
        assertThat(evaluate(members.reversed(), prices)).isEqualTo(output);
    }

    @Test
    void checksListingAndCorporateActionEvidenceSeparatelyFromLiquidity() {
        var output = evaluate(List.of(
                new Member("IPO", COMMON_STOCK, ACTIVE, date(11), CLEAR),
                new Member("SUSP", COMMON_STOCK, ListingStatus.SUSPENDED, OLD_LISTING, CLEAR),
                new Member("DELS", COMMON_STOCK, ListingStatus.DELISTED, OLD_LISTING, CLEAR),
                new Member("UNKN", COMMON_STOCK, ListingStatus.UNKNOWN, OLD_LISTING, CLEAR),
                new Member("DATE", COMMON_STOCK, ACTIVE, null, CLEAR),
                new Member("ACTN", COMMON_STOCK, ACTIVE, OLD_LISTING, CorporateActionStatus.AMBIGUOUS),
                new Member("TYPE", SecurityType.UNKNOWN, ACTIVE, OLD_LISTING, CLEAR),
                new Member("OTHR", SecurityType.OTHER, ACTIVE, OLD_LISTING, CLEAR),
                member("DUPE"), member(" dupe ")), List.of());

        rejection(output, "IPO", INELIGIBLE, DataQuality.NOT_EVALUATED, IPO_TOO_RECENT);
        rejection(output, "SUSP", INELIGIBLE, DataQuality.NOT_EVALUATED, SUSPENDED);
        rejection(output, "DELS", INELIGIBLE, DataQuality.NOT_EVALUATED, DELISTED);
        rejection(output, "UNKN", UNVERIFIED, DataQuality.UNVERIFIED, LISTING_STATUS_UNVERIFIED);
        rejection(output, "DATE", UNVERIFIED, DataQuality.UNVERIFIED, LISTING_DATE_UNVERIFIED);
        rejection(output, "ACTN", UNVERIFIED, DataQuality.UNVERIFIED, CORPORATE_ACTION_UNVERIFIED);
        rejection(output, "TYPE", UNVERIFIED, DataQuality.UNVERIFIED, SECURITY_TYPE_UNVERIFIED);
        rejection(output, "OTHR", INELIGIBLE, DataQuality.NOT_EVALUATED, NOT_COMMON_STOCK);
        rejection(output, "DUPE", UNVERIFIED, DataQuality.UNVERIFIED, DUPLICATE_INSTRUMENT);
        assertThat(output.results()).hasSize(9);
        assertThat(LiquidCandidateRanking.rank(output)).isEmpty();
    }

    @Test
    void usesCompletedVersionedSessionsAndIgnoresAllFutureAvailability() {
        var original = bars("TEST", 1000, 2000, 3000, 4000);
        var beforeClose = Instant.parse("2026-11-13T04:29:59Z");
        var beforeAvailability = withAvailability(original.getLast(), CUTOFF.plusSeconds(1));
        var missing = evaluate(List.of(member("TEST")), original.subList(0, 3));
        var late = evaluate(List.of(member("TEST")),
                List.of(original.get(0), original.get(1), original.get(2), beforeAvailability));
        assertThat(late).isEqualTo(missing);
        rejection(late, "TEST", UNVERIFIED, DataQuality.STALE, MISSING_CANDLE, STALE_PRICE);

        var lateCorrection = new ArrayList<>(original);
        lateCorrection.add(beforeAvailability);
        lateCorrection.add(withAvailability(original.getLast(), CUTOFF.plusSeconds(2)));
        lateCorrection.add(bar("TEST", date(16), 9999999, 100));
        assertThat(evaluate(List.of(member("TEST")), lateCorrection))
                .isEqualTo(evaluate(List.of(member("TEST")), original));

        var earlyReference = new Reference("qa-early", beforeClose, beforeClose, "fixture", List.of(member("TEST")));
        var threeSessions = new LiquidUniverseService(policy(3, Duration.ofDays(3)));
        var intraday = threeSessions.evaluate(earlyReference, CALENDAR, original, beforeClose);
        var row = result(intraday, "TEST");
        assertThat(row.eligibility()).isEqualTo(ELIGIBLE);
        assertThat(row.liquidity().latestDailyTurnoverIdr()).isEqualByComparingTo("3000");
        assertThat(row.liquidity().medianDailyTurnoverIdr()).isEqualByComparingTo("2000");

        var availableAtCutoff = new ArrayList<>(original.subList(0, 3));
        availableAtCutoff.add(withAvailability(original.getLast(), CUTOFF));
        assertThat(result(evaluate(List.of(member("TEST")), availableAtCutoff), "TEST").eligibility())
                .isEqualTo(ELIGIBLE);
    }

    @Test
    void rejectsMissingDuplicateInvalidAndNonTradingCandles() {
        var valid = bars("TEST", 1000, 2000, 3000, 4000);
        var missing = evaluate(List.of(member("TEST")), List.of(valid.get(0), valid.get(2), valid.get(3)));
        rejection(missing, "TEST", UNVERIFIED, DataQuality.INCOMPLETE, MISSING_CANDLE);
        assertThat(result(missing, "TEST").completeness()).isEqualTo(new Completeness(4, 3, true));

        var duplicate = new ArrayList<>(valid);
        duplicate.add(valid.get(1));
        var output = evaluate(List.of(member("TEST")), duplicate);
        rejection(output, "TEST", UNVERIFIED, DataQuality.INCOMPLETE, DUPLICATE_CANDLE, MISSING_CANDLE);
        assertThat(result(output, "TEST").completeness().validCandles()).isEqualTo(3);

        var holiday = new ArrayList<>(valid);
        holiday.add(bar("TEST", date(12), 1000, 100));
        rejection(evaluate(List.of(member("TEST")), holiday), "TEST",
                UNVERIFIED, DataQuality.INCOMPLETE, NON_TRADING_CANDLE);

        var invalid = new ArrayList<>(valid);
        invalid.set(1, bar("TEST", date(10), 0, 100));
        rejection(evaluate(List.of(member("TEST")), invalid), "TEST",
                UNVERIFIED, DataQuality.INCOMPLETE, INVALID_CANDLE, MISSING_CANDLE);
    }

    @Test
    void verifiesFreshnessAndBasisEvenWithKnownListing() {
        var valid = bars("TEST", 1000, 2000, 3000, 4000);
        var withAdjusted = new ArrayList<>(valid);
        var row = valid.get(1);
        withAdjusted.set(1, new DailyPrice(row.symbol(), row.tradingDate(), row.open(), row.high(), row.low(),
                row.close(), row.volume(), row.turnover(), row.source(), row.observedAt(), row.ingestedAt(),
                AdjustmentStatus.ADJUSTED));
        rejection(evaluate(List.of(member("TEST")), withAdjusted), "TEST",
                UNVERIFIED, DataQuality.UNVERIFIED, ADJUSTMENT_BASIS_UNVERIFIED);

        var premature = new ArrayList<>(valid);
        var beforeClose = row.observedAt().minusMinutes(2);
        premature.set(1, new DailyPrice(row.symbol(), row.tradingDate(), row.open(), row.high(), row.low(),
                row.close(), row.volume(), row.turnover(), row.source(), beforeClose, row.ingestedAt(),
                row.adjustmentStatus()));
        rejection(evaluate(List.of(member("TEST")), premature), "TEST",
                UNVERIFIED, DataQuality.INCOMPLETE, INVALID_PRICE_AVAILABILITY);

        var strictFreshness = new LiquidUniverseService(policy(4, Duration.ofMinutes(20)));
        var stale = strictFreshness.evaluate(reference(List.of(member("TEST"))), CALENDAR, valid, CUTOFF);
        rejection(stale, "TEST", UNVERIFIED, DataQuality.STALE, STALE_PRICE);
        assertThat(result(stale, "TEST").completeness().validCandles()).isEqualTo(4);
    }

    @Test
    void failsClosedOnMissingCalendarCoverageOrPointInTimeReference() {
        var prices = bars("TEST", 1000, 2000, 3000, 4000);
        var reference = reference(List.of(member("TEST")));
        var strictLookback = new LiquidUniverseService(policy(6, Duration.ofDays(3)));
        var noCoverage = strictLookback.evaluate(reference, CALENDAR, prices, CUTOFF);
        assertThat(noCoverage.referenceIssues()).containsExactly(new Issue(CALENDAR_OUT_OF_COVERAGE, date(8)));
        rejection(noCoverage, "TEST", UNVERIFIED, DataQuality.UNVERIFIED, CALENDAR_OUT_OF_COVERAGE);

        var laterCalendar = new CalendarReference(CALENDAR.calendar(), CUTOFF.plusSeconds(1));
        var calendarResult = service.evaluate(reference, laterCalendar, prices, CUTOFF);
        rejection(calendarResult, "TEST", UNVERIFIED, DataQuality.UNVERIFIED, CALENDAR_NOT_AVAILABLE_AT_CUTOFF);
        assertThat(calendarResult.calendarVersion()).isNull();

        var laterReference = new Reference("future", REFERENCE_TIME, CUTOFF.plusSeconds(1),
                "fixture", List.of(member("TEST")));
        var future = service.evaluate(laterReference, CALENDAR, prices, CUTOFF);
        assertThat(future.referenceIssues()).containsExactly(new Issue(REFERENCE_NOT_AVAILABLE_AT_CUTOFF, null));
        assertThat(future.universeVersion()).isNull();
        assertThat(future.results()).isEmpty();

        var oldReference = new Reference("stale", Instant.EPOCH, Instant.EPOCH, "fixture", List.of(member("TEST")));
        rejection(service.evaluate(oldReference, CALENDAR, prices, CUTOFF), "TEST",
                UNVERIFIED, DataQuality.UNVERIFIED, STALE_UNIVERSE_REFERENCE);
    }

    @Test
    void rejectsInvalidThresholds() {
        assertThatThrownBy(() -> policy(2, Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
    }

    private Evaluation evaluate(List<Member> members, List<DailyPrice> prices) {
        return service.evaluate(reference(members), CALENDAR, prices, CUTOFF);
    }

    private static Reference reference(List<Member> members) {
        return new Reference("qa-master-v1", REFERENCE_TIME, REFERENCE_TIME.plusSeconds(1),
                "Synthetic IDX master", members);
    }

    private static Member member(String symbol) {
        return new Member(symbol, COMMON_STOCK, ACTIVE, OLD_LISTING, CLEAR);
    }

    private static void rejection(Evaluation evaluation, String symbol, Eligibility eligibility,
                                  DataQuality quality, Reason... reasons) {
        var row = result(evaluation, symbol);
        assertThat(row.eligibility()).isEqualTo(eligibility);
        assertThat(row.dataQuality()).isEqualTo(quality);
        assertThat(row.rejectionReasons()).extracting(Issue::reason).contains(reasons);
        assertThat(row.liquidity()).isNull();
    }

    private static Result result(Evaluation evaluation, String symbol) {
        return evaluation.results().stream().filter(row -> row.symbol().equals(symbol)).findFirst().orElseThrow();
    }

    private static LiquidityPolicy policy(int sessions, Duration freshness) {
        return new LiquidityPolicy(sessions, sessions, 2, new BigDecimal("0.75"), 2,
                BigDecimal.valueOf(1000), BigDecimal.valueOf(2000), 50, freshness, Duration.ofDays(7));
    }

    private static List<DailyPrice> bars(String symbol, long... values) {
        var prices = new ArrayList<DailyPrice>();
        for (int i = 0; i < values.length; i++) {
            prices.add(bar(symbol, DAYS.get(i), values[i], values[i] == 0 ? 0 : 100));
        }
        return prices;
    }

    private static DailyPrice bar(String symbol, LocalDate date, long turnover, long volume) {
        var observedAt = CALENDAR.calendar().sessionClose(date).orElse(date.atTime(9, 30).toInstant(ZoneOffset.UTC))
                .atOffset(ZoneOffset.UTC).plusMinutes(1);
        return new DailyPrice(symbol, date, BigDecimal.TEN, BigDecimal.valueOf(volume == 0 ? 10 : 100),
                BigDecimal.TEN, BigDecimal.TEN, volume, BigDecimal.valueOf(turnover), "synthetic feed",
                observedAt, observedAt.plusSeconds(1), AdjustmentStatus.UNADJUSTED);
    }

    private static DailyPrice withAvailability(DailyPrice row, Instant availableAt) {
        return new DailyPrice(row.symbol(), row.tradingDate(), row.open(), row.high(), row.low(), row.close(),
                row.volume(), row.turnover(), row.source(), row.observedAt(), OffsetDateTime.ofInstant(availableAt,
                        ZoneOffset.UTC), row.adjustmentStatus());
    }

    private static VersionedIdxCalendar readCalendar() {
        try (var input = LiquidUniverseServiceTest.class.getResourceAsStream("/phivealpha/liquid-universe/calendar.json")) {
            return new VersionedIdxCalendar(JsonMapper.builder().build().readTree(input));
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    private static LocalDate date(int day) {
        return LocalDate.of(2026, 11, day);
    }
}
