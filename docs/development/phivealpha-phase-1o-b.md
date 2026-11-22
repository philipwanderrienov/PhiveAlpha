# PhiveAlpha Phase 1O-B — Point-in-Time Screening Validation

Baseline `main` at `a9756ba`. `HistoricalScreeningService` is an internal research-only workflow. Call `screen(Context)` for an immutable materialized vintage or `screenPersisted(Context)` for candle reads from the guarded development store. Both paths require an explicit cutoff and the expected scoring-model version. Existing screener and replay entry points retain their behavior.

## Existing safeguards and the new gate

- Phase 1G selects a visible frame per cutoff and freezes forward outcomes separately. Phase 1K already filters future-observed and future-ingested candles. Liquid Universe checks reference availability, session coverage, candle economics, and freshness. The news scorer checks factor evidence, freshness, publication, first-seen and availability, and ranks equal scores by symbol. These rules remain in their existing services.
- The new context takes an explicit replay frame, Phase 1O-A dataset and source declarations, historical scope, and session evidence. It freezes the nested news-event lists. Universe and calendar must be available by frame assembly and the frame by cutoff. The caller selects the historical vintage, and a later replacement is rejected with its version recorded.
- The workflow excludes candles with known timestamps after the cutoff so a late revision cannot replace or invalidate an already-visible candle. Missing timestamps remain subject to audit. If the visible set is missing a session, or contains a candle available by cutoff but after frame assembly, readiness blocks scoring. Only price rows within the audited dates and symbol scope reach the screener. Phase 1O-A still audits raw revisions when invoked directly.
- An explicit lookback smaller than 250 is supported. Historical screening raises the requested horizon to cover the active liquidity lookback, minimum listing window, and 11-session momentum. Phase 1O-A requests without an explicit lookback still default to 250. The generated report is independent of wall-clock time.
- News coverage requires first-seen attestation. Factor evidence later than frame assembly is rejected and recorded per symbol or for the market factor. The scorer excludes later news events and rejects missing, stale or malformed factors for the affected equity. Other fully verified candidates can rank. An empty ranking is blocked. The report carries readiness, policy, model version, liquidity decisions, and factor evidence with reasons.
- The persistence path reads the scoped range at the cutoff through the Phase 1J development profile and repository. It preserves original row sources and ignores the frame's materialized prices. Missing profiles or read errors return structured unavailability, without fallback. Storage queries select the declared basis, so an inventory-level audit of all bases or discarded revisions still needs a raw export.

## Evidence boundary

The tests verify cutoff enforcement, deterministic rankings, and rejection behavior against local synthetic evidence. They rely on the supplied availability timestamps, provenance, historical membership, session-level status and corporate actions, and first-seen attestation. Independent verification of real IDX licensing, coverage and accuracy, including IHSG, remains necessary. The workflow publishes no endpoint or trading recommendations.

## Validation commands

```bash
CI=true mvn -o -B -f backend/pom.xml clean verify
scripts/validation/phivealpha-persistence-it.sh --daily-ingestion -o
scripts/validation/phivealpha-persistence-it.sh --stock-screener -o
git diff --check
```

Maven offline reported 123 tests total, 122 passed, one existing quote-canary loopback skip, and zero failures or errors. The 10 new historical screening tests, 18 readiness tests, seven replay tests, six screener tests, seven daily-ingestion tests, and one in-process storage-failure regression passed. The storage-failure regression exercises both the existing screener path and the new historical path. Whitespace checks passed.

Both PostgreSQL runners stopped at the socket probe with `EPERM` before creating a cluster. Run them on a PostgreSQL 17 development host with loopback support.
