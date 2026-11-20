package com.phivealpha;

import com.coinmind.CoinMindApplication;
import com.phivealpha.marketdata.ApiCoIdProvider;
import com.phivealpha.marketdata.FixtureApiCoIdProvider;
import com.phivealpha.marketdata.apicoid.ApiCoIdHttpProvider;
import com.phivealpha.persistence.IdxPersistenceService;
import com.phivealpha.screening.LiquidUniverseService;
import com.phivealpha.screening.LiquidityPolicy;
import com.phivealpha.screening.news.NewsDrivenScoringService;
import com.phivealpha.screening.news.NewsScoringPolicy;
import io.r2dbc.spi.ConnectionFactory;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = PhiveAlphaApplication.class,
        useMainMethod = SpringBootTest.UseMainMethod.ALWAYS,
        properties = {"spring.r2dbc.url=r2dbc:postgresql://127.0.0.1:1/unused",
                "phivealpha.liquid-universe.min-daily-volume-shares=250000",
                "phivealpha.news-scoring.news-weight=40", "phivealpha.news-scoring.momentum-weight=15"}
)
class IdxFoundationTest {
    @Autowired
    private ApplicationContext context;
    private WebTestClient client;

    @BeforeEach
    void bindClient() {
        client = WebTestClient.bindToApplicationContext(context).build();
    }

    @Test
    void isolatesFixtureFromLegacyApplicationAndDatabase() {
        assertThat(context.getBean(ApiCoIdProvider.class)).isInstanceOf(FixtureApiCoIdProvider.class);
        assertThat(context.getBeansOfType(ApiCoIdHttpProvider.class)).isEmpty();
        assertThat(context.getEnvironment().matchesProfiles("phivealpha-api-co-id")).isFalse();
        assertThat(context.getBeansOfType(ConnectionFactory.class)).isEmpty();
        assertThat(context.getBeansOfType(IdxPersistenceService.class)).isEmpty();
        assertThat(context.getBeansOfType(LiquidUniverseService.class)).hasSize(1);
        var policy = context.getBean(LiquidityPolicy.class);
        assertThat(policy.lookbackSessions()).isEqualTo(20);
        assertThat(policy.minDailyVolumeShares()).isEqualTo(250_000);
        assertThat(context.getBeansOfType(NewsDrivenScoringService.class)).hasSize(1);
        assertThat(context.getBean(NewsScoringPolicy.class).newsWeight()).isEqualByComparingTo("40");
        assertThat(context.getBeansOfType(CoinMindApplication.class)).isEmpty();
        assertThat(context.getBeansOfType(ScheduledAnnotationBeanPostProcessor.class)).isEmpty();
        assertThat(context.getEnvironment().getProperty("spring.sql.init.mode")).isEqualTo("never");
        assertThat(context.getEnvironment().getProperty("coinmind.ai.enabled")).isNull();
    }

    @Test
    void servesUniverseAndResolvesSymbolsCaseInsensitively() {
        client.get().uri("/api/idx/v1/stocks").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.length()").isEqualTo(6);
        client.get().uri("/api/idx/v1/stocks/bbca").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.symbol").isEqualTo("BBCA")
                .jsonPath("$.closeIdr").isEqualTo(9800)
                .jsonPath("$.sessionDate").isEqualTo("2026-11-13");
        client.get().uri("/api/idx/v1/stocks/UNKNOWN").exchange().expectStatus().isNotFound();
    }

    @Test
    void returnsFiveStocksInDescendingWeeklyOrder() {
        client.get().uri("/api/idx/v1/weekly-top-five").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$[*].symbol").isEqualTo(
                        List.of("ASII", "BMRI", "BBRI", "BBCA", "TLKM"))
                .jsonPath("$[0].weeklyChangePercent").isEqualTo(2.73);
    }

    @Test
    void returnsSyntheticHeadlineProvenance() {
        client.get().uri("/api/idx/v1/headlines").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$[0].symbol").isEqualTo("BBCA")
                .jsonPath("$[0].source").isEqualTo("Fixture PhiveAlpha");
    }

    @Test
    void previewsWholeLotsAndRejectsInvalidInput() {
        client.get().uri("/api/idx/v1/paper-trade/preview?symbol=BBCA&lots=2")
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.shares").isEqualTo(200)
                .jsonPath("$.grossIdr").isEqualTo(1960000);
        for (String lots : new String[]{"0", "10001", "1.5"}) {
            client.get().uri("/api/idx/v1/paper-trade/preview?symbol=BBCA&lots=" + lots)
                    .exchange().expectStatus().isBadRequest();
        }
        client.get().uri("/api/idx/v1/paper-trade/preview?symbol=MISSING&lots=1")
                .exchange().expectStatus().isNotFound();
        client.post().uri("/api/idx/v1/paper-trade/preview?symbol=BBCA&lots=1")
                .exchange().expectStatus().isEqualTo(405);
    }
}
