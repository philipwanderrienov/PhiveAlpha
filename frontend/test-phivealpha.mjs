import '@angular/compiler';
import { registerLocaleData } from '@angular/common';
import localeId from '@angular/common/locales/id';
import { provideLocationMocks } from '@angular/common/testing';
import { HttpClient } from '@angular/common/http';
import { createComponent, Injector } from '@angular/core';
import { provideRouter, Router } from '@angular/router';
import assert from 'node:assert/strict';
import { test } from 'node:test';
import { Subject, of } from 'rxjs';
import { application, componentModule, content, descendants, element } from './test-phivealpha-renderer.mjs';

registerLocaleData(localeId);

const apiUrl = componentModule('core/idx-api');
const { IdxApi } = await import(apiUrl);
const detailUrl = componentModule('stock-screener/screener-detail');
const momentumUrl = componentModule('stock-screener/momentum-snapshot');
const pageUrl = componentModule('stock-screener/stock-screener.page', {
  '../core/idx-api': apiUrl,
  './screener-detail': detailUrl,
  './momentum-snapshot': momentumUrl,
});
const { StockScreenerPage } = await import(pageUrl);

const { AppComponent } = await import(componentModule('app.component'));
const { routes } = await import(componentModule('app.routes', {
  './stock-screener/stock-screener.page': pageUrl,
  './weekly-top-five/weekly-top-five.page': componentModule('weekly-top-five/weekly-top-five.page', {
    '../core/idx-api': apiUrl,
  }),
  './overview/overview.page': componentModule('overview/overview.page', {
    '../core/idx-api': apiUrl,
    '../stock-screener/momentum-snapshot': momentumUrl,
  }),
  './news-intelligence/news-intelligence.page': componentModule('news-intelligence/news-intelligence.page', {
    '../core/idx-api': apiUrl,
  }),
  './paper-trading/paper-trading.page': componentModule('paper-trading/paper-trading.page', {
    '../core/idx-api': apiUrl,
  }),
  './stock-analysis/stock-analysis.page': componentModule('stock-analysis/stock-analysis.page', {
    '../core/idx-api': apiUrl,
  }),
}));

const cases = [
  ['stocks', api => api.stocks(), ['/api/idx/v1/stocks']],
  ['stock encodes the symbol', api => api.stock('AA/B?#'), ['/api/idx/v1/stocks/AA%2FB%3F%23']],
  ['weekly ranking', api => api.weeklyTopFive(), ['/api/idx/v1/weekly-top-five']],
  ['stock screener', api => api.stockScreener(), ['/api/idx/v1/stock-screener']],
  ['headlines', api => api.headlines(), ['/api/idx/v1/headlines']],
  ['paper estimate uses GET with lot parameters', api => api.paperEstimate('BBCA', 2),
    ['/api/idx/v1/paper-trade/preview', { params: { symbol: 'BBCA', lots: 2 } }]],
];

for (const [name, request, expected] of cases) {
  test(name, () => {
    const calls = [];
    const response = { fixture: true };
    const injector = Injector.create({
      providers: [
        { provide: HttpClient, useValue: { get: (...args) => { calls.push(args); return response; } } },
        { provide: IdxApi, useClass: IdxApi },
      ],
    });

    assert.equal(request(injector.get(IdxApi)), response);
    assert.deepEqual(calls, [expected]);
  });
}

const cutoff = '2026-11-13T10:00:00Z';
const emptySnapshot = {
  mode: 'FIXTURE', quality: 'COMPLETE', generatedAt: cutoff, dataAsOf: null,
  cutoff, liquidity: null, scoring: null, failure: null, decisions: [],
};

function mount(app, component) {
  const host = element('app-root');
  const page = createComponent(component, { hostElement: host, environmentInjector: app.injector });
  app.attachView(page.hostView);
  app.tick();
  return host;
}

async function renderSnapshot(t, snapshot) {
  const app = await application([provideRouter([]), provideLocationMocks(), {
    provide: IdxApi,
    useValue: {
      stockScreener: () => of(snapshot),
      stocks: () => of([{ symbol: 'TEST0', name: 'Synthetic issuer' }]),
    },
  }]);
  t.after(() => app.destroy());
  return { app, host: mount(app, StockScreenerPage) };
}

