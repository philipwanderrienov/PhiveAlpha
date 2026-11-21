package com.phivealpha.screening;

import static com.phivealpha.screening.LiquidUniverse.*;
import static com.phivealpha.screening.StockScreenerFixture.CUTOFF;
import static org.assertj.core.api.Assertions.assertThat;

import com.phivealpha.persistence.IdxRecords.DailyPrice;
import com.phivealpha.screening.news.NewsDrivenScoringService;
import com.phivealpha.screening.news.NewsScoring;
import com.phivealpha.screening.news.NewsScoringPolicy;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

class StockScreenerServiceTest {
    private final StockScreenerFixture.Data fixture = StockScreenerFixture.data();
    private final StockScreenerService service = service(20, Duration.ofDays(7));

    @Test
    void capsTiedRankingAndReportsEligibilityAndFactorProvenance() {
        var result = screen(fixture.prices());
        assertThat(result.mode()).isEqualTo(StockScreenerService.Mode.FIXTURE);
        assertThat(result.cutoff()).isEqualTo(CUTOFF);
        assertThat(result.generatedAt()).isNotNull();
        assertThat(result.dataAsOf()).isBefore(CUTOFF);
        assertThat(result.quality()).isEqualTo(StockScreenerService.Quality.COMPLETE);
        assertThat(ranked(result)).containsExactly("ASII", "BBCA", "BBRI", "BMRI", "ICBP");
        assertThat(result.scoring().purpose()).isEqualTo("RESEARCH_ONLY");
        assertThat(result.scoring().ranking()).allSatisfy(row -> {
            assertThat(row.assessment().factors()).hasSize(5).allSatisfy(factor -> {
                assertThat(factor.evidence().availableAt()).isBeforeOrEqualTo(CUTOFF);
                assertThat(factor.contribution()).isNotNull();
            });
        });
        var suspended = decision(result, "SUSP");
        assertThat(suspended.status()).isEqualTo(Eligibility.INELIGIBLE);
        assertThat(suspended.liquidity().rejectionReasons()).extracting(Issue::reason).containsExactly(Reason.SUSPENDED);
        var reversed = new ArrayList<>(fixture.prices());
        Collections.reverse(reversed);
        assertThat(screen(reversed).scoring()).isEqualTo(result.scoring());
    }

    @Test
    void rejectsMissingAndDuplicateCandlesButRanksOtherCompleteSymbols() {
        var prices = new ArrayList<>(fixture.prices());
        var missing = prices.stream().filter(row -> row.symbol().equals("ASII")).toList().getLast();
        var duplicate = prices.stream().filter(row -> row.symbol().equals("BBCA")).toList().getLast();
        prices.remove(missing);
        prices.add(duplicate);
        var result = screen(prices);
        assertThat(ranked(result)).doesNotContain("ASII", "BBCA");
        assertRejected(result, "ASII", Reason.MISSING_CANDLE);
        assertRejected(result, "BBCA", Reason.DUPLICATE_CANDLE);
    }

    @Test
    void rejectsStalePricesAndUnreviewedCorporateAction() {
        var stale = service(20, Duration.ofMinutes(30)).screenFixture(fixture.context(), fixture.prices(), CUTOFF);
        assertThat(stale.quality()).isEqualTo(StockScreenerService.Quality.STALE);
        assertRejected(stale, "ASII", Reason.STALE_PRICE);
        assertThat(ranked(stale)).isEmpty();

        var context = fixture.context();
        var universe = context.universe();
        var original = universe.members().getFirst();
        var ambiguous = new Member(original.symbol(), original.securityType(), original.listingStatus(),
                original.listedOn(), CorporateActionStatus.AMBIGUOUS);
        var changed = new Reference(universe.version(), universe.asOf(), universe.availableAt(), universe.source(),
                List.of(ambiguous));
        var blocked = service.screenFixture(new StockScreenerService.Context(changed, context.calendar(),
                context.factors(), context.market()), fixture.prices(), CUTOFF);
        assertRejected(blocked, "ASII", Reason.CORPORATE_ACTION_UNVERIFIED);
        assertThat(ranked(blocked)).isEmpty();
    }

