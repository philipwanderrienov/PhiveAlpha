## 2026-11-20 — PhiveAlpha Phase 1G (lokal)

`HistoricalReplayService` mengevaluasi ranking riset pada cutoff point-in-time melalui universe dan scoring yang ada. Replay sintetis memakai entry open berikutnya, exit 2/5/10 sesi, biaya dan batas lot/likuiditas, baseline likuiditas dan IHSG sintetis, serta split kronologis dengan embargo. Posisi unresolved tetap tercatat. Maven offline 271 lulus, satu uji loopback Phase 1D.2 dilewati karena sandbox. Kedua paket backend, unit frontend, dan kedua build Angular lulus. [Handoff Phase 1G](phivealpha-phase-1g.md)

## 2026-11-19 — PhiveAlpha Phase 1F (lokal)

`NewsDrivenScoringService` memberi ranking riset maksimum lima kandidat dari universe eligible dengan data lengkap pada cutoff. Lima faktor berbobot configurable menyimpan breakdown dan provenance. Duplikasi, ketersediaan point-in-time, dan kualitas source diperiksa sebelum ranking. Bobot dan koefisien masih hipotesis fixture. Maven offline 264 lulus dan satu uji loopback Phase 1D.2 dilewati karena sandbox. Kedua paket backend, unit frontend, dan build Angular lulus. Endpoint fixture tetap, tanpa sinyal atau request live. [Handoff Phase 1F](phivealpha-phase-1f.md)

## 2026-11-19 — PhiveAlpha Phase 1E (lokal)

`LiquidUniverseService` menilai saham biasa IDX dari referensi dan kalender berversi serta candle EOD yang tersedia pada cutoff. Eligibility, kualitas data, alasan penolakan, dan ranking likuiditas deterministik tersedia untuk caller internal. Policy masih hipotesis, hasil tidak dibatasi lima, dan endpoint demo tetap memakai fixture. Maven offline: 257 lulus, satu uji loopback Phase 1D.2 dilewati karena sandbox. Kedua paket backend dan build Angular lulus. [Handoff Phase 1E](phivealpha-phase-1e.md)

## 2026-11-19 — PhiveAlpha Phase 1D.2 (lokal)

Canary developer standalone untuk quote BBCA menuntut `--confirm-live` dan persetujuan entitlement lokal yang berlaku. Budget/concurrency/pages masing-masing satu, retry nol, laporan metadata aman, dan hasil parser lolos tetap unverified atau stale. Maven offline mencatat 250 lulus dan satu uji connector loopback dilewati karena sandbox. Kedua paket backend dan build Angular lulus. Live canary menunggu uji connector pada host development dan persetujuan akun/vendor. [Handoff Phase 1D.2](phivealpha-phase-1d2.md)

## 2026-11-18 — PhiveAlpha Phase 1D (lokal)

Adapter API.co.id opt-in menyediakan tujuh dataset IDX untuk caller internal, dengan budget per proses, throttle, circuit breaker, cache dan audit freshness. Kalender BEI berversi dan referensi universe memagari cakupan. Fixture tetap default. Profil HTTP memblokir rute Phase 1A dan screening selama kontrak masih unverified, serta ditolak di CI. Maven offline 238 tes lulus dengan Mockito Java agent, kedua paket backend dan kedua build Angular lulus. Live canary belum dijalankan. [Handoff Phase 1D](phivealpha-phase-1d.md)

## 2026-11-18 — PhiveAlpha Phase 1C (lokal)

Persistence IDX tersedia melalui profil opt-in `phivealpha-persistence` dan migrasi SQL manual pada klaster development terisolasi. Service reaktif mencakup master instrumen, harga NUMERIC per basis, berita dengan deduplikasi dan cutoff artikel/emiten, serta audit ingestion. Mode default dan endpoint tetap memakai fixture. Maven 220 tes lulus dengan Mockito Java agent, paket backend dan build Angular kedua target lulus. DDL dan rollback lulus di PostgreSQL single-user. Integration R2DBC dan smoke browser berstatus NOT RUN karena pembatasan sandbox. [Handoff Phase 1C](phivealpha-phase-1c.md)

## 2026-11-18 — PhiveAlpha Phase 1A (lokal)

Fondasi aplikasi IDX tersedia pada `main` dengan entry point Spring Boot/WebFlux dan target Angular terpisah. API fixture menyediakan snapshot, Weekly Top 5, headline dan estimasi lot untuk kelima halaman. R2DBC/PostgreSQL tersedia sebagai dependensi. Mode fixture mematikan koneksi dan inisialisasi SQL. CoinMind tetap tersedia melalui build default. Kontrak, cara menjalankan demo, dan dependensi penghapusan crypto tercatat di [handoff Phase 1A](phivealpha-phase-1a.md).

## 2026-11-16 — CM-102 Provider Health & Recovery (local, NOT deployed)

Manifold health now per BTCUSDT/ETHUSDT/SOLUSDT, with additive symbol/map and retained endpoint fields; UNKNOWN/UP/EMPTY/UNAVAILABLE, attempt timestamp/count, isolated cooldown/cache. Invalid JSON/non-array fails unavailable; cached success suppressed after failed refresh, NONE fallback never contributes advisor confidence/reasons. RSS read-only `/api/v1/news/source-health` reports each configured publisher, attempt/last-success, safe error, usable/invalid-date counts; 5s connect/10s response+total deadline. Missing/invalid/future dates skipped; NewsService rejects null/future updates without replacing valid history or publishing events. CoinDesk/Cointelegraph config and RFC1123 parser retained. No Angular changes.

Final offline Maven clean verify: 210 passed, zero failures/errors/skips (Manifold4, RSS3, News3, Trade Setup6, CM-101 Binance9; Gemini regressions retained). git diff --check passed. No frontend tests required because Angular unchanged. Log: /tmp/coinmind-cm102-final-verify.log. CM-102 ready for release-candidate review; live upstream UAT remains. Full contract, file inventory and remaining upstream/compatibility limits: [CM-102 report](cm102-provider-health.md). Existing dirty work preserved on local main9bf10ce. No Gemini recovery/ledger/quota/circuit/freshness, Binance/CM-101, technical scoring or trading changes; no live Gemini/orders, .env/server config, production restart/deploy, commit/push/reset. CM-103 not started.

