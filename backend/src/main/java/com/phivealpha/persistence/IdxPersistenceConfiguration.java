package com.phivealpha.persistence;

import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;

@Configuration(proxyBeanMethods = false)
@Profile("phivealpha-persistence")
class IdxPersistenceConfiguration {
    @Bean
    ConnectionFactory idxConnectionFactory(
            @Value("${phivealpha.persistence.url}") String url,
            @Value("${phivealpha.persistence.username}") String username,
            @Value("${phivealpha.persistence.password}") String password) {
        if (!url.startsWith("r2dbc:postgresql://") || username.isBlank()) {
            throw new IllegalArgumentException("Configure a PostgreSQL R2DBC URL and user for PhiveAlpha");
        }
        return ConnectionFactories.get(ConnectionFactoryOptions.parse(url).mutate()
                .option(ConnectionFactoryOptions.USER, username)
                .option(ConnectionFactoryOptions.PASSWORD, password)
                .option(ConnectionFactoryOptions.CONNECT_TIMEOUT, Duration.ofSeconds(3))
                .build());
    }

    @Bean
    R2dbcEntityTemplate idxEntityTemplate(ConnectionFactory idxConnectionFactory) {
        return new R2dbcEntityTemplate(idxConnectionFactory);
    }

    @Bean
    TransactionalOperator idxTransactions(ConnectionFactory idxConnectionFactory) {
        return TransactionalOperator.create(new R2dbcTransactionManager(idxConnectionFactory));
    }
}
