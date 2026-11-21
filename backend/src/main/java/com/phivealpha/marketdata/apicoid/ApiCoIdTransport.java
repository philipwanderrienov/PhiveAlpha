package com.phivealpha.marketdata.apicoid;

import static com.phivealpha.marketdata.apicoid.ApiCoIdData.Quality.INCOMPLETE;
import static com.phivealpha.marketdata.apicoid.ApiCoIdData.Quality.UNAVAILABLE;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

final class ApiCoIdTransport {
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build();

    private final WebClient client;
    private final ApiCoIdSettings settings;
    private final Clock clock;
    private final ApiCoIdRequestGate gate;
    private final ApiCoIdAccess access;
    private final ApiCoIdAuditWriter audit;

    private final Consumer<JsonNode> observeJson;

    ApiCoIdTransport(WebClient client, ApiCoIdSettings settings, Clock clock) {
        this(client, settings, clock, ignored -> {});
    }

    ApiCoIdTransport(WebClient client, ApiCoIdSettings settings, Clock clock, Consumer<JsonNode> observeJson) {
        this(client, settings, clock, observeJson, null);
    }

    ApiCoIdTransport(WebClient client, ApiCoIdSettings settings, Clock clock, Consumer<JsonNode> observeJson,
                     ApiCoIdAccess access) {
        this(client, settings, clock, observeJson, access, null);
    }

    ApiCoIdTransport(WebClient client, ApiCoIdSettings settings, Clock clock, Consumer<JsonNode> observeJson,
                     ApiCoIdAccess access, ApiCoIdAuditWriter audit) {
        this.audit = audit;
        this.access = access;
        this.observeJson = observeJson;
        this.client = client;
        this.settings = settings;
        this.clock = clock;
        this.gate = new ApiCoIdRequestGate(settings, clock);
    }

    Mono<JsonNode> get(ApiCoIdEndpoint endpoint, URI uri) {
        return get(datasetFor(endpoint, uri.getPath().equals(ApiCoIdEndpoint.NEWS.path)), uri);
    }

    static ApiCoIdAccess.Dataset datasetFor(ApiCoIdEndpoint endpoint, boolean marketNews) {
        return endpoint == ApiCoIdEndpoint.NEWS
                ? (marketNews ? ApiCoIdAccess.Dataset.MARKET_NEWS
                        : ApiCoIdAccess.Dataset.SYMBOL_NEWS)
                : ApiCoIdAccess.Dataset.valueOf(endpoint.name());
    }

    Mono<JsonNode> get(ApiCoIdAccess.Dataset dataset, URI uri) {
        Mono<JsonNode> request = access == null
                ? Mono.using(() -> new Attempt(gate.acquire(() -> null)), attempt -> execute(uri, attempt),
                        attempt -> finish(dataset, attempt))
                : Mono.usingWhen(Mono.fromCallable(() -> new Attempt(gate.acquire(() -> access.reserve(dataset))))
                            .subscribeOn(Schedulers.boundedElastic()),
                        attempt -> execute(uri, attempt),
                        attempt -> finishAsync(dataset, attempt),
                        (attempt, error) -> finishAsync(dataset, attempt),
                        attempt -> finishAsync(dataset, attempt));

        if (settings.maxRetries() == 0) {
            return request;
        }
        Duration delay = settings.retryDelay().compareTo(settings.minRequestInterval()) >= 0
                ? settings.retryDelay() : settings.minRequestInterval();
        return request.retryWhen(Retry.fixedDelay(settings.maxRetries(), delay)
                .filter(ApiCoIdTransport::retryable)
                .onRetryExhaustedThrow((spec, signal) -> signal.failure()));
    }