## 2026-11-14 — CM-101 Market Data Reliability (local, NOT deployed)

Binance ticker/kline/trade validation now requires exchange timestamps within30s old/5s future tolerance; malformed fields and invalid candles rejected. Shared WS reconnect handles graceful completion and errors (existing30s receive timeout,2–30s backoff). Cache/history reject regressions/reopening closed candles. REST candle bootstrap/repair has10s deadline, validates complete ordered contiguous/fresh history and retries only invalid/stale series every60s. ContextBuilder blocks advisor scoring for unavailable/stale/invalid ticker/history/book/trades. Feed health exposes per-symbol/timeframe exchange freshness and explicit UNAVAILABLE; Angular LIVE requires fresh selected ticker/candle plus UP health. Advisor-only retained.

Final Maven offline clean verify205 passed (9 CM-101 regression methods), zero failures/errors/skips; Angular production build passed with existing template warnings; freshness8, AI labels10, News41, Tutorial41 plus News request regressions passed; diff whitespace passed. Logs /tmp/coinmind-cm101-{clean-verify,angular}.log. Earlier overlapping Maven runs caused invalid build-artifact failures; excluded from acceptance, final run sequential and clean. Full audit/source rationale/limits: [CM-101 report](cm101-market-data-reliability.md). Binance Spot bookTicker has local receipt time only; REST current candle uses fetch observation time plus exchange open/close times. No real upstream/socket/browser UAT claimed; end-to-end Sprint DoD awaits that acceptance. Local main9bf10ce inspected, prior dirty work preserved. No Gemini/ledger/quota/key/Manifold/RSS/config/.env/production change, order, commit/push/reset/deployment; CM-102 not started.

## 2026-11-14 — CM-106 desktop UAT follow-up (local, NOT deployed)

Sidebar now has one Tutorial link; module list remains in Tutorial Center. News distinguishes RSS loading, empty, available and failure; sentiment shows loading/unavailable instead of premature zero counts. Request generations reject stale symbol/request responses. Article step waits up to5 seconds for the first actual news card, then highlights the list as a nonblocking fallback; late cards are reacquired. Popover chooses top/bottom/left/right by available viewport space and clamps collisions, with directional markers. Animation-frame target reacquisition/geometry updates and capture-phase scroll handling track navigation, nested scrolling, resizing and asynchronous layout; teardown cancels tracking. Inert shell and blocker retain action protection, including AI; keyboard/navigation/focus controls retained, initial Shift+Tab corrected.

Validation: Angular production build passed (existing template warnings); tutorial41 isolated unit cases plus actual News request-handler regressions (delayed loading/empty/data/failure/stale responses), News41 and AI-label10 passed; diff whitespace passed. No Angular general test target configured. Browser launch failed sandbox EPERM (Operation not permitted); actual desktop/mobile visuals, target/popover alignment under real sticky/nested scrolling, pointer/touch interception, focus return and screen-reader behavior remain unverified. Logs /tmp/coinmind-cm106-uat-{build,browser}.log. Local main9bf10ce inspected; remote-main query blocked by existing SSH include permissions. No backend/Gemini/ledger/production configuration changes, commit/push/reset or deployment. Prior dirty work preserved.

## 2026-11-13 — CM-106 Interactive Tutorial (local, NOT deployed)

Sidebar Tutorial includes Tutorial Center, working Mengenal Dashboard module (10 Indonesian steps), and upcoming modules. Local reusable Angular guided-tour component highlights stable data-tour-id targets on original UI; automatically switches Dashboard/News, opens mobile sidebar and news details, scrolls targets, supports previous/next/restart/skip/exit/completion, Escape/arrows, focus containment/return, resize tracking and teardown. App shell is inert while touring; tour has no API dependencies, Gemini calls, trade execution or configuration writes. Existing polling remains unchanged. Progress is session memory only.

Driver.js 1.4.0 evaluated, but npm installation failed EAI_AGAIN; offline attempt ENOTCACHED. No dependency added. Local Angular engine is the implemented fallback; external guided-tour library requirement is still outstanding. 27 isolated tutorial unit cases (Angular/DOM adapters), 41 News and 10 AI-label cases passed. Angular production build passed with existing NG8102/NG8107 warnings after correcting ElementRef generic typing. Visual desktop/mobile/browser acceptance remains pending: all10 target positions, sticky header/scroll, sidebar visibility, narrow/landscape popover, keyboard/touch/focus, empty/news-loaded states and blocked AI interaction. Log /tmp/coinmind-cm106-build.log. No backend, production configuration, deployment, commit/push/reset changes; prior dirty work preserved. Local main9bf10ce inspected.

## 2026-11-12 — News publisher UI (validated locally, NOT deployed)

Angular news cards show explicit source names from API metadata (CoinDesk/Cointelegraph domain mapping, sanitized plain-text fallback), full publication date/time, title, optional two-line summary and original article HTTP(S) link. Invalid URLs render an unavailable label; links reject non-HTTP(S), credentials, malformed URLs/control characters and use noopener/noreferrer. Angular interpolation only, no raw RSS HTML. Existing symbol filtering, five-card limit, news scores, AI/manual actions and empty/loading flows retained. Responsive source wrapping and one-column cards below480px. No favicon/external logo requests.

Validation:41 source/link unit cases and10 existing AI-status cases passed; Angular production build passed (existing template warnings). Browser smoke attempt blocked by sandbox Chromium EPERM; mobile/desktop visual acceptance remains unverified. Logs /tmp/coinmind-news-ui-{build,browser}.log; optional browser harness /tmp/coinmind-news-ui-browser.mjs. No backend, production config, service, deployment, commit or push changes. Prior dirty work preserved.

## 2026-11-12 — Headline selection follow-up validation

Existing selection fix retained and reviewed against the requested constraints. Added provider defensive-batch/no-HTTP regression and exact-24h service-to-provider mock success regression (7 headline tests total). Coordinator test verification now polls with Awaitility instead of Mockito timeout on synchronized NewsService methods, avoiding worker monitor contention; production recovery unchanged. Follow-up final validation results are recorded in the latest session-log entry. No deployment or live Gemini request.

