# PhiveAlpha Phase 1G — Point-in-Time Backtesting Foundation

`HistoricalReplayService.replay(dataset, split, costs, cutoffs)` runs an in-memory, research-only replay. Fixtures exercise the method without connecting to a provider, database or trading system. The report carries `RESEARCH_ONLY_SYNTHETIC` and strategy version `synthetic-replay-v1/fixture-news-v1`.

## Data and replay architecture

- Each cutoff selects the newest eligible decision vintage by `asOf`, then by `availableAt`. Both times must be at or before cutoff. Tied vintage timestamps are rejected. Each vintage contains a listing master, calendar manifest, price version and EOD rows, scored-factor snapshots, and a lot-size map. `LiquidUniverseService` filters listing and liquidity using its Phase 1E rules. `NewsDrivenScoringService` ranks the candidates using Phase 1F and validates individual factor/event availability.
- The historical master contains instruments that existed on each date, including securities that later delist. IPO and suspension eligibility use the historical master and calendar at cutoff. Later membership data stays outside that decision. Historical news requires attested first-seen coverage and both first-seen and availability on visible events. Suspect events yield `FIRST_SEEN_UNVERIFIED` for the cutoff. An entirely blocked replay is `NOT_EVALUABLE`. Publication time is never substituted for these timestamps.
- The benchmark/outcome stream is separate from decision data. Each as-traded session row includes listing and corporate action status, open/close, availability and version. Outcome versions must be available by the explicit evaluation freeze. A row available before its session close, or tied revisions for the same symbol/date, fails validation. The report retains dataset and outcome versions, realized calendar revision, cutoff-level decision versions, and accepted outcome versions along each observed path.
- The liquidity baseline takes the same number of candidates as the news arm from the Phase 1E liquidity ranking, without using news scores to order that list. The synthetic IHSG arm uses supplied `IHSG` rows on the same planned horizon and reports gross return. Benchmark values are fixture inputs. Coverage is reported separately per arm, so any comparison of incomplete arms needs a matched-sample review.
- No fitting or tuning occurs in replay. The scoring policy is frozen at 30/25/20/15/10 with seven-day score freshness and news lookback. The report records the policy and the active Phase 1E eligibility policy. Choose policies, costs, and splits before opening out-of-sample results. The engine cannot enforce a researcher's process across separate runs.

## Entry, exit and costs

1. Compute the earliest opening strictly after cutoff using the calendar vintage known at that time. A cutoff before the day's opening may enter at that opening. A cutoff at or after opening waits until the next session. A mismatched realized opening invalidates the observation. There is no same-close entry or forward search for another fill when the scheduled instrument entry fails.
2. Enter at the as-traded open with friction. Exit at the close of session number 2, 5 or 10, counting the entry session as day one. Confirmed holidays do not count. Measure the daily marked path using the realized calendar. Listing state, corporate action status and closes must remain evaluable throughout the holding window.
3. With round-trip full spread in bps and per-side slippage in bps, let `f = (spread/2 + slippage)/10,000`. Let `feeBuy` and `feeSell` be per-side fee rates after conversion from bps. Entry fill = open × (1 + f). All-in buy cost per share = entry fill × (1 + feeBuy). Net sale proceeds per share = exit close × (1 − f) × (1 − feeSell).
4. Lots = the smaller of floor(capital / all-in buy cost per lot) and floor(prior median turnover × participation / entry fill notional per lot). Shares = lots × historical lot size. A zero-lot result is excluded, and missing lot size is unverified. This is a prior-turnover capacity proxy. Intraday depth and fill priority are outside the fixture contract.
5. Gross return = exit close / entry open − 1. Net return = net sale proceeds / all-in buy cost − 1. Returns are on deployed capital. Share count cancels in the return ratio, and the report records the simulated position size.

`Costs.syntheticDefaults()` sets assumed capital Rp10 million, buy fee 15 bps, sell fee 25 bps, full spread 20 bps, slippage per side 10 bps, and 1% of trailing median turnover as the entry notional cap. Callers may pass other assumptions explicitly. Lot size comes from each decision vintage. These values are research assumptions, without assertions about actual fees or fill conditions.

A missing scheduled entry, unverified bar, suspension, delisting, corporate action of any kind, or missing holding/exit price receives an explicit reason and failure date. The engine does not extend the holding period, fill against a carried close, assume recovery value, or apply a corporate action factor. A position that entered and lacks a validated path remains unresolved, including when the planned exit has not matured by the evaluation freeze. Its returns and drawdown are null.

## Temporal splits and metrics

