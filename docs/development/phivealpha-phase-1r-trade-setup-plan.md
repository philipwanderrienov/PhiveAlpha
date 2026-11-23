# Phase 1R — Swing Trade Setup Engine (proposed; not implemented)

## Current status
UI placeholder delivered on Phase 1Q design branch. Backend **does not** currently calculate validated entry, TP, SL, risk/reward, or setup freshness. Historical replay evaluates returns from synthetic sessions; paper trading previews notional buy value. Neither is a live entry-level recommendation.

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

## Next engineering handoff
Audit existing domain models, choose deterministic policy and tick-size rules supported by verified historical IDX data, implement versioned service + tests, wire read-only endpoint, then replace UI placeholders only after host validation and out-of-sample checks. Keep UI design branch separate from production.
