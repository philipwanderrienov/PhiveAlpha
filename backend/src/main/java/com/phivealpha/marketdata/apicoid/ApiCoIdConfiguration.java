package com.phivealpha.marketdata.apicoid;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
@Profile("phivealpha-api-co-id")
@EnableConfigurationProperties(ApiCoIdSettings.class)
class ApiCoIdConfiguration {
    @Bean
    ApiCoIdHttpProvider apiCoIdHttpProvider(ApiCoIdSettings settings, @Value("${API_CO_ID_KEY}") String apiKey) {
        if ("true".equalsIgnoreCase(System.getenv("CI")) || "true".equalsIgnoreCase(System.getenv("GITHUB_ACTIONS"))) {
            throw new IllegalStateException("The API.co.id network profile is disabled in CI");
        }
        var calendar = readCalendar(settings.calendarPath());
        var clock = Clock.systemUTC();
        return new ApiCoIdHttpProvider(settings, new ApiCoIdTransport(client(settings, apiKey).build(), settings, clock),
                clock, calendar);
    }

    static WebClient.Builder client(ApiCoIdSettings settings, String apiKey) {
        if (apiKey == null || !apiKey.matches("[!-~]{1,1024}")) {
            throw new IllegalArgumentException("Invalid API.co.id credential configuration");
        }
        var http = HttpClient.newConnection().followRedirect(false).disableRetry(true)
                .responseTimeout(settings.timeout());
        return WebClient.builder().clientConnector(new ReactorClientHttpConnector(http))
                .defaultHeader("x-api-co-id", apiKey)
                .codecs(codecs -> {
                    codecs.defaultCodecs().enableLoggingRequestDetails(false);
                    codecs.defaultCodecs().maxInMemorySize(1024 * 1024);
                });
    }

    static VersionedIdxCalendar readCalendar(String location) {
        if (location == null || location.isBlank()) {
            throw new IllegalArgumentException("Configure a reviewed IDX calendar manifest");
        }
        var path = Path.of(location);
        if (!Files.isRegularFile(path) || !path.isAbsolute()) {
            throw new IllegalArgumentException("IDX calendar manifest must be an absolute local file");
        }
        final int maxBytes = 4 * 1024 * 1024;
        try (var input = Files.newInputStream(path)) {
            byte[] bytes = input.readNBytes(maxBytes + 1);
            if (bytes.length > maxBytes) {
                throw new IllegalArgumentException("IDX calendar manifest exceeds size limit");
            }
            return new VersionedIdxCalendar(JsonMapper.builder().build().readTree(bytes));
        } catch (IOException | RuntimeException invalid) {
            throw new IllegalArgumentException("Invalid IDX calendar manifest");
        }
    }
}