    private Mono<JsonNode> execute(URI uri, Attempt attempt) {
        return request(uri, attempt).timeout(settings.timeout())
                .onErrorMap(error -> {
                    if (error instanceof ApiCoIdFailure) {
                        return error;
                    }
                    if (error instanceof TimeoutException) {
                        return new ApiCoIdFailure(UNAVAILABLE, "TIMEOUT");
                    }
                    if (error instanceof WebClientRequestException) {
                        return new ApiCoIdFailure(UNAVAILABLE, "TRANSPORT_ERROR");
                    }
                    if (error instanceof JacksonException) {
                        return new ApiCoIdFailure(INCOMPLETE, "INVALID_JSON");
                    }
                    return new ApiCoIdFailure(UNAVAILABLE, "RESPONSE_ERROR");
                })
                .doOnError(ApiCoIdFailure.class, error -> {
                    attempt.outcome = error.reason;
                    attempt.transientError = retryable(error);
                })
                .doOnNext(ignored -> attempt.outcome = "OK");
    }

    private Mono<Void> finishAsync(ApiCoIdAccess.Dataset dataset, Attempt attempt) {
        return Mono.fromRunnable(() -> finish(dataset, attempt)).subscribeOn(Schedulers.boundedElastic())
                .then(Mono.defer(() -> audit == null || !access.metadataAllowed(dataset) ? Mono.empty()
                        : audit.record(attempt.ticket.reservation(), attempt.outcome,
                                access.configuredLimit(dataset.product))));

    }

    private void finish(ApiCoIdAccess.Dataset dataset, Attempt attempt) {
        try {
            if (access != null) {
                access.finish(attempt.ticket.reservation(), attempt.status, attempt.outcome, attempt.retryAfter,
                        settings.breakerCooldown());
            }
        } finally {
            gate.finish(attempt.ticket, dataset, attempt.status, attempt.outcome, attempt.transientError,
                    attempt.retryAfter);
        }
    }

    void check(ApiCoIdAccess.Dataset dataset) {
        if (access != null) {
            access.check(dataset);
        }
    }

    int remainingBudget() {
        return gate.remaining();
    }

    private Mono<JsonNode> request(URI uri, Attempt attempt) {
        return client.get().uri(uri).exchangeToMono(response -> {
            attempt.status = response.statusCode().value();
            if (response.statusCode().is2xxSuccessful()) {
                return response.bodyToMono(byte[].class).map(JSON::readTree).doOnNext(observeJson)
                        .switchIfEmpty(Mono.error(new ApiCoIdFailure(INCOMPLETE, "EMPTY_RESPONSE")));
            }
            String reason = switch (attempt.status) {
                case 401 -> "UNAUTHORIZED";
                case 402 -> "PAYMENT_REQUIRED";
                case 403 -> "FORBIDDEN";
                case 429 -> {
                    attempt.retryAfter = retryAfter(response.headers().asHttpHeaders().getFirst(HttpHeaders.RETRY_AFTER));
                    yield "UPSTREAM_RATE_LIMIT";
                }
                case 500, 502, 503, 504 -> "UPSTREAM_TRANSIENT";
                default -> attempt.status >= 500 ? "UPSTREAM_ERROR" : "HTTP_REJECTED";
            };
            return Mono.error(new ApiCoIdFailure(UNAVAILABLE, reason));
        });
    }

    private Instant retryAfter(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            if (value.matches("[0-9]+")) {
                return clock.instant().plusSeconds(Long.parseLong(value));
            }
            return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
        } catch (RuntimeException invalid) {
            // An unusable provider header falls back to the locally configured cooldown.
            return null;
        }
    }

    private static boolean retryable(Throwable error) {
        return error instanceof ApiCoIdFailure failure && switch (failure.reason) {
            case "UPSTREAM_TRANSIENT", "TIMEOUT", "TRANSPORT_ERROR" -> true;
            default -> false;
        };
    }

    private static final class Attempt {
        final ApiCoIdRequestGate.Ticket ticket;
        volatile int status;
        volatile String outcome = "CANCELLED";
        volatile boolean transientError;
        volatile Instant retryAfter;

        Attempt(ApiCoIdRequestGate.Ticket ticket) {
            this.ticket = ticket;
        }
    }
}
