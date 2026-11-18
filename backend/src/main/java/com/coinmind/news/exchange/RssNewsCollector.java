package com.coinmind.news.exchange;

import com.coinmind.news.config.NewsProperties;
import com.coinmind.news.service.NewsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import reactor.netty.http.client.HttpClient;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.net.URI;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class RssNewsCollector {

    private static final Logger log = LoggerFactory.getLogger(RssNewsCollector.class);

    private final NewsProperties properties;
    private final NewsService newsService;
    private final WebClient webClient;

    private final java.util.Map<String, SourceStatus> health = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.time.Duration RESPONSE_TIMEOUT = java.time.Duration.ofSeconds(10);

    /** UNKNOWN: never completed; UP: valid feed with usable dated articles;
     * EMPTY: valid feed without usable articles; UNAVAILABLE: transport/HTTP/XML failure. */
    public record SourceStatus(String source, String state, Instant lastCheckedAt,
                               Instant lastSuccessAt, String error, int articleCount, int skippedInvalidDate) {}
    public record SourceHealth(java.util.List<SourceStatus> sources, Instant checkedAt) {}

    public SourceHealth sourceHealth() {
        var urls = properties.rssUrls() == null ? java.util.List.<String>of() : properties.rssUrls();
        return new SourceHealth(urls.stream().filter(u -> u != null && !u.isBlank()).distinct()
                .map(u -> health.getOrDefault(u, new SourceStatus(host(u), "UNKNOWN", null, null, null, 0, 0)))
                .toList(), Instant.now());
    }

    public RssNewsCollector(
            NewsProperties properties,
            NewsService newsService
    ) {
        this.properties = properties;
        this.newsService = newsService;

        HttpClient httpClient = HttpClient.create()
                .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .responseTimeout(RESPONSE_TIMEOUT)
                .followRedirect(true);

        this.webClient = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader(
                        HttpHeaders.USER_AGENT,
                        "Mozilla/5.0 (compatible; CoinMindAI/0.1; +https://github.com/philipwanderrienov/CoinMind-AI)"
                )
                .defaultHeader(
                        HttpHeaders.ACCEPT,
                        "application/rss+xml, application/atom+xml, application/xml, text/xml;q=0.9, */*;q=0.8"
                )
                .build();
    }

    @Scheduled(fixedDelayString = "${coinmind.news.refresh-interval-ms:300000}")
    public void refresh() {
        if (!properties.enabled() || properties.rssUrls() == null) {
            return;
        }

        properties.rssUrls().stream()
                .filter(url -> url != null && !url.isBlank())
                .distinct()
                .forEach(this::fetch);
    }

    private void fetch(String url) {
        fetchResult(url).subscribe();
    }

    // Deferred so malformed URIs and parser failures use the same health/error path.
    reactor.core.publisher.Mono<Void> fetchResult(String url) {
        return reactor.core.publisher.Mono.defer(() -> webClient.get().uri(URI.create(url))
                .exchangeToMono(response -> {
                    if (!response.statusCode().is2xxSuccessful()) {
                        return response.releaseBody().then(reactor.core.publisher.Mono.error(
                                new IllegalStateException("HTTP_" + response.statusCode().value())));
                    }
                    return response.bodyToMono(String.class).defaultIfEmpty("");
                }))
                .timeout(RESPONSE_TIMEOUT)
                .doOnNext(xml -> parse(url, xml))
                .onErrorResume(error -> {
                    String reason = error instanceof IllegalStateException && error.getMessage() != null
                            && error.getMessage().matches("HTTP_[0-9]{3}") ? error.getMessage()
                            : error instanceof java.util.concurrent.TimeoutException ? "TIMEOUT" : "FETCH_OR_XML_ERROR";
                    health.compute(url, (key, old) -> new SourceStatus(host(url), "UNAVAILABLE",
                            Instant.now(), old == null ? null : old.lastSuccessAt(), reason, 0, 0));
                    log.warn("RSS source unavailable: source={}, reason={}", host(url), reason);
                    return reactor.core.publisher.Mono.empty();
                }).then();
    }

    private void parse(String sourceUrl, String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setExpandEntityReferences(false);
            Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
            String root = document.getDocumentElement().getTagName();
            if (!root.equals("rss") && !root.equals("rdf:RDF")) throw new IllegalArgumentException("Invalid RSS root");
            NodeList items = document.getElementsByTagName("item");
            int count = 0;
            int skipped = 0;
            for (int i = 0; i < items.getLength(); i++) {
                Element item = (Element) items.item(i);
                Instant date = parseDate(text(item, "pubDate"));
                if (date == null || date.isAfter(Instant.now())) { skipped++; continue; }
                String title = text(item, "title");
                if (title.isBlank()) continue;
                String link = text(item, "link");
                String guid = text(item, "guid");
                String id = !guid.isBlank() ? guid : (!link.isBlank() ? link : sourceUrl + "#" + title.hashCode());
                newsService.ingest(id, title, host(sourceUrl), link, stripHtml(text(item, "description")), date);
                count++;
            }
            health.put(sourceUrl, new SourceStatus(host(sourceUrl), count > 0 ? "UP" : "EMPTY",
                    Instant.now(), Instant.now(), skipped > 0 ? "INVALID_PUBLICATION_DATE_SKIPPED" : null, count, skipped));
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid RSS XML", ex);
        }
    }

    private String text(Element element, String tag) {
        NodeList nodes = element.getElementsByTagName(tag);
        return nodes.getLength() == 0 ? "" : nodes.item(0).getTextContent().trim();
    }

    private String stripHtml(String value) {
        return value == null ? "" : value.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
    }

    private Instant parseDate(String value) {
        try {
            return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
        } catch (Exception ignored) {
            return null;
        }
    }

    private String host(String url) {
        try {
            return URI.create(url).getHost();
        } catch (Exception ignored) {
            return "rss";
        }
    }
}
