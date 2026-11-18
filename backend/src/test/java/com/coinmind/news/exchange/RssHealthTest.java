package com.coinmind.news.exchange;

import com.coinmind.news.config.NewsProperties;
import com.coinmind.news.service.NewsService;
import com.coinmind.news.api.NewsController;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.*;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class RssHealthTest {
    final NewsService news = mock(NewsService.class);
    final String url = "https://www.coindesk.com/feed";
    RssNewsCollector collector = new RssNewsCollector(new NewsProperties(true, 300000,
            java.util.List.of(url, "https://cointelegraph.com/rss"), false, 1, 1), news);
    void client(Mono<ClientResponse> response) throws Exception {
        var field = RssNewsCollector.class.getDeclaredField("webClient");
        field.setAccessible(true);
        field.set(collector, WebClient.builder().exchangeFunction(r -> response).build());
    }
    Mono<ClientResponse> response(HttpStatus status, String body) {
        return Mono.just(ClientResponse.create(status).body(body).build());
    }
    @Test void failuresAndRecoveryAndDates() throws Exception {
        for (var failure : java.util.List.of(Mono.<ClientResponse>error(new java.util.concurrent.TimeoutException()),
                response(HttpStatus.FORBIDDEN, "secret"), response(HttpStatus.OK, "<rss>"),
                response(HttpStatus.OK, "<html/>"))) {
            client(failure); collector.fetchResult(url).block();
            assertThat(collector.sourceHealth().sources().getFirst().state()).isEqualTo("UNAVAILABLE");
            assertThat(collector.sourceHealth().sources().getFirst().lastCheckedAt()).isNotNull();
        }
        client(response(HttpStatus.OK, "<rss><channel><item><title>Bitcoin</title><pubDate>bad</pubDate></item><item><title>Ethereum</title></item></channel></rss>"));
        collector.fetchResult(url).block();
        assertThat(collector.sourceHealth().sources().getFirst().state()).isEqualTo("EMPTY");
        assertThat(collector.sourceHealth().sources().getFirst().skippedInvalidDate()).isEqualTo(2);
        verifyNoInteractions(news);
        for (String source : java.util.List.of(url, "https://cointelegraph.com/rss")) {
            client(response(HttpStatus.OK, "<rss><channel><item><title>Bitcoin</title><guid>valid</guid><pubDate>Mon, 16 Nov 2020 10:00:00 GMT</pubDate></item></channel></rss>"));
            collector.fetchResult(source).block();
        }
        assertThat(collector.sourceHealth().sources()).allSatisfy(s -> {
            assertThat(s.state()).isEqualTo("UP"); assertThat(s.lastSuccessAt()).isNotNull();
        });
        verify(news, times(2)).ingest(eq("valid"), eq("Bitcoin"), anyString(), eq(""), eq(""), eq(java.time.Instant.parse("2020-11-16T10:00:00Z")));
    }
    @Test void stalledHttpTimesOutAndRetainsLastSuccess() throws Exception {
        client(response(HttpStatus.OK, "<rss><channel/></rss>"));
        collector.fetchResult(url).block();
        var success = collector.sourceHealth().sources().getFirst().lastSuccessAt();
        client(Mono.never());
        reactor.test.StepVerifier.withVirtualTime(() -> collector.fetchResult(url))
                .thenAwait(java.time.Duration.ofSeconds(11)).verifyComplete();
        var status = collector.sourceHealth().sources().getFirst();
        assertThat(status.state()).isEqualTo("UNAVAILABLE");
        assertThat(status.error()).isEqualTo("TIMEOUT");
        assertThat(status.lastSuccessAt()).isEqualTo(success);
        verifyNoInteractions(news);
    }
    @Test void endpointOnlyReadsSnapshot() throws Exception {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var field = RssNewsCollector.class.getDeclaredField("webClient"); field.setAccessible(true);
        field.set(collector, WebClient.builder().exchangeFunction(r -> { calls.incrementAndGet(); return Mono.error(new AssertionError("network")); }).build());
        var http = org.springframework.test.web.reactive.server.WebTestClient.bindToController(new NewsController(news, collector)).build();
        http.get().uri("/api/v1/news/source-health").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.sources[0].state").isEqualTo("UNKNOWN");
        assertThat(calls).hasValue(0); verifyNoInteractions(news);
    }
}
