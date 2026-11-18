import { readFileSync } from 'node:fs';
import assert from 'node:assert/strict';
import ts from 'typescript';
const url = path => new URL(path, import.meta.url);
const compile = source => ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext, experimentalDecorators: true } }).outputText;
const moduleUrl = code => 'data:text/javascript;base64,' + Buffer.from(code).toString('base64');
const modelCode = compile(readFileSync(url('./src/app/features/tutorial/dashboard-tour.ts'), 'utf8'));
const { DASHBOARD_STEPS: steps, tourIndex, spotlight, popoverPosition } = await import(moduleUrl(modelCode));
let cases = 0;
function check(fn) { fn(); cases++; }
check(() => assert.equal(steps.length, 10));
check(() => assert.deepEqual(steps.map(s => s.view), ['dashboard','dashboard','dashboard','dashboard','dashboard','news','news','news','dashboard','dashboard']));
check(() => assert.equal(tourIndex(-1), 0));
check(() => assert.equal(tourIndex(10), 9));
check(() => assert.equal(tourIndex(4), 4));
check(() => assert.deepEqual(spotlight({left:-20,top:-20,right:350,bottom:900},320,640), {left:4,top:4,width:312,height:632}));
const template = readFileSync(url('./src/app/app.component.html'), 'utf8');
for (const step of steps) check(() => assert.ok(template.includes(step.target), `Stable target ${step.target}`));
const emitted = [];
let frame;
const buttons = Array.from({length:5}, (_, i) => ({ focus: () => { globalThis.document.activeElement = buttons[i]; } }));
const panel = { offsetHeight: 260, focus() {} };
const target = {getClientRects: () => [1], scrollIntoView: options => assert.equal(options.behavior, 'instant'), getBoundingClientRect: () => ({left:10,top:20,right:310,bottom:80})};
globalThis.innerWidth = 390; globalThis.innerHeight = 844;
globalThis.requestAnimationFrame = fn => { frame = fn; return 1; };
globalThis.cancelAnimationFrame = () => {};
globalThis.ResizeObserver = class {observe() {} disconnect() {}};
globalThis.document = {removeEventListener() {},querySelector: () => target, activeElement: null};
const mockAngular = `
class ElementRef {}
const Component = () => x => x, HostListener = () => () => {}, afterNextRender = () => {};
const signal = value => { const fn = () => value; fn.set = v => value = v; return fn; };
const output = () => ({ emit: value => globalThis.__emitted.push(value) });
const inject = () => globalThis.__host;
`;
globalThis.__emitted = emitted;
globalThis.__host = {nativeElement: {querySelector: () => panel, querySelectorAll: () => buttons}};
let source = readFileSync(url('./src/app/features/tutorial/guided-tour.component.ts'), 'utf8');
source = source.replace(/^import .*from '@angular\/core';\n/m, '').replace("'./dashboard-tour'", JSON.stringify(moduleUrl(modelCode)));
const { GuidedTourComponent } = await import(moduleUrl(mockAngular + compile(source)));
const tour = new GuidedTourComponent();
check(() => { tour.go(0); frame(); assert.equal(tour.index(),0); assert.equal(tour.missing(),false); });
check(() => { tour.next(); frame(); assert.equal(tour.index(),1); });
check(() => { tour.go(7); frame(); assert.equal(tour.step().target,'news-ai'); });
check(() => { tour.go(0); frame(); assert.equal(tour.index(),0); });
check(() => { tour.go(9); frame(); tour.next(); assert.equal(emitted.at(-1),'completed'); });
check(() => { tour.keydown({key:'Escape',preventDefault(){}}); assert.equal(emitted.at(-1),'exited'); });
check(() => { tour.go(3); frame(); tour.keydown({key:'ArrowLeft',preventDefault(){}}); assert.equal(tour.index(),2); });
check(() => { document.querySelector = () => null; tour.go(6); frame(); assert.equal(tour.waiting(),true); assert.equal(tour.missing(),false); });
check(() => { tour.deadline = 0; frame(); assert.equal(tour.missing(),true); assert.equal(tour.waiting(),false); });
check(() => { document.querySelector = () => target; frame(); assert.equal(tour.missing(),false); assert.equal(tour.box().left,5); });
check(() => { tour.go(8); frame(); assert.equal(tour.step().view,'dashboard'); assert.equal(tour.waiting(),false); });
for (const [box, expected] of [
  [{left:400,top:30,width:200,height:50},'bottom'],
  [{left:400,top:700,width:200,height:50},'top'],
  [{left:10,top:100,width:100,height:600},'right'],
  [{left:1000,top:100,width:100,height:600},'left']
]) check(() => { const p = popoverPosition(box,{width:300,height:250},1200,800); assert.equal(p.placement,expected); assert.ok(p.left>=12 && p.top>=12 && p.left+300<=1188 && p.top+250<=788); });
check(() => { const p = popoverPosition({left:0,top:0,width:320,height:600},{width:296,height:400},320,640); assert.ok(p.left>=12 && p.top>=12 && p.left+296<=308 && p.top+400<=628); });
check(() => assert.ok(template.includes('[attr.inert]="tutorialActive()')));
check(() => assert.ok(!template.includes('<details class="tutorial-menu"')));
check(() => assert.ok(template.includes('@if (newsLoading())')));
check(() => assert.ok(template.includes('@if (sentimentLoading())')));

