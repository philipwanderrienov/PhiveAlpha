package com.phivealpha.screening;

import static com.phivealpha.screening.LiquidUniverse.*;
import static com.phivealpha.screening.LiquidUniverse.Reason.*;

import com.phivealpha.marketdata.apicoid.VersionedIdxCalendar;
import com.phivealpha.persistence.IdxMarketCalendar;
import com.phivealpha.persistence.IdxRecords.AdjustmentStatus;
import com.phivealpha.persistence.IdxRecords.DailyPrice;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.springframework.stereotype.Service;

@Service
public class LiquidUniverseService {
    private final LiquidityPolicy policy;

    public LiquidUniverseService(LiquidityPolicy policy) {
        this.policy = Objects.requireNonNull(policy);
    }

    public LiquidityPolicy policy() {
        return policy;
    }

    public Evaluation evaluate(Reference reference, CalendarReference calendarReference,
                               List<DailyPrice> prices, Instant cutoff) {
        Objects.requireNonNull(cutoff);
        var calendar = calendarReference.calendar();
        var issues = new ArrayList<Issue>();
        var results = new ArrayList<Result>();
        if (reference.availableAt().isAfter(cutoff)) {
            issues.add(new Issue(REFERENCE_NOT_AVAILABLE_AT_CUTOFF, null));
            return new Evaluation(cutoff, null, null, null, null, null, null, null, policy, issues, results);
        }

        var members = new TreeMap<String, Member>();
        var duplicateSymbols = new HashSet<String>();
        for (var member : reference.members()) {
            if (members.putIfAbsent(member.symbol(), member) != null) {
                duplicateSymbols.add(member.symbol());
            }
        }
        boolean calendarAvailable = !calendarReference.availableAt().isAfter(cutoff);
        if (Duration.between(reference.asOf(), cutoff).compareTo(policy.maxReferenceAge()) > 0) {
            issues.add(new Issue(STALE_UNIVERSE_REFERENCE, null));
        }
        if (!calendarAvailable) {
            issues.add(new Issue(CALENDAR_NOT_AVAILABLE_AT_CUTOFF, null));
        }
        var closedDates = issues.isEmpty() ? closedDates(calendar, cutoff, Math.max(policy.lookbackSessions(), policy.minListingSessions()), issues) : List.<LocalDate>of();
        var calendarVersion = calendarAvailable ? calendar.version() : null;
        if (issues.isEmpty()) {
            int lookback = policy.lookbackSessions();
            var expected = new TreeSet<>(closedDates.subList(0, lookback));
            LocalDate listingBoundary = closedDates.get(policy.minListingSessions() - 1);
            var bySymbol = groupPrices(prices, members.keySet(), cutoff);
            for (var member : members.values()) {
                var decision = duplicateSymbols.contains(member.symbol())
                        ? rejected(Eligibility.UNVERIFIED, DataQuality.UNVERIFIED, lookback, DUPLICATE_INSTRUMENT)
                        : assess(member, calendar, listingBoundary, expected,
                                bySymbol.getOrDefault(member.symbol(), List.of()), cutoff);
                results.add(result(member.symbol(), reference.version(), calendarVersion, cutoff, decision));
            }
        } else {
            for (String symbol : members.keySet()) {
                results.add(result(symbol, reference.version(), calendarVersion, cutoff,
                        new Decision(Eligibility.UNVERIFIED, DataQuality.UNVERIFIED, null,
                                new Completeness(policy.lookbackSessions(), 0, false), issues)));
            }
        }
        return new Evaluation(cutoff, reference.version(), reference.asOf(), reference.availableAt(),
                reference.source(), calendarVersion, calendarAvailable ? calendar.source() : null,
                calendarAvailable ? calendarReference.availableAt() : null, policy, issues, results);
    }

    /** Returns the exact EOD query window, or an empty list when calendar coverage is insufficient. */
    public List<LocalDate> priceWindow(CalendarReference reference, Instant cutoff) {
        return priceWindow(reference, cutoff, policy.lookbackSessions());
    }

