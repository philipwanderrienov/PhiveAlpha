BEGIN;

CREATE SCHEMA phivealpha_idx;

CREATE TABLE phivealpha_idx.idx_instruments (
    symbol text PRIMARY KEY CHECK (symbol ~ '^[A-Z0-9]{1,16}$'),
    name text NOT NULL,
    sector text NOT NULL,
    instrument_type text NOT NULL,
    listing_status text NOT NULL,
    currency text NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    lot_size integer NOT NULL CHECK (lot_size > 0),
    updated_at timestamptz NOT NULL
);

CREATE TABLE phivealpha_idx.idx_daily_prices (
    symbol text NOT NULL REFERENCES phivealpha_idx.idx_instruments,
    trading_date date NOT NULL,
    open numeric NOT NULL CHECK (open > 0),
    high numeric NOT NULL,
    low numeric NOT NULL CHECK (low > 0),
    close numeric NOT NULL CHECK (close > 0),
    volume bigint NOT NULL CHECK (volume >= 0),
    turnover numeric NOT NULL CHECK (turnover >= 0),
    source text NOT NULL,
    observed_at timestamptz NOT NULL,
    ingested_at timestamptz NOT NULL CHECK (ingested_at >= observed_at),
    adjustment_status text NOT NULL CHECK (adjustment_status IN ('UNKNOWN', 'UNADJUSTED', 'ADJUSTED')),
    PRIMARY KEY (symbol, adjustment_status, trading_date),
    CHECK (high >= greatest(open, close) AND low <= least(open, close))
);

CREATE TABLE phivealpha_idx.idx_news (
    article_id text PRIMARY KEY,
    title text NOT NULL,
    source text NOT NULL,
    source_url text NOT NULL,
    published_at timestamptz NOT NULL,
    first_seen_at timestamptz NOT NULL CHECK (first_seen_at >= published_at),
    ingested_at timestamptz NOT NULL CHECK (ingested_at >= first_seen_at),
    available_at timestamptz NOT NULL CHECK (available_at >= ingested_at),
    content_hash text NOT NULL UNIQUE CHECK (content_hash ~ '^[a-f0-9]{64}$'),
    provenance text NOT NULL,
    validation_status text NOT NULL CHECK (validation_status IN ('PENDING', 'VALIDATED', 'REJECTED'))
);

CREATE TABLE phivealpha_idx.idx_news_aliases (
    article_id text PRIMARY KEY,
    canonical_id text NOT NULL REFERENCES phivealpha_idx.idx_news
);

CREATE TABLE phivealpha_idx.idx_news_symbols (
    article_id text NOT NULL REFERENCES phivealpha_idx.idx_news,
    symbol text NOT NULL REFERENCES phivealpha_idx.idx_instruments,
    available_at timestamptz NOT NULL,
    PRIMARY KEY (article_id, symbol)
);
CREATE INDEX idx_news_symbols_lookup ON phivealpha_idx.idx_news_symbols (symbol, available_at, article_id);
CREATE INDEX idx_news_availability ON phivealpha_idx.idx_news (available_at, article_id)
    WHERE validation_status = 'VALIDATED';

CREATE TABLE phivealpha_idx.idx_data_ingestion (
    ingestion_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    provider text NOT NULL,
    dataset text NOT NULL,
    scope text NOT NULL,
    fetched_at timestamptz NOT NULL,
    status text NOT NULL CHECK (status IN ('SUCCESS', 'PARTIAL', 'FAILED')),
    error_category text NOT NULL,
    record_count bigint NOT NULL CHECK (record_count >= 0),
    rejected_count bigint NOT NULL CHECK (rejected_count BETWEEN 0 AND record_count),
    requests_used integer NOT NULL CHECK (requests_used >= 0),
    request_budget integer NOT NULL CHECK (request_budget >= 0),
    budget_window text NOT NULL
);
CREATE INDEX idx_ingestion_lookup ON phivealpha_idx.idx_data_ingestion (provider, dataset, scope, fetched_at);

COMMIT;
