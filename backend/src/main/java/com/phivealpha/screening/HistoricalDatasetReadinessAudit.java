package com.phivealpha.screening;

import static com.phivealpha.screening.HistoricalDatasetReadiness.*;
import static com.phivealpha.screening.HistoricalDatasetReadiness.Reason.*;

import com.phivealpha.marketdata.apicoid.VersionedIdxCalendar;
import com.phivealpha.persistence.IdxRecords.AdjustmentStatus;
import com.phivealpha.persistence.IdxRecords.DailyPrice;
import com.phivealpha.research.HistoricalReplay.CorporateAction;
import com.phivealpha.research.HistoricalReplay.SessionBar;
import java.math.BigDecimal;
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
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** Explicit, read-only preflight for materialized inputs at one decision cutoff. */
@Service
public final class HistoricalDatasetReadinessAudit {
    private final LiquidityPolicy policy;

    public HistoricalDatasetReadinessAudit(LiquidityPolicy policy) {
        this.policy = policy;
    }

    public Report audit(Request request) {
        var frame = request.frame();
        var issues = new ArrayList<Issue>();
        if (frame == null) {
            issues.add(new Issue(FRAME_UNAVAILABLE, null));
            return report(request, Outcome.UNAVAILABLE, List.of(), List.of(), null, issues);
        }
        issues.addAll(vintageIssues(request));
        if (!issues.isEmpty()) {
            return report(request, Outcome.UNVERIFIED, List.of(), List.of(), null, issues);
        }
        if (Duration.between(frame.universe().asOf(), request.cutoff()).compareTo(policy.maxReferenceAge()) > 0) {
            issues.add(new Issue(STALE_UNIVERSE, null));
        }
        if (request.priceBasis() != AdjustmentStatus.UNADJUSTED) {
            issues.add(new Issue(UNSUPPORTED_PRICE_BASIS, null));
        }

        var calendar = frame.calendar().calendar();
        var calendarIssues = new ArrayList<LiquidUniverse.Issue>();
        var dates = LiquidUniverseService.closedDates(calendar, request.cutoff(), request.lookbackSessions(), calendarIssues)
                .reversed();
        if (!calendarIssues.isEmpty()) {
            issues.add(new Issue(CALENDAR_OUT_OF_COVERAGE, calendarIssues.getFirst().tradingDate()));
            return report(request, Outcome.UNAVAILABLE, dates, List.of(), null, issues);
        }

        var window = new AuditWindow(calendar, dates, request.cutoff());
        var members = group(frame.universe().members(), LiquidUniverse.Member::symbol);
        var prices = new HashMap<String, List<DailyPrice>>();
        for (var price : frame.dailyPrices()) {
            try {
                var symbol = LiquidUniverse.normalizeSymbol(price.symbol());
                prices.computeIfAbsent(symbol, ignored -> new ArrayList<>()).add(price);
            } catch (NullPointerException | IllegalArgumentException invalidSymbol) {
                issues.add(new Issue(INVALID_CANDLE_SYMBOL, price.tradingDate()));
            }
        }
        var evidence = group(request.sessionEvidence(), SessionBar::symbol);
        var equities = request.symbols().stream().map(symbol -> equity(request, window, symbol,
                members.getOrDefault(symbol, List.of()), prices.getOrDefault(symbol, List.of()),
                evidence.getOrDefault(symbol, List.of()))).toList();
        var benchmark = benchmark(window, evidence.getOrDefault(BENCHMARK, List.of()));
        var outcome = Outcome.READY;
        if (!issues.isEmpty() || equities.stream().anyMatch(row -> row.outcome() == Outcome.UNVERIFIED)
                || benchmark.outcome() == Outcome.UNVERIFIED) {
            outcome = Outcome.UNVERIFIED;
        } else if (equities.stream().allMatch(row -> row.outcome() == Outcome.UNAVAILABLE)) {
            outcome = Outcome.UNAVAILABLE;
        } else if (benchmark.outcome() != Outcome.READY || equities.stream().anyMatch(row -> row.outcome() != Outcome.READY)) {
            outcome = Outcome.INCOMPLETE;
        }
        return report(request, outcome, dates, equities, benchmark, issues);
    }