check(() => { document.activeElement = buttons[4]; tour.keydown({key:'Tab',shiftKey:false,preventDefault(){}}); assert.equal(document.activeElement,buttons[0]); });
check(() => { document.activeElement = buttons[0]; tour.keydown({key:'Tab',shiftKey:true,preventDefault(){}}); assert.equal(document.activeElement,buttons[4]); });
check(() => { document.querySelector = selector => selector.includes('headlines') ? target : null; tour.go(6); frame(); tour.deadline = 0; frame(); assert.equal(tour.missing(),true); assert.equal(tour.box().width,310); });
check(() => { document.activeElement = null; tour.keydown({key:'Tab',shiftKey:true,preventDefault(){}}); assert.equal(document.activeElement,buttons[4]); });
check(() => tour.ngOnDestroy());
console.log(`${cases} tutorial unit cases passed (isolated Angular/DOM adapters; visual browser checks still required)`);

// Exercise the actual request handler with delayed subscriptions, errors and superseded responses.
const app = readFileSync(url('./src/app/app.component.ts'), 'utf8');
const body = app.slice(app.indexOf('  private loadNews(): void {'), app.indexOf('  private loadMarketContext(): void {'));
const loadNews = new Function(body.slice(body.indexOf('{') + 1, body.lastIndexOf('}')));
const cell = value => { const fn = () => value; fn.set = next => value = next; return fn; };
const requests = [], sentiments = [];
const state = { newsRequest:0, newsLoading:cell(true), newsFailed:cell(false), sentimentLoading:cell(true), news:cell([]), newsSentiment:cell(null), selectedSymbol:()=> 'BTCUSDT', subscriptions:{add(){}}, marketApi:{getNews:()=>({subscribe: handlers => requests.push(handlers)}),getNewsSentiment:()=>({subscribe: handlers => sentiments.push(handlers)})} };
loadNews.call(state);
assert.equal(state.newsLoading(),true);
assert.equal(state.sentimentLoading(),true);
requests[0].next([]);
assert.equal(state.newsLoading(),false);
assert.equal(state.newsFailed(),false);
loadNews.call(state);
requests[1].next([{id:'article'}]); sentiments[1].next({articleCount:1});
assert.equal(state.news().length,1); assert.equal(state.sentimentLoading(),false);
loadNews.call(state); loadNews.call(state);
requests[2].next([{id:'stale'}]); sentiments[2].next({articleCount:99});
assert.equal(state.newsLoading(),true); assert.equal(state.newsSentiment().articleCount,1);
requests[3].error(); sentiments[3].error();
assert.equal(state.newsLoading(),false); assert.equal(state.newsFailed(),true); assert.equal(state.newsSentiment(),null);
console.log('News request regression: delayed loading, empty, available, failure and stale response passed');