    List<LocalDate> priceWindow(CalendarReference reference, Instant cutoff, int sessions) {
        if (reference.availableAt().isAfter(cutoff)) {
            return List.of();
        }
        var issues = new ArrayList<Issue>();
        var dates = closedDates(reference.calendar(), cutoff, sessions, issues);
        return issues.isEmpty() ? List.copyOf(dates) : List.of();
    }

    private static List<LocalDate> closedDates(VersionedIdxCalendar calendar, Instant cutoff,
                                              int needed, List<Issue> issues) {
        var closed = new ArrayList<LocalDate>(needed);
        for (var day = cutoff.atZone(IdxMarketCalendar.ZONE).toLocalDate();
                closed.size() < needed; day = day.minusDays(1)) {
            if (!calendar.covers(day, day)) {
                issues.add(new Issue(CALENDAR_OUT_OF_COVERAGE, day));
                break;
            }
            if (calendar.isClosedBy(day, cutoff)) {
                closed.add(day);
            }
        }
        return closed;
    }

    private static Map<String, List<DailyPrice>> groupPrices(List<DailyPrice> prices, Set<String> symbols, Instant cutoff) {
        var bySymbol = new HashMap<String, List<DailyPrice>>();
        for (var price : prices) {
            if (after(price.observedAt(), cutoff) || after(price.ingestedAt(), cutoff)) {
                continue;
            }
            String symbol = normalizeSymbol(price.symbol());
            if (symbols.contains(symbol)) {
                bySymbol.computeIfAbsent(symbol, key -> new ArrayList<>()).add(price);
            }
        }
        return bySymbol;
    }

    private Decision assess(Member member, VersionedIdxCalendar calendar, LocalDate listingBoundary,
                            NavigableSet<LocalDate> expected, List<DailyPrice> prices, Instant cutoff) {
        int sessions = expected.size();
        Reason instrumentIssue = instrumentIssue(member, listingBoundary, calendar, cutoff);
        if (instrumentIssue != null) {
            return switch (instrumentIssue) {
                case NOT_COMMON_STOCK, SUSPENDED, DELISTED, IPO_TOO_RECENT ->
                    rejected(Eligibility.INELIGIBLE, DataQuality.NOT_EVALUATED, sessions, instrumentIssue);
                default -> rejected(Eligibility.UNVERIFIED, DataQuality.UNVERIFIED, sessions, instrumentIssue);
            };
        }

        var issues = new ArrayList<Issue>();
        var valid = new TreeMap<LocalDate, DailyPrice>();
        var seen = new HashSet<LocalDate>();
        LocalDate cutoffDate = cutoff.atZone(IdxMarketCalendar.ZONE).toLocalDate();
        for (var price : prices) {
            consider(price, cutoffDate, calendar, member.listedOn(), expected, seen, valid, issues);
        }
        if (valid.values().stream().map(DailyPrice::source).distinct().count() > 1) {
            issues.add(new Issue(MIXED_PRICE_SOURCE, null));
        }
        for (var date : expected) {
            if (!valid.containsKey(date)) {
                issues.add(new Issue(MISSING_CANDLE, date));
            }
        }
        boolean stale = !valid.isEmpty() && (!valid.containsKey(expected.last())
                || Duration.between(calendar.sessionClose(valid.lastKey()).orElseThrow(), cutoff)
                        .compareTo(policy.maxPriceAge()) > 0);
        if (stale) {
            issues.add(new Issue(STALE_PRICE, valid.lastKey()));
        }

        var completeness = new Completeness(sessions, valid.size(), true);
        if (!issues.isEmpty()) {
            var quality = priceQuality(issues, stale);
            var ordered = issues.stream().distinct()
                    .sorted(Comparator.comparing(Issue::reason)
                            .thenComparing(Issue::tradingDate, Comparator.nullsFirst(Comparator.naturalOrder())))
                    .toList();
            return new Decision(Eligibility.UNVERIFIED, quality, null, completeness, ordered);
        }

        var metrics = metrics(List.copyOf(valid.values()), calendar.sessionClose(valid.lastKey()).orElseThrow());
        var activeMinimum = policy.minActiveRatio().multiply(BigDecimal.valueOf(sessions));
        if (metrics.activeSessions() < policy.minActiveSessions()) {
            issues.add(new Issue(ACTIVE_SESSIONS_BELOW_MINIMUM, null));
        }
        if (BigDecimal.valueOf(metrics.activeSessions()).compareTo(activeMinimum) < 0) {
            issues.add(new Issue(ACTIVE_RATIO_BELOW_MINIMUM, null));
        }
        if (metrics.liquidSessions() < policy.minLiquidSessions()) {
            issues.add(new Issue(LIQUID_SESSIONS_BELOW_MINIMUM, null));
        }
        if (metrics.medianDailyTurnoverIdr().compareTo(policy.minMedianTurnoverIdr()) < 0) {
            issues.add(new Issue(MEDIAN_TURNOVER_BELOW_MINIMUM, null));
        }
        return new Decision(issues.isEmpty() ? Eligibility.ELIGIBLE : Eligibility.INELIGIBLE,
                DataQuality.COMPLETE, metrics, completeness, issues);
    }

