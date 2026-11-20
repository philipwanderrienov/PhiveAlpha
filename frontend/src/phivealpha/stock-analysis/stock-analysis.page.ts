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
    <h1>Stock Analysis</h1>
    <p class="subtitle">Lihat harga penutupan dan perubahan mingguan berdasarkan kode saham.</p>
    <form class="panel form-row" (submit)="search($event, ticker.value)">
      <label>Kode saham <input #ticker required [value]="symbol()" placeholder="BBCA" /></label>
      <button type="submit">Lihat saham</button>
    </form>
    @if (snapshot.isLoading()) {
      <p role="status">Memuat data saham…</p>
    } @else if (snapshot.error()) {
      <p role="alert">{{ message() }}</p>
    } @else if (snapshot.hasValue()) {
      @let stock = snapshot.value();
      <section class="panel">
        <h2>{{ stock.symbol }} · {{ stock.name }}</h2>
        <dl>
          <dt>Sektor</dt><dd>{{ stock.sector }}</dd>
          <dt>Sesi</dt><dd>{{ stock.sessionDate | date:'d MMM y':'UTC' }}</dd>
          <dt>Harga penutupan</dt><dd>{{ stock.closeIdr | currency:'IDR':'symbol':'1.0-0' }}</dd>
          <dt>Perubahan mingguan</dt><dd>{{ stock.weeklyChangePercent | number:'1.2-2' }}%</dd>
        </dl>
      </section>
    }
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
