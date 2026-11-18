# Historical prediction-market implementation (superseded 2026-11-12)

Archive only. URLs, configuration and provider failover below are retired and must not be used operationally. Preserved as migration/audit evidence. Current runtime uses Manifold only.

### Prediction source health

Because Polymarket access is officially blocked in Indonesia, CoinMind does not attempt to bypass ISP/regulatory blocking.

A runtime source-health endpoint now exposes the last observed status of each prediction source:

```text
GET /api/v1/polymarket/source-health
```

Possible states:

```text
UNKNOWN
UP
EMPTY
UNAVAILABLE
```

Tracked sources:

```text
POLYMARKET_GAMMA
POLYMARKET_CLOB
MANIFOLD
```

The endpoint records the last check time, concise failure/reachability detail, and last relevant signal count. It does not trigger a new network request by itself; it reports the state captured during normal refreshes.

Important separation:

- Gamma/CLOB/Manifold = prediction-market / forecast context.
- CoinDesk/Cointelegraph RSS = news context.
- Polymarket Gamma is not a news API.

## Polymarket state

Polymarket adalah prediction-market intelligence, bukan sumber berita.

Market discovery sekarang memakai **multi-source failover**: Gamma `/events` sebagai primary, CLOB `/simplified-markets` sebagai secondary, lalu **Manifold public forecasting API** sebagai compliant third-source fallback jika Polymarket diblokir/unreachable. Frontend menampilkan source aktual agar provenance data tidak ambigu.

Endpoint:

```text
GET  /api/v1/polymarket/{symbol}
POST /api/v1/polymarket/{symbol}/refresh
```

Server pada 2026-10-21 terobservasi dapat resolve DNS `gamma-api.polymarket.com`, tetapi koneksi TCP/HTTPS dapat timeout.

Hardening yang sudah masuk `main`:

- Reactor Netty connect timeout default 5 detik
- response/operation timeout default 8 detik
- failure cooldown default 2 menit per symbol
- fallback antar-source: Gamma events -> CLOB simplified markets -> Manifold search markets
- source field ikut dikirim ke frontend (`POLYMARKET_GAMMA`, `POLYMARKET_CLOB`, `MANIFOLD`, `NONE`)
- fallback ke last cached result bila semua source gagal
- fallback ke neutral result bila cache belum ada
- timeout/error expected dicatat sebagai warning ringkas tanpa full stack trace
- failure Polymarket tidak boleh menjatuhkan backend atau Trade Decision

Config:

```text
POLYMARKET_BASE_URL
POLYMARKET_CLOB_BASE_URL
PREDICTION_MANIFOLD_BASE_URL
POLYMARKET_GAMMA_EVENT_LIMIT
POLYMARKET_CLOB_PAGE_LIMIT
POLYMARKET_CONNECT_TIMEOUT_MS
POLYMARKET_RESPONSE_TIMEOUT_MS
POLYMARKET_FAILURE_COOLDOWN_MS
```

Latest commits:

```text
accbf7a fix: harden Polymarket timeouts and fallback
2276883 config: add Polymarket resilience settings
```

Backend Maven build dan smoke test pada `phive-server` masih perlu dijalankan setelah pull commit ini.


### AI Polymarket Intelligence

Polymarket sekarang memiliki dua layer terpisah:

```text
Polymarket API
   ↓
Deterministic parser / scorer
   ↓
PolymarketIntelligence
   ↓
Materiality gate + cache + budget guardrail
   ↓ (hanya jika perlu)
AI Polymarket Intelligence
```

Automatic AI call hanya layak jika deterministic data dianggap material, default:

- absolute average probability change >= 3%
- atau importance/relevance >= 80
- atau materiality score >= 75

Materiality tinggi memilih `AI_DEEP_MODEL`; kondisi lain memilih `AI_ROUTINE_MODEL`.

AI input dibatasi ke structured data dan maksimum lima market teratas, bukan raw Polymarket payload penuh.

AI output:

- direction
- importance
- confidence
- horizon
- summary
- catalysts
- risks
- contradictions
- materiality score
- actual model used

Cache AI Polymarket default 60 menit. Repeated identical context menggunakan cache agar tidak membuang token.

Automatic Polymarket AI calls juga menggunakan `AiBudgetGuardrailService` sehingga tunduk pada hourly/daily limit.

Endpoints:

```text
GET  /api/v1/ai/polymarket-intelligence/{symbol}
POST /api/v1/ai/polymarket-intelligence/{symbol}
```

GET hanya membaca cache/fallback deterministic dan tidak memaksa paid call.
POST adalah manual analysis request.

Config:

```text
AI_POLYMARKET_ENABLED
AI_POLYMARKET_MIN_PROBABILITY_CHANGE
AI_POLYMARKET_MIN_IMPORTANCE
AI_POLYMARKET_DEEP_MATERIALITY_SCORE
AI_POLYMARKET_CACHE_MINUTES
```

`AiProvider` tetap menjadi abstraction layer, sehingga provider lain dapat ditambahkan untuk benchmark tanpa mengubah service/use-case layer.

AI Polymarket interpretation **belum menjadi modifier tambahan untuk Decision Engine**. Untuk sekarang ia ditampilkan sebagai advisor context/second opinion. Deterministic Polymarket modifier yang sudah dibatasi tetap menjadi satu-satunya Polymarket contribution ke Decision Engine sampai hasil AI layer dievaluasi.

Frontend Prediction Market panel sekarang menampilkan deterministic layer dan AI interpretation layer secara terpisah.


## Pre-live AI readiness audit (2026-10-26)

Paid AI must remain unconfigured until the server validates this increment.

Key safety changes from the audit:

- Manifold fallback is now restricted to markets closing within roughly one month and sorted by 24h volume.
- Manifold static probability is no longer converted into an automatic bullish/bearish trading signal.
- Only `POLYMARKET_GAMMA` may modify deterministic Trade Decision confidence; CLOB/Manifold remain context only.
- Automatic paid forecast AI is disabled for fallback forecast sources.
- `AI_AUTOMATIC_ENABLED=false` is now a separate master switch; adding an API key does not enable scheduled/event-triggered paid AI.
- `AI_MAX_MONTHLY_SPEND_USD=0` is a hard fail-closed default.
- Paid AI HTTP POSTs are blocked with HTTP 402 unless persistence is enabled and recorded monthly spend is below the configured hard cap.
- AI guardrail status now exposes automatic-enabled state and monthly hard spend cap.

Current safe first-live configuration should be:

```text
PERSISTENCE_ENABLED=true
AI_ENABLED=true
AI_PROVIDER=openai
AI_AUTOMATIC_ENABLED=false
AI_MAX_MONTHLY_SPEND_USD=<small deliberate amount>
AI_CREDIT_BUDGET_USD=<optional internal reference>
```

Then verify one manual call / benchmark before considering automatic AI.

Forecast-source policy:

```text
POLYMARKET_GAMMA -> decision-confidence modifier allowed (bounded)
POLYMARKET_CLOB  -> context only
MANIFOLD         -> short-horizon context only
NONE             -> neutral fallback
```

Manifold API documentation labels the API alpha; it is acceptable as a resilient context source for this project, but should not be treated as equivalent to Polymarket temporal signal quality.
