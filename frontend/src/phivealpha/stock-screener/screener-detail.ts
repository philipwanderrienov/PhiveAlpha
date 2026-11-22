import { KeyValuePipe } from '@angular/common';
import { Component, Input } from '@angular/core';
import type { Assessment, Factor, LiquidityReason, ScreeningDecision, ScreeningQuality } from '../core/stock-screener';

const liquidityReasons: Record<LiquidityReason, string> = {
  REFERENCE_NOT_AVAILABLE_AT_CUTOFF: 'Daftar saham belum tersedia pada batas waktu riset.',
  STALE_UNIVERSE_REFERENCE: 'Daftar saham sudah kedaluwarsa.',
  DUPLICATE_INSTRUMENT: 'Kode saham muncul lebih dari sekali dalam daftar.',
  SECURITY_TYPE_UNVERIFIED: 'Jenis instrumen belum terverifikasi.',
  NOT_COMMON_STOCK: 'Instrumen ini bukan saham biasa.',
  LISTING_STATUS_UNVERIFIED: 'Status pencatatan belum terverifikasi.',
  SUSPENDED: 'Perdagangan saham sedang dihentikan sementara.',
  DELISTED: 'Saham sudah tidak tercatat di bursa.',
  IPO_TOO_RECENT: 'Saham belum cukup lama tercatat untuk periode riset.',
  LISTING_DATE_UNVERIFIED: 'Tanggal pencatatan belum terverifikasi.',
  CORPORATE_ACTION_UNVERIFIED: 'Aksi korporasi yang memengaruhi harga belum terverifikasi.',
  CALENDAR_NOT_AVAILABLE_AT_CUTOFF: 'Kalender bursa belum tersedia pada batas waktu riset.',
  CALENDAR_OUT_OF_COVERAGE: 'Kalender bursa belum mencakup seluruh periode riset.',
  INVALID_CANDLE: 'Ada catatan harga atau volume harian yang tidak valid.',
  NON_TRADING_CANDLE: 'Ada catatan harga pada hari tanpa perdagangan.',
  DUPLICATE_CANDLE: 'Ada catatan harga harian ganda.',
  MISSING_CANDLE: 'Catatan harga harian tidak lengkap.',
  STALE_PRICE: 'Catatan harga sudah kedaluwarsa.',
  INVALID_PRICE_AVAILABILITY: 'Waktu ketersediaan harga tidak valid.',
  ADJUSTMENT_BASIS_UNVERIFIED: 'Dasar penyesuaian harga belum terverifikasi.',
  MIXED_PRICE_SOURCE: 'Catatan harga berasal dari sumber yang berbeda.',
  ACTIVE_SESSIONS_BELOW_MINIMUM: 'Jumlah hari dengan perdagangan di bawah batas minimum.',
  ACTIVE_RATIO_BELOW_MINIMUM: 'Proporsi hari dengan perdagangan di bawah batas minimum.',
  LIQUID_SESSIONS_BELOW_MINIMUM: 'Terlalu sedikit hari dengan nilai dan volume transaksi yang memadai.',
  MEDIAN_TURNOVER_BELOW_MINIMUM: 'Nilai tengah transaksi harian di bawah batas minimum.',
};

const factors: Record<Factor, { label: string; description: string }> = {
  VOLUME_LIQUIDITY: { label: 'Likuiditas', description: 'Seberapa aktif saham diperdagangkan, dilihat dari nilai dan volume transaksi.' },
  PRICE_MOMENTUM: { label: 'Momentum harga', description: 'Arah perubahan harga selama lima dan sepuluh sesi bursa.' },
  NEWS_CATALYST: { label: 'Katalis berita', description: 'Peristiwa yang berpotensi memengaruhi saham dalam periode riset.' },
  FOREIGN_BROKER_FLOW: { label: 'Arus transaksi', description: 'Selisih nilai pembelian dan penjualan investor asing dan kelompok broker yang diamati.' },
  MARKET_REGIME_RISK: { label: 'Risiko pasar', description: 'Arah indeks acuan dan tingkat risiko pasar dalam model riset.' },
};

