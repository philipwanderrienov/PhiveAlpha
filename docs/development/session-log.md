# Development Session Log

This is a concise chronological handoff log shared by ChatGPT and Codex.

## 2026-10-01

- Added logical mobile `#market` and desktop `#dashboard` routes to prevent first-load anchor jumps while preserving active navigation state.
- Kept mobile header/status visible on first load and preserved sticky mobile section navigation.
- Added draggable, edge-snapping floating BTC/ETH/SOL selector on mobile.
- Normalized responsive layout rules and added a desktop dashboard composition pass.
- Removed duplicate AI Decision Review content.
- Added AI News Intelligence and conservative Decision Engine integration.
- Added Polymarket prediction-market intelligence and bounded confidence integration.
- Enabled public HTTPS access through Tailscale Funnel at `https://phive-server.tail597574.ts.net/`.
- Verified Funnel DNS, TLS certificate, Nginx response, and HTTP 200.
- Added `docs/development/current-state.md` as the project handoff snapshot.

## 2026-10-02

- Established GitHub as the synchronization layer between the main ChatGPT conversation and Codex running on the server.
- Added root `AGENTS.md` with mandatory project-read, validation, handoff, and synchronization rules.
- Added this session log.
- Going forward, every meaningful ChatGPT/Codex change should update both `current-state.md` and this log.

### Desktop layout implementation (local only)

- Refined the existing >=1100px composition block in `app.component.css`; unified all desktop containers, compacted upper controls/tickers, reduced chart/support-panel height, and tightened Trade Decision grids.
- Retained chart/decision 8/4 split, paired Microstructure/Polymarket and News/Insight; compacted Detail Market and full-width History, pairing evaluation/usage cards.
- Removed the conflicting >=1500px container/chart override and desktop sidebar stickiness so long decision content remains reachable.
- No HTML, TypeScript, navigation IDs/routes, mobile rules, API, business logic, or backend changes. Source comparison verified pre-desktop CSS unchanged.
- Production build passed using installed Node 22.23.2 and network access for Google Fonts; existing Angular NG8102/NG8107 template warnings remain. Default Node 20 fails the CLI version requirement; sandbox build cannot resolve Google Fonts.
- Visual/browser acceptance pending: check desktop widths >=1100px, short screens, long decision/AI content, indicators and expanded News/History; smoke-check mobile navigation/selector and breakpoint resizing.
- Updated current-state handoff. No deployment or GitHub push, per user instruction. Existing untracked deploy script/package lock excluded.


### Desktop structure/navigation correction

- Replaced mismatched section route/id naming with semantic ids: `dashboard`, `signals`, `market`, `news`, `history`.
- Updated desktop and mobile nav hrefs/click targets to the same semantic section ids.
- Changed logical first-load aliases to `#section=dashboard` and `#section=market` so initial header visibility is preserved without colliding with real anchors.
- Corrected desktop scroll offset: desktop no longer subtracts the full control-shell height because the shell is in normal flow.
- Removed the desktop 8/4 chart/Signals composition; Dashboard and Signals now stack full-width in normal document order.
- Explicitly disabled sticky positioning/max-height constraints on Trade Decision for desktop.
- Preserved lower desktop Market/News/History composition and current mobile interaction model.
- Changes were pushed to `main`; Angular production build is still pending in this ChatGPT environment and must be run on `phive-server` before deployment/acceptance.


### Navigation-only source correction (local, no commit/deployment/push)

- Rechecked HTML semantic hrefs/ids and the responsive CSS; retained existing section targets and all visual layout/selector/sticky-nav behavior.
- Moved tracking after view creation; normalized initial entry before ids render to keep both viewport types at the top, including real-anchor reloads (desktop Dashboard/mobile Market).
- Unified scripted offsets and CSS anchor margins; tracked sorted rendered section positions with rounding tolerance. Added in-session hash handling and cancellable scroll finishing/selection release for manual scrolling.
- Source simulation passed 30 menu-target cases across six widths plus entry, manual tracking, hashchange, rounding, and bottom clamping; actual browser verification remains pending.
- Final production build passed with Node 22.23.2; existing Angular NG8102/NG8107 warnings remain. No commit/push/deploy allowed by the resumed instruction. Earlier sudo deployment attempt failed authentication before copying files or reloading Nginx.

### Desktop persistent section menu (local only)

- Added a footprint slot around the desktop menu; only the menu pins to the viewport top after scrolling past its original position, without moving section layout.
- Desktop-only final rules neutralize legacy shell stickiness and its backdrop filter. Mobile navigation and floating selector remain unchanged.
- Desktop offset is measured menu height + 18px, shared by click/hash scrolling, CSS anchors, and active tracking; mobile offset and first-load behavior are preserved.
- No deployment, commit, or push. Browser verification of the new behavior remains pending.

- Validation for desktop persistence: standard `npm run build` passed on retry with Node 22.23.2 (first attempt hit Google Fonts DNS EAI_AGAIN); existing Angular template warnings remain. New browser/visual checks remain pending.


### View-based application sidebar

- Replaced the legacy horizontal section navigator with four primary application views: Dashboard, Trade Decision, News, and AI & Usage.
- Added persistent desktop sidebar with active state and selected-market summary.
- Added mobile hamburger + off-canvas drawer; retained the draggable floating coin selector.
- Dashboard view keeps chart, market condition/hourly activity, and Detail Market context.
- Trade Decision view isolates the detailed decision engine output.
- News view isolates the coin news/sentiment section.
- AI & Usage view exposes AI Insight, engine/model details, token usage, cost estimates, evaluation summary, and history.
- Removed obsolete horizontal navigation markup so Angular no longer compiles stale event handlers.
- Changes are pushed to main. Production Angular build and real browser visual validation are still pending on phive-server.


### Mobile header cleanup after sidebar introduction

- Fixed the broken mobile header composition introduced by the sidebar pass.
- Hamburger, brand mark, and CoinMind AI title now share one 64px row.
- Status cards were separated into a dedicated two-column strip with mobile-safe sizing/wrapping.
- Legacy sticky controls are hidden on mobile; main/content widths and bottom spacing are normalized.
- Commit pushed: `ee472d3 fix: stabilize mobile header and sidebar layout`.
- Production build and real-device/browser visual verification remain pending.

## 2026-10-21

### Polymarket resilience hardening

- Investigated production timeout to `gamma-api.polymarket.com:443`; backend itself remained healthy on port 8080.
- Added configurable Reactor Netty connect timeout (default 5s) and response/operation timeout (default 8s).
- Added per-symbol failure cooldown (default 2 minutes) to prevent repeated upstream hammering while Polymarket is unreachable.
- Added fallback behavior: return last cached intelligence when available, otherwise return neutral intelligence.
- Replaced expected timeout stack-trace logging with concise warning messages containing symbol, reason, and fallback mode.
- Added `POLYMARKET_BASE_URL`, `POLYMARKET_CONNECT_TIMEOUT_MS`, `POLYMARKET_RESPONSE_TIMEOUT_MS`, and `POLYMARKET_FAILURE_COOLDOWN_MS` configuration.
- Implementation commits: `accbf7a`, `2276883`.
- Server-side Maven build/restart/smoke validation remains pending after `git pull origin main`.

### Handoff cleanup

- Rewrote `docs/development/current-state.md` so the view-based sidebar architecture is canonical.
- Removed the stale legacy horizontal-navigation state from the primary handoff.
- Added current backend verification state, Polymarket hardening details, frontend validation priorities, and technical cleanup priorities.

### Frontend legacy navigation CSS cleanup

- Verified current HTML no longer contains `desktop-dashboard-nav`, `desktop-nav-slot`, `mobile-sticky-dashboard-nav`, `#signals`, or `#history`.
- Removed the obsolete tail CSS that still implemented scroll-anchor offsets and pinned horizontal desktop navigation for those deleted elements.
- Preserved current view-based sidebar styles and existing Trade Decision grid rules.
- Commit: `585f26f style: remove obsolete section navigation CSS`.
- Production Angular build and browser validation are still required after pulling latest `main`.

### Advisor-only product scope

- Product scope was explicitly fixed as advisor / decision-support only.
- CoinMind will not send BUY/SELL orders to Binance or another exchange.
- Updated Trade Decision wording so BUY/SELL/WATCH are presented as recommendations.
- Added an advisor-only disclaimer in the Trade view.
- Reworded stop/invalidation and entry areas as risk/reference levels.
- Updated README and canonical current-state documentation.
- Commits: `1942cea`, `9eaede0`, `43251c7`.
- Frontend production build/deployment remains pending on `phive-server`.

## 2026-10-22

### Cost-aware AI router and Polymarket AI intelligence

- Added routine/deep AI model routing with `AI_ROUTINE_MODEL` and `AI_DEEP_MODEL`.
- General market analysis and Decision Review use the deep route; News Intelligence uses the routine route.
- Added `AiPolymarketIntelligence` model, service and API endpoints.
- Polymarket AI analysis receives only compact structured data from the deterministic layer, capped to the top five markets.
- Added Polymarket materiality gating, cache, hourly/daily automatic-call guardrail, and scheduled analysis of tracked symbols.
- Default automatic trigger thresholds: 3% probability change, importance/relevance 80, deep materiality 75, cache 60 minutes.
- Added deterministic/AI separation in the frontend Prediction Market panel plus manual AI analysis action.
- Added route-specific token pricing so routine/deep cost estimates can remain accurate when different models are configured.
- Enforced `AI_ENABLED` as a hard master switch for every live OpenAI provider call.
- Updated `.env.example` with model-routing, pricing, Polymarket AI and upstream resilience settings.
- AI Polymarket output remains advisor context only; it does not yet modify the deterministic Decision Engine.
- Live API provider remains disabled until a server API key is intentionally configured.
- Local build could not be executed in the ChatGPT container because external GitHub DNS/network access is unavailable; server-side Maven + Angular production validation is required before deployment acceptance.

### Polymarket multi-source discovery failover

- Replaced Gamma `/public-search` as primary discovery with the documented `GET /events?active=true&closed=false&order=volume_24hr...` flow.
- Added local BTC/ETH/SOL filtering over active events and direct extraction from nested markets, removing the extra per-event fetch roundtrip.
- Added read-only CLOB `/simplified-markets` as a second-host fallback when Gamma is unreachable.
- CLOB fallback confidence is capped lower than Gamma because it may lack 24h probability-change metadata; current YES probability is used only as bounded contextual evidence.
- Existing cache/neutral fallback and cooldown remain the final degradation layer.
- Added `POLYMARKET_CLOB_BASE_URL`, `POLYMARKET_GAMMA_EVENT_LIMIT`, and `POLYMARKET_CLOB_PAGE_LIMIT`.
- Server-side Maven validation is required before deployment acceptance.

### Compliant forecast-source fallback for Indonesia

- Production server logs confirmed both `gamma-api.polymarket.com` and `clob.polymarket.com` were DNS/routing-blocked and timed out from the Indonesian ISP path.
- Added Manifold public forecasting API as a third-source fallback instead of implementing VPN/proxy circumvention.
- Prediction intelligence now carries explicit source provenance: `POLYMARKET_GAMMA`, `POLYMARKET_CLOB`, `MANIFOLD`, or `NONE`.
- Manifold uses public `/v0/search-markets` binary/open-market discovery for BTC/ETH/SOL-related questions.
- Manifold and CLOB fallback confidence remains lower than Gamma because 24h probability-change data may be unavailable.
- Frontend relabeled the panel to generic Forecast Signal and shows the active data source.
- AI structured context now includes the source and uses source-neutral prediction/forecast wording.
- Server Maven + frontend production build validation remains required.

### Routine/deep AI benchmark harness

- Updated current OpenAI benchmark candidates to `gpt-6-luna` (routine) and `gpt-6.1-sol` (deep), while keeping `AI_ENABLED=false` by default.
- Updated token-cost defaults for the two routes.
- Extended `AiProvider` with an explicit model override for controlled benchmark runs.
- Added a manual forecast benchmark service that runs routine and deep sequentially against exactly the same structured prediction context.
- Benchmark output includes per-model latency/result, direction agreement, deterministic-bias agreement, source and materiality.
- Added free `GET /api/v1/ai/benchmark/config` and paid/manual-only `POST /api/v1/ai/benchmark/forecast/{symbol}`.
- No scheduled benchmark calls are made.
- Frontend AI advanced details now displays routine/deep model routing.
- Backend Maven + frontend production build remain required on phive-server before this increment is accepted.

### Dedicated AI Billing / Cost Center

- Added a fifth application view: `AI Billing`.
- Added backend cost-center aggregation for today, current month and lifetime usage.
- Cost accounting separates input and output cost and tracks input/cached/output/reasoning tokens.
- Added monthly cost breakdown by model and trigger type.
- Added optional `AI_CREDIT_BUDGET_USD` internal budget reference and estimated remaining internal budget.
- Added `GET /api/v1/ai/usage/cost-center`.
- Frontend displays internal credit budget, estimated lifetime spend, remaining estimate, daily/monthly token/cost cards, per-model costs and per-activity costs.
- UI explicitly states that this is a CoinMind estimate, not the live OpenAI prepaid balance.
- Historical/lifetime accounting depends on persistence being enabled.
- Backend Maven and Angular production builds remain required on phive-server for acceptance.

## 2026-10-26

### Pre-live paid AI readiness audit

- Re-audited market, forecast, trade decision, AI routing, billing, persistence and paid-call failure modes before API-key activation.
- Found Manifold fallback markets could be multi-year and were capable of affecting forecast bias despite lacking equivalent 24h probability-change data.
- Restricted Manifold to closing-month / <=35-day markets, sorted by 24h volume.
- Manifold is now neutral deterministic context only; static probabilities do not create trading direction.
- Only `POLYMARKET_GAMMA` may apply the bounded prediction-market confidence modifier in TradeSetup.
- Automatic AI interpretation is skipped for fallback forecast sources; fallback sources remain available for manual second-opinion analysis.
- Added `AI_AUTOMATIC_ENABLED=false` fail-closed master switch for automatic/event-triggered AI.
- Added `AI_MAX_MONTHLY_SPEND_USD=0` fail-closed paid API spend cap.
- Added WebFlux paid-call guardrail: live OpenAI POSTs require persistence plus a positive monthly cap with recorded spend below the cap.
- Exposed automatic AI state and monthly cap in AI guardrail API/UI.
- Code-level syntax sanity passed; phive-server Maven/Angular production builds and smoke tests are still required before API key provisioning.

### Final smoke-test confidence alignment

- Real server smoke tests passed for health, trade setup, forecast fallback, AI mock status, guardrails and cost center.
- Observed `WAIT` with confidence 86, which was technically possible because confidence and actionability were computed separately but was misleading for advisor UX.
- Added recommendation confidence caps: WAIT <= 60, WATCH <= 80, BUY/SELL <= 95.
- Added regression test for WAIT confidence cap.
- Requires one final backend test/build/restart on phive-server before live API provisioning.

### Prediction source health diagnostics

- Reconfirmed that Polymarket Gamma is prediction-market metadata/probability data, not a news API.
- Current Indonesian network restrictions mean CoinMind must not depend on bypassing Polymarket access.
- Added in-memory source health tracking for Gamma, CLOB and Manifold.
- Added `GET /api/v1/polymarket/source-health` reporting UNKNOWN/UP/EMPTY/UNAVAILABLE, last checked time, concise detail and signal count.
- Health endpoint is observational only and does not cause an extra upstream request.
- Existing compliant failover remains Gamma -> CLOB -> Manifold -> cache/neutral.
- Backend Maven validation on phive-server is required after pull.


## 2026-10-27 — Futures intraday work on main (partial, not deployed)

User requested direct main development. Implemented paper-only `POST /api/v1/futures/risk/preview` with validated sizing, fees/slippage assumptions, 1% risk cap, 5x leverage cap, 20% margin allocation ceiling, and associated unit tests. `GET /api/v1/futures/readiness` explicitly reports BLOCKED until futures-specific feeds, contract liquidation rules and strategy validation are ready. Prediction refresh now calls Manifold exclusively and source-health reports Manifold only; old Polymarket-named classes, unused Gamma/CLOB methods, configuration, and frontend/AI references still require full cleanup. Do not call this complete. Server Maven/Angular tests were not run; no deployment was made.


### 2026-10-27 — Manifold-only runtime and automated validation setup

- The legacy PolymarketIntelligenceService name remains for dependency compatibility, but its implementation was rewritten to request ONLY Manifold public search, filter binary unresolved questions that close in the next 35 days, and return NEUTRAL contextual evidence; no Polymarket Gamma or CLOB network client/method remains inside this service. Manifold source-health only. Configuration moved to coinmind.manifold.*.
- Old Polymarket-named routes, models, frontend labels, AI service names and legacy configuration sections elsewhere in project have NOT all been migrated. Migration is incomplete.
- Futures preview inputs now reject null side cleanly.
- Added GitHub Actions Java 25 Maven verify and Angular Node 24 production build workflow in .github/workflows/validate.yml. At last query GitHub reported zero workflow runs, so checks are not yet verified.
- DO NOT deploy or claim futures-live readiness. Production tests, exchange futures data, fee/risk/liquidation guards, and end-to-end verification remain blockers.

## 2026-10-27 — Futures public derivatives feed (main)

- Added GET /api/v1/futures/market-data/{symbol} (BTCUSDT/ETHUSDT/SOLUSDT) as a read-only opt-in endpoint.
- Pulls USD-M premiumIndex (mark/index/funding/nextFundingTime) and openInterest from the futures venue, only when FUTURES_MARKET_DATA_ENABLED=true. Default false; emits DISABLED and never silently substitutes spot data.
- Data status UP requires valid positive prices/OI, numeric funding and timestamps <2 minutes old; otherwise STALE_OR_INVALID, UNAVAILABLE, or DISABLED. No order endpoints.
- Added deterministic unit tests for disabled feed and unknown symbol.
- Not integrated into TradeSetup decision yet. Existing spot market data remains, so futuresSignalsEnabled remains false and /api/v1/futures/readiness stays BLOCKED.
- GitHub Actions failed on this revision; root CI cause not retrievable via connector. Maven and startup tests on phive-server still required. Do not deploy until verification.
- Remaining: futures candles/depth/taker pressure and historical OI, basis and funding history, exchange filters and liquidation tiers, position/risk lifecycle, after-fee backtesting and paper tracking, Manifold-only renaming/Polymarket legacy removal, UI.


