package com.phivealpha.marketdata;

import com.phivealpha.newsintelligence.IdxHeadline;
import com.phivealpha.stock.IdxStock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@Profile("!phivealpha-api-co-id")
public class FixtureApiCoIdProvider implements ApiCoIdProvider {
    private static final LocalDate SESSION = LocalDate.of(2026, 11, 13);

    private static final List<IdxQuote> QUOTES = List.of(
            new IdxQuote(new IdxStock("BBCA", "Bank Central Asia", "Keuangan"), SESSION, 9800, 9700),
            new IdxQuote(new IdxStock("BBRI", "Bank Rakyat Indonesia", "Keuangan"), SESSION, 4020, 3950),
            new IdxQuote(new IdxStock("BMRI", "Bank Mandiri", "Keuangan"), SESSION, 4900, 4800),
            new IdxQuote(new IdxStock("TLKM", "Telkom Indonesia", "Infrastruktur"), SESSION, 3020, 2990),
            new IdxQuote(new IdxStock("ASII", "Astra International", "Perindustrian"), SESSION, 5650, 5500),
            new IdxQuote(new IdxStock("ICBP", "Indofood CBP", "Barang Konsumen Primer"), SESSION, 10400, 10500)
    );

    private static final List<IdxHeadline> HEADLINES = List.of(
            new IdxHeadline("BBCA", "Contoh pemantauan sektor perbankan", "Fixture PhiveAlpha",
                    Instant.parse("2026-11-13T03:00:00Z")),
            new IdxHeadline("TLKM", "Contoh pemantauan sektor telekomunikasi", "Fixture PhiveAlpha",
                    Instant.parse("2026-11-13T02:00:00Z"))
    );

    @Override
    public boolean fixtureRankingAvailable() {
        return true;
    }

    @Override
    public Flux<IdxQuote> quotes() {
        return Flux.fromIterable(QUOTES);
    }

    @Override
    public Mono<IdxQuote> quote(String symbol) {
        return quotes().filter(quote -> quote.stock().symbol().equalsIgnoreCase(symbol)).next();
    }

    @Override
    public Flux<IdxHeadline> headlines() {
        return Flux.fromIterable(HEADLINES);
    }
}
