package com.phivealpha.screening;

import com.phivealpha.marketdata.ApiCoIdProvider;
import com.phivealpha.marketdata.IdxQuote;
import java.util.Comparator;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;

@Service
public class WeeklyScreening {
    private final ApiCoIdProvider provider;

    public WeeklyScreening(ApiCoIdProvider provider) {
        this.provider = provider;
    }

    public Flux<IdxQuote> topFive() {
        if (!provider.fixtureRankingAvailable()) {
            return Flux.error(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "UNVERIFIED"));
        }
        return provider.quotes()
                .sort(Comparator.comparing(IdxQuote::weeklyChangePercent).reversed()
                        .thenComparing(quote -> quote.stock().symbol()))
                .take(5);
    }
}