## 2026-11-12 — Gemini headline selection fix (validated locally, NOT deployed)

Gemini news selection filters every symbol candidate using the provider's captured clock and shared field/24h eligibility rules before relevance-descending/publication-descending ranking, ID deduplication and limit five. Exact 24h cutoff is inclusive; future timestamps are excluded. Empty selection returns UNAVAILABLE without invoking AI. Provider validArticles remains the defensive batch guard. Existing non-Gemini selection, relevance thresholds, recovery/ledger/quota/circuit, automatic AI/trading and Manifold-only policy unchanged. No production release/deployment/config/.env changes or live Gemini requests. Regression tests use fixed clock and mock HTTP. Maven clean verify and final Maven verify: 194 tests passed, zero failures/errors/skips. Final log: /tmp/coinmind-headline-verify-final2.log. Intermediate mock JSON fixture and asynchronous coordinator timeout failures corrected; only test wait tolerance increased.

## 2026-11-12 — Manifold-only conservative baseline accepted in /tmp (NOT deployed)

- Production pointer observed `releases/baseline-phase15`; candidate `/opt/coinmind/releases/candidate-manifold-only` verified read-only. This supersedes earlier pre-migration host snapshots below. Production ledger `/var/lib/coinmind/ai-recovery` was not read or modified.
- New sealed bundle `/tmp/coinmind-baseline-acceptance-wup4qyla/baseline`: **24 process/ledger checks passed**, lock exclusivity both directions, candidate→baseline rollback, OPEN/AUTH_ERROR/quota/HALF_OPEN byte preservation, candidate completed recovery retention, corrupt refusal; all17 test JVMs stopped, zero mock POSTs. Master AI/news/automatic AI literal false, Gemini selected for ledger locking, blank embedded key slots. Manifold/recovery/trading classes unchanged; retired providers absent. **READY FOR OPERATOR REVIEW**, no deployment authorization.
- SHA256 manifest `78f86c861d93351d557d84c9dbf72c9c334b3d47ab3136879348ef33f461448a`; all8 artifacts/phase15-v1 verified. Release12 and isolated Maven189 passed; all230 compiled application classes match imported candidate. Candidate/old baseline/current unchanged. Details/config delta/limits: [acceptance report](manifold-baseline-acceptance.md).
- Root-protected import, higher-precedence config audit (must not enable baseline AI or alter provider/ledger), real-ledger/singleton/infrastructure review and separately authorized activation remain required. /tmp read-only modes are not root-owned production immutability. No .env/key/attestation/service/Nginx/systemd/PhiveGarage/production change, real Gemini call, commit/push/reset. Remote-main query blocked by existing SSH include permissions; local main9bf10ce retained.

# Current migration update — 2026-11-12

Polymarket operational contracts/config/UI/AI interpretation removed; Manifold-only migration supersedes older incomplete-migration notes below. Advisor-only/Futures BLOCKED retained. Gemini recovery/ledger/quota/circuit/API credentials unchanged. No deployment, commit or push authorized. Validation: Maven clean verify **189 passed**, Angular production build passed (existing warnings), release/bootstrap **12 passed**, offline research **52 passed**, AI status **10 passed**. Operational source and packaged application contain no Polymarket references. Full changed-file inventory, residual-reference rationale and compatibility risks: [migration audit](manifold-only-migration.md).

# Current Development State

_Last updated: 2026-11-10 (Asia/Jakarta)_

Dokumen ini adalah sumber kebenaran (handoff) untuk state CoinMind-AI saat ini. Baca file ini sebelum melakukan perubahan.

## Phase 1.6 — bootstrap mechanism implemented, NOT executed (2026-11-10)

- Separate `scripts/release/bootstrap_gemini_ledger.py`: explicit evidence/UTC cutoff, completed24h global project/key quiet period and stopped-client/no-unresolved-restriction acknowledgments. Private owner-controlled directory, Java-compatible exclusive lock, exact UNVERIFIED initial schema, fsynced non-overwriting atomic publication; existing/corrupt accounting or interruption remnants fail closed.
- **12 targeted/release tests passed** (7 new,5 existing), including actual Java lock/schema probes. No app/bundle rebuild; activate.py and its gates unchanged. Mock-only preparation is separable; existing activation and Gemini-selected conservative baseline still require accounting. Detailed prerequisites/command/interruption guidance: production-release.md Phase1.6.
- Production remains MOCK; real bootstrap, operator attestations and activation NOT performed. Global containment/trustworthy cutoff/full24h and longer restrictions, private provisioning, singleton/config/rollback/infrastructure acceptance remain operator requirements. No production/service/config/ledger mutation, live Gemini/orders or Git synchronization/commit/branch. Prior NO-GO remains in force until operator gates are met.

## Final read-only migration audit — 2026-11-09: NO-GO

- Both candidate/baseline reverified offline unchanged, no rebuild. Full status checklist and exact proposed operator sequence: [phase15-migration-readiness.md](phase15-migration-readiness.md); runbook updated. Conservative baseline remains READY for review, production migration NOT authorized.
- Host inspection confirms active PID299694/8080/UP, safe status MOCK. Running JVM holds deleted legacy JAR inode17844963 (SHA f1d7bc859df3b47f72772c1583d206be2efd519388a78181b522251170850a4e),156 classes/no GeminiRecoveryState. Current on-disk target is different; NEVER use either legacy runtime or target path as rollback baseline. Archive actual deleted inode before a later authorized stop. Sole accepted baseline is the named conservative bundle after immutable import.
- Legacy directory only zero-byte gemini.lock, no trustworthy accounting. Global singleton/scheduler containment and quiet-period T0/full rolling24h are unproven; approved post-quiet initialization still required. Current MOCK/old lock date do not establish project history. /opt/coinmind and /var/lib/coinmind absent; import/config/migration/real acceptance remain operator gates. No key/.env/process-environment/commandline inspection.
- Effective systemd fields/host process metadata and complete readable Nginx include tree checked: CoinMind80->8080 and PhiveGarage127.0.0.1:8090->8081 have separate roots/services. Privileged nginx validation BLOCKED (sudo password required); future shared reload requires both-site acceptance. No root config dump saved.
- SSH correction: host root-owned0777 symlink points to root-owned0644 vendor file; ssh-G parses successfully. Earlier nobody mapping is sandbox-specific; DO NOT chown/chmod the correct host include/target. Fix/avoid namespace mapping instead; remote authentication/main comparison remains unverified. Evidence /tmp/coinmind-final-readiness-evidence/. Documentation only; no service action, production mutation, credentials/live Gemini, Git sync/commit/branch or activation.