    private static Reason instrumentIssue(Member member, LocalDate listingBoundary,
                                          VersionedIdxCalendar calendar, Instant cutoff) {
        if (member.securityType() == SecurityType.OTHER) {
            return NOT_COMMON_STOCK;
        }
        if (member.securityType() == SecurityType.UNKNOWN) {
            return SECURITY_TYPE_UNVERIFIED;
        }
        return switch (member.listingStatus()) {
            case SUSPENDED -> SUSPENDED;
            case DELISTED -> DELISTED;
            case UNKNOWN -> LISTING_STATUS_UNVERIFIED;
            case ACTIVE -> activeInstrumentIssue(member, listingBoundary, calendar, cutoff);
        };
    }

    private static Reason activeInstrumentIssue(Member member, LocalDate listingBoundary,
                                                VersionedIdxCalendar calendar, Instant cutoff) {
        var listed = member.listedOn();
        if (listed == null || listed.isAfter(cutoff.atZone(IdxMarketCalendar.ZONE).toLocalDate())
                || (calendar.covers(listed, listed) && !calendar.isTradingSession(listed))) {
            return LISTING_DATE_UNVERIFIED;
        }
        if (listed.isAfter(listingBoundary)) {
            return IPO_TOO_RECENT;
        }
        return member.corporateActionStatus() == CorporateActionStatus.CLEAR ? null : CORPORATE_ACTION_UNVERIFIED;
    }

    private static void consider(DailyPrice price, LocalDate cutoffDate,
                                 VersionedIdxCalendar calendar, LocalDate listedOn, NavigableSet<LocalDate> expected,
                                 Set<LocalDate> seen, Map<LocalDate, DailyPrice> valid, List<Issue> issues) {
        var date = price.tradingDate();
        if (date != null && (date.isBefore(expected.first()) || date.isAfter(cutoffDate))) {
            return;
        }
        if (date == null) {
            issues.add(new Issue(INVALID_CANDLE, null));
            return;
        }
        if (!calendar.isTradingSession(date)) {
            issues.add(new Issue(NON_TRADING_CANDLE, date));
            return;
        }
        if (!expected.contains(date)) {
            return; // An open session is outside the EOD window.
        }
        boolean first = seen.add(date);
        if (!first) {
            valid.remove(date);
            issues.add(new Issue(DUPLICATE_CANDLE, date));
        }
        Reason issue = candleIssue(price, listedOn, calendar.sessionClose(date).orElseThrow());
        if (issue != null) {
            issues.add(new Issue(issue, date));
        } else if (first) {
            valid.put(date, price);
        }
    }

