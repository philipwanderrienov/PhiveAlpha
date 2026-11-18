import { Component, ElementRef, HostListener, OnDestroy, afterNextRender, inject, output, signal } from '@angular/core';
import { DASHBOARD_STEPS, spotlight, tourIndex, popoverPosition } from './dashboard-tour';

/** Reusable local Angular guided-tour engine; no API or application action dependencies. */
@Component({
  selector: 'app-guided-tour', standalone: true,
  template: `
    <div class="tour-blocker" aria-hidden="true"></div>
    <div class="tour-spotlight" aria-hidden="true" [style.left.px]="box().left" [style.top.px]="box().top" [style.width.px]="box().width" [style.height.px]="box().height"></div>
    <section class="tour-popover" role="dialog" aria-modal="true" aria-labelledby="tour-title" aria-describedby="tour-description" tabindex="-1" [style.top.px]="panelTop()" [style.left.px]="panelLeft()" [attr.data-placement]="placement()">
      <p aria-live="polite">Langkah {{ index() + 1 }} dari {{ steps.length }}</p>
      <h2 id="tour-title">{{ step().title }}</h2>
      <p id="tour-description">{{ step().text }}</p>
      @if (waiting()) { <p role="status">Menunggu artikel, maksimal 5 detik. Anda tetap dapat melanjutkan.</p> }
      @if (missing()) { <p role="status">Elemen belum terlihat. Anda tetap dapat melanjutkan tutorial.</p> }
      <div class="tour-actions">
        <button type="button" (click)="go(index() - 1)" [disabled]="index() === 0">Sebelumnya</button>
        <button type="button" (click)="next()">{{ index() === steps.length - 1 ? 'Selesai' : 'Berikutnya' }}</button>
        <button type="button" (click)="go(0)">Ulangi</button>
        <button type="button" (click)="finish.emit('skipped')">Lewati tutorial</button>
        <button type="button" (click)="finish.emit('exited')">Keluar</button>
      </div>
    </section>`,
  styles: [`
    :host { position: fixed; inset: 0; z-index: 10000; pointer-events: none; }
    .tour-blocker { position: fixed; inset: 0; pointer-events: auto; }
    .tour-spotlight { position: fixed; border: 3px solid #a79aff; border-radius: 12px; box-shadow: 0 0 0 100vmax #111827bb; }
    .tour-popover { position: fixed; box-sizing: border-box; width: min(460px, calc(100vw - 24px)); max-height: calc(100dvh - 24px); overflow: auto; background: white; color: #172033; padding: 18px; border-radius: 16px; box-shadow: 0 8px 40px #0005; pointer-events: auto; }
    h2 { font-size: 1.15rem; margin: 8px 0; } p { margin: 8px 0; }
    .tour-popover::before { position: absolute; color: #6d5dfc; font-size: 16px; }
    .tour-popover[data-placement="bottom"]::before { content: '▲'; top: 2px; left: 50%; }
    .tour-popover[data-placement="top"]::before { content: '▼'; bottom: 2px; left: 50%; }
    .tour-popover[data-placement="right"]::before { content: '◀'; left: 2px; top: 50%; }
    .tour-popover[data-placement="left"]::before { content: '▶'; right: 2px; top: 50%; }
    .tour-actions {
      display: grid;
      grid-template-columns: repeat(12, minmax(0, 1fr));
      gap: 9px;
      margin-top: 22px;
      padding-top: 16px;
      border-top: 1px solid #eef0f5;
    }

    .tour-actions button {
      grid-column: span 6;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      min-width: 0;
      min-height: 44px;
      padding: 10px 14px;
      border: 1px solid transparent;
      border-radius: 12px;
      font: inherit;
      font-size: 14px;
      font-weight: 600;
      line-height: 1.35;
      cursor: pointer;
      transition: background-color .16s ease,
                  border-color .16s ease,
                  box-shadow .16s ease;
    }

    .tour-actions button:first-child {
      background: #fff;
      border-color: #dce0ea;
      color: #46516b;
    }

    .tour-actions button:first-child:hover:not(:disabled) {
      background: #f6f7fc;
      border-color: #bfc5d8;
    }

    .tour-actions button:nth-child(2) {
      background: #6255e8;
      border-color: #6255e8;
      color: white;
      box-shadow: 0 2px 6px #6255e822;
    }

    .tour-actions button:nth-child(2):hover:not(:disabled) {
      background: #5144cf;
      border-color: #5144cf;
    }

    .tour-actions button:nth-child(n+3) {
      grid-column: span 4;
      background: transparent;
      color: #69718a;
      font-size: 13px;
      font-weight: 500;
    }

    .tour-actions button:nth-child(n+3):hover:not(:disabled) {
      background: #f3f2ff;
      color: #5144cf;
    }

    .tour-actions button:active:not(:disabled) {
      filter: brightness(.96);
    }

    .tour-actions button:focus-visible {
      outline: 3px solid #9389ff;
      outline-offset: 2px;
    }

    .tour-actions button:disabled {
      opacity: .42;
      cursor: not-allowed;
      box-shadow: none;
    }

    @media (max-width: 520px) {
      .tour-actions {
        gap: 6px;
      }

      .tour-actions button {
        padding: 10px 8px;
        font-size: 13px;
      }

      .tour-actions button:nth-child(n+3) {
        grid-column: span 6;
        font-size: 12px;
      }

      .tour-actions button:last-child {
        grid-column: span 12;
      }
    }

    @media (prefers-reduced-motion: reduce) {
      .tour-actions button {
        transition: none;
      }
    }
  `]
})
export class GuidedTourComponent implements OnDestroy {
  readonly steps = DASHBOARD_STEPS;
  readonly index = signal(0);
  readonly missing = signal(false);
  readonly box = signal({ left: 0, top: 0, width: 0, height: 0 });
  readonly waiting = signal(false);
  readonly panelLeft = signal(12);
  readonly placement = signal('bottom');
  readonly panelTop = signal(12);
  readonly prepare = output<number>();
  readonly finish = output<'completed' | 'skipped' | 'exited'>();
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private frame = 0;
  private target?: HTMLElement;
  private deadline = 0;
  private scrollTarget = true;
  private readonly onScroll = () => this.position();
  step() { return this.steps[this.index()]; }

