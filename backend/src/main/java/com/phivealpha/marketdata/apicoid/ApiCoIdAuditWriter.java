package com.phivealpha.marketdata.apicoid;

import com.phivealpha.persistence.IdxPersistenceService;
import com.phivealpha.persistence.IdxRecords.Ingestion;
import com.phivealpha.persistence.IdxRecords.IngestionStatus;
import java.net.URI;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Set;
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
        try {
            if (url.startsWith("r2dbc:postgresql://") && username.matches("phivealpha_dev_[a-z0-9_]+")) {
                var uri = URI.create(url.substring(6));
                if (uri.getHost() != null && Set.of("127.0.0.1", "[::1]").contains(uri.getHost())
                        && uri.getPort() > 0 && uri.getRawPath().matches("/phivealpha_dev_[a-z0-9_]+")
                        && uri.getRawUserInfo() == null && uri.getRawQuery() == null && uri.getRawFragment() == null) {
                    return;
                }
            }
        } catch (RuntimeException ignored) {
            // Keep connection details out of startup diagnostics.
        }
        throw new IllegalArgumentException("API.co.id audit requires an isolated PhiveAlpha development database");
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