## 2026-10-28 — CoinAPI synthetic fixture groundwork

Added disabled-by-default /api/v1/dev/futures-fixtures/{symbol} with NORMAL/STALE/MISSING_OI/DISCONNECTED responses and JUnit checks. This is an isolated internal normalized simulator with explicit SYNTHETIC_FIXTURE provenance, not verified CoinAPI wire compatibility and not connected to live TradeSetup. Requires Maven validation on server. See docs/development/coinapi-adapter-validation.md. Real Futures market data and model performance remain unvalidated.


## 2026-10-28 — Offline Futures candle replay engine (main; not deployed)

Added `FuturesReplayEngine` as a pure deterministic Java component and five JUnit tests. Explicit SYNTHETIC_FIXTURE or HISTORICAL_REPLAY provenance; strict ascending OHLC timestamps, direction validation, LONG/SHORT entry/exit, conservative stop-before-target on intrabar ambiguity, adverse slippage, two-sided trading fees and TIME_EXIT at final candle. No HTTP endpoint or connection to live TradeSetup; no exchange order placement. PnL is only a partial net-of-trading-fee simulation, not complete Futures net PnL: funding, liquidation, margin tiers, quantity step filters, venue fee tiers, partial fills, realistic intrabar path, and risk limits remain missing. Requires Maven verify on phive-server; do not treat results as evidence of profitable strategy.


## 2026-10-28 — Offline futures performance metrics on main

Added FuturesPerformanceAnalytics with explicit replay-only provenance checks and tests. Evaluates closed replay outputs, excludes no-entry scenarios from executed trades, computes net expectancy, win rate, profit factor (null if there are no losing trades), and max drawdown in quote currency. All outputs are experimental and exclude funding, liquidation, and exchange-specific impacts. Requires Maven server verification. Not connected to the live Decision Engine; futures readiness remains BLOCKED.


## 2026-10-28 — Intraday risk preflight (main; isolated)

Added pure Java FuturesIntradayRiskGuard and six unit tests. Fails closed for synthetic or stale data, account state not fresh, daily realized-loss threshold, maximum open positions, or consecutive-loss cooldown. Passing a preflight is NOT a live trade authorization; no order execution is performed. This isolated guard is NOT yet wired into TradeSetup, persistence, actual exchange account or an end-to-end trading workflow. Current equity daily-loss calculation is preliminary and needs authoritative start-of-day equity plus unrealized PnL and pending exposure before live use. The existing Futures readiness endpoint remains BLOCKED. Requires Maven verify on phive-server.


## 2026-10-28 — Conservative candle replay hardening

On main: full candle series is now validated before running a trade replay, even if a trade would stop out early. Entry-candle target touches no longer count as TARGET because OHLC cannot establish whether target occurred before entry. STOP on the entry candle still wins conservatively. Added two regression tests and adjusted the short target fixture to require a second candle. Maven verify is pending on phive-server. This improves simulation integrity but is NOT a full intrabar execution model; funding, liquidation, orders, exchange fee tiers, and walk-forward validation remain unimplemented. Futures readiness stays BLOCKED.


## 2026-10-28 — Independent futures data gate

Added FuturesSignalGate and four unit tests to main. Validates declared live provenance, symbol and contract metadata, time ordering/freshness, mark/index/funding/OI, execution candles/order book freshness, and contract/liquidation model verification flags. This gate is currently standalone, NOT wired to TradeSetup; these boolean flags must only come from trusted verified services, never client input. A successful gate result merely validates supplied data, not profitability or permission to trade. Existing Futures readiness stays BLOCKED. Maven verification on phive-server pending.


## 2026-10-28 — Offline futures orchestration

Added FuturesOfflineValidationPipeline combining replay results and performance metrics in a single isolated offline entry point, with three tests for synthetic batch PnL, disallowed LIVE_EXCHANGE provenance, and empty report. It does not yet run real FuturesSignalGate or FuturesIntradayRiskGuard, and is not connected to live TradeSetup or the CoinAPI adapter. No live order functionality. Maven verify is pending on phive-server; overall Futures readiness remains BLOCKED.


## 2026-10-28 — Experimental offline intraday decision engine

Added FuturesIntradayDecisionEngine and five tests on main. The separate engine proposes WATCH_LONG/WATCH_SHORT only with offline provenance, completed candles, 5m/15m momentum agreement against 1h/4h trend; otherwise WAIT. Initial thresholds are hypotheses only and NOT empirically optimized, and the engine is not yet coupled to replay, risk guard, CoinAPI adapter, or live TradeSetup. Requires Maven validation on phive-server; no live Futures recommendation is authorized.


## 2026-10-28 — Experimental strategy-to-replay integration

Added FuturesStrategyReplayPipeline and four tests. The pipeline joins offline 5m/15m decision outputs with future-only execution candles, side/provenance checks, replay outcomes, and aggregate performance; WAIT candidates produce no trade. This only checks supplied timestamps and provenance: score construction is not independently verified to exclude future data, and risk guard, CoinAPI adapter, historical ingestion, funding and liquidation remain unintegrated. Maven verification pending on phive-server; live Futures readiness remains BLOCKED.


## 2026-10-28 — Replay candidate chronology hardening

The experimental FuturesStrategyReplayPipeline now rejects nonascending decision timestamps and overlapping candidate candle windows. This deliberately conservative rule avoids counting overlapping sample windows as independent trades but also may exclude valid overlapping market opportunities. Two regression tests added. This is still an offline prototype; indicator provenance, execution realism, risk integration and historical backtesting remain open. Maven verify pending on phive-server; live readiness BLOCKED.


## 2026-10-28 — Closed candle score derivation (experimental)

Added FuturesClosedCandleScoring and four tests: calculates bounded [-100,100] close-to-close 2-interval momentum for 5m, 15m, 1h, 4h. Candle timestamp is explicitly OPEN time, and every candle must have ended by decisionTime. Rejects unclosed/future, malformed, out-of-order candles and non-offline provenance. This is a provisional score formula, not a validated strategy; caller must ensure data is truly exchange-representative and indicator history wasn't selected using future information. It is not yet wired into the strategy replay orchestrator. Requires Maven server verification; Futures readiness remains BLOCKED.


## 2026-10-28 — Closed-candle strategy-to-replay pipeline

Added FuturesCandleStrategyPipeline, which builds offline 5m/15m/1h/4h scores from completed candles, then feeds the existing FuturesStrategyReplayPipeline for chronological execution replay and net performance aggregation. Five regression tests check trade replay, WAIT, unclosed candle rejection, live-provenance rejection, and same-timestamp execution rejection. The existing standalone score-based API remains unchanged for compatibility. This is an experimental OFFLINE_ONLY pipeline: candle authenticity and a continuous historical data ingestion pipeline are not verified, the replay model excludes funding/liquidation/venue constraints, and no production decision endpoints or live futures signals were enabled. Maven verify pending on phive-server; Futures readiness remains BLOCKED.


## 2026-10-28 — Experimental chronological historical runner

Added FuturesHistoricalWalkForwardRunner and four tests. Offline-only walk-forward over contiguous 5m candles, with 15m/1h/4h closed-bar snapshots; constructs scores only using historical closed bars, executes fixed-horizon trades on future 5m bars, excludes overlap and reports aggregate PnL. Rejects missing cadence candles and LIVE_EXCHANGE provenance. Price for pending entry is the following candle's opening price (known only at execution, not scored); this is a simplified fill assumption, not exchange-realistic. No real historical feed, funding, liquidation, strategy optimization, fees by venue, portfolio sizing or end-to-end risk integration. Server Maven verification pending; Futures readiness remains BLOCKED.


## 2026-10-28 — Fix historical walk-forward decision/entry timestamp collision

Diagnosed likely cause of initial 73-test / 1-error Maven failure: decisionTime is close of 5m bar i, exactly equal to open of bar i+1; strategy replay requires strictly later execution candle timestamps. Runner now enters at open of bar i+2 (one extra 5m bar delay) and adjusts horizon bounds, preserving strict anti-lookahead. Added regression test for trending fixture path. This is a conservative experimental fill delay, not an exchange-realistic execution model. Verify with Maven on phive-server; Futures readiness remains BLOCKED.


## 2026-10-28 — Strict UTC candle time boundary validation

Updated FuturesHistoricalWalkForwardRunner to reject any OHLC open timestamp not aligned to its UTC timeframe boundary (5m, 15m, 1h, 4h) or having nonzero subsecond precision, in addition to existing contiguous-cadence checks. Added two regression tests: regularly spaced but minute-shifted 1h candles, and a subsecond-offset 5m candle. The checks validate supplied data shape, not market authenticity; no live signals enabled. Maven verification on phive-server pending and Futures readiness remains BLOCKED.


## 2026-10-28 — Risk-budgeted offline position sizing adapter

Added FuturesPaperPositionSizer, wrapping the existing FuturesRiskCalculator for SYNTHETIC_FIXTURE/HISTORICAL_REPLAY only, with four new tests (LONG, SHORT, risk cap and live-data exclusion). Estimates quantity, risk budget, notional and initial margin on fixed reference equity; rejects unsuitable risk parameters via calculator. This is an isolated paper-only adapter and is NOT yet wired into FuturesHistoricalWalkForwardRunner. Sizing uses simplified fees and slippage, not funding, gap loss, liquidation, exchange minimum steps or real account state. Server Maven verification pending. Futures live readiness remains BLOCKED.


## 2026-10-28 — Opt-in paper sizing on historical runner

Added FuturesHistoricalWalkForwardRunner.runWithPaperSizing(Input, PaperSizing) using existing FuturesPaperPositionSizer for per-candidate risk-budgeted quantity at fixed reference equity. Existing run(Input) retains fixed-quantity behavior. Strict paper constraints cap risk at 1%, leverage at 5x, with margin cap delegated to FuturesRiskCalculator. Two tests exercise sized vs legacy outcomes and invalid parameters. This is NOT compounding/equity-curve sizing; outcomes do not feed back into equity or risk controls. Exchange lot-size, adverse gaps, funding, liquidation and realized drawdown constraints remain unmodeled. Maven server verification pending. No live Futures signals; readiness BLOCKED.


## 2026-10-29 — Experimental realized paper equity ledger

Added FuturesPaperEquityLedger and four regression tests on main. This standalone OFFLINE_ONLY ledger tracks realized net PnL, sequential equity, peak-to-trough absolute drawdown, and a UTC-day loss stop which blocks subsequent submitted trades after the threshold is reached, resetting on the next UTC day. It rejects non-offline provenance and nonascending realized timestamps. **Not yet integrated into FuturesHistoricalWalkForwardRunner or live TradeSetup**: reports use externally supplied realized timestamps and cannot guarantee that trades were opened after a daily stop; equity does not yet dynamically determine the position size; no intratrade equity, unrealized PnL, funding, liquidation or exchange execution rules. Maven verify pending on phive-server. Futures readiness remains BLOCKED.


## 2026-10-29 — Opt-in dynamic equity walk-forward mode

Added FuturesHistoricalWalkForwardRunner.runWithDynamicEquity(Input,PaperSizing,dailyLossLimitPercent) with three tests. Keeps legacy modes intact, sizes each eligible candidate against latest realized simulated equity, enforces UTC daily loss stop before candidate entry, computes equity points and realized peak drawdown, and attributes PnL to conservative full-horizon settlement date. All outputs remain OFFLINE_ONLY. It currently reuses the existing runner for data pre-validation (including its legacy simulation), then performs separate sequential dynamic replay. No unrealized equity, funding, liquidation, position netting, exchange rounding, adverse gaps or true execution fills; the full horizon is reserved even if stop/target is touched sooner. Maven verify still required on phive-server; Futures live readiness BLOCKED.


## 2026-10-29 — Dynamic equity settlement chronology hardening

Fixed runWithDynamicEquity so prior replay PnL is realized at the **close** of the final execution 5m candle, rather than its open. The occupancy guard now excludes candidate decisions through that settlement instant; this prevents next decisions consuming unclosed prior-trade PnL. Added one regression test for strictly ascending, UTC-aligned settlement timestamps. This is a targeted chronology fix, not proof of complete daily-stop enforcement across all scenarios. Future work: explicit loss-triggering stress fixtures, pre-entry settlement day boundaries, realistic time-to-exit, and account-state risk integration. Maven verification pending on phive-server; live Futures remains BLOCKED.


## 2026-10-30 — Adverse-day daily-loss stress regression

Added a targeted synthetic stress test for runWithDynamicEquity: strong closed-candle bullish signals combined with adverse 5m lows that touch protective stops, a restrictive 0.5% daily realized-loss limit, blocked subsequent entries, and re-enabled entries after the next UTC day. The test also checks offline-only status and negative net equity. No production/live changes, and no exchange-side orders or automatic position closures. The daily rule remains realized-PnL based and can exceed its threshold on the triggering trade; funding, gaps, unrealized exposure and venue-specific liquidation are not modeled. Maven verify pending on phive-server; Futures readiness BLOCKED.


## 2026-10-30 — Offline daily risk dashboard snapshot model

Added FuturesPaperRiskStatus and five tests: OFFLINE_ONLY status NORMAL, LOSS_MONITORING, DAILY_RISK_LOCK, remaining realized-loss allowance and UTC risk day. Data provenance restricted to SYNTHETIC_FIXTURE or HISTORICAL_REPLAY; results cannot authorize live signals. This standalone presentational snapshot is not yet wired into the Angular dashboard or dynamic runner, and callers must provide internally trustworthy ledger values. Limit uses day-start equity with realized PnL only and omits unrealized losses, funding, liquidation, exposure and external account reconciliation. Maven verify pending on phive-server; Futures live readiness remains BLOCKED.


## 2026-10-30 — Offline dynamic replay to risk status adapter

Added FuturesPaperRiskReport.fromReplay: a pure read-only adapter reconstructing day-start equity, realized daily PnL and FuturesPaperRiskStatus from a chronological DynamicReport, with four regression tests for intraday state, next-UTC-day reset, future equity event rejection, and LIVE_EXCHANGE rejection. API exposure and Angular dashboard integration are explicitly deferred until authenticated/trusted replay persistence exists; accepting arbitrary client-supplied replay/account state as authoritative would be unsafe. Still OFFLINE_ONLY, live Futures BLOCKED. Maven verify pending on phive-server.


## 2026-11-01 — Gated internal offline replay snapshot read API (phase 1)

Added process-local FuturesPaperReplayRegistry, registering only server-side OFFLINE_ONLY DynamicReport snapshots with strict provenance and chronology checks via FuturesPaperRiskReport. Added GET /api/v1/dev/futures-paper/replays/{id}/risk as a read-only endpoint, default disabled via FUTURES_PAPER_REPLAY_READ_ENABLED=false; missing IDs and disabled requests return HTTP 404. Added four unit tests covering disabled endpoint, registered snapshot, live rejection and future settlement rejection. **No public POST/write endpoint, no automatic runner registration, no persistent PostgreSQL storage, no Angular integration.** Process-local data disappears at restart; UUID lookup is not an authentication mechanism. Keep endpoint disabled on internet-facing deployments until authorization is designed. Maven verify pending on phive-server. Futures live readiness remains BLOCKED.


## 2026-11-01 — Gated PostgreSQL paper replay archive (phase 2)

Added FuturesPaperReplayArchive (R2DBC DatabaseClient, opt-in), manual schema backend/src/main/resources/db/futures_paper_replay_archive.sql, and read-only FuturesPaperReplayArchiveController endpoints GET /api/v1/dev/futures-paper/archive and /{id} with limit capped at 100. Added four unit tests for server-side archive summary validation. Config FUTURES_PAPER_REPLAY_ARCHIVE_ENABLED=false and FUTURES_PAPER_REPLAY_ARCHIVE_READ_ENABLED=false by default. Archive saves only validated OFFLINE_ONLY snapshots via internal saveTrusted(), not direct browser input; currently **no automatic integration with runner or registry**, and schema installation must be done manually in a trusted development database. Not yet production-ready: no authentication on developer GET, no authorization or retention, no end-to-end PostgreSQL integration test, no Angular UI. Do not enable internet-facing read flags. Maven build pending on phive-server; live Futures readiness remains BLOCKED.

Added two further controller guard tests for fail-closed disabled routes and bounded history limit (1..100). Total new unit tests in this phase: six. Server Maven verification pending.


## 2026-11-02 — Novice-friendly Angular Simulasi & Risiko workspace

Added a dedicated sidebar view `paper-risk` to the existing single-shell Angular application, retaining the default Market dashboard. New standalone PaperRiskComponent provides an explicit simulation-only banner, explanations of NORMAL/LOSS_MONITORING/DAILY_RISK_LOCK, beginner definitions of equity/drawdown and UTC reset, refresh control, responsive historical replay cards, loading/disabled/empty/error states, and live-signal disclaimer. Uses read-only `GET /api/v1/dev/futures-paper/archive?limit=20`; does NOT fabricate summary metrics when archive is absent. Currently archive reads remain OFF by default and there is no automatic runner-to-PostgreSQL write flow, so expect the informative unavailable state. No authorization has been added: do not expose archive endpoints publicly or enable developer flags on internet-facing deployment. No backend live signal/order changes. `npm run build` pending on phive-server, no UI deployment performed. Future: authenticated archive access, verified DB integration and validated persisted replay records including time series for charts.


## 2026-11-02 — Angular navigation compile fix

Fixed `AppComponent.navigateToView` parameter union to accept `paper-risk`, matching sidebar template and activeSection. Resolves reported TS2345 at app.component.html line 40. Existing NG8102 nullish coalescing warnings are separate. Frontend `npm run build` pending verification on phive-server; no deployment performed.


## 2026-11-02 — Fix washed-out paper risk mobile theme and floating BTC overlay

Replaced dark-theme fallback text and translucent cards in PaperRiskComponent with an accessible light palette: dark heading/body text, white cards, clear blue-violet reload action, visible borders, status accents, mobile vertical header/layout and extra bottom padding. On paper-risk view the redundant floating BTC selector is now hidden; on other mobile views its un-dragged form is compact (158px max) to reduce obstruction while retaining drag/menu functionality. No changes to market signals, storage, security flags or live trading. `npm run build` and visual mobile/desktop verification pending on phive-server; no deployment performed.


