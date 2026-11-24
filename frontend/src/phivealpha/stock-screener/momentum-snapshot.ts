import { DecimalPipe } from '@angular/common';
import { Component, Input } from '@angular/core';
import type { Assessment, ScreeningQuality } from '../core/stock-screener';

/**
 * Displays observed historical momentum only; never emits an entry signal.
 * Fail closed when snapshot/factor provenance is incomplete or stale.
 */
@Component({
  selector: 'app-momentum-snapshot',
  imports: [DecimalPipe],
  template: `
    <section class="momentum-snapshot" [class.momentum-compact]="compact" aria-label="Momentum harga historis">
      @if (!compact) {
        <div class="momentum-heading">
          <div>
            <p class="momentum-eyebrow">MARKET SNAPSHOT</p>
            <h2>Momentum harga</h2>
            <p class="momentum-description">Perubahan harga historis, bukan prediksi pergerakan berikutnya.</p>
          </div>
          <span class="momentum-quality" [attr.data-tone]="available ? 'valid' : 'unknown'">
            {{ available ? 'Data terverifikasi untuk simulasi' : 'Belum dapat dinilai' }}
          </span>
        </div>
      }

      @if (available && returns; as r) {
        <div class="momentum-values">
          <div class="momentum-measure" [attr.data-trend]="trend(r.five)">
            <span>5 sesi</span>
            <strong class="numeric">{{ r.five > 0 ? '+' : '' }}{{ r.five | number:'1.2-2':'id-ID' }}%</strong>
          </div>
          <div class="momentum-measure" [attr.data-trend]="trend(r.ten)">
            <span>10 sesi</span>
            <strong class="numeric">{{ r.ten > 0 ? '+' : '' }}{{ r.ten | number:'1.2-2':'id-ID' }}%</strong>
          </div>
          @if (!compact) {
            <div class="momentum-reading">
              <span>Interpretasi historis</span>
              <strong>{{ interpretation(r.five, r.ten) }}</strong>
            </div>
          }
        </div>
      } @else {
        <div class="momentum-unavailable">
          <strong>Momentum belum tersedia</strong>
          <span>Kualitas data atau bukti faktor belum memadai.</span>
        </div>
      }

      @if (!compact) {
        <div class="momentum-bottom">
          @if (assessment?.riskNotes?.length) {
            <span class="momentum-caution">Perhatian: {{ riskLabel }}</span>
          } @else {
            <span class="momentum-neutral">Belum ada catatan risiko khusus dari model.</span>
          }
          <span class="momentum-footnote">Data simulasi · Riset saja · Bukan sinyal entry</span>
        </div>
      }
    </section>
  `,
  styles: [`
    :host { display:block; min-width:0; }
    .momentum-snapshot { display:grid; gap:1.1rem; padding:1.3rem; border:1px solid #cee5d5; border-radius:1.08rem; background:linear-gradient(116deg,#ecf8ef,#fff 85%); }
    .momentum-heading { display:flex; flex-wrap:wrap; justify-content:space-between; align-items:start; gap:.8rem; }
    .momentum-eyebrow { font-size:.69rem; letter-spacing:.12em; font-weight:760; color:#087f5b; margin:0 0 .35rem; }
    .momentum-heading h2 { margin:0; font-size:1.35rem; letter-spacing:-.035em; }
    .momentum-description { margin:.3rem 0 0; color:#61776a; font-size:.79rem; }
    .momentum-quality { background:#e0f1e6; color:#236746; border-radius:999px; padding:.35rem .65rem; font-size:.7rem; font-weight:700; }
    .momentum-quality[data-tone=unknown] { background:#fff3df; color:#855c1f; }
    .momentum-values { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:.75rem; }
    .momentum-measure,.momentum-reading { display:grid; align-content:center; gap:.3rem; min-width:0; padding:1rem; border-radius:.84rem; border:1px solid #e5eee8; background:#fff; }
    .momentum-measure span,.momentum-reading span { color:#63776a; font-size:.75rem; }
    .momentum-measure strong { font-size:clamp(1.3rem,2.5vw,2rem); letter-spacing:-.05em; white-space:nowrap; }
    .momentum-measure[data-trend=up] strong { color:#087552; }
    .momentum-measure[data-trend=down] strong { color:#b14b3a; }
    .momentum-measure[data-trend=flat] strong { color:#42574c; }
    .momentum-reading strong { font-size:.94rem; overflow-wrap:anywhere; }
    .momentum-bottom { display:flex; align-items:center; justify-content:space-between; flex-wrap:wrap; gap:.7rem; font-size:.74rem; }
    .momentum-caution { background:#fff2e0; color:#845518; border-radius:.6rem; padding:.45rem .7rem; }
    .momentum-neutral { color:#577364; }
    .momentum-footnote { color:#6c7d72; }
    .momentum-compact { padding:0; gap:0; border:0; background:transparent; }
    .momentum-compact .momentum-values { grid-template-columns:repeat(2,minmax(0,1fr)); gap:.4rem; }
    .momentum-compact .momentum-measure { padding:.2rem .65rem; border:0; background:transparent; }
    .momentum-compact .momentum-measure span { font-size:.67rem; }
    .momentum-compact .momentum-measure strong { font-size:1rem; letter-spacing:-.025em; }
    .momentum-unavailable { display:grid; gap:.3rem; padding:.8rem; color:#6a756d; background:#f6f7f4; border-radius:.7rem; }
    .momentum-unavailable strong { font-size:.82rem; }
    .momentum-unavailable span { font-size:.74rem; }
    @media(max-width:42rem) {
      .momentum-values { grid-template-columns:repeat(2,minmax(0,1fr)); }
      .momentum-reading { grid-column:1/-1; }
      .momentum-snapshot { padding:1rem; }
      .momentum-compact { padding:0; }
    }
  `],
})
export class MomentumSnapshot {
  @Input() assessment: Assessment | null = null;
  @Input() snapshotQuality: ScreeningQuality = 'UNAVAILABLE';
  @Input() compact = false;

