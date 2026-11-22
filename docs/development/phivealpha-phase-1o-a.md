# PhiveAlpha Phase 1O-A — Historical Dataset Readiness & Integrity Audit

Baseline `main` commit `e4972f5`. `HistoricalDatasetReadinessAudit.audit(Request)` is a deterministic, read-only preflight over materialized inputs for one historical decision cutoff. It uses the existing replay frame, daily price records, versioned calendar, universe eligibility and replay session evidence. The request specifies dataset identity and provenance, equity scope, expected candle source and price basis, and the session-evidence source. The default horizon is 250 trading sessions. An explicit lookback can override it.

## Readiness rules

- The versioned calendar determines the expected Jakarta trading dates. Only sessions closed by the cutoff count. Confirmed holidays contribute no sessions, special days use their manifest close, and a missing calendar date stops coverage. Candle gaps never define the calendar. A suspended equity still has an expected exchange session.
- The caller supplies the historical frame selected for the cutoff. A later frame is rejected before evaluating members. Universe and calendar must be available by frame assembly, and scoped equity membership and listing eligibility come from that frame. The shared screening policy supplies reference and price freshness thresholds.
- Expected equity candles require exact source identity, unadjusted prices, positive and internally consistent OHLC, nonnegative volume, and actual turnover. Observation must follow or equal the session close, ingestion cannot precede observation, and both timestamps must be visible at frame assembly and cutoff. A duplicate invalidates the date. Missing turnover is reported separately and never imputed.
- Per-session equity evidence confirms listing and corporate-action status across the horizon. The existing `SessionBar` supplies availability and version. It must be available between the session close and cutoff inclusive. Suspensions, delistings, unknown status, and any declared corporate action block readiness. The audit performs no adjustment calculation. Where only an EOD candle and cutoff-level master exist, missing session evidence makes corporate-action and tradability coverage unverified.
- The same contract supplies an independent trailing IHSG series. Each expected IHSG date requires one visible row with positive open and close. Missing IHSG blocks overall readiness. Session-evidence source is caller-declared because `SessionBar` has a version but lacks a row-level source field. Revisions must be materialized as a single vintage per series and date. A duplicate or late revision submitted in the requested window is rejected.
- The report exposes expected dates, observed session counts, valid counts, coverage, freshness, sources and versions, eligibility reasons and dated integrity issues. Equity candle coverage and historical eligibility are separate checks. `READY` requires all checks to pass. `INCOMPLETE` indicates a usable evaluation scope with a gap or invalid values, including missing IHSG alongside equity data. `UNVERIFIED` indicates failed evidence, provenance, eligibility, or temporal checks. `UNAVAILABLE` covers a missing frame, insufficient calendar coverage, or an absent observed equity scope without a higher-priority evidence failure. Readiness describes the explicit scope and cutoff only.

## Replay boundary

Invoke the audit explicitly before accepting a frame for a workflow that requires this gate. Supply each frame and its trailing session evidence independently for its own cutoff, and proceed only for `Outcome.READY`. Keep the existing forward outcome bundle and outcome freeze separate. Phase 1G supports later published outcome revisions up to the freeze, fixtures with intentionally short decision windows, and incomplete arms reported as replay results. Automatically inserting this stricter preflight into `HistoricalReplayService.replay` would change those semantics. Scoring, costs, benchmark execution and replay outputs retain their existing contracts.

The Phase 1J/1K screening reader already filters prices by cutoff and basis and can normalize source labels. For the audit, callers must supply immutable snapshots with original provenance and any conflicting rows that must be audited. A prefiltered reader cannot prove the absence of future revisions or mixed sources in the underlying inventory. The audit also does not verify dataset licensing, reference accuracy, membership completeness outside the supplied scope, first-seen news, lot sizes, or the forward outcome paths.

## Suggested next work

Independently verify IDX data rights, coverage and accuracy, then qualify calendar and universe vintages that include delistings, session-level equity status, corporate-action evidence, and licensed IHSG history. Define an immutable snapshot export that preserves provenance and revision visibility. Apply this preflight per cutoff before deciding whether to gate real-data research. The synthetic fixture results establish code behavior only.

## Validation

```bash
CI=true mvn -o -B -f backend/pom.xml clean verify
scripts/validation/phivealpha-persistence-it.sh --daily-ingestion -o
scripts/validation/phivealpha-persistence-it.sh --stock-screener -o
git diff --check
```

Maven offline reported 113 tests total, 112 passing, one existing quote-canary loopback skip, and zero failures or errors. All 18 new audit cases passed. Existing regressions passed for daily ingestion (7), Liquid Universe (7), news scoring (7), Stock Screener (6), screener storage failure (1), and historical replay (7). The whitespace check passed.

The two existing PostgreSQL runners stopped at the loopback socket probe with `EPERM`. Neither test ran and neither temporary cluster was created. Run them on a development host with PostgreSQL 17 and loopback support. The new preflight has no persistence changes, so its audit cases run in memory.