## 2026-11-02 — Internal offline replay workflow bridge

Added `FuturesPaperReplayWorkflow` as a Spring service with `runAndRegister()` to execute dynamic paper replay and register an internally computed snapshot, plus `archiveRegistered(UUID)` to explicitly persist that trusted snapshot using the existing R2DBC archive when enabled. No HTTP POST, no automatic archive subscriptions, no production migration or API flag changes. Added three regression tests for fail-closed input/live/unknown ID. Archive persistence remains opt-in and must be independently validated against PostgreSQL after manually applying its schema. Existing archive GET endpoints remain disabled by default and unauthenticated; do not enable them on an internet-facing server. Maven verify pending on phive-server; Futures live readiness BLOCKED.


## 2026-11-02 — Optional PostgreSQL archive integration validation

Added `FuturesPaperReplayPostgresIT` (manual opt-in JUnit test) which runs only when `FUTURES_ARCHIVE_TEST_CONFIRM=ISOLATED_TEST_DATABASE_ONLY` and explicit test R2DBC URL are supplied. Refuses the standard `/coinmind` database name, inserts a random-id synthetic OFFLINE_ONLY summary, reads it through a separately created repository object, checks history/idempotent insert, and removes its test row. Instructions: `docs/development/paper-replay-postgres-test.md`. This is NOT enabled in normal `mvn verify` and has NOT been run against a live database yet. No schema applied or production flags changed. Further work needed for secure archive API, automated persistence and persisted equity time series. Futures live remains BLOCKED.


## 2026-11-02 — Realized equity curve persistence groundwork

Added `FuturesPaperEquityArchive` as opt-in R2DBC storage of server-computed realized equity points and time-ordered reads, plus isolated SQL `backend/src/main/resources/db/futures_paper_replay_equity.sql` (FK to replay summary). Registry now exposes an internal defensive curve copy. `FuturesPaperReplayPersistence.archiveRegisteredWithCurve(UUID)` explicitly archives an already registered trusted OFFLINE_ONLY replay: validates chronology, PnL arithmetic, final equity and executed count, then stores summary and curve. **Not automatic**; no new HTTP endpoint and no public API activation. Writes are not yet atomic across the two tables; failures may leave a summary without full curve and must be retried using the same ID. Added five curve validation tests. Test DB migrations must be run manually in an isolated DB only; neither Maven nor production DB migrations have been executed here. Next: verify Maven, dedicated PostgreSQL test and transaction-safe archive, then authenticated UI read/graph. Do not advertise demo values as actual backtests; Futures live stays BLOCKED.


## 2026-11-02 — Atomic PostgreSQL replay summary + equity curve archive

Updated `FuturesPaperReplayPersistence` to wrap `summaries.saveTrusted` and `curves.saveTrusted` in a reactive `TransactionalOperator` backed by `R2dbcTransactionManager` using the application `ConnectionFactory`. A failure inserting any equity point should now roll back the summary transaction rather than leave a partial archive (subject to actual PostgreSQL integration verification). Validation remains ahead of the transaction; there is still no automatic replay scheduling, public POST route, schema migration or enabling of archive API flags. Maven verification and isolated PostgreSQL rollback test are pending on phive-server. Existing `FuturesPaperReplayWorkflow.archiveRegistered` remains a summary-only internal method; call `FuturesPaperReplayPersistence.archiveRegisteredWithCurve` for atomic summary+curve archives. Futures live stays BLOCKED.


## 2026-11-02 — Secure opt-in PostgreSQL verification and transaction rollback test

Introduced `FuturesArchiveTestConnection` to assemble isolated test R2DBC ConnectionFactory from a credential-free URL (`r2dbc:postgresql://127.0.0.1:5432/coinmind_replay_test`) plus separate `FUTURES_ARCHIVE_TEST_DB_USER`/`FUTURES_ARCHIVE_TEST_DB_PASSWORD` environment variables. Helper accepts only local dedicated DB, and confirmation flag stays mandatory. Updated `FuturesPaperReplayPostgresIT`; added `FuturesPaperReplayTransactionIT` which commits summary + curve together and deliberately triggers a duplicate point-key database exception in a subsequent transaction, expecting rollback of both tables. Updated test command in `docs/development/paper-replay-postgres-test.md`. No production DB or API flags touched; Maven and real DB verification on phive-server PENDING. Important: this test proves the same transaction pattern, not an invocation of `FuturesPaperReplayPersistence.archiveRegisteredWithCurve`; service-level integration remains future work. Never include passwords in shared logs. Futures live BLOCKED.


## 2026-11-02 — Validated internal replay history projection

Added `FuturesPaperReplayHistory` as internal read-only service combining archived PostgreSQL summary with ordered realized equity points. It rejects missing/incomplete points, invalid point indices or timestamps, PnL arithmetic mismatches and final-equity mismatches rather than presenting broken curves as valid. Added six focused unit tests and extended isolated `FuturesPaperReplayTransactionIT` to verify read-back after successful commit and absence after rollback. No HTTP route, no auth bypass, no live data, no production DB migration, and archive flags remain OFF. Both Maven verify and dedicated PostgreSQL integration tests need rerun on phive-server; prior user report showed 118/118 unit and 2/2 DB tests before this change. Next steps: service-level archive integration, authenticated read API and Angular chart; Futures live remains BLOCKED.


## 2026-11-02 — Private keyed replay history API + real equity curve UI (verification pending)

Updated previously unsafe developer archive GET routes to fail closed unless read-enabled flag is true **and** a separate server-side read key of at least 32 characters matches `X-CoinMind-Archive-Key` (constant-time comparison). Added GET `/{id}/equity` using internally validated `FuturesPaperReplayHistory`. Angular Simulasi & Risiko now accepts a session-only private key (no localStorage), requests archive list with key, loads persisted per-replay equity and renders an SVG line plus realized PnL rows with provenance. By default APIs remain unavailable; do not publicly enable without further production-grade authentication/access controls, rate limiting, and safe reverse-proxy configuration. A test-specific full `FuturesPaperReplayPersistence` PostgreSQL service round-trip was added as opt-in `FuturesPaperReplayServiceIT`; controller negative authorization regression test added. No automated replay runner/scheduler or production database migration; no fabricated historical curve. Backend Maven, Angular build and test PostgreSQL suite are NOT run in this environment and must be confirmed on phive-server before deploying. Futures live stays BLOCKED.


## 2026-11-02 — Trusted run-and-archive convenience path

`FuturesPaperReplayWorkflow.runAndArchive(input, sizing, dailyLossLimitPercent, completedAt, persistence)` now composes vetted offline dynamic replay -> internal registry -> transactional summary+equity archival in a deferred Mono. No scheduler, unattended automatic execution, or public HTTP writes; a trusted caller still must supply vetted candles and subscribe. Pending server Maven/DB/Angular verification and a production-ready authenticated user flow. Do not turn on developer archive endpoints on public deployments just to make the chart populate.


## 2026-11-02 — Opt-in internal Offline Replay Job

Added `FuturesOfflineReplayJob`, disabled by default via `coinmind.futures.offline-replay-job-enabled:false`. A trusted internal caller may invoke `execute(input,sizing,dailyLossLimitPercent)` with vetted OFFLINE_ONLY fixture/historical bars; it composes the previously tested `FuturesPaperReplayWorkflow.runAndArchive` into transactional PostgreSQL summary+equity archival. It uses boundedElastic for CPU replay work, a process-local single-flight guard to prevent overlapping replay jobs, and a maximum of 100,000 5m candles. No scheduled trigger, HTTP endpoint, CLI, live feed or automatic ingestion was introduced. Existing archive flags still default OFF and production credentials/schema are untouched. Added three fail-closed unit tests. Build and integration checks still need running on phive-server; do NOT enable job or archive reads in public deployments until ingest validation, safe internal trigger and production-grade auth are in place. Futures live remains BLOCKED.


## 2026-11-02 — Strict offline candle CSV ingestion foundation

Added `FuturesOfflineCandleImport` pure Java CSV reader with mandatory header `open_time_utc,open,high,low,close`; accepts ISO-8601 UTC instants and decimal OHLC for 5m/15m/1h/4h, validates contiguous timestamps, exact timeframe boundaries, positive and internally consistent candle prices and bounded input (100,000 rows per frame). `assemble()` verifies sequences again before producing `FuturesHistoricalWalkForwardRunner.Input` with explicitly offline provenance only. Added four ingestion unit tests for success, gaps, invalid timeframe/OHLC, wrong headers and non-offline sources. **This neither downloads candles nor verifies external historical authenticity**: provenance must be established by trusted operators before labeling a source `HISTORICAL_REPLAY`. No scheduled job trigger, HTTP upload, production DB migration or live trading access was enabled. Backend Maven verification pending on phive-server. Next: trusted multi-timeframe import from authorized local files and job integration test against isolated DB. Futures live BLOCKED.


## 2026-11-02 — Cross-timeframe OHLC consistency gate

`FuturesOfflineCandleImport.assemble()` now compares each 15m/1h/4h candle to the corresponding aggregate of 5m candles when the full higher-timeframe window is covered by the 5m input; open = first 5m open, close = last 5m close, high/low = max/min. Rejects mismatches before replay. Two unit tests cover matching and mismatching 15m examples. Partial edge windows are deliberately not compared, so this is not a complete data authenticity check; existing runner closed-bar/no-look-ahead validation remains separate. No new network, HTTP, scheduled job or production access. Prior user 132-unit-test build was successful; new 134-unit-test suite awaits verification on phive-server. Further work required for trusted file-bundle ingest and fully isolated CSV→job→PostgreSQL integration. Futures live BLOCKED.


## 2026-11-02 — Four-stream offline CSV bundle ingestion

Added `FuturesOfflineCsvBundle.read(...)` for four explicitly trusted Reader streams (5m/15m/1h/4h). It reuses strict CSV row parsing and the new complete-window cross-timeframe consistency gate, returning a validated `FuturesHistoricalWalkForwardRunner.Input` ready for a trusted caller to pass to the disabled-by-default internal job. No filesystem path handling, auto-discovery, exchange API call, scheduled trigger, or public HTTP endpoint. Added two tests for four-frame fixture and rejection of live provenance before reading. **136 unit tests expected**, pending Maven verification on phive-server. Import and job are not yet automatically connected to real historical datasets or production persistence. Futures live BLOCKED.


## 2026-11-02 — Isolated four-CSV-to-PostgreSQL end-to-end integration test

Added opt-in `FuturesOfflineCsvToPostgresIT`: produces four synchronized synthetic flat OHLC CSV streams (5m 325 bars, 15m 109, 1h 28, 4h 7); reads via `FuturesOfflineCsvBundle`, executes `FuturesOfflineReplayJob` with explicit test-only enablement, persists via the real transactional `FuturesPaperReplayPersistence` to isolated PostgreSQL, then reloads via `FuturesPaperReplayHistory` and deletes the test snapshot. Explicitly tests a zero-trade flat-market case (empty equity curve and unchanged initial/final equity), **not** a profitable trading scenario or a nonempty equity curve. Requires isolated `coinmind_replay_test` database, same test credentials and `FUTURES_ARCHIVE_TEST_CONFIRM=ISOLATED_TEST_DATABASE_ONLY`; never deploy or enable public routes. Maven integration execution on phive-server PENDING. Follow-up: add nonzero-trade CSV integration fixture and more failure-path coverage, then trusted real-data ingestion and operational auth. Futures live BLOCKED.


## 2026-11-02 — Nonempty realized-PnL CSV-to-PostgreSQL scenario

Extended isolated `FuturesOfflineCsvToPostgresIT` with a second end-to-end scenario constructing consistent rising synthetic 5m/15m/1h/4h CSV streams, invoking the real opt-in internal job, persisting through R2DBC, and independently reading back and reconciling the nonempty realized equity curve against net PnL and final equity. Existing zero-trade flat case remains. The fixture is synthetic; results must never be represented as genuine historical trading performance. This new test has NOT been executed on phive-server yet: run dedicated integration suite with isolated DB and report failures for correction. No production DB, live trading, HTTP route or scheduler changes. Futures live BLOCKED.


## 2026-11-02 — Fix weak synthetic trend in CSV/PostgreSQL integration fixture

The isolated `FuturesOfflineCsvToPostgresIT.trendingCsvProducesPersistedRealizedPnl` failed on phive-server with zero executed trades (5 tests; 1 failure). Investigated the actual decision engine: five/15m weighted momentum requires absolute score >=55; prior fixture increased price by only 0.01 per 5m bar and correctly triggered WAIT. Changed ONLY the test generator to rise by 1 price unit per 5m bar; higher-timeframe OHLC remains derived from the same 5m sequence. No lowered strategy thresholds, no production logic changes, no assertion weakening. Integration test on isolated PostgreSQL must be rerun and may uncover further constraints. Futures live BLOCKED.


## 2026-11-02 — Fix exact equity ledger accumulation after nonempty CSV integration failure

User's test progressed beyond zero-trade assertion but failed with `Invalid or future realized equity event` in `FuturesPaperRiskReport.fromReplay`. Audited runner math: `equity.add(netPnl, MathContext.DECIMAL64)` rounded equity after individual netPnL was calculated, while downstream risk/archive validation requires `previousEquity + netPnl == currentEquity` exactly. Changed realized equity, daily PnL and max drawdown accumulation to exact BigDecimal arithmetic (leaving fee/slippage/strategy calculations intact). Strengthened dynamic runner regression to reconcile each realized point and final equity with exact sums. This is a plausible root cause, not independently confirmed without full exception stack trace and rerun; phive-server dedicated PostgreSQL integration tests and `mvn clean verify` are PENDING. No archive validation relaxation, no live trading or production data updates. Futures live BLOCKED.


## 2026-11-02 — PostgreSQL numeric precision fix for replay readback

User confirmed `mvn clean verify` passes 136 tests but nonempty CSV-to-PostgreSQL integration fails in `FuturesPaperReplayHistory.verify` with `Archived equity/PnL mismatch` **after** successful archive write. Root cause: both SQL schemas use fixed `NUMERIC(28,10)` for every equity/PnL monetary column, while replay computations preserve BigDecimal digits beyond 10 decimal places. PostgreSQL silently rounds individual equity and PnL on insert, breaking exact `previous equity + net PnL = stored equity` validation on readback. Updated both **new installation schemas** to unconstrained `NUMERIC`; added `backend/src/main/resources/db/futures_paper_replay_precision_migration.sql` to ALTER relevant monetary columns on **existing isolated DBs**. Do not assume changing CREATE TABLE IF NOT EXISTS alters existing tables. Run migration manually against `coinmind_replay_test` only, then rerun 2 integration tests. Values already rounded in old rows cannot be reconstructed; preserve strict arithmetic validator. No production DB altered, no live trading changes. Migration and test results on phive-server PENDING. Futures live BLOCKED.


## 2026-11-02 — Historical dataset integrity gate

The user confirmed isolated `FuturesOfflineCsvToPostgresIT` **2/2 tests passed** after manually applying `futures_paper_replay_precision_migration.sql` to `coinmind_replay_test`; production remains unchanged. Added `FuturesOfflineVerifiedDataset` to require an operator-reviewed explicit source, `SPOT` or `USD_M_FUTURES` market, symbol, `HISTORICAL_REPLAY` provenance, and 4 SHA-256 hashes of UTF-8 CSV strings before strict four-frame replay import. Added three unit tests: valid fixture, tampering rejection, invalid provenance/market. SHA-256 is only content integrity versus supplied hashes, **not cryptographic provider authentication or evidence that candles are genuine**. No dataset has been downloaded, no network, no production DB/API/scheduler/live trading access. Next: vetted real BTCUSDT Futures source acquisition and reproducible local artifact metadata, then run historical walk-forward. New backend unit suite expected 139 tests, **not yet verified** by phive-server.


## 2026-11-02 — Official Binance USD-M Futures archive acquisition (opt-in)

User confirmed 139/139 backend unit tests passed. Added standalone Python standard-library `scripts/offline/binance_um_monthly.py` for **explicit opt-in** downloading of completed monthly BTCUSDT USD-M Futures ZIP klines (5m,15m,1h,4h) from Binance public archive `https://data.binance.vision/data/futures/um/monthly/klines/`. Every ZIP is checked against the companion provider `.CHECKSUM` SHA-256 before bounded one-member extraction; 12-column rows normalize to `open_time_utc,open,high,low,close` with UTC open times, contiguous candles and OHLC checks. Writes four normalized CSVs and `manifest.json` containing symbol, market, origin URL, provider ZIP checksum, local CSV hash and counts. Added offline conversion tests in `scripts/offline/test_binance_um_monthly.py`. Usage from repo root: `python3 -m unittest discover -s scripts/offline -p 'test_*.py'`; `python3 scripts/offline/binance_um_monthly.py --month 2026-10 --output /home/phive/coinmind-btcusdt-2026-10` (new directory; no overwrites). Inspect source files/checksums and manifest before internal historical replay. **The script has not been run against the live archive or tested on phive-server**, and does not invoke the Java replay/job nor PostgreSQL. Archive completeness, remote availability, network access and normalized multi-frame agreement need validation on server. It does not require API keys or enable public trading endpoints. Do not commit downloaded data or sensitive local artifacts. Futures live BLOCKED.


## 2026-11-02 — Diagnose real-archive timestamp failures without weakening integrity checks

User ran Python 2/2 tests successfully; actual `--month 2026-10` downloader failed `ValueError: Unaligned, duplicate, or missing candles` while normalizing first archive(s), before a manifest was written. The previous error collapsed timestamp unit, unaligned clock and true missing candles into one message. Updated Python normalizer to explicitly recognize 13-digit millisecond and 16-digit microsecond epoch timestamps, reject fractional or nonconforming timestamps, and report timeframe, row, expected UTC open, actual UTC open and exact delta for gaps or duplicates. **Real-archive source of failure not yet confirmed**; Binance documentation specifically describes microseconds for SPOT starting 2025, not necessarily Futures. No missing candles filled and no thresholds bypassed. Move output directory creation after successful validation of all four archives so failed runs do not leave partial output. Added 2 Python regression tests; total target 4 tests, pending execution on phive-server. Run `python3 -m unittest discover -s scripts/offline -p 'test_*.py'` then retry with a NEW output directory, e.g. `/home/phive/coinmind-btcusdt-2026-10-v2`. If failure recurs, share the precise new error; treat actual gaps as a dataset integrity issue. No trading, DB writes or live endpoints. Futures live BLOCKED.


