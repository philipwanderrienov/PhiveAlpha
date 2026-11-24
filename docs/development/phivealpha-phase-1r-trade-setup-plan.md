# Phase 1R — Swing Trade Setup Engine (offline prototype + guarded integration)

## Current status
UI placeholder delivered on Phase 1Q design branch. Phase 1R-A now includes a **standalone offline experimental calculator** (`com.phivealpha.trade.SwingTradeSetup`) and seven regression tests. It is **not connected to an API or live data**; its seven tests passed locally on phive-server (141 backend tests total on 2026-11-24). Backend **does not** currently publish validated actionable entry, TP, SL, risk/reward, or setup freshness. Historical replay evaluates returns from synthetic sessions; paper trading previews notional buy value. Neither is a live entry-level recommendation.

## Research-only contract
For a given IDX common-stock symbol and point-in-time snapshot, return a versioned **proposal** only when all prerequisites pass:
- price and volume history, adjustment basis, trading calendar, listing status, corporate actions, and eligibility are verified;
- data availableAt and asOf are before or equal to evaluation cutoff;
- latest valid close/session, tick size, lot size, and liquidity are known;
- volatility and price-structure calculations are reproducible and rely on past-only candles;
- risk notes are available and setup validity conditions documented.

### Fields
`symbol`, `cutoff`, `dataAsOf`, `dataAvailableAt`, `policyVersion`, `sourceVersions`,
`status` (VALID / INCOMPLETE / STALE / UNVERIFIED / UNAVAILABLE / NO_SETUP),
`reasons`, `entryZoneLowIdr`, `entryZoneHighIdr`, `stopLossIdr`,
`takeProfitTargetsIdr`, `riskReward`, `invalidation`, `horizonSessions` (2–10),
`riskPerTradeIdr`, `maximumLots`, `costAssumptions`, `expiresAt`.

Use null numeric fields when the model cannot calculate a level; do not return zero or substitute last close as entry. A stale, suspended, or unverifiable candidate must not have actionable displayed levels.

## Candidate design for a research policy
Investigate (do not ship without backtesting) a deterministic long-only approach combining past-only support/resistance, ATR(14), tick-size rounding, volatility buffer, and maximum portfolio risk fraction. Derive SL from structural invalidation with a volatility floor; derive TP from tested resistance or a bounded R multiple. Reject setups with inadequate reward/risk after modeled taxes, fees, spread and slippage. Distinguish *entry zone*, *activation trigger*, and *execution price*. Never imply a guaranteed fill.

### Validation
- Offline synthetic fixture tests for all status gates, split/price adjustment, missing candles, zero ATR, suspension/delisting, OHLCV anomalies, no lookahead, deterministic signals, tick sizes, corporate actions, market gaps, fees, and expiry.
- Chronological out-of-sample evaluation with non-overlapping/embargoed windows; monitor false positives, adverse excursion, drawdown, and realistic fills.
- Keep data collection and exchange rights verification separate. Do not call API.co.id/Gemini live or modify CoinMind production.
- No buy/sell order execution or notification triggers.

## UX acceptance
Overview watchlist exposes per-symbol entry/TP/SL directly. Before a valid policy exists, show clear dashes and “Setup belum dihitung”; never show suggested prices based only on momentum. When engine exists, show dated levels, activation status, target, invalidation, costs and risk/reward with an expandable detailed rationale. Display source quality and fixture/research-only status above the fold.

## Phase 1R-A experimental prototype\n\nThe pure calculator evaluates exactly 21 ordered trading sessions supplied by a caller, with verified eligibility/reference, explicit tick and lot configuration, adjusted OHLCV, consistent source, no duplicates, and as-of availability before cutoff. Conditional breakout trigger uses the prior 20-session high, a 14-session true-range average, historical support and a two-R take-profit target. All levels are withheld when a gate fails. This is **not a validated strategy**; it does not yet account for executable fills, fees/slippage, verified exchange tick schedules, point-in-time corporate actions, adjusted history suitability, or true IDX market observations. No production endpoint was added. Keep UI placeholders until those dependencies are satisfied and real-data out-of-sample validity is established.\n\n## Phase 1R-B1: Historical readiness guard (pending host test)

`SwingTradeReadinessGate` now calls `HistoricalDatasetReadinessAudit` and preserves the audited outcome. It explicitly blocks a trade setup from any current persisted or fixture historical data, because the existing readiness audit supports only `UNADJUSTED` data, whereas `SwingTradeSetup` currently requires `ADJUSTED` data. This is deliberate: no implicit price-basis conversion, no fabricated level, no API endpoint, and no UI signal. Five regression tests exercise future frames, absent history, audited unadjusted history, and unsupported adjusted basis. The change is committed to the design branch and awaits validation on phive-server.

Next unblockers: establish verified point-in-time corporate-action adjustments, prove adjusted historical-price readiness and session evidence; add exchange-verified tick schedule by price/date, trading permissions and lot size, fees/spread/slippage with realistic executions and walk-forward out-of-sample checks. Only then wire a read-only API returning versioned status and nullable levels.

## Phase 1R-B2: Exchange-rule reference validator (pending host test)

`SwingTradeMarketRules` validates source/version, point-in-time availability and effective date in the Asia/Jakarta timezone, symbol, positive board lot, nonoverlapping tick price bands, and entry/stop/target alignment against their **individual** bands. Seven fixture-based tests cover positive and fail-closed cases. No real IDX bands are embedded or assumed; the fixture values are artificial. An independently sourced, effective-dated rule dataset, adjusted-price audit and a validated connection from this check to `SwingTradeSetup` are still required before displaying levels. No public API or UI signals were activated.

## Phase 1R-B3: Tick-band-aware rounding (pending host validation)

Added `SwingTradeMarketRules.validateReference` (standalone provenance and rule-structure preflight) and `SwingTradeTickRounding` (UP/DOWN tick rounding across all versioned bands). The rule for a candidate price is selected **at its resulting price band**, not blindly from an entry reference tick. Six synthetic tests cover ranges, boundary crossings, unchanged exact ticks, future/unverified rules, and withheld prices. This is an isolated offline utility; neither the historical readiness gate nor the production trade setup/API/Overview uses it yet. The broader adjusted-price, historical corporate-action and execution-cost blockers remain, and all UI price placeholders must remain withheld.

## Next engineering handoff
Audit existing domain models, choose deterministic policy and tick-size rules supported by verified historical IDX data, implement versioned service + tests, wire read-only endpoint, then replace UI placeholders only after host validation and out-of-sample checks. Keep UI design branch separate from production.
