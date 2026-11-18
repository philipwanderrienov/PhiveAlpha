> Update 2026-11-12: legacy Polymarket cleanup completed in working tree; earlier migration tasks/descriptions below are historical. Runtime prediction provider is Manifold only. See current-state.md.

# Gemini News Intelligence Phase 1

Explicit provider selection `coinmind.ai.provider=gemini` (`AI_PROVIDER=gemini`) enables only the Gemini implementation. The existing application default remains `openai` with `AI_ENABLED=false`. Absent/unknown provider outside application YAML uses only NoOp; mock/OpenAI selection keeps its existing implementation. Gemini additionally requires `AI_ENABLED=true`, `GEMINI_NEWS_ENABLED=true` and `GEMINI_API_KEY` (or the secured `coinmind.ai.gemini.api-key` configuration mapping). OpenAI's `AI_API_KEY` is never used for Gemini. No secrets are printed, stored in results, or included in request URLs. Gemini configuration's string representation is redacted.

The existing `POST /api/v1/ai/news-intelligence/{symbol}` accepts BTCUSDT, ETHUSDT or SOLUSDT. It classifies at most five public RSS headlines and sources, not full article bodies or private market/account context. Uses Google `generateContent`, header authentication and `responseMimeType=application/json`; contract checked against [Google API reference](https://ai.google.dev/api/generate-content) and [authentication reference](https://ai.google.dev/api). Model default `gemini-3.5-flash-lite` follows the user's manual HTTP 200 verification and existing locally modified research script; no live verification was performed by automated tests. Configurable via `GEMINI_MODEL`. The offline script and CSV schema are untouched.

## Compatibility and time semantics

- Response JSON must contain exactly one result for every supplied article ID. Strict duplicate JSON property detection; no unknown IDs, missing IDs, duplicate IDs, unknown classifications, extra item fields, missing rationale or invalid relevance numbers. Require one completed STOP candidate and one text part. Reject malformed/truncated/blocked results wholesale.
- `ArticleImpact.articleId/title` come from the original input. Sentiment permits BULLISH, BEARISH, NEUTRAL, UNCERTAIN. Qualitative HIGH/MEDIUM/LOW/UNKNOWN maps to importance 75/50/25/0; these are display encodings, not calibrated price-impact estimates. Relevance 0..1 is validated and intentionally has no field mapping because the existing record has no relevance field. It never becomes confidence. Confidence is always zero and horizon NONE because headline classification supplies neither trading certainty nor a justified horizon.
- Aggregate direction is the common direction only if all items agree, otherwise UNCERTAIN; aggregate importance is maximum qualitative importance. No generated aggregate prediction. Failures return direction UNAVAILABLE, zero confidence/importance, empty impacts and a bounded reason. Real neutral classifications remain distinguishable from failures.
- Live inputs must have publication timestamps no later than current observation and no older than 24h. `analyzedAt` is actual scoring completion time, never article publication time. `NewsArticle` does not carry first-seen provenance; this implementation does not claim it does. Results are process-local and are **not historical replay inputs**. Historical integration requires genuine headline firstSeen and scoring-availability provenance in a later migration; do not backdate results or reuse present classifications in past decisions.
- `AiNewsIntelligenceService.latestFresh` deliberately returns no Gemini trading modifier: Phase 1 is classification/display only. Trading analysis/review return UNAVAILABLE with zero confidence; review retains the original deterministic action. Prediction interpretation returns UNAVAILABLE without making a network request. No risk engine or execution behavior changes.

## Bounds and failure behavior

Two explicit switches plus selected provider and configured key. Local process-wide maximum one attempt per 60 seconds and ten per rolling 24h, across symbols; `GEMINI_MAX_CALLS_PER_DAY` may reduce this (hard ceiling ten, zero disables). Failures consume an attempt. Maximum five articles, 400 characters per title, 100 per source, 200 per ID, 64 KiB buffered HTTP response, 1600 output tokens, 3s connect timeout and 15s total timeout. `GEMINI_TIMEOUT_MS` may reduce the operation timeout (100..15000ms). Redirects disabled. No retry, other model, OpenAI or paid fallback. 429/401/403/5xx are UNAVAILABLE with status only; response bodies and exception details are never exposed/logged.

Cache and in-flight deduplication use exact input article records (including ID, headline, source and timestamps), per supported symbol, for at most 15 minutes. Inputs are freshness-validated on every access. Content changes invalidate prior results before rate-limit checks, so failures cannot return old classifications for changed headlines. Failures are cached too to avoid repeated spend. Limits/cache are process-local, reset on restart, and are not a billing guarantee or multi-instance quota. Keep one controlled instance and confirm the project actually has the intended free-tier/billing configuration in Google AI Studio. No paid usage estimator pricing is invented for Gemini.

## Legacy prediction audit (removal deferred)

The current `polymarket/service/PolymarketIntelligenceService` makes Manifold-only network requests. Legacy `AiProvider.analyzePolymarket*`, `AiPolymarket*` records/services/benchmark/controller, `Polymarket*` models/routes, `AiProperties.polymarket*`, YAML AI_POLYMARKET settings, TradeSetupService Gamma-specific modifier checks, frontend labels/models and older handoff prose still exist. Their removal/renaming needs a separate coordinated contract/config/frontend migration. Gemini implements only the required unavailable interface stub; it restores no Gamma/CLOB clients and introduces no prediction provider. Manifold Markets remains the only approved provider.

## Controlled manual smoke test (not deployment)

Use a separate foreground development process on loopback, not the production service. First confirm Google project billing/quota and that fresh real BTC headlines are present. In a trusted terminal with shell tracing off:

```bash
export AI_PROVIDER=gemini
export AI_ENABLED=true
export AI_AUTOMATIC_ENABLED=false
export GEMINI_NEWS_ENABLED=true
export GEMINI_MODEL=gemini-3.5-flash-lite
export GEMINI_MAX_CALLS_PER_DAY=1
export GEMINI_TIMEOUT_MS=15000
export PERSISTENCE_ENABLED=false
export SERVER_ADDRESS=127.0.0.1
export SERVER_PORT=18080
read -rsp 'Gemini API key: ' GEMINI_API_KEY; printf '\n'
export GEMINI_API_KEY
java -jar backend/target/coinmind-backend-0.1.0-SNAPSHOT.jar
```

In another terminal, once startup/RSS ingestion finishes:

```bash
curl --fail-with-body -sS -X POST http://127.0.0.1:18080/api/v1/ai/news-intelligence/BTCUSDT
```

Expect headline impacts with confidence zero, or a clearly marked UNAVAILABLE reason if no valid fresh headlines, disabled/missing credentials, quota, HTTP or validation failure. Repeat unchanged request to exercise cache without another API attempt. Stop the foreground process and `unset GEMINI_API_KEY` in the original shell. No production service restart, deploy, live order, automatic trading analysis, or historical backfill.

## UNAVAILABLE investigation (2026-11-05; local changes only)

Confirmed: the old `Gemini request or response unavailable; no retry or fallback` summary came from the catch-all error handler, which discarded the distinction between transport, timeout, malformed JSON, response-size and strict validation failures. Disabled/key/model/article/rate-limit preflight failures had separate summaries; ordinary completed non-2xx responses had `HTTP_STATUS_nnn`. A body-drain failure could previously erase that HTTP status. The new typed HTTP path preserves it even when draining the discarded error body fails.

No live Gemini call was made during this investigation. Available `/tmp/coinmind*log` files and the latest 1000 `coinmind-backend` journal lines contained no diagnostic evidence for the reported live failure. These logs cannot identify that foreground smoke run's original exception. Mocked realistic five-article STOP envelopes and serialized request checks exercise the existing contract; no confirmed request-schema or parser defect justifies relaxing it. Offline scoring uses its own `btcRelevance` contract; backend prompts require `relevance`, so offline success alone does not validate the backend response contract.

Failures now log only category, HTTP status (NONE if not received), validated model, article count and a locally generated correlation UUID. No exception/message/stack trace, provider header, URL, input or response body is logged. Results retain bounded category/status summaries and zero confidence/importance. Categories distinguish HTTP 400/401/403/429/5xx/other, transport, timeout, INVALID_JSON, VALIDATION_FAILED, EMPTY_RESPONSE, RESPONSE_TOO_LARGE and INTERNAL_ERROR. HTTP status 200 alongside a failure means the response reached the parser/reader; it does not certify valid classification. Failure cache, no retry/fallback, local daily/minute caps and all strict validation remain intact.

Unconfirmed possibilities: network/TLS/DNS/connect/read timeout, malformed/truncated JSON, blocked/non-STOP candidates, output-contract or identity mismatch, or oversized response. No model-specific incompatibility or quota/authentication failure has been established. Startup/health and successful RSS retrieval do not test the outbound Gemini path.

Further smoke is deferred: do not spend another attempt today or restart to reset the cap. If the cause still needs isolation after the existing rolling 24-hour budget is available, use the existing controlled foreground setup above with the rebuilt jar, automatic AI off and cap 1; leave production untouched. Capture safe diagnostics by starting that foreground process with:

```bash
java -jar backend/target/coinmind-backend-0.1.0-SNAPSHOT.jar 2>&1 | tee /tmp/coinmind-gemini-local-smoke.log
```

Once health/RSS are ready, issue exactly one POST:

```bash
curl --fail-with-body -sS -X POST http://127.0.0.1:18080/api/v1/ai/news-intelligence/BTCUSDT
```

On failure, inspect only the bounded diagnostic line (no need to share whole logs):

```bash
grep -E 'GeminiAiProvider.*category=' /tmp/coinmind-gemini-local-smoke.log
```

Stop the foreground process afterward. Do not repeat the POST to troubleshoot, increase the cap, change models, or enable wire/debug logging. A transport/timeout still cannot prove whether Google consumed the attempt. The correlation ID is local, not a Google support request ID.

## Phase 1.5 — automatic recovery and Indonesian status (2026-11-06; local only)

This section supersedes the earlier process-local/reset-on-restart and no-recovery descriptions. Strict output validation, classification-only confidence zero and no paid fallback remain unchanged.

Gemini now admits one request at a time globally. A failed request opens the circuit: initial 300 seconds, then 600/1200/2400/3600 seconds with additive 0..<20% jitter capped at 3600 seconds. HTTP 200 with malformed/invalid output remains a failure. A validated result closes the circuit. HTTP 429 honors either Retry-After seconds or HTTP date, even beyond the backoff cap. HTTP 401/403 latches AUTH_ERROR: operator intervention is required; automatic probing stops. There is no HTTP retry loop. A scheduled 30-second coordinator makes one bounded pass through BTC/ETH/SOL via existing news service only when recovery is due. Empty/invalid/stale articles never reach HTTP. Cache hits never manufacture a recovery. Scheduler subscription is asynchronous; market analysis does not await Gemini.

Durability and execution constraint: run **exactly one Gemini-enabled CoinMind instance per Google project/key**, on one host with a persistent local filesystem. The default ledger is `data/ai-recovery`, relative to the working directory; set an absolute persistent directory with JVM `-Dcoinmind.gemini.state-dir=/absolute/persistent/path`. All starts must use the same directory. Exclusive lifetime file lock rejects a second process using that directory. This is NOT distributed coordination across hosts, independent directories, replicas or other clients using the same Google quota. Do not run those concurrently. Atomic ledger replacement and file force preserve attempt timestamps, circuit/cooldown, last success/failure and backoff; failed attempts count before HTTP. A crash during HALF_OPEN retains the pre-request one-hour deadline; loading the ledger converts the orphaned HALF_OPEN state to OPEN so scheduled recovery resumes after that deadline. Status snapshots prune expired rolling-day attempts under the admission lock. Corrupt/unwritable/locked storage fails closed. Preserve the ledger across deployment/restart; never delete it to reset quotas. Initial migration cannot reconstruct pre-ledger attempts: wait a full rolling 24h after the last old-process attempt before enabling this version. Disk loss/power-loss filesystem guarantees and external client usage are not billing guarantees. Auth failures require correcting configuration and an operator-controlled ledger reset only after retaining attempt history / waiting the rolling day; no automatic reset on restart.

Existing GET `/api/v1/ai/status` now includes a safe nested `recovery` object for Gemini: `state` (CLOSED/OPEN/HALF_OPEN/AUTH_ERROR), `reasonCode`, `lastSuccess`, `lastFailure`, `nextEligibleRecoveryTime`, `dataFreshness` (MISSING/FRESH/STALE, 15-minute success freshness). Next time includes minute/daily admission bounds, is null for zero daily quota, and is an earliest eligibility time, not a promised retry. Credentials, error bodies and logs are never included. Existing top-level state remains compatible; READY requires a fresh validated success, ERROR means open/half-open, RATE_LIMITED means daily cap, AUTH_ERROR means credentials rejected, CONFIGURED_UNVERIFIED covers no fresh validated result. Mock is never presented as live AI.

2026-11-09 audit: when the configured daily cap is reduced, quota eligibility waits for enough persisted attempts to expire, rather than only the oldest attempt. Ledger loading rejects unknown circuits, invalid reason codes, negative failure counts, more than ten attempts and unordered attempt histories; startup refuses requests instead of resetting accounting.

Header indicator polls this existing API every 30 seconds, on desktop and mobile. Labels: READY → **AI Aktif**; ERROR/RATE_LIMITED → **AI Sedang Mengalami Gangguan**; DISABLED/MISSING_API_KEY → **AI Belum Diaktifkan**; unverified/auth/mock/status-fetch failure → **AI Tidak Tersedia**. Keyboard-accessible native details/summary opens a compact panel showing freshness, last success/failure, earliest recovery and deterministic/WAIT disclosure. Status fetch failure clears prior status instead of retaining an active badge. The browser never calls Gemini. The AI view uses the same primary labels.

Current live strategy is optional AI by default and now discloses deterministic-only recommendations when AI is absent. `coinmind.ai.required-for-entry=true` opts the live strategy into mandatory fresh AI: missing/unavailable AI forces WAIT with no entry/targets. Gemini Phase 1 remains excluded from trading modifiers, so mandatory mode conservatively waits even after Gemini news classification recovers; recovery is not evidence of trading-grade AI. Offline replay policy is unchanged. No fabricated AI confidence or forced BUY/SELL.

Builds no longer inline remote Google Fonts; this permits an offline production build, retaining the existing CSS font import/fallback at runtime. Angular has no configured test target; `node frontend/test-ai-status.mjs` (run from frontend: `node test-ai-status.mjs`) verifies the actual TypeScript label helper without extra dependencies. Browser visual acceptance remains outstanding.

### Follow-up audit — 2026-11-09

Subsequent isolated E2E now completed: see `phase15-e2e-verification.md` and the latest current-state/session-log entries for real loopback HTTP, process-restart, PostgreSQL and responsive-browser evidence. Earlier pending browser/process/PostgreSQL statements describe the pre-E2E state. The only product correction from E2E was the scoped mobile brand width override; provider/trading behavior was preserved.

Reused the existing working tree on local main `9bf10ce`. The follow-up changes only move the recovery subscription onto Reactor boundedElastic (so synchronized RSS reads and initial durable admission do not run on Spring's shared scheduler), adapt asynchronous coordinator tests and add a blocked-news-read/repeated-tick regression. Recovery remains a bounded three-symbol pass with a single-pass atomic gate and provider-level global admission. This does not promise independent CPU/disk resources or production latency measurements. No implementation was rebuilt from scratch.

Work eligibility means one to five structurally valid public headlines, published no more than 24 hours ago and not in the future. A previously failed batch may be retried after cooldown; it does not require a different article ID for each probe. This is processing outstanding news work, not a synthetic health heartbeat. Successful exact-input cache reuse is bounded to 15 minutes; there is no durable per-article exactly-once processing guarantee. Cache and news-service result maps are in memory; only admission/circuit history persists across restart. Freshness in global status describes the last validated response, not guaranteed coverage for all three symbols. Gemini remains excluded from advisor modifiers.

Working-tree inventory relative to HEAD (includes work predating this follow-up):

- New backend implementation: `GeminiProperties.java`, `GeminiAiProvider.java`, `GeminiRecoveryState.java`, `NoOpProviderCondition.java`.
- Changed backend implementation/config: `AiConfiguration.java`, `NoOpAiProvider.java`, `AiNewsIntelligenceService.java`, `AiEngineStatus.java`, `AiStatusService.java`, `TradeSetupService.java`, `application.yml`.
- New backend tests: `AiProviderSelectionTest.java`, `GeminiAiProviderTest.java`, `GeminiRecoveryStateTest.java`, `GeminiNewsIsolationTest.java`, `GeminiRecoveryCoordinatorTest.java`, `GeminiStatusTest.java`; changed `TradeSetupServiceTest.java`.
- Existing unrelated replay startup fix: changed `FuturesPaperEquityArchive.java`, `FuturesPaperReplayArchive.java`, new `FuturesReplayContextTest.java`; retained and covered by full verification.
- New frontend files: `package-lock.json`, `src/app/core/ai-status-label.ts`, `test-ai-status.mjs`. Changed: `angular.json`, `package.json`, `app.component.ts`, `app.component.html`, `app.component.css`, `core/models/market.models.ts`.
- Documentation: changed `current-state.md`, `session-log.md`; this Phase 1/1.5 guide is untracked relative to HEAD.
- Preexisting research/operator artifacts retained: modified `scripts/offline/gemini_news_research.py`, untracked `.py.bak`, Python `__pycache__` bytecode, and `deploy.sh`. Deployment script was inspected and syntax-checked, never executed. Its restart/delete commands are outside this audit's authorization.

Security review: Gemini authenticates by header against a fixed HTTPS endpoint; redirects/connection retries are disabled. Public title/source/ID/publication fields only; no article bodies, private portfolio context, credentials or URLs in request payload. Strict response validation, bounded response/timeout, safe log metadata and failed-attempt accounting remain intact. Pattern scan of changed/untracked text found no Gemini/OpenAI key or private-key patterns; this is not an exhaustive secret or dependency-vulnerability audit. No fallback provider is activated by recovery. Runtime prediction-market client remains Manifold-only despite legacy Polymarket names. Free billing tier and project-wide quota cannot be certified by local counters; independent clients and separate state directories are outside the lock.

E2E acceptance still needs an isolated browser/backend harness with mocked external HTTP at mobile and desktop widths, real UI polling/disclosure, and durable restart across processes. No visual/browser acceptance or disposable PostgreSQL IT was performed here. Remote HEAD comparison is blocked by local SSH configuration ownership/permissions; no SSH configuration was modified. No live Gemini call, commit, push, deploy, branch switch or production service restart.

Validation after the follow-up: exact `mvn -f backend/pom.xml clean verify` outside sandbox **BUILD SUCCESS, 185 tests, zero failures/errors/skips** (`/tmp/coinmind-phase15-audit-final-maven.log`). Initial sandbox JVM attach failed; first new synchronization regression was corrected before this passing clean run. Python offline **52 passed** (`/tmp/coinmind-phase15-audit-python.log`); Angular production build **passed** with existing template warnings (`/tmp/coinmind-phase15-audit-build.log`); actual label helper **10 cases passed**. `npm test` reports no project/target; general Angular suite unavailable. `git diff --check` passed. These checks support proceeding to isolated mocked E2E, not production acceptance.
