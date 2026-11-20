package com.phivealpha.marketdata.apicoid;

import static com.phivealpha.marketdata.apicoid.ApiCoIdData.*;
import static com.phivealpha.persistence.IdxMarketCalendar.ZONE;

import com.phivealpha.persistence.IdxRecords.AdjustmentStatus;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import tools.jackson.databind.JsonNode;

final class ApiCoIdPayload {
    private ApiCoIdPayload() {}

    record Row(String key, Object value) {}

    static Row normalize(ApiCoIdEndpoint endpoint, String expected, JsonNode wrapper, JsonNode row,
                         Instant fetchedAt, VersionedIdxCalendar calendar) {
        return switch (endpoint) {
            case INSTRUMENTS -> {
                String code = symbol(text(row, "symbol"));
                yield new Row(code, new Instrument(code, text(row, "name"), text(row, "sector"),
                        text(row, "board"), optionalDate(row, "listing_date")));
            }
            case QUOTE -> {
                String code = symbol(text(row, "symbol"));
                require(code.equals(expected), "SYMBOL_MISMATCH");
                LocalDate date = date(row, "date");
                require(calendar.isTradingSession(date)
                        && !date.isAfter(fetchedAt.atZone(ZONE).toLocalDate()), "UNKNOWN_TRADING_DATE");
                var price = prices(row, "last");
                yield new Row(code, new Quote(code, text(row, "name"), date, price.close(), price.open(),
                        price.high(), price.low(), volume(row), turnover(row), AdjustmentStatus.UNKNOWN));
            }
            case DAILY -> {
                LocalDate date = date(row, "date");
                require(calendar.isClosedBy(date, fetchedAt), "UNSETTLED_OR_UNKNOWN_SESSION");
                var price = prices(row, "close");
                // Public documentation describes split/bonus adjustments for this endpoint only.
                yield new Row(date.toString(), new DailyBar(expected, date, price.open(), price.high(),
                        price.low(), price.close(), volume(row), turnover(row), AdjustmentStatus.ADJUSTED));
            }
            case NEWS -> {
                var id = text(row, "id");
                var symbols = array(row, "symbols").valueStream()
                        .map(item -> symbol(string(item))).distinct().toList();
                require(expected == null || symbols.contains(expected), "SYMBOL_MISMATCH");
                var published = instant(row, "published_at");
                require(!published.isAfter(fetchedAt), "FUTURE_PUBLICATION");
                yield new Row(id, new News(id, symbols, text(row, "title"), optionalText(row, "source"),
                        optionalUrl(row), published, fetchedAt));
            }
            case CORPORATE_ACTIONS -> {
                var action = new CorporateAction(expected, text(row, "type").toUpperCase(Locale.ROOT),
                        optionalDate(row, "announce_date"), optionalDate(row, "ex_date"),
                        optionalDate(row, "record_date"), optionalDate(row, "pay_date"),
                        optionalNonnegative(row, "value"), optionalNonnegative(row, "ratio_old"),
                        optionalNonnegative(row, "ratio_new"));
                require(action.announcedOn() != null || action.exDate() != null
                        || action.recordDate() != null || action.paymentDate() != null, "MISSING_ACTION_DATE");
                yield new Row(action.toString(), action);
            }
            case FOREIGN_FLOW -> {
                LocalDate date = date(row, "date");
                require(calendar.isClosedBy(date, fetchedAt), "UNSETTLED_OR_UNKNOWN_SESSION");
                var buy = nonnegative(row, "foreign_buy");
                var sell = nonnegative(row, "foreign_sell");
                var net = decimal(row, "foreign_net");
                require(buy.subtract(sell).compareTo(net) == 0, "INCONSISTENT_NET_FLOW");
                yield new Row(date.toString(), new ForeignFlow(expected, date, buy, sell, net));
            }
            case BROKER_FLOW -> {
                LocalDate date = date(wrapper, "date");
                require(calendar.isClosedBy(date, fetchedAt), "UNSETTLED_OR_UNKNOWN_SESSION");
                String time = text(row, "time");
                Instant bucket = time.contains("T") ? OffsetDateTime.parse(time).toInstant()
                        : date.atTime(LocalTime.parse(time)).atZone(ZONE).toInstant();
                require(bucket.atZone(ZONE).toLocalDate().equals(date)
                        && calendar.containsTradingTime(bucket), "OUTSIDE_TRADING_SESSION");
                var tracked = array(wrapper, "brokers").valueStream()
                        .map(code -> symbol(string(code))).toList();
                Set<String> brokers = new HashSet<>();
                var positions = array(row, "brokers").valueStream().map(position -> {
                    String code = symbol(text(position, "broker_code"));
                    require(tracked.contains(code) && brokers.add(code), "UNKNOWN_OR_DUPLICATE_BROKER");
                    return new BrokerPosition(code, decimal(position, "net_value"),
                            decimal(position, "net_lot").longValueExact());
                }).toList();
                yield new Row(bucket.toString(), new BrokerBar(expected, date, bucket,
                        positive(row, "price"), positions));
            }
        };
    }