  constructor() { afterNextRender(() => {
    document.addEventListener('scroll', this.onScroll, true);
    this.go(0);
    this.host.nativeElement.querySelector<HTMLElement>('[role="dialog"]')?.focus();
  }); }

  go(index: number): void {
    cancelAnimationFrame(this.frame);
    this.index.set(tourIndex(index));
    this.prepare.emit(this.index());
    this.target = undefined;
    this.deadline = performance.now() + 5000;
    this.scrollTarget = true;
    this.missing.set(false);
    this.waiting.set(false);
    this.frame = requestAnimationFrame(() => this.track());
  }

  private track(): void {
    const wanted = document.querySelector<HTMLElement>(`[data-tour-id="${this.step().target}"]`);
    const visible = wanted && wanted.getClientRects().length ? wanted : undefined;
    const pending = !visible && performance.now() < this.deadline;
    this.waiting.set(pending);
    this.missing.set(!visible && !pending);
    const fallback = !pending && this.step().target === 'news-article'
      ? document.querySelector<HTMLElement>('[data-tour-id="headlines"]') ?? undefined : undefined;
    this.target = visible ?? fallback;
    if (this.target && this.scrollTarget) {
      this.target.scrollIntoView({ block: 'center', behavior: 'instant' });
      this.scrollTarget = false;
    }
    this.position();
    this.frame = requestAnimationFrame(() => this.track());
  }

  next(): void {
    if (this.index() === this.steps.length - 1) this.finish.emit('completed');
    else this.go(this.index() + 1);
  }

  @HostListener('window:resize')
  @HostListener('window:scroll')
  position(): void {
    const rect = this.target?.getBoundingClientRect();
    const box = rect ? spotlight(rect, innerWidth, innerHeight) : { left: 0, top: 0, width: 0, height: 0 };
    this.box.set(box);
    const panelHeight = this.host.nativeElement.querySelector<HTMLElement>('[role="dialog"]')?.offsetHeight ?? 320;
    const panelWidth = this.host.nativeElement.querySelector<HTMLElement>('[role="dialog"]')?.offsetWidth ?? Math.min(460, innerWidth - 24);
    const pos = this.target ? popoverPosition(box, {width: panelWidth, height: panelHeight}, innerWidth, innerHeight)
      : {left: Math.max(12, (innerWidth-panelWidth)/2), top: Math.max(12, (innerHeight-panelHeight)/2), placement:'fallback'};
    this.panelTop.set(pos.top);
    this.panelLeft.set(pos.left);
    this.placement.set(pos.placement);
  }

  @HostListener('document:keydown', ['$event'])
  keydown(event: KeyboardEvent): void {
    if (event.key === 'Escape') { event.preventDefault(); this.finish.emit('exited'); }
    if (event.key === 'ArrowRight') { event.preventDefault(); this.next(); }
    if (event.key === 'ArrowLeft') { event.preventDefault(); this.go(this.index() - 1); }
    if (event.key === 'Tab') {
      const buttons = [...this.host.nativeElement.querySelectorAll<HTMLButtonElement>('button:not(:disabled)')];
      const current = buttons.indexOf(document.activeElement as HTMLButtonElement);
      event.preventDefault();
      buttons[current < 0 ? (event.shiftKey ? buttons.length - 1 : 0) : (current + (event.shiftKey ? -1 : 1) + buttons.length) % buttons.length]?.focus();
    }
  }

  ngOnDestroy(): void { cancelAnimationFrame(this.frame); document.removeEventListener('scroll', this.onScroll, true); }
}
