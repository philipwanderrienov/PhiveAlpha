import '@angular/compiler';
import { HttpClient } from '@angular/common/http';
import { Injector } from '@angular/core';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import ts from 'typescript';

const source = readFileSync(new URL('./src/phivealpha/core/idx-api.ts', import.meta.url), 'utf8');
const compiled = ts.transpileModule(source, {
  compilerOptions: { module: ts.ModuleKind.ESNext, experimentalDecorators: true },
}).outputText.replace(/from '(@angular\/[^']+)'/g, (_, specifier) => `from '${import.meta.resolve(specifier)}'`);
const { IdxApi } = await import(`data:text/javascript;base64,${Buffer.from(compiled).toString('base64')}`);

const cases = [
  ['stocks', api => api.stocks(), ['/api/idx/v1/stocks']],
  ['stock encodes the symbol', api => api.stock('AA/B?#'), ['/api/idx/v1/stocks/AA%2FB%3F%23']],
  ['weekly ranking', api => api.weeklyTopFive(), ['/api/idx/v1/weekly-top-five']],
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
