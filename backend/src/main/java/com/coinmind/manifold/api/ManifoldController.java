package com.coinmind.manifold.api;

import com.coinmind.manifold.model.ManifoldIntelligence;
import com.coinmind.manifold.model.PredictionSourceHealth;
import com.coinmind.manifold.service.ManifoldIntelligenceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/manifold")
public class ManifoldController {

    private final ManifoldIntelligenceService service;

    public ManifoldController(ManifoldIntelligenceService service) {
        this.service = service;
    }

    @GetMapping("/source-health")
    public PredictionSourceHealth sourceHealth() {
        return service.sourceHealth();
    }

    @GetMapping("/{symbol}")
    public Mono<ManifoldIntelligence> latest(@PathVariable String symbol) {
        ManifoldIntelligence cached = service.latestFresh(symbol);
        return cached == null
                ? service.refresh(symbol)
                : Mono.just(cached);
    }

    @PostMapping("/{symbol}/refresh")
    public Mono<ManifoldIntelligence> refresh(@PathVariable String symbol) {
        return service.refresh(symbol);
    }
}
