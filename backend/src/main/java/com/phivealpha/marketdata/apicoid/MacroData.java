package com.phivealpha.marketdata.apicoid;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class MacroData {
    private MacroData() {}

    public enum Quality { UNAVAILABLE, STALE, INCOMPLETE, UNVERIFIED, VERIFIED }

    public record Provenance(String provider, String dataset, Instant asOf, Instant fetchedAt, String version) {}

    public record Snapshot<T>(String purpose, Quality quality, String reason, T data, Provenance provenance) {}

    public record ExchangeRate(String base, String quote, BigDecimal rate) {}
    public record Holiday(String id, LocalDate date, String name, String type) {}
    public record NationalCalendar(int year, List<Holiday> holidays) {
        public NationalCalendar {
            holidays = List.copyOf(holidays);
        }
    }
}