    static Reason candleIssue(DailyPrice price, LocalDate listedOn, Instant sessionClose) {
        if (price.observedAt() == null || price.ingestedAt() == null
                || price.observedAt().toInstant().isBefore(sessionClose) || price.ingestedAt().isBefore(price.observedAt())) {
            return INVALID_PRICE_AVAILABILITY;
        }
        if (price.adjustmentStatus() != AdjustmentStatus.UNADJUSTED) {
            return ADJUSTMENT_BASIS_UNVERIFIED;
        }
        if (price.tradingDate().isBefore(listedOn) || price.source() == null || price.source().isBlank()
                || !positive(price.open()) || !positive(price.high()) || !positive(price.low())
                || !positive(price.close()) || price.low().compareTo(price.open().min(price.close())) > 0
                || price.high().compareTo(price.open().max(price.close())) < 0 || price.volume() < 0
                || price.turnover() == null || price.turnover().signum() < 0
                || ((price.volume() == 0) != (price.turnover().signum() == 0))
                || (price.volume() == 0 && price.low().compareTo(price.high()) != 0)) {
            return INVALID_CANDLE;
        }
        return null;
    }

    private LiquidityMetrics metrics(List<DailyPrice> valid, Instant latestSessionClose) {
        var turnovers = valid.stream().map(DailyPrice::turnover).sorted().toList();
        int middle = turnovers.size() / 2;
        var median = turnovers.size() % 2 == 0
                ? turnovers.get(middle - 1).add(turnovers.get(middle)).divide(BigDecimal.TWO)
                : turnovers.get(middle);
        int active = (int) valid.stream().filter(bar -> bar.volume() > 0).count();
        int liquid = (int) valid.stream().filter(bar -> bar.volume() >= policy.minDailyVolumeShares()
                && bar.turnover().compareTo(policy.minDailyTurnoverIdr()) >= 0).count();
        var ratio = BigDecimal.valueOf(active).divide(BigDecimal.valueOf(valid.size()), 8, RoundingMode.HALF_UP);
        var latest = valid.getLast();
        var availableAt = valid.stream().map(price -> price.ingestedAt().toInstant())
                .max(Comparator.naturalOrder()).orElseThrow();
        return new LiquidityMetrics(latest.turnover(), median, latest.volume(), active, ratio, liquid,
                latest.source(), latestSessionClose, availableAt);
    }

    private static DataQuality priceQuality(List<Issue> issues, boolean stale) {
        if (issues.stream().anyMatch(issue -> switch (issue.reason()) {
            case INVALID_CANDLE, NON_TRADING_CANDLE, DUPLICATE_CANDLE, INVALID_PRICE_AVAILABILITY -> true;
            default -> false;
        })) {
            return DataQuality.INCOMPLETE;
        }
        if (issues.stream().anyMatch(issue -> issue.reason() == ADJUSTMENT_BASIS_UNVERIFIED
                || issue.reason() == MIXED_PRICE_SOURCE)) {
            return DataQuality.UNVERIFIED;
        }
        return stale ? DataQuality.STALE : DataQuality.INCOMPLETE;
    }

    private static boolean after(OffsetDateTime time, Instant cutoff) {
        return time != null && time.toInstant().isAfter(cutoff);
    }

    private static boolean positive(BigDecimal price) {
        return price != null && price.signum() > 0;
    }

    private static Decision rejected(Eligibility eligibility, DataQuality quality, int sessions, Reason reason) {
        return new Decision(eligibility, quality, null, new Completeness(sessions, 0, false),
                List.of(new Issue(reason, null)));
    }

    private static Result result(String symbol, String universeVersion, String calendarVersion,
                                 Instant cutoff, Decision decision) {
        return new Result(symbol, decision.eligibility(), decision.metrics(), decision.completeness(),
                cutoff, universeVersion, calendarVersion, decision.issues(), decision.quality());
    }

    private record Decision(Eligibility eligibility, DataQuality quality, LiquidityMetrics metrics,
                            Completeness completeness, List<Issue> issues) {}
}
