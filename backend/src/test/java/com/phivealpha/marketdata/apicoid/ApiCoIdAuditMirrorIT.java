package com.phivealpha.marketdata.apicoid;

import static com.phivealpha.marketdata.apicoid.ApiCoIdAccess.Dataset.USD_IDR;
import static com.phivealpha.marketdata.apicoid.ApiCoIdAccess.Product.FX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.phivealpha.PhiveAlphaApplication;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

@EnabledIfEnvironmentVariable(named = "PHIVEALPHA_IT_DATA_DIR", matches = "/tmp/phivealpha-it\\.[A-Za-z0-9]+/data")
@SpringBootTest(classes = PhiveAlphaApplication.class, useMainMethod = SpringBootTest.UseMainMethod.ALWAYS)
@ActiveProfiles({"phivealpha-persistence", "phivealpha-api-co-id-audit"})
@ExtendWith(OutputCaptureExtension.class)
class ApiCoIdAuditMirrorIT {
    private static final String DATABASE = "phivealpha_dev_audit_it";
    private static final String SECRET = "synthetic-sit-02b-secret";
    private static final URI URL = URI.create(ApiCoIdMacroCache.ORIGIN + "/currency/exchange-rate?pair=USDIDR");
    private static final Duration WAIT = Duration.ofSeconds(15);
    private static final Instant FIRST = Instant.parse("2026-11-13T09:30:00.123456789Z");
    private static final ApiCoIdAccess.Window WINDOW =
            new ApiCoIdAccess.Window(FIRST.minusSeconds(60), FIRST.plusSeconds(600), 3);

    @Autowired ApiCoIdAuditWriter audit;
    @Autowired R2dbcEntityTemplate template;
    @TempDir Path temporary;

    @DynamicPropertySource
    static void isolatedConnection(DynamicPropertyRegistry registry) {
        String url = System.getenv("PHIVEALPHA_R2DBC_URL");
        assertThat(url).matches("r2dbc:postgresql://127\\.0\\.0\\.1:[0-9]+/" + DATABASE);
        assertThat(System.getenv("PHIVEALPHA_R2DBC_USERNAME")).isEqualTo(DATABASE);
        registry.add("phivealpha.persistence.url", () -> url);
        registry.add("phivealpha.persistence.username", () -> DATABASE);
        registry.add("phivealpha.persistence.password", () -> System.getenv("PHIVEALPHA_R2DBC_PASSWORD"));
    }

    @Test
    void mirrorsTransportMetadataAndKeepsQuotaDurableWhenPostgresRejectsAnInsert(CapturedOutput output)
            throws Exception {
        verifyIsolatedCluster();
        assertThat(rows()).isEmpty();

        Path journal = Files.writeString(temporary.resolve("quota.journal"), "PHIVEALPHA-QUOTA-V1\n");
        var now = new AtomicReference<>(FIRST);
        InstantSource time = now::get;
        Clock clock = time.withZone(ZoneOffset.UTC);
        var hits = new AtomicInteger();
        ExchangeFunction mock = request -> {
            assertThat(request.url()).isEqualTo(URL);
            assertThat(request.method()).isEqualTo(HttpMethod.GET);
            int status = hits.incrementAndGet() == 2 ? 404 : 200;
            return Mono.just(ClientResponse.create(HttpStatusCode.valueOf(status))
                    .body("{\"fixture\":\"" + SECRET + "\"}").build());
        };

        var access = access(clock, journal);
        var transport = transport(clock, access, mock);
        assertThat(transport.get(USD_IDR, URL).block(WAIT).get("fixture").asString()).isEqualTo(SECRET);
        now.set(FIRST.plusSeconds(1));
        assertThatThrownBy(() -> transport.get(USD_IDR, URL).block(WAIT))
                .isInstanceOf(ApiCoIdFailure.class).hasMessage("HTTP_REJECTED");

        var mirrored = rows();
        assertThat(mirrored).containsExactly(
                row(1, FIRST, "PARTIAL", "SCHEMA_NOT_EVALUATED"),
                row(2, now.get(), "FAILED", "HTTP_REJECTED"));
        var usage = access.usage(FX);
        assertThat(usage.used()).isEqualTo(2);
        assertThat(usage.estimatedRemaining()).isEqualTo(1);
        assertThat(usage.datasets().get(USD_IDR))
                .isEqualTo(new ApiCoIdQuotaLedger.DatasetUsage(2, 1, 0, FIRST));
        assertThat(hits.get()).isEqualTo(2);

        // Rebuild process-local components against the same journal and database.
        byte[] beforeRestart = Files.readAllBytes(journal);
        var restarted = access(clock, journal);
        var restartedTransport = transport(clock, restarted, mock);
        assertThat(restarted.usage(FX)).isEqualTo(usage);
        assertThat(Files.readAllBytes(journal)).isEqualTo(beforeRestart);
        assertThat(rows()).isEqualTo(mirrored);

        // Reject only new writes. Existing rows and the query connection remain available.
        template.getDatabaseClient().sql("""
                ALTER TABLE phivealpha_idx.idx_data_ingestion
                ADD CONSTRAINT sit_02b_reject_new_audits CHECK (false) NOT VALID
                """).then().block(WAIT);
        now.set(FIRST.plusSeconds(2));
        assertThat(restartedTransport.get(USD_IDR, URL).block(WAIT).get("fixture").asString()).isEqualTo(SECRET);
        assertThat(hits.get()).isEqualTo(3);
        assertThat(rows()).isEqualTo(mirrored);

        var afterFailure = access(clock, journal).usage(FX);
        assertThat(afterFailure.used()).isEqualTo(3);
        assertThat(afterFailure.estimatedRemaining()).isZero();
        assertThat(afterFailure.datasets().get(USD_IDR))
                .isEqualTo(new ApiCoIdQuotaLedger.DatasetUsage(3, 1, 0, now.get()));
        var journalLines = Files.readAllLines(journal);
        assertThat(journalLines).hasSize(7);
        String[] failedMirrorAdmission = journalLines.get(5).split("\t");
        assertThat(failedMirrorAdmission).containsExactly("A", failedMirrorAdmission[1], "USD_IDR",
                WINDOW.start().toString(), WINDOW.end().toString(), now.get().toString());
        assertThat(journalLines.get(6)).isEqualTo(
                "R\t" + failedMirrorAdmission[1] + "\t" + now.get() + "\t200\tOK\t");
        assertThat(output.getAll()).contains("metadata mirror failed dataset=USD_IDR reservation="
                        + failedMirrorAdmission[1] + " transportOutcome=OK errorType=PersistenceException")
                .doesNotContain(SECRET, "r2dbc:postgresql://");

        now.set(FIRST.plusSeconds(3));
        var exhausted = transport(clock, access(clock, journal), mock);
        assertThatThrownBy(() -> exhausted.get(USD_IDR, URL).block(WAIT))
                .isInstanceOf(ApiCoIdFailure.class).hasMessage("PERSISTENT_BUDGET_EXHAUSTED");
        assertThat(hits.get()).isEqualTo(3);
        assertThat(rows()).isEqualTo(mirrored);
        assertThat(Files.readAllLines(journal)).isEqualTo(journalLines);
    }

