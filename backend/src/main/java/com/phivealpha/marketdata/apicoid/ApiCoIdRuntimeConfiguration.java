package com.phivealpha.marketdata.apicoid;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
class ApiCoIdRuntimeConfiguration {
    static final Set<ApiCoIdAccess.Dataset> FREE_IDX = Set.of(ApiCoIdAccess.Dataset.INSTRUMENTS,
            ApiCoIdAccess.Dataset.DAILY, ApiCoIdAccess.Dataset.MARKET_NEWS, ApiCoIdAccess.Dataset.SYMBOL_NEWS);

    @Bean
    ApiCoIdDiagnostics apiCoIdDiagnostics() {
        return new ApiCoIdDiagnostics();
    }

    @Bean
    @Profile("phivealpha-api-co-id | phivealpha-api-co-id-fx | phivealpha-api-co-id-holiday | phivealpha-developer")
    ApiCoIdAccess apiCoIdAccess() {
        return ApiCoIdAccess.load(System.getenv("PHIVEALPHA_API_APPROVAL_PATH"),
                System.getenv("PHIVEALPHA_API_QUOTA_LEDGER_PATH"), Clock.systemUTC());
    }

    @Bean
    @Profile("phivealpha-api-co-id-fx")
    ExchangeRateProvider exchangeRateProvider(ApiCoIdAccess access, ApiCoIdDiagnostics diagnostics,
            ObjectProvider<ApiCoIdAuditWriter> audit,
            @Value("${phivealpha.api-co-id-fx.cache-ttl:PT30M}") Duration ttl,
            @Value("${phivealpha.api-co-id-fx.max-age:PT2H}") Duration maxAge,
            @Value("${phivealpha.api-co-id-fx.max-retries:0}") int retries,
            @Value("${phivealpha.api-co-id-fx.timeout:PT5S}") Duration timeout) {
        var clock = Clock.systemUTC();
        var scope = access.restrictedToFree(Set.of(ApiCoIdAccess.Dataset.USD_IDR));
        return new ExchangeRateProvider(macroTransport(scope, clock, retries, timeout, audit.getIfAvailable()),
                scope, clock, ttl, maxAge, diagnostics);
    }

    @Bean
    @Profile("phivealpha-api-co-id-holiday")
    IndonesiaHolidayCalendarProvider holidayCalendarProvider(ApiCoIdAccess access, ApiCoIdDiagnostics diagnostics,
            ObjectProvider<ApiCoIdAuditWriter> audit,
            @Value("${phivealpha.api-co-id-holiday.cache-ttl:P30D}") Duration ttl) {
        var clock = Clock.systemUTC();
        var scope = access.restrictedToFree(Set.of(ApiCoIdAccess.Dataset.NATIONAL_HOLIDAYS));
        return new IndonesiaHolidayCalendarProvider(macroTransport(scope, clock, 0, Duration.ofSeconds(5),
                audit.getIfAvailable()), scope, clock, ttl, ttl, diagnostics);
    }

    static ApiCoIdSettings macroSettings(int retries, Duration timeout, int localBudget) {
        return new ApiCoIdSettings(URI.create(ApiCoIdMacroCache.ORIGIN), timeout, Duration.ofSeconds(30),
                1, Duration.ofMillis(250), localBudget, retries, Duration.ofMillis(500),
                3, Duration.ofMinutes(1), Duration.ofMinutes(1), 1, 1, 100, Map.of(), null, null, null);
    }

    private static ApiCoIdTransport macroTransport(ApiCoIdAccess access, Clock clock, int retries, Duration timeout,
                                                   ApiCoIdAuditWriter audit) {
        ApiCoIdConfiguration.rejectCi();
        var settings = macroSettings(retries, timeout, 100_000);
        var client = ApiCoIdConfiguration.client(settings, System.getenv("API_CO_ID_KEY")).build();
        return new ApiCoIdTransport(client, settings, clock, ignored -> {}, access, audit);
    }
}