## 2026-11-02 — Operator-only verified September historical replay test

User downloaded official BTCUSDT USD-M Futures September 2026 archive on phive-server: 5m=8640, 15m=2880, 1h=720, 4h=180, local manifest hashes all match. Added `backend/src/test/java/com/coinmind/trade/futures/FuturesHistoricalDatasetReplayIT.java` opt-in JUnit integration test, gated by `COINMIND_HISTORICAL_DATASET_DIR`, to read local manifest/files (no network), enforce exact four timeframe filenames/order/official source URL metadata, month completeness, hash/count checks, call existing Java multi-frame OHLC verified loader and run realized-equity walk-forward. Prints executions, waits, risk blocks, realized net PnL, max quote drawdown, wins/losses/win-rate/profit factor and reconciles all equity points. This is a first research/backtest baseline only: default sizing/equity/fees/slippage are explicit test assumptions; no funding, liquidation, venue fill realism, out-of-sample testing or profitability assertion. Does NOT persist PostgreSQL or expose HTTP/order execution. Use `cd backend && COINMIND_HISTORICAL_DATASET_DIR=/home/phive/coinmind-btcusdt-2026-09 mvn '-Dtest=FuturesHistoricalDatasetReplayIT' test`; integration test is disabled unless env supplied. Actual test compilation, Sept OHLC cross-timeframe agreement and replay outcomes are PENDING verification on phive-server. `mvn clean verify` standard should remain 139 tests (this opt-in test skipped unless enabled). Futures live BLOCKED.


## 2026-11-02 — Compile fix for opt-in historical replay integration test

User's phive-server `mvn clean verify` failed in `testCompile` because `FuturesHistoricalDatasetReplayIT` imports `com.fasterxml.jackson.databind.JsonNode/ObjectMapper`, but Jackson 2 databind was absent from the Maven test classpath (Spring Boot 4 project). Added explicit `com.fasterxml.jackson.core:jackson-databind:2.19.4` with Maven `test` scope to `backend/pom.xml`; no production runtime dependency, business logic, trading or database change. Re-run `mvn clean verify`, then `COINMIND_HISTORICAL_DATASET_DIR=/home/phive/coinmind-btcusdt-2026-09 mvn '-Dtest=FuturesHistoricalDatasetReplayIT' test`. The historical replay test itself and new dependency resolution are NOT yet verified on phive-server. Futures live BLOCKED.


## 2026-11-02 — Offline historical per-trade diagnostics

User confirmed real BTCUSDT USD-M Sep 2026 historical backtest `BUILD SUCCESS` with 15 executed trades, 5 wins, 10 losses, net PnL about -8.30 USDT, profit factor ~0.546. Added immutable `TradeDetail` records to dynamic replay results with decision UTC, execution-window open UTC, conservative settlement UTC, LONG/SHORT, entry/exit reason, fill prices, gross PnL, fees and net PnL. Preserved old 11-argument `DynamicReport` constructor for existing callers/fixtures; does not alter strategy/risk/entry/exit or persistence schema. Historical opt-in test now prints `COINMIND HISTORICAL TRADE` detail rows, and unit test reconciles trade results with realized curve. Disclaimer: settlement timestamp is end-of-reserved-horizon accounting time, NOT necessarily actual STOP/TARGET touch time; execution-window open is not a precise actual fill timestamp. No production API or trading changes. Build/test execution on phive-server PENDING. Run `mvn clean verify` and then `COINMIND_HISTORICAL_DATASET_DIR=/home/phive/coinmind-btcusdt-2026-09 mvn '-Dtest=FuturesHistoricalDatasetReplayIT' test`. Futures live BLOCKED.


## 2026-11-02 — Historical backtest diagnostics and horizon comparison

After user's successful September 2026 offline backtest (15 trades, 5 wins/10 losses, ~-8.30 USDT net), added pure `FuturesBacktestDiagnostics` grouping by LONG/SHORT and STOP/TARGET/TIME_EXIT with counts, wins/losses, gross PnL, total fees, net PnL and average net PnL. Added unit regression ensuring group partitions reconcile to total realized equity. Extended opt-in `FuturesHistoricalDatasetReplayIT` to print `COINMIND HISTORICAL GROUP` summaries and run 2-, 3-, and 6-bar horizons on the exact same verified dataset, risk sizing and cost assumptions, labeled IN-SAMPLE EXPLORATION (not parameter optimization). No strategy thresholds, entry/exit rules, risk limits, live order routing, database or public endpoint changes. New backend unit test count expected 140, unverified on phive-server; historical output also pending. Run `mvn clean verify` then `COINMIND_HISTORICAL_DATASET_DIR=/home/phive/coinmind-btcusdt-2026-09 mvn '-Dtest=FuturesHistoricalDatasetReplayIT' test`. A separate out-of-sample month is required before any strategy/parameter recommendation. Futures live BLOCKED.


## 2026-11-02 — Read-only multi-month holdout comparison launcher

User confirmed real September BTCUSDT USD-M replay and diagnostics successfully ran. Added `scripts/offline/compare_historical_months.py` to orchestrate the EXISTING checksum/source/month-validated `FuturesHistoricalDatasetReplayIT` once per supplied month in isolated Maven test processes, defaulting to Jul/Aug/Sep 2026. It fails closed when any dataset directory is absent, the JUnit process fails, or report month/market/symbol/metrics mismatch. No CSV is written unless all months succeed; creates a new metrics-only CSV, not DB data. Added 3 parser unit tests. No strategy/entry/exit/fee parameters changed, no orders or production DB updates. Run `python3 -m unittest discover -s scripts/offline -p 'test_*.py'`, then download July and August into `/home/phive/coinmind-btcusdt-2026-07` and `...-08` with `binance_um_monthly.py` (new paths only), then from repo root run `python3 scripts/offline/compare_historical_months.py --backend backend --dataset-root /home/phive --months 2026-07 2026-08 2026-09 --output /home/phive/coinmind-historical-comparison-2026-07-09.csv`. WARNING: July/Aug are EARLIER periods; this is chronological retrospective robustness/holdout, not a genuinely later prospective out-of-sample. Maven/report and new Python tests pending server verification. Futures live BLOCKED.


## 2026-11-03 — Cross-month strategy diagnosis, trade-side and exit breakdown

Following user's successful Jul/Aug/Sep BTCUSDT USD-M monthly comparison (11, 20, 15 trades respectively; all 3 months net negative), extended `scripts/offline/compare_historical_months.py` to require complete reconciled `COINMIND HISTORICAL GROUP` output from each existing verified Java historical integration test. New `--groups-output` produces an additional CSV with month, dimension (side/exit), category LONG/SHORT/STOP/TARGET/TIME_EXIT, trade count, wins, losses, gross, fees, net and average net. Strictly checks 5 unique categories per month; both the side and exit partitions must independently sum to that month's executed count and exact net PnL. Prints independent-month aggregate trading fees/net PnL; not compounded portfolio performance. All exports remain offline/read-only and fail closed on missing/mismatched results. Three added parser tests (expected Python suite 10 tests), **not yet run on phive-server**. Usage after `git pull`: `python3 -m unittest discover -s scripts/offline -p 'test_*.py'` then `python3 scripts/offline/compare_historical_months.py --backend backend --dataset-root /home/phive --months 2026-07 2026-08 2026-09 --output /home/phive/coinmind-historical-comparison-2026-07-09-v2.csv --groups-output /home/phive/coinmind-historical-groups-2026-07-09.csv`. New output paths must not exist. No modification of strategy, production API, database, or trading; Futures live BLOCKED.


## 2026-11-03 — Isolated LONG-only versus baseline research runner

User's validated BTCUSDT USD-M Jul/Aug/Sep baseline: 46 trades (LONG 37, SHORT 9), net -35.241 USDT, fees 27.512 USDT; SHORT 1/9 wins, TIME_EXIT 45/46. Added explicit `FuturesHistoricalWalkForwardRunner.ExperimentalDirection` (`BOTH`, `LONG_ONLY`) and `runWithDynamicEquityExperimental(...)`. The existing `runWithDynamicEquity(...)` delegates to `BOTH` and keeps original semantics. For LONG_ONLY the WATCH_SHORT candidate is discarded **before** sizing, risk accounting, execution-window occupancy, and equity updates, rather than post-hoc removing SHORT PnL. The official historical manifest-gated JUnit test runs a predeclared 2x4 matrix (BOTH/LONG_ONLY x 2/3/6/12 five-minute bars), reconciles baseline BOTH/2 result, and emits `COINMIND EXPERIMENTAL SCENARIO` lines. Added regression tests for baseline parity and bearish synthetic SHORT exclusion. `scripts/offline/compare_historical_months.py` now accepts optional `--experiments-output` and validates all 8 scenarios per month, with BOTH/2 matching the existing monthly baseline before writing an experimental CSV. Added two parser tests. No strategy optimization, production mode, DB write, API or trading order changes. Data from Jul–Sep was previously inspected; these are IN-SAMPLE retrospective comparisons, not an unseen prospective validation. Build/new code/tests pending phive-server verification. Expected backend default JUnit total increases from 140 to 142; Python from 10 to 12. Run `mvn clean verify` and `python3 -m unittest discover -s scripts/offline -p 'test_*.py'` before opt-in historical multi-month comparison. Futures live BLOCKED.


## 2026-11-03 — Frozen-candidate untouched-month retrospective validation

User verified GitHub main `e0837c1`: Java Maven 142/142 and Python 12/12 passed, Jul-Aug-Sep full 24 scenario grid succeeded. Aggregated independent-month results: BOTH 10m -35.24 USDT (0/3 months positive), BOTH 60m +17.00 (2/3), LONG_ONLY 60m +18.77 (2/3). This is an in-sample retrospective exploration, not validated profitability. Added `scripts/offline/validate_frozen_candidates.py`: fixed predeclared selections (BOTH 2 bars baseline, BOTH 12 bars, LONG_ONLY 12 bars), one explicitly named month not among July/August/September, checks existing Maven `FuturesHistoricalDatasetReplayIT` manifest + checksums and complete groups + 8 experimental scenarios, baseline parity, then writes a new CSV with trades/wins/losses/net PnL/max realized drawdown and delta to baseline. Raises errors and writes nothing if the dataset is missing, incomplete or test fails. Added 3 Python regression tests. Default suggested holdout is June 2026, not previously reviewed in this session: it PRECEDES the exploratory months so is an untouched retrospective robustness check, not prospective / chronological forward OOS. October 2026 is known to have a missing 24-hour 5m interval and must NOT be silently filled or accepted. No parameter selection, no changes to strategy/production API/DB/order execution. New code/tests not yet verified on phive-server. Commands from project root:
```bash
git pull --ff-only origin main
python3 -m unittest discover -s scripts/offline -p 'test_*.py'
python3 scripts/offline/binance_um_monthly.py --month 2026-06 --output /home/phive/coinmind-btcusdt-2026-06
python3 scripts/offline/validate_frozen_candidates.py --backend backend --dataset-root /home/phive --month 2026-06 --output /home/phive/coinmind-frozen-candidates-2026-06.csv
```
Use a fresh output path; do not deploy/restart backend. Futures live BLOCKED.


## 2026-11-03 — Observational market-volatility diagnostics (June failure analysis)

User confirmed July–Sep strategy exploration was followed by June 2026 frozen-candidate retrospective validation: Python 15/15 passed, June Binance USD-M manifest and four TF archives validated, frozen BOTH/10m 48 trades net -76.279794191323238, drawdown 76.279794191323238; BOTH/60m 37 trades net -89.441860214026280, drawdown 92.246225210551555; LONG_ONLY/60m 10 trades net -29.210092881331277, drawdown 29.210092881331277. All three lost money; a 60m horizon is NOT robust. Implemented read-only `FuturesMarketRegimeDiagnostics` which reconstructs last three fully CLOSED candles at each actual historical trade decision across 5m/15m/1h/4h, computes existing scoring input, checks reconstructed side matches trade, records the four timeframe momentum values, entry strength and 5m three-candle high-low range %, and bins the range into descriptive LOW (<0.5%), MEDIUM (0.5% to <1%), HIGH (>=1%). **The bins are descriptive research labels only, not validated volatility or market-regime thresholds.** Existing manifest-gated historical JUnit test emits 3 bands for each of the 3 frozen scenarios BOTH/2, BOTH/12, LONG_ONLY/12 after existing replay. Added Java JUnit tests for reconciliation and failure on mismatched decision/provenance. `validate_frozen_candidates.py` gained optional `--regimes-output`, validating every 3x3 band partition's exact trade counts, wins/losses and net PnL match the frozen scenario before writing CSV; added two Python tests for valid/invalid partition. Existing scenarios and strategy, entry/exit, risk, fees, DB and APIs unchanged, Futures live BLOCKED. Java backend now expected 144 default JUnit tests, Python 17; **not run yet on phive-server**. Run:
```bash
cd /home/phive/Documents/CoinMind-AI
git pull --ff-only origin main
cd backend && mvn clean verify && cd ..
python3 -m unittest discover -s scripts/offline -p 'test_*.py'
python3 scripts/offline/validate_frozen_candidates.py --backend backend --dataset-root /home/phive --month 2026-06 --output /home/phive/coinmind-frozen-candidates-2026-06-v2.csv --regimes-output /home/phive/coinmind-regime-diagnostics-2026-06.csv
```
Do not reuse an existing CSV output path. June is an already inspected retrospective month, not untouched holdout and certainly not prospective OOS; further regime findings are exploratory only. No production deployment/restart required.


## 2026-11-03 — Entry quality and momentum diagnostics (offline descriptive)

Following successful `phive-server` checks (Java 144/144, Python 17/17) and verified June volatility groups: BOTH 10m LOW 1 trade -6.94, MEDIUM 27 -27.23, HIGH 20 -42.11; BOTH 60m LOW 1 -7.02, MEDIUM 20 -45.96, HIGH 16 -36.45; LONG_ONLY 60m MEDIUM 7 -18.78, HIGH 3 -10.43. All populated volatility groups lost on June; volatility band alone did not isolate a profitable subset. Extended existing **read-only** `FuturesMarketRegimeDiagnostics.Observation` with (a) number of momentum directions aligned with the actual entry side across 5m/15m/1h/4h (0-4); (b) a clearly labeled descriptive *deceleration proxy* derived from the two 5m close-to-close changes in the last three **fully closed** 5m bars at decision time, not a verified exhaustion detector; (c) gross PnL and trading fees, in addition to existing pre-entry four momentum values, entryStrength, volatility range/band and net PnL. Historical opt-in integration test prints `COINMIND ENTRY DIAGNOSTIC` rows for the three previously frozen candidates BOTH/2, BOTH/12, LONG_ONLY/12 and checks the row totals reconcile with each replay's net PnL. `scripts/offline/compare_historical_months.py` now has optional `--entries-output` to export the three-candidate per-trade entry diagnostics for the requested months, fail-closed on missing rows, duplicate timestamps, unexpected sides, nonfinite numbers, fees mismatch or failure to reconcile with scenario count, wins, losses and net PnL. New `scripts/offline/summarize_entry_quality.py` summarizes direction/horizon/side/deceleration/alignment with counts, wins, gross, fees and net; Python tests cover valid and corrupt records. No changes to strategy thresholds, entry, exit, risk, signals, production API, database, or trading. FOUR MONTHS June–September are all previously inspected and results will be **retrospective diagnostics only**, not out-of-sample, and descriptive group correlations are not predictive. New code **PENDING Maven/Python/server checks**. Run from repo root:
```bash
git pull --ff-only origin main
cd backend && mvn clean verify && cd ..
python3 -m unittest discover -s scripts/offline -p 'test_*.py'
python3 scripts/offline/compare_historical_months.py --backend backend --dataset-root /home/phive --months 2026-06 2026-07 2026-08 2026-09 --output /home/phive/coinmind-entry-monthly-v1.csv --groups-output /home/phive/coinmind-entry-groups-v1.csv --experiments-output /home/phive/coinmind-entry-experiments-v1.csv --entries-output /home/phive/coinmind-entry-quality-v1.csv
python3 scripts/offline/summarize_entry_quality.py --entries /home/phive/coinmind-entry-quality-v1.csv
```
New CSV output paths must not exist. Expected Java default tests still 144; Python suite grows from 17 to 21. Do not deploy production backend; Futures live BLOCKED.


## 2026-11-04 — Real Binance volume research sidecar + closed-candle RVOL20 (pending server verification)

User requested trading-volume diagnostics alongside momentum. Added opt-in `--include-volume` to `scripts/offline/binance_um_monthly.py`; its existing OHLC output (5m/15m/1h/4h, five CSV columns, manifest file list) is **unchanged**, preserving all verified Java replays. Opt-in additionally extracts genuine 5m Binance USD-M kline column 5 base-volume, 7 quote-volume, 8 trade count, 9 taker-buy base-volume, 10 taker-buy quote-volume into `volume-5m.csv`; checks monotonic aligned timestamps, raw fields nonnegative finite, taker buys <= total, exact OHLC timestamp parity, and adds dedicated `volume_5m` schema/provenance/checksum to manifest. This is an *additional historical dataset*, never a fabricated OHLC-derived volume. New `scripts/offline/analyze_entry_volume.py` verifies manifest identity, hash of both volume sidecar and 5m OHLC, full 5m month count/timestamp continuity, and joins an existing `--entries-output` CSV (from the previous verified multi-month runner) by each recorded decision UTC. RVOL20 = last fully closed 5m candle's base volume / mean BASE volume of the **20 immediately preceding closed 5m candles**, excluding the current reference candle; also exports quote volume, trade count and taker buy base/quote ratios. Refuses missing history or mismatched timestamps. Three new unit tests for sidecar normalization/invalid volume/gaps and three for no-lookahead join/missing history (Python suite expected 27, Java still 144). These are diagnostics only: RVOL20 is not CVD/order-book pressure; taker buy portion is not an order-book imbalance estimate. No changes to strategy, trade decisions, execution, fees, Java backend, API, DB, or production. Four historical months June–September 2026 are already inspected, all retrospective and hypothesis-generating, NOT unseen out-of-sample evidence. **Changes not tested on phive-server yet.** User can run:
```bash
cd /home/phive/Documents/CoinMind-AI
git pull --ff-only origin main
python3 -m unittest discover -s scripts/offline -p 'test_*.py'
for month in 2026-06 2026-07 2026-08 2026-09; do
  python3 scripts/offline/binance_um_monthly.py --month "$month" --include-volume --output "/home/phive/coinmind-btcusdt-$month-volume" || break
done
python3 scripts/offline/analyze_entry_volume.py --dataset-root /home/phive --entries /home/phive/coinmind-entry-quality-v1.csv --output /home/phive/coinmind-entry-with-volume-v1.csv
```
Only use fresh directory/output names. DO NOT deploy; Futures live BLOCKED.


