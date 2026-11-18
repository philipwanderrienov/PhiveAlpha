package com.coinmind.ai.provider;

import com.coinmind.CoinMindApplication;
import com.coinmind.ai.config.GeminiProperties;
import com.coinmind.ai.service.AiNewsIntelligenceService;
import com.coinmind.market.model.*;
import com.coinmind.market.service.*;
import com.coinmind.news.service.NewsService;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.SpringApplication;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import reactor.netty.http.client.HttpClient;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.*;
import java.util.*;

/** Test-classpath-only process fixture. Never packaged in the production jar. */
public final class GeminiRecoveryE2eApplication {
    static final class TestClock extends Clock {
        private volatile Instant now;
        TestClock(Instant now) { this.now = now; }
        public Instant instant() { return now; }
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        synchronized void advance(long seconds) {
            if (seconds < 0 || seconds > 172800) throw new IllegalArgumentException("test advance bound");
            now = now.plusSeconds(seconds);
        }
    }

    public static void main(String[] args) {
        if (!"LOOPBACK_MOCKS_ONLY".equals(System.getenv("COINMIND_E2E_CONFIRM")) || args.length != 5)
            throw new IllegalStateException("Explicit isolated test confirmation required");
        int port = testPort(args[0]), mockPort = testPort(args[1]);
        Path state = Path.of(args[2]).toAbsolutePath().normalize();
        if (!state.startsWith(Path.of("/tmp")) || !state.toString().contains("coinmind-phase15-e2e-"))
            throw new IllegalArgumentException("Use a dedicated temporary E2E ledger");
        var clock = new TestClock(Instant.parse(args[3]));
        int quota = Integer.parseInt(args[4]);
        System.setProperty("coinmind.gemini.state-dir", state.toString());
        var app = new SpringApplication(CoinMindApplication.class);
        app.addInitializers(context -> context.addBeanFactoryPostProcessor(factory -> {
            var registry = (DefaultListableBeanFactory)factory;
            // Replace the fixed production endpoint only on the test classpath, before instantiation.
            registry.removeBeanDefinition("geminiAiProvider");
            registry.registerBeanDefinition("geminiAiProvider", new RootBeanDefinition(GeminiAiProvider.class, () -> {
                var client = WebClient.builder().baseUrl("http://127.0.0.1:" + mockPort)
                        .clientConnector(new ReactorClientHttpConnector(HttpClient.create().followRedirect(false).disableRetry(true)))
                        .codecs(c -> c.defaultCodecs().maxInMemorySize(65536)).build();
                var provider = new GeminiAiProvider(true,
                        new GeminiProperties(true, "gemini-e2e-mock", "e2e-placeholder-not-a-credential", 15000, quota), client, clock);
                try {
                    var field = GeminiAiProvider.class.getDeclaredField("recovery");
                    field.setAccessible(true);
                    field.set(provider, new GeminiRecoveryState(clock, state));
                } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
                return provider;
            }));
            registry.registerBeanDefinition("e2eControl", new RootBeanDefinition(Control.class,
                    () -> new Control(clock, context.getBean(NewsService.class), context.getBean(MarketTickerService.class),
                            context.getBean(OrderBookService.class), context.getBean(MarketHistoryService.class),
                            context.getBean(AiNewsIntelligenceService.class))));
        }));
        app.run(
                "--spring.profiles.active=coinmind-isolated-e2e",
                "--server.address=127.0.0.1", "--server.port=" + port,
                "--spring.config.location=classpath:/application.yml",
                "--spring.r2dbc.url=r2dbc:postgresql://127.0.0.1:1/unused",
                "--spring.r2dbc.username=unused", "--spring.r2dbc.password=unused",
                "--spring.sql.init.mode=never", "--management.health.r2dbc.enabled=false",
                "--coinmind.persistence.enabled=false", "--coinmind.ai.enabled=true", "--coinmind.ai.provider=gemini",
                "--coinmind.ai.api-key=", "--coinmind.ai.base-url=http://127.0.0.1:" + mockPort,
                "--coinmind.ai.gemini.news-enabled=true", "--coinmind.ai.gemini.model=gemini-e2e-mock",
                "--coinmind.ai.gemini.api-key=e2e-placeholder-not-a-credential",
                "--coinmind.ai.gemini.max-calls-per-day=" + quota,
                "--coinmind.ai.automatic-enabled=false",
                "--coinmind.billing.enabled=false", "--coinmind.push.enabled=false",
                "--coinmind.news.enabled=false", "--coinmind.news.retention-enabled=false",
                "--coinmind.news.rss-urls[0]=http://127.0.0.1:" + mockPort + "/rss",
                "--coinmind.news.rss-urls[1]=http://127.0.0.1:" + mockPort + "/rss",
                "--coinmind.futures.paper-replay-archive-enabled=false",
                "--coinmind.futures.offline-replay-job-enabled=false", "--coinmind.futures.market-data-enabled=false",
                "--coinmind.futures.base-url=http://127.0.0.1:" + mockPort,
                "--coinmind.market.binance-rest-base-url=http://127.0.0.1:" + mockPort,
                "--coinmind.market.binance-websocket-base-url=ws://127.0.0.1:" + mockPort,
                "--coinmind.manifold.base-url=http://127.0.0.1:" + mockPort);
    }