    static List<Issue> vintageIssues(Request request) {
        var frame = request.frame();
        if (frame == null) return List.of(new Issue(FRAME_UNAVAILABLE, null));
        var issues = new ArrayList<Issue>();
        if (frame.availableAt().isAfter(request.cutoff())) issues.add(new Issue(FRAME_AFTER_CUTOFF, null));
        if (frame.universe().availableAt().isAfter(frame.availableAt())) issues.add(new Issue(UNIVERSE_AFTER_FRAME, null));
        if (frame.calendar().availableAt().isAfter(frame.availableAt())) issues.add(new Issue(CALENDAR_AFTER_FRAME, null));
        return issues;
    }

    static Report storageUnavailable(Request request) {
        return report(request, Outcome.UNAVAILABLE, List.of(), List.of(), null,
                List.of(new Issue(HISTORICAL_STORAGE_UNAVAILABLE, null)));
    }

    private Series equity(Request request, AuditWindow window, String symbol, List<LiquidUniverse.Member> members,
                          List<DailyPrice> prices, List<SessionBar> evidence) {
        var issues = new ArrayList<Issue>();
        LiquidUniverse.Reason eligibility = null;
        if (members.isEmpty()) {
            issues.add(new Issue(MISSING_HISTORICAL_MEMBER, null));
        } else if (members.size() > 1) {
            issues.add(new Issue(DUPLICATE_HISTORICAL_MEMBER, null));
        } else {
            eligibility = LiquidUniverseService.instrumentIssue(members.getFirst(), window.dates().getFirst(),
                    window.calendar(), request.cutoff());
            if (eligibility != null) issues.add(new Issue(INELIGIBLE_HISTORICAL_MEMBER, null));
        }

        var byDate = group(prices.stream().filter(row -> {
            var date = row.tradingDate();
            if (date == null) issues.add(new Issue(INVALID_CANDLE_DATE, null));
            return date != null && !date.isBefore(window.dates().getFirst());
        }).toList(), DailyPrice::tradingDate);
        var expected = new HashSet<>(window.dates());
        byDate.forEach((date, rows) -> {
            if (!expected.contains(date)) {
                var reason = window.calendar().isTradingSession(date) ? SESSION_NOT_CLOSED : NON_TRADING_CANDLE;
                issues.add(new Issue(reason, date));
                rows.forEach(row -> inspectAvailability(request, window, date, row, issues));
            }
        });
        var observed = 0;
        var valid = new ArrayList<LocalDate>();
        for (var date : window.dates()) {
            var rows = byDate.getOrDefault(date, List.of());
            if (rows.isEmpty()) {
                issues.add(new Issue(MISSING_CANDLE, date));
                continue;
            }
            observed++;
            if (rows.size() > 1) issues.add(new Issue(DUPLICATE_CANDLE, date));
            int before = issues.size();
            if (members.size() == 1 && members.getFirst().listedOn() != null && date.isBefore(members.getFirst().listedOn())) {
                issues.add(new Issue(CANDLE_BEFORE_LISTING, date));
            }
            for (var row : rows) {
                inspectPrice(request, window, date, row, issues);
            }
            if (rows.size() == 1 && before == issues.size()) valid.add(date);
        }

        var verified = inspectEvidence(window, evidence, false, issues);
        var freshness = freshness(window, valid, issues);
        return new Series(symbol, outcome(issues, observed), observed, valid.size(), coverage(window, valid), freshness, eligibility,
                verified.versions(), ordered(issues));
    }