## 2026-11-04 — Taker buy/sell pressure diagnostics (research-only; pending validation)

User shared June–September 2026 per-month BOTH/60m results showing HIGH RVOL + STABLE LONG was not consistently profitable (June -15.80, July +1.29, August +20.49, September -0.53 USDT). High volume alone is insufficient; the user requested continued real development. Added `scripts/offline/analyze_taker_pressure.py` to **read** the existing `/home/phive/coinmind-entry-with-volume-v1.csv` (199 verified historical entry-volume rows produced on phive-server). This tool groups the three fixed candidates BOTH/2, BOTH/12, LONG_ONLY/12 separately by month, side, taker buy base-volume share (>0.5 = BUY_DOMINANT; <0.5 = SELL_DOMINANT; exactly 0.5 = BALANCED; null due to no volume = NO_VOLUME) and 5m momentum deceleration. Exports new CSV with trades, wins, losses, net PnL, average PnL, average taker buy share and RVOL; validates complete existing input columns, allowed candidate/side combinations, nonfinite/negative volume, impossible zero volume, range of taker ratios (base and quote), nonnegative integer trade count, unique (month,candidate,decision timestamp) keys, and total trade/realized PnL reconciliation. Adds five Python tests for exact 50% boundary, month/direction separation, duplicate rejection, nonfinite/invalid shares, zero-volume handling. This is descriptive observational grouping only, **NOT** a filter backtest, nor CVD / live order flow. Do not select rules by the same four months; an actual opt-in execution-time gate needs to operate BEFORE sizing/occupancy/equity in the Java replay with the independently verified original 5m volume sidecar, and must be evaluated on separate reserved data. No changes to Java strategy, entry, exit, API, DB, production, or live trading. New Python code **NOT YET RUN** on phive-server (previous 27-test volume suite was not explicitly verified by user). Run:
```bash
cd /home/phive/Documents/CoinMind-AI
git pull --ff-only origin main
python3 -m unittest discover -s scripts/offline -p 'test_*.py'
python3 scripts/offline/analyze_taker_pressure.py --input /home/phive/coinmind-entry-with-volume-v1.csv --output /home/phive/coinmind-taker-pressure-v1.csv
```
Total expected Python tests: 32 (previous suite 27 + five new tests). Output path must be new. Futures live BLOCKED, and no deploy/restart needed.


## 2026-11-04 — Executable offline Volume Confirmation replay + point-in-time news audit (pending server checks)

Following user-confirmed 32/32 Python tests and 199 input observations / 50 taker-pressure groups, implemented an **opt-in actual chronological volume-filtered replay** (not a trade-row post-filter). `FuturesOfflineVolumeConfirmation` requires aligned actual 5m volume bars, enforces continuous timestamps, nonnegative figures and taker buy ≤ volume, and evaluates the last **fully CLOSED** signal 5m candle using predeclared descriptive conditions: RVOL20 >= 1 (reference base volume versus preceding 20 bars), non-decelerating directional 5m close-to-close change, and taker buy BASE share strictly >50% for LONG or strictly <50% for SHORT. These thresholds are speculative research choices from previously inspected data and MUST NOT be treated as validated production gates. New `FuturesHistoricalWalkForwardRunner.runWithDynamicEquityVolumeResearch` is restricted to `HISTORICAL_REPLAY` and uses the same chronological loop as baseline with gate applied BEFORE position sizing, daily-risk accounting, occupancy, equity updates or fees, so other future entry opportunities can arise. Baseline and regular production behavior unchanged. `FuturesOfflineVolumeCsv` strictly parses the opt-in source sidecar. The existing checksum-verifying `FuturesHistoricalDatasetReplayIT` executes additional 3 frozen scenarios (BOTH/2, BOTH/12, LONG_ONLY/12) only when the manifest explicitly includes `volume_5m`. It validates source URL, manifest schema, source CSV SHA256, timestamp parity and count, and emits `COINMIND VOLUME REPLAY` baseline-vs-gated trades, net PnL, gated fees and realized drawdown. Historical replays remain read-only, no network or orders. New `scripts/offline/compare_volume_replays.py` runs this opt-in test for supplied volume-month directories, checks exact 3 frozen scenarios per month and reconciles reported baseline to preexisting experimental scenario lines, then writes new CSV; three Python tests cover valid and corrupt output. Java new three regression tests validate closed candle and timestamps, improper volume, and actual rerun of occupancy/equity.

Also added **first-seen News Intelligence research foundation**, `scripts/offline/audit_news_at_decision.py`, which requires a REAL captured news CSV with columns `newsId,source,url,publishedUtc,firstSeenUtc`, refuses missing/duplicate/backdated timestamps and invalid HTTP URLs, and counts only articles first actually seen by system within 24h prior to each entry, with `publishedUtc <= decisionUtc`. This is an availability audit only, **not** news sentiment, a future-bar leak, or an actual news-influenced replay. Do not generate fictional historical news or assign firstSeenUtc retrospectively from publication time. No historical news capture has been provided, so do NOT run the news audit as if historical news is present. Four Python tests verify no future news visibility, duplicate/backdated rejects, etc.

**All code pending phive-server tests**, target existing Java 144 + 3 new = 147 default tests, Python 32 + 3 comparison + 4 news audit = 39 tests. No change to production strategy, API, DB, deployed services, Manifold provider, or advisor-only constraint; Futures live stays BLOCKED. Run on phive-server:
```bash
cd /home/phive/Documents/CoinMind-AI
git pull --ff-only origin main
cd backend && mvn clean verify && cd ..
python3 -m unittest discover -s scripts/offline -p 'test_*.py'
python3 scripts/offline/compare_volume_replays.py --backend backend --dataset-root /home/phive --months 2026-06 2026-07 2026-08 2026-09 --output /home/phive/coinmind-volume-filter-replay-v1.csv
```
Verified volume directories expected: `/home/phive/coinmind-btcusdt-2026-{06,07,08,09}-volume`, created in earlier phase. Output CSV path must be new. Do not claim filter profits until user shares actual replay; because all months are previously inspected, results are retrospective and not valid prospective OOS evidence. Production deploy/restart NOT required. Historical news archive capture collection is a prerequisite for meaningful news-augmented trading simulation.


## 2026-11-04 — Future-only real RSS capture and locked volume holdout mode (code committed; tests PENDING server)

The user's 2026-11-04 phive-server run of prior development passed **147/147 Java tests**, **39/39 Python tests**, and 12 checksum-gated volume-filter replay comparisons across June–September. Retrospective total USDT net PnL sums: BOTH/2 baseline -111.520956 vs gated -96.447671; BOTH/12 baseline -72.441201 vs gated -37.679710; LONG_ONLY/12 baseline -10.437672 vs gated +9.491776. These monthly runs independently reset to USDT 1000; August drives much of the LONG_ONLY gain, and September worsens. Results are NOT prospective proof, and no strategy/threshold changes follow.

Implemented `scripts/offline/capture_news_rss.py`: **opt-in real network fetch from user-specified HTTPS official/publisher RSS 2.0/Atom feeds**, restricted to allowlisted domains federalreserve.gov/coindesk.com/cointelegraph.com, rejects redirects and oversized feeds, requires publisher timestamp with explicit timezone, excludes unparseable/future-dated items, sets `firstSeenUtc` from actual UTC successful fetch time and only appends new first-seen metadata. File is CSV compatible with `audit_news_at_decision.py`: `newsId,source,url,publishedUtc,firstSeenUtc`. Exclusive local file lock, no-follow symlink protection, restricted new-file perms, fsync and preservation of preexisting records, fail-closed on corrupt prior rows. Five unit tests exercise RSS, Atom, first-seen immutability, approved domains, and corrupt file rejection. This captures NOW and cannot magically supply an authentic 2026-06..09 contemporaneous news archive. Real news sentiment, classification and price-impact testing are NOT implemented; existing production NewsService is untouched. Capture remains manual/opt-in (no scheduling/daemon deployed).

Extended `compare_volume_replays.py` with `--phase retrospective|future_holdout`. Holdout guard rejects all months before **2026-12**, and months not yet completed. December 2026 is first ENTIRE calendar month after the frozen Nov 4 experiment, so eligible only from Jan 2027 when archives actually become available; strict integrity checks still apply. No claim that eligibility proves data were unseen. Added two Python tests on holdout guard and distinct valid month checks. Added `docs/research/news-capture-and-validation.md` with real Fed/Coindesk feed invocation and future-only replay examples; updated `AGENTS.md` to reflect Manifold Markets ONLY (prior AGENTS mistakenly referenced removed Polymarket) and the Futures live BLOCKED boundary.

**Current changes not yet verified by user/server.** Expected defaults: Java 147 unchanged; Python **46** (prior 39 plus 5 new RSS tests plus 2 guard tests). Handoff:
```bash
cd /home/phive/Documents/CoinMind-AI
git pull --ff-only origin main
python3 -m unittest discover -s scripts/offline -p 'test_*.py'
python3 scripts/offline/capture_news_rss.py --feed-url https://www.federalreserve.gov/feeds/press_monetary.xml --source "Federal Reserve" --archive /home/phive/coinmind-news-first-seen.csv
python3 scripts/offline/capture_news_rss.py --feed-url 'https://www.coindesk.com/arc/outboundfeeds/rss/' --source "CoinDesk" --archive /home/phive/coinmind-news-first-seen.csv
```
Only feed sources which are accessible from phive-server; failed feeds should not be fabricated. The archive must be polled repeatedly to build genuine firstSeen history. Do NOT run future holdout before Jan 2027, DO NOT deploy/restart backend, do NOT enable live futures.


## 2026-11-04 — Server feedback: holdout regex + publisher RSS redirect corrections (PENDING re-test)

User ran `git pull` to `1b53ead`; prior Java `mvn clean verify` passed 147/147 and previous Python 39/39. New Python suite ran 46 with **one failure**: `test_future_holdout_requires_post_freeze_complete_month` reached `Distinct YYYY-MM months required` because new `validate_evaluation_months` had an accidental double-backslash raw regex `r"20\\\\d{2}..."`. Fixed to proper `r"20\\d{2}..."` to recognize valid calendar months while retaining the post-Nov 4 frozen criteria. **15 Federal Reserve RSS articles successfully captured** to `/home/phive/coinmind-news-first-seen.csv`; preserve that CSV. Original CoinDesk `https://www.coindesk.com/arc/outboundfeeds/rss/` was rejected due to its HTTP redirect under strict no-redirect policy. Updated `capture_news_rss.py` to allow only HTTPS redirect destinations within approved publisher domains, bounded at 3 hops, and still validate final URL. No redirect to external domains, HTTP or unapproved hosts. One extra synthetic redirect regression test; documented alternative publisher XML URL `https://www.coindesk.com/arc/outboundfeeds/rss/?outputType=xml` that may avoid redirects. No guarantee server egress/CDN will permit CoinDesk. Did not alter stored news, production news, volume replay, or trading.

**Needs revalidation on phive-server**; expected **47 Python tests** now (prior 46 + new redirect test). Run:
```bash
cd /home/phive/Documents/CoinMind-AI
git pull --ff-only origin main
python3 -m unittest discover -s scripts/offline -p 'test_*.py'
python3 scripts/offline/capture_news_rss.py --feed-url 'https://www.coindesk.com/arc/outboundfeeds/rss/?outputType=xml' --source 'CoinDesk' --archive /home/phive/coinmind-news-first-seen.csv
```
If approved redirect still fails, inspect final 3xx Location via read-only `curl -sSI` or use verified approved publisher alternate; don't silently whitelist unknown third-party domains. No Maven, deploy, or restarts required for these changes. Futures live BLOCKED.


## 2026-11-05 — Gemini API free-tier-first OFFLINE News Intelligence prototype (NOT YET SERVER-VERIFIED)

User has a Gemini API key on Free tier and wants CoinMind-AI to maximize FREE API capabilities before considering paid usage. Reviewed official Google Gemini API docs: `gemini-2.5-flash-lite` and `gemini-2.5-flash` are free-tier-capable, but model quotas are per project and may change. Free-tier API content may be used by Google to improve its products; do not send private user data.

Added `scripts/offline/gemini_news_research.py` with two commands:
- `capture`: fetch publisher-allowlisted HTTPS RSS/Atom via existing secure collector, save actual URL, publication, new real firstSeenUtc, AND the actual RSS title to **separate new headline-only CSV**. The older 40 source metadata records (15 Fed, 25 CoinDesk; previously confirmed by user) have no title and are never retroactively filled/backdated. Titles previously unknown become known only now.
- `score`: default **dry run**, explicit `--execute` reads `GEMINI_API_KEY` environment only, posts batches of <= 5 public headlines to Gemini REST generateContent `gemini-2.5-flash-lite`, JSON responses, bounded 1 to 3 batches per invocation (default one), response-ID/labels/range validation, cached immutable CSV by (newsId,model), append-only and lock/fsync, fails closed on API error/429; no retries or fallback to paid models, no key logging or GitHub secrets. Each `scoredUtc` MUST be used in any future point-in-time replay: classification unavailable before actual API scoring, even when a headline was published earlier. Gemini output is only BTC relevance, sentiment, qualitative impact, short explanation; TITLE-ONLY and can be wrong, not trade signals, no production provider changes. Added 5 Python regression tests for capture timestamps, immutable archive, Gemini JSON validation, dry-run opt-in, and cache skip. New expected Python total **52 tests** (prior 47 + 5), not yet actually run on server. Java remains last confirmed 147 tests (unchanged). Added `docs/research/gemini-free-tier-news.md` with full safe usage instructions and explicit project Free tier verification and privacy notice.

**User action needed, no key sharing:** on phive-server:
```bash
cd /home/phive/Documents/CoinMind-AI
git pull --ff-only origin main
python3 -m unittest discover -s scripts/offline -p 'test_*.py'
python3 scripts/offline/gemini_news_research.py capture --feed-url 'https://www.coindesk.com/arc/outboundfeeds/rss?outputType=xml' --source CoinDesk --archive /home/phive/coinmind-news-headlines-v1.csv
python3 scripts/offline/gemini_news_research.py capture --feed-url 'https://www.federalreserve.gov/feeds/press_monetary.xml' --source 'Federal Reserve' --archive /home/phive/coinmind-news-headlines-v1.csv
python3 scripts/offline/gemini_news_research.py score --archive /home/phive/coinmind-news-headlines-v1.csv --output /home/phive/coinmind-gemini-news-scores-v1.csv
```
Only after confirming active key is Free-tier in Google AI Studio (being issued a "free-tier key" does NOT certify unbilled project):
```bash
read -rsp 'Gemini API key: ' GEMINI_API_KEY; echo
export GEMINI_API_KEY
python3 scripts/offline/gemini_news_research.py score --archive /home/phive/coinmind-news-headlines-v1.csv --output /home/phive/coinmind-gemini-news-scores-v1.csv --max-batches 1 --execute
unset GEMINI_API_KEY
```
No production deployment, service restart, real orders, auto trading, or new prediction-market provider. Futures live BLOCKED. Gemini is research annotation only. Longer-term integrate with backend AI provider via explicit opt-in only after tests and privacy review.


## 2026-11-05 — Gemini News Intelligence Phase 1 (uncommitted; not deployed)

Inspected local/GitHub main (`9bf10ce`, matched), all existing providers/contracts, AI properties/services/status/filter/policy, news ingestion/tests, configuration and locally modified offline Gemini prototype before changes. Preserved that preexisting script edit and unrelated untracked files. Added explicit Gemini provider (`AI_PROVIDER=gemini`) with separate `GEMINI_NEWS_ENABLED` opt-in, `AI_ENABLED` master switch, secured `GEMINI_API_KEY` mapping, configurable `GEMINI_MODEL=gemini-3.5-flash-lite`. Existing application defaults remain OpenAI/disabled; NoOp now conditional so selected/absent/unknown configurations resolve exactly one provider. Gemini status uses its own credential/model and reports configuration only.

Phase 1 sends only up to five public titles/sources/IDs/publication timestamps as untrusted data to Google's generateContent endpoint with header authentication, JSON MIME, strict local identity/duplicate/type/label/range/completion validation. Invalid/failure results are explicitly UNAVAILABLE with zero confidence/importance and no article impacts; no error bodies/exceptions/credentials logged or returned. Conservative in-process attempt caps (1/minute, <=10/rolling day), 15-minute exact-content cache/in-flight sharing, current-input freshness checks, 64 KiB response cap, bounded connect/total timeout, redirects and Netty automatic connection retry disabled. No retry/model switch/paid fallback. Qualitative impact maps to importance 75/50/25/0; relevance validated but never mapped to confidence; confidence zero and horizon NONE. Completion-time analyzedAt, no invented firstSeen provenance, no historical/replay integration. Gemini results intentionally excluded from the deterministic news modifier; trading analysis/review and legacy prediction methods safely return UNAVAILABLE without HTTP. Trading/risk engine/offline CSV schemas unchanged.

Audit: runtime prediction service remains Manifold-only. Legacy Polymarket-named interfaces/models/routes/AI config/frontend and old Gamma-specific modifier checks remain for a separate migration; no new prediction provider or Polymarket behavior added. Full mapping, limits, provenance caveats and exact isolated manual smoke-test variables/commands: `docs/development/gemini-news-phase1.md`.

