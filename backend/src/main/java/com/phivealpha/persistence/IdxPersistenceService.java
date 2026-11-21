package com.phivealpha.persistence;

import static com.phivealpha.persistence.IdxRecords.*;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Locale;
import java.util.concurrent.TimeoutException;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@Profile("phivealpha-persistence")
public class IdxPersistenceService {
    private static final Duration DEADLINE = Duration.ofSeconds(5);
    private final IdxRepository repository;
    private final TransactionalOperator transactions;

    IdxPersistenceService(IdxRepository repository, TransactionalOperator transactions) {
        this.repository = repository;
        this.transactions = transactions;
    }

    /** Returns true when an instrument was inserted or refreshed by a newer observation. */
    public Mono<Boolean> upsertInstrument(Instrument instrument) {
        return write(Mono.defer(() -> {
            required(instrument.name(), instrument.sector(), instrument.instrumentType(), instrument.listingStatus());
            check(instrument.currency() != null && instrument.currency().matches("[A-Z]{3}")
                    && instrument.lotSize() > 0, "Invalid currency or lot size");
            timestamp(instrument.updatedAt());
            return repository.upsert(new Instrument(symbol(instrument.symbol()), instrument.name(),
                    instrument.sector(), instrument.instrumentType(), instrument.listingStatus(),
                    instrument.currency(), instrument.lotSize(), instrument.updatedAt())).map(count -> count == 1);
        }));
    }

    /** Returns true on first insertion for the symbol, date and adjustment basis. */
    public Mono<Boolean> saveDailyPrice(DailyPrice price, IdxMarketCalendar calendar) {
        return write(Mono.defer(() -> {
            String symbol = symbol(price.symbol());
            check(price.tradingDate() != null && calendar != null && calendar.isTradingSession(price.tradingDate()),
                    "Date is outside confirmed IDX trading sessions");
            required(price.source());
            check(price.adjustmentStatus() != null, "Price adjustment basis is required");
            chronological(price.observedAt(), price.ingestedAt());
            check(!price.tradingDate().isAfter(price.observedAt().atZoneSameInstant(IdxMarketCalendar.ZONE).toLocalDate()),
                    "Observation precedes trading date");
            check(validDailyValues(price), "Invalid daily price values");
            return repository.insert(new DailyPrice(symbol, price.tradingDate(), price.open(), price.high(),
                    price.low(), price.close(), price.volume(), price.turnover(), price.source(),
                    price.observedAt(), price.ingestedAt(), price.adjustmentStatus())).map(count -> count == 1);
        }));
    }

    static boolean validDailyValues(DailyPrice price) {
        return positive(price.open()) && positive(price.close()) && positive(price.low())
                && positive(price.high()) && price.high().compareTo(price.open().max(price.close())) >= 0
                && price.low().compareTo(price.open().min(price.close())) <= 0 && price.volume() >= 0
                && (price.turnover() == null || price.turnover().signum() >= 0);
    }

    public Flux<DailyPrice> historicalPrices(String symbol, AdjustmentStatus basis, LocalDate from,
                                             LocalDate through, OffsetDateTime cutoff) {
        return read(Flux.defer(() -> {
            check(basis != null && from != null && through != null && cutoff != null && !from.isAfter(through),
                    "Invalid price basis or date range");
            return repository.history(symbol(symbol), basis, from, through, cutoff.truncatedTo(ChronoUnit.MICROS));
        }));
    }

