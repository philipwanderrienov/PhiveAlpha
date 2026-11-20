package com.phivealpha.marketdata.apicoid;

import com.phivealpha.persistence.IdxMarketCalendar;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import tools.jackson.databind.JsonNode;

public final class VersionedIdxCalendar implements IdxMarketCalendar {
    private final String source;
    private final String version;
    private final Map<LocalDate, Day> days;

    private record Session(LocalTime open, LocalTime close) {}

    private record Day(List<Session> sessions) {
        boolean open() {
            return !sessions.isEmpty();
        }
    }

    public VersionedIdxCalendar(JsonNode manifest) {
        this.source = ApiCoIdPayload.text(manifest, "source");
        this.version = ApiCoIdPayload.text(manifest, "version");
        var rows = ApiCoIdPayload.array(manifest, "days");
        var parsed = new HashMap<LocalDate, Day>();
        for (JsonNode row : rows) {
            var date = ApiCoIdPayload.date(row, "date");
            var sessions = ApiCoIdPayload.array(row, "sessions").valueStream().map(session -> new Session(
                    LocalTime.parse(ApiCoIdPayload.text(session, "open")),
                    LocalTime.parse(ApiCoIdPayload.text(session, "close")))).toList();
            var kind = ApiCoIdPayload.text(row, "kind");
            if (!(kind.equals("HOLIDAY") || kind.equals("REGULAR") || kind.equals("SPECIAL"))
                    || (kind.equals("HOLIDAY") != sessions.isEmpty())
                    || parsed.putIfAbsent(date, new Day(sessions)) != null) {
                throw new IllegalArgumentException("Invalid or duplicate IDX calendar day");
            }
            LocalTime lastClose = LocalTime.MIN;
            for (Session session : sessions) {
                if (session.open().isBefore(lastClose) || !session.open().isBefore(session.close())) {
                    throw new IllegalArgumentException("Invalid IDX session hours");
                }
                lastClose = session.close();
            }
        }
        if (parsed.isEmpty()) {
            throw new IllegalArgumentException("IDX calendar is empty");
        }
        days = Map.copyOf(parsed);
    }

    public String source() { return source; }
    public String version() { return version; }

    public boolean covers(LocalDate from, LocalDate to) {
        return from != null && to != null && !to.isBefore(from)
                && java.time.temporal.ChronoUnit.DAYS.between(from, to) < days.size()
                && from.datesUntil(to.plusDays(1)).allMatch(days::containsKey);
    }

    @Override
    public boolean isTradingSession(LocalDate date) {
        Day day = days.get(date);
        return day != null && day.open();
    }

    public boolean containsTradingTime(Instant instant) {
        var local = instant.atZone(ZONE);
        Day day = days.get(local.toLocalDate());
        return day != null && day.sessions().stream().anyMatch(session ->
                !local.toLocalTime().isBefore(session.open()) && local.toLocalTime().isBefore(session.close()));
    }

    public boolean isClosedBy(LocalDate date, Instant instant) {
        return sessionClose(date).filter(close -> !close.isAfter(instant)).isPresent();
    }

    public Optional<Instant> sessionOpen(LocalDate date) {
        return Optional.ofNullable(days.get(date)).filter(Day::open)
                .map(day -> date.atTime(day.sessions().getFirst().open()).atZone(ZONE).toInstant());
    }

    public Optional<Instant> sessionClose(LocalDate date) {
        return Optional.ofNullable(days.get(date)).filter(Day::open)
                .map(day -> date.atTime(day.sessions().getLast().close()).atZone(ZONE).toInstant());
    }

    public boolean hasNewerSession(LocalDate date, Instant now) {
        return days.entrySet().stream().anyMatch(entry -> entry.getKey().isAfter(date)
                && entry.getValue().open()
                && !entry.getKey().atTime(entry.getValue().sessions().getFirst().open())
                        .atZone(ZONE).toInstant().isAfter(now));
    }
}