## Conservative rollback baseline acceptance — 2026-11-09 (READY for review, NOT deployed)

- Resolves the earlier isolated compatibility blocker: new conservative bundle `/tmp/coinmind-baseline-acceptance-zqwyj349/baseline` passed **22 packaged-process/persistence checks**, and **42 targeted Maven regressions** passed (8 suites). All 17 temporary JVMs stopped. Evidence/report and fixture snapshot in its parent directory; regression source/build `/tmp/coinmind-baseline-regression-87adful1`. Production JAR and prior candidate bundles preserved; fresh candidate not rebuilt.
- Read-only inspection: every ZIP entry's contents in configured production-path JAR and earlier staged JAR matches fresh candidate (all 236 app classes/dependencies). On-disk JAR is ledger-compatible; running JVM provenance/live state remains unverified. Conservative configuration-only repack selects Gemini for ledger locking but fixes AI/news/automatic AI disabled and keys blank. Legacy synthetic enable variables cannot enable it. Only embedded application.yml changes; frontend/deterministic bytecode unchanged.
- Baseline -> candidate -> baseline startup/shutdown/restart preserves existing synthetic ledger files byte-for-byte for OPEN, AUTH_ERROR, quota and HALF_OPEN; both lock directions and corrupt-ledger refusal passed. Candidate synthetic success survives baseline rollback with attempts retained; AUTH_ERROR remains blocking after 48h. Both apps return deterministic WATCH_BUY on mock data; Futures/live orders BLOCKED. Zero mock POSTs, no real credentials/live Gemini. Candidate tested with AI explicitly disabled; HTTP recovery validation remains mocked-regression/prior E2E evidence.
- **Baseline READY for operator review; production migration remains gated, not authorized**: remote/source reconciliation, effective runtime/config/singleton ownership, trustworthy actual ledger migration (or quiet-period/initialization), root-owned import, infrastructure config/DB/health/UI acceptance and truthful attestations. Require existing valid shared ledger and forbid higher-precedence configuration enabling baseline AI; missing accounting cannot be reconstructed. Full evidence, hashes and exact remaining checklist: production-release.md conservative acceptance section. No service/config/live-ledger/.env/SSH change, commit/pull/push/branch, order execution or production activation.

## Fresh release preparation audit — 2026-11-09 (NOT activated)

- Fresh unique stage `/tmp/coinmind-phase15-audit-20261109-r1`: offline Maven **185 passed**, Angular production build and **10 status cases passed**; default activate.py offline verification passed, no authorization. All **295 source hashes / 7 artifacts** verified; no excluded sensitive paths, ledger, missing index assets or E2E fixture. Four non-authorized release tests passed. Toolchain Java 25.0.4.1/Maven 3.9.9/explicit Node 22.23.2; 762 installed package versions match lockfile. Default Node 20 is unsuitable. Evidence `/tmp/coinmind-phase15-audit-20261109-evidence/`; detailed checklist and limitations in `production-release.md` fresh audit section.
- Existing research `.bak` is source-inventoried by broad staging but absent from deployable bundle; future curated release must decide its inclusion. All prior work/recovery retained. Handoff additions postdate staged snapshot. No application/tooling implementation changed.
- **Migration BLOCKED**: legacy main lacks persistence; earlier candidate's 236 application classes match fresh tested candidate, but no conservative operational baseline/actual migration compatibility acceptance is established. SSH include nobody:nogroup/0777 fails before authentication; administrator ownership/mode repair needed in correct host namespace, then read-only remote-main retry. No SSH changes, pull/push/commit/branch change, service action, production mutation, .env read or live Gemini call.

## Safe release preparation — 2026-11-09 (not deployed)

- `deploy.sh` now defaults to a no-mutation plan; explicit staging copies source into temporary storage, runs Maven verify and Angular production/status checks, and produces a versioned bundle with artifact/source hashes. Existing active Maven target, frontend, `.env`, ledgers and unrelated dirty work retained.
- Separate opt-in activation script requires root-owned bundles, compatible baseline, operator attestations, persistent `/var/lib/coinmind/ai-recovery` and migrated configuration. Shared `/opt/coinmind/current` selects backend/frontend together; stop-before-start and failed-activation rollback preserve the live ledger. No production command was run. Templates and detailed migration/rollback procedures: [production-release.md](production-release.md).
- New script tests **5 passed**; isolated staging Maven **185 passed**, Angular production build and **10 status cases passed**; offline Python **52 passed**. Dry-run candidate verification passed. Shell syntax/Python compilation/diff whitespace checks passed; ShellCheck unavailable. Staged candidate `/tmp/coinmind-release-phase15-candidate`, log `/tmp/coinmind-release-stage.log`. Standard PostgreSQL/browser isolated evidence retained from previous session; not rerun for release tooling.
- Blockers: remote main SSH permission failure; systemd bus unavailable inside sandbox; operator must establish single project/key owner, migrate/verify persistent state and compatible baseline, install reviewed configuration and perform infrastructure acceptance. Legacy in-memory/no-ledger migration requires rolling 24h quiet period and separate reviewed initialization. No commit/push/pull, deploy, active configuration mutation, service restart/reload, live Gemini/paid call or state reset.

## Phase 1.5 isolated E2E — verified 2026-11-09 (local main, not deployed)

