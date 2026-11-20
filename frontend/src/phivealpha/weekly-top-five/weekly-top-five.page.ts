import { DecimalPipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { IdxApi } from '../core/idx-api';

@Component({
  imports: [DecimalPipe, RouterLink],
  template: `
    <h1>Weekly Top 5</h1>
    <p class="subtitle">Peringkat berdasarkan perubahan harga dari penutupan pekan sebelumnya.</p>
    @if (ranking.isLoading()) {
      <p role="status">Memuat peringkat…</p>
    } @else if (ranking.error()) {
      <p role="alert">Peringkat gagal dimuat. <button (click)="ranking.reload()">Coba lagi</button></p>
    } @else if (ranking.hasValue()) {
      <ol class="ranking panel">
        @for (stock of ranking.value(); track stock.symbol) {
          <li>
            <div class="ranking-row">
              <a routerLink="/stock-analysis" [queryParams]="{symbol: stock.symbol}">{{ stock.symbol }} · {{ stock.name }}</a>
              <strong>{{ stock.weeklyChangePercent | number:'1.2-2' }}%</strong>
            </div>
          </li>
        } @empty {
          <li>Belum ada saham dalam peringkat.</li>
        }
      </ol>
    }
  `,
})
export class WeeklyTopFivePage {
  private readonly api = inject(IdxApi);
  protected readonly ranking = rxResource({ stream: () => this.api.weeklyTopFive() });
}