@Component({
  selector: 'app-screener-detail',
  imports: [KeyValuePipe],
  templateUrl: './screener-detail.html',
  styleUrl: './screener-detail.css',
})
export class ScreenerDetail {
  @Input() decision?: ScreeningDecision;
  @Input({ required: true }) assessment: Assessment | null = null;
  @Input() showScores = false;
  @Input() snapshotQuality: ScreeningQuality = 'UNAVAILABLE';
  @Input() excluded = false;

  protected readonly liquidityReasons = liquidityReasons;
  protected readonly factors = factors;
  protected get issues() {
    const decision = this.decision;
    return [...(decision?.liquidity.rejectionReasons ?? []), ...(decision?.momentumIssue ? [decision.momentumIssue] : [])];
  }

  protected get summaries() {
    const assessment = this.assessment;
    const decision = this.decision;
    return (Object.keys(factors) as Factor[]).map(key => {
      const factor = assessment?.factors.find(item => item.factor === key);
      const metrics = factor?.rawMetrics;
      const unavailable = { tone: 'unavailable', label: 'Belum tersedia', text: 'Bukti yang dapat digunakan belum tersedia.' };
      const neutral = { tone: 'neutral', label: 'Netral', text: 'Bukti tersedia dengan arah yang beragam atau mendatar.' };
      const favorable = (text: string) => ({ tone: 'favorable', label: 'Mendukung', text });
      const caution = (text: string) => ({ tone: 'caution', label: 'Perlu perhatian', text });
      let summary = unavailable;

      if (this.snapshotQuality === 'STALE' || this.snapshotQuality === 'UNVERIFIED') {
        summary = { ...unavailable, text: 'Mutu data riset belum memadai untuk menyimpulkan arah faktor.' };
      } else if (this.snapshotQuality !== 'UNAVAILABLE' && key === 'VOLUME_LIQUIDITY'
          && decision?.liquidity.eligibility === 'ELIGIBLE' && decision.liquidity.dataQuality === 'COMPLETE') {
        summary = favorable('Aktivitas dan nilai transaksi memenuhi ambang penyaringan.');
      } else if (this.snapshotQuality !== 'UNAVAILABLE' && factor?.quality === 'AVAILABLE'
          && factor.evidence?.quality === 'AVAILABLE' && metrics) {
        switch (key) {
          case 'PRICE_MOMENTUM': {
            const { returnFiveSessionsPct: five, returnTenSessionsPct: ten } = metrics;
            if (five === undefined || ten === undefined) break;
            summary = five > 0 && ten > 0 ? favorable('Harga naik pada kedua periode yang diamati.')
              : five < 0 && ten < 0 ? caution('Harga turun pada kedua periode yang diamati.') : neutral;
            break;
          }
          case 'NEWS_CATALYST': {
            const impact = assessment?.catalystSummary?.impact;
            if (assessment?.riskNotes.includes('MATERIAL_NEGATIVE_CATALYST') || (impact ?? 0) < 0) {
              summary = caution('Model mencatat peristiwa dengan dampak negatif.');
            } else if (impact !== undefined && impact > 0) {
              summary = favorable('Peristiwa utama dinilai berdampak positif oleh model.');
            } else if (metrics['impact'] === 0 || impact === 0) {
              summary = { ...neutral, text: 'Model tidak mencatat katalis dengan dampak terukur.' };
            }
            break;
          }
          case 'FOREIGN_BROKER_FLOW': {
            const { foreignNetIdr: foreign, brokerCohortNetIdr: broker } = metrics;
            if (foreign === undefined || broker === undefined) break;
            summary = foreign > 0 && broker > 0 ? favorable('Investor asing dan kelompok broker mencatat pembelian bersih.')
              : foreign < 0 && broker < 0 ? caution('Investor asing dan kelompok broker mencatat penjualan bersih.') : neutral;
            break;
          }
          case 'MARKET_REGIME_RISK':
            if (assessment?.riskNotes.includes('BEARISH_MARKET') || assessment?.riskNotes.includes('ELEVATED_MARKET_RISK')) {
              summary = caution('Model menandai pelemahan pasar atau tingkat risiko yang tinggi.');
            } else if (metrics['riskLevel'] !== undefined && metrics['benchmarkReturnFiveSessionsPct'] !== undefined) {
              summary = { ...neutral, text: 'Model tidak menandai pelemahan pasar atau risiko tinggi.' };
            }
            break;
        }
      }
      return { key, ...factors[key], ...summary };
    });
  }
}
