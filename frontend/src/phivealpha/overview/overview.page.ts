import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { IdxApi } from '../core/idx-api';

@Component({
  imports: [CurrencyPipe, DatePipe, RouterLink],
  template: `
    <h1>Overview</h1>
    <p class="subtitle">Daftar saham Bursa Efek Indonesia dalam fixture.</p>
    @if (stocks.isLoading()) {
      <p role="status">Memuat daftar saham…</p>
    } @else if (stocks.error()) {
      <p role="alert">Daftar saham gagal dimuat. <button (click)="stocks.reload()">Coba lagi</button></p>
    } @else if (stocks.hasValue()) {
      <ul class="cards">
        @for (stock of stocks.value(); track stock.symbol) {
          <li class="panel">
            <h2><a routerLink="/stock-analysis" [queryParams]="{symbol: stock.symbol}">{{ stock.symbol }}</a></h2>
            <p>{{ stock.name }} · {{ stock.sector }}</p>
            <strong>{{ stock.closeIdr | currency:'IDR':'symbol':'1.0-0' }}</strong>
            <p>Penutupan {{ stock.sessionDate | date:'d MMM y':'UTC' }}</p>
          </li>
        } @empty {
          <li>Daftar saham kosong.</li>
        }
      </ul>
    }
  `,
})
export class OverviewPage {
  private readonly api = inject(IdxApi);
  protected readonly stocks = rxResource({ stream: () => this.api.stocks() });
}
