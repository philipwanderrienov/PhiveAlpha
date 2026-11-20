import { DatePipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { IdxApi } from '../core/idx-api';

@Component({
  imports: [DatePipe, RouterLink],
  template: `
    <h1>News Intelligence</h1>
    <p class="subtitle">Contoh berita bertanda waktu dengan tautan ke saham terkait.</p>
    @if (headlines.isLoading()) {
      <p role="status">Memuat berita…</p>
    } @else if (headlines.error()) {
      <p role="alert">Berita gagal dimuat. <button (click)="headlines.reload()">Coba lagi</button></p>
    } @else if (headlines.hasValue()) {
      <ul class="cards">
        @for (item of headlines.value(); track item.symbol + item.publishedAt) {
          <li class="panel">
            <a routerLink="/stock-analysis" [queryParams]="{symbol: item.symbol}">{{ item.symbol }}</a>
            <h2>{{ item.title }}</h2>
            <p>{{ item.source }} · {{ item.publishedAt | date:'d MMM y, HH:mm':'+0700' }} WIB</p>
          </li>
        } @empty {
          <li>Belum ada berita dalam fixture.</li>
        }
      </ul>
    }
  `,
})
export class NewsIntelligencePage {
  private readonly api = inject(IdxApi);
  protected readonly headlines = rxResource({ stream: () => this.api.headlines() });
}
