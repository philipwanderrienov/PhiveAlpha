package com.phivealpha.screening;

import static com.phivealpha.screening.LiquidUniverse.*;

import com.phivealpha.marketdata.apicoid.VersionedIdxCalendar;
import com.phivealpha.persistence.IdxDailyIngestionService;
import com.phivealpha.persistence.IdxMarketCalendar;
import com.phivealpha.persistence.IdxRecords.AdjustmentStatus;
import com.phivealpha.persistence.IdxRecords.DailyPrice;
import com.phivealpha.screening.news.NewsScoring;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import tools.jackson.databind.json.JsonMapper;

/** Deterministic, synthetic data vintage for the read-only demo. */
public final class StockScreenerFixture {
    public static final Instant CUTOFF = Instant.parse("2026-11-13T10:00:00Z");
    private static final LocalDate LAST = CUTOFF.atZone(IdxMarketCalendar.ZONE).toLocalDate();
    private static final List<String> ACTIVE = List.of("ASII", "BBCA", "BBRI", "BMRI", "ICBP", "TLKM");
    private static final String UNIVERSE = "screener-fixture-universe-v1";
    private static final String CALENDAR = "screener-fixture-calendar-v1";
    private static final Instant AVAILABLE = CUTOFF.minusSeconds(60);

    public record Data(StockScreenerService.Context context, List<DailyPrice> prices) {
        public Data {
            prices = List.copyOf(prices);
        }
    }

    private StockScreenerFixture() {}

    public static Data data() {
        var manifest = JsonMapper.builder().build().createObjectNode();
        manifest.put("source", "PhiveAlpha synthetic trading calendar");
        manifest.put("version", CALENDAR);
        var days = manifest.putArray("days");
        LocalDate first = LAST.minusDays(42);
        for (LocalDate date = first; !date.isAfter(LAST); date = date.plusDays(1)) {
            boolean closed = date.getDayOfWeek() == DayOfWeek.SATURDAY
                    || date.getDayOfWeek() == DayOfWeek.SUNDAY || date.equals(LAST.minusDays(1));
            var day = days.addObject();
            day.put("date", date.toString());
            day.put("kind", closed ? "HOLIDAY" : date.equals(LAST) ? "SPECIAL" : "REGULAR");
            var sessions = day.putArray("sessions");
            if (!closed) {
                sessions.addObject().put("open", "09:00").put("close", date.equals(LAST) ? "11:30" : "16:00");
            }
        }

        var calendar = new VersionedIdxCalendar(manifest);
        var calendarRef = new CalendarReference(calendar, first.atStartOfDay(ZoneOffset.UTC).toInstant());
        var members = new ArrayList<Member>();
        var factors = new HashMap<String, StockScreenerService.Factors>();
        var prices = new ArrayList<DailyPrice>();
        var evidence = new NewsScoring.Evidence("PhiveAlpha synthetic factors", "screener-fixture-factors-v1",
                AVAILABLE, AVAILABLE, NewsScoring.Quality.AVAILABLE);
        for (String symbol : ACTIVE) {
            members.add(member(symbol, ListingStatus.ACTIVE));
            factors.put(symbol, new StockScreenerService.Factors(
                    new NewsScoring.Snapshot<>(evidence, List.of()),
                    new NewsScoring.Snapshot<>(evidence, new NewsScoring.Flow(
                            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.valueOf(5_000_000_000L)))));
            for (LocalDate date = first; !date.isAfter(LAST); date = date.plusDays(1)) {
                var close = calendar.sessionClose(date);
                if (close.isEmpty()) {
                    continue;
                }
                var observed = close.orElseThrow().plusSeconds(60).atOffset(ZoneOffset.UTC);
                var price = BigDecimal.valueOf(8_000 + date.toEpochDay() - first.toEpochDay());
                prices.add(new DailyPrice(symbol, date, price, price.add(BigDecimal.TEN),
                        price.subtract(BigDecimal.TEN), price, 625_000, BigDecimal.valueOf(5_000_000_000L),
                        IdxDailyIngestionService.FIXTURE + "/daily/" + CALENDAR + "/" + UNIVERSE,
                        observed, observed.plusSeconds(20), AdjustmentStatus.UNADJUSTED));
            }
        }
        members.add(member("SUSP", ListingStatus.SUSPENDED));
        var universe = new Reference(UNIVERSE, AVAILABLE, AVAILABLE, "PhiveAlpha synthetic listing vintage", members);
        var market = new NewsScoring.Snapshot<>(evidence,
                new NewsScoring.Regime(BigDecimal.ZERO, new BigDecimal("0.25")));
        return new Data(new StockScreenerService.Context(universe, calendarRef, factors, market), prices);
    }

    private static Member member(String symbol, ListingStatus status) {
        return new Member(symbol, SecurityType.COMMON_STOCK, status, LAST.minusYears(2), CorporateActionStatus.CLEAR);
    }
}