Validation: `python3 -m unittest discover -s scripts/offline -p 'test_*.py'` **52 passed**. `mvn -f backend/pom.xml clean verify` **BUILD SUCCESS; 164 tests, zero failures/errors/skips**, including 17 new tests covering success, invalid JSON/labels/ranges/identities, missing key/disabled switches, HTTP 429/401/403/5xx, timeout, cache invalidation/expiry/local limit, concurrent deduplication, oversized response, provider selection, status and trading isolation. Initial sandbox verification hit Mockito JVM self-attachment restrictions; approved rerun outside sandbox succeeded. No live Gemini API request during tests or this implementation. No commit, push, branch, deploy, production restart or historical backfill. Futures production remains BLOCKED.


## 2026-11-05 — Fix replay repository CGLIB startup failure (uncommitted; not deployed)

Inspected existing Gemini changes, main at `9bf10ce`, repository annotations/proxy configuration, replay workflow/job/persistence/history and unit/PostgreSQL IT tests. Remote main check failed on local SSH config ownership/permissions. Boot `PersistenceExceptionTranslationAutoConfiguration` registers the repository advisor and defaults `spring.aop.proxy-target-class=true`; unconditional `@Repository` archive beans were final and had no interfaces. Exception translation, not transaction advice, triggered the failing subclass. Found the same latent issue in `FuturesPaperEquityArchive`. Removed `final` from both and documented why. Other eight repository classes are non-final; no other production transactional/async/cache/retry/aspect annotation or custom proxy configuration found. Explicit reactive transaction boundaries, PostgreSQL SQL/idempotency, validation and replay consistency unchanged.

Added two `FuturesReplayContextTest` tests using real Boot auto-configuration with the replay dependency graph, archive opt-in both false and true, asserting both exception-translation CGLIB proxies and reactive transaction manager presence. Confirmed exact original final-class failure before fix, both pass afterward. Python offline 52 passed. Plain Maven clean verify attempted: sandbox Mockito self-attachment blocked (166 run, 2 failures/6 errors). Supplying the cached Mockito 5.23.0 jar at JVM startup via `-DargLine=-javaagent:/home/phive/.m2/repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar` allowed full clean verify: BUILD SUCCESS, 166 passed, zero failures/errors/skips. Default Surefire does not include opt-in PostgreSQL *IT classes; those were inspected but not executed without a disposable test DB.

Packaged-jar isolated smoke attempted on 127.0.0.1:18080 with persistence/master AI/automatic AI/Gemini news/archive/job/news/push/billing off, credentials cleared, DB and market endpoints loopback-only, SQL init/R2DBC health off. Passed replay bean creation, then failed creating Netty listener because sandbox socket creation is forbidden (`newSocketStream(..): Operation not permitted`). No HTTP health result can be claimed. JVM exited/stopped. Unsandboxed execution rejected by permission policy. Remaining blocker: run the isolated listener/health check where sockets are allowed. Exact harness `/tmp/coinmind-replay-smoke.py`; logs `/tmp/coinmind-replay-smoke.log`, `/tmp/coinmind-replay-agent-verify.log`, `/tmp/coinmind-replay-python.log`. No deployed-service changes, commit, push, branch change, deployment, paid external API call, data alteration or Gemini implementation changes. Manifold-only policy and deterministic trading rules preserved.


## 2026-11-05 — Gemini UNAVAILABLE investigation, safe diagnostics (local main only)

Inspected Phase 1 provider request serialization, strict parser/validation, config, service selection, cache/limits, available /tmp logs and latest 1000 backend journal lines. Confirmed generic catch-all destroyed failure distinctions; no evidence identifies the reported live failure. Disabled/key/model/articles/local-limit paths had separate reasons; completed HTTP errors normally retained status. Fixed a reproduced edge case where discarding an HTTP error body failed and erased the known status. No confirmed request-schema/parser compatibility bug; strict validation preserved. Blank response now explicitly EMPTY_RESPONSE.

Added category/status-only unavailable reasons and warning metadata limited to category, HTTP status, validated model, article count and locally generated correlation UUID. No exception/message/stack trace, body, raw headline, credential, headers or URL logged. Mocked tests cover realistic five-article envelope, actual serialized request, HTTP 400/401/403/404/429/500/503, transport/wrapped connect timeout/operation timeout, malformed JSON, strict validation, empty/oversized responses, error-body drain failure, redaction, cache and caps. All Gemini HTTP seams remain mocked.

Validation: requested plain `mvn -f backend/pom.xml clean verify` ran, failed on existing sandbox Mockito self-attachment restrictions and a new empty-response category assertion; fixed the latter. Full rerun with `-DargLine=-javaagent:/home/phive/.m2/repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar`: BUILD SUCCESS, 170 tests passed (16 Gemini provider), zero failures/errors/skips. Python `python3 -m unittest discover -s scripts/offline -p 'test_*.py'`: 52 passed. Default Maven suite excludes opt-in PostgreSQL ITs. Logs `/tmp/coinmind-gemini-diagnosis-verify.log`, `/tmp/coinmind-gemini-diagnosis-agent-verify.log`, `/tmp/coinmind-gemini-diagnosis-python.log`.

Original cause remains unconfirmed: transport/timeout, malformed/truncated/blocked response or strict contract/identity validation are hypotheses. Controlled one-POST follow-up documented in gemini-news-phase1.md, deferred until existing rolling-day budget available. No live Gemini API request, branch, commit, push, deployment or production restart. Prior working-tree changes preserved; RSS, deterministic decisions, safety caps and Manifold-only prediction policy unchanged.

## 2026-11-06 — Phase 1.5 automatic Gemini recovery and Indonesian status (local main only)

Inspected existing uncommitted Phase 1.5 work, main history, strict Gemini HTTP/parser/cache/admission, status API, news service, deterministic trade strategy and Angular polling/header before extending. Preserved preexisting unrelated Futures/research/deploy files. No branch switch, live Gemini call, commit, push, production restart or deployment.

Added durable local admission/circuit ledger and lifetime exclusive file lock; one enabled instance per Google project/key is an explicit deployment constraint, not a distributed quota guarantee. Attempts are recorded before HTTP; cooldown, rolling timestamps, backoff and validated success/failure survive restart. HALF_OPEN crash defers one hour. Initial five-minute cooldown, capped exponential jitter, one concurrent request, Retry-After seconds/date, credential latch and strict validation before recovery. Scheduled recovery runs one bounded asynchronous pass through existing BTC/ETH/SOL fresh news analysis; no heartbeat or paid fallback. Storage failure blocks requests. Initial migration requires a rolling-day quiet period because old in-memory attempts cannot be reconstructed.

Existing status endpoint now returns safe recovery metadata; Angular reuses existing 30-second polling. Sticky responsive header uses Indonesian labels with accessible native disclosure, timestamps/freshness and deterministic/WAIT explanation. Status-fetch failure clears active status. Optional strategy discloses deterministic-only recommendations; required-for-entry mode WAITs with no entry/targets when fresh AI is missing. Gemini remains excluded from trading modifiers, so mandatory mode stays WAIT even with recovered headline classification.

Validation: full offline Maven clean verify with existing Mockito javaagent passed 180 tests; repeat verify after final eligibility presentation adjustments also passed 180 tests, zero failures/errors/skips (`/tmp/coinmind-recovery-verified.log`). Fake clocks/mocked HTTP cover 503 cooldown, validation failures, recovery, admission concurrency/HALF_OPEN, jitter cap, rolling quota, Retry-After formats, persistent restart/exclusive lock, coordinator and strategy WAIT/disclosure/status. Python offline unittest suite: 52 passed (`/tmp/coinmind-recovery-python.log`). Angular production build passed (`/tmp/coinmind-recovery-final-build.log`), with existing template diagnostics. Removed build-time Google Fonts fetching (font inlining disabled) after sandbox DNS caused initial build failure; runtime CSS import/fallback retained. `npm run test:ai-status`: nine actual helper mapping cases passed. Existing `npm test -- --watch=false` cannot run: no Angular test target is configured (unknown watch argument). No browser visual acceptance or opt-in PostgreSQL integration tests performed. Initial Maven run exposed a status/provider DI cycle; fixed with lazy ObjectProvider and provider-selection tests now pass.

Operational limitations and exact UI/API mappings documented in gemini-news-phase1.md Phase 1.5 and current-state.md. Keep ledger on persistent local storage; do not run replicas/independent ledger directories or other Gemini clients against the same budget. Auth errors require operator action; restart does not clear them. No inference of free-tier billing guarantees.


## 2026-11-06 — Phase 1.5 recovery review and restart regression

Reviewed existing Phase 1.5 implementation on local main `9bf10ce`; extended it without duplicating provider/coordinator/status/UI capabilities. Remote main verification failed because `/etc/ssh/ssh_config.d/20-systemd-ssh-proxy.conf` has invalid ownership/permissions. Fixed orphaned persisted HALF_OPEN preventing scheduled recovery forever: restore OPEN while preserving its conservative pre-request one-hour deadline and all attempts. Status snapshots now prune expired rolling-day attempts and provider status reads hold the admission lock throughout, avoiding inconsistent eligibility metadata. Header disclosure accessible name includes the current Indonesian status.

Added fake-clock regressions for crashed HALF_OPEN admission/quota preservation, expired usage snapshots and actual scheduled recovery with mocked HTTP: cooldown blocks RSS access, empty RSS consumes no attempt, fresh news plus validated response closes the circuit. Existing strict parser, bounded backoff, one concurrent request, Retry-After/auth gates, strategy WAIT/disclosure and no paid fallback preserved.

Final checks: `mvn -o -f backend/pom.xml clean verify -DargLine=-javaagent:/home/phive/.m2/repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar` **BUILD SUCCESS, 182 tests, zero failures/errors/skips** (`/tmp/coinmind-recovery-review-final-maven.log`). Python offline unittest **52 passed** (`/tmp/coinmind-recovery-review-python.log`). Angular production build **passed**, existing template warnings (`/tmp/coinmind-recovery-review-final-build.log`); status helper **9 cases passed**. General `npm test -- --watch=false` fails because no Angular test target is configured (unknown watch argument). `git diff --check` passed. Browser visual acceptance and opt-in PostgreSQL IT remain unverified. Single Gemini-enabled instance per project/key and persistent ledger remain mandatory; migration requires waiting 24h after old-process usage. No live Gemini requests, commit, push, branch change, production restart or deployment. Existing unrelated uncommitted files preserved.


## 2026-11-09 — Phase 1.5 recovery restart audit (local main only)

Inspected existing uncommitted Phase 1.5 architecture, provider, scheduled coordinator, strict validation, durable admission, safe status API, strategy WAIT policy and responsive Angular header; reused those capabilities. Fixed quota eligibility after lowering the configured daily cap: enough persisted attempts must expire before the next admission. Ledger loading now rejects missing accounting fields, unknown circuits/invalid reason codes, negative failure counts, excessive or unordered attempts, preserving fail-closed startup. Added fake-clock restart regressions for reduced caps and corrupt/incomplete ledger plus lock release; added legacy DEGRADED label regression.

Final offline Maven verify with cached Mockito javaagent: BUILD SUCCESS, 184 tests, zero failures/errors/skips (/tmp/coinmind-restart-review-complete-maven.log). Python offline suite: 52 passed (/tmp/coinmind-restart-review-python.log). Angular production build passed with existing template warnings (/tmp/coinmind-restart-review-build.log); status helper 10 passed. General npm test -- --watch=false remains unavailable because no Angular test target is configured. An initial Maven run overlapped final source edits and failed compilation; subsequent clean verify passed, followed by full verify after the final incomplete-ledger guard. git diff --check passed. Browser visual acceptance and opt-in PostgreSQL IT remain unverified.

Exactly one Gemini-enabled instance per project/key and a persistent shared local ledger path remain mandatory; no distributed coordination claim. Initial migration waits 24h after old-process usage; authentication errors require operator intervention. Required-AI entries WAIT, including after Gemini news-only recovery; optional entries disclose deterministic analysis. Remote main verification blocked by SSH configuration ownership/permissions; local main retained. No live Gemini request, commit, push, branch switch, production restart or deployment. Existing unrelated edits preserved.

## 2026-11-09 — Phase 1.5 follow-up audit and scheduler isolation

Inspected tracked/untracked working tree on main `9bf10ce`, including earlier Futures proxy fix, research model override, deployment script, lockfile, backup and bytecode artifacts; retained them. Remote HEAD comparison failed on existing SSH config ownership/permissions; did not alter SSH or switch branches. Reused provider/circuit/ledger/status/UI implementation. Found scheduled recovery subscription still executed initial synchronized news reads and ledger I/O on Spring's shared scheduler. Added boundedElastic subscription and blocked-news-read/repeated-tick regression; adapted existing coordinator integration test for asynchronous completion. A first new regression incorrectly verified a synchronized mock while its monitor was blocked; moved that verification after release and reran full verification.

Final exact `mvn -f backend/pom.xml clean verify` outside sandbox: BUILD SUCCESS, **185 tests**, zero failures/errors/skips (`/tmp/coinmind-phase15-audit-final-maven.log`). Initial sandbox run failed Mockito JVM attachment. Python offline **52 passed** (`/tmp/coinmind-phase15-audit-python.log`); Angular production build **passed**, existing template warnings (`/tmp/coinmind-phase15-audit-build.log`); actual status helper **10 cases passed**. `npm test` confirms no configured target; `npm test -- --watch=false` rejects watch. `git diff --check` and deployment-script syntax check passed. No live Gemini API request; all provider HTTP tests use mocked exchanges.

Documented complete changed/new file inventory and actual semantics in `gemini-news-phase1.md`: 503 -> 300s OPEN, jittered exponential cooldown capped 3600s, one HALF_OPEN attempt, strict validated 200 closes circuit; quota/Retry-After/auth and restart accounting retained. Fresh outstanding failed news may be reused; no heartbeat and no durable per-article exactly-once guarantee. Pattern scan found no Gemini/OpenAI/private-key patterns in changed/untracked text (not exhaustive security/dependency audit). No added provider/fallback; prediction runtime Manifold-only. Single enabled instance/project with persistent ledger remains required. Browser visual/responsive acceptance, process-level mocked E2E and disposable PostgreSQL IT remain outstanding. Updated handoff; no commit, push, deploy, branch switch, production restart or unrelated deletion.

## 2026-11-09 — Phase 1.5 isolated HTTP/process/browser/PostgreSQL E2E

Retained dirty main `9bf10ce` and earlier changes. Socket creation fails in sandbox; approved outside-sandbox loopback execution succeeded. Added test-classpath-only full Boot child-process fixture with loopback transport/dummy key, fake provider clock, isolated ledger and synthetic news/market controls; added opt-in Python HTTP/WebSocket/process and disposable PostgreSQL harnesses plus production Angular Playwright/axe test. Production backend/trading rules unchanged. Provider-selection test now uses temporary ledger instead of its default working directory.

Real HTTP/process checks passed: 503 -> 300s OPEN, no early calls, actual 30s scheduler -> HALF_OPEN (28.164s observed), 8s delayed valid 200 -> READY, concurrent calls deduplicated, continuing mock WS -> production SSE and deterministic advisor operations (paired ticker/advisor 44–275ms), repeated 503 -> 660s jittered cooldown, separate JVM OPEN/quota/auth persistence, killed HALF_OPEN -> OPEN preserving one-hour deadline, exclusive same-ledger lock, 429 Retry-After 7200, 401/403 automatic stop and restart latch. Separate independent ledger admits another same-key mock attempt: documented operational single-instance constraint, not distributed coordination.

Browser first encountered axe's explicit-context harness requirement after label/polling checks; corrected harness. Initial screenshot review then found mobile brand/status overlap and +60px horizontal overflow from legacy width:100%. Added scoped width:auto to existing mobile reservation rule and stronger overlap/document-width assertions; rebuilt and reran browser verification. Final **18 browser checks passed**, widths 320/360/390/768/1024/1440, actual HTTP response snapshot mapping, polling/fetch failure, keyboard/aria, zero scoped WCAG axe violations; final screenshots reviewed. No uncaught JS errors. External browser font request blocked; browser SSE proxy disabled (backend SSE tested separately).

Final exact Maven clean verify outside sandbox **185 passed**, zero failures/errors/skips (`/tmp/coinmind-phase15-e2e-verify.log`); JAR contains no E2E fixture/control classes. Python offline **52 passed**; Angular production build passed with existing warnings; label helper **10 passed**, general Angular test target absent. Fresh PostgreSQL17 cluster `/tmp/coinmind-phase15-postgres-auttw65n`, loopback59227, dedicated coinmind_replay_test: **5 IT passed**, including atomic rollback; cluster stopped. HTTP/browser report `/tmp/coinmind-phase15-e2e-s6o2rgel/report.json` PASS; all child JVMs stopped. Original HTTP log and final browser retry log under `/tmp/coinmind-phase15-e2e-*.log`. Pattern scan of modified/untracked text found no real-key/private-key patterns; no exhaustive dependency scan/OS egress capture. Detailed evidence/reproduction/deployment proposal in `phase15-e2e-verification.md`.

Remote main comparison remains blocked by SSH config permissions. No live Gemini/paid provider request, production database/service use, order execution, branch, commit, push, deployment, production restart or unrelated deletion. Existing Futures/research/deploy/backup/bytecode artifacts retained. Deployment remains unperformed; one enabled instance/project/key, persistent ledger, quota-preserving rollout/migration, credential/free-tier verification and separately authorized live-news acceptance remain operator responsibilities.

## 2026-11-09 — Safe production release mechanism prepared (no deployment)

Read canonical handoffs, inspected local main 9bf10ce and preserved all prior tracked/untracked work. Remote main inspection failed on existing SSH config ownership/permissions. Audited original untracked deploy.sh, active unit file and Nginx coinmind/phivegarage sites read-only; systemd bus inaccessible in sandbox. Replaced deploy.sh with preparation-only wrapper. Added scripts/release/prepare.py, activate.py, test_release.py and templates/coinmind-release.conf, nginx-root.conf, operator-attestation.json. Added docs/development/production-release.md and updated current-state deployment guidance. No application/market/advisor/provider behavior changed.

Isolated source staging avoids overwriting the live target JAR. Candidate has versioned backend/frontend bundle, SHA256 manifest/source inventory; default dry-run never activates. Separate explicit root activation requires compatible baseline, immutable root-owned artifacts, operator singleton/ledger/config/policy attestations, absolute persistent ledger and reviewed unit paths. It stops before starting, switches one shared pointer, verifies backend/Nginx index and restores previous pair on failure without rolling ledger back. Runbook covers legacy in-memory 24h quiet-period migration, conservative baseline, interrupted transactions and manual rollback. Config templates are not installed.

