package com.coinmind.market.exchange;

import com.coinmind.config.MarketProperties;
import com.coinmind.market.service.*;
import com.coinmind.market.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.web.reactive.function.client.*;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tools.jackson.databind.ObjectMapper;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class BinanceReliabilityTest {
    private final MarketProperties properties = new MarketProperties("wss://unused", "https://unused", 500,
            List.of("BTCUSDT", "ETHUSDT", "SOLUSDT"), List.of("1m", "5m", "15m", "1h", "4h", "1d"));
    private BinanceHistoricalKlineClient rest(ExchangeFunction exchange) {
        return new BinanceHistoricalKlineClient(properties, new MarketHistoryService(),
                new StaticListableBeanFactory().getBeanProvider(com.coinmind.market.persistence.CandlestickPersistenceService.class),
                WebClient.builder().exchangeFunction(exchange).build());
    }
    @Test void restTimeoutIsBounded() {
        StepVerifier.withVirtualTime(() -> rest(request -> Mono.never()).loadSeries("BTCUSDT", "1m"))
                .thenAwait(Duration.ofSeconds(10)).expectError(java.util.concurrent.TimeoutException.class).verify();
    }
    @Test void restErrorsAndInvalidSuccessfulResponsesFailClosed() {
        for (HttpStatus status : List.of(HttpStatus.INTERNAL_SERVER_ERROR, HttpStatus.OK)) {
            StepVerifier.create(rest(request -> Mono.just(ClientResponse.create(status)
                    .header("Content-Type", "application/json").body("{}").build())).loadSeries("BTCUSDT", "1m"))
                    .expectError().verify();
        }
    }
    @Test void staleFutureAndIncompletePayloadsCannotRecoverButValidPayloadCan() throws Exception {
        var tickers = new MarketTickerService();
        var client = new BinanceTickerClient(properties, tickers, new ObjectMapper());
        var method = BinanceTickerClient.class.getDeclaredMethod("handlePayload", String.class);
        method.setAccessible(true);
        for (long timestamp : new long[]{0, Instant.now().plusSeconds(60).toEpochMilli()}) {
            method.invoke(client, payload(timestamp));
            assertThat(tickers.getAllLatest()).isEmpty();
        }
        method.invoke(client, "{\"data\":{\"s\":\"BTCUSDT\"}}");
        assertThat(tickers.getAllLatest()).isEmpty();
        method.invoke(client, payload(Instant.now().toEpochMilli()));
        assertThat(tickers.getAllLatest()).hasSize(1);
    }
    @Test void cleanCloseAndErrorReconnectAndResume() {
        for (boolean clean : List.of(true, false)) {
            var attempts = new java.util.concurrent.atomic.AtomicInteger();
            StepVerifier.withVirtualTime(() -> MarketStreamRecovery.reconnecting(Mono.defer(() -> {
                int attempt = attempts.incrementAndGet();
                if (attempt == 1) return clean ? Mono.empty() : Mono.error(new IllegalStateException("disconnect"));
                return Mono.never();
            }))).thenAwait(Duration.ofSeconds(5)).then(() -> assertThat(attempts.get()).isEqualTo(2))
                    .thenCancel().verify();
        }
    }
    @Test void simultaneousProviderFailureStopsAdvisorBeforeIndicatorsOrScoring() {
        var indicators = org.mockito.Mockito.mock(com.coinmind.indicator.service.TechnicalIndicatorService.class);
        var scores = org.mockito.Mockito.mock(com.coinmind.ai.service.MarketSignalScoringService.class);
        var tickers = new MarketTickerService();
        var builder = new com.coinmind.ai.service.MarketContextBuilder(tickers, new MarketHistoryService(),
                new OrderBookService(), new TradeService(), indicators, null, scores, null);
        assertThatThrownBy(() -> builder.build("BTCUSDT", "1m")).isInstanceOf(IllegalStateException.class);
        tickers.publish(new TickerSnapshot("BTCUSDT", BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN,
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.TEN, Instant.now().minusSeconds(31)));
        assertThatThrownBy(() -> builder.build("BTCUSDT", "1m")).hasMessageContaining("stale");
        org.mockito.Mockito.verifyNoInteractions(indicators, scores);
    }
    @Test void restRecoveryAcceptsCompleteCurrentHistoryOnly() {
        Instant open = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
        StringBuilder rows = new StringBuilder("[");
        for (int i=30; i>=0; i--) {
            if (i<30) rows.append(',');
            long start = open.minusSeconds(i*60).toEpochMilli();
            rows.append("[").append(start).append(",\"10\",\"11\",\"9\",\"10\",\"1\",")
                    .append(start+59999).append(",\"10\",1]");
        }
        rows.append(']');
        StepVerifier.create(rest(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                .header("Content-Type", "application/json").body(rows.toString()).build())).loadSeries("BTCUSDT", "1m"))
                .assertNext(candles -> assertThat(candles).hasSize(31)).verifyComplete();
    }
    @Test void anotherSymbolCannotHideMissingOrStaleSeriesHealth() {
        var tickers = new MarketTickerService();
        tickers.publish(new TickerSnapshot("BTCUSDT", BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN,
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.TEN, Instant.now()));
        var health = new com.coinmind.market.api.MarketFeedHealthController(properties, tickers,
                new MarketCandlestickService(), new OrderBookService(), new TradeService()).health();
        assertThat(health.status()).isEqualTo("DEGRADED");
        assertThat(health.streams().get("ticker:ETHUSDT").status()).isEqualTo("UNAVAILABLE");
        assertThat(health.streams().get("ticker:BTCUSDT").status()).isEqualTo("UP");
    }
    private String payload(long time) {
        return "{\"data\":{\"s\":\"BTCUSDT\",\"c\":\"10\",\"o\":\"10\",\"h\":\"11\",\"l\":\"9\",\"v\":\"1\",\"q\":\"10\",\"E\":" + time + "}}";
    }
    private Candlestick candle(Instant open, Instant event, boolean closed) {
        return new Candlestick("BTCUSDT", "1m", open, open.plusSeconds(60).minusMillis(1),
                BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ONE, BigDecimal.TEN,
                BigDecimal.ONE, BigDecimal.TEN, 1, closed, event);
    }
    @Test void outOfOrderCannotReplaceCurrentOrReopenClosedCandle() {
        Instant now = Instant.now();
        var service = new MarketCandlestickService();
        var history = new MarketHistoryService();
        var current = candle(now.minusSeconds(60), now, true);
        service.publish(current); history.upsert(current);
        service.publish(candle(now.minusSeconds(120), now.minusSeconds(1), true));
        history.upsert(candle(current.openTime(), now.minusSeconds(1), false));
        assertThat(service.getLatest("BTCUSDT", "1m")).contains(current);
        assertThat(history.get("BTCUSDT", "1m", 5)).containsExactly(current);
    }
    @Test void advisorHistoryRejectsStaleFutureGapsAndIncompleteThenRecovers() {
        Instant now = Instant.now();
        List<Candlestick> valid = new ArrayList<>();
        for (int i=31; i>0; i--) valid.add(candle(now.minusSeconds(i*60), i==1 ? now : now.minusSeconds(i*60-59), true));
        MarketDataValidity.requireHistory(valid, now);
        assertThatThrownBy(() -> MarketDataValidity.requireHistory(valid, now.plusSeconds(31))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> MarketDataValidity.requireHistory(valid, now.minusSeconds(60))).isInstanceOf(IllegalStateException.class);
        var gap = new ArrayList<>(valid); gap.remove(5);
        assertThatThrownBy(() -> MarketDataValidity.requireHistory(gap, now)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> MarketDataValidity.requireHistory(List.of(), now)).isInstanceOf(IllegalStateException.class);
        MarketDataValidity.requireHistory(valid, now);
    }
}
