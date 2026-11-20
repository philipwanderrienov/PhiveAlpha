package com.phivealpha.marketdata.apicoid;

import static com.phivealpha.marketdata.apicoid.ApiCoIdData.*;
import static com.phivealpha.marketdata.apicoid.ApiCoIdPayload.*;
import static com.phivealpha.persistence.IdxMarketCalendar.ZONE;

import com.phivealpha.marketdata.ApiCoIdProvider;
import com.phivealpha.marketdata.IdxQuote;
import com.phivealpha.newsintelligence.IdxHeadline;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;

public final class ApiCoIdHttpProvider implements ApiCoIdProvider {
    private static final Logger LOG = LoggerFactory.getLogger(ApiCoIdHttpProvider.class);

    private record Request(ApiCoIdEndpoint endpoint, String symbol, LocalDate from, LocalDate to) {}
    private record Batch(List<?> rows, Instant asOf, Instant ageAnchor, Instant completedAt) {}

    private static final class Pages {
        final Map<String, Object> rows = new LinkedHashMap<>();
        final Set<String> cursors = new HashSet<>();
        Instant asOf;
        Instant ageAnchor;
        boolean missingAsOf;
    }

    private final ApiCoIdSettings settings;
    private final ApiCoIdTransport transport;
    private final Clock clock;
    private final VersionedIdxCalendar calendar;
    private final LinkedHashMap<Request, Batch> cache = new LinkedHashMap<>(16, 0.75f, true);
    private final Map<Request, Mono<Batch>> inFlight = new LinkedHashMap<>();

    ApiCoIdHttpProvider(ApiCoIdSettings settings, ApiCoIdTransport transport, Clock clock,
                        VersionedIdxCalendar calendar) {
        this.settings = settings;
        this.transport = transport;
        this.clock = clock;
        this.calendar = calendar;
    }

    // Legacy UI explicitly describes synthetic inputs and requires a comparable weekly reference.
    // Keep unverified contract data available only through the typed, internal dataset methods.
    @Override
    public Flux<IdxQuote> quotes() { return Flux.error(legacyUnavailable()); }

    @Override
    public Mono<IdxQuote> quote(String symbol) { return Mono.error(legacyUnavailable()); }

    @Override
    public Flux<IdxHeadline> headlines() { return Flux.error(legacyUnavailable()); }

