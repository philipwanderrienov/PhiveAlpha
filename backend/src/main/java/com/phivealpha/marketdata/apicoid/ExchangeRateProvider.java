package com.phivealpha.marketdata.apicoid;

import static com.phivealpha.marketdata.apicoid.ApiCoIdPayload.*;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;

public final class ExchangeRateProvider {
    private static final URI URI_USD_IDR = URI.create(ApiCoIdMacroCache.ORIGIN + "/currency/exchange-rate?pair=USDIDR");
    private final ApiCoIdMacroCache<String, MacroData.ExchangeRate> cache;

    ExchangeRateProvider(ApiCoIdTransport transport, ApiCoIdAccess access, Clock clock, Duration ttl,
                         Duration maxAge, ApiCoIdDiagnostics diagnostics) {
        cache = new ApiCoIdMacroCache<>(transport, access, ApiCoIdAccess.Dataset.USD_IDR, clock, ttl, maxAge, diagnostics);
    }

    public Mono<MacroData.Snapshot<MacroData.ExchangeRate>> usdIdr() {
        return cache.get("USDIDR", URI_USD_IDR, ExchangeRateProvider::parse);
    }

    private static ApiCoIdMacroCache.Value<MacroData.ExchangeRate> parse(JsonNode root, Instant fetchedAt) {
        var data = root.path("data");
        require("USDIDR".equals(text(data, "pair")), "PAIR_MISMATCH");
        var rate = nonnegative(data, "rate");
        require(rate.signum() > 0, "NONPOSITIVE_RATE");
        var updated = data.path("updated_at");
        require(updated.isIntegralNumber() && updated.canConvertToLong()
                && updated.asLong() >= 946684800000L, "INVALID_EPOCH_MILLIS");
        var asOf = Instant.ofEpochMilli(updated.asLong());
        require(!asOf.isAfter(fetchedAt), "FUTURE_AS_OF");
        return new ApiCoIdMacroCache.Value<>(new MacroData.ExchangeRate("USD", "IDR", rate),
                asOf, fetchedAt, "USDIDR-" + asOf.toEpochMilli());
    }
}
