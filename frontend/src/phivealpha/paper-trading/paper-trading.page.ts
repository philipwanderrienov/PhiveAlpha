import { CurrencyPipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { IdxApi } from '../core/idx-api';

@Component({
  imports: [CurrencyPipe, DecimalPipe],
  template: `
    <header class="page-header">
      <h1>Paper Trading</h1>
      <p class="subtitle">Perkirakan nilai saham berdasarkan jumlah lot dan harga penutupan dalam simulasi.</p>
    </header>
    <div class="stack">
      <form class="panel" (submit)="calculate($event, ticker.value, lots.valueAsNumber)">
        <h2 class="section-heading">Hitung nilai saham</h2>
        <div class="form-row">
          <label>Kode saham <input #ticker required autocapitalize="characters" spellcheck="false" value="BBCA" /></label>
          <label>Jumlah lot <input #lots required type="number" min="1" max="10000" step="1" value="1" /></label>
          <button type="submit">Hitung estimasi</button>
        </div>
        <p class="meta">Satu lot berisi 100 saham.</p>
      </form>
      @if (estimate.isLoading()) {
        <p class="panel state" role="status">Menghitung estimasi…</p>
      } @else if (estimate.error()) {
        <p class="panel state state-error" role="alert">{{ message() }}</p>
      } @else if (estimate.hasValue()) {
        @let result = estimate.value();
        <section class="panel feature-panel" aria-labelledby="estimate-result" aria-live="polite">
          <h2 id="estimate-result">Estimasi {{ result.symbol }}</h2>
          <p class="metric-value numeric gross-value">{{ result.grossIdr | currency:'IDR':'symbol':'1.0-0' }}</p>
          <p class="meta">Nilai bruto sebelum biaya</p>
          <dl class="metric-grid">
            <div><dt>Jumlah saham</dt><dd class="metric-value numeric">{{ result.shares | number }}</dd></div>
            <div><dt>Harga per saham</dt><dd class="metric-value numeric">{{ result.priceIdr | currency:'IDR':'symbol':'1.0-0' }}</dd></div>
          </dl>
        </section>
      }
    </div>
  `,
})
export class PaperTradingPage {
  private readonly api = inject(IdxApi);
  private readonly request = signal<{ symbol: string; lots: number } | undefined>(undefined);
  protected readonly estimate = rxResource({
    params: () => this.request(),
    stream: ({ params }) => this.api.paperEstimate(params.symbol, params.lots),
  });

  protected calculate(event: SubmitEvent, ticker: string, lots: number) {
    event.preventDefault();
    this.request.set({ symbol: ticker.trim().toUpperCase(), lots });
  }

  protected message() {
    const error = this.estimate.error();
    return error instanceof HttpErrorResponse && error.status === 404
      ? 'Kode saham tidak tersedia dalam fixture.'
      : 'Estimasi gagal dihitung. Periksa kode saham dan jumlah lot.';
  }
}
