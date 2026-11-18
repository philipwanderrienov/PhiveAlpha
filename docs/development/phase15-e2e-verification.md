# Phase 1.5 isolated E2E verification — 2026-11-09

Local branch `main`, HEAD `9bf10ce`. Existing tracked and untracked changes retained. No branch, commit, push, deployment, production restart, order execution or live/paid AI call. Remote main comparison remains blocked by invalid SSH configuration ownership/permissions; SSH configuration was not changed.

## Isolation and method

- Full Spring Boot application runs in child JVMs bound exclusively to `127.0.0.1`, on dynamically selected unprivileged ports excluding 8080/5432. Environment is rebuilt from a small allowlist; credentials, Java injection options and production connection configuration are not inherited.
- Test-classpath-only `GeminiRecoveryE2eApplication` replaces the Gemini bean's HTTP transport with a loopback WebClient and supplies a fake provider clock plus a dedicated temporary durable ledger. The existing provider/parser/circuit, scheduler, news service, status/controller serialization and deterministic advisor are used. No production provider endpoint override or test control API is shipped in the application JAR; JAR contents were inspected.
- Gemini, market REST/WebSocket and prediction endpoints are loopback mocks. RSS, automatic AI triggers, persistence, archive/job, push and billing are disabled. The application R2DBC target is unused loopback port 1, SQL initialization and DB health are disabled. News and market history are synthetic fixtures; these tests make no historical provenance or strategy profitability claim.
- Actual production scheduler retains its 30-second interval. Provider time advances to test cooldowns without sleeping minutes/hours. Further scenarios use the same coordinator through a test-only HTTP control. The first HALF_OPEN probe was observed from the actual scheduler without invoking that control.
- PostgreSQL is a separate newly initialized cluster in `/tmp`, loopback port **59227**, database `coinmind_replay_test`, synthetic credentials and explicitly installed archive schemas. It does not use the deployed cluster/database. Its report confirms shutdown.
- Chromium uses the Angular production build with same-origin proxying to the isolated backend. The first status response is real HTTP; other states replay captured real backend response bodies. Angular polling is advanced with Playwright's clock. Browser external requests are blocked, including Google Fonts; service workers are disabled for reliable HTTP interception. Backend WebSocket/SSE transport is verified separately; browser proxy SSE is intentionally disabled.

## HTTP and process evidence

Artifacts: `/tmp/coinmind-phase15-e2e-s6o2rgel/`; driver log `/tmp/coinmind-phase15-e2e-http.log`. `status-snapshots.json` contains actual endpoint bodies, `report.json` records checks and cleanup.

| Criterion | Observed evidence |
| --- | --- |
| HTTP 503 / OPEN | First attempt returns UNAVAILABLE; status ERROR/OPEN; earliest recovery exactly +300s |
| Cooldown | Direct API and coordinator attempts at +299s produce no extra mock HTTP call |
| Real scheduler / HALF_OPEN | Scheduled probe observed after **28.164s** wall time, with HALF_OPEN exposed over HTTP |
| Nonblocking operation | During an **8s** delayed Gemini response, real mock WS publications continue through production SSE; received prices `[70001,70001,70002]` |
| Advisor independence | Five paired ticker/advisor HTTP checks take **0.275, 0.132, 0.069, 0.053, 0.044s**; deterministic disclosure remains present |
| Deduplication / success | Extra direct/coordinator requests during HALF_OPEN create no second mock HTTP call; validated 200 closes circuit and exposes READY; classification confidence remains zero |
| Repeated 503 | Further failed HALF_OPEN yields **660s** cooldown, within expected 600..<720s jitter range |
| OPEN restart | Separate JVM restart preserves recovery metadata/deadline and blocks early requests |
| Shared-ledger race | Second JVM with same ledger fails startup due to exclusive lifetime lock |
| HALF_OPEN process kill | Killing a real in-flight JVM and restarting restores OPEN, preserving pre-request conservative **one-hour** deadline |
| Rolling quota restart | Failed request exhausts configured daily cap of one; separate JVM restart an hour later remains RATE_LIMITED without another HTTP call |
| Separate-ledger hazard | Simultaneous second JVM with another ledger and the same mock key can admit another request: **single-instance constraint is mandatory** |
| HTTP 429 | `Retry-After: 7200` sets earliest recovery +7200s; +7199s still blocks requests |
| HTTP 401 and 403 | AUTH_ERROR stops attempts; auth latch survives separate JVM restart; fresh news after restart still cannot send HTTP |