- Full production Spring application exercised through loopback HTTP in test-only child JVMs with local Gemini/market WS mocks, dummy credentials, fake provider clock and temporary persistent ledgers. Actual 30-second scheduler initiated HALF_OPEN after 28.164s; delayed 8s Gemini did not stop WS-to-SSE updates or deterministic advisor HTTP (five paired checks 44–275ms). Real 503/cooldown/repeated backoff/validated 200/429 Retry-After/401/403, separate-process OPEN/quota/auth persistence, killed HALF_OPEN recovery and same-ledger process lock passed.
- Separate-ledger race hazard reproduced: another JVM using the same mock key but a different ledger can admit another request. Exactly one enabled instance/project/key and a persistent absolute ledger path remain required; no distributed quota guarantee. No live AI call, new provider, trading-rule change or order execution.
- Production Angular browser **18 checks passed**, widths 320/360/390/768/1024/1440, real HTTP state snapshots and polling, failed-fetch clearing, keyboard/aria disclosure, scoped axe zero violations. Found/fixed legacy mobile brand `width:100%` overriding badge reservation: scoped `width:auto` removes brand/status overlap and +60px document overflow. Final screenshots reviewed; all document widths equal viewport widths. This supersedes previous pending status-indicator browser acceptance (not whole-app accessibility certification).
- Final standard Maven clean verify **185 passed**; isolated PostgreSQL archive/CSV/transaction IT **5 passed** on a new temporary cluster, now stopped; Python offline **52 passed**; production Angular build passed (existing warnings), label helper **10 passed**. General Angular test target absent. Provider-selection tests now isolate ledger under `@TempDir`; test control fixture confirmed absent from final production JAR.
- Evidence, exact safety/configuration boundaries, repeat commands and proposed deployment sequence: `phase15-e2e-verification.md`. HTTP/browser artifacts `/tmp/coinmind-phase15-e2e-s6o2rgel/`; disposable DB artifacts `/tmp/coinmind-phase15-postgres-auttw65n/`. All child JVMs/servers and DB stopped. Main remains `9bf10ce`; remote comparison blocked by SSH configuration permissions. No branch, commit, push, deployment, unrelated deletion or production service restart. Previous edits retained.

## Recovery audit — 2026-11-09 (local main, not deployed)

- Follow-up audit reused all existing work; moved scheduled recovery subscription to boundedElastic so synchronized news reads and initial ledger I/O do not occupy Spring's shared scheduler. Added blocked-news-read/repeated-tick regression and adapted asynchronous tests. Full standard `mvn -f backend/pom.xml clean verify` passed outside sandbox: **185 tests**, zero failures/errors/skips. Python **52 passed**, Angular production build **passed** (existing template warnings), label helper **10 passed**. General Angular test target remains absent. Logs `/tmp/coinmind-phase15-audit-{final-maven,python,build}.log`.
- Full working-tree inventory, security review and actual work eligibility are recorded in `gemini-news-phase1.md`, follow-up audit. A failed batch of still-valid headlines can be retried; no empty heartbeat or durable per-article exactly-once guarantee. Global AI freshness is last validated response, not all-symbol coverage. Browser/mobile/desktop E2E acceptance and opt-in PostgreSQL IT remain pending. Remote main comparison still blocked by SSH configuration permissions. No live Gemini request, commit, push, deploy, branch switch or production restart; unrelated files retained.
- Reused existing Phase 1.5 implementation. Fixed earliest quota eligibility after reducing the daily cap: enough attempts must expire to fall below the new cap. Added persisted-ledger validation for unknown circuit/reason, negative failure count, excessive or unordered attempt history; invalid storage fails closed.
- New fake-clock restart regressions cover reduced quota and corrupted ledger/lock release. Frontend label regression includes legacy DEGRADED, displayed as “AI Tidak Tersedia”. Existing single-instance/persistent-ledger limitations and required-AI WAIT behavior remain mandatory.
- Final offline Maven verify with Mockito startup agent: **184 passed**; Python **52 passed**; frontend production build **passed** with existing template warnings; status helper **10 passed**. General Angular test target absent; browser visual acceptance and PostgreSQL IT not run. Remote main check blocked by local SSH configuration permissions. No live Gemini calls, commit, push, production restart or deployment.

## Latest increment — Gemini Phase 1.5 recovery/status (2026-11-06; local main, not deployed)

- Extends existing strict Gemini provider, cache, minute/day caps and news-service path. Scheduled bounded recovery uses real eligible fresh RSS work only; initial 503 cooldown five minutes, exponential jittered backoff capped one hour, single concurrent HALF_OPEN, recovery only after output validation. 429 Retry-After respected; 401/403 stop automatic attempts. No heartbeat, paid fallback or market-analysis wait.
- Durable attempt/circuit/status ledger with exclusive process lock; requires exactly ONE Gemini-enabled instance per project/key on a persistent local directory (`-Dcoinmind.gemini.state-dir=/absolute/path`, default `data/ai-recovery`). Not distributed coordination. Preserve ledger across restart/deploy. Migration from old in-memory caps requires waiting a rolling 24h after the last old-process attempt. Auth failure is latched for operator intervention. See `gemini-news-phase1.md` Phase 1.5 for limitations.
- Existing status API includes safe nested recovery state/reason/timestamps/eligibility/freshness. Sticky header polls every 30s: READY “AI Aktif”; ERROR/RATE_LIMITED “AI Sedang Mengalami Gangguan”; disabled/missing key “AI Belum Diaktifkan”; unverified/auth/mock/fetch error “AI Tidak Tersedia”. Accessible native disclosure gives freshness/times and deterministic/WAIT explanation. Mobile header reserves indicator space; browser visual acceptance pending.
- Optional live strategy discloses deterministic-only analysis when AI absent; `coinmind.ai.required-for-entry=true` forces WAIT with no entry/targets. Gemini remains news-classification-only, so required mode conservatively waits even after Gemini recovery. Existing deterministic confidence is unchanged; no invented AI confidence.
- Review validation: Maven offline clean verify with cached Mockito startup agent **182 passed**; Python **52 passed**; frontend production build and **9 status label cases passed**. Angular general test target absent; browser visual acceptance/PostgreSQL IT pending. Remote main verification blocked by local SSH configuration permissions. No live Gemini request, commit, push, branch switch, production restart or deployment. Preexisting uncommitted work preserved.

## Latest investigation — Gemini UNAVAILABLE diagnostics (2026-11-05; uncommitted, not deployed)