test('screener renders loading, transport failure, retry and an empty shortlist', async t => {
  const first = new Subject();
  let attempts = 0;
  const app = await application([{
    provide: IdxApi,
    useValue: { stockScreener: () => ++attempts === 1 ? first : of(emptySnapshot), stocks: () => of([]) },
  }]);
  t.after(() => app.destroy());
  const host = mount(app, StockScreenerPage);
  assert.match(content(host), /Memuat hasil screener/);
  first.error(new Error('offline'));
  await app.whenStable();
  app.tick();
  assert.equal(content(descendants(host, node => node.attrs.role === 'alert')[0]), 'Hasil screener gagal dimuat.');
  descendants(host, node => node.tag === 'button')[0].events.click();
  await app.whenStable();
  app.tick();
  assert.equal(attempts, 2);
  assert.match(content(host), /Belum ada kandidat dalam shortlist/);
});

const evidence = { source: 'Synthetic factor source', version: 'factor-v1', asOf: cutoff,
  availableAt: cutoff, quality: 'AVAILABLE' };
const ranking = Array.from({ length: 6 }, (_, index) => ({
  rank: index + 1,
  assessment: {
    symbol: `TEST${index}`, status: 'SCOREABLE', overallScore: 61.256789, dataQuality: 'AVAILABLE',
    confidence: 'COMPLETE_FIXTURE', reasons: [], riskNotes: [],
    catalystSummary: { label: 'Synthetic event', eventId: 'test-event', publishedAt: cutoff,
      firstSeenAt: cutoff, availableAt: cutoff, impact: 0 },
    factors: [
      { factor: 'PRICE_MOMENTUM', quality: 'AVAILABLE', weightPct: 25, score: 60, contribution: 15,
        evidence, explanation: 'Momentum explanation', rawMetrics: { returnFiveSessionsPct: 0.423456789, returnTenSessionsPct: 1 } },
      { factor: 'NEWS_CATALYST', quality: 'AVAILABLE', weightPct: 30, score: 50, contribution: 15,
        evidence, explanation: 'News explanation', rawMetrics: {} },
    ],
  },
}));
const result = {
  eligibility: 'ELIGIBLE', dataQuality: 'COMPLETE', rejectionReasons: [],
  completeness: { evaluated: true, validCandles: 20, expectedSessions: 20 },
  liquidity: { latestDailyTurnoverIdr: 10_000, medianDailyTurnoverIdr: 15_000,
    latestDailyVolumeShares: 100, activeSessions: 20, activeRatio: 1, liquidSessions: 19,
    priceSource: 'Synthetic OHLCV', latestSessionClose: cutoff, pricesAvailableAt: cutoff },
};
const rankedSnapshot = {
  ...emptySnapshot,
  dataAsOf: cutoff,
  scoring: { purpose: 'RESEARCH_ONLY', modelVersion: 'fixture-model-v1', ranking, reasons: [] },
  liquidity: { universeSource: 'Synthetic universe', universeVersion: 'u-v1', universeAsOf: cutoff,
    universeAvailableAt: cutoff, calendarSource: 'Synthetic calendar', calendarVersion: 'c-v1',
    calendarAvailableAt: cutoff, referenceIssues: [] },
  decisions: [{ symbol: 'TEST0', status: 'ELIGIBLE', liquidity: result, assessment: ranking[0].assessment },
    { symbol: 'TEST_REJECTED', status: 'INELIGIBLE', liquidity: {
      ...result, eligibility: 'INELIGIBLE', liquidity: null,
      completeness: { ...result.completeness, evaluated: false },
      rejectionReasons: [{ reason: 'SUSPENDED', tradingDate: null }],
    }, assessment: null }],
};

const candidateActions = host => descendants(host, node => node.id?.startsWith('candidate-'));
const researchView = host => descendants(host, node => node.attrs.id === 'research-detail')[0];

