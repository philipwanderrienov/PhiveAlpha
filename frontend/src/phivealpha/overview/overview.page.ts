import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { IdxApi } from '../core/idx-api';

@Component({
  imports: [CurrencyPipe, DatePipe, RouterLink],
  template: `
    <header class="page-header">
      <h1>Overview</h1>
      <p class="subtitle">Mulai riset saham IDX dengan data simulasi.</p>
    </header>
    <div class="stack">
      <section class="panel feature-panel" aria-labelledby="start-research">
        <h2 id="start-research">Temukan saham untuk diteliti</h2>
        <p>Bandingkan kandidat, lalu lihat bukti yang mendasari setiap peringkat.</p>
        <div class="action-row">
          <a class="action-link" routerLink="/stock-screener">Buka Stock Screener <span aria-hidden="true">→</span></a>
          <a routerLink="/weekly-top-five">Bandingkan perubahan mingguan</a>
        </div>
      </section>
      @if (stocks.isLoading()) {
        <p class="panel state" role="status">Memuat daftar saham…</p>
      } @else if (stocks.error()) {
        <div class="panel state state-error">
          <p role="alert">Daftar saham gagal dimuat.</p>
          <button type="button" (click)="stocks.reload()">Coba lagi</button>
        </div>
      } @else if (stocks.hasValue()) {
        <section aria-labelledby="stock-list">
          <h2 class="section-heading" id="stock-list">Harga penutupan saham</h2>
          <ul class="cards">
            @for (stock of stocks.value(); track stock.symbol) {
              <li class="panel stock-card">
                <div>
                  <h3><a class="ticker-link" routerLink="/stock-analysis"
                    [queryParams]="{symbol: stock.symbol}">{{ stock.symbol }}</a></h3>
                  <p class="meta">{{ stock.name }}</p>
                  <p class="badge">{{ stock.sector }}</p>
                </div>
                <div>
                  <strong class="metric-value numeric">{{ stock.closeIdr | currency:'IDR':'symbol':'1.0-0' }}</strong>
                  <p class="meta">Penutupan {{ stock.sessionDate | date:'d MMM y':'UTC' }}</p>
                </div>
              </li>
            } @empty {
              <li class="panel state">Daftar saham kosong.</li>
            }
          </ul>
        </section>
      }
    </div>
  `,
})
export class OverviewPage {
  private readonly api = inject(IdxApi);
  protected readonly stocks = rxResource({ stream: () => this.api.stocks() });
}
