package com.phivealpha.persistence;

import java.time.LocalDate;
import java.time.ZoneId;

@FunctionalInterface
public interface IdxMarketCalendar {
    ZoneId ZONE = ZoneId.of("Asia/Jakarta");

    /** Return true only for a confirmed BEI session. Unknown dates must fail closed. */
    boolean isTradingSession(LocalDate date);
}
