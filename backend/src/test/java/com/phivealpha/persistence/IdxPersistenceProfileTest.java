package com.phivealpha.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.coinmind.CoinMindApplication;
import com.phivealpha.PhiveAlphaApplication;
import com.phivealpha.marketdata.ApiCoIdProvider;
import com.phivealpha.marketdata.FixtureApiCoIdProvider;
import io.r2dbc.spi.ConnectionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = PhiveAlphaApplication.class, useMainMethod = SpringBootTest.UseMainMethod.ALWAYS,
        properties = {
                "phivealpha.persistence.url=r2dbc:postgresql://127.0.0.1:1/unused",
                "phivealpha.persistence.username=unused",
                "phivealpha.persistence.password=unused"
        })
@ActiveProfiles("phivealpha-persistence")
class IdxPersistenceProfileTest {
    @Autowired ApplicationContext context;

    @Test
    void wiresOnlyIdxPersistenceWithoutDatabaseInitialization() {
        assertThat(context.getBeansOfType(ConnectionFactory.class)).hasSize(1);
        assertThat(context.getBeansOfType(IdxPersistenceService.class)).hasSize(1);
        assertThat(context.getBean(ApiCoIdProvider.class)).isInstanceOf(FixtureApiCoIdProvider.class);
        assertThat(context.getBeansOfType(CoinMindApplication.class)).isEmpty();
        assertThat(context.getBeansOfType(ScheduledAnnotationBeanPostProcessor.class)).isEmpty();
        assertThat(context.getEnvironment().getProperty("spring.sql.init.mode")).isEqualTo("never");
        assertThat(context.getEnvironment().getProperty("coinmind.ai.enabled")).isNull();
    }
}
