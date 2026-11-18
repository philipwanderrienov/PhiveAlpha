// Optional real-browser verification. Dependencies live in /tmp, never in production bundles.
import assert from 'node:assert/strict';
import { readFileSync, writeFileSync } from 'node:fs';
import { chromium } from '/tmp/coinmind-phase15-browser/node_modules/playwright/index.mjs';
import AxeBuilder from '/tmp/coinmind-phase15-browser/node_modules/@axe-core/playwright/dist/index.mjs';

const base = process.env.COINMIND_E2E_UI_URL;
const artifacts = process.env.COINMIND_E2E_ARTIFACTS;
assert.match(base ?? '', /^http:\/\/127\.0\.0\.1:\d+$/);
assert.match(artifacts ?? '', /^\/tmp\/coinmind-phase15-e2e-/);
const snapshots = JSON.parse(readFileSync(`${artifacts}/status-snapshots.json`, 'utf8'));
const results = [];
const browser = await chromium.launch({ headless: true });
try {
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, serviceWorkers: 'block' });
  const page = await context.newPage();
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  let snapshot = null;
  let fetchFails = false;
  let statusRequests = 0;
  const blockedExternalOrigins = new Set();
  await page.route('**/*', async route => {
    const url = new URL(route.request().url());
    if (url.origin !== base) {
      blockedExternalOrigins.add(url.origin);
      return route.abort();
    }
    if (url.pathname === '/api/v1/ai/status') {
      statusRequests++;
      if (fetchFails) return route.fulfill({ status: 503, contentType: 'application/json', body: '{}' });
      if (snapshot) return route.fulfill({ json: snapshot });
    }
    return route.continue();
  });
  await page.clock.install();
  await page.goto(base, { waitUntil: 'domcontentloaded' });
  const summary = page.locator('.header-ai-status summary');
  await summary.waitFor();
  await page.waitForFunction(() => document.querySelector('.header-ai-status summary')?.textContent?.trim() === 'AI Tidak Tersedia');
  assert.ok(statusRequests > 0);
  results.push({ check: 'real HTTP endpoint AUTH_ERROR maps to AI Tidak Tersedia', statusRequests });
  async function pollStatus() {
    const pending = page.waitForResponse(response => new URL(response.url()).pathname === '/api/v1/ai/status');
    await page.clock.fastForward(30_001);
    const response = await pending;
    await response.finished();
    assert.equal(response.status(), fetchFails ? 503 : 200);
    if (!fetchFails && snapshot) assert.equal((await response.json()).state, snapshot.state);
  }
  // Exercise the existing 30-second timer without waiting wall time; requests still use HTTP.
  snapshot = snapshots.ready;
  const before = statusRequests;
  await pollStatus();
  await page.waitForFunction(() => document.querySelector('.header-ai-status summary')?.textContent?.trim() === 'AI Aktif');
  assert.ok(statusRequests > before);
  results.push({ check: '30-second Angular polling updates actual production component', statusRequests });

  for (const [name, label] of [
    ['open', 'AI Sedang Mengalami Gangguan'], ['half_open', 'AI Sedang Mengalami Gangguan'],
    ['ready', 'AI Aktif'], ['quota', 'AI Sedang Mengalami Gangguan'],
    ['429', 'AI Sedang Mengalami Gangguan'], ['401', 'AI Tidak Tersedia'],
    ['403', 'AI Tidak Tersedia'], ['unverified', 'AI Tidak Tersedia']
  ]) {
    snapshot = snapshots[name];
    assert.ok(snapshot, name);
    await pollStatus();
    await page.waitForFunction(expected => document.querySelector('.header-ai-status summary')?.textContent?.trim() === expected, label);
    assert.equal(await summary.getAttribute('aria-label'), `Detail status AI: ${label}`);
    results.push({ check: 'HTTP snapshot contract -> Indonesian label', name, state: snapshot.state, label });
  }
  snapshot = snapshots.ready;
  await pollStatus();
  await page.waitForFunction(() => document.querySelector('.header-ai-status summary')?.textContent?.trim() === 'AI Aktif');
  fetchFails = true;
  await pollStatus();
  await page.waitForFunction(() => document.querySelector('.header-ai-status summary')?.textContent?.trim() === 'AI Tidak Tersedia');
  results.push({ check: 'failed status HTTP clears previously active indicator' });
  fetchFails = false;
  snapshot = snapshots.open;
  await pollStatus();
  await page.waitForFunction(() => document.querySelector('.header-ai-status summary')?.textContent?.trim() === 'AI Sedang Mengalami Gangguan');

  for (const width of [1440, 1024, 768, 390, 360, 320]) {
    await page.setViewportSize({ width, height: 900 });
    await summary.focus();
    assert.equal(await summary.evaluate(e => e === document.activeElement), true);
    if (!(await page.locator('.header-ai-status').getAttribute('open'))) {
      // Native details uses an empty-string open attribute, so check the DOM property.
      if (!(await page.locator('.header-ai-status').evaluate(e => e.open))) await summary.press('Enter');
    }
    assert.equal(await page.locator('.header-ai-status').evaluate(e => e.open), true);
    const boxes = await page.locator('.header-ai-status').evaluate(e => {
      const rect = el => {
        const r = el.getBoundingClientRect();
        return { x: r.x, y: r.y, width: r.width, height: r.height, right: r.right, bottom: r.bottom };
      };
      return { summary: rect(e.querySelector('summary')), detail: rect(e.querySelector('.header-ai-detail')),
        brand: rect(document.querySelector('.topbar > .brand')),
        viewport: innerWidth, pageWidth: document.documentElement.scrollWidth,
        topbarHeight: document.querySelector('.topbar').getBoundingClientRect().height };
    });
    assert.ok(boxes.summary.x >= 0 && boxes.summary.right <= width, JSON.stringify(boxes));
    assert.ok(boxes.detail.x >= 0 && boxes.detail.right <= width, JSON.stringify(boxes));
    if (width <= 760) {
      assert.equal(boxes.topbarHeight, 64);
      assert.ok(boxes.brand.right <= boxes.summary.x, JSON.stringify(boxes));
    }
    assert.ok(boxes.pageWidth <= width, JSON.stringify(boxes));
    const disclosure = await page.locator('.header-ai-detail').textContent();
    assert.ok(disclosure.includes('Analisis deterministik tetap berjalan'));
    assert.ok(disclosure.includes('Pemulihan paling awal'));
    const a11y = await new AxeBuilder({ page }).include('.header-ai-status').withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze();
    const violations = a11y.violations.map(v => ({ id: v.id, impact: v.impact, nodes: v.nodes.map(n => n.target) }));
    results.push({ check: 'responsive keyboard disclosure and scoped axe', width, boxes, violations });
    await page.screenshot({ path: `${artifacts}/status-${width}.png`, fullPage: false });
    assert.equal(violations.length, 0, JSON.stringify(violations));
    await summary.press('Enter');
    assert.equal(await page.locator('.header-ai-status').evaluate(e => e.open), false);
  }
  assert.deepEqual(errors, []);
  results.push({ check: 'no uncaught application JS errors', blockedExternalOrigins: [...blockedExternalOrigins] });
  console.log(`Browser checks passed: ${results.length}`);
} finally {
  writeFileSync(`${artifacts}/browser-results.json`, JSON.stringify(results, null, 2));
  await browser.close();
}
