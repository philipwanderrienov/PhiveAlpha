package com.phivealpha.marketdata.apicoid;

import static com.phivealpha.marketdata.apicoid.ApiCoIdData.Quality.UNAVAILABLE;

import java.time.Clock;
import java.time.Instant;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ApiCoIdRequestGate {
    private static final Logger LOG = LoggerFactory.getLogger(ApiCoIdRequestGate.class);
    private final ApiCoIdSettings settings;
    private final Clock clock;
    private int used;
    private int active;
    private int failures;
    private long generation;
    private boolean credentialsBlocked;
    private boolean probeActive;
    private Instant nextRequest = Instant.MIN;
    private Instant throttledUntil = Instant.MIN;
    private Instant openUntil;

    record Ticket(long generation, boolean probe, int number, ApiCoIdQuotaLedger.Reservation reservation) {}

    ApiCoIdRequestGate(ApiCoIdSettings settings, Clock clock) {
        this.settings = settings;
        this.clock = clock;
    }

    synchronized Ticket acquire(Supplier<ApiCoIdQuotaLedger.Reservation> reserve) {
        var now = clock.instant();
        if (credentialsBlocked) {
            throw blocked("CREDENTIALS_OR_ENTITLEMENT_BLOCKED");
        }
        if (now.isBefore(throttledUntil)) {
            throw blocked("UPSTREAM_RATE_LIMIT");
        }
        if (openUntil != null && (now.isBefore(openUntil) || probeActive)) {
            throw blocked("CIRCUIT_OPEN");
        }
        if (used >= settings.requestBudget()) {
            throw blocked("REQUEST_BUDGET_EXHAUSTED");
        }
        if (active >= settings.concurrency()) {
            throw blocked("CONCURRENCY_LIMIT");
        }
        if (now.isBefore(nextRequest)) {
            throw blocked("LOCAL_RATE_LIMIT");
        }
        var reservation = reserve.get();
        probeActive = openUntil != null;
        nextRequest = now.plus(settings.minRequestInterval());
        active++;
        return new Ticket(generation, probeActive, ++used, reservation);
    }

    synchronized void finish(Ticket ticket, ApiCoIdAccess.Dataset endpoint, int status, String outcome,
                             boolean transientError, Instant retryAfter) {
        active--;
        var now = clock.instant();
        if (status == 401 || status == 402 || status == 403) {
            credentialsBlocked = true;
        }
        if (status == 429) {
            var until = retryAfter == null || !retryAfter.isAfter(now)
                    ? now.plus(settings.breakerCooldown()) : retryAfter;
            if (until.isAfter(throttledUntil)) {
                throttledUntil = until;
            }
        }
        if (ticket.generation() == generation) {
            if (ticket.probe()) {
                probeActive = false;
                openUntil = "OK".equals(outcome) ? null : now.plus(settings.breakerCooldown());
                failures = 0;
                generation++;
            } else if (transientError && ++failures >= settings.breakerThreshold()) {
                openUntil = now.plus(settings.breakerCooldown());
                generation++;
            } else if ("OK".equals(outcome)) {
                failures = 0;
            }
        }
        LOG.info("API.co.id attempt dataset={} number={} status={} outcome={} remaining={}",
                endpoint, ticket.number(), status, outcome, remaining());
    }

    synchronized int remaining() {
        return settings.requestBudget() - used;
    }

    private static ApiCoIdFailure blocked(String reason) {
        return new ApiCoIdFailure(UNAVAILABLE, reason);
    }
}
