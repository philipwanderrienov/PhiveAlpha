package com.phivealpha.marketdata.apicoid;

import static com.phivealpha.marketdata.apicoid.ApiCoIdPayload.*;

import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public final class IndonesiaHolidayCalendarProvider {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final ApiCoIdMacroCache<Integer, MacroData.NationalCalendar> cache;

    IndonesiaHolidayCalendarProvider(ApiCoIdTransport transport, ApiCoIdAccess access, Clock clock, Duration ttl,
                                     Duration maxAge, ApiCoIdDiagnostics diagnostics) {
        cache = new ApiCoIdMacroCache<>(transport, access, ApiCoIdAccess.Dataset.NATIONAL_HOLIDAYS,
                clock, ttl, maxAge, diagnostics);
    }

    /** Civic calendar only. This type does not implement the BEI market-calendar interface. */
    public Mono<MacroData.Snapshot<MacroData.NationalCalendar>> holidays(int year) {
        if (year < 2000 || year > 2100) {
            return Mono.error(new IllegalArgumentException("Unsupported calendar year"));
        }
        var uri = URI.create(ApiCoIdMacroCache.ORIGIN + "/holidays/indonesia/?year=" + year + "&page=1");
        return cache.get(year, uri, (json, fetchedAt) -> parse(json, fetchedAt, year));
    }

    public Mono<MacroData.Snapshot<MacroData.NationalCalendar>> cachedVersion(int year, String version) {
        if (year < 2000 || year > 2100 || version == null || !version.matches("[0-9a-f]{64}")) {
            return Mono.error(new IllegalArgumentException("Unsupported calendar year or version"));
        }
        return cache.cachedVersion(year, version);
    }

    private static ApiCoIdMacroCache.Value<MacroData.NationalCalendar> parse(JsonNode root, Instant fetchedAt, int year) {
        var rows = array(root, "data");
        var paging = root.path("paging");
        require(integer(paging, "page", 1) && integer(paging, "size", 100)
                && integer(paging, "total_page", 1) && integer(paging, "total_item", rows.size())
                && rows.size() > 0 && rows.size() <= 100, "INCOMPLETE_HOLIDAY_PAGE");

        var holidays = new ArrayList<MacroData.Holiday>();
        var ids = new HashSet<String>();
        for (var row : rows) {
            var id = text(row, "id");
            var rawDate = text(row, "date");
            require(rawDate.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"), "INVALID_HOLIDAY_DATE");
            var date = LocalDate.parse(rawDate);
            require(date.getYear() == year && ids.add(id), "HOLIDAY_YEAR_OR_DUPLICATE");
            var national = row.path("is_national_holiday");
            require(national.isBoolean(), "HOLIDAY_SCOPE_UNKNOWN");
            var name = text(row, "name");
            var type = text(row, "type");
            if (national.asBoolean()) {
                holidays.add(new MacroData.Holiday(id, date, name, type));
            }
        }
        holidays.sort(Comparator.comparing(MacroData.Holiday::date).thenComparing(MacroData.Holiday::id));
        var calendar = new MacroData.NationalCalendar(year, holidays);
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(JSON.writeValueAsBytes(calendar));
            return new ApiCoIdMacroCache.Value<>(calendar, null, fetchedAt, HexFormat.of().formatHex(digest));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static boolean integer(JsonNode data, String field, int expected) {
        var value = data.path(field);
        return value.isIntegralNumber() && value.canConvertToInt() && value.asInt() == expected;
    }
}
