// Wire contract for StockScreenerService.ScreeningSnapshot and its nested records.
export type ScreeningMode = 'FIXTURE' | 'DEVELOPMENT_PERSISTENCE';
export type ScreeningQuality = 'COMPLETE' | 'INCOMPLETE' | 'STALE' | 'UNVERIFIED' | 'UNAVAILABLE';
export type ScreeningFailure = 'PERSISTENCE_DISABLED' | 'HISTORICAL_STORAGE_UNAVAILABLE';
export type Eligibility = 'ELIGIBLE' | 'INELIGIBLE' | 'UNVERIFIED';
export type LiquidityQuality = Exclude<ScreeningQuality, 'UNAVAILABLE'> | 'NOT_EVALUATED';
export type FactorQuality = Exclude<ScreeningQuality, 'COMPLETE'> | 'AVAILABLE';

export type LiquidityReason =
  | 'REFERENCE_NOT_AVAILABLE_AT_CUTOFF' | 'STALE_UNIVERSE_REFERENCE' | 'DUPLICATE_INSTRUMENT'
  | 'SECURITY_TYPE_UNVERIFIED' | 'NOT_COMMON_STOCK' | 'LISTING_STATUS_UNVERIFIED' | 'SUSPENDED' | 'DELISTED'
  | 'IPO_TOO_RECENT' | 'LISTING_DATE_UNVERIFIED' | 'CORPORATE_ACTION_UNVERIFIED'
  | 'CALENDAR_NOT_AVAILABLE_AT_CUTOFF' | 'CALENDAR_OUT_OF_COVERAGE'
  | 'INVALID_CANDLE' | 'NON_TRADING_CANDLE' | 'DUPLICATE_CANDLE' | 'MISSING_CANDLE'
  | 'STALE_PRICE' | 'INVALID_PRICE_AVAILABILITY' | 'ADJUSTMENT_BASIS_UNVERIFIED' | 'MIXED_PRICE_SOURCE'
  | 'ACTIVE_SESSIONS_BELOW_MINIMUM' | 'ACTIVE_RATIO_BELOW_MINIMUM'
  | 'LIQUID_SESSIONS_BELOW_MINIMUM' | 'MEDIAN_TURNOVER_BELOW_MINIMUM';

export interface LiquidityIssue {
  reason: LiquidityReason;
  tradingDate: string | null;
}

interface LiquidityMetrics {
  latestDailyTurnoverIdr: number;
  medianDailyTurnoverIdr: number;
  latestDailyVolumeShares: number;
  activeSessions: number;
  activeRatio: number;
  liquidSessions: number;
  priceSource: string;
  latestSessionClose: string;
  pricesAvailableAt: string;
}

export interface LiquidityResult {
  symbol: string;
  eligibility: Eligibility;
  liquidity: LiquidityMetrics | null;
  completeness: { expectedSessions: number; validCandles: number; evaluated: boolean };
  asOf: string;
  universeVersion: string;
  calendarVersion: string | null;
  rejectionReasons: LiquidityIssue[];
  dataQuality: LiquidityQuality;
}

interface LiquidityEvaluation {
  asOf: string;
  universeVersion: string | null;
  universeAsOf: string | null;
  universeAvailableAt: string | null;
  universeSource: string | null;
  calendarVersion: string | null;
  calendarSource: string | null;
  calendarAvailableAt: string | null;
  policy: {
    lookbackSessions: number;
    minListingSessions: number;
    minActiveSessions: number;
    minActiveRatio: number;
    minLiquidSessions: number;
    minDailyTurnoverIdr: number;
    minMedianTurnoverIdr: number;
    minDailyVolumeShares: number;
    maxPriceAge: string;
    maxReferenceAge: string;
  };
  referenceIssues: LiquidityIssue[];
  results: LiquidityResult[];
}

export type Factor =
  | 'NEWS_CATALYST' | 'PRICE_MOMENTUM' | 'VOLUME_LIQUIDITY' | 'FOREIGN_BROKER_FLOW' | 'MARKET_REGIME_RISK';

type ScoringReason =
  | 'UNIVERSE_UNVERIFIED' | 'SOURCE_MISSING' | 'NOT_AVAILABLE_AT_CUTOFF' | 'SOURCE_STALE'
  | 'SOURCE_DECLARED_QUALITY' | 'INVALID_PROVENANCE' | 'INVALID_METRICS' | 'INVALID_NEWS_EVENT'
  | 'CONFLICTING_NEWS_EVENT';

interface Evidence {
  source: string;
  version: string;
  asOf: string;
  availableAt: string;
  quality: FactorQuality;
}

interface FactorScore {
  factor: Factor;
  weightPct: number;
  score: number | null;
  contribution: number | null;
  rawMetrics: Record<string, number>;
  evidence: Evidence | null;
  quality: FactorQuality;
  explanation: string;
  reason: ScoringReason | null;
}

export interface Assessment {
  symbol: string;
  status: 'SCOREABLE' | 'INSUFFICIENT_DATA' | 'UNVERIFIED';
  overallScore: number | null;
  factors: FactorScore[];
  catalystSummary: {
    eventId: string;
    label: string;
    publishedAt: string;
    firstSeenAt: string;
    availableAt: string;
    impact: number;
  } | null;
  riskNotes: ('MATERIAL_NEGATIVE_CATALYST' | 'BEARISH_MARKET' | 'ELEVATED_MARKET_RISK')[];
  asOf: string;
  sourceVersions: Record<string, string>;
  confidence: 'COMPLETE_FIXTURE' | 'INSUFFICIENT_DATA' | 'UNVERIFIED';
  dataQuality: FactorQuality;
  reasons: ScoringReason[];
}

interface ScoringReport {
  purpose: 'RESEARCH_ONLY';
  modelVersion: string;
  asOf: string;
  policy: {
    newsWeight: number;
    momentumWeight: number;
    liquidityWeight: number;
    flowWeight: number;
    regimeWeight: number;
    maxSnapshotAge: string;
    newsLookback: string;
  };
  ranking: { rank: number; assessment: Assessment }[];
  assessments: Assessment[];
  reasons: ScoringReason[];
}

export interface ScreeningDecision {
  symbol: string;
  status: Eligibility;
  liquidity: LiquidityResult;
  assessment: Assessment | null;
  momentumIssue: LiquidityIssue | null;
}

export interface ScreeningSnapshot {
  mode: ScreeningMode;
  generatedAt: string;
  dataAsOf: string | null;
  cutoff: string;
  quality: ScreeningQuality;
  failure: ScreeningFailure | null;
  liquidity: LiquidityEvaluation | null;
  scoring: ScoringReport | null;
  decisions: ScreeningDecision[];
}
