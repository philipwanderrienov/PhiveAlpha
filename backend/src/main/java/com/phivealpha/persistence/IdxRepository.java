package com.phivealpha.persistence;

import static com.phivealpha.persistence.IdxRecords.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.springframework.context.annotation.Profile;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
@Profile("phivealpha-persistence")
class IdxRepository {
    private final DatabaseClient db;

    IdxRepository(R2dbcEntityTemplate template) {
        this.db = template.getDatabaseClient();
    }

    Mono<Long> upsert(Instrument instrument) {
        return db.sql("""
                INSERT INTO phivealpha_idx.idx_instruments AS existing
                    (symbol, name, sector, instrument_type, listing_status, currency, lot_size, updated_at)
                VALUES (:symbol, :name, :sector, :instrumentType, :listingStatus, :currency, :lotSize, :updatedAt)
                ON CONFLICT (symbol) DO UPDATE SET name = EXCLUDED.name, sector = EXCLUDED.sector,
                    instrument_type = EXCLUDED.instrument_type, listing_status = EXCLUDED.listing_status,
                    currency = EXCLUDED.currency, lot_size = EXCLUDED.lot_size, updated_at = EXCLUDED.updated_at
                WHERE EXCLUDED.updated_at > existing.updated_at
                """).bindProperties(instrument).fetch().rowsUpdated();
    }

    Mono<Long> insert(DailyPrice price) {
        return db.sql("""
                INSERT INTO phivealpha_idx.idx_daily_prices
                    (symbol, trading_date, open, high, low, close, volume, turnover, source, observed_at,
                     ingested_at, adjustment_status)
                VALUES (:symbol, :tradingDate, :open, :high, :low, :close, :volume, :turnover, :source,
                        :observedAt, :ingestedAt, :adjustmentStatus)
                ON CONFLICT (symbol, adjustment_status, trading_date) DO NOTHING
                """).bindProperties(price).bind("adjustmentStatus", price.adjustmentStatus().name())
                .fetch().rowsUpdated();
    }

    Flux<DailyPrice> history(String symbol, AdjustmentStatus basis, LocalDate from, LocalDate through,
                             OffsetDateTime cutoff) {
        return db.sql("""
                SELECT * FROM phivealpha_idx.idx_daily_prices
                WHERE symbol = :symbol AND adjustment_status = :basis AND trading_date BETWEEN :from AND :through
                    AND ingested_at <= :cutoff
                ORDER BY trading_date
                """).bind("symbol", symbol).bind("basis", basis.name()).bind("from", from).bind("through", through)
                .bind("cutoff", cutoff)
                .mapProperties(DailyPrice.class).all();
    }

    Mono<Long> insert(News news) {
        return db.sql("""
                INSERT INTO phivealpha_idx.idx_news
                    (article_id, title, source, source_url, published_at, first_seen_at, ingested_at, available_at,
                     content_hash, provenance, validation_status)
                VALUES (:articleId, :title, :source, :sourceUrl, :publishedAt, :firstSeenAt, :ingestedAt,
                        :availableAt, :contentHash, :provenance, :validationStatus)
                ON CONFLICT DO NOTHING
                """).bindProperties(news).bind("validationStatus", news.validationStatus().name())
                .fetch().rowsUpdated();
    }

    Mono<News> articleForHash(String hash) {
        return db.sql("SELECT * FROM phivealpha_idx.idx_news WHERE content_hash = :hash")
                .bind("hash", hash).mapProperties(News.class).one();
    }

    Mono<String> reserveAlias(String alias, String canonical) {
        return db.sql("""
                INSERT INTO phivealpha_idx.idx_news_aliases (article_id, canonical_id)
                VALUES (:alias, :canonical) ON CONFLICT (article_id) DO NOTHING
                """).bind("alias", alias).bind("canonical", canonical).fetch().rowsUpdated()
                .then(db.sql("SELECT canonical_id FROM phivealpha_idx.idx_news_aliases WHERE article_id = :alias")
                        .bind("alias", alias).map((row, metadata) -> row.get("canonical_id", String.class)).one());
    }

    Mono<Long> link(String canonical, String symbol, OffsetDateTime availableAt) {
        return db.sql("""
                INSERT INTO phivealpha_idx.idx_news_symbols (article_id, symbol, available_at)
                VALUES (:canonical, :symbol, :availableAt) ON CONFLICT DO NOTHING
                """).bind("canonical", canonical).bind("symbol", symbol).bind("availableAt", availableAt)
                .fetch().rowsUpdated();
    }

    Flux<News> availableNews(String symbol, OffsetDateTime cutoff, int limit) {
        return db.sql("""
                SELECT n.* FROM phivealpha_idx.idx_news_symbols AS s
                JOIN phivealpha_idx.idx_news AS n ON n.article_id = s.article_id
                WHERE s.symbol = :symbol AND s.available_at <= :cutoff AND n.available_at <= :cutoff
                    AND n.validation_status = 'VALIDATED'
                ORDER BY greatest(s.available_at, n.available_at) DESC, n.article_id
                LIMIT :limit
                """).bind("symbol", symbol).bind("cutoff", cutoff).bind("limit", limit)
                .mapProperties(News.class).all();
    }

    Mono<Long> insert(Ingestion ingestion) {
        return db.sql("""
                INSERT INTO phivealpha_idx.idx_data_ingestion
                    (provider, dataset, scope, fetched_at, status, error_category, record_count, rejected_count,
                     requests_used, request_budget, budget_window)
                VALUES (:provider, :dataset, :scope, :fetchedAt, :status, :errorCategory, :recordCount,
                        :rejectedCount, :requestsUsed, :requestBudget, :budgetWindow)
                RETURNING ingestion_id
                """).bindProperties(ingestion).bind("status", ingestion.status().name())
                .map((row, metadata) -> row.get("ingestion_id", Long.class)).one();
    }
}
