import { CurrencyPipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { IdxApi } from '../core/idx-api';

@Component({
  imports: [CurrencyPipe, DecimalPipe],
  template: `
    <h1>Paper Trading</h1>
    <p class="subtitle">Simulasi nilai bruto pada harga penutupan fixture, sebelum biaya. Satu lot berisi 100 saham.</p>
    <form class="panel form-row" (submit)="calculate($event, ticker.value, lots.valueAsNumber)">
      <label>Kode saham <input #ticker required value="BBCA" /></label>
      <label>Jumlah lot <input #lots required type="number" min="1" max="10000" step="1" value="1" /></label>
      <button type="submit">Hitung estimasi</button>
    </form>
    @if (estimate.isLoading()) {
      <p role="status">Menghitung estimasi…</p>
    } @else if (estimate.error()) {
      <p role="alert">{{ message() }}</p>
    } @else if (estimate.hasValue()) {
      @let result = estimate.value();
      <section class="panel" aria-live="polite">
        <h2>Estimasi {{ result.symbol }}</h2>
        <dl>
          <dt>Jumlah saham</dt><dd>{{ result.shares | number }}</dd>
          <dt>Harga per saham</dt><dd>{{ result.priceIdr | currency:'IDR':'symbol':'1.0-0' }}</dd>
          <dt>Nilai bruto</dt><dd>{{ result.grossIdr | currency:'IDR':'symbol':'1.0-0' }}</dd>
        </dl>
      </section>
    }
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