    @Test
    void excludesFutureUniverseFutureCandleAndFutureFirstSeenNews() {
        var context = fixture.context();
        var universe = context.universe();
        var future = new Reference("later-vintage", universe.asOf(), CUTOFF.plusSeconds(1),
                universe.source(), universe.members());
        var blocked = service.screenFixture(new StockScreenerService.Context(future, context.calendar(),
                context.factors(), context.market()), fixture.prices(), CUTOFF);
        assertThat(blocked.decisions()).isEmpty();
        assertThat(blocked.liquidity().referenceIssues()).extracting(Issue::reason)
                .containsExactly(Reason.REFERENCE_NOT_AVAILABLE_AT_CUTOFF);

        var original = fixture.prices().stream().filter(row -> row.symbol().equals("ASII")).toList().getLast();
        var futurePrice = new DailyPrice(original.symbol(), original.tradingDate(), original.open(), original.high(),
                original.low(), original.close(), original.volume(), original.turnover(), original.source(),
                original.observedAt(), CUTOFF.plusSeconds(1).atOffset(ZoneOffset.UTC), original.adjustmentStatus());
        var prices = new ArrayList<>(fixture.prices());
        prices.add(futurePrice);
        var factors = new HashMap<>(context.factors());
        var asii = factors.get("ASII");
        var lateEvent = new NewsScoring.Event("later", "after cutoff", CUTOFF.minusSeconds(3600),
                CUTOFF.plusSeconds(1), CUTOFF.plusSeconds(2), BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ONE, BigDecimal.ZERO);
        factors.put("ASII", new StockScreenerService.Factors(
                new NewsScoring.Snapshot<>(asii.news().evidence(), List.of(lateEvent)), asii.flow()));
        var result = service.screenFixture(new StockScreenerService.Context(universe, context.calendar(),
                factors, context.market()), prices, CUTOFF);
        assertThat(result.scoring()).isEqualTo(screen(fixture.prices()).scoring());
    }

    @Test
    void requiresFullMomentumWindowEvenWithShorterLiquidityPolicy() {
        var prices = new ArrayList<>(fixture.prices());
        var asii = prices.stream().filter(row -> row.symbol().equals("ASII")).toList();
        var missing = asii.get(asii.size() - 11);
        prices.remove(missing);
        var result = service(5, Duration.ofDays(7)).screenFixture(fixture.context(), prices, CUTOFF);
        var decision = decision(result, "ASII");
        assertThat(decision.liquidity().eligibility()).isEqualTo(Eligibility.ELIGIBLE);
        assertThat(decision.status()).isEqualTo(Eligibility.UNVERIFIED);
        assertThat(decision.momentumIssue()).isEqualTo(new Issue(Reason.MISSING_CANDLE, missing.tradingDate()));
        assertThat(decision.assessment().factors()).filteredOn(factor ->
                factor.factor() == NewsScoring.Factor.PRICE_MOMENTUM).singleElement()
                .satisfies(factor -> assertThat(factor.quality()).isEqualTo(NewsScoring.Quality.UNAVAILABLE));
        assertThat(ranked(result)).doesNotContain("ASII");
    }

    @Test
    void failsClosedWhenPersistenceProfileIsDisabled() {
        var result = service.screenPersisted(fixture.context(), CUTOFF).block();
        assertThat(result.quality()).isEqualTo(StockScreenerService.Quality.UNAVAILABLE);
        assertThat(result.failure()).isEqualTo(StockScreenerService.Failure.PERSISTENCE_DISABLED);
        assertThat(result.decisions()).isEmpty();
    }

    private StockScreenerService.ScreeningSnapshot screen(List<DailyPrice> prices) {
        return service.screenFixture(fixture.context(), prices, CUTOFF);
    }

    private static StockScreenerService service(int lookback, Duration maxPriceAge) {
        var policy = new LiquidityPolicy(lookback, 20, 1, BigDecimal.ONE, 1, BigDecimal.ONE, BigDecimal.ONE,
                1, maxPriceAge, Duration.ofDays(7));
        var score = new NewsScoringPolicy(BigDecimal.valueOf(30), BigDecimal.valueOf(25), BigDecimal.valueOf(20),
                BigDecimal.valueOf(15), BigDecimal.TEN, Duration.ofDays(7), Duration.ofDays(7));
        return new StockScreenerService(new LiquidUniverseService(policy), new NewsDrivenScoringService(score),
                new StaticListableBeanFactory().getBeanProvider(com.phivealpha.persistence.IdxDailyIngestionService.class));
    }

    private static List<String> ranked(StockScreenerService.ScreeningSnapshot result) {
        return result.scoring().ranking().stream().map(row -> row.assessment().symbol()).toList();
    }

    private static StockScreenerService.Decision decision(StockScreenerService.ScreeningSnapshot result, String symbol) {
        return result.decisions().stream().filter(row -> row.symbol().equals(symbol)).findFirst().orElseThrow();
    }

    private static void assertRejected(StockScreenerService.ScreeningSnapshot result, String symbol, Reason reason) {
        var decision = decision(result, symbol);
        assertThat(decision.status()).isEqualTo(Eligibility.UNVERIFIED);
        assertThat(decision.liquidity().rejectionReasons()).extracting(Issue::reason).contains(reason);
    }
}
