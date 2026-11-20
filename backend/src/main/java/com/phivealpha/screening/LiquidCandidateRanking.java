package com.phivealpha.screening;

import static com.phivealpha.screening.LiquidUniverse.Eligibility.ELIGIBLE;

import com.phivealpha.screening.LiquidUniverse.Evaluation;
import com.phivealpha.screening.LiquidUniverse.Result;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

public final class LiquidCandidateRanking {
    private static final Comparator<Result> ORDER = Comparator
            .<Result, BigDecimal>comparing(result -> result.liquidity().medianDailyTurnoverIdr())
            .thenComparingInt(result -> result.liquidity().liquidSessions())
            .thenComparingInt(result -> result.liquidity().activeSessions())
            .reversed()
            .thenComparing(Result::symbol);

    private LiquidCandidateRanking() {}

    public static List<Result> rank(Evaluation evaluation) {
        return evaluation.results().stream().filter(result -> result.eligibility() == ELIGIBLE)
                .sorted(ORDER).toList();
    }
}
