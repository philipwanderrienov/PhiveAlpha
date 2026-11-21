package com.phivealpha.persistence;

import static com.phivealpha.marketdata.apicoid.ApiCoIdData.*;
import static com.phivealpha.persistence.IdxRecords.*;

import com.phivealpha.screening.LiquidUniverse.CalendarReference;
import com.phivealpha.screening.LiquidUniverse.Evaluation;
import com.phivealpha.screening.LiquidUniverse.Reference;
import com.phivealpha.screening.LiquidUniverseService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@Profile("phivealpha-persistence & phivealpha-daily-ingestion")
public class IdxDailyIngestionService {
    public static final String FIXTURE = "fixture-phivealpha";

    @FunctionalInterface
    public interface Feed {
        Mono<Snapshot<DailyBar>> dailyOhlcv(String symbol, LocalDate from, LocalDate through);
    }

    public enum WriteStatus { INSERTED, ALREADY_EXISTS, REJECTED, FAILED }
    public record CandleResult(LocalDate session, WriteStatus status) {}
    public record Summary(Quality quality, String reason, List<CandleResult> candles, boolean auditRecorded) {
        public Summary {
            candles = List.copyOf(candles);
        }
    }

    private final IdxPersistenceService persistence;
    private final LiquidUniverseService screening;
    private final Clock clock;

    @Autowired
    IdxDailyIngestionService(IdxPersistenceService persistence, LiquidUniverseService screening,
            @Value("${phivealpha.persistence.url}") String url,
            @Value("${phivealpha.persistence.username}") String username) {
        this(persistence, screening, Clock.systemUTC());
        IdxDevelopmentDatabase.requireIsolated(url, username);
    }

    IdxDailyIngestionService(IdxPersistenceService persistence, LiquidUniverseService screening, Clock clock) {
        this.persistence = persistence;
        this.screening = screening;
        this.clock = clock;
    }

    public Mono<Summary> ingest(Feed feed, String symbol, LocalDate from, LocalDate through,
                                CalendarReference reference, String universeVersion) {
        return Mono.defer(() -> {
            String normalized = Objects.requireNonNull(symbol).strip().toUpperCase(Locale.ROOT);
            var source = source(reference, universeVersion);
            var calendar = reference.calendar();
            var now = clock.instant();
            if (!normalized.matches("[A-Z0-9]{1,16}") || from == null || through == null
                    || through.isBefore(from) || ChronoUnit.DAYS.between(from, through) > 365
                    || through.isAfter(now.atZone(IdxMarketCalendar.ZONE).toLocalDate())
                    || !calendar.covers(from, through)) {
                return result(Quality.INCOMPLETE, "INVALID_RANGE_OR_SYMBOL");
            }
            if (reference.availableAt().isAfter(now)) {
                return result(Quality.UNVERIFIED, "CALENDAR_NOT_AVAILABLE");
            }
            var sessions = from.datesUntil(through.plusDays(1)).filter(calendar::isTradingSession).toList();
            if (sessions.isEmpty() || sessions.stream().anyMatch(date -> !calendar.isClosedBy(date, now))) {
                return result(Quality.INCOMPLETE, "NO_CLOSED_SESSION_OR_OPEN_SESSION");
            }

            return Mono.defer(() -> feed.dailyOhlcv(normalized, from, through))
                    .onErrorResume(error -> Mono.just(new Snapshot<>(Quality.UNAVAILABLE,
                            "PROVIDER_FAILURE", List.of(), null)))
                    .flatMap(snapshot -> validateAndStore(snapshot, normalized, sessions, reference, universeVersion, source))
                    .switchIfEmpty(result(Quality.UNAVAILABLE, "PROVIDER_EMPTY"));
        });
    }