    static String symbol(String value) {
        if (value == null || !value.strip().matches("[a-zA-Z0-9]{1,16}")) {
            throw new IllegalArgumentException("Invalid IDX symbol");
        }
        return value.strip().toUpperCase(Locale.ROOT);
    }

    static void require(boolean valid, String reason) {
        if (!valid) {
            throw new ApiCoIdFailure(Quality.INCOMPLETE, reason);
        }
    }

    static JsonNode array(JsonNode parent, String field) {
        JsonNode node = parent.path(field);
        require(node.isArray(), "EXPECTED_ARRAY");
        return node;
    }

    static String string(JsonNode node) {
        require(node.isString() && !node.asString().isBlank(), "EXPECTED_TEXT");
        return node.asString().strip();
    }

    static String text(JsonNode parent, String field) {
        return string(parent.path(field));
    }

    static String optionalText(JsonNode parent, String field) {
        JsonNode node = parent.path(field);
        return absent(node) ? null : string(node);
    }

    static LocalDate date(JsonNode row, String field) {
        return LocalDate.parse(text(row, field));
    }

    private static LocalDate optionalDate(JsonNode row, String field) {
        var value = optionalText(row, field);
        return value == null ? null : LocalDate.parse(value);
    }

    static Instant instant(JsonNode row, String field) {
        return OffsetDateTime.parse(text(row, field)).toInstant();
    }

    static boolean absent(JsonNode node) {
        return node.isMissingNode() || node.isNull() || (node.isString() && node.asString().isBlank());
    }

    static BigDecimal decimal(JsonNode row, String field) {
        JsonNode node = row.path(field);
        require(node.isNumber() || node.isString(), "EXPECTED_NUMBER");
        String raw = node.asString().strip();
        require(raw.length() <= 96 && raw.matches("-?[0-9]+(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]{1,3})?"),
                "INVALID_NUMBER");
        return new BigDecimal(raw);
    }

    static BigDecimal nonnegative(JsonNode row, String field) {
        BigDecimal value = decimal(row, field);
        require(value.signum() >= 0, "NEGATIVE_VALUE");
        return value;
    }

    private static BigDecimal optionalNonnegative(JsonNode row, String field) {
        return absent(row.path(field)) ? null : nonnegative(row, field);
    }

    private static BigDecimal positive(JsonNode row, String field) {
        BigDecimal value = decimal(row, field);
        require(value.signum() > 0, "NONPOSITIVE_PRICE");
        return value;
    }

    private static long volume(JsonNode row) {
        return nonnegative(row, "volume").longValueExact();
    }

    private static BigDecimal turnover(JsonNode row) {
        return optionalNonnegative(row, "value");
    }

    private record Prices(BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close) {}

    private static Prices prices(JsonNode row, String closingField) {
        var values = new Prices(positive(row, "open"), positive(row, "high"),
                positive(row, "low"), positive(row, closingField));
        require(values.high().compareTo(values.open().max(values.close())) >= 0
                && values.low().compareTo(values.open().min(values.close())) <= 0, "INVALID_OHLC");
        return values;
    }

    private static URI optionalUrl(JsonNode row) {
        String value = optionalText(row, "url");
        if (value == null) {
            return null;
        }
        URI url = URI.create(value);
        require(("https".equalsIgnoreCase(url.getScheme()) || "http".equalsIgnoreCase(url.getScheme()))
                && url.getHost() != null && url.getRawUserInfo() == null, "INVALID_NEWS_URL");
        return url;
    }
}
