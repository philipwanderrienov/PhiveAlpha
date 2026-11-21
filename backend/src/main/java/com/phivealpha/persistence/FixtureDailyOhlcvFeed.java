package com.phivealpha.persistence;

import static com.phivealpha.marketdata.apicoid.ApiCoIdData.*;

import com.phivealpha.marketdata.apicoid.VersionedIdxCalendar;
import com.phivealpha.persistence.IdxRecords.AdjustmentStatus;
import com.phivealpha.screening.LiquidUniverse.CalendarReference;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

@Component
@Profile("!phivealpha-api-co-id")
public class FixtureDailyOhlcvFeed implements IdxDailyIngestionService.Feed {
    private static final LocalDate FIRST = LocalDate.parse("2026-11-11");
    private static final LocalDate LAST = LocalDate.parse("2026-11-13");
    private static final Instant OBSERVED = Instant.parse("2026-11-13T09:30:00Z");
    private static final String UNIVERSE_VERSION = "fixture-2026-11";
    private static final CalendarReference CALENDAR = new CalendarReference(
            new VersionedIdxCalendar(JsonMapper.builder().build().readTree("""
                    {"source":"PhiveAlpha synthetic sessions","version":"fixture-bei-v1","days":[
                      {"date":"2026-11-11","kind":"REGULAR","sessions":[{"open":"09:00","close":"16:15"}]},
                      {"date":"2026-11-12","kind":"HOLIDAY","sessions":[]},
                      {"date":"2026-11-13","kind":"SPECIAL","sessions":[{"open":"09:00","close":"11:30"}]}
                    ]}
                    """)), Instant.parse("2026-11-10T00:00:00Z"));

    public CalendarReference calendar() {
        return CALENDAR;
    }

    public String universeVersion() {
        return UNIVERSE_VERSION;
    }

    @Override
    public Mono<Snapshot<DailyBar>> dailyOhlcv(String symbol, LocalDate from, LocalDate through) {
        if (!"BBCA".equals(symbol) || !CALENDAR.calendar().covers(from, through)) {
            return Mono.just(new Snapshot<>(Quality.UNAVAILABLE, "FIXTURE_OUT_OF_SCOPE", List.of(), null));
        }
        var bars = List.of(bar(FIRST, 9790, 350_000, 3_426_500_000L), bar(LAST, 9800, 450_000, 4_410_000_000L));
        return Mono.just(new Snapshot<>(Quality.COMPLETE, "SYNTHETIC_FIXTURE",
                bars.stream().filter(bar -> !bar.tradingDate().isBefore(from)
                        && !bar.tradingDate().isAfter(through)).toList(),
                new Provenance(IdxDailyIngestionService.FIXTURE, "DAILY", OBSERVED, OBSERVED,
                        CALENDAR.calendar().source(), CALENDAR.calendar().version(), UNIVERSE_VERSION, OBSERVED)));
    }

    private static DailyBar bar(LocalDate day, long close, long volume, long turnover) {
        return new DailyBar("BBCA", day, BigDecimal.valueOf(close - 10), BigDecimal.valueOf(close + 20),
                BigDecimal.valueOf(close - 20), BigDecimal.valueOf(close), volume, BigDecimal.valueOf(turnover),
                AdjustmentStatus.UNADJUSTED);
    }
}
