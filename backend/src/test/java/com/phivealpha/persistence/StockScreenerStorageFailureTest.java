package com.phivealpha.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.phivealpha.PhiveAlphaApplication;
import com.phivealpha.screening.StockScreenerController;
import com.phivealpha.screening.StockScreenerService;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryMetadata;
import io.r2dbc.spi.R2dbcNonTransientResourceException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import reactor.core.publisher.Mono;

class StockScreenerStorageFailureTest {
    @Test
    void returnsUnavailableWithoutFallbackToFixtureWhenTheDatabaseReadFails() {
        var attempts = new AtomicInteger();
        ConnectionFactory failingStorage = new ConnectionFactory() {
            @Override
            public Publisher<? extends Connection> create() {
                attempts.incrementAndGet();
                return Mono.error(new R2dbcNonTransientResourceException("synthetic read failure"));
            }

            @Override
            public ConnectionFactoryMetadata getMetadata() {
                return () -> "PostgreSQL";
            }
        };

        new ApplicationContextRunner()
                .withInitializer(context -> context.getEnvironment()
                        .setActiveProfiles("phivealpha-persistence", "phivealpha-daily-ingestion"))
                .withUserConfiguration(PhiveAlphaApplication.class)
                .withBean(ConnectionFactory.class, () -> failingStorage, definition -> definition.setPrimary(true))
                .withPropertyValues("phivealpha.persistence.url=r2dbc:postgresql://127.0.0.1:1/phivealpha_dev_stub",
                        "phivealpha.persistence.username=phivealpha_dev_stub",
                        "phivealpha.persistence.password=synthetic-password", "spring.sql.init.mode=never",
                        "phivealpha.stock-screener.persisted=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var controller = context.getBean(StockScreenerController.class);
                    var failed = controller.snapshot().block(Duration.ofSeconds(15));
                    assertThat(failed.mode()).isEqualTo(StockScreenerService.Mode.DEVELOPMENT_PERSISTENCE);
                    assertThat(failed.quality()).isEqualTo(StockScreenerService.Quality.UNAVAILABLE);
                    assertThat(failed.failure()).isEqualTo(StockScreenerService.Failure.HISTORICAL_STORAGE_UNAVAILABLE);
                    assertThat(failed.scoring()).isNull();
                    assertThat(failed.decisions()).isEmpty();
                    assertThat(attempts.get()).isEqualTo(1);
                });
    }
}