Validation: isolated offline Maven verify with cached Mockito startup javaagent **185 passed**, zero failures/errors/skips; Angular production build passed with existing NG8102 warnings; status helper **10 passed**. Stage `/tmp/coinmind-release-phase15-candidate`; log `/tmp/coinmind-release-stage.log`. Offline Python **52 passed**, log `/tmp/coinmind-release-python.log`. New release unittest **5 passed**, including fault-injected health rollback and byte-preserved ledger; dry-run artifact verification, bash syntax, Python compilation and git diff --check passed. ShellCheck unavailable; PostgreSQL/browser E2E not repeated (prior evidence retained). Stage snapshots predate final release-tool documentation edits and are validation artifacts, not operator-approved release provenance.

No git pull/commit/push, production deploy/service restart/reload, active systemd/Nginx config mutation, .env read/write, live Gemini/paid call, web-root overwrite, ledger reset or unrelated deletion. Temporary build outputs were isolated and only newly generated release-test bytecode cleaned. Operator blockers: remote main verification, effective runtime inspection, single project/key ownership, ledger migration and compatibility proof, root-owned import/config installation, infrastructure health acceptance and separate explicit activation authorization. Production readiness is not certified.


## 2026-11-09 — Fresh Phase 1.5 release preparation audit (no activation)

Preserved dirty main 9bf10ce and inventoried 19 modified/51 untracked files with hashes under /tmp/coinmind-phase15-audit-20261109-evidence. Read-only remote query reproduced SSH include nobody:nogroup/0777 rejection before authentication; documented administrator repair without changing SSH. Verified deploy.sh preparation-only and activate default early return; no authorization supplied. Explicit Node22.23.2, Java25.0.4.1/Maven3.9.9; lock roots and 762 installed versions match, npm ls offline passed. Staged NEW /tmp/coinmind-phase15-audit-20261109-r1 with prepare.py, cached Mockito startup agent: Maven185/Angular production/status10 passed. Default offline verification and four non-authorized release tests passed. All295 source hashes/7 artifacts passed, index refs complete, no ledger/sensitive paths/E2E fixture or credential-pattern matches. Broad source inventory includes preexisting research backup; deployable bundle does not. No clean npm-ci or browser/PostgreSQL rerun.

Earlier candidate's236 application classes are identical; checked-in main lacks persistent ledger class. Compatible Phase1.5 artifacts are available, but no distinct conservative baseline/runtime migration acceptance against production state is proven: migration BLOCKED. Added precise later operator checklist, SSH repair and provenance limits to production-release.md/current-state. Documentation postdates candidate snapshot. No source behavior change, commit/push/pull/branch, production filesystem mutation, service action, .env read, live Gemini or trading execution. All existing tracked/untracked/recovery state retained.


## 2026-11-09 — Conservative rollback baseline acceptance READY (no activation)

Read canonical handoffs/local main9bf10ce, unit and production-path/earlier staged JARs read-only. Every ZIP entry content matches fresh candidate; production-file ledger code is compatible, but effective running JVM/live ledger uninspected (read-only systemd fields denied by sandbox bus). Production JAR hash retained. Added scripts/release/baseline_acceptance.py: builds a NEW temporary configuration-only baseline from existing verified candidate; only embedded application.yml changes to Gemini-selected/AI/news/automatic-AI disabled/blank keys, frontend and all236 classes unchanged. Manifest records base source and exact config derivation. No candidate rebuild.

Initial sandbox attempt failed before child apps on socket permission, retained /tmp/coinmind-baseline-acceptance-tc_og9h9. Approved loopback retry /tmp/coinmind-baseline-acceptance-zqwyj349: **22 checks passed**, accepted bundle baseline/, report.json and process/HTTP/synthetic-ledger evidence; all17 child JVMs stopped. Actual java-jar baseline->candidate->baseline starts/graceful stops/restarts preserve all ledger files byte-for-byte for OPEN/AUTH_ERROR/two attempts/HALF_OPEN; cap-two probe blocks, crashed-half deadline remains, both lock directions/corrupt storage refuse startup. Candidate synthetic completed recovery persists through real baseline rollback with both attempts retained. Separate packaged-class AUTH probes after48h remain blocked, bytes preserved. No HTTP Gemini request: provider validation covered by regressions/prior E2E; this acceptance uses disabled apps and direct state probes. Both full apps delivered WATCH_BUY from250 synthetic candles; Futures readiness/live orders BLOCKED. Legacy synthetic enable environment variables cannot enable baseline; zero mock POSTs. Original candidate/default offline checks and baseline hashes pass; source/bundle recovery retained.

Offline isolated Maven targeted regressions **42 passed,8 suites,zero failures/errors/skips**, cached Mockito startup agent, source/build /tmp/coinmind-baseline-regression-87adful1; fresh compiled236 application classes match candidate. Log /tmp/coinmind-baseline-regression.log. Script syntax/diff whitespace checks passed. Updated runbook/current-state with exact hashes/evidence and operator gates. **Conservative baseline READY for operator review; production migration still gated/not authorized** pending remote provenance/effective runtime/singleton, actual ledger migration or quiet-period initialization, higher-precedence config exclusion, root-owned infrastructure import/config and real acceptance/attestations. Existing valid ledger is mandatory; disabled app cannot reconstruct deleted state. No production process/service action, filesystem mutation, .env/live-ledger read, SSH edit, git commit/pull/push/branch, live Gemini or orders. No PostgreSQL/browser rerun; frontend unchanged.


## 2026-11-09 — Final READ-ONLY production migration readiness: NO-GO

Revalidated existing candidate and conservative baseline with activate default offline mode; no build or authorization flag. Approved read-only host inspection overcame sandbox bus/proc/ownership mapping: systemd active PID299694,phive,old backend cwd/JAR path,.env path only,no drop-ins; port8080/healthUP and safe AI statusMOCK (no analysis request). PID299694 holds deleted original JAR inode17844963,sha f1d7bc859df3b47f72772c1583d206be2efd519388a78181b522251170850a4e,156 classes/no GeminiRecoveryState. Running binary is incompatible; newer target file is not running binary or accepted rollback bundle. Sole accepted conservative baseline remains the previously tested named bundle. Preserve deleted inode before any later authorized stop.

Legacy recovery directory metadata only: phive0775,zero-byte lock0664 datedNov6,no gemini.properties; no trustworthy accounting. Current mock/local process snapshot cannot establish global project/key history or a quiet-period start. No competing Gemini runtime positively identified among local Java/Node/Python names/cwd/fds; PhiveGarage PID617366 identified, cron/anacron remain, user/root/container/remote scheduler/ownership unproven. No commandlines/process environments/APIkeys/.env read. Require separately authorized containment, trusted UTC T0 after all possible clients/in-flight attempts contained,full rolling24h plus longer known restrictions,auth preservation,and reviewed/authorized initial-state procedure before startup. Lock timestamps are not evidence.

All readable Nginx includes recursively inspected with safe routing output only; separate roots/ports CoinMind80/8080,PhiveGarage8090/8081. Unprivileged nginx test denied; sudo-n nginx-T requires password,so privileged config/loaded acceptance remains BLOCKED. /opt/coinmind,/var/lib/coinmind absent; stages phive-owned/private parent need root-owned import and shared ledger/infrastructure migration. Host SSH symlinkroot0777 ->vendorroot0644,parentsroot0755,ssh-G exit0: earlier nobody observations are sandbox mapping,NOT host defect. Corrected prior chown/chmod advice in runbook; no SSH edits. Remote auth/main comparison still unverified.

Added phase15-migration-readiness.md with VERIFIED/BLOCKED/REQUIRES OPERATOR AUTHORIZATION checklist,explicit NO-GO,and ordered proposed maintenance actions incl forensic archive before stop,never legacy rollback,quiet/init gate,immutable import,scoped config,shared-Nginx PhiveGarage acceptance and separately authorized candidate activation. Updated runbook/current-state. Masked evidence/config hashes /tmp/coinmind-final-readiness-evidence/. No service stop/start/reload/restart,production config/filesystem/ledger mutation,live Gemini,orders,Git pull/push/commit/branch or cleanup. Existing processes,changes and bundles preserved. Documentation-only audit; no tests/build beyond offline verification needed.

## 2026-11-10 — Phase 1.6 explicit ledger bootstrap mechanism only

Added separate bootstrap_gemini_ledger.py with required nonsecret cutoff evidence, explicit UTC cutoff/full elapsed24h/global stopped-client/restriction acknowledgments, private preprovisioned directory, Java-compatible POSIX exclusive lock, exact CLOSED/UNVERIFIED seven-field schema and no invented usage. Exclusive pending file + fsync + atomic non-overwriting hard-link publication + directory fsync; remnants fail closed. Existing/corrupt/partial accounting cannot be reset. activate.py/runtime/bundles unchanged. Runbook covers prerequisites, permissions, interruption ambiguity and mock-only migration investigation: preparation is separable, current activation/baseline is not; no security bypass added.

Relevant new tests and release regressions: 12 passed (7 new/5 existing), including actual Java lock contention/schema loading, incomplete/future quiet periods, active clients/restrictions, corrupt/existing accounting, interruption, racing destination and fresh initialization. Temporary synthetic evidence/ledgers only; no real bootstrap or true operator attestation, production ledger/config/service change, live Gemini/order, bundle rebuild, git pull/push/commit/branch. Dirty local main9bf10ce and unrelated work preserved; inspected local main, no remote synchronization requested/performed. Operator global containment/evidence/quiet period, provisioning and separately authorized activation remain outstanding.


## 2026-11-12 — Complete operational Polymarket removal (local main, no deployment)

Inspected local/remote main at 9bf10ce; preserved all existing dirty work. Migrated existing Manifold-only parser/models/controller/scheduler to com.coinmind.manifold and /api/v1/manifold; removed legacy prediction AI services/models/controller/provider methods, forecast benchmark execution, properties/YAML/example env and Angular AI controls/client/types/styles. Kept unrelated benchmark config endpoint. Removed unreachable Gamma confidence/opposition logic without redistributing technical/news weights; Manifold stays neutral context. UI/reasons explicitly mark unavailable 24h probability change. Gemini recovery/ledger/quota/circuit/keys unchanged; only unsupported prediction interface stub and obsolete test/config references removed. Advisor-only/Futures BLOCKED retained.

Final clean offline Maven verify with cached Mockito startup agent: **189 passed**, zero failures/errors/skips (/tmp/coinmind-manifold-verify-complete.log). Angular production build Node22 passed with existing NG8102 warnings; status10/release-bootstrap12/offline-research52 passed. New tests check Manifold public-search contract/cache/failure/cooldown/neutral provenance, retired routes404, source prohibition and confidence/action/entry/target/risk/warnings invariance. JAR/Angular asset scans contain no Polymarket; diff whitespace passed. Browser and opt-in PostgreSQL process tests not rerun; real upstream availability not claimed. Earlier intermediate compile/fixture errors corrected before final clean verify.

Historical configuration/routes moved to explicitly non-operational prediction-market-legacy-archive.md; dated evidence preserved with superseding notices. Full task file inventory, remaining-reference rationale, commands/logs and API compatibility risk: manifold-only-migration.md. Main/HEAD unchanged. No .env inspection/change, real Gemini request, attestation true, recovery reset, release bundle preparation/activation, commit/push/reset/clean/branch, production service/config/filesystem or PhiveGarage mutation.


## 2026-11-12 — Manifold-only conservative rollback baseline preparation (NO deployment)

Verified imported candidate /opt/coinmind/releases/candidate-manifold-only using unchanged activate.py offline mode. Current pointer observed releases/baseline-phase15. Adjusted baseline_acceptance.py to accept read-only imported releases, remove obsolete historical-JAR comparisons, verify forbidden providers/routes absent and seal its newly generated staging bundle. Retained all230 candidate application classes/dependencies/Manifold/GeminiRecoveryState/frontend; only embedded application.yml changes to AI/news/automaticfalse, Geminiprovider for locking, blank key slots. No real credentials changed.

New /tmp/coinmind-baseline-acceptance-wup4qyla/baseline: **24 process/persistence checks passed**, **all17 JVMs stopped**, zero mockPOSTs. Actual baseline→candidate→baseline startup/health/graceful-shutdown cycles preserve synthetic OPEN/AUTH_ERROR/quota/HALF_OPEN ledger/evidence byte-for-byte; both-direction process lock refusal, candidate direct-state completed recovery retention and quota2 blocking, corrupt accounting unchanged/refused. Market/advisor mock data functional, Futures/live orders BLOCKED, retired routes404, Manifold source-health only. First sandbox attempt failed socket creation (blocked evidence retained); approved host-loopback retry succeeded.

Manifest schema1/phase15-v1/all8 artifacts verified after file0444/dir0555 seal. Manifest SHA78f86c861d93351d557d84c9dbf72c9c334b3d47ab3136879348ef33f461448a; backend SHAc8b496028b11b098f9f74b8b0920d8eea2c16977337a575dba75c3d9327e4b70. Candidate unchanged by harness, old baseline9-file hashes unchanged, current pointer unchanged. Release/bootstrap12 passed; full isolated Maven189 passed, all230 compiled classes byte-identical to candidate, all198 inventoried backend source/POM match. Regression /tmp/coinmind-manifold-baseline-regression-z8lixz7o; logs /tmp/coinmind-manifold-baseline-{acceptance-host,regression,release-tests}.log. Detailed provenance/config/risks: manifold-baseline-acceptance.md.

READY FOR OPERATOR REVIEW only. Root-owned protected import, no enabling higher-precedence config, existing real-ledger integrity/singleton and actual infrastructure acceptance remain operator gates; read-only /tmp mode is not filesystem immutable-bit protection. No production ledger read/write, .env/key/systemd/Nginx/attestation/PhiveGarage/service mutation, live Gemini, activation/import/commit/push/reset/branch. Local main9bf10ce; remote query failed existing SSH include permissions without repair. Canonical handoffs updated; prior dirty work retained.


## 2026-11-12 — Gemini fresh headline selection regression fix (NO deployment)

Preserved existing dirty work and inspected local main9bf10ce; remote main read failed on existing SSH config permissions. Added NewsService eligibility overload: filter complete symbol candidate set before relevance-desc/publication-desc sorting, deduplicate IDs keeping highest rank, then limit five. GeminiAiProvider exposes a predicate capturing its clock once and shares per-article eligibility with retained validArticles defensive batch validation. Unchanged id/title/source constraints and inclusive24h cutoff, no future timestamps. AiNewsIntelligenceService returns UNAVAILABLE without invoking provider for empty eligible selection; non-Gemini behavior preserved.

Added GeminiHeadlineSelectionTest (5 cases covering stale top5 hiding fresh work, mixed invalid/null/overlong fields and nanosecond time boundaries, duplicate ID/rank/limit5, all expired, empty input/no AI call). Real provider success tested using fixed clock plus mock WebClient exchange, no network. Updated GeminiAiProviderTest, GeminiNewsIsolationTest and GeminiRecoveryCoordinatorTest to new selection seam; retained trading isolation and scheduler checks. Intermediate JSON mock fixture corrected; flaky2s async waits increased in coordinator tests only. No production recovery logic changes.

Maven offline clean verify194 passed; final offline verify194 passed, zero failures/errors/skips, Mockito cached startup javaagent5.23.0. Final command: mvn -o -f backend/pom.xml -DargLine=-javaagent:/home/phive/.m2/repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar verify. Logs /tmp/coinmind-headline-verify.log and /tmp/coinmind-headline-verify-final2.log. git diff --check passed. No real Gemini POST, fake production news, threshold/freshness changes, automatic AI/trading activation, recovery/ledger/quota/circuit changes, prediction-provider change, .env/systemd/Nginx/release/deployment mutation, commit/push/reset. Immutable release preparation remains a separate task after review.


## 2026-11-12 — Headline selection follow-up review and verification

Reused the existing requested selection implementation and preserved unrelated dirty work. Added two regressions to GeminiHeadlineSelectionTest: defensive provider rejection of empty/mixed-expired/duplicate/oversized batches without HTTP, and exact24h service-to-provider success with fixed clock/mock exchange. Seven headline selection tests now cover all requested cases. No production implementation or recovery changes in this follow-up.

Initial targeted run exposed flaky Mockito timeout verification on synchronized NewsService (worker monitor contention); replaced the two coordinator timeout verifications with Awaitility polling, preserving assertions. Final targeted32 passed and Maven offline verify196 passed, zero failures/errors/skips. Commands used cached Mockito5.23.0 startup agent; logs /tmp/coinmind-headline-review-targeted-final.log and /tmp/coinmind-headline-review-verify-final.log. git diff --check passed. Local main9bf10ce inspected; remote comparison remains blocked by existing SSH include permissions. No live Gemini POST, production fake news, threshold/freshness change, automatic AI/trading activation, Manifold policy change, recovery/ledger/quota/circuit mutation, .env/systemd/Nginx/release/deployment change, commit/push/reset. Immutable release remains a separate subsequent task.


## 2026-11-12 — Explicit publisher metadata on Angular News cards

Added frontend/src/app/core/news-presentation.ts: explicit coindesk.com/www and cointelegraph.com/www mappings; other source labels use trimmed plain text with control/bidi removal, bounded length, unavailable fallback for empty/invalid metadata. URL helper accepts only absolute HTTP(S), rejects credential-bearing/malformed/control-character/backslash URLs. No publisher inference from title, article URL or Gemini. App template renders metadata on every existing card, date/time, title, optional clamped summary and original link with noopener noreferrer; invalid URL gets noninteractive fallback. Scoped responsive CSS preserves scores and supports narrow screens. No external favicon calls or raw HTML rendering. Existing API requests/filtering/loading/empty states and manual AI workflow unchanged.

