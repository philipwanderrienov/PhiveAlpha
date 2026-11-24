import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, inject } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { IdxApi } from '../core/idx-api';
import type { Assessment } from '../core/stock-screener';
import { MomentumSnapshot } from '../stock-screener/momentum-snapshot';

@Component({
  imports: [CurrencyPipe, DatePipe, DecimalPipe, RouterLink, MomentumSnapshot],
  styleUrl: './overview.page.css',
  template: `
    <header class="workspace-header">
      <div>
        <p class="section-label">PHIVEALPHA / IDX RESEARCH</p>
        <h1>Market overview</h1>
        <p class="subtitle">Kandidat, momentum historis, dan risiko riset dalam satu layar.</p>
      </div>
      <span class="workspace-mode">Simulasi · Bukan data live</span>
    </header>

    <section class="workspace-stats" aria-label="Ringkasan pasar dan kualitas riset">
      <div class="workspace-stat">
        <span class="stat-label">IHSG / Market Pulse</span>
        <strong>Belum tersedia</strong>
        <small>Benchmark pasar belum tersedia melalui endpoint overview.</small>
      </div>
      <div class="workspace-stat">
        <span class="stat-label">Kandidat riset</span>
        @if (screening.hasValue()) {
          <strong class="stat-number">{{ candidates().length }}</strong>
          <small>Hasil screening pada snapshot simulasi.</small>
        } @else {
          <strong>—</strong><small>Belum bisa dihitung.</small>
        }
      </div>
      <div class="workspace-stat">
        <span class="stat-label">Kualitas screening</span>
        @if (screening.hasValue()) {
          <strong>{{ screening.value().quality }}</strong>
          <small>{{ screening.value().mode === 'FIXTURE' ? 'Fixture sintetis' : 'Development persistence' }}</small>
        } @else {
          <strong>Belum tersedia</strong>
          <small>Menunggu hasil snapshot.</small>
        }
      </div>
    </section>

    <section class="workspace-section" aria-labelledby="watchlist-heading">
      <div class="workspace-section-head">
        <div>
          <p class="section-label">DISCOVER</p>
          <h2 id="watchlist-heading">Momentum watchlist</h2>
          <p class="workspace-help">Perubahan historis 5 dan 10 sesi terlihat langsung, tanpa membuka detail faktor.</p>
        </div>
        <a class="workspace-link" routerLink="/stock-screener">Semua riset <span aria-hidden="true">→</span></a>
      </div>

      @if (screening.isLoading()) {
        <p class="panel state" role="status">Memuat ringkasan screening…</p>
      } @else if (screening.error()) {
        <div class="panel state state-error">
          <p role="alert">Ringkasan screener gagal dimuat. Tidak ada momentum yang ditampilkan.</p>
          <button type="button" (click)="screening.reload()">Coba lagi</button>
        </div>
      } @else if (screening.hasValue()) {
        @let data = screening.value();
        <div class="watchlist-panel">
          <div class="watchlist-meta">
            <span class="badge" [class.state-warning]="data.quality !== 'COMPLETE'">{{ data.quality === 'COMPLETE' ? 'Kualitas lengkap' : 'Perlu verifikasi' }}</span>
            <span>{{ data.dataAsOf ? 'Snapshot ' + (data.dataAsOf | date:'d MMM y, HH:mm':'+0700') + ' WIB' : 'Waktu snapshot belum tersedia' }}</span>
            <span>Data {{ data.mode === 'FIXTURE' ? 'sintetis' : 'development' }} · Riset saja</span>
          </div>
          <div class="watchlist-columns" aria-hidden="true">
            <span>Emiten</span><span>Harga acuan</span><span>Momentum 5 / 10 sesi</span><span>Skor</span><span>Risiko</span><span>Trade setup</span><span></span>
          </div>
          <ol class="watchlist-rows">
            @for (candidate of candidates(); track candidate.assessment.symbol) {
              @let assessment = candidate.assessment;
              <li class="watchlist-item">
                <div class="watchlist-identity">
                  <span class="rank-mark">{{ candidate.rank }}</span>
                  <div>
                    <strong>{{ assessment.symbol }}</strong>
                    <small>{{ names().get(assessment.symbol) ?? 'Nama emiten belum tersedia' }}</small>
                  </div>
                </div>
                <div class="watchlist-price">
                  <small class="mobile-label">Penutupan simulasi</small>
                  @if (prices().get(assessment.symbol); as stock) {
                    <strong>{{ stock.closeIdr | currency:'IDR':'symbol':'1.0-0' }}</strong>
                    <small>{{ stock.sessionDate | date:'d MMM y':'UTC' }}</small>
                  } @else {
                    <strong>—</strong><small>Belum tersedia</small>
                  }
                </div>
                <app-momentum-snapshot [assessment]="assessment" [snapshotQuality]="data.quality" [compact]="true" />
                <div class="watchlist-score">
                  <small class="mobile-label">Skor riset</small>
                  @if (canShowScores(assessment) && assessment.overallScore !== null) {
                    <strong>{{ assessment.overallScore | number:'1.0-2':'id-ID' }}</strong>
                  } @else { <strong>—</strong> }
                </div>
                <div class="watchlist-risk" [attr.data-risk]="assessment.riskNotes.length ? 'warning' : 'neutral'">
                  {{ assessment.riskNotes.length ? 'Ada catatan risiko' : 'Belum ada flag risiko model' }}
                </div>
                <div class="watchlist-setup">
                  <strong>Entry · TP · SL</strong>
                  <span>— · — · —</span>
                  <small>Belum dihitung</small>
                </div>
                <a class="watchlist-open" routerLink="/stock-screener" [queryParams]="{ symbol: assessment.symbol }" [attr.aria-label]="'Buka riset ' + assessment.symbol">
                  Detail <span aria-hidden="true">↗</span>
                </a>
              </li>
            } @empty {
              <li class="watchlist-empty">Belum ada kandidat yang dapat ditampilkan.</li>
            }
          </ol>
          <p class="watchlist-footnote">Momentum masa lalu bukan prediksi harga dan bukan rekomendasi entry. Skor disembunyikan jika snapshot tidak memenuhi syarat kualitas.</p>
        </div>
      }
    </section>

    <section class="workspace-section trade-plan-explainer" aria-labelledby="trade-planning-heading">
      <div class="workspace-section-head">
        <div>
          <p class="section-label">TRADE PLANNING</p>
          <h2 id="trade-planning-heading">Entry, target profit & stop loss</h2>
          <p class="workspace-help">Rencana harga akan tampil per emiten setelah engine setup tervalidasi. Saat ini belum ada level entry otomatis.</p>
        </div>
        <a class="workspace-link" routerLink="/paper-trading">Simulasi modal <span aria-hidden="true">→</span></a>
      </div>
      <div class="trade-plan-preview" role="note">
        <div class="trade-plan-icon" aria-hidden="true">◎</div>
        <div>
          <strong>Trade setup belum tersedia</strong>
          <p>Entry zone, TP, SL, risk/reward, dan validitas setup menunggu perhitungan backend. Harga penutupan dan momentum tidak dipakai sebagai pengganti sinyal beli.</p>
        </div>
        <span class="trade-plan-pill">Research only</span>
      </div>
    </section>

    <div class="workspace-bottom">
      <section class="panel workspace-risk" aria-labelledby="risk-heading">
        <p class="section-label">RISK MONITOR</p>
        <h2 id="risk-heading">Yang perlu diperhatikan</h2>
        @if (screening.hasValue()) {
          <div class="risk-metrics">
            <div><strong>{{ rejectedCount() }}</strong><span>Saham belum lolos screening</span></div>
            <div><strong>{{ riskFlagCount() }}</strong><span>Kandidat dengan catatan risiko model</span></div>
          </div>
          @if (screening.value().quality !== 'COMPLETE') {
            <p class="risk-warning">Kualitas dataset belum lengkap. Penilaian momentum dan skor tidak boleh dianggap valid.</p>
          } @else {
            <p class="workspace-help">Status ini berasal dari fixture riset, bukan kondisi pasar live.</p>
          }
        } @else {
          <p class="state">Ringkasan risiko belum tersedia.</p>
        }
        <a class="workspace-link" routerLink="/stock-screener">Periksa keputusan screening <span aria-hidden="true">→</span></a>
      </section>

      <section class="panel workspace-chart" aria-labelledby="chart-heading">
        <p class="section-label">CHART WORKSPACE</p>
        <h2 id="chart-heading">Analisis grafik</h2>
        <div class="chart-placeholder" role="note">
          <span aria-hidden="true">▥</span>
          <strong>TradingView belum diintegrasikan</strong>
          <small>Grafik dan indikator akan ditambahkan setelah verifikasi dukungan simbol IDX dan ketersediaan data.</small>
        </div>
        <a class="workspace-link" routerLink="/stock-analysis">Buka Stock Analysis <span aria-hidden="true">→</span></a>
      </section>
    </div>

    <section class="workspace-section" aria-labelledby="prices-heading">
      <div class="workspace-section-head">
        <div>
          <p class="section-label">PRICE REFERENCE</p>
          <h2 id="prices-heading">Harga penutupan</h2>
          <p class="workspace-help">Data historis simulasi; bukan harga perdagangan saat ini.</p>
        </div>
      </div>
      @if (stocks.isLoading()) {
        <p class="panel state" role="status">Memuat harga historis…</p>
      } @else if (stocks.error()) {
        <div class="panel state state-error">
          <p role="alert">Data harga gagal dimuat.</p>
          <button type="button" (click)="stocks.reload()">Coba lagi</button>
        </div>
      } @else if (stocks.hasValue()) {
        <ul class="price-list">
          @for (stock of stocks.value(); track stock.symbol) {
            <li class="price-item">
              <div>
                <a class="ticker-link" routerLink="/stock-analysis" [queryParams]="{ symbol: stock.symbol }">{{ stock.symbol }}</a>
                <small>{{ stock.name }}</small>
              </div>
              <div class="price-number">
                <strong>{{ stock.closeIdr | currency:'IDR':'symbol':'1.0-0' }}</strong>
                <small>{{ stock.sessionDate | date:'d MMM y':'UTC' }}</small>
              </div>
            </li>
          } @empty {
            <li class="watchlist-empty">Belum ada harga historis.</li>
          }
        </ul>
      }
    </section>
  `,
})
export class OverviewPage {
  private readonly api = inject(IdxApi);
  protected readonly stocks = rxResource({ stream: () => this.api.stocks() });
  protected readonly screening = rxResource({ stream: () => this.api.stockScreener() });

  protected readonly names = computed(() => new Map(
    (this.stocks.hasValue() ? this.stocks.value() : []).map(stock => [stock.symbol, stock.name]),
  ));

  protected readonly prices = computed(() => new Map(
    (this.stocks.hasValue() ? this.stocks.value() : []).map(stock => [stock.symbol, stock]),
  ));

  protected readonly candidates = computed(() => this.screening.hasValue()
    ? (this.screening.value().scoring?.ranking ?? []).slice(0, 5)
    : [],
  );

  protected readonly rejectedCount = computed(() => this.screening.hasValue()
    ? this.screening.value().decisions.filter(decision => decision.status !== 'ELIGIBLE').length
    : 0,
  );

  protected readonly riskFlagCount = computed(() => this.candidates()
    .filter(candidate => candidate.assessment.riskNotes.length > 0).length,
  );

  protected canShowScores(assessment: Assessment): boolean {
    return this.screening.hasValue()
      && this.screening.value().quality === 'COMPLETE'
      && this.screening.value().scoring?.purpose === 'RESEARCH_ONLY'
      && assessment.status === 'SCOREABLE'
      && assessment.dataQuality === 'AVAILABLE';
  }
}