    private static void inspectPrice(Request request, AuditWindow window, LocalDate date, DailyPrice price,
                                     List<Issue> issues) {
        inspectAvailability(request, window, date, price, issues);
        if (!LiquidUniverseService.validCandleAvailability(price, window.calendar().sessionClose(date).orElseThrow())) {
            issues.add(new Issue(INVALID_PRICE_AVAILABILITY, date));
        }
        if (!request.dailyPriceSource().equals(price.source())) issues.add(new Issue(PRICE_SOURCE_MISMATCH, date));
        if (price.adjustmentStatus() != request.priceBasis() || request.priceBasis() != AdjustmentStatus.UNADJUSTED) {
            issues.add(new Issue(PRICE_BASIS_MISMATCH, date));
        }
        if (price.turnover() == null) issues.add(new Issue(TURNOVER_UNAVAILABLE, date));
        if (!LiquidUniverseService.validCandleValues(price)) issues.add(new Issue(INVALID_OHLCV, date));
    }

    private static void inspectAvailability(Request request, AuditWindow window, LocalDate date,
                                            DailyPrice price, List<Issue> issues) {
        if (after(price.observedAt(), window.cutoff())) issues.add(new Issue(OBSERVATION_AFTER_CUTOFF, date));
        if (after(price.ingestedAt(), window.cutoff())) issues.add(new Issue(INGESTION_AFTER_CUTOFF, date));
        if (after(price.observedAt(), request.frame().availableAt()) || after(price.ingestedAt(), request.frame().availableAt())) {
            issues.add(new Issue(PRICE_AFTER_FRAME, date));
        }
    }

    private Series benchmark(AuditWindow window, List<SessionBar> rows) {
        var issues = new ArrayList<Issue>();
        var verified = inspectEvidence(window, rows, true, issues);
        if (verified.observed() == 0) issues.add(new Issue(BENCHMARK_UNAVAILABLE, null));
        var freshness = freshness(window, verified.valid(), issues);
        return new Series(BENCHMARK, outcome(issues, verified.observed()), verified.observed(),
                verified.valid().size(), coverage(window, verified.valid()), freshness, null, verified.versions(), ordered(issues));
    }

    private static Evidence inspectEvidence(AuditWindow window, List<SessionBar> rows, boolean benchmark,
                                            List<Issue> issues) {
        var byDate = group(rows.stream().filter(row -> !row.date().isBefore(window.dates().getFirst())).toList(),
                SessionBar::date);
        var expected = new HashSet<>(window.dates());
        for (var date : byDate.keySet()) {
            if (!expected.contains(date)) {
                issues.add(new Issue(SESSION_EVIDENCE_OUTSIDE_WINDOW, date));
                if (byDate.get(date).stream().anyMatch(row -> row.availableAt().isAfter(window.cutoff()))) {
                    issues.add(new Issue(SESSION_EVIDENCE_AFTER_CUTOFF, date));
                }
            }
        }

        var valid = new ArrayList<LocalDate>();
        var versions = new ArrayList<String>();
        int observed = 0;
        for (var date : window.dates()) {
            var revisions = byDate.getOrDefault(date, List.of());
            if (revisions.isEmpty()) {
                issues.add(new Issue(benchmark ? BENCHMARK_SESSION_MISSING : SESSION_EVIDENCE_MISSING, date));
                continue;
            }
            observed++;
            if (revisions.size() > 1) issues.add(new Issue(DUPLICATE_SESSION_EVIDENCE, date));
            int before = issues.size();
            for (var row : revisions) inspectSession(window, row, benchmark, issues);
            if (revisions.size() == 1 && before == issues.size()) {
                valid.add(date);
                versions.add(revisions.getFirst().version());
            }
        }
        return new Evidence(observed, valid, versions.stream().distinct().sorted().toList());
    }

    private static void inspectSession(AuditWindow window, SessionBar row, boolean benchmark, List<Issue> issues) {
        var date = row.date();
        if (row.availableAt().isAfter(window.cutoff())) issues.add(new Issue(SESSION_EVIDENCE_AFTER_CUTOFF, date));
        if (row.availableAt().isBefore(window.calendar().sessionClose(date).orElseThrow())) {
            issues.add(new Issue(SESSION_EVIDENCE_BEFORE_CLOSE, date));
        }
        var listingIssue = switch (row.listing()) {
            case ACTIVE -> null;
            case SUSPENDED -> SESSION_SUSPENDED;
            case DELISTED -> SESSION_DELISTED;
            case UNKNOWN -> SESSION_LISTING_UNVERIFIED;
        };
        if (listingIssue != null) issues.add(new Issue(listingIssue, date));
        if (row.action() != CorporateAction.NONE) {
            issues.add(new Issue(SESSION_CORPORATE_ACTION, date));
        }
        if (benchmark && (!positive(row.open()) || !positive(row.close()))) {
            issues.add(new Issue(INVALID_BENCHMARK_PRICE, date));
        }
    }

