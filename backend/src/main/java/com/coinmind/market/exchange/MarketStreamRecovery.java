package com.coinmind.market.exchange;

import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import java.time.Duration;

final class MarketStreamRecovery {
    private MarketStreamRecovery() { }
    static Mono<Void> reconnecting(Mono<Void> connection) {
        return connection.then(Mono.<Void>error(new IllegalStateException("WebSocket closed")))
                .retryWhen(Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(2))
                        .maxBackoff(Duration.ofSeconds(30))
                        .doBeforeRetry(signal -> org.slf4j.LoggerFactory.getLogger(MarketStreamRecovery.class)
                                .warn("Market WebSocket reconnect attempt={}, cause={}", signal.totalRetries() + 1, signal.failure().toString())));
    }
}
