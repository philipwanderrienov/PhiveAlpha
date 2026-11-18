# Manifold-only migration — 2026-11-12

Local main and remote main both inspected at 9bf10ce. Existing dirty work retained. No commit/push, branch switch, release preparation/activation, production service/config mutation, real provider request or .env inspection. Standard Maven/Angular validation outputs are local build artifacts only.

## Behavior and compatibility

The existing Manifold public-search parser, binary/unresolved/next35days filters, symbol normalization, top8 volume ordering, neutral context, timeouts, cooldown, freshness and scheduler are preserved under com.coinmind.manifold. Only MANIFOLD/NONE provenance remains in the frontend contract. GET /api/v1/manifold/{symbol}, POST /api/v1/manifold/{symbol}/refresh, GET /api/v1/manifold/source-health replace the old routes; no alias remains.

Deleted AI prediction interpretation, materiality cache/scheduler, forecast benchmark execution and all provider prediction methods. The unrelated GET /api/v1/ai/benchmark/config remains because existing model-routing UI consumes it. AI news, market analysis and decision review remain. Gemini changes remove only the unsupported prediction interface stub and obsolete test assertion; recovery/ledger/quota/circuit/key behavior unchanged.

Removed unreachable Gamma-specific confidence +/-1/3 and opposition warnings; no weights redistributed. Technical scoring, AI news modifier, risk and confidence caps unchanged. Manifold remains neutral context; original probability-change zero sentinel is retained in its existing wire model, but UI/reasons explicitly say unavailable instead of presenting a measured 24h change. No replacement directional signal, AI forecast or provider was invented. Advisor-only and Futures production BLOCKED remain.

Risks: intentional API break for external consumers of old prediction/AI forecast routes; frontend and backend must eventually be deployed together under separate authorization. Existing disabled/mock production stays untouched. Manifold upstream availability is not certified by synthetic tests. Browser visual and opt-in PostgreSQL process tests were not rerun; responsive grid rules retained and production Angular compilation checked. No dedicated Polymarket dependency existed in pom.xml/package.json.

## Remaining retired-provider references

- ManifoldMigrationTest.java: negative legacy-route checks and production source prohibition guard, never an upstream HTTP request.
- AGENTS.md: explicit architectural prohibition on retired providers.
- session-log.md: chronological audit/migration evidence retained.
- prediction-market-legacy-archive.md: retired endpoints/config/provenance preserved and prominently marked non-operational.
- current-state.md: migration summary and dated older snapshots, superseded by the top migration update.
- gemini-news-phase1.md, coinapi-adapter-validation.md, futures-intraday-roadmap.md: earlier incomplete-migration observations/tasks, now headed by an explicit superseding update.
- Git history and ignored prior build/cache artifacts are not rewritten. .env secrets were not read, changed or inventoried.

## Files touched in this task

This list excludes unrelated changes already present when the session began. Deleted paths are identified; Manifold files replace the former package.

- `.env.example`
- `backend/src/main/java/com/coinmind/ai/api/AiBenchmarkController.java`
- `backend/src/main/java/com/coinmind/ai/api/AiPolymarketIntelligenceController.java` — deleted
- `backend/src/main/java/com/coinmind/ai/config/AiProperties.java`
- `backend/src/main/java/com/coinmind/ai/model/AiPolymarketBenchmarkResult.java` — deleted
- `backend/src/main/java/com/coinmind/ai/model/AiPolymarketIntelligence.java` — deleted
- `backend/src/main/java/com/coinmind/ai/provider/AiProvider.java`
- `backend/src/main/java/com/coinmind/ai/provider/MockAiProvider.java`
- `backend/src/main/java/com/coinmind/ai/provider/NoOpAiProvider.java`
- `backend/src/main/java/com/coinmind/ai/provider/OpenAiLunaProvider.java`
- `backend/src/main/java/com/coinmind/ai/provider/GeminiAiProvider.java`
- `backend/src/main/java/com/coinmind/ai/service/AiPolymarketBenchmarkService.java` — deleted
- `backend/src/main/java/com/coinmind/ai/service/AiPolymarketIntelligenceService.java` — deleted
- `backend/src/main/java/com/coinmind/polymarket/api/PolymarketController.java` — deleted
- `backend/src/main/java/com/coinmind/polymarket/model/PolymarketIntelligence.java` — deleted
- `backend/src/main/java/com/coinmind/polymarket/model/PredictionSourceHealth.java` — deleted
- `backend/src/main/java/com/coinmind/polymarket/service/PolymarketIntelligenceService.java` — deleted
- `backend/src/main/java/com/coinmind/trade/service/TradeSetupService.java`
- `backend/src/main/resources/application.yml`
- `backend/src/test/java/com/coinmind/ai/provider/GeminiAiProviderTest.java`
- `backend/src/test/java/com/coinmind/ai/provider/GeminiRecoveryE2eApplication.java`
- `backend/src/test/java/com/coinmind/trade/service/TradeSetupServiceTest.java`
- `frontend/src/app/app.component.ts`
- `frontend/src/app/app.component.html`
- `frontend/src/app/app.component.css`
- `frontend/src/app/core/models/market.models.ts`
- `frontend/src/app/core/services/market-api.service.ts`
- `scripts/release/baseline_acceptance.py`
- `docs/development/current-state.md`
- `docs/development/session-log.md`
- `docs/development/coinapi-adapter-validation.md`
- `docs/development/futures-intraday-roadmap.md`
- `docs/development/gemini-news-phase1.md`
- `docs/development/prediction-market-legacy-archive.md`
- `docs/development/manifold-only-migration.md`
- `backend/src/main/java/com/coinmind/manifold/api/ManifoldController.java`
- `backend/src/main/java/com/coinmind/manifold/model/ManifoldIntelligence.java`
- `backend/src/main/java/com/coinmind/manifold/model/PredictionSourceHealth.java`
- `backend/src/main/java/com/coinmind/manifold/service/ManifoldIntelligenceService.java`
- `backend/src/test/java/com/coinmind/manifold/ManifoldMigrationTest.java`

## Final validation

- Maven: `mvn -o -f backend/pom.xml clean verify -DargLine=-javaagent:/home/phive/.m2/repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar` — BUILD SUCCESS, 189 tests, zero failures/errors/skips. Log /tmp/coinmind-manifold-verify-complete.log. Startup agent avoids environment-dependent dynamic attach. Includes three Manifold migration tests, technical confidence/risk regression, Gemini recovery/persistence and Futures safety regressions. Earlier compile/test failures during migration were corrected before this clean run; the initial Manifold fixture omitted its mock baseUrl, corrected without changing production transport.
- Angular production build (Node22.23.2): passed, existing NG8102 warnings. Log /tmp/coinmind-manifold-angular.log.
- AI status labels: 10 passed. Release/bootstrap unit tests: 12 passed (/tmp/coinmind-manifold-release-tests.log). Offline research: 52 passed (/tmp/coinmind-manifold-offline-tests.log), mocks/dry-run only.
- Source scan: no retired provider references in operational Java/Angular/config/example environment/scripts/dependencies/workflow. Production JAR classes/constants/routes and Angular assets contain no Polymarket; Manifold service present. git diff --check passed.
- No new production release bundle built or activated. No real upstream availability/browser/PostgreSQL process acceptance claimed.