Failed news batches may be retried while structurally valid and published within 24h. There is no empty heartbeat, distributed quota coordination or durable per-article exactly-once guarantee. Global freshness reflects the last validated response, not coverage of every symbol. HTTP-date Retry-After, capped repeated backoff, empty/stale/invalid-news rejection and reduced quota after restart are additionally covered by the existing fake-clock unit tests.

## UI and accessibility evidence

Browser results and screenshots are in the same artifact directory. Test tools: Playwright **1.65.0**, Chromium headless shell **158.0.8127.18**, axe Playwright **4.14.0**, installed only under `/tmp`; repository dependencies were not changed for them.

| Backend state | Indonesian label |
| --- | --- |
| READY | AI Aktif |
| ERROR (OPEN/HALF_OPEN), RATE_LIMITED | AI Sedang Mengalami Gangguan |
| AUTH_ERROR, CONFIGURED_UNVERIFIED, failed status fetch | AI Tidak Tersedia |
| DISABLED, MISSING_API_KEY | AI Belum Diaktifkan (existing label-helper tests) |

Actual production component polling, aria-label updates, keyboard focus/Enter disclosure, freshness/timestamp explanations and clearing a previously active status on HTTP 503 were exercised. Axe checks are scoped to `.header-ai-status` (WCAG 2 A/AA and 2.1 AA); they are not a whole-app accessibility certification. Viewports: **320, 360, 390, 768, 1024, 1440px**.

Screenshot review found a mobile defect missed by the initial bounds assertions: legacy `.brand { width:100% }` caused brand/badge overlap and a page width 60px wider than the viewport. Added only `width:auto !important` to the existing scoped mobile brand reservation rule. After rebuilding, **18 browser checks passed**: zero scoped axe violations at all six widths, document width exactly equals viewport width, mobile brand/badge remain separated by 8px, and the mobile header remains 64px. Final `status-{width}.png` images were reviewed; `status-320-before-width-fix.png` retains the baseline defect.

The first browser attempt stopped at an axe harness error (`browser.newPage` does not provide the explicit context axe needs), after HTTP label/polling checks passed. Switched to `browser.newContext().newPage()` and reran browser verification using the already captured backend states and isolated auth ledger. This was a test-tool correction, not an application failure. Backend scenario results were preserved; browser retry cleanup also stops all child JVMs.

## Validation and changes

- Standard `mvn -f backend/pom.xml clean verify`: final post-isolation rerun **BUILD SUCCESS, 185 tests, zero failures/errors/skips**, recorded in `/tmp/coinmind-phase15-e2e-verify.log`. Initial sandbox run failed Mockito attachment; approved execution outside sandbox was used.
- Python offline suite: **52 passed**, `/tmp/coinmind-phase15-e2e-python.log`.
- PostgreSQL opt-in tests: **5 passed**, zero failures/errors/skips: two CSV-to-DB cases, archive readback, service persistence, atomic commit/rollback. `/tmp/coinmind-phase15-postgres-auttw65n/{commands.log,report.json,postgres.log}`; cluster stopped. Dataset-dependent historical IT requires separate real data and was not run.
- Post-fix Angular production build **passed**: `/tmp/coinmind-phase15-e2e-build.log`; existing template warnings retained. Label helper: **10 passed**. General `npm test` still has no configured Angular target; the optional real-browser test supplies targeted UI verification. Final browser retry log: `/tmp/coinmind-phase15-e2e-browser-recheck.log`; report PASS and `allChildJvmsStopped=true`.
- This increment adds the test-only Java process fixture, `scripts/validation/phase15_e2e.py`, `scripts/validation/phase15_postgres.py`, `frontend/test-ai-status-browser.mjs`, and this report; updates provider-selection tests to `@TempDir` ledger isolation; applies the single mobile CSS width override; updates canonical handoff files. Deterministic rules and production backend code are unchanged by this verification increment.
- Previous Gemini/Futures/research/frontend work, `deploy.sh`, lockfile, backup and bytecode artifacts remain. Full preexisting file inventory is in `gemini-news-phase1.md`. No unrelated files removed.