- Original generic UNAVAILABLE discarded transport/timeout/JSON/strict-validation errors. Existing local logs and latest 1000 backend journal lines do not identify the reported smoke failure. No confirmed request-schema/parser incompatibility; network/timeout, malformed/truncated/blocked or contract-invalid output remain hypotheses. RSS/health and Python success do not establish the backend Gemini path works.
- Provider now logs only bounded error category, HTTP status, validated model, article count and generated local correlation UUID. No credentials, URLs, headlines, bodies, exception messages or stack traces. Separate HTTP 400/401/403/429/5xx/other, transport, timeout, invalid JSON, validation, empty/oversized response and internal categories. Fixed loss of known HTTP status when discarding an error body fails; empty strings are explicitly EMPTY_RESPONSE.
- Strict validation, five-headline bound, 64 KiB response limit, no retry/fallback, rolling-day/minute caps, failed-attempt accounting and cache unchanged. Request serialization and realistic five-item responses covered by mocked tests; no live Gemini requests. Existing Phase 1, RSS, deterministic trading and Manifold-only policy preserved.
- Validation: full `mvn -f backend/pom.xml clean verify -DargLine=-javaagent:/home/phive/.m2/repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar` **BUILD SUCCESS, 170 passed**, including 16 Gemini provider tests. Plain requested clean verify was run first and hit existing sandbox Mockito attachment restrictions (plus an empty-response test mismatch corrected before the passing run). Python offline **52 passed**. Default suite excludes opt-in PostgreSQL ITs. Logs: `/tmp/coinmind-gemini-diagnosis-agent-verify.log`, `/tmp/coinmind-gemini-diagnosis-python.log`.
- Controlled single-attempt follow-up instructions and evidence limits: `docs/development/gemini-news-phase1.md`, UNAVAILABLE investigation section. Defer any attempt until the existing rolling-day budget is available; never restart to evade cap. Work remains on main; no commit, push, deployment or production restart.

## Latest fix — replay repository startup proxies (2026-11-05; uncommitted, not deployed)

- Removed `final` only from `FuturesPaperReplayArchive` and `FuturesPaperEquityArchive`. Boot 4.1.1 repository exception translation creates CGLIB proxies (`spring.aop.proxy-target-class` defaults true); both unconditional `@Repository` beans must be subclassable even with archival disabled. No `@Transactional` annotations caused this failure. The explicit `TransactionalOperator` / `R2dbcTransactionManager` summary-and-equity transaction, SQL, idempotency, validation and offline ordering are unchanged.
- Added `FuturesReplayContextTest`: real Boot auto-configuration and complete internal replay service dependency graph, archival disabled/enabled, asserting both CGLIB exception-translation proxies and a reactive transaction manager. Both tests reproduced the exact final-class startup error before the fix and pass afterward. Other eight repository classes are non-final; no other transactional/async/cache/retry/aspect annotations or custom proxy configuration found in production sources.
- Validation: Python offline **52 passed**. Plain `mvn -f backend/pom.xml clean verify` hit sandbox Mockito self-attachment restrictions (166 run, 2 failures/6 errors). Rerun with `-DargLine=-javaagent:/home/phive/.m2/repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar` **BUILD SUCCESS, 166 passed, zero failures/errors/skips**. PostgreSQL `*IT` tests are opt-in and outside the default Surefire suite; no isolated PostgreSQL database was used.
- Isolated packaged-jar smoke attempted on `127.0.0.1:18080`, persistence/AI/automatic AI/Gemini news/archive/job/news/push/billing disabled, credentials cleared, DB and external market endpoints redirected to loopback, SQL initialization and R2DBC health disabled. Replay bean creation succeeded, but sandbox socket restrictions prevented Netty binding (`newSocketStream(..): Operation not permitted`); HTTP health could not be verified. Test JVM stopped; deployed service untouched. Unsandboxed execution rejected by current permission policy. Logs: `/tmp/coinmind-replay-agent-verify.log`, `/tmp/coinmind-replay-python.log`, `/tmp/coinmind-replay-smoke.log`; exact smoke harness: `/tmp/coinmind-replay-smoke.py`.
- Remaining validation: rerun isolated loopback smoke where socket creation is permitted; optionally run existing PostgreSQL commit/rollback ITs against an explicitly disposable database. Existing Gemini work, Manifold-only policy, deterministic rules and data preserved. No commit, push, deployment, branch change or paid external API call. Local main remains `9bf10ce`; remote check blocked by SSH config ownership/permissions.

## Latest increment — Gemini News Intelligence Phase 1 (2026-11-05; uncommitted, not deployed)

- Explicit `AI_PROVIDER=gemini`, master `AI_ENABLED=true`, separate `GEMINI_NEWS_ENABLED=true`, and secured `GEMINI_API_KEY` enable live public headline classification only. Configurable model defaults to `gemini-3.5-flash-lite`, matching the user's manual HTTP 200 test and preexisting local offline-script edit. Application defaults remain OpenAI/disabled. Exactly one provider bean for configured, absent or unknown selection.
- Strict generateContent JSON/identity/classification/relevance validation, 64 KiB response limit, bounded timeouts, no redirects/retries/fallback; five headlines, one request/minute, at most ten per rolling day (may reduce via config). Exact article/content cache and in-flight deduplication, 15-minute maximum reuse, freshness checked on every access. Failures return `UNAVAILABLE`, never fake neutral sentiment. Credentials only in header/config, never result/log/URL.
- Record compatibility: qualitative impact -> importance 75/50/25/0; relevance never becomes trading confidence, confidence remains zero and horizon NONE. Completion-time `analyzedAt`; no historical backdating/first-seen claims. Gemini is excluded from the existing deterministic news modifier. Trading analysis/review and prediction interpretation return unavailable without network calls.
- Manifold remains the sole approved prediction-market provider. Runtime client is Manifold-only; legacy Polymarket-named interface/models/routes/config/frontend and old Gamma modifier checks require a separate migration, unchanged here. Older sections below are historical and do not authorize other prediction providers.
- Full design/mapping/audit and exact controlled loopback smoke-test environment: `docs/development/gemini-news-phase1.md`. Local limits reset on restart and cannot certify the Google project's free billing tier. No Gemini price/accounting figures invented.
- Validation: Python offline suite **52 passed**; `mvn -f backend/pom.xml clean verify` **BUILD SUCCESS, 164 tests passed**, including 17 new provider/selection/isolation/status tests. Mockito attachment required the approved run outside the sandbox. No live Gemini request, commit, push, deploy or production restart performed. Preexisting offline script edit/untracked files preserved. Futures production remains BLOCKED.

