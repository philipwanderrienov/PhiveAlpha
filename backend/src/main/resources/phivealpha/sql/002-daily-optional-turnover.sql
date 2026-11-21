BEGIN;

ALTER TABLE phivealpha_idx.idx_daily_prices ALTER COLUMN turnover DROP NOT NULL;

COMMIT;