test('discover compares five ranked cards, then investigates one stock and returns to the shortlist', async t => {
  const { app, host } = await renderSnapshot(t, rankedSnapshot);
  const actions = candidateActions(host);
  assert.deepEqual(actions.map(button => button.id), ranking.slice(0, 5).map(
    ({ assessment }) => `candidate-${assessment.symbol}`,
  ));
  assert.match(content(host), /#1.*TEST0.*Synthetic issuer.*Skor riset61,26/);
  assert.match(content(host), /TEST_REJECTED.*Perdagangan saham sedang dihentikan sementara/);
  assert.equal(researchView(host), undefined);

  actions[0].events.click();
  app.tick();
  const research = researchView(host);
  const details = descendants(research, node => node.tag === 'details');
  assert.ok(details.length > 1);
  assert.ok(details.every(detail => !('open' in detail.attrs) && !detail.open));
  for (const expected of [
    'Synthetic issuer', 'Mengapa masuk daftar riset?', 'Aktivitas dan nilai transaksi memenuhi',
    'Harga naik pada kedua periode', 'Netral', 'Belum tersedia', 'Synthetic OHLCV',
    'Nilai asli skor riset61.256789', '0.423456789', 'Synthetic event', 'Synthetic factor source', 'Kontribusi 15', cutoff,
  ]) {
    assert.ok(content(research).includes(expected), expected);
  }
  assert.equal(descendants(research, node => node.tag === 'a')[0].attrs.href, '/stock-analysis?symbol=TEST0');

  descendants(research, node => node.tag === 'button')[0].events.click();
  app.tick();
  assert.equal(researchView(host), undefined);
  candidateActions(host)[1].events.click();
  app.tick();
  assert.match(content(researchView(host)), /TEST1.*Nama emiten belum tersedia/);
});

test('formats scores in Indonesian without changing or sorting the API values', async t => {
  const order = [ranking[1], ranking[0], ranking[2]];
  const apiRanking = order.map((candidate, index) => ({
    ...candidate,
    assessment: { ...candidate.assessment, overallScore: index === 2 ? 98.5 : 61.256789 },
  }));
  const { app, host } = await renderSnapshot(t, {
    ...rankedSnapshot,
    scoring: { ...rankedSnapshot.scoring, ranking: apiRanking },
  });
  assert.deepEqual(candidateActions(host).map(button => button.id), ['candidate-TEST1', 'candidate-TEST0', 'candidate-TEST2']);
  const scores = descendants(host, node => node.tag === 'data');
  assert.deepEqual(scores.map(content), ['61,26', '61,26', '98,5']);
  assert.deepEqual(scores.map(node => node.attrs.value), ['61.256789', '61.256789', '98.5']);
  assert.match(content(host), /model riset, bukan probabilitas keuntungan atau sinyal beli\/jual/);

  candidateActions(host)[0].events.click();
  app.tick();
  const research = researchView(host);
  assert.match(content(research), /Skor riset 61,26/);
  assert.match(content(research), /Nilai asli skor riset61\.256789/);
});

for (const [quality, label] of [
  ['INCOMPLETE', 'Data belum lengkap'],
  ['STALE', 'Data kedaluwarsa'],
  ['UNVERIFIED', 'Data belum terverifikasi'],
]) {
  test(`${quality} preserves research details while hiding scores`, async t => {
    const { app, host } = await renderSnapshot(t, { ...rankedSnapshot, mode: 'DEVELOPMENT_PERSISTENCE', quality });
    const text = content(host);
    assert.ok(text.includes('DEVELOPMENT_PERSISTENCE'));
    assert.ok(text.includes(label));
    assert.doesNotMatch(text, /Skor riset|Skor faktor|Kontribusi/);
    candidateActions(host)[0].events.click();
    app.tick();
    const details = content(researchView(host));
    assert.match(details, /Momentum harga/);
    assert.doesNotMatch(details, /Skor riset|Skor faktor|Kontribusi/);
    if (quality !== 'INCOMPLETE') assert.doesNotMatch(details, /Harga naik pada kedua periode/);
  });
}

test('unavailable storage identifies failure, missing shortlist and missing provenance', async t => {
  const { host } = await renderSnapshot(t, {
    ...emptySnapshot, mode: 'DEVELOPMENT_PERSISTENCE', quality: 'UNAVAILABLE',
    failure: 'HISTORICAL_STORAGE_UNAVAILABLE',
  });
  const text = content(host);
  for (const label of ['UNAVAILABLE', 'HISTORICAL_STORAGE_UNAVAILABLE', 'Shortlist belum tersedia',
    'Provenance belum tersedia']) {
    assert.ok(text.includes(label), label);
  }
  assert.equal(descendants(host, node => node.attrs['aria-controls'] === 'research-detail').length, 0);
  assert.equal(researchView(host), undefined);
});

test('navigation activates the screener and keeps Weekly Top 5 and Stock Analysis connected', async t => {
  const app = await application([provideRouter(routes), provideLocationMocks(), {
    provide: IdxApi,
    useValue: {
      stockScreener: () => of(emptySnapshot),
      stocks: () => of([]),
      weeklyTopFive: () => of([{ symbol: 'TEST', name: 'Synthetic listing', weeklyChangePercent: 2 }]),
      stock: symbol => of({ symbol, name: 'Synthetic listing', sessionDate: '2026-11-13', closeIdr: 100,
        weeklyChangePercent: 2, sector: 'Test' }),
    },
  }]);
  t.after(() => app.destroy());
  const host = mount(app, AppComponent);
  const router = app.injector.get(Router);
  const links = () => descendants(host, node => node.tag === 'a');
  const heading = () => content(descendants(host, node => node.tag === 'h1')[0]);

  await router.navigateByUrl('/stock-screener');
  app.tick();
  await app.whenStable();
  assert.equal(heading(), 'Stock Screener');
  const screener = links().find(link => link.attrs['aria-label'] === 'Stock Screener');
  assert.equal(screener.attrs.href, '/stock-screener');
  assert.equal(screener.attrs['aria-current'], 'page');

  await router.navigateByUrl('/weekly-top-five');
  app.tick();
  await app.whenStable();
  assert.equal(heading(), 'Weekly Top 5');
  assert.equal(screener.attrs['aria-current'], undefined);
  const stock = links().find(link => content(link).includes('Synthetic listing'));
  assert.equal(stock.attrs.href, '/stock-analysis?symbol=TEST');

  await router.navigateByUrl(stock.attrs.href);
  app.tick();
  await app.whenStable();
  assert.equal(heading(), 'Stock Analysis');
  assert.match(content(host), /TEST · Synthetic listing/);
});

test('overview starts the research journey and a headline links to its issuer', async t => {
  const app = await application([provideRouter(routes), provideLocationMocks(), {
    provide: IdxApi,
    useValue: {
      stocks: () => of([]),
      headlines: () => of([{ symbol: 'TEST', title: 'Synthetic headline', source: 'Fixture', publishedAt: cutoff }]),
      stock: symbol => of({ symbol, name: 'Synthetic issuer', sector: 'Test', sessionDate: '2026-11-13',
        closeIdr: 100, weeklyChangePercent: 0 }),
    },
  }]);
  t.after(() => app.destroy());
  const host = mount(app, AppComponent);
  const router = app.injector.get(Router);

  await router.navigateByUrl('/overview');
  app.tick();
  const primary = descendants(host, node => node.tag === 'a').find(link => content(link).includes('Semua riset'));
  assert.equal(primary.attrs.href, '/stock-screener');

  await router.navigateByUrl('/news-intelligence');
  app.tick();
  assert.match(content(host), /Synthetic headline.*Fixture.*WIB/);
  const issuer = descendants(host, node => node.tag === 'a').find(link => content(link).includes('Lihat saham TEST'));
  assert.equal(issuer.attrs.href, '/stock-analysis?symbol=TEST');
  await router.navigateByUrl(issuer.attrs.href);
  app.tick();
  assert.match(content(host), /TEST · Synthetic issuer/);
});

test('paper trading submits a lot estimate and emphasizes the gross fixture value', async t => {
  const calls = [];
  const app = await application([provideRouter(routes), provideLocationMocks(), {
    provide: IdxApi,
    useValue: {
      paperEstimate: (symbol, lots) => {
        calls.push({ symbol, lots });
        return of({ symbol, lots, shares: 200, priceIdr: 100, grossIdr: 20_000 });
      },
    },
  }]);
  t.after(() => app.destroy());
  const host = mount(app, AppComponent);
  await app.injector.get(Router).navigateByUrl('/paper-trading');
  app.tick();
  const [ticker, lots] = descendants(host, node => node.tag === 'input');
  ticker.value = ' test ';
  lots.valueAsNumber = 2;
  const form = descendants(host, node => node.tag === 'form')[0];
  form.events.submit({ preventDefault() {} });
  app.tick();
  assert.deepEqual(calls, [{ symbol: 'TEST', lots: 2 }]);
  assert.match(content(host), /Estimasi TEST.*20,000.*Nilai bruto sebelum biaya.*Jumlah saham200/);
});
