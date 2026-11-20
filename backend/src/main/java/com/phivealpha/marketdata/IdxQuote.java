package com.phivealpha.marketdata;

import com.phivealpha.stock.IdxStock;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

public record IdxQuote(IdxStock stock, LocalDate sessionDate, long closeIdr, long priorWeekCloseIdr) {
    public BigDecimal weeklyChangePercent() {
        return BigDecimal.valueOf(closeIdr - priorWeekCloseIdr)
                .movePointRight(2)
                .divide(BigDecimal.valueOf(priorWeekCloseIdr), 2, RoundingMode.HALF_UP);
    }
}
