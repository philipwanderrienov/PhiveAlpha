package com.phivealpha.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.phivealpha.PhiveAlphaApplication;
import com.phivealpha.persistence.IdxRecords.AdjustmentStatus;
import com.phivealpha.research.HistoricalReplay.Frame;
import com.phivealpha.screening.HistoricalDatasetReadiness;
import com.phivealpha.screening.HistoricalScreening;
import com.phivealpha.screening.HistoricalScreeningService;
import com.phivealpha.screening.StockScreenerFixture;
import com.phivealpha.screening.StockScreenerController;
import com.phivealpha.screening.StockScreenerService;
import com.phivealpha.screening.news.NewsDrivenScoringService;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryMetadata;
import io.r2dbc.spi.R2dbcNonTransientResourceException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
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

                    var fixture = StockScreenerFixture.data();
                    var cutoff = StockScreenerFixture.CUTOFF;
                    var vintage = new Frame("fixture-frame", cutoff, cutoff, fixture.context().universe(),
                            fixture.context().calendar(), "fixture-prices", fixture.prices(), Map.of(),
                            fixture.context().market(), true, Map.of());
                    var request = new HistoricalDatasetReadiness.Request("fixture-dataset", "v1", "synthetic",
                            fixture.prices().getFirst().source(), AdjustmentStatus.UNADJUSTED, "synthetic",
                            cutoff, List.of("ASII"), vintage, List.of(), 20);
                    var historical = context.getBean(HistoricalScreeningService.class)
                            .screenPersisted(new HistoricalScreening.Context(request, NewsDrivenScoringService.modelVersion()))
                            .block(Duration.ofSeconds(15));
                    assertThat(historical.status()).isEqualTo(HistoricalScreening.Status.BLOCKED);
                    assertThat(historical.readiness().outcome()).isEqualTo(HistoricalDatasetReadiness.Outcome.UNAVAILABLE);
                    assertThat(historical.rejections()).containsExactly(new HistoricalScreening.Rejection(
                            HistoricalScreening.Reason.HISTORICAL_STORAGE_UNAVAILABLE, null, null));
                    assertThat(historical.scoring()).isNull();
                    assertThat(historical.decisions()).isEmpty();
                    assertThat(attempts.get()).isEqualTo(2);
                });
    }
}