## Product state

CoinMind-AI adalah aplikasi crypto market intelligence dan trade-advisor / decision-support untuk:

- BTCUSDT
- ETHUSDT
- SOLUSDT

Decision Engine deterministic/multi-timeframe tetap menjadi sumber rekomendasi utama. AI News Intelligence hanya berfungsi sebagai bounded supporting signals dan tidak boleh menciptakan BUY/SELL secara independen.

Scope produk sudah diputuskan sebagai **advisor-only**. CoinMind tidak mengeksekusi order ke Binance/exchange. Output BUY/SELL/WATCH, entry, target, dan invalidation adalah rekomendasi yang harus dieksekusi manual oleh user di exchange.

Primary application views:

```text
Dashboard
Trade Decision
News
AI & Usage
AI Billing
```

## Current access

Public test URL:

```text
https://phive-server.tail597574.ts.net/
```

Private Tailscale URL:

```text
http://100.89.143.40/
```

Backend local:

```text
http://localhost:8080/
```

## Deployment workflow

Use the preparation-only `deploy.sh` and the separately authorized activation workflow in [production-release.md](production-release.md). The former git-pull/target-JAR/destructive frontend deployment commands are retired. Production is still on the existing configuration; one-time migration is pending explicit authorization. Never restart to reset Gemini state.

## Navigation / responsive shell

CoinMind menggunakan view-based application sidebar.

### Desktop

- persistent left sidebar, 252px
- tidak menggunakan horizontal section navigation lama
- klik menu mengganti view aplikasi, bukan melakukan smooth-scroll ke halaman panjang
- sidebar footer menampilkan selected market
- Dashboard berisi chart, market condition, hourly activity, Detail Market, technical/microstructure context
- Trade Decision berisi penjelasan lengkap Decision Engine
- News berisi news/sentiment dan AI News Intelligence
- AI & Usage berisi AI status, engine/model, token usage, estimasi biaya, evaluation, dan history

### Mobile

- sidebar berubah menjadi off-canvas drawer melalui hamburger
- memilih menu menutup drawer
- floating BTC/ETH/SOL selector tetap draggable dan edge-snapping
- header mobile ditargetkan sebagai satu row 64px
- status strip berada di row terpisah
- legacy sticky horizontal navigation sudah dihapus

View state menggunakan logical hash:

```text
#view=dashboard
#view=trade
#view=news
#view=ai
```

First-load logic menggunakan manual scroll restoration agar browser tidak meloncat ke chart/DOM anchor.

Latest mobile header hardening commit:

```text
937f6c6 fix: harden mobile header geometry
```

Visual acceptance setelah commit tersebut masih perlu dilakukan pada production build / browser nyata.

## Backend / Decision Engine state

Backend telah terbukti berjalan pada port 8080 dan endpoint trade setup merespons.

Trade actions:

```text
WAIT
WATCH_BUY
WATCH_SELL
BUY
SELL
```

Decision timeframes:

```text
1m  -> Execution
15m -> Short-term
1h  -> Decision
4h  -> Trend
1d  -> Context
```

Trade setup dapat memuat:

- confidence
- signal score
- risk level
- alignment score/label
- market regime
- market activity
- entry range
- invalidation
- target 1 / target 2
- risk/reward
- reasons
- warnings

Guardrail penting: alignment tinggi tidak otomatis berarti BUY/SELL. Jika setup minimum belum terpenuhi, engine tetap dapat menghasilkan WAIT/WATCH.

## Market / realtime state

Market source utama:

- Binance REST
- Binance WebSocket

Frontend menggunakan SSE/EventSource untuk:

- ticker
- candle
- order book

Frontend stale threshold saat ini sekitar 20 detik dengan reconnect cooldown sekitar 15 detik.

## News / AI state

News pipeline:

```text
RSS
 -> relevansi BTC/ETH/SOL
 -> deterministic sentiment
 -> persistence/history
 -> optional AI News Intelligence
```

Baseline RSS:

- CoinDesk
- Cointelegraph

AI News Intelligence menghasilkan:

- BULLISH / BEARISH / NEUTRAL
- importance
- confidence
- horizon
- summary
- catalysts
- risks
- per-article impact

AI News hanya bounded modifier untuk Decision Engine.

### Manifold prediction context

Only Manifold public search is supported. GET `/api/v1/manifold/{symbol}`, POST `/api/v1/manifold/{symbol}/refresh`, GET `/api/v1/manifold/source-health`. The existing binary/open/35-day parser, timeouts, cooldown and fresh-cache behavior are retained. No directional trade modifier: probabilities remain neutral context; 24h probability change is unavailable. Legacy endpoints and AI prediction interpretation/benchmark execution have been removed. See [historical archive](prediction-market-legacy-archive.md) for retired contracts.

## AI & Usage state

AI framework sekarang mendukung **cost-aware task routing**.

Important: AI provider live **belum aktif** sampai server memiliki API key dan `AI_ENABLED=true`. ChatGPT Plus/Codex subscription tidak digunakan oleh backend; CoinMind memakai API key dan API billing terpisah.

Task routing:

```text
Routine model
- News Intelligence

Deep model
- Manual/general market analysis
- AI Decision Review
```

Config:

```text
AI_PROVIDER
AI_MODEL
AI_ROUTINE_MODEL
AI_DEEP_MODEL
AI_API_KEY
AI_ENABLED
```

Default routine/deep model masih fallback ke `AI_MODEL`, sehingga model berbeda tidak wajib digunakan sebelum benchmark.

Existing cost controls:

- token usage tracking
- estimated cost tracking
- cached input token tracking
- hourly automatic-call cap
- daily automatic-call cap
- automatic signal-score guardrail
- route-specific pricing for routine/deep models
- `AI_ENABLED=false` is a hard master switch; paid provider methods return unavailable instead of calling the API

If routine/deep models use different prices, configure:

