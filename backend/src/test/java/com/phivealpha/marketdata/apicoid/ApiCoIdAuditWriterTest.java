package com.phivealpha.marketdata.apicoid;

import static com.phivealpha.marketdata.apicoid.ApiCoIdAccess.Dataset.USD_IDR;
import static com.phivealpha.marketdata.apicoid.ApiCoIdAccess.Product.FX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.phivealpha.PhiveAlphaApplication;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryMetadata;
import io.r2dbc.spi.R2dbcNonTransientResourceException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.reactivestreams.Publisher;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.core.publisher.Mono;

@ExtendWith(OutputCaptureExtension.class)
class ApiCoIdAuditWriterTest {
    @TempDir Path temporary;

    @Test
    void auditStorageFailureIsDiagnosableWithoutRetryOrBudgetRefund(CapturedOutput output) throws Exception {
        String sensitiveError = "synthetic-connection-detail";
        var storageAttempts = new AtomicInteger();
        ConnectionFactory failingStorage = new ConnectionFactory() {
            @Override
            public Publisher<? extends Connection> create() {
                storageAttempts.incrementAndGet();
                return Mono.error(new R2dbcNonTransientResourceException(sensitiveError));
            }

            @Override
            public ConnectionFactoryMetadata getMetadata() {
                return () -> "PostgreSQL";
            }
        };
        Path journal = Files.writeString(temporary.resolve("quota"), "PHIVEALPHA-QUOTA-V1\n");
        var now = Instant.parse("2026-11-13T09:30:00Z");
        var clock = Clock.fixed(now, ZoneOffset.UTC);
        var window = new ApiCoIdAccess.Window(now.minusSeconds(60), now.plusSeconds(60), 1);
        var access = new ApiCoIdAccess(Map.of(FX, window),
                Map.of(USD_IDR, new ApiCoIdAccess.Grant(window.end(), true, true, true)),
                new ApiCoIdQuotaLedger(journal), clock).restrictedToFree(Set.of(USD_IDR));
        var hits = new AtomicInteger();
        var settings = ApiCoIdRuntimeConfiguration.macroSettings(2, Duration.ofSeconds(3), 30);
        var client = ApiCoIdConfiguration.client(settings, "synthetic-key").exchangeFunction(request -> {
            hits.incrementAndGet();
            return Mono.just(ClientResponse.create(HttpStatus.OK).body("{}").build());
        }).build();

        new ApplicationContextRunner()
                .withInitializer(context -> context.getEnvironment()
                        .setActiveProfiles("phivealpha-persistence", "phivealpha-api-co-id-audit"))
                .withUserConfiguration(PhiveAlphaApplication.class)
                .withBean(ConnectionFactory.class, () -> failingStorage, definition -> definition.setPrimary(true))
                .withPropertyValues("phivealpha.persistence.url=r2dbc:postgresql://127.0.0.1:1/phivealpha_dev_stub",
                        "phivealpha.persistence.username=phivealpha_dev_stub",
                        "phivealpha.persistence.password=test-only-not-a-real-password", "spring.sql.init.mode=never")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(ApiCoIdAuditWriter.class);
                    var audit = context.getBean(ApiCoIdAuditWriter.class);
                    var uri = URI.create(ApiCoIdMacroCache.ORIGIN + "/currency/exchange-rate?pair=USDIDR");
                    var transport = new ApiCoIdTransport(client, settings, clock, ignored -> {}, access, audit);
                    assertThat(transport.get(USD_IDR, uri).block(Duration.ofSeconds(10))).isNotNull();
                    var usage = access.usage(FX);
                    assertThat(usage.used()).isEqualTo(1);
                    assertThat(usage.estimatedRemaining()).isZero();
                    assertThat(usage.datasets().get(USD_IDR))
                            .isEqualTo(new ApiCoIdQuotaLedger.DatasetUsage(1, 0, 0, now));

                    var restarted = new ApiCoIdTransport(client, settings, clock, ignored -> {}, access, audit);
                    assertThatThrownBy(() -> restarted.get(USD_IDR, uri).block(Duration.ofSeconds(10)))
                            .isInstanceOf(ApiCoIdFailure.class).hasMessage("PERSISTENT_BUDGET_EXHAUSTED");
                    assertThat(hits.get()).isEqualTo(1);
                    assertThat(storageAttempts.get()).isEqualTo(1);
                    assertThat(output.getAll()).contains("metadata mirror failed dataset=USD_IDR reservation=",
                            "transportOutcome=OK errorType=PersistenceException").doesNotContain(sensitiveError);
                });
    }
}