    /** Returns the canonical ID. The article, alias and issuer links commit together. */
    public Mono<String> saveNews(News news, Collection<String> symbols) {
        return write(Mono.defer(() -> {
            required(news.articleId(), news.title(), news.source(), news.sourceUrl(), news.provenance());
            URI url = URI.create(news.sourceUrl());
            check(("https".equalsIgnoreCase(url.getScheme()) || "http".equalsIgnoreCase(url.getScheme()))
                    && url.getHost() != null && url.getUserInfo() == null, "Invalid news URL");
            check(news.contentHash() != null && news.contentHash().matches("[a-f0-9]{64}")
                    && news.validationStatus() != null, "Invalid news hash or validation status");
            chronological(news.publishedAt(), news.firstSeenAt(), news.ingestedAt(), news.availableAt());
            check(symbols != null, "IDX news symbols collection is required");
            var links = symbols.stream().map(IdxPersistenceService::symbol).distinct().sorted().toList();

            return repository.insert(news)
                    .then(repository.articleForHash(news.contentHash()))
                    .switchIfEmpty(Mono.error(articleConflict()))
                    .flatMap(canonical -> repository.reserveAlias(news.articleId(), canonical.articleId())
                            .flatMap(resolved -> {
                                if (!canonical.articleId().equals(resolved)) {
                                    return Mono.error(articleConflict());
                                }
                                // Pending/rejected duplicates cannot introduce a signal to a new issuer.
                                if (canonical.validationStatus() == ValidationStatus.VALIDATED
                                        && news.validationStatus() != ValidationStatus.VALIDATED) {
                                    return Mono.just(resolved);
                                }
                                return Flux.fromIterable(links)
                                        .concatMap(symbol -> repository.link(resolved, symbol, news.availableAt()))
                                        .then(Mono.just(resolved));
                            }))
                    .as(transactions::transactional);
        }));
    }

    public Flux<News> newsAvailableAt(String symbol, OffsetDateTime cutoff, int limit) {
        return read(Flux.defer(() -> {
            check(cutoff != null && limit >= 1 && limit <= 1000, "Invalid news cutoff or limit");
            return repository.availableNews(symbol(symbol), cutoff.truncatedTo(ChronoUnit.MICROS), limit);
        }));
    }

    /** Records request budget and rejected rows independently of a failed ingestion transaction. */
    public Mono<Long> recordIngestion(Ingestion ingestion) {
        return write(Mono.defer(() -> {
            required(ingestion.provider(), ingestion.dataset(), ingestion.scope(), ingestion.budgetWindow());
            timestamp(ingestion.fetchedAt());
            check(ingestion.status() != null && ingestion.errorCategory() != null
                    && (ingestion.status() == IngestionStatus.SUCCESS || !ingestion.errorCategory().isBlank())
                    && ingestion.recordCount() >= 0 && ingestion.rejectedCount() >= 0
                    && ingestion.rejectedCount() <= ingestion.recordCount()
                    && ingestion.requestsUsed() >= 0 && ingestion.requestBudget() >= 0,
                    "Invalid ingestion metadata");
            return repository.insert(ingestion);
        }));
    }

    private static String symbol(String value) {
        check(value != null, "IDX symbol is required");
        String normalized = value.strip().toUpperCase(Locale.ROOT);
        check(normalized.matches("[A-Z0-9]{1,16}"), "Invalid IDX symbol");
        return normalized;
    }

    private static void required(String... values) {
        for (String value : values) {
            check(value != null && !value.isBlank(), "Required IDX text is missing");
        }
    }

    private static void chronological(OffsetDateTime... values) {
        OffsetDateTime previous = null;
        for (OffsetDateTime value : values) {
            timestamp(value);
            check(previous == null || !value.isBefore(previous), "IDX timestamps are out of order");
            previous = value;
        }
    }

    private static void timestamp(OffsetDateTime value) {
        check(value != null && value.getNano() % 1000 == 0, "IDX timestamp must have microsecond precision or coarser");
    }

    private static boolean positive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    private static void check(boolean valid, String message) {
        if (!valid) {
            throw new IllegalArgumentException(message);
        }
    }

    private static IllegalArgumentException articleConflict() {
        return new IllegalArgumentException("Article identifier is already associated with different content");
    }

    private static <T> Mono<T> write(Mono<T> result) {
        return result.timeout(DEADLINE).onErrorMap(IdxPersistenceService::databaseError,
                cause -> new PersistenceException("IDX write failed", cause));
    }

    private static <T> Flux<T> read(Flux<T> result) {
        return result.takeUntilOther(Mono.delay(DEADLINE).then(Mono.error(new TimeoutException())))
                .onErrorMap(IdxPersistenceService::databaseError,
                        cause -> new PersistenceException("IDX read failed", cause));
    }

    private static boolean databaseError(Throwable cause) {
        return cause instanceof DataAccessException || cause instanceof TransactionException
                || cause instanceof TimeoutException;
    }

    public static final class PersistenceException extends RuntimeException {
        private PersistenceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