```text
AI_ROUTINE_INPUT_PRICE_MICROS_PER_MILLION
AI_ROUTINE_CACHED_INPUT_PRICE_MICROS_PER_MILLION
AI_ROUTINE_OUTPUT_PRICE_MICROS_PER_MILLION
AI_DEEP_INPUT_PRICE_MICROS_PER_MILLION
AI_DEEP_CACHED_INPUT_PRICE_MICROS_PER_MILLION
AI_DEEP_OUTPUT_PRICE_MICROS_PER_MILLION
```

### AI model benchmark

Current benchmark candidates (AI remains disabled by default):

```text
Routine: gpt-6-luna
Deep:    gpt-6.1-sol
```

Rationale:

- routine route is for high-volume structured classification/interpretation
- deep route is for Decision Review, general market reasoning, and high-materiality forecast context
- these are benchmark candidates, not a claim that the deep route is always superior for CoinMind

Current standard API pricing configured in the usage estimator:

```text
gpt-6-luna:
  input       $0.10 / 1M
  cached      $0.01 / 1M
  output      $0.50 / 1M

gpt-6.1-sol:
  input       $2.00 / 1M
  cached      $0.10 / 1M
  output      $10.00 / 1M
```

Manual benchmark endpoints:

```text
GET  /api/v1/ai/benchmark/config
POST /api/v1/ai/benchmark/forecast/{symbol}
```

GET is free and only reports configured routes.

POST requires `AI_ENABLED=true` and an API key and intentionally performs two paid calls against the same forecast context: routine then deep. The result reports latency, each structured analysis, model agreement, and whether each direction matches the deterministic forecast bias.

Benchmark is manual only; there is no scheduled benchmark job.

Frontend AI & Usage advanced details now shows the configured routine/deep models.

## AI Billing / Cost Center

Dedicated `AI Billing` view tracks CoinMind's own AI usage accounting:

- input tokens
- cached input tokens
- output tokens
- reasoning tokens
- estimated input cost
- estimated output cost
- total cost today / current month / lifetime
- monthly breakdown by model
- monthly breakdown by trigger/activity
- optional internal credit budget via `AI_CREDIT_BUDGET_USD`
- estimated remaining internal budget

Endpoint:

```text
GET /api/v1/ai/usage/cost-center
```

Important accounting rule:

- CoinMind calculates cost from token usage returned by the provider response and configured model pricing.
- `AI_CREDIT_BUDGET_USD` is only an internal reference budget.
- Estimated remaining budget is NOT the live OpenAI prepaid balance.
- OpenAI Billing remains authoritative for actual prepaid credit, grants, invoices and account-level spend.
- Usage persistence must be enabled for historical/lifetime accounting.

## Advisor-only UI state

Frontend sekarang secara eksplisit menampilkan CoinMind sebagai advisor:

- Dashboard quick decision menggunakan label `Rekomendasi`
- Trade view menggunakan eyebrow `TRADE ADVISOR`
- disclaimer advisor-only tampil di Trade Decision
- entry/target/invalidation dijelaskan sebagai reference/recommendation, bukan execution command
- footer Trade Decision menggunakan `CoinMind advisor engine`
- tidak ada tombol Place Order / Execute BUY / Execute SELL

Latest commits:

```text
1942cea ui: clarify CoinMind advisor-only trade recommendations
9eaede0 style: add advisor-only recommendation notice
43251c7 docs: define CoinMind as advisor-only platform
```

Frontend production build masih perlu dijalankan di server setelah pull terbaru.

### Real-server smoke-test finding

Production smoke tests on 2026-10-26 confirmed:

- actuator health = UP
- TradeSetup responds with live Binance-derived context
- forecast fallback source = MANIFOLD
- Manifold remains NEUTRAL/context-only with confidence 45
- AI provider = MOCK
- automatic AI = false
- monthly hard spend cap = 0
- AI usage/cost center = zero paid usage

A real output exposed one recommendation-semantics issue: WAIT could still display very high confidence because confidence was calculated independently from actionability. This has been fixed by capping recommendation confidence:

```text
WAIT       <= 60
WATCH_*    <= 80
BUY/SELL   <= 95
```

This keeps the displayed confidence aligned with the recommendation state.

## Known open issues / priorities

Urutan prioritas saat ini:

1. Validate Manifold-only migration using offline tests; production deployment requires separate instructions.

2. **Frontend shell visual validation**
   - production Angular build setelah commit `937f6c6`
   - cek mobile header 390px
   - cek desktop 1280/1440/1920px
   - cek sidebar, status strip, chart, long Trade Decision/AI content

3. **Frontend architecture cleanup**
   - `AppComponent` masih terlalu besar
   - `app.component.css` mengandung banyak legacy/incremental overrides
   - target split: AppShell, Dashboard, TradeDecision, News, AiUsage, MobileCoinSelector

4. **CSS cleanup**
   - obsolete CSS untuk horizontal section anchor / pinned desktop menu sudah dihapus dari tail stylesheet (commit `585f26f`)
   - masih ada technical debt besar karena `app.component.css` >10k baris dan banyak incremental responsive overrides
   - setelah visual baseline diterima, lanjut hapus rule obsolete secara bertahap; jangan menambah append-only override tanpa membersihkan konflik

5. **Testing**
   - E2E view switching
   - first-load top position
   - mobile drawer
   - responsive breakpoints
   - realtime disconnect/reconnect
   - external source degraded state

## Important frontend files

```text
frontend/src/app/app.component.ts
frontend/src/app/app.component.html
frontend/src/app/app.component.css
frontend/src/app/core/models/market.models.ts
frontend/src/app/core/services/market-api.service.ts
frontend/src/app/features/market-chart/
```

## Important backend areas

```text
backend/src/main/java/com/coinmind/manifold/
backend/src/main/resources/application.yml
```

Decision, market, news, AI, persistence modules should be inspected directly before modification.

## Synchronization rule

GitHub `main` adalah shared source of truth antara ChatGPT dan Codex.

Untuk perubahan bermakna:

1. inspect latest `main`
2. implement
3. validate relevant build/test
4. update `current-state.md`
5. append `session-log.md`
6. push/commit sesuai authorization

Jangan menggunakan chat history sebagai canonical implementation state.


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
