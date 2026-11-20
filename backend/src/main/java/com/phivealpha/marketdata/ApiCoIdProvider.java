package com.phivealpha.marketdata;

import com.phivealpha.newsintelligence.IdxHeadline;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Application-side compatibility contract for the fixture UI and API.co.id adapter. */
public interface ApiCoIdProvider {
    Flux<IdxQuote> quotes();
    Mono<IdxQuote> quote(String symbol);
    Flux<IdxHeadline> headlines();

    /** The weekly fixture is a demo ranking without an actionable signal. */
    default boolean fixtureRankingAvailable() {
        return false;
    }
}