    private static int testPort(String value) {
        int port = Integer.parseInt(value);
        if (port < 1024 || port > 65535 || port == 8080 || port == 5432)
            throw new IllegalArgumentException("Dedicated unprivileged port required");
        return port;
    }

    // Deliberately inactive profile: manual registration above is the sole entrypoint.
    @Profile("coinmind-e2e-manual-registration-only")
    @RestController
    @RequestMapping("/__e2e")
    public static class Control {
        final TestClock clock;
        final NewsService news;
        final MarketTickerService tickers;
        final OrderBookService books;
        final MarketHistoryService history;
        final AiNewsIntelligenceService intelligence;
        Control(TestClock clock, NewsService news, MarketTickerService tickers, OrderBookService books,
                MarketHistoryService history, AiNewsIntelligenceService intelligence) {
            this.clock = clock; this.news = news; this.tickers = tickers; this.books = books; this.history = history;
            this.intelligence = intelligence;
        }
        @PostMapping("/advance/{seconds}")
        public Map<String, Object> advance(@PathVariable long seconds) {
            clock.advance(seconds); return Map.of("now", clock.instant());
        }
        @PostMapping("/news/{id}")
        public Map<String, Object> news(@PathVariable String id) {
            news.ingest(id, "Bitcoin E2E public headline " + id, "E2E publisher", "https://example.invalid/" + id,
                    "Public synthetic fixture", clock.instant().minusSeconds(1));
            return Map.of("id", id);
        }
        @PostMapping("/recover")
        public Map<String, Object> recover() {
            intelligence.recover();
            return Map.of("submitted", true);
        }
        @PostMapping("/market/{sequence}")
        public Map<String, Object> seed(@PathVariable int sequence) {
            Instant now = Instant.now();
            var price = BigDecimal.valueOf(70000 + sequence);
            tickers.publish(new TickerSnapshot("BTCUSDT", price, price, price.add(BigDecimal.TEN),
                    price.subtract(BigDecimal.TEN), BigDecimal.TEN, new BigDecimal("700000"), now));
            books.publish(new OrderBookSnapshot("BTCUSDT", price.subtract(BigDecimal.ONE), BigDecimal.ONE,
                    price.add(BigDecimal.ONE), BigDecimal.ONE, new BigDecimal("2"), price, now));
            for (String interval : List.of("1m", "5m", "15m", "1h", "4h", "1d")) {
                var candles = new ArrayList<Candlestick>();
                for (int i = 0; i < 250; i++) {
                    var p = BigDecimal.valueOf(69000 + i * 4);
                    Instant open = now.minusSeconds((250L - i) * 60);
                    candles.add(new Candlestick("BTCUSDT", interval, open, open.plusSeconds(59), p,
                            p.add(BigDecimal.TEN), p.subtract(BigDecimal.TEN), p.add(BigDecimal.ONE),
                            BigDecimal.TEN, new BigDecimal("700000"), 100, true, now));
                }
                history.mergeBootstrap("BTCUSDT", interval, candles);
            }
            return Map.of("sequence", sequence);
        }
    }
}