Changed app.component.ts/html/css, new helper/test-news.mjs and package.json test:news script, plus canonical handoffs.41 source/link unit cases passed;10 existing AI status cases passed; Angular production build passed with existing NG8102/NG8107 template warnings. Used Node22.23.2. Build log /tmp/coinmind-news-ui-build.log. Optional mocked browser check could not launch Chromium due sandbox EPERM (loopback listener also prohibited); no visual pass claimed. Harness moved to /tmp/coinmind-news-ui-browser.mjs, diagnostic /tmp/coinmind-news-ui-browser.log. git diff --check passed. Local main9bf10ce inspected; remote read still blocked by existing SSH include permissions. Initial test-file command used the wrong cwd, corrected before final unit run. No backend/Gemini/recovery/ledger/quota/collector/database/Manifold changes, fake production news, automatic AI/trading activation, production config mutation, deployment, commit, push or reset.


## 2026-11-13 — CM-106 Dashboard interactive tutorial

Added Tutorial sidebar group, Tutorial Center and one functional ten-step Indonesian Dashboard walkthrough on native Angular elements; upcoming modules disabled as informational labels. Stable data-tour-id anchors, automatic Dashboard/News navigation, mobile menu reveal, instant target scroll, spotlight/popover, previous/next/skip/exit/restart/finish, keyboard/focus handling, resize observer and cleanup. Inert app shell blocks highlighted actions including manual AI; tour code has no API/trading/configuration side effects. Existing News UI, market feed and Gemini implementation preserved. Completion status is in-session only.

Driver.js registry install failed EAI_AGAIN and offline attempt ENOTCACHED. Implemented reusable local Angular tour engine; external library requirement remains unfulfilled pending package availability. Changed app.component.ts/html/css, package.json (test:tutorial only), added features/tutorial/dashboard-tour.ts, guided-tour.component.ts, test-tutorial.mjs and canonical handoffs. Tutorial27 isolated unit cases passed; existing News41 and AI-label10 passed. Initial build caught ElementRef generic error, corrected; final Angular production build passed with existing template warnings. Evidence /tmp/coinmind-cm106-{build,tests}.log. git diff --check passed. Browser visual validation not performed; desktop/mobile spotlight positioning, sidebar/scroll, touch/keyboard/focus return, empty/loaded news and underlying action blocking remain manual acceptance tasks. Local main9bf10ce inspected. No backend, production config, deployment, commit, push or reset; pre-existing edits retained.


## 2026-11-14 — CM-106 desktop visual UAT fixes (NOT deployed)

Changed frontend app.component.ts/html, tutorial/dashboard-tour.ts, tutorial/guided-tour.component.ts and test-tutorial.mjs; updated canonical handoffs. Added independent News/sentiment loading and request-generation guards, explicit empty/error/data rendering; actual article spotlight with bounded5s wait and list fallback. Adaptive adjacent popover placement/collision clamping, directional indicators, continuous target/layout tracking and nested-scroll cleanup. Sidebar reduced to one Tutorial entry with modules in Center. Inert shell/blocker continue preventing underlying actions; preserved controls and corrected initial reverse focus traversal.

Angular production build Node22 passed with existing template warnings (/tmp/coinmind-cm106-uat-build.log). Tutorial41 cases plus News request-handler loading/empty/available/error/stale regressions passed; News41 and AI-label10 passed. No general Angular test target exists. Chromium launch failed sandbox EPERM (/tmp/coinmind-cm106-uat-browser.log), so no visual UAT pass claimed; desktop/mobile placement, sticky/scroll alignment, real pointer blocking and assistive technology still require browser acceptance. git diff --check passed. Local main9bf10ce inspected, remote read failed existing SSH permissions. No backend/Gemini/ledger/config/production changes, commit/push/reset/deploy; unrelated dirty work retained.


## 2026-11-14 — CM-101 market-data reliability only (NOT deployed)

Audited Binance Spot WS/REST, BTC/ETH/SOL and1m/5m/15m/1h/4h/1d, timestamp/receipt health, indicator-to-advisor and Angular status paths. Fixed graceful-close reconnect, missing numeric defaults, stale/future events, candle regression/reopening, unbounded/invalid REST bootstrap and post-disconnect history gaps; added10s REST deadline/60s stale-history repair, per-series exchange health/UNAVAILABLE and selected-symbol freshness gates. ContextBuilder fails closed before signal scoring on invalid inputs; strategies/weights/order behavior unchanged. Source inventory and residual risks in cm101-market-data-reliability.md.

Final command mvn -o -f backend/pom.xml -DargLine=-javaagent:/home/phive/.m2/repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar clean verify:205 passed,0 failures/errors/skips, including9 BinanceReliabilityTest methods. Final Angular build Node22 passed with existing template warnings; frontend freshness8, labels10, News41, Tutorial41 and News request regressions passed. git diff --check passed. Logs /tmp/coinmind-cm101-clean-verify.log and /tmp/coinmind-cm101-angular.log. Intermediate overlapping Maven runs caused missing-class artifacts and were discarded; final clean run had no concurrent Maven process. Initial Angular command used repo root; rerun successfully in frontend. No real Binance/socket/browser UAT; bookTicker exchange event timestamp unavailable by upstream contract, local receive time retained. Source/offline acceptance passed; full end-to-end Sprint DoD not yet claimed. Prior dirty work retained on local main9bf10ce; no commit/push/reset/deploy, production/.env/config/Gemini recovery/ledger/quota/key/Manifold/RSS changes, live orders or CM-102 work.


## 2026-11-16 — CM-102 Provider Health & Recovery (NOT deployed)

Replaced global Manifold health with per-symbol status/timestamp/count; existing endpoint fields retained, additive symbol and symbols lookup. Preserved per-symbol cache/cooldown; successful EMPTY cached; failed refresh retains stored cache but suppresses it from advisor, NONE fallback zero-confidence/no valid reasons. Malformed/non-array JSON unavailable. RSS read-only health snapshot, UNKNOWN/UP/EMPTY/UNAVAILABLE, last success retained across failures, safe errors/counts; 5s connect/10s response+total deadlines. Invalid/missing/future dates skipped/counted; NewsService rejects null/future timestamps before overwriting history/event emission. CoinDesk/Cointelegraph config unchanged. Contract, changed-file inventory and limitations: cm102-provider-health.md.

Final sequential offline Maven clean verify 210 passed,0 failures/errors/skips; Manifold4, RSS3, News3, Trade Setup6, CM-101 Binance9 and Gemini regression suites passed. RSS virtual-time stalled request verifies actual10s operator; health HTTP test asserts zero fetch/NewsService calls. Initial209-test run passed before final additive metadata/virtual-time coverage; final210-test run is acceptance. Log /tmp/coinmind-cm102-final-verify.log. git diff --check passed. Angular unchanged, frontend checks not required. CM-102 ready for RC review; live upstream/network UAT and single-entry Manifold client compatibility remain risks. Local main9bf10ce inspected; prior dirty work retained. No technical scoring, Binance, Gemini recovery/ledger/lock/quota/cooldown/freshness change, live Gemini/order, .env/server config, deploy/restart, commit/push/reset. CM-103 not started.

## 2026-11-18 — PhiveAlpha Phase 1A (lokal)

Menambahkan entry point dan profil build PhiveAlpha, paket IDX `stock`, `marketdata`, `newsintelligence`, `screening`, `risk`, serta kontrak provider API.co.id dengan fixture. Target Angular terpisah memuat kelima halaman lewat lazy routing. Paper Trading menghitung estimasi bruto dari harga fixture. Stack R2DBC/PostgreSQL tersedia sebagai dependensi dan koneksi dinonaktifkan pada runtime fixture.

Maven offline 215 tes lulus, termasuk lima tes konteks WebFlux, perilaku endpoint, dan isolasi CoinMind/R2DBC. Build produksi Angular 22.1.7 lulus untuk kedua target. Target CoinMind masih menghasilkan peringatan template lama. Sandbox menolak pembuatan socket untuk uji browser loopback. Artefak PhiveAlpha dikemas secara lokal. Lihat [runbook lokal, inventaris perubahan dan dependensi penghapusan crypto](phivealpha-phase-1a.md).

Branch `main` dan recovery tag `coinmind-pre-phivealpha-20261118` tetap di commit `10b7a67`. Fase ini berjalan tanpa perubahan production, migration baru, API call eksternal, API key, commit atau push.

## 2026-11-18 — PhiveAlpha Phase 1C (lokal)

Menambahkan schema IDX, repository/service reaktif, kalender sesi BEI, profil opt-in dan runner integration PostgreSQL terisolasi. Fixture tetap menjadi sumber untuk endpoint Phase 1A. Maven 220 tes lulus dengan Mockito Java agent setelah self-attach diblokir sandbox. Kedua paket backend dan build Angular lulus. DDL menghasilkan enam tabel dan rollback menghapus schema di PostgreSQL single-user sementara. Integration R2DBC dan smoke browser berstatus NOT RUN karena sandbox. Tidak ada commit, push, deploy, perubahan production atau panggilan provider live. Recovery tag tetap menunjuk `10b7a67`. [Handoff Phase 1C](phivealpha-phase-1c.md)

## 2026-11-18 — PhiveAlpha Phase 1D (lokal)

Menambahkan adapter HTTP IDX opt-in dengan tujuh mapping dataset, pagination lengkap atau gagal tertutup, kebijakan kuota, cache, retry/circuit breaker dan audit aman. Kalender BEI berversi memvalidasi hari libur dan sesi khusus. Fixture tetap default. Profil HTTP memisahkan hasil unverified dari UI dan screening. Maven offline 238 tes lulus dengan Mockito Java agent, termasuk 18 kasus baru. Ulang tes profil dengan `CI=true` membuktikan penolakan startup profil network. Kedua paket backend dan build Angular lulus. Live canary NOT RUN. Tidak ada commit, push, perubahan production atau panggilan API.co.id. [Handoff Phase 1D](phivealpha-phase-1d.md)

## 2026-11-19 — PhiveAlpha Phase 1D.2 (lokal)

Menambahkan CLI developer quote BBCA di luar konteks Spring, guard CI, flag live dan persetujuan entitlement per endpoint, batas satu admission tanpa retry, serta laporan metadata tanpa payload/credential. Audit universe IHSG, pagination, adjustment OHLCV, riwayat berita dan kuota akun ada di handoff. Maven 250 lulus dan satu uji loopback dilewati karena sandbox; kedua paket backend, unit frontend dan kedua build Angular lulus. Live disengaja belum dijalankan. Fixture, BUY dan produksi tetap mengikuti konfigurasi sebelumnya. Tidak ada commit atau push. [Handoff Phase 1D.2](phivealpha-phase-1d2.md)

## 2026-11-19 — PhiveAlpha Phase 1E (lokal)

Menambahkan engine eligibility saham biasa IDX, referensi universe dan kalender point-in-time, kontrol kualitas EOD dan ranking likuiditas terpisah. Fixture menguji no-look-ahead, median dan aktivitas, kalender termasuk libur/sesi khusus, serta tiga hasil eligible tanpa padding. Maven offline 257 lulus dengan satu skip connector Phase 1D.2 akibat sandbox. Kedua paket backend, unit frontend, dan kedua build Angular lulus. Tidak ada koneksi provider/database, sinyal baru, perubahan produksi, commit, atau push. [Handoff Phase 1E](phivealpha-phase-1e.md)

## 2026-11-19 — PhiveAlpha Phase 1F (lokal)

Menambahkan scoring research-only lima faktor di atas hasil LiquidUniverse, dengan mutu dan provenance per faktor, deduplikasi event kanonik, penolakan input masa depan dan ranking deterministik sampai lima kandidat. Fixture utama memberi dua kandidat. Maven offline 264 lulus dengan satu skip connector Phase 1D.2 akibat sandbox. Kedua paket backend, unit frontend, dan build Angular lulus. Tidak ada perubahan perilaku fixture, provider live, database, produksi, sinyal, commit, atau push. [Handoff Phase 1F](phivealpha-phase-1f.md)

## 2026-11-20 — PhiveAlpha Phase 1G (lokal)

Menambahkan historical replay sintetis dengan vintage point-in-time, guard first-seen, asumsi biaya, benchmark berlabel, dan metrik per partisi/horizon. Embargo dan purge label mencegah exit melampaui batas partisi. Fixture mencakup berita/revisi terlambat, delisting/suspensi, hari libur, lot, corporate action, dan posisi unresolved. Maven offline 271 lulus dengan satu skip connector Phase 1D.2 akibat sandbox. Kedua paket backend, unit frontend, dan build Angular lulus. Endpoint fixture berjalan seperti sebelumnya. Tidak ada provider live, database production, deploy, restart, sinyal, commit, atau push. [Handoff Phase 1G](phivealpha-phase-1g.md)

## 2026-11-20 — PhiveAlpha Phase 1H (lokal)

Baseline `3e96542`. Menambahkan adapter USD/IDR dan kalender nasional, reuse transport IDX dengan manifest akses Free dan jurnal durable, status developer privat, mirror metadata opsional Phase 1C, serta canary Daily BBCA. Entitlement akun vendor tetap unverified. Maven offline 287 lulus dan satu uji socket lama dilewati karena sandbox. Kedua paket backend, unit frontend dan kedua build Angular lulus. Tidak ada request live, eksekusi canary, sinyal, perubahan produksi/DB, commit, atau push. [Handoff Phase 1H](phivealpha-phase-1h.md)

## 2026-11-21 — PhiveAlpha Phase 1I (lokal)

Build default Maven dan Angular sekarang menerbitkan PhiveAlpha IDX. Package, resource dan aset crypto dikeluarkan dari distribusi. Sumber legacy tetap tersedia untuk pemulihan dan dokumen aktif memakai runbook PhiveAlpha. Baseline Maven 287 lulus dan 1 skip. Setelah isolasi, Maven CI dan lokal masing-masing 77 lulus dan 1 skip. JAR fixture berhasil startup tanpa HTTP, lima unit frontend dan build Angular lulus. Uji HTTP/browser dan PostgreSQL tertahan izin loopback. Perubahan Phase 1H terjaga. Tidak ada perubahan production, request live, order, commit atau push. [Handoff Phase 1I](phivealpha-phase-1i.md)

## 2026-11-21 — PhiveAlpha Phase 1H SIT-02B (lokal)

Menambah jalur SIT PostgreSQL 17 terisolasi untuk mirror metadata dan regresi in-process saat koneksi audit gagal. Warning memuat dataset, UUID reservasi, hasil transport dan tipe error. Maven offline 78 lulus dan 1 skip. PostgreSQL single-user menyimpan metadata dan menolak insert yang disimulasikan. Runner audit dan regresi Phase 1C terhalang socket sandbox sebelum membuat klaster. Tidak ada request live, koneksi produksi, deployment, order, commit atau push. [Handoff SIT-02B](phivealpha-sit-02b.md)

## 2026-11-21 — PhiveAlpha Phase 1J (lokal)

Menambah ingestion OHLCV fixture dengan validasi sesi dan provenance, penulisan tanpa overwrite, turnover opsional, audit fixture dan jalur screening as-of. Baseline lokal 3f8940f. Maven offline CI 86 lulus dan satu skip. SQL PostgreSQL 17 single-user lulus dan artefak PhiveAlpha bebas kelas serta resource CoinMind. Runner R2DBC tertahan probe socket. Tidak ada API live, transaksi, perubahan production, deployment, commit atau push. [Handoff Phase 1J](phivealpha-phase-1j.md)

## 2026-11-21 — PhiveAlpha Phase 1K (lokal)

Menghubungkan history OHLCV Phase 1J dengan Liquid Universe dan News-Driven Scoring. Snapshot mencatat cutoff, kualitas, alasan eligibility, breakdown faktor, dan ranking deterministik maksimum lima. Endpoint read-only memakai fixture default, dengan PostgreSQL development opt-in. Maven offline 94 lulus dan satu skip. Tes in-process memeriksa kegagalan baca tanpa fallback, dan runner PostgreSQL terisolasi tertahan probe socket. Tidak ada API live, order, perubahan produksi, deployment, commit atau push. [Handoff Phase 1K](phivealpha-phase-1k.md)

## 2026-11-21 — PhiveAlpha Phase 1L (lokal)

Baseline `c614812`. Menambah Stock Screener pada navigasi Angular, integrasi bertipe dengan snapshot API, shortlist interaktif, rincian faktor dan penolakan, status data, provenance, serta loading, empty dan retry. JSON WebFlux fixture aktual berhasil dirender in-process. Unit frontend 13 lulus, build Angular lulus, dan Maven offline 94 lulus dengan satu skip. Browser dan socket loopback tertahan sandbox. Perubahan terisolasi pada frontend dan dokumentasi development. [Handoff Phase 1L](phivealpha-phase-1l.md)

## 2026-11-21 — PhiveAlpha Phase 1M (lokal)

Memperbarui shell dan keenam halaman dengan desain light-only di atas worktree Phase 1L. Sidebar desktop dan navigasi compact memakai rute yang sama. Stock Screener memisahkan shortlist dari detail kandidat yang dapat ditutup dengan provenance dan guard skor tetap berlaku. Unit frontend 13 lulus, build Angular lulus, dan Maven offline 94 lulus dengan satu skip. Pemeriksaan visual browser tertahan startup headless di sandbox. [Handoff Phase 1M](phivealpha-phase-1m.md)

## 2026-11-22 — PhiveAlpha Phase 1N (lokal)

Redesain Angular light-only dengan navigasi bawah mobile dan sidebar desktop. Screener menampilkan maksimal lima kandidat dan membuka satu tampilan riset dengan bukti ringkas, rincian teknis tertutup, dan alasan saham belum lolos. API fixture dan rute lama tetap berlaku. Regresi Maven offline mencatat 94 lulus dan satu skip loopback. Panduan pemeriksaan browser pada host tersedia di [handoff Phase 1N](phivealpha-phase-1n.md).

## 2026-11-22 — PhiveAlpha Phase 1N.1 (lokal)

Kartu Screener dipadatkan dengan target sentuh 44 px. Skor tampil dengan maksimal dua desimal Indonesia, sementara nilai API tetap tersedia pada rincian dan urutan backend tetap dipakai saat skor sama. Navigasi bawah memakai ikon garis seragam. Unit frontend 16 lulus, build Angular lulus, dan git diff --check lulus. Review Chromium dengan fixture lokal mencakup 360, 768, dan 1280 px. Pemeriksaan meliputi ukuran target, overflow, urutan kandidat, serta Enter dan Spasi untuk membuka riset dan rincian, pengembalian fokus, dan navigasi keyboard. [Handoff Phase 1N](phivealpha-phase-1n.md#phase-1n1--mobile-usability-polish)
