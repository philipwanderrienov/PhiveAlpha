package com.phivealpha.risk;

import com.phivealpha.marketdata.ApiCoIdProvider;
import com.phivealpha.stock.IdxStock;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@Service
public class PaperTradePreview {
    private final ApiCoIdProvider provider;

    public PaperTradePreview(ApiCoIdProvider provider) {
        this.provider = provider;
    }

    public Mono<Estimate> estimate(String symbol, int lots) {
        if (lots < 1 || lots > 10_000) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lots must be between 1 and 10000"));
        }

        int shares = lots * IdxStock.SHARES_PER_LOT;
        return provider.quote(symbol)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown IDX symbol")))
                .map(quote -> new Estimate(quote.stock().symbol(), lots, shares, quote.closeIdr(), shares * quote.closeIdr()));
    }

    public record Estimate(String symbol, int lots, int shares, long priceIdr, long grossIdr) {}
}
