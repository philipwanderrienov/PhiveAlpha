import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-root',
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  template: `
    <header class="shell-header">
      <div class="wrap header-inner">
        <a class="brand" routerLink="/overview">PhiveAlpha</a>
        <p class="fixture-label">Simulasi IDX · Data sintetis</p>
      </div>
    </header>
    <nav class="wrap navigation" aria-label="Navigasi utama">
      <a routerLink="/overview" routerLinkActive="active" ariaCurrentWhenActive="page">Overview</a>
      <a routerLink="/weekly-top-five" routerLinkActive="active" ariaCurrentWhenActive="page">Weekly Top 5</a>
      <a routerLink="/stock-analysis" routerLinkActive="active" ariaCurrentWhenActive="page">Stock Analysis</a>
      <a routerLink="/news-intelligence" routerLinkActive="active" ariaCurrentWhenActive="page">News Intelligence</a>
      <a routerLink="/paper-trading" routerLinkActive="active" ariaCurrentWhenActive="page">Paper Trading</a>
    </nav>
    <main class="wrap">
      <router-outlet />
    </main>
  `,
})
export class AppComponent {}
