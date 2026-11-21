package com.phivealpha.marketdata.apicoid;

import com.phivealpha.persistence.IdxRecords.AdjustmentStatus;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class ApiCoIdData {
    private ApiCoIdData() {}

    public enum Quality { COMPLETE, UNAVAILABLE, STALE, INCOMPLETE, UNVERIFIED }

    public record Provenance(String provider, String dataset, Instant asOf, Instant fetchedAt,
                             String calendarSource, String calendarVersion, String universeVersion,
                             Instant ageAnchor) {}

    public record Snapshot<T>(Quality quality, String reason, List<T> rows, Provenance provenance) {
        public Snapshot {
            rows = List.copyOf(rows);
        }
    }

    public record Instrument(String symbol, String name, String sector, String board, LocalDate listingDate) {}

    public record Quote(String symbol, String name, LocalDate tradingDate, BigDecimal lastIdr,
                        BigDecimal openIdr, BigDecimal highIdr, BigDecimal lowIdr, long volume,
                        BigDecimal turnoverIdr, AdjustmentStatus adjustment) {}

    public record DailyBar(String symbol, LocalDate tradingDate, BigDecimal openIdr, BigDecimal highIdr,
                           BigDecimal lowIdr, BigDecimal closeIdr, long volume, BigDecimal turnoverIdr,
                           AdjustmentStatus adjustment) {}

    public record News(String id, List<String> symbols, String title, String publisher, URI url,
                       Instant publishedAt, Instant observedAt) {}

    public record CorporateAction(String symbol, String type, LocalDate announcedOn, LocalDate exDate,
                                  LocalDate recordDate, LocalDate paymentDate, BigDecimal valueIdr,
                                  BigDecimal oldRatio, BigDecimal newRatio) {}

    public record ForeignFlow(String symbol, LocalDate tradingDate, BigDecimal buyIdr,
                              BigDecimal sellIdr, BigDecimal netIdr) {}

    public record BrokerPosition(String code, BigDecimal netIdr, long netLots) {}

    public record BrokerBar(String symbol, LocalDate tradingDate, Instant bucketAt, BigDecimal priceIdr,
                            List<BrokerPosition> positions) {}
}
