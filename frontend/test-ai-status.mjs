import { readFileSync } from 'node:fs';
import assert from 'node:assert/strict';
import ts from 'typescript';
const source = readFileSync(new URL('./src/app/core/ai-status-label.ts', import.meta.url), 'utf8');
const code = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ESNext } }).outputText;
const { aiStatusLabel } = await import('data:text/javascript;base64,' + Buffer.from(code).toString('base64'));
for (const [state, expected] of [
  ['READY', 'AI Aktif'], ['DISABLED', 'AI Belum Diaktifkan'], ['MISSING_API_KEY', 'AI Belum Diaktifkan'],
  ['ERROR', 'AI Sedang Mengalami Gangguan'], ['RATE_LIMITED', 'AI Sedang Mengalami Gangguan'],
  ['AUTH_ERROR', 'AI Tidak Tersedia'], ['CONFIGURED_UNVERIFIED', 'AI Tidak Tersedia'],
  ['MOCK', 'AI Tidak Tersedia'], ['DEGRADED', 'AI Tidak Tersedia'], [undefined, 'AI Tidak Tersedia']
]) assert.equal(aiStatusLabel(state), expected);
console.log('10 AI status label cases passed');
