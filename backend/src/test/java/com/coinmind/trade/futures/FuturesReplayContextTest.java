package com.coinmind.trade.futures;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.dao.annotation.PersistenceExceptionTranslationPostProcessor;
import org.springframework.dao.support.PersistenceExceptionTranslationInterceptor;
import org.springframework.transaction.ReactiveTransactionManager;

/** Real Boot post-processors and replay dependency graph; never connects to a database. */
class FuturesReplayContextTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ReplayConfiguration.class)
            .withPropertyValues(
                    "spring.r2dbc.url=r2dbc:postgresql://127.0.0.1:1/unused",
                    "spring.r2dbc.username=unused", "spring.r2dbc.password=unused",
                    "spring.sql.init.mode=never");

    @Test
    void disabledArchivesStillStartWithExceptionTranslation() {
        assertReplayContext(false);
    }

    @Test
    void enabledArchivesStartWithExceptionTranslationAndTransactions() {
        assertReplayContext(true);
    }

    private void assertReplayContext(boolean enabled) {
        runner.withPropertyValues("coinmind.futures.paper-replay-archive-enabled=" + enabled)
                .run(context -> {
                    assertThat(context).hasNotFailed()
                            .hasSingleBean(FuturesOfflineReplayJob.class)
                            .hasSingleBean(FuturesPaperReplayWorkflow.class)
                            .hasSingleBean(FuturesPaperReplayPersistence.class)
                            .hasSingleBean(FuturesPaperReplayHistory.class)
                            .hasSingleBean(PersistenceExceptionTranslationPostProcessor.class)
                            .hasSingleBean(ReactiveTransactionManager.class);
                    for (Class<?> type : new Class<?>[] {
                            FuturesPaperReplayArchive.class, FuturesPaperEquityArchive.class}) {
                        Object archive = context.getBean(type);
                        assertThat(AopUtils.isCglibProxy(archive)).isTrue();
                        assertThat(((Advised) archive).getAdvisors()).anySatisfy(advisor ->
                                assertThat(advisor.getAdvice())
                                        .isInstanceOf(PersistenceExceptionTranslationInterceptor.class));
                    }
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import({FuturesOfflineReplayJob.class, FuturesPaperReplayWorkflow.class,
            FuturesPaperReplayRegistry.class, FuturesPaperReplayArchive.class,
            FuturesPaperEquityArchive.class, FuturesPaperReplayPersistence.class,
            FuturesPaperReplayHistory.class})
    static class ReplayConfiguration {}
}
