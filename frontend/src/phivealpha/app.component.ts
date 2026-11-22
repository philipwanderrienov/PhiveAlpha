import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-root',
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  template: `
    <a class="skip-link" href="#main-content">Lewati ke konten</a>
    <div class="app-shell">
      <div class="shell-sidebar">
        <header class="shell-header">
          <a class="brand" routerLink="/overview">
            <span class="brand-mark" aria-hidden="true">P</span> PhiveAlpha
          </a>
          <p class="fixture-label badge"><span class="fixture-context">Simulasi IDX ·&nbsp;</span>Data sintetis</p>
        </header>
        <nav class="navigation" aria-label="Navigasi utama">
          <a routerLink="/overview" routerLinkActive="active" ariaCurrentWhenActive="page" aria-label="Overview">
            <svg class="nav-icon" viewBox="0 0 24 24" aria-hidden="true" focusable="false">
              <path d="m3 10 9-7 9 7v10H3z M9 20v-6h6v6" />
            </svg>
            <span class="nav-short" aria-hidden="true">Overview</span><span class="nav-full">Overview</span>
          </a>
          <a routerLink="/stock-screener" routerLinkActive="active" ariaCurrentWhenActive="page" aria-label="Stock Screener">
            <svg class="nav-icon" viewBox="0 0 24 24" aria-hidden="true" focusable="false">
              <path d="M3 5h18l-7 8v5l-4 2v-7z" />
            </svg>
            <span class="nav-short" aria-hidden="true">Screener</span><span class="nav-full">Stock Screener</span>
          </a>
          <a routerLink="/weekly-top-five" routerLinkActive="active" ariaCurrentWhenActive="page" aria-label="Weekly Top 5">
            <svg class="nav-icon" viewBox="0 0 24 24" aria-hidden="true" focusable="false">
              <path d="m3 17 6-6 4 4 8-9 M15 6h6v6" />
            </svg>
            <span class="nav-short" aria-hidden="true">Top 5</span><span class="nav-full">Weekly Top 5</span>
          </a>
          <a routerLink="/stock-analysis" routerLinkActive="active" ariaCurrentWhenActive="page" aria-label="Stock Analysis">
            <svg class="nav-icon" viewBox="0 0 24 24" aria-hidden="true" focusable="false">
              <path d="M16 16l5 5 M18 11a7 7 0 1 1-14 0 7 7 0 0 1 14 0" />
            </svg>
            <span class="nav-short" aria-hidden="true">Analysis</span><span class="nav-full">Stock Analysis</span>
          </a>
          <a routerLink="/news-intelligence" routerLinkActive="active" ariaCurrentWhenActive="page" aria-label="News Intelligence">
            <svg class="nav-icon" viewBox="0 0 24 24" aria-hidden="true" focusable="false">
              <path d="M5 4h14v16H5z M8 8h8 M8 12h8 M8 16h5" />
            </svg>
            <span class="nav-short" aria-hidden="true">News</span><span class="nav-full">News Intelligence</span>
          </a>
          <a routerLink="/paper-trading" routerLinkActive="active" ariaCurrentWhenActive="page" aria-label="Paper Trading">
            <svg class="nav-icon" viewBox="0 0 24 24" aria-hidden="true" focusable="false">
              <path d="M12 3 3 8l9 5 9-5z M3 12l9 5 9-5 M3 16l9 5 9-5" />
            </svg>
            <span class="nav-short" aria-hidden="true">Paper</span><span class="nav-full">Paper Trading</span>
          </a>
        </nav>
      </div>
      <main id="main-content" tabindex="-1">
        <router-outlet />
      </main>
    </div>
  `,
})
export class AppComponent {}
