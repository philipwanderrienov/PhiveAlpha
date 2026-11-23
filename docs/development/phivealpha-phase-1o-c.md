# PhiveAlpha Phase 1O-C — Forward Outcome & Predictive Value Evaluation

Baseline `main` at `80bcb2f`.

## Gap analysis

| Existing capability | Addition in Phase 1O-C |
| --- | --- |
| Phase 1O-A readiness and Phase 1O-B screening at a fixed cutoff | Evaluate the exact screened ranking after the decision is frozen |
| Phase 1G entry, 2/5/10 session paths, revisions, fees, capacity, returns and drawdown | Reuse the same simulator and require calendar agreement throughout the holding window |
| Phase 1G IHSG arm and chronological embargoed partitions | Pair each candidate with its same-window IHSG observation and aggregate only within partition and horizon |
| Phase 1G arm-level coverage | Add paired coverage, rejection counts, and explicit overlap and sample-size limits |

## Workflow

Call `ForwardOutcomeEvaluationService.evaluate(contexts, outcomes, split, costs, policy)` with immutable Phase 1O-B contexts and a separate versioned outcome bundle. The service finishes screening before it indexes forward bars. The report retains each cutoff's screening report, readiness and provenance, ranking and model/policy, plus the outcome tape version, source, basis, calendar, and evaluation freeze. Candidate scores, weights, and selection order come from Phase 1O-B.

- Entry is the first confirmed open strictly after cutoff. That open must match the realized calendar. Each 2-, 5-, or 10-session window counts entry as session one and ends at its last confirmed close. Confirmed holidays are skipped and special-session times come from the manifest. A screening-vintage calendar with missing dates or conflicting opens/closes within the holding path invalidates only the affected paths.
- Prices use the declared unadjusted as-traded basis. IHSG is an unadjusted price index series with the same window and no assumed costs. The outcome bundle declares both bases because the existing `SessionBar` has no row-level basis field. The caller must verify that each row conforms to the declaration. The replay simulator applies the same friction, fees, lot and capacity assumptions for the equity, using lot size and median turnover from the historical context. Drawdown is the largest peak-to-trough decline along the cost-adjusted daily-close valuation path, initialized at one.
- Only frozen outcomes are visible. The latest eligible version wins when unambiguous. Gross excess is candidate gross return minus IHSG gross return. Net excess subtracts IHSG gross return from the candidate's net return. Missing entry, intermediate price, exit, or benchmark is recorded on its leg. An invalid leg leaves both excess values null. An available candidate return remains visible if the benchmark leg fails.
- Dataset readiness or provenance failure blocks that cutoff. Missing price, suspension, delisting, corporate-action uncertainty and path failures invalidate the affected observation and horizon. An incompatible bundle basis, invalid outcome calendar or split, or repeated cutoff blocks the evaluation. Insufficient paired coverage suppresses the aggregate for that partition and horizon. Blocked cutoffs remain visible separately from rejected candidate/benchmark pairs.
- The existing chronological train, validation, and out-of-sample windows require a session embargo of at least ten and purge outcomes crossing the partition end. The service performs no fitting or optimization. Fix the split, cost assumptions and coverage policy before inspecting final-test results.
- Aggregates use matched pairs within each partition and horizon. The coverage policy defaults to at least ten distinct matched cutoffs and 80% paired coverage. The hit rate is the fraction of matched pairs with net excess strictly above zero. The report counts distinct matched cutoffs and how many later cutoff windows overlap an earlier window. Multiple candidates at one cutoff share market exposure, and horizons from the same cutoff reuse sessions. Aggregates are descriptive, without independence or significance assumptions. There is no portfolio simulation.

## Evidence boundary

Synthetic fixtures establish arithmetic, cutoff isolation, benchmark alignment and rejection behavior. They provide no evidence of predictive value or profitability. A real study requires independently verified and licensed historical IDX and IHSG data, genuine point-in-time universe, session, corporate-action and factor evidence, sufficiently broad coverage, and a pre-registered out-of-sample protocol that addresses dependent observations and selection bias.

## Host validation

Run from the repository root using Java 25 and offline Maven dependencies.

```bash
CI=true mvn -o -B -f backend/pom.xml clean verify
git diff --check
```

This phase uses in-memory outcomes. For adjacent PostgreSQL regression on an unprivileged development host with PostgreSQL 17 and loopback support, use the existing ephemeral runners.

```bash
scripts/validation/phivealpha-persistence-it.sh --daily-ingestion -o
scripts/validation/phivealpha-persistence-it.sh --stock-screener -o
```

The full offline Maven run reported 134 tests total, 133 passed, one existing quote-canary loopback skip, and zero failures or errors. The targeted run passed all 18 tests, including 11 new forward-evaluation cases and 7 legacy replay cases. Whitespace checks passed.

Both runners stopped at the sandbox's loopback probe with `EPERM`, before creating a cluster.

## Changed files

- `ForwardOutcomeEvaluation.java` and `ForwardOutcomeEvaluationService.java` define the internal input, paired observations, and coverage-aware report.
- `HistoricalReplay.java` and `HistoricalReplayService.java` share the existing simulator and add the forward workflow's holding-calendar check. Legacy replay keeps its original calendar policy.
- `ForwardOutcomeEvaluationTest.java` covers synthetic end-to-end paths, data and provenance failures, costs and drawdown, partition isolation, overlap, replay equivalence, and cutoff isolation.
- This handoff, `current-state.md`, and `session-log.md` record the change and validation.
