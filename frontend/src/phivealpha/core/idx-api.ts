import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';

export interface StockSnapshot {
  symbol: string;
  name: string;
  sector: string;
  sessionDate: string;
  closeIdr: number;
  weeklyChangePercent: number;
}

export interface Headline {
  symbol: string;
  title: string;
  source: string;
  publishedAt: string;
}

export interface PaperEstimate {
  symbol: string;
  lots: number;
  shares: number;
  priceIdr: number;
  grossIdr: number;
}

@Injectable({ providedIn: 'root' })
export class IdxApi {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/idx/v1';

  stocks() {
    return this.http.get<StockSnapshot[]>(`${this.base}/stocks`);
  }

  stock(symbol: string) {
    return this.http.get<StockSnapshot>(`${this.base}/stocks/${encodeURIComponent(symbol)}`);
  }

  weeklyTopFive() {
    return this.http.get<StockSnapshot[]>(`${this.base}/weekly-top-five`);
  }

  headlines() {
    return this.http.get<Headline[]>(`${this.base}/headlines`);
  }

  paperEstimate(symbol: string, lots: number) {
    return this.http.get<PaperEstimate>(`${this.base}/paper-trade/preview`, { params: { symbol, lots } });
  }
}