Every cutoff is assigned to TRAIN, VALIDATION or OUT_OF_SAMPLE by its Jakarta date. The split is chronological, uses inclusive ranges, and must have at least ten confirmed trading sessions in each gap. Gaps are embargoed. The maximum label horizon is ten trading sessions, and any horizon whose exit would cross the partition end is excluded before simulating entry. Isolation applies to forward labels. Trailing point-in-time inputs may use earlier history.

Per partition, arm and horizon, the report includes requested observations, positions entered, settled and unresolved positions, excluded count with reasons, and coverage as settled / observations. Cutoff exclusions such as unavailable vintage, missing first-seen, or no scoreable candidates are separately counted and remain visible alongside all attempted cutoffs. Empty denominators produce null. Mean gross/net return, win rate and average gains/losses describe settled rows only. Gains and losses use positive and negative net returns, respectively, with benchmark metrics using gross. A zero return counts as a non-win and is excluded from average gain/loss.

Drawdown uses the greatest decline from the previous peak on each settled position's path. The initial value is one, followed by daily close liquidation value net of assumed sell costs divided by all-in entry cost. The aggregate takes the worst position drawdown. Positions are independent and can overlap. The result does not model synchronized portfolio drawdown, cash allocation or leverage. Unresolved positions receive no imputed valuation and downgrade the report to `PARTIAL` when some strategy positions settle, or `NOT_EVALUABLE` when none settle.

## Example synthetic output

The training fixture cutoff is `2026-01-09T10:00:00Z`. After a synthetic holiday on January 12, `OLD` enters at the special session's January 13 open. Planned exits are January 14, 19 and 26 for 2, 5 and 10 sessions. The fixture overrides capital to Rp100,000 and the participation cap to 10% of its Rp500,000 prior median turnover. The two-session observation has 400 shares at raw open 100, exit close 110, gross return +10.00%, net return about +9.12%, and path drawdown about 10.72%. The fixture IHSG gross return is +2.00% and its net field is absent. With the January 14 close removed, the two-session report counts one unresolved position, zero coverage, and null average return. These numbers exercise accounting and calendar rules using synthetic inputs.

| Arm | Planned exit | Shares | Settled / attempted | Gross return | Net return | Exclusion |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| News scoring, complete fixture | 2026-01-14 | 400 | 1 / 1 | +10.00% | +9.12% | — |
| Synthetic IHSG, complete fixture | 2026-01-14 | — | 1 / 1 | +2.00% | — | — |
| News scoring, missing exit fixture | 2026-01-14 | 400 | 0 / 1 | — | — | `MISSING_EXIT` |

## Requirements before a valid historical backtest

- Obtain licensed historical universe vintages with actual dissemination/availability times, delisted instruments, status intervals, listing dates, and then-effective lot sizes. Verify the universe against a historical reference and preserve point-in-time revisions.
- Obtain verified BEI session calendars and actual open/close executions, suspensions, delisting settlements and corporate action terms. Define cash and share adjustments, execution availability and conservative unresolved-position valuation before portfolio-level analysis.
- Require genuine first-seen and availability times, archive completeness, versioned point-in-time factor annotations and corrections, and aligned market/flow/benchmark coverage. Historical publication timestamps by themselves cannot establish what was observable.
- Pre-register the experiment, assumed costs, freeze, selection policy and walk-forward split. Evaluate robustness and sample size before interpreting empirical returns. No real market performance has been evaluated in this phase.

## Files

- `backend/src/main/java/com/phivealpha/research/HistoricalReplay.java` and `HistoricalReplayService.java` contain the replay inputs, simulated outcomes and metrics.
- `VersionedIdxCalendar.java` exposes the first opening time. `LiquidUniverseService.java` exposes the effective eligibility policy for provenance.
- `HistoricalReplayServiceTest.java` constructs the synthetic calendar, masters, prices, news and benchmark fixtures in memory.

## Reproducing offline validation

```bash
JDK_JAVA_OPTIONS="-javaagent:$HOME/.m2/repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar" \
  mvn -o -f backend/pom.xml clean verify
mvn -o -f backend/pom.xml -Pphivealpha -DskipTests package
(cd frontend && npm run build && npm run build:phivealpha)
```

Test coverage includes delayed price, news and universe revisions, survivorship bias, the holiday and special session, entry and all three holding periods, fees and lots, missing exit, corporate action, suspension and delisting, deterministic input reordering, synthetic IHSG, missing first-seen, and temporal split isolation.

The backend suite reported 272 cases, with 271 passing and zero failures or errors. The existing Phase 1D.2 socket test was skipped because the sandbox disallows loopback sockets. Seven Phase 1G cases passed. CoinMind and PhiveAlpha backend packages and Angular production builds passed. Existing frontend tests passed for market freshness, AI statuses, news source links, tutorial, and news request lifecycle.
