# CM-101 — Market Data Reliability (2026-11-14, local only)

Scope: local main 9bf10ce plus the pre-existing dirty working tree. No commit, push, reset or deployment. No production configuration, .env, Gemini recovery/ledger/quota/key, Manifold or RSS implementation changes.

## Audit and confirmed defects

- Production market path uses Binance Spot combined WebSockets: miniTicker, kline, bookTicker, aggTrade; REST /api/v3/klines supplies initial history. Configured symbols BTCUSDT/ETHUSDT/SOLUSDT; intervals 1m/5m/15m/1h/4h/1d. No synthetic market source was added. Tests use offline fixtures only.
- Existing sockets have a 30-second receive timeout and exponential retry (2–30 seconds), but graceful completion did not trigger retry. Connection/receipt alone did not establish payload freshness.
- Missing numeric fields silently defaulted to zero. Old/future exchange events could enter caches. Latest candle and same-open history could regress or reopen a closed candle.
- REST bootstrap had no request timeout, accepted malformed/partial or stale history, and ran only at startup. Missing candles after a long disconnect could persist indefinitely.
- Feed health used global receipt times; another symbol could mask a failed symbol/timeframe. Angular marked LIVE on any ticker/candle receipt using local time.
- Indicator calculation uses existing EMA/RSI/MACD/ATR/Bollinger/volume scoring; no strategy/formula changes. ContextBuilder previously required only ticker presence and 30 candles, allowing stale data into BUY/SELL scoring and multi-timeframe advisor decisions.

## Changes and bounded behavior

- Shared socket retry policy treats graceful close and errors as reconnectable, retaining jittered 2–30 second backoff, existing receive timeout and retry logging.
- Binance parsers reject missing numeric fields; ticker/kline/trade require fresh exchange timestamps (maximum 30 seconds old, maximum 5 seconds ahead). Kline checks complete metadata, positive coherent OHLC, nonnegative volumes/counts, configured symbols/intervals and future open times. Book quotes reject invalid price/quantity/spread.
- Latest caches reject older events/open times and closed-to-open regressions. History upsert/bootstrap keeps the newer observation and closed candle.
- REST fetch deadline is 10 seconds. Whole response must contain at least 30 valid ordered contiguous candles, no future open/event times and a fresh final observation. Errors/invalid 200s leave existing history untouched. Every 60 seconds only stale/incomplete history is retried, at most one request per configured series per pass (18 with current defaults). HTTP errors do not enter a rapid retry loop. REST repair never marks the WebSocket LIVE or supplies invented prices.
- ContextBuilder fails closed before scoring when ticker, history, order book or trades are missing/stale/invalid. No aggressive decision is returned; existing frontend request failure clears setup. Advisor-only and order execution behavior unchanged.
- Feed health adds exchange-time health per symbol/timeframe; missing streams explicitly UNAVAILABLE and overall DEGRADED. Existing global receipt fields remain available. Angular requires fresh selected ticker/candle plus UP health for LIVE; stale/missing data gets STALE/UNAVAILABLE/DEGRADED. Five-second watchdog ages observations even without new messages; freshness helper has offline tests.

## Regression coverage

BinanceReliabilityTest covers graceful/error reconnect/resubscribe, REST timeout (virtual time), HTTP failure, malformed HTTP 200, complete current REST recovery, stale/future/incomplete ticker rejection then valid recovery, out-of-order/closed candle protection, missing/stale/future/gapped history, simultaneous unavailable feeds blocking advisor scoring and per-symbol missing health despite another live symbol. Frontend test-market-data.mjs covers freshness boundaries, invalid dates, aging and recovery. Existing backend and frontend suites are also run. Final offline Maven clean verify: **205 passed**, zero failures/errors/skips, including **9 CM-101 regression methods**. Angular production build passed (existing template warnings); frontend freshness8, labels10, News41, Tutorial41 plus News request regressions passed. git diff --check passed. Logs: /tmp/coinmind-cm101-clean-verify.log and /tmp/coinmind-cm101-angular.log. Earlier overlapping Maven runs had artifact races and were discarded; final clean verification ran without another Maven process. Source/offline acceptance passes; end-to-end Sprint Definition of Done is not claimed before real connection and browser UAT.

## Limits and residual risks

- Binance Spot bookTicker has no exchange event timestamp: its eventTime remains local receipt time. REST current candle similarly has no event timestamp; existing representation uses fetch time for its observation and actual exchange open/close times. These are not claimed as exchange event provenance. Ticker/kline/aggTrade use Binance E timestamps.
- One provider (Binance) remains the source; simultaneous WS/REST outage intentionally removes advisor availability. REST repairs candles only, not ticker/order-book/trades. No alternate provider or simulated feed is introduced.
- Freshness assumes host clock accuracy; five-second future tolerance is explicit. UI watchdog/health polling has up to five-second observation delay. Historical cached prices can remain visible with a non-LIVE status.
- No real Binance network, real socket/browser UAT or upstream rate-limit behavior is verified by the offline suite. Reconnect tests exercise the actual shared reactive policy without opening sockets. Recovery gaps older than retained REST history may require more time/data; advisor stays unavailable while requested history is invalid.
- Global order-book/trade feed health still reports receipt freshness; advisor checks selected-symbol latest observations separately. No new connection-state tracking API was introduced.
