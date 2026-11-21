package com.phivealpha.persistence;

import static com.phivealpha.marketdata.apicoid.ApiCoIdData.*;
import static com.phivealpha.persistence.IdxDailyIngestionService.WriteStatus.*;
import static com.phivealpha.persistence.IdxRecords.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.phivealpha.screening.LiquidUniverse.*;
import com.phivealpha.screening.LiquidUniverseService;
import com.phivealpha.screening.LiquidityPolicy;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class IdxDailyIngestionTest {
    static final LocalDate FIRST = LocalDate.parse("2026-11-11");
    static final LocalDate LAST = LocalDate.parse("2026-11-13");
    static final Instant NOW = Instant.parse("2026-11-13T10:00:00.123456789Z");
    static final Instant STORED = Instant.parse("2026-11-13T10:00:00.123457Z");
    static final FixtureDailyOhlcvFeed FEED = new FixtureDailyOhlcvFeed();
    private final MemoryPersistence storage = new MemoryPersistence();
    private final IdxDailyIngestionService service = service(storage, NOW);

    static IdxDailyIngestionService service(IdxPersistenceService persistence, Instant time) {
        var policy = new LiquidityPolicy(2, 2, 1, BigDecimal.ONE, 1, BigDecimal.ONE, BigDecimal.ONE,
                1, Duration.ofDays(7), Duration.ofDays(7));
        return new IdxDailyIngestionService(persistence, new LiquidUniverseService(policy),
                Clock.fixed(time, ZoneOffset.UTC));
    }

    static Reference universe() {
        return new Reference(FEED.universeVersion(), NOW.minusSeconds(7200), NOW.minusSeconds(3600),
                "synthetic historical universe", List.of(new Member("BBCA", SecurityType.COMMON_STOCK,
                        ListingStatus.ACTIVE, FIRST.minusYears(1), CorporateActionStatus.CLEAR)));
    }

    @Test
    void insertsIdempotentlyAndReadsOnlyAvailabilityAtTheCutoff() {
        var first = ingest(FEED);
        assertThat(first.quality()).isEqualTo(Quality.COMPLETE);
        assertThat(first.auditRecorded()).isTrue();
        assertThat(first.candles()).extracting(IdxDailyIngestionService.CandleResult::status)
                .containsExactly(INSERTED, INSERTED);
        assertThat(service(storage, NOW.plusSeconds(1))
                .ingest(FEED, "BBCA", FIRST, LAST, FEED.calendar(), FEED.universeVersion())
                .block().candles()).extracting(IdxDailyIngestionService.CandleResult::status)
                .containsExactly(ALREADY_EXISTS, ALREADY_EXISTS);
        assertThat(storage.prices).hasSize(2).allSatisfy(price -> {
            assertThat(price.source()).contains(FEED.universeVersion());
            assertThat(price.ingestedAt().toInstant()).isEqualTo(STORED);
            assertThat(price.observedAt().toInstant()).isBefore(NOW);
        });
        assertThat(storage.audits).hasSize(2).allSatisfy(audit -> assertThat(audit.requestsUsed()).isZero());
        assertThat(service.evaluate(universe(), FEED.calendar(), NOW).block().results().getFirst().dataQuality())
                .isEqualTo(DataQuality.INCOMPLETE);
        var reopened = service(storage, NOW.plusSeconds(1));
        assertThat(reopened.evaluate(universe(), FEED.calendar(), STORED).block().results().getFirst().eligibility())
                .isEqualTo(Eligibility.ELIGIBLE);
    }

    @Test
    void rejectsInvalidPriceNegativeVolumeAndNegativeTurnoverBeforeWriting() {
        assertRejected(change(bar -> new DailyBar(bar.symbol(), bar.tradingDate(), bar.openIdr(), BigDecimal.ONE,
                bar.lowIdr(), bar.closeIdr(), bar.volume(), bar.turnoverIdr(), bar.adjustment())), "INVALID_CANDLE");
        assertRejected(change(bar -> withVolumeAndTurnover(bar, -1, bar.turnoverIdr())), "INVALID_CANDLE");
        assertRejected(change(bar -> withVolumeAndTurnover(bar, bar.volume(), BigDecimal.valueOf(-1))), "INVALID_CANDLE");
    }

    @Test
    void missingTurnoverIsPreservedAndScreeningRejectsIt() {
        var summary = ingest(change(bar -> withVolumeAndTurnover(bar, bar.volume(), null)));
        assertThat(summary.quality()).isEqualTo(Quality.COMPLETE);
        assertThat(storage.prices.getFirst().turnover()).isNull();
        assertThat(service.evaluate(universe(), FEED.calendar(), STORED).block().results().getFirst().dataQuality())
                .isEqualTo(DataQuality.INCOMPLETE);
    }

    @Test
    void rejectsDuplicateMissingAndIncompleteBatches() {
        var fixture = snapshot();
        assertRejected(feed(new Snapshot<>(Quality.COMPLETE, "fixture",
                List.of(fixture.rows().getFirst(), fixture.rows().getFirst()), fixture.provenance())),
                "DUPLICATE_OR_UNEXPECTED_CANDLE");
        assertRejected(feed(new Snapshot<>(Quality.COMPLETE, "fixture", List.of(), fixture.provenance())),
                "MISSING_CANDLE");
        assertRejected(feed(new Snapshot<>(Quality.INCOMPLETE, "PAGINATION_INCOMPLETE",
                fixture.rows(), fixture.provenance())), "PAGINATION_INCOMPLETE");
    }

    @Test
    void rejectsOpenSessionBeforeCallingProviderAndRejectsPrecloseObservation() {
        var calls = new AtomicInteger();
        var early = Instant.parse("2026-11-13T04:29:00Z");
        var summary = service(storage, early).ingest((symbol, from, to) -> {
            calls.incrementAndGet();
            return FEED.dailyOhlcv(symbol, from, to);
        }, "BBCA", FIRST, LAST, FEED.calendar(), FEED.universeVersion()).block();
        assertThat(summary.quality()).isEqualTo(Quality.INCOMPLETE);
        assertThat(calls.get()).isZero();
        var evidence = snapshot().provenance();
        assertRejected(feed(new Snapshot<>(Quality.COMPLETE, "fixture", snapshot().rows(),
                new Provenance(evidence.provider(), evidence.dataset(), early, evidence.fetchedAt(),
                        evidence.calendarSource(), evidence.calendarVersion(), evidence.universeVersion(), early))),
                "OBSERVATION_BEFORE_CLOSE");
    }

    @Test
    void doesNotPromoteUnknownBasisExternalProvenanceOrProviderDenials() {
        var unknown = change(bar -> new DailyBar(bar.symbol(), bar.tradingDate(), bar.openIdr(), bar.highIdr(),
                bar.lowIdr(), bar.closeIdr(), bar.volume(), bar.turnoverIdr(), AdjustmentStatus.UNKNOWN));
        assertThat(ingest(unknown).quality()).isEqualTo(Quality.UNVERIFIED);
        var data = snapshot();
        var p = data.provenance();
        var external = new Provenance("API.co.id", p.dataset(), p.asOf(), p.fetchedAt(), p.calendarSource(),
                p.calendarVersion(), p.universeVersion(), p.ageAnchor());
        assertThat(ingest(feed(new Snapshot<>(Quality.COMPLETE, "fixture", data.rows(), external))).quality())
                .isEqualTo(Quality.UNVERIFIED);
        for (Quality quality : List.of(Quality.UNAVAILABLE, Quality.STALE, Quality.UNVERIFIED)) {
            assertThat(ingest(feed(new Snapshot<>(quality, "PROVIDER_STATUS", data.rows(), external))).quality())
                    .isEqualTo(quality);
        }
        assertThat(ingest((symbol, from, to) -> Mono.error(new IllegalStateException("sensitive"))).reason())
                .isEqualTo("PROVIDER_FAILURE");
        assertThat(storage.prices).isEmpty();
    }

    @Test
    void leavesOriginalCandleOnConflictingRetry() {
        ingest(FEED);
        var conflict = ingest(change(bar -> withVolumeAndTurnover(bar, bar.volume() + 1, bar.turnoverIdr())));
        assertThat(conflict.reason()).isEqualTo("HISTORICAL_CONFLICT");
        assertThat(conflict.candles()).extracting(IdxDailyIngestionService.CandleResult::status)
                .containsExactly(REJECTED, ALREADY_EXISTS);
        assertThat(storage.prices.getFirst().volume()).isEqualTo(snapshot().rows().getFirst().volume());
    }

    private void assertRejected(IdxDailyIngestionService.Feed provider, String reason) {
        assertThat(ingest(provider).reason()).isEqualTo(reason);
        assertThat(storage.prices).isEmpty();
    }

    private IdxDailyIngestionService.Summary ingest(IdxDailyIngestionService.Feed feed) {
        return service.ingest(feed, " bbca ", FIRST, LAST, FEED.calendar(), FEED.universeVersion()).block();
    }

    private static Snapshot<DailyBar> snapshot() {
        return FEED.dailyOhlcv("BBCA", FIRST, LAST).block();
    }

    private static IdxDailyIngestionService.Feed change(UnaryOperator<DailyBar> update) {
        var data = snapshot();
        return feed(new Snapshot<>(data.quality(), data.reason(),
                List.of(update.apply(data.rows().getFirst()), data.rows().getLast()), data.provenance()));
    }

    private static IdxDailyIngestionService.Feed feed(Snapshot<DailyBar> snapshot) {
        return (symbol, from, to) -> Mono.just(snapshot);
    }

    private static DailyBar withVolumeAndTurnover(DailyBar bar, long volume, BigDecimal turnover) {
        return new DailyBar(bar.symbol(), bar.tradingDate(), bar.openIdr(), bar.highIdr(), bar.lowIdr(), bar.closeIdr(),
                volume, turnover, bar.adjustment());
    }

    private static final class MemoryPersistence extends IdxPersistenceService {
        private final List<DailyPrice> prices = new ArrayList<>();
        private final List<Ingestion> audits = new ArrayList<>();

        MemoryPersistence() {
            super(null, null);
        }

        @Override
        public Mono<Boolean> saveDailyPrice(DailyPrice price, IdxMarketCalendar calendar) {
            if (prices.stream().anyMatch(row -> row.symbol().equals(price.symbol())
                    && row.tradingDate().equals(price.tradingDate()) && row.adjustmentStatus() == price.adjustmentStatus())) {
                return Mono.just(false);
            }
            prices.add(price);
            return Mono.just(true);
        }

        @Override
        public Flux<DailyPrice> historicalPrices(String symbol, AdjustmentStatus basis, LocalDate from,
                                                LocalDate through, OffsetDateTime cutoff) {
            return Flux.fromIterable(prices).filter(row -> row.symbol().equals(symbol) && row.adjustmentStatus() == basis
                    && !row.tradingDate().isBefore(from) && !row.tradingDate().isAfter(through)
                    && !row.ingestedAt().isAfter(cutoff));
        }

        @Override
        public Mono<Long> recordIngestion(Ingestion ingestion) {
            audits.add(ingestion);
            return Mono.just((long) audits.size());
        }
    }
}