    private static Coverage coverage(AuditWindow window, List<LocalDate> valid) {
        if (valid.isEmpty()) return Coverage.NONE;
        return valid.size() == window.dates().size() ? Coverage.COMPLETE : Coverage.PARTIAL;
    }

    private Freshness freshness(AuditWindow window, List<LocalDate> valid, List<Issue> issues) {
        if (valid.isEmpty()) return Freshness.NOT_ASSESSED;
        var latest = valid.getLast();
        var close = window.calendar().sessionClose(latest).orElseThrow();
        if (!latest.equals(window.dates().getLast()) || Duration.between(close, window.cutoff()).compareTo(policy.maxPriceAge()) > 0) {
            issues.add(new Issue(STALE_PRICE, latest));
            return Freshness.STALE;
        }
        return Freshness.CURRENT;
    }

    private static Outcome outcome(List<Issue> issues, int observed) {
        if (issues.isEmpty()) return Outcome.READY;
        if (issues.stream().anyMatch(issue -> switch (issue.reason()) {
            case MISSING_CANDLE, DUPLICATE_CANDLE, INVALID_OHLCV, TURNOVER_UNAVAILABLE, STALE_PRICE,
                    SESSION_SUSPENDED, SESSION_DELISTED, BENCHMARK_UNAVAILABLE,
                    BENCHMARK_SESSION_MISSING, INVALID_BENCHMARK_PRICE -> false;
            default -> true;
        })) return Outcome.UNVERIFIED;
        return observed == 0 ? Outcome.UNAVAILABLE : Outcome.INCOMPLETE;
    }

    private static Report report(Request request, Outcome outcome, List<LocalDate> dates,
                                 List<Series> equities, Series benchmark, List<Issue> issues) {
        var frame = request.frame();
        var universe = frame == null ? null : frame.universe();
        var calendar = frame == null ? null : frame.calendar();
        var provenance = new Provenance(request.datasetId(), request.datasetVersion(), request.datasetSource(),
                frame == null ? null : frame.version(), frame == null ? null : frame.asOf(),
                frame == null ? null : frame.availableAt(), frame == null ? null : frame.dailyPriceVersion(),
                request.dailyPriceSource(), request.priceBasis(), universe == null ? null : universe.version(),
                universe == null ? null : universe.source(), universe == null ? null : universe.asOf(),
                universe == null ? null : universe.availableAt(), calendar == null ? null : calendar.calendar().version(),
                calendar == null ? null : calendar.calendar().source(), calendar == null ? null : calendar.availableAt(),
                request.sessionEvidenceSource());
        return new Report(outcome, provenance, request.cutoff(), request.lookbackSessions(), dates, request.symbols(),
                equities, benchmark, ordered(issues));
    }

    private static List<Issue> ordered(List<Issue> issues) {
        return issues.stream().distinct().sorted(Comparator.comparing(Issue::reason)
                .thenComparing(Issue::tradingDate, Comparator.nullsFirst(Comparator.naturalOrder()))).toList();
    }

    private static boolean after(OffsetDateTime time, Instant cutoff) {
        return time != null && time.toInstant().isAfter(cutoff);
    }

    private static boolean positive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    private static <T, K> Map<K, List<T>> group(List<T> rows, Function<T, K> key) {
        return rows.stream().collect(Collectors.groupingBy(key));
    }

    private record AuditWindow(VersionedIdxCalendar calendar, List<LocalDate> dates, Instant cutoff) {}
    private record Evidence(int observed, List<LocalDate> valid, List<String> versions) {}
}
