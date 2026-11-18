import { readFileSync } from 'node:fs';
import assert from 'node:assert/strict';
import ts from 'typescript';

const source = readFileSync(new URL('./src/app/core/news-presentation.ts', import.meta.url), 'utf8');
const code = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ESNext } }).outputText;
const { newsSourceLabel, safeNewsUrl } = await import('data:text/javascript;base64,' + Buffer.from(code).toString('base64'));
let cases = 0;
for (const [input, expected] of [
  ['coindesk.com', 'CoinDesk'], ['www.coindesk.com', 'CoinDesk'],
  ['cointelegraph.com', 'Cointelegraph'], ['www.cointelegraph.com', 'Cointelegraph'],
  ['  WWW.COINDESK.COM  ', 'CoinDesk'], ['CoinDesk', 'CoinDesk'],
  ['Reuters', 'Reuters'], ['example.org', 'example.org'],
  ['coindesk.com.evil.test', 'coindesk.com.evil.test'],
  ['  Media   Lokal ', 'Media Lokal'], ['Media\u202e Lokal', 'Media Lokal'],
  ['', 'Sumber tidak tersedia'], [' \n ', 'Sumber tidak tersedia'],
  [null, 'Sumber tidak tersedia'], [undefined, 'Sumber tidak tersedia'],
  [123, 'Sumber tidak tersedia'], ['<img src=x onerror=alert(1)>', 'Sumber tidak tersedia'],
  ['x'.repeat(120), 'x'.repeat(100)]
]) {
  assert.equal(newsSourceLabel(input), expected);
  cases++;
}
for (const input of [
  '', ' ', null, undefined, 123, 'javascript:alert(1)', 'data:text/html,<script>alert(1)</script>',
  'file:///etc/passwd', '//example.com/story', '/story', 'ftp://example.com',
  'https://', 'https://user:pass@example.com/story', 'https://trusted.com@evil.test',
  'https://example.com/\narticle', 'https://example.com/space here', 'https://example.com\\@evil.test',
  'https://example.com/\u0000', 'https:example.com'
]) {
  assert.equal(safeNewsUrl(input), null, `Reject ${JSON.stringify(input)}`);
  cases++;
}
for (const [input, expected] of [
  ['https://www.coindesk.com/story?x=1&y=2#section', 'https://www.coindesk.com/story?x=1&y=2#section'],
  ['http://example.org/story', 'http://example.org/story'],
  ['  HTTPS://EXAMPLE.ORG/story  ', 'https://example.org/story'],
  ['https://example.org/a%20b', 'https://example.org/a%20b']
]) {
  assert.equal(safeNewsUrl(input), expected);
  cases++;
}
console.log(`${cases} news source/link unit cases passed`);
