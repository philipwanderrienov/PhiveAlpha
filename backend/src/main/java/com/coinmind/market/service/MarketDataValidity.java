package com.coinmind.market.service;

import com.coinmind.market.model.Candlestick;
import com.coinmind.market.model.TickerSnapshot;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Exchange timestamps, never HTTP/SSE receipt alone, establish freshness. */
public final class MarketDataValidity {
    private MarketDataValidity() { }
    public static boolean fresh(Instant event, Instant now) {
        return event != null && !event.isBefore(now.minusSeconds(30))
                && !event.isAfter(now.plusSeconds(5));
    }
    private static boolean positive(BigDecimal n) { return n != null && n.signum() > 0; }
    private static boolean nonnegative(BigDecimal n) { return n != null && n.signum() >= 0; }
    public static boolean valid(TickerSnapshot t, Instant now) {
        return t != null && t.symbol() != null && !t.symbol().isBlank() && fresh(t.eventTime(), now)
                && positive(t.closePrice()) && positive(t.openPrice()) && positive(t.highPrice())
                && positive(t.lowPrice()) && nonnegative(t.baseVolume()) && nonnegative(t.quoteVolume());
    }
    public static boolean candle(Candlestick c) {
        return c != null && c.symbol() != null && c.interval() != null && c.openTime() != null
                && c.closeTime() != null && c.eventTime() != null && !c.closeTime().isBefore(c.openTime())
                && positive(c.open()) && positive(c.close()) && positive(c.high()) && positive(c.low())
                && c.high().compareTo(c.open().max(c.close())) >= 0
                && c.low().compareTo(c.open().min(c.close())) <= 0
                && nonnegative(c.baseVolume()) && nonnegative(c.quoteVolume()) && c.tradeCount() >= 0;
    }
    public static void requireHistory(List<Candlestick> candles, Instant now) {
        if (candles == null || candles.size() < 30) throw new IllegalStateException("Market data unavailable: insufficient candles");
        Candlestick previous = null;
        for (Candlestick c : candles) {
            if (!candle(c) || c.openTime().isAfter(now.plusSeconds(5))
                    || c.eventTime().isAfter(now.plusSeconds(5))
                    || (previous != null && (!c.openTime().isAfter(previous.openTime())
                        || c.openTime().toEpochMilli() - previous.closeTime().toEpochMilli() > 1)))
                throw new IllegalStateException("Market data invalid: incomplete or unordered candles");
            previous = c;
        }
        if (!fresh(previous.eventTime(), now)) throw new IllegalStateException("Market data stale: candles");
    }
}