    private static ResponseStatusException legacyUnavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "UNVERIFIED");
    }

    public Mono<Snapshot<Instrument>> instrumentMaster() {
        return dataset(new Request(ApiCoIdEndpoint.INSTRUMENTS, null, null, null), Instrument.class);
    }

    public Mono<Snapshot<Quote>> marketQuote(String symbol) {
        return dataset(new Request(ApiCoIdEndpoint.QUOTE, symbol(symbol), null, null), Quote.class);
    }

    public Mono<Snapshot<DailyBar>> dailyOhlcv(String symbol, LocalDate from, LocalDate to) {
        return dataset(new Request(ApiCoIdEndpoint.DAILY, symbol(symbol), from, to), DailyBar.class);
    }

    public Mono<Snapshot<News>> marketNews() {
        return dataset(new Request(ApiCoIdEndpoint.NEWS, null, null, null), News.class);
    }

    public Mono<Snapshot<News>> symbolNews(String symbol) {
        return dataset(new Request(ApiCoIdEndpoint.NEWS, symbol(symbol), null, null), News.class);
    }

    public Mono<Snapshot<CorporateAction>> corporateActions(String symbol) {
        return dataset(new Request(ApiCoIdEndpoint.CORPORATE_ACTIONS, symbol(symbol), null, null), CorporateAction.class);
    }

    public Mono<Snapshot<ForeignFlow>> foreignFlow(String symbol, LocalDate from, LocalDate to) {
        return dataset(new Request(ApiCoIdEndpoint.FOREIGN_FLOW, symbol(symbol), from, to), ForeignFlow.class);
    }

    public Mono<Snapshot<BrokerBar>> brokerFlow(String symbol, LocalDate session) {
        return dataset(new Request(ApiCoIdEndpoint.BROKER_FLOW, symbol(symbol), session, session), BrokerBar.class);
    }

    public int remainingRequestBudget() {
        return transport.remainingBudget();
    }

    private <T> Mono<Snapshot<T>> dataset(Request request, Class<T> type) {
        return Mono.defer(() -> {
            preflight(request);
            return cached(request).map(batch -> snapshot(request, type, batch));
        }).onErrorResume(ApiCoIdFailure.class, error ->
                Mono.just(new Snapshot<>(error.quality, error.reason, List.<T>of(), provenance(request, null))))
          .doOnNext(result -> LOG.info("API.co.id quality dataset={} quality={} reason={} rows={} asOf={} fetchedAt={}",
                  request.endpoint(), result.quality(), result.reason(), result.rows().size(),
                  result.provenance().asOf(), result.provenance().fetchedAt()));
    }

    private void preflight(Request request) {
        switch (request.endpoint()) {
            case INSTRUMENTS -> {
                if (settings.expectedUniverse().isEmpty()) {
                    throw new ApiCoIdFailure(Quality.UNVERIFIED, "MISSING_UNIVERSE_REFERENCE");
                }
            }
            case DAILY, FOREIGN_FLOW, BROKER_FLOW -> {
                Instant now = clock.instant();
                if (!calendar.covers(request.from(), request.to())
                        || request.to().isAfter(now.atZone(ZONE).toLocalDate())
                        || (calendar.isTradingSession(request.to()) && !calendar.isClosedBy(request.to(), now))
                        || (request.endpoint() == ApiCoIdEndpoint.BROKER_FLOW
                                && !calendar.isClosedBy(request.to(), now))) {
                    throw new ApiCoIdFailure(Quality.UNVERIFIED, "CALENDAR_COVERAGE_OR_SESSION");
                }
                if (request.from().datesUntil(request.to().plusDays(1)).noneMatch(calendar::isTradingSession)) {
                    throw new ApiCoIdFailure(Quality.UNVERIFIED, "NO_TRADING_SESSIONS");
                }
            }
            default -> { }
        }
    }

    private synchronized Mono<Batch> cached(Request request) {
        var now = clock.instant();
        Batch stored = cache.get(request);
        if (stored != null && now.isBefore(stored.completedAt().plus(settings.cacheTtl()))) {
            return Mono.just(stored);
        }
        cache.remove(request);
        Mono<Batch> pending = inFlight.get(request);
        if (pending != null) {
            return pending;
        }
        if (inFlight.size() >= settings.cacheEntries()) {
            return Mono.error(new ApiCoIdFailure(Quality.UNAVAILABLE, "PENDING_LIMIT"));
        }
        var load = Mono.defer(() -> {
            Pages pages = new Pages();
            return page(request, pages, null, 1)
                    .then(Mono.fromSupplier(() -> complete(request, pages)));
        }).timeout(settings.operationTimeout())
                .onErrorMap(TimeoutException.class, error ->
                        new ApiCoIdFailure(Quality.UNAVAILABLE, "OPERATION_TIMEOUT"))
                .onErrorMap(error -> !(error instanceof ApiCoIdFailure),
                        error -> new ApiCoIdFailure(Quality.INCOMPLETE, "INVALID_PAYLOAD"))
                .doOnSuccess(batch -> finish(request, batch))
                .doOnError(error -> finish(request, null))
                .cache();
        inFlight.put(request, load);
        return load;
    }

    private synchronized void finish(Request request, Batch batch) {
        if (batch != null) {
            cache.put(request, batch);
            if (cache.size() > settings.cacheEntries()) {
                cache.pollFirstEntry();
            }
        }
        inFlight.remove(request);
    }

    private Mono<Void> page(Request request, Pages pages, String cursor, int number) {
        return transport.get(request.endpoint(), uri(request, cursor)).flatMap(json -> {
            require(json.path("is_success").isBoolean() && json.path("is_success").asBoolean(),
                    "UPSTREAM_ENVELOPE_REJECTED");
            var wrapper = json.path("data");
            var meta = wrapper.path("meta");
            var data = wrapper.path("data");
            boolean single = request.endpoint() == ApiCoIdEndpoint.QUOTE;
            require(meta.isObject() && (single ? data.isObject() : data.isArray()), "INVALID_ENVELOPE");
            JsonNode count = meta.path("count");
            require(count.isIntegralNumber() && count.canConvertToInt()
                    && count.asInt() == (single ? 1 : data.size()), "PAGE_COUNT_MISMATCH");
            var fetchedAt = clock.instant();
            Instant pageAsOf = null;
            if (absent(meta.path("as_of"))) {
                pages.missingAsOf = true;
            } else {
                pageAsOf = instant(meta, "as_of");
                require(!pageAsOf.isAfter(fetchedAt), "FUTURE_AS_OF");
                pages.asOf = older(pages.asOf, pageAsOf);
                pages.ageAnchor = older(pages.ageAnchor, pageAsOf);
            }
            if (!absent(meta.path("stale_seconds"))) {
                var reportedAge = nonnegative(meta, "stale_seconds").longValueExact();
                pages.ageAnchor = older(pages.ageAnchor, fetchedAt.minusSeconds(reportedAge));
            }
            if (single) {
                append(request, pages, wrapper, data, fetchedAt, pageAsOf);
            } else {
                for (JsonNode row : data) {
                    append(request, pages, wrapper, row, fetchedAt, pageAsOf);
                }
            }
            JsonNode next = meta.path("cursor");
            if (single || request.endpoint() == ApiCoIdEndpoint.CORPORATE_ACTIONS) {
                require(next.isMissingNode() || next.isNull(), "UNEXPECTED_CURSOR");
                return Mono.empty();
            }
            require(!next.isMissingNode(), "CURSOR_TERMINATION_UNVERIFIED");
            if (next.isNull()) {
                return Mono.empty();
            }
            require(next.isString(), "INVALID_CURSOR");
            String token = next.asString();
            require(!token.isBlank() && token.length() <= 2048 && token.chars().noneMatch(Character::isISOControl),
                    "INVALID_CURSOR");
            require(data.size() > 0 && pages.cursors.add(token), "PAGINATION_LOOP");
            require(number < settings.maxPages() && pages.rows.size() < settings.maxRows(), "PAGINATION_LIMIT");
            return Mono.delay(settings.minRequestInterval())
                    .then(Mono.defer(() -> page(request, pages, token, number + 1)));
        });
    }

    private void append(Request request, Pages pages, JsonNode wrapper, JsonNode row, Instant fetchedAt,
                        Instant pageAsOf) {
        var item = normalize(request.endpoint(), request.symbol(), wrapper, row, fetchedAt, calendar);
        if (item.value() instanceof Quote quote && pageAsOf != null) {
            require(!quote.tradingDate().isAfter(pageAsOf.atZone(ZONE).toLocalDate()), "QUOTE_AFTER_AS_OF");
        }
        LocalDate date = switch (item.value()) {
            case DailyBar bar -> bar.tradingDate();
            case ForeignFlow flow -> flow.tradingDate();
            case BrokerBar bar -> bar.tradingDate();
            default -> null;
        };
        if (date != null) {
            require(!date.isBefore(request.from()) && !date.isAfter(request.to()), "OUTSIDE_REQUESTED_RANGE");
            require(pageAsOf != null && calendar.isClosedBy(date, pageAsOf), "SETTLEMENT_UNVERIFIED");
        }
        require(pages.rows.size() < settings.maxRows(), "ROW_LIMIT");
        require(pages.rows.putIfAbsent(item.key(), item.value()) == null, "DUPLICATE_ROW_OR_PAGE");
    }

    private Batch complete(Request request, Pages pages) {
        if (request.endpoint() == ApiCoIdEndpoint.INSTRUMENTS) {
            require(pages.rows.keySet().equals(settings.expectedUniverse()), "UNIVERSE_MISMATCH");
        }
        if (request.endpoint() == ApiCoIdEndpoint.DAILY || request.endpoint() == ApiCoIdEndpoint.FOREIGN_FLOW) {
            require(request.from().datesUntil(request.to().plusDays(1)).filter(calendar::isTradingSession)
                    .allMatch(date -> pages.rows.containsKey(date.toString())), "MISSING_TRADING_SESSIONS");
        }
        if (request.endpoint() == ApiCoIdEndpoint.BROKER_FLOW) {
            require(!pages.rows.isEmpty(), "MISSING_BROKER_ROWS");
        }
        return new Batch(List.copyOf(pages.rows.values()), pages.missingAsOf ? null : pages.asOf,
                pages.ageAnchor, clock.instant());
    }

    private <T> Snapshot<T> snapshot(Request request, Class<T> type, Batch batch) {
        Instant now = clock.instant();
        Duration maxAge = settings.maxAge().get(request.endpoint());
        Quality quality = Quality.UNVERIFIED;
        String reason = "CANARY_CONTRACT_UNVERIFIED";
        if (maxAge != null && batch.ageAnchor() != null
                && Duration.between(batch.ageAnchor(), now).compareTo(maxAge) > 0) {
            quality = Quality.STALE;
            reason = "MAX_AGE_EXCEEDED";
        } else if (request.endpoint() == ApiCoIdEndpoint.QUOTE && !batch.rows().isEmpty()) {
            Quote quote = (Quote) batch.rows().getFirst();
            if (!calendar.covers(quote.tradingDate(), now.atZone(ZONE).toLocalDate())) {
                reason = "CALENDAR_COVERAGE_OR_SESSION";
            } else if (calendar.hasNewerSession(quote.tradingDate(), now)) {
                quality = Quality.STALE;
                reason = "NEWER_TRADING_SESSION";
            }
        }
        if (quality == Quality.UNVERIFIED && "CANARY_CONTRACT_UNVERIFIED".equals(reason)) {
            if (batch.asOf() == null) {
                reason = "MISSING_AS_OF";
            } else if (maxAge == null) {
                reason = "FRESHNESS_POLICY_UNVERIFIED";
            }
        }
        return new Snapshot<>(quality, reason, batch.rows().stream().map(type::cast).toList(),
                provenance(request, batch));
    }

    private Provenance provenance(Request request, Batch batch) {
        String universe = request.endpoint() == ApiCoIdEndpoint.INSTRUMENTS ? settings.universeVersion() : null;
        return new Provenance("API.co.id", request.endpoint().name(), batch == null ? null : batch.asOf(),
                batch == null ? null : batch.completedAt(), calendar.source(), calendar.version(), universe,
                batch == null ? null : batch.ageAnchor());
    }

    private URI uri(Request request, String cursor) {
        var builder = UriComponentsBuilder.fromUri(settings.baseUrl()).replacePath(request.endpoint().path);
        if (request.symbol() != null) {
            builder.pathSegment(request.symbol());
        }
        switch (request.endpoint()) {
            case INSTRUMENTS -> builder.queryParam("index", "IHSG")
                    .queryParam("limit", settings.expectedUniverse().size());
            case DAILY -> builder.queryParam("timeframe", "1d").queryParam("order", "asc")
                    .queryParam("from", request.from()).queryParam("to", request.to());
            case FOREIGN_FLOW -> builder.queryParam("order", "asc")
                    .queryParam("from", request.from()).queryParam("to", request.to());
            case BROKER_FLOW -> builder.queryParam("at", request.from()).queryParam("investor_type", "all");
            case NEWS -> builder.queryParam("category", "news");
            default -> { }
        }
        if (cursor != null) {
            return builder.queryParam("cursor", "{cursor}").encode().buildAndExpand(cursor).toUri();
        }
        return builder.build().toUri();
    }

    private static Instant older(Instant previous, Instant next) {
        return previous == null || next.isBefore(previous) ? next : previous;
    }
}
