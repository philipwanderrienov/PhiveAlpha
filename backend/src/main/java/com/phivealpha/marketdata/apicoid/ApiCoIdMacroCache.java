package com.phivealpha.marketdata.apicoid;

import static com.phivealpha.marketdata.apicoid.MacroData.Quality.*;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;

final class ApiCoIdMacroCache<K, T> {
    static final String ORIGIN = "https://use.api.co.id";
    private static final int MAX_ENTRIES = 32;
    private final LinkedHashMap<K, Value<T>> cache = new LinkedHashMap<>(16, 0.75f, true);
    private final Map<K, Mono<Value<T>>> pending = new HashMap<>();
    private final ApiCoIdTransport transport;
    private final ApiCoIdAccess access;
    private final ApiCoIdAccess.Dataset dataset;
    private final Clock clock;
    private final Duration ttl;
    private final Duration maxAge;
    private final ApiCoIdDiagnostics diagnostics;

    record Value<T>(T data, Instant asOf, Instant fetchedAt, String version) {}

    ApiCoIdMacroCache(ApiCoIdTransport transport, ApiCoIdAccess access, ApiCoIdAccess.Dataset dataset, Clock clock,
                      Duration ttl, Duration maxAge, ApiCoIdDiagnostics diagnostics) {
        if (ttl == null || ttl.isNegative() || ttl.isZero()
                || maxAge == null || maxAge.isNegative() || maxAge.isZero()) {
            throw new IllegalArgumentException("Invalid macro cache limits");
        }
        this.transport = transport;
        this.access = access;
        this.dataset = dataset;
        this.clock = clock;
        this.ttl = ttl;
        this.maxAge = maxAge;
        this.diagnostics = diagnostics;
    }

    Mono<MacroData.Snapshot<T>> get(K key, URI uri, BiFunction<JsonNode, Instant, Value<T>> parse) {
        return Mono.defer(() -> {
            if (access != null) {
                access.check(dataset);
            }
            return value(key, uri, parse).map(this::snapshot);
        }).onErrorResume(ApiCoIdFailure.class, failure -> Mono.just(failed(failure)))
                .doOnNext(snapshot -> {
                    var provenance = snapshot.provenance();
                    diagnostics.record(dataset, snapshot.quality().name(), provenance.asOf(), provenance.fetchedAt(),
                            provenance.asOf() == null ? provenance.fetchedAt() : provenance.asOf(),
                            provenance.version(), snapshot.data() != null);
                });
    }

    Mono<MacroData.Snapshot<T>> cachedVersion(K key, String version) {
        return Mono.fromSupplier(() -> {
            if (access != null) {
                access.check(dataset);
            }
            synchronized (this) {
                var value = cache.get(key);
                if (value != null && value.version().equals(version)
                        && clock.instant().isBefore(value.fetchedAt().plus(ttl))) {
                    return snapshot(value);
                }
            }
            return unavailable("CACHED_VERSION_UNAVAILABLE");
        }).onErrorResume(ApiCoIdFailure.class, failure -> Mono.just(failed(failure)));
    }

    private MacroData.Snapshot<T> unavailable(String reason) {
        return new MacroData.Snapshot<>("RESEARCH_ONLY", UNAVAILABLE, reason, null, provenance(null));
    }

    private MacroData.Snapshot<T> failed(ApiCoIdFailure failure) {
        return new MacroData.Snapshot<>("RESEARCH_ONLY", MacroData.Quality.valueOf(failure.quality.name()),
                failure.reason, null, provenance(null));
    }

    private synchronized Mono<Value<T>> value(K key, URI uri, BiFunction<JsonNode, Instant, Value<T>> parse) {
        var cached = cache.get(key);
        if (cached != null && clock.instant().isBefore(cached.fetchedAt().plus(ttl))) {
            return Mono.just(cached);
        }
        cache.remove(key);
        var existing = pending.get(key);
        if (existing != null) {
            return existing;
        }
        if (pending.size() >= MAX_ENTRIES) {
            return Mono.error(new ApiCoIdFailure(ApiCoIdData.Quality.UNAVAILABLE, "PENDING_LIMIT"));
        }
        var request = transport.get(dataset, uri).map(json -> {
                    try {
                        ApiCoIdPayload.require(json.path("is_success").isBoolean()
                                && json.path("is_success").asBoolean(), "UPSTREAM_ENVELOPE_REJECTED");
                        return parse.apply(json, clock.instant());
                    } catch (ApiCoIdFailure failure) {
                        throw failure;
                    } catch (RuntimeException invalid) {
                        throw new ApiCoIdFailure(ApiCoIdData.Quality.INCOMPLETE, "INVALID_PAYLOAD");
                    }
                }).timeout(Duration.ofSeconds(30))
                .onErrorMap(error -> !(error instanceof ApiCoIdFailure),
                        error -> new ApiCoIdFailure(ApiCoIdData.Quality.UNAVAILABLE, "OPERATION_UNAVAILABLE"))
                .doOnSuccess(value -> complete(key, value)).doOnError(error -> complete(key, null)).cache();
        pending.put(key, request);
        return request;
    }

    private synchronized void complete(K key, Value<T> value) {
        pending.remove(key);
        if (value != null) {
            cache.put(key, value);
            if (cache.size() > MAX_ENTRIES) {
                cache.pollFirstEntry();
            }
        }
    }

    private MacroData.Snapshot<T> snapshot(Value<T> value) {
        var anchor = value.asOf() == null ? value.fetchedAt() : value.asOf();
        boolean stale = Duration.between(anchor, clock.instant()).compareTo(maxAge) > 0;
        boolean verified = !stale && value.asOf() != null && access != null && access.verified(dataset);
        return new MacroData.Snapshot<>("RESEARCH_ONLY", stale ? STALE : verified ? VERIFIED : UNVERIFIED,
                stale ? "MAX_AGE_EXCEEDED" : verified ? "REVIEWED_CONTRACT_AND_ENTITLEMENT" : "PROVENANCE_UNVERIFIED",
                value.data(), provenance(value));
    }

    private MacroData.Provenance provenance(Value<T> value) {
        return new MacroData.Provenance("API.co.id", dataset.name(), value == null ? null : value.asOf(),
                value == null ? null : value.fetchedAt(), value == null ? null : value.version());
    }
}