    private Mono<Summary> validateAndStore(Snapshot<DailyBar> snapshot, String symbol, List<LocalDate> sessions,
            CalendarReference reference, String universeVersion, String source) {
        if (snapshot.quality() != Quality.COMPLETE) {
            return result(snapshot.quality(), snapshot.reason());
        }
        var evidence = snapshot.provenance();
        var calendar = reference.calendar();
        Instant now = clock.instant();
        // Only synthetic, explicitly reviewed input is authorized for storage in this phase.
        if (evidence == null || !FIXTURE.equals(evidence.provider()) || !"DAILY".equals(evidence.dataset())
                || !calendar.source().equals(evidence.calendarSource())
                || !calendar.version().equals(evidence.calendarVersion())
                || !universeVersion.equals(evidence.universeVersion())
                || evidence.asOf() == null || evidence.fetchedAt() == null || evidence.ageAnchor() == null
                || evidence.ageAnchor().isAfter(evidence.asOf()) || evidence.asOf().isAfter(evidence.fetchedAt())
                || evidence.fetchedAt().isAfter(now)) {
            return result(Quality.UNVERIFIED, "INVALID_PROVENANCE");
        }
        if (Duration.between(evidence.ageAnchor(), now).compareTo(screening.policy().maxPriceAge()) > 0) {
            return result(Quality.STALE, "MAX_AGE_EXCEEDED");
        }
        if (evidence.asOf().isBefore(calendar.sessionClose(sessions.getLast()).orElseThrow())) {
            return result(Quality.INCOMPLETE, "OBSERVATION_BEFORE_CLOSE");
        }

        var bars = new HashMap<LocalDate, DailyBar>();
        for (var bar : snapshot.rows()) {
            if (!symbol.equals(bar.symbol()) || !sessions.contains(bar.tradingDate())
                    || bars.putIfAbsent(bar.tradingDate(), bar) != null) {
                return rejected("DUPLICATE_OR_UNEXPECTED_CANDLE", sessions);
            }
            if (bar.adjustment() == null || bar.adjustment() == AdjustmentStatus.UNKNOWN) {
                return rejected(Quality.UNVERIFIED, "ADJUSTMENT_BASIS_UNVERIFIED", sessions);
            }
        }
        if (bars.size() != sessions.size()) {
            return rejected("MISSING_CANDLE", sessions);
        }
        if (bars.values().stream().map(DailyBar::adjustment).distinct().count() != 1) {
            return rejected(Quality.UNVERIFIED, "MIXED_ADJUSTMENT_BASIS", sessions);
        }
        var prices = sessions.stream().map(date -> normalize(bars.get(date), source, evidence.fetchedAt(), now)).toList();
        if (prices.stream().anyMatch(price -> !IdxPersistenceService.validDailyValues(price)
                || (price.turnover() != null && ((price.volume() == 0) != (price.turnover().signum() == 0)))
                || (price.volume() == 0 && price.low().compareTo(price.high()) != 0))) {
            return rejected("INVALID_CANDLE", sessions);
        }

        return Flux.fromIterable(sessions).concatMap(date -> persist(
                        normalize(bars.get(date), source, evidence.fetchedAt(), clock.instant()), reference))
                .collectList().flatMap(outcomes -> audit(symbol, outcomes, now));
    }

    private Mono<CandleResult> persist(DailyPrice price, CalendarReference reference) {
        if (price.ingestedAt().isBefore(price.observedAt())) {
            return Mono.just(new CandleResult(price.tradingDate(), WriteStatus.FAILED));
        }
        return persistence.saveDailyPrice(price, reference.calendar())
                .flatMap(inserted -> inserted ? Mono.just(WriteStatus.INSERTED)
                        : persistence.historicalPrices(price.symbol(), price.adjustmentStatus(), price.tradingDate(),
                                price.tradingDate(), price.ingestedAt())
                                .next().map(existing -> equivalent(existing, price)
                                        ? WriteStatus.ALREADY_EXISTS : WriteStatus.REJECTED)
                                .defaultIfEmpty(WriteStatus.REJECTED))
                .onErrorReturn(IdxPersistenceService.PersistenceException.class, WriteStatus.FAILED)
                .onErrorReturn(IllegalArgumentException.class, WriteStatus.REJECTED)
                .map(status -> new CandleResult(price.tradingDate(), status));
    }

    private Mono<Summary> audit(String symbol, List<CandleResult> outcomes, Instant now) {
        long failed = outcomes.stream().filter(row -> row.status() == WriteStatus.FAILED).count();
        long rejected = outcomes.stream().filter(row -> row.status() == WriteStatus.REJECTED).count();
        var quality = failed == outcomes.size() ? Quality.UNAVAILABLE
                : failed + rejected > 0 ? Quality.INCOMPLETE : Quality.COMPLETE;
        String reason = failed > 0 ? "STORAGE_FAILURE" : rejected > 0 ? "HISTORICAL_CONFLICT" : "OK";
        var status = quality == Quality.COMPLETE ? IngestionStatus.SUCCESS
                : failed + rejected == outcomes.size() ? IngestionStatus.FAILED : IngestionStatus.PARTIAL;
        return persistence.recordIngestion(new Ingestion(FIXTURE, "DAILY", symbol, ceiling(now), status,
                        quality == Quality.COMPLETE ? "" : reason, outcomes.size(), rejected,
                        0, 0, "FIXTURE_NO_QUOTA"))
                .map(id -> new Summary(quality, reason, outcomes, true))
                .onErrorReturn(IdxPersistenceService.PersistenceException.class,
                        new Summary(quality, reason, outcomes, false));
    }

