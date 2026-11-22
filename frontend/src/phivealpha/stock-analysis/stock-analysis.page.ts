import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject } from '@angular/core';
import { rxResource, toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { map } from 'rxjs';
import { IdxApi } from '../core/idx-api';

@Component({
  imports: [CurrencyPipe, DatePipe, DecimalPipe],
  template: `
    <header class="page-header">
      <h1>Stock Analysis</h1>
      <p class="subtitle">Masukkan kode emiten untuk melihat harga penutupan dan perubahan mingguannya.</p>
    </header>
    <div class="stack">
      <form class="panel form-row" (submit)="search($event, ticker.value)">
        <label>Kode saham <input #ticker required autocapitalize="characters" spellcheck="false" [value]="symbol()" placeholder="BBCA" /></label>
        <button type="submit">Lihat saham</button>
      </form>
      @if (snapshot.isLoading()) {
        <p class="panel state" role="status">Memuat data saham…</p>
      } @else if (snapshot.error()) {
        <p class="panel state state-error" role="alert">{{ message() }}</p>
      } @else if (snapshot.hasValue()) {
        @let stock = snapshot.value();
        <section class="panel feature-panel" aria-labelledby="stock-result" aria-live="polite">
          <h2 id="stock-result">{{ stock.symbol }} · {{ stock.name }}</h2>
          <p class="meta">{{ stock.sector }} · Sesi {{ stock.sessionDate | date:'d MMM y':'UTC' }}</p>
          <dl class="metric-grid">
            <div>
              <dt>Harga penutupan</dt>
              <dd class="metric-value numeric">{{ stock.closeIdr | currency:'IDR':'symbol':'1.0-0' }}</dd>
            </div>
            <div>
              <dt>Perubahan mingguan</dt>
              <dd class="metric-value numeric" [class.change-up]="stock.weeklyChangePercent > 0"
                [class.change-down]="stock.weeklyChangePercent < 0">
                {{ stock.weeklyChangePercent > 0 ? '+' : '' }}{{ stock.weeklyChangePercent | number:'1.2-2' }}%
              </dd>
            </div>
          </dl>
        </section>
      } @else {
        <p class="panel state">Ketik kode saham, misalnya BBCA, untuk memulai.</p>
      }
    </div>
  `,
})
export class StockAnalysisPage {
  private readonly api = inject(IdxApi);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  protected readonly symbol = toSignal(this.route.queryParamMap.pipe(map(params => params.get('symbol') ?? '')), {
    initialValue: '',
  });
  protected readonly snapshot = rxResource({
    params: () => this.symbol() || undefined,
    stream: ({ params }) => this.api.stock(params),
  });

  protected search(event: SubmitEvent, value: string) {
    event.preventDefault();
    const symbol = value.trim().toUpperCase();
    if (symbol === this.symbol()) {
      this.snapshot.reload();
    } else {
      void this.router.navigate([], { relativeTo: this.route, queryParams: { symbol: symbol || null } });
    }
  }

  protected message() {
    const error = this.snapshot.error();
    return error instanceof HttpErrorResponse && error.status === 404
      ? 'Kode saham tidak tersedia dalam fixture.'
      : 'Data saham gagal dimuat. Coba lagi.';
  }
}
