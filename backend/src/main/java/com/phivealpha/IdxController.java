package com.phivealpha;

import com.phivealpha.marketdata.ApiCoIdProvider;
import com.phivealpha.marketdata.IdxQuote;
import com.phivealpha.newsintelligence.IdxHeadline;
import com.phivealpha.risk.PaperTradePreview;
import com.phivealpha.screening.WeeklyScreening;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/idx/v1")
public class IdxController {
    private final ApiCoIdProvider provider;
    private final WeeklyScreening screening;
    private final PaperTradePreview paperTrade;

    public IdxController(ApiCoIdProvider provider, WeeklyScreening screening, PaperTradePreview paperTrade) {
        this.provider = provider;
        this.screening = screening;
        this.paperTrade = paperTrade;
    }

    @GetMapping("/stocks")
    public Mono<List<StockSnapshot>> stocks() {
        return provider.quotes().map(StockSnapshot::from).collectList();
    }

    @GetMapping("/stocks/{symbol}")
    public Mono<StockSnapshot> stock(@PathVariable String symbol) {
        return provider.quote(symbol).map(StockSnapshot::from)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown IDX symbol")));
    }

    @GetMapping("/weekly-top-five")
    public Mono<List<StockSnapshot>> weeklyTopFive() {
        return screening.topFive().map(StockSnapshot::from).collectList();
    }

    @GetMapping("/headlines")
    public Mono<List<IdxHeadline>> headlines() {
        return provider.headlines().collectList();
    }

    @GetMapping("/paper-trade/preview")
    public Mono<PaperTradePreview.Estimate> preview(@RequestParam String symbol, @RequestParam int lots) {
        return paperTrade.estimate(symbol, lots);
    }

    public record StockSnapshot(
            String symbol, String name, String sector, LocalDate sessionDate,
            long closeIdr, BigDecimal weeklyChangePercent
    ) {
        static StockSnapshot from(IdxQuote quote) {
            var stock = quote.stock();
            return new StockSnapshot(stock.symbol(), stock.name(), stock.sector(), quote.sessionDate(),
                    quote.closeIdr(), quote.weeklyChangePercent());
        }
    }
}
