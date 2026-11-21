package com.phivealpha.screening;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/idx/v1")
public class StockScreenerController {
    private final StockScreenerService service;
    private final boolean persisted;
    private final StockScreenerFixture.Data fixture = StockScreenerFixture.data();

    public StockScreenerController(StockScreenerService service,
            @Value("${phivealpha.stock-screener.persisted:false}") boolean persisted) {
        this.service = service;
        this.persisted = persisted;
    }

    @GetMapping("/stock-screener")
    public Mono<StockScreenerService.ScreeningSnapshot> snapshot() {
        return persisted ? service.screenPersisted(fixture.context(), StockScreenerFixture.CUTOFF)
                : Mono.fromSupplier(() -> service.screenFixture(fixture.context(), fixture.prices(),
                        StockScreenerFixture.CUTOFF));
    }
}
