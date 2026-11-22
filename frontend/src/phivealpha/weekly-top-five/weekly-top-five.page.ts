import { DecimalPipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { IdxApi } from '../core/idx-api';

@Component({
  imports: [DecimalPipe, RouterLink],
  template: `
    <header class="page-header">
      <h1>Weekly Top 5</h1>
      <p class="subtitle">Bandingkan perubahan harga dari penutupan pekan sebelumnya. Pilih saham untuk melihat harga dan tanggal sesinya.</p>
    </header>
    @if (ranking.isLoading()) {
      <p class="panel state" role="status">Memuat peringkat…</p>
    } @else if (ranking.error()) {
      <div class="panel state state-error">
        <p role="alert">Peringkat gagal dimuat.</p>
        <button type="button" (click)="ranking.reload()">Coba lagi</button>
      </div>
    } @else if (ranking.hasValue()) {
      <section class="panel" aria-labelledby="weekly-ranking">
        <h2 class="section-heading" id="weekly-ranking">Perubahan mingguan</h2>
        <ol class="ranking" role="list">
          @for (stock of ranking.value(); track stock.symbol) {
            <li class="ranking-row">
              <span class="rank-mark">{{ $index + 1 }}</span>
              <a class="ticker-link" routerLink="/stock-analysis"
                [queryParams]="{symbol: stock.symbol}">{{ stock.symbol }}<span class="ranking-company">{{ stock.name }}</span></a>
              <strong class="change numeric" [class.change-up]="stock.weeklyChangePercent > 0"
                [class.change-down]="stock.weeklyChangePercent < 0">
                {{ stock.weeklyChangePercent > 0 ? '+' : '' }}{{ stock.weeklyChangePercent | number:'1.2-2' }}%
              </strong>
            </li>
          } @empty {
            <li class="state">Belum ada saham dalam peringkat.</li>
          }
        </ol>
      </section>
    }
  `,
})
export class WeeklyTopFivePage {
  private readonly api = inject(IdxApi);
  protected readonly ranking = rxResource({ stream: () => this.api.weeklyTopFive() });
}
