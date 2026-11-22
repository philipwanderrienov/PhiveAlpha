import { DatePipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { IdxApi } from '../core/idx-api';

@Component({
  imports: [DatePipe, RouterLink],
  template: `
    <header class="page-header">
      <h1>News Intelligence</h1>
      <p class="subtitle">Telusuri kabar simulasi per emiten dan lihat kapan berita diterbitkan.</p>
    </header>
    @if (headlines.isLoading()) {
      <p class="panel state" role="status">Memuat berita…</p>
    } @else if (headlines.error()) {
      <div class="panel state state-error">
        <p role="alert">Berita gagal dimuat.</p>
        <button type="button" (click)="headlines.reload()">Coba lagi</button>
      </div>
    } @else if (headlines.hasValue()) {
      <ul class="plain-list news-feed">
        @for (item of headlines.value(); track item.symbol + item.publishedAt) {
          <li class="panel news-card">
            <div>
              <span class="badge">{{ item.symbol }}</span>
              <h2 class="news-title">{{ item.title }}</h2>
            </div>
            <p class="meta">{{ item.source }} · <time [attr.datetime]="item.publishedAt">{{ item.publishedAt | date:'d MMM y, HH:mm':'+0700' }} WIB</time></p>
            <a class="page-link" routerLink="/stock-analysis" [queryParams]="{symbol: item.symbol}">Lihat saham {{ item.symbol }} <span aria-hidden="true">→</span></a>
          </li>
        } @empty {
          <li class="panel state">Belum ada berita dalam fixture.</li>
        }
      </ul>
    }
  `,
})
export class NewsIntelligencePage {
  private readonly api = inject(IdxApi);
  protected readonly headlines = rxResource({ stream: () => this.api.headlines() });
}
