package com.coinmind.market.exchange;

import com.coinmind.config.MarketProperties;
import com.coinmind.market.model.Candlestick;
import com.coinmind.market.persistence.CandlestickPersistenceService;
import com.coinmind.market.service.MarketHistoryService;
import tools.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class BinanceHistoricalKlineClient {

    private static final Logger log = LoggerFactory.getLogger(BinanceHistoricalKlineClient.class);

    private final MarketProperties properties;
    private final MarketHistoryService historyService;
    private final ObjectProvider<CandlestickPersistenceService> persistenceService;
    private final WebClient webClient;

    @org.springframework.beans.factory.annotation.Autowired
    public BinanceHistoricalKlineClient(
            MarketProperties properties,
            MarketHistoryService historyService,
            ObjectProvider<CandlestickPersistenceService> persistenceService
    ) {
        this(properties, historyService, persistenceService, WebClient.builder()
                .baseUrl(properties.binanceRestBaseUrl()).build());
    }

    BinanceHistoricalKlineClient(MarketProperties properties, MarketHistoryService historyService,
            ObjectProvider<CandlestickPersistenceService> persistenceService, WebClient webClient) {
        this.properties = properties;
        this.historyService = historyService;
        this.persistenceService = persistenceService;
        this.webClient = webClient;
    }

    @org.springframework.scheduling.annotation.Scheduled(initialDelay = 60000, fixedDelay = 60000)
    @EventListener(ApplicationReadyEvent.class)
    public void bootstrap() {
        properties.symbols().forEach(symbol ->
                properties.intervals().forEach(interval ->
                        (historyFresh(symbol, interval) ? reactor.core.publisher.Mono.<List<Candlestick>>empty() : loadSeries(symbol, interval))
                                .subscribe(
                                        candles -> {
                                            historyService.mergeBootstrap(symbol, interval, candles);

                                            persistenceService.ifAvailable(service ->
                                                    service.backfillClosed(candles)
                                                            .subscribe(count -> log.info(
                                                                    "Historical candles backfilled. symbol={}, interval={}, count={}",
                                                                    symbol,
                                                                    interval,
                                                                    count
                                                            ))
                                            );

                                            log.info(
                                                    "Historical candles loaded. symbol={}, interval={}, count={}",
                                                    symbol,
                                                    interval,
                                                    candles.size()
                                            );
                                        },
                                        error -> log.warn(
                                                "Historical candle bootstrap failed. symbol={}, interval={}",
                                                symbol,
                                                interval,
                                                error
                                        )
                                )
                )
        );
    }

    private boolean historyFresh(String symbol, String interval) {
        try {
            com.coinmind.market.service.MarketDataValidity.requireHistory(historyService.get(symbol, interval, 500), Instant.now());
            return true;
        } catch (IllegalStateException invalid) {
            return false;
        }
    }

    reactor.core.publisher.Mono<List<Candlestick>> loadSeries(
            String symbol,
            String interval
    ) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v3/klines")
                        .queryParam("symbol", symbol)
                        .queryParam("interval", interval)
                        .queryParam("limit", properties.historicalCandleLimit())
                        .build())
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(java.time.Duration.ofSeconds(10))
                .map(root -> parse(root, symbol, interval));
    }

    private List<Candlestick> parse(
            JsonNode root,
            String symbol,
            String interval
    ) {
        List<Candlestick> candles = new ArrayList<>();
        Instant now = Instant.now();

        if (!root.isArray()) {
            throw new IllegalArgumentException("Invalid Binance candle response");
        }

        for (JsonNode row : root) {
            if (!row.isArray() || row.size() < 9) {
                throw new IllegalArgumentException("Incomplete Binance candle row");
            }

            Instant closeTime = Instant.ofEpochMilli(row.get(6).asLong());

            candles.add(new Candlestick(
                    symbol,
                    interval,
                    Instant.ofEpochMilli(row.get(0).asLong()),
                    closeTime,
                    decimal(row.get(1)),
                    decimal(row.get(2)),
                    decimal(row.get(3)),
                    decimal(row.get(4)),
                    decimal(row.get(5)),
                    decimal(row.get(7)),
                    row.get(8).asLong(),
                    !closeTime.isAfter(now),
                    closeTime.isAfter(now) ? now : closeTime
            ));
        }

        com.coinmind.market.service.MarketDataValidity.requireHistory(candles, now);
        return candles;
    }

    private BigDecimal decimal(JsonNode node) {
        return new BigDecimal(node.asText("0"));
    }
}
