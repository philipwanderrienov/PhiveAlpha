# Conservative Manifold-only rollback baseline — 2026-11-12

## Scope and provenance

Preparation only; no production activation authorized. Imported source candidate: `/opt/coinmind/releases/candidate-manifold-only`. `/opt/coinmind/current` resolves to `releases/baseline-phase15`; this work does not modify it or either imported release. The user reports CoinMind and PhiveGarage active; no service action is part of this acceptance. Existing production ledger `/var/lib/coinmind/ai-recovery` is never read or used by this test.

New evidence directory: `/tmp/coinmind-baseline-acceptance-wup4qyla`. Baseline bundle: its `baseline/` directory. Final readiness and manifest hashes are recorded below only after all checks complete.

Candidate manifest schema1/ledger_protocol `phase15-v1` verified by activate.py in default offline mode. All 198 inventoried backend source/POM files match the current source. Full isolated regression build under `/tmp/coinmind-manifold-baseline-regression-z8lixz7o` passes 189 tests, zero failures/errors/skips; all 230 compiled application classes match the imported candidate byte-for-byte. The new baseline retains the same application classes and dependencies and the entire frontend, changing only `BOOT-INF/classes/application.yml` inside the JAR. No source recompilation is used to construct the baseline.

## Exact configuration derivation

| Property | Candidate embedded value | Baseline embedded value |
| --- | --- | --- |
| coinmind.ai.enabled | `${AI_ENABLED:false}` | `false` |
| coinmind.ai.provider | `${AI_PROVIDER:openai}` | `gemini` |
| coinmind.ai.gemini.news-enabled | `${GEMINI_NEWS_ENABLED:false}` | `false` |
| coinmind.ai.automatic-enabled | `${AI_AUTOMATIC_ENABLED:false}` | `false` |
| coinmind.ai.api-key | `${AI_API_KEY:}` | empty string |
| coinmind.ai.gemini.api-key | `${GEMINI_API_KEY:}` | empty string |

These are artifact-only placeholder changes, not changes to any actual credential or .env file. `GeminiRecoveryState` and Manifold classes remain identical. Gemini selection retains process-exclusive ledger locking even with master AI disabled. No quota/circuit/recovery/trading implementation or API key is changed. Legacy AI_ENABLED/GEMINI_NEWS_ENABLED/AI_AUTOMATIC_ENABLED variables are deliberately true in the baseline test child environment to verify the literal disabled policy wins over those placeholder variables. Candidate test JVMs are explicitly disabled and use blank keys; no live recovery HTTP request is tested here.

## Test boundaries and reproduction

`PYTHONDONTWRITEBYTECODE=1 python3 scripts/release/baseline_acceptance.py --candidate /opt/coinmind/releases/candidate-manifold-only --confirm SYNTHETIC_LEDGER_LOOPBACK_ONLY`

The script now allows read-only imported candidates, removes obsolete historical-JAR comparisons, verifies absent retired providers and actual retired-route404/source-health responses, and seals only its new staging output. Activation script/gates unchanged. All child JVMs use minimal environments, loopback-only mock dependencies, dynamically assigned loopback ports, disabled persistence/external news, blank keys and private synthetic ledgers under the evidence directory. No production service, database, .env, state, API call or operator attestation is involved. The first sandbox attempt could not create a socket; retained blocked evidence `/tmp/coinmind-baseline-acceptance-k0emt41n`. Approved host execution uses the same synthetic boundaries.

Logs: `/tmp/coinmind-manifold-baseline-acceptance-host.log`, `/tmp/coinmind-manifold-baseline-regression.log`, `/tmp/coinmind-manifold-baseline-release-tests.log`. Release/bootstrap tests: 12 passed. Regression command: `mvn -o -f /tmp/coinmind-manifold-baseline-regression-z8lixz7o/backend/pom.xml verify -DargLine=-javaagent:/home/phive/.m2/repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar`.

## Deployment limits

This accepts synthetic compatibility, not deployment authorization or current production-ledger correctness. No release import, symlink switch, systemd/Nginx/.env/key/attestation change, commit/push/reset or PhiveGarage action. Operator must separately review/import a root-owned, protected immutable release and audit higher-precedence Spring configuration (command-line, JVM/system environment or external config) so it cannot enable baseline AI or change provider/ledger path. Read-only modes in /tmp prevent routine writes; the staging owner/root can still change permissions, so SHA256 verification and protected production import remain required. Preserve the real ledger in place; do not initialize/reset it from synthetic evidence. Global singleton/project-key ownership and live infrastructure/rollback procedure remain operator responsibilities. Remote-main comparison in this turn failed due existing SSH include permissions; local main remains 9bf10ce. No browser/PostgreSQL process rerun or live Gemini/provider claim.

## Final result — READY FOR OPERATOR REVIEW, NOT DEPLOYED

New sealed bundle: `/tmp/coinmind-baseline-acceptance-wup4qyla/baseline`.

- **24 process/persistence checks passed**, all 17 temporary JVMs stopped. Report: `/tmp/coinmind-baseline-acceptance-wup4qyla/report.json`.
- Baseline → candidate → baseline actual startup/health/graceful-shutdown cycles preserve every existing synthetic ledger/evidence byte for OPEN, AUTH_ERROR, quota and recovered HALF_OPEN. Both lock directions reject a second JVM. Candidate direct packaged-state validated completion survives rollback with both attempts retained; quota2 admission stays blocked. This direct-state completion is not a live Gemini API response.
- Corrupt accounting refuses startup in both JARs without rewriting its contents. Zero mock POSTs. Both binaries deliver WATCH_BUY from250 mock candles and retain Futures BLOCKED/liveOrdersEnabled=false/futuresSignalsEnabled=false.
- Application classes/config/frontend scan contains no Polymarket/Kalshi/FinFeedAPI. Actual retired-route HTTP404 and Manifold-only source-health pass. Only application.yml ZIP entry differs; all230 application classes/dependencies and frontend artifacts retained.
- All **8 manifest artifacts** verified with schema1/phase15-v1 using unchanged activate.py default offline mode. Entire new bundle is read-only (files0444/directories0555); no root ownership or filesystem immutable-bit claim.
- Manifest SHA256: `78f86c861d93351d557d84c9dbf72c9c334b3d47ab3136879348ef33f461448a`.
- Baseline backend SHA256: `c8b496028b11b098f9f74b8b0920d8eea2c16977337a575dba75c3d9327e4b70`.
- Imported candidate hashes unchanged by harness; old imported baseline9-file inventory unchanged; current still `releases/baseline-phase15`. No production ledger or service access required.
- Release/bootstrap12 and isolated Maven189 tests passed; all230 regression application classes byte-identical to imported candidate. `git diff --check` passed. Harness snapshot and regression class-match evidence saved outside the sealed bundle.

Only repository changes for this preparation: `scripts/release/baseline_acceptance.py`, this acceptance note, and the current-state/session-log handoffs. Earlier uncommitted application work retained. No commit/push or production action performed.
