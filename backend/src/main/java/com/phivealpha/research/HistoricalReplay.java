package com.phivealpha.research;

import com.phivealpha.persistence.IdxRecords.DailyPrice;
import com.phivealpha.screening.LiquidUniverse;
import com.phivealpha.screening.LiquidityPolicy;
import com.phivealpha.screening.news.NewsScoring;
import com.phivealpha.screening.news.NewsScoringPolicy;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class HistoricalReplay {
    private HistoricalReplay() {}

    public enum Partition { TRAIN, VALIDATION, OUT_OF_SAMPLE }
    public enum Arm { NEWS_SCORING, LIQUIDITY_BASELINE, SYNTHETIC_IHSG }
    public enum Listing { ACTIVE, SUSPENDED, DELISTED, UNKNOWN }
    public enum CorporateAction { NONE, PRESENT, UNKNOWN }
    public enum Status { EVALUATED, PARTIAL, NOT_EVALUABLE }
    public enum Reason {
        OUTSIDE_PARTITIONS, NO_POINT_IN_TIME_FRAME, FIRST_SEEN_UNVERIFIED, UNIVERSE_UNVERIFIED, NO_SCOREABLE_CANDIDATES,
        CALENDAR_COVERAGE, ENTRY_CALENDAR_CHANGED, HOLDING_CALENDAR_CHANGED, EXIT_BEYOND_PARTITION,
        MISSING_ENTRY, MISSING_HOLDING_PRICE, MISSING_EXIT, OUTCOME_NOT_MATURE, OUTCOME_PROVENANCE_UNVERIFIED,
        SUSPENDED, DELISTED, LISTING_UNVERIFIED, CORPORATE_ACTION, INVALID_PRICE,
        LOT_SIZE_UNVERIFIED, INSUFFICIENT_CAPACITY
    }

    /** A decision vintage. Availability includes when membership and all published input versions were assembled. */
    public record Frame(String version, Instant asOf, Instant availableAt, LiquidUniverse.Reference universe,
                        LiquidUniverse.CalendarReference calendar, String dailyPriceVersion, List<DailyPrice> dailyPrices,
                        Map<String, NewsScoring.Inputs> inputs, NewsScoring.Snapshot<NewsScoring.Regime> market,
                        boolean historicalNewsFirstSeenVerified, Map<String, Integer> lotSizes) {
        public Frame {
            requireText(version);
            requireText(dailyPriceVersion);
            Objects.requireNonNull(universe);
            Objects.requireNonNull(calendar);
            requireTimes(asOf, availableAt);
            dailyPrices = List.copyOf(dailyPrices);
            inputs = Map.copyOf(inputs);
            lotSizes = Map.copyOf(lotSizes);
        }
    }

    /** A versioned as-traded observation, including tradability and corporate action status of the session. */
    public record SessionBar(String symbol, LocalDate date, Instant availableAt, String version,
                             Listing listing, CorporateAction action, BigDecimal open, BigDecimal close) {
        public SessionBar {
            requireText(symbol);
            requireText(version);
            Objects.requireNonNull(date);
            Objects.requireNonNull(availableAt);
            Objects.requireNonNull(listing);
            Objects.requireNonNull(action);
        }
    }

    public record Dataset(String version, String source, List<Frame> frames,
                          LiquidUniverse.CalendarReference outcomeCalendar, String outcomeVersion,
                          Instant outcomeFreeze, List<SessionBar> outcomes) {
        public Dataset {
            requireText(version);
            requireText(source);
            requireText(outcomeVersion);
            Objects.requireNonNull(outcomeCalendar);
            Objects.requireNonNull(outcomeFreeze);
            frames = List.copyOf(frames);
            outcomes = List.copyOf(outcomes);
        }
    }

    public record Window(LocalDate first, LocalDate last) {
        public Window {
            if (Objects.requireNonNull(last).isBefore(Objects.requireNonNull(first))) {
                throw new IllegalArgumentException("Replay window ends before it starts");
            }
        }
        boolean includes(LocalDate date) {
            return !date.isBefore(first) && !date.isAfter(last);
        }
    }

    public record Split(Window training, Window validation, Window outOfSample, int embargoSessions) {
        public Split {
            Objects.requireNonNull(training);
            Objects.requireNonNull(validation);
            Objects.requireNonNull(outOfSample);
            if (!training.last().isBefore(validation.first()) || !validation.last().isBefore(outOfSample.first())
                    || embargoSessions < 10) {
                throw new IllegalArgumentException("Replay requires chronological splits and a ten-session minimum embargo");
            }
        }
    }

    /** Assumptions for independent, long-only, synthetic positions. Monetary values are in IDR and friction is in bps. */
    public record Costs(BigDecimal capitalIdr, BigDecimal buyFeeBps, BigDecimal sellFeeBps,
                        BigDecimal fullSpreadBps, BigDecimal slippagePerSideBps, BigDecimal maxMedianParticipation) {
        public Costs {
            if (capitalIdr == null || capitalIdr.signum() <= 0 || maxMedianParticipation == null
                    || maxMedianParticipation.signum() <= 0 || maxMedianParticipation.compareTo(BigDecimal.ONE) > 0
                    || !validBps(buyFeeBps) || !validBps(sellFeeBps)
                    || !validBps(fullSpreadBps) || !validBps(slippagePerSideBps)
                    || fullSpreadBps.divide(BigDecimal.TWO).add(slippagePerSideBps)
                        .compareTo(BigDecimal.valueOf(10_000)) >= 0) {
                throw new IllegalArgumentException("Invalid synthetic replay cost assumptions");
            }
        }

        public static Costs syntheticDefaults() {
            return new Costs(BigDecimal.valueOf(10_000_000), BigDecimal.valueOf(15), BigDecimal.valueOf(25),
                    BigDecimal.valueOf(20), BigDecimal.valueOf(10), new BigDecimal("0.01"));
        }

        private static boolean validBps(BigDecimal rate) {
            return rate != null && rate.signum() >= 0 && rate.compareTo(BigDecimal.valueOf(10_000)) < 0;
        }
    }

    public record Decision(Instant cutoff, Partition partition, String frameVersion, Instant frameAsOf,
                           Instant frameAvailableAt, String universeVersion, String universeSource,
                           String calendarVersion, String calendarSource, String dailyPriceVersion,
                           List<LiquidUniverse.Result> universe,
                           NewsScoring.Report scoring, List<String> baselineSymbols, Reason reason) {
        public Decision {
            universe = List.copyOf(universe);
            baselineSymbols = List.copyOf(baselineSymbols);
        }
    }

    public record Observation(Instant cutoff, Partition partition, Arm arm, String symbol, int holdingSessions,
                              LocalDate entryDate, LocalDate exitDate, boolean entered, BigDecimal shares,
                              BigDecimal grossReturn, BigDecimal netReturn, BigDecimal maxPathDrawdown,
                              List<String> outcomeBarVersions, LocalDate failureDate, Reason exclusion) {
        public Observation {
            outcomeBarVersions = List.copyOf(outcomeBarVersions);
        }
    }

    public record Metrics(Partition partition, Arm arm, int holdingSessions, int observations, int entered,
                          int settled, int unresolved, int excluded, BigDecimal coverage, BigDecimal meanGrossReturn,
                          BigDecimal meanNetReturn, BigDecimal winRate, BigDecimal averageGain, BigDecimal averageLoss,
                          BigDecimal maxPathDrawdown, Map<Reason, Long> exclusions) {
        public Metrics {
            exclusions = Map.copyOf(exclusions);
        }
    }

    public record Report(String purpose, Status status, String strategyVersion, String datasetVersion,
                         String datasetSource, String outcomeVersion, String outcomeCalendarVersion,
                         String outcomeCalendarSource, Instant outcomeCalendarAvailableAt, Instant outcomeFreeze,
                         Split split, Costs costAssumptions,
                         LiquidityPolicy liquidityPolicy, NewsScoringPolicy scoringPolicy, List<Decision> decisions,
                         List<Observation> observations, List<Metrics> metrics, Map<Reason, Long> excludedCutoffs,
                         List<String> limitations) {
        public Report {
            decisions = List.copyOf(decisions);
            observations = List.copyOf(observations);
            metrics = List.copyOf(metrics);
            excludedCutoffs = Map.copyOf(excludedCutoffs);
            limitations = List.copyOf(limitations);
        }
    }

    private static void requireText(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Replay provenance is required");
        }
    }

    private static void requireTimes(Instant asOf, Instant availableAt) {
        if (Objects.requireNonNull(availableAt).isBefore(Objects.requireNonNull(asOf))) {
            throw new IllegalArgumentException("Replay availability precedes as-of");
        }
    }
}
