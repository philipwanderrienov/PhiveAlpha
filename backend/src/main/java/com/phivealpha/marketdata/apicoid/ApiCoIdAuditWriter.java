package com.phivealpha.marketdata.apicoid;

import com.phivealpha.persistence.IdxDevelopmentDatabase;
import com.phivealpha.persistence.IdxPersistenceService;
import com.phivealpha.persistence.IdxRecords.Ingestion;
import com.phivealpha.persistence.IdxRecords.IngestionStatus;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@Profile("phivealpha-api-co-id-audit & phivealpha-persistence")
final class ApiCoIdAuditWriter {
    private static final Logger LOG = LoggerFactory.getLogger(ApiCoIdAuditWriter.class);

    private final IdxPersistenceService persistence;

    ApiCoIdAuditWriter(IdxPersistenceService persistence, @Value("${phivealpha.persistence.url}") String url,
                       @Value("${phivealpha.persistence.username}") String username) {
        requireDevelopment(url, username);
        this.persistence = persistence;
    }

    static void requireDevelopment(String url, String username) {
        IdxDevelopmentDatabase.requireIsolated(url, username);
    }

    Mono<Void> record(ApiCoIdQuotaLedger.Reservation reservation, String outcome, int budget) {
        boolean ok = "OK".equals(outcome);
        return persistence.recordIngestion(new Ingestion("API.co.id", reservation.dataset().name(), "TRANSPORT_ONLY",
                reservation.attemptedAt().atOffset(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS),
                ok ? IngestionStatus.PARTIAL : IngestionStatus.FAILED, ok ? "SCHEMA_NOT_EVALUATED" : outcome,
                0, 0, 1, budget, reservation.start() + "/" + reservation.end()))
                .doOnError(error -> LOG.warn(
                        "API.co.id metadata mirror failed dataset={} reservation={} transportOutcome={} errorType={}",
                        reservation.dataset(), reservation.id(), outcome, error.getClass().getSimpleName()))
                .onErrorComplete().then();
    }
}
