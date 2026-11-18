# CM-102 — Provider Health & Recovery

Local main only, not deployed. Existing dirty work retained. Scope: Manifold and RSS; no technical scoring, Binance/CM-101, Gemini recovery/ledger/quota/circuit/freshness or configuration changes.

## Health contract

`GET /api/v1/manifold/source-health` retains `sources`, `checkedAt` and existing source-status fields. `sources` now has BTCUSDT/ETHUSDT/SOLUSDT entries in that order, each with additive `symbol`. Additive `symbols` map supports direct lookup. `lastCheckedAt` is actual completed attempt time, not endpoint-read time; `lastSignalCount` is accepted signal count from that attempt. Initially UNKNOWN/null/0; valid search with relevant markets UP; valid search without relevant markets EMPTY; HTTP/transport/invalid JSON or non-array response UNAVAILABLE. Unsupported symbols do not mutate tracked health. Health reads never fetch.

Per-symbol 10-minute cache, 2-minute default failure cooldown and 5-minute scheduler remain. Successful EMPTY is cached. Failure retains stored cache but suppresses it from advisor/latestFresh; refresh/cooldown fallback is NONE/zero/empty, never a valid trading reason. A later successful attempt recovers that symbol independently. Manifold remains context only, with zero confidence adjustment.

`GET /api/v1/news/source-health` returns `sources` and `checkedAt`. Each source exposes safe publisher host, `state`, `lastCheckedAt`, `lastSuccessAt`, `error`, `articleCount`, `skippedInvalidDate`. No feed URLs, HTTP bodies, exception messages or credentials in errors. Definitions:

- UNKNOWN: no completed attempt (including disabled collector).
- UP: successful HTTP and valid RSS with at least one usable dated article; duplicate/historical valid articles still prove feed usability.
- EMPTY: successful HTTP and valid RSS without usable articles. Invalid/missing/future dates are skipped and counted, with `INVALID_PUBLICATION_DATE_SKIPPED` reason.
- UNAVAILABLE: transport/timeout/non-2xx/invalid XML or unsupported root. Safe errors: TIMEOUT, HTTP_nnn, FETCH_OR_XML_ERROR. Last successful fetch time survives failure; success includes valid EMPTY responses.

RSS connect deadline 5 seconds; response and total reactive fetch deadline 10 seconds. Existing redirects, headers, configured CoinDesk/Cointelegraph sources unchanged. Dates remain RFC1123; no new production data or fabricated timestamps. NewsService also rejects missing/future publication dates before replacing stored articles or publishing relevance events. Historical valid dates remain unchanged on invalid updates. Health controller only reads collector state and cannot trigger NewsService events/Gemini.

## Validation and limits

Offline WebClient exchange fixtures cover independent Manifold BTC UP/ETH EMPTY/SOL UNAVAILABLE, scheduler isolation, cached-success-to-failure suppression; advisor baseline equality for fallback confidence/reasons; RSS timeout, non-2xx, malformed XML/root, invalid/missing dates, historical publication preservation, both publisher feed formats, recovery, retained last-success and no-fetch/no-NewsService health endpoint. Full Maven acceptance and counts recorded in canonical handoff.

No live upstream acceptance performed. Upstream schema/feed changes and actual networking remain operational UAT. RSS/ RDF item parsing retained; Atom is not supported and reports UNAVAILABLE. Snapshot health is in-memory and resets to UNKNOWN on restart; it describes the latest completed attempt, not a separate freshness SLA. Clients assuming exactly one Manifold `sources` entry must migrate to symbol lookup. No production restart/deploy, live Gemini request, order, commit/push, .env/server configuration or ledger mutation. CM-103 not started.

## CM-102 changed files (relative to session start)

- backend/src/main/java/com/coinmind/manifold/model/PredictionSourceHealth.java
- backend/src/main/java/com/coinmind/manifold/service/ManifoldIntelligenceService.java
- backend/src/main/java/com/coinmind/news/api/NewsController.java
- backend/src/main/java/com/coinmind/news/exchange/RssNewsCollector.java
- backend/src/main/java/com/coinmind/news/service/NewsService.java
- backend/src/test/java/com/coinmind/manifold/ManifoldMigrationTest.java
- backend/src/test/java/com/coinmind/news/exchange/RssHealthTest.java
- backend/src/test/java/com/coinmind/news/service/NewsServiceTest.java
- backend/src/test/java/com/coinmind/trade/service/TradeSetupServiceTest.java
- docs/development/cm102-provider-health.md
- docs/development/current-state.md
- docs/development/session-log.md

Expected Manifold snapshot excerpt (timestamps illustrative):

```json
{"sources":[{"source":"MANIFOLD","symbol":"BTCUSDT","state":"UP","detail":"Public search responded","lastCheckedAt":"2026-11-16T09:00:00Z","lastSignalCount":1},{"source":"MANIFOLD","symbol":"ETHUSDT","state":"EMPTY","detail":"Public search responded","lastCheckedAt":"2026-11-16T09:00:01Z","lastSignalCount":0},{"source":"MANIFOLD","symbol":"SOLUSDT","state":"UNAVAILABLE","detail":"TimeoutException","lastCheckedAt":"2026-11-16T09:00:11Z","lastSignalCount":0}],"checkedAt":"2026-11-16T09:01:00Z","symbols":{"BTCUSDT":{"source":"MANIFOLD","symbol":"BTCUSDT","state":"UP","detail":"Public search responded","lastCheckedAt":"2026-11-16T09:00:00Z","lastSignalCount":1}}}
```

The actual `symbols` map includes all three symbols, matching `sources`.

Expected RSS source entry after a failed fetch following success:

```json
{"source":"cointelegraph.com","state":"UNAVAILABLE","lastCheckedAt":"2026-11-16T09:05:10Z","lastSuccessAt":"2026-11-16T09:00:00Z","error":"TIMEOUT","articleCount":0,"skippedInvalidDate":0}
```

Final acceptance: Maven offline clean verify **210 tests passed, 0 failures/errors/skips**. Manifold4, RSS3, News3, Trade Setup6, Binance reliability9; existing Gemini isolation/recovery/provider tests passed. Log `/tmp/coinmind-cm102-final-verify.log`. `git diff --check` passed. No Angular changes. CM-102 ready for release-candidate review, subject to listed live upstream/compatibility UAT.