    /** Reads the supplied historical universe, including members absent from today's universe. */
    public Mono<Evaluation> evaluate(Reference universe, CalendarReference calendar, Instant cutoff) {
        return Mono.defer(() -> {
            var window = screening.priceWindow(calendar, cutoff);
            if (window.isEmpty() || universe.availableAt().isAfter(cutoff)
                    || Duration.between(universe.asOf(), cutoff).compareTo(screening.policy().maxReferenceAge()) > 0) {
                return Mono.just(screening.evaluate(universe, calendar, List.of(), cutoff));
            }
            var prefix = sourcePrefix(calendar);
            return Flux.fromIterable(universe.members()).map(member -> member.symbol()).distinct()
                    .concatMap(symbol -> persistence.historicalPrices(symbol, AdjustmentStatus.UNADJUSTED,
                            window.getLast(), window.getFirst(), cutoff.atOffset(ZoneOffset.UTC)))
                    .filter(price -> price.source().startsWith(prefix))
                    .map(price -> new DailyPrice(price.symbol(), price.tradingDate(), price.open(), price.high(),
                            price.low(), price.close(), price.volume(), price.turnover(), prefix,
                            price.observedAt(), price.ingestedAt(), price.adjustmentStatus()))
                    .collectList().map(prices -> screening.evaluate(universe, calendar, prices, cutoff));
        });
    }

    private static DailyPrice normalize(DailyBar bar, String source, Instant observed, Instant now) {
        return new DailyPrice(bar.symbol(), bar.tradingDate(), bar.openIdr(), bar.highIdr(), bar.lowIdr(),
                bar.closeIdr(), bar.volume(), bar.turnoverIdr(), source, ceiling(observed), ceiling(now), bar.adjustment());
    }

    private static OffsetDateTime ceiling(Instant instant) {
        var floor = instant.truncatedTo(ChronoUnit.MICROS);
        return (floor.equals(instant) ? floor : floor.plus(1, ChronoUnit.MICROS)).atOffset(ZoneOffset.UTC);
    }

    private static boolean equivalent(DailyPrice left, DailyPrice right) {
        int versionStart = right.source().lastIndexOf('/') + 1;
        return left.source().startsWith(right.source().substring(0, versionStart))
                && left.volume() == right.volume()
                && sameDecimal(left.open(), right.open()) && sameDecimal(left.high(), right.high())
                && sameDecimal(left.low(), right.low()) && sameDecimal(left.close(), right.close())
                && sameDecimal(left.turnover(), right.turnover());
    }

    private static boolean sameDecimal(BigDecimal left, BigDecimal right) {
        return left == null || right == null ? left == right : left.compareTo(right) == 0;
    }

    private static String source(CalendarReference reference, String universeVersion) {
        var calendarVersion = reference.calendar().version();
        if (universeVersion == null || !universeVersion.matches("[A-Za-z0-9._-]{1,80}")
                || !calendarVersion.matches("[A-Za-z0-9._-]{1,80}")) {
            throw new IllegalArgumentException("Invalid daily provenance version");
        }
        return sourcePrefix(reference) + universeVersion;
    }

    private static String sourcePrefix(CalendarReference reference) {
        return FIXTURE + "/daily/" + reference.calendar().version() + "/";
    }

    private static Mono<Summary> result(Quality quality, String reason) {
        return Mono.just(new Summary(quality, reason, List.of(), false));
    }

    private static Mono<Summary> rejected(String reason, List<LocalDate> sessions) {
        return rejected(Quality.INCOMPLETE, reason, sessions);
    }

    private static Mono<Summary> rejected(Quality quality, String reason, List<LocalDate> sessions) {
        return Mono.just(new Summary(quality, reason,
                sessions.stream().map(date -> new CandleResult(date, WriteStatus.REJECTED)).toList(), false));
    }
}