## Security and readiness limits

Changed/untracked text scan found no Gemini/OpenAI key or private-key patterns. Dummy test credentials are explicit and never real. Fixed endpoint/header authentication, strict validation, bounded timeout/response, attempt-before-HTTP accounting and safe diagnostics remain intact. No provider fallback added; prediction runtime remains Manifold-only. No production dependency additions. Pattern scanning is not an exhaustive secret scan or dependency-vulnerability audit, and no OS-level egress capture was used.

Deployment constraints remain: exactly one enabled instance per Google project/key; persistent local ledger shared across all starts; no independent clients consuming the assumed budget. The lock does not coordinate different directories or hosts. Never erase the ledger to reset quotas; AUTH_ERROR requires operator intervention while preserving attempt history. Migrating from the older in-memory provider requires 24h quiet after its last attempt. Local caps do not certify the project's free billing tier. Gemini remains headline-classification-only; mandatory-AI entry policy conservatively WAITs even after headline recovery.

Readiness: requested isolated recovery, HTTP, process persistence, advisor/market isolation, responsive status UI, scoped accessibility and PostgreSQL criteria **passed**. This does not establish live Google endpoint/model/credential compatibility, project-wide quota coordination, whole-app accessibility, production latency or production acceptance. No remaining blocker to these isolated scenarios; release synchronization, persistent-ledger/single-instance setup and any live-news acceptance still require operator review/authorization.

## Safe repeat commands and recommended deployment sequence

Run from the repository root on a terminal permitted to bind loopback sockets. These commands do not deploy or contact live AI:

```bash
mvn -f backend/pom.xml clean verify
python3 -m unittest discover -s scripts/offline -p 'test_*.py'
(cd frontend && npm run build && npm run test:ai-status)
npm install --prefix /tmp/coinmind-phase15-browser playwright@1.65.0 @axe-core/playwright@4.14.0
PLAYWRIGHT_BROWSERS_PATH=/tmp/coinmind-phase15-browsers node /tmp/coinmind-phase15-browser/node_modules/playwright/cli.js install chromium --only-shell
python3 scripts/validation/phase15_e2e.py --confirm LOOPBACK_MOCKS_ONLY --browser
python3 scripts/validation/phase15_postgres.py --confirm ISOLATED_TEST_DATABASE_ONLY
```

Prerequisites: JDK/Maven and frontend dependencies already installed; PostgreSQL 17 binaries under `/usr/lib/postgresql/17/bin`; Node 22 at the path used by the browser harness. Artifacts and stopped DB data are retained in `/tmp`, not recursively deleted. If sockets or browser launch are forbidden, those scenarios are **unverified**, not passing; use these commands from an authorized terminal instead. Package/browser downloads access public registries/CDNs only, never AI APIs.

Recommended future deployment, **not performed**:

1. Review the complete dirty working tree and approve a concrete release artifact; resolve remote SSH comparison before any future synchronization.
2. Confirm one enabled instance, other clients' quota usage, secure credential delivery, free-tier/project settings, and an absolute persistent ledger path writable by the service account. Preserve accounting through deployment and rollback; apply the migration quiet period if needed.
3. Promote the reviewed build only with deployment authorization. Keep deterministic advisor behavior, production Futures BLOCKED and order execution disabled. Test-control fixture must remain absent from the production JAR.
4. Check health/status/news/market/advisor and desktop/mobile UI. A real Gemini smoke test requires separate authorization, an eligible genuinely useful news batch, available quota and operator observation; never use synthetic heartbeat calls or restart to bypass cooldown.
5. Observe logs/status through a recovery cycle, polling, ledger persistence and quota limits. Roll back the application artifact if needed while retaining the ledger and its accounting history.
