import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, afterNextRender, computed, ElementRef, inject, Injector, signal, ViewChild } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { IdxApi } from '../core/idx-api';
import type { Assessment, ScreeningQuality } from '../core/stock-screener';
import { ScreenerDetail } from './screener-detail';
import { MomentumSnapshot } from './momentum-snapshot';

@Component({
  imports: [DatePipe, DecimalPipe, RouterLink, ScreenerDetail, MomentumSnapshot],
  templateUrl: './stock-screener.page.html',
  styleUrl: './stock-screener.page.css',
})
export class StockScreenerPage {
  private readonly api = inject(IdxApi);
  protected readonly snapshot = rxResource({ stream: () => this.api.stockScreener() });
  private readonly injector = inject(Injector);
  private readonly catalog = rxResource({ stream: () => this.api.stocks() });
  protected readonly names = computed(() => new Map(
    (this.catalog.hasValue() ? this.catalog.value() : []).map(stock => [stock.symbol, stock.name]),
  ));
  @ViewChild('researchHeading') private heading?: ElementRef<HTMLElement>;
  @ViewChild('shortlistHeading') private shortlist?: ElementRef<HTMLElement>;

  protected openResearch(symbol: string) {
    this.selectedSymbol.set(symbol);
    afterNextRender(() => this.heading?.nativeElement.focus?.(), { injector: this.injector });
  }

  protected closeResearch() {
    const symbol = this.selectedSymbol();
    this.selectedSymbol.set(null);
    afterNextRender(() => {
      const heading = this.shortlist?.nativeElement;
      const button = heading?.ownerDocument?.getElementById(`candidate-${symbol}`);
      (button ?? heading)?.focus?.();
    }, { injector: this.injector });
  }

  protected readonly candidates = computed(() => {
    if (!this.snapshot.hasValue()) return [];
    const snapshot = this.snapshot.value();
    const decisions = new Map(snapshot.decisions.map(decision => [decision.symbol, decision]));
    return (snapshot.scoring?.ranking ?? []).slice(0, 5).map(candidate => ({
      ...candidate,
      decision: decisions.get(candidate.assessment.symbol),
    }));
  });
  protected readonly hasVisibleScores = computed(() => this.candidates().some(
    ({ assessment }) => assessment.overallScore !== null && this.canShowScores(assessment),
  ));
  protected readonly scoreExplanation = 'Skor berasal dari model riset, bukan probabilitas keuntungan atau sinyal beli/jual.';
  protected readonly selectedSymbol = signal<string | null>(null);
  protected readonly selectedCandidate = computed(() => this.candidates().find(
    candidate => candidate.assessment.symbol === this.selectedSymbol(),
  ));

  protected readonly rejections = computed(() => this.snapshot.hasValue()
    ? this.snapshot.value().decisions.filter(decision => decision.status !== 'ELIGIBLE')
    : []);

  protected readonly qualityLabels: Record<ScreeningQuality, string> = {
    COMPLETE: 'Data lengkap',
    INCOMPLETE: 'Data belum lengkap',
    STALE: 'Data kedaluwarsa',
    UNVERIFIED: 'Data belum terverifikasi',
    UNAVAILABLE: 'Data tidak tersedia',
  };

  protected canShowScores(assessment: Assessment) {
    return this.snapshot.hasValue() && this.snapshot.value().quality === 'COMPLETE'
      && this.snapshot.value().scoring?.purpose === 'RESEARCH_ONLY'
      && assessment.status === 'SCOREABLE' && assessment.dataQuality === 'AVAILABLE';
  }
}