    private void verifyIsolatedCluster() {
        var identity = template.getDatabaseClient().sql("""
                SELECT current_setting('data_directory'), current_database(), current_user,
                       current_setting('server_version_num'), to_regclass('phivealpha_idx.idx_data_ingestion') IS NOT NULL
                """).map((row, meta) -> List.of(row.get(0, String.class), row.get(1, String.class),
                        row.get(2, String.class), row.get(3, String.class), row.get(4, Boolean.class)))
                .one().block(WAIT);
        assertThat(identity).isNotNull();
        assertThat(identity.subList(0, 3)).containsExactly(System.getenv("PHIVEALPHA_IT_DATA_DIR"), DATABASE, DATABASE);
        assertThat((String) identity.get(3)).matches("17[0-9]{4}");
        assertThat(identity.get(4)).isEqualTo(true);
    }

    private List<AuditRow> rows() {
        return template.getDatabaseClient().sql("""
                SELECT ingestion_id, provider, dataset, scope, fetched_at, status, error_category,
                       record_count, rejected_count, requests_used, request_budget, budget_window
                FROM phivealpha_idx.idx_data_ingestion ORDER BY ingestion_id
                """).map((row, meta) -> new AuditRow(row.get(0, Long.class), row.get(1, String.class),
                        row.get(2, String.class), row.get(3, String.class), row.get(4, OffsetDateTime.class).toInstant(),
                        row.get(5, String.class), row.get(6, String.class), row.get(7, Long.class),
                        row.get(8, Long.class), row.get(9, Integer.class), row.get(10, Integer.class),
                        row.get(11, String.class))).all().collectList().block(WAIT);
    }

    private static AuditRow row(long id, Instant at, String status, String category) {
        return new AuditRow(id, "API.co.id", "USD_IDR", "TRANSPORT_ONLY",
                at.truncatedTo(java.time.temporal.ChronoUnit.MICROS), status, category, 0, 0,
                1, WINDOW.limit(), WINDOW.start() + "/" + WINDOW.end());
    }

    private static ApiCoIdAccess access(Clock clock, Path journal) {
        return new ApiCoIdAccess(Map.of(FX, WINDOW),
                Map.of(USD_IDR, new ApiCoIdAccess.Grant(WINDOW.end(), true, true, true)),
                new ApiCoIdQuotaLedger(journal), clock).restrictedToFree(Set.of(USD_IDR));
    }

    private ApiCoIdTransport transport(Clock clock, ApiCoIdAccess access, ExchangeFunction mock) {
        var settings = ApiCoIdRuntimeConfiguration.macroSettings(2, Duration.ofSeconds(3), 30);
        var client = ApiCoIdConfiguration.client(settings, SECRET).exchangeFunction(mock).build();
        return new ApiCoIdTransport(client, settings, clock, ignored -> {}, access, audit);
    }

    private record AuditRow(long id, String provider, String dataset, String scope, Instant fetchedAt, String status,
                            String errorCategory, long records, long rejected, int used, int budget, String window) {}
}