  protected get returns(): { five: number; ten: number } | null {
    const assessment = this.assessment;
    if (this.snapshotQuality !== 'COMPLETE'
      || assessment?.status !== 'SCOREABLE'
      || assessment.dataQuality !== 'AVAILABLE') return null;
    const factor = assessment.factors.find(item => item.factor === 'PRICE_MOMENTUM');
    if (factor?.quality !== 'AVAILABLE' || factor.evidence?.quality !== 'AVAILABLE') return null;
    const five = factor.rawMetrics?.['returnFiveSessionsPct'];
    const ten = factor.rawMetrics?.['returnTenSessionsPct'];
    return typeof five === 'number' && Number.isFinite(five)
      && typeof ten === 'number' && Number.isFinite(ten) ? { five, ten } : null;
  }

  protected get available(): boolean { return this.returns !== null; }

  protected trend(value: number): 'up' | 'down' | 'flat' {
    return value > 0 ? 'up' : value < 0 ? 'down' : 'flat';
  }

  protected interpretation(five: number, ten: number): string {
    if (five > 0 && ten > 0) return 'Menguat pada kedua periode';
    if (five < 0 && ten < 0) return 'Melemah pada kedua periode';
    return 'Arah pergerakan belum sejalan';
  }

  protected get riskLabel(): string {
    const notes = this.assessment?.riskNotes ?? [];
    const labels: Record<string, string> = {
      MATERIAL_NEGATIVE_CATALYST: 'Katalis berita negatif',
      BEARISH_MARKET: 'Pasar bearish',
      ELEVATED_MARKET_RISK: 'Risiko pasar meningkat',
    };
    return notes.map(note => labels[note] ?? note).join(' · ');
  }
}
